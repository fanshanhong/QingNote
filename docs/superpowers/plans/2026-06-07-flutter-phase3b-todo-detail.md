# Phase 3B: 待办详情编辑页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the todo detail/edit page that allows creating new todos and editing existing ones, with auto-save on exit.

**Architecture:** Riverpod `StateNotifierProvider.autoDispose.family` keyed by todoId (same pattern as NoteEditorProvider). The page is a full-screen route `/todo/:todoId` with form fields for all Todo properties, a folder picker dialog, and a bottom bar with share/delete actions. Auto-save triggers on pop/deactivate.

**Tech Stack:** Flutter, Riverpod, go_router, sqflite, share_plus (for system share)

---

## File Structure

| File | Responsibility |
|---|---|
| `lib/providers/todo_detail_provider.dart` | State + Notifier for detail page |
| `lib/pages/todo_detail_page.dart` | UI: form layout, bottom bar, lifecycle handling |
| `lib/router.dart` (modify) | Add `/todo/:todoId` route |
| `lib/pages/todo_list_page.dart` (modify) | Wire TodoCard onTap to navigate |
| `test/todo_detail_provider_test.dart` | Unit tests for provider logic |

---

### Task 1: TodoDetailProvider 状态管理

**Files:**
- Create: `lib/providers/todo_detail_provider.dart`
- Test: `test/todo_detail_provider_test.dart`

- [ ] **Step 1: Write the test file with initial tests**

```dart
// test/todo_detail_provider_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:huawei_note_flutter/models/todo.dart';
import 'package:huawei_note_flutter/providers/todo_detail_provider.dart';

void main() {
  group('TodoDetailState', () {
    test('initial state for new todo has default values', () {
      final state = TodoDetailState.initial(todoId: 0);
      expect(state.todoId, 0);
      expect(state.title, '');
      expect(state.memo, '');
      expect(state.isCompleted, false);
      expect(state.isImportant, false);
      expect(state.remindAt, 0);
      expect(state.repeatType, RepeatType.none);
      expect(state.folderId, null);
      expect(state.folderName, '未分类');
      expect(state.isLoaded, false);
    });

    test('initial state for existing todo has correct todoId', () {
      final state = TodoDetailState.initial(todoId: 42);
      expect(state.todoId, 42);
      expect(state.isLoaded, false);
    });

    test('remindTimeText returns placeholder when no remind', () {
      final state = TodoDetailState.initial(todoId: 0);
      expect(state.remindTimeText, '添加提醒');
    });

    test('remindTimeText shows formatted time when set', () {
      final dt = DateTime(2026, 6, 7, 14, 30);
      final state = TodoDetailState.initial(todoId: 0).copyWith(
        remindAt: dt.millisecondsSinceEpoch,
      );
      expect(state.remindTimeText, contains('2:30'));
    });

    test('repeatTypeText returns correct labels', () {
      final state = TodoDetailState.initial(todoId: 0);
      expect(state.copyWith(repeatType: RepeatType.none).repeatTypeText, '不重复');
      expect(state.copyWith(repeatType: RepeatType.daily).repeatTypeText, '每天');
      expect(state.copyWith(repeatType: RepeatType.weekly).repeatTypeText, '每周');
      expect(state.copyWith(repeatType: RepeatType.monthly).repeatTypeText, '每月');
      expect(state.copyWith(repeatType: RepeatType.yearly).repeatTypeText, '每年');
    });

    test('isOverdue returns true when remind time is past and not completed', () {
      final pastTime = DateTime.now().subtract(const Duration(hours: 1)).millisecondsSinceEpoch;
      final state = TodoDetailState.initial(todoId: 0).copyWith(
        remindAt: pastTime,
        isCompleted: false,
      );
      expect(state.isOverdue, true);
    });

    test('isOverdue returns false when completed', () {
      final pastTime = DateTime.now().subtract(const Duration(hours: 1)).millisecondsSinceEpoch;
      final state = TodoDetailState.initial(todoId: 0).copyWith(
        remindAt: pastTime,
        isCompleted: true,
      );
      expect(state.isOverdue, false);
    });
  });
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test test/todo_detail_provider_test.dart`
Expected: FAIL — cannot find `todo_detail_provider.dart`

