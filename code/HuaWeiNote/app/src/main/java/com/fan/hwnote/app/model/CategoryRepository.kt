package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import com.fan.hwnote.app.model.db.NoteDbHelper
import com.fan.hwnote.app.model.entity.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CategoryRepository {

    private lateinit var dbHelper: NoteDbHelper

    fun init(context: Context) {
        dbHelper = NoteDbHelper(context.applicationContext)
    }

    suspend fun list(): List<Category> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Category>()
        val cursor = dbHelper.readableDatabase.query(
            "categories", null, null, null, null, null, "order_index ASC, id ASC",
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                out += Category(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    color = c.getString(c.getColumnIndexOrThrow("color")),
                    orderIndex = c.getInt(c.getColumnIndexOrThrow("order_index")),
                )
            }
        }
        out
    }

    suspend fun get(id: Long): Category? = withContext(Dispatchers.IO) {
        val cursor = dbHelper.readableDatabase.query(
            "categories", null, "id = ?", arrayOf(id.toString()), null, null, null,
        )
        cursor.use { c ->
            if (!c.moveToFirst()) return@withContext null
            Category(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                name = c.getString(c.getColumnIndexOrThrow("name")),
                color = c.getString(c.getColumnIndexOrThrow("color")),
                orderIndex = c.getInt(c.getColumnIndexOrThrow("order_index")),
            )
        }
    }

    /** 新建分类。order_index = 当前 MAX + 1（新分类追加到末尾）。返回新生成 id。 */
    suspend fun insert(name: String, color: String): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val maxOrder = db.rawQuery("SELECT COALESCE(MAX(order_index), -1) FROM categories", null)
            .use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }
        val cv = ContentValues().apply {
            put("name", name)
            put("color", color)
            put("order_index", maxOrder + 1)
        }
        db.insert("categories", null, cv)
    }

    suspend fun update(id: Long, name: String, color: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("name", name)
            put("color", color)
        }
        dbHelper.writableDatabase.update("categories", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    /** 删除分类。该分类下的 notes 自动 SET category_id = NULL（手动 UPDATE 实现，SQLite 未启用 FK 级联）。 */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE notes SET category_id = NULL WHERE category_id = ?", arrayOf<Any>(id))
            db.delete("categories", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 批量持久化排序：传 ordered id 列表，按位置写 order_index。 */
    suspend fun reorder(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { idx, id ->
                val cv = ContentValues().apply { put("order_index", idx) }
                db.update("categories", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
