package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.fan.hwnote.app.model.db.NoteDbHelper
import com.fan.hwnote.app.model.entity.RepeatType
import com.fan.hwnote.app.model.entity.Todo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object TodoRepository {

    private lateinit var dbHelper: NoteDbHelper

    fun init(context: Context) {
        dbHelper = NoteDbHelper(context.applicationContext)
    }

    suspend fun insert(todo: Todo): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cv = toContentValues(todo).apply {
            put("created_at", if (todo.createdAt > 0) todo.createdAt else now)
            put("updated_at", now)
        }
        dbHelper.writableDatabase.insert("todos", null, cv)
    }

    suspend fun update(todo: Todo) = withContext(Dispatchers.IO) {
        val cv = toContentValues(todo).apply {
            put("updated_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(todo.id.toString()))
        Unit
    }

    suspend fun getById(id: Long): Todo? = withContext(Dispatchers.IO) {
        val cursor = dbHelper.readableDatabase.query(
            "todos", null, "id = ?", arrayOf(id.toString()), null, null, null,
        )
        cursor.use { c -> if (c.moveToFirst()) cursorToTodo(c) else null }
    }

    suspend fun list(
        folderId: Long? = null,
        includeDeleted: Boolean = false,
        hideCompleted: Boolean = false,
    ): List<Todo> = withContext(Dispatchers.IO) {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (!includeDeleted) {
            where += "deleted_at = 0"
        } else {
            where += "deleted_at > 0"
        }
        if (folderId != null) {
            where += "folder_id = ?"
            args += folderId.toString()
        }
        if (hideCompleted) {
            where += "is_completed = 0"
        }
        val selection = where.joinToString(" AND ")
        val out = mutableListOf<Todo>()
        val cursor = dbHelper.readableDatabase.query(
            "todos", null, selection, args.toTypedArray(),
            null, null, "remind_at ASC, created_at DESC",
        )
        cursor.use { c -> while (c.moveToNext()) out += cursorToTodo(c) }
        out
    }

    suspend fun listUncategorized(hideCompleted: Boolean = false): List<Todo> =
        withContext(Dispatchers.IO) {
            val where = mutableListOf("deleted_at = 0", "folder_id IS NULL")
            if (hideCompleted) where += "is_completed = 0"
            val selection = where.joinToString(" AND ")
            val out = mutableListOf<Todo>()
            val cursor = dbHelper.readableDatabase.query(
                "todos", null, selection, null, null, null,
                "remind_at ASC, created_at DESC",
            )
            cursor.use { c -> while (c.moveToNext()) out += cursorToTodo(c) }
            out
        }

    suspend fun count(
        folderId: Long? = null,
        includeDeleted: Boolean = false,
    ): Int = withContext(Dispatchers.IO) {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (!includeDeleted) {
            where += "deleted_at = 0"
        } else {
            where += "deleted_at > 0"
        }
        if (folderId != null) {
            where += "folder_id = ?"
            args += folderId.toString()
        }
        val selection = where.joinToString(" AND ")
        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM todos WHERE $selection", args.toTypedArray(),
        )
        cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    suspend fun countUncategorized(): Int = withContext(Dispatchers.IO) {
        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM todos WHERE deleted_at = 0 AND folder_id IS NULL", null,
        )
        cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    suspend fun softDelete(id: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("deleted_at", System.currentTimeMillis()) }
        dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun softDeleteBatch(ids: List<Long>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply { put("deleted_at", now) }
            for (id in ids) {
                db.update("todos", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    suspend fun restore(id: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("deleted_at", 0L) }
        dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun deletePermanently(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("todos", "id = ?", arrayOf(id.toString()))
    }

    suspend fun purgeExpired(
        now: Long = System.currentTimeMillis(),
        ttlMs: Long = 30L * 24 * 60 * 60 * 1000,
    ): Int = withContext(Dispatchers.IO) {
        val cutoff = now - ttlMs
        dbHelper.writableDatabase.delete(
            "todos", "deleted_at > 0 AND deleted_at < ?", arrayOf(cutoff.toString()),
        )
    }

    suspend fun completeTodo(id: Long) = withContext(Dispatchers.IO) {
        val todo = getByIdSync(id) ?: return@withContext
        if (todo.repeatType != RepeatType.NONE && todo.remindAt > 0) {
            val nextRemind = advanceRemindAt(todo.remindAt, todo.repeatType)
            val cv = ContentValues().apply {
                put("remind_at", nextRemind)
                put("updated_at", System.currentTimeMillis())
            }
            dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(id.toString()))
        } else {
            val cv = ContentValues().apply {
                put("is_completed", 1)
                put("updated_at", System.currentTimeMillis())
            }
            dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(id.toString()))
        }
    }

    suspend fun uncompleteTodo(id: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("is_completed", 0)
            put("updated_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun listPendingAlarms(): List<Todo> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Todo>()
        val cursor = dbHelper.readableDatabase.query(
            "todos", null,
            "remind_at > 0 AND is_completed = 0 AND deleted_at = 0",
            null, null, null, "remind_at ASC",
        )
        cursor.use { c -> while (c.moveToNext()) out += cursorToTodo(c) }
        out
    }

    internal fun advanceRemindAt(remindAt: Long, repeatType: RepeatType): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = remindAt }
        do {
            when (repeatType) {
                RepeatType.DAILY -> cal.add(Calendar.DAY_OF_MONTH, 1)
                RepeatType.WEEKLY -> cal.add(Calendar.DAY_OF_MONTH, 7)
                RepeatType.MONTHLY -> cal.add(Calendar.MONTH, 1)
                RepeatType.YEARLY -> cal.add(Calendar.YEAR, 1)
                RepeatType.NONE -> return remindAt
            }
        } while (cal.timeInMillis < now)
        return cal.timeInMillis
    }

    private fun getByIdSync(id: Long): Todo? {
        val cursor = dbHelper.readableDatabase.query(
            "todos", null, "id = ?", arrayOf(id.toString()), null, null, null,
        )
        return cursor.use { c -> if (c.moveToFirst()) cursorToTodo(c) else null }
    }

    private fun toContentValues(todo: Todo): ContentValues = ContentValues().apply {
        put("title", todo.title)
        put("memo", todo.memo)
        put("is_completed", if (todo.isCompleted) 1 else 0)
        put("is_important", if (todo.isImportant) 1 else 0)
        put("remind_at", todo.remindAt)
        put("repeat_type", todo.repeatType.value)
        if (todo.folderId == null) putNull("folder_id") else put("folder_id", todo.folderId)
        put("deleted_at", todo.deletedAt)
    }

    private fun cursorToTodo(c: Cursor): Todo {
        val folderIdx = c.getColumnIndexOrThrow("folder_id")
        return Todo(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
            memo = c.getString(c.getColumnIndexOrThrow("memo")) ?: "",
            isCompleted = c.getInt(c.getColumnIndexOrThrow("is_completed")) == 1,
            isImportant = c.getInt(c.getColumnIndexOrThrow("is_important")) == 1,
            remindAt = c.getLong(c.getColumnIndexOrThrow("remind_at")),
            repeatType = RepeatType.fromValue(
                c.getInt(c.getColumnIndexOrThrow("repeat_type")),
            ),
            folderId = if (c.isNull(folderIdx)) null else c.getLong(folderIdx),
            deletedAt = c.getLong(c.getColumnIndexOrThrow("deleted_at")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        )
    }
}
