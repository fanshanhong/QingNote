package com.fan.hwnote.app.util

object TextUtils {

    /**
     * 从 plain_text 生成卡片摘要：
     * - 把所有换行 \n 替换为空格
     * - 折叠连续空白
     * - 截断到 maxLen
     */
    fun summary(plainText: String, maxLen: Int = 60): String {
        val flattened = plainText.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        return if (flattened.length <= maxLen) flattened else flattened.take(maxLen)
    }

    /** 标题为空时显示"无标题"占位（文案在 Activity 内部决定，本函数只判空）。*/
    fun isBlankTitle(title: String): Boolean = title.trim().isEmpty()
}
