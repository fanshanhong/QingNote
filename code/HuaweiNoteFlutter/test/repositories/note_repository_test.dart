import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';
import 'package:hwnote/repositories/note_repository.dart';
import 'package:hwnote/models/note.dart';
import 'package:hwnote/models/note_content.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late DatabaseHelper dbHelper;
  late NoteRepository repo;

  setUp(() async {
    dbHelper = DatabaseHelper(inMemory: true);
    await dbHelper.database;
    repo = NoteRepository(dbHelper);
  });

  tearDown(() async {
    await dbHelper.close();
  });

  group('NoteRepository', () {
    test('save inserts new note and returns id', () async {
      final note = Note.newNote();
      final id = await repo.save(note.copyWith(title: 'Test'));
      expect(id, greaterThan(0));
    });

    test('save updates existing note', () async {
      final note = Note.newNote();
      final id = await repo.save(note.copyWith(title: 'V1'));
      await repo.save(Note(
          id: id,
          title: 'V2',
          createdAt: note.createdAt,
          updatedAt: note.updatedAt));
      final loaded = await repo.get(id);
      expect(loaded, isNotNull);
      expect(loaded!.title, 'V2');
    });

    test('list returns non-deleted notes', () async {
      await repo.save(Note.newNote().copyWith(title: 'A'));
      await repo.save(Note.newNote().copyWith(title: 'B'));
      final notes = await repo.list();
      expect(notes.length, 2);
    });

    test('softDelete marks note as deleted', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'ToDelete'));
      await repo.softDelete(id);
      final all = await repo.list();
      expect(all, isEmpty);
      final deleted = await repo.list(filter: NoteListFilter.deleted);
      expect(deleted.length, 1);
    });

    test('restore brings back deleted note', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'Restore'));
      await repo.softDelete(id);
      await repo.restore(id);
      final notes = await repo.list();
      expect(notes.length, 1);
    });

    test('deletePermanently removes row', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'Gone'));
      await repo.deletePermanently(id);
      expect(await repo.get(id), isNull);
    });

    test('setFavorite toggles favorite', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'Fav'));
      await repo.setFavorite(id, true);
      final loaded = await repo.get(id);
      expect(loaded!.isFavorite, true);
    });

    test('list with search query', () async {
      await repo.save(Note.newNote().copyWith(
        title: 'Shopping',
        content: NoteContent(
          documentJson: {
            'document': {
              'type': 'page',
              'children': [
                {
                  'type': 'paragraph',
                  'data': {
                    'delta': [
                      {'insert': 'Buy apples'}
                    ]
                  }
                },
              ],
            },
          },
          handwriting: [],
        ),
      ));
      await repo.save(Note.newNote().copyWith(title: 'Work'));
      final results = await repo.list(query: 'apple');
      expect(results.length, 1);
    });

    test('count returns correct number', () async {
      await repo.save(Note.newNote().copyWith(title: 'A'));
      await repo.save(Note.newNote().copyWith(title: 'B'));
      expect(await repo.count(), 2);
    });

    test('moveNoteToNotebook updates notebook_id', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'Move'));
      await repo.moveNoteToNotebook(id, 1);
      final loaded = await repo.get(id);
      expect(loaded!.notebookId, 1);
    });

    test('purgeExpired removes old soft-deleted notes', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'Old'));
      await repo.softDelete(id);
      final db = await dbHelper.database;
      final longAgo =
          DateTime.now().millisecondsSinceEpoch - 31 * 24 * 60 * 60 * 1000;
      await db.update('notes', {'deleted_at': longAgo},
          where: 'id = ?', whereArgs: [id]);
      final purged = await repo.purgeExpired();
      expect(purged, 1);
      expect(await repo.get(id), isNull);
    });
  });
}
