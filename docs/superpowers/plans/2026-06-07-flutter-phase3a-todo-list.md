# Phase 3A：Flutter 待办列表页 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 TodoPlaceholderPage 替换为完整的待办列表页，包含分组列表、筛选面板、快速新增、批量模式、删除视图。

**Architecture:** Riverpod StateNotifier 管理状态（参照已有 NoteListNotifier 模式），TodoRepository/FolderRepository 提供数据，UI 层拆分为多个聚焦 widget。

**Tech Stack:** Flutter + Riverpod + shared_preferences + sqflite（已有） + CupertinoDatePicker

---

### Task 1: 分组工具函数 + 单元测试

**Files:**
- Create: `lib/utils/todo_group_utils.dart`
- Create: `test/todo_group_utils_test.dart`

- [ ] **Step 1: 写 todo_group_utils.dart — 分组算法**

```dart
// lib/utils/todo_group_utils.dart
import '../models/todo.dart';

enum TodoSection { overdue, today, tomorrow, later, noDate, completed }

class TodoGroupItem {
  final TodoSection section;
  final String label;
  final bool isOverdue;
  final List<Todo> todos;
  const TodoGroupItem({required this.section, required this.label, this.isOverdue = false, required this.todos});
}

List<TodoGroupItem> groupTodos(List<Todo> todos) {
  final now = DateTime.now();
  final todayStart = DateTime(now.year, now.month, now.day).millisecondsSinceEpoch;
  final tomorrowStart = todayStart + 86400000;
  final dayAfterTomorrow = tomorrowStart + 86400000;

  final overdue = <Todo>[];
  final today = <Todo>[];
  final tomorrow = <Todo>[];
  final later = <Todo>[];
  final noDate = <Todo>[];
  final completed = <Todo>[];

  for (final t in todos) {
    if (t.isCompleted) {
      completed.add(t);
    } else if (t.remindAt == 0) {
      noDate.add(t);
    } else if (t.remindAt < todayStart) {
      overdue.add(t);
    } else if (t.remindAt < tomorrowStart) {
      today.add(t);
    } else if (t.remindAt < dayAfterTomorrow) {
      tomorrow.add(t);
    } else {
      later.add(t);
    }
  }

  final groups = <TodoGroupItem>[];
  if (overdue.isNotEmpty) groups.add(TodoGroupItem(section: TodoSection.overdue, label: '已过期', isOverdue: true, todos: overdue));
  if (today.isNotEmpty) groups.add(TodoGroupItem(section: TodoSection.today, label: '今天', todos: today));
  if (tomorrow.isNotEmpty) groups.add(TodoGroupItem(section: TodoSection.tomorrow, label: '明天', todos: tomorrow));
  if (later.isNotEmpty) groups.add(TodoGroupItem(section: TodoSection.later, label: '更晚', todos: later));
  if (noDate.isNotEmpty) groups.add(TodoGroupItem(section: TodoSection.noDate, label: '无日期', todos: noDate));
  if (completed.isNotEmpty) groups.add(TodoGroupItem(section: TodoSection.completed, label: '已完成', todos: completed));
  return groups;
}
```

- [ ] **Step 2: 写单元测试**

```dart
// test/todo_group_utils_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/todo.dart';
import 'package:hwnote/utils/todo_group_utils.dart';

void main() {
  group('groupTodos', () {
    final now = DateTime.now().millisecondsSinceEpoch;
    final todayStart = DateTime(DateTime.now().year, DateTime.now().month, DateTime.now().day).millisecondsSinceEpoch;

    test('empty list returns empty groups', () {
      expect(groupTodos([]), isEmpty);
    });

    test('completed todos go to completed section', () {
      final todos = [
        Todo(id: 1, isCompleted: true, createdAt: now, updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups.length, 1);
      expect(groups[0].section, TodoSection.completed);
    });

    test('no-date todos go to noDate section', () {
      final todos = [
        Todo(id: 1, remindAt: 0, createdAt: now, updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups.length, 1);
      expect(groups[0].section, TodoSection.noDate);
    });

    test('overdue todos marked isOverdue', () {
      final todos = [
        Todo(id: 1, remindAt: todayStart - 86400000, createdAt: now, updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups[0].isOverdue, true);
      expect(groups[0].section, TodoSection.overdue);
    });

    test('today todos grouped correctly', () {
      final todos = [
        Todo(id: 1, remindAt: todayStart + 3600000, createdAt: now, updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups[0].section, TodoSection.today);
    });

    test('multiple sections ordered correctly', () {
      final todos = [
        Todo(id: 1, remindAt: todayStart - 1000, createdAt: now, updatedAt: now),
        Todo(id: 2, remindAt: todayStart + 1000, createdAt: now, updatedAt: now),
        Todo(id: 3, remindAt: 0, createdAt: now, updatedAt: now),
        Todo(id: 4, isCompleted: true, createdAt: now, updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups[0].section, TodoSection.overdue);
      expect(groups[1].section, TodoSection.today);
      expect(groups[2].section, TodoSection.noDate);
      expect(groups[3].section, TodoSection.completed);
    });
  });
}
```

- [ ] **Step 3: 运行测试验证**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test test/todo_group_utils_test.dart`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add lib/utils/todo_group_utils.dart test/todo_group_utils_test.dart
git commit -m "feat(p3a): 添加 Todo 分组工具函数 + 单元测试"
```

---

### Task 2: TodoListProvider 状态管理

