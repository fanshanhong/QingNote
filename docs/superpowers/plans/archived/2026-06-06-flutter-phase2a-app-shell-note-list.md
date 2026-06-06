# Flutter Phase 2A: App Shell + 笔记列表页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Flutter 跨平台 App 的底部导航 2-tab 容器 + 完整笔记列表页，所有 UI/UX/交互/功能与 Android 原生版 1:1 一致。

**Architecture:** 单 `NoteListNotifier extends StateNotifier<NoteListState>` 集中管理列表全部状态，与 Android `NoteListFragment` 的字段 1:1 对应。go_router ShellRoute 提供底部导航 2-tab 容器。所有 UI Widget 通过 `ref.watch` 响应状态变化实现声明式重建。

**Tech Stack:** Flutter 3.7+, flutter_riverpod ^2.6.1, go_router ^14.8.1, sqflite ^2.4.2, shared_preferences, flutter_staggered_grid_view

**Spec:** `docs/superpowers/specs/2026-06-06-flutter-phase2a-app-shell-note-list.md`

**Working directory:** `code/HuaweiNoteFlutter/`

**Android reference:**
- `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`
- `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListFragment.kt`
- `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListAdapter.kt`
- `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/list/FilterPanelAdapter.kt`
- `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/util/DateUtils.kt`
- `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/util/TextUtils.kt`

**Color constants (from Android `colors.xml`):**

| Name | Hex |
|---|---|
| primary | #007DFF |
| primary_light | #E3F2FD |
| text_primary | #212121 |
| text_secondary | #666666 |
| text_hint | #9E9E9E |
| bg_window | #FAFAFA |
| bg_card | #FFFFFF |
| divider | #EEEEEE |

**Dimension constants (from Android `dimens.xml`):**

| Name | Value |
|---|---|
| spacing_xs | 4 |
| spacing_s | 8 |
| spacing_m | 12 |
| spacing_l | 16 |
| spacing_xl | 24 |
| radius_card | 10 |
| text_title | 16 |
| text_body | 14 |
| text_caption | 12 |
| text_hint | 11 |
| header_title_size | 26 |
| bottom_nav_height | 56 |

---

## File Structure

```
lib/
├── main.dart                              # MODIFY — 替换 demo 为 ProviderScope + MaterialApp.router
├── theme.dart                             # CREATE — ThemeData + AppColors + AppDimens 常量
├── router.dart                            # CREATE — go_router ShellRoute 配置
├── providers/
│   ├── database_provider.dart             # CREATE — DatabaseHelper singleton provider
│   ├── repository_providers.dart          # CREATE — 5 个 Repository provider
│   └── note_list_provider.dart            # CREATE — NoteListState + NoteListNotifier + provider
├── pages/
│   ├── app_shell.dart                     # CREATE — ShellRoute shell widget（底部导航栏）
│   ├── note_list_page.dart                # CREATE — NoteListPage 主页面
│   └── todo_placeholder_page.dart         # CREATE — 待办占位页
├── widgets/
│   ├── note_list_header.dart              # CREATE — Header 组件（正常/批量双模式）
│   ├── note_search_bar.dart               # CREATE — 搜索框（200ms debounce）
│   ├── note_card.dart                     # CREATE — 笔记卡片
│   ├── filter_panel.dart                  # CREATE — 筛选面板
│   ├── sort_sheet.dart                    # CREATE — 排序 BottomSheet
│   └── delete_confirm_sheet.dart          # CREATE — 删除确认 BottomSheet
└── utils/
    ├── date_utils.dart                    # CREATE — 相对时间格式化
    └── text_utils.dart                    # CREATE — 标题判空 + 摘要截取

test/
├── utils/
│   ├── date_utils_test.dart               # CREATE
│   └── text_utils_test.dart               # CREATE
├── providers/
│   └── note_list_provider_test.dart       # CREATE
└── widget_test.dart                       # MODIFY — 更新为新 App 入口的 smoke test
```

---

### Task 1: 新增依赖 + 工具函数 + NoteListFilter 公开化

**Files:**
- Modify: `pubspec.yaml`
- Modify: `lib/repositories/note_repository.dart` — 将私有 filter 子类改为公开
- Create: `lib/utils/date_utils.dart`
- Create: `lib/utils/text_utils.dart`
- Create: `test/utils/date_utils_test.dart`
- Create: `test/utils/text_utils_test.dart`

**Android 参考：** `code/HuaWeiNote/.../util/DateUtils.kt` 和 `code/HuaWeiNote/.../util/TextUtils.kt`

- [ ] **Step 1: 在 pubspec.yaml 添加新依赖**

在 `pubspec.yaml` 的 `dependencies:` 块末尾添加：

```yaml
  shared_preferences: ^2.3.4
  flutter_staggered_grid_view: ^0.7.0
```

- [ ] **Step 2: 安装依赖**

Run: `cd code/HuaweiNoteFlutter && flutter pub get`
Expected: 依赖解析成功

- [ ] **Step 3: 将 NoteListFilter 子类改为公开**

在 `lib/repositories/note_repository.dart` 中，将所有 `_` 前缀的 filter 子类改为公开名称。

替换 sealed class 定义为：

```dart
sealed class NoteListFilter {
  const NoteListFilter();
  static const all = AllFilter();
  static const uncategorized = UncategorizedFilter();
  static const favorite = FavoriteFilter();
  static const deleted = DeletedFilter();
  static NoteListFilter folder(int folderId) => FolderFilter(folderId);
  static NoteListFilter notebook(int notebookId) => NotebookFilter(notebookId);
}

class AllFilter extends NoteListFilter { const AllFilter(); }
class UncategorizedFilter extends NoteListFilter { const UncategorizedFilter(); }
class FavoriteFilter extends NoteListFilter { const FavoriteFilter(); }
class DeletedFilter extends NoteListFilter { const DeletedFilter(); }
class FolderFilter extends NoteListFilter {
  final int folderId;
  const FolderFilter(this.folderId);
}
class NotebookFilter extends NoteListFilter {
  final int notebookId;
  const NotebookFilter(this.notebookId);
}
```

在 `NoteRepository` 的 `list()` 和 `count()` 方法中，将 `case _AllFilter()` → `case AllFilter()` 等（所有 6 个 case × 2 个方法 = 12 处）。

- [ ] **Step 4: 运行已有测试确认未破坏**

Run: `cd code/HuaweiNoteFlutter && flutter test`
Expected: 79 tests PASS

- [ ] **Step 5: 写 DateUtils 测试**

创建 `test/utils/date_utils_test.dart`：

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/utils/date_utils.dart';

