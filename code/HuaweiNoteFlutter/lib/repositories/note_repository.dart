import 'package:sqflite/sqflite.dart';
import '../db/database_helper.dart';
import '../models/note.dart';
import '../models/note_content.dart';
import '../services/search_service.dart';

enum NoteSortBy { updatedDesc, createdDesc }

sealed class NoteListFilter {
  const NoteListFilter();
  static const all = AllFilter();
  static const uncategorized = UncategorizedFilter();
  static const favorite = FavoriteFilter();
  static const deleted = DeletedFilter();
  static NoteListFilter folder(int folderId) => FolderFilter(folderId);
  static NoteListFilter notebook(int notebookId) => NotebookFilter(notebookId);
}

class AllFilter extends NoteListFilter {
  const AllFilter();
}

class UncategorizedFilter extends NoteListFilter {
  const UncategorizedFilter();
}

class FavoriteFilter extends NoteListFilter {
  const FavoriteFilter();
}

class DeletedFilter extends NoteListFilter {
  const DeletedFilter();
}

class FolderFilter extends NoteListFilter {
  final int folderId;
  const FolderFilter(this.folderId);
}

class NotebookFilter extends NoteListFilter {
  final int notebookId;
  const NotebookFilter(this.notebookId);
}

class NoteRepository {
  final DatabaseHelper _dbHelper;

  NoteRepository(this._dbHelper);

  Future<List<Note>> list({
    NoteListFilter filter = const AllFilter(),
    NoteSortBy sortBy = NoteSortBy.updatedDesc,
    String? query,
  }) async {
    final db = await _dbHelper.database;
    final orderBy = switch (sortBy) {
      NoteSortBy.updatedDesc => 'updated_at DESC',
      NoteSortBy.createdDesc => 'created_at DESC',
    };
    final where = <String>[];
    final args = <String>[];
    switch (filter) {
      case AllFilter():
        where.add('deleted_at = 0');
      case UncategorizedFilter():
        where.add('deleted_at = 0 AND notebook_id IS NULL');
      case FavoriteFilter():
        where.add('deleted_at = 0 AND is_favorite = 1');
      case DeletedFilter():
        where.add('deleted_at != 0');
      case FolderFilter(:final folderId):
        where.add(
            'deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)');
        args.add(folderId.toString());
      case NotebookFilter(:final notebookId):
        where.add('deleted_at = 0 AND notebook_id = ?');
        args.add(notebookId.toString());
    }
    if (query != null && query.isNotEmpty) {
      final like = '%$query%';
      where.add('(title LIKE ? OR plain_text LIKE ?)');
      args.addAll([like, like]);
    }
    final rows = await db.query(
      'notes',
      where: where.join(' AND '),
      whereArgs: args,
      orderBy: orderBy,
    );
    return rows.map(_rowToNote).toList();
  }

  Future<Note?> get(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.query('notes', where: 'id = ?', whereArgs: [id]);
    if (rows.isEmpty) return null;
    return _rowToNote(rows.first);
  }

  Future<List<Note>> getByIds(List<int> ids) async {
    if (ids.isEmpty) return [];
    final db = await _dbHelper.database;
    final placeholders = List.filled(ids.length, '?').join(',');
    final rows = await db.query(
      'notes',
      where: 'id IN ($placeholders) AND deleted_at = 0',
      whereArgs: ids,
    );
    return rows.map(_rowToNote).toList();
  }

  Future<List<Note>> searchByLike(String keyword, {int limit = 50}) async {
    if (keyword.isEmpty) return [];
    final db = await _dbHelper.database;
    final pattern = '%$keyword%';
    final rows = await db.query(
      'notes',
      where: 'deleted_at = 0 AND (title LIKE ? OR plain_text LIKE ?)',
      whereArgs: [pattern, pattern],
      orderBy: 'updated_at DESC',
      limit: limit,
    );
    return rows.map(_rowToNote).toList();
  }

  Future<int> save(Note note) async {
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;

    final firstImage = _extractFirstImagePath(note.content);
    final hasTodo = _hasTodoBlock(note.content);

    final values = <String, Object?>{
      'title': note.title,
      'plain_text': note.content.toPlainText(),
      'content_json': note.content.toJson(),
      'is_favorite': note.isFavorite ? 1 : 0,
      'updated_at': now,
      'category_id': note.categoryId,
      'deleted_at': note.deletedAt,
      'notebook_id': note.notebookId,
      'background': note.background,
      'first_image_path': firstImage,
      'has_todo': hasTodo ? 1 : 0,
    };
    int resultId;
    if (note.id == 0) {
      values['created_at'] = note.createdAt > 0 ? note.createdAt : now;
      resultId = await db.insert('notes', values);
    } else {
      await db.update('notes', values, where: 'id = ?', whereArgs: [note.id]);
      resultId = note.id;
    }
    if (note.deletedAt == 0) {
      SearchService.instance.indexNote(note.copyWith(id: resultId));
    } else {
      SearchService.instance.removeNote(resultId);
    }
    return resultId;
  }

