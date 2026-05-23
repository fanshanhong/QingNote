package com.fan.hwnote.app.view.handwriting

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.Stroke
import com.fan.hwnote.app.model.entity.StrokePoint
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrushPainterTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun stroke(
        brush: BrushType = BrushType.PEN,
        color: String = "#212121",
        width: Int = 3,
    ) = Stroke(
        brush = brush,
        color = color,
        width = width,
        points = listOf(StrokePoint(0, 0, 0)),
    )

    @Test
    fun `same_key_returns_same_paint_instance`() {
        val painter = BrushPainter(context)
        val a = painter.paintFor(stroke())
        val b = painter.paintFor(stroke())
        assertSame(a, b)
    }

    @Test
    fun `different_brush_returns_different_paint`() {
        val painter = BrushPainter(context)
        val pen = painter.paintFor(stroke(brush = BrushType.PEN))
        val marker = painter.paintFor(stroke(brush = BrushType.MARKER))
        assertNotSame(pen, marker)
    }

    @Test
    fun `different_color_returns_different_paint`() {
        val painter = BrushPainter(context)
        val red = painter.paintFor(stroke(color = "#E53935"))
        val blue = painter.paintFor(stroke(color = "#1E88E5"))
        assertNotSame(red, blue)
    }
}
