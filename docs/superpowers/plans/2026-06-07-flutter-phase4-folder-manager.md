# Phase 4: 文件夹/笔记本管理页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a full-screen folder/notebook management page with CRUD, drag-and-drop reorder, and color picker — matching the Android `FolderManagerActivity`.

**Architecture:** `FolderManagerProvider` (StateNotifier) manages the flattened row list and CRUD. `FolderManagerPage` renders a `ReorderableListView` of three row types. Two BottomSheet components handle folder and notebook creation/editing. FilterPanel's "管理" button navigates to the page and refreshes on return.

**Tech Stack:** Flutter, Riverpod, go_router, ReorderableListView

---

## File Structure

| File | Responsibility |
|---|---|
| `lib/providers/folder_manager_provider.dart` (create) | FolderManagerRow sealed class, FolderManagerState, FolderManagerNotifier (load/CRUD/reorder) |
| `lib/widgets/folder/new_folder_sheet.dart` (create) | New/rename folder BottomSheet |
| `lib/widgets/folder/new_notebook_sheet.dart` (create) | New/edit notebook BottomSheet with 8-color palette |
| `lib/pages/folder_manager_page.dart` (create) | Full-screen page: AppBar + ReorderableListView + overflow menus |
| `lib/router.dart` (modify) | Register `/folder-manager` route |
| `lib/widgets/filter_panel.dart` (modify) | Wire "管理" button to navigate, refresh on return |

---

### Task 1: FolderManagerProvider 状态管理

**Files:**
- Create: `lib/providers/folder_manager_provider.dart`
- Test: `test/folder_manager_provider_test.dart`

- [ ] **Step 1: Write unit tests for FolderManagerRow and FolderManagerState**

```dart
// test/folder_manager_provider_test.dart
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test test/folder_manager_provider_test.dart`
Expected: FAIL — cannot find `package:hwnote/providers/folder_manager_provider.dart`

- [ ] **Step 3: Create FolderManagerProvider**

```dart
// lib/providers/folder_manager_provider.dart
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
      var insertAt = newIndex > oldIndex ? newIndex - segment.length + 1 : newIndex;
      if (insertAt < 0) insertAt = 0;
      if (insertAt > 0 && newRows[insertAt - 1] is FolderHeadRow) {
        final prev = newRows[insertAt - 1] as FolderHeadRow;
        if (prev.folder.id == 1 && insertAt - 1 == 0 && newIndex < oldIndex) return;
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
      if (moving.notebook.id == 1) return;
      final folderId = moving.notebook.folderId;
      final notebookIndices = <int>[];
      for (var i = 0; i < rows.length; i++) {
        final r = rows[i];
        if (r is NotebookItemRow && r.notebook.folderId == folderId) {
          notebookIndices.add(i);
        }
      }
      if (!notebookIndices.contains(oldIndex)) return;
      if (newIndex < notebookIndices.first || newIndex > notebookIndices.last) return;
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

final folderManagerProvider =
    StateNotifierProvider.autoDispose<FolderManagerNotifier, FolderManagerState>((ref) {
  return FolderManagerNotifier(
    ref.watch(folderRepositoryProvider),
    ref.watch(notebookRepositoryProvider),
  );
});
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test test/folder_manager_provider_test.dart`
Expected: All tests PASS

- [ ] **Step 5: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/providers/folder_manager_provider.dart`
Expected: No issues found

- [ ] **Step 6: Commit**

```bash
git add lib/providers/folder_manager_provider.dart test/folder_manager_provider_test.dart
git commit -m "feat(p4): 添加 FolderManagerProvider 状态管理"
```

---

### Task 2: NewFolderSheet + NewNotebookSheet 底部弹窗

**Files:**
- Create: `lib/widgets/folder/new_folder_sheet.dart`
- Create: `lib/widgets/folder/new_notebook_sheet.dart`

- [ ] **Step 1: Create NewFolderSheet**

```dart
// lib/widgets/folder/new_folder_sheet.dart
import 'package:flutter/material.dart';
import '../../theme.dart';

