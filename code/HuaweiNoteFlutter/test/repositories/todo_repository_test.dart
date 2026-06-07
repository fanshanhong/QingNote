// test/repositories/todo_repository_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';
import 'package:hwnote/repositories/todo_repository.dart';
import 'package:hwnote/models/todo.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late DatabaseHelper dbHelper;
  late TodoRepository repo;

  setUp(() async {
    dbHelper = DatabaseHelper(inMemory: true);
    await dbHelper.database;
    repo = TodoRepository(dbHelper);
  });

  tearDown(() async => await dbHelper.close());

  group('TodoRepository', () {
    test('insert and getById', () async {
      final todo = Todo.newTodo().copyWith(title: 'Buy milk');
      final id = await repo.insert(todo);
      expect(id, greaterThan(0));
      final loaded = await repo.getById(id);
      expect(loaded, isNotNull);
      expect(loaded!.title, 'Buy milk');
    });

    test('update changes fields', () async {
      final id = await repo.insert(Todo.newTodo().copyWith(title: 'Old'));
      final loaded = (await repo.getById(id))!;
      await repo.update(loaded.copyWith(title: 'New'));
      final updated = await repo.getById(id);
      expect(updated!.title, 'New');
    });

    test('list returns non-deleted todos', () async {
      await repo.insert(Todo.newTodo().copyWith(title: 'A'));
      await repo.insert(Todo.newTodo().copyWith(title: 'B'));
      final list = await repo.list();
      expect(list.length, 2);
    });

    test('softDelete marks as deleted', () async {
      final id = await repo.insert(Todo.newTodo().copyWith(title: 'Del'));
      await repo.softDelete(id);
      final list = await repo.list();
      expect(list, isEmpty);
    });

    test('restore brings back deleted todo', () async {
      final id = await repo.insert(Todo.newTodo().copyWith(title: 'Restore'));
      await repo.softDelete(id);
      await repo.restore(id);
      final list = await repo.list();
      expect(list.length, 1);
    });

    test('completeTodo sets is_completed for non-repeating', () async {
      final id = await repo.insert(Todo.newTodo().copyWith(title: 'Done'));
      await repo.completeTodo(id);
      final loaded = await repo.getById(id);
      expect(loaded!.isCompleted, true);
    });

    test('completeTodo advances remindAt for repeating', () async {
      final now = DateTime.now().millisecondsSinceEpoch;
      final id = await repo.insert(Todo.newTodo().copyWith(
        title: 'Repeat',
        remindAt: now - 1000,
        repeatType: RepeatType.daily,
      ));
      await repo.completeTodo(id);
      final loaded = await repo.getById(id);
      expect(loaded!.isCompleted, false);
      expect(loaded.remindAt, greaterThan(now));
    });

    test('count returns correct number', () async {
      await repo.insert(Todo.newTodo().copyWith(title: 'A'));
      await repo.insert(Todo.newTodo().copyWith(title: 'B'));
      expect(await repo.count(), 2);
    });

    test('advanceRemindAt advances past now', () {
      final past = DateTime.now().millisecondsSinceEpoch - 86400000 * 3;
      final result = TodoRepository.advanceRemindAt(past, RepeatType.daily);
      expect(result, greaterThan(DateTime.now().millisecondsSinceEpoch));
    });

    test('purgeExpired removes old soft-deleted todos', () async {
      final id = await repo.insert(Todo.newTodo().copyWith(title: 'Old'));
      await repo.softDelete(id);
      final db = await dbHelper.database;
      final longAgo =
          DateTime.now().millisecondsSinceEpoch - 31 * 24 * 60 * 60 * 1000;
      await db.update('todos', {'deleted_at': longAgo},
          where: 'id = ?', whereArgs: [id]);
      final purged = await repo.purgeExpired();
      expect(purged, 1);
      expect(await repo.getById(id), isNull);
    });
  });
}
