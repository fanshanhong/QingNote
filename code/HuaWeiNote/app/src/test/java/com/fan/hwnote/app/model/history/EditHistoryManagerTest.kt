package com.fan.hwnote.app.model.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditHistoryManagerTest {

    /** 测试用假命令：apply/revert 仅记录调用次数。 */
    private class FakeCommand(override val label: String = "fake") : Command {
        var applied = 0
        var reverted = 0
        override fun apply() { applied++ }
        override fun revert() { reverted++ }
    }

    @Test fun `初始状态两栈都空`() {
        val m = EditHistoryManager()
        assertFalse(m.canUndo())
        assertFalse(m.canRedo())
    }

    @Test fun `push 后可 undo 不可 redo`() {
        val m = EditHistoryManager()
        m.push(FakeCommand())
        assertTrue(m.canUndo())
        assertFalse(m.canRedo())
    }

    @Test fun `undo 后调用 revert 且 cmd 进 redo 栈`() {
        val m = EditHistoryManager()
        val c = FakeCommand()
        m.push(c)
        m.undo()
        assertEquals(1, c.reverted)
        assertFalse(m.canUndo())
        assertTrue(m.canRedo())
    }

    @Test fun `redo 后调用 apply 且 cmd 回 undo 栈`() {
        val m = EditHistoryManager()
        val c = FakeCommand()
        m.push(c)
        m.undo()
        m.redo()
        assertEquals(1, c.applied)
        assertTrue(m.canUndo())
        assertFalse(m.canRedo())
    }

    @Test fun `push 后 redo 栈被清空`() {
        val m = EditHistoryManager()
        m.push(FakeCommand("a"))
        m.undo()
        assertTrue(m.canRedo())
        m.push(FakeCommand("b"))
        assertFalse(m.canRedo())
    }

    @Test fun `超过 50 上限最老条目淘汰`() {
        val m = EditHistoryManager()
        val first = FakeCommand("first")
        m.push(first)
        repeat(50) { m.push(FakeCommand("x$it")) }
        var count = 0
        while (m.canUndo()) { m.undo(); count++ }
        assertEquals(50, count)
        assertEquals(0, first.reverted)
    }

    @Test fun `clear 后两栈都空 + listener 触发 false false`() {
        val m = EditHistoryManager()
        var lastCanUndo: Boolean? = null
        var lastCanRedo: Boolean? = null
        m.listener = { u, r -> lastCanUndo = u; lastCanRedo = r }
        m.push(FakeCommand())
        m.undo()
        m.push(FakeCommand())
        m.clear()
        assertFalse(m.canUndo())
        assertFalse(m.canRedo())
        assertEquals(false, lastCanUndo)
        assertEquals(false, lastCanRedo)
    }

    @Test fun `空栈 undo 与 redo 是 no-op 不抛`() {
        val m = EditHistoryManager()
        m.undo()
        m.redo()
        assertFalse(m.canUndo())
        assertFalse(m.canRedo())
    }
}
