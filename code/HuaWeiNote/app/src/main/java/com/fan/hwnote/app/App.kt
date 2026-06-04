package com.fan.hwnote.app

import android.app.Application
import com.fan.hwnote.app.model.CategoryRepository
import com.fan.hwnote.app.model.NoteRepository
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
        // 启动清理：删除超 30 天的软删笔记。GlobalScope 无生命周期边界，App 单例存活全程，runCatching 兜底吞错。
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { NoteRepository.purgeExpired() }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
