package com.fan.hwnote.app.view.toolbar

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.controller.editor.EditorPresenter
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.SpanType
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 样式选择 BottomSheet：B/I/U/S 行内样式 + 字号 + 颜色 + H1/H2 块级样式。
 * 直接驱动 EditorPresenter 既有方法（toggleInline/toggleSize/pickColor/toggleHeading）；
 * 每次点击后从 presenter 拉一次状态，更新按钮 selected 态。BottomSheet 不主动关闭，
 * 用户可连续操作多个样式，关闭由用户外部点击或下拉触发。
 */
class StylePickerBottomSheet(
    context: Context,
    private val presenter: EditorPresenter,
) : BottomSheetDialog(context) {

    // 行 1: B/I/U/S
    private lateinit var btnBold: TextView
    private lateinit var btnItalic: TextView
    private lateinit var btnUnderline: TextView
    private lateinit var btnStrike: TextView

    // 行 2: A-/A/A+
    private lateinit var btnSizeSmall: TextView
    private lateinit var btnSizeNormal: TextView
    private lateinit var btnSizeLarge: TextView

    // 行 3: 5 色
    private lateinit var btnColorBlack: ImageView
    private lateinit var btnColorRed: ImageView
    private lateinit var btnColorYellow: ImageView
    private lateinit var btnColorGreen: ImageView
    private lateinit var btnColorBlue: ImageView

    // 行 4: H1/H2
    private lateinit var btnH1: TextView
    private lateinit var btnH2: TextView

    private val colorButtons: List<Pair<ImageView, String>> by lazy {
        listOf(
            btnColorBlack to "#212121",
            btnColorRed to "#E53935",
            btnColorYellow to "#FB8C00",
            btnColorGreen to "#43A047",
            btnColorBlue to "#1E88E5",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_style_picker)

        btnBold = findViewById(R.id.btn_style_bold)!!
        btnItalic = findViewById(R.id.btn_style_italic)!!
        btnUnderline = findViewById(R.id.btn_style_underline)!!
        btnStrike = findViewById(R.id.btn_style_strike)!!
        btnSizeSmall = findViewById(R.id.btn_style_size_small)!!
        btnSizeNormal = findViewById(R.id.btn_style_size_normal)!!
        btnSizeLarge = findViewById(R.id.btn_style_size_large)!!
        btnColorBlack = findViewById(R.id.btn_style_color_black)!!
        btnColorRed = findViewById(R.id.btn_style_color_red)!!
        btnColorYellow = findViewById(R.id.btn_style_color_yellow)!!
        btnColorGreen = findViewById(R.id.btn_style_color_green)!!
        btnColorBlue = findViewById(R.id.btn_style_color_blue)!!
        btnH1 = findViewById(R.id.btn_style_h1)!!
        btnH2 = findViewById(R.id.btn_style_h2)!!

        wireListeners()
        refreshSelected()
    }

    private fun wireListeners() {
        btnBold.setOnClickListener { presenter.toggleInline(SpanType.BOLD); refreshSelected() }
        btnItalic.setOnClickListener { presenter.toggleInline(SpanType.ITALIC); refreshSelected() }
        btnUnderline.setOnClickListener { presenter.toggleInline(SpanType.UNDERLINE); refreshSelected() }
        btnStrike.setOnClickListener { presenter.toggleInline(SpanType.STRIKETHROUGH); refreshSelected() }

        btnSizeSmall.setOnClickListener { presenter.toggleSize("small"); refreshSelected() }
        btnSizeNormal.setOnClickListener { presenter.toggleSize("medium"); refreshSelected() }
        btnSizeLarge.setOnClickListener { presenter.toggleSize("large"); refreshSelected() }

        for ((view, hex) in colorButtons) {
            view.setOnClickListener { presenter.pickColor(hex); refreshSelected() }
        }

        btnH1.setOnClickListener { presenter.toggleHeading(Heading.H1); refreshSelected() }
        btnH2.setOnClickListener { presenter.toggleHeading(Heading.H2); refreshSelected() }
    }

    private fun refreshSelected() {
        val pendingInline = presenter.pendingInlineSet()
        btnBold.isSelected = SpanType.BOLD in pendingInline
        btnItalic.isSelected = SpanType.ITALIC in pendingInline
        btnUnderline.isSelected = SpanType.UNDERLINE in pendingInline
        btnStrike.isSelected = SpanType.STRIKETHROUGH in pendingInline

        val pendingSize = presenter.pendingSize()
        btnSizeSmall.isSelected = pendingSize == "small"
        btnSizeNormal.isSelected = pendingSize == "medium"
        btnSizeLarge.isSelected = pendingSize == "large"

        val pendingColor = presenter.pendingColor()
        for ((view, hex) in colorButtons) view.isSelected = pendingColor == hex
        // H1/H2 没有 pending 概念（块级），不做高亮
    }
}
