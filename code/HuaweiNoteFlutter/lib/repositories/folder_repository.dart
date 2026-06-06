import 'package:sqflite/sqflite.dart';
import '../db/database_helper.dart';
import '../models/folder.dart';

class FolderRepository {
  final DatabaseHelper _dbHelper;

  FolderRepository(this._dbHelper);

  Future<List<Folder>> list() async {
    final db = await _dbHelper.database;
    final rows = await db.rawQuery(
      'SELECT id, name, order_index, is_default, deleted_at FROM folders WHERE deleted_at = 0 ORDER BY is_default DESC, order_index ASC, id ASC',
    );
    return rows.map(_rowToFolder).toList();
  }

  Future<Folder?> get(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.rawQuery(
      'SELECT id, name, order_index, is_default, deleted_at FROM folders WHERE id = ? AND deleted_at = 0',
      [id],
    );
    if (rows.isEmpty) return null;
    return _rowToFolder(rows.first);
  }

  Future<int> insert(String name) async {
    final db = await _dbHelper.database;
    final nextIndex = await _nextOrderIndex(db);
    return db.insert('folders', {
      'name': name,
      'order_index': nextIndex,
      'is_default': 0,
      'deleted_at': 0,
    });
  }

  Future<void> rename(int id, String name) async {
    assert(id != 1, '默认文件夹不可改名');
    final db = await _dbHelper.database;
    await db.update('folders', {'name': name},
        where: 'id = ?', whereArgs: [id]);
  }

  Future<void> softDelete(int id) async {
    assert(id != 1, '默认文件夹不可删除');
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.transaction((txn) async {
      await txn.rawUpdate(
        'UPDATE notes SET deleted_at = ? WHERE deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)',
        [now, id],
      );
      await txn.update('notebooks', {'deleted_at': now},
          where: 'folder_id = ? AND deleted_at = 0', whereArgs: [id]);
      await txn.update('folders', {'deleted_at': now},
          where: 'id = ?', whereArgs: [id]);
    });
  }

  Future<void> reorder(List<int> orderedIds) async {
    final db = await _dbHelper.database;
    await db.transaction((txn) async {
      for (var i = 0; i < orderedIds.length; i++) {
        final id = orderedIds[i];
        if (id == 1) continue;
        await txn.update('folders', {'order_index': i},
            where: 'id = ?', whereArgs: [id]);
      }
    });
  }

  Future<int> _nextOrderIndex(Database db) async {
    final result = await db.rawQuery(
      'SELECT IFNULL(MAX(order_index), -1) + 1 FROM folders WHERE deleted_at = 0',
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Folder _rowToFolder(Map<String, Object?> row) => Folder(
        id: row['id'] as int,
        name: row['name'] as String,
        orderIndex: row['order_index'] as int,
        isDefault: (row['is_default'] as int) == 1,
        deletedAt: row['deleted_at'] as int? ?? 0,
      );
}
