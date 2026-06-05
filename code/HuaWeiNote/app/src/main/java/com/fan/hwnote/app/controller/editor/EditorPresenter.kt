package com.fan.hwnote.app.controller.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.LinearLayout
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.entity.NoteContent
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import com.fan.hwnote.app.model.history.Command
import com.fan.hwnote.app.model.history.CompositeCommand
import com.fan.hwnote.app.model.history.EditHistoryManager
import com.fan.hwnote.app.model.history.commands.AddBlockCommand
import com.fan.hwnote.app.model.history.commands.ApplyHeadingCommand
import com.fan.hwnote.app.model.history.commands.ApplySpanCommand
import com.fan.hwnote.app.model.history.commands.BlockMutator
import com.fan.hwnote.app.model.history.commands.MoveBlockCommand
import com.fan.hwnote.app.model.history.commands.RemoveBlockCommand
import com.fan.hwnote.app.model.history.commands.ReplaceBlockCommand
import com.fan.hwnote.app.model.history.commands.ReplaceTextCommand
import com.fan.hwnote.app.model.history.commands.StyleMutator
import com.fan.hwnote.app.model.history.commands.TextMutator
import com.fan.hwnote.app.util.applyTo
import com.fan.hwnote.app.util.toTextSpans
import com.fan.hwnote.app.view.block.BlockView
import com.fan.hwnote.app.view.block.ChecklistBlockView
import com.fan.hwnote.app.view.block.ChecklistItemView
import com.fan.hwnote.app.view.block.ImageBlockView
import com.fan.hwnote.app.view.block.TextBlockView
import java.util.UUID

/**
 * 编辑器协调器 — 不是 MVP 的 Presenter，而是把 BlockView 列表与待生效样式状态独立出来的辅助类。
 *
 * 状态：
 *  - currentBlocks: container 内的 BlockView 顺序快照（与 ViewGroup 子 view 顺序一致）
 *  - currentNote: onCreate 拿到的 Note 快照（保留 id / createdAt / isFavorite）
 *  - focusedTextBlock: 最近一次获得焦点的 TextBlockView（Presenter 通过 callback 维护）
 */
