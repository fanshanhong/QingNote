package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import com.fan.hwnote.app.model.db.NoteDbHelper
import com.fan.hwnote.app.model.entity.Notebook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotebookRepository {

    private lateinit var helper: NoteDbHelper

    fun init(context: Context) {
        helper = NoteDbHelper(context.applicationContext)
    }

    suspend fun listByFolder(folderId: Long): List<Notebook> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Notebook>()
        helper.readableDatabase.rawQuery(
            "SELECT id, folder_id, name, color, order_index, is_default, deleted_at FROM notebooks WHERE folder_id = ? AND deleted_at = 0 ORDER BY is_default DESC, order_index ASC, id ASC",
            arrayOf(folderId.toString())
        ).use { c ->
            while (c.moveToNext()) out += cursorToNotebook(c)
        }
        out
    }

    suspend fun get(id: Long): Notebook? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT id, folder_id, name, color, order_index, is_default, deleted_at FROM notebooks WHERE id = ? AND deleted_at = 0",
            arrayOf(id.toString())
        ).use { c -> if (c.moveToFirst()) cursorToNotebook(c) else null }
    }

    suspend fun insert(folderId: Long, name: String, color: String): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val nextIndex = nextOrderIndex(db, folderId)
        val cv = ContentValues().apply {
            put("folder_id", folderId)
            put("name", name)
            put("color", color)
            put("order_index", nextIndex)
            put("is_default", 0)
            put("deleted_at", 0L)
        }
        db.insert("notebooks", null, cv)
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("name", name) }
        helper.writableDatabase.update("notebooks", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun updateColor(id: Long, color: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("color", color) }
        helper.writableDatabase.update("notebooks", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun softDelete(id: Long) = withContext(Dispatchers.IO) {
        require(id != 1L) { "默认笔记本不可删除" }
        val now = System.currentTimeMillis()
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE notes SET deleted_at = ? WHERE deleted_at = 0 AND notebook_id = ?",
                arrayOf<Any>(now, id)
            )
            val cv = ContentValues().apply { put("deleted_at", now) }
            db.update("notebooks", cv, "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    suspend fun move(id: Long, targetFolderId: Long) = withContext(Dispatchers.IO) {
        require(id != 1L) { "默认笔记本不可跨文件夹移动" }
        val db = helper.writableDatabase
        val nextIndex = nextOrderIndex(db, targetFolderId)
        val cv = ContentValues().apply {
            put("folder_id", targetFolderId)
            put("order_index", nextIndex)
        }
        db.update("notebooks", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun reorderInFolder(folderId: Long, orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { idx, id ->
                if (id == 1L && folderId == 1L) return@forEachIndexed
                val cv = ContentValues().apply { put("order_index", idx) }
                db.update("notebooks", cv, "id = ? AND folder_id = ?", arrayOf(id.toString(), folderId.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    private fun nextOrderIndex(db: android.database.sqlite.SQLiteDatabase, folderId: Long): Int {
        db.rawQuery(
            "SELECT IFNULL(MAX(order_index), -1) + 1 FROM notebooks WHERE deleted_at = 0 AND folder_id = ?",
            arrayOf(folderId.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    private fun cursorToNotebook(c: android.database.Cursor): Notebook = Notebook(
        id = c.getLong(0),
        folderId = c.getLong(1),
        name = c.getString(2),
        color = c.getString(3),
        orderIndex = c.getInt(4),
        isDefault = c.getInt(5) == 1,
        deletedAt = c.getLong(6),
    )
}
