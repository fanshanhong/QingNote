package com.fan.hwnote.app.model.history

/**
 * 多步骤复合命令：把 N 个 [Command] 打包成 1 个组合单元入栈。
 *
 * 设计目的：保证"1 个用户动作 = 1 次撤销"。例如插入图片时，UI 上一次操作产生 2 个底层
 * 变更（AddBlock(image) + AddBlock(tailText)），如各自入栈则用户感知"按 1 下 ↶ 撤不掉"。
 *
 * 语义：
 *  - [apply] 顺序调用子命令的 apply()（与原始入栈顺序一致）
 *  - [revert] 逆序调用子命令的 revert()（栈式回滚，先撤后做的）
 *
 * 原子性边界：此处"原子"仅指入栈 / 出栈语义 —— 整个 Composite 作为 1 步 undo / redo 单元；
 * 并 *不* 保证子命令执行级别的事务性。若 revert 中途某子命令抛异常，前面已成功 revert 的
 * 子命令不会被自动反向 re-apply，可能留下"部分回滚"中间态。外层 [EditHistoryManager]
 * 的 try/catch 仅做日志兜底，不做补偿。
 */
class CompositeCommand(
    override val label: String,
    commands: List<Command>,
) : Command {

    private val commands: List<Command> = commands.toList()

    init {
        require(this.commands.isNotEmpty()) { "CompositeCommand 需至少 1 个子命令" }
    }

    override fun apply() {
        for (c in commands) c.apply()
    }

    override fun revert() {
        for (c in commands.asReversed()) c.revert()
    }
}
