package com.fan.hwnote.app.controller.editor

import android.content.Context
import android.widget.LinearLayout
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.entity.NoteContent
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
    private lateinit var currentNote: Note
    private var focusedTextBlock: TextBlockView? = null

    fun bind(note: Note) {
        currentNote = note
        container.removeAllViews()
        currentBlocks.clear()
        focusedTextBlock = null

        val blocks = note.content.blocks.ifEmpty { listOf(emptyTextBlock()) }
        for (b in blocks) {
            when (b) {
                is Block.TextBlock -> addTextBlockView(b)
                is Block.ImageBlock -> Unit // M5 接入
                is Block.ChecklistBlock -> Unit // M6 接入
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
