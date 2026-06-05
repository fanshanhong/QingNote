package com.fan.hwnote.app.view.picker

import android.content.Context
import android.view.LayoutInflater
import android.widget.RadioGroup
import android.widget.TextView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.RepeatType
import com.google.android.material.bottomsheet.BottomSheetDialog

class RepeatPickerBottomSheet(
    context: Context,
    private val current: RepeatType,
    private val onRepeatSelected: (RepeatType) -> Unit,
) : BottomSheetDialog(context) {

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_repeat_picker, null)
        setContentView(view)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)

        val checkedId = when (current) {
            RepeatType.NONE -> R.id.rb_none
            RepeatType.DAILY -> R.id.rb_daily
            RepeatType.WEEKLY -> R.id.rb_weekly
            RepeatType.MONTHLY -> R.id.rb_monthly
            RepeatType.YEARLY -> R.id.rb_yearly
        }
        radioGroup.check(checkedId)

        radioGroup.setOnCheckedChangeListener { _, id ->
            val type = when (id) {
                R.id.rb_none -> RepeatType.NONE
                R.id.rb_daily -> RepeatType.DAILY
                R.id.rb_weekly -> RepeatType.WEEKLY
                R.id.rb_monthly -> RepeatType.MONTHLY
                R.id.rb_yearly -> RepeatType.YEARLY
                else -> RepeatType.NONE
            }
            onRepeatSelected(type)
            dismiss()
        }

        btnCancel.setOnClickListener { dismiss() }
    }
}
