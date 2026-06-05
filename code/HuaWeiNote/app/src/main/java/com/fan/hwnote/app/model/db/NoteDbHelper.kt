package com.fan.hwnote.app.model.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite 表结构见 PRD §6.1 + §13.2。
 * v1 → v2（2026-06-04）：notes 加 category_id / deleted_at；新增 categories 表。
 * v2 → v3（2026-06-04）：新增 folders / notebooks 表，notes 加 notebook_id 列，
 *                       预置默认文件夹/笔记本（id=1, is_default=1），老 notes.notebook_id 全填 1。
 *                       老 categories 表 + notes.category_id 列保留作"数据墓地"，业务层不再读写。
 */
class NoteDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_NOTES)
        db.execSQL(SQL_INDEX_UPDATED)
        db.execSQL(SQL_INDEX_FAVORITE)
        db.execSQL(SQL_INDEX_CATEGORY)
        db.execSQL(SQL_INDEX_DELETED)
        db.execSQL(SQL_INDEX_NOTEBOOK)
        db.execSQL(SQL_CREATE_CATEGORIES)
        applyV3Tables(db)
        seedDefaults(db, allNotesAlreadyExist = false)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE notes ADD COLUMN category_id INTEGER")
            db.execSQL("ALTER TABLE notes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL(SQL_CREATE_CATEGORIES)
            db.execSQL(SQL_INDEX_CATEGORY)
            db.execSQL(SQL_INDEX_DELETED)
        }
        if (oldVersion < 3) {
            db.beginTransaction()
            try {
                applyV3Tables(db)
                db.execSQL("ALTER TABLE notes ADD COLUMN notebook_id INTEGER")
                db.execSQL(SQL_INDEX_NOTEBOOK)
                seedDefaults(db, allNotesAlreadyExist = true)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE notes ADD COLUMN background TEXT NOT NULL DEFAULT 'plain'")
        }
    }

    private fun applyV3Tables(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_FOLDERS)
        db.execSQL(SQL_INDEX_FOLDER_DELETED)
        db.execSQL(SQL_CREATE_NOTEBOOKS)
        db.execSQL(SQL_INDEX_NOTEBOOK_FOLDER)
        db.execSQL(SQL_INDEX_NOTEBOOK_DELETED)
    }

    private fun seedDefaults(db: SQLiteDatabase, allNotesAlreadyExist: Boolean) {
        db.execSQL(
            "INSERT INTO folders(id, name, order_index, is_default, deleted_at) VALUES(1, ?, 0, 1, 0)",
            arrayOf<Any>("默认"),
        )
        db.execSQL(
            "INSERT INTO notebooks(id, name, folder_id, color, order_index, is_default, deleted_at) VALUES(1, ?, 1, ?, 0, 1, 0)",
            arrayOf<Any>("默认", "#9E9E9E"),
        )
        if (allNotesAlreadyExist) {
            db.execSQL("UPDATE notes SET notebook_id = 1")
        }
    }

    companion object {
        const val DB_NAME = "hwnote.db"
        const val DB_VERSION = 4

        // v4 全新建表 —— notes 自带 notebook_id + background 列
        private const val SQL_CREATE_NOTES = """
            CREATE TABLE notes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL DEFAULT '',
              plain_text TEXT NOT NULL DEFAULT '',
              content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
              is_favorite INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              category_id INTEGER,
              deleted_at INTEGER NOT NULL DEFAULT 0,
              notebook_id INTEGER,
              background TEXT NOT NULL DEFAULT 'plain'
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

        private const val SQL_CREATE_FOLDERS = """
            CREATE TABLE folders (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              order_index INTEGER NOT NULL DEFAULT 0,
              is_default INTEGER NOT NULL DEFAULT 0,
              deleted_at INTEGER NOT NULL DEFAULT 0
            )
        """

        private const val SQL_CREATE_NOTEBOOKS = """
            CREATE TABLE notebooks (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              folder_id INTEGER NOT NULL,
              color TEXT NOT NULL DEFAULT '#9E9E9E',
              order_index INTEGER NOT NULL DEFAULT 0,
              is_default INTEGER NOT NULL DEFAULT 0,
              deleted_at INTEGER NOT NULL DEFAULT 0
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
        private const val SQL_INDEX_NOTEBOOK =
            "CREATE INDEX idx_notes_notebook ON notes(notebook_id)"
        private const val SQL_INDEX_FOLDER_DELETED =
            "CREATE INDEX idx_folders_deleted ON folders(deleted_at)"
        private const val SQL_INDEX_NOTEBOOK_FOLDER =
            "CREATE INDEX idx_notebooks_folder ON notebooks(folder_id)"
        private const val SQL_INDEX_NOTEBOOK_DELETED =
            "CREATE INDEX idx_notebooks_deleted ON notebooks(deleted_at)"
    }
}
