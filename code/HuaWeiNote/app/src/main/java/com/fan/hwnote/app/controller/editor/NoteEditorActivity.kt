package com.fan.hwnote.app.controller.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.view.folder.NotebookPickerPopupWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var titleInput: EditText
    private lateinit var blocksContainer: LinearLayout
    private lateinit var presenter: EditorPresenter
    private lateinit var handwritingOverlay: com.fan.hwnote.app.view.handwriting.HandwritingOverlayView
    private lateinit var handwritingToolbar: com.fan.hwnote.app.view.toolbar.HandwritingToolbarView
    private lateinit var textToolbar: com.fan.hwnote.app.view.toolbar.TextToolbarView

    private lateinit var btnBack: android.widget.ImageView
    private lateinit var btnUndo: android.widget.ImageView
    private lateinit var btnRedo: android.widget.ImageView
    private lateinit var btnDone: android.widget.ImageView
    private lateinit var editActions: LinearLayout
    private lateinit var browseActionBar: android.view.View
    private lateinit var browseFavoriteIcon: android.widget.ImageView
    private lateinit var browseFavoriteLabel: android.widget.TextView

    private var isEditing: Boolean = false

    // M9 T10：标题下方 metadata strip（时间 · 分类）
    private lateinit var metaTime: android.widget.TextView
    private lateinit var metaCategoryDot: android.widget.ImageView
    private lateinit var metaCategoryName: android.widget.TextView
    private lateinit var metaCategoryChip: android.view.View

    private lateinit var notebookIndicator: android.view.View
    private lateinit var indicatorDot: android.view.View
    private lateinit var indicatorText: android.widget.TextView

    private var noteId: Long = -1L
    private var loadedNote: Note? = null
    private var pendingNotebookId: Long? = null
    private var pendingBackground: String? = null
    @Volatile private var saveInFlight: Boolean = false

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
            compressAndInsertImages(listOf(uri), onDone = { runCatching { file?.delete() } })
        } else {
            runCatching { file?.delete() }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else showCameraPermissionDialog()
    }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startAudioRecording()
        else showRecordAudioPermissionDialog()
    }

    private var currentRecordingSheet: com.fan.hwnote.app.view.editor.AudioRecordingBottomSheet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        // 顶栏
        btnBack = findViewById(R.id.btn_back)
        btnBack.setOnClickListener { finish() }
        editActions = findViewById(R.id.edit_actions)
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        btnDone = findViewById(R.id.btn_done)

        titleInput = findViewById(R.id.title_input)
        blocksContainer = findViewById(R.id.blocks_container)

        val editorRoot = findViewById<android.view.View>(R.id.editor_root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(editorRoot) { v, insets ->
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, kotlin.math.max(ime.bottom, bars.bottom))
            insets
        }

        handwritingOverlay = findViewById(R.id.handwriting_overlay)
        presenter = EditorPresenter(this, blocksContainer, handwritingOverlay)
        presenter.history.listener = { _, _ ->
            if (!handwritingOverlay.isHandwritingMode) updateUndoRedoButtons()
        }
        btnUndo.setOnClickListener {
            if (handwritingOverlay.isHandwritingMode) {
                handwritingOverlay.undo()
            } else {
                presenter.undo()
            }
            updateUndoRedoButtons()
        }
        btnRedo.setOnClickListener {
            if (handwritingOverlay.isHandwritingMode) {
                handwritingOverlay.redo()
            } else {
                presenter.redo()
            }
            updateUndoRedoButtons()
        }
        btnDone.setOnClickListener {
            if (handwritingOverlay.isHandwritingMode) exitHandwritingMode()
            exitEditMode()
        }

        // 把"空白点击聚焦末尾文本块"挂在 FrameLayout 上而非 editor_content：
        // editor_content 高度是 wrap_content，只覆盖"标题+已输入文本"那一小块；
        // 而 FrameLayout 在 NestedScrollView(fillViewport=true) 下会被撑到一屏，
        // 下方空白才能拿到点击事件。Overlay 在非手写态 onTouchEvent return false 不消费，
        // EditText 自己消费则不冒泡，恰好只有"真空白处点击"会进这条回调。
        val editorScrollInner = findViewById<android.view.View>(R.id.editor_scroll_inner)
        editorScrollInner.setOnClickListener {
            if (handwritingOverlay.isHandwritingMode) return@setOnClickListener
            if (!isEditing) enterEditMode()
            else presenter.focusLastEditableBlock()
        }
        val editorContent = findViewById<android.view.View>(R.id.editor_content)
        editorContent.setOnClickListener {
            if (handwritingOverlay.isHandwritingMode) return@setOnClickListener
            if (!isEditing) enterEditMode()
            else presenter.focusLastEditableBlock()
        }

        textToolbar = findViewById(R.id.text_toolbar)
        handwritingToolbar = findViewById(R.id.handwriting_toolbar)
        browseActionBar = findViewById(R.id.browse_action_bar)
        browseFavoriteIcon = findViewById(R.id.browse_favorite_icon)
        browseFavoriteLabel = findViewById(R.id.browse_favorite_label)
        findViewById<android.view.View>(R.id.btn_browse_share).setOnClickListener {
            android.widget.Toast.makeText(this, R.string.toast_share_placeholder,
                android.widget.Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btn_browse_favorite).setOnClickListener {
            toggleFavorite()
        }
        findViewById<android.view.View>(R.id.btn_browse_delete).setOnClickListener {
            softDeleteAndFinish()
        }
        findViewById<android.view.View>(R.id.btn_browse_more).setOnClickListener { anchor ->
            showBrowseMoreMenu(anchor)
        }
        textToolbar.listener = object : com.fan.hwnote.app.view.toolbar.TextToolbarView.Listener {
            override fun onChecklistClicked() {
                presenter.toggleChecklistAtFocus()
            }
            override fun onStyleClicked() {
                com.fan.hwnote.app.view.toolbar.StylePickerBottomSheet(
                    this@NoteEditorActivity, presenter
                ) { bg ->
                    pendingBackground = bg
                    applyEditorBackground(bg)
                }.show()
            }
            override fun onImageClicked() {
                ensureNoteSavedAndThen { showImageSourceDialog() }
            }
            override fun onHandwritingClicked() {
                enterHandwritingMode()
            }
            override fun onRecordClicked() {
                ensureNoteSavedAndThen { launchRecordAudioWithPermission() }
            }
        }
        handwritingToolbar.listener = object : com.fan.hwnote.app.view.toolbar.HandwritingToolbarView.Listener {
            override fun onColorClicked() {
                showHandwritingColorPopup(handwritingToolbar.colorButton())
            }
            override fun onBrushSelected(type: com.fan.hwnote.app.model.entity.BrushType) {
                handwritingOverlay.isErasing = false
                handwritingOverlay.currentBrush = type
                handwritingToolbar.highlightBrush(type)
                handwritingToolbar.highlightEraser(false)
            }
            override fun onEraserClicked() {
                handwritingOverlay.isErasing = !handwritingOverlay.isErasing
                handwritingToolbar.highlightEraser(handwritingOverlay.isErasing)
            }
            override fun onPlusClicked() {
                android.widget.Toast.makeText(
                    this@NoteEditorActivity,
                    R.string.toast_hw_plus_placeholder,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        metaTime = findViewById(R.id.meta_time)
        metaCategoryDot = findViewById(R.id.meta_category_dot)
        metaCategoryName = findViewById(R.id.meta_category_name)
        metaCategoryChip = findViewById(R.id.meta_category_chip)
        metaCategoryChip.setOnClickListener { showCategoryPicker() }

        notebookIndicator = findViewById(R.id.notebook_indicator)
        indicatorDot = findViewById(R.id.indicator_dot)
        indicatorText = findViewById(R.id.indicator_text)
        notebookIndicator.setOnClickListener {
            val current = pendingNotebookId ?: loadedNote?.notebookId
            NotebookPickerPopupWindow(
                context = this,
                currentNotebookId = current,
                onPicked = { picked ->
                    pendingNotebookId = picked
                    lifecycleScope.launch { refreshIndicator(picked) }
                },
            ).show(notebookIndicator)
        }

        if (savedInstanceState != null) {
            pendingCameraOutputUri =
                @Suppress("DEPRECATION") savedInstanceState.getParcelable(STATE_CAMERA_URI)
            pendingCameraOutputFile = savedInstanceState.getString(STATE_CAMERA_FILE)?.let { java.io.File(it) }
        }
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        loadNote()
    }

    override fun onPause() {
        super.onPause()
        // 停掉编辑器内任何在播的音频
        presenter.stopAllPlayback()
        // 兜底取消正在录的 Sheet（用户切后台/锁屏/跳别的 Activity）
        currentRecordingSheet?.forceCancel()
        currentRecordingSheet = null
        // M11: 防抖文本先入栈，再走 save（保证最后一次连续输入也能进 history）
        presenter.flushPendingTextEdits()
        // 退出（包括按返回 / Home / 横屏）都落库一次。
        saveNote()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCameraOutputUri?.let { outState.putParcelable(STATE_CAMERA_URI, it) }
        pendingCameraOutputFile?.let { outState.putString(STATE_CAMERA_FILE, it.absolutePath) }
    }

    private fun loadNote() {
        lifecycleScope.launch {
            val note = if (noteId == -1L) Note.new() else (NoteRepository.get(noteId) ?: Note.new())
            loadedNote = note
            presenter.noteId = note.id // 0L for 新笔记，正数 for 已落库
            titleInput.setText(note.title)
            presenter.bind(note)
            applyEditorBackground(note.background)
            refreshMetadataStrip()
            refreshIndicator(note.notebookId)
            if (noteId == -1L) {
                enterEditMode()
            } else {
                presenter.setReadOnly(true)
                browseActionBar.visibility = android.view.View.VISIBLE
                titleInput.isFocusableInTouchMode = false
                titleInput.isFocusable = false
                titleInput.isClickable = false
                titleInput.isLongClickable = false
                refreshFavoriteButton()
            }
        }
    }

    private suspend fun refreshIndicator(notebookId: Long?) {
        val nb = if (notebookId != null) NotebookRepository.get(notebookId) else null
        if (nb != null) {
            indicatorText.text = nb.name
            val gd = indicatorDot.background as? android.graphics.drawable.GradientDrawable
                ?: android.graphics.drawable.GradientDrawable().also {
                    it.shape = android.graphics.drawable.GradientDrawable.OVAL
                    indicatorDot.background = it
                }
            gd.setColor(android.graphics.Color.parseColor(nb.color))
        } else {
            indicatorText.setText(R.string.indicator_no_notebook)
            val gd = indicatorDot.background as? android.graphics.drawable.GradientDrawable
                ?: android.graphics.drawable.GradientDrawable().also {
                    it.shape = android.graphics.drawable.GradientDrawable.OVAL
                    indicatorDot.background = it
                }
            gd.setColor(android.graphics.Color.parseColor("#CCCCCC"))
        }
    }

    private fun applyEditorBackground(background: String) {
        val editorRoot = findViewById<android.view.View>(R.id.editor_root)
        when (background) {
            "plain" -> editorRoot.setBackgroundResource(R.color.bg_card)
            "linen" -> editorRoot.setBackgroundResource(R.drawable.bg_linen_tile)
            "kraft" -> editorRoot.setBackgroundResource(R.drawable.bg_kraft_tile)
            "grid" -> editorRoot.setBackgroundResource(R.drawable.bg_grid_tile)
        }
    }

    /**
     * 刷新标题下方的「时间 · 分类」strip：
     * - 时间走 DateUtils.formatRelative（与列表卡片右上角同规则）
     * - 分类：未分类显示灰色圆点 + "未分类"；已分类显示分类色 + 分类名
     */
    private fun refreshMetadataStrip() {
        val n = loadedNote ?: return
        val ts = if (n.updatedAt > 0) n.updatedAt else System.currentTimeMillis()
        metaTime.text = com.fan.hwnote.app.util.DateUtils.formatRelative(ts)
        lifecycleScope.launch {
            val cat = n.categoryId?.let { com.fan.hwnote.app.model.CategoryRepository.get(it) }
            // 防竞态：若 loadedNote 在 suspend 期间已被新一次 refresh 替换，跳过 UI 写入
            // 避免 last-write-wins 导致旧分类名/圆点颜色覆盖最新状态
            if (loadedNote !== n) return@launch
            if (cat == null) {
                metaCategoryName.text = getString(R.string.filter_uncategorized)
                val hint = androidx.core.content.ContextCompat.getColor(
                    this@NoteEditorActivity, R.color.text_hint,
                )
                androidx.core.widget.ImageViewCompat.setImageTintList(
                    metaCategoryDot,
                    android.content.res.ColorStateList.valueOf(hint),
                )
            } else {
                metaCategoryName.text = cat.name
                val tint = runCatching { android.graphics.Color.parseColor(cat.color) }
                    .getOrDefault(android.graphics.Color.GRAY)
                androidx.core.widget.ImageViewCompat.setImageTintList(
                    metaCategoryDot,
                    android.content.res.ColorStateList.valueOf(tint),
                )
            }
        }
    }

    /**
     * 弹「移动到」分类选择 BottomSheet。
     * - 已落库笔记（id > 0）：选完即时持久化 categoryId（onPause 时 saveNote 会再合并一次）
     * - 未落库笔记（id == 0）：只更新 loadedNote，不内联 INSERT，避免与后续 onPause 重复 INSERT；
     *   等用户离开页面 / 触发 ensureNoteSavedAndThen 时统一走 saveNote 路径（该路径会从
     *   loadedNote 合并 categoryId）。
     */
    private fun showCategoryPicker() {
        val cur = loadedNote ?: return
        com.fan.hwnote.app.view.editor.CategoryPickerBottomSheet(
            activity = this,
            currentCategoryId = cur.categoryId,
            onPick = { newCatId ->
                val updated = cur.copy(categoryId = newCatId)
                loadedNote = updated
                refreshMetadataStrip()
                if (cur.id > 0L) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        NoteRepository.save(updated)
                    }
                }
            },
        ).show()
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

    private fun compressAndInsertImages(uris: List<android.net.Uri>, onDone: (() -> Unit)? = null) {
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
                onDone?.invoke()
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

    private fun showCameraPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.camera_permission_dialog_title)
            .setMessage(R.string.camera_permission_dialog_message)
            .setPositiveButton(R.string.action_open_settings) { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
                runCatching { startActivity(intent) }
                    .onFailure {
                        android.widget.Toast.makeText(this,
                            R.string.camera_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
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

    private fun launchRecordAudioWithPermission() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) startAudioRecording()
        else recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    private fun showRecordAudioPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.record_audio_permission_dialog_title)
            .setMessage(R.string.record_audio_permission_dialog_message)
            .setPositiveButton(R.string.action_open_settings) { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
                runCatching { startActivity(intent) }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun startAudioRecording() {
        val curNoteId = loadedNote?.id ?: return
        if (curNoteId <= 0L) return
        val storage = com.fan.hwnote.app.model.storage.NoteFileStorage(this)
        val fileName = "${java.util.UUID.randomUUID()}.m4a"
        val target = storage.audioFile(curNoteId, fileName)
        val sheet = com.fan.hwnote.app.view.editor.AudioRecordingBottomSheet(
            context = this,
            targetFile = target,
            onComplete = { durationMs ->
                currentRecordingSheet = null
                val block = com.fan.hwnote.app.model.entity.Block.AudioBlock(
                    id = "a-${java.util.UUID.randomUUID().toString().take(8)}",
                    fileName = fileName,
                    durationMs = durationMs,
                )
                presenter.insertAudioBlockAtFocus(block)
            },
            onCancel = {
                currentRecordingSheet = null
            },
        )
        currentRecordingSheet = sheet
        sheet.show()
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
        if (saveInFlight) return
        saveInFlight = true
        // 这里复用 saveNote 路径，但要等 IO 完成后再回主线程跑 block
        val title = titleInput.text.toString()
        // 同 saveNote：编辑期内通过 CategoryPickerBottomSheet 改的 categoryId 不在 presenter 快照里
        val toSave = presenter.collectCurrentNote(title)
            .copy(
                categoryId = loadedNote?.categoryId,
                notebookId = pendingNotebookId ?: loadedNote?.notebookId,
                background = pendingBackground ?: loadedNote?.background ?: "plain",
            )
        lifecycleScope.launch(Dispatchers.IO) {
            val newId = NoteRepository.save(toSave)
            withContext(Dispatchers.Main) {
                saveInFlight = false
                if (newId > 0L) {
                    noteId = newId
                    loadedNote = toSave.copy(id = newId)
                    presenter.noteId = newId
                    block()
                } else {
                    android.widget.Toast.makeText(this@NoteEditorActivity,
                        R.string.note_save_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveNote() {
        val loaded = loadedNote ?: return // 还没加载完成，不存
        val title = titleInput.text.toString()
        // 注意：presenter.collectCurrentNote 基于 bind 时的快照，不含编辑期内通过
        // CategoryPickerBottomSheet 改写过的 categoryId，必须从 loadedNote 重新合并。
        val toSave = presenter.collectCurrentNote(title)
            .copy(
                id = loaded.id,
                categoryId = loaded.categoryId,
                notebookId = pendingNotebookId ?: loaded.notebookId,
                background = pendingBackground ?: loaded.background,
            )
        // 全空且是新笔记则不存
        val isAllEmpty = title.isEmpty() && toSave.plainText.isEmpty()
        if (loaded.id == 0L && isAllEmpty) return
        if (loaded.id == 0L && saveInFlight) return  // ensureNoteSavedAndThen 正在跑同一条 INSERT
        lifecycleScope.launch(Dispatchers.IO) {
            val newId = NoteRepository.save(toSave)
            // M11: 仅保存成功路径（newId > 0）才异步清孤 + 清栈（spec §4.5 "onSaveSuccess 是唯一清栈入口"）
            if (newId > 0L) {
                NoteRepository.cleanOrphanFiles(newId, toSave.copy(id = newId))
            }
            withContext(Dispatchers.Main) {
                // 更新 noteId / loadedNote，避免下次 onPause 再 insert 一条
                if (loaded.id == 0L && newId > 0) {
                    noteId = newId
                    loadedNote = toSave.copy(id = newId)
                    presenter.noteId = newId
                } else if (newId <= 0L) {
                    android.widget.Toast.makeText(this@NoteEditorActivity,
                        R.string.note_save_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
                // M11: 跨保存清栈（仅成功）
                if (newId > 0L) presenter.history.clear()
            }
        }
    }

    private fun enterHandwritingMode() {
        handwritingOverlay.isHandwritingMode = true
        handwritingOverlay.isErasing = false
        handwritingOverlay.visibility = android.view.View.VISIBLE
        blocksContainer.alpha = 0.5f
        titleInput.alpha = 0.5f
        textToolbar.visibility = android.view.View.GONE
        handwritingToolbar.visibility = android.view.View.VISIBLE
        updateUndoRedoButtons()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(blocksContainer.windowToken, 0)
        for (view in presenter.allBlocks()) {
            if (view is com.fan.hwnote.app.view.block.TextBlockView) view.edit.isCursorVisible = false
        }
        handwritingToolbar.highlightBrush(handwritingOverlay.currentBrush)
        handwritingToolbar.setColorIndicator(handwritingOverlay.currentColor)
    }

    private fun exitHandwritingMode() {
        handwritingOverlay.isHandwritingMode = false
        blocksContainer.alpha = 1f
        titleInput.alpha = 1f
        textToolbar.visibility = if (isEditing) android.view.View.VISIBLE else android.view.View.GONE
        handwritingToolbar.visibility = android.view.View.GONE
        if (isEditing) {
            for (view in presenter.allBlocks()) {
                if (view is com.fan.hwnote.app.view.block.TextBlockView) view.edit.isCursorVisible = true
            }
        }
        updateUndoRedoButtons()
    }

    private fun showHandwritingColorPopup(anchor: android.view.View) {
        val colors = listOf(
            "#212121", "#E53935", "#FB8C00", "#FDD835",
            "#43A047", "#00897B", "#1E88E5", "#8E24AA",
        )
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }
        val dotSize = resources.getDimensionPixelSize(R.dimen.style_color_dot_size)
        val dotPadding = (8 * resources.displayMetrics.density).toInt()
        val popup = android.widget.PopupWindow(
            row,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        )
        for (hex in colors) {
            val dot = android.widget.ImageView(this).apply {
                setImageResource(R.drawable.ic_color_dot)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(hex)
                )
                layoutParams = android.widget.LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    setMargins(dotPadding, dotPadding, dotPadding, dotPadding)
                }
                isClickable = true
                isFocusable = true
                background = getDrawable(android.R.drawable.list_selector_background)
            }
            dot.setOnClickListener {
                handwritingOverlay.currentColor = hex
                handwritingToolbar.setColorIndicator(hex)
                popup.dismiss()
            }
            row.addView(dot)
        }
        row.setBackgroundColor(android.graphics.Color.WHITE)
        row.elevation = 8f * resources.displayMetrics.density
        popup.elevation = 8f * resources.displayMetrics.density
        popup.showAsDropDown(anchor, 0, -(anchor.height + row.measuredHeight))
    }

    private fun enterEditMode() {
        if (isEditing) return
        isEditing = true
        editActions.visibility = android.view.View.VISIBLE
        textToolbar.visibility = android.view.View.VISIBLE
        browseActionBar.visibility = android.view.View.GONE
        titleInput.isFocusableInTouchMode = true
        titleInput.isFocusable = true
        titleInput.isClickable = true
        titleInput.isLongClickable = true
        presenter.setReadOnly(false)
        updateUndoRedoButtons()
        presenter.focusLastEditableBlock()
    }

    private fun exitEditMode() {
        if (!isEditing) return
        isEditing = false
        editActions.visibility = android.view.View.GONE
        textToolbar.visibility = android.view.View.GONE
        browseActionBar.visibility = android.view.View.VISIBLE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(blocksContainer.windowToken, 0)
        titleInput.isFocusableInTouchMode = false
        titleInput.isFocusable = false
        titleInput.isClickable = false
        titleInput.isLongClickable = false
        titleInput.clearFocus()
        presenter.setReadOnly(true)
        presenter.flushPendingTextEdits()
        saveNote()
        refreshFavoriteButton()
    }

    private fun updateUndoRedoButtons() {
        val canUndo: Boolean
        val canRedo: Boolean
        if (handwritingOverlay.isHandwritingMode) {
            canUndo = handwritingOverlay.canUndo()
            canRedo = handwritingOverlay.canRedo()
        } else {
            canUndo = presenter.history.canUndo()
            canRedo = presenter.history.canRedo()
        }
        btnUndo.isEnabled = canUndo
        btnRedo.isEnabled = canRedo
        btnUndo.alpha = if (canUndo) 1.0f else 0.4f
        btnRedo.alpha = if (canRedo) 1.0f else 0.4f
    }

    private fun refreshFavoriteButton() {
        val fav = loadedNote?.isFavorite == true
        browseFavoriteIcon.setImageResource(
            if (fav) R.drawable.ic_star else R.drawable.ic_star_outline
        )
        browseFavoriteLabel.setText(
            if (fav) R.string.editor_unfavorite else R.string.editor_favorite
        )
    }

    private fun toggleFavorite() {
        val note = loadedNote ?: return
        val updated = note.copy(isFavorite = !note.isFavorite)
        loadedNote = updated
        refreshFavoriteButton()
        if (note.id > 0L) {
            lifecycleScope.launch(Dispatchers.IO) {
                NoteRepository.save(updated)
            }
        }
    }

    private fun softDeleteAndFinish() {
        val note = loadedNote ?: return
        if (note.id <= 0L) { finish(); return }
        com.fan.hwnote.app.view.list.DeleteConfirmBottomSheet(
            context = this,
            message = getString(R.string.dialog_soft_delete_message),
            confirmLabel = getString(R.string.action_delete),
            onConfirm = {
                lifecycleScope.launch(Dispatchers.IO) {
                    NoteRepository.softDelete(note.id)
                    withContext(Dispatchers.Main) { finish() }
                }
            },
        ).show()
    }

    private fun showBrowseMoreMenu(anchor: android.view.View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_editor_browse_more, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_move_notebook -> {
                    val current = pendingNotebookId ?: loadedNote?.notebookId
                    NotebookPickerPopupWindow(
                        context = this,
                        currentNotebookId = current,
                        onPicked = { picked ->
                            pendingNotebookId = picked
                            lifecycleScope.launch { refreshIndicator(picked) }
                        },
                    ).show(anchor)
                    true
                }
                R.id.action_set_category -> {
                    showCategoryPicker()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    companion object {
        private const val EXTRA_NOTE_ID = "noteId"
        private const val STATE_CAMERA_URI = "pendingCameraOutputUri"
        private const val STATE_CAMERA_FILE = "pendingCameraOutputFile"
        fun newIntent(context: Context, noteId: Long): Intent =
            Intent(context, NoteEditorActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
            }
    }
}
