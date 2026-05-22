package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.NoteContent
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteJsonTextBlockTest {

    private fun roundTrip(content: NoteContent): NoteContent =
        NoteJson.fromJson(NoteJson.toJson(content))

    @Test
    fun `empty content round-trips`() {
        val src = NoteContent.empty()
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `plain text block round-trips`() {
        val src = NoteContent(
            blocks = listOf(Block.TextBlock(id = "b1", text = "hello")),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `heading h1 and h2 round-trip`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", heading = Heading.H1, text = "标题一"),
                Block.TextBlock(id = "b2", heading = Heading.H2, text = "标题二"),
                Block.TextBlock(id = "b3", heading = null, text = "正文"),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `all six span types round-trip`() {
        val spans = listOf(
            TextSpan(0, 2, SpanType.BOLD),
            TextSpan(2, 4, SpanType.ITALIC),
            TextSpan(4, 6, SpanType.UNDERLINE),
            TextSpan(6, 8, SpanType.STRIKETHROUGH),
            TextSpan(8, 10, SpanType.FONT_SIZE, value = "large"),
            TextSpan(10, 12, SpanType.COLOR, value = "#FF0000"),
        )
        val src = NoteContent(
            blocks = listOf(Block.TextBlock(id = "b1", text = "粗斜下划线删字号颜色 ", spans = spans)),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }
}
