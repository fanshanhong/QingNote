package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * M12 T5：ListFilter 由 Category/Uncategorized 切到 Folder/Notebook 后的覆盖测试。
 *
 * 默认 folder_id=1 / notebook_id=1 由 DB v3 迁移种入；这里假设其存在并复用。
 */
@RunWith(RobolectricTestRunner::class)
class NoteRepositoryListFilterTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        NoteRepository.init(ctx)
        FolderRepository.init(ctx)
        NotebookRepository.init(ctx)
    }

    @Test
    fun `filter Notebook matches notebookId`() = runBlocking {
        val nb2 = NotebookRepository.insert(folderId = 1L, name = "工作", color = "#1E88E5")
        val a = NoteRepository.save(Note.new().copy(title = "A", notebookId = 1L))
        val b = NoteRepository.save(Note.new().copy(title = "B", notebookId = nb2))
        val list = NoteRepository.list(filter = NoteRepository.ListFilter.Notebook(nb2))
        assertEquals(listOf(b), list.map { it.id })
    }

    @Test
    fun `filter Folder includes notes whose notebook lives under that folder`() = runBlocking {
        val f2 = FolderRepository.insert("f2")
        val nb2 = NotebookRepository.insert(folderId = f2, name = "本子2", color = "#43A047")
        val under1 = NoteRepository.save(Note.new().copy(title = "A", notebookId = 1L))
        val underF2 = NoteRepository.save(Note.new().copy(title = "B", notebookId = nb2))
        val list = NoteRepository.list(filter = NoteRepository.ListFilter.Folder(f2))
        assertEquals(listOf(underF2), list.map { it.id })
    }

    @Test
    fun `filter Deleted shows only soft-deleted notes`() = runBlocking {
        val a = NoteRepository.save(Note.new().copy(title = "A"))
        val b = NoteRepository.save(Note.new().copy(title = "B"))
        NoteRepository.softDelete(a)
        val list = NoteRepository.list(filter = NoteRepository.ListFilter.Deleted)
        assertEquals(listOf(a), list.map { it.id })
        assertTrue(list.all { it.deletedAt != 0L })
    }
}
