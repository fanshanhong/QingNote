package com.fan.hwnote.app.model.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fan.hwnote.app.model.TodoRepository
import com.fan.hwnote.app.model.entity.Todo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TodoAlarmManager {

    const val ACTION_REMIND = "com.fan.hwnote.ACTION_TODO_REMIND"
    const val ACTION_COMPLETE = "com.fan.hwnote.ACTION_TODO_COMPLETE"
    const val ACTION_SNOOZE = "com.fan.hwnote.ACTION_TODO_SNOOZE"
    const val EXTRA_TODO_ID = "todo_id"
    const val EXTRA_TODO_TITLE = "todo_title"
    const val CHANNEL_ID = "todo_reminders"

    fun scheduleAlarm(context: Context, todo: Todo) {
        if (todo.remindAt <= 0 || todo.isCompleted || todo.deletedAt > 0) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            return
        }

        val intent = Intent(ACTION_REMIND).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_TODO_ID, todo.id)
            putExtra(EXTRA_TODO_TITLE, todo.title)
        }
        val pi = PendingIntent.getBroadcast(
            context, todo.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, todo.remindAt, pi)
    }

    fun cancelAlarm(context: Context, todoId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_REMIND).apply {
            setPackage(context.packageName)
        }
        val pi = PendingIntent.getBroadcast(
            context, todoId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
    }

    fun scheduleSnooze(context: Context, todoId: Long, todoTitle: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_REMIND).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_TODO_ID, todoId)
            putExtra(EXTRA_TODO_TITLE, todoTitle)
        }
        val pi = PendingIntent.getBroadcast(
            context, todoId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000L
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pi)
    }

    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val todos = TodoRepository.listPendingAlarms()
        for (todo in todos) {
            if (todo.remindAt > System.currentTimeMillis()) {
                scheduleAlarm(context, todo)
            }
        }
    }
}
