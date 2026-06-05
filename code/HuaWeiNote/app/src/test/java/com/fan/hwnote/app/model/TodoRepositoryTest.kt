package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.RepeatType
import com.fan.hwnote.app.model.entity.Todo
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
class TodoRepositoryTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        TodoRepository.init(ctx)
    }

    @Test
    fun `insert returns positive id and getById retrieves it`() = runBlocking {
        val todo = Todo.new().copy(title = "买牛奶")
        val id = TodoRepository.insert(todo)
        assertTrue(id > 0)
        val fetched = TodoRepository.getById(id)
        assertNotNull(fetched)
        assertEquals("买牛奶", fetched!!.title)
        assertEquals(false, fetched.isCompleted)
    }

    @Test
    fun `update modifies fields`() = runBlocking {
        val id = TodoRepository.insert(Todo.new().copy(title = "旧标题"))
        val old = TodoRepository.getById(id)!!
        TodoRepository.update(old.copy(title = "新标题", isImportant = true))
        val updated = TodoRepository.getById(id)!!
        assertEquals("新标题", updated.title)
        assertEquals(true, updated.isImportant)
    }

    @Test
    fun `list returns non-deleted todos`() = runBlocking {
        TodoRepository.insert(Todo.new().copy(title = "正常"))
        TodoRepository.insert(Todo.new().copy(title = "已删", deletedAt = 1L))
        val list = TodoRepository.list()
        assertEquals(1, list.size)
        assertEquals("正常", list[0].title)
    }

    @Test
    fun `count returns correct number`() = runBlocking {
        TodoRepository.insert(Todo.new().copy(title = "A"))
        TodoRepository.insert(Todo.new().copy(title = "B"))
        assertEquals(2, TodoRepository.count())
    }

    @Test
    fun `softDelete sets deletedAt`() = runBlocking {
        val id = TodoRepository.insert(Todo.new().copy(title = "待删"))
        TodoRepository.softDelete(id)
        val todo = TodoRepository.getById(id)!!
        assertTrue(todo.deletedAt > 0)
    }

    @Test
    fun `restore clears deletedAt`() = runBlocking {
        val id = TodoRepository.insert(Todo.new().copy(title = "待恢复"))
        TodoRepository.softDelete(id)
        TodoRepository.restore(id)
        val todo = TodoRepository.getById(id)!!
        assertEquals(0L, todo.deletedAt)
    }

    @Test
    fun `deletePermanently removes from database`() = runBlocking {
        val id = TodoRepository.insert(Todo.new().copy(title = "彻底删"))
        TodoRepository.deletePermanently(id)
        assertNull(TodoRepository.getById(id))
    }

    @Test
    fun `purgeExpired deletes old soft-deleted todos`() = runBlocking {
        val now = System.currentTimeMillis()
        val oldTime = now - 31L * 24 * 60 * 60 * 1000
        TodoRepository.insert(Todo.new().copy(title = "旧删除", deletedAt = oldTime))
        TodoRepository.insert(Todo.new().copy(title = "新删除", deletedAt = now))
        val purged = TodoRepository.purgeExpired(now)
        assertEquals(1, purged)
    }
}
