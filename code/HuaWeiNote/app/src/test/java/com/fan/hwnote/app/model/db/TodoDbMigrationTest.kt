package com.fan.hwnote.app.model.db

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodoDbMigrationTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase(NoteDbHelper.DB_NAME)
    }

    @Test
    fun `onCreate v5 creates todos table with all columns`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = NoteDbHelper(ctx).writableDatabase

        val cursor = db.rawQuery("PRAGMA table_info(todos)", null)
        val columns = mutableSetOf<String>()
        cursor.use { while (it.moveToNext()) columns.add(it.getString(it.getColumnIndexOrThrow("name"))) }

        assertEquals(
            setOf("id", "title", "memo", "is_completed", "is_important", "remind_at",
                "repeat_type", "folder_id", "deleted_at", "created_at", "updated_at"),
            columns,
        )
        db.close()
    }

    @Test
    fun `onCreate v5 creates todos indexes`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = NoteDbHelper(ctx).writableDatabase

        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='todos'", null,
        )
        val indexes = mutableSetOf<String>()
        cursor.use { while (it.moveToNext()) indexes.add(it.getString(0)) }

        assertTrue("缺索引 idx_todos_remind_at: $indexes", indexes.contains("idx_todos_remind_at"))
        assertTrue("缺索引 idx_todos_deleted_at: $indexes", indexes.contains("idx_todos_deleted_at"))
        db.close()
    }

    @Test
    fun `onUpgrade v4 to v5 creates todos table`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

        val dbFile = ctx.getDatabasePath(NoteDbHelper.DB_NAME)
        dbFile.parentFile?.mkdirs()
        val v4Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v4Db.version = 4
        v4Db.execSQL("""CREATE TABLE notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL DEFAULT '',
            plain_text TEXT NOT NULL DEFAULT '',
            content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
            is_favorite INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            category_id INTEGER,
            deleted_at INTEGER NOT NULL DEFAULT 0,
            notebook_id INTEGER,
            background TEXT NOT NULL DEFAULT 'plain'
        )""")
        v4Db.execSQL("""CREATE TABLE folders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            order_index INTEGER NOT NULL DEFAULT 0,
            is_default INTEGER NOT NULL DEFAULT 0,
            deleted_at INTEGER NOT NULL DEFAULT 0
        )""")
        v4Db.execSQL("""CREATE TABLE notebooks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            folder_id INTEGER NOT NULL,
            color TEXT NOT NULL DEFAULT '#9E9E9E',
            order_index INTEGER NOT NULL DEFAULT 0,
            is_default INTEGER NOT NULL DEFAULT 0,
            deleted_at INTEGER NOT NULL DEFAULT 0
        )""")
        v4Db.execSQL("""CREATE TABLE categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            color TEXT NOT NULL,
            order_index INTEGER NOT NULL DEFAULT 0
        )""")
        v4Db.close()

        val v5Db = NoteDbHelper(ctx).writableDatabase

        val tableCheck = v5Db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='todos'", null,
        )
        tableCheck.use { assertTrue("todos 表缺失", it.moveToFirst()) }

        val now = System.currentTimeMillis()
        val cv = android.content.ContentValues().apply {
            put("title", "测试待办")
            put("created_at", now)
            put("updated_at", now)
        }
        val id = v5Db.insert("todos", null, cv)
        assertTrue("插入失败", id > 0)

        val cursor = v5Db.rawQuery("SELECT * FROM todos WHERE id = ?", arrayOf(id.toString()))
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("测试待办", it.getString(it.getColumnIndexOrThrow("title")))
            assertEquals("", it.getString(it.getColumnIndexOrThrow("memo")))
            assertEquals(0, it.getInt(it.getColumnIndexOrThrow("is_completed")))
            assertEquals(0, it.getInt(it.getColumnIndexOrThrow("is_important")))
            assertEquals(0L, it.getLong(it.getColumnIndexOrThrow("remind_at")))
            assertEquals(0, it.getInt(it.getColumnIndexOrThrow("repeat_type")))
            assertTrue(it.isNull(it.getColumnIndexOrThrow("folder_id")))
            assertEquals(0L, it.getLong(it.getColumnIndexOrThrow("deleted_at")))
        }
        v5Db.close()
    }
}
