import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import '../src/rust/api/search.dart' as rust_search;
import '../models/note.dart';

class SearchService {
  SearchService._();
  static final instance = SearchService._();

  bool _initialized = false;

  Future<void> init() async {
    if (_initialized) return;
    try {
      final dir = await getApplicationSupportDirectory();
      final indexPath = '${dir.path}${Platform.pathSeparator}search_index';
      await rust_search.initSearchEngine(indexPath: indexPath);
      _initialized = true;
    } catch (e) {
      debugPrint('[SearchService] init failed: $e');
    }
  }

  Future<void> indexNote(Note note) async {
    if (!_initialized) return;
    try {
      await rust_search.upsertNoteIndex(
        id: note.id,
        title: note.title,
        content: note.content.toPlainText(),
      );
    } catch (e) {
      debugPrint('[SearchService] indexNote failed: $e');
    }
  }

  Future<void> removeNote(int noteId) async {
    if (!_initialized) return;
    try {
      await rust_search.deleteNoteIndex(id: noteId);
    } catch (e) {
      debugPrint('[SearchService] removeNote failed: $e');
    }
  }

  Future<List<rust_search.SearchHit>> search(String query,
      {int limit = 50}) async {
    if (!_initialized || query.isEmpty) return [];
    try {
      return await rust_search.searchNotes(query: query, limit: limit);
    } catch (e) {
      debugPrint('[SearchService] search failed: $e');
      return [];
    }
  }

  Future<void> rebuildIndex(List<Note> notes) async {
    if (!_initialized) return;
    try {
      final items = notes
          .map((n) => rust_search.NoteForIndex(
                id: n.id,
                title: n.title,
                plainText: n.content.toPlainText(),
              ))
          .toList();
      await rust_search.rebuildIndex(notes: items);
    } catch (e) {
      debugPrint('[SearchService] rebuildIndex failed: $e');
    }
  }
}
