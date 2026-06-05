package com.fan.hwnote.app.model.entity

data class Todo(
    val id: Long = 0L,
    val title: String = "",
    val memo: String = "",
    val isCompleted: Boolean = false,
    val isImportant: Boolean = false,
    val remindAt: Long = 0L,
    val repeatType: RepeatType = RepeatType.NONE,
    val folderId: Long? = null,
    val deletedAt: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        fun new(now: Long = System.currentTimeMillis()): Todo = Todo(
            createdAt = now,
            updatedAt = now,
        )
    }
}
