package com.fan.hwnote.app.view.list

import android.content.Context
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.fan.hwnote.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 通用底部二次确认。confirmIsDanger=true 时，主按钮文案染 @color/error。
 */
class DeleteConfirmBottomSheet(
    context: Context,
    private val title: String,
    private val message: String,
    private val confirmLabel: String,
    private val confirmIsDanger: Boolean = true,
    private val onConfirm: () -> Unit,
) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val view = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        setContentView(view)
        view.findViewById<TextView>(R.id.confirm_title).text = title
        view.findViewById<TextView>(R.id.confirm_message).text = message
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)
        btnConfirm.text = confirmLabel
        if (confirmIsDanger) {
            btnConfirm.setTextColor(ContextCompat.getColor(context, R.color.error))
        } else {
            btnConfirm.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }
        // 双击守卫：消失动画期间按钮仍可点 → 防止 onConfirm/dismiss 双触发
        var fired = false
        btnConfirm.setOnClickListener {
            if (fired) return@setOnClickListener
            fired = true
            dismiss()
            onConfirm()
        }
        btnCancel.setOnClickListener {
            if (fired) return@setOnClickListener
            fired = true
            dismiss()
        }
    }
}
