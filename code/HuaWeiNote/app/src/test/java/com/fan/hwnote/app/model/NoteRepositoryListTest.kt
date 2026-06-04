package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.entity.NoteContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryListTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        NoteRepository.init(ctx)
    }

    /** 准备 3 条笔记，标题 / 创建时间 / 修改时间 / plainText 各异 */
    private suspend fun seed(): Triple<Long, Long, Long> {
        val nA = Note.new(now = 1_000L).copy(
            title = "Alpha", isFavorite = false,
            content = NoteContent(
                blocks = listOf(Block.TextBlock("b", text = "李雷今天来了")),
                handwriting = emptyList(),
            ),
        )
        val nB = Note.new(now = 2_000L).copy(
            title = "Beta", isFavorite = true,
            content = NoteContent(
                blocks = listOf(Block.TextBlock("b", text = "韩梅梅明天到")),
                handwriting = emptyList(),
            ),
        )
        val nC = Note.new(now = 3_000L).copy(
            title = "Charlie",
            content = NoteContent(
                blocks = listOf(Block.TextBlock("b", text = "李雷与韩梅梅")),
                handwriting = emptyList(),
            ),
        )
        // 顺序保存 — id 自增；createdAt 通过 Note.new(now) 控制
        // 但 updatedAt 由 save 内部 = System.currentTimeMillis()
        // 为了让 updatedAt 也分散，每次插入间稍 sleep
        val idA = NoteRepository.save(nA); Thread.sleep(5)
        val idB = NoteRepository.save(nB); Thread.sleep(5)
        val idC = NoteRepository.save(nC)
        return Triple(idA, idB, idC)
    }

    @Test
    fun `list default sort is updated_at desc`() = runBlocking {
        val (idA, idB, idC) = seed()
        val list = NoteRepository.list()
        assertEquals(listOf(idC, idB, idA), list.map { it.id })
    }

    @Test
    fun `sort by created_at desc uses createdAt field`() = runBlocking {
        val (idA, idB, idC) = seed()
        val list = NoteRepository.list(sortBy = NoteRepository.SortBy.CREATED_DESC)
        // C(3000) > B(2000) > A(1000)
        assertEquals(listOf(idC, idB, idA), list.map { it.id })
    }

    @Test
    fun `search matches title`() = runBlocking {
        seed()
        val list = NoteRepository.list(query = "Alpha")
        assertEquals(1, list.size)
        assertEquals("Alpha", list[0].title)
    }

    @Test
    fun `search matches plain_text`() = runBlocking {
        seed()
        val list = NoteRepository.list(query = "李雷")
        // A 和 C 的 plainText 含李雷
        assertEquals(2, list.size)
        assertTrue(list.all { it.plainText.contains("李雷") })
    }

    @Test
    fun `search with no hit returns empty`() = runBlocking {
        seed()
        val list = NoteRepository.list(query = "找不到的关键词")
        assertTrue(list.isEmpty())
    }

    @Test
    fun `filter Favorite includes only is_favorite notes`() = runBlocking {
        val a = NoteRepository.save(Note.new().copy(title = "A", isFavorite = false))
        val b = NoteRepository.save(Note.new().copy(title = "B", isFavorite = true))
        val list = NoteRepository.list(filter = NoteRepository.ListFilter.Favorite)
        assertEquals(listOf(b), list.map { it.id })
    }

    @Test
    fun `filter Deleted shows only soft-deleted notes`() = runBlocking {
        val a = NoteRepository.save(Note.new().copy(title = "A"))
        val b = NoteRepository.save(Note.new().copy(title = "B"))
        NoteRepository.softDelete(a)
        val list = NoteRepository.list(filter = NoteRepository.ListFilter.Deleted)
        assertEquals(listOf(a), list.map { it.id })
    }

}
