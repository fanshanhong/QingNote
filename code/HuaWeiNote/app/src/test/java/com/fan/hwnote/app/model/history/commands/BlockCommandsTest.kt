package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Block
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BlockCommandsTest {

    /** 假 mutator：内部维护一个 MutableList<Block>，无 View，无焦点处理。 */
    private class FakeMutator(initial: List<Block> = emptyList()) : BlockMutator {
        val blocks: MutableList<Block> = initial.toMutableList()
        var lastFocus: String? = null
        override fun silentInsertBlock(index: Int, block: Block) {
            val safe = index.coerceIn(0, blocks.size)
            blocks.add(safe, block)
        }
        override fun silentRemoveBlock(blockId: String) {
            blocks.removeAll { it.id == blockId }
        }
        override fun silentMoveBlock(from: Int, to: Int) {
            if (from !in blocks.indices || to !in blocks.indices) return
            val b = blocks.removeAt(from)
            blocks.add(to, b)
        }
        override fun silentReplaceBlock(blockId: String, newBlock: Block) {
            val i = indexOfBlock(blockId)
            if (i < 0) return
            blocks[i] = newBlock
        }
        override fun silentRequestFocus(blockId: String, cursorIndex: Int) {
            lastFocus = blockId
        }
        override fun indexOfBlock(blockId: String): Int =
            blocks.indexOfFirst { it.id == blockId }
        override fun blockAt(index: Int): Block? =
            blocks.getOrNull(index)
        /**
         * Block 各子类型字段为 var，可被外部 in-place mutate。snapshot 必须返回深拷贝
         * 实例（含 ChecklistBlock.items 这种内部可变集合的 shallow copy），否则 revert
         * 拿到的会是后续 mutation 后的状态。
         */
        override fun snapshotBlock(blockId: String): Block? {
            val b = blocks.find { it.id == blockId } ?: return null
            return when (b) {
                is Block.TextBlock -> b.copy()
                is Block.ImageBlock -> b.copy()
                is Block.ChecklistBlock -> b.copy(items = b.items.toMutableList())
                is Block.AudioBlock -> b.copy()
            }
        }
    }

    @Test fun `AddBlockCommand apply 后块在指定位置 revert 后移除`() {
        val m = FakeMutator(listOf(Block.TextBlock(id = "a"), Block.TextBlock(id = "c")))
        val cmd = AddBlockCommand(m, 1, Block.TextBlock(id = "b"))
        cmd.apply()
        assertEquals(listOf("a", "b", "c"), m.blocks.map { it.id })
        assertEquals("b", m.lastFocus)
        cmd.revert()
        assertEquals(listOf("a", "c"), m.blocks.map { it.id })
        // 焦点回到 index-1（"a"），符合 spec §4.4。
        assertEquals("a", m.lastFocus)
    }

    @Test fun `AddBlockCommand 在 index=0 插入时 revert 后不主动设焦点`() {
        val m = FakeMutator(listOf(Block.TextBlock(id = "b")))
        val cmd = AddBlockCommand(m, 0, Block.TextBlock(id = "a"))
        cmd.apply()
        assertEquals(listOf("a", "b"), m.blocks.map { it.id })
        assertEquals("a", m.lastFocus)
        // 重置 lastFocus 以观察 revert 是否再次写入。
        m.lastFocus = null
        cmd.revert()
        assertEquals(listOf("b"), m.blocks.map { it.id })
        // head 特例：AddBlockCommand 不知道列表余下首块，留给 Presenter Task 5 处理。
        assertNull(m.lastFocus)
    }

    @Test fun `RemoveBlockCommand apply 后块消失 revert 后复活到原 index`() {
        val m = FakeMutator(listOf(
            Block.TextBlock(id = "a"), Block.TextBlock(id = "b"), Block.TextBlock(id = "c"),
        ))
        val cmd = RemoveBlockCommand(m, "b")
        cmd.apply()
        assertEquals(listOf("a", "c"), m.blocks.map { it.id })
        cmd.revert()
        assertEquals(listOf("a", "b", "c"), m.blocks.map { it.id })
        assertEquals("b", m.lastFocus)
    }

    @Test fun `RemoveBlockCommand 对不存在 blockId apply 是 no-op revert 也是 no-op`() {
        val m = FakeMutator(listOf(Block.TextBlock(id = "a")))
        val cmd = RemoveBlockCommand(m, "nope")
        cmd.apply()
        cmd.revert()
        assertEquals(listOf("a"), m.blocks.map { it.id })
    }

    @Test fun `RemoveBlockCommand revert 应使用 apply 时刻快照 而不是后续 mutation 状态`() {
        val original = Block.TextBlock(id = "b", text = "original")
        val m = FakeMutator(listOf(
            Block.TextBlock(id = "a"), original, Block.TextBlock(id = "c"),
        ))
        val cmd = RemoveBlockCommand(m, "b")
        cmd.apply()
        // apply 之后、revert 之前对原 block 实例 in-place mutate。
        // 若 snapshotBlock 返回 live ref，revert 将恢复 mutated 状态；
        // 正确实现下 snapshot 已脱离，revert 应恢复 apply 时刻的 "original"。
        original.text = "mutated"
        cmd.revert()
        val restored = m.blocks.find { it.id == "b" } as Block.TextBlock
        assertEquals("original", restored.text)
    }

    @Test fun `MoveBlockCommand apply 后顺序变 revert 后还原`() {
        val m = FakeMutator(listOf(
            Block.TextBlock(id = "a"), Block.TextBlock(id = "b"), Block.TextBlock(id = "c"),
        ))
        val cmd = MoveBlockCommand(m, from = 2, to = 0)
        cmd.apply()
        assertEquals(listOf("c", "a", "b"), m.blocks.map { it.id })
        cmd.revert()
        assertEquals(listOf("a", "b", "c"), m.blocks.map { it.id })
    }

    @Test fun `ReplaceBlockCommand apply 后块换型 revert 后还原`() {
        val m = FakeMutator(listOf(
            Block.TextBlock(id = "a", text = "hi"),
        ))
        val newBlock = Block.ChecklistBlock(id = "x")
        val cmd = ReplaceBlockCommand(m, "a", newBlock)
        cmd.apply()
        assertEquals("x", m.blocks[0].id)
        assertEquals(true, m.blocks[0] is Block.ChecklistBlock)
        assertEquals("x", m.lastFocus)
        cmd.revert()
        assertEquals("a", m.blocks[0].id)
        assertEquals("hi", (m.blocks[0] as Block.TextBlock).text)
        assertEquals("a", m.lastFocus)
    }

    @Test fun `ReplaceBlockCommand 对不存在 blockId apply 是 no-op`() {
        val m = FakeMutator(listOf(Block.TextBlock(id = "a")))
        val cmd = ReplaceBlockCommand(m, "nope", Block.TextBlock(id = "x"))
        cmd.apply()
        assertEquals(listOf("a"), m.blocks.map { it.id })
        cmd.revert()
        assertEquals(listOf("a"), m.blocks.map { it.id })
    }
}
