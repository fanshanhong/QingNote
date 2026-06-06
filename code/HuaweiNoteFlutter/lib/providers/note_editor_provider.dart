import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/note.dart';
import '../models/note_content.dart';
import '../repositories/note_repository.dart';
import '../repositories/notebook_repository.dart';
import '../repositories/category_repository.dart';
import 'repository_providers.dart';

class NoteEditorState {
  final int noteId;
  final bool isEditing;
  final bool isSaving;
  final String title;
  final Note? loadedNote;
  final int? pendingNotebookId;
  final String pendingBackground;
  final bool documentHasContent;

  const NoteEditorState({
    required this.noteId,
    this.isEditing = false,
    this.isSaving = false,
    this.title = '',
    this.loadedNote,
    this.pendingNotebookId,
    this.pendingBackground = 'plain',
    this.documentHasContent = false,
  });

  factory NoteEditorState.initial({required int noteId}) => NoteEditorState(
    noteId: noteId,
    isEditing: noteId == 0,
  );

  bool get isNoteEmpty => title.trim().isEmpty && !documentHasContent;

  NoteEditorState copyWith({
    int? noteId,
    bool? isEditing,
    bool? isSaving,
    String? title,
    Note? loadedNote,
    int? pendingNotebookId,
    bool clearPendingNotebookId = false,
    String? pendingBackground,
    bool? documentHasContent,
  }) => NoteEditorState(
    noteId: noteId ?? this.noteId,
    isEditing: isEditing ?? this.isEditing,
    isSaving: isSaving ?? this.isSaving,
    title: title ?? this.title,
    loadedNote: loadedNote ?? this.loadedNote,
    pendingNotebookId: clearPendingNotebookId ? null : (pendingNotebookId ?? this.pendingNotebookId),
    pendingBackground: pendingBackground ?? this.pendingBackground,
    documentHasContent: documentHasContent ?? this.documentHasContent,
  );
}

class NoteEditorNotifier extends StateNotifier<NoteEditorState> {
  final NoteRepository _noteRepo;
  final NotebookRepository _notebookRepo;
  final CategoryRepository _categoryRepo;
  EditorState? _editorState;

  NoteEditorNotifier(this._noteRepo, this._notebookRepo, this._categoryRepo, int noteId)
      : super(NoteEditorState.initial(noteId: noteId));

  EditorState? get editorState => _editorState;

  Future<void> loadNote() async {
    if (state.noteId == 0) {
      final doc = Document.blank();
      _editorState = EditorState(document: doc);
      return;
    }
    final note = await _noteRepo.get(state.noteId);
    if (note == null) return;
    final content = note.content;
    final docJson = content.documentJson['document'] as Map<String, dynamic>?;
    final doc = docJson != null
        ? Document.fromJson(docJson)
        : Document.blank();
    _editorState = EditorState(document: doc);
    state = state.copyWith(
      title: note.title,
      loadedNote: note,
      pendingNotebookId: note.notebookId,
      pendingBackground: note.background,
      documentHasContent: content.toPlainText().isNotEmpty,
    );
  }

  void enterEditMode() {
    state = state.copyWith(isEditing: true);
  }

  void exitEditMode() {
    state = state.copyWith(isEditing: false);
  }

  void updateTitle(String title) {
    state = state.copyWith(title: title);
  }

  void setNotebook(int? notebookId) {
    state = state.copyWith(
      pendingNotebookId: notebookId,
      clearPendingNotebookId: notebookId == null,
    );
  }

  void setBackground(String background) {
    state = state.copyWith(pendingBackground: background);
  }

  void updateDocumentHasContent(bool hasContent) {
    state = state.copyWith(documentHasContent: hasContent);
  }

  Future<void> toggleFavorite() async {
    final note = state.loadedNote;
    if (note == null) return;
    await _noteRepo.setFavorite(note.id, !note.isFavorite);
    final updated = note.copyWith(isFavorite: !note.isFavorite);
    state = state.copyWith(loadedNote: updated);
  }

  Future<bool> saveNote() async {
    if (state.isSaving) return false;
    if (state.noteId == 0 && state.isNoteEmpty) return false;

    state = state.copyWith(isSaving: true);
    try {
      final editorDoc = _editorState?.document;
      final docJson = editorDoc?.toJson() ?? {};
      final content = NoteContent(
        documentJson: {'document': docJson},
        handwriting: state.loadedNote?.content.handwriting ?? [],
      );
      final now = DateTime.now().millisecondsSinceEpoch;
      final note = (state.loadedNote ?? Note.newNote(now: now)).copyWith(
        title: state.title,
        plainText: content.toPlainText(),
        content: content,
        notebookId: state.pendingNotebookId,
        background: state.pendingBackground,
        updatedAt: now,
      );
      final id = await _noteRepo.save(note);
      if (state.noteId == 0) {
        state = state.copyWith(
          noteId: id,
          loadedNote: note.copyWith(id: id),
          isSaving: false,
        );
      } else {
        state = state.copyWith(
          loadedNote: note,
          isSaving: false,
        );
      }
      return true;
    } catch (_) {
      state = state.copyWith(isSaving: false);
      return false;
    }
  }

  Future<void> softDelete() async {
    if (state.noteId == 0) return;
    await _noteRepo.softDelete(state.noteId);
  }

  Future<List<({int id, String name, String color})>> loadNotebooks() async {
    final folders = await _notebookRepo.listByFolder(1);
    return folders.map((nb) => (id: nb.id, name: nb.name, color: nb.color)).toList();
  }

  Future<List<({int id, String name, String color})>> loadCategories() async {
    final cats = await _categoryRepo.list();
    return cats.map((c) => (id: c.id, name: c.name, color: c.color)).toList();
  }

  Future<void> setCategoryId(int? categoryId) async {
    if (state.noteId == 0) return;
    final note = state.loadedNote;
    if (note == null) return;
    final updated = note.copyWith(
      categoryId: categoryId,
      setCategoryIdNull: categoryId == null,
    );
    state = state.copyWith(loadedNote: updated);
  }
}

final noteEditorProvider = StateNotifierProvider.autoDispose
    .family<NoteEditorNotifier, NoteEditorState, int>((ref, noteId) {
  return NoteEditorNotifier(
    ref.watch(noteRepositoryProvider),
    ref.watch(notebookRepositoryProvider),
    ref.watch(categoryRepositoryProvider),
    noteId,
  );
});
