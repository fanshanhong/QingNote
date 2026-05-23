package com.fan.hwnote.app.controller.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
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
import com.fan.hwnote.app.util.applyTo
import com.fan.hwnote.app.util.toTextSpans
import com.fan.hwnote.app.view.block.BlockView
import com.fan.hwnote.app.view.block.ChecklistBlockView
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
) : BlockView.Callback {

    private val currentBlocks = mutableListOf<BlockView>()
    private lateinit var currentNote: Note
    private var focusedTextBlock: TextBlockView? = null

    /** 当前正在编辑的 noteId（>0 表示已落库）；ImageBlockView 用它定位本地文件目录。 */
    var noteId: Long = 0L

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
            applyInlineToRange(edit.text as Spannable, type, start, end, null)
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
            applyInlineToRange(edit.text as Spannable, SpanType.FONT_SIZE, start, end, value)
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
            applyInlineToRange(edit.text as Spannable, SpanType.COLOR, start, end, hex)
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
            }
        }
        // 默认让第一个 TextBlock 拿到焦点（找不到就让第一块的可聚焦子 view 自己来）
        (currentBlocks.firstOrNull { it is TextBlockView } as? TextBlockView)?.focusEditEnd()
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

    /**
     * 把 UI 当前内容收集成一份新 Note（保留原 id / createdAt / isFavorite，更新 title / content）。
     * 调用方负责 save 到 Repository。
     */
    fun collectCurrentNote(title: String): Note {
        val newBlocks = currentBlocks.map { it.toBlock() }
        val content = NoteContent(blocks = newBlocks, handwriting = emptyList())
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
    }

    override fun onRequestDelete(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx <= 0) return // 第一块不可删
        // 图片块顺手清掉本地 jpg；ChecklistBlock 没有文件需要清
        if (view is ImageBlockView) {
            val block = view.toBlock() as? Block.ImageBlock
            if (block != null && noteId > 0L) {
                runCatching {
                    com.fan.hwnote.app.model.storage.NoteFileStorage(context)
                        .imageFile(noteId, block.fileName).delete()
                }
            }
        }
        container.removeView(view)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === view) focusedTextBlock = null
        (currentBlocks[idx - 1] as? TextBlockView)?.focusEditEnd()
    }

    override fun onFocusGained(view: BlockView) {
        if (view is TextBlockView) focusedTextBlock = view
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
            bind(block)
        }
        if (insertAt < 0 || insertAt >= currentBlocks.size) {
            container.addView(v)
            currentBlocks.add(v)
        } else {
            container.addView(v, insertAt)
            currentBlocks.add(insertAt, v)
        }
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
    }

    /** Task 10 H1/H2 用：对当前焦点 TextBlock 切 heading。 */
    fun toggleHeading(target: Heading) {
        val v = focusedTextBlock ?: return
        v.setHeading(if (v.currentHeading() == target) null else target)
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
        for (b in blocks) {
            addImageBlockView(b, insertAt = insertAt)
            insertAt += 1
        }
        // 末尾补一个空 TextBlock，让用户可继续输入
        val tail = emptyTextBlock()
        addTextBlockView(tail, insertAt = insertAt)
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
    }
}
