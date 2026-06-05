package com.fan.hwnote.app.view.list

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.entity.Folder
import com.fan.hwnote.app.model.entity.Notebook

class FilterPanelAdapter(
    private val rows: List<Row>,
    private val selected: NoteRepository.ListFilter,
    private val onFilterPicked: (NoteRepository.ListFilter) -> Unit,
    private val onManageFolders: () -> Unit,
    private val onToggleFolder: (Long) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Pseudo(
            val kind: PseudoKind,
            val count: Int,
        ) : Row()

        object Divider : Row()

        data class SectionHeader(
            val title: String,
            val actionLabel: String,
        ) : Row()

        data class FolderHead(
            val folder: Folder,
            val expanded: Boolean,
            val count: Int,
        ) : Row()

        data class NotebookRow(
            val notebook: Notebook,
            val count: Int,
        ) : Row()
    }

    enum class PseudoKind { All, Uncategorized, Favorite, Deleted }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Pseudo -> 0
        is Row.Divider -> 1
        is Row.SectionHeader -> 2
        is Row.FolderHead -> 3
        is Row.NotebookRow -> 4
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> PseudoVH(inflater.inflate(R.layout.item_filter_pseudo, parent, false))
            1 -> DividerVH(View(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(parent.context, 1))
                setBackgroundColor(ContextCompat.getColor(parent.context, R.color.divider))
            })
            2 -> SectionVH(inflater.inflate(R.layout.item_filter_section_header, parent, false))
            3 -> FolderHeadVH(inflater.inflate(R.layout.item_filter_folder_header, parent, false))
            else -> NotebookVH(inflater.inflate(R.layout.item_filter_notebook, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Pseudo -> (holder as PseudoVH).bind(row)
            is Row.Divider -> Unit
            is Row.SectionHeader -> (holder as SectionVH).bind(row)
            is Row.FolderHead -> (holder as FolderHeadVH).bind(row)
            is Row.NotebookRow -> (holder as NotebookVH).bind(row)
        }
    }

    private fun pseudoFilter(kind: PseudoKind): NoteRepository.ListFilter = when (kind) {
        PseudoKind.All -> NoteRepository.ListFilter.All
        PseudoKind.Uncategorized -> NoteRepository.ListFilter.Uncategorized
        PseudoKind.Favorite -> NoteRepository.ListFilter.Favorite
        PseudoKind.Deleted -> NoteRepository.ListFilter.Deleted
    }

    private fun isSelected(filter: NoteRepository.ListFilter): Boolean = selected == filter

    private fun applySelectedState(view: View, bar: View, label: TextView, count: TextView, sel: Boolean) {
        val ctx = view.context
        val blue = ContextCompat.getColor(ctx, R.color.primary)
        val blueLight = ContextCompat.getColor(ctx, R.color.primary_light)
        bar.visibility = if (sel) View.VISIBLE else View.GONE
        view.setBackgroundColor(if (sel) blueLight else Color.TRANSPARENT)
        label.setTextColor(if (sel) blue else ContextCompat.getColor(ctx, R.color.text_primary))
        count.setTextColor(if (sel) blue else ContextCompat.getColor(ctx, R.color.text_hint))
    }

    private inner class PseudoVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: Row.Pseudo) {
            val ctx = itemView.context
            val icon = itemView.findViewById<ImageView>(R.id.icon)
            val label = itemView.findViewById<TextView>(R.id.label)
            val countTv = itemView.findViewById<TextView>(R.id.count)
            val bar = itemView.findViewById<View>(R.id.selected_bar)

            val iconRes = when (row.kind) {
                PseudoKind.All -> R.drawable.ic_note_tab
                PseudoKind.Uncategorized -> R.drawable.ic_uncategorized
                PseudoKind.Favorite -> R.drawable.ic_star_outline
                PseudoKind.Deleted -> R.drawable.ic_delete
            }
            icon.setImageResource(iconRes)
            label.text = when (row.kind) {
                PseudoKind.All -> ctx.getString(R.string.filter_all_notes)
                PseudoKind.Uncategorized -> ctx.getString(R.string.filter_uncategorized)
                PseudoKind.Favorite -> ctx.getString(R.string.filter_favorite)
                PseudoKind.Deleted -> ctx.getString(R.string.filter_deleted)
            }
            countTv.text = row.count.toString()

            val filter = pseudoFilter(row.kind)
            val sel = isSelected(filter)
            applySelectedState(itemView, bar, label, countTv, sel)
            val tintColor = if (sel) ContextCompat.getColor(ctx, R.color.primary) else ContextCompat.getColor(ctx, R.color.text_primary)
            icon.setColorFilter(tintColor)

            itemView.setOnClickListener { onFilterPicked(filter) }
        }
    }

    private class DividerVH(v: View) : RecyclerView.ViewHolder(v)

    private inner class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: Row.SectionHeader) {
            itemView.findViewById<TextView>(R.id.section_title).text = row.title
            val action = itemView.findViewById<TextView>(R.id.section_action)
            action.text = row.actionLabel
            action.setOnClickListener { onManageFolders() }
        }
    }

    private inner class FolderHeadVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: Row.FolderHead) {
            itemView.findViewById<TextView>(R.id.folder_name).text = row.folder.name
            itemView.findViewById<View>(R.id.expand_chevron).rotation = if (row.expanded) 180f else 0f
            itemView.findViewById<TextView>(R.id.folder_count).text = row.count.toString()
            itemView.setOnClickListener { onToggleFolder(row.folder.id) }
        }
    }

    private inner class NotebookVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: Row.NotebookRow) {
            itemView.findViewById<TextView>(R.id.notebook_name).text = row.notebook.name
            val dot = itemView.findViewById<View>(R.id.color_dot)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(row.notebook.color))
            }
            itemView.findViewById<TextView>(R.id.notebook_count).text = row.count.toString()

            val filter = NoteRepository.ListFilter.Notebook(row.notebook.id)
            val sel = isSelected(filter)
            if (sel) {
                val ctx = itemView.context
                itemView.setBackgroundColor(ContextCompat.getColor(ctx, R.color.primary_light))
                itemView.findViewById<TextView>(R.id.notebook_name)
                    .setTextColor(ContextCompat.getColor(ctx, R.color.primary))
                itemView.findViewById<TextView>(R.id.notebook_count)
                    .setTextColor(ContextCompat.getColor(ctx, R.color.primary))
            }
            itemView.setOnClickListener { onFilterPicked(filter) }
        }
    }

    companion object {
        private fun dpToPx(ctx: Context, dp: Int): Int =
            (dp * ctx.resources.displayMetrics.density + 0.5f).toInt()
    }
}
