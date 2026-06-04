package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderRepositoryTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        FolderRepository.init(ctx)
        NotebookRepository.init(ctx)
        NoteRepository.init(ctx)
    }

    @Test
    fun `seed creates default folder with id=1`() = runBlocking {
        val list = FolderRepository.list()
        assertEquals(1, list.size)
        assertEquals(1L, list[0].id)
        assertTrue(list[0].isDefault)
    }

    @Test
    fun `insert assigns increasing order_index after default`() = runBlocking {
        val a = FolderRepository.insert("工作")
        val b = FolderRepository.insert("生活")
        val list = FolderRepository.list()
        assertEquals(listOf(1L, a, b), list.map { it.id })
        assertEquals(0, list[0].orderIndex)
        assertEquals(1, list[1].orderIndex)
        assertEquals(2, list[2].orderIndex)
    }

    @Test
    fun `rename updates name only`() = runBlocking {
        val id = FolderRepository.insert("Old")
        FolderRepository.rename(id, "New")
        assertEquals("New", FolderRepository.get(id)!!.name)
    }

    @Test
    fun `softDelete cascades to notebooks and notes`() = runBlocking {
        val fId = FolderRepository.insert("F1")
        val nbId = NotebookRepository.insert(fId, "NB1", "#43A047")
        val noteId = NoteRepository.save(
            com.fan.hwnote.app.model.entity.Note.new().copy(title = "N", notebookId = nbId)
        )
        FolderRepository.softDelete(fId)
        assertNull(FolderRepository.list().firstOrNull { it.id == fId })
        assertNull(NotebookRepository.listByFolder(fId).firstOrNull { it.id == nbId })
        val n = NoteRepository.get(noteId)!!
        assertTrue(n.deletedAt > 0L)
    }

    @Test
    fun `softDelete refuses default folder`() = runBlocking {
        var threw = false
        try { FolderRepository.softDelete(1L) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        assertNotNull(FolderRepository.get(1L))
    }

    @Test
    fun `rename refuses default folder`() = runBlocking {
        var threw = false
        try { FolderRepository.rename(1L, "X") } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        assertEquals("默认", FolderRepository.get(1L)!!.name)
    }

    @Test
    fun `reorder writes new order_index by position (default pinned at 0 ignored in list)`() = runBlocking {
        val a = FolderRepository.insert("A")
        val b = FolderRepository.insert("B")
        val c = FolderRepository.insert("C")
        FolderRepository.reorder(listOf(c, a, b))
        val nonDefault = FolderRepository.list().filter { !it.isDefault }
        assertEquals(listOf(c, a, b), nonDefault.map { it.id })
    }
}
