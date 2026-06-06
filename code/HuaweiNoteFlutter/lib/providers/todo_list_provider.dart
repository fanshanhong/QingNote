import 'dart:developer' as dev;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/todo.dart';
import '../repositories/todo_repository.dart';
import '../repositories/folder_repository.dart';
import 'repository_providers.dart';

sealed class TodoListFilter {
  const TodoListFilter();
  static const all = TodoAllFilter();
  static const uncategorized = TodoUncategorizedFilter();
  static const deleted = TodoDeletedFilter();
  static TodoFolderFilter folder(int folderId) => TodoFolderFilter(folderId);
}

class TodoAllFilter extends TodoListFilter {
  const TodoAllFilter();
}

class TodoUncategorizedFilter extends TodoListFilter {
  const TodoUncategorizedFilter();
}

class TodoDeletedFilter extends TodoListFilter {
  const TodoDeletedFilter();
}

class TodoFolderFilter extends TodoListFilter {
  final int folderId;
  const TodoFolderFilter(this.folderId);
}

class TodoListState {
  final TodoListFilter filter;
  final bool hideCompleted;
  final List<Todo> todos;
  final bool isBatchMode;
  final Set<int> selectedIds;
  final bool filterPanelVisible;
  final bool isQuickAddVisible;
  final String headerTitle;
  final String headerSubtitle;

  const TodoListState({
    this.filter = const TodoAllFilter(),
    this.hideCompleted = false,
    this.todos = const [],
    this.isBatchMode = false,
    this.selectedIds = const {},
    this.filterPanelVisible = false,
    this.isQuickAddVisible = false,
    this.headerTitle = '全部待办',
    this.headerSubtitle = '0 条待办',
  });

  TodoListState copyWith({
    TodoListFilter? filter,
    bool? hideCompleted,
    List<Todo>? todos,
    bool? isBatchMode,
    Set<int>? selectedIds,
    bool? filterPanelVisible,
    bool? isQuickAddVisible,
    String? headerTitle,
    String? headerSubtitle,
  }) =>
      TodoListState(
        filter: filter ?? this.filter,
        hideCompleted: hideCompleted ?? this.hideCompleted,
        todos: todos ?? this.todos,
        isBatchMode: isBatchMode ?? this.isBatchMode,
        selectedIds: selectedIds ?? this.selectedIds,
        filterPanelVisible: filterPanelVisible ?? this.filterPanelVisible,
        isQuickAddVisible: isQuickAddVisible ?? this.isQuickAddVisible,
        headerTitle: headerTitle ?? this.headerTitle,
        headerSubtitle: headerSubtitle ?? this.headerSubtitle,
      );
}

class TodoListNotifier extends StateNotifier<TodoListState> {
  final TodoRepository _todoRepo;
  final FolderRepository _folderRepo;