class EditorPresenter(
    private val context: Context,
    private val container: LinearLayout,
    private val overlay: com.fan.hwnote.app.view.handwriting.HandwritingOverlayView,
) : BlockView.Callback, BlockMutator, StyleMutator, TextMutator {

    private val currentBlocks = mutableListOf<BlockView>()
    private lateinit var currentNote: Note
    private var focusedTextBlock: TextBlockView? = null

    /** 当前正在编辑的 noteId（>0 表示已落库）；ImageBlockView 用它定位本地文件目录。 */
    var noteId: Long = 0L

    var isReadOnly: Boolean = false
        private set

    /** 编辑器内共享 AudioPlayer（多块共用，新点播放会停旧的）。 */
    val audioPlayer = com.fan.hwnote.app.model.audio.AudioPlayer()

    /** M11 撤销 / 重做管理器。loadNote 完成 = 起点；saveNote 成功后 clear。 */
    val history = EditHistoryManager()

    /** 无选区时，下次输入应套用的 span 类型集合。同 type 第二次点表示取消。 */
    private val pendingInline = mutableSetOf<SpanType>()
    /** 待生效字号 / 颜色（互斥单值，null 表示未启用 pending）。 */
    private var pendingSize: String? = null
    private var pendingColor: String? = null

    fun pendingInlineSet(): Set<SpanType> = pendingInline
    fun pendingSize(): String? = pendingSize
    fun pendingColor(): String? = pendingColor

    /**
     * 行内 B/I/U/S 按钮入口。
     *  - 有选区：对选区翻转该类型 span（已有则全删，没有则加一条覆盖整段）
     *  - 无选区：翻转 pendingInline 中的标志位（UI 由调用方设置 selected 高亮）
     * 返回 true 表示 pending（高亮按钮），false 表示已对选区直接生效（不需要高亮）。
     */
    fun toggleInline(type: SpanType): Boolean {
        val v = focusedTextBlock ?: return false
        val edit = v.edit
        val start = edit.selectionStart
        val end = edit.selectionEnd
        if (start in 0 until end) {
            val blockId = v.toBlock().id
            val before = v.toBlock().spans
            applyInlineToRange(edit.text as Spannable, type, start, end, null)
            val after = v.toBlock().spans
            history.push(ApplySpanCommand(this, blockId, before, after, focusCursor = end, typeForLabel = type))
            return false
        }
        if (pendingInline.contains(type)) pendingInline.remove(type) else pendingInline.add(type)
        return pendingInline.contains(type)
    }

    /**
     * 对范围 [start, end) 翻转 type 类样式：已有同类型 span 全部删除；否则添加一条整段覆盖。
     * 字号 / 颜色由 [value] 提供（type=FONT_SIZE / COLOR 时必填）。
     */
    fun applyInlineToRange(
        sp: Spannable,
        type: SpanType,
        start: Int,
        end: Int,
        value: String?,
    ) {
        // 用 SpanConverter 收集已有富文本 span（与 toBlock 保持同一份解析逻辑，避免漂移）
        val raw: List<TextSpan> = sp.toTextSpans()
        // 清掉所有富文本 span（CharacterStyle 子类）；不要扫到 Selection / IME composing / SuggestionSpan
        for (s in sp.getSpans(0, sp.length, CharacterStyle::class.java)) sp.removeSpan(s)
        // 翻转：删除范围内同 type；如本来无任何同 type 覆盖该段，加一条整段
        val sameType = raw.filter { it.type == type && rangeOverlaps(it.start, it.end, start, end) }
        val kept = raw.filter { it !in sameType }
        val newList = if (sameType.isEmpty()) kept + TextSpan(start, end, type, value) else kept
        newList.applyTo(sp)
    }

    private fun rangeOverlaps(a1: Int, a2: Int, b1: Int, b2: Int): Boolean =
        a1 < b2 && b1 < a2

    /** TextWatcher 调：刚插入的范围 [start, start+count)，应用 pendingInline / pendingSize / pendingColor。 */
    fun applyPendingTo(sp: Spannable, start: Int, count: Int) {
        if (count <= 0) return
        val end = start + count
        val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        if (pendingInline.contains(SpanType.BOLD)) {
            sp.setSpan(StyleSpan(Typeface.BOLD), start, end, flag)
        }
        if (pendingInline.contains(SpanType.ITALIC)) {
            sp.setSpan(StyleSpan(Typeface.ITALIC), start, end, flag)
        }
        if (pendingInline.contains(SpanType.UNDERLINE)) {
            sp.setSpan(UnderlineSpan(), start, end, flag)
        }
        if (pendingInline.contains(SpanType.STRIKETHROUGH)) {
            sp.setSpan(StrikethroughSpan(), start, end, flag)
        }
        pendingSize?.let { v ->
            val r = when (v) { "small" -> 0.85f; "large" -> 1.25f; else -> 1.0f }
            sp.setSpan(RelativeSizeSpan(r), start, end, flag)
        }
        pendingColor?.let { hex ->
            val c = runCatching { Color.parseColor(hex) }.getOrNull() ?: return@let
            sp.setSpan(ForegroundColorSpan(c), start, end, flag)
        }
    }

    /** Task 9 入口：字号 pending（再点同档取消）。返回新 pending 值（null 表示已取消）。 */
    fun toggleSize(value: String): String? {
        val v = focusedTextBlock ?: return null
        val edit = v.edit
        val start = edit.selectionStart; val end = edit.selectionEnd
        if (start in 0 until end) {
            val blockId = v.toBlock().id
            val before = v.toBlock().spans
            applyInlineToRange(edit.text as Spannable, SpanType.FONT_SIZE, start, end, value)
            val after = v.toBlock().spans
            history.push(ApplySpanCommand(this, blockId, before, after, focusCursor = end, typeForLabel = SpanType.FONT_SIZE))
            return null
        }
        pendingSize = if (pendingSize == value) null else value
        return pendingSize
    }

    /** Task 9 入口：颜色 pending 或选区直接生效。 */
    fun pickColor(hex: String): String? {
        val v = focusedTextBlock ?: return null
        val edit = v.edit
        val start = edit.selectionStart; val end = edit.selectionEnd
        if (start in 0 until end) {
            val blockId = v.toBlock().id
            val before = v.toBlock().spans
            applyInlineToRange(edit.text as Spannable, SpanType.COLOR, start, end, hex)
            val after = v.toBlock().spans
            history.push(ApplySpanCommand(this, blockId, before, after, focusCursor = end, typeForLabel = SpanType.COLOR))
            return null
        }
        pendingColor = if (pendingColor == hex) null else hex
        return pendingColor
    }

    fun bind(note: Note) {
        currentNote = note
        container.removeAllViews()
        currentBlocks.clear()
        focusedTextBlock = null

        val blocks = note.content.blocks.ifEmpty { listOf(emptyTextBlock()) }
        for (b in blocks) {
            when (b) {
                is Block.TextBlock -> addTextBlockView(b)
                is Block.ImageBlock -> addImageBlockView(b)
                is Block.ChecklistBlock -> addChecklistBlockView(b)
                is Block.AudioBlock -> addAudioBlockView(b)
            }
        }
        // 默认让第一个 TextBlock 拿到焦点（找不到就让第一块的可聚焦子 view 自己来）
        (currentBlocks.firstOrNull { it is TextBlockView } as? TextBlockView)?.focusEditEnd()
        overlay.setStrokes(note.content.handwriting)
    }

    fun currentFocusedTextBlock(): TextBlockView? = focusedTextBlock

    /** 空白点击入口：优先聚焦最近一次有焦点的 TextBlock；否则倒着找最后一个 TextBlockView 聚焦；都没有则 no-op。 */
    fun focusLastTextBlock() {
        val target = focusedTextBlock
            ?: (currentBlocks.lastOrNull { it is TextBlockView } as? TextBlockView)
            ?: return
        target.focusEditEnd()
        // requestFocus 不会自动拉起 IME（Manifest 无 stateVisible），在「无键盘 → 点空白」
        // 场景下显式 showSoftInput 兜底；SHOW_IMPLICIT 不强制覆盖系统状态。
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(target.edit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    fun setReadOnly(readOnly: Boolean) {
        isReadOnly = readOnly
        for (view in currentBlocks) {
            when (view) {
                is TextBlockView -> view.setEditable(!readOnly)
                is ImageBlockView -> view.setDeleteVisible(!readOnly)
                is ChecklistBlockView -> view.setEditable(!readOnly)
                is com.fan.hwnote.app.view.block.AudioBlockView -> view.setDeleteEnabled(!readOnly)
            }
        }
    }

    /**
     * 把 UI 当前内容收集成一份新 Note（保留原 id / createdAt / isFavorite，更新 title / content）。
     * 调用方负责 save 到 Repository。
     */
    fun collectCurrentNote(title: String): Note {
        val newBlocks = currentBlocks.map { it.toBlock() }
        val content = NoteContent(blocks = newBlocks, handwriting = overlay.getStrokes())
        return currentNote.copy(
            title = title,
            plainText = content.toPlainText(),
            content = content,
        )
    }

    // ----- BlockView.Callback -----

    override fun onRequestSplitAfter(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx < 0) return
        val newBlock = emptyTextBlock()
        addTextBlockView(newBlock, insertAt = idx + 1)
        (currentBlocks[idx + 1] as TextBlockView).focusEditEnd()
        history.push(AddBlockCommand(this, index = idx + 1, block = newBlock))
    }

    override fun onRequestDelete(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx <= 0) return // 第一块不可删
        // M11: 不再 purge 本地文件 — undo 需要文件还在，遗孤由 Task 10 saveNote 后
        // NoteRepository.cleanOrphanFiles 异步收口。
        val blockSnapshot = view.toBlock()
        container.removeView(view)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === view) focusedTextBlock = null
        (currentBlocks[idx - 1] as? TextBlockView)?.focusEditEnd()
        history.push(RemoveBlockCommand(this, blockSnapshot.id,
            presnapshot = blockSnapshot, presavedIndex = idx))
    }

    override fun onFocusGained(view: BlockView) {
        if (view is TextBlockView) focusedTextBlock = view
    }

    /** 图片加载失败的安全移除：第一块时退化为换成空 TextBlock，避免列表为空崩溃。M11 不再清磁盘 jpg，由 cleanOrphanFiles 收口。 */
    override fun onImageLoadFailed(view: BlockView) {
        if (view !is ImageBlockView) { onRequestDelete(view); return }
        val idx = currentBlocks.indexOf(view)
        if (idx < 0) return
        val snapshot = view.toBlock()
        container.removeView(view)
        currentBlocks.removeAt(idx)
        if (idx == 0 && currentBlocks.isEmpty()) {
            val tail = emptyTextBlock()
            addTextBlockView(tail)
            (currentBlocks[0] as? TextBlockView)?.focusEditEnd()
            val removeCmd = RemoveBlockCommand(this, snapshot.id,
                presnapshot = snapshot, presavedIndex = idx)
            val addTailCmd = AddBlockCommand(this, 0, tail)
            // 1 个 ↶ 同时撤回"删坏图 + 占位空块"，恢复回坏图状态（用户可继续选择删 / 替换）
            history.push(CompositeCommand("ImageLoadFailedReplaceWithEmpty",
                listOf(removeCmd, addTailCmd)))
        } else {
            history.push(RemoveBlockCommand(this, snapshot.id,
                presnapshot = snapshot, presavedIndex = idx))
        }
    }

    /** 清单整块替换为一个空 TextBlock（原位）；焦点交给它。供 ChecklistBlockView 在唯一项退格或末尾空项回车时调用。 */
    override fun onChecklistConvertBlockToText(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx < 0) return
        val oldSnapshot = view.toBlock()
        container.removeView(view)
        currentBlocks.removeAt(idx)
        val newBlock = emptyTextBlock()
        addTextBlockView(newBlock, insertAt = idx)
        (currentBlocks[idx] as TextBlockView).focusEditEnd()
        history.push(ReplaceBlockCommand(this, oldSnapshot.id, newBlock,
            preOldBlock = oldSnapshot))
    }

    /** 在清单块之后追加空 TextBlock，焦点交给它。供清单末尾空项回车时调用（清单还剩其他项的情况）。 */
    override fun onChecklistAppendTextAfter(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx < 0) return
        val newBlock = emptyTextBlock()
        addTextBlockView(newBlock, insertAt = idx + 1)
        (currentBlocks[idx + 1] as TextBlockView).focusEditEnd()
        history.push(AddBlockCommand(this, idx + 1, newBlock))
    }

    // ----- private -----

    private fun emptyTextBlock(): Block.TextBlock =
        Block.TextBlock(id = "b-${UUID.randomUUID().toString().take(8)}")

    private fun addTextBlockView(block: Block.TextBlock, insertAt: Int = -1) {
        val v = TextBlockView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            callback = this@EditorPresenter
            pendingApplier = { sp, start, count -> applyPendingTo(sp, start, count) }
            textDebounceCallback = { blockId, beforeText, beforeSpans, afterText, afterSpans ->
                recordTextEdit(blockId, beforeText, beforeSpans, afterText, afterSpans)
            }
            bind(block)
        }
        if (insertAt < 0 || insertAt >= currentBlocks.size) {
            container.addView(v)
            currentBlocks.add(v)
        } else {
            container.addView(v, insertAt)
            currentBlocks.add(insertAt, v)
        }
        if (isReadOnly) v.setEditable(false)
    }

    private fun addImageBlockView(block: Block.ImageBlock, insertAt: Int = -1) {
        val v = ImageBlockView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            callback = this@EditorPresenter
            noteId = this@EditorPresenter.noteId
            bind(block)
        }
        if (insertAt < 0 || insertAt >= currentBlocks.size) {
            container.addView(v); currentBlocks.add(v)
        } else {
            container.addView(v, insertAt); currentBlocks.add(insertAt, v)
        }
        if (isReadOnly) v.setDeleteVisible(false)
    }

    private fun addChecklistBlockView(block: Block.ChecklistBlock, insertAt: Int = -1) {
        val v = ChecklistBlockView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            callback = this@EditorPresenter
            bind(block)
        }
        if (insertAt < 0 || insertAt >= currentBlocks.size) {
            container.addView(v); currentBlocks.add(v)
        } else {
            container.addView(v, insertAt); currentBlocks.add(insertAt, v)
        }
        if (isReadOnly) v.setEditable(false)
    }

    private fun addAudioBlockView(block: Block.AudioBlock, insertAt: Int = -1) {
        val v = com.fan.hwnote.app.view.block.AudioBlockView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            callback = this@EditorPresenter
            noteId = this@EditorPresenter.noteId
            audioPlayer = this@EditorPresenter.audioPlayer
            bind(block)
        }
        if (insertAt < 0 || insertAt >= currentBlocks.size) {
            container.addView(v); currentBlocks.add(v)
        } else {
            container.addView(v, insertAt); currentBlocks.add(insertAt, v)
        }
        if (isReadOnly) v.setDeleteEnabled(false)
    }

    /** Task 10 H1/H2 用：对当前焦点 TextBlock 切 heading。 */
    fun toggleHeading(target: Heading) {
        val v = focusedTextBlock ?: return
        val blockId = v.toBlock().id
        val before = v.currentHeading()
        val after = if (before == target) null else target
        v.setHeading(after)
        history.push(ApplyHeadingCommand(this, blockId, before, after))
    }

    /**
     * Task 10 入口：把若干 ImageBlock 插到当前焦点 TextBlock 之后，并在最后追加一个空 TextBlock 接管焦点。
     * 焦点未知（如刚进图片块）→ 追加到列表末尾。
     */
    fun insertImageBlocksAtFocus(blocks: List<Block.ImageBlock>) {
        if (blocks.isEmpty()) return
        val anchor = focusedTextBlock
        val baseIdx = if (anchor != null) currentBlocks.indexOf(anchor) + 1
                      else currentBlocks.size
        var insertAt = baseIdx
        // 收集所有底层 Command 打包为 1 个 CompositeCommand，确保用户按 1 下 ↶ 整体撤销
        // （否则尾 TextBlock 会先被撤回，图片还在，违反 spec §5.3 真机走查 #4 期望）。
        val commands = mutableListOf<Command>()
        for (b in blocks) {
            addImageBlockView(b, insertAt = insertAt)
            commands.add(AddBlockCommand(this, insertAt, b))
            insertAt += 1
        }
        // 末尾补一个空 TextBlock，让用户可继续输入
        val tail = emptyTextBlock()
        addTextBlockView(tail, insertAt = insertAt)
        commands.add(AddBlockCommand(this, insertAt, tail))
        history.push(CompositeCommand("InsertImages(${blocks.size})", commands))
        (currentBlocks[insertAt] as TextBlockView).focusEditEnd()
    }

    /**
     * Task 8 入口：在焦点 TextBlock 之后插一个新清单块（含 1 个空项）；焦点交给该空项。
     */
    fun insertChecklistBlockAtFocus() {
        val anchor = focusedTextBlock
        val insertAt = if (anchor != null) currentBlocks.indexOf(anchor) + 1
                       else currentBlocks.size
        val block = Block.ChecklistBlock(
            id = "c-${UUID.randomUUID().toString().take(8)}",
            items = mutableListOf(com.fan.hwnote.app.model.entity.ChecklistItem(false, "")),
        )
        addChecklistBlockView(block, insertAt = insertAt)
        (currentBlocks[insertAt] as ChecklistBlockView).focusLastItemEnd()
        history.push(AddBlockCommand(this, insertAt, block))
    }

    /**
     * "清单"按钮入口（M8 反向 toggle）：
     * - 焦点在某清单项 → 取消该项：把该项文字搬出来作 TextBlock 插到清单块之后；删该项；
     *   清单空了则整块替换 TextBlock；焦点交新 TextBlock
     * - 焦点在 TextBlock → 原地把该 TextBlock 转 ChecklistBlock（首项 = 该块当前文字）
     * - 无焦点 → 走 [insertChecklistBlockAtFocus] 兜底（追加到末尾）
     */
    fun toggleChecklistAtFocus() {
        val focused = container.findFocus()
        var v: android.view.View? = focused
        var itemView: ChecklistItemView? = null
        var blockView: ChecklistBlockView? = null
        while (v != null) {
            if (itemView == null && v is ChecklistItemView) itemView = v
            if (v is ChecklistBlockView) { blockView = v; break }
            v = v.parent as? android.view.View
        }
        if (itemView != null && blockView != null) {
            convertChecklistItemToText(blockView, itemView)
            return
        }
        // 焦点在 TextBlock → 原地转 ChecklistBlock（heading/spans 丢失，仅保文字）
        val tb = focusedTextBlock
        if (tb != null) {
            convertTextBlockToChecklist(tb)
            return
        }
        // 兜底：无焦点 → 末尾追加新清单块
        insertChecklistBlockAtFocus()
    }

    /** 原地把 TextBlock 转 ChecklistBlock：取该块当前文字（toString，丢 heading/spans）作首项；焦点交首项。 */
    private fun convertTextBlockToChecklist(tb: TextBlockView) {
        val idx = currentBlocks.indexOf(tb)
        if (idx < 0) return
        val oldSnapshot = tb.toBlock()
        val text = tb.edit.text.toString()
        container.removeView(tb)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === tb) focusedTextBlock = null
        val newBlock = Block.ChecklistBlock(
            id = "c-${UUID.randomUUID().toString().take(8)}",
            items = mutableListOf(com.fan.hwnote.app.model.entity.ChecklistItem(false, text)),
        )
        addChecklistBlockView(newBlock, insertAt = idx)
        (currentBlocks[idx] as ChecklistBlockView).focusLastItemEnd()
        history.push(ReplaceBlockCommand(this, oldSnapshot.id, newBlock,
            preOldBlock = oldSnapshot))
    }

    /** 把某个清单项变成 TextBlock：取文字 → 在清单块后插 TextBlock → 从清单删该项；清单空了连块一起删（替换为 TextBlock 在原位）。 */
    private fun convertChecklistItemToText(
        block: ChecklistBlockView,
        item: ChecklistItemView,
    ) {
        val blockIdx = currentBlocks.indexOf(block)
        if (blockIdx < 0) return
        val oldChecklistSnapshot = block.toBlock()
        val text = item.edit.text.toString()
        val becameEmpty = block.removeItemAndReturnEmpty(item)
        val newTextBlock = Block.TextBlock(
            id = "b-${UUID.randomUUID().toString().take(8)}",
            text = text,
        )
        if (becameEmpty) {
            // 清单空了：原位替换为 TextBlock
            container.removeView(block)
            currentBlocks.removeAt(blockIdx)
            addTextBlockView(newTextBlock, insertAt = blockIdx)
            (currentBlocks[blockIdx] as TextBlockView).focusEditEnd()
            history.push(ReplaceBlockCommand(this, oldChecklistSnapshot.id, newTextBlock,
                preOldBlock = oldChecklistSnapshot))
        } else {
            // 清单还剩项：TextBlock 插在清单块后
            addTextBlockView(newTextBlock, insertAt = blockIdx + 1)
            (currentBlocks[blockIdx + 1] as TextBlockView).focusEditEnd()
            // 用 ReplaceBlockCommand 表达整个 ChecklistBlock 前后差异（细粒度"删 item"由整块快照对承载），
            // 再用 AddBlockCommand 表达新 TextBlock 的插入。两条共同回退即可。
            val newChecklistSnapshot = block.toBlock()
            val replaceCmd = ReplaceBlockCommand(this, oldChecklistSnapshot.id, newChecklistSnapshot,
                preOldBlock = oldChecklistSnapshot)
            val addCmd = AddBlockCommand(this, blockIdx + 1, newTextBlock)
            // 1 个 ↶ 同时复原清单项 + 撤回新 TextBlock，避免"按 1 下只撤回 TextBlock 留下半残清单"
            history.push(CompositeCommand("ChecklistItem→Text", listOf(replaceCmd, addCmd)))
        }
    }

    /**
     * M10 入口：把 1 个 AudioBlock 插到当前焦点 TextBlock 之后，并补尾 TextBlock 接管焦点。
     * 焦点未知 → 追加到列表末尾。
     */
    fun insertAudioBlockAtFocus(block: Block.AudioBlock) {
        val anchor = focusedTextBlock
        val baseIdx = if (anchor != null) currentBlocks.indexOf(anchor) + 1
                      else currentBlocks.size
        addAudioBlockView(block, insertAt = baseIdx)
        val audioCmd = AddBlockCommand(this, baseIdx, block)
        val tail = emptyTextBlock()
        addTextBlockView(tail, insertAt = baseIdx + 1)
        val tailCmd = AddBlockCommand(this, baseIdx + 1, tail)
        // 1 个 ↶ 撤回整次插入（语音块 + 尾 TextBlock），与 spec §5.3 走查 #5 期望一致
        history.push(CompositeCommand("InsertAudio", listOf(audioCmd, tailCmd)))
        (currentBlocks[baseIdx + 1] as TextBlockView).focusEditEnd()
    }

    // ----- M11 silent mutators (不入栈，apply/revert 共用入口) -----

    override fun silentInsertBlock(index: Int, block: Block) {
        val safe = index.coerceIn(0, currentBlocks.size)
        when (block) {
            is Block.TextBlock -> addTextBlockView(block, insertAt = safe)
            is Block.ImageBlock -> addImageBlockView(block, insertAt = safe)
            is Block.ChecklistBlock -> addChecklistBlockView(block, insertAt = safe)
            is Block.AudioBlock -> addAudioBlockView(block, insertAt = safe)
        }
    }

    override fun silentRemoveBlock(blockId: String) {
        val idx = currentBlocks.indexOfFirst { it.toBlock().id == blockId }
        if (idx < 0) return
        val view = currentBlocks[idx]
        container.removeView(view)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === view) focusedTextBlock = null
    }

    override fun silentMoveBlock(from: Int, to: Int) {
        if (from !in currentBlocks.indices || to !in currentBlocks.indices) return
        val view = currentBlocks.removeAt(from)
        container.removeView(view)
        currentBlocks.add(to, view)
        container.addView(view, to)
    }

    override fun silentReplaceBlock(blockId: String, newBlock: Block) {
        val idx = currentBlocks.indexOfFirst { it.toBlock().id == blockId }
        if (idx < 0) return
        val old = currentBlocks[idx]
        container.removeView(old)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === old) focusedTextBlock = null
        silentInsertBlock(idx, newBlock)
    }

    override fun silentRequestFocus(blockId: String, cursorIndex: Int) {
        val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
        view.focusEditEnd()
        val safeCursor = cursorIndex.coerceIn(0, view.edit.text.length)
        view.edit.setSelection(safeCursor)
    }

    override fun indexOfBlock(blockId: String): Int =
        currentBlocks.indexOfFirst { it.toBlock().id == blockId }

    override fun blockAt(index: Int): Block? =
        currentBlocks.getOrNull(index)?.toBlock()

    override fun snapshotBlock(blockId: String): Block? =
        currentBlocks.firstOrNull { it.toBlock().id == blockId }?.toBlock()

    // ----- StyleMutator -----

    override fun snapshotSpans(blockId: String): List<TextSpan>? =
        (snapshotBlock(blockId) as? Block.TextBlock)?.spans

    override fun setBlockSpans(blockId: String, newSpans: List<TextSpan>) {
        val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
        val sp = SpannableString(view.edit.text.toString())
        newSpans.applyTo(sp)
        view.edit.setText(sp)
    }

    override fun snapshotHeading(blockId: String): Heading? =
        (snapshotBlock(blockId) as? Block.TextBlock)?.heading

    override fun setBlockHeading(blockId: String, heading: Heading?) {
        val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
        view.setHeading(heading)
    }

    // ----- TextMutator -----

    override fun silentReplaceText(blockId: String, text: String, spans: List<TextSpan>) {
        val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
        val sp = SpannableString(text)
        spans.applyTo(sp)
        view.suppressDebounceWhile {
            view.edit.setText(sp)
        }
    }

    // ----- M11 公共入口 -----

    /** Activity 顶部 ↶ 按钮入口。 */
    fun undo() {
        flushPendingTextEdits()
        history.undo()
    }

    /** Activity 顶部 ↷ 按钮入口。 */
    fun redo() {
        flushPendingTextEdits()
        history.redo()
    }

    /** TextBlockView 防抖窗口结束时调，落 ReplaceTextCommand 入栈。 */
    fun recordTextEdit(
        blockId: String,
        beforeText: String,
        beforeSpans: List<TextSpan>,
        afterText: String,
        afterSpans: List<TextSpan>,
    ) {
        if (beforeText == afterText && beforeSpans == afterSpans) return
        history.push(ReplaceTextCommand(
            this, blockId, beforeText, beforeSpans, afterText, afterSpans,
        ))
    }

    /** 遍历当前所有 TextBlockView 强制 flush 防抖窗口未落栈的变更。 */
    fun flushPendingTextEdits() {
        for (v in currentBlocks) {
            if (v is TextBlockView) v.flushPendingTextEdit()
        }
    }

    /** Activity onPause 调：停掉编辑器内任何在播的音频。 */
    fun stopAllPlayback() {
        audioPlayer.stop()
    }
}