void main() {
  group('AppDateUtils.formatRelative', () {
    test('同一天只显示 HH:mm', () {
      final now = DateTime(2026, 6, 6, 15, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 6, 14, 30).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '14:30');
    });

    test('昨天显示 昨天 HH:mm', () {
      final now = DateTime(2026, 6, 6, 15, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 5, 9, 15).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '昨天 09:15');
    });

    test('7天内显示 周X HH:mm', () {
      final now = DateTime(2026, 6, 6, 15, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 3, 10, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '周三 10:00');
    });

    test('更早显示 yyyy/MM/dd', () {
      final now = DateTime(2026, 6, 6, 15, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 5, 1, 10, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '2026/05/01');
    });

    test('跨年显示 yyyy/MM/dd', () {
      final now = DateTime(2026, 1, 1, 0, 0).millisecondsSinceEpoch;
      final target = DateTime(2025, 12, 25, 14, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '2025/12/25');
    });
  });
}
```

- [ ] **Step 6: 运行 DateUtils 测试验证失败**

Run: `cd code/HuaweiNoteFlutter && flutter test test/utils/date_utils_test.dart`
Expected: FAIL — `date_utils.dart` 不存在

- [ ] **Step 7: 实现 AppDateUtils（不依赖 intl，中文周X 与 Android 一致）**

创建 `lib/utils/date_utils.dart`：

```dart
class AppDateUtils {
  AppDateUtils._();

  static String _pad2(int n) => n.toString().padLeft(2, '0');

  /// 列表卡片时间显示规则（与 Android DateUtils.formatRelative 1:1 对应）：
  /// 同一天 → HH:mm | 昨天 → 昨天 HH:mm | 7天内 → 周X HH:mm | 更早 → yyyy/MM/dd
  static String formatRelative(int timeMs, {int? now}) {
    final nowMs = now ?? DateTime.now().millisecondsSinceEpoch;
    final target = DateTime.fromMillisecondsSinceEpoch(timeMs);
    final current = DateTime.fromMillisecondsSinceEpoch(nowMs);
    final hhmm = '${_pad2(target.hour)}:${_pad2(target.minute)}';

    if (target.year == current.year &&
        target.month == current.month &&
        target.day == current.day) {
      return hhmm;
    }

    final yesterday = current.subtract(const Duration(days: 1));
    if (target.year == yesterday.year &&
        target.month == yesterday.month &&
        target.day == yesterday.day) {
      return '昨天 $hhmm';
    }

    final diffDays = (nowMs - timeMs) ~/ (24 * 60 * 60 * 1000);
    if (diffDays >= 0 && diffDays <= 6) {
      const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
      return '${weekdays[target.weekday - 1]} $hhmm';
    }

    return '${target.year}/${_pad2(target.month)}/${_pad2(target.day)}';
  }
}
```

- [ ] **Step 8: 运行 DateUtils 测试验证通过**

Run: `cd code/HuaweiNoteFlutter && flutter test test/utils/date_utils_test.dart`
Expected: 全部 PASS

- [ ] **Step 9: 写 TextUtils 测试**

创建 `test/utils/text_utils_test.dart`：

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/utils/text_utils.dart';

void main() {
  group('AppTextUtils', () {
    group('isBlankTitle', () {
      test('空字符串返回 true', () => expect(AppTextUtils.isBlankTitle(''), true));
      test('纯空白返回 true', () => expect(AppTextUtils.isBlankTitle('   '), true));
      test('有内容返回 false', () => expect(AppTextUtils.isBlankTitle('会议笔记'), false));
    });

    group('summary', () {
      test('换行替换为空格', () {
        expect(AppTextUtils.summary('line1\nline2\nline3'), 'line1 line2 line3');
      });
      test('连续空白折叠', () {
        expect(AppTextUtils.summary('a   b  c'), 'a b c');
      });
      test('截断到 maxLen', () {
        expect(AppTextUtils.summary('a' * 100).length, 60);
      });
      test('短文本不截断', () => expect(AppTextUtils.summary('hello'), 'hello'));
      test('空文本返回空', () => expect(AppTextUtils.summary(''), ''));
      test('自定义 maxLen', () {
        expect(AppTextUtils.summary('a' * 20, maxLen: 10).length, 10);
      });
    });
  });
}
```

- [ ] **Step 10: 运行 TextUtils 测试验证失败**

Run: `cd code/HuaweiNoteFlutter && flutter test test/utils/text_utils_test.dart`
Expected: FAIL

- [ ] **Step 11: 实现 AppTextUtils**

创建 `lib/utils/text_utils.dart`：

```dart
class AppTextUtils {
  AppTextUtils._();

  static bool isBlankTitle(String title) => title.trim().isEmpty;

  static String summary(String plainText, {int maxLen = 60}) {
    final flattened =
        plainText.replaceAll('\n', ' ').replaceAll(RegExp(r'\s+'), ' ').trim();
    return flattened.length <= maxLen ? flattened : flattened.substring(0, maxLen);
  }
}
```

- [ ] **Step 12: 运行全部测试**

Run: `cd code/HuaweiNoteFlutter && flutter test`
Expected: 79 + 8 = 87 tests PASS

- [ ] **Step 13: 提交**

```bash
git add pubspec.yaml pubspec.lock lib/repositories/note_repository.dart lib/utils/date_utils.dart lib/utils/text_utils.dart test/utils/date_utils_test.dart test/utils/text_utils_test.dart
git commit -m "feat(phase2a): 添加依赖 + 工具函数 + NoteListFilter 公开化"
```

---

### Task 2: 主题 + 颜色/尺寸常量

**Files:**
- Create: `lib/theme.dart`

- [ ] **Step 1: 创建 theme.dart**

创建 `lib/theme.dart`：

```dart
import 'package:flutter/material.dart';

class AppColors {
  AppColors._();
  static const primary = Color(0xFF007DFF);
  static const primaryLight = Color(0xFFE3F2FD);
  static const textPrimary = Color(0xFF212121);
  static const textSecondary = Color(0xFF666666);
  static const textHint = Color(0xFF9E9E9E);
  static const bgWindow = Color(0xFFFAFAFA);
  static const bgCard = Color(0xFFFFFFFF);
  static const divider = Color(0xFFEEEEEE);
  static const cardStroke = Color(0xFFE8E8E8);
  static const bgLinen = Color(0xFFF5F0E8);
  static const bgKraft = Color(0xFFE8D5B7);
  static const bgGrid = Color(0xFFF8F8F8);
}

class AppDimens {
  AppDimens._();
  static const spacingXs = 4.0;
  static const spacingS = 8.0;
  static const spacingM = 12.0;
  static const spacingL = 16.0;
  static const spacingXl = 24.0;
  static const radiusCard = 10.0;
  static const textTitle = 16.0;
  static const textBody = 14.0;
  static const textCaption = 12.0;
  static const textHint = 11.0;
  static const headerTitleSize = 26.0;
  static const bottomNavHeight = 56.0;
}

ThemeData buildAppTheme() {
  return ThemeData(
    colorScheme: ColorScheme.light(
      primary: AppColors.primary,
      surface: AppColors.bgWindow,
    ),
    scaffoldBackgroundColor: AppColors.bgWindow,
    dividerColor: AppColors.divider,
    floatingActionButtonTheme: const FloatingActionButtonThemeData(
      backgroundColor: AppColors.primary,
      foregroundColor: Colors.white,
    ),
  );
}
```