  TodoListNotifier(this._todoRepo, this._folderRepo)
      : super(const TodoListState());

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    final filter = _loadFilter(prefs);
    final hideCompleted = prefs.getBool('hide_completed_todos') ?? false;
    state = state.copyWith(filter: filter, hideCompleted: hideCompleted);
    await reload();
  }

  TodoListFilter _loadFilter(SharedPreferences prefs) {
    final type = prefs.getString('todo_filter_type') ?? 'ALL';
    switch (type) {
      case 'UNCATEGORIZED':
        return TodoListFilter.uncategorized;
      case 'DELETED':
        return TodoListFilter.deleted;
      case 'FOLDER':
        final id = prefs.getInt('todo_filter_folder_id') ?? -1;
        return id > 0 ? TodoListFilter.folder(id) : TodoListFilter.all;
      default:
        return TodoListFilter.all;
    }
  }

  Future<void> _saveFilter(TodoListFilter filter) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('todo_filter_folder_id');
    switch (filter) {
      case TodoAllFilter():
        await prefs.setString('todo_filter_type', 'ALL');
      case TodoUncategorizedFilter():
        await prefs.setString('todo_filter_type', 'UNCATEGORIZED');
      case TodoDeletedFilter():
        await prefs.setString('todo_filter_type', 'DELETED');
      case TodoFolderFilter(:final folderId):
        await prefs.setString('todo_filter_type', 'FOLDER');
        await prefs.setInt('todo_filter_folder_id', folderId);
    }
  }

  Future<void> reload() async {
    try {
      final todos = await _fetchTodos();
      final title = await _computeHeaderTitle();
      state = state.copyWith(
        todos: todos,
        headerTitle: title,
        headerSubtitle: '${todos.length} 条待办',
      );
    } catch (e, st) {
      dev.log('TodoList reload failed', error: e, stackTrace: st);
    }
  }

  Future<List<Todo>> _fetchTodos() async {
    switch (state.filter) {
      case TodoAllFilter():
        return _todoRepo.list(hideCompleted: state.hideCompleted);
      case TodoUncategorizedFilter():
        return _todoRepo.listUncategorized(hideCompleted: state.hideCompleted);
      case TodoDeletedFilter():
        return _todoRepo.list(includeDeleted: true);
      case TodoFolderFilter(:final folderId):
        return _todoRepo.list(
            folderId: folderId, hideCompleted: state.hideCompleted);
    }
  }

  Future<String> _computeHeaderTitle() async {
    switch (state.filter) {
      case TodoAllFilter():
        return '全部待办';
      case TodoUncategorizedFilter():
        return '未分类';
      case TodoDeletedFilter():
        return '最近删除';
      case TodoFolderFilter(:final folderId):
        final folder = await _folderRepo.get(folderId);
        return folder?.name ?? '全部待办';
    }
  }

  Future<void> setFilter(TodoListFilter filter) async {
    state = state.copyWith(filter: filter, filterPanelVisible: false);
    await _saveFilter(filter);
    await reload();
  }

  void toggleFilterPanel() {
    state = state.copyWith(filterPanelVisible: !state.filterPanelVisible);
  }

  Future<void> toggleHideCompleted() async {
    final next = !state.hideCompleted;
    state = state.copyWith(hideCompleted: next);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('hide_completed_todos', next);
    await reload();
  }

  Future<void> toggleComplete(int todoId) async {
    await _todoRepo.completeTodo(todoId);
    await reload();
  }

  Future<void> uncomplete(int todoId) async {
    await _todoRepo.uncompleteTodo(todoId);
    await reload();
  }

  void enterBatchMode() {
    state = state.copyWith(isBatchMode: true, selectedIds: {});
  }

  Future<void> exitBatchMode() async {
    state = state.copyWith(isBatchMode: false, selectedIds: {});
    await reload();
  }

  void toggleBatchSelection(int todoId) {
    final ids = Set<int>.from(state.selectedIds);
    if (ids.contains(todoId)) {
      ids.remove(todoId);
    } else {
      ids.add(todoId);
    }
    state = state.copyWith(selectedIds: ids);
  }

  Future<void> batchDelete() async {
    if (state.selectedIds.isEmpty) return;
    await _todoRepo.softDeleteBatch(state.selectedIds.toList());
    await exitBatchMode();
  }

  Future<void> restore(int todoId) async {
    await _todoRepo.restore(todoId);
    await reload();
  }

  Future<void> deletePermanently(int todoId) async {
    await _todoRepo.deletePermanently(todoId);
    await reload();
  }

  void showQuickAdd() {
    state = state.copyWith(isQuickAddVisible: true);
  }

  void hideQuickAdd() {
    state = state.copyWith(isQuickAddVisible: false);
  }

  Future<void> quickAdd({
    required String title,
    int remindAt = 0,
    bool isImportant = false,
    RepeatType repeatType = RepeatType.none,
  }) async {
    final int? folderId = switch (state.filter) {
      TodoFolderFilter(:final folderId) => folderId,
      _ => null,
    };
    final todo = Todo.newTodo().copyWith(
      title: title,
      remindAt: remindAt,
      isImportant: isImportant,
      repeatType: repeatType,
      folderId: folderId,
      setFolderIdNull: folderId == null,
    );
    await _todoRepo.insert(todo);
    state = state.copyWith(isQuickAddVisible: false);
    await reload();
  }
}

final todoListProvider =
    StateNotifierProvider<TodoListNotifier, TodoListState>((ref) {
  return TodoListNotifier(
    ref.watch(todoRepositoryProvider),
    ref.watch(folderRepositoryProvider),
  );
});
