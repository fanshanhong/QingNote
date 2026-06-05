package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.ListType
import com.fan.hwnote.app.model.entity.NoteContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NoteJsonParagraphTest {

    private fun roundTrip(content: NoteContent): NoteContent =
        NoteJson.fromJson(NoteJson.toJson(content))

    @Test
    fun `alignment round-trips`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", text = "left", alignment = Alignment.START),
                Block.TextBlock(id = "b2", text = "center", alignment = Alignment.CENTER),
                Block.TextBlock(id = "b3", text = "right", alignment = Alignment.END),
                Block.TextBlock(id = "b4", text = "default"),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `listType round-trips`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", text = "bullet", listType = ListType.BULLET),
                Block.TextBlock(id = "b2", text = "hollow", listType = ListType.HOLLOW_BULLET),
                Block.TextBlock(id = "b3", text = "numbered", listType = ListType.NUMBERED),
                Block.TextBlock(id = "b4", text = "lettered", listType = ListType.LETTERED),
                Block.TextBlock(id = "b5", text = "none"),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `indentLevel round-trips`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", text = "indent0"),
                Block.TextBlock(id = "b2", text = "indent1", indentLevel = 1),
                Block.TextBlock(id = "b3", text = "indent3", indentLevel = 3),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `heading H3-H6 round-trip`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", heading = Heading.H3, text = "h3"),
                Block.TextBlock(id = "b2", heading = Heading.H4, text = "h4"),
                Block.TextBlock(id = "b3", heading = Heading.H5, text = "h5"),
                Block.TextBlock(id = "b4", heading = Heading.H6, text = "h6"),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `missing alignment and listType deserialize as null`() {
        val json = """{"blocks":[{"type":"text","id":"b1","text":"hi","spans":[]}],"handwriting":{"strokes":[]}}"""
        val content = NoteJson.fromJson(json)
        val block = content.blocks.first() as Block.TextBlock
        assertNull(block.alignment)
        assertNull(block.listType)
        assertEquals(0, block.indentLevel)
    }

    @Test
    fun `combined paragraph fields round-trip`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(
                    id = "b1", text = "styled",
                    alignment = Alignment.CENTER,
                    listType = ListType.NUMBERED,
                    indentLevel = 2,
                    heading = Heading.H2,
                ),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }
}
