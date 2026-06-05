package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.ListType
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import com.fan.hwnote.app.model.history.Command

/**
 * Style 级 silent mutator 接口。
 *
 * "替换 spans" 模型：apply / revert 都是用 setBlockSpans 把目标块的整段 spans 列表换掉，
 * 不做增量增删 — 简化语义、避免 span 重叠歧义。
 */
interface StyleMutator {
    /** 取某 blockId 的当前 spans 快照。TextSpan 所有字段均为 val + 不可变枚举，引用共享安全；不存在返回 null。 */
    fun snapshotSpans(blockId: String): List<TextSpan>?
    /** 用 newSpans 整段替换 blockId 的 spans。不存在则 no-op。 */
    fun setBlockSpans(blockId: String, newSpans: List<TextSpan>)
    /** 取某 blockId 的 heading。不存在或非 TextBlock 返回 null。 */
    fun snapshotHeading(blockId: String): Heading?
    /** 设置 blockId 的 heading（null = 普通段）。 */
    fun setBlockHeading(blockId: String, heading: Heading?)
    /** 取某 blockId 的对齐方式。不存在返回 null。 */
    fun snapshotAlignment(blockId: String): Alignment?
    /** 设置 blockId 的对齐方式（null = 默认 START）。 */
    fun setBlockAlignment(blockId: String, alignment: Alignment?)
    /** 取某 blockId 的列表类型。不存在返回 null。 */
    fun snapshotListType(blockId: String): ListType?
    /** 设置 blockId 的列表类型（null = 非列表）。 */
    fun setBlockListType(blockId: String, listType: ListType?)
    /** 取某 blockId 的缩进级别。不存在返回 0。 */
    fun snapshotIndentLevel(blockId: String): Int
    /** 设置 blockId 的缩进级别。 */
    fun setBlockIndentLevel(blockId: String, indentLevel: Int)
    /** 不带默认值 — 多 Mutator 继承时只能由 BlockMutator 提供 default（Kotlin 限制）。 */
    fun silentRequestFocus(blockId: String, cursorIndex: Int)
}

/**
 * 应用 / 翻转某个 inline span 类型（B/I/U/S/FONT_SIZE/COLOR）到某 block 的指定 range。
 * 用 before/after 快照对：apply 写 after、revert 写 before。
 *
 * 调用方负责在构造前算好"翻转后的 spans"。
 */
class ApplySpanCommand(
    private val mutator: StyleMutator,
    private val blockId: String,
    private val before: List<TextSpan>,
    private val after: List<TextSpan>,
    private val focusCursor: Int,
    private val typeForLabel: SpanType,
) : Command {
    override val label = "ApplySpan($blockId, $typeForLabel)"
    override fun apply() {
        mutator.setBlockSpans(blockId, after)
        mutator.silentRequestFocus(blockId, focusCursor)
    }
    override fun revert() {
        mutator.setBlockSpans(blockId, before)
        mutator.silentRequestFocus(blockId, focusCursor)
    }
}

/**
 * 切换某 block 的 heading（H1/H2/普通段）。
 */
class ApplyHeadingCommand(
    private val mutator: StyleMutator,
    private val blockId: String,
    private val before: Heading?,
    private val after: Heading?,
) : Command {
    override val label = "ApplyHeading($blockId: $before -> $after)"
    override fun apply() {
        mutator.setBlockHeading(blockId, after)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
    override fun revert() {
        mutator.setBlockHeading(blockId, before)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
}