- [ ] **Step 2: 验证编译**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/theme.dart`
Expected: No issues found

- [ ] **Step 3: 提交**

```bash
git add lib/theme.dart
git commit -m "feat(phase2a): 添加主题 + AppColors/AppDimens 常量"
```

---

### Task 3: Provider 层（Database + Repository + NoteListState/Notifier）

**Files:**
- Create: `lib/providers/database_provider.dart`
- Create: `lib/providers/repository_providers.dart`
- Create: `lib/providers/note_list_provider.dart`
- Create: `test/providers/note_list_provider_test.dart`

- [ ] **Step 1: 创建 database_provider.dart**

创建 `lib/providers/database_provider.dart`：

```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../db/database_helper.dart';

final databaseHelperProvider = Provider<DatabaseHelper>((ref) {
  return DatabaseHelper();
});
```

- [ ] **Step 2: 创建 repository_providers.dart**

创建 `lib/providers/repository_providers.dart`：

```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'database_provider.dart';
import '../repositories/note_repository.dart';
import '../repositories/folder_repository.dart';
import '../repositories/notebook_repository.dart';
import '../repositories/category_repository.dart';
import '../repositories/todo_repository.dart';

final noteRepositoryProvider = Provider<NoteRepository>((ref) {
  return NoteRepository(ref.watch(databaseHelperProvider));
});

final folderRepositoryProvider = Provider<FolderRepository>((ref) {
  return FolderRepository(ref.watch(databaseHelperProvider));
});

final notebookRepositoryProvider = Provider<NotebookRepository>((ref) {
  return NotebookRepository(ref.watch(databaseHelperProvider));
});

final categoryRepositoryProvider = Provider<CategoryRepository>((ref) {
  return CategoryRepository(ref.watch(databaseHelperProvider));
});

final todoRepositoryProvider = Provider<TodoRepository>((ref) {
  return TodoRepository(ref.watch(databaseHelperProvider));
});
```

- [ ] **Step 3: 创建 NoteListState + NoteListNotifier**

创建 `lib/providers/note_list_provider.dart`。这是核心状态管理文件，与 Android `NoteListFragment` 的字段/方法 1:1 对应。

```dart
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
    final sortBy = sortName == 'createdDesc' ? NoteSortBy.createdDesc : NoteSortBy.updatedDesc;
    final isGridView = prefs.getBool('note_grid_view') ?? false;
    final filter = await _loadFilter(prefs);
    state = state.copyWith(sortBy: sortBy, isGridView: isGridView, filter: filter);
    await reload();
  }

  Future<NoteListFilter> _loadFilter(SharedPreferences prefs) async {
    final type = prefs.getString('filter_type') ?? 'ALL';
    switch (type) {
      case 'UNCATEGORIZED': return NoteListFilter.uncategorized;
      case 'FAVORITE': return NoteListFilter.favorite;
      case 'DELETED': return NoteListFilter.deleted;
      case 'FOLDER':
        final id = prefs.getInt('filter_folder_id') ?? -1;
        if (id > 0 && await _folderRepo.get(id) != null) return NoteListFilter.folder(id);
        return NoteListFilter.all;
      case 'NOTEBOOK':
        final id = prefs.getInt('filter_notebook_id') ?? -1;
        if (id > 0 && await _notebookRepo.get(id) != null) return NoteListFilter.notebook(id);
        return NoteListFilter.all;
      default: return NoteListFilter.all;
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
      filter: filter, sortBy: state.sortBy,
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
      filter: filter, notes: notes, notebookColorMap: colorMap,
      isLoading: false, headerTitle: title, headerSubtitle: subtitle,
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
      default: return filter;
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

  Future<String> _computeHeaderSubtitle(NoteListFilter filter, int count) async {
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
    if (expanded.contains(folderId)) expanded.remove(folderId);
    else expanded.add(folderId);
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
    if (ids.contains(noteId)) ids.remove(noteId);
    else ids.add(noteId);
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
    final note = state.notes.firstWhere((n) => n.id == id);
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
```

- [ ] **Step 4: 写 NoteListState 单元测试**

创建 `test/providers/note_list_provider_test.dart`：

```dart
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
```

- [ ] **Step 5: 运行测试**

Run: `cd code/HuaweiNoteFlutter && flutter test test/providers/note_list_provider_test.dart`
Expected: PASS

- [ ] **Step 6: 验证全部编译**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/providers/`
Expected: No issues found

- [ ] **Step 7: 提交**

```bash
git add lib/providers/database_provider.dart lib/providers/repository_providers.dart lib/providers/note_list_provider.dart test/providers/note_list_provider_test.dart
git commit -m "feat(phase2a): Provider 层 — DatabaseHelper/Repository/NoteListNotifier"
```

---

### Task 4: 路由 + App Shell + 占位页 + main.dart 改造

**Files:**
- Create: `lib/router.dart`
- Create: `lib/pages/app_shell.dart`
- Create: `lib/pages/todo_placeholder_page.dart`
- Create: `lib/pages/note_list_page.dart` — 先创建占位版
- Modify: `lib/main.dart`
- Modify: `test/widget_test.dart`

- [ ] **Step 1: 创建 TodoPlaceholderPage**

创建 `lib/pages/todo_placeholder_page.dart`：

```dart
import 'package:flutter/material.dart';
import '../theme.dart';

class TodoPlaceholderPage extends StatelessWidget {
  const TodoPlaceholderPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Text(
          '待办功能开发中',
          style: TextStyle(fontSize: AppDimens.textBody, color: AppColors.textHint),
        ),
      ),
    );
  }
}
```

- [ ] **Step 2: 创建 AppShell**

创建 `lib/pages/app_shell.dart`：

```dart
import 'package:flutter/material.dart';
import '../theme.dart';

class AppShell extends StatelessWidget {
  final int currentIndex;
  final Widget child;
  final ValueChanged<int> onTabChanged;
  final bool bottomNavVisible;

  const AppShell({
    super.key,
    required this.currentIndex,
    required this.child,
    required this.onTabChanged,
    this.bottomNavVisible = true,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: child,
      bottomNavigationBar: bottomNavVisible
          ? SizedBox(
              height: AppDimens.bottomNavHeight,
              child: BottomNavigationBar(
                currentIndex: currentIndex,
                onTap: onTabChanged,
                selectedItemColor: AppColors.primary,
                unselectedItemColor: AppColors.textHint,
                selectedFontSize: AppDimens.textHint,
                unselectedFontSize: AppDimens.textHint,
                type: BottomNavigationBarType.fixed,
                items: const [
                  BottomNavigationBarItem(
                    icon: Icon(Icons.note_outlined),
                    activeIcon: Icon(Icons.note),
                    label: '笔记',
                  ),
                  BottomNavigationBarItem(
                    icon: Icon(Icons.check_box_outlined),
                    activeIcon: Icon(Icons.check_box),
                    label: '待办',
                  ),
                ],
              ),
            )
          : null,
    );
  }
}
```

- [ ] **Step 3: 创建 NoteListPage 占位版**

创建 `lib/pages/note_list_page.dart`：

```dart
import 'package:flutter/material.dart';
import '../theme.dart';

class NoteListPage extends StatelessWidget {
  const NoteListPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Text('笔记列表', style: TextStyle(fontSize: AppDimens.textBody)),
      ),
    );
  }
}
```

- [ ] **Step 4: 创建 router.dart**

创建 `lib/router.dart`：

```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'pages/app_shell.dart';
import 'pages/note_list_page.dart';
import 'pages/todo_placeholder_page.dart';
import 'providers/note_list_provider.dart';

final initialTabProvider = StateProvider<int>((ref) => 0);

final appRouterProvider = Provider<GoRouter>((ref) {
  final initialTab = ref.read(initialTabProvider);
  return GoRouter(
    initialLocation: initialTab == 1 ? '/todos' : '/notes',
    routes: [
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return Consumer(
            builder: (context, ref, _) {
              final isBatchMode =
                  ref.watch(noteListProvider.select((s) => s.isBatchMode));
              return AppShell(
                currentIndex: navigationShell.currentIndex,
                onTabChanged: (index) {
                  navigationShell.goBranch(index);
                  SharedPreferences.getInstance()
                      .then((prefs) => prefs.setInt('active_tab', index));
                },
                bottomNavVisible: !isBatchMode,
                child: navigationShell,
              );
            },
          );
        },
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/notes',
              builder: (context, state) => const NoteListPage(),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/todos',
              builder: (context, state) => const TodoPlaceholderPage(),
            ),
          ]),
        ],
      ),
    ],
  );
});
```

- [ ] **Step 5: 改造 main.dart**

用以下内容完全替换 `lib/main.dart`：

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'router.dart';
import 'theme.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  final savedTab = prefs.getInt('active_tab') ?? 0;
  runApp(ProviderScope(
    overrides: [initialTabProvider.overrideWith((ref) => savedTab)],
    child: const HwNoteApp(),
  ));
}

class HwNoteApp extends ConsumerWidget {
  const HwNoteApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);
    return MaterialApp.router(
      title: '备忘录',
      theme: buildAppTheme(),
      routerConfig: router,
      debugShowCheckedModeBanner: false,
    );
  }
}
```

- [ ] **Step 6: 更新 widget_test.dart**

替换 `test/widget_test.dart`：

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:hwnote/main.dart';

void main() {
  testWidgets('App 启动 smoke test', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const ProviderScope(child: HwNoteApp()));
    await tester.pumpAndSettle();
    expect(find.text('笔记'), findsOneWidget);
    expect(find.text('待办'), findsOneWidget);
  });
}
```

