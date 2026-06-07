import 'package:sqflite/sqflite.dart';
import '../db/database_helper.dart';
import '../models/category.dart';

class CategoryRepository {
  final DatabaseHelper _dbHelper;

  CategoryRepository(this._dbHelper);

  Future<List<Category>> list() async {
    final db = await _dbHelper.database;
    final rows =
        await db.query('categories', orderBy: 'order_index ASC, id ASC');
    return rows.map(_rowToCategory).toList();
  }

  Future<Category?> get(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.query('categories', where: 'id = ?', whereArgs: [id]);
    if (rows.isEmpty) return null;
    return _rowToCategory(rows.first);
  }

  Future<int> insert(String name, String color) async {
    final db = await _dbHelper.database;
    final maxOrder = Sqflite.firstIntValue(
          await db.rawQuery(
              'SELECT COALESCE(MAX(order_index), -1) FROM categories'),
        ) ??
        -1;
    return db.insert('categories', {
      'name': name,
      'color': color,
      'order_index': maxOrder + 1,
    });
  }

  Future<void> update(int id, String name, String color) async {
    final db = await _dbHelper.database;
    await db.update('categories', {'name': name, 'color': color},
        where: 'id = ?', whereArgs: [id]);
  }

  Future<void> delete(int id) async {
    final db = await _dbHelper.database;
    await db.transaction((txn) async {
      await txn.rawUpdate(
          'UPDATE notes SET category_id = NULL WHERE category_id = ?', [id]);
      await txn.delete('categories', where: 'id = ?', whereArgs: [id]);
    });
  }

  Future<void> reorder(List<int> orderedIds) async {
    final db = await _dbHelper.database;
    await db.transaction((txn) async {
      for (var i = 0; i < orderedIds.length; i++) {
        await txn.update('categories', {'order_index': i},
            where: 'id = ?', whereArgs: [orderedIds[i]]);
      }
    });
  }

  Category _rowToCategory(Map<String, Object?> row) => Category(
        id: row['id'] as int,
        name: row['name'] as String,
        color: row['color'] as String,
        orderIndex: row['order_index'] as int? ?? 0,
      );
}
