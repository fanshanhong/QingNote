package com.fan.hwnote.app.model.history

/**
 * 撤销 / 重做最小单元。Inverse Op 风格：每个 Command 自带 apply() 与 revert()。
 *
 * label 仅用于调试 / 日志，UI 不展示。
 */
interface Command {
    fun apply()
    fun revert()
    val label: String
}
