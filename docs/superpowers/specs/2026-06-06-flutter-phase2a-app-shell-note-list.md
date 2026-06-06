# Flutter Phase 2A: App Shell + 笔记列表页

**目标：** 实现 Flutter 跨平台 App 的第一个可见画面——底部导航 2-tab 容器 + 完整的笔记列表页，所有 UI/UX/交互/功能与 Android 原生版 `NoteListActivity` + `NoteListFragment` 完全一致。

**前置：** Phase 1（数据层）已完成，9 models + DatabaseHelper(v5) + 5 Repositories + 79 tests 位于 `code/HuaweiNoteFlutter/`。

**参考源码：**
- `code/HuaWeiNote/app/src/main/java/.../controller/list/NoteListActivity.kt` — 容器 Activity + 底部导航
- `code/HuaWeiNote/app/src/main/java/.../controller/list/NoteListFragment.kt` — 笔记列表全部逻辑
- `code/HuaWeiNote/app/src/main/java/.../controller/list/NoteListAdapter.kt` — 笔记卡片 Adapter
- `code/HuaWeiNote/app/src/main/java/.../view/list/FilterPanelAdapter.kt` — 筛选面板 Adapter
- `code/HuaWeiNote/app/src/main/res/layout/activity_note_list.xml` — 容器布局
- `code/HuaWeiNote/app/src/main/res/layout/fragment_note_list.xml` — 列表布局
- `code/HuaWeiNote/app/src/main/res/layout/item_note_card.xml` — 卡片布局
- `code/HuaWeiNote/app/src/main/res/layout/dialog_sort_picker.xml` — 排序 Sheet

---

## 1. 路由 + App Shell

### 路由结构（go_router）

```
ShellRoute（带 bottomNavigationBar）
  ├── /notes → NoteListPage
  └── /todos → TodoPlaceholderPage
```

- `main.dart` → `ProviderScope` → `MaterialApp.router(routerConfig: appRouter)`
- 初始路由 `/notes`

### 主题

```dart
ThemeData(
  colorScheme: ColorScheme.light(primary: Color(0xFF007DFF)),
  scaffoldBackgroundColor: Color(0xFFF5F5F5),  // bg_window
)
```

### 底部导航栏

- 2 tab：「笔记」(ic_note_tab) + 「待办」(ic_todo_tab)
- 选中态 `#007DFF` (primary)，未选中 `#999999` (text_hint)
- 批量模式时隐藏底部导航（对应 `NoteListActivity.setBottomNavVisible(false)`）
- tab 状态持久化到 SharedPreferences

**Android 对应：** `activity_note_list.xml` 中 `bottom_nav` LinearLayout + `NoteListActivity.updateNavHighlight()`

### TodoPlaceholderPage

居中文本「待办功能开发中」。Phase 2D 替换为真实实现。

---

## 2. NoteListPage 布局结构

**Android 对应：** `fragment_note_list.xml` 的 LinearLayout 层级

```
NoteListPage (ConsumerStatefulWidget)
├── Scaffold
│   ├── body: Column
│   │   ├── _NoteListHeader（标题区）
│   │   │   ├── 正常模式：大标题 + "N条笔记" + ▼ + ⋮
│   │   │   └── 批量模式：✕ + "已选中N项"（隐藏 ⋮/搜索/FAB）
│   │   ├── _SearchBar（200ms debounce TextField）
│   │   └── Expanded → Stack
│   │       ├── 笔记列表 (ListView/MasonryGridView) 或 空状态
│   │       └── 筛选面板 (Visibility/AnimatedSwitcher 覆盖)
│   ├── floatingActionButton: FAB（批量模式/筛选面板打开时隐藏）
│   └── bottomSheet/persistent bottom bar: 批量模式底部删除按钮
```

**关键对照：**
- 筛选面板通过 `Stack` + `Visibility` 覆盖在笔记列表上方（Android: `filterPanel.visibility = VISIBLE`）
- 搜索框在批量模式下隐藏（Android: `searchBar.visibility = GONE`）
- header 在筛选面板打开时隐藏（Android: `headerTitleArea.visibility = GONE`）
- FAB 在筛选面板/批量模式下隐藏

---

## 3. 状态管理 + 数据流

