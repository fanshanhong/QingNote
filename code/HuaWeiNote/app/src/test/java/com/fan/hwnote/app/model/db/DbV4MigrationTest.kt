package com.fan.hwnote.app.model.db

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DbV4MigrationTest {

    private lateinit var ctx: Context
    private lateinit var helper: NoteDbHelper

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase("hwnote.db")
        helper = NoteDbHelper(ctx)
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun `fresh db has background column with default plain`() {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("title", "test")
            put("plain_text", "")
            put("content_json", "{}")
            put("created_at", 1L)
            put("updated_at", 1L)
        }
        val id = db.insert("notes", null, cv)
        val cursor = db.query("notes", null, "id = ?", arrayOf(id.toString()), null, null, null)
        cursor.use { c ->
            c.moveToFirst()
            val bg = c.getString(c.getColumnIndexOrThrow("background"))
            assertEquals("plain", bg)
        }
    }

    @Test
    fun `background can be set to linen`() {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("title", "test")
            put("plain_text", "")
            put("content_json", "{}")
            put("created_at", 1L)
            put("updated_at", 1L)
            put("background", "linen")
        }
        val id = db.insert("notes", null, cv)
        val cursor = db.query("notes", null, "id = ?", arrayOf(id.toString()), null, null, null)
        cursor.use { c ->
            c.moveToFirst()
            assertEquals("linen", c.getString(c.getColumnIndexOrThrow("background")))
        }
    }
}
