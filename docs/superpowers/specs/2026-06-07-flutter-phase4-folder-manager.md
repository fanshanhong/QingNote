# Phase 4：Flutter 文件夹/笔记本管理页 设计文档

## 概述

实现文件夹/笔记本管理页面，对标 Android 版 `FolderManagerActivity`。提供文件夹和笔记本的新建、重命名、删除、改色、移动、拖拽排序等完整管理功能。

**目标**：用户可以在独立管理页面中组织文件夹和笔记本层级结构。

**前提**：
- `FolderRepository` 已实现完整 CRUD（list/insert/rename/softDelete/reorder）
- `NotebookRepository` 已实现完整 CRUD（listByFolder/insert/rename/updateColor/softDelete/move/reorderInFolder）
- `FilterPanel` 已有 "管理" 占位按钮
- `Folder` / `Notebook` 数据模型已定义
- 路由 `go_router` 已配置

---

## 1. 页面结构

独立全屏页面 `FolderManagerPage`，路由 `/folder-manager`。

### 1.1 页面布局

- **顶栏**：返回按钮 + 标题 "文件夹管理" + 右上角新建文件夹图标按钮
- **列表区**：`ReorderableListView` 展示三种行类型

### 1.2 行类型

| 行类型 | 展示内容 |
|---|---|
| FolderHeader | 文件夹图标 + 文件夹名 + 展开/收起箭头 + 溢出菜单(⋮) |
| NotebookItem | 缩进(左侧 48px) + 颜色圆点 + 笔记本名 + 溢出菜单(⋮) |
| CreateNotebookButton | 缩进 + "+" 图标 + "新建笔记本" 文字（每个展开文件夹末尾） |

---

## 2. 交互功能

### 2.1 文件夹操作

| 操作 | 触发方式 | 行为 |
|---|---|---|
| 展开/收起 | 点击 FolderHeader 任意位置 | 切换文件夹下笔记本的显示/隐藏，箭头动画旋转 |
| 新建文件夹 | 顶栏右上角图标按钮 | 弹出 `NewFolderSheet`（名称输入） |
| 重命名 | FolderHeader 溢出菜单 → "重命名" | 弹出 `NewFolderSheet`（预填当前名称） |
| 删除 | FolderHeader 溢出菜单 → "删除" | 确认对话框 → `FolderRepository.softDelete`（级联删除子笔记本和笔记） |

### 2.2 笔记本操作

| 操作 | 触发方式 | 行为 |
|---|---|---|
| 新建笔记本 | 展开区尾部 CreateNotebookButton | 弹出 `NewNotebookSheet`（名称 + 8色调色盘） |
| 重命名/改色 | NotebookItem 溢出菜单 → "编辑" | 弹出 `NewNotebookSheet`（预填名称和当前颜色） |
| 移动到 | NotebookItem 溢出菜单 → "移动到" | `AlertDialog` 列出所有文件夹供选择 → `NotebookRepository.move` |
| 删除 | NotebookItem 溢出菜单 → "删除" | 确认对话框 → `NotebookRepository.softDelete`（级联删除笔记） |

### 2.3 拖拽排序

- **触发**：长按列表项
- **文件夹**：整段移动（folder header + 子笔记本 + 新建按钮作为一组）
- **笔记本**：同文件夹内排序
- **释放后**：调用 `FolderRepository.reorder` 或 `NotebookRepository.reorderInFolder` 持久化

### 2.4 保护规则

| 对象 | 限制 |
|---|---|
| 默认文件夹(id=1) | 不可重命名、不可删除、不可拖拽，始终排在首位 |
| 默认笔记本(id=1) | 不可删除、不可移动 |

操作被拒时通过 `Toast` 提示用户（如 "默认文件夹不可删除"）。

---

## 3. 文件结构

