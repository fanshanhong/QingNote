package com.fan.hwnote.app.model.entity

/**
 * 笔记顶层实体。id == 0L 表示尚未持久化（NoteRepository.save 时插入）。
 *
 * Note 是不可变快照（用 .copy 改字段）；Block 内字段是 var（编辑器 View 直接改）。
 *
 * M12：新增 notebookId（null = "未分类"），与旧 categoryId 并存（Task 5 改造完毕后会下线 categoryId）。
 */
data class Note(
    val id: Long = 0L,
    val title: String = "",
    val plainText: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val content: NoteContent = NoteContent.empty(),
    val categoryId: Long? = null,     // null = 未分类
    val deletedAt: Long = 0L,         // 0 = 未删除；>0 = 删除时间戳
    val notebookId: Long? = null,     // M12：null = "未分类"
) {
    companion object {
        /** 新建一条笔记的内存对象。createdAt/updatedAt 都填 now，等待 save 时入库。 */
        fun new(now: Long = System.currentTimeMillis()): Note = Note(
            id = 0L,
            createdAt = now,
            updatedAt = now,
        )
    }
}
