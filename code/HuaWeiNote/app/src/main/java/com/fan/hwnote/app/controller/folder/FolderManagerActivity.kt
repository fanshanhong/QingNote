package com.fan.hwnote.app.controller.folder

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Folder
import com.fan.hwnote.app.model.entity.Notebook
import com.fan.hwnote.app.view.folder.NewFolderBottomSheet
import com.fan.hwnote.app.view.folder.NewNotebookBottomSheet
import kotlinx.coroutines.launch

class FolderManagerActivity : AppCompatActivity(), FolderManagerAdapter.Callbacks {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: FolderManagerAdapter
    private val expanded = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_manager)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        recycler = findViewById(R.id.recycler_folder_manager)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = FolderManagerAdapter(emptyList(), this)
        recycler.adapter = adapter
        expanded += 1L
        rebind()
    }

    private fun rebind() {
        lifecycleScope.launch {
            val folders = FolderRepository.list()
            val rows = mutableListOf<FolderManagerRow>()
            folders.forEach { f ->
                rows += FolderManagerRow.FolderHead(f, expanded.contains(f.id))
                if (expanded.contains(f.id)) {
                    NotebookRepository.listByFolder(f.id).forEach { rows += FolderManagerRow.NotebookItem(it) }
                    rows += FolderManagerRow.CreateNotebook(f.id)
                }
            }
            adapter.rows = rows
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_folder_manager_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_new_folder) {
            NewFolderBottomSheet(this, onSaved = { rebind() }).show()
            return true
        }
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    override fun onFolderHeaderClicked(folder: Folder) {
        if (expanded.contains(folder.id)) expanded -= folder.id else expanded += folder.id
        rebind()
    }

    override fun onFolderHeaderOverflow(folder: Folder, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_folder_overflow, popup.menu)
        popup.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                R.id.action_rename -> {
                    if (folder.id == 1L) {
                        Toast.makeText(this, R.string.folder_default_undeletable, Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    NewFolderBottomSheet(this, editing = folder.id, initialName = folder.name, onSaved = { rebind() }).show()
                    true
                }
                R.id.action_delete -> {
                    if (folder.id == 1L) {
                        Toast.makeText(this, R.string.folder_default_undeletable, Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    AlertDialog.Builder(this)
                        .setTitle(R.string.folder_delete)
                        .setMessage(R.string.dialog_delete_folder_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.action_delete) { _, _ ->
                            lifecycleScope.launch { FolderRepository.softDelete(folder.id); rebind() }
                        }.show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onNotebookClicked(nb: Notebook) {
        // 普通点击不动；长按出菜单
    }

    override fun onNotebookOverflow(nb: Notebook, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_notebook_overflow, popup.menu)
        popup.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                R.id.action_rename, R.id.action_change_color -> {
                    NewNotebookBottomSheet(this, folderId = nb.folderId, editing = nb, onSaved = { rebind() }).show()
                    true
                }
                R.id.action_move_to -> {
                    if (nb.id == 1L) {
                        Toast.makeText(this, "默认笔记本不可移动", Toast.LENGTH_SHORT).show(); return@setOnMenuItemClickListener true
                    }
                    showFolderPicker { targetFolderId ->
                        lifecycleScope.launch { NotebookRepository.move(nb.id, targetFolderId); rebind() }
                    }
                    true
                }
                R.id.action_delete -> {
                    if (nb.id == 1L) {
                        Toast.makeText(this, "默认笔记本不可删除", Toast.LENGTH_SHORT).show(); return@setOnMenuItemClickListener true
                    }
                    AlertDialog.Builder(this)
                        .setTitle(R.string.notebook_delete)
                        .setMessage(R.string.dialog_delete_notebook_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.action_delete) { _, _ ->
                            lifecycleScope.launch { NotebookRepository.softDelete(nb.id); rebind() }
                        }.show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onCreateNotebookClicked(folderId: Long) {
        NewNotebookBottomSheet(this, folderId = folderId, editing = null, onSaved = { rebind() }).show()
    }

    private fun showFolderPicker(onPicked: (Long) -> Unit) {
        lifecycleScope.launch {
            val folders = FolderRepository.list()
            val names = folders.map { it.name }.toTypedArray()
            AlertDialog.Builder(this@FolderManagerActivity)
                .setTitle(R.string.notebook_move_to)
                .setItems(names) { _, idx -> onPicked(folders[idx].id) }
                .show()
        }
    }
}
