package com.fan.hwnote.app.model.entity

/**
 * 文件夹实体（M12）。id == 0L 表示尚未持久化。
 *
 * 系统预置默认文件夹 id=1, isDefault=true，UI 不允许删除（仅允许改名）。
 */
data class Folder(
    val id: Long = 0L,
    val name: String,
    val orderIndex: Int = 0,
    val isDefault: Boolean = false,
    val deletedAt: Long = 0L,    // 0 = 未删除；>0 = 软删时间戳
)
