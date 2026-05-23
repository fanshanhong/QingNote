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
import kotlinx.coroutines.withContext

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var titleInput: EditText
    private lateinit var blocksContainer: LinearLayout
    private lateinit var presenter: EditorPresenter

    private var noteId: Long = -1L
    private var loadedNote: Note? = null

    private val galleryLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uris = mutableListOf<android.net.Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
        } else {
            data.data?.let { uris += it }
        }
        if (uris.isNotEmpty()) compressAndInsertImages(uris)
    }

    private var pendingCameraOutputUri: android.net.Uri? = null
    private var pendingCameraOutputFile: java.io.File? = null

    private val cameraLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraOutputUri
        val file = pendingCameraOutputFile
        pendingCameraOutputUri = null
        pendingCameraOutputFile = null
        if (success && uri != null) {
            compressAndInsertImages(listOf(uri))
            // 等 compressAndInsertImages 完成后删 cache（它已经把内容复制走了；用 post 避免争用）
            blocksContainer.post { runCatching { file?.delete() } }
        } else {
            runCatching { file?.delete() }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else android.widget.Toast.makeText(this,
            R.string.camera_permission_denied, android.widget.Toast.LENGTH_SHORT).show()
    }

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
            override fun onHeadingToggle(isH1: Boolean) {
                val target = if (isH1) com.fan.hwnote.app.model.entity.Heading.H1
                             else com.fan.hwnote.app.model.entity.Heading.H2
                presenter.toggleHeading(target)
            }
            override fun onImageClicked() {
                ensureNoteSavedAndThen { showImageSourceDialog() }
            }
            override fun onChecklistClicked() {
                presenter.insertChecklistBlockAtFocus()
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
            presenter.noteId = note.id // 0L for 新笔记，正数 for 已落库
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

    private fun showImageSourceDialog() {
        val labels = arrayOf(
            getString(R.string.image_source_gallery),
            getString(R.string.image_source_camera),
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.image_source_title)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> launchGalleryPicker()
                    1 -> launchCameraWithPermission()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun launchGalleryPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        galleryLauncher.launch(Intent.createChooser(intent, getString(R.string.image_source_gallery)))
    }

    private fun compressAndInsertImages(uris: List<android.net.Uri>) {
        val curNoteId = loadedNote?.id ?: return
        if (curNoteId <= 0L) return
        val storage = com.fan.hwnote.app.model.storage.NoteFileStorage(this)
        lifecycleScope.launch {
            val results = mutableListOf<com.fan.hwnote.app.model.entity.Block.ImageBlock>()
            withContext(Dispatchers.IO) {
                for (u in uris) {
                    val fileName = "${java.util.UUID.randomUUID()}.jpg"
                    val target = storage.imageFile(curNoteId, fileName)
                    val r = com.fan.hwnote.app.util.ImageCompressor
                        .compressToFile(this@NoteEditorActivity, u, target) ?: continue
                    results += com.fan.hwnote.app.model.entity.Block.ImageBlock(
                        id = "i-${java.util.UUID.randomUUID().toString().take(8)}",
                        fileName = fileName,
                        width = r.width,
                        height = r.height,
                    )
                }
            }
            if (results.isEmpty()) {
                android.widget.Toast.makeText(this@NoteEditorActivity,
                    R.string.image_save_failed, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                presenter.insertImageBlocksAtFocus(results)
            }
        }
    }

    private fun launchCameraWithPermission() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera()
        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val curNoteId = loadedNote?.id ?: return
        if (curNoteId <= 0L) return
        val cameraDir = java.io.File(cacheDir, "camera").apply { mkdirs() }
        val temp = java.io.File(cameraDir, "${java.util.UUID.randomUUID()}.jpg")
        val uri = try {
            androidx.core.content.FileProvider.getUriForFile(
                this, "com.fan.hwnote.app.fileprovider", temp,
            )
        } catch (e: IllegalArgumentException) {
            android.widget.Toast.makeText(this,
                R.string.image_save_failed, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraOutputFile = temp
        pendingCameraOutputUri = uri
        runCatching { cameraLauncher.launch(uri) }
            .onFailure {
                pendingCameraOutputFile = null
                pendingCameraOutputUri = null
                runCatching { temp.delete() }
                android.widget.Toast.makeText(this,
                    R.string.camera_unavailable, android.widget.Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * 图片插入前置：若笔记尚未落库（noteId<=0），先保存一次拿到 id；落库成功后再执行回调。
     * 已落库（noteId>0）直接执行。在主线程上回调。
     */
    private fun ensureNoteSavedAndThen(block: () -> Unit) {
        val loaded = loadedNote
        if (loaded != null && loaded.id > 0L) {
            block(); return
        }
        // 这里复用 saveNote 路径，但要等 IO 完成后再回主线程跑 block
        val title = titleInput.text.toString()
        val toSave = presenter.collectCurrentNote(title)
        lifecycleScope.launch(Dispatchers.IO) {
            val newId = NoteRepository.save(toSave)
            withContext(Dispatchers.Main) {
                if (newId > 0L) {
                    noteId = newId
                    loadedNote = toSave.copy(id = newId)
                    presenter.noteId = newId
                    block()
                } else {
                    android.widget.Toast.makeText(this@NoteEditorActivity,
                        R.string.image_save_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                presenter.noteId = newId
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
