package com.fan.hwnote.app.model.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CompositeCommandTest {

    /** 测试用假命令：把自身 label 按 apply / revert 时机分别追加到对应 log，用于断言调用顺序。 */
    private class RecordingCommand(
        override val label: String,
        val applyLog: MutableList<String>,
        val revertLog: MutableList<String>,
    ) : Command {
        override fun apply() { applyLog.add(label) }
        override fun revert() { revertLog.add(label) }
    }

    @Test fun `apply 按顺序调用子命令`() {
        val applyLog = mutableListOf<String>()
        val revertLog = mutableListOf<String>()
        val c0 = RecordingCommand("c0", applyLog, revertLog)
        val c1 = RecordingCommand("c1", applyLog, revertLog)
        val c2 = RecordingCommand("c2", applyLog, revertLog)
        val composite = CompositeCommand("combo", listOf(c0, c1, c2))

        composite.apply()

        assertEquals(listOf("c0", "c1", "c2"), applyLog)
    }

    @Test fun `revert 逆序调用子命令`() {
        val applyLog = mutableListOf<String>()
        val revertLog = mutableListOf<String>()
        val c0 = RecordingCommand("c0", applyLog, revertLog)
        val c1 = RecordingCommand("c1", applyLog, revertLog)
        val c2 = RecordingCommand("c2", applyLog, revertLog)
        val composite = CompositeCommand("combo", listOf(c0, c1, c2))

        composite.revert()

        assertEquals(listOf("c2", "c1", "c0"), revertLog)
    }

    @Test fun `空 commands 列表构造抛 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            CompositeCommand("empty", emptyList())
        }
    }
}
