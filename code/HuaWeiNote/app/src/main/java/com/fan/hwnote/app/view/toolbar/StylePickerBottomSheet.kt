package com.fan.hwnote.app.view.toolbar

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.controller.editor.EditorPresenter
import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.ListType
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.view.block.TextBlockView
import com.google.android.material.bottomsheet.BottomSheetDialog

class StylePickerBottomSheet(
    context: Context,
    private val presenter: EditorPresenter,
    private val onBackgroundPicked: (String) -> Unit = {},
) : BottomSheetDialog(context) {

    // Row 1: B/I/U/S + alignment
    private lateinit var btnBold: TextView
    private lateinit var btnItalic: TextView
    private lateinit var btnUnderline: TextView
    private lateinit var btnStrike: TextView
    private lateinit var btnAlignLeft: ImageView
    private lateinit var btnAlignCenter: ImageView
    private lateinit var btnAlignRight: ImageView

    // Row 2: indent + lists
    private lateinit var btnIndentInc: ImageView
    private lateinit var btnIndentDec: ImageView
    private lateinit var btnListNumbered: ImageView
    private lateinit var btnListLettered: ImageView
    private lateinit var btnListBullet: ImageView
    private lateinit var btnListHollow: ImageView

    // Row 3: font size slider
    private lateinit var seekFontSize: SeekBar

    // Row 4: 7 colors
    private lateinit var colorViews: Array<ImageView>
    private val colorHexes = arrayOf("#E53935", "#FB8C00", "#43A047", "#29B6F6", "#1E88E5", "#AB47BC", "#212121")

    // Row 5: H1-H6
    private lateinit var headingViews: Array<TextView>
    private val headingValues = Heading.values()

    // Row 6: background textures
    private lateinit var bgViews: Array<ImageView>
    private val bgNames = arrayOf("plain", "linen", "kraft", "grid")

    private var currentBackground: String = "plain"

    private val sizeNames = arrayOf("xs", "small", "medium", "large", "xl")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_style_picker)

        // Title bar
        findViewById<android.view.View>(R.id.btn_close)!!.setOnClickListener { dismiss() }

        // Row 1
        btnBold = findViewById(R.id.btn_bold)!!
        btnItalic = findViewById(R.id.btn_italic)!!
        btnUnderline = findViewById(R.id.btn_underline)!!
        btnStrike = findViewById(R.id.btn_strike)!!
        btnAlignLeft = findViewById(R.id.btn_align_left)!!
        btnAlignCenter = findViewById(R.id.btn_align_center)!!
        btnAlignRight = findViewById(R.id.btn_align_right)!!

        // Row 2
        btnIndentInc = findViewById(R.id.btn_indent_inc)!!
        btnIndentDec = findViewById(R.id.btn_indent_dec)!!
        btnListNumbered = findViewById(R.id.btn_list_numbered)!!
        btnListLettered = findViewById(R.id.btn_list_lettered)!!
        btnListBullet = findViewById(R.id.btn_list_bullet)!!
        btnListHollow = findViewById(R.id.btn_list_hollow)!!

        // Row 3
        seekFontSize = findViewById(R.id.seek_font_size)!!

        // Row 4
        colorViews = arrayOf(
            findViewById(R.id.color_0)!!,
            findViewById(R.id.color_1)!!,
            findViewById(R.id.color_2)!!,
            findViewById(R.id.color_3)!!,
            findViewById(R.id.color_4)!!,
            findViewById(R.id.color_5)!!,
            findViewById(R.id.color_6)!!,
        )

        // Row 5
        headingViews = arrayOf(
            findViewById(R.id.btn_h1)!!,
            findViewById(R.id.btn_h2)!!,
            findViewById(R.id.btn_h3)!!,
            findViewById(R.id.btn_h4)!!,
            findViewById(R.id.btn_h5)!!,
            findViewById(R.id.btn_h6)!!,
        )

        // Row 6
        bgViews = arrayOf(
            findViewById(R.id.bg_plain)!!,
            findViewById(R.id.bg_linen)!!,
            findViewById(R.id.bg_kraft)!!,
            findViewById(R.id.bg_grid)!!,
        )

        wireListeners()
        refreshSelected()
    }

    private fun wireListeners() {
        // Row 1 - inline styles
        btnBold.setOnClickListener { presenter.toggleInline(SpanType.BOLD); refreshSelected() }
        btnItalic.setOnClickListener { presenter.toggleInline(SpanType.ITALIC); refreshSelected() }
        btnUnderline.setOnClickListener { presenter.toggleInline(SpanType.UNDERLINE); refreshSelected() }
        btnStrike.setOnClickListener { presenter.toggleInline(SpanType.STRIKETHROUGH); refreshSelected() }

        // Row 1 - alignment
        btnAlignLeft.setOnClickListener { presenter.toggleAlignment(Alignment.START); refreshSelected() }
        btnAlignCenter.setOnClickListener { presenter.toggleAlignment(Alignment.CENTER); refreshSelected() }
        btnAlignRight.setOnClickListener { presenter.toggleAlignment(Alignment.END); refreshSelected() }

        // Row 2 - indent + lists
        btnIndentInc.setOnClickListener { presenter.indent(); refreshSelected() }
        btnIndentDec.setOnClickListener { presenter.outdent(); refreshSelected() }
        btnListNumbered.setOnClickListener { presenter.toggleListType(ListType.NUMBERED); refreshSelected() }
        btnListLettered.setOnClickListener { presenter.toggleListType(ListType.LETTERED); refreshSelected() }
        btnListBullet.setOnClickListener { presenter.toggleListType(ListType.BULLET); refreshSelected() }
        btnListHollow.setOnClickListener { presenter.toggleListType(ListType.HOLLOW_BULLET); refreshSelected() }

        // Row 3 - font size slider
        seekFontSize.max = 4
        seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) presenter.toggleSize(sizeNames[progress])
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Row 4 - colors
        for (i in colorViews.indices) {
            colorViews[i].setOnClickListener {
                presenter.pickColor(colorHexes[i])
                refreshSelected()
            }
        }

        // Row 5 - headings
        for (i in headingViews.indices) {
            headingViews[i].setOnClickListener {
                presenter.toggleHeading(headingValues[i])
                refreshSelected()
            }
        }

        // Row 6 - background textures
        for (i in bgViews.indices) {
            bgViews[i].setOnClickListener {
                currentBackground = bgNames[i]
                onBackgroundPicked(bgNames[i])
                refreshSelected()
            }
        }
    }

    private fun refreshSelected() {
        // Row 1 - inline
        val pending = presenter.pendingInlineSet()
        btnBold.isSelected = SpanType.BOLD in pending
        btnItalic.isSelected = SpanType.ITALIC in pending
        btnUnderline.isSelected = SpanType.UNDERLINE in pending
        btnStrike.isSelected = SpanType.STRIKETHROUGH in pending

        // Row 1 - alignment
        val focused = presenter.currentFocusedTextBlock()
        val alignment = (focused as? TextBlockView)?.currentAlignment()
        btnAlignLeft.isSelected = alignment == Alignment.START
        btnAlignCenter.isSelected = alignment == Alignment.CENTER
        btnAlignRight.isSelected = alignment == Alignment.END

        // Row 2 - lists
        val listType = (focused as? TextBlockView)?.currentListType()
        btnListNumbered.isSelected = listType == ListType.NUMBERED
        btnListLettered.isSelected = listType == ListType.LETTERED
        btnListBullet.isSelected = listType == ListType.BULLET
        btnListHollow.isSelected = listType == ListType.HOLLOW_BULLET

        // Row 3 - font size slider
        val size = presenter.pendingSize()
        seekFontSize.progress = when (size) {
            "xs" -> 0; "small" -> 1; "large" -> 3; "xl" -> 4; else -> 2
        }

        // Row 4 - colors
        val pc = presenter.pendingColor()
        for (i in colorViews.indices) {
            colorViews[i].isSelected = pc != null && pc.equals(colorHexes[i], ignoreCase = true)
        }

        // Row 5 - headings
        val heading = presenter.pendingHeading()
        for (i in headingViews.indices) {
            headingViews[i].isSelected = heading == headingValues[i]
        }

        // Row 6 - backgrounds
        for (i in bgViews.indices) {
            bgViews[i].isSelected = currentBackground == bgNames[i]
        }
    }
}
