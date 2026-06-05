package com.fan.hwnote.app.controller.todo

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fan.hwnote.app.R
import com.fan.hwnote.app.controller.list.NoteListActivity
import com.fan.hwnote.app.model.TodoRepository
import com.fan.hwnote.app.model.alarm.TodoAlarmManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TodoAlarmReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(TodoAlarmManager.EXTRA_TODO_ID, -1L)
        val todoTitle = intent.getStringExtra(TodoAlarmManager.EXTRA_TODO_TITLE) ?: ""
        if (todoId <= 0L) return

        when (intent.action) {
            TodoAlarmManager.ACTION_REMIND -> showNotification(context, todoId, todoTitle)

            TodoAlarmManager.ACTION_COMPLETE -> {
                val pending = goAsync()
                GlobalScope.launch(Dispatchers.IO) {
                    runCatching { TodoRepository.completeTodo(todoId) }
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager
                    nm.cancel(todoId.toInt())
                    pending.finish()
                }
            }

            TodoAlarmManager.ACTION_SNOOZE -> {
                TodoAlarmManager.scheduleSnooze(context, todoId, todoTitle)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
                nm.cancel(todoId.toInt())
            }
        }
    }

    private fun showNotification(context: Context, todoId: Long, todoTitle: String) {
        val tapIntent = Intent(context, NoteListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPi = PendingIntent.getActivity(
            context, todoId.toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val completeIntent = Intent(TodoAlarmManager.ACTION_COMPLETE).apply {
            setPackage(context.packageName)
            putExtra(TodoAlarmManager.EXTRA_TODO_ID, todoId)
            putExtra(TodoAlarmManager.EXTRA_TODO_TITLE, todoTitle)
        }
        val completePi = PendingIntent.getBroadcast(
            context, (todoId.toInt() * 10 + 1), completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = Intent(TodoAlarmManager.ACTION_SNOOZE).apply {
            setPackage(context.packageName)
            putExtra(TodoAlarmManager.EXTRA_TODO_ID, todoId)
            putExtra(TodoAlarmManager.EXTRA_TODO_TITLE, todoTitle)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context, (todoId.toInt() * 10 + 2), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, TodoAlarmManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(context.getString(R.string.notification_todo_title))
            .setContentText(todoTitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPi)
            .addAction(0, context.getString(R.string.notification_action_complete), completePi)
            .addAction(0, context.getString(R.string.notification_action_snooze), snoozePi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(todoId.toInt(), notification)
    }
}
