package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryDeleteFavoriteTest {

    private lateinit var ctx: android.content.Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase("hwnote.db")
        // 清残留文件
        File(ctx.filesDir, "notes").deleteRecursively()
        NoteRepository.init(ctx)
    }

    @Test
    fun `delete removes db row`() = runBlocking {
        val id = NoteRepository.save(Note.new(now = 1_000L).copy(title = "to delete"))
        assertTrue(NoteRepository.list().any { it.id == id })

        NoteRepository.delete(id)

        assertNull(NoteRepository.get(id))
        assertFalse(NoteRepository.list().any { it.id == id })
    }

    @Test
    fun `delete also removes filesDir notes id directory`() = runBlocking {
        val id = NoteRepository.save(Note.new(now = 1_000L).copy(title = "with image"))
        // 模拟图片：手动创建目录 + 文件
        val noteDir = File(ctx.filesDir, "notes/$id")
        File(noteDir, "images").mkdirs()
        File(noteDir, "images/x.jpg").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(noteDir.exists())

        NoteRepository.delete(id)

        assertFalse(noteDir.exists())
    }

    @Test
    fun `setFavorite true makes note favorite`() = runBlocking {
        val id = NoteRepository.save(Note.new(now = 1_000L).copy(title = "n", isFavorite = false))
        NoteRepository.setFavorite(id, true)
        val loaded = NoteRepository.get(id)!!
        assertTrue(loaded.isFavorite)
    }

    @Test
    fun `setFavorite false unmarks favorite`() = runBlocking {
        val id = NoteRepository.save(Note.new(now = 1_000L).copy(title = "n", isFavorite = true))
        NoteRepository.setFavorite(id, false)
        val loaded = NoteRepository.get(id)!!
        assertFalse(loaded.isFavorite)
    }
}
