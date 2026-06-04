package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import com.fan.hwnote.app.model.db.NoteDbHelper
import com.fan.hwnote.app.model.entity.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FolderRepository {

    private lateinit var helper: NoteDbHelper

    fun init(context: Context) {
        helper = NoteDbHelper(context.applicationContext)
    }

    suspend fun list(): List<Folder> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Folder>()
        helper.readableDatabase.rawQuery(
            "SELECT id, name, order_index, is_default, deleted_at FROM folders WHERE deleted_at = 0 ORDER BY is_default DESC, order_index ASC, id ASC",
            null
        ).use { c ->
            while (c.moveToNext()) out += cursorToFolder(c)
        }
        out
    }

    suspend fun get(id: Long): Folder? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT id, name, order_index, is_default, deleted_at FROM folders WHERE id = ? AND deleted_at = 0",
            arrayOf(id.toString())
        ).use { c -> if (c.moveToFirst()) cursorToFolder(c) else null }
    }

    suspend fun insert(name: String): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val nextIndex = nextOrderIndex(db)
        val cv = ContentValues().apply {
            put("name", name)
            put("order_index", nextIndex)
            put("is_default", 0)
            put("deleted_at", 0L)
        }
        db.insert("folders", null, cv)
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        require(id != 1L) { "默认文件夹不可改名" }
        val cv = ContentValues().apply { put("name", name) }
        helper.writableDatabase.update("folders", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun softDelete(id: Long) = withContext(Dispatchers.IO) {
        require(id != 1L) { "默认文件夹不可删除" }
        val now = System.currentTimeMillis()
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE notes SET deleted_at = ? WHERE deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)",
                arrayOf<Any>(now, id)
            )
            val nbCv = ContentValues().apply { put("deleted_at", now) }
            db.update("notebooks", nbCv, "folder_id = ? AND deleted_at = 0", arrayOf(id.toString()))
            val fCv = ContentValues().apply { put("deleted_at", now) }
            db.update("folders", fCv, "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    suspend fun reorder(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { idx, id ->
                if (id == 1L) return@forEachIndexed
                val cv = ContentValues().apply { put("order_index", idx) }
                db.update("folders", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    private fun nextOrderIndex(db: android.database.sqlite.SQLiteDatabase): Int {
        db.rawQuery(
            "SELECT IFNULL(MAX(order_index), -1) + 1 FROM folders WHERE deleted_at = 0",
            null
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    private fun cursorToFolder(c: android.database.Cursor): Folder = Folder(
        id = c.getLong(0),
        name = c.getString(1),
        orderIndex = c.getInt(2),
        isDefault = c.getInt(3) == 1,
        deletedAt = c.getLong(4),
    )
}