- [ ] **Step 7: 验证编译 + 测试**

Run: `cd code/HuaweiNoteFlutter && flutter analyze && flutter test`
Expected: No issues found + 全部 PASS

- [ ] **Step 8: 提交**

```bash
git add lib/main.dart lib/router.dart lib/pages/app_shell.dart lib/pages/note_list_page.dart lib/pages/todo_placeholder_page.dart test/widget_test.dart
git commit -m "feat(phase2a): App Shell + go_router 底部导航 2-tab + main 改造"
```

---

### Task 5: 删除确认 Sheet + 排序 Sheet 组件

**Files:**
- Create: `lib/widgets/delete_confirm_sheet.dart`
- Create: `lib/widgets/sort_sheet.dart`

- [ ] **Step 1: 创建 DeleteConfirmSheet**

创建 `lib/widgets/delete_confirm_sheet.dart`：

```dart
import 'package:flutter/material.dart';
import '../theme.dart';

Future<bool> showDeleteConfirmSheet(
  BuildContext context, {
  required String message,
  required String confirmLabel,
}) async {
  final result = await showModalBottomSheet<bool>(
    context: context,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppDimens.spacingXl, AppDimens.spacingL,
          AppDimens.spacingXl, AppDimens.spacingL,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message, style: const TextStyle(
              fontSize: AppDimens.textBody, color: AppColors.textSecondary, height: 1.6,
            )),
            const SizedBox(height: AppDimens.spacingL),
            Row(children: [
              Expanded(child: TextButton(
                onPressed: () => Navigator.pop(context, false),
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
                onPressed: () => Navigator.pop(context, true),
                style: TextButton.styleFrom(
                  backgroundColor: const Color(0xFFFF4444),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                child: Text(confirmLabel, style: const TextStyle(
                  color: Colors.white, fontSize: AppDimens.textBody,
                )),
              )),
            ]),
          ],
        ),
      ),
    ),
  );
  return result ?? false;
}
```

- [ ] **Step 2: 创建 SortSheet**

创建 `lib/widgets/sort_sheet.dart`：

```dart
import 'package:flutter/material.dart';
import '../repositories/note_repository.dart';
import '../theme.dart';

Future<NoteSortBy?> showSortSheet(BuildContext context, {required NoteSortBy current}) {
  return showModalBottomSheet<NoteSortBy>(
    context: context,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppDimens.spacingL, AppDimens.spacingL, AppDimens.spacingL, AppDimens.spacingM,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.only(bottom: AppDimens.spacingM),
              child: Text('排序方式', style: TextStyle(
                fontSize: AppDimens.textTitle, fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              )),
            ),
            RadioListTile<NoteSortBy>(
              title: const Text('按编辑时间', style: TextStyle(
                fontSize: AppDimens.textBody, color: AppColors.textPrimary)),
              value: NoteSortBy.updatedDesc, groupValue: current,
              activeColor: AppColors.primary,
              onChanged: (v) => Navigator.pop(context, v),
            ),
            RadioListTile<NoteSortBy>(
              title: const Text('按创建时间', style: TextStyle(
                fontSize: AppDimens.textBody, color: AppColors.textPrimary)),
              value: NoteSortBy.createdDesc, groupValue: current,
              activeColor: AppColors.primary,
              onChanged: (v) => Navigator.pop(context, v),
            ),
            const Divider(height: 1),
            Center(child: TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('取消', style: TextStyle(
                color: AppColors.primary, fontSize: AppDimens.textBody,
              )),
            )),
          ],
        ),
      ),
    ),
  );
}
```

