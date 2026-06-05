package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.fan.hwnote.app.model.db.NoteDbHelper
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.json.NoteJson
import com.fan.hwnote.app.model.storage.NoteFileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据层统一门面。所有 IO 都是 suspend，内部已切到 Dispatchers.IO。
 *
 * 用法：在 App.onCreate 调一次 NoteRepository.init(this)；之后从任何地方 NoteRepository.list() 等。
 *
 * 单例形态：object，状态用 lateinit 注入 — init 可重复调（覆盖之前的 dbHelper / fileStorage）。
 */
object NoteRepository {

    private lateinit var dbHelper: NoteDbHelper
    private lateinit var fileStorage: NoteFileStorage

    fun init(context: Context) {
        val app = context.applicationContext
        dbHelper = NoteDbHelper(app)
        fileStorage = NoteFileStorage(app)
    }

    suspend fun list(
        filter: ListFilter = ListFilter.All,
        sortBy: SortBy = SortBy.UPDATED_DESC,
        query: String? = null,
    ): List<Note> = withContext(Dispatchers.IO) {
        val orderBy = when (sortBy) {
            SortBy.UPDATED_DESC -> "updated_at DESC"
            SortBy.CREATED_DESC -> "created_at DESC"
        }
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        when (filter) {
            ListFilter.All -> where += "deleted_at = 0"
            ListFilter.Uncategorized -> where += "deleted_at = 0 AND notebook_id IS NULL"
            ListFilter.Favorite -> where += "deleted_at = 0 AND is_favorite = 1"
            ListFilter.Deleted -> where += "deleted_at != 0"
            is ListFilter.Folder -> {
                where += "deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)"
                args += filter.folderId.toString()
            }
            is ListFilter.Notebook -> {
                where += "deleted_at = 0 AND notebook_id = ?"
                args += filter.notebookId.toString()
            }
        }
        if (!query.isNullOrEmpty()) {
            val like = "%$query%"
            where += "(title LIKE ? OR plain_text LIKE ?)"
            args += like
            args += like
        }
        val selection = where.joinToString(" AND ")
        val out = mutableListOf<Note>()
        val cursor = dbHelper.readableDatabase.query(
            "notes", null, selection, args.toTypedArray(), null, null, orderBy,
        )
        cursor.use { c -> while (c.moveToNext()) out += cursorToNote(c) }
        out
    }