Future<String?> showNewFolderSheet(
  BuildContext context, {
  String? initialName,
}) async {
  return showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => _NewFolderContent(initialName: initialName),
  );
}

class _NewFolderContent extends StatefulWidget {
  final String? initialName;
  const _NewFolderContent({this.initialName});

  @override
  State<_NewFolderContent> createState() => _NewFolderContentState();
}

class _NewFolderContentState extends State<_NewFolderContent> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialName ?? '');
    if (widget.initialName != null) {
      _controller.selection = TextSelection(
        baseOffset: 0,
        extentOffset: widget.initialName!.length,
      );
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isEditing = widget.initialName != null;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        AppDimens.spacingXl, AppDimens.spacingL,
        AppDimens.spacingXl, MediaQuery.of(context).viewInsets.bottom + AppDimens.spacingL,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            isEditing ? '重命名文件夹' : '新建文件夹',
            style: const TextStyle(fontSize: AppDimens.textTitle, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: AppDimens.spacingL),
          TextField(
            controller: _controller,
            autofocus: true,
            decoration: InputDecoration(
              hintText: '文件夹名称',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            ),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: AppDimens.spacingL),
          Row(children: [
            Expanded(child: TextButton(
              onPressed: () => Navigator.pop(context),
              style: TextButton.styleFrom(
                backgroundColor: const Color(0xFFF5F5F5),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
              child: const Text('取消', style: TextStyle(
                color: AppColors.textSecondary, fontSize: AppDimens.textBody,
              )),
            )),
            const SizedBox(width: AppDimens.spacingM),
            Expanded(child: TextButton(
              onPressed: _controller.text.trim().isEmpty
                  ? null
                  : () => Navigator.pop(context, _controller.text.trim()),
              style: TextButton.styleFrom(
                backgroundColor: AppColors.primary,
                disabledBackgroundColor: AppColors.primary.withValues(alpha: 0.3),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
              child: const Text('确认', style: TextStyle(
                color: Colors.white, fontSize: AppDimens.textBody,
              )),
            )),
          ]),
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: Create NewNotebookSheet**

```dart
// lib/widgets/folder/new_notebook_sheet.dart
import 'package:flutter/material.dart';
import '../../theme.dart';

class NotebookSheetResult {
  final String name;
  final String color;
  const NotebookSheetResult({required this.name, required this.color});
}

const notebookPalette = [
  '#9E9E9E', '#E53935', '#FB8C00', '#FBC02D',
  '#43A047', '#00ACC1', '#1E88E5', '#8E24AA',
];

Future<NotebookSheetResult?> showNewNotebookSheet(
  BuildContext context, {
  String? initialName,
  String? initialColor,
}) async {
  return showModalBottomSheet<NotebookSheetResult>(
    context: context,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => _NewNotebookContent(
      initialName: initialName,
      initialColor: initialColor,
    ),
  );
}

class _NewNotebookContent extends StatefulWidget {
  final String? initialName;
  final String? initialColor;
  const _NewNotebookContent({this.initialName, this.initialColor});

  @override
  State<_NewNotebookContent> createState() => _NewNotebookContentState();
}

class _NewNotebookContentState extends State<_NewNotebookContent> {
  late final TextEditingController _controller;
  late String _selectedColor;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialName ?? '');
    _selectedColor = widget.initialColor ?? notebookPalette.first;
    if (widget.initialName != null) {
      _controller.selection = TextSelection(
        baseOffset: 0,
        extentOffset: widget.initialName!.length,
      );
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Color _parseHex(String hex) {
    final cleaned = hex.replaceFirst('#', '');
    final value = int.tryParse(cleaned, radix: 16) ?? 0x9E9E9E;
    return Color(0xFF000000 | value);
  }

  @override
  Widget build(BuildContext context) {
    final isEditing = widget.initialName != null;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        AppDimens.spacingXl, AppDimens.spacingL,
        AppDimens.spacingXl, MediaQuery.of(context).viewInsets.bottom + AppDimens.spacingL,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            isEditing ? '编辑笔记本' : '新建笔记本',
            style: const TextStyle(fontSize: AppDimens.textTitle, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: AppDimens.spacingL),
          TextField(
            controller: _controller,
            autofocus: true,
            decoration: InputDecoration(
              hintText: '笔记本名称',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            ),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: AppDimens.spacingL),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: notebookPalette.map((hex) {
              final selected = hex == _selectedColor;
              return GestureDetector(
                onTap: () => setState(() => _selectedColor = hex),
                child: Container(
                  width: 32,
                  height: 32,
                  margin: const EdgeInsets.symmetric(horizontal: 6),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: _parseHex(hex),
                    border: selected
                        ? Border.all(color: AppColors.primary, width: 2.5)
                        : null,
                  ),
                  child: selected
                      ? const Icon(Icons.check, size: 16, color: Colors.white)
                      : null,
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: AppDimens.spacingL),
          Row(children: [
            Expanded(child: TextButton(
              onPressed: () => Navigator.pop(context),
              style: TextButton.styleFrom(
                backgroundColor: const Color(0xFFF5F5F5),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
              child: const Text('取消', style: TextStyle(
                color: AppColors.textSecondary, fontSize: AppDimens.textBody,
              )),
            )),
            const SizedBox(width: AppDimens.spacingM),
            Expanded(child: TextButton(
              onPressed: _controller.text.trim().isEmpty
                  ? null
                  : () => Navigator.pop(context, NotebookSheetResult(
                      name: _controller.text.trim(),
                      color: _selectedColor,
                    )),
              style: TextButton.styleFrom(
                backgroundColor: AppColors.primary,
                disabledBackgroundColor: AppColors.primary.withValues(alpha: 0.3),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
              child: const Text('确认', style: TextStyle(
                color: Colors.white, fontSize: AppDimens.textBody,
              )),
            )),
          ]),
        ],
      ),
    );
  }
}
```

- [ ] **Step 3: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/widgets/folder/`
Expected: No issues found

- [ ] **Step 4: Commit**

```bash
git add lib/widgets/folder/new_folder_sheet.dart lib/widgets/folder/new_notebook_sheet.dart
git commit -m "feat(p4): 添加 NewFolderSheet + NewNotebookSheet 底部弹窗"
```

---

### Task 3: FolderManagerPage 管理页面

**Files:**
- Create: `lib/pages/folder_manager_page.dart`

- [ ] **Step 1: Create FolderManagerPage**

```dart
// lib/pages/folder_manager_page.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../models/folder.dart';
import '../models/notebook.dart';
import '../providers/folder_manager_provider.dart';
import '../providers/repository_providers.dart';
import '../theme.dart';
import '../widgets/delete_confirm_sheet.dart';
import '../widgets/folder/new_folder_sheet.dart';
import '../widgets/folder/new_notebook_sheet.dart';

class FolderManagerPage extends ConsumerStatefulWidget {
  const FolderManagerPage({super.key});

  @override
  ConsumerState<FolderManagerPage> createState() => _FolderManagerPageState();
}

class _FolderManagerPageState extends ConsumerState<FolderManagerPage> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(folderManagerProvider.notifier).load());
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(folderManagerProvider);
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        context.pop(ref.read(folderManagerProvider.notifier).hasChanges);
      },
      child: Scaffold(
        backgroundColor: Colors.white,
        appBar: AppBar(
          backgroundColor: Colors.white,
          elevation: 0,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back, color: AppColors.textPrimary),
            onPressed: () => context.pop(
                ref.read(folderManagerProvider.notifier).hasChanges),
          ),
          title: const Text('文件夹管理', style: TextStyle(
            color: AppColors.textPrimary, fontSize: AppDimens.textTitle,
            fontWeight: FontWeight.w600,
          )),
          centerTitle: false,
          actions: [
            IconButton(
              icon: const Icon(Icons.create_new_folder_outlined, color: AppColors.primary),
              onPressed: _onCreateFolder,
            ),
          ],
        ),
        body: state.isLoading
            ? const Center(child: CircularProgressIndicator())
            : ReorderableListView.builder(
                itemCount: state.rows.length,
                onReorder: (oldIndex, newIndex) =>
                    ref.read(folderManagerProvider.notifier).onReorder(oldIndex, newIndex),
                buildDefaultDragHandles: false,
                itemBuilder: (context, index) {
                  final row = state.rows[index];
                  return switch (row) {
                    FolderHeadRow r => _buildFolderHead(r, index),
                    NotebookItemRow r => _buildNotebookItem(r, index),
                    CreateNotebookRow r => _buildCreateNotebook(r, index),
                  };
                },
              ),
      ),
    );
  }

  Widget _buildFolderHead(FolderHeadRow row, int index) {
    final canDrag = row.folder.id != 1;
    return ReorderableDragStartListener(
      key: ValueKey('folder_${row.folder.id}'),
      index: index,
      enabled: canDrag,
      child: InkWell(
        onTap: () => ref.read(folderManagerProvider.notifier).toggleExpand(row.folder.id),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: 14),
          child: Row(children: [
            if (canDrag)
              const Icon(Icons.drag_handle, size: 20, color: AppColors.textHint)
            else
              const SizedBox(width: 20),
            const SizedBox(width: AppDimens.spacingS),
            const Icon(Icons.folder_outlined, size: 20, color: AppColors.textPrimary),
            const SizedBox(width: AppDimens.spacingS),
            Expanded(child: Text(row.folder.name, style: const TextStyle(
              fontSize: AppDimens.textBody, color: AppColors.textPrimary,
              fontWeight: FontWeight.w500,
            ))),
            AnimatedRotation(
              turns: row.expanded ? 0.5 : 0,
              duration: const Duration(milliseconds: 200),
              child: const Icon(Icons.arrow_drop_down, size: 20, color: AppColors.textHint),
            ),
            if (row.folder.id != 1)
              _overflowButton(() => _showFolderMenu(row.folder))
            else
              const SizedBox(width: 40),
          ]),
        ),
      ),
    );
  }

  Widget _buildNotebookItem(NotebookItemRow row, int index) {
    final canDrag = row.notebook.id != 1;
    final cleaned = row.notebook.color.replaceFirst('#', '');
    final dotColor = int.tryParse(cleaned, radix: 16);
    return ReorderableDragStartListener(
      key: ValueKey('notebook_${row.notebook.id}'),
      index: index,
      enabled: canDrag,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(48, 10, AppDimens.spacingL, 10),
        child: Row(children: [
          if (canDrag)
            const Icon(Icons.drag_handle, size: 18, color: AppColors.textHint)
          else
            const SizedBox(width: 18),
          const SizedBox(width: AppDimens.spacingS),
          Container(width: 10, height: 10, decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: dotColor != null ? Color(0xFF000000 | dotColor) : AppColors.textHint,
          )),
          const SizedBox(width: 10),
          Expanded(child: Text(row.notebook.name, style: const TextStyle(
            fontSize: AppDimens.textBody, color: AppColors.textPrimary,
          ))),
          _overflowButton(() => _showNotebookMenu(row.notebook)),
        ]),
      ),
    );
  }

  Widget _buildCreateNotebook(CreateNotebookRow row, int index) {
    return Container(
      key: ValueKey('create_${row.folderId}'),
      padding: const EdgeInsets.fromLTRB(76, 10, AppDimens.spacingL, 10),
      child: GestureDetector(
        onTap: () => _onCreateNotebook(row.folderId),
        child: const Row(children: [
          Icon(Icons.add, size: 18, color: AppColors.primary),
          SizedBox(width: AppDimens.spacingS),
          Text('新建笔记本', style: TextStyle(
            fontSize: AppDimens.textBody, color: AppColors.primary,
          )),
        ]),
      ),
    );
  }

  Widget _overflowButton(VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: const Padding(
        padding: EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        child: Icon(Icons.more_vert, size: 20, color: AppColors.textHint),
      ),
    );
  }

  void _showFolderMenu(Folder folder) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            leading: const Icon(Icons.edit_outlined),
            title: const Text('重命名'),
            onTap: () {
              Navigator.pop(ctx);
              _onRenameFolder(folder);
            },
          ),
          ListTile(
            leading: const Icon(Icons.delete_outline, color: AppColors.danger),
            title: const Text('删除', style: TextStyle(color: AppColors.danger)),
            onTap: () {
              Navigator.pop(ctx);
              _onDeleteFolder(folder);
            },
          ),
        ]),
      ),
    );
  }

  void _showNotebookMenu(Notebook nb) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            leading: const Icon(Icons.edit_outlined),
            title: const Text('编辑'),
            onTap: () {
              Navigator.pop(ctx);
              _onEditNotebook(nb);
            },
          ),
          if (nb.id != 1)
            ListTile(
              leading: const Icon(Icons.drive_file_move_outlined),
              title: const Text('移动到'),
              onTap: () {
                Navigator.pop(ctx);
                _onMoveNotebook(nb);
              },
            ),
          if (nb.id != 1)
            ListTile(
              leading: const Icon(Icons.delete_outline, color: AppColors.danger),
              title: const Text('删除', style: TextStyle(color: AppColors.danger)),
              onTap: () {
                Navigator.pop(ctx);
                _onDeleteNotebook(nb);
              },
            ),
        ]),
      ),
    );
  }

  Future<void> _onCreateFolder() async {
    final name = await showNewFolderSheet(context);
    if (name != null && name.isNotEmpty) {
      await ref.read(folderManagerProvider.notifier).createFolder(name);
    }
  }

  Future<void> _onRenameFolder(Folder folder) async {
    final name = await showNewFolderSheet(context, initialName: folder.name);
    if (name != null && name.isNotEmpty) {
      await ref.read(folderManagerProvider.notifier).renameFolder(folder.id, name);
    }
  }

  Future<void> _onDeleteFolder(Folder folder) async {
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '删除文件夹「${folder.name}」将同时删除其中的笔记本和笔记，确定删除？',
      confirmLabel: '删除',
    );
    if (confirmed) {
      await ref.read(folderManagerProvider.notifier).deleteFolder(folder.id);
    }
  }

  Future<void> _onCreateNotebook(int folderId) async {
    final result = await showNewNotebookSheet(context);
    if (result != null) {
      await ref.read(folderManagerProvider.notifier).createNotebook(
        folderId, result.name, result.color,
      );
    }
  }

  Future<void> _onEditNotebook(Notebook nb) async {
    final result = await showNewNotebookSheet(
      context,
      initialName: nb.name,
      initialColor: nb.color,
    );
    if (result != null) {
      await ref.read(folderManagerProvider.notifier).renameNotebook(
        nb.id, result.name, result.color,
      );
    }
  }

  Future<void> _onMoveNotebook(Notebook nb) async {
    final folderRepo = ref.read(folderRepositoryProvider);
    final folders = await folderRepo.list();
    if (!mounted) return;
    final targetId = await showDialog<int>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('移动到'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: folders.map((f) => RadioListTile<int>(
            title: Text(f.name),
            value: f.id,
            groupValue: nb.folderId,
            onChanged: (value) => Navigator.pop(ctx, value),
          )).toList(),
        ),
      ),
    );
    if (targetId != null && targetId != nb.folderId) {
      await ref.read(folderManagerProvider.notifier).moveNotebook(nb.id, targetId);
    }
  }

  Future<void> _onDeleteNotebook(Notebook nb) async {
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '删除笔记本「${nb.name}」将同时删除其中的笔记，确定删除？',
      confirmLabel: '删除',
    );
    if (confirmed) {
      await ref.read(folderManagerProvider.notifier).deleteNotebook(nb.id);
    }
  }
}
```

- [ ] **Step 2: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/pages/folder_manager_page.dart`
Expected: No issues found

