package com.fan.hwnote.app.controller.todo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fan.hwnote.app.model.alarm.TodoAlarmManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TodoBootReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        GlobalScope.launch {
            runCatching { TodoAlarmManager.rescheduleAll(context.applicationContext) }
            pending.finish()
        }
    }
}
