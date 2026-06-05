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
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Folder
import com.fan.hwnote.app.model.entity.Notebook
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * 笔记本筛选弹层：3 个伪条目（全部 / 收藏 / 回收站）+ 文件夹折叠树（长按文件夹头选 Folder 过滤；点笔记本选 Notebook 过滤）
 * + 末尾「管理文件夹」入口（M12 T9 落地）。
 */
class NotebookFilterPopupWindow(
    private val context: Context,
    private val current: NoteRepository.ListFilter,
    private val onPicked: (NoteRepository.ListFilter) -> Unit,
) : PopupWindow() {

    private val expanded = mutableSetOf<Long>()
    private val scope = (context as? LifecycleOwner)?.lifecycleScope ?: MainScope()

    init {
        contentView = LayoutInflater.from(context).inflate(R.layout.popup_notebook_filter, null)
        width = ViewGroup.LayoutParams.WRAP_CONTENT
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        isOutsideTouchable = true
        isFocusable = true
    }

    fun show(anchor: View) {
        val recycler = contentView.findViewById<RecyclerView>(R.id.recycler_filter)
        recycler.layoutManager = LinearLayoutManager(context)
        when (val f = current) {
            is NoteRepository.ListFilter.Folder -> expanded += f.folderId
            is NoteRepository.ListFilter.Notebook -> {
                scope.launch {
                    val nb = NotebookRepository.get(f.notebookId)
                    nb?.folderId?.let { expanded += it }
                    rebind(recycler)
                }
            }
            NoteRepository.ListFilter.All,
            NoteRepository.ListFilter.Uncategorized,
            NoteRepository.ListFilter.Favorite,
            NoteRepository.ListFilter.Deleted -> Unit
        }
        rebind(recycler)
        showAsDropDown(anchor)
    }

    private fun rebind(recycler: RecyclerView) {
        scope.launch {
            val folders = FolderRepository.list()
            val rows = mutableListOf<Row>()
            rows += Row.Pseudo(PseudoKind.All)
            rows += Row.Pseudo(PseudoKind.Favorite)
            rows += Row.Pseudo(PseudoKind.Deleted)
            folders.forEach { f ->
                rows += Row.FolderHead(f, expanded.contains(f.id))
                if (expanded.contains(f.id)) {
                    val nbs = NotebookRepository.listByFolder(f.id)
                    nbs.forEach { rows += Row.Notebook(it) }
                }
            }
            rows += Row.Pseudo(PseudoKind.Manage)
            recycler.adapter = FilterAdapter(rows)
        }
    }

    private sealed class Row {
        data class Pseudo(val kind: PseudoKind) : Row()
        data class FolderHead(val folder: Folder, val expanded: Boolean) : Row()
        data class Notebook(val notebook: com.fan.hwnote.app.model.entity.Notebook) : Row()
    }

    private enum class PseudoKind { All, Favorite, Deleted, Manage }

    private inner class FilterAdapter(val rows: List<Row>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.Pseudo -> 0
            is Row.FolderHead -> 1
            is Row.Notebook -> 2
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> PseudoVH(inflater.inflate(R.layout.item_filter_pseudo, parent, false))
                1 -> FolderHeadVH(inflater.inflate(R.layout.item_filter_folder_header, parent, false))
                else -> NotebookVH(inflater.inflate(R.layout.item_filter_notebook, parent, false))
            }
        }
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            when (val r = rows[p]) {
                is Row.Pseudo -> (h as PseudoVH).bind(r.kind)
                is Row.FolderHead -> (h as FolderHeadVH).bind(r.folder, r.expanded)
                is Row.Notebook -> (h as NotebookVH).bind(r.notebook)
            }
        }
    }

    private inner class PseudoVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(kind: PseudoKind) {
            val label = itemView.findViewById<TextView>(R.id.label)
            label.text = when (kind) {
                PseudoKind.All -> context.getString(R.string.filter_all)
                PseudoKind.Favorite -> context.getString(R.string.filter_favorite)
                PseudoKind.Deleted -> context.getString(R.string.filter_deleted)
                PseudoKind.Manage -> context.getString(R.string.folder_manage)
            }
            itemView.setOnClickListener {
                when (kind) {
                    PseudoKind.All -> { onPicked(NoteRepository.ListFilter.All); dismiss() }
                    PseudoKind.Favorite -> { onPicked(NoteRepository.ListFilter.Favorite); dismiss() }
                    PseudoKind.Deleted -> { onPicked(NoteRepository.ListFilter.Deleted); dismiss() }
                    PseudoKind.Manage -> {
                        context.startActivity(android.content.Intent(context, com.fan.hwnote.app.controller.folder.FolderManagerActivity::class.java))
                        dismiss()
                    }
                }
            }
        }
    }

    private inner class FolderHeadVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(folder: Folder, expand: Boolean) {
            itemView.findViewById<TextView>(R.id.folder_name).text = folder.name
            itemView.findViewById<View>(R.id.expand_chevron).rotation = if (expand) 180f else 0f
            itemView.setOnClickListener {
                if (expanded.contains(folder.id)) expanded -= folder.id else expanded += folder.id
                rebind(itemView.parent as RecyclerView)
            }
            itemView.setOnLongClickListener {
                onPicked(NoteRepository.ListFilter.Folder(folder.id)); dismiss(); true
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
            itemView.setOnClickListener {
                onPicked(NoteRepository.ListFilter.Notebook(nb.id)); dismiss()
            }
        }
    }
}