- [ ] **Step 3: 验证编译**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/widgets/`
Expected: No issues found

- [ ] **Step 4: 提交**

```bash
git add lib/widgets/delete_confirm_sheet.dart lib/widgets/sort_sheet.dart
git commit -m "feat(phase2a): 排序 Sheet + 删除确认 Sheet 组件"
```

---

### Task 6: 笔记卡片组件

**Files:**
- Create: `lib/widgets/note_card.dart`

**Android 参考：** `code/HuaWeiNote/.../controller/list/NoteListAdapter.kt` + `res/layout/item_note_card.xml`

- [ ] **Step 1: 创建 NoteCard**

创建 `lib/widgets/note_card.dart`：

```dart
import 'package:flutter/material.dart';
import '../models/note.dart';
import '../theme.dart';
import '../utils/date_utils.dart';
import '../utils/text_utils.dart';

class NoteCard extends StatelessWidget {
  final Note note;
  final bool isBatchMode;
  final bool isSelected;
  final String? notebookColor;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final ValueChanged<bool> onBatchToggle;

  const NoteCard({
    super.key,
    required this.note,
    required this.isBatchMode,
    required this.isSelected,
    this.notebookColor,
    required this.onTap,
    required this.onLongPress,
    required this.onBatchToggle,
  });

  Color _cardBackground() {
    if (note.background != 'plain') {
      return switch (note.background) {
        'linen' => AppColors.bgLinen,
        'kraft' => AppColors.bgKraft,
        'grid' => AppColors.bgGrid,
        _ => AppColors.bgCard,
      };
    }
    if (notebookColor != null) {
      final c = _parseHexColor(notebookColor!);
      if (c != null) return Color.fromARGB(20, c.red, c.green, c.blue);
    }
    return AppColors.bgCard;
  }

  static Color? _parseHexColor(String hex) {
    final cleaned = hex.replaceFirst('#', '');
    if (cleaned.length != 6) return null;
    final value = int.tryParse(cleaned, radix: 16);
    if (value == null) return null;
    return Color(0xFF000000 | value);
  }

  @override
  Widget build(BuildContext context) {
    final title = AppTextUtils.isBlankTitle(note.title) ? '无标题' : note.title;
    final summaryText = AppTextUtils.summary(note.plainText);
    final timeText = AppDateUtils.formatRelative(note.updatedAt);

    return Card(
      elevation: 0,
      margin: const EdgeInsets.symmetric(
        vertical: AppDimens.spacingXs, horizontal: AppDimens.spacingXs,
      ),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppDimens.radiusCard),
        side: const BorderSide(color: AppColors.cardStroke, width: 0.5),
      ),
      color: _cardBackground(),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppDimens.radiusCard),
        onTap: isBatchMode ? () => onBatchToggle(!isSelected) : onTap,
        onLongPress: isBatchMode ? null : onLongPress,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: 14),
          child: Row(children: [
            if (isBatchMode) ...[
              SizedBox(width: 24, height: 24, child: Checkbox(
                value: isSelected,
                onChanged: (v) => onBatchToggle(v ?? false),
                activeColor: AppColors.primary,
              )),
              const SizedBox(width: AppDimens.spacingS),
            ],
            Expanded(child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  Expanded(child: Text(title, maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: AppDimens.textBody, fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary,
                    ),
                  )),
                  if (!isBatchMode) ...[
                    const SizedBox(width: AppDimens.spacingS),
                    Icon(
                      note.isFavorite ? Icons.star : Icons.star_border,
                      size: 20,
                      color: note.isFavorite ? Colors.amber : AppColors.textHint,
                    ),
                  ],
                  const SizedBox(width: AppDimens.spacingS),
                  Text(timeText, style: const TextStyle(
                    fontSize: AppDimens.textHint, color: AppColors.textHint,
                  )),
                ]),
                if (summaryText.isNotEmpty) ...[
                  const SizedBox(height: AppDimens.spacingXs),
                  Text(summaryText, maxLines: 2, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: AppDimens.textCaption, color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ],
            )),
          ]),
        ),
      ),
    );
  }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/widgets/note_card.dart`
Expected: No issues found

- [ ] **Step 3: 提交**

```bash
git add lib/widgets/note_card.dart
git commit -m "feat(phase2a): 笔记卡片组件 NoteCard"
```

---

### Task 7: Header + 搜索框组件

**Files:**
- Create: `lib/widgets/note_list_header.dart`
- Create: `lib/widgets/note_search_bar.dart`

- [ ] **Step 1: 创建 NoteSearchBar（200ms debounce）**

创建 `lib/widgets/note_search_bar.dart`：

```dart
import 'dart:async';
import 'package:flutter/material.dart';
import '../theme.dart';

class NoteSearchBar extends StatefulWidget {
  final ValueChanged<String> onQueryChanged;
  const NoteSearchBar({super.key, required this.onQueryChanged});

  @override
  State<NoteSearchBar> createState() => _NoteSearchBarState();
}

class _NoteSearchBarState extends State<NoteSearchBar> {
  final _controller = TextEditingController();
  Timer? _debounce;

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onChanged(String text) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 200), () {
      widget.onQueryChanged(text.trim());
    });
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppDimens.spacingL, AppDimens.spacingXs, AppDimens.spacingL, AppDimens.spacingM,
      ),
      child: TextField(
        controller: _controller,
        onChanged: _onChanged,
        decoration: InputDecoration(
          hintText: '搜索笔记',
          hintStyle: const TextStyle(color: AppColors.textHint, fontSize: AppDimens.textBody),
          prefixIcon: const Icon(Icons.search, color: AppColors.textHint),
          filled: true, fillColor: const Color(0xFFF5F5F5),
          contentPadding: const EdgeInsets.symmetric(vertical: 10),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none,
          ),
        ),
        style: const TextStyle(fontSize: AppDimens.textBody, color: AppColors.textPrimary),
      ),
    );
  }
}
```

- [ ] **Step 2: 创建 NoteListHeader（正常/批量双模式）**

创建 `lib/widgets/note_list_header.dart`：

```dart
import 'package:flutter/material.dart';
import '../theme.dart';

class NoteListHeader extends StatelessWidget {
  final String title;
  final String subtitle;
  final bool isBatchMode;
  final int selectedCount;
  final bool filterPanelVisible;
  final VoidCallback onToggleFilterPanel;
  final VoidCallback onExitBatchMode;
  final VoidCallback onOverflowTap;

