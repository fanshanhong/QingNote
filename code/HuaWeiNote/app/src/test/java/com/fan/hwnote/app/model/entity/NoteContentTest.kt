package com.fan.hwnote.app.model.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteContentTest {

    @Test
    fun `toPlainText returns empty when blocks are empty`() {
        val content = NoteContent(blocks = emptyList(), handwriting = emptyList())
        assertEquals("", content.toPlainText())
    }

    @Test
    fun `toPlainText concatenates text blocks with newlines and skips images`() {
        val content = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", text = "今天会议要点"),
                Block.ImageBlock(id = "b2", fileName = "x.jpg", width = 100, height = 100),
                Block.TextBlock(id = "b3", text = "结论：发版推迟"),
            ),
            handwriting = emptyList(),
        )
        assertEquals("今天会议要点\n结论：发版推迟", content.toPlainText())
    }

    @Test
    fun `toPlainText flattens checklist items and ignores empty blocks`() {
        val content = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", text = "TODO"),
                Block.TextBlock(id = "b2", text = ""), // 空块跳过
                Block.ChecklistBlock(
                    id = "b3",
                    items = mutableListOf(
                        ChecklistItem(checked = true, text = "确认排期"),
                        ChecklistItem(checked = false, text = "联系李雷"),
                    ),
                ),
            ),
            handwriting = emptyList(),
        )
        assertEquals("TODO\n确认排期\n联系李雷", content.toPlainText())
    }
}
