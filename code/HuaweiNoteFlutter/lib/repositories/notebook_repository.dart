import 'package:sqflite/sqflite.dart';
import '../db/database_helper.dart';
import '../models/notebook.dart';

class NotebookRepository {
  final DatabaseHelper _dbHelper;

  NotebookRepository(this._dbHelper);

  Future<List<Notebook>> listByFolder(int folderId) async {
    final db = await _dbHelper.database;
    final rows = await db.rawQuery(
      'SELECT id, folder_id, name, color, order_index, is_default, deleted_at FROM notebooks WHERE folder_id = ? AND deleted_at = 0 ORDER BY is_default DESC, order_index ASC, id ASC',
      [folderId],
    );
    return rows.map(_rowToNotebook).toList();
  }

  Future<Notebook?> get(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.rawQuery(
      'SELECT id, folder_id, name, color, order_index, is_default, deleted_at FROM notebooks WHERE id = ? AND deleted_at = 0',
      [id],
    );
    if (rows.isEmpty) return null;
    return _rowToNotebook(rows.first);
  }

  Future<int> insert(int folderId, String name, String color) async {
    final db = await _dbHelper.database;
    final nextIndex = await _nextOrderIndex(db, folderId);
    return db.insert('notebooks', {
      'folder_id': folderId, 'name': name, 'color': color,
      'order_index': nextIndex, 'is_default': 0, 'deleted_at': 0,
    });
  }

  Future<void> rename(int id, String name) async {
    final db = await _dbHelper.database;
    await db.update('notebooks', {'name': name}, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> updateColor(int id, String color) async {
    final db = await _dbHelper.database;
    await db.update('notebooks', {'color': color}, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> softDelete(int id) async {
    assert(id != 1, '默认笔记本不可删除');
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.transaction((txn) async {
      await txn.rawUpdate(
        'UPDATE notes SET deleted_at = ? WHERE deleted_at = 0 AND notebook_id = ?',
        [now, id],
      );
      await txn.update('notebooks', {'deleted_at': now}, where: 'id = ?', whereArgs: [id]);
    });
  }

  Future<void> move(int id, int targetFolderId) async {
    assert(id != 1, '默认笔记本不可跨文件夹移动');
    final db = await _dbHelper.database;
    final nextIndex = await _nextOrderIndex(db, targetFolderId);
    await db.update('notebooks', {
      'folder_id': targetFolderId, 'order_index': nextIndex,
    }, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> reorderInFolder(int folderId, List<int> orderedIds) async {
    final db = await _dbHelper.database;
    await db.transaction((txn) async {
      for (var i = 0; i < orderedIds.length; i++) {
        final id = orderedIds[i];
        if (id == 1 && folderId == 1) continue;
        await txn.update('notebooks', {'order_index': i},
          where: 'id = ? AND folder_id = ?', whereArgs: [id, folderId]);
      }
    });
  }

  Future<int> _nextOrderIndex(Database db, int folderId) async {
    final result = await db.rawQuery(
      'SELECT IFNULL(MAX(order_index), -1) + 1 FROM notebooks WHERE deleted_at = 0 AND folder_id = ?',
      [folderId],
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Notebook _rowToNotebook(Map<String, Object?> row) => Notebook(
    id: row['id'] as int,
    folderId: row['folder_id'] as int,
    name: row['name'] as String,
    color: row['color'] as String,
    orderIndex: row['order_index'] as int,
    isDefault: (row['is_default'] as int) == 1,
    deletedAt: row['deleted_at'] as int? ?? 0,
  );
}