### 方案：单 StateNotifier 集中管理

一个 `NoteListNotifier extends StateNotifier<NoteListState>` 持有所有列表状态，与 Android `NoteListFragment` 的状态字段 1:1 对应。

### State

```dart
class NoteListState {
  final NoteListFilter filter;          // 当前筛选条件（对应 currentFilter）
  final NoteSortBy sortBy;              // 排序方式（对应 sortBy）
  final String query;                   // 搜索关键词（对应 currentQuery）
  final bool isGridView;                // 列表/宫格（对应 isGridView）
  final bool isBatchMode;               // 批量模式（对应 isBatchMode）
  final Set<int> selectedIds;           // 批量选中 ID
  final List<Note> notes;               // 当前列表数据
  final Map<int, String> notebookColorMap; // notebookId → color hex
  final bool isLoading;                 // 加载中
  final Set<int> expandedFolders;       // 筛选面板展开的文件夹（对应 expandedFolders）
  final bool filterPanelVisible;        // 筛选面板是否打开（对应 filterPanelVisible）
}
```

### Notifier 方法（对应 NoteListFragment 的方法）

| Flutter Notifier 方法 | Android Fragment 方法 |
|---|---|
| `reload()` | `reload()` — 调用 `NoteRepository.list(filter, sortBy, query)` |
| `setFilter(f)` | `onFilterPicked` lambda — 保存到 prefs + reload |
| `setSort(s)` | `showSortDialog()` 内 — 保存到 prefs + reload |
| `setQuery(q)` | search debounce callback — reload |
| `toggleGridView()` | overflow menu MENU_TOGGLE_VIEW — 保存 + 刷新 |
| `toggleFilterPanel()` | `toggleFilterPanel()` |
| `toggleFolderExpand(id)` | `onToggleFolder` lambda |
| `enterBatchMode()` | `enterBatchMode()` |
| `exitBatchMode()` | `exitBatchMode()` |
| `toggleSelection(id)` | adapter checkbox click |
| `batchDelete()` | `confirmBatchDelete()` |
| `softDelete(id)` | card menu action_delete |
| `toggleFavorite(id)` | card menu action_toggle_favorite |
| `restore(id)` | card menu action_restore |
| `deletePermanently(id)` | card menu action_delete_permanently |

### 数据流

```
用户操作 → Notifier 方法 → state = state.copyWith(...) → Widget 自动重建
                         ↓ (涉及 DB 操作时)
                    Repository.xxx() → DB → reload() → 更新 notes
```

### Provider 定义

```dart
final noteListProvider = StateNotifierProvider<NoteListNotifier, NoteListState>((ref) {
  final noteRepo = ref.watch(noteRepositoryProvider);
  final folderRepo = ref.watch(folderRepositoryProvider);
  final notebookRepo = ref.watch(notebookRepositoryProvider);
  return NoteListNotifier(noteRepo, folderRepo, notebookRepo);
});
```

Repository providers 从 Phase 1 已有的 `DatabaseHelper` 构建。

### 持久化偏好

`filter`、`sortBy`、`isGridView`、`activeTab` 的初始值从 `SharedPreferences` 读取，修改后写回。对应 Android 的 `loadSort()`/`saveSort()`、`loadFilter()`/`saveFilter()`、`loadGridView()`/`saveGridView()`、`loadActiveTab()`/`saveActiveTab()`。

---

## 4. 笔记卡片

**Android 对应：** `NoteListAdapter` + `item_note_card.xml`

### 外观

- `Card(elevation: 0, shape: RoundedRectangleBorder(borderRadius: radius_card, side: BorderSide(Color(0xFFE8E8E8), 0.5)))`
- 内部 `Padding(16h, 14v)` → `Row`
  - 批量模式：`Checkbox` (24×24)
  - `Expanded(Column)`
    - `Row`: 标题（加粗，单行截断） + 星标图标(20×20) + 时间
    - 摘要（最多 2 行，无内容时隐藏）

### 背景色规则（`NoteListAdapter.VH.bind()` 1:1 复制）

1. `note.background != "plain"` → 背景纹理色（linen=#F5F0E8, kraft=#E8D5B7, grid=#F8F8F8）
2. 否则，`note.notebookId` 存在且有笔记本颜色 → `Color.fromARGB(20, r, g, b)` 淡化
3. 否则 → `bg_card`（白色）

