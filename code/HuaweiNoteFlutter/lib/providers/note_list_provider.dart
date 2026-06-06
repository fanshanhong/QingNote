import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/note.dart';
import '../repositories/note_repository.dart';
import '../repositories/folder_repository.dart';
import '../repositories/notebook_repository.dart';
import 'repository_providers.dart';

class NoteListState {
  final NoteListFilter filter;
  final NoteSortBy sortBy;
  final String query;
  final bool isGridView;
  final bool isBatchMode;
  final Set<int> selectedIds;
  final List<Note> notes;
  final Map<int, String> notebookColorMap;
  final bool isLoading;
  final Set<int> expandedFolders;
  final bool filterPanelVisible;
  final String headerTitle;
  final String headerSubtitle;

  const NoteListState({
    this.filter = const AllFilter(),
    this.sortBy = NoteSortBy.updatedDesc,
    this.query = '',
    this.isGridView = false,
    this.isBatchMode = false,
    this.selectedIds = const {},
    this.notes = const [],
    this.notebookColorMap = const {},
    this.isLoading = false,
    this.expandedFolders = const {},
    this.filterPanelVisible = false,
    this.headerTitle = '全部笔记',
    this.headerSubtitle = '0 条笔记',
  });

  NoteListState copyWith({
    NoteListFilter? filter,
    NoteSortBy? sortBy,
    String? query,
    bool? isGridView,
    bool? isBatchMode,
    Set<int>? selectedIds,
    List<Note>? notes,
    Map<int, String>? notebookColorMap,
    bool? isLoading,
    Set<int>? expandedFolders,
    bool? filterPanelVisible,
    String? headerTitle,
    String? headerSubtitle,
  }) {
    return NoteListState(
      filter: filter ?? this.filter,
      sortBy: sortBy ?? this.sortBy,
      query: query ?? this.query,
      isGridView: isGridView ?? this.isGridView,
      isBatchMode: isBatchMode ?? this.isBatchMode,
      selectedIds: selectedIds ?? this.selectedIds,
      notes: notes ?? this.notes,
      notebookColorMap: notebookColorMap ?? this.notebookColorMap,
      isLoading: isLoading ?? this.isLoading,
      expandedFolders: expandedFolders ?? this.expandedFolders,
      filterPanelVisible: filterPanelVisible ?? this.filterPanelVisible,
      headerTitle: headerTitle ?? this.headerTitle,
      headerSubtitle: headerSubtitle ?? this.headerSubtitle,
    );
  }
}

class NoteListNotifier extends StateNotifier<NoteListState> {
  final NoteRepository _noteRepo;
  final FolderRepository _folderRepo;
  final NotebookRepository _notebookRepo;

