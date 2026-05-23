package com.fan.hwnote.app.view.handwriting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.util.TypedValue
import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.Stroke
import kotlin.random.Random

/**
 * 4 笔种 Paint 工厂。每个 Stroke 调 [paintFor] 取一支配置好的 Paint。
 *
 * Stroke.width 单位是 dp（1/3/6），构造时拿 Context 转 px。
 *
 * PRD §7.5：
 *   pen     STROKE/ROUND/255
 *   brush   STROKE/ROUND/255 + BlurMaskFilter(2px,NORMAL) + 宽×1.3
 *   marker  STROKE/ROUND/140 + 宽×1.6
 *   pencil  STROKE/ROUND/160 + BitmapShader(noise REPEAT,REPEAT)
 */
class BrushPainter(context: Context) {

    private val density = context.resources.displayMetrics.density
    private val noiseShader: BitmapShader by lazy { buildNoiseShader() }
    private val blurFilter by lazy { BlurMaskFilter(2f * density, BlurMaskFilter.Blur.NORMAL) }

    fun paintFor(stroke: Stroke): Paint {
        val basePx = dpToPx(stroke.width)
        val color = runCatching { Color.parseColor(stroke.color) }.getOrDefault(Color.BLACK)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
        when (stroke.brush) {
            BrushType.PEN -> {
                p.alpha = 255
                p.strokeWidth = basePx
            }
            BrushType.BRUSH -> {
                p.alpha = 255
                p.strokeWidth = basePx * 1.3f
                p.maskFilter = blurFilter
            }
            BrushType.MARKER -> {
                p.alpha = 140
                p.strokeWidth = basePx * 1.6f
            }
            BrushType.PENCIL -> {
                p.alpha = 160
                p.strokeWidth = basePx
                p.shader = noiseShader
            }
        }
        return p
    }

    fun dpToPx(dp: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
            android.content.res.Resources.getSystem().displayMetrics)

    /** 16×16 灰度噪点（REPEAT 平铺）。固定种子保证笔记之间纹理一致；不缩放。 */
    private fun buildNoiseShader(): BitmapShader {
        val size = 16
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val rng = Random(42)
        val pixels = ByteArray(size * size)
        for (i in pixels.indices) {
            // 偏黑色噪点：alpha 在 80-180 之间，约 50% 半透明颗粒感
            pixels[i] = (80 + rng.nextInt(100)).toByte()
        }
        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
        return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}