**Files:**
- Create: `lib/providers/todo_list_provider.dart`

- [ ] **Step 1: 创建 TodoListFilter 类型**

```dart
// lib/providers/todo_list_provider.dart
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

class TodoAllFilter extends TodoListFilter { const TodoAllFilter(); }
class TodoUncategorizedFilter extends TodoListFilter { const TodoUncategorizedFilter(); }
class TodoDeletedFilter extends TodoListFilter { const TodoDeletedFilter(); }
class TodoFolderFilter extends TodoListFilter {
  final int folderId;
  const TodoFolderFilter(this.folderId);
}
```

- [ ] **Step 2: 创建 TodoListState**

```dart
class TodoListState {
  final TodoListFilter filter;
  final bool hideCompleted;
  final List<Todo> todos;
  final bool isBatchMode;
  final Set<int> selectedIds;
  final bool filterPanelVisible;
  final String headerTitle;
  final String headerSubtitle;

  const TodoListState({
    this.filter = const TodoAllFilter(),
    this.hideCompleted = false,
    this.todos = const [],
    this.isBatchMode = false,
    this.selectedIds = const {},
    this.filterPanelVisible = false,
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
    String? headerTitle,
    String? headerSubtitle,
  }) => TodoListState(
    filter: filter ?? this.filter,
    hideCompleted: hideCompleted ?? this.hideCompleted,
    todos: todos ?? this.todos,
    isBatchMode: isBatchMode ?? this.isBatchMode,
    selectedIds: selectedIds ?? this.selectedIds,
    filterPanelVisible: filterPanelVisible ?? this.filterPanelVisible,
    headerTitle: headerTitle ?? this.headerTitle,
    headerSubtitle: headerSubtitle ?? this.headerSubtitle,
  );
}
```

- [ ] **Step 3: 创建 TodoListNotifier 完整实现**

```dart
class TodoListNotifier extends StateNotifier<TodoListState> {
  final TodoRepository _todoRepo;
  final FolderRepository _folderRepo;

  TodoListNotifier(this._todoRepo, this._folderRepo) : super(const TodoListState());

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
      case 'UNCATEGORIZED': return TodoListFilter.uncategorized;
      case 'DELETED': return TodoListFilter.deleted;
      case 'FOLDER':
        final id = prefs.getInt('todo_filter_folder_id') ?? -1;
        return id > 0 ? TodoListFilter.folder(id) : TodoListFilter.all;
      default: return TodoListFilter.all;
    }
  }

  Future<void> _saveFilter(TodoListFilter filter) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('todo_filter_folder_id');
    switch (filter) {
      case TodoAllFilter(): await prefs.setString('todo_filter_type', 'ALL');
      case TodoUncategorizedFilter(): await prefs.setString('todo_filter_type', 'UNCATEGORIZED');
      case TodoDeletedFilter(): await prefs.setString('todo_filter_type', 'DELETED');
      case TodoFolderFilter(:final folderId):
        await prefs.setString('todo_filter_type', 'FOLDER');
        await prefs.setInt('todo_filter_folder_id', folderId);
    }
  }

  Future<void> reload() async {
    try {
      final todos = await _fetchTodos();
      final title = await _computeHeaderTitle();
      final count = todos.length;
      state = state.copyWith(
        todos: todos,
        headerTitle: title,
        headerSubtitle: '$count 条待办',
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
        return _todoRepo.list(folderId: folderId, hideCompleted: state.hideCompleted);
    }
  }

  Future<String> _computeHeaderTitle() async {
    switch (state.filter) {
      case TodoAllFilter(): return '全部待办';
      case TodoUncategorizedFilter(): return '未分类';
      case TodoDeletedFilter(): return '最近删除';
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
    if (ids.contains(todoId)) { ids.remove(todoId); } else { ids.add(todoId); }
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

  Future<void> quickAdd({
    required String title,
    int remindAt = 0,
    bool isImportant = false,
    RepeatType repeatType = RepeatType.none,
  }) async {
    final folderId = switch (state.filter) {
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
```

- [ ] **Step 4: Commit**

```bash
git add lib/providers/todo_list_provider.dart
git commit -m "feat(p3a): 添加 TodoListProvider 状态管理"
```

---

### Task 3: TodoCard + TodoSectionHeader 组件

**Files:**
- Create: `lib/widgets/todo/todo_card.dart`
- Create: `lib/widgets/todo/todo_section_header.dart`

- [ ] **Step 1: 写 TodoSectionHeader**

```dart
// lib/widgets/todo/todo_section_header.dart
import 'package:flutter/material.dart';
import '../../theme.dart';

class TodoSectionHeader extends StatelessWidget {
  final String title;
  final bool isOverdue;

  const TodoSectionHeader({super.key, required this.title, this.isOverdue = false});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppDimens.spacingL, AppDimens.spacingXl, AppDimens.spacingL, AppDimens.spacingS),
      child: Text(
        title,
        style: TextStyle(
          fontSize: AppDimens.textBody,
          fontWeight: FontWeight.bold,
          color: isOverdue ? AppColors.danger : AppColors.textSecondary,
        ),
      ),
    );
  }
}
```

- [ ] **Step 2: 写 TodoCard**

