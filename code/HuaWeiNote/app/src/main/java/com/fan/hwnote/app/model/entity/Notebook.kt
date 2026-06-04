package com.fan.hwnote.app.model.entity

/**
 * 笔记本实体（M12）。id == 0L 表示尚未持久化。
 *
 * folderId 必填（DB schema 同样 NOT NULL）。
 * color = 8 色调色板（spec §6）的 hex 字符串。
 * 系统预置默认笔记本 id=1, folderId=1, isDefault=true。
 */
data class Notebook(
    val id: Long = 0L,
    val name: String,
    val folderId: Long,
    val color: String = "#9E9E9E",
    val orderIndex: Int = 0,
    val isDefault: Boolean = false,
    val deletedAt: Long = 0L,
)
