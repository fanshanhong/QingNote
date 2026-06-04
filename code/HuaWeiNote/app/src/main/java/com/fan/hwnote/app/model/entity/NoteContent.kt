package com.fan.hwnote.app.model.entity

/**
 * 笔记的完整内容：内容块列表 + 手写笔画列表。
 * 持久化为 content_json 字段（详见 PRD §6.2）。
 */
data class NoteContent(
    val blocks: List<Block>,
    val handwriting: List<Stroke>,
) {
    /**
     * 生成 plain_text（搜索字段）：
     * - TextBlock：取 text，非空才计入
     * - ChecklistBlock：每项 text，非空才计入
     * - ImageBlock：忽略
     * - AudioBlock：忽略
     * - handwriting：忽略
     * 多段用 '\n' 拼接。
     */
    fun toPlainText(): String {
        val sb = StringBuilder()
        for (b in blocks) {
            when (b) {
                is Block.TextBlock -> appendIfNotEmpty(sb, b.text)
                is Block.ChecklistBlock -> b.items.forEach { appendIfNotEmpty(sb, it.text) }
                is Block.ImageBlock -> Unit // 图片不进搜索
                is Block.AudioBlock -> Unit // 音频不进搜索
            }
        }
        return sb.toString()
    }

    private fun appendIfNotEmpty(sb: StringBuilder, text: String) {
        if (text.isEmpty()) return
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(text)
    }

    companion object {
        fun empty(): NoteContent = NoteContent(emptyList(), emptyList())
    }
}
