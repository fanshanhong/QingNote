package com.fan.hwnote.app.view.folder

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.FolderRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * 新建文件夹（editing == null）或重命名（editing == 文件夹 id）的 BottomSheet。
 * 名称为空时按钮 no-op；fired 防双击。
 */
class NewFolderBottomSheet(
    context: Context,
    private val editing: Long? = null,
    private val initialName: String? = null,
    private val onSaved: (Long) -> Unit,
) : BottomSheetDialog(context) {

    private var fired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sheet_new_folder)
        val title = findViewById<TextView>(R.id.title_text)!!
        val edit = findViewById<EditText>(R.id.edit_name)!!
        val btnConfirm = findViewById<Button>(R.id.btn_confirm)!!
        val btnCancel = findViewById<Button>(R.id.btn_cancel)!!

        if (editing != null) title.setText(R.string.folder_rename)
        initialName?.let { edit.setText(it); edit.setSelection(it.length) }

        btnCancel.setOnClickListener { dismiss() }
        btnConfirm.setOnClickListener {
            if (fired) return@setOnClickListener
            val name = edit.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            fired = true
            val scope = (context as? LifecycleOwner)?.lifecycleScope ?: MainScope()
            scope.launch {
                val id = if (editing != null) {
                    FolderRepository.rename(editing, name)
                    editing
                } else {
                    FolderRepository.insert(name)
                }
                onSaved(id)
                dismiss()
            }
        }
    }
}
