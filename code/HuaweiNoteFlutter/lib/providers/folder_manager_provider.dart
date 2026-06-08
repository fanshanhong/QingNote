import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/folder.dart';
import '../models/notebook.dart';
import '../repositories/folder_repository.dart';
import '../repositories/notebook_repository.dart';
import 'repository_providers.dart';

sealed class FolderManagerRow {
  const FolderManagerRow();
}

class FolderHeadRow extends FolderManagerRow {
  final Folder folder;
  final bool expanded;
  const FolderHeadRow({required this.folder, required this.expanded});
}

class NotebookItemRow extends FolderManagerRow {
  final Notebook notebook;
  const NotebookItemRow({required this.notebook});
}

class CreateNotebookRow extends FolderManagerRow {
  final int folderId;
  const CreateNotebookRow({required this.folderId});
}

class FolderManagerState {
  final List<FolderManagerRow> rows;
  final Set<int> expandedFolders;
  final bool isLoading;

  const FolderManagerState({
    this.rows = const [],
    this.expandedFolders = const {1},
    this.isLoading = false,
  });

  FolderManagerState copyWith({
    List<FolderManagerRow>? rows,
    Set<int>? expandedFolders,
    bool? isLoading,
  }) {
    return FolderManagerState(
      rows: rows ?? this.rows,
      expandedFolders: expandedFolders ?? this.expandedFolders,
      isLoading: isLoading ?? this.isLoading,
    );
  }
}

class FolderManagerNotifier extends StateNotifier<FolderManagerState> {
  final FolderRepository _folderRepo;
  final NotebookRepository _notebookRepo;
  bool _hasChanges = false;

  bool get hasChanges => _hasChanges;

  FolderManagerNotifier(this._folderRepo, this._notebookRepo)
      : super(const FolderManagerState());

  Future<void> load() async {
    state = state.copyWith(isLoading: true);
    final folders = await _folderRepo.list();
    final rows = <FolderManagerRow>[];
    for (final folder in folders) {
      final expanded = state.expandedFolders.contains(folder.id);
      rows.add(FolderHeadRow(folder: folder, expanded: expanded));
      if (expanded) {
        final notebooks = await _notebookRepo.listByFolder(folder.id);
        for (final nb in notebooks) {
          rows.add(NotebookItemRow(notebook: nb));
        }
        rows.add(CreateNotebookRow(folderId: folder.id));
      }
    }
    state = state.copyWith(rows: rows, isLoading: false);
  }

  void toggleExpand(int folderId) {
    final expanded = Set<int>.from(state.expandedFolders);
    if (expanded.contains(folderId)) {
      expanded.remove(folderId);
    } else {
      expanded.add(folderId);
    }
    state = state.copyWith(expandedFolders: expanded);
    load();
  }

  Future<void> createFolder(String name) async {
    await _folderRepo.insert(name);
    _hasChanges = true;
    await load();
  }

  Future<void> renameFolder(int id, String name) async {
    await _folderRepo.rename(id, name);
    _hasChanges = true;
    await load();
  }

  Future<void> deleteFolder(int id) async {
    await _folderRepo.softDelete(id);
    final expanded = Set<int>.from(state.expandedFolders)..remove(id);
    state = state.copyWith(expandedFolders: expanded);
    _hasChanges = true;
    await load();
  }

  Future<void> createNotebook(int folderId, String name, String color) async {
    await _notebookRepo.insert(folderId, name, color);
    _hasChanges = true;
    await load();
  }

  Future<void> renameNotebook(int id, String name, String color) async {
    await _notebookRepo.rename(id, name);
    await _notebookRepo.updateColor(id, color);
    _hasChanges = true;
    await load();
  }

  Future<void> moveNotebook(int id, int targetFolderId) async {
    await _notebookRepo.move(id, targetFolderId);
    _hasChanges = true;
    await load();
  }

  Future<void> deleteNotebook(int id) async {
    await _notebookRepo.softDelete(id);
    _hasChanges = true;
    await load();
  }

  Future<void> onReorder(int oldIndex, int newIndex) async {
    if (oldIndex < newIndex) newIndex--;
    final rows = state.rows;
    if (oldIndex < 0 || oldIndex >= rows.length) return;
    if (newIndex < 0 || newIndex >= rows.length) return;
    final moving = rows[oldIndex];

    if (moving is FolderHeadRow) {
      if (moving.folder.id == 1) return;
      final segStart = oldIndex;
      var segEnd = oldIndex + 1;
      while (segEnd < rows.length && rows[segEnd] is! FolderHeadRow) {
        segEnd++;
      }
      final segment = rows.sublist(segStart, segEnd);
      final newRows = List<FolderManagerRow>.from(rows);
      newRows.removeRange(segStart, segEnd);
      var insertAt =
          newIndex > oldIndex ? newIndex - segment.length + 1 : newIndex;
      if (insertAt < 0) {
        insertAt = 0;
      }
      if (insertAt > 0 && newRows[insertAt - 1] is FolderHeadRow) {
        final prev = newRows[insertAt - 1] as FolderHeadRow;
        if (prev.folder.id == 1 && insertAt - 1 == 0 && newIndex < oldIndex) {
          return;
        }
      }
      newRows.insertAll(insertAt, segment);
      state = state.copyWith(rows: newRows);
      final folderOrder = <int>[];
      for (final r in newRows) {
        if (r is FolderHeadRow) folderOrder.add(r.folder.id);
      }
      await _folderRepo.reorder(folderOrder);
      _hasChanges = true;
      await load();
      return;
    }

    if (moving is NotebookItemRow) {
      final folderId = moving.notebook.folderId;
      final notebookIndices = <int>[];
      for (var i = 0; i < rows.length; i++) {
        final r = rows[i];
        if (r is NotebookItemRow && r.notebook.folderId == folderId) {
          notebookIndices.add(i);
        }
      }
      if (!notebookIndices.contains(oldIndex)) return;
      if (newIndex < notebookIndices.first || newIndex > notebookIndices.last) {
        return;
      }
      final newRows = List<FolderManagerRow>.from(rows);
      final item = newRows.removeAt(oldIndex);
      newRows.insert(newIndex, item);
      state = state.copyWith(rows: newRows);
      final orderedIds = <int>[];
      for (final r in newRows) {
        if (r is NotebookItemRow && r.notebook.folderId == folderId) {
          orderedIds.add(r.notebook.id);
        }
      }
      await _notebookRepo.reorderInFolder(folderId, orderedIds);
      _hasChanges = true;
      await load();
    }
  }
}

final folderManagerProvider = StateNotifierProvider.autoDispose<
    FolderManagerNotifier, FolderManagerState>((ref) {
  return FolderManagerNotifier(
    ref.watch(folderRepositoryProvider),
    ref.watch(notebookRepositoryProvider),
  );
});
