package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        NoteRepository.init(ctx)
    }

    @Test
    fun `count returns correct number for All filter`() = runBlocking {
        NoteRepository.save(Note.new().copy(title = "A"))
        NoteRepository.save(Note.new().copy(title = "B"))
        val count = NoteRepository.count(NoteRepository.ListFilter.All)
        assertEquals(2, count)
    }

    @Test
    fun `count returns zero for empty Favorite filter`() = runBlocking {
        NoteRepository.save(Note.new().copy(title = "X"))
        val count = NoteRepository.count(NoteRepository.ListFilter.Favorite)
        assertEquals(0, count)
    }

    @Test
    fun `list with Uncategorized filter returns notes without notebook`() = runBlocking {
        NoteRepository.save(Note.new().copy(title = "No NB", notebookId = null))
        NoteRepository.save(Note.new().copy(title = "Has NB", notebookId = 1L))
        val list = NoteRepository.list(NoteRepository.ListFilter.Uncategorized)
        assertEquals(1, list.size)
        assertEquals("No NB", list[0].title)
    }
}