  const NoteListHeader({
    super.key,
    required this.title, required this.subtitle,
    required this.isBatchMode, required this.selectedCount,
    required this.filterPanelVisible,
    required this.onToggleFilterPanel, required this.onExitBatchMode,
    required this.onOverflowTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppDimens.spacingL, AppDimens.spacingXl, AppDimens.spacingL, AppDimens.spacingS,
      ),
      child: isBatchMode ? _buildBatchHeader() : _buildNormalHeader(),
    );
  }

  Widget _buildNormalHeader() {
    return Row(children: [
      Expanded(child: GestureDetector(
        onTap: onToggleFilterPanel,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Text(title, style: const TextStyle(
              fontSize: AppDimens.headerTitleSize, fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            )),
            const SizedBox(width: 4),
            AnimatedRotation(
              turns: filterPanelVisible ? 0.5 : 0,
              duration: const Duration(milliseconds: 200),
              child: const Icon(Icons.arrow_drop_down, color: AppColors.textHint, size: 24),
            ),
          ]),
          const SizedBox(height: 2),
          Text(subtitle, style: const TextStyle(
            fontSize: AppDimens.textCaption + 1, color: AppColors.textHint,
          )),
        ]),
      )),
      GestureDetector(
        onTap: onOverflowTap,
        child: const Padding(
          padding: EdgeInsets.all(AppDimens.spacingS),
          child: Icon(Icons.more_vert, color: AppColors.textSecondary, size: 24),
        ),
      ),
    ]);
  }

  Widget _buildBatchHeader() {
    return Row(children: [
      GestureDetector(
        onTap: onExitBatchMode,
        child: const Padding(
          padding: EdgeInsets.all(AppDimens.spacingS),
          child: Icon(Icons.close, color: AppColors.textSecondary, size: 24),
        ),
      ),
      const SizedBox(width: AppDimens.spacingS),
      Text('已选中 $selectedCount 项', style: const TextStyle(
        fontSize: 18, color: AppColors.textPrimary,
      )),
    ]);
  }
}
```

- [ ] **Step 3: 验证编译**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/widgets/note_list_header.dart lib/widgets/note_search_bar.dart`
Expected: No issues found

- [ ] **Step 4: 提交**

```bash
git add lib/widgets/note_list_header.dart lib/widgets/note_search_bar.dart
git commit -m "feat(phase2a): NoteListHeader + NoteSearchBar 组件"
```

---

### Task 8: 筛选面板组件

**Files:**
- Create: `lib/widgets/filter_panel.dart`

**Android 参考：** `code/HuaWeiNote/.../view/list/FilterPanelAdapter.kt`（5 种行类型 + 选中态样式）

- [ ] **Step 1: 创建 FilterPanel**

创建 `lib/widgets/filter_panel.dart`。此组件较大，包含 sealed class `_FilterRow` 定义和 5 种行类型的渲染。参考 Android `FilterPanelAdapter` 的 `Row` sealed class 和 5 种 ViewHolder。

完整代码见 spec 第 5 节的 Flutter 映射。关键实现点：

- `_buildRows()` 异步方法：调用各 Repository 的 `count()` 和 `list()` 构建行数据
- `_buildPseudoTile()`：4 个伪分类行（全部笔记/未分类/收藏/最近删除），选中态有左蓝条 + 浅蓝背景
- `_buildSectionHeader()`：「文件夹」section + 「管理」action（Toast 占位）
- `_buildFolderHead()`：文件夹行，点击展开/折叠，箭头动画旋转
- `_buildNotebookTile()`：笔记本行（缩进 48px），带颜色圆点

选中态对比逻辑：由于 `NoteListFilter` 子类无 `==` 重写，需按 `runtimeType` + 字段手动比较。

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/folder.dart';
import '../models/notebook.dart';
import '../providers/note_list_provider.dart';
import '../providers/repository_providers.dart';
import '../repositories/note_repository.dart';
import '../theme.dart';

class FilterPanel extends ConsumerWidget {
  const FilterPanel({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final listState = ref.watch(noteListProvider);
    return FutureBuilder<List<_FilterRow>>(
      future: _buildRows(ref, listState),
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const SizedBox.shrink();
        return Container(
          color: Colors.white,
          child: ListView.builder(
            itemCount: snapshot.data!.length,
            itemBuilder: (context, index) =>
                _buildRowWidget(context, ref, snapshot.data![index], listState.filter),
          ),
        );
      },
    );
  }

  Future<List<_FilterRow>> _buildRows(WidgetRef ref, NoteListState s) async {
    final noteRepo = ref.read(noteRepositoryProvider);
    final folderRepo = ref.read(folderRepositoryProvider);
    final notebookRepo = ref.read(notebookRepositoryProvider);
    final rows = <_FilterRow>[];

    rows.add(_PseudoRow('全部笔记', Icons.note_outlined,
        await noteRepo.count(filter: NoteListFilter.all), NoteListFilter.all));
    rows.add(_PseudoRow('未分类', Icons.folder_off_outlined,
        await noteRepo.count(filter: NoteListFilter.uncategorized), NoteListFilter.uncategorized));
    rows.add(_PseudoRow('收藏', Icons.star_border,
        await noteRepo.count(filter: NoteListFilter.favorite), NoteListFilter.favorite));
    rows.add(_PseudoRow('最近删除', Icons.delete_outline,
        await noteRepo.count(filter: NoteListFilter.deleted), NoteListFilter.deleted));
    rows.add(const _DividerRow());
    rows.add(const _SectionHeaderRow());

    for (final folder in await folderRepo.list()) {
      final fCount = await noteRepo.count(filter: NoteListFilter.folder(folder.id));
      final expanded = s.expandedFolders.contains(folder.id);
      rows.add(_FolderHeadRow(folder, expanded, fCount));
      if (expanded) {
        for (final nb in await notebookRepo.listByFolder(folder.id)) {
          final nbCount = await noteRepo.count(filter: NoteListFilter.notebook(nb.id));
          rows.add(_NotebookRow(nb, nbCount));
        }
      }
    }
    return rows;
  }

  Widget _buildRowWidget(BuildContext ctx, WidgetRef ref, _FilterRow row, NoteListFilter current) {
    return switch (row) {
      _PseudoRow r => _buildPseudoTile(ctx, ref, r, current),
      _DividerRow _ => const Divider(height: 1, indent: 16, endIndent: 16),
      _SectionHeaderRow _ => _buildSectionHeader(ctx),
      _FolderHeadRow r => _buildFolderHead(ref, r),
      _NotebookRow r => _buildNotebookTile(ctx, ref, r, current),
    };
  }

  bool _filtersEqual(NoteListFilter a, NoteListFilter b) {
    if (a is AllFilter && b is AllFilter) return true;
    if (a is UncategorizedFilter && b is UncategorizedFilter) return true;
    if (a is FavoriteFilter && b is FavoriteFilter) return true;
    if (a is DeletedFilter && b is DeletedFilter) return true;
    if (a is FolderFilter && b is FolderFilter) return a.folderId == b.folderId;
    if (a is NotebookFilter && b is NotebookFilter) return a.notebookId == b.notebookId;
    return false;
  }

