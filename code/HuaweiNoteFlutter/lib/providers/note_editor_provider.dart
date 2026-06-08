import 'dart:io';
import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import '../editor/audio_block_component.dart';
import '../models/note.dart';
import '../models/note_content.dart';
import '../models/stroke.dart';
import '../repositories/note_repository.dart';
import '../repositories/notebook_repository.dart';
import '../repositories/category_repository.dart';
import '../utils/image_compressor.dart';
import '../utils/note_file_storage.dart';
import '../widgets/editor/audio_recording_sheet.dart';
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
  final bool isHandwritingMode;
  final BrushType currentBrush;
  final String currentBrushColor;
  final int currentBrushWidth;
  final bool isErasing;
  final String? notebookName;
  final String? notebookColor;
  final String? categoryName;
  final bool canUndo;
  final bool canRedo;

  const NoteEditorState({
    required this.noteId,
    this.isEditing = false,
    this.isSaving = false,
    this.title = '',
    this.loadedNote,
    this.pendingNotebookId,
    this.pendingBackground = 'plain',
    this.documentHasContent = false,
    this.isHandwritingMode = false,
    this.currentBrush = BrushType.pen,
    this.currentBrushColor = '#212121',
    this.currentBrushWidth = 3,
    this.isErasing = false,
    this.notebookName,
    this.notebookColor,
    this.categoryName,
    this.canUndo = false,
    this.canRedo = false,
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
    bool? isHandwritingMode,
    BrushType? currentBrush,
    String? currentBrushColor,
    int? currentBrushWidth,
    bool? isErasing,
    String? notebookName,
    bool clearNotebookName = false,
    String? notebookColor,
    bool clearNotebookColor = false,
    String? categoryName,
    bool clearCategoryName = false,
    bool? canUndo,
    bool? canRedo,
  }) => NoteEditorState(
    noteId: noteId ?? this.noteId,
    isEditing: isEditing ?? this.isEditing,
    isSaving: isSaving ?? this.isSaving,
    title: title ?? this.title,
    loadedNote: loadedNote ?? this.loadedNote,
    pendingNotebookId: clearPendingNotebookId ? null : (pendingNotebookId ?? this.pendingNotebookId),
    pendingBackground: pendingBackground ?? this.pendingBackground,
    documentHasContent: documentHasContent ?? this.documentHasContent,
    isHandwritingMode: isHandwritingMode ?? this.isHandwritingMode,
    currentBrush: currentBrush ?? this.currentBrush,
    currentBrushColor: currentBrushColor ?? this.currentBrushColor,
    currentBrushWidth: currentBrushWidth ?? this.currentBrushWidth,
    isErasing: isErasing ?? this.isErasing,
    notebookName: clearNotebookName ? null : (notebookName ?? this.notebookName),
    notebookColor: clearNotebookColor ? null : (notebookColor ?? this.notebookColor),
    categoryName: clearCategoryName ? null : (categoryName ?? this.categoryName),
    canUndo: canUndo ?? this.canUndo,
    canRedo: canRedo ?? this.canRedo,
  );
}

class NoteEditorNotifier extends StateNotifier<NoteEditorState> {
  final NoteRepository _noteRepo;
  final NotebookRepository _notebookRepo;
  final CategoryRepository _categoryRepo;
  final int _initialNoteId;
  EditorState? _editorState;

  NoteEditorNotifier(this._noteRepo, this._notebookRepo, this._categoryRepo, int noteId)
      : _initialNoteId = noteId,
        super(NoteEditorState.initial(noteId: noteId));

  EditorState? get editorState => _editorState;

