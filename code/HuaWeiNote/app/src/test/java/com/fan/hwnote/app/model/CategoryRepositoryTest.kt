package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        CategoryRepository.init(ctx)
        NoteRepository.init(ctx)
    }

    @Test
    fun `insert assigns increasing order_index`() = runBlocking {
        val a = CategoryRepository.insert("工作", "#FDD835")
        val b = CategoryRepository.insert("个人", "#43A047")
        val list = CategoryRepository.list()
        assertEquals(listOf(a, b), list.map { it.id })
        assertEquals(0, list[0].orderIndex)
        assertEquals(1, list[1].orderIndex)
    }

    @Test
    fun `reorder writes new order_index by position`() = runBlocking {
        val a = CategoryRepository.insert("A", "#000000")
        val b = CategoryRepository.insert("B", "#000000")
        val c = CategoryRepository.insert("C", "#000000")
        CategoryRepository.reorder(listOf(c, a, b))
        val list = CategoryRepository.list()
        assertEquals(listOf(c, a, b), list.map { it.id })
    }

    @Test
    fun `update changes name and color`() = runBlocking {
        val id = CategoryRepository.insert("Old", "#000000")
        CategoryRepository.update(id, "New", "#FFFFFF")
        val c = CategoryRepository.get(id)!!
        assertEquals("New", c.name)
        assertEquals("#FFFFFF", c.color)
    }

    @Test
    fun `delete removes the category row`() = runBlocking {
        val id = CategoryRepository.insert("Temp", "#000000")
        assertEquals(1, CategoryRepository.list().size)
        CategoryRepository.delete(id)
        assertEquals(0, CategoryRepository.list().size)
        assertNull(CategoryRepository.get(id))
    }

    @Test
    fun `delete sets associated notes category_id to NULL`() = runBlocking {
        val catId = CategoryRepository.insert("Temp", "#000000")
        val noteId = NoteRepository.save(
            com.fan.hwnote.app.model.entity.Note.new().copy(title = "N1", categoryId = catId)
        )
        CategoryRepository.delete(catId)
        val n = NoteRepository.get(noteId)
        assertNull(n?.categoryId)
    }
}
