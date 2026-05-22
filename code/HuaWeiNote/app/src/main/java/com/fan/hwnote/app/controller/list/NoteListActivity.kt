package com.fan.hwnote.app.controller.list

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.entity.NoteContent
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
                Toast.makeText(
                    this, R.string.toast_open_editor_placeholder, Toast.LENGTH_SHORT
                ).show()
                // M4：替换为 startActivity(NoteEditorActivity.newIntent(this, note.id))
            },
            onLongClick = { _, _ -> /* Task 10 接 */ },
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fab.setOnClickListener {
            // M3 占位：直接落一条空笔记，验证 列表→数据→刷新 闭环
            // M4：替换为 startActivity(NoteEditorActivity.newIntent(this, noteId = -1L))
            lifecycleScope.launch {
                val now = System.currentTimeMillis()
                NoteRepository.save(
                    Note(
                        id = 0L,
                        title = "",
                        plainText = "",
                        isFavorite = false,
                        createdAt = now,
                        updatedAt = now,
                        content = NoteContent.empty(),
                    )
                )
                reload()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
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
}
