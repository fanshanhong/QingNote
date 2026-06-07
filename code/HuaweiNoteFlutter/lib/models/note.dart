import 'note_content.dart';

class Note {
  final int id;
  final String title;
  final String plainText;
  final bool isFavorite;
  final int createdAt;
  final int updatedAt;
  final NoteContent content;
  final int? categoryId;
  final int deletedAt;
  final int? notebookId;
  final String background;
  final String? firstImagePath;
  final bool hasTodo;

  Note({
    required this.id,
    this.title = '',
    this.plainText = '',
    this.isFavorite = false,
    required this.createdAt,
    required this.updatedAt,
    NoteContent? content,
    this.categoryId,
    this.deletedAt = 0,
    this.notebookId,
    this.background = 'plain',
    this.firstImagePath,
    this.hasTodo = false,
  }) : content = content ?? NoteContent.empty();

  factory Note.newNote({int? now}) {
    final ts = now ?? DateTime.now().millisecondsSinceEpoch;
    return Note(id: 0, createdAt: ts, updatedAt: ts);
  }

  Note copyWith({
    int? id, String? title, String? plainText, bool? isFavorite,
    int? createdAt, int? updatedAt, NoteContent? content, int? categoryId,
    bool setCategoryIdNull = false, int? deletedAt, int? notebookId,
    bool setNotebookIdNull = false, String? background,
    String? firstImagePath, bool clearFirstImagePath = false,
    bool? hasTodo,
  }) => Note(
    id: id ?? this.id,
    title: title ?? this.title,
    plainText: plainText ?? this.plainText,
    isFavorite: isFavorite ?? this.isFavorite,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    content: content ?? this.content,
    categoryId: setCategoryIdNull ? null : (categoryId ?? this.categoryId),
    deletedAt: deletedAt ?? this.deletedAt,
    notebookId: setNotebookIdNull ? null : (notebookId ?? this.notebookId),
    background: background ?? this.background,
    firstImagePath: clearFirstImagePath ? null : (firstImagePath ?? this.firstImagePath),
    hasTodo: hasTodo ?? this.hasTodo,
  );
}