  Future<void> loadNote() async {
    if (_initialNoteId == 0) {
      state = NoteEditorState.initial(noteId: 0);
      final doc = Document.blank(withInitialText: true);
      _editorState = EditorState(document: doc);
      return;
    }
    final note = await _noteRepo.get(state.noteId);
    if (note == null) return;
    final content = note.content;
    final docJson = content.documentJson['document'] as Map<String, dynamic>?;
    Document doc;
    if (docJson != null) {
      doc = Document.fromJson(docJson);
    } else {
      doc = Document.blank(withInitialText: true);
    }
    if (doc.root.children.isEmpty) {
      doc.insert([0], [paragraphNode()]);
    }
    _editorState = EditorState(document: doc);

    String? nbName;
    String? nbColor;
    if (note.notebookId != null) {
      final nb = await _notebookRepo.get(note.notebookId!);
      if (nb != null) { nbName = nb.name; nbColor = nb.color; }
    }

    String? catName;
    if (note.categoryId != null) {
      final cat = await _categoryRepo.get(note.categoryId!);
      if (cat != null) catName = cat.name;
    }

    state = state.copyWith(
      title: note.title,
      loadedNote: note,
      pendingNotebookId: note.notebookId,
      pendingBackground: note.background,
      documentHasContent: content.toPlainText().isNotEmpty,
      notebookName: nbName,
      notebookColor: nbColor,
      categoryName: catName,
    );
  }

  void enterEditMode() {
    state = state.copyWith(isEditing: true);
  }

  void exitEditMode() {
    state = state.copyWith(isEditing: false);
  }

  void enterHandwritingMode() {
    state = state.copyWith(isHandwritingMode: true, isErasing: false);
  }

  void exitHandwritingMode() {
    state = state.copyWith(isHandwritingMode: false, isErasing: false);
  }

  void setBrush(BrushType type) {
    state = state.copyWith(currentBrush: type, isErasing: false);
  }

  void setBrushColor(String hex) {
    state = state.copyWith(currentBrushColor: hex);
  }

  void setBrushWidth(int width) {
    state = state.copyWith(currentBrushWidth: width);
  }

  void toggleEraser() {
    state = state.copyWith(isErasing: !state.isErasing);
  }

  void updateTitle(String title) {
    state = state.copyWith(title: title);
  }

  Future<void> setNotebook(int? notebookId) async {
    if (notebookId != null) {
      final nb = await _notebookRepo.get(notebookId);
      state = state.copyWith(
        pendingNotebookId: notebookId,
        notebookName: nb?.name,
        notebookColor: nb?.color,
      );
    } else {
      state = state.copyWith(
        clearPendingNotebookId: true,
        clearNotebookName: true,
        clearNotebookColor: true,
      );
    }
  }

  void setBackground(String background) {
    state = state.copyWith(pendingBackground: background);
  }

  void updateDocumentHasContent(bool hasContent) {
    if (state.documentHasContent == hasContent) return;
    state = state.copyWith(documentHasContent: hasContent);
  }

  void updateUndoRedoState(bool canUndo, bool canRedo) {
    if (state.canUndo == canUndo && state.canRedo == canRedo) return;
    state = state.copyWith(canUndo: canUndo, canRedo: canRedo);
  }

  Future<void> toggleFavorite() async {
    final note = state.loadedNote;
    if (note == null) return;
    await _noteRepo.setFavorite(note.id, !note.isFavorite);
    final updated = note.copyWith(isFavorite: !note.isFavorite);
    state = state.copyWith(loadedNote: updated);
  }

