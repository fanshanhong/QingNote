package com.fan.hwnote.app.view.folder

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Folder
import com.fan.hwnote.app.model.entity.Notebook
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class NotebookPickerPopupWindow(
    private val context: Context,
    private val currentNotebookId: Long?,
    private val onPicked: (Long) -> Unit,
) : PopupWindow() {

    private val expanded = mutableSetOf<Long>()
    private val scope = (context as? LifecycleOwner)?.lifecycleScope ?: MainScope()

    init {
        contentView = LayoutInflater.from(context).inflate(R.layout.popup_notebook_picker, null)
        width = ViewGroup.LayoutParams.WRAP_CONTENT
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        isOutsideTouchable = true
        isFocusable = true
    }

    fun show(anchor: View) {
        if (currentNotebookId != null) {
            scope.launch {
                val nb = NotebookRepository.get(currentNotebookId)
                nb?.folderId?.let { expanded += it }
                rebind()
            }
        } else {
            expanded += 1L
        }
        rebind()
        showAsDropDown(anchor)
    }

    private fun rebind() {
        val recycler = contentView.findViewById<RecyclerView>(R.id.recycler_picker)
        recycler.layoutManager = LinearLayoutManager(context)
        scope.launch {
            val folders = FolderRepository.list()
            val rows = mutableListOf<Row>()
            folders.forEach { f ->
                rows += Row.FolderHead(f, expanded.contains(f.id))
                if (expanded.contains(f.id)) {
                    val nbs = NotebookRepository.listByFolder(f.id)
                    nbs.forEach { rows += Row.Notebook(it) }
                    rows += Row.CreateNew(f.id)
                }
            }
            recycler.adapter = PickerAdapter(rows)
        }
    }

    private sealed class Row {
        data class FolderHead(val folder: Folder, val expanded: Boolean) : Row()
        data class Notebook(val notebook: com.fan.hwnote.app.model.entity.Notebook) : Row()
        data class CreateNew(val folderId: Long) : Row()
    }

    private inner class PickerAdapter(val rows: List<Row>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.FolderHead -> 0
            is Row.Notebook -> 1
            is Row.CreateNew -> 2
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> FolderHeadVH(inflater.inflate(R.layout.item_filter_folder_header, parent, false))
                1 -> NotebookVH(inflater.inflate(R.layout.item_filter_notebook, parent, false))
                else -> CreateVH(inflater.inflate(R.layout.item_picker_create_nb, parent, false))
            }
        }
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            when (val r = rows[p]) {
                is Row.FolderHead -> (h as FolderHeadVH).bind(r.folder, r.expanded)
                is Row.Notebook -> (h as NotebookVH).bind(r.notebook)
                is Row.CreateNew -> (h as CreateVH).bind(r.folderId)
            }
        }
    }

    private inner class FolderHeadVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(folder: Folder, expand: Boolean) {
            itemView.findViewById<TextView>(R.id.folder_name).text = folder.name
            itemView.findViewById<View>(R.id.expand_chevron).rotation = if (expand) 180f else 0f
            itemView.setOnClickListener {
                if (expanded.contains(folder.id)) expanded -= folder.id else expanded += folder.id
                rebind()
            }
        }
    }

    private inner class NotebookVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(nb: Notebook) {
            itemView.findViewById<TextView>(R.id.notebook_name).text = nb.name
            val dot = itemView.findViewById<View>(R.id.color_dot)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(nb.color))
            }
            itemView.setOnClickListener { onPicked(nb.id); dismiss() }
        }
    }

    private inner class CreateVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(folderId: Long) {
            itemView.setOnClickListener {
                NewNotebookBottomSheet(context, folderId, editing = null, onSaved = { newId ->
                    onPicked(newId)
                    dismiss()
                }).show()
            }
        }
    }
}
