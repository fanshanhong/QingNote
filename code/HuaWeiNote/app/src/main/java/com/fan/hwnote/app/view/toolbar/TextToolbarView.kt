package com.fan.hwnote.app.view.toolbar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.SpanType

class TextToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var listener: Listener? = null

    private val btnBold by lazy { findViewById<android.widget.TextView>(R.id.btn_bold) }
    private val btnItalic by lazy { findViewById<android.widget.TextView>(R.id.btn_italic) }
    private val btnUnderline by lazy { findViewById<android.widget.TextView>(R.id.btn_underline) }
    private val btnStrike by lazy { findViewById<android.widget.TextView>(R.id.btn_strike) }
    private val btnSizeSmall by lazy { findViewById<android.widget.TextView>(R.id.btn_size_small) }
    private val btnSizeNormal by lazy { findViewById<android.widget.TextView>(R.id.btn_size_normal) }
    private val btnSizeLarge by lazy { findViewById<android.widget.TextView>(R.id.btn_size_large) }
    private val btnColor by lazy { findViewById<android.widget.ImageView>(R.id.btn_color) }
    private val btnH1 by lazy { findViewById<android.widget.TextView>(R.id.btn_h1) }
    private val btnH2 by lazy { findViewById<android.widget.TextView>(R.id.btn_h2) }
    private val btnImage by lazy { findViewById<android.widget.ImageView>(R.id.btn_image) }
    private val btnChecklist by lazy { findViewById<android.widget.ImageView>(R.id.btn_checklist) }

    init {
        LayoutInflater.from(context).inflate(R.layout.toolbar_text, this, true)
        wireListeners()
    }

    /** 设置按钮的 selected 高亮（pending 样式时显示）。 */
    fun setInlineSelected(type: SpanType, selected: Boolean) {
        val btn = when (type) {
            SpanType.BOLD -> btnBold
            SpanType.ITALIC -> btnItalic
            SpanType.UNDERLINE -> btnUnderline
            SpanType.STRIKETHROUGH -> btnStrike
            else -> return
        }
        btn.isSelected = selected
        btn.setBackgroundColor(
            if (selected) context.getColor(R.color.toolbar_btn_selected)
            else android.graphics.Color.TRANSPARENT
        )
    }

    private fun wireListeners() {
        btnBold.setOnClickListener { listener?.onInlineToggle(SpanType.BOLD) }
        btnItalic.setOnClickListener { listener?.onInlineToggle(SpanType.ITALIC) }
        btnUnderline.setOnClickListener { listener?.onInlineToggle(SpanType.UNDERLINE) }
        btnStrike.setOnClickListener { listener?.onInlineToggle(SpanType.STRIKETHROUGH) }

        // Task 9 接入字号 / 颜色
        btnSizeSmall.setOnClickListener { listener?.onSizePicked("small") }
        btnSizeNormal.setOnClickListener { listener?.onSizePicked("medium") }
        btnSizeLarge.setOnClickListener { listener?.onSizePicked("large") }
        btnColor.setOnClickListener { listener?.onColorClicked() }

        // Task 10 接入 H1/H2
        btnH1.setOnClickListener { listener?.onHeadingToggle(true) }
        btnH2.setOnClickListener { listener?.onHeadingToggle(false) }

        // M5/M6 占位
        btnImage.setOnClickListener { listener?.onImageClicked() }
        btnChecklist.setOnClickListener { listener?.onChecklistClicked() }
    }

    interface Listener {
        fun onInlineToggle(type: SpanType)
        fun onSizePicked(value: String)              // "small" / "medium" / "large"
        fun onColorClicked()
        fun onHeadingToggle(isH1: Boolean)            // true=H1, false=H2
        fun onImageClicked()
        fun onChecklistClicked()
    }
}
