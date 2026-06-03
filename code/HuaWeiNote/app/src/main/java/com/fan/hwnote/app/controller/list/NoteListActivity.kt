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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.controller.editor.NoteEditorActivity
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.entity.Note
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class NoteListActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var searchInput: EditText
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: NoteListAdapter

    private var sortBy: NoteRepository.SortBy = NoteRepository.SortBy.UPDATED_DESC
    private var currentQuery: String? = null

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        toolbar = findViewById(R.id.toolbar)
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
            val list = NoteRepository.list(sortBy, currentQuery)
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
        popup.menuInflater.inflate(R.menu.menu_note_card_long_press, popup.menu)
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
                    AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_delete_title)
                        .setMessage(R.string.dialog_delete_message)
                        .setPositiveButton(R.string.action_ok) { _, _ ->
                            lifecycleScope.launch {
                                NoteRepository.delete(note.id)
                                reload()
                            }
                        }
                        .setNegativeButton(R.string.action_cancel, null)
                        .show()
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
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_sort_picker, null)
        val group = view.findViewById<android.widget.RadioGroup>(R.id.sort_radio_group)
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

    companion object {
        private const val PREFS = "hwnote_settings"
        private const val KEY_SORT = "sort_by"
    }
}
