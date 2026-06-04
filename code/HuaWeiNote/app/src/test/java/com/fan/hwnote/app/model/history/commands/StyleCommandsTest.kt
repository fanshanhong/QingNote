package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StyleCommandsTest {

    private class FakeMutator : StyleMutator {
        val spans: MutableMap<String, List<TextSpan>> = mutableMapOf()
        val headings: MutableMap<String, Heading?> = mutableMapOf()
        var lastFocus: String? = null
        var lastCursor: Int = -1
        override fun snapshotSpans(blockId: String) = spans[blockId]
        override fun setBlockSpans(blockId: String, newSpans: List<TextSpan>) {
            spans[blockId] = newSpans
        }
        override fun snapshotHeading(blockId: String) = headings[blockId]
        override fun setBlockHeading(blockId: String, heading: Heading?) {
            headings[blockId] = heading
        }
        override fun silentRequestFocus(blockId: String, cursorIndex: Int) {
            lastFocus = blockId
            lastCursor = cursorIndex
        }
    }

    @Test fun `ApplySpanCommand apply 写 after revert 写 before`() {
        val m = FakeMutator().apply { spans["a"] = emptyList() }
        val before: List<TextSpan> = emptyList()
        val after = listOf(TextSpan(0, 5, SpanType.BOLD))
        val cmd = ApplySpanCommand(m, "a", before, after, focusCursor = 5, typeForLabel = SpanType.BOLD)
        cmd.apply()
        assertEquals(after, m.spans["a"])
        assertEquals("a", m.lastFocus)
        assertEquals(5, m.lastCursor)
        cmd.revert()
        assertEquals(before, m.spans["a"])
        assertEquals("a", m.lastFocus)
        assertEquals(5, m.lastCursor)
    }

    @Test fun `ApplyHeadingCommand apply 写 after revert 写 before`() {
        val m = FakeMutator().apply { headings["a"] = null }
        val cmd = ApplyHeadingCommand(m, "a", before = null, after = Heading.H1)
        cmd.apply()
        assertEquals(Heading.H1, m.headings["a"])
        assertEquals("a", m.lastFocus)
        assertEquals(Int.MAX_VALUE, m.lastCursor)
        cmd.revert()
        assertEquals(null, m.headings["a"])
        assertEquals("a", m.lastFocus)
        assertEquals(Int.MAX_VALUE, m.lastCursor)
    }

    @Test fun `ApplyHeadingCommand 从 H1 切到普通段 - revert 回 H1`() {
        val m = FakeMutator().apply { headings["a"] = Heading.H1 }
        val cmd = ApplyHeadingCommand(m, "a", before = Heading.H1, after = null)
        cmd.apply()
        assertEquals(null, m.headings["a"])
        assertEquals("a", m.lastFocus)
        assertEquals(Int.MAX_VALUE, m.lastCursor)
        cmd.revert()
        assertEquals(Heading.H1, m.headings["a"])
        assertEquals("a", m.lastFocus)
        assertEquals(Int.MAX_VALUE, m.lastCursor)
    }

    @Test fun `多个 ApplySpanCommand 链式应用 - 逆序 revert 还原`() {
        val m = FakeMutator().apply { spans["a"] = emptyList() }
        val s0: List<TextSpan> = emptyList()
        val s1 = listOf(TextSpan(0, 3, SpanType.BOLD))
        val s2 = listOf(TextSpan(0, 3, SpanType.BOLD), TextSpan(0, 3, SpanType.ITALIC))
        val c1 = ApplySpanCommand(m, "a", s0, s1, focusCursor = 3, typeForLabel = SpanType.BOLD)
        val c2 = ApplySpanCommand(m, "a", s1, s2, focusCursor = 3, typeForLabel = SpanType.ITALIC)
        c1.apply()
        assertEquals("a", m.lastFocus)
        assertEquals(3, m.lastCursor)
        c2.apply()
        assertEquals(s2, m.spans["a"])
        assertEquals("a", m.lastFocus)
        assertEquals(3, m.lastCursor)
        c2.revert()
        assertEquals(s1, m.spans["a"])
        assertEquals("a", m.lastFocus)
        assertEquals(3, m.lastCursor)
        c1.revert()
        assertEquals(s0, m.spans["a"])
        assertEquals("a", m.lastFocus)
        assertEquals(3, m.lastCursor)
    }
}
