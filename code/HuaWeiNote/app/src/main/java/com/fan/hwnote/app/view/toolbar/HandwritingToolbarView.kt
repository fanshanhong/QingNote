package com.fan.hwnote.app.view.toolbar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import com.fan.hwnote.app.R

class HandwritingToolbarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onDoneClicked()
        fun onUndoClicked()
        fun onRedoClicked()
        fun onClearClicked()
        fun onEraserClicked()
        fun onStyleClicked()
    }

    var listener: Listener? = null

    private val btnDone: ImageView by lazy { findViewById(R.id.btn_hw_done) }
    private val btnUndo: ImageView by lazy { findViewById(R.id.btn_hw_undo) }
    private val btnRedo: ImageView by lazy { findViewById(R.id.btn_hw_redo) }
    private val btnClear: ImageView by lazy { findViewById(R.id.btn_hw_clear) }
    private val btnEraser: ImageView by lazy { findViewById(R.id.btn_hw_eraser) }
    private val btnStyle: ImageView by lazy { findViewById(R.id.btn_hw_style) }

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.toolbar_handwriting, this, true)
        btnDone.setOnClickListener { listener?.onDoneClicked() }
        btnUndo.setOnClickListener { listener?.onUndoClicked() }
        btnRedo.setOnClickListener { listener?.onRedoClicked() }
        btnClear.setOnClickListener { listener?.onClearClicked() }
        btnEraser.setOnClickListener { listener?.onEraserClicked() }
        btnStyle.setOnClickListener { listener?.onStyleClicked() }
    }

    fun highlightEraser(erasing: Boolean) {
        btnEraser.isSelected = erasing
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
