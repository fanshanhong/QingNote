import 'package:sqflite/sqflite.dart';
import '../db/database_helper.dart';
import '../models/todo.dart';

class TodoRepository {
  final DatabaseHelper _dbHelper;

  TodoRepository(this._dbHelper);

  Future<int> insert(Todo todo) async {
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final values = _toMap(todo);
    values['created_at'] = todo.createdAt > 0 ? todo.createdAt : now;
    values['updated_at'] = now;
    return db.insert('todos', values);
  }

  Future<void> update(Todo todo) async {
    final db = await _dbHelper.database;
    final values = _toMap(todo);
    values['updated_at'] = DateTime.now().millisecondsSinceEpoch;
    await db.update('todos', values, where: 'id = ?', whereArgs: [todo.id]);
  }

  Future<Todo?> getById(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.query('todos', where: 'id = ?', whereArgs: [id]);
    if (rows.isEmpty) return null;
    return _rowToTodo(rows.first);
  }

  Future<List<Todo>> list({
    int? folderId,
    bool includeDeleted = false,
    bool hideCompleted = false,
  }) async {
    final db = await _dbHelper.database;
    final where = <String>[];
    final args = <Object>[];
    where.add(includeDeleted ? 'deleted_at > 0' : 'deleted_at = 0');
    if (folderId != null) { where.add('folder_id = ?'); args.add(folderId); }
    if (hideCompleted) { where.add('is_completed = 0'); }
    final rows = await db.query('todos',
      where: where.join(' AND '), whereArgs: args,
      orderBy: 'remind_at ASC, created_at DESC',
    );
    return rows.map(_rowToTodo).toList();
  }

  Future<List<Todo>> listUncategorized({bool hideCompleted = false}) async {
    final db = await _dbHelper.database;
    final where = <String>['deleted_at = 0', 'folder_id IS NULL'];
    if (hideCompleted) where.add('is_completed = 0');
    final rows = await db.query('todos',
      where: where.join(' AND '), orderBy: 'remind_at ASC, created_at DESC',
    );
    return rows.map(_rowToTodo).toList();
  }

  Future<int> count({int? folderId, bool includeDeleted = false}) async {
    final db = await _dbHelper.database;
    final where = <String>[];
    final args = <Object>[];
    where.add(includeDeleted ? 'deleted_at > 0' : 'deleted_at = 0');
    if (folderId != null) { where.add('folder_id = ?'); args.add(folderId); }
    final result = await db.rawQuery(
      'SELECT COUNT(*) FROM todos WHERE ${where.join(' AND ')}', args,
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<int> countUncategorized() async {
    final db = await _dbHelper.database;
    final result = await db.rawQuery(
      'SELECT COUNT(*) FROM todos WHERE deleted_at = 0 AND folder_id IS NULL',
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<void> softDelete(int id) async {
    final db = await _dbHelper.database;
    await db.update('todos', {'deleted_at': DateTime.now().millisecondsSinceEpoch},
      where: 'id = ?', whereArgs: [id]);
  }

  Future<void> softDeleteBatch(List<int> ids) async {
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.transaction((txn) async {
      for (final id in ids) {
        await txn.update('todos', {'deleted_at': now}, where: 'id = ?', whereArgs: [id]);
      }
    });
  }

  Future<void> restore(int id) async {
    final db = await _dbHelper.database;
    await db.update('todos', {'deleted_at': 0}, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> deletePermanently(int id) async {
    final db = await _dbHelper.database;
    await db.delete('todos', where: 'id = ?', whereArgs: [id]);
  }

  Future<void> completeTodo(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.query('todos', where: 'id = ?', whereArgs: [id]);
    if (rows.isEmpty) return;
    final todo = _rowToTodo(rows.first);
    if (todo.repeatType != RepeatType.none && todo.remindAt > 0) {
      final nextRemind = advanceRemindAt(todo.remindAt, todo.repeatType);
      await db.update('todos', {
        'remind_at': nextRemind,
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      }, where: 'id = ?', whereArgs: [id]);
    } else {
      await db.update('todos', {
        'is_completed': 1,
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      }, where: 'id = ?', whereArgs: [id]);
    }
  }

  Future<void> uncompleteTodo(int id) async {
    final db = await _dbHelper.database;
    await db.update('todos', {
      'is_completed': 0,
      'updated_at': DateTime.now().millisecondsSinceEpoch,
    }, where: 'id = ?', whereArgs: [id]);
  }

  Future<List<Todo>> listPendingAlarms() async {
    final db = await _dbHelper.database;
    final rows = await db.query('todos',
      where: 'remind_at > 0 AND is_completed = 0 AND deleted_at = 0',
      orderBy: 'remind_at ASC',
    );
    return rows.map(_rowToTodo).toList();
  }

  Future<int> purgeExpired({
    int? now,
    int ttlMs = 30 * 24 * 60 * 60 * 1000,
  }) async {
    final db = await _dbHelper.database;
    final cutoff = (now ?? DateTime.now().millisecondsSinceEpoch) - ttlMs;
    return db.delete('todos',
      where: 'deleted_at > 0 AND deleted_at < ?', whereArgs: [cutoff]);
  }

  static int advanceRemindAt(int remindAt, RepeatType repeatType) {
    if (repeatType == RepeatType.none) return remindAt;
    final now = DateTime.now().millisecondsSinceEpoch;
    var dt = DateTime.fromMillisecondsSinceEpoch(remindAt);
    do {
      dt = switch (repeatType) {
        RepeatType.daily => dt.add(const Duration(days: 1)),
        RepeatType.weekly => dt.add(const Duration(days: 7)),
        RepeatType.monthly => _addMonth(dt, 1),
        RepeatType.yearly => _addMonth(dt, 12),
        RepeatType.none => dt,
      };
    } while (dt.millisecondsSinceEpoch <= now);
    return dt.millisecondsSinceEpoch;
  }

  static DateTime _addMonth(DateTime dt, int months) {
    final targetYear = dt.year + (dt.month + months - 1) ~/ 12;
    final targetMonth = (dt.month + months - 1) % 12 + 1;
    final lastDay = DateTime(targetYear, targetMonth + 1, 0).day;
    final day = dt.day > lastDay ? lastDay : dt.day;
    return DateTime(targetYear, targetMonth, day, dt.hour, dt.minute, dt.second);
  }

  Map<String, Object?> _toMap(Todo todo) => {
    'title': todo.title,
    'memo': todo.memo,
    'is_completed': todo.isCompleted ? 1 : 0,
    'is_important': todo.isImportant ? 1 : 0,
    'remind_at': todo.remindAt,
    'repeat_type': todo.repeatType.value,
    'folder_id': todo.folderId,
    'deleted_at': todo.deletedAt,
  };

  Todo _rowToTodo(Map<String, Object?> row) => Todo(
    id: row['id'] as int,
    title: row['title'] as String? ?? '',
    memo: row['memo'] as String? ?? '',
    isCompleted: (row['is_completed'] as int) == 1,
    isImportant: (row['is_important'] as int) == 1,
    remindAt: row['remind_at'] as int? ?? 0,
    repeatType: RepeatType.fromValue(row['repeat_type'] as int? ?? 0),
    folderId: row['folder_id'] as int?,
    deletedAt: row['deleted_at'] as int? ?? 0,
    createdAt: row['created_at'] as int,
    updatedAt: row['updated_at'] as int,
  );
}
