package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.TextSpan
import com.fan.hwnote.app.model.history.Command

/**
 * Text 防抖 silent mutator 接口。
 */
interface TextMutator {
    /** 用 text + spans 整段替换 blockId 的内容。不存在则 no-op。 */
    fun silentReplaceText(blockId: String, text: String, spans: List<TextSpan>)
    /** 不带默认值 — 多 Mutator 继承时只能由 BlockMutator 提供 default（Kotlin 限制）。 */
    fun silentRequestFocus(blockId: String, cursorIndex: Int)
}

/**
 * 文本块文字替换。TextBlockView 防抖 800ms 后捕捉 before / after 快照，构造本 Command 入栈。
 *
 * apply = 写 after（用于 redo 路径，初始 push 时调用方已先调 silent mutator，无需再 apply 一次）
 *   ↑ 调用约定：调用方 push 前已经把 after 写入；EditHistoryManager.push 不调 apply。
 *     redo 路径才会调 apply。
 *
 * revert = 写 before。
 */
class ReplaceTextCommand(
    private val mutator: TextMutator,
    private val blockId: String,
    private val beforeText: String,
    private val beforeSpans: List<TextSpan>,
    private val afterText: String,
    private val afterSpans: List<TextSpan>,
) : Command {
    override val label = "ReplaceText($blockId, len ${beforeText.length}->${afterText.length})"
    override fun apply() {
        mutator.silentReplaceText(blockId, afterText, afterSpans)
        mutator.silentRequestFocus(blockId, afterText.length)
    }
    override fun revert() {
        mutator.silentReplaceText(blockId, beforeText, beforeSpans)
        mutator.silentRequestFocus(blockId, beforeText.length)
    }
}
