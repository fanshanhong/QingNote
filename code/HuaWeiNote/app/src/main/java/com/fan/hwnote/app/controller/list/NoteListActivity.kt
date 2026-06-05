package com.fan.hwnote.app.controller.list

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fan.hwnote.app.R

class NoteListActivity : AppCompatActivity() {

    private lateinit var navNotesIcon: ImageView
    private lateinit var navNotesLabel: TextView
    private lateinit var navTodoIcon: ImageView
    private lateinit var navTodoLabel: TextView

    private var activeTab: Int = TAB_NOTES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        navNotesIcon = findViewById(R.id.nav_notes_icon)
        navNotesLabel = findViewById(R.id.nav_notes_label)
        navTodoIcon = findViewById(R.id.nav_todo_icon)
        navTodoLabel = findViewById(R.id.nav_todo_label)

        findViewById<android.view.View>(R.id.nav_notes).setOnClickListener { switchTab(TAB_NOTES) }
        findViewById<android.view.View>(R.id.nav_todo).setOnClickListener { switchTab(TAB_TODO) }

        if (savedInstanceState != null) {
            activeTab = savedInstanceState.getInt(KEY_ACTIVE_TAB, loadActiveTab())
        } else {
            activeTab = loadActiveTab()
        }
        switchTab(activeTab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_TAB, activeTab)
    }

    private fun switchTab(tab: Int) {
        if (activeTab == tab && supportFragmentManager.findFragmentById(R.id.fragment_container) != null) {
            return
        }
        activeTab = tab
        saveActiveTab(tab)

        val fragment: Fragment = when (tab) {
            TAB_TODO -> TodoListFragment()
            else -> NoteListFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()

        updateNavHighlight(tab)
    }

    private fun updateNavHighlight(tab: Int) {
        val primary = ContextCompat.getColor(this, R.color.primary)
        val hint = ContextCompat.getColor(this, R.color.text_hint)

        val notesActive = tab == TAB_NOTES
        navNotesIcon.setColorFilter(if (notesActive) primary else hint)
        navNotesLabel.setTextColor(if (notesActive) primary else hint)

        val todoActive = tab == TAB_TODO
        navTodoIcon.setColorFilter(if (todoActive) primary else hint)
        navTodoLabel.setTextColor(if (todoActive) primary else hint)
    }

    private fun loadActiveTab(): Int {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        return prefs.getInt(KEY_ACTIVE_TAB, TAB_NOTES)
    }

    private fun saveActiveTab(tab: Int) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_ACTIVE_TAB, tab).apply()
    }

    fun setBottomNavVisible(visible: Boolean) {
        findViewById<View>(R.id.bottom_nav).visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    companion object {
        private const val PREFS = "hwnote_settings"
        private const val KEY_ACTIVE_TAB = "active_tab"
        const val TAB_NOTES = 0
        const val TAB_TODO = 1
    }
}
