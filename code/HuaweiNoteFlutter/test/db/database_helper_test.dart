// test/db/database_helper_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  group('DatabaseHelper', () {
    late DatabaseHelper helper;

    setUp(() async {
      helper = DatabaseHelper(inMemory: true);
      await helper.database;
    });

    tearDown(() async {
      await helper.close();
    });

    test('creates all tables', () async {
      final db = await helper.database;
      final tables = await db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY name",
      );
      final names = tables.map((r) => r['name'] as String).toList();
      expect(names, containsAll(['notes', 'folders', 'notebooks', 'categories', 'todos']));
    });

    test('seeds default folder and notebook', () async {
      final db = await helper.database;
      final folders = await db.query('folders', where: 'id = 1');
      expect(folders.length, 1);
      expect(folders[0]['is_default'], 1);

      final notebooks = await db.query('notebooks', where: 'id = 1');
      expect(notebooks.length, 1);
      expect(notebooks[0]['is_default'], 1);
      expect(notebooks[0]['folder_id'], 1);
    });

    test('notes table has all expected columns', () async {
      final db = await helper.database;
      final now = DateTime.now().millisecondsSinceEpoch;
      final id = await db.insert('notes', {
        'title': 'Test',
        'plain_text': 'test',
        'content_json': '{}',
        'is_favorite': 0,
        'created_at': now,
        'updated_at': now,
        'deleted_at': 0,
        'notebook_id': 1,
        'background': 'plain',
      });
      expect(id, greaterThan(0));
    });

    test('todos table has all expected columns', () async {
      final db = await helper.database;
      final now = DateTime.now().millisecondsSinceEpoch;
      final id = await db.insert('todos', {
        'title': 'Test todo',
        'memo': '',
        'is_completed': 0,
        'is_important': 0,
        'remind_at': 0,
        'repeat_type': 0,
        'deleted_at': 0,
        'created_at': now,
        'updated_at': now,
      });
      expect(id, greaterThan(0));
    });
  });
}