```dart
// lib/widgets/todo/todo_card.dart
import 'package:flutter/material.dart';
import '../../models/todo.dart';
import '../../theme.dart';

class TodoCard extends StatelessWidget {
  final Todo todo;
  final bool isBatchMode;
  final bool isSelected;
  final bool isDeletedView;
  final VoidCallback? onCheckToggle;
  final VoidCallback? onTap;
  final VoidCallback? onBatchToggle;
  final VoidCallback? onRestore;
  final VoidCallback? onDeletePermanently;

  const TodoCard({
    super.key,
    required this.todo,
    this.isBatchMode = false,
    this.isSelected = false,
    this.isDeletedView = false,
    this.onCheckToggle,
    this.onTap,
    this.onBatchToggle,
    this.onRestore,
    this.onDeletePermanently,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: isBatchMode ? onBatchToggle : (isDeletedView ? null : onTap),
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: AppDimens.spacingXs),
        padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: AppDimens.spacingM),
        decoration: BoxDecoration(
          color: AppColors.bgCard,
          borderRadius: BorderRadius.circular(AppDimens.radiusCard),
        ),
        child: Row(
          children: [
            _buildLeading(),
            const SizedBox(width: AppDimens.spacingM),
            Expanded(child: _buildContent(context)),
            if (isDeletedView) _buildDeletedActions(),
          ],
        ),
      ),
    );
  }

  Widget _buildLeading() {
    if (isBatchMode) {
      return Icon(
        isSelected ? Icons.check_circle : Icons.radio_button_unchecked,
        size: 24,
        color: isSelected ? AppColors.primary : AppColors.textHint,
      );
    }
    if (isDeletedView) return const SizedBox.shrink();
    return GestureDetector(
      onTap: onCheckToggle,
      child: Icon(
        todo.isCompleted ? Icons.check_circle_outline : Icons.radio_button_unchecked,
        size: 24,
        color: todo.isCompleted ? AppColors.primary : AppColors.textHint,
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    final titleWidget = _buildTitle();
    final subtitle = _buildSubtitle();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        titleWidget,
        if (subtitle != null) ...[
          const SizedBox(height: 2),
          subtitle,
        ],
      ],
    );
  }

  Widget _buildTitle() {
    final title = todo.title.isEmpty ? '待办事项' : todo.title;
    if (todo.isImportant && !todo.isCompleted) {
      return RichText(text: TextSpan(children: [
        TextSpan(text: '❗', style: TextStyle(color: AppColors.danger, fontSize: AppDimens.textBody)),
        TextSpan(
          text: title,
          style: TextStyle(fontSize: AppDimens.textBody, color: AppColors.textPrimary),
        ),
      ]));
    }
    return Text(
      title,
      style: TextStyle(
        fontSize: AppDimens.textBody,
        color: todo.isCompleted ? AppColors.textHint : AppColors.textPrimary,
        decoration: todo.isCompleted ? TextDecoration.lineThrough : null,
      ),
    );
  }

  Widget? _buildSubtitle() {
    final parts = <String>[];
    if (todo.remindAt > 0) {
      final dt = DateTime.fromMillisecondsSinceEpoch(todo.remindAt);
      final amPm = dt.hour < 12 ? '上午' : '下午';
      final hour = dt.hour == 0 ? 12 : (dt.hour > 12 ? dt.hour - 12 : dt.hour);
      final minute = dt.minute.toString().padLeft(2, '0');
      parts.add('$amPm$hour:$minute');
    }
    if (todo.repeatType != RepeatType.none) {
      parts.add(switch (todo.repeatType) {
        RepeatType.daily => '每天',
        RepeatType.weekly => '每周',
        RepeatType.monthly => '每月',
        RepeatType.yearly => '每年',
        RepeatType.none => '',
      });
    }
    if (parts.isEmpty) return null;
    final isOverdue = todo.remindAt > 0 && todo.remindAt < DateTime.now().millisecondsSinceEpoch && !todo.isCompleted;
    return Row(children: [
      if (todo.repeatType != RepeatType.none)
        Padding(
          padding: const EdgeInsets.only(right: 4),
          child: Icon(Icons.repeat, size: 14, color: isOverdue ? AppColors.danger : AppColors.textHint),
        ),
      Text(
        parts.join(' | '),
        style: TextStyle(fontSize: AppDimens.textCaption, color: isOverdue ? AppColors.danger : AppColors.textHint),
      ),
    ]);
  }

  Widget _buildDeletedActions() {
    return Row(mainAxisSize: MainAxisSize.min, children: [
      TextButton(onPressed: onRestore, child: const Text('恢复', style: TextStyle(fontSize: AppDimens.textCaption))),
      TextButton(
        onPressed: onDeletePermanently,
        child: Text('删除', style: TextStyle(fontSize: AppDimens.textCaption, color: AppColors.danger)),
      ),
    ]);
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add lib/widgets/todo/todo_card.dart lib/widgets/todo/todo_section_header.dart
git commit -m "feat(p3a): 添加 TodoCard + TodoSectionHeader 组件"
```

---

### Task 4: DateTimePicker + RepeatPicker BottomSheet

**Files:**
- Create: `lib/widgets/todo/date_time_picker_sheet.dart`
- Create: `lib/widgets/todo/repeat_picker_sheet.dart`

- [ ] **Step 1: 写 DateTimePickerSheet**

