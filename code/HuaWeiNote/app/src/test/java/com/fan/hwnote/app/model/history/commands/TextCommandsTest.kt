package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextCommandsTest {

    private class FakeMutator : TextMutator {
        val texts: MutableMap<String, String> = mutableMapOf()
        val spans: MutableMap<String, List<TextSpan>> = mutableMapOf()
        var lastFocusBlock: String? = null
        var lastFocusCursor: Int = -1
        override fun silentReplaceText(blockId: String, text: String, spans: List<TextSpan>) {
            texts[blockId] = text
            this.spans[blockId] = spans
        }
        override fun silentRequestFocus(blockId: String, cursorIndex: Int) {
            lastFocusBlock = blockId
            lastFocusCursor = cursorIndex
        }
    }

    @Test fun `ReplaceTextCommand apply 写 after revert 写 before`() {
        val m = FakeMutator().apply { texts["a"] = "hello"; spans["a"] = emptyList() }
        val cmd = ReplaceTextCommand(
            m, "a",
            beforeText = "hello", beforeSpans = emptyList(),
            afterText = "hello world", afterSpans = emptyList(),
        )
        cmd.apply()
        assertEquals("hello world", m.texts["a"])
        cmd.revert()
        assertEquals("hello", m.texts["a"])
    }

    @Test fun `ReplaceTextCommand 同步替换 spans`() {
        val m = FakeMutator()
        val beforeSpans = listOf(TextSpan(0, 5, SpanType.BOLD))
        val afterSpans = listOf(TextSpan(0, 11, SpanType.BOLD))
        val cmd = ReplaceTextCommand(
            m, "a",
            beforeText = "hello", beforeSpans = beforeSpans,
            afterText = "hello world", afterSpans = afterSpans,
        )
        cmd.apply()
        assertEquals(afterSpans, m.spans["a"])
        cmd.revert()
        assertEquals(beforeSpans, m.spans["a"])
    }

    @Test fun `apply 后焦点移到 afterText 末尾`() {
        val m = FakeMutator()
        val cmd = ReplaceTextCommand(
            m, "a",
            beforeText = "hi", beforeSpans = emptyList(),
            afterText = "hi there", afterSpans = emptyList(),
        )
        cmd.apply()
        assertEquals("a", m.lastFocusBlock)
        assertEquals("hi there".length, m.lastFocusCursor)
    }

    @Test fun `revert 后焦点移到 beforeText 末尾`() {
        val m = FakeMutator()
        val cmd = ReplaceTextCommand(
            m, "a",
            beforeText = "hi", beforeSpans = emptyList(),
            afterText = "hi there", afterSpans = emptyList(),
        )
        cmd.apply()
        cmd.revert()
        assertEquals("a", m.lastFocusBlock)
        assertEquals("hi".length, m.lastFocusCursor)
    }
}
