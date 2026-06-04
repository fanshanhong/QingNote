package com.fan.hwnote.app.view.folder

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Notebook
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * 新建笔记本（editing == null）或重命名+改色（editing != null）的 BottomSheet。
 * 8 色调色盘横排展示，选中态用 bg_color_dot_selectable 描边。
 */
class NewNotebookBottomSheet(
    context: Context,
    private val folderId: Long,
    private val editing: Notebook? = null,
    private val onSaved: (Long) -> Unit,
) : BottomSheetDialog(context) {

    private var fired = false
    private var pickedColor: String = editing?.color ?: PALETTE.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sheet_new_notebook)
        val title = findViewById<TextView>(R.id.title_text)!!
        val edit = findViewById<EditText>(R.id.edit_name)!!
        val row = findViewById<LinearLayout>(R.id.color_row)!!
        val btnConfirm = findViewById<Button>(R.id.btn_confirm)!!
        val btnCancel = findViewById<Button>(R.id.btn_cancel)!!

        if (editing != null) {
            title.setText(R.string.notebook_rename)
            edit.setText(editing.name)
            edit.setSelection(editing.name.length)
        }
        renderColors(row)

        btnCancel.setOnClickListener { dismiss() }
        btnConfirm.setOnClickListener {
            if (fired) return@setOnClickListener
            val name = edit.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            fired = true
            val scope = (context as? LifecycleOwner)?.lifecycleScope ?: MainScope()
            scope.launch {
                val id = if (editing != null) {
                    NotebookRepository.rename(editing.id, name)
                    NotebookRepository.updateColor(editing.id, pickedColor)
                    editing.id
                } else {
                    NotebookRepository.insert(folderId, name, pickedColor)
                }
                onSaved(id)
                dismiss()
            }
        }
    }

    private fun renderColors(row: LinearLayout) {
        row.removeAllViews()
        val size = context.resources.getDimensionPixelSize(R.dimen.notebook_color_dot_picker)
        val margin = (8 * context.resources.displayMetrics.density).toInt()
        PALETTE.forEach { hex ->
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                }
                isSelected = (hex == pickedColor)
                if (isSelected) {
                    foreground = ContextCompat.getDrawable(context, R.drawable.bg_color_dot_selectable)
                }
                setOnClickListener {
                    pickedColor = hex
                    renderColors(row)
                }
            }
            row.addView(dot)
        }
    }

    companion object {
        val PALETTE = listOf(
            "#9E9E9E", "#E53935", "#FB8C00", "#FBC02D",
            "#43A047", "#00ACC1", "#1E88E5", "#8E24AA",
        )
    }
}