```dart
// lib/widgets/todo/date_time_picker_sheet.dart
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import '../../theme.dart';

Future<int?> showDateTimePickerSheet(BuildContext context, {int initialEpochMs = 0}) {
  return showModalBottomSheet<int>(
    context: context,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (ctx) => _DateTimePickerContent(initialEpochMs: initialEpochMs),
  );
}

class _DateTimePickerContent extends StatefulWidget {
  final int initialEpochMs;
  const _DateTimePickerContent({required this.initialEpochMs});
  @override
  State<_DateTimePickerContent> createState() => _DateTimePickerContentState();
}

class _DateTimePickerContentState extends State<_DateTimePickerContent> {
  late DateTime _selected;

  @override
  void initState() {
    super.initState();
    if (widget.initialEpochMs > 0) {
      _selected = DateTime.fromMillisecondsSinceEpoch(widget.initialEpochMs);
    } else {
      final now = DateTime.now().add(const Duration(hours: 1));
      final minute = (now.minute / 5).ceil() * 5;
      _selected = DateTime(now.year, now.month, now.day, now.hour, minute);
    }
  }

  String _formatHeader() {
    const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
    final wd = weekdays[_selected.weekday - 1];
    return '${_selected.year}年${_selected.month}月${_selected.day}日星期$wd';
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: AppDimens.spacingL),
          Text(_formatHeader(), style: const TextStyle(fontSize: AppDimens.textTitle, fontWeight: FontWeight.bold)),
          SizedBox(
            height: 200,
            child: CupertinoDatePicker(
              mode: CupertinoDatePickerMode.dateAndTime,
              initialDateTime: _selected,
              minimumDate: DateTime.now().subtract(const Duration(days: 365)),
              use24hFormat: false,
              onDateTimeChanged: (dt) => setState(() => _selected = dt),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingXl, vertical: AppDimens.spacingL),
            child: Row(children: [
              Expanded(child: TextButton(
                onPressed: () => Navigator.pop(context),
                style: TextButton.styleFrom(
                  backgroundColor: const Color(0xFFF5F5F5),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                child: const Text('取消', style: TextStyle(color: AppColors.textSecondary)),
              )),
              const SizedBox(width: AppDimens.spacingM),
              Expanded(child: TextButton(
                onPressed: () => Navigator.pop(context, _selected.millisecondsSinceEpoch),
                style: TextButton.styleFrom(
                  backgroundColor: AppColors.primary,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                child: const Text('确定', style: TextStyle(color: Colors.white)),
              )),
            ]),
          ),
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: 写 RepeatPickerSheet**

```dart
// lib/widgets/todo/repeat_picker_sheet.dart
import 'package:flutter/material.dart';
import '../../models/todo.dart';
import '../../theme.dart';

Future<RepeatType?> showRepeatPickerSheet(BuildContext context, {RepeatType current = RepeatType.none}) {
  return showModalBottomSheet<RepeatType>(
    context: context,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (ctx) => _RepeatPickerContent(current: current),
  );
}

class _RepeatPickerContent extends StatelessWidget {
  final RepeatType current;
  const _RepeatPickerContent({required this.current});

  @override
  Widget build(BuildContext context) {
    final options = [
      (RepeatType.none, '不重复'),
      (RepeatType.daily, '每天'),
      (RepeatType.weekly, '每周'),
      (RepeatType.monthly, '每月'),
      (RepeatType.yearly, '每年'),
    ];
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(AppDimens.spacingXl, AppDimens.spacingL, AppDimens.spacingXl, AppDimens.spacingS),
            child: Text('重复', style: TextStyle(fontSize: AppDimens.textTitle, fontWeight: FontWeight.bold)),
          ),
          ...options.map((opt) => ListTile(
            title: Text(opt.$2),
            trailing: current == opt.$1
                ? const Icon(Icons.circle, size: 16, color: AppColors.primary)
                : null,
            onTap: () => Navigator.pop(context, opt.$1),
          )),
          Center(child: TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消', style: TextStyle(color: AppColors.primary)),
          )),
          const SizedBox(height: AppDimens.spacingS),
        ],
      ),
    );
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add lib/widgets/todo/date_time_picker_sheet.dart lib/widgets/todo/repeat_picker_sheet.dart
git commit -m "feat(p3a): 添加 DateTimePickerSheet + RepeatPickerSheet"
```

---

### Task 5: QuickAddBar 组件

**Files:**
- Create: `lib/widgets/todo/todo_quick_add_bar.dart`

- [ ] **Step 1: 写 QuickAddBar**

```dart
// lib/widgets/todo/todo_quick_add_bar.dart
import 'package:flutter/material.dart';
import '../../models/todo.dart';
import '../../theme.dart';
import 'date_time_picker_sheet.dart';
import 'repeat_picker_sheet.dart';

class TodoQuickAddBar extends StatefulWidget {
  final void Function(String title, int remindAt, bool isImportant, RepeatType repeatType) onSave;
  final VoidCallback onClose;

  const TodoQuickAddBar({super.key, required this.onSave, required this.onClose});

  @override
  State<TodoQuickAddBar> createState() => _TodoQuickAddBarState();
}

