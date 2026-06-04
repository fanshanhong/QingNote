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
class NotebookRepositoryTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        FolderRepository.init(ctx)
        NotebookRepository.init(ctx)
        NoteRepository.init(ctx)
    }

    @Test
    fun `seed creates default notebook id=1 inside default folder`() = runBlocking {
        val list = NotebookRepository.listByFolder(1L)
        assertEquals(1, list.size)
        assertEquals(1L, list[0].id)
        assertTrue(list[0].isDefault)
    }

    @Test
    fun `insert assigns increasing order_index within folder`() = runBlocking {
        val fId = FolderRepository.insert("F1")
        val a = NotebookRepository.insert(fId, "A", "#43A047")
        val b = NotebookRepository.insert(fId, "B", "#1E88E5")
        val list = NotebookRepository.listByFolder(fId)
        assertEquals(listOf(a, b), list.map { it.id })
        assertEquals(0, list[0].orderIndex)
        assertEquals(1, list[1].orderIndex)
    }

    @Test
    fun `rename and updateColor change individual fields`() = runBlocking {
        val id = NotebookRepository.insert(1L, "Old", "#000000")
        NotebookRepository.rename(id, "New")
        NotebookRepository.updateColor(id, "#FBC02D")
        val nb = NotebookRepository.get(id)!!
        assertEquals("New", nb.name)
        assertEquals("#FBC02D", nb.color)
    }

    @Test
    fun `softDelete cascades to notes only`() = runBlocking {
        val fId = FolderRepository.insert("F1")
        val nbId = NotebookRepository.insert(fId, "NB1", "#43A047")
        val noteId = NoteRepository.save(
            com.fan.hwnote.app.model.entity.Note.new().copy(title = "N", notebookId = nbId)
        )
        NotebookRepository.softDelete(nbId)
        assertNull(NotebookRepository.listByFolder(fId).firstOrNull { it.id == nbId })
        val n = NoteRepository.get(noteId)!!
        assertTrue(n.deletedAt > 0L)
    }

    @Test
    fun `move changes folder_id and resets order_index to tail of target folder`() = runBlocking {
        val fA = FolderRepository.insert("A")
        val fB = FolderRepository.insert("B")
        val nbA1 = NotebookRepository.insert(fA, "A1", "#43A047")
        val nbB1 = NotebookRepository.insert(fB, "B1", "#1E88E5")
        NotebookRepository.move(nbA1, fB)
        val listB = NotebookRepository.listByFolder(fB)
        assertEquals(listOf(nbB1, nbA1), listB.map { it.id })
        assertEquals(1, listB[1].orderIndex)
    }

    @Test
    fun `softDelete refuses default notebook`() = runBlocking {
        var threw = false
        try { NotebookRepository.softDelete(1L) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        assertNotNull(NotebookRepository.get(1L))
    }

    @Test
    fun `move refuses default notebook`() = runBlocking {
        val fB = FolderRepository.insert("B")
        var threw = false
        try { NotebookRepository.move(1L, fB) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        assertEquals(1L, NotebookRepository.get(1L)!!.folderId)
    }
}