  Widget _buildPseudoTile(BuildContext ctx, WidgetRef ref, _PseudoRow r, NoteListFilter current) {
    final sel = _filtersEqual(r.filter, current);
    return InkWell(
      onTap: () => ref.read(noteListProvider.notifier).setFilter(r.filter),
      child: Container(
        decoration: BoxDecoration(
          color: sel ? AppColors.primaryLight : Colors.transparent,
          border: sel ? const Border(left: BorderSide(color: AppColors.primary, width: 3)) : null,
        ),
        padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: 14),
        child: Row(children: [
          Icon(r.icon, size: 20, color: sel ? AppColors.primary : AppColors.textPrimary),
          const SizedBox(width: AppDimens.spacingM),
          Expanded(child: Text(r.label, style: TextStyle(
            fontSize: AppDimens.textBody,
            color: sel ? AppColors.primary : AppColors.textPrimary,
            fontWeight: sel ? FontWeight.w500 : FontWeight.normal,
          ))),
          Text('${r.count}', style: TextStyle(
            fontSize: AppDimens.textCaption + 1,
            color: sel ? AppColors.primary : AppColors.textHint,
          )),
        ]),
      ),
    );
  }

  Widget _buildSectionHeader(BuildContext ctx) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: AppDimens.spacingM),
      child: Row(children: [
        const Expanded(child: Text('文件夹', style: TextStyle(
          fontSize: AppDimens.textCaption + 1, color: AppColors.textSecondary, fontWeight: FontWeight.w500,
        ))),
        GestureDetector(
          onTap: () => ScaffoldMessenger.of(ctx).showSnackBar(
            const SnackBar(content: Text('文件夹管理将在 Phase 2E 实现'))),
          child: const Text('管理', style: TextStyle(
            fontSize: AppDimens.textCaption + 1, color: AppColors.primary,
          )),
        ),
      ]),
    );
  }

  Widget _buildFolderHead(WidgetRef ref, _FolderHeadRow r) {
    return InkWell(
      onTap: () => ref.read(noteListProvider.notifier).toggleFolderExpand(r.folder.id),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: AppDimens.spacingM),
        child: Row(children: [
          const Icon(Icons.folder_outlined, size: 20, color: AppColors.textPrimary),
          const SizedBox(width: AppDimens.spacingS),
          Expanded(child: Text(r.folder.name, style: const TextStyle(
            fontSize: AppDimens.textBody, color: AppColors.textPrimary,
          ))),
          Text('${r.count}', style: const TextStyle(
            fontSize: AppDimens.textCaption + 1, color: AppColors.textHint,
          )),
          const SizedBox(width: AppDimens.spacingS),
          AnimatedRotation(
            turns: r.expanded ? 0.5 : 0,
            duration: const Duration(milliseconds: 200),
            child: const Icon(Icons.arrow_drop_down, size: 20, color: AppColors.textHint),
          ),
        ]),
      ),
    );
  }

  Widget _buildNotebookTile(BuildContext ctx, WidgetRef ref, _NotebookRow r, NoteListFilter current) {
    final filter = NoteListFilter.notebook(r.notebook.id);
    final sel = _filtersEqual(filter, current);
    final cleaned = r.notebook.color.replaceFirst('#', '');
    final dotColor = int.tryParse(cleaned, radix: 16);
    return InkWell(
      onTap: () => ref.read(noteListProvider.notifier).setFilter(filter),
      child: Container(
        color: sel ? AppColors.primaryLight : Colors.transparent,
        padding: const EdgeInsets.fromLTRB(48, 10, AppDimens.spacingL, 10),
        child: Row(children: [
          Container(width: 10, height: 10, decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: dotColor != null ? Color(0xFF000000 | dotColor) : AppColors.textHint,
          )),
          const SizedBox(width: 10),
          Expanded(child: Text(r.notebook.name, style: TextStyle(
            fontSize: AppDimens.textBody,
            color: sel ? AppColors.primary : AppColors.textPrimary,
          ))),
          Text('${r.count}', style: TextStyle(
            fontSize: AppDimens.textCaption + 1,
            color: sel ? AppColors.primary : AppColors.textHint,
          )),
        ]),
      ),
    );
  }
}