class _TodoQuickAddBarState extends State<TodoQuickAddBar> {
  final _controller = TextEditingController();
  final _focusNode = FocusNode();
  int _remindAt = 0;
  bool _isImportant = false;
  RepeatType _repeatType = RepeatType.none;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _focusNode.requestFocus());
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _pickTime() async {
    final result = await showDateTimePickerSheet(context, initialEpochMs: _remindAt);
    if (result != null) {
      setState(() => _remindAt = result);
    }
  }

  void _pickRepeat() async {
    final result = await showRepeatPickerSheet(context, current: _repeatType);
    if (result != null) {
      setState(() => _repeatType = result);
    }
  }

  void _toggleImportant() {
    setState(() => _isImportant = !_isImportant);
  }

  void _save() {
    final title = _controller.text.trim();
    if (title.isEmpty) return;
    widget.onSave(title, _remindAt, _isImportant, _repeatType);
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(AppDimens.spacingL, AppDimens.spacingS, AppDimens.spacingL, AppDimens.spacingS),
      decoration: const BoxDecoration(
        color: AppColors.bgCard,
        border: Border(top: BorderSide(color: AppColors.divider)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _controller,
              focusNode: _focusNode,
              decoration: const InputDecoration(
                hintText: '待办事项',
                border: InputBorder.none,
                hintStyle: TextStyle(color: AppColors.textHint),
              ),
            ),
            Row(children: [
              IconButton(
                icon: Icon(Icons.access_time, color: _remindAt > 0 ? AppColors.primary : AppColors.textHint),
                onPressed: _pickTime,
              ),
              IconButton(
                icon: Icon(Icons.priority_high, color: _isImportant ? AppColors.primary : AppColors.textHint),
                onPressed: _toggleImportant,
              ),
              if (_remindAt > 0)
                GestureDetector(
                  onTap: _pickRepeat,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      border: Border.all(color: AppColors.divider),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      _repeatLabel(),
                      style: const TextStyle(fontSize: AppDimens.textCaption, color: AppColors.textSecondary),
                    ),
                  ),
                ),
              const Spacer(),
              TextButton(
                onPressed: _save,
                style: TextButton.styleFrom(
                  backgroundColor: AppColors.primary,
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                ),
                child: const Text('保存', style: TextStyle(color: Colors.white, fontSize: AppDimens.textCaption)),
              ),
            ]),
          ],
        ),
      ),
    );
  }

  String _repeatLabel() => switch (_repeatType) {
    RepeatType.none => '不重复',
    RepeatType.daily => '每天',
    RepeatType.weekly => '每周',
    RepeatType.monthly => '每月',
    RepeatType.yearly => '每年',
  };
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/widgets/todo/todo_quick_add_bar.dart
git commit -m "feat(p3a): 添加 TodoQuickAddBar 快速新增栏"
```

---

### Task 6: TodoFilterPanel 筛选面板

**Files:**
- Create: `lib/widgets/todo/todo_filter_panel.dart`

- [ ] **Step 1: 写 TodoFilterPanel**

```dart
// lib/widgets/todo/todo_filter_panel.dart
import 'package:flutter/material.dart';
import '../../models/folder.dart';
import '../../providers/todo_list_provider.dart';
import '../../theme.dart';

class TodoFilterPanel extends StatelessWidget {
  final TodoListFilter currentFilter;
  final int allCount;
  final int uncategorizedCount;
  final int deletedCount;
  final List<Folder> folders;
  final Map<int, int> folderCounts;
  final ValueChanged<TodoListFilter> onFilterSelected;

  const TodoFilterPanel({
    super.key,
    required this.currentFilter,
    required this.allCount,
    required this.uncategorizedCount,
    required this.deletedCount,
    required this.folders,
    required this.folderCounts,
    required this.onFilterSelected,
  });

