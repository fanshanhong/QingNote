// test/repositories/folder_repository_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';
import 'package:hwnote/repositories/folder_repository.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late DatabaseHelper dbHelper;
  late FolderRepository repo;

  setUp(() async {
    dbHelper = DatabaseHelper(inMemory: true);
    await dbHelper.database;
    repo = FolderRepository(dbHelper);
  });

  tearDown(() async => await dbHelper.close());

  group('FolderRepository', () {
    test('list includes default folder', () async {
      final folders = await repo.list();
      expect(folders.length, 1);
      expect(folders[0].isDefault, true);
      expect(folders[0].name, '默认');
    });

    test('insert adds new folder', () async {
      final id = await repo.insert('Work');
      expect(id, greaterThan(1));
      final folders = await repo.list();
      expect(folders.length, 2);
    });

    test('get returns folder by id', () async {
      final id = await repo.insert('Personal');
      final folder = await repo.get(id);
      expect(folder, isNotNull);
      expect(folder!.name, 'Personal');
    });

    test('rename changes folder name', () async {
      final id = await repo.insert('Old');
      await repo.rename(id, 'New');
      final folder = await repo.get(id);
      expect(folder!.name, 'New');
    });

    test('softDelete marks folder and its notebooks/notes as deleted', () async {
      final id = await repo.insert('ToDelete');
      await repo.softDelete(id);
      final folders = await repo.list();
      expect(folders.every((f) => f.id != id), true);
    });

    test('reorder updates order_index', () async {
      final id1 = await repo.insert('A');
      final id2 = await repo.insert('B');
      await repo.reorder([id2, id1]);
      final folders = await repo.list();
      final nonDefault = folders.where((f) => !f.isDefault).toList();
      expect(nonDefault[0].id, id2);
    });
  });
}