- [ ] **Step 3: Commit**

```bash
git add lib/pages/folder_manager_page.dart
git commit -m "feat(p4): 添加 FolderManagerPage 管理页面"
```

---

### Task 4: 路由注册 + FilterPanel 接入

**Files:**
- Modify: `lib/router.dart`
- Modify: `lib/widgets/filter_panel.dart`

- [ ] **Step 1: Register route in router.dart**

In `lib/router.dart`:
1. Add import: `import 'pages/folder_manager_page.dart';`
2. Add a new `GoRoute` before the `StatefulShellRoute`, after the existing `/todo/:todoId` route:
```dart
GoRoute(
  path: '/folder-manager',
  builder: (context, state) => const FolderManagerPage(),
),
```

- [ ] **Step 2: Wire FilterPanel "管理" button**

In `lib/widgets/filter_panel.dart`:
1. Add import: `import 'package:go_router/go_router.dart';`
2. Replace the `_buildSectionHeader` method. Change the `GestureDetector` `onTap` from the SnackBar placeholder to navigation:

Replace:
```dart
GestureDetector(
  onTap: () => ScaffoldMessenger.of(ctx).showSnackBar(
    const SnackBar(content: Text('文件夹管理将在 Phase 2E 实现'))),
  child: const Text('管理', style: TextStyle(
    fontSize: AppDimens.textCaption + 1, color: AppColors.primary,
  )),
),
```