  NoteListNotifier(this._noteRepo, this._folderRepo, this._notebookRepo)
      : super(const NoteListState());

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    final sortName = prefs.getString('sort_by') ?? 'updatedDesc';
    final sortBy = sortName == 'createdDesc'
        ? NoteSortBy.createdDesc
        : NoteSortBy.updatedDesc;
    final isGridView = prefs.getBool('note_grid_view') ?? false;
    final filter = await _loadFilter(prefs);
    state = state.copyWith(sortBy: sortBy, isGridView: isGridView, filter: filter);
    await reload();
  }

  Future<NoteListFilter> _loadFilter(SharedPreferences prefs) async {
    final type = prefs.getString('filter_type') ?? 'ALL';
    switch (type) {
      case 'UNCATEGORIZED':
        return NoteListFilter.uncategorized;
      case 'FAVORITE':
        return NoteListFilter.favorite;
      case 'DELETED':
        return NoteListFilter.deleted;
      case 'FOLDER':
        final id = prefs.getInt('filter_folder_id') ?? -1;
        if (id > 0 && await _folderRepo.get(id) != null) {
          return NoteListFilter.folder(id);
        }
        return NoteListFilter.all;
      case 'NOTEBOOK':
        final id = prefs.getInt('filter_notebook_id') ?? -1;
        if (id > 0 && await _notebookRepo.get(id) != null) {
          return NoteListFilter.notebook(id);
        }
        return NoteListFilter.all;
      default:
        return NoteListFilter.all;
    }
  }

  Future<void> _saveFilter(NoteListFilter filter) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('filter_folder_id');
    await prefs.remove('filter_notebook_id');
    switch (filter) {
      case AllFilter():
        await prefs.setString('filter_type', 'ALL');
      case UncategorizedFilter():
        await prefs.setString('filter_type', 'UNCATEGORIZED');
      case FavoriteFilter():
        await prefs.setString('filter_type', 'FAVORITE');
      case DeletedFilter():
        await prefs.setString('filter_type', 'DELETED');
      case FolderFilter(:final folderId):
        await prefs.setString('filter_type', 'FOLDER');
        await prefs.setInt('filter_folder_id', folderId);
      case NotebookFilter(:final notebookId):
        await prefs.setString('filter_type', 'NOTEBOOK');
        await prefs.setInt('filter_notebook_id', notebookId);
    }
  }

  Future<void> reload() async {
    state = state.copyWith(isLoading: true);
    final filter = await _validateFilter(state.filter);
    final notes = await _noteRepo.list(
      filter: filter,
      sortBy: state.sortBy,
      query: state.query.isEmpty ? null : state.query,
    );
    final colorMap = <int, String>{};
    final nbIds = notes.map((n) => n.notebookId).whereType<int>().toSet();
    for (final id in nbIds) {
      final nb = await _notebookRepo.get(id);
      if (nb != null) colorMap[id] = nb.color;
    }
    final title = await _computeHeaderTitle(filter);
    final subtitle = await _computeHeaderSubtitle(filter, notes.length);
    state = state.copyWith(
      filter: filter,
      notes: notes,
      notebookColorMap: colorMap,
      isLoading: false,
      headerTitle: title,
      headerSubtitle: subtitle,
    );
  }

  Future<NoteListFilter> _validateFilter(NoteListFilter filter) async {
    switch (filter) {
      case FolderFilter(:final folderId):
        if (await _folderRepo.get(folderId) == null) {
          await _saveFilter(NoteListFilter.all);
          return NoteListFilter.all;
        }
        return filter;
      case NotebookFilter(:final notebookId):
        if (await _notebookRepo.get(notebookId) == null) {
          await _saveFilter(NoteListFilter.all);
          return NoteListFilter.all;
        }
        return filter;
      case AllFilter():
        return filter;
      case UncategorizedFilter():
        return filter;
      case FavoriteFilter():
        return filter;
      case DeletedFilter():
        return filter;
    }
  }

  Future<String> _computeHeaderTitle(NoteListFilter filter) async {
    return switch (filter) {
      AllFilter() => '全部笔记',
      UncategorizedFilter() => '未分类',
      FavoriteFilter() => '收藏',
      DeletedFilter() => '最近删除',
      FolderFilter(:final folderId) =>
        (await _folderRepo.get(folderId))?.name ?? '全部笔记',
      NotebookFilter(:final notebookId) =>
        (await _notebookRepo.get(notebookId))?.name ?? '全部笔记',
    };
  }

  Future<String> _computeHeaderSubtitle(
    NoteListFilter filter,
    int count,
  ) async {
    if (filter is NotebookFilter) {
      final nb = await _notebookRepo.get(filter.notebookId);
      if (nb != null) {
        final folder = await _folderRepo.get(nb.folderId);
        if (folder != null) return '$count 条笔记 · ${folder.name}';
      }
    }
    return '$count 条笔记';
  }

  Future<void> setFilter(NoteListFilter filter) async {
    state = state.copyWith(filter: filter, filterPanelVisible: false);
    await _saveFilter(filter);
    await reload();
  }

  Future<void> setSort(NoteSortBy sortBy) async {
    state = state.copyWith(sortBy: sortBy);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('sort_by', sortBy.name);
    await reload();
  }

  Future<void> setQuery(String query) async {
    state = state.copyWith(query: query);
    await reload();
  }

  Future<void> toggleGridView() async {
    final next = !state.isGridView;
    state = state.copyWith(isGridView: next);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('note_grid_view', next);
  }

  void toggleFilterPanel() {
    state = state.copyWith(filterPanelVisible: !state.filterPanelVisible);
  }

  void toggleFolderExpand(int folderId) {
    final expanded = Set<int>.from(state.expandedFolders);
    if (expanded.contains(folderId)) {
      expanded.remove(folderId);
    } else {
      expanded.add(folderId);
    }
    state = state.copyWith(expandedFolders: expanded);
  }

  void enterBatchMode() {
    state = state.copyWith(isBatchMode: true, selectedIds: {});
  }

  Future<void> exitBatchMode() async {
    state = state.copyWith(isBatchMode: false, selectedIds: {});
    await reload();
  }

  void toggleSelection(int noteId) {
    final ids = Set<int>.from(state.selectedIds);
    if (ids.contains(noteId)) {
      ids.remove(noteId);
    } else {
      ids.add(noteId);
    }
    state = state.copyWith(selectedIds: ids);
  }

  Future<void> batchDelete() async {
    if (state.selectedIds.isEmpty) return;
    await _noteRepo.softDeleteBatch(state.selectedIds.toList());
    await exitBatchMode();
  }

  Future<void> softDelete(int id) async {
    await _noteRepo.softDelete(id);
    await reload();
  }

  Future<void> toggleFavorite(int id) async {
    final note = state.notes.where((n) => n.id == id).firstOrNull;
    if (note == null) return;
    await _noteRepo.setFavorite(id, !note.isFavorite);
    await reload();
  }

  Future<void> restore(int id) async {
    await _noteRepo.restore(id);
    await reload();
  }

  Future<void> deletePermanently(int id) async {
    await _noteRepo.deletePermanently(id);
    await reload();
  }
}

final noteListProvider =
    StateNotifierProvider<NoteListNotifier, NoteListState>((ref) {
  return NoteListNotifier(
    ref.watch(noteRepositoryProvider),
    ref.watch(folderRepositoryProvider),
    ref.watch(notebookRepositoryProvider),
  );
});
