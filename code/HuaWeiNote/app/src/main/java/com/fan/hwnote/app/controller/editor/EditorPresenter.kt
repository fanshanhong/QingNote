package com.fan.hwnote.app.controller.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
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
import com.fan.hwnote.app.view.block.BlockView
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
    private val passthroughBlocks = mutableListOf<Block>()
    private lateinit var currentNote: Note
    private var focusedTextBlock: TextBlockView? = null

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
        val raw: List<TextSpan> = run {
            val out = mutableListOf<TextSpan>()
            for (s in sp.getSpans(0, sp.length, Any::class.java)) {
                val st = sp.getSpanStart(s); val en = sp.getSpanEnd(s)
                if (st < 0 || en <= st) continue
                when (s) {
                    is StyleSpan -> when (s.style) {
                        Typeface.BOLD -> out += TextSpan(st, en, SpanType.BOLD)
                        Typeface.ITALIC -> out += TextSpan(st, en, SpanType.ITALIC)
                    }
                    is UnderlineSpan -> out += TextSpan(st, en, SpanType.UNDERLINE)
                    is StrikethroughSpan -> out += TextSpan(st, en, SpanType.STRIKETHROUGH)
                    is RelativeSizeSpan -> {
                        val v = when {
                            kotlin.math.abs(s.sizeChange - 0.85f) < 0.01f -> "small"
                            kotlin.math.abs(s.sizeChange - 1.25f) < 0.01f -> "large"
                            kotlin.math.abs(s.sizeChange - 1.0f) < 0.01f -> "medium"
                            else -> null
                        }
                        if (v != null) out += TextSpan(st, en, SpanType.FONT_SIZE, v)
                    }
                    is ForegroundColorSpan -> {
                        val hex = "#%06X".format(0xFFFFFF and s.foregroundColor)
                        out += TextSpan(st, en, SpanType.COLOR, hex)
                    }
                }
            }
            out
        }
        // 清掉所有 span（不会动文字本身）
        for (s in sp.getSpans(0, sp.length, Any::class.java)) sp.removeSpan(s)
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
        passthroughBlocks.clear()
        focusedTextBlock = null

        val blocks = note.content.blocks.ifEmpty { listOf(emptyTextBlock()) }
        for (b in blocks) {
            when (b) {
                is Block.TextBlock -> addTextBlockView(b)
                is Block.ImageBlock,
                is Block.ChecklistBlock -> passthroughBlocks += b // M5/M6 渲染前先缓存，避免重保存丢数据
            }
        }
        // 默认让第一块拿到焦点
        (currentBlocks.firstOrNull() as? TextBlockView)?.focusEditEnd()
    }

    fun currentFocusedTextBlock(): TextBlockView? = focusedTextBlock

    /**
     * 把 UI 当前内容收集成一份新 Note（保留原 id / createdAt / isFavorite，更新 title / content）。
     * 调用方负责 save 到 Repository。
     */
    fun collectCurrentNote(title: String): Note {
        val newBlocks = currentBlocks.map { it.toBlock() } + passthroughBlocks
        // ImageBlock/ChecklistBlock 当前在 M4 不渲染，bind 时缓存、collect 时尾部拼回，避免数据丢失
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

    /** Task 10 H1/H2 用：对当前焦点 TextBlock 切 heading。 */
    fun toggleHeading(target: Heading) {
        val v = focusedTextBlock ?: return
        v.setHeading(if (v.currentHeading() == target) null else target)
    }
}
