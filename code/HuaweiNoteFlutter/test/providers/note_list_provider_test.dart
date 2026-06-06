import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/providers/note_list_provider.dart';
import 'package:hwnote/repositories/note_repository.dart';

void main() {
  group('NoteListState', () {
    test('默认状态', () {
      const s = NoteListState();
      expect(s.sortBy, NoteSortBy.updatedDesc);
      expect(s.query, '');
      expect(s.isGridView, false);
      expect(s.isBatchMode, false);
      expect(s.selectedIds, isEmpty);
      expect(s.notes, isEmpty);
      expect(s.isLoading, false);
      expect(s.filterPanelVisible, false);
      expect(s.headerTitle, '全部笔记');
      expect(s.headerSubtitle, '0 条笔记');
    });

    test('copyWith 只更新指定字段', () {
      const s = NoteListState();
      final s2 = s.copyWith(isGridView: true, isBatchMode: true);
      expect(s2.isGridView, true);
      expect(s2.isBatchMode, true);
      expect(s2.sortBy, NoteSortBy.updatedDesc);
    });

    test('copyWith selectedIds', () {
      const s = NoteListState();
      final s2 = s.copyWith(selectedIds: {1, 2, 3});
      expect(s2.selectedIds, {1, 2, 3});
    });

    test('copyWith headerTitle', () {
      const s = NoteListState();
      final s2 = s.copyWith(headerTitle: '收藏', headerSubtitle: '3 条笔记');
      expect(s2.headerTitle, '收藏');
      expect(s2.headerSubtitle, '3 条笔记');
    });
  });
}
