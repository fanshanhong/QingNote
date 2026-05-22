package com.fan.hwnote.app.model.storage

import android.content.Context
import java.io.File

/**
 * 笔记文件目录布局：
 *   filesDir/notes/<noteId>/images/<uuid>.jpg
 *
 * 所有 noteDir / imageDir 调用都是 mkdirs 幂等的，即已存在不创建。
 */
class NoteFileStorage(context: Context) {

    private val filesDir: File = context.applicationContext.filesDir

    fun noteDir(noteId: Long): File {
        val dir = File(filesDir, "notes/$noteId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun imageDir(noteId: Long): File {
        val dir = File(noteDir(noteId), "images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun imageFile(noteId: Long, fileName: String): File =
        File(imageDir(noteId), fileName)

    fun deleteNoteDir(noteId: Long) {
        val dir = File(filesDir, "notes/$noteId")
        if (dir.exists()) dir.deleteRecursively()
    }
}
