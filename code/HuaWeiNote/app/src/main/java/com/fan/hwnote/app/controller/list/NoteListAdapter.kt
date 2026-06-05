package com.fan.hwnote.app.controller.list

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.util.DateUtils
import com.fan.hwnote.app.util.TextUtils
import com.google.android.material.card.MaterialCardView

class NoteListAdapter(
    private val onClick: (Note) -> Unit,
    private val onLongClick: (Note, View) -> Unit,
) : RecyclerView.Adapter<NoteListAdapter.VH>() {

    private val items = mutableListOf<Note>()
    private var notebookColorMap: Map<Long, String> = emptyMap()

    fun submit(list: List<Note>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun submitColors(map: Map<Long, String>) {
        notebookColorMap = map
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView as MaterialCardView
        private val title: TextView = itemView.findViewById(R.id.card_title)
        private val star: ImageView = itemView.findViewById(R.id.card_star)
        private val time: TextView = itemView.findViewById(R.id.card_time)
        private val summary: TextView = itemView.findViewById(R.id.card_summary)

        fun bind(note: Note) {
            title.text = if (TextUtils.isBlankTitle(note.title))
                itemView.context.getString(R.string.untitled_note)
            else note.title
            star.setImageResource(
                if (note.isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline
            )
            time.text = DateUtils.formatRelative(note.updatedAt)
            summary.text = TextUtils.summary(note.plainText)
            summary.visibility = if (summary.text.isNullOrEmpty()) View.GONE else View.VISIBLE

            val colorStr = note.notebookId?.let { notebookColorMap[it] }
            if (colorStr != null) {
                val c = Color.parseColor(colorStr)
                card.setCardBackgroundColor(Color.argb(20, Color.red(c), Color.green(c), Color.blue(c)))
            } else {
                card.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.bg_card))
            }

            itemView.setOnClickListener { onClick(note) }
            itemView.setOnLongClickListener {
                onLongClick(note, itemView)
                true
            }
        }
    }
}