- [ ] **Step 3: Implement TodoDetailProvider**

```dart
// lib/providers/todo_detail_provider.dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/todo.dart';
import '../repositories/todo_repository.dart';
import '../repositories/folder_repository.dart';
import 'repository_providers.dart';

class TodoDetailState {
  final int todoId;
  final String title;
  final String memo;
  final bool isCompleted;
  final bool isImportant;
  final int remindAt;
  final RepeatType repeatType;
  final int? folderId;
  final String folderName;
  final bool isLoaded;

  const TodoDetailState({
    required this.todoId,
    this.title = '',
    this.memo = '',
    this.isCompleted = false,
    this.isImportant = false,
    this.remindAt = 0,
    this.repeatType = RepeatType.none,
    this.folderId,
    this.folderName = '未分类',
    this.isLoaded = false,
  });

  factory TodoDetailState.initial({required int todoId}) =>
      TodoDetailState(todoId: todoId);

  String get remindTimeText {
    if (remindAt <= 0) return '添加提醒';
    final dt = DateTime.fromMillisecondsSinceEpoch(remindAt);
    final now = DateTime.now();
    final isToday = dt.year == now.year &&
        dt.month == now.month &&
        dt.day == now.day;
    final amPm = dt.hour < 12 ? '上午' : '下午';
    final hour = dt.hour == 0 ? 12 : (dt.hour > 12 ? dt.hour - 12 : dt.hour);
    final minute = dt.minute.toString().padLeft(2, '0');
    if (isToday) {
      return '$amPm$hour:$minute';
    }
    return '${dt.month}月${dt.day}日 $amPm$hour:$minute';
  }

  String get repeatTypeText => switch (repeatType) {
        RepeatType.none => '不重复',
        RepeatType.daily => '每天',
        RepeatType.weekly => '每周',
        RepeatType.monthly => '每月',
        RepeatType.yearly => '每年',
      };

  bool get isOverdue =>
      remindAt > 0 &&
      remindAt < DateTime.now().millisecondsSinceEpoch &&
      !isCompleted;

  TodoDetailState copyWith({
    int? todoId,
    String? title,
    String? memo,
    bool? isCompleted,
    bool? isImportant,
    int? remindAt,
    RepeatType? repeatType,
    int? folderId,
    bool setFolderIdNull = false,
    String? folderName,
    bool? isLoaded,
  }) =>
      TodoDetailState(
        todoId: todoId ?? this.todoId,
        title: title ?? this.title,
        memo: memo ?? this.memo,
        isCompleted: isCompleted ?? this.isCompleted,
        isImportant: isImportant ?? this.isImportant,
        remindAt: remindAt ?? this.remindAt,
        repeatType: repeatType ?? this.repeatType,
        folderId: setFolderIdNull ? null : (folderId ?? this.folderId),
        folderName: folderName ?? this.folderName,
        isLoaded: isLoaded ?? this.isLoaded,
      );
}

class TodoDetailNotifier extends StateNotifier<TodoDetailState> {
  final TodoRepository _todoRepo;
  final FolderRepository _folderRepo;

  TodoDetailNotifier(this._todoRepo, this._folderRepo, int todoId)
      : super(TodoDetailState.initial(todoId: todoId));

  Future<void> load() async {
    if (state.todoId == 0) {
      state = state.copyWith(isLoaded: true);
      return;
    }
    final todo = await _todoRepo.getById(state.todoId);
    if (todo == null) return;
    String folderName = '未分类';
    if (todo.folderId != null) {
      final folder = await _folderRepo.get(todo.folderId!);
      folderName = folder?.name ?? '未分类';
    }
    state = state.copyWith(
      title: todo.title,
      memo: todo.memo,
      isCompleted: todo.isCompleted,
      isImportant: todo.isImportant,
      remindAt: todo.remindAt,
      repeatType: todo.repeatType,
      folderId: todo.folderId,
      setFolderIdNull: todo.folderId == null,
      folderName: folderName,
      isLoaded: true,
    );
  }

  void setTitle(String value) => state = state.copyWith(title: value);
  void setMemo(String value) => state = state.copyWith(memo: value);
  void setCompleted(bool value) => state = state.copyWith(isCompleted: value);
  void setImportant(bool value) => state = state.copyWith(isImportant: value);

  void setRemindAt(int value) => state = state.copyWith(remindAt: value);

  void clearRemind() =>
      state = state.copyWith(remindAt: 0, repeatType: RepeatType.none);

  void setRepeatType(RepeatType value) =>
      state = state.copyWith(repeatType: value);

  Future<void> setFolder(int? folderId) async {
    if (folderId == null) {
      state = state.copyWith(setFolderIdNull: true, folderName: '未分类');
      return;
    }
    final folder = await _folderRepo.get(folderId);
    state = state.copyWith(
      folderId: folderId,
      folderName: folder?.name ?? '未分类',
    );
  }

  Future<bool> save() async {
    if (state.todoId == 0 && state.title.trim().isEmpty) return false;
    final now = DateTime.now().millisecondsSinceEpoch;
    final todo = Todo(
      id: state.todoId,
      title: state.title.trim(),
      memo: state.memo,
      isCompleted: state.isCompleted,
      isImportant: state.isImportant,
      remindAt: state.remindAt,
      repeatType: state.repeatType,
      folderId: state.folderId,
      createdAt: now,
      updatedAt: now,
    );
    if (state.todoId == 0) {
      final newId = await _todoRepo.insert(todo);
      if (newId > 0) {
        state = state.copyWith(todoId: newId);
        return true;
      }
      return false;
    } else {
      await _todoRepo.update(todo);
      return true;
    }
  }

  Future<void> delete() async {
    if (state.todoId > 0) {
      await _todoRepo.softDelete(state.todoId);
    }
  }
}

final todoDetailProvider = StateNotifierProvider.autoDispose
    .family<TodoDetailNotifier, TodoDetailState, int>((ref, todoId) {
  return TodoDetailNotifier(
    ref.watch(todoRepositoryProvider),
    ref.watch(folderRepositoryProvider),
    todoId,
  );
});
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test test/todo_detail_provider_test.dart`
Expected: All 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add lib/providers/todo_detail_provider.dart test/todo_detail_provider_test.dart
git commit -m "feat(p3b): 添加 TodoDetailProvider 状态管理"
```

---

### Task 2: TodoDetailPage 页面 UI

**Files:**
- Create: `lib/pages/todo_detail_page.dart`

- [ ] **Step 1: Create the detail page**

```dart
// lib/pages/todo_detail_page.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:share_plus/share_plus.dart';
import '../models/folder.dart';
import '../models/todo.dart';
import '../providers/todo_detail_provider.dart';
import '../providers/repository_providers.dart';
import '../theme.dart';
import '../widgets/delete_confirm_sheet.dart';
import '../widgets/todo/date_time_picker_sheet.dart';
import '../widgets/todo/repeat_picker_sheet.dart';

