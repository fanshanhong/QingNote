import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

class DatabaseHelper {
  static const _dbName = 'hwnote.db';
  static const _dbVersion = 5;

  final bool inMemory;
  Database? _database;

  DatabaseHelper({this.inMemory = false});

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _open();
    return _database!;
  }

  Future<void> close() async {
    await _database?.close();
    _database = null;
  }

  Future<Database> _open() async {
    final path = inMemory
        ? inMemoryDatabasePath
        : join(await getDatabasesPath(), _dbName);
    return openDatabase(
      path,
      version: _dbVersion,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
  }

  Future<void> _onCreate(Database db, int version) async {
    await db.execute(_sqlCreateNotes);
    await db.execute(_sqlIndexUpdated);
    await db.execute(_sqlIndexFavorite);
    await db.execute(_sqlIndexCategory);
    await db.execute(_sqlIndexDeleted);
    await db.execute(_sqlIndexNotebook);
    await db.execute(_sqlCreateCategories);
    await _applyV3Tables(db);
    await _seedDefaults(db, allNotesExist: false);
    await db.execute(_sqlCreateTodos);
    await db.execute(_sqlIndexTodosRemind);
    await db.execute(_sqlIndexTodosDeleted);
  }

  Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    if (oldVersion < 2) {
      await db.execute('ALTER TABLE notes ADD COLUMN category_id INTEGER');
      await db.execute(
        'ALTER TABLE notes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0',
      );
      await db.execute(_sqlCreateCategories);
      await db.execute(_sqlIndexCategory);
      await db.execute(_sqlIndexDeleted);
    }
    if (oldVersion < 3) {
      await db.transaction((txn) async {
        await _applyV3Tables(txn);
        await txn.execute('ALTER TABLE notes ADD COLUMN notebook_id INTEGER');
        await txn.execute(_sqlIndexNotebook);
        await _seedDefaults(txn, allNotesExist: true);
      });
    }
    if (oldVersion < 4) {
      await db.execute(
        "ALTER TABLE notes ADD COLUMN background TEXT NOT NULL DEFAULT 'plain'",
      );
    }
    if (oldVersion < 5) {
      await db.execute(_sqlCreateTodos);
      await db.execute(_sqlIndexTodosRemind);
      await db.execute(_sqlIndexTodosDeleted);
    }
  }

  Future<void> _applyV3Tables(DatabaseExecutor db) async {
    await db.execute(_sqlCreateFolders);
    await db.execute(_sqlIndexFolderDeleted);
    await db.execute(_sqlCreateNotebooks);
    await db.execute(_sqlIndexNotebookFolder);
    await db.execute(_sqlIndexNotebookDeleted);
  }

  Future<void> _seedDefaults(
    DatabaseExecutor db, {
    required bool allNotesExist,
  }) async {
    await db.insert('folders', {
      'id': 1,
      'name': '默认',
      'order_index': 0,
      'is_default': 1,
      'deleted_at': 0,
    });
    await db.insert('notebooks', {
      'id': 1,
      'name': '默认',
      'folder_id': 1,
      'color': '#9E9E9E',
      'order_index': 0,
      'is_default': 1,
      'deleted_at': 0,
    });
    if (allNotesExist) {
      await db.execute('UPDATE notes SET notebook_id = 1');
    }
  }

  static const _sqlCreateNotes = '''
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
  ''';

  static const _sqlCreateCategories = '''
    CREATE TABLE categories (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      color TEXT NOT NULL,
      order_index INTEGER NOT NULL DEFAULT 0
    )
  ''';

  static const _sqlCreateFolders = '''
    CREATE TABLE folders (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      order_index INTEGER NOT NULL DEFAULT 0,
      is_default INTEGER NOT NULL DEFAULT 0,
      deleted_at INTEGER NOT NULL DEFAULT 0
    )
  ''';

  static const _sqlCreateNotebooks = '''
    CREATE TABLE notebooks (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      folder_id INTEGER NOT NULL,
      color TEXT NOT NULL DEFAULT '#9E9E9E',
      order_index INTEGER NOT NULL DEFAULT 0,
      is_default INTEGER NOT NULL DEFAULT 0,
      deleted_at INTEGER NOT NULL DEFAULT 0
    )
  ''';

  static const _sqlCreateTodos = '''
    CREATE TABLE todos (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      title TEXT NOT NULL DEFAULT '',
      memo TEXT NOT NULL DEFAULT '',
      is_completed INTEGER NOT NULL DEFAULT 0,
      is_important INTEGER NOT NULL DEFAULT 0,
      remind_at INTEGER NOT NULL DEFAULT 0,
      repeat_type INTEGER NOT NULL DEFAULT 0,
      folder_id INTEGER,
      deleted_at INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    )
  ''';

  static const _sqlIndexUpdated =
      'CREATE INDEX idx_notes_updated_at ON notes(updated_at DESC)';
  static const _sqlIndexFavorite =
      'CREATE INDEX idx_notes_favorite ON notes(is_favorite)';
  static const _sqlIndexCategory =
      'CREATE INDEX idx_notes_category ON notes(category_id)';
  static const _sqlIndexDeleted =
      'CREATE INDEX idx_notes_deleted ON notes(deleted_at)';
  static const _sqlIndexNotebook =
      'CREATE INDEX idx_notes_notebook ON notes(notebook_id)';
  static const _sqlIndexFolderDeleted =
      'CREATE INDEX idx_folders_deleted ON folders(deleted_at)';
  static const _sqlIndexNotebookFolder =
      'CREATE INDEX idx_notebooks_folder ON notebooks(folder_id)';
  static const _sqlIndexNotebookDeleted =
      'CREATE INDEX idx_notebooks_deleted ON notebooks(deleted_at)';
  static const _sqlIndexTodosRemind =
      'CREATE INDEX idx_todos_remind_at ON todos(remind_at)';
  static const _sqlIndexTodosDeleted =
      'CREATE INDEX idx_todos_deleted_at ON todos(deleted_at)';
}