With:
```dart
GestureDetector(
  onTap: () async {
    final result = await ctx.push<bool>('/folder-manager');
    if (result == true) {
      setState(() {
        _rowsFuture = _buildRows();
      });
    }
  },
  child: const Text('管理', style: TextStyle(
    fontSize: AppDimens.textCaption + 1, color: AppColors.primary,
  )),
),
```

- [ ] **Step 3: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/router.dart lib/widgets/filter_panel.dart`
Expected: No issues found

- [ ] **Step 4: Run all tests**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add lib/router.dart lib/widgets/filter_panel.dart
git commit -m "feat(p4): 注册 /folder-manager 路由 + FilterPanel 管理按钮接入"
```

---

## Self-Review

**Spec coverage:**
- ✅ Section 1.1 (页面布局) — Task 3 AppBar + ReorderableListView
- ✅ Section 1.2 (行类型) — Task 1 sealed class + Task 3 三种 build 方法
- ✅ Section 2.1 (文件夹操作) — Task 3 展开/收起/新建/重命名/删除
- ✅ Section 2.2 (笔记本操作) — Task 3 新建/编辑/移动/删除
- ✅ Section 2.3 (拖拽排序) — Task 1 onReorder + Task 3 ReorderableDragStartListener
- ✅ Section 2.4 (保护规则) — Task 1 id==1 guard + Task 3 menu hiding
- ✅ Section 3 (文件结构) — All 6 files covered
- ✅ Section 4 (状态管理) — Task 1 complete provider
- ✅ Section 5.1 (NewFolderSheet) — Task 2
- ✅ Section 5.2 (NewNotebookSheet) — Task 2 with 8-color palette
- ✅ Section 6 (路由与导航) — Task 4
- ✅ Section 7 (集成) — Task 4 FilterPanel refresh

**Placeholder scan:** No TBD/TODO found.

**Type consistency:** `FolderManagerRow` / `FolderHeadRow` / `NotebookItemRow` / `CreateNotebookRow` used consistently across Task 1 and Task 3. `showNewFolderSheet` / `showNewNotebookSheet` / `NotebookSheetResult` match between Task 2 and Task 3.
