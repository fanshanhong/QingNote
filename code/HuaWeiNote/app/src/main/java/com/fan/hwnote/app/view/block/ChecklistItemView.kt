package com.fan.hwnote.app.view.block

import android.content.Context
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.ChecklistItem

class ChecklistItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    val checkbox: CheckBox
    val edit: EditText

    interface Listener {
        /** 末尾按回车要求新增下一项。 */
        fun onEnterAtEnd(view: ChecklistItemView)
        /** 空项退格要求删本项 + 焦点上移。 */
        fun onBackspaceWhenEmpty(view: ChecklistItemView)
        /** EditText 获得焦点。 */
        fun onItemFocusGained(view: ChecklistItemView)
    }

    var listener: Listener? = null

    init {
        orientation = HORIZONTAL
        setPadding(0, 0, 0, 0)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        background = ContextCompat.getDrawable(context, R.drawable.bg_checklist_item)
        LayoutInflater.from(context).inflate(R.layout.block_checklist_item, this, true)
        checkbox = findViewById(R.id.item_checkbox)
        edit = findViewById(R.id.item_edit)

        checkbox.setOnCheckedChangeListener { _, isChecked -> applyCheckedStyle(isChecked) }

        edit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) listener?.onItemFocusGained(this)
        }

        edit.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    val sel = edit.selectionStart
                    val len = edit.text?.length ?: 0
                    if (sel == len) {
                        listener?.onEnterAtEnd(this)
                        true
                    } else false
                }
                KeyEvent.KEYCODE_DEL -> {
                    if ((edit.text?.length ?: 0) == 0) {
                        listener?.onBackspaceWhenEmpty(this)
                        true
                    } else false
                }
                else -> false
            }
        }

        // 防 paste / 程序 setText 后 strike 样式失效
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { /* no-op，样式只跟 checked 走 */ }
        })
    }

    fun bind(item: ChecklistItem) {
        // 先卸 listener 避免回调里把 isChecked 改了又触发上层修改
        checkbox.setOnCheckedChangeListener(null)
        checkbox.isChecked = item.checked
        applyCheckedStyle(item.checked)
        checkbox.setOnCheckedChangeListener { _, isChecked -> applyCheckedStyle(isChecked) }
        edit.setText(item.text)
    }

    fun toItem(): ChecklistItem = ChecklistItem(
        checked = checkbox.isChecked,
        text = edit.text?.toString().orEmpty(),
    )

    fun focusEditEnd() {
        edit.requestFocus()
        edit.setSelection(edit.text?.length ?: 0)
    }

    private fun applyCheckedStyle(checked: Boolean) {
        val flags = edit.paintFlags
        edit.paintFlags = if (checked) flags or Paint.STRIKE_THRU_TEXT_FLAG
                          else flags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        val colorRes = if (checked) R.color.text_checked else R.color.text_primary
        edit.setTextColor(ContextCompat.getColor(context, colorRes))
    }
}
