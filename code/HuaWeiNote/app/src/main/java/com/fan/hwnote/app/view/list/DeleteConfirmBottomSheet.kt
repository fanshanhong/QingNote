package com.fan.hwnote.app.view.list

import android.content.Context
import android.widget.TextView
import com.fan.hwnote.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class DeleteConfirmBottomSheet(
    context: Context,
    private val message: String,
    private val confirmLabel: String,
    private val onConfirm: () -> Unit,
) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val view = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        setContentView(view)
        view.findViewById<TextView>(R.id.confirm_message).text = message
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)
        btnConfirm.text = confirmLabel
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
