package com.fan.hwnote.app.controller.list

import android.graphics.Color
import android.graphics.Paint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.RepeatType
import com.fan.hwnote.app.model.entity.Todo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TodoListAdapter(
    private val onCheckToggle: (Todo, View) -> Unit,
    private val onClick: (Todo) -> Unit,
    private val onRestore: ((Todo) -> Unit)? = null,
    private val onDeletePermanently: ((Todo) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Item {
        data class Header(val title: String, val isOverdue: Boolean = false) : Item()
        data class TodoItem(val todo: Todo) : Item()
    }

    private val items = mutableListOf<Item>()

    var isBatchMode = false
        set(value) {
            field = value
            selectedIds.clear()
            notifyDataSetChanged()
        }
    var isDeletedView = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    val selectedIds = mutableSetOf<Long>()
    val selectedCount: Int get() = selectedIds.size
    var onBatchSelectionChanged: (() -> Unit)? = null

    fun submit(todos: List<Todo>) {
        items.clear()
        items.addAll(groupTodos(todos))
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Header -> TYPE_SECTION_HEADER
        is Item.TodoItem -> TYPE_TODO_ITEM
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION_HEADER -> SectionVH(
                inflater.inflate(R.layout.item_todo_section_header, parent, false),
            )
            else -> TodoVH(inflater.inflate(R.layout.item_todo_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as SectionVH).bind(item)
            is Item.TodoItem -> (holder as TodoVH).bind(item.todo)
        }
    }

    private inner class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.section_title)
        fun bind(header: Item.Header) {
            title.text = header.title
            title.setTextColor(
                if (header.isOverdue) Color.parseColor("#E53935")
                else ContextCompat.getColor(itemView.context, R.color.text_secondary),
            )
        }
    }

    private inner class TodoVH(v: View) : RecyclerView.ViewHolder(v) {
        private val checkbox: ImageView = v.findViewById(R.id.todo_checkbox)
        private val batchCheckbox: CheckBox = v.findViewById(R.id.batch_checkbox)
        private val deletedActions: View = v.findViewById(R.id.deleted_actions)
        private val titleTv: TextView = v.findViewById(R.id.todo_title)
        private val subtitle: TextView = v.findViewById(R.id.todo_subtitle)

        fun bind(todo: Todo) {
            itemView.alpha = 1f
            itemView.translationX = 0f

            if (isBatchMode) {
                batchCheckbox.visibility = View.VISIBLE
                checkbox.visibility = View.GONE
                deletedActions.visibility = View.GONE
                batchCheckbox.setOnCheckedChangeListener(null)
                batchCheckbox.isChecked = selectedIds.contains(todo.id)
                batchCheckbox.setOnClickListener {
                    if (selectedIds.contains(todo.id)) selectedIds.remove(todo.id)
                    else selectedIds.add(todo.id)
                    onBatchSelectionChanged?.invoke()
                }
                itemView.setOnClickListener {
                    batchCheckbox.isChecked = !batchCheckbox.isChecked
                    if (selectedIds.contains(todo.id)) selectedIds.remove(todo.id)
                    else selectedIds.add(todo.id)
                    onBatchSelectionChanged?.invoke()
                }
            } else if (isDeletedView) {
                batchCheckbox.visibility = View.GONE
                checkbox.visibility = View.GONE
                deletedActions.visibility = View.VISIBLE
                itemView.findViewById<View>(R.id.btn_restore).setOnClickListener {
                    onRestore?.invoke(todo)
                }
                itemView.findViewById<View>(R.id.btn_delete_permanently).setOnClickListener {
                    onDeletePermanently?.invoke(todo)
                }
                itemView.setOnClickListener(null)
            } else {
                batchCheckbox.visibility = View.GONE
                checkbox.visibility = View.VISIBLE
                deletedActions.visibility = View.GONE
                checkbox.setImageResource(
                    if (todo.isCompleted) R.drawable.ic_todo_checkbox_checked
                    else R.drawable.ic_todo_checkbox,
                )
                checkbox.setOnClickListener { onCheckToggle(todo, itemView) }
                itemView.setOnClickListener { onClick(todo) }
            }

            if (todo.isImportant && !todo.isCompleted) {
                val sp = SpannableString("❗${todo.title}")
                sp.setSpan(
                    ForegroundColorSpan(Color.parseColor("#E53935")),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                titleTv.text = sp
            } else {
                titleTv.text = todo.title.ifEmpty {
                    itemView.context.getString(R.string.todo_quick_add_hint)
                }
            }

            if (todo.isCompleted) {
                titleTv.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_hint),
                )
                titleTv.paintFlags = titleTv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                titleTv.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_primary),
                )
                titleTv.paintFlags = titleTv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            val parts = mutableListOf<String>()
            if (todo.remindAt > 0) {
                val fmt = SimpleDateFormat("a h:mm", Locale.getDefault())
                parts += fmt.format(todo.remindAt)
            }
            if (todo.repeatType != RepeatType.NONE) {
                val ctx = itemView.context
                parts += when (todo.repeatType) {
                    RepeatType.DAILY -> ctx.getString(R.string.todo_repeat_daily)
                    RepeatType.WEEKLY -> ctx.getString(R.string.todo_repeat_weekly)
                    RepeatType.MONTHLY -> ctx.getString(R.string.todo_repeat_monthly)
                    RepeatType.YEARLY -> ctx.getString(R.string.todo_repeat_yearly)
                    RepeatType.NONE -> ""
                }
            }
            if (parts.isNotEmpty()) {
                subtitle.text = parts.joinToString(" | ")
                subtitle.visibility = View.VISIBLE
                val isOverdue = todo.remindAt > 0 && todo.remindAt < System.currentTimeMillis()
                    && !todo.isCompleted
                subtitle.setTextColor(
                    if (isOverdue) Color.parseColor("#E53935")
                    else ContextCompat.getColor(itemView.context, R.color.text_hint),
                )
            } else {
                subtitle.visibility = View.GONE
            }

            itemView.setOnClickListener { onClick(todo) }
        }
    }

    companion object {
        private const val TYPE_SECTION_HEADER = 0
        private const val TYPE_TODO_ITEM = 1

        fun groupTodos(todos: List<Todo>): List<Item> {
            val now = System.currentTimeMillis()
            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayStart = todayCal.timeInMillis
            val tomorrowStart = todayStart + 24 * 60 * 60 * 1000L
            val dayAfterTomorrow = tomorrowStart + 24 * 60 * 60 * 1000L

            val overdue = mutableListOf<Todo>()
            val today = mutableListOf<Todo>()
            val tomorrow = mutableListOf<Todo>()
            val later = mutableListOf<Todo>()
            val noDate = mutableListOf<Todo>()
            val completed = mutableListOf<Todo>()

            for (t in todos) {
                if (t.isCompleted) {
                    completed += t
                } else if (t.remindAt == 0L) {
                    noDate += t
                } else if (t.remindAt < todayStart) {
                    overdue += t
                } else if (t.remindAt < tomorrowStart) {
                    today += t
                } else if (t.remindAt < dayAfterTomorrow) {
                    tomorrow += t
                } else {
                    later += t
                }
            }

            val items = mutableListOf<Item>()
            fun addSection(title: String, list: List<Todo>, isOverdue: Boolean = false) {
                if (list.isNotEmpty()) {
                    items += Item.Header(title, isOverdue)
                    for (t in list) items += Item.TodoItem(t)
                }
            }

            addSection("已过期", overdue, isOverdue = true)
            addSection("今天", today)
            addSection("明天", tomorrow)
            addSection("更晚", later)
            addSection("无日期", noDate)
            addSection("已完成", completed)

            return items
        }
    }
}