  bool _isSelected(TodoListFilter filter) {
    return switch ((currentFilter, filter)) {
      (TodoAllFilter(), TodoAllFilter()) => true,
      (TodoUncategorizedFilter(), TodoUncategorizedFilter()) => true,
      (TodoDeletedFilter(), TodoDeletedFilter()) => true,
      (TodoFolderFilter(folderId: final a), TodoFolderFilter(folderId: final b)) => a == b,
      _ => false,
    };
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.symmetric(vertical: AppDimens.spacingS),
      children: [
        _buildRow(context, Icons.list_alt, '全部待办', allCount, TodoListFilter.all),
        _buildRow(context, Icons.article_outlined, '未分类', uncategorizedCount, TodoListFilter.uncategorized),
        _buildRow(context, Icons.delete_outline, '最近删除', deletedCount, TodoListFilter.deleted),
        const Divider(height: 1),
        Padding(
          padding: const EdgeInsets.fromLTRB(AppDimens.spacingL, AppDimens.spacingL, AppDimens.spacingL, AppDimens.spacingS),
          child: Row(children: [
            const Text('文件夹', style: TextStyle(fontSize: AppDimens.textBody, color: AppColors.textSecondary)),
            const Spacer(),
            GestureDetector(
              onTap: () {},
              child: const Text('管理', style: TextStyle(fontSize: AppDimens.textBody, color: AppColors.primary)),
            ),
          ]),
        ),
        ...folders.map((f) => _buildRow(
          context, Icons.folder_outlined, f.name,
          folderCounts[f.id] ?? 0, TodoListFilter.folder(f.id),
        )),
      ],
    );
  }

  Widget _buildRow(BuildContext context, IconData icon, String title, int count, TodoListFilter filter) {
    final selected = _isSelected(filter);
    return ListTile(
      leading: Icon(icon, color: selected ? AppColors.primary : AppColors.textSecondary),
      title: Text(title, style: TextStyle(
        color: selected ? AppColors.primary : AppColors.textPrimary,
        fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
      )),
      trailing: Text('$count', style: TextStyle(color: AppColors.textHint, fontSize: AppDimens.textBody)),
      selected: selected,
      selectedTileColor: AppColors.primaryLight,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      onTap: () => onFilterSelected(filter),
    );
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/widgets/todo/todo_filter_panel.dart
git commit -m "feat(p3a): 添加 TodoFilterPanel 筛选面板"
```

---

### Task 7: TodoListHeader 组件

**Files:**
- Create: `lib/widgets/todo/todo_list_header.dart`

- [ ] **Step 1: 写 TodoListHeader**

```dart
// lib/widgets/todo/todo_list_header.dart
import 'package:flutter/material.dart';
import '../../theme.dart';

class TodoListHeader extends StatelessWidget {
  final String title;
  final String subtitle;
  final bool filterPanelVisible;
  final bool isBatchMode;
  final int batchCount;
  final VoidCallback onTitleTap;
  final VoidCallback onOverflowTap;
  final VoidCallback? onBatchClose;

  const TodoListHeader({
    super.key,
    required this.title,
    required this.subtitle,
    required this.filterPanelVisible,
    this.isBatchMode = false,
    this.batchCount = 0,
    required this.onTitleTap,
    required this.onOverflowTap,
    this.onBatchClose,
  });

  @override
  Widget build(BuildContext context) {
    if (isBatchMode) {
      return _buildBatchHeader();
    }
    return _buildNormalHeader();
  }

  Widget _buildNormalHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppDimens.spacingL, AppDimens.spacingXl, AppDimens.spacingL, AppDimens.spacingS),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(child: GestureDetector(
            onTap: onTitleTap,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  Text(title, style: const TextStyle(
                    fontSize: AppDimens.headerTitleSize,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary,
                  )),
                  const SizedBox(width: 4),
                  AnimatedRotation(
                    turns: filterPanelVisible ? 0.5 : 0,
                    duration: const Duration(milliseconds: 200),
                    child: const Icon(Icons.arrow_drop_down, color: AppColors.textPrimary),
                  ),
                ]),
                const SizedBox(height: 2),
                Text(subtitle, style: const TextStyle(
                  fontSize: AppDimens.textCaption,
                  color: AppColors.textHint,
                )),
              ],
            ),
          )),
          GestureDetector(
            onTap: onOverflowTap,
            child: const Padding(
              padding: EdgeInsets.all(8),
              child: Icon(Icons.more_vert, color: AppColors.textSecondary),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBatchHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppDimens.spacingL, AppDimens.spacingXl, AppDimens.spacingL, AppDimens.spacingS),
      child: Row(children: [
        GestureDetector(
          onTap: onBatchClose,
          child: const Icon(Icons.close, color: AppColors.textPrimary),
        ),
        const SizedBox(width: AppDimens.spacingM),
        Text('已选择 $batchCount 项', style: const TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.bold,
          color: AppColors.textPrimary,
        )),
      ]),
    );
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/widgets/todo/todo_list_header.dart
git commit -m "feat(p3a): 添加 TodoListHeader 组件"
```

---

### Task 8: TodoListPage 主页面

**Files:**
- Create: `lib/pages/todo_list_page.dart`
- Modify: `lib/router.dart` (替换 TodoPlaceholderPage 引用)

- [ ] **Step 1: 写 TodoListPage**

```dart
// lib/pages/todo_list_page.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/todo.dart';
import '../providers/todo_list_provider.dart';
import '../providers/repository_providers.dart';
import '../theme.dart';
import '../utils/todo_group_utils.dart';
import '../widgets/delete_confirm_sheet.dart';
import '../widgets/todo/todo_card.dart';
import '../widgets/todo/todo_filter_panel.dart';
import '../widgets/todo/todo_list_header.dart';
import '../widgets/todo/todo_quick_add_bar.dart';
import '../widgets/todo/todo_section_header.dart';

class TodoListPage extends ConsumerStatefulWidget {
  const TodoListPage({super.key});
  @override
  ConsumerState<TodoListPage> createState() => _TodoListPageState();
}

