package com.fan.hwnote.app

import android.app.Application
import com.fan.hwnote.app.model.CategoryRepository
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.TodoRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class App : Application() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        NoteRepository.init(this)
        CategoryRepository.init(this)
        FolderRepository.init(this)
        NotebookRepository.init(this)
        TodoRepository.init(this)
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { NoteRepository.purgeExpired() }
            runCatching { TodoRepository.purgeExpired() }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
