package com.fan.hwnote.app.model.db

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteDbHelperTest {

    @Test
    fun `creates notes table with all columns`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase

        val cursor = db.rawQuery("PRAGMA table_info(notes)", null)
        val columns = mutableSetOf<String>()
        cursor.use {
            while (it.moveToNext()) columns.add(it.getString(it.getColumnIndexOrThrow("name")))
        }

        assertEquals(
            setOf("id", "title", "plain_text", "content_json", "is_favorite", "created_at", "updated_at", "category_id", "deleted_at"),
            columns,
        )
    }

    @Test
    fun `creates updated_at and is_favorite indexes`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase

        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='notes'", null,
        )
        val indexes = mutableSetOf<String>()
        cursor.use {
            while (it.moveToNext()) indexes.add(it.getString(0))
        }

        assertTrue("缺索引 idx_notes_updated_at: $indexes", indexes.contains("idx_notes_updated_at"))
        assertTrue("缺索引 idx_notes_favorite: $indexes",  indexes.contains("idx_notes_favorite"))
    }

    @Test
    fun `id is auto-increment primary key`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase

        val now = System.currentTimeMillis()
        val cv1 = android.content.ContentValues().apply {
            put("created_at", now); put("updated_at", now)
        }
        val cv2 = android.content.ContentValues().apply {
            put("created_at", now); put("updated_at", now)
        }
        val id1 = db.insert("notes", null, cv1)
        val id2 = db.insert("notes", null, cv2)

        assertTrue("expected id1 > 0, got $id1", id1 > 0)
        assertTrue("expected id2 > id1, got id1=$id1 id2=$id2", id2 > id1)
    }

    @Test
    fun `onCreate v2 has categories table and indexes`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase(NoteDbHelper.DB_NAME)
        val db = NoteDbHelper(ctx).writableDatabase

        // categories 表必须存在
        val tableCheck = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='categories'", null,
        )
        tableCheck.use { assertTrue("categories 表缺失", it.moveToFirst()) }

        // 两个新索引存在
        val idxCursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='notes'", null,
        )
        val idxs = mutableSetOf<String>()
        idxCursor.use { while (it.moveToNext()) idxs.add(it.getString(0)) }
        assertTrue("缺 idx_notes_category: $idxs", idxs.contains("idx_notes_category"))
        assertTrue("缺 idx_notes_deleted: $idxs",  idxs.contains("idx_notes_deleted"))

        db.close()
    }

    @Test
    fun `onUpgrade v1 to v2 alters notes and creates categories`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase(NoteDbHelper.DB_NAME)

        // 手工建 v1 schema 模拟旧装机
        val v1Helper = object : SQLiteOpenHelper(ctx, NoteDbHelper.DB_NAME, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL DEFAULT '',
                        plain_text TEXT NOT NULL DEFAULT '',
                        content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
                        is_favorite INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )""",
                )
                db.execSQL("INSERT INTO notes(title, created_at, updated_at) VALUES('old', 1, 1)")
            }
            override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
        }
        v1Helper.writableDatabase.close()

        // 用 v2 helper 打开 → 触发 onUpgrade
        val v2Db = NoteDbHelper(ctx).writableDatabase
        val cursor = v2Db.rawQuery(
            "SELECT title, category_id, deleted_at FROM notes WHERE title='old'", null,
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("old", it.getString(0))
            assertTrue("category_id 应为 NULL", it.isNull(1))
            assertEquals(0L, it.getLong(2))
        }

        // categories 表也已建出
        val tableCheck = v2Db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='categories'", null,
        )
        tableCheck.use { assertTrue(it.moveToFirst()) }

        v2Db.close()
    }
}