class _TodoListPageState extends ConsumerState<TodoListPage> {
  bool _quickAddVisible = false;
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_initialized) {
        _initialized = true;
        ref.read(todoListProvider.notifier).init();
      }
    });
  }

  void _showQuickAdd() => setState(() => _quickAddVisible = true);
  void _hideQuickAdd() => setState(() => _quickAddVisible = false);

  void _onQuickAddSave(String title, int remindAt, bool isImportant, RepeatType repeatType) {
    ref.read(todoListProvider.notifier).quickAdd(
      title: title, remindAt: remindAt, isImportant: isImportant, repeatType: repeatType,
    );
    _hideQuickAdd();
  }

  void _showOverflowMenu() {
    final state = ref.read(todoListProvider);
    showMenu(
      context: context,
      position: RelativeRect.fromLTRB(MediaQuery.of(context).size.width - 60, 80, 16, 0),
      items: [
        PopupMenuItem(
          value: 'toggle_completed',
          child: Text(state.hideCompleted ? '显示已完成待办' : '隐藏已完成待办'),
        ),
        const PopupMenuItem(value: 'batch_delete', child: Text('批量删除')),
      ],
    ).then((value) {
      if (value == 'toggle_completed') {
        ref.read(todoListProvider.notifier).toggleHideCompleted();
      } else if (value == 'batch_delete') {
        ref.read(todoListProvider.notifier).enterBatchMode();
      }
    });
  }

  Future<void> _confirmBatchDelete() async {
    final count = ref.read(todoListProvider).selectedIds.length;
    if (count == 0) return;
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '确定删除选中的 $count 条待办？',
      confirmLabel: '删除',
    );
    if (confirmed) {
      ref.read(todoListProvider.notifier).batchDelete();
    }
  }

  Future<void> _confirmDeletePermanently(int todoId) async {
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '彻底删除后不可恢复，确定删除？',
      confirmLabel: '彻底删除',
    );
    if (confirmed) {
      ref.read(todoListProvider.notifier).deletePermanently(todoId);
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(todoListProvider);
    final isDeletedView = state.filter is TodoDeletedFilter;

    return Scaffold(
      backgroundColor: AppColors.bgWindow,
      body: SafeArea(
        child: Column(children: [
          TodoListHeader(
            title: state.headerTitle,
            subtitle: state.headerSubtitle,
            filterPanelVisible: state.filterPanelVisible,
            isBatchMode: state.isBatchMode,
            batchCount: state.selectedIds.length,
            onTitleTap: () => ref.read(todoListProvider.notifier).toggleFilterPanel(),
            onOverflowTap: _showOverflowMenu,
            onBatchClose: () => ref.read(todoListProvider.notifier).exitBatchMode(),
          ),
          Expanded(child: state.filterPanelVisible
            ? _buildFilterPanel(state)
            : _buildTodoList(state, isDeletedView),
          ),
          if (state.isBatchMode)
            _buildBatchBottomBar(state),
          if (_quickAddVisible && !state.isBatchMode)
            TodoQuickAddBar(onSave: _onQuickAddSave, onClose: _hideQuickAdd),
        ]),
      ),
      floatingActionButton: (!state.filterPanelVisible && !state.isBatchMode && !_quickAddVisible && !isDeletedView)
        ? FloatingActionButton(onPressed: _showQuickAdd, child: const Icon(Icons.add))
        : null,
    );
  }

  Widget _buildFilterPanel(TodoListState state) {
    final todoRepo = ref.read(todoRepositoryProvider);
    final folderRepo = ref.read(folderRepositoryProvider);
    return FutureBuilder(
      future: Future.wait([
        todoRepo.count(),
        todoRepo.countUncategorized(),
        todoRepo.count(includeDeleted: true),
        folderRepo.list(),
      ]),
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
        final data = snapshot.data!;
        final allCount = data[0] as int;
        final uncatCount = data[1] as int;
        final delCount = data[2] as int;
        final folders = data[3] as List;
        return TodoFilterPanel(
          currentFilter: state.filter,
          allCount: allCount,
          uncategorizedCount: uncatCount,
          deletedCount: delCount,
          folders: folders.cast(),
          folderCounts: const {},
          onFilterSelected: (f) => ref.read(todoListProvider.notifier).setFilter(f),
        );
      },
    );
  }

  Widget _buildTodoList(TodoListState state, bool isDeletedView) {
    if (state.todos.isEmpty) {
      return Center(child: Text(
        isDeletedView ? '暂无已删除待办' : '暂无待办',
        style: const TextStyle(color: AppColors.textHint, fontSize: AppDimens.textBody),
      ));
    }
    final groups = groupTodos(state.todos);
    return ListView.builder(
      itemCount: _totalItemCount(groups),
      itemBuilder: (context, index) => _buildItem(groups, index, state, isDeletedView),
    );
  }

  int _totalItemCount(List<TodoGroupItem> groups) {
    int count = 0;
    for (final g in groups) { count += 1 + g.todos.length; }
    return count;
  }

  Widget _buildItem(List<TodoGroupItem> groups, int index, TodoListState state, bool isDeletedView) {
    int offset = 0;
    for (final g in groups) {
      if (index == offset) {
        return TodoSectionHeader(title: g.label, isOverdue: g.isOverdue);
      }
      offset++;
      if (index < offset + g.todos.length) {
        final todo = g.todos[index - offset];
        return TodoCard(
          todo: todo,
          isBatchMode: state.isBatchMode,
          isSelected: state.selectedIds.contains(todo.id),
          isDeletedView: isDeletedView,
          onCheckToggle: () {
            if (todo.isCompleted) {
              ref.read(todoListProvider.notifier).uncomplete(todo.id);
            } else {
              ref.read(todoListProvider.notifier).toggleComplete(todo.id);
            }
          },
          onTap: () {},
          onBatchToggle: () => ref.read(todoListProvider.notifier).toggleBatchSelection(todo.id),
          onRestore: () => ref.read(todoListProvider.notifier).restore(todo.id),
          onDeletePermanently: () => _confirmDeletePermanently(todo.id),
        );
      }
      offset += g.todos.length;
    }
    return const SizedBox.shrink();
  }

  Widget _buildBatchBottomBar(TodoListState state) {
    return Container(
      padding: const EdgeInsets.all(AppDimens.spacingL),
      decoration: const BoxDecoration(
        color: AppColors.bgCard,
        border: Border(top: BorderSide(color: AppColors.divider)),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          width: double.infinity,
          child: TextButton(
            onPressed: state.selectedIds.isEmpty ? null : _confirmBatchDelete,
            style: TextButton.styleFrom(
              backgroundColor: state.selectedIds.isEmpty ? AppColors.divider : AppColors.danger,
              padding: const EdgeInsets.symmetric(vertical: 12),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            ),
            child: Text('删除 (${state.selectedIds.length})',
              style: TextStyle(color: state.selectedIds.isEmpty ? AppColors.textHint : Colors.white)),
          ),
        ),
      ),
    );
  }
}
```

- [ ] **Step 2: 更新 router.dart — 替换 placeholder**

将 `router.dart` 中：
- 导入 `todo_placeholder_page.dart` → 改为导入 `todo_list_page.dart`
- `TodoPlaceholderPage()` → 改为 `TodoListPage()`
- 同时让 bottomNavVisible 也考虑 todo 批量模式

```dart
// router.dart 修改点：
// import 'pages/todo_placeholder_page.dart'; → import 'pages/todo_list_page.dart';
// builder: (context, state) => const TodoPlaceholderPage(), → builder: (context, state) => const TodoListPage(),
```

- [ ] **Step 3: 更新 AppShell bottomNavVisible 逻辑**

在 `router.dart` 的 StatefulShellRoute builder 中，让 bottomNavVisible 也监听 todoListProvider 的 isBatchMode：

```dart
builder: (context, state, navigationShell) {
  return Consumer(
    builder: (context, ref, _) {
      final noteBatch = ref.watch(noteListProvider.select((s) => s.isBatchMode));
      final todoBatch = ref.watch(todoListProvider.select((s) => s.isBatchMode));
      return AppShell(
        currentIndex: navigationShell.currentIndex,
        onTabChanged: (index) { ... },
        bottomNavVisible: !noteBatch && !todoBatch,
        child: navigationShell,
      );
    },
  );
},
```

- [ ] **Step 4: Commit**

```bash
git add lib/pages/todo_list_page.dart lib/router.dart
git commit -m "feat(p3a): 添加 TodoListPage 主页面 + 路由替换"
```

---

### Task 9: 完成动画 + 筛选面板文件夹计数 + 边缘情况修复

**Files:**
- Modify: `lib/widgets/todo/todo_card.dart` (添加完成动画)
- Modify: `lib/pages/todo_list_page.dart` (筛选面板文件夹计数)

- [ ] **Step 1: TodoCard 添加完成时淡出+右滑动画**

在 TodoCard 的 checkbox 点击时，如果是从未完成→完成，使用 `AnimatedContainer` 或让父级 `TodoListPage` 管理动画状态。最简方案：在 onCheckToggle 回调中直接交给 provider 处理，视觉上列表刷新后完成项移动到"已完成"组即可（与 Android 一致，完成时该行消失，重新出现在"已完成"组）。

如需显式动画，在 TodoListPage 中使用 `AnimatedList` 替代普通 ListView，或在 TodoCard 中包裹 `SlideTransition`。

实际方案：在 TodoCard 中增加一个 `onCompleteAnimate` 回调机制 —— 点击 checkbox 时先播放动画再触发 state 变更：

```dart
// 在 _TodoListPageState 中管理一个 _animatingId
int? _animatingId;

