import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/providers/note_editor_provider.dart';

void main() {
  group('NoteEditorState', () {
    test('initial state is browse mode', () {
      final s = NoteEditorState.initial(noteId: 1);
      expect(s.noteId, 1);
      expect(s.isEditing, isFalse);
      expect(s.isSaving, isFalse);
      expect(s.title, isEmpty);
      expect(s.pendingNotebookId, isNull);
      expect(s.pendingBackground, 'plain');
    });

    test('new note starts in edit mode', () {
      final s = NoteEditorState.initial(noteId: 0);
      expect(s.noteId, 0);
      expect(s.isEditing, isTrue);
    });

    test('copyWith preserves unchanged fields', () {
      final s = NoteEditorState.initial(noteId: 1);
      final s2 = s.copyWith(isEditing: true, title: 'Hello');
      expect(s2.noteId, 1);
      expect(s2.isEditing, isTrue);
      expect(s2.title, 'Hello');
      expect(s2.isSaving, isFalse);
    });

    test('isNoteEmpty with empty title and no content', () {
      final s = NoteEditorState.initial(noteId: 0);
      expect(s.isNoteEmpty, isTrue);
    });

    test('isNoteEmpty with title is false', () {
      final s = NoteEditorState.initial(noteId: 0).copyWith(title: 'Hi');
      expect(s.isNoteEmpty, isFalse);
    });
  });
}
