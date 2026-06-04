package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.history.Command

/**
 * Block 级别 silent mutator 接口。EditorPresenter 实现这组方法；Command 持有该接口引用便于测试。
 * 实现方保证：这些方法不调用 history.push（避免无限递归）。
 */
interface BlockMutator {
    /** 在 index 处插入 block；index 越界时插到末尾。 */
    fun silentInsertBlock(index: Int, block: Block)
    /** 按 blockId 移除；不存在则 no-op。 */
    fun silentRemoveBlock(blockId: String)
    /** 从 from 移到 to；任一越界则 no-op。 */
    fun silentMoveBlock(from: Int, to: Int)
    /** 替换某 blockId 处的整块为 newBlock；不存在则 no-op。 */
    fun silentReplaceBlock(blockId: String, newBlock: Block)
    /** 把焦点拉到某 blockId（光标可选位置）；不存在或非 TextBlock 则 no-op。 */
    fun silentRequestFocus(blockId: String, cursorIndex: Int = Int.MAX_VALUE)
    /** 查询某 blockId 当前 index；不存在返回 -1。 */
    fun indexOfBlock(blockId: String): Int
    /** 取某 index 处的 Block（不深拷贝；越界返回 null）。Command 用于捕获邻居 id 等只读查询。 */
    fun blockAt(index: Int): Block?
    /** 取某 blockId 当前 Block 快照（深拷贝语义由 data class copy 给出）。不存在返回 null。 */
    fun snapshotBlock(blockId: String): Block?
}

/**
 * 在 index 处插入 block。
 * apply = insert + 焦点到新块；revert = removeByBlockId + 焦点回到插入前的前一块（若有）。
 *
 * 焦点还原策略（spec §4.4）：apply 时记录 index-1 处块的 id；revert 在移除新块后把焦点
 * 拉回该 id。若 index=0（无前块），revert 不主动设焦点 —— 该 head 特例由 Presenter
 * Task 5 利用完整列表上下文补齐。
 */
class AddBlockCommand(
    private val mutator: BlockMutator,
    private val index: Int,
    private val block: Block,
) : Command {
    override val label = "AddBlock(${block.id}@$index)"
    private var previousBlockId: String? = null
    override fun apply() {
        previousBlockId = if (index > 0) mutator.blockAt(index - 1)?.id else null
        mutator.silentInsertBlock(index, block)
        mutator.silentRequestFocus(block.id)
    }
    override fun revert() {
        mutator.silentRemoveBlock(block.id)
        previousBlockId?.let { mutator.silentRequestFocus(it, Int.MAX_VALUE) }
    }
}

/**
 * 移除某 block。需要在 apply 前抓快照（Block 数据 + 当前 index）以便 revert 还原。
 *
 * 调用方若已手工 remove（如 onRequestDelete 路径），可通过 [presnapshot] / [presavedIndex]
 * 预填快照；此时 apply 不再二次抓取（push 不调 apply，预填仅服务 revert 路径）。
 */
class RemoveBlockCommand(
    private val mutator: BlockMutator,
    private val blockId: String,
    /** 可选预填 — 调用方已 remove 时传入。null 时 apply 路径会自己抓。 */
    presnapshot: Block? = null,
    presavedIndex: Int = -1,
) : Command {
    override val label = "RemoveBlock($blockId)"
    private var snapshot: Block? = presnapshot
    private var savedIndex: Int = presavedIndex
    override fun apply() {
        if (snapshot == null) {
            snapshot = mutator.snapshotBlock(blockId)
            savedIndex = mutator.indexOfBlock(blockId)
        }
        mutator.silentRemoveBlock(blockId)
    }
    override fun revert() {
        val s = snapshot ?: return
        mutator.silentInsertBlock(savedIndex.coerceAtLeast(0), s)
        mutator.silentRequestFocus(s.id)
    }
}

/**
 * 把第 from 个块移到第 to 个位置。
 * revert = 反向移动。
 */
class MoveBlockCommand(
    private val mutator: BlockMutator,
    private val from: Int,
    private val to: Int,
) : Command {
    override val label = "MoveBlock($from->$to)"
    override fun apply() {
        mutator.silentMoveBlock(from, to)
    }
    override fun revert() {
        mutator.silentMoveBlock(to, from)
    }
}

/**
 * 把某 blockId 的整块替换为 newBlock（用于清单↔文本转换 / 改变 block 类型场景）。
 * 需要在 apply 前抓 oldBlock 快照以便 revert。
 *
 * 调用方若已手工 replace（如 onChecklistConvertBlockToText），可通过 [preOldBlock] 预填
 * 旧块快照；此时 apply 不再二次抓取（push 不调 apply，预填仅服务 revert 路径）。
 */
class ReplaceBlockCommand(
    private val mutator: BlockMutator,
    private val blockId: String,
    private val newBlock: Block,
    preOldBlock: Block? = null,
) : Command {
    override val label = "ReplaceBlock($blockId -> ${newBlock.id})"
    private var oldBlock: Block? = preOldBlock
    override fun apply() {
        if (oldBlock == null) oldBlock = mutator.snapshotBlock(blockId)
        mutator.silentReplaceBlock(blockId, newBlock)
        mutator.silentRequestFocus(newBlock.id)
    }
    override fun revert() {
        val o = oldBlock ?: return
        mutator.silentReplaceBlock(newBlock.id, o)
        mutator.silentRequestFocus(o.id)
    }
}
