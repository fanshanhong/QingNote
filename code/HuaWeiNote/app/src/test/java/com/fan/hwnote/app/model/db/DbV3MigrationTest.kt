package com.fan.hwnote.app.model.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DbV3MigrationTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase("hwnote.db")
    }

    @Test
    fun `fresh install creates v3 schema with default folder and notebook`() {
        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase
        // folders 表存在 + 默认行 id=1 isDefault=1
        db.rawQuery("SELECT id, name, is_default FROM folders WHERE id=1", null).use { c ->
            assert(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("默认", c.getString(1))
            assertEquals(1, c.getInt(2))
        }
        // notebooks 表存在 + 默认行 id=1 folder_id=1
        db.rawQuery("SELECT id, name, folder_id, is_default FROM notebooks WHERE id=1", null).use { c ->
            assert(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("默认", c.getString(1))
            assertEquals(1L, c.getLong(2))
            assertEquals(1, c.getInt(3))
        }
        // notes 表的 notebook_id 列存在（空表也能查列）
        db.rawQuery("SELECT notebook_id FROM notes LIMIT 0", null).use { c ->
            assertNotNull(c)
        }
        helper.close()
    }

    @Test
    fun `v2 to v3 upgrade migrates legacy notes notebook_id to 1`() {
        // 模拟 v2 老数据：手工建 v2 schema + 写一条老 note
        val raw = SQLiteDatabase.openOrCreateDatabase(
            ctx.getDatabasePath("hwnote.db").absolutePath, null, null
        )
        raw.execSQL("""
            CREATE TABLE notes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL DEFAULT '',
              plain_text TEXT NOT NULL DEFAULT '',
              content_json TEXT NOT NULL DEFAULT '',
              is_favorite INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              category_id INTEGER,
              deleted_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        raw.execSQL("""
            CREATE TABLE categories (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              color TEXT NOT NULL,
              order_index INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        raw.execSQL("INSERT INTO notes(title, plain_text, content_json, created_at, updated_at) VALUES('老笔记','x','{}', 1, 1)")
        raw.version = 2
        raw.close()

        // 触发升级
        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase

        // 老 note 的 notebook_id 应该被 UPDATE 为 1
        db.rawQuery("SELECT notebook_id FROM notes", null).use { c ->
            assert(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
        }
        // 默认 folder/notebook 都已预置
        db.rawQuery("SELECT COUNT(*) FROM folders WHERE id=1", null).use { c ->
            assert(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        db.rawQuery("SELECT COUNT(*) FROM notebooks WHERE id=1", null).use { c ->
            assert(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun `v2 to v3 keeps legacy categories table data intact`() {
        val raw = SQLiteDatabase.openOrCreateDatabase(
            ctx.getDatabasePath("hwnote.db").absolutePath, null, null
        )
        raw.execSQL("""
            CREATE TABLE notes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL DEFAULT '',
              plain_text TEXT NOT NULL DEFAULT '',
              content_json TEXT NOT NULL DEFAULT '',
              is_favorite INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              category_id INTEGER,
              deleted_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        raw.execSQL("""
            CREATE TABLE categories (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              color TEXT NOT NULL,
              order_index INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        raw.execSQL("INSERT INTO categories(name, color) VALUES('工作','#FDD835')")
        raw.version = 2
        raw.close()

        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase
        db.rawQuery("SELECT name, color FROM categories", null).use { c ->
            assert(c.moveToFirst())
            assertEquals("工作", c.getString(0))
            assertEquals("#FDD835", c.getString(1))
        }
        helper.close()
    }
}
