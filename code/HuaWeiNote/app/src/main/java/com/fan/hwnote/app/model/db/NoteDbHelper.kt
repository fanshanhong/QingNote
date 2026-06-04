package com.fan.hwnote.app.model.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite 表结构见 PRD §6.1 + §13.2。
 * v1 → v2（2026-06-04）：notes 加 category_id / deleted_at；新增 categories 表。
 */
class NoteDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_NOTES_V2)
        db.execSQL(SQL_INDEX_UPDATED)
        db.execSQL(SQL_INDEX_FAVORITE)
        db.execSQL(SQL_INDEX_CATEGORY)
        db.execSQL(SQL_INDEX_DELETED)
        db.execSQL(SQL_CREATE_CATEGORIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE notes ADD COLUMN category_id INTEGER")
            db.execSQL("ALTER TABLE notes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL(SQL_CREATE_CATEGORIES)
            db.execSQL(SQL_INDEX_CATEGORY)
            db.execSQL(SQL_INDEX_DELETED)
        }
    }

    companion object {
        const val DB_NAME = "hwnote.db"
        const val DB_VERSION = 2

        private const val SQL_CREATE_NOTES_V2 = """
            CREATE TABLE notes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL DEFAULT '',
              plain_text TEXT NOT NULL DEFAULT '',
              content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
              is_favorite INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              category_id INTEGER,
              deleted_at INTEGER NOT NULL DEFAULT 0
            )
        """

        private const val SQL_CREATE_CATEGORIES = """
            CREATE TABLE categories (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              color TEXT NOT NULL,
              order_index INTEGER NOT NULL DEFAULT 0
            )
        """

        private const val SQL_INDEX_UPDATED =
            "CREATE INDEX idx_notes_updated_at ON notes(updated_at DESC)"
        private const val SQL_INDEX_FAVORITE =
            "CREATE INDEX idx_notes_favorite ON notes(is_favorite)"
        private const val SQL_INDEX_CATEGORY =
            "CREATE INDEX idx_notes_category ON notes(category_id)"
        private const val SQL_INDEX_DELETED =
            "CREATE INDEX idx_notes_deleted ON notes(deleted_at)"
    }
}
