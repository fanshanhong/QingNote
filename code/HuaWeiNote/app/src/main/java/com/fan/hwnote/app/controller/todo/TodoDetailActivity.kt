package com.fan.hwnote.app.controller.todo

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.TodoRepository
import com.fan.hwnote.app.model.alarm.TodoAlarmManager
import com.fan.hwnote.app.model.entity.RepeatType
import com.fan.hwnote.app.model.entity.Todo
import com.fan.hwnote.app.view.list.DeleteConfirmBottomSheet
import com.fan.hwnote.app.view.picker.DateTimePickerDialog
import com.fan.hwnote.app.view.picker.RepeatPickerBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class TodoDetailActivity : AppCompatActivity() {

    private lateinit var folderIndicator: TextView
    private lateinit var checkComplete: CheckBox
    private lateinit var inputTitle: EditText
    private lateinit var rowRemind: View
    private lateinit var tvRemind: TextView
    private lateinit var btnClearRemind: ImageView
    private lateinit var rowRepeat: View
    private lateinit var tvRepeatValue: TextView
    private lateinit var switchImportant: Switch
    private lateinit var inputMemo: EditText

    private var todoId = -1L
    private var loadedTodo: Todo? = null
    private var pendingRemindAt = 0L
    private var pendingRepeatType = RepeatType.NONE
    private var pendingFolderId: Long? = null
    private var pendingIsCompleted = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showDateTimePicker()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_todo_detail)

        folderIndicator = findViewById(R.id.folder_indicator)
        checkComplete = findViewById(R.id.check_complete)
        inputTitle = findViewById(R.id.input_title)
        rowRemind = findViewById(R.id.row_remind)
        tvRemind = findViewById(R.id.tv_remind)
        btnClearRemind = findViewById(R.id.btn_clear_remind)
        rowRepeat = findViewById(R.id.row_repeat)
        tvRepeatValue = findViewById(R.id.tv_repeat_value)
        switchImportant = findViewById(R.id.switch_important)
        inputMemo = findViewById(R.id.input_memo)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        rowRemind.setOnClickListener { onRemindClicked() }
        btnClearRemind.setOnClickListener { clearRemind() }
        rowRepeat.setOnClickListener { showRepeatPicker() }
        checkComplete.setOnCheckedChangeListener { _, isChecked ->
            pendingIsCompleted = isChecked
        }
        folderIndicator.setOnClickListener { showFolderPicker() }
        findViewById<View>(R.id.btn_share).setOnClickListener {
            Toast.makeText(this, R.string.toast_todo_share_placeholder, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_delete).setOnClickListener { deleteTodo() }

        todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        loadTodo()
    }

    private fun loadTodo() {
        if (todoId <= 0L) {
            val newTodo = Todo.new()
            loadedTodo = newTodo
            pendingRemindAt = 0L
            pendingRepeatType = RepeatType.NONE
            pendingFolderId = null
            pendingIsCompleted = false
            updateRemindUI()
            updateRepeatUI()
            updateFolderUI()
            inputTitle.requestFocus()
            return
        }

        lifecycleScope.launch {
            val todo = TodoRepository.getById(todoId) ?: run {
                Toast.makeText(this@TodoDetailActivity,
                    R.string.todo_save_failed, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            loadedTodo = todo
            pendingRemindAt = todo.remindAt
            pendingRepeatType = todo.repeatType
            pendingFolderId = todo.folderId
            pendingIsCompleted = todo.isCompleted

            inputTitle.setText(todo.title)
            inputMemo.setText(todo.memo)
            checkComplete.isChecked = todo.isCompleted
            switchImportant.isChecked = todo.isImportant
            updateRemindUI()
            updateRepeatUI()
            updateFolderUI()
        }
    }

    private fun updateRemindUI() {
        if (pendingRemindAt <= 0) {
            tvRemind.text = getString(R.string.todo_detail_add_remind)
            tvRemind.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            btnClearRemind.visibility = View.GONE
            return
        }

        val cal = Calendar.getInstance().apply { timeInMillis = pendingRemindAt }
        val now = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            && cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM)
            getString(R.string.picker_datetime_am) else getString(R.string.picker_datetime_pm)
        var hour = cal.get(Calendar.HOUR)
        if (hour == 0) hour = 12
        val minute = cal.get(Calendar.MINUTE)

        val text = if (isToday) {
            getString(R.string.todo_remind_today_format, amPm, hour, minute)
        } else {
            val dateStr = "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
            getString(R.string.todo_remind_format, dateStr, amPm, hour, minute)
        }
        tvRemind.text = text

        val overdue = pendingRemindAt < System.currentTimeMillis()
        val color = if (overdue) ContextCompat.getColor(this, R.color.danger)
            else ContextCompat.getColor(this, R.color.primary)
        tvRemind.setTextColor(color)
        btnClearRemind.visibility = View.VISIBLE
    }

    private fun updateRepeatUI() {
        val text = when (pendingRepeatType) {
            RepeatType.NONE -> getString(R.string.todo_detail_no_repeat)
            RepeatType.DAILY -> getString(R.string.todo_repeat_daily)
            RepeatType.WEEKLY -> getString(R.string.todo_repeat_weekly)
            RepeatType.MONTHLY -> getString(R.string.todo_repeat_monthly)
            RepeatType.YEARLY -> getString(R.string.todo_repeat_yearly)
        }
        tvRepeatValue.text = text
    }

    private fun updateFolderUI() {
        val fId = pendingFolderId
        if (fId == null) {
            folderIndicator.text = getString(R.string.todo_detail_folder_none)
            return
        }
        lifecycleScope.launch {
            val folder = FolderRepository.get(fId)
            folderIndicator.text = folder?.name ?: getString(R.string.todo_detail_folder_none)
        }
    }

    override fun onPause() {
        super.onPause()
        saveTodo()
    }

    private fun saveTodo() {
        val loaded = loadedTodo ?: return
        val title = inputTitle.text.toString().trim()
        val memo = inputMemo.text.toString()
        val isImportant = switchImportant.isChecked

        if (loaded.id == 0L && title.isEmpty()) return

        val toSave = loaded.copy(
            title = title,
            memo = memo,
            isCompleted = pendingIsCompleted,
            isImportant = isImportant,
            remindAt = pendingRemindAt,
            repeatType = pendingRepeatType,
            folderId = pendingFolderId,
        )

        lifecycleScope.launch(Dispatchers.IO) {
            if (loaded.id == 0L) {
                val newId = TodoRepository.insert(toSave)
                if (newId > 0L) {
                    val saved = toSave.copy(id = newId)
                    if (saved.remindAt > System.currentTimeMillis()) {
                        TodoAlarmManager.scheduleAlarm(this@TodoDetailActivity, saved)
                    }
                    withContext(Dispatchers.Main) {
                        todoId = newId
                        loadedTodo = saved
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TodoDetailActivity,
                            R.string.todo_save_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                TodoRepository.update(toSave)
                if (toSave.remindAt > System.currentTimeMillis()) {
                    TodoAlarmManager.scheduleAlarm(this@TodoDetailActivity, toSave)
                } else {
                    TodoAlarmManager.cancelAlarm(this@TodoDetailActivity, toSave.id)
                }
                withContext(Dispatchers.Main) {
                    loadedTodo = toSave
                }
            }
        }
    }

    private fun onRemindClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.alarm_permission_title)
                    .setMessage(R.string.alarm_permission_message)
                    .setPositiveButton(R.string.action_open_settings) { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        showDateTimePicker()
    }

    private fun showDateTimePicker() {
        DateTimePickerDialog(this, pendingRemindAt) { epochMillis ->
            pendingRemindAt = epochMillis
            updateRemindUI()
        }.show()
    }

    private fun clearRemind() {
        pendingRemindAt = 0L
        pendingRepeatType = RepeatType.NONE
        if (todoId > 0L) {
            TodoAlarmManager.cancelAlarm(this, todoId)
        }
        updateRemindUI()
        updateRepeatUI()
    }

    private fun showRepeatPicker() {
        RepeatPickerBottomSheet(this, pendingRepeatType) { type ->
            pendingRepeatType = type
            updateRepeatUI()
        }.show()
    }

    private fun showFolderPicker() {
        lifecycleScope.launch {
            val folders = FolderRepository.list()
            val names = mutableListOf(getString(R.string.todo_detail_folder_none))
            val ids = mutableListOf<Long?>(null)
            for (f in folders) {
                names += f.name
                ids += f.id
            }
            val currentIdx = ids.indexOf(pendingFolderId).coerceAtLeast(0)
            AlertDialog.Builder(this@TodoDetailActivity)
                .setTitle(R.string.category_picker_title)
                .setSingleChoiceItems(names.toTypedArray(), currentIdx) { dialog, which ->
                    pendingFolderId = ids[which]
                    updateFolderUI()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun deleteTodo() {
        if (todoId <= 0L) {
            finish()
            return
        }
        DeleteConfirmBottomSheet(
            context = this,
            message = getString(R.string.todo_delete_message),
            confirmLabel = getString(R.string.action_delete),
        ) {
            lifecycleScope.launch {
                TodoRepository.softDelete(todoId)
                TodoAlarmManager.cancelAlarm(this@TodoDetailActivity, todoId)
                finish()
            }
        }.show()
    }

    companion object {
        const val EXTRA_TODO_ID = "todo_id"
    }
}