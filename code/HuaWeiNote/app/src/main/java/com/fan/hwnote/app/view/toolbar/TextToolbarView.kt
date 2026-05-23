package com.fan.hwnote.app.view.toolbar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import com.fan.hwnote.app.R

class TextToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var listener: Listener? = null

    private val btnChecklist by lazy { findViewById<ImageView>(R.id.btn_checklist) }
    private val btnStyle by lazy { findViewById<ImageView>(R.id.btn_style) }
    private val btnImage by lazy { findViewById<ImageView>(R.id.btn_image) }
    private val btnHandwriting by lazy { findViewById<ImageView>(R.id.btn_handwriting) }

    init {
        LayoutInflater.from(context).inflate(R.layout.toolbar_text, this, true)
        wireListeners()
    }

    private fun wireListeners() {
        btnChecklist.setOnClickListener { listener?.onChecklistClicked() }
        btnStyle.setOnClickListener { listener?.onStyleClicked() }
        btnImage.setOnClickListener { listener?.onImageClicked() }
        btnHandwriting.setOnClickListener { listener?.onHandwritingClicked() }
    }

    interface Listener {
        fun onChecklistClicked()
        fun onStyleClicked()
        fun onImageClicked()
        fun onHandwritingClicked()
    }
}
