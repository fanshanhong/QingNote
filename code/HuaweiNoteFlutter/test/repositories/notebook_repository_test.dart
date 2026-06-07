// test/repositories/notebook_repository_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';
import 'package:hwnote/repositories/notebook_repository.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late DatabaseHelper dbHelper;
  late NotebookRepository repo;

  setUp(() async {
    dbHelper = DatabaseHelper(inMemory: true);
    await dbHelper.database;
    repo = NotebookRepository(dbHelper);
  });

  tearDown(() async => await dbHelper.close());

  group('NotebookRepository', () {
    test('listByFolder returns default notebook for folder 1', () async {
      final notebooks = await repo.listByFolder(1);
      expect(notebooks.length, 1);
      expect(notebooks[0].isDefault, true);
    });

    test('insert adds new notebook', () async {
      final id = await repo.insert(1, 'Science', '#FF0000');
      expect(id, greaterThan(1));
    });

    test('get returns notebook by id', () async {
      final id = await repo.insert(1, 'Math', '#00FF00');
      final nb = await repo.get(id);
      expect(nb, isNotNull);
      expect(nb!.name, 'Math');
      expect(nb.color, '#00FF00');
    });

    test('rename updates name', () async {
      final id = await repo.insert(1, 'Old', '#000');
      await repo.rename(id, 'New');
      final nb = await repo.get(id);
      expect(nb!.name, 'New');
    });

    test('updateColor updates color', () async {
      final id = await repo.insert(1, 'Nb', '#000');
      await repo.updateColor(id, '#FFF');
      final nb = await repo.get(id);
      expect(nb!.color, '#FFF');
    });

    test('softDelete marks notebook and its notes as deleted', () async {
      final id = await repo.insert(1, 'ToDelete', '#000');
      await repo.softDelete(id);
      final nb = await repo.get(id);
      expect(nb, isNull);
    });

    test('move changes folder_id', () async {
      final db = await dbHelper.database;
      await db.insert('folders', {
        'name': 'Folder2',
        'order_index': 1,
        'is_default': 0,
        'deleted_at': 0,
      });
      final nbId = await repo.insert(1, 'Movable', '#000');
      await repo.move(nbId, 2);
      final nb = await repo.get(nbId);
      expect(nb!.folderId, 2);
    });
  });
}
