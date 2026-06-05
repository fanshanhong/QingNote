package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.ListType
import com.fan.hwnote.app.model.history.Command

class ApplyAlignmentCommand(
    private val mutator: StyleMutator,
    private val blockId: String,
    private val before: Alignment?,
    private val after: Alignment?,
) : Command {
    override val label = "ApplyAlignment($blockId: $before -> $after)"
    override fun apply() {
        mutator.setBlockAlignment(blockId, after)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
    override fun revert() {
        mutator.setBlockAlignment(blockId, before)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
}

class ApplyListTypeCommand(
    private val mutator: StyleMutator,
    private val blockId: String,
    private val before: ListType?,
    private val after: ListType?,
) : Command {
    override val label = "ApplyListType($blockId: $before -> $after)"
    override fun apply() {
        mutator.setBlockListType(blockId, after)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
    override fun revert() {
        mutator.setBlockListType(blockId, before)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
}

class ApplyIndentCommand(
    private val mutator: StyleMutator,
    private val blockId: String,
    private val before: Int,
    private val after: Int,
) : Command {
    override val label = "ApplyIndent($blockId: $before -> $after)"
    override fun apply() {
        mutator.setBlockIndentLevel(blockId, after)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
    override fun revert() {
        mutator.setBlockIndentLevel(blockId, before)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
}
