package com.fan.hwnote.app.controller.list

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fan.hwnote.app.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

class NoteListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        findViewById<FloatingActionButton>(R.id.fab_new_note).setOnClickListener {
            Toast.makeText(this, R.string.toast_new_note_placeholder, Toast.LENGTH_SHORT).show()
        }
    }
}
