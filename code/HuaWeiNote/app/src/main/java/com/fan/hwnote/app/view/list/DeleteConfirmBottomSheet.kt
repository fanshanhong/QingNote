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
        btnConfirm.text = confirmLabel
        if (confirmIsDanger) {
            btnConfirm.setTextColor(ContextCompat.getColor(context, R.color.error))
        }
        btnConfirm.setOnClickListener { dismiss(); onConfirm() }
        view.findViewById<TextView>(R.id.btn_cancel).setOnClickListener { dismiss() }
    }
}
