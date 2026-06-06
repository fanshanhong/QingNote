package com.fan.hwnote.app.controller.list

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.controller.editor.NoteEditorActivity
import com.fan.hwnote.app.controller.folder.FolderManagerActivity
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.view.folder.NotebookPickerPopupWindow
import com.fan.hwnote.app.view.list.DeleteConfirmBottomSheet
import com.fan.hwnote.app.view.list.FilterPanelAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class NoteListFragment : Fragment() {

    private lateinit var rootLayout: View
    private lateinit var headerTitleArea: View
    private lateinit var headerTitle: TextView
    private lateinit var headerArrow: ImageView
    private lateinit var headerSubtitle: TextView
    private lateinit var btnOverflow: ImageView
    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var filterPanel: RecyclerView
    private lateinit var recycler: RecyclerView
    private lateinit var contentArea: View
    private lateinit var emptyState: View
    private lateinit var fab: FloatingActionButton

    private lateinit var adapter: NoteListAdapter

    private var sortBy: NoteRepository.SortBy = NoteRepository.SortBy.UPDATED_DESC
    private var currentQuery: String? = null
    private var currentFilter: NoteRepository.ListFilter = NoteRepository.ListFilter.All
    private var filterPanelVisible = false

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private val expandedFolders = mutableSetOf<Long>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_note_list, container, false)

        rootLayout = view.findViewById(R.id.root_layout)
        headerTitleArea = view.findViewById(R.id.header_title_area)
        headerTitle = view.findViewById(R.id.header_title)
        headerArrow = view.findViewById(R.id.header_arrow)
        headerSubtitle = view.findViewById(R.id.header_subtitle)
        btnOverflow = view.findViewById(R.id.btn_overflow)
        searchBar = view.findViewById(R.id.search_bar)
        searchInput = view.findViewById(R.id.search_input)
        contentArea = view.findViewById(R.id.content_area)
        filterPanel = view.findViewById(R.id.filter_panel)
        recycler = view.findViewById(R.id.recycler_notes)
        emptyState = view.findViewById(R.id.empty_state)
        fab = view.findViewById(R.id.fab_new_note)

        adapter = NoteListAdapter(
            onClick = { note ->
                startActivity(NoteEditorActivity.newIntent(requireContext(), note.id))
            },
            onLongClick = { note, anchor -> showCardMenu(note, anchor) },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        sortBy = loadSort()
        currentFilter = loadFilter()

        headerTitleArea.setOnClickListener { toggleFilterPanel() }
        btnOverflow.setOnClickListener { showOverflowMenu() }
        fab.setOnClickListener {
            startActivity(NoteEditorActivity.newIntent(requireContext(), -1L))
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val text = s?.toString()?.trim().orEmpty()
                searchRunnable = Runnable {
                    currentQuery = text.ifEmpty { null }
                    reload()
                }
                searchHandler.postDelayed(searchRunnable!!, 200L)
            }
        })

        return view
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { updateHeader() }
        reload()
    }

    override fun onDestroyView() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        super.onDestroyView()
    }

    private fun reload() {
        lifecycleScope.launch {
            val list = NoteRepository.list(currentFilter, sortBy, currentQuery)
            val colorMap = mutableMapOf<Long, String>()
            val nbIds = list.mapNotNull { it.notebookId }.toSet()
            for (id in nbIds) {
                val nb = NotebookRepository.get(id)
                if (nb != null) colorMap[id] = nb.color
            }
            adapter.submitColors(colorMap)
            adapter.submit(list)
            renderEmpty(list.isEmpty())
        }
    }

    private fun renderEmpty(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private suspend fun updateHeader() {
        val f = currentFilter
        val _ensureValid = when (f) {
            is NoteRepository.ListFilter.Folder ->
                if (FolderRepository.get(f.folderId) == null) {
                    currentFilter = NoteRepository.ListFilter.All
                    saveFilter(NoteRepository.ListFilter.All)
                } else Unit
            is NoteRepository.ListFilter.Notebook ->
                if (NotebookRepository.get(f.notebookId) == null) {
                    currentFilter = NoteRepository.ListFilter.All
                    saveFilter(NoteRepository.ListFilter.All)
                } else Unit
            NoteRepository.ListFilter.All,
            NoteRepository.ListFilter.Uncategorized,
            NoteRepository.ListFilter.Favorite,
            NoteRepository.ListFilter.Deleted -> Unit
        }
        val cur = currentFilter
        val title = when (cur) {
            NoteRepository.ListFilter.All -> getString(R.string.filter_all_notes)
            NoteRepository.ListFilter.Uncategorized -> getString(R.string.filter_uncategorized)
            NoteRepository.ListFilter.Favorite -> getString(R.string.filter_favorite)
            NoteRepository.ListFilter.Deleted -> getString(R.string.filter_deleted)
            is NoteRepository.ListFilter.Folder ->
                FolderRepository.get(cur.folderId)?.name ?: getString(R.string.filter_all_notes)
            is NoteRepository.ListFilter.Notebook ->
                NotebookRepository.get(cur.notebookId)?.name ?: getString(R.string.filter_all_notes)
        }
        headerTitle.text = title

        val count = NoteRepository.count(cur)
        val subtitle = when (cur) {
            is NoteRepository.ListFilter.Notebook -> {
                val nb = NotebookRepository.get(cur.notebookId)
                val folderName = nb?.folderId?.let { FolderRepository.get(it)?.name }
                if (folderName != null) {
                    getString(R.string.note_count_with_folder_format, count, folderName)
                } else {
                    getString(R.string.note_count_format, count)
                }
            }
            NoteRepository.ListFilter.All,
            NoteRepository.ListFilter.Uncategorized,
            NoteRepository.ListFilter.Favorite,
            NoteRepository.ListFilter.Deleted,
            is NoteRepository.ListFilter.Folder ->
                getString(R.string.note_count_format, count)
        }
        headerSubtitle.text = subtitle

        applyNotebookBackground(cur)
    }

    private suspend fun applyNotebookBackground(filter: NoteRepository.ListFilter) {
        val ctx = requireContext()
        val bgColor = when (filter) {
            is NoteRepository.ListFilter.Notebook -> {
                val nb = NotebookRepository.get(filter.notebookId)
                if (nb != null) {
                    val c = Color.parseColor(nb.color)
                    Color.argb(25, Color.red(c), Color.green(c), Color.blue(c))
                } else {
                    ContextCompat.getColor(ctx, R.color.bg_window)
                }
            }
            NoteRepository.ListFilter.All,
            NoteRepository.ListFilter.Uncategorized,
            NoteRepository.ListFilter.Favorite,
            NoteRepository.ListFilter.Deleted,
            is NoteRepository.ListFilter.Folder ->
                ContextCompat.getColor(ctx, R.color.bg_window)
        }
        rootLayout.setBackgroundColor(bgColor)
    }

    private fun toggleFilterPanel() {
        filterPanelVisible = !filterPanelVisible
        headerArrow.rotation = if (filterPanelVisible) 180f else 0f
        if (filterPanelVisible) {
            searchBar.visibility = View.GONE
            recycler.visibility = View.GONE
            emptyState.visibility = View.GONE
            fab.visibility = View.GONE
            btnOverflow.visibility = View.GONE
            filterPanel.visibility = View.VISIBLE
            filterPanel.layoutManager = LinearLayoutManager(requireContext())
            contentArea.setOnClickListener { if (filterPanelVisible) toggleFilterPanel() }
            rebuildFilterPanel()
        } else {
            filterPanel.visibility = View.GONE
            searchBar.visibility = View.VISIBLE
            fab.visibility = View.VISIBLE
            btnOverflow.visibility = View.VISIBLE
            contentArea.setOnClickListener(null)
            reload()
        }
    }

    private fun rebuildFilterPanel() {
        lifecycleScope.launch {
            val rows = mutableListOf<FilterPanelAdapter.Row>()

            val allCount = NoteRepository.count(NoteRepository.ListFilter.All)
            val uncatCount = NoteRepository.count(NoteRepository.ListFilter.Uncategorized)
            val favCount = NoteRepository.count(NoteRepository.ListFilter.Favorite)
            val delCount = NoteRepository.count(NoteRepository.ListFilter.Deleted)

            rows += FilterPanelAdapter.Row.Pseudo(FilterPanelAdapter.PseudoKind.All, allCount)
            rows += FilterPanelAdapter.Row.Pseudo(FilterPanelAdapter.PseudoKind.Uncategorized, uncatCount)
            rows += FilterPanelAdapter.Row.Pseudo(FilterPanelAdapter.PseudoKind.Favorite, favCount)
            rows += FilterPanelAdapter.Row.Pseudo(FilterPanelAdapter.PseudoKind.Deleted, delCount)
            rows += FilterPanelAdapter.Row.Divider
            rows += FilterPanelAdapter.Row.SectionHeader(
                getString(R.string.filter_section_folders),
                getString(R.string.filter_manage_action),
            )

            val folders = FolderRepository.list()
            for (f in folders) {
                val fCount = NoteRepository.count(NoteRepository.ListFilter.Folder(f.id))
                rows += FilterPanelAdapter.Row.FolderHead(f, expandedFolders.contains(f.id), fCount)
                if (expandedFolders.contains(f.id)) {
                    val nbs = NotebookRepository.listByFolder(f.id)
                    for (nb in nbs) {
                        val nbCount = NoteRepository.count(NoteRepository.ListFilter.Notebook(nb.id))
                        rows += FilterPanelAdapter.Row.NotebookRow(nb, nbCount)
                    }
                }
            }

            filterPanel.adapter = FilterPanelAdapter(
                rows = rows,
                selected = currentFilter,
                onFilterPicked = { picked ->
                    currentFilter = picked
                    saveFilter(picked)
                    lifecycleScope.launch { updateHeader() }
                    toggleFilterPanel()
                },
                onManageFolders = {
                    startActivity(Intent(requireContext(), FolderManagerActivity::class.java))
                },
                onToggleFolder = { folderId ->
                    if (expandedFolders.contains(folderId)) expandedFolders -= folderId
                    else expandedFolders += folderId
                    rebuildFilterPanel()
                },
            )
        }
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(requireContext(), btnOverflow)
        popup.menuInflater.inflate(R.menu.menu_note_list_overflow, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sort -> { showSortDialog(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showCardMenu(note: Note, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        val isDeletedView = currentFilter == NoteRepository.ListFilter.Deleted
        val menuRes = if (isDeletedView) R.menu.menu_note_card_deleted else R.menu.menu_note_card_long_press
        popup.menuInflater.inflate(menuRes, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_toggle_favorite -> {
                    lifecycleScope.launch {
                        NoteRepository.setFavorite(note.id, !note.isFavorite)
                        reload()
                    }
                    true
                }
                R.id.action_delete -> {
                    DeleteConfirmBottomSheet(
                        requireContext(),
                        message = getString(R.string.dialog_soft_delete_message),
                        confirmLabel = getString(R.string.action_delete),
                        onConfirm = {
                            lifecycleScope.launch { NoteRepository.softDelete(note.id); reload() }
                        },
                    ).show()
                    true
                }
                R.id.action_restore -> {
                    lifecycleScope.launch { NoteRepository.restore(note.id); reload() }
                    true
                }
                R.id.action_delete_permanently -> {
                    DeleteConfirmBottomSheet(
                        requireContext(),
                        message = getString(R.string.dialog_delete_permanently_message),
                        confirmLabel = getString(R.string.action_delete_permanently),
                        onConfirm = {
                            lifecycleScope.launch { NoteRepository.deletePermanently(note.id); reload() }
                        },
                    ).show()
                    true
                }
                R.id.action_move_notebook -> {
                    NotebookPickerPopupWindow(
                        context = requireContext(),
                        currentNotebookId = note.notebookId,
                        onPicked = { picked ->
                            lifecycleScope.launch { NoteRepository.moveNoteToNotebook(note.id, picked); reload() }
                        },
                    ).show(anchor)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showSortDialog() {
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_sort_picker, null)
        val group = view.findViewById<RadioGroup>(R.id.sort_radio_group)
        val checkedId = when (sortBy) {
            NoteRepository.SortBy.UPDATED_DESC -> R.id.sort_updated
            NoteRepository.SortBy.CREATED_DESC -> R.id.sort_created
        }
        group.check(checkedId)
        group.setOnCheckedChangeListener { _, id ->
            sortBy = when (id) {
                R.id.sort_created -> NoteRepository.SortBy.CREATED_DESC
                else -> NoteRepository.SortBy.UPDATED_DESC
            }
            saveSort(sortBy)
            reload()
            sheet.dismiss()
        }
        view.findViewById<TextView>(R.id.btn_sort_cancel).setOnClickListener {
            sheet.dismiss()
        }
        sheet.setContentView(view)
        sheet.show()
    }

    private fun loadSort(): NoteRepository.SortBy {
        val prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_SORT, NoteRepository.SortBy.UPDATED_DESC.name)
        return runCatching { NoteRepository.SortBy.valueOf(name!!) }
            .getOrDefault(NoteRepository.SortBy.UPDATED_DESC)
    }

    private fun saveSort(sortBy: NoteRepository.SortBy) {
        requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString(KEY_SORT, sortBy.name).apply()
    }

    private fun loadFilter(): NoteRepository.ListFilter {
        val prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val type = prefs.getString(KEY_FILTER_TYPE, "ALL") ?: "ALL"
        return when (type) {
            "ALL" -> NoteRepository.ListFilter.All
            "UNCATEGORIZED" -> NoteRepository.ListFilter.Uncategorized
            "FAVORITE" -> NoteRepository.ListFilter.Favorite
            "DELETED" -> NoteRepository.ListFilter.Deleted
            "FOLDER" -> {
                val id = prefs.getLong(KEY_FILTER_FOLDER_ID, -1L)
                if (id > 0) NoteRepository.ListFilter.Folder(id) else NoteRepository.ListFilter.All
            }
            "NOTEBOOK" -> {
                val id = prefs.getLong(KEY_FILTER_NOTEBOOK_ID, -1L)
                if (id > 0) NoteRepository.ListFilter.Notebook(id) else NoteRepository.ListFilter.All
            }
            "CATEGORY" -> NoteRepository.ListFilter.All
            else -> NoteRepository.ListFilter.All
        }
    }

    private fun saveFilter(filter: NoteRepository.ListFilter) {
        val editor = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .remove(KEY_FILTER_FOLDER_ID).remove(KEY_FILTER_NOTEBOOK_ID)
        val _save = when (filter) {
            NoteRepository.ListFilter.All -> editor.putString(KEY_FILTER_TYPE, "ALL")
            NoteRepository.ListFilter.Uncategorized -> editor.putString(KEY_FILTER_TYPE, "UNCATEGORIZED")
            NoteRepository.ListFilter.Favorite -> editor.putString(KEY_FILTER_TYPE, "FAVORITE")
            NoteRepository.ListFilter.Deleted -> editor.putString(KEY_FILTER_TYPE, "DELETED")
            is NoteRepository.ListFilter.Folder ->
                editor.putString(KEY_FILTER_TYPE, "FOLDER").putLong(KEY_FILTER_FOLDER_ID, filter.folderId)
            is NoteRepository.ListFilter.Notebook ->
                editor.putString(KEY_FILTER_TYPE, "NOTEBOOK").putLong(KEY_FILTER_NOTEBOOK_ID, filter.notebookId)
        }
        editor.apply()
    }

    companion object {
        private const val PREFS = "hwnote_settings"
        private const val KEY_SORT = "sort_by"
        private const val KEY_FILTER_TYPE = "filter_type"
        private const val KEY_FILTER_FOLDER_ID = "filter_folder_id"
        private const val KEY_FILTER_NOTEBOOK_ID = "filter_notebook_id"
    }
}