sealed class _FilterRow { const _FilterRow(); }
class _PseudoRow extends _FilterRow {
  final String label; final IconData icon; final int count; final NoteListFilter filter;
  const _PseudoRow(this.label, this.icon, this.count, this.filter);
}
class _DividerRow extends _FilterRow { const _DividerRow(); }
class _SectionHeaderRow extends _FilterRow { const _SectionHeaderRow(); }
class _FolderHeadRow extends _FilterRow {
  final Folder folder; final bool expanded; final int count;
  const _FolderHeadRow(this.folder, this.expanded, this.count);
}
class _NotebookRow extends _FilterRow {
  final Notebook notebook; final int count;
  const _NotebookRow(this.notebook, this.count);
}
```

- [ ] **Step 2: 验证编译**

Run: `cd code/HuaweiNoteFlutter && flutter analyze lib/widgets/filter_panel.dart`
Expected: No issues found

- [ ] **Step 3: 提交**

```bash
git add lib/widgets/filter_panel.dart
git commit -m "feat(phase2a): 筛选面板 FilterPanel 组件"
```

---

### Task 9: NoteListPage 完整实现

**Files:**
- Modify: `lib/pages/note_list_page.dart` — 从占位替换为完整实现

**Android 参考：** `code/HuaWeiNote/.../controller/list/NoteListFragment.kt`（完整 548 行）

- [ ] **Step 1: 完整实现 NoteListPage**

用以下内容完全替换 `lib/pages/note_list_page.dart`。这是整个 Phase 2A 的核心页面，整合所有组件：

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import '../models/note.dart';
import '../providers/note_list_provider.dart';
import '../repositories/note_repository.dart';
import '../theme.dart';
import '../widgets/delete_confirm_sheet.dart';
import '../widgets/filter_panel.dart';
import '../widgets/note_card.dart';
import '../widgets/note_list_header.dart';
import '../widgets/note_search_bar.dart';
import '../widgets/sort_sheet.dart';

class NoteListPage extends ConsumerStatefulWidget {
  const NoteListPage({super.key});

  @override
  ConsumerState<NoteListPage> createState() => _NoteListPageState();
}

class _NoteListPageState extends ConsumerState<NoteListPage> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(noteListProvider.notifier).init());
  }

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(noteListProvider);
    final notifier = ref.read(noteListProvider.notifier);

    return Scaffold(
      backgroundColor: _pageBackground(s),
      body: SafeArea(
        child: Column(children: [
          if (!s.filterPanelVisible)
            NoteListHeader(
              title: s.headerTitle,
              subtitle: s.headerSubtitle,
              isBatchMode: s.isBatchMode,
              selectedCount: s.selectedIds.length,
              filterPanelVisible: s.filterPanelVisible,
              onToggleFilterPanel: notifier.toggleFilterPanel,
              onExitBatchMode: () => notifier.exitBatchMode(),
              onOverflowTap: () => _showOverflowMenu(context, s, notifier),
            ),
          if (!s.isBatchMode && !s.filterPanelVisible)
            NoteSearchBar(onQueryChanged: (q) => notifier.setQuery(q)),
          Expanded(child: Stack(children: [
            if (s.filterPanelVisible)
              const FilterPanel()
            else if (s.notes.isEmpty && !s.isLoading)
              _buildEmptyState()
            else
              _buildNoteList(s, notifier),
          ])),
          if (s.isBatchMode) _buildBatchBottomBar(s, notifier),
        ]),
      ),
      floatingActionButton:
          (s.isBatchMode || s.filterPanelVisible) ? null : FloatingActionButton(
            onPressed: () => ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('新建笔记将在 Phase 2B 实现'))),
            child: const Icon(Icons.add),
          ),
    );
  }

  Color _pageBackground(NoteListState s) {
    if (s.filter is NotebookFilter) {
      final nbId = (s.filter as NotebookFilter).notebookId;
      final hex = s.notebookColorMap[nbId];
      if (hex != null) {
        final cleaned = hex.replaceFirst('#', '');
        final v = int.tryParse(cleaned, radix: 16);
        if (v != null) {
          final c = Color(0xFF000000 | v);
          return Color.fromARGB(25, c.red, c.green, c.blue);
        }
      }
    }
    return AppColors.bgWindow;
  }

  Widget _buildNoteList(NoteListState s, NoteListNotifier notifier) {
    if (s.isGridView) {
      return MasonryGridView.count(
        crossAxisCount: 2,
        itemCount: s.notes.length,
        itemBuilder: (context, i) => _buildCard(s, notifier, s.notes[i]),
      );
    }
    return ListView.builder(
      itemCount: s.notes.length,
      itemBuilder: (context, i) => _buildCard(s, notifier, s.notes[i]),
    );
  }

  Widget _buildCard(NoteListState s, NoteListNotifier notifier, Note note) {
    return NoteCard(
      note: note,
      isBatchMode: s.isBatchMode,
      isSelected: s.selectedIds.contains(note.id),
      notebookColor: note.notebookId != null ? s.notebookColorMap[note.notebookId] : null,
      onTap: () => ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('编辑器将在 Phase 2B 实现'))),
      onLongPress: () => _showCardMenu(context, note, s, notifier),
      onBatchToggle: (_) => notifier.toggleSelection(note.id),
    );
  }

  Widget _buildEmptyState() {
    return const Center(child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.note_outlined, size: 64, color: AppColors.textHint),
        SizedBox(height: AppDimens.spacingS),
        Text('暂无笔记', style: TextStyle(fontSize: AppDimens.textBody, color: AppColors.textHint)),
      ],
    ));
  }

  Widget _buildBatchBottomBar(NoteListState s, NoteListNotifier notifier) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppDimens.spacingL),
      child: ElevatedButton(
        onPressed: s.selectedIds.isEmpty ? null : () async {
          final confirmed = await showDeleteConfirmSheet(context,
            message: '确定要删除选中的 ${s.selectedIds.length} 条笔记吗？\n删除后可在"最近删除"中恢复',
            confirmLabel: '删除');
          if (confirmed) await notifier.batchDelete();
        },
        style: ElevatedButton.styleFrom(
          backgroundColor: const Color(0xFFFF4444), foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(vertical: 14),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
        child: const Text('删除', style: TextStyle(fontSize: AppDimens.textBody)),
      ),
    );
  }

  void _showOverflowMenu(BuildContext ctx, NoteListState s, NoteListNotifier notifier) {
    showMenu<String>(
      context: ctx,
      position: RelativeRect.fromLTRB(MediaQuery.of(ctx).size.width - 48, 80, 8, 0),
      items: [
        const PopupMenuItem(value: 'sort', child: Text('排序方式')),
        PopupMenuItem(value: 'toggle_view',
          child: Text(s.isGridView ? '切换为列表视图' : '切换为宫格视图')),
        const PopupMenuItem(value: 'batch', child: Text('批量删除')),
      ],
    ).then((value) async {
      if (value == null) return;
      switch (value) {
        case 'sort':
          final result = await showSortSheet(ctx, current: s.sortBy);
          if (result != null) await notifier.setSort(result);
        case 'toggle_view': await notifier.toggleGridView();
        case 'batch': notifier.enterBatchMode();
      }
    });
  }

  void _showCardMenu(BuildContext ctx, Note note, NoteListState s, NoteListNotifier notifier) {
    final isDeleted = s.filter is DeletedFilter;
    showMenu<String>(
      context: ctx,
      position: RelativeRect.fromLTRB(MediaQuery.of(ctx).size.width / 2, 300, 50, 0),
      items: isDeleted
          ? [const PopupMenuItem(value: 'restore', child: Text('恢复')),
             const PopupMenuItem(value: 'perm_delete',
               child: Text('永久删除', style: TextStyle(color: Color(0xFFFF4444))))]
          : [PopupMenuItem(value: 'fav', child: Text(note.isFavorite ? '取消收藏' : '收藏')),
             const PopupMenuItem(value: 'delete', child: Text('删除')),
             const PopupMenuItem(value: 'move', child: Text('移入笔记本'))],
    ).then((value) async {
      if (value == null) return;
      switch (value) {
        case 'fav': await notifier.toggleFavorite(note.id);
        case 'delete':
          final ok = await showDeleteConfirmSheet(ctx,
            message: '确定要删除这条笔记吗？\n删除后可在"最近删除"中恢复', confirmLabel: '删除');
          if (ok) await notifier.softDelete(note.id);
        case 'restore': await notifier.restore(note.id);
        case 'perm_delete':
          final ok = await showDeleteConfirmSheet(ctx,
            message: '确定要永久删除这条笔记吗？\n此操作不可恢复', confirmLabel: '永久删除');
          if (ok) await notifier.deletePermanently(note.id);
        case 'move':
          ScaffoldMessenger.of(ctx).showSnackBar(
            const SnackBar(content: Text('移入笔记本将在 Phase 2E 实现')));
      }
    });
  }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd code/HuaweiNoteFlutter && flutter analyze`
Expected: No issues found

- [ ] **Step 3: 提交**

```bash
git add lib/pages/note_list_page.dart
git commit -m "feat(phase2a): 完整实现 NoteListPage 笔记列表页"
```

---

### Task 10: 全量集成验证 + 收尾

**Files:**
- 全部文件 — 运行分析和测试

- [ ] **Step 1: 运行静态分析**

Run: `cd code/HuaweiNoteFlutter && flutter analyze`
Expected: No issues found

- [ ] **Step 2: 运行全部测试**

Run: `cd code/HuaweiNoteFlutter && flutter test`
Expected: 全部 PASS（79 原有 + 12 新增 ≈ 91）

- [ ] **Step 3: 修复问题（如有）**

常见问题及解决：
- 未使用的 import → 删除
- 类型不匹配 → 检查 filter 子类名称是否全部从 `_XxxFilter` 更新为 `XxxFilter`
- `shared_preferences` 在测试中需要 mock → 添加 `SharedPreferences.setMockInitialValues({})` 到测试 setUp

- [ ] **Step 4: 最终提交（仅在有修复时）**

```bash
git add -u
git commit -m "fix(phase2a): 集成问题修复 + 全量测试通过"
```
