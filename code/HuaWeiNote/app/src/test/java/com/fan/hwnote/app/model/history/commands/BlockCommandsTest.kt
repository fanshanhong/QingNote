package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Block
import org.junit.jupiter.api.Assertions.assertEquals
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
        override fun snapshotBlock(blockId: String): Block? =
            blocks.find { it.id == blockId }
    }

    @Test fun `AddBlockCommand apply 后块在指定位置 revert 后移除`() {
        val m = FakeMutator(listOf(Block.TextBlock(id = "a"), Block.TextBlock(id = "c")))
        val cmd = AddBlockCommand(m, 1, Block.TextBlock(id = "b"))
        cmd.apply()
        assertEquals(listOf("a", "b", "c"), m.blocks.map { it.id })
        assertEquals("b", m.lastFocus)
        cmd.revert()
        assertEquals(listOf("a", "c"), m.blocks.map { it.id })
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
