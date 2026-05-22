package com.fan.hwnote.app.controller.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.entity.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var titleInput: EditText
    private lateinit var blocksContainer: LinearLayout
    private lateinit var presenter: EditorPresenter

    private var noteId: Long = -1L
    private var loadedNote: Note? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        toolbar = findViewById(R.id.editor_toolbar)
        titleInput = findViewById(R.id.title_input)
        blocksContainer = findViewById(R.id.blocks_container)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        presenter = EditorPresenter(this, blocksContainer)

        val toolbarView = findViewById<com.fan.hwnote.app.view.toolbar.TextToolbarView>(R.id.text_toolbar)
        toolbarView.listener = object : com.fan.hwnote.app.view.toolbar.TextToolbarView.Listener {
            override fun onInlineToggle(type: com.fan.hwnote.app.model.entity.SpanType) {
                val pending = presenter.toggleInline(type)
                toolbarView.setInlineSelected(type, pending)
            }
            override fun onSizePicked(value: String) {
                presenter.toggleSize(value)
                // 字号档位无需高亮（用户能直接看到字大小变化），可省略 selected 反馈
            }
            override fun onColorClicked() {
                showColorPickerDialog()
            }
            override fun onHeadingToggle(isH1: Boolean) { /* Task 10 */ }
            override fun onImageClicked() {
                android.widget.Toast.makeText(this@NoteEditorActivity,
                    R.string.toast_image_placeholder, android.widget.Toast.LENGTH_SHORT).show()
            }
            override fun onChecklistClicked() {
                android.widget.Toast.makeText(this@NoteEditorActivity,
                    R.string.toast_checklist_placeholder, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        loadNote()
    }

    override fun onPause() {
        super.onPause()
        // 退出（包括按返回 / Home / 横屏）都落库一次。
        saveNote()
    }

    private fun loadNote() {
        lifecycleScope.launch {
            val note = if (noteId == -1L) Note.new() else (NoteRepository.get(noteId) ?: Note.new())
            loadedNote = note
            titleInput.setText(note.title)
            presenter.bind(note)
        }
    }

    private fun showColorPickerDialog() {
        val labels = arrayOf(
            getString(R.string.color_black),
            getString(R.string.color_red),
            getString(R.string.color_yellow),
            getString(R.string.color_green),
            getString(R.string.color_blue),
        )
        val hexes = arrayOf("#212121", "#E53935", "#FB8C00", "#43A047", "#1E88E5")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_pick_color_title)
            .setItems(labels) { _, which ->
                val hex = hexes[which]
                val pending = presenter.pickColor(hex)
                val toolbarView = findViewById<com.fan.hwnote.app.view.toolbar.TextToolbarView>(R.id.text_toolbar)
                // 仅当 pendingColor 真的被设置时才高亮按钮；选区直接生效或取消 pending 时清除 tint
                if (pending != null) {
                    toolbarView.setColorIndicator(android.graphics.Color.parseColor(pending))
                } else {
                    toolbarView.clearColorIndicator()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun saveNote() {
        val loaded = loadedNote ?: return // 还没加载完成，不存
        val title = titleInput.text.toString()
        val toSave = presenter.collectCurrentNote(title).copy(id = loaded.id)
        // 全空且是新笔记则不存
        val isAllEmpty = title.isEmpty() && toSave.plainText.isEmpty()
        if (loaded.id == 0L && isAllEmpty) return
        lifecycleScope.launch(Dispatchers.IO) {
            val newId = NoteRepository.save(toSave)
            // 更新 noteId / loadedNote，避免下次 onPause 再 insert 一条
            if (loaded.id == 0L && newId > 0) {
                noteId = newId
                loadedNote = toSave.copy(id = newId)
            }
        }
    }

    companion object {
        private const val EXTRA_NOTE_ID = "noteId"
        fun newIntent(context: Context, noteId: Long): Intent =
            Intent(context, NoteEditorActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
            }
    }
}
