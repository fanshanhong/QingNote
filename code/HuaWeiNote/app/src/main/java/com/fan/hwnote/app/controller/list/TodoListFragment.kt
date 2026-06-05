package com.fan.hwnote.app.controller.list

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.controller.folder.FolderManagerActivity
import com.fan.hwnote.app.controller.todo.TodoDetailActivity
import com.fan.hwnote.app.model.alarm.TodoAlarmManager
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.TodoRepository
import com.fan.hwnote.app.model.entity.Todo
import com.fan.hwnote.app.model.entity.RepeatType
import com.fan.hwnote.app.view.picker.DateTimePickerDialog
import com.fan.hwnote.app.view.picker.RepeatPickerBottomSheet
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class TodoListFragment : Fragment() {

    private lateinit var headerTitleArea: View
    private lateinit var headerTitle: TextView
    private lateinit var headerArrow: ImageView
    private lateinit var headerSubtitle: TextView
    private lateinit var btnOverflow: ImageView
    private lateinit var filterPanel: RecyclerView
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var fab: FloatingActionButton

    private lateinit var quickAddBar: View
    private lateinit var quickAddInput: EditText
    private lateinit var quickAddTime: ImageView
    private lateinit var quickAddImportant: ImageView
    private lateinit var quickAddSave: TextView

    private lateinit var adapter: TodoListAdapter

    private var currentFilter: TodoListFilter = TodoListFilter.All
    private var filterPanelVisible = false
    private var hideCompleted = false

    private var quickAddRemindAt = 0L
    private var quickAddIsImportant = false
    private lateinit var quickAddRepeat: TextView
    private var quickAddRepeatType = RepeatType.NONE

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_todo_list, container, false)

        headerTitleArea = view.findViewById(R.id.header_title_area)
        headerTitle = view.findViewById(R.id.header_title)
        headerArrow = view.findViewById(R.id.header_arrow)
        headerSubtitle = view.findViewById(R.id.header_subtitle)
        btnOverflow = view.findViewById(R.id.btn_overflow)
        filterPanel = view.findViewById(R.id.filter_panel)
        recycler = view.findViewById(R.id.recycler_todos)
        emptyState = view.findViewById(R.id.empty_state)
        fab = view.findViewById(R.id.fab_new_todo)

        quickAddBar = view.findViewById(R.id.quick_add_bar)
        quickAddInput = view.findViewById(R.id.quick_add_input)
        quickAddTime = view.findViewById(R.id.quick_add_time)
        quickAddImportant = view.findViewById(R.id.quick_add_important)
        quickAddSave = view.findViewById(R.id.quick_add_save)
        quickAddRepeat = view.findViewById(R.id.quick_add_repeat)

        adapter = TodoListAdapter(
            onCheckToggle = { todo, itemView ->
                lifecycleScope.launch {
                    if (todo.isCompleted) {
                        TodoRepository.uncompleteTodo(todo.id)
                        reload()
                    } else {
                        TodoRepository.completeTodo(todo.id)
                        itemView.animate()
                            .alpha(0f)
                            .translationX(itemView.width * 0.3f)
                            .setDuration(300)
                            .withEndAction { reload() }
                            .start()
                    }
                }
            },
            onClick = { todo ->
                val intent = Intent(requireContext(), TodoDetailActivity::class.java)
                intent.putExtra(TodoDetailActivity.EXTRA_TODO_ID, todo.id)
                startActivity(intent)
            },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        currentFilter = loadFilter()
        hideCompleted = loadHideCompleted()

        headerTitleArea.setOnClickListener { toggleFilterPanel() }
        btnOverflow.setOnClickListener { showOverflowMenu() }
        fab.setOnClickListener { showQuickAddBar() }

        quickAddTime.setOnClickListener {
            DateTimePickerDialog(requireContext(), quickAddRemindAt) { epochMillis ->
                quickAddRemindAt = epochMillis
                updateQuickAddTimeIcon()
                quickAddRepeat.visibility = View.VISIBLE
                updateQuickAddRepeatLabel()
            }.show()
        }
        quickAddImportant.setOnClickListener { toggleQuickAddImportant() }
        quickAddSave.setOnClickListener { saveQuickAdd() }
        quickAddRepeat.setOnClickListener {
            RepeatPickerBottomSheet(requireContext(), quickAddRepeatType) { type ->
                quickAddRepeatType = type
                updateQuickAddRepeatLabel()
            }.show()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val todos = when (val f = currentFilter) {
                TodoListFilter.All -> TodoRepository.list(hideCompleted = hideCompleted)
                TodoListFilter.Uncategorized -> TodoRepository.listUncategorized(hideCompleted)
                TodoListFilter.Deleted -> TodoRepository.list(includeDeleted = true)
                is TodoListFilter.ByFolder -> TodoRepository.list(
                    folderId = f.folderId, hideCompleted = hideCompleted,
                )
            }
            adapter.submit(todos)
            renderEmpty(todos.isEmpty())
            updateHeader()
        }
    }

    private fun renderEmpty(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private suspend fun updateHeader() {
        val cur = currentFilter
        val title = when (cur) {
            TodoListFilter.All -> getString(R.string.filter_all_todos)
            TodoListFilter.Uncategorized -> getString(R.string.filter_uncategorized)
            TodoListFilter.Deleted -> getString(R.string.filter_deleted)
            is TodoListFilter.ByFolder ->
                FolderRepository.get(cur.folderId)?.name ?: getString(R.string.filter_all_todos)
        }
        headerTitle.text = title

        val count = when (cur) {
            TodoListFilter.All -> TodoRepository.count()
            TodoListFilter.Uncategorized -> TodoRepository.countUncategorized()
            TodoListFilter.Deleted -> TodoRepository.count(includeDeleted = true)
            is TodoListFilter.ByFolder -> TodoRepository.count(folderId = cur.folderId)
        }
        headerSubtitle.text = getString(R.string.todo_count_format, count)
    }

    private fun toggleFilterPanel() {
        filterPanelVisible = !filterPanelVisible
        headerArrow.rotation = if (filterPanelVisible) 180f else 0f
        if (filterPanelVisible) {
            recycler.visibility = View.GONE
            emptyState.visibility = View.GONE
            fab.visibility = View.GONE
            quickAddBar.visibility = View.GONE
            filterPanel.visibility = View.VISIBLE
            filterPanel.layoutManager = LinearLayoutManager(requireContext())
            rebuildFilterPanel()
        } else {
            filterPanel.visibility = View.GONE
            fab.visibility = View.VISIBLE
            reload()
        }
    }

    private fun rebuildFilterPanel() {
        lifecycleScope.launch {
            val rows = mutableListOf<TodoFilterPanelAdapter.Row>()

            val allCount = TodoRepository.count()
            val uncatCount = TodoRepository.countUncategorized()
            val delCount = TodoRepository.count(includeDeleted = true)

            rows += TodoFilterPanelAdapter.Row.Pseudo(TodoFilterPanelAdapter.PseudoKind.All, allCount)
            rows += TodoFilterPanelAdapter.Row.Pseudo(TodoFilterPanelAdapter.PseudoKind.Uncategorized, uncatCount)
            rows += TodoFilterPanelAdapter.Row.Pseudo(TodoFilterPanelAdapter.PseudoKind.Deleted, delCount)
            rows += TodoFilterPanelAdapter.Row.Divider
            rows += TodoFilterPanelAdapter.Row.SectionHeader(
                getString(R.string.filter_section_folders),
                getString(R.string.filter_manage_action),
            )

            val folders = FolderRepository.list()
            for (f in folders) {
                val fCount = TodoRepository.count(folderId = f.id)
                rows += TodoFilterPanelAdapter.Row.FolderRow(f, fCount)
            }

            filterPanel.adapter = TodoFilterPanelAdapter(
                rows = rows,
                selected = currentFilter,
                onFilterPicked = { picked ->
                    currentFilter = picked
                    saveFilter(picked)
                    toggleFilterPanel()
                },
                onManageFolders = {
                    startActivity(Intent(requireContext(), FolderManagerActivity::class.java))
                },
            )
        }
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(requireContext(), btnOverflow)
        popup.menu.add(0, MENU_TOGGLE_COMPLETED, 0,
            if (hideCompleted) R.string.todo_menu_show_completed
            else R.string.todo_menu_hide_completed,
        )
        popup.menu.add(0, MENU_BATCH_DELETE, 1, R.string.todo_menu_batch_delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_TOGGLE_COMPLETED -> {
                    hideCompleted = !hideCompleted
                    saveHideCompleted(hideCompleted)
                    reload()
                    true
                }
                MENU_BATCH_DELETE -> {
                    Toast.makeText(requireContext(), "批量删除（M14c 实现）", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showQuickAddBar() {
        (activity as? NoteListActivity)?.setBottomNavVisible(false)
        fab.visibility = View.GONE
        quickAddBar.visibility = View.VISIBLE
        quickAddInput.text.clear()
        quickAddRemindAt = 0L
        quickAddIsImportant = false
        quickAddRepeatType = RepeatType.NONE
        quickAddRepeat.visibility = View.GONE
        updateQuickAddImportantIcon()
        updateQuickAddTimeIcon()
        quickAddInput.requestFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.showSoftInput(quickAddInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideQuickAddBar() {
        (activity as? NoteListActivity)?.setBottomNavVisible(true)
        quickAddBar.visibility = View.GONE
        fab.visibility = View.VISIBLE
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.hideSoftInputFromWindow(quickAddInput.windowToken, 0)
    }

    private fun toggleQuickAddImportant() {
        quickAddIsImportant = !quickAddIsImportant
        updateQuickAddImportantIcon()
    }

    private fun updateQuickAddImportantIcon() {
        val color = if (quickAddIsImportant)
            ContextCompat.getColor(requireContext(), R.color.primary)
        else
            ContextCompat.getColor(requireContext(), R.color.text_hint)
        quickAddImportant.setColorFilter(color)
    }

    private fun updateQuickAddTimeIcon() {
        val color = if (quickAddRemindAt > 0)
            ContextCompat.getColor(requireContext(), R.color.primary)
        else
            ContextCompat.getColor(requireContext(), R.color.text_hint)
        quickAddTime.setColorFilter(color)
    }

    private fun updateQuickAddRepeatLabel() {
        quickAddRepeat.text = when (quickAddRepeatType) {
            RepeatType.NONE -> getString(R.string.todo_repeat_not_repeat)
            RepeatType.DAILY -> getString(R.string.todo_repeat_daily)
            RepeatType.WEEKLY -> getString(R.string.todo_repeat_weekly)
            RepeatType.MONTHLY -> getString(R.string.todo_repeat_monthly)
            RepeatType.YEARLY -> getString(R.string.todo_repeat_yearly)
        }
    }

    private fun saveQuickAdd() {
        val title = quickAddInput.text.toString().trim()
        if (title.isEmpty()) return

        val folderId = when (val f = currentFilter) {
            is TodoListFilter.ByFolder -> f.folderId
            TodoListFilter.All, TodoListFilter.Uncategorized, TodoListFilter.Deleted -> null
        }

        lifecycleScope.launch {
            val todo = Todo.new().copy(
                title = title,
                remindAt = quickAddRemindAt,
                repeatType = quickAddRepeatType,
                isImportant = quickAddIsImportant,
                folderId = folderId,
            )
            val newId = TodoRepository.insert(todo)
            if (newId > 0L && todo.remindAt > System.currentTimeMillis()) {
                TodoAlarmManager.scheduleAlarm(requireContext(), todo.copy(id = newId))
            }
            hideQuickAddBar()
            reload()
        }
    }

    private fun loadFilter(): TodoListFilter {
        val prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val type = prefs.getString(KEY_TODO_FILTER_TYPE, "ALL") ?: "ALL"
        return when (type) {
            "ALL" -> TodoListFilter.All
            "UNCATEGORIZED" -> TodoListFilter.Uncategorized
            "DELETED" -> TodoListFilter.Deleted
            "FOLDER" -> {
                val id = prefs.getLong(KEY_TODO_FILTER_FOLDER_ID, -1L)
                if (id > 0) TodoListFilter.ByFolder(id) else TodoListFilter.All
            }
            else -> TodoListFilter.All
        }
    }

    private fun saveFilter(filter: TodoListFilter) {
        val editor = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .remove(KEY_TODO_FILTER_FOLDER_ID)
        val _save = when (filter) {
            TodoListFilter.All -> editor.putString(KEY_TODO_FILTER_TYPE, "ALL")
            TodoListFilter.Uncategorized -> editor.putString(KEY_TODO_FILTER_TYPE, "UNCATEGORIZED")
            TodoListFilter.Deleted -> editor.putString(KEY_TODO_FILTER_TYPE, "DELETED")
            is TodoListFilter.ByFolder ->
                editor.putString(KEY_TODO_FILTER_TYPE, "FOLDER")
                    .putLong(KEY_TODO_FILTER_FOLDER_ID, filter.folderId)
        }
        editor.apply()
    }

    private fun loadHideCompleted(): Boolean {
        val prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_COMPLETED, false)
    }

    private fun saveHideCompleted(hide: Boolean) {
        requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_HIDE_COMPLETED, hide).apply()
    }

    companion object {
        private const val PREFS = "hwnote_settings"
        private const val KEY_TODO_FILTER_TYPE = "todo_filter_type"
        private const val KEY_TODO_FILTER_FOLDER_ID = "todo_filter_folder_id"
        private const val KEY_HIDE_COMPLETED = "hide_completed_todos"
        private const val MENU_TOGGLE_COMPLETED = 1001
        private const val MENU_BATCH_DELETE = 1002
    }
}