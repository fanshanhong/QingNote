package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.NoteContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteJsonAudioTest {

    @Test
    fun `audio block round trips`() {
        val content = NoteContent(
            blocks = listOf(
                Block.AudioBlock(id = "a-001", fileName = "uuid-1.m4a", durationMs = 12345L),
            ),
            handwriting = emptyList(),
        )
        val json = NoteJson.toJson(content)
        val back = NoteJson.fromJson(json)
        val b = back.blocks.single() as Block.AudioBlock
        assertEquals("a-001", b.id)
        assertEquals("uuid-1.m4a", b.fileName)
        assertEquals(12345L, b.durationMs)
    }

    @Test
    fun `audio with missing duration defaults to 0`() {
        val raw = """{"blocks":[{"type":"audio","id":"a-002","fileName":"x.m4a"}],"handwriting":{"strokes":[]}}"""
        val content = NoteJson.fromJson(raw)
        val b = content.blocks.single() as Block.AudioBlock
        assertEquals(0L, b.durationMs)
    }
}
