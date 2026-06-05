package com.fan.hwnote.app.controller.list

sealed class TodoListFilter {
    object All : TodoListFilter()
    object Uncategorized : TodoListFilter()
    object Deleted : TodoListFilter()
    data class ByFolder(val folderId: Long) : TodoListFilter()
}
