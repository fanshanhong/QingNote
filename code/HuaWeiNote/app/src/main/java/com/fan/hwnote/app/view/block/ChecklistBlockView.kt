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
        // 仅"末尾位置 + 空项"才退出清单：删该空项 + 通知 Presenter 在清单后插 TextBlock；清单空了则整块替换。
        // 中间空项保持原行为（仍新增下一项），以匹配用户选择"保持原"。
        if (view.edit.text.isEmpty() && idx == items.size - 1) {
            removeView(view)
            items.removeAt(idx)
            if (items.isEmpty()) {
                callback?.onChecklistConvertBlockToText(this)
            } else {
                callback?.onChecklistAppendTextAfter(this)
            }
            return
        }
        // 其余 → 原行为（新增空项 + 焦点到新项）
        val newItem = ChecklistItem(false, "")
        addItemView(newItem, insertAt = idx + 1)
        items[idx + 1].focusEditEnd()
    }

    override fun onBackspaceWhenEmpty(view: ChecklistItemView) {
        val idx = items.indexOf(view)
        if (idx < 0) return
        if (items.size <= 1) {
            // 唯一项空且按退格 → 整块替换为空 TextBlock（焦点稳交 TextBlock）
            callback?.onChecklistConvertBlockToText(this)
            return
        }
        removeView(view)
        items.removeAt(idx)
        val target = items[(idx - 1).coerceAtLeast(0)]
        target.focusEditEnd()
    }

    /** 删除指定 item view（不通知 callback），返回 true 表示清单变空。供 Presenter 在 toggle 取消单项时调用。 */
    fun removeItemAndReturnEmpty(item: ChecklistItemView): Boolean {
        val idx = items.indexOf(item)
        if (idx >= 0) {
            removeView(item)
            items.removeAt(idx)
        }
        return items.isEmpty()
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

    fun setEditable(editable: Boolean) {
        for (item in items) item.setEditable(editable)
    }

    fun focusLastItemEnd() {
        items.lastOrNull()?.focusEditEnd()
    }

    fun lastItemEdit(): android.widget.EditText? = items.lastOrNull()?.edit
}
