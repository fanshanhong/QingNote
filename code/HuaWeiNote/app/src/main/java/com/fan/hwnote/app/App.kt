package com.fan.hwnote.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.fan.hwnote.app.model.CategoryRepository
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.TodoRepository
import com.fan.hwnote.app.model.alarm.TodoAlarmManager
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
        createNotificationChannel()
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { NoteRepository.purgeExpired() }
            runCatching { TodoRepository.purgeExpired() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TodoAlarmManager.CHANNEL_ID,
                getString(R.string.notification_channel_todo),
                NotificationManager.IMPORTANCE_HIGH,
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
