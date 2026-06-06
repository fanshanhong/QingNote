import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/note.dart';

void main() {
  group('Note', () {
    test('creates with defaults', () {
      final n = Note(id: 0, createdAt: 1000, updatedAt: 1000);
      expect(n.title, '');
      expect(n.isFavorite, false);
      expect(n.deletedAt, 0);
      expect(n.background, 'plain');
      expect(n.notebookId, isNull);
      expect(n.categoryId, isNull);
    });

    test('newNote() sets timestamps', () {
      final now = DateTime.now().millisecondsSinceEpoch;
      final n = Note.newNote(now: now);
      expect(n.id, 0);
      expect(n.createdAt, now);
      expect(n.updatedAt, now);
    });

    test('copyWith preserves unmodified fields', () {
      final n = Note(id: 1, title: 'A', createdAt: 100, updatedAt: 200);
      final n2 = n.copyWith(title: 'B');
      expect(n2.id, 1);
      expect(n2.title, 'B');
      expect(n2.createdAt, 100);
    });
  });
}
