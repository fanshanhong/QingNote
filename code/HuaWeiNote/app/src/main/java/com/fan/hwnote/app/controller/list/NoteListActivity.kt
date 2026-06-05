package com.fan.hwnote.app.controller.list

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.controller.editor.NoteEditorActivity
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.view.folder.NotebookFilterPopupWindow
import com.fan.hwnote.app.view.folder.NotebookPickerPopupWindow
import com.fan.hwnote.app.view.list.DeleteConfirmBottomSheet
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class NoteListActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var filterChip: LinearLayout
    private lateinit var filterChipText: TextView
    private lateinit var searchInput: EditText
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: NoteListAdapter

    private var sortBy: NoteRepository.SortBy = NoteRepository.SortBy.UPDATED_DESC
    private var currentQuery: String? = null
    private var currentFilter: NoteRepository.ListFilter = NoteRepository.ListFilter.All

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        toolbar = findViewById(R.id.toolbar)
        filterChip = findViewById(R.id.filter_chip)
        filterChipText = findViewById(R.id.filter_chip_text)
        searchInput = findViewById(R.id.search_input)
        recycler = findViewById(R.id.recycler_notes)
        emptyState = findViewById(R.id.empty_state)
        fab = findViewById(R.id.fab_new_note)

        setSupportActionBar(toolbar)

        adapter = NoteListAdapter(
            onClick = { note ->
                startActivity(NoteEditorActivity.newIntent(this, note.id))
            },
            onLongClick = { note, anchor -> showCardMenu(note, anchor) },
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        sortBy = loadSort()
        currentFilter = loadFilter()
        lifecycleScope.launch { updateFilterChipLabel() }
        filterChip.setOnClickListener { showFilterPicker() }

        fab.setOnClickListener {
            startActivity(NoteEditorActivity.newIntent(this, -1L))
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
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun reload() {
        lifecycleScope.launch {
            val list = NoteRepository.list(currentFilter, sortBy, currentQuery)
            adapter.submit(list)
            renderEmpty(list.isEmpty())
        }
    }

    private fun renderEmpty(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showCardMenu(note: Note, anchor: View) {
        val popup = PopupMenu(this, anchor)
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
                        this,
                        title = getString(R.string.dialog_delete_title),
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
                        this,
                        title = getString(R.string.dialog_delete_permanently_title),
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
                        context = this,
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_note_list_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_sort) {
            showSortDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSortDialog() {
        val sheet = BottomSheetDialog(this)
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
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val name = prefs.getString(KEY_SORT, NoteRepository.SortBy.UPDATED_DESC.name)
        return runCatching { NoteRepository.SortBy.valueOf(name!!) }
            .getOrDefault(NoteRepository.SortBy.UPDATED_DESC)
    }

    private fun saveSort(sortBy: NoteRepository.SortBy) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_SORT, sortBy.name).apply()
    }

    private fun showFilterPicker() {
        NotebookFilterPopupWindow(
            context = this,
            current = currentFilter,
            onPicked = { picked ->
                currentFilter = picked
                saveFilter(picked)
                lifecycleScope.launch { updateFilterChipLabel() }
                reload()
            },
        ).show(filterChip)
    }

    private fun loadFilter(): NoteRepository.ListFilter {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val type = prefs.getString(KEY_FILTER_TYPE, "ALL") ?: "ALL"
        return when (type) {
            "ALL" -> NoteRepository.ListFilter.All
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
            "UNCATEGORIZED" -> NoteRepository.ListFilter.Uncategorized
            "CATEGORY" -> NoteRepository.ListFilter.All
            else -> NoteRepository.ListFilter.All
        }
    }

    private fun saveFilter(filter: NoteRepository.ListFilter) {
        val editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .remove(KEY_FILTER_FOLDER_ID).remove(KEY_FILTER_NOTEBOOK_ID)
        when (filter) {
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

    private suspend fun updateFilterChipLabel() {
        val f = currentFilter
        when (f) {
            is NoteRepository.ListFilter.Folder ->
                if (FolderRepository.get(f.folderId) == null) {
                    currentFilter = NoteRepository.ListFilter.All
                    saveFilter(NoteRepository.ListFilter.All)
                }
            is NoteRepository.ListFilter.Notebook ->
                if (NotebookRepository.get(f.notebookId) == null) {
                    currentFilter = NoteRepository.ListFilter.All
                    saveFilter(NoteRepository.ListFilter.All)
                }
            NoteRepository.ListFilter.All,
            NoteRepository.ListFilter.Uncategorized,
            NoteRepository.ListFilter.Favorite,
            NoteRepository.ListFilter.Deleted -> Unit
        }
        val label = when (val cur = currentFilter) {
            NoteRepository.ListFilter.All -> getString(R.string.filter_all)
            NoteRepository.ListFilter.Uncategorized -> getString(R.string.filter_uncategorized)
            NoteRepository.ListFilter.Favorite -> getString(R.string.filter_favorite)
            NoteRepository.ListFilter.Deleted -> getString(R.string.filter_deleted)
            is NoteRepository.ListFilter.Folder ->
                FolderRepository.get(cur.folderId)?.name ?: getString(R.string.filter_all)
            is NoteRepository.ListFilter.Notebook ->
                NotebookRepository.get(cur.notebookId)?.name ?: getString(R.string.filter_all)
        }
        filterChipText.text = label
    }

    companion object {
        private const val PREFS = "hwnote_settings"
        private const val KEY_SORT = "sort_by"
        private const val KEY_FILTER_TYPE = "filter_type"
        private const val KEY_FILTER_FOLDER_ID = "filter_folder_id"
        private const val KEY_FILTER_NOTEBOOK_ID = "filter_notebook_id"
    }
}
