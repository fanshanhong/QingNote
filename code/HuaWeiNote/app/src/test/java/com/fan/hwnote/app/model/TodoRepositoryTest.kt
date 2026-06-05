package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.RepeatType
import com.fan.hwnote.app.model.entity.Todo
import java.util.Calendar
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

    @Test
    fun `completeTodo marks non-repeat as completed`() = runBlocking {
        val id = TodoRepository.insert(Todo.new().copy(
            title = "一次性",
            repeatType = RepeatType.NONE,
        ))
        TodoRepository.completeTodo(id)
        val todo = TodoRepository.getById(id)!!
        assertTrue(todo.isCompleted)
    }

    @Test
    fun `completeTodo advances daily repeat remindAt`() = runBlocking {
        val now = System.currentTimeMillis()
        val remindAt = now + 60_000L
        val id = TodoRepository.insert(Todo.new().copy(
            title = "每天",
            repeatType = RepeatType.DAILY,
            remindAt = remindAt,
        ))
        TodoRepository.completeTodo(id)
        val todo = TodoRepository.getById(id)!!
        assertEquals(false, todo.isCompleted)
        assertTrue("remindAt 应推进", todo.remindAt > remindAt)
    }

    @Test
    fun `completeTodo advances past-due repeat to future`() = runBlocking {
        val now = System.currentTimeMillis()
        val pastRemind = now - 3L * 24 * 60 * 60 * 1000
        val id = TodoRepository.insert(Todo.new().copy(
            title = "过期重复",
            repeatType = RepeatType.DAILY,
            remindAt = pastRemind,
        ))
        TodoRepository.completeTodo(id)
        val todo = TodoRepository.getById(id)!!
        assertEquals(false, todo.isCompleted)
        assertTrue("remindAt 应推进到未来", todo.remindAt >= now)
    }

    @Test
    fun `uncompleteTodo clears completed flag`() = runBlocking {
        val id = TodoRepository.insert(Todo.new().copy(title = "取消完成"))
        TodoRepository.completeTodo(id)
        TodoRepository.uncompleteTodo(id)
        val todo = TodoRepository.getById(id)!!
        assertEquals(false, todo.isCompleted)
    }

    @Test
    fun `advanceRemindAt weekly adds 7 days`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 5, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val futureBase = cal.timeInMillis + 100L * 24 * 60 * 60 * 1000
        val advanced = TodoRepository.advanceRemindAt(futureBase, RepeatType.WEEKLY)
        assertTrue(advanced > futureBase)
    }

    @Test
    fun `listPendingAlarms returns only active reminders`() = runBlocking {
        val future = System.currentTimeMillis() + 60_000L
        TodoRepository.insert(Todo.new().copy(title = "有提醒", remindAt = future))
        TodoRepository.insert(Todo.new().copy(title = "无提醒", remindAt = 0L))
        TodoRepository.insert(Todo.new().copy(title = "已完成", remindAt = future, isCompleted = true))
        val pending = TodoRepository.listPendingAlarms()
        assertEquals(1, pending.size)
        assertEquals("有提醒", pending[0].title)
    }
}
