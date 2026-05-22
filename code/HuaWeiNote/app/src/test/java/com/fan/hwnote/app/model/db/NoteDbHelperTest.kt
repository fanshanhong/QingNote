package com.fan.hwnote.app.model.db

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
            setOf("id", "title", "plain_text", "content_json", "is_favorite", "created_at", "updated_at"),
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
}
