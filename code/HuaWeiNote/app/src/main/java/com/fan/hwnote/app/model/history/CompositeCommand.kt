package com.fan.hwnote.app.model.history

/**
 * 多步骤复合命令：把 N 个 [Command] 打包成 1 个原子单元入栈。
 *
 * 设计目的：保证"1 个用户动作 = 1 次撤销"。例如插入图片时，UI 上一次操作产生 2 个底层
 * 变更（AddBlock(image) + AddBlock(tailText)），如各自入栈则用户感知"按 1 下 ↶ 撤不掉"。
 *
 * 语义：
 *  - [apply] 顺序调用子命令的 apply()（与原始入栈顺序一致）
 *  - [revert] 逆序调用子命令的 revert()（栈式回滚，先撤后做的）
 *
 * 注意：子命令本身的 apply / revert 由本类显式触发；本类对子命令异常不做吞抛处理 —
 * 由外层 [EditHistoryManager] 的 try/catch 统一兜底（与单 Command 一致）。
 */
class CompositeCommand(
    override val label: String,
    private val commands: List<Command>,
) : Command {

    init {
        require(commands.isNotEmpty()) { "CompositeCommand 需至少 1 个子命令" }
    }

    override fun apply() {
        for (c in commands) c.apply()
    }

    override fun revert() {
        for (i in commands.indices.reversed()) commands[i].revert()
    }
}
