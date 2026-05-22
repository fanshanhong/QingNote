package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.entity.NoteContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositorySaveGetTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // 确保每个测试拿到干净的 DB（Robolectric 每个测试方法默认新 Context）
        ctx.deleteDatabase("hwnote.db")
        NoteRepository.init(ctx)
    }

    @Test
    fun `save inserts new note and returns positive id`() = runBlocking {
        val note = Note.new(now = 1_000L).copy(
            title = "我的第一条笔记",
            content = NoteContent(
                blocks = listOf(Block.TextBlock(id = "b1", text = "hello world")),
                handwriting = emptyList(),
            ),
        )
        val id = NoteRepository.save(note)
        assertTrue("expected id > 0, got $id", id > 0)
    }

    @Test
    fun `get returns null for unknown id`() = runBlocking {
        assertNull(NoteRepository.get(9999L))
    }

    @Test
    fun `save then get returns note with same content and computed plainText`() = runBlocking {
        val note = Note.new(now = 1_000L).copy(
            title = "T",
            content = NoteContent(
                blocks = listOf(
                    Block.TextBlock(id = "b1", heading = Heading.H1, text = "标题段"),
                    Block.TextBlock(id = "b2", text = "正文段"),
                ),
                handwriting = emptyList(),
            ),
        )
        val id = NoteRepository.save(note)
        val loaded = NoteRepository.get(id)

        assertNotNull(loaded)
        assertEquals("T", loaded!!.title)
        assertEquals("标题段\n正文段", loaded.plainText)
        assertEquals(note.content, loaded.content)
        assertEquals(1_000L, loaded.createdAt)
    }

    @Test
    fun `save existing note preserves createdAt and bumps updatedAt`() = runBlocking {
        val original = Note.new(now = 1_000L).copy(title = "v1")
        val id = NoteRepository.save(original)
        val firstLoad = NoteRepository.get(id)!!

        // 模拟时间过去：update 之前等一点（系统时钟）
        Thread.sleep(5)

        val updated = firstLoad.copy(title = "v2")
        NoteRepository.save(updated)
        val secondLoad = NoteRepository.get(id)!!

        assertEquals("v2", secondLoad.title)
        assertEquals(firstLoad.createdAt, secondLoad.createdAt)  // 不变
        assertNotEquals(firstLoad.updatedAt, secondLoad.updatedAt)  // 变
        assertTrue(secondLoad.updatedAt > firstLoad.updatedAt)
    }
}
