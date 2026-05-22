package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.NoteContent
import com.fan.hwnote.app.model.entity.Stroke
import com.fan.hwnote.app.model.entity.StrokePoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteJsonStrokeTest {

    private fun roundTrip(content: NoteContent): NoteContent =
        NoteJson.fromJson(NoteJson.toJson(content))

    @Test
    fun `single stroke round-trips`() {
        val src = NoteContent(
            blocks = emptyList(),
            handwriting = listOf(
                Stroke(
                    brush = BrushType.PEN,
                    color = "#000000",
                    width = 3,
                    points = listOf(
                        StrokePoint(120, 80, 17),
                        StrokePoint(125, 82, 38),
                        StrokePoint(130, 86, 55),
                    ),
                ),
            ),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `all four brush types round-trip`() {
        val src = NoteContent(
            blocks = emptyList(),
            handwriting = listOf(
                Stroke(BrushType.PEN, "#000000", 1, listOf(StrokePoint(0, 0, 0), StrokePoint(10, 10, 5))),
                Stroke(BrushType.BRUSH, "#FF0000", 3, listOf(StrokePoint(0, 0, 0), StrokePoint(20, 5, 8))),
                Stroke(BrushType.MARKER, "#00FF00", 6, listOf(StrokePoint(50, 50, 0), StrokePoint(60, 55, 12))),
                Stroke(BrushType.PENCIL, "#0000FF", 1, listOf(StrokePoint(100, 100, 0), StrokePoint(110, 105, 9))),
            ),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `multiple strokes preserve order`() {
        val src = NoteContent(
            blocks = emptyList(),
            handwriting = listOf(
                Stroke(BrushType.PEN, "#111111", 1, listOf(StrokePoint(0, 0, 0))),
                Stroke(BrushType.PEN, "#222222", 1, listOf(StrokePoint(0, 0, 0))),
                Stroke(BrushType.PEN, "#333333", 1, listOf(StrokePoint(0, 0, 0))),
            ),
        )
        val rt = roundTrip(src)
        assertEquals(listOf("#111111", "#222222", "#333333"), rt.handwriting.map { it.color })
    }
}
