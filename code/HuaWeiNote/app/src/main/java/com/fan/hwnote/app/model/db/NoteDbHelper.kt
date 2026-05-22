package com.fan.hwnote.app.model.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite 表结构见 PRD §6.1。
 * MVP v1：单一版本，没有 onUpgrade 路径。
 */
class NoteDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_NOTES)
        db.execSQL(SQL_INDEX_UPDATED)
        db.execSQL(SQL_INDEX_FAVORITE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // MVP v1：尚无升级路径。后续如要加列，在此 ALTER TABLE。
    }

    companion object {
        const val DB_NAME = "hwnote.db"
        const val DB_VERSION = 1

        private const val SQL_CREATE_NOTES = """
            CREATE TABLE notes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL DEFAULT '',
              plain_text TEXT NOT NULL DEFAULT '',
              content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
              is_favorite INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            )
        """

        private const val SQL_INDEX_UPDATED =
            "CREATE INDEX idx_notes_updated_at ON notes(updated_at DESC)"
        private const val SQL_INDEX_FAVORITE =
            "CREATE INDEX idx_notes_favorite ON notes(is_favorite)"
    }
}