### 列表/宫格切换

- 列表模式：`ListView.builder`
- 宫格模式：`MasonryGridView.count(crossAxisCount: 2)` — 对应 Android `StaggeredGridLayoutManager(2)`
- 切换通过 `isGridView` 状态控制

### 点击/长按

- 正常模式单击 → Toast「编辑器在 Phase 2B 中实现」
- 正常模式长按 → 弹出 PopupMenu
- 批量模式单击 → 切换选中状态

### 长按菜单

**正常模式菜单（对应 `menu_note_card_long_press`）：**
- 收藏/取消收藏 → `toggleFavorite(id)`
- 删除 → 弹出 DeleteConfirmSheet → `softDelete(id)`
- 移入笔记本 → Toast「Phase 2E 实现」

**回收站模式菜单（对应 `menu_note_card_deleted`）：**
- 恢复 → `restore(id)`
- 永久删除 → 弹出 DeleteConfirmSheet → `deletePermanently(id)`

### 批量模式

- 进入：溢出菜单「批量删除」 → `enterBatchMode()`
- header 变为「✕ 已选中 N 项」，字号 18sp
- 卡片显示 Checkbox，点击切换选中
- 底部出现「删除」按钮，底部导航隐藏
- 退出：点击 ✕ 或完成删除 → `exitBatchMode()` → reload

---

## 5. 筛选面板

**Android 对应：** `FilterPanelAdapter` + `rebuildFilterPanel()`

### 行类型映射

| Android `FilterPanelAdapter.Row` | Flutter Widget |
|---|---|
| `Pseudo(kind, count)` | `_PseudoFilterTile` |
| `Divider` | `Divider(height: 1)` |
| `SectionHeader(title, actionLabel)` | `_SectionHeaderTile` |
| `FolderHead(folder, expanded, count)` | `_FolderHeaderTile` |
| `NotebookRow(notebook, count)` | `_NotebookTile` |

### 数据构建（对应 `rebuildFilterPanel()`）

```
rows = [
  Pseudo(All, noteCount),
  Pseudo(Uncategorized, uncatCount),
  Pseudo(Favorite, favCount),
  Pseudo(Deleted, delCount),
  Divider,
  SectionHeader("文件夹", "管理"),
  for each folder:
    FolderHead(folder, expanded, folderNoteCount),
    if expanded:
      for each notebook in folder:
        NotebookRow(notebook, notebookNoteCount),
]
```

### 选中态样式（对应 `applySelectedState()`）

- 左侧 3px 蓝色竖条 (`selected_bar`)
- 浅蓝背景 (`primary_light` / `#E8F0FE`)
- 蓝色文字 + 蓝色图标 (`primary` / `#007DFF`)

### 交互

- 点击 Pseudo → `setFilter(对应 filter)` → 关闭面板
- 点击 FolderHead → `toggleFolderExpand(folderId)` → 展开/折叠子笔记本
- 点击 NotebookRow → `setFilter(NoteListFilter.notebook(id))` → 关闭面板
- 点击「管理」→ Toast「Phase 2E 实现文件夹管理页」

---

## 6. Header 标题

**Android 对应：** `NoteListFragment.updateHeader()`

### 标题根据 filter 类型变化

| NoteListFilter | 标题 |
|---|---|
| All | "全部笔记" |
| Uncategorized | "未分类" |
| Favorite | "收藏" |
| Deleted | "最近删除" |
| Folder(id) | 文件夹名称（若已删除回退到 All）|
| Notebook(id) | 笔记本名称（若已删除回退到 All）|

### 副标题

- 默认格式："N 条笔记"
- Notebook 筛选时：如果笔记本属于某文件夹，副标题为 "N 条笔记 · 文件夹名"（对应 Android `note_count_with_folder_format`）

### 页面背景色（对应 `applyNotebookBackground()`）

- 筛选到某笔记本时：页面根容器背景变为该笔记本颜色的 `Color.fromARGB(25, r, g, b)` 淡化色
- 其他筛选条件：默认 `bg_window`（`#F5F5F5`）

### ▼ 箭头

