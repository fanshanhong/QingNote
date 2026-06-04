package com.fan.hwnote.app.model.history

/**
 * 撤销 / 重做双栈管理器。
 * - 上限 50：超过时淘汰最老条目（队首出队）
 * - push 会清 redo 栈（新操作发生 → 之前 redo 路径作废）
 * - listener 在任一栈大小变化时回调（含 push / undo / redo / clear）
 *
 * 线程模型：所有方法仅主线程调用（Presenter / Activity 调用方保证）。
 */
class EditHistoryManager(private val cap: Int = 50) {

    private val undoStack: ArrayDeque<Command> = ArrayDeque()
    private val redoStack: ArrayDeque<Command> = ArrayDeque()

    /** 状态变化回调。参数：(canUndo, canRedo)。 */
    var listener: ((Boolean, Boolean) -> Unit)? = null

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /** 入栈一个新操作（操作真实变更已由调用方完成）。会清空 redo 栈、并按 cap 淘汰最老。 */
    fun push(cmd: Command) {
        redoStack.clear()
        undoStack.addLast(cmd)
        while (undoStack.size > cap) undoStack.removeFirst()
        notifyListener()
    }

    /** 撤销一步。空栈 no-op。revert 抛异常时丢弃该 cmd（不入 redo 栈），保持栈一致。 */
    fun undo() {
        if (undoStack.isEmpty()) return
        val cmd = undoStack.removeLast()
        try {
            cmd.revert()
            redoStack.addLast(cmd)
        } catch (t: Throwable) {
            System.err.println("EditHistoryManager: undo revert failed: ${cmd.label}: ${t.message}")
        }
        notifyListener()
    }

    /** 重做一步。空栈 no-op。apply 抛异常时丢弃该 cmd（不入 undo 栈）。 */
    fun redo() {
        if (redoStack.isEmpty()) return
        val cmd = redoStack.removeLast()
        try {
            cmd.apply()
            undoStack.addLast(cmd)
        } catch (t: Throwable) {
            System.err.println("EditHistoryManager: redo apply failed: ${cmd.label}: ${t.message}")
        }
        notifyListener()
    }

    /** 清空两栈（用于 onSaveSuccess 等"跨保存清栈"语义）。 */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        notifyListener()
    }

    private fun notifyListener() {
        listener?.invoke(canUndo(), canRedo())
    }
}