// 当 checkbox 点击（未完成→完成）时：
void _onCheckComplete(int todoId) {
  setState(() => _animatingId = todoId);
  Future.delayed(const Duration(milliseconds: 300), () {
    setState(() => _animatingId = null);
    ref.read(todoListProvider.notifier).toggleComplete(todoId);
  });
}
```

TodoCard 根据 `isAnimatingOut` 属性播放 opacity + translate 动画：
```dart
// 添加 isAnimatingOut 属性
final bool isAnimatingOut;
// 在 build 中用 AnimatedOpacity + AnimatedSlide 包裹
```

- [ ] **Step 2: 筛选面板加载各文件夹的待办计数**

在 `_buildFilterPanel` 中，对每个文件夹查询 count 并传递 `folderCounts`：

```dart
final folders = data[3] as List<Folder>;
final folderCounts = <int, int>{};
for (final f in folders) {
  folderCounts[f.id] = await todoRepo.count(folderId: f.id);
}
```

由于 `FutureBuilder` 内不宜做多次 await，改为一次性构建查询列表。

- [ ] **Step 3: QuickAddBar 显示时隐藏底部导航**

在 `router.dart` 中，让 AppShell 接收一个额外的 quickAddVisible 信号来隐藏 bottom nav（或通过一个全局 provider）。最简方案：将 `_quickAddVisible` 状态提升到 todoListProvider 中（添加 `isQuickAddVisible` 字段），这样 router 中可以监听它。

- [ ] **Step 4: 运行 flutter analyze + 测试**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/`
Run: `flutter test`

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat(p3a): 完善完成动画 + 筛选面板计数 + QuickAdd 隐藏导航"
```

---

### Task 10: 删除 TodoPlaceholderPage + 最终验证

**Files:**
- Delete: `lib/pages/todo_placeholder_page.dart`
- Modify: `lib/router.dart` (确认无 placeholder 引用)

- [ ] **Step 1: 删除 todo_placeholder_page.dart**

```bash
rm lib/pages/todo_placeholder_page.dart
```

- [ ] **Step 2: 确认无编译错误**

Run: `flutter analyze lib/`
Expected: No issues found

- [ ] **Step 3: 运行全部单元测试**

Run: `flutter test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add lib/pages/todo_placeholder_page.dart lib/router.dart
git commit -m "feat(p3a): 删除 TodoPlaceholderPage，待办列表页完成"
```

---

## 总结

| Task | 内容 | 预估 |
|------|------|------|
| 1 | 分组工具函数 + 单测 | 5 min |
| 2 | TodoListProvider 状态管理 | 8 min |
| 3 | TodoCard + SectionHeader | 8 min |
| 4 | DateTimePicker + RepeatPicker | 6 min |
| 5 | QuickAddBar | 5 min |
| 6 | TodoFilterPanel | 5 min |
| 7 | TodoListHeader | 4 min |
| 8 | TodoListPage 主页面 + 路由 | 10 min |
| 9 | 动画 + 计数 + 边缘修复 | 8 min |
| 10 | 清理 + 最终验证 | 3 min |
