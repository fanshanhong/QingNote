package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.fan.hwnote.app.model.db.NoteDbHelper
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
        sortBy: SortBy = SortBy.UPDATED_DESC,
        query: String? = null,
    ): List<Note> = withContext(Dispatchers.IO) {
        val orderBy = when (sortBy) {
            SortBy.UPDATED_DESC -> "updated_at DESC"
            SortBy.CREATED_DESC -> "created_at DESC"
            SortBy.TITLE_ASC -> "title COLLATE NOCASE ASC"
        }
        val (selection, args) = if (!query.isNullOrEmpty()) {
            val like = "%$query%"
            "title LIKE ? OR plain_text LIKE ?" to arrayOf(like, like)
        } else {
            null to null
        }
        val out = mutableListOf<Note>()
        val db = dbHelper.readableDatabase
        val cursor = db.query("notes", null, selection, args, null, null, orderBy)
        cursor.use { c ->
            while (c.moveToNext()) out.add(cursorToNote(c))
        }
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

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("notes", "id = ?", arrayOf(id.toString()))
        fileStorage.deleteNoteDir(id)
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("is_favorite", if (favorite) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.update("notes", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    enum class SortBy { UPDATED_DESC, CREATED_DESC, TITLE_ASC }

    // ----- private -----

    private fun cursorToNote(c: Cursor): Note {
        val contentJson = c.getString(c.getColumnIndexOrThrow("content_json")) ?: ""
        return Note(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
            plainText = c.getString(c.getColumnIndexOrThrow("plain_text")) ?: "",
            isFavorite = c.getInt(c.getColumnIndexOrThrow("is_favorite")) == 1,
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            content = NoteJson.fromJson(contentJson),
        )
    }
}
