package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun `deletePermanently removes db row`() = runBlocking {
        val id = NoteRepository.save(Note.new(now = 1_000L).copy(title = "to delete"))
        assertTrue(NoteRepository.list().any { it.id == id })

        NoteRepository.deletePermanently(id)

        assertNull(NoteRepository.get(id))
        assertFalse(NoteRepository.list().any { it.id == id })
    }

    @Test
    fun `deletePermanently also removes filesDir notes id directory`() = runBlocking {
        val id = NoteRepository.save(Note.new(now = 1_000L).copy(title = "with image"))
        // 模拟图片：手动创建目录 + 文件
        val noteDir = File(ctx.filesDir, "notes/$id")
        File(noteDir, "images").mkdirs()
        File(noteDir, "images/x.jpg").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(noteDir.exists())

        NoteRepository.deletePermanently(id)

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

    @Test
    fun `softDelete marks deleted_at and excludes from default list`() = runBlocking {
        val a = NoteRepository.save(Note.new().copy(title = "A"))
        val b = NoteRepository.save(Note.new().copy(title = "B"))
        NoteRepository.softDelete(a)
        val visible = NoteRepository.list()
        assertEquals(listOf(b), visible.map { it.id })
    }

    @Test
    fun `restore brings note back to deletedAt zero`() = runBlocking {
        val id = NoteRepository.save(Note.new().copy(title = "X"))
        NoteRepository.softDelete(id)
        NoteRepository.restore(id)
        val n = NoteRepository.get(id)!!
        assertEquals(0L, n.deletedAt)
    }

    @Test
    fun `purgeExpired deletes rows older than ttl and keeps fresh`() = runBlocking {
        val now = 1_000_000_000_000L
        val ttl = 30L * 24 * 60 * 60 * 1000
        val oldId = NoteRepository.save(Note.new().copy(title = "old"))
        val freshId = NoteRepository.save(Note.new().copy(title = "fresh"))
        // 直接改库：oldId.deleted_at = now - ttl - 1（超期）；freshId.deleted_at = now - 1（未超期）
        val db = com.fan.hwnote.app.model.db.NoteDbHelper(ctx).writableDatabase
        db.execSQL("UPDATE notes SET deleted_at = ? WHERE id = ?", arrayOf<Any>(now - ttl - 1, oldId))
        db.execSQL("UPDATE notes SET deleted_at = ? WHERE id = ?", arrayOf<Any>(now - 1, freshId))
        val purged = NoteRepository.purgeExpired(now = now, ttlMs = ttl)
        assertEquals(1, purged)
        assertNull(NoteRepository.get(oldId))
        assertEquals(now - 1, NoteRepository.get(freshId)!!.deletedAt)
    }
}