  Future<void> softDelete(int id) async {
    final db = await _dbHelper.database;
    await db.update(
      'notes',
      {'deleted_at': DateTime.now().millisecondsSinceEpoch},
      where: 'id = ?',
      whereArgs: [id],
    );
    SearchService.instance.removeNote(id);
  }

  Future<void> softDeleteBatch(List<int> ids) async {
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.transaction((txn) async {
      for (final id in ids) {
        await txn.update('notes', {'deleted_at': now},
            where: 'id = ?', whereArgs: [id]);
      }
    });
  }

  Future<void> restore(int id) async {
    final db = await _dbHelper.database;
    await db.update('notes', {'deleted_at': 0},
        where: 'id = ?', whereArgs: [id]);
    final note = await get(id);
    if (note != null) SearchService.instance.indexNote(note);
  }

  Future<void> deletePermanently(int id) async {
    final db = await _dbHelper.database;
    await db.delete('notes', where: 'id = ?', whereArgs: [id]);
    SearchService.instance.removeNote(id);
  }

  Future<int> count({NoteListFilter filter = const AllFilter()}) async {
    final db = await _dbHelper.database;
    final where = <String>[];
    final args = <String>[];
    switch (filter) {
      case AllFilter():
        where.add('deleted_at = 0');
      case UncategorizedFilter():
        where.add('deleted_at = 0 AND notebook_id IS NULL');
      case FavoriteFilter():
        where.add('deleted_at = 0 AND is_favorite = 1');
      case DeletedFilter():
        where.add('deleted_at != 0');
      case FolderFilter(:final folderId):
        where.add(
            'deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)');
        args.add(folderId.toString());
      case NotebookFilter(:final notebookId):
        where.add('deleted_at = 0 AND notebook_id = ?');
        args.add(notebookId.toString());
    }
    final result = await db.rawQuery(
      'SELECT COUNT(*) FROM notes WHERE ${where.join(' AND ')}',
      args,
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<void> setFavorite(int id, bool favorite) async {
    final db = await _dbHelper.database;
    await db.update(
        'notes',
        {
          'is_favorite': favorite ? 1 : 0,
          'updated_at': DateTime.now().millisecondsSinceEpoch,
        },
        where: 'id = ?',
        whereArgs: [id]);
  }

  Future<void> moveNoteToNotebook(int noteId, int? targetNotebookId) async {
    final db = await _dbHelper.database;
    await db.update(
        'notes',
        {
          'notebook_id': targetNotebookId,
          'updated_at': DateTime.now().millisecondsSinceEpoch,
        },
        where: 'id = ?',
        whereArgs: [noteId]);
  }

  Future<int> purgeExpired({
    int? now,
    int ttlMs = 30 * 24 * 60 * 60 * 1000,
  }) async {
    final db = await _dbHelper.database;
    final cutoff = (now ?? DateTime.now().millisecondsSinceEpoch) - ttlMs;
    return db.delete('notes',
        where: 'deleted_at > 0 AND deleted_at < ?', whereArgs: [cutoff]);
  }

  static String? _extractFirstImagePath(NoteContent content) {
    final doc = content.documentJson['document'] as Map<String, dynamic>?;
    if (doc == null) return null;
    final children = doc['children'] as List<dynamic>? ?? [];
    for (final node in children) {
      final map = node as Map<String, dynamic>;
      if (map['type'] == 'image') {
        final data = map['data'] as Map<String, dynamic>?;
        if (data != null) {
          return data['url'] as String?;
        }
      }
    }
    return null;
  }

  static bool _hasTodoBlock(NoteContent content) {
    final doc = content.documentJson['document'] as Map<String, dynamic>?;
    if (doc == null) return false;
    final children = doc['children'] as List<dynamic>? ?? [];
    for (final node in children) {
      final map = node as Map<String, dynamic>;
      if (map['type'] == 'todo_list') return true;
    }
    return false;
  }

  Note _rowToNote(Map<String, Object?> row) {
    final contentJson = row['content_json'] as String? ?? '';
    return Note(
      id: row['id'] as int,
      title: row['title'] as String? ?? '',
      plainText: row['plain_text'] as String? ?? '',
      isFavorite: (row['is_favorite'] as int) == 1,
      createdAt: row['created_at'] as int,
      updatedAt: row['updated_at'] as int,
      content: NoteContent.fromJson(contentJson),
      categoryId: row['category_id'] as int?,
      deletedAt: row['deleted_at'] as int? ?? 0,
      notebookId: row['notebook_id'] as int?,
      background: row['background'] as String? ?? 'plain',
      firstImagePath: row['first_image_path'] as String?,
      hasTodo: (row['has_todo'] as int? ?? 0) == 1,
    );
  }
}
