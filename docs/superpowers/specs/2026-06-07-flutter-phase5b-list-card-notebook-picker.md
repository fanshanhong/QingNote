# Phase 5B: 笔记列表卡片 + 笔记本选择器 + 默认数据

## 概述

四项改动：

1. **笔记列表卡片重做** — 增加缩略图、清单标记、无标题回退
2. **颜色调板对齐** — 笔记本颜色从 8 色改为 9 色
3. **默认数据改造** — "默认"文件夹→"我的笔记" + 预置 4 个笔记本
4. **编辑器笔记本选择器** — 从 PopupMenu 改为文件夹层级弹窗

## 1. 笔记列表卡片重做

### 1.1 数据层

**Note 表增加两列**（DB version 5→6）：

| 列名 | 类型 | 说明 |
|------|------|------|
| `first_image_path` | TEXT | 笔记中第一张图片的文件路径，无图片时为 null |
| `has_todo` | INTEGER | 0 = 无清单，1 = 有清单 |

**保存时自动提取**：在 `NoteRepository.save()` 中从 `content_json` 提取这两个值。提取逻辑：
- `first_image_path`：遍历 document JSON 子节点，找第一个 `type == 'image'` 的节点，取其 `data.url`
- `has_todo`：遍历 document JSON 子节点，找是否有 `type == 'todo_list'` 的节点

**Note model 增加字段**：
```dart
class Note {
  ...
  final String? firstImagePath;
  final bool hasTodo;
}
```

**DB 升级**（v5→v6）：
```sql
ALTER TABLE notes ADD COLUMN first_image_path TEXT;
ALTER TABLE notes ADD COLUMN has_todo INTEGER NOT NULL DEFAULT 0;
```

由于 APP 未上线，也可以直接卸载重装。但加升级语句更稳妥。

### 1.2 列表视图卡片布局

参考 Android 截图 `笔记列表样式.jpg`：

```
┌──────────────────────────────────────┐
│ 标题（粗体，最多1行）         [缩略图] │
│ 🔵 时间 | 摘要                48x48  │
└──────────────────────────────────────┘
```

- **标题**：`note.title`，如果 `isBlankTitle` 则取 `plainText` 第一行（截断到 maxLines=1）
- **第二行**：清单图标（✅，如 `hasTodo`）或录音图标（🎤，如有音频）+ 时间 + `|` + 摘要片段
- **缩略图**：48x48 圆角 4dp，右侧对齐。数据来自 `note.firstImagePath`，用 `Image.file()` 加载，`fit: BoxFit.cover`
- 批量模式下左侧仍有 checkbox
- 无缩略图时不显示图片区域

### 1.3 宫格视图

保持现有垂直布局不变，但也增加缩略图支持（如果有图片，显示在摘要文字下方）。

## 2. 颜色调板对齐

修改 `lib/widgets/folder/new_notebook_sheet.dart` 中 `notebookPalette`：

```dart
const notebookPalette = [
  '#E53935',  // 红
  '#FB8C00',  // 橙
  '#FBC02D',  // 黄
  '#43A047',  // 绿
  '#66BB6A',  // 浅绿
  '#00ACC1',  // 青
  '#42A5F5',  // 浅蓝
  '#8E24AA',  // 紫
  '#1E88E5',  // 蓝
];
```

从 8 色改为 9 色，新增"浅绿"和"浅蓝"，去掉原来的灰色 `#9E9E9E`。

默认选中颜色改为第一个（红色），而非灰色。

## 3. 默认数据改造

修改 `database_helper.dart` 中 `_seedDefaults`：

### 3.1 文件夹

| id | name | is_default |
|----|------|-----------|
| 1 | 我的笔记 | 1 |

### 3.2 预置笔记本

| id | name | folder_id | color | is_default |
|----|------|-----------|-------|-----------|
| 1 | 旅游 | 1 | #FBC02D（黄） | 0 |
| 2 | 个人 | 1 | #43A047（绿） | 0 |
| 3 | 生活 | 1 | #66BB6A（浅绿）| 0 |
| 4 | 工作 | 1 | #E53935（红） | 0 |

去掉原来 id=1 的"默认"笔记本。笔记的 `notebook_id` 默认不指定（NULL），在筛选面板中作为"未分类"显示。

### 3.3 DB 升级

APP 未上线，不需要迁移脚本。直接修改 `_seedDefaults` 即可。但 DB version 仍从 5→6（因为新增了 `first_image_path`/`has_todo` 列），升级时也需要处理默认数据。

实际处理：在 `_onUpgrade` v5→v6 中：
1. 添加 `first_image_path` 和 `has_todo` 列
2. 不做 seed 数据迁移（用户可卸载重装获取新 seed）

## 4. 编辑器笔记本选择器

### 4.1 触发方式

点击 MetadataStrip 右侧的"未分类▼"或"笔记本名▼"文字，弹出选择器。

### 4.2 弹窗样式

参考 `editor-notebook-picker.png`，使用 `showDialog` + 自定义 `Align` 定位在右侧：

```
┌──────────────────┐
│ 📁 我的笔记    ∧  │ ← 文件夹行，可展开/收起
│   📕 旅游          │ ← 笔记本行，点击选择
│   📗 个人          │
│   📘 生活          │
│   📙 工作          │
│   📄+ 新建         │ ← 新建笔记本（蓝色文字）
│──────────────────│
│ 📁 视频        ∧  │
│   📘 默认笔记本    │
│   📄+ 新建         │
└──────────────────┘
```

### 4.3 交互

- 点击笔记本行 → 调用 `notifier.setNotebook(nbId)`，关闭弹窗
- 点击"新建" → 弹出 `showNewNotebookSheet`，创建后自动选中新笔记本
- 点击文件夹行 → 展开/收起该文件夹
- 点击弹窗外部 → 关闭弹窗

### 4.4 实现

新建 `lib/widgets/editor/notebook_picker_popup.dart`，包含：
- `showNotebookPickerPopup(BuildContext, {int? currentNotebookId})` 函数
- 内部使用 `Dialog` + `FutureBuilder` 加载文件夹/笔记本数据
- 返回选中的 `notebookId`

在 `note_editor_page.dart` 中替换 `_showNotebookPicker` 方法。

## 文件变更清单

| 操作 | 文件 |
|------|------|
| Modify | `lib/db/database_helper.dart` — DB v6, 新增两列, 修改 seed |
| Modify | `lib/models/note.dart` — 增加 firstImagePath, hasTodo |
| Modify | `lib/repositories/note_repository.dart` — save() 提取缩略图/清单, rowToNote 读取新列 |
| Modify | `lib/widgets/note_card.dart` — 列表视图增加缩略图, 清单图标, 无标题回退 |
| Modify | `lib/widgets/folder/new_notebook_sheet.dart` — 颜色调板 9 色 |
| Create | `lib/widgets/editor/notebook_picker_popup.dart` — 笔记本选择器弹窗 |
| Modify | `lib/pages/note_editor_page.dart` — 替换 _showNotebookPicker |
