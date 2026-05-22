package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.NoteContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteJsonResilienceTest {

    @Test
    fun `malformed json returns empty content`() {
        val rt = NoteJson.fromJson("not a json {")
        assertEquals(NoteContent.empty(), rt)
    }

    @Test
    fun `empty string returns empty content`() {
        val rt = NoteJson.fromJson("")
        assertEquals(NoteContent.empty(), rt)
    }

    @Test
    fun `missing blocks field defaults to empty list`() {
        val rt = NoteJson.fromJson("""{"handwriting":{"strokes":[]}}""")
        assertTrue(rt.blocks.isEmpty())
    }

    @Test
    fun `missing handwriting field defaults to empty list`() {
        val rt = NoteJson.fromJson("""{"blocks":[]}""")
        assertTrue(rt.handwriting.isEmpty())
    }

    @Test
    fun `unknown block type is skipped`() {
        val json = """
            {
              "blocks": [
                { "type": "text", "id": "b1", "text": "kept" },
                { "type": "weird", "id": "b2" },
                { "type": "text", "id": "b3", "text": "also kept" }
              ],
              "handwriting": { "strokes": [] }
            }
        """.trimIndent()
        val rt = NoteJson.fromJson(json)
        assertEquals(listOf("b1", "b3"), rt.blocks.map { it.id })
    }

    @Test
    fun `unknown span type is dropped but block survives`() {
        val json = """
            {
              "blocks": [
                {
                  "type": "text", "id": "b1", "text": "hello",
                  "spans": [
                    { "start": 0, "end": 1, "type": "bold" },
                    { "start": 1, "end": 2, "type": "rainbow" }
                  ]
                }
              ],
              "handwriting": { "strokes": [] }
            }
        """.trimIndent()
        val rt = NoteJson.fromJson(json)
        assertEquals(1, rt.blocks.size)
        val tb = rt.blocks[0] as com.fan.hwnote.app.model.entity.Block.TextBlock
        assertEquals(1, tb.spans.size)
        assertEquals(com.fan.hwnote.app.model.entity.SpanType.BOLD, tb.spans[0].type)
    }
}
