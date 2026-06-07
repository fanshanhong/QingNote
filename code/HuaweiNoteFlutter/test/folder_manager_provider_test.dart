import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/providers/folder_manager_provider.dart';
import 'package:hwnote/models/folder.dart';
import 'package:hwnote/models/notebook.dart';

void main() {
  group('FolderManagerRow', () {
    test('FolderHeadRow holds folder and expanded state', () {
      const folder = Folder(id: 1, name: '默认');
      const row = FolderHeadRow(folder: folder, expanded: true);
      expect(row.folder.name, '默认');
      expect(row.expanded, true);
    });

    test('NotebookItemRow holds notebook', () {
      const nb = Notebook(id: 1, name: '日记', folderId: 1);
      const row = NotebookItemRow(notebook: nb);
      expect(row.notebook.name, '日记');
    });

    test('CreateNotebookRow holds folderId', () {
      const row = CreateNotebookRow(folderId: 2);
      expect(row.folderId, 2);
    });
  });

  group('FolderManagerState', () {
    test('initial state has empty rows and default folder 1 expanded', () {
      const state = FolderManagerState();
      expect(state.rows, isEmpty);
      expect(state.expandedFolders, {1});
      expect(state.isLoading, false);
    });

    test('copyWith preserves unchanged fields', () {
      const state = FolderManagerState(isLoading: true);
      final copy = state.copyWith(isLoading: false);
      expect(copy.isLoading, false);
      expect(copy.expandedFolders, {1});
    });
  });
}