class TodoDetailPage extends ConsumerStatefulWidget {
  final int todoId;
  const TodoDetailPage({super.key, required this.todoId});

  @override
  ConsumerState<TodoDetailPage> createState() => _TodoDetailPageState();
}

class _TodoDetailPageState extends ConsumerState<TodoDetailPage>
    with WidgetsBindingObserver {
  final _titleController = TextEditingController();
  final _memoController = TextEditingController();
  final _titleFocusNode = FocusNode();
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    Future.microtask(() async {
      final notifier = ref.read(todoDetailProvider(widget.todoId).notifier);
      await notifier.load();
      final state = ref.read(todoDetailProvider(widget.todoId));
      _titleController.text = state.title;
      _memoController.text = state.memo;
      _loaded = true;
      if (widget.todoId == 0) {
        _titleFocusNode.requestFocus();
      }
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _titleController.dispose();
    _memoController.dispose();
    _titleFocusNode.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive) {
      _save();
    }
  }

  @override
  void deactivate() {
    _save();
    super.deactivate();
  }

  void _save() {
    if (!_loaded) return;
    final notifier = ref.read(todoDetailProvider(widget.todoId).notifier);
    notifier.setTitle(_titleController.text);
    notifier.setMemo(_memoController.text);
    notifier.save();
  }

  void _onBack() {
    _save();
    context.pop(true);
  }

  Future<void> _pickRemindTime() async {
    final state = ref.read(todoDetailProvider(widget.todoId));
    final result = await showDateTimePickerSheet(context,
        initialEpochMs: state.remindAt);
    if (result != null) {
      ref.read(todoDetailProvider(widget.todoId).notifier).setRemindAt(result);
    }
  }

  Future<void> _pickRepeatType() async {
    final state = ref.read(todoDetailProvider(widget.todoId));
    final result = await showRepeatPickerSheet(context,
        current: state.repeatType);
    if (result != null) {
      ref.read(todoDetailProvider(widget.todoId).notifier).setRepeatType(result);
    }
  }

  Future<void> _pickFolder() async {
    final folders = await ref.read(folderRepositoryProvider).list();
    if (!mounted) return;
    final state = ref.read(todoDetailProvider(widget.todoId));
    final names = <String>['未分类'];
    final ids = <int?>[null];
    for (final f in folders) {
      names.add(f.name);
      ids.add(f.id);
    }
    final currentIdx = ids.indexOf(state.folderId).clamp(0, ids.length - 1);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('选择分类'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: List.generate(names.length, (i) => RadioListTile<int>(
              title: Text(names[i]),
              value: i,
              groupValue: currentIdx,
              onChanged: (val) {
                ref.read(todoDetailProvider(widget.todoId).notifier)
                    .setFolder(ids[val!]);
                Navigator.pop(ctx);
              },
            )),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('取消'),
          ),
        ],
      ),
    );
  }

  void _shareTodo() {
    final title = _titleController.text.trim();
    final memo = _memoController.text.trim();
    final text = [
      if (title.isNotEmpty) title,
      if (memo.isNotEmpty) memo,
    ].join('\n\n');
    if (text.isEmpty) return;
    SharePlus.instance.share(ShareParams(text: text));
  }

  Future<void> _deleteTodo() async {
    final state = ref.read(todoDetailProvider(widget.todoId));
    if (state.todoId <= 0) {
      context.pop(false);
      return;
    }
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '确定删除该待办？',
      confirmLabel: '删除',
    );
    if (confirmed) {
      await ref.read(todoDetailProvider(widget.todoId).notifier).delete();
      if (mounted) context.pop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(todoDetailProvider(widget.todoId));
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _onBack();
      },
      child: Scaffold(
        backgroundColor: AppColors.bgCard,
        body: SafeArea(
          child: Column(
            children: [
              _buildTopBar(),
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.symmetric(
                      horizontal: AppDimens.spacingXl),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildFolderIndicator(state),
                      const SizedBox(height: AppDimens.spacingM),
                      _buildTitleRow(state),
                      const Divider(height: 32),
                      _buildRemindRow(state),
                      const Divider(height: 32),
                      _buildRepeatRow(state),
                      const Divider(height: 32),
                      _buildImportantRow(state),
                      const Divider(height: 32),
                      _buildMemoSection(),
                    ],
                  ),
                ),
              ),
              _buildBottomBar(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTopBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          AppDimens.spacingS, AppDimens.spacingS, AppDimens.spacingL, 0),
      child: Row(
        children: [
          IconButton(
            onPressed: _onBack,
            icon: const Icon(Icons.arrow_back, color: AppColors.textPrimary),
          ),
        ],
      ),
    );
  }

  Widget _buildFolderIndicator(TodoDetailState state) {
    return GestureDetector(
      onTap: _pickFolder,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.folder_outlined, size: 16, color: AppColors.textHint),
          const SizedBox(width: 4),
          Text(state.folderName,
              style: const TextStyle(
                  fontSize: AppDimens.textCaption, color: AppColors.textHint)),
          const SizedBox(width: 2),
          const Icon(Icons.arrow_drop_down, size: 16, color: AppColors.textHint),
        ],
      ),
    );
  }

  Widget _buildTitleRow(TodoDetailState state) {
    return Row(
      children: [
        GestureDetector(
          onTap: () {
            final notifier =
                ref.read(todoDetailProvider(widget.todoId).notifier);
            notifier.setCompleted(!state.isCompleted);
          },
          child: Icon(
            state.isCompleted
                ? Icons.check_circle_outline
                : Icons.radio_button_unchecked,
            size: 24,
            color: state.isCompleted ? AppColors.primary : AppColors.textHint,
          ),
        ),
        const SizedBox(width: AppDimens.spacingM),
        Expanded(
          child: TextField(
            controller: _titleController,
            focusNode: _titleFocusNode,
            decoration: const InputDecoration(
              hintText: '待办事项',
              border: InputBorder.none,
              hintStyle: TextStyle(color: AppColors.textHint),
            ),
            style: const TextStyle(
                fontSize: 18, color: AppColors.textPrimary),
          ),
        ),
      ],
    );
  }

  Widget _buildRemindRow(TodoDetailState state) {
    final hasRemind = state.remindAt > 0;
    final textColor = hasRemind
        ? (state.isOverdue ? AppColors.danger : AppColors.primary)
        : AppColors.textHint;
    return GestureDetector(
      onTap: _pickRemindTime,
      child: Row(
        children: [
          Icon(Icons.alarm, size: 22, color: AppColors.textHint),
          const SizedBox(width: AppDimens.spacingM),
          Expanded(
            child: Text(state.remindTimeText,
                style: TextStyle(fontSize: AppDimens.textBody, color: textColor)),
          ),
          if (hasRemind)
            GestureDetector(
              onTap: () => ref
                  .read(todoDetailProvider(widget.todoId).notifier)
                  .clearRemind(),
              child: const Icon(Icons.close, size: 20, color: AppColors.textHint),
            ),
        ],
      ),
    );
  }

  Widget _buildRepeatRow(TodoDetailState state) {
    return GestureDetector(
      onTap: _pickRepeatType,
      child: Row(
        children: [
          const Icon(Icons.repeat, size: 22, color: AppColors.textHint),
          const SizedBox(width: AppDimens.spacingM),
          const Expanded(
            child: Text('重复',
                style: TextStyle(
                    fontSize: AppDimens.textBody, color: AppColors.textPrimary)),
          ),
          Text(state.repeatTypeText,
              style: const TextStyle(
                  fontSize: AppDimens.textBody, color: AppColors.textHint)),
          const SizedBox(width: 4),
          const Icon(Icons.chevron_right, size: 20, color: AppColors.textHint),
        ],
      ),
    );
  }

  Widget _buildImportantRow(TodoDetailState state) {
    return Row(
      children: [
        const Icon(Icons.priority_high, size: 22, color: AppColors.textHint),
        const SizedBox(width: AppDimens.spacingM),
        const Expanded(
          child: Text('重要',
              style: TextStyle(
                  fontSize: AppDimens.textBody, color: AppColors.textPrimary)),
        ),
        Switch(
          value: state.isImportant,
          onChanged: (v) => ref
              .read(todoDetailProvider(widget.todoId).notifier)
              .setImportant(v),
        ),
      ],
    );
  }

  Widget _buildMemoSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.subject, size: 22, color: AppColors.textHint),
            const SizedBox(width: AppDimens.spacingM),
            const Text('备注',
                style: TextStyle(
                    fontSize: AppDimens.textBody, color: AppColors.textPrimary)),
          ],
        ),
        Padding(
          padding: const EdgeInsets.only(left: 34),
          child: TextField(
            controller: _memoController,
            maxLines: null,
            decoration: const InputDecoration(
              hintText: '添加备注',
              border: InputBorder.none,
              hintStyle: TextStyle(color: AppColors.textHint),
            ),
            style: const TextStyle(
                fontSize: AppDimens.textBody, color: AppColors.textPrimary),
          ),
        ),
      ],
    );
  }

  Widget _buildBottomBar() {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.bgCard,
        border: Border(top: BorderSide(color: AppColors.divider)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            Expanded(
              child: TextButton.icon(
                onPressed: _shareTodo,
                icon: const Icon(Icons.share_outlined, size: 20),
                label: const Text('分享'),
              ),
            ),
            Expanded(
              child: TextButton.icon(
                onPressed: _deleteTodo,
                icon: const Icon(Icons.delete_outline, size: 20),
                label: const Text('删除'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 2: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/pages/todo_detail_page.dart`
Expected: No issues found

- [ ] **Step 3: Commit**

```bash
git add lib/pages/todo_detail_page.dart
git commit -m "feat(p3b): 添加 TodoDetailPage 详情页 UI"
```

---

### Task 3: 路由注册 + 列表页导航

**Files:**
- Modify: `lib/router.dart`
- Modify: `lib/pages/todo_list_page.dart`

- [ ] **Step 1: Add route in router.dart**

In `lib/router.dart`, add import and route:

```dart
// Add import at top:
import 'pages/todo_detail_page.dart';

// Add route BEFORE the StatefulShellRoute (after the existing /editor/:noteId route):
GoRoute(
  path: '/todo/:todoId',
  builder: (context, state) {
    final todoId = int.tryParse(state.pathParameters['todoId'] ?? '0') ?? 0;
    return TodoDetailPage(todoId: todoId);
  },
),
```

- [ ] **Step 2: Wire TodoCard onTap in todo_list_page.dart**

In `lib/pages/todo_list_page.dart`, add import and modify `onTap`:

```dart
// Add import at top:
import 'package:go_router/go_router.dart';

// In _buildItem method, change onTap:
onTap: () async {
  final result = await context.push<bool>('/todo/${todo.id}');
  if (result == true) {
    ref.read(todoListProvider.notifier).reload();
  }
},
```

- [ ] **Step 3: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/router.dart lib/pages/todo_list_page.dart`
Expected: No issues found

- [ ] **Step 4: Run all tests**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add lib/router.dart lib/pages/todo_list_page.dart
git commit -m "feat(p3b): 注册 /todo/:todoId 路由 + TodoCard 点击导航"
```

---

### Task 4: 添加 share_plus 依赖 + 最终验证

**Files:**
- Modify: `pubspec.yaml`

- [ ] **Step 1: Add share_plus to pubspec.yaml**

In `pubspec.yaml` under `dependencies:`, add:

```yaml
  share_plus: ^10.1.4
```

- [ ] **Step 2: Run pub get**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter pub get`
Expected: Success

- [ ] **Step 3: Run full analyze**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/`
Expected: No issues found

- [ ] **Step 4: Run all tests**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add pubspec.yaml pubspec.lock
git commit -m "feat(p3b): 添加 share_plus 依赖"
```

---

## Self-Review

**Spec coverage:**
- ✅ Section 1 (布局) — Task 2 builds the full layout
- ✅ Section 2 (路由) — Task 3 adds `/todo/:todoId`
- ✅ Section 3.1 (返回按钮) — Task 2 `_onBack` + `PopScope`
- ✅ Section 3.2 (文件夹指示器) — Task 2 `_buildFolderIndicator` + `_pickFolder`
- ✅ Section 3.3 (完成+标题) — Task 2 `_buildTitleRow`
- ✅ Section 3.4 (提醒时间) — Task 2 `_buildRemindRow` + `_pickRemindTime`
- ✅ Section 3.5 (重复方式) — Task 2 `_buildRepeatRow` + `_pickRepeatType`
- ✅ Section 3.6 (重要开关) — Task 2 `_buildImportantRow`
- ✅ Section 3.7 (备注) — Task 2 `_buildMemoSection`
- ✅ Section 3.8 (底部操作栏) — Task 2 `_buildBottomBar`
- ✅ Section 4 (保存逻辑) — Task 1 `save()` + Task 2 lifecycle
- ✅ Section 5 (状态管理) — Task 1
- ✅ Section 7 (新增文件) — Tasks 1-3
- ✅ Section 8 (列表页联动) — Task 3

**Placeholder scan:** No TBD/TODO found.

**Type consistency:** `TodoDetailState`, `TodoDetailNotifier`, `todoDetailProvider` names used consistently across all tasks.
