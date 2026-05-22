package com.fan.hwnote.app.model.storage

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NoteFileStorageTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `noteDir creates filesDir notes id`() {
        val storage = NoteFileStorage(ctx)
        val dir = storage.noteDir(42L)

        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
        assertEquals(File(ctx.filesDir, "notes/42").absolutePath, dir.absolutePath)
    }

    @Test
    fun `imageDir creates noteDir images and is child of noteDir`() {
        val storage = NoteFileStorage(ctx)
        val imgDir = storage.imageDir(7L)

        assertTrue(imgDir.exists())
        assertEquals("images", imgDir.name)
        assertEquals(File(ctx.filesDir, "notes/7").absolutePath, imgDir.parentFile!!.absolutePath)
    }

    @Test
    fun `imageFile returns child of imageDir`() {
        val storage = NoteFileStorage(ctx)
        val f = storage.imageFile(99L, "abc.jpg")
        assertEquals("abc.jpg", f.name)
        assertEquals("images", f.parentFile!!.name)
    }

    @Test
    fun `deleteNoteDir removes the directory and all contents`() {
        val storage = NoteFileStorage(ctx)
        // setup：创建一些文件
        val imgDir = storage.imageDir(123L)
        File(imgDir, "a.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(imgDir, "b.jpg").writeBytes(byteArrayOf(4, 5, 6))
        assertTrue(File(ctx.filesDir, "notes/123").exists())

        storage.deleteNoteDir(123L)

        assertFalse(File(ctx.filesDir, "notes/123").exists())
    }

    @Test
    fun `deleteNoteDir on nonexistent id is a no-op`() {
        val storage = NoteFileStorage(ctx)
        // 不应抛异常
        storage.deleteNoteDir(99999L)
    }
}
