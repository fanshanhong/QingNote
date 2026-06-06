// test/repositories/category_repository_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';
import 'package:hwnote/repositories/category_repository.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late DatabaseHelper dbHelper;
  late CategoryRepository repo;

  setUp(() async {
    dbHelper = DatabaseHelper(inMemory: true);
    await dbHelper.database;
    repo = CategoryRepository(dbHelper);
  });

  tearDown(() async => await dbHelper.close());

  group('CategoryRepository', () {
    test('list is initially empty', () async {
      final list = await repo.list();
      expect(list, isEmpty);
    });

    test('insert adds category', () async {
      final id = await repo.insert('Work', '#FDD835');
      expect(id, greaterThan(0));
      final list = await repo.list();
      expect(list.length, 1);
      expect(list[0].name, 'Work');
      expect(list[0].color, '#FDD835');
    });

    test('get returns category by id', () async {
      final id = await repo.insert('Personal', '#FF0000');
      final cat = await repo.get(id);
      expect(cat, isNotNull);
      expect(cat!.name, 'Personal');
    });

    test('update changes name and color', () async {
      final id = await repo.insert('Old', '#000');
      await repo.update(id, 'New', '#FFF');
      final cat = await repo.get(id);
      expect(cat!.name, 'New');
      expect(cat.color, '#FFF');
    });

    test('delete removes category', () async {
      final id = await repo.insert('Gone', '#000');
      await repo.delete(id);
      expect(await repo.get(id), isNull);
    });

    test('order_index auto-increments', () async {
      await repo.insert('A', '#000');
      await repo.insert('B', '#000');
      final list = await repo.list();
      expect(list[0].orderIndex, 0);
      expect(list[1].orderIndex, 1);
    });
  });
}
