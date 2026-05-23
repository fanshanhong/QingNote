package com.fan.hwnote.app.view.block

import android.content.Context
import android.util.AttributeSet
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.ChecklistItem
import java.util.UUID

class ChecklistBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BlockView(context, attrs, defStyleAttr), ChecklistItemView.Listener {

    private var blockId: String = "c-${UUID.randomUUID().toString().take(8)}"
    private val items = mutableListOf<ChecklistItemView>()

    override fun bind(block: Block) {
        require(block is Block.ChecklistBlock) { "ChecklistBlockView only accepts ChecklistBlock" }
        blockId = block.id
        removeAllViews()
        items.clear()
        val list = block.items.ifEmpty { mutableListOf(ChecklistItem(false, "")) }
        for (it in list) addItemView(it)
    }

    override fun toBlock(): Block = Block.ChecklistBlock(
        id = blockId,
        items = items.map { it.toItem() }.toMutableList(),
    )

    // ----- ChecklistItemView.Listener -----

    override fun onEnterAtEnd(view: ChecklistItemView) {
        val idx = items.indexOf(view)
        if (idx < 0) return
        val newItem = ChecklistItem(false, "")
        addItemView(newItem, insertAt = idx + 1)
        items[idx + 1].focusEditEnd()
    }

    override fun onBackspaceWhenEmpty(view: ChecklistItemView) {
        val idx = items.indexOf(view)
        if (idx < 0) return
        if (items.size <= 1) {
            // 唯一项空且按退格 → 整个清单块删除
            callback?.onRequestDelete(this)
            return
        }
        removeView(view)
        items.removeAt(idx)
        val target = items[(idx - 1).coerceAtLeast(0)]
        target.focusEditEnd()
    }

    override fun onItemFocusGained(view: ChecklistItemView) {
        callback?.onFocusGained(this)
    }

    // ----- private -----

    private fun addItemView(item: ChecklistItem, insertAt: Int = -1) {
        val v = ChecklistItemView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            listener = this@ChecklistBlockView
            bind(item)
        }
        if (insertAt < 0 || insertAt >= items.size) {
            addView(v)
            items.add(v)
        } else {
            addView(v, insertAt)
            items.add(insertAt, v)
        }
    }

    fun focusLastItemEnd() {
        items.lastOrNull()?.focusEditEnd()
    }
}