- 点击展开筛选面板
- 筛选面板打开时旋转 180°
- 批量模式时变为 ✕ 图标

### ⋮ 溢出按钮

- 正常模式可见
- 批量模式隐藏

---

## 7. 搜索

**Android 对应：** `NoteListFragment` 中 `searchBar` EditText + 200ms Handler debounce

- `TextField` 带搜索图标
- 输入时 200ms debounce，然后调用 `setQuery(q)` → reload
- 批量模式下隐藏
- 筛选面板打开时隐藏

---

## 8. 排序 Sheet

**Android 对应：** `NoteListFragment.showSortDialog()` + `dialog_sort_picker.xml`

- `showModalBottomSheet` 弹出
- 标题「排序方式」（加粗）
- 2 个 `RadioListTile`：
  - 「按编辑时间」→ `NoteSortBy.updatedDesc`（默认）
  - 「按创建时间」→ `NoteSortBy.createdDesc`
- 「取消」按钮（蓝色文字）
- 选择后：保存到 prefs → reload → 关闭 sheet

---

## 9. 删除确认 Sheet

**Android 对应：** `DeleteConfirmBottomSheet`

通用组件 `DeleteConfirmSheet`：

```dart
DeleteConfirmSheet({
  required String message,
  required String confirmLabel,
  required VoidCallback onConfirm,
})
```

- 显示确认消息
- 2 个按钮：「取消」(灰底) + confirmLabel (红底白字)
- 使用场景：单条软删除、批量软删除、永久删除

---

## 10. 溢出菜单

**Android 对应：** `NoteListFragment.showOverflowMenu()`

`PopupMenuButton` 3 项：
1. 「排序方式」→ 打开排序 Sheet
2. 「切换为宫格/列表视图」→ `toggleGridView()`（文案动态：宫格时显示"切换为列表视图"）
3. 「批量删除」→ `enterBatchMode()`

---

## 11. FAB

- 右下角蓝色圆形 `+` 按钮
- 点击 → Toast「新建笔记在 Phase 2B 中实现」
- 筛选面板打开时隐藏
- 批量模式时隐藏

---

## 12. 空状态

- 笔记列表为空时居中显示
- 图标 + "暂无笔记" 文字

---

## 13. Phase 2A 边界处理

| 操作 | 行为 | 后续 Phase |
|---|---|---|
| 点击笔记卡片 | Toast 提示 | Phase 2B |
| FAB 新建笔记 | Toast 提示 | Phase 2B |
| 长按菜单「移入笔记本」 | Toast 提示 | Phase 2E |
| 筛选面板「管理」按钮 | Toast 提示 | Phase 2E |

---

## 14. 新增依赖

| Package | 用途 |
|---|---|
| `shared_preferences` | 持久化 filter / sortBy / isGridView / activeTab |
| `flutter_staggered_grid_view` | 宫格瀑布流布局（对应 Android StaggeredGridLayoutManager） |

---

## 15. 文件结构

```
lib/
├── main.dart                          # App 入口 + ProviderScope + MaterialApp.router
├── router.dart                        # go_router 配置（ShellRoute + 2 tab）
├── theme.dart                         # ThemeData 定义
├── providers/
│   ├── database_provider.dart         # DatabaseHelper provider
│   ├── repository_providers.dart      # 5 个 Repository provider
│   └── note_list_provider.dart        # NoteListNotifier + NoteListState + provider
├── pages/
│   ├── app_shell.dart                 # ShellRoute 的 shell widget（底部导航栏）
│   ├── note_list_page.dart            # NoteListPage 主页面
│   └── todo_placeholder_page.dart     # 待办占位页
├── widgets/
│   ├── note_list_header.dart          # Header 组件（正常/批量模式）
│   ├── search_bar.dart                # 搜索框（200ms debounce）
│   ├── note_card.dart                 # 笔记卡片
│   ├── filter_panel.dart              # 筛选面板
│   ├── sort_sheet.dart                # 排序 BottomSheet
│   └── delete_confirm_sheet.dart      # 删除确认 BottomSheet
└── utils/
    ├── date_utils.dart                # 相对时间格式化（对应 Android DateUtils）
    └── text_utils.dart                # 标题判空 + 摘要截取（对应 Android TextUtils）
```
