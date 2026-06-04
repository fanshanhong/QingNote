package com.fan.hwnote.app.controller.folder

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class FolderManagerDragHelper(
    private val getRows: () -> List<FolderManagerRow>,
    private val setRows: (List<FolderManagerRow>) -> Unit,
    private val commitFolderOrder: (List<Long>) -> Unit,
    private val commitNotebookOrder: (folderId: Long, ids: List<Long>) -> Unit,
    private val commitNotebookMove: (notebookId: Long, targetFolderId: Long) -> Unit,
) : ItemTouchHelper.Callback() {

    private var dirty = false

    override fun isLongPressDragEnabled() = true
    override fun isItemViewSwipeEnabled() = false

    override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
        val pos = vh.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return 0
        val row = getRows()[pos]
        if (row is FolderManagerRow.CreateNotebook) return 0
        if (row is FolderManagerRow.FolderHead && row.folder.id == 1L) return 0
        return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    }

    override fun onMove(rv: RecyclerView, src: RecyclerView.ViewHolder, dst: RecyclerView.ViewHolder): Boolean {
        val from = src.bindingAdapterPosition
        val to = dst.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        val rows = getRows().toMutableList()
        val moving = rows[from]
        val target = rows[to]

        if (moving is FolderManagerRow.NotebookItem && moving.notebook.id == 1L) {
            val srcFolderId = moving.notebook.folderId
            val targetFolderId = inferFolderIdAt(rows, to) ?: return false
            if (srcFolderId != targetFolderId) return false
        }

        if (moving is FolderManagerRow.FolderHead) {
            val segStart = from
            var segEnd = from + 1
            while (segEnd < rows.size && rows[segEnd] !is FolderManagerRow.FolderHead) segEnd++
            val segment = rows.subList(segStart, segEnd).toList()
            if (target is FolderManagerRow.FolderHead && target.folder.id == 1L && to < from) return false
            repeat(segment.size) { rows.removeAt(segStart) }
            val insertAt = if (to > from) to - segment.size + 1 else to
            rows.addAll(insertAt.coerceAtLeast(0), segment)
            setRows(rows)
            dirty = true
            return true
        }

        if (moving is FolderManagerRow.NotebookItem) {
            rows.removeAt(from)
            rows.add(to, moving)
            setRows(rows)
            dirty = true
            return true
        }

        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        super.clearView(rv, vh)
        if (!dirty) return
        dirty = false
        val rows = getRows()
        val folderOrder = mutableListOf<Long>()
        val notebookByFolder = linkedMapOf<Long, MutableList<Long>>()
        var currentFolder: Long? = null
        rows.forEach { r ->
            when (r) {
                is FolderManagerRow.FolderHead -> {
                    folderOrder += r.folder.id
                    currentFolder = r.folder.id
                    notebookByFolder.getOrPut(r.folder.id) { mutableListOf() }
                }
                is FolderManagerRow.NotebookItem -> {
                    val cf = currentFolder ?: return@forEach
                    notebookByFolder.getOrPut(cf) { mutableListOf() } += r.notebook.id
                    if (r.notebook.folderId != cf) commitNotebookMove(r.notebook.id, cf)
                }
                is FolderManagerRow.CreateNotebook -> Unit
            }
        }
        commitFolderOrder(folderOrder)
        notebookByFolder.forEach { (fId, ids) -> commitNotebookOrder(fId, ids) }
    }

    private fun inferFolderIdAt(rows: List<FolderManagerRow>, index: Int): Long? {
        for (i in index downTo 0) {
            val r = rows[i]
            if (r is FolderManagerRow.FolderHead) return r.folder.id
            if (r is FolderManagerRow.NotebookItem) return r.notebook.folderId
        }
        return null
    }
}
