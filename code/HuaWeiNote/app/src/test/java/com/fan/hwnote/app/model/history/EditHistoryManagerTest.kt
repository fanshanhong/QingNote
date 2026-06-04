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

    /** 测试用：apply / revert 永远抛异常的命令。 */
    private class ThrowingCommand(override val label: String = "boom") : Command {
        override fun apply() { throw RuntimeException("boom apply") }
        override fun revert() { throw RuntimeException("boom revert") }
    }

    @Test fun `undo 期间 revert 抛异常 - cmd 不入 redo 栈 listener 仍通知`() {
        val m = EditHistoryManager()
        var calls = 0
        m.listener = { _, _ -> calls++ }
        val good = FakeCommand("g")
        m.push(good)             // calls=1
        m.push(ThrowingCommand())// calls=2
        val callsBeforeUndo = calls
        m.undo()                 // 抛异常但被吞，cmd 丢弃
        assertFalse(m.canRedo()) // 关键：不入 redo 栈
        assertTrue(m.canUndo())  // good 还在
        assertEquals(callsBeforeUndo + 1, calls) // listener 仍触发
    }

    @Test fun `redo 期间 apply 抛异常 - cmd 不入 undo 栈 listener 仍通知`() {
        val m = EditHistoryManager()
        // 构造 redoStack：先 push 一个 ThrowingCommand，再 undo 让它进 redo 栈
        // 但 ThrowingCommand.revert 也抛 → 第一次 undo 直接吞掉，cmd 既不在 undo 也不在 redo
        // 所以改造：先 push 一个能 revert 的 FakeCommand，undo 后把 FakeCommand 从 redo 栈拿出来
        // 替换为 ThrowingCommand。最简单做法是用反射 / 暴露内部 — 这里走另一条路：
        // 让 ThrowingCommand 只在 apply 抛，revert 不抛。
        val cmd = object : Command {
            override val label = "redo-throw"
            var revertCalled = false
            override fun apply() { throw RuntimeException("boom apply") }
            override fun revert() { revertCalled = true }
        }
        var calls = 0
        m.listener = { _, _ -> calls++ }
        m.push(cmd)              // calls=1, undoStack=[cmd]
        m.undo()                 // calls=2, redoStack=[cmd] (revert 成功)
        val callsBeforeRedo = calls
        m.redo()                 // apply 抛 → cmd 丢弃
        assertFalse(m.canUndo()) // 关键：不入 undo 栈
        assertFalse(m.canRedo()) // redoStack 也被 removeLast 掉了
        assertEquals(callsBeforeRedo + 1, calls) // listener 仍触发
    }
}
