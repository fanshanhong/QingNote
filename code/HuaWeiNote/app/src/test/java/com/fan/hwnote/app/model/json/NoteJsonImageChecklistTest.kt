package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.ChecklistItem
import com.fan.hwnote.app.model.entity.NoteContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteJsonImageChecklistTest {

    private fun roundTrip(content: NoteContent): NoteContent =
        NoteJson.fromJson(NoteJson.toJson(content))

    @Test
    fun `image block round-trips`() {
        val src = NoteContent(
            blocks = listOf(
                Block.ImageBlock(id = "b1", fileName = "9c2f.jpg", width = 1080, height = 720),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `checklist block round-trips with mixed checked states`() {
        val src = NoteContent(
            blocks = listOf(
                Block.ChecklistBlock(
                    id = "b1",
                    items = mutableListOf(
                        ChecklistItem(checked = true, text = "确认排期"),
                        ChecklistItem(checked = false, text = "联系李雷"),
                        ChecklistItem(checked = false, text = ""),  // 空项也保留
                    ),
                ),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `mixed block types preserve ordering`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "t1", text = "首段"),
                Block.ImageBlock(id = "i1", fileName = "a.jpg", width = 100, height = 100),
                Block.ChecklistBlock(
                    id = "c1",
                    items = mutableListOf(ChecklistItem(checked = true, text = "x")),
                ),
                Block.TextBlock(id = "t2", text = "末段"),
            ),
            handwriting = emptyList(),
        )
        val rt = roundTrip(src)
        assertEquals(src, rt)
        assertEquals(listOf("t1", "i1", "c1", "t2"), rt.blocks.map { it.id })
    }
}