    suspend fun get(id: Long): Note? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query("notes", null, "id = ?", arrayOf(id.toString()), null, null, null)
        cursor.use { c ->
            if (!c.moveToFirst()) null else cursorToNote(c)
        }
    }

    /**
     * 保存笔记。
     * - 若 note.id == 0L：插入新行，使用 note.createdAt（>0 时）或 now；返回新生成的 id。
     * - 若 note.id  > 0：UPDATE 该行，createdAt 不动，updatedAt = now；返回原 id。
     */
    suspend fun save(note: Note): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("title", note.title)
            put("plain_text", note.content.toPlainText())
            put("content_json", NoteJson.toJson(note.content))
            put("is_favorite", if (note.isFavorite) 1 else 0)
            put("updated_at", now)
            if (note.categoryId == null) putNull("category_id") else put("category_id", note.categoryId)
            put("deleted_at", note.deletedAt)
            if (note.notebookId == null) putNull("notebook_id") else put("notebook_id", note.notebookId)
        }
        val db = dbHelper.writableDatabase
        if (note.id == 0L) {
            val createdAt = if (note.createdAt > 0) note.createdAt else now
            cv.put("created_at", createdAt)
            db.insert("notes", null, cv)
        } else {
            db.update("notes", cv, "id = ?", arrayOf(note.id.toString()))
            note.id
        }
    }

    /** 软删除：标记 deleted_at = now。文件不动（restore 后还要用）。 */
    suspend fun softDelete(id: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("deleted_at", System.currentTimeMillis()) }
        dbHelper.writableDatabase.update("notes", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    /** 恢复：deleted_at = 0。 */
    suspend fun restore(id: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("deleted_at", 0L) }
        dbHelper.writableDatabase.update("notes", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    /** 彻底删除：DELETE 行 + 删本地文件目录。 */
    suspend fun deletePermanently(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("notes", "id = ?", arrayOf(id.toString()))
        fileStorage.deleteNoteDir(id)
    }

    /** 清理超 ttlMs 的软删笔记。返回清理的笔记数。 */
    suspend fun purgeExpired(
        now: Long = System.currentTimeMillis(),
        ttlMs: Long = 30L * 24 * 60 * 60 * 1000,
    ): Int = withContext(Dispatchers.IO) {
        val cutoff = now - ttlMs
        val db = dbHelper.writableDatabase
        val ids = mutableListOf<Long>()
        db.query(
            "notes", arrayOf("id"),
            "deleted_at > 0 AND deleted_at < ?", arrayOf(cutoff.toString()),
            null, null, null,
        ).use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        for (id in ids) {
            db.delete("notes", "id = ?", arrayOf(id.toString()))
            fileStorage.deleteNoteDir(id)
        }
        ids.size
    }

    /**
     * 扫描某笔记本地 images/ + audio/ 目录，删除当前 content.blocks 未引用的孤儿文件。
     *
     * 用于 M11 撤销 / 重做：编辑器删块时不立即 rm 文件（保证 undo 能复活），
     * 保存成功后异步清理本次操作产生的真正孤儿。
     *
     * - 容错：任何 IOException 仅 log，不抛
     * - 幂等：多次调用对同一份 note 行为一致
     */
    suspend fun cleanOrphanFiles(noteId: Long, note: Note) =
        withContext(Dispatchers.IO) {
            if (noteId <= 0L) return@withContext
            val referenced = mutableSetOf<String>()
            for (b in note.content.blocks) {
                when (b) {
                    is Block.ImageBlock -> referenced += b.fileName
                    is Block.AudioBlock -> referenced += b.fileName
                    is Block.TextBlock, is Block.ChecklistBlock -> Unit
                }
            }
            runCatching {
                fileStorage.imageDir(noteId).listFiles()?.forEach { f ->
                    if (f.isFile && f.name !in referenced) f.delete()
                }
            }.onFailure { android.util.Log.w("NoteRepository", "cleanOrphan image failed", it) }
            runCatching {
                fileStorage.audioDir(noteId).listFiles()?.forEach { f ->
                    if (f.isFile && f.name !in referenced) f.delete()
                }
            }.onFailure { android.util.Log.w("NoteRepository", "cleanOrphan audio failed", it) }
        }

    suspend fun count(filter: ListFilter): Int = withContext(Dispatchers.IO) {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        when (filter) {
            ListFilter.All -> where += "deleted_at = 0"
            ListFilter.Uncategorized -> where += "deleted_at = 0 AND notebook_id IS NULL"
            ListFilter.Favorite -> where += "deleted_at = 0 AND is_favorite = 1"
            ListFilter.Deleted -> where += "deleted_at != 0"
            is ListFilter.Folder -> {
                where += "deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)"
                args += filter.folderId.toString()
            }
            is ListFilter.Notebook -> {
                where += "deleted_at = 0 AND notebook_id = ?"
                args += filter.notebookId.toString()
            }
        }
        val selection = where.joinToString(" AND ")
        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM notes WHERE $selection",
            args.toTypedArray(),
        )
        cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("is_favorite", if (favorite) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.update("notes", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun moveNoteToNotebook(noteId: Long, targetNotebookId: Long?) = withContext(Dispatchers.IO) {
        val cv = ContentValues()
        if (targetNotebookId == null) cv.putNull("notebook_id") else cv.put("notebook_id", targetNotebookId)
        cv.put("updated_at", System.currentTimeMillis())
        dbHelper.writableDatabase.update("notes", cv, "id = ?", arrayOf(noteId.toString()))
        Unit
    }

    enum class SortBy { UPDATED_DESC, CREATED_DESC }

    sealed class ListFilter {
        object All : ListFilter()
        object Uncategorized : ListFilter()
        object Favorite : ListFilter()
        object Deleted : ListFilter()
        data class Folder(val folderId: Long) : ListFilter()
        data class Notebook(val notebookId: Long) : ListFilter()
    }

    // ----- private -----

    private fun cursorToNote(c: Cursor): Note {
        val contentJson = c.getString(c.getColumnIndexOrThrow("content_json")) ?: ""
        val catIdx = c.getColumnIndexOrThrow("category_id")
        return Note(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
            plainText = c.getString(c.getColumnIndexOrThrow("plain_text")) ?: "",
            isFavorite = c.getInt(c.getColumnIndexOrThrow("is_favorite")) == 1,
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            content = NoteJson.fromJson(contentJson),
            categoryId = if (c.isNull(catIdx)) null else c.getLong(catIdx),
            deletedAt = c.getLong(c.getColumnIndexOrThrow("deleted_at")),
            notebookId = c.getColumnIndex("notebook_id").let { idx ->
                if (idx < 0 || c.isNull(idx)) null else c.getLong(idx)
            },
        )
    }
}
