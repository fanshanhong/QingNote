package com.fan.hwnote.app.view.toolbar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.BrushType

class HandwritingToolbarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onColorClicked()
        fun onBrushSelected(type: BrushType)
        fun onEraserClicked()
        fun onPlusClicked()
    }

    var listener: Listener? = null

    private val btnColor: ImageView by lazy { findViewById(R.id.btn_hw_color) }
    private val btnPen: ImageView by lazy { findViewById(R.id.btn_hw_pen) }
    private val btnBrush: ImageView by lazy { findViewById(R.id.btn_hw_brush) }
    private val btnMarker: ImageView by lazy { findViewById(R.id.btn_hw_marker) }
    private val btnPencil: ImageView by lazy { findViewById(R.id.btn_hw_pencil) }
    private val btnEraser: ImageView by lazy { findViewById(R.id.btn_hw_eraser) }
    private val btnPlus: ImageView by lazy { findViewById(R.id.btn_hw_plus) }

    private val brushButtons: List<Pair<ImageView, BrushType>> by lazy {
        listOf(
            btnPen to BrushType.PEN,
            btnBrush to BrushType.BRUSH,
            btnMarker to BrushType.MARKER,
            btnPencil to BrushType.PENCIL,
        )
    }

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.toolbar_handwriting, this, true)
        btnColor.setOnClickListener { listener?.onColorClicked() }
        btnPen.setOnClickListener { listener?.onBrushSelected(BrushType.PEN) }
        btnBrush.setOnClickListener { listener?.onBrushSelected(BrushType.BRUSH) }
        btnMarker.setOnClickListener { listener?.onBrushSelected(BrushType.MARKER) }
        btnPencil.setOnClickListener { listener?.onBrushSelected(BrushType.PENCIL) }
        btnEraser.setOnClickListener { listener?.onEraserClicked() }
        btnPlus.setOnClickListener { listener?.onPlusClicked() }
    }

    fun highlightBrush(type: BrushType) {
        for ((view, t) in brushButtons) view.isSelected = (t == type)
    }

    fun highlightEraser(erasing: Boolean) {
        btnEraser.isSelected = erasing
    }

    fun setColorIndicator(colorHex: String) {
        val c = runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.BLACK)
        btnColor.imageTintList = ColorStateList.valueOf(c)
    }

    fun colorButton(): ImageView = btnColor
}
