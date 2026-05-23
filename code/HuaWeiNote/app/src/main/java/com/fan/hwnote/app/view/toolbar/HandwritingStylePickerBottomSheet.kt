package com.fan.hwnote.app.view.toolbar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.view.handwriting.HandwritingOverlayView
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 手写样式选择 BottomSheet：4 笔种 + 8 色 + 3 档粗细。
 * 直接驱动 HandwritingOverlayView 的 currentBrush / currentColor / currentWidth；
 * 选笔种自动取消橡皮态；不主动 dismiss，让用户连续选；关闭时回调外部同步顶层橡皮按钮态。
 */
class HandwritingStylePickerBottomSheet(
    context: Context,
    private val overlay: HandwritingOverlayView,
) : BottomSheetDialog(context) {

    private lateinit var btnPen: ImageView
    private lateinit var btnBrush: ImageView
    private lateinit var btnMarker: ImageView
    private lateinit var btnPencil: ImageView

    private lateinit var btnWidthThin: TextView
    private lateinit var btnWidthMedium: TextView
    private lateinit var btnWidthThick: TextView

    private val brushButtons: List<Pair<ImageView, BrushType>> by lazy {
        listOf(
            btnPen to BrushType.PEN,
            btnBrush to BrushType.BRUSH,
            btnMarker to BrushType.MARKER,
            btnPencil to BrushType.PENCIL,
        )
    }

    private val colorButtons: MutableList<Pair<ImageView, String>> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_handwriting_style_picker)

        btnPen = findViewById(R.id.btn_hwstyle_pen)!!
        btnBrush = findViewById(R.id.btn_hwstyle_brush)!!
        btnMarker = findViewById(R.id.btn_hwstyle_marker)!!
        btnPencil = findViewById(R.id.btn_hwstyle_pencil)!!

        colorButtons.clear()
        colorButtons += findViewById<ImageView>(R.id.btn_hwstyle_color_black)!! to "#212121"
        colorButtons += findViewById<ImageView>(R.id.btn_hwstyle_color_red)!! to "#E53935"
        colorButtons += findViewById<ImageView>(R.id.btn_hwstyle_color_orange)!! to "#FB8C00"
        colorButtons += findViewById<ImageView>(R.id.btn_hwstyle_color_yellow)!! to "#FDD835"
        colorButtons += findViewById<ImageView>(R.id.btn_hwstyle_color_green)!! to "#43A047"
        colorButtons += findViewById<ImageView>(R.id.btn_hwstyle_color_teal)!! to "#00897B"
        colorButtons += findViewById<ImageView>(R.id.btn_hwstyle_color_blue)!! to "#1E88E5"
        colorButtons += findViewById<ImageView>(R.id.btn_hwstyle_color_purple)!! to "#8E24AA"

        btnWidthThin = findViewById(R.id.btn_hwstyle_width_thin)!!
        btnWidthMedium = findViewById(R.id.btn_hwstyle_width_medium)!!
        btnWidthThick = findViewById(R.id.btn_hwstyle_width_thick)!!

        wireListeners()
        refreshSelected()
    }

    private fun wireListeners() {
        for ((view, type) in brushButtons) {
            view.setOnClickListener {
                overlay.isErasing = false
                overlay.currentBrush = type
                refreshSelected()
            }
        }
        for ((view, hex) in colorButtons) {
            view.setOnClickListener {
                overlay.currentColor = hex
                refreshSelected()
            }
        }
        btnWidthThin.setOnClickListener { overlay.currentWidth = 1; refreshSelected() }
        btnWidthMedium.setOnClickListener { overlay.currentWidth = 3; refreshSelected() }
        btnWidthThick.setOnClickListener { overlay.currentWidth = 6; refreshSelected() }
    }

    private fun refreshSelected() {
        for ((view, type) in brushButtons) view.isSelected = (overlay.currentBrush == type)
        for ((view, hex) in colorButtons) {
            view.isSelected = (overlay.currentColor.equals(hex, ignoreCase = true))
            val c = runCatching { Color.parseColor(hex) }.getOrDefault(Color.BLACK)
            view.imageTintList = ColorStateList.valueOf(c)
        }
        btnWidthThin.isSelected = overlay.currentWidth == 1
        btnWidthMedium.isSelected = overlay.currentWidth == 3
        btnWidthThick.isSelected = overlay.currentWidth == 6
    }
}
