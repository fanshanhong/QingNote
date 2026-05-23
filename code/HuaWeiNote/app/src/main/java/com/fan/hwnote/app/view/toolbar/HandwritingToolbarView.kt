package com.fan.hwnote.app.view.toolbar

import android.content.Context
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
        fun onDoneClicked()
        fun onBrushClicked(brush: BrushType)
        fun onColorClicked()
        fun onWidthClicked()
        fun onEraserClicked()
        fun onUndoClicked()
        fun onRedoClicked()
        fun onClearClicked()
    }

    var listener: Listener? = null

    private val btnDone: ImageView by lazy { findViewById(R.id.btn_hw_done) }
    private val btnPen: ImageView by lazy { findViewById(R.id.btn_hw_pen) }
    private val btnBrush: ImageView by lazy { findViewById(R.id.btn_hw_brush) }
    private val btnMarker: ImageView by lazy { findViewById(R.id.btn_hw_marker) }
    private val btnPencil: ImageView by lazy { findViewById(R.id.btn_hw_pencil) }
    private val btnColor: ImageView by lazy { findViewById(R.id.btn_hw_color) }
    private val btnWidth: ImageView by lazy { findViewById(R.id.btn_hw_width) }
    private val btnEraser: ImageView by lazy { findViewById(R.id.btn_hw_eraser) }
    private val btnUndo: ImageView by lazy { findViewById(R.id.btn_hw_undo) }
    private val btnRedo: ImageView by lazy { findViewById(R.id.btn_hw_redo) }
    private val btnClear: ImageView by lazy { findViewById(R.id.btn_hw_clear) }

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.toolbar_handwriting, this, true)
        btnDone.setOnClickListener { listener?.onDoneClicked() }
        btnPen.setOnClickListener { listener?.onBrushClicked(BrushType.PEN) }
        btnBrush.setOnClickListener { listener?.onBrushClicked(BrushType.BRUSH) }
        btnMarker.setOnClickListener { listener?.onBrushClicked(BrushType.MARKER) }
        btnPencil.setOnClickListener { listener?.onBrushClicked(BrushType.PENCIL) }
        btnColor.setOnClickListener { listener?.onColorClicked() }
        btnWidth.setOnClickListener { listener?.onWidthClicked() }
        btnEraser.setOnClickListener { listener?.onEraserClicked() }
        btnUndo.setOnClickListener { listener?.onUndoClicked() }
        btnRedo.setOnClickListener { listener?.onRedoClicked() }
        btnClear.setOnClickListener { listener?.onClearClicked() }
    }

    fun highlightBrush(brush: BrushType) {
        btnPen.isSelected = brush == BrushType.PEN
        btnBrush.isSelected = brush == BrushType.BRUSH
        btnMarker.isSelected = brush == BrushType.MARKER
        btnPencil.isSelected = brush == BrushType.PENCIL
        btnEraser.isSelected = false
    }

    fun highlightEraser(erasing: Boolean) {
        btnEraser.isSelected = erasing
        if (erasing) {
            btnPen.isSelected = false
            btnBrush.isSelected = false
            btnMarker.isSelected = false
            btnPencil.isSelected = false
        }
    }

    fun setColor(hex: String) {
        val c = runCatching { android.graphics.Color.parseColor(hex) }
            .getOrDefault(android.graphics.Color.BLACK)
        btnColor.imageTintList = android.content.res.ColorStateList.valueOf(c)
    }

    fun setUndoEnabled(enabled: Boolean) {
        btnUndo.isEnabled = enabled
        btnUndo.alpha = if (enabled) 1f else 0.4f
    }

    fun setRedoEnabled(enabled: Boolean) {
        btnRedo.isEnabled = enabled
        btnRedo.alpha = if (enabled) 1f else 0.4f
    }
}
