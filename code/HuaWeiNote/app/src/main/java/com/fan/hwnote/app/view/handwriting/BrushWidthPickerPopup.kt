package com.fan.hwnote.app.view.handwriting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import com.fan.hwnote.app.R

class BrushWidthPickerPopup(
    private val context: Context,
    private val currentWidth: Int,
    private val brushColor: String,
    private val onWidthSelected: (Int) -> Unit,
) {

    private data class WidthOption(val widthDp: Int, val dotDiameterDp: Int)

    private val options = listOf(
        WidthOption(1, 8),
        WidthOption(3, 16),
        WidthOption(6, 24),
    )

    fun show(anchor: View) {
        val density = context.resources.displayMetrics.density
        val color = runCatching { Color.parseColor(brushColor) }.getOrDefault(Color.BLACK)
        val primaryColor = ContextCompat.getColor(context, R.color.primary)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (12 * density).toInt(),
                (8 * density).toInt(),
                (12 * density).toInt(),
                (8 * density).toInt(),
            )
        }

        val bg = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 12 * density
        }
        row.background = bg
        row.elevation = 8 * density

        val popup = PopupWindow(
            row,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        )
        popup.elevation = 8 * density

        for (opt in options) {
            val cellSize = (40 * density).toInt()
            val dotSize = (opt.dotDiameterDp * density).toInt()
            val isSelected = opt.widthDp == currentWidth

            val dot = object : View(context) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                }
                private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = primaryColor
                    style = Paint.Style.STROKE
                    strokeWidth = 2 * density
                }

                override fun onDraw(canvas: Canvas) {
                    val cx = width / 2f
                    val cy = height / 2f
                    val r = dotSize / 2f
                    canvas.drawCircle(cx, cy, r, paint)
                    if (isSelected) {
                        canvas.drawCircle(cx, cy, r + 3 * density, borderPaint)
                    }
                }
            }
            dot.layoutParams = LinearLayout.LayoutParams(cellSize, cellSize)
            dot.setOnClickListener {
                onWidthSelected(opt.widthDp)
                popup.dismiss()
            }
            row.addView(dot)
        }

        popup.showAsDropDown(anchor, 0, -(anchor.height + (56 * density).toInt()))
    }
}