  Future<bool> saveNote({List<Stroke>? handwritingStrokes}) async {
    if (state.isSaving) return false;
    if (state.noteId == 0 && state.isNoteEmpty) return false;

    state = state.copyWith(isSaving: true);
    try {
      final editorDoc = _editorState?.document;
      final docJson = editorDoc?.toJson() ?? {};
      final content = NoteContent(
        documentJson: {'document': docJson},
        handwriting: handwritingStrokes ?? state.loadedNote?.content.handwriting ?? [],
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
      // 异步清理孤儿文件（不阻塞 UI）
      if (editorDoc != null) {
        final effectiveId = state.noteId == 0 ? id : state.noteId;
        final referencedPaths = _extractImagePaths(editorDoc);
        NoteFileStorage.cleanOrphanImages(effectiveId, referencedPaths);
        final referencedAudioNames = _extractAudioFileNames(editorDoc);
        NoteFileStorage.cleanOrphanAudios(effectiveId, referencedAudioNames);
      }
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

  Future<int> ensureNoteSaved() async {
    if (state.noteId != 0) return state.noteId;
    if (state.title.trim().isEmpty) {
      state = state.copyWith(title: '新笔记');
    }
    await saveNote();
    return state.noteId;
  }

  Future<void> insertImage({bool useCamera = false, Selection? insertAt}) async {
    final es = _editorState;
    if (es == null) return;
    final selection = insertAt ?? es.selection;
    final basePath = selection?.end.path ?? es.document.root.children.last.path;

    final noteId = await ensureNoteSaved();
    if (noteId == 0) return;

    final picker = ImagePicker();
    final picked = await picker.pickImage(
      source: useCamera ? ImageSource.camera : ImageSource.gallery,
    );
    if (picked == null) return;

    final source = File(picked.path);
    final compressed = await ImageCompressor.compress(source);
    if (compressed == null) return;

    final savedFile = await NoteFileStorage.saveImage(noteId, compressed);
    final insertPath = basePath.next;

    final transaction = es.transaction;
    transaction.insertNode(insertPath, imageNode(url: savedFile.path));
    transaction.insertNode(insertPath.next, paragraphNode());
    final sel = Selection.collapsed(
      Position(path: insertPath.next, offset: 0),
    );
    transaction.afterSelection = sel;
    await es.apply(transaction);
  }

  Future<void> startRecording(BuildContext context) async {
    final es = _editorState;
    if (es == null) return;
    final selectionBeforeModal = es.selection;
    final basePath = selectionBeforeModal?.end.path ?? es.document.root.children.last.path;

    final noteId = await ensureNoteSaved();
    if (noteId == 0) return;

    final dir = await NoteFileStorage.audioDir(noteId);
    if (!dir.existsSync()) dir.createSync(recursive: true);
    final targetPath = '${dir.path}/${DateTime.now().millisecondsSinceEpoch}.m4a';

    if (!context.mounted) return;
    final result = await showAudioRecordingSheet(
      context,
      targetFilePath: targetPath,
    );
    if (result == null) return;

    final fileName = result.filePath.split('/').last;
    final insertPath = basePath.next;

    final transaction = es.transaction;
    transaction.insertNode(
      insertPath,
      audioNode(fileName: fileName, durationMs: result.durationMs),
    );
    transaction.insertNode(insertPath.next, paragraphNode());
    final sel = Selection.collapsed(
      Position(path: insertPath.next, offset: 0),
    );
    transaction.afterSelection = sel;
    await es.apply(transaction);
  }

  Set<String> _extractImagePaths(Document document) {
    final paths = <String>{};
    void visit(Node node) {
      if (node.type == ImageBlockKeys.type) {
        final url = node.attributes[ImageBlockKeys.url] as String?;
        if (url != null) paths.add(url);
      }
      for (final child in node.children) {
        visit(child);
      }
    }
    visit(document.root);
    return paths;
  }

  Set<String> _extractAudioFileNames(Document document) {
    final names = <String>{};
    void visit(Node node) {
      if (node.type == AudioBlockKeys.type) {
        final name = node.attributes[AudioBlockKeys.fileName] as String?;
        if (name != null) names.add(name);
      }
      for (final child in node.children) {
        visit(child);
      }
    }
    visit(document.root);
    return names;
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

    String? catName;
    if (categoryId != null) {
      final cat = await _categoryRepo.get(categoryId);
      catName = cat?.name;
    }

    final updated = note.copyWith(
      categoryId: categoryId,
      setCategoryIdNull: categoryId == null,
    );
    state = state.copyWith(
      loadedNote: updated,
      categoryName: catName,
      clearCategoryName: categoryId == null,
    );
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