| 文件 | 职责 |
|---|---|
| `lib/pages/folder_manager_page.dart` (create) | 管理页主体：顶栏 + 列表 + 拖拽 + 溢出菜单 |
| `lib/providers/folder_manager_provider.dart` (create) | 状态管理：行列表构建、展开状态、CRUD |
| `lib/widgets/folder/new_folder_sheet.dart` (create) | 新建/重命名文件夹 BottomSheet |
| `lib/widgets/folder/new_notebook_sheet.dart` (create) | 新建/编辑笔记本 BottomSheet（含 8 色调色盘） |
| `lib/router.dart` (modify) | 注册 `/folder-manager` 路由 |
| `lib/widgets/filter_panel.dart` (modify) | "管理" 按钮导航到管理页，返回时刷新 |

---

## 4. 状态管理

### 4.1 FolderManagerState

```dart
class FolderManagerState {
  final List<FolderManagerRow> rows;
  final Set<int> expandedFolders;
  final bool isLoading;
}
```

### 4.2 FolderManagerRow（sealed class）

```dart
sealed class FolderManagerRow {
  const FolderManagerRow();
}
class FolderHeadRow extends FolderManagerRow {
  final Folder folder;
  final bool expanded;
}
class NotebookItemRow extends FolderManagerRow {
  final Notebook notebook;
}
class CreateNotebookRow extends FolderManagerRow {
  final int folderId;
}
```

### 4.3 FolderManagerNotifier 方法

| 方法 | 行为 |
|---|---|
| `load()` | 从 DB 构建行列表（文件夹 + 展开的子笔记本 + 新建按钮） |
| `toggleExpand(int folderId)` | 切换展开状态并重建行列表 |
| `createFolder(String name)` | 调用 `FolderRepository.insert` → reload |
| `renameFolder(int id, String name)` | 调用 `FolderRepository.rename` → reload |
| `deleteFolder(int id)` | 调用 `FolderRepository.softDelete` → reload |
| `createNotebook(int folderId, String name, String color)` | 调用 `NotebookRepository.insert` → reload |
| `renameNotebook(int id, String name, String color)` | 调用 `rename` + `updateColor` → reload |
| `moveNotebook(int id, int targetFolderId)` | 调用 `NotebookRepository.move` → reload |
| `deleteNotebook(int id)` | 调用 `NotebookRepository.softDelete` → reload |
| `onReorder(int oldIndex, int newIndex)` | 处理拖拽排序，按类型调用 reorder/reorderInFolder |

---

## 5. BottomSheet 组件

### 5.1 NewFolderSheet

- 标题：新建时 "新建文件夹"，编辑时 "重命名文件夹"
- 输入框：文件夹名称，编辑时预填当前名称并选中
- 按钮：取消 + 确认（名称为空时确认按钮无效）

### 5.2 NewNotebookSheet

- 标题：新建时 "新建笔记本"，编辑时 "编辑笔记本"
- 输入框：笔记本名称
- 调色盘：8 色横排圆点，选中态有描边
- 颜色列表：`#9E9E9E`、`#E53935`、`#FB8C00`、`#FBC02D`、`#43A047`、`#00ACC1`、`#1E88E5`、`#8E24AA`
- 按钮：取消 + 确认

---

## 6. 路由与导航

### 6.1 路由注册

在 `router.dart` 添加：
```dart
GoRoute(path: '/folder-manager', builder: (_, __) => const FolderManagerPage())
```

### 6.2 FilterPanel 接入

将 FilterPanel 的 "管理" 按钮从 SnackBar 改为导航：
```dart
context.push<bool>('/folder-manager')
```

返回 `true` 时触发 FilterPanel 重建（重新构建行列表以反映变更）。

---

## 7. 与已有代码的集成

### FilterPanel 刷新

管理页返回 `true` 时，FilterPanel 通过 `setState` 重新调用 `_buildRows()` 刷新筛选面板中的文件夹/笔记本列表和计数。

### NoteListProvider

文件夹/笔记本的变更可能影响当前筛选条件。如果用户删除了当前正在筛选的文件夹/笔记本，NoteListProvider 应回退到 "全部笔记" 筛选。但这属于防御性处理，不在本 Phase 核心范围内，通过管理页返回时刷新列表即可保证数据一致性。
