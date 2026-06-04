package com.fan.hwnote.app.controller.folder

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Folder
import com.fan.hwnote.app.model.entity.Notebook

sealed class FolderManagerRow {
    data class FolderHead(val folder: Folder, val expanded: Boolean) : FolderManagerRow()
    data class NotebookItem(val notebook: Notebook) : FolderManagerRow()
    data class CreateNotebook(val folderId: Long) : FolderManagerRow()
}

class FolderManagerAdapter(
    var rows: List<FolderManagerRow>,
    private val callbacks: Callbacks,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Callbacks {
        fun onFolderHeaderClicked(folder: Folder)
        fun onFolderHeaderOverflow(folder: Folder, anchor: View)
        fun onNotebookClicked(nb: Notebook)
        fun onNotebookOverflow(nb: Notebook, anchor: View)
        fun onCreateNotebookClicked(folderId: Long)
    }

    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is FolderManagerRow.FolderHead -> 0
        is FolderManagerRow.NotebookItem -> 1
        is FolderManagerRow.CreateNotebook -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> FolderHeadVH(inflater.inflate(R.layout.item_filter_folder_header, parent, false))
            1 -> NotebookVH(inflater.inflate(R.layout.item_filter_notebook, parent, false))
            else -> CreateVH(inflater.inflate(R.layout.item_folder_manager_create, parent, false))
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
        when (val r = rows[p]) {
            is FolderManagerRow.FolderHead -> (h as FolderHeadVH).bind(r.folder, r.expanded)
            is FolderManagerRow.NotebookItem -> (h as NotebookVH).bind(r.notebook)
            is FolderManagerRow.CreateNotebook -> (h as CreateVH).bind(r.folderId)
        }
    }

    inner class FolderHeadVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(folder: Folder, expanded: Boolean) {
            itemView.findViewById<TextView>(R.id.folder_name).text = folder.name
            itemView.findViewById<View>(R.id.expand_chevron).rotation = if (expanded) 180f else 0f
            itemView.setOnClickListener { callbacks.onFolderHeaderClicked(folder) }
            itemView.setOnLongClickListener { callbacks.onFolderHeaderOverflow(folder, itemView); true }
        }
    }

    inner class NotebookVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(nb: Notebook) {
            itemView.findViewById<TextView>(R.id.notebook_name).text = nb.name
            val dot = itemView.findViewById<View>(R.id.color_dot)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(nb.color))
            }
            itemView.setOnClickListener { callbacks.onNotebookClicked(nb) }
            itemView.setOnLongClickListener { callbacks.onNotebookOverflow(nb, itemView); true }
        }
    }

    inner class CreateVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(folderId: Long) {
            itemView.setOnClickListener { callbacks.onCreateNotebookClicked(folderId) }
        }
    }
}
