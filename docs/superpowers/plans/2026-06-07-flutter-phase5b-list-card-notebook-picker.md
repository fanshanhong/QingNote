# Phase 5B: 笔记列表卡片 + 笔记本选择器 + 默认数据 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** 笔记列表卡片增加缩略图/清单标记，笔记本颜色调板对齐 9 色，默认数据改为"我的笔记"+ 4 个预置笔记本，编辑器笔记本选择器改为文件夹层级弹窗。

**Architecture:** 数据层先行（DB schema + model + repository），再做 UI 层改动。每个 Task 独立可编译。

**Tech Stack:** Flutter + Riverpod + sqflite + go_router + AppFlowyEditor

**分支:** `feature/cross-platform`

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| Modify | `lib/db/database_helper.dart` | DB v6：新增两列 + 修改 seed |
| Modify | `lib/models/note.dart` | 增加 firstImagePath, hasTodo 字段 |
| Modify | `lib/repositories/note_repository.dart` | save() 提取缩略图/清单，_rowToNote 读取新列 |
| Modify | `lib/widgets/note_card.dart` | 列表视图增加缩略图、清单图标、无标题回退 |
| Modify | `lib/widgets/folder/new_notebook_sheet.dart` | 颜色调板 8 色→9 色 |
| Create | `lib/widgets/editor/notebook_picker_popup.dart` | 编辑器笔记本选择器弹窗 |
| Modify | `lib/pages/note_editor_page.dart` | 替换 _showNotebookPicker |

---

## Task 1: DB schema 升级 + 默认数据改造

**Files:**
- Modify: `lib/db/database_helper.dart`

- [ ] **Step 1: 修改 _dbVersion 和 _seedDefaults**

```dart
// database_helper.dart 第 6 行
static const _dbVersion = 6;
```

`_seedDefaults` 方法完整替换为：

```dart
Future<void> _seedDefaults(
  DatabaseExecutor db, {
  required bool allNotesExist,
}) async {
  await db.insert('folders', {
    'id': 1,
    'name': '我的笔记',
    'order_index': 0,
    'is_default': 1,
    'deleted_at': 0,
  });
  final presetNotebooks = [
    {'id': 1, 'name': '旅游', 'color': '#FBC02D', 'order_index': 0},
    {'id': 2, 'name': '个人', 'color': '#43A047', 'order_index': 1},
    {'id': 3, 'name': '生活', 'color': '#66BB6A', 'order_index': 2},
    {'id': 4, 'name': '工作', 'color': '#E53935', 'order_index': 3},
  ];
  for (final nb in presetNotebooks) {
    await db.insert('notebooks', {
      ...nb,
      'folder_id': 1,
      'is_default': 0,
      'deleted_at': 0,
    });
  }
}
```

- [ ] **Step 2: 添加 v5→v6 升级逻辑**

在 `_onUpgrade` 方法中，`if (oldVersion < 5)` 块之后添加：

```dart
if (oldVersion < 6) {
  await db.execute(
    'ALTER TABLE notes ADD COLUMN first_image_path TEXT',
  );
  await db.execute(
    'ALTER TABLE notes ADD COLUMN has_todo INTEGER NOT NULL DEFAULT 0',
  );
}
```

- [ ] **Step 3: 更新 _sqlCreateNotes 添加新列**

在 `_sqlCreateNotes` 常量中 `background` 行之后添加两行：

```sql
first_image_path TEXT,
has_todo INTEGER NOT NULL DEFAULT 0
```

完整的 `_sqlCreateNotes`：

```dart
static const _sqlCreateNotes = '''
  CREATE TABLE notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL DEFAULT '',
    plain_text TEXT NOT NULL DEFAULT '',
    content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
    is_favorite INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    category_id INTEGER,
    deleted_at INTEGER NOT NULL DEFAULT 0,
    notebook_id INTEGER,
    background TEXT NOT NULL DEFAULT 'plain',
    first_image_path TEXT,
    has_todo INTEGER NOT NULL DEFAULT 0
  )
''';
```

- [ ] **Step 4: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze lib/db/database_helper.dart
```

- [ ] **Step 5: 提交**

```bash
git add code/HuaweiNoteFlutter/lib/db/database_helper.dart
git commit -m "feat: DB v6 增加缩略图/清单列 + 默认数据改为我的笔记+4预置笔记本"
```

---

## Task 2: Note model + NoteRepository 适配新列

**Files:**
- Modify: `lib/models/note.dart`
- Modify: `lib/repositories/note_repository.dart`

- [ ] **Step 1: Note model 增加字段**

`lib/models/note.dart` 完整替换为：

```dart
import 'note_content.dart';

class Note {
  final int id;
  final String title;
  final String plainText;
  final bool isFavorite;
  final int createdAt;
  final int updatedAt;
  final NoteContent content;
  final int? categoryId;
  final int deletedAt;
  final int? notebookId;
  final String background;
  final String? firstImagePath;
  final bool hasTodo;

  Note({
    required this.id,
    this.title = '',
    this.plainText = '',
    this.isFavorite = false,
    required this.createdAt,
    required this.updatedAt,
    NoteContent? content,
    this.categoryId,
    this.deletedAt = 0,
    this.notebookId,
    this.background = 'plain',
    this.firstImagePath,
    this.hasTodo = false,
  }) : content = content ?? NoteContent.empty();

  factory Note.newNote({int? now}) {
    final ts = now ?? DateTime.now().millisecondsSinceEpoch;
    return Note(id: 0, createdAt: ts, updatedAt: ts);
  }

  Note copyWith({
    int? id, String? title, String? plainText, bool? isFavorite,
    int? createdAt, int? updatedAt, NoteContent? content, int? categoryId,
    bool setCategoryIdNull = false, int? deletedAt, int? notebookId,
    bool setNotebookIdNull = false, String? background,
    String? firstImagePath, bool clearFirstImagePath = false,
    bool? hasTodo,
  }) => Note(
    id: id ?? this.id,
    title: title ?? this.title,
    plainText: plainText ?? this.plainText,
    isFavorite: isFavorite ?? this.isFavorite,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    content: content ?? this.content,
    categoryId: setCategoryIdNull ? null : (categoryId ?? this.categoryId),
    deletedAt: deletedAt ?? this.deletedAt,
    notebookId: setNotebookIdNull ? null : (notebookId ?? this.notebookId),
    background: background ?? this.background,
    firstImagePath: clearFirstImagePath ? null : (firstImagePath ?? this.firstImagePath),
    hasTodo: hasTodo ?? this.hasTodo,
  );
}
```

- [ ] **Step 2: NoteRepository save() 提取缩略图和清单标记**

在 `lib/repositories/note_repository.dart` 中，`save` 方法里，在 `final values = <String, Object?>{` 块中添加两个字段。在 `values` 声明前添加提取逻辑：

```dart
Future<int> save(Note note) async {
  final db = await _dbHelper.database;
  final now = DateTime.now().millisecondsSinceEpoch;

  final contentStr = note.content.toJson();
  final firstImage = _extractFirstImagePath(note.content);
  final hasTodo = _hasTodoBlock(note.content);

  final values = <String, Object?>{
    'title': note.title,
    'plain_text': note.content.toPlainText(),
    'content_json': contentStr,
    'is_favorite': note.isFavorite ? 1 : 0,
    'updated_at': now,
    'category_id': note.categoryId,
    'deleted_at': note.deletedAt,
    'notebook_id': note.notebookId,
    'background': note.background,
    'first_image_path': firstImage,
    'has_todo': hasTodo ? 1 : 0,
  };
  if (note.id == 0) {
    values['created_at'] = note.createdAt > 0 ? note.createdAt : now;
    return await db.insert('notes', values);
  } else {
    await db.update('notes', values, where: 'id = ?', whereArgs: [note.id]);
    return note.id;
  }
}
```

- [ ] **Step 3: 添加提取方法**

在 `NoteRepository` 类底部（`_rowToNote` 之前）添加：

```dart
static String? _extractFirstImagePath(NoteContent content) {
  final doc = content.documentJson['document'] as Map<String, dynamic>?;
  if (doc == null) return null;
  final children = doc['children'] as List<dynamic>? ?? [];
  for (final node in children) {
    final map = node as Map<String, dynamic>;
    if (map['type'] == 'image') {
      final data = map['data'] as Map<String, dynamic>?;
      if (data != null) {
        return data['url'] as String?;
      }
    }
  }
  return null;
}

static bool _hasTodoBlock(NoteContent content) {
  final doc = content.documentJson['document'] as Map<String, dynamic>?;
  if (doc == null) return false;
  final children = doc['children'] as List<dynamic>? ?? [];
  for (final node in children) {
    final map = node as Map<String, dynamic>;
    if (map['type'] == 'todo_list') return true;
  }
  return false;
}
```

- [ ] **Step 4: 更新 _rowToNote 读取新列**

```dart
Note _rowToNote(Map<String, Object?> row) {
  final contentJson = row['content_json'] as String? ?? '';
  return Note(
    id: row['id'] as int,
    title: row['title'] as String? ?? '',
    plainText: row['plain_text'] as String? ?? '',
    isFavorite: (row['is_favorite'] as int) == 1,
    createdAt: row['created_at'] as int,
    updatedAt: row['updated_at'] as int,
    content: NoteContent.fromJson(contentJson),
    categoryId: row['category_id'] as int?,
    deletedAt: row['deleted_at'] as int? ?? 0,
    notebookId: row['notebook_id'] as int?,
    background: row['background'] as String? ?? 'plain',
    firstImagePath: row['first_image_path'] as String?,
    hasTodo: (row['has_todo'] as int? ?? 0) == 1,
  );
}
```

- [ ] **Step 5: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze lib/models/note.dart lib/repositories/note_repository.dart
```

- [ ] **Step 6: 提交**

```bash
git add code/HuaweiNoteFlutter/lib/models/note.dart code/HuaweiNoteFlutter/lib/repositories/note_repository.dart
git commit -m "feat: Note model/repository 适配缩略图+清单列"
```

---

## Task 3: 笔记列表卡片重做

**Files:**
- Modify: `lib/widgets/note_card.dart`

- [ ] **Step 1: 重写 NoteCard**

`lib/widgets/note_card.dart` 完整替换为：

```dart
import 'dart:io';
import 'package:flutter/material.dart';
import '../models/note.dart';
import '../theme.dart';
import '../utils/color_utils.dart';
import '../utils/date_utils.dart';
import '../utils/text_utils.dart';

class NoteCard extends StatelessWidget {
  final Note note;
  final bool isGridView;
  final bool isBatchMode;
  final bool isSelected;
  final String? notebookColor;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final ValueChanged<bool> onBatchToggle;

  const NoteCard({
    super.key,
    required this.note,
    this.isGridView = false,
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
      final c = AppColorUtils.parseHex(notebookColor!);
      if (c != null) return AppColorUtils.tint(c, 20);
    }
    return AppColors.bgCard;
  }

  String _displayTitle() {
    if (!AppTextUtils.isBlankTitle(note.title)) return note.title;
    final firstLine = note.plainText.split('\n').firstWhere(
      (l) => l.trim().isNotEmpty,
      orElse: () => '',
    );
    return firstLine.isNotEmpty ? firstLine : '无标题';
  }

  @override
  Widget build(BuildContext context) {
    final title = _displayTitle();
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
          child: isGridView
              ? _buildGridContent(title, summaryText, timeText)
              : _buildListContent(title, summaryText, timeText),
        ),
      ),
    );
  }

  Widget _buildListContent(String title, String summaryText, String timeText) {
    return Row(children: [
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
          Text(title, maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: AppDimens.textBody, fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: AppDimens.spacingXs),
          Row(children: [
            if (note.hasTodo) ...[
              const Icon(Icons.check_circle_outline, size: 14, color: AppColors.textHint),
              const SizedBox(width: 4),
            ],
            Text(timeText, style: const TextStyle(
              fontSize: AppDimens.textHint, color: AppColors.textHint,
            )),
            if (summaryText.isNotEmpty) ...[
              const Text(' | ', style: TextStyle(
                fontSize: AppDimens.textHint, color: AppColors.textHint,
              )),
              Expanded(child: Text(summaryText, maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: AppDimens.textHint, color: AppColors.textHint,
                ),
              )),
            ],
          ]),
        ],
      )),
      if (!isBatchMode && note.firstImagePath != null) ...[
        const SizedBox(width: AppDimens.spacingS),
        ClipRRect(
          borderRadius: BorderRadius.circular(4),
          child: Image.file(
            File(note.firstImagePath!),
            width: 48,
            height: 48,
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) => const SizedBox.shrink(),
          ),
        ),
      ],
    ]);
  }

  Widget _buildGridContent(String title, String summaryText, String timeText) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            fontSize: AppDimens.textBody, fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        if (summaryText.isNotEmpty) ...[
          const SizedBox(height: AppDimens.spacingXs),
          Text(summaryText, maxLines: 5, overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: AppDimens.textCaption, color: AppColors.textSecondary,
            ),
          ),
        ],
        const SizedBox(height: AppDimens.spacingS),
        Row(children: [
          if (note.hasTodo) ...[
            const Icon(Icons.check_circle_outline, size: 14, color: AppColors.textHint),
            const SizedBox(width: 4),
          ],
          if (note.isFavorite) ...[
            const Icon(Icons.star, size: 14, color: Colors.amber),
            const SizedBox(width: 4),
          ],
          Text(timeText, style: const TextStyle(
            fontSize: AppDimens.textHint, color: AppColors.textHint,
          )),
        ]),
      ],
    );
  }
}
```

关键变更：
- `_displayTitle()` 新方法：无标题时取 plainText 第一行
- 列表视图第二行：清单图标 + 时间 + `|` + 摘要（参考 Android 截图样式）
- 列表视图右侧缩略图：48x48 圆角 4dp，用 `Image.file()`
- 宫格视图底部增加清单图标
- 移除了列表视图中单独的星标图标（参考截图列表视图无星标，只在宫格视图有）

- [ ] **Step 2: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze lib/widgets/note_card.dart
```

- [ ] **Step 3: 提交**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/note_card.dart
git commit -m "feat: 笔记列表卡片增加缩略图、清单标记、无标题回退"
```

---

## Task 4: 颜色调板 8 色→9 色

**Files:**
- Modify: `lib/widgets/folder/new_notebook_sheet.dart`

- [ ] **Step 1: 更新 notebookPalette 常量**

`lib/widgets/folder/new_notebook_sheet.dart` 第 10-13 行，替换 `notebookPalette`：

```dart
const notebookPalette = [
  '#E53935', // 红
  '#FB8C00', // 橙
  '#FBC02D', // 黄
  '#43A047', // 绿
  '#66BB6A', // 浅绿
  '#00ACC1', // 青
  '#42A5F5', // 浅蓝
  '#8E24AA', // 紫
  '#1E88E5', // 蓝
];
```

- [ ] **Step 2: 缩小颜色圆点尺寸以适配 9 个**

颜色圆点从 `width: 32, margin: horizontal: 6` 改为 `width: 28, margin: horizontal: 4`，确保 9 个圆点在一行内：

在 `_NewNotebookContentState.build()` 中（约第 99-120 行），替换颜色行的 Row：

```dart
Wrap(
  alignment: WrapAlignment.center,
  spacing: 8,
  runSpacing: 8,
  children: notebookPalette.map((hex) {
    final selected = hex == _selectedColor;
    return GestureDetector(
      onTap: () => setState(() => _selectedColor = hex),
      child: Container(
        width: 28,
        height: 28,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: _parseHex(hex),
          border: selected
              ? Border.all(color: AppColors.primary, width: 2.5)
              : null,
        ),
        child: selected
            ? const Icon(Icons.check, size: 14, color: Colors.white)
            : null,
      ),
    );
  }).toList(),
),
```

- [ ] **Step 3: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze lib/widgets/folder/new_notebook_sheet.dart
```

- [ ] **Step 4: 提交**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/folder/new_notebook_sheet.dart
git commit -m "feat: 笔记本颜色调板从8色改为9色"
```

---

## Task 5: 编辑器笔记本选择器弹窗

**Files:**
- Create: `lib/widgets/editor/notebook_picker_popup.dart`
- Modify: `lib/pages/note_editor_page.dart`

- [ ] **Step 1: 创建 notebook_picker_popup.dart**

```dart
import 'package:flutter/material.dart';
import '../../models/folder.dart';
import '../../models/notebook.dart';
import '../../repositories/folder_repository.dart';
import '../../repositories/notebook_repository.dart';
import '../../theme.dart';
import '../../utils/color_utils.dart';
import '../folder/new_notebook_sheet.dart';

Future<int?> showNotebookPickerPopup(
  BuildContext context, {
  required FolderRepository folderRepo,
  required NotebookRepository notebookRepo,
  int? currentNotebookId,
}) async {
  return showDialog<int>(
    context: context,
    barrierColor: Colors.transparent,
    builder: (ctx) => _NotebookPickerDialog(
      folderRepo: folderRepo,
      notebookRepo: notebookRepo,
      currentNotebookId: currentNotebookId,
    ),
  );
}

class _NotebookPickerDialog extends StatefulWidget {
  final FolderRepository folderRepo;
  final NotebookRepository notebookRepo;
  final int? currentNotebookId;

  const _NotebookPickerDialog({
    required this.folderRepo,
    required this.notebookRepo,
    this.currentNotebookId,
  });

  @override
  State<_NotebookPickerDialog> createState() => _NotebookPickerDialogState();
}

class _NotebookPickerDialogState extends State<_NotebookPickerDialog> {
  List<_PickerRow>? _rows;
  final Set<int> _expanded = {};

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final folders = await widget.folderRepo.list();
    if (_expanded.isEmpty) {
      _expanded.addAll(folders.map((f) => f.id));
    }
    await _buildRows(folders);
  }

  Future<void> _buildRows(List<Folder>? folders) async {
    folders ??= await widget.folderRepo.list();
    final rows = <_PickerRow>[];
    for (final folder in folders) {
      final expanded = _expanded.contains(folder.id);
      rows.add(_FolderRow(folder, expanded));
      if (expanded) {
        final notebooks = await widget.notebookRepo.listByFolder(folder.id);
        for (final nb in notebooks) {
          rows.add(_NotebookRow(nb));
        }
        rows.add(_CreateRow(folder.id));
      }
    }
    if (mounted) setState(() => _rows = rows);
  }

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.topRight,
      child: Padding(
        padding: const EdgeInsets.only(top: 100, right: 16),
        child: Material(
          elevation: 8,
          borderRadius: BorderRadius.circular(12),
          color: Colors.white,
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxWidth: 240,
              maxHeight: MediaQuery.of(context).size.height * 0.5,
            ),
            child: _rows == null
                ? const Padding(
                    padding: EdgeInsets.all(24),
                    child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
                  )
                : ListView.builder(
                    shrinkWrap: true,
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    itemCount: _rows!.length,
                    itemBuilder: (ctx, i) => _buildRow(_rows![i]),
                  ),
          ),
        ),
      ),
    );
  }

  Widget _buildRow(_PickerRow row) {
    return switch (row) {
      _FolderRow r => _buildFolderRow(r),
      _NotebookRow r => _buildNotebookRow(r),
      _CreateRow r => _buildCreateRow(r),
    };
  }

  Widget _buildFolderRow(_FolderRow row) {
    return InkWell(
      onTap: () {
        if (_expanded.contains(row.folder.id)) {
          _expanded.remove(row.folder.id);
        } else {
          _expanded.add(row.folder.id);
        }
        _buildRows(null);
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(children: [
          const Icon(Icons.folder_outlined, size: 18, color: AppColors.textPrimary),
          const SizedBox(width: 8),
          Expanded(child: Text(row.folder.name, style: const TextStyle(
            fontSize: AppDimens.textBody, color: AppColors.textPrimary,
            fontWeight: FontWeight.w500,
          ))),
          AnimatedRotation(
            turns: row.expanded ? 0.5 : 0,
            duration: const Duration(milliseconds: 200),
            child: const Icon(Icons.keyboard_arrow_down, size: 18, color: AppColors.textHint),
          ),
        ]),
      ),
    );
  }

  Widget _buildNotebookRow(_NotebookRow row) {
    final isSelected = row.notebook.id == widget.currentNotebookId;
    final dotColor = AppColorUtils.parseHex(row.notebook.color);
    return InkWell(
      onTap: () => Navigator.pop(context, row.notebook.id),
      child: Container(
        color: isSelected ? AppColors.primaryLight : null,
        padding: const EdgeInsets.fromLTRB(40, 10, 16, 10),
        child: Row(children: [
          Icon(Icons.menu_book, size: 18,
            color: dotColor ?? AppColors.textHint,
          ),
          const SizedBox(width: 8),
          Expanded(child: Text(row.notebook.name, style: TextStyle(
            fontSize: AppDimens.textBody,
            color: isSelected ? AppColors.primary : AppColors.textPrimary,
          ))),
        ]),
      ),
    );
  }

  Widget _buildCreateRow(_CreateRow row) {
    return InkWell(
      onTap: () async {
        final result = await showNewNotebookSheet(context);
        if (result != null) {
          final newId = await widget.notebookRepo.insert(
            row.folderId, result.name, result.color,
          );
          if (mounted) Navigator.pop(context, newId);
        }
      },
      child: const Padding(
        padding: EdgeInsets.fromLTRB(40, 10, 16, 10),
        child: Row(children: [
          Icon(Icons.add, size: 18, color: AppColors.primary),
          SizedBox(width: 8),
          Text('新建', style: TextStyle(
            fontSize: AppDimens.textBody, color: AppColors.primary,
          )),
        ]),
      ),
    );
  }
}

sealed class _PickerRow {
  const _PickerRow();
}

class _FolderRow extends _PickerRow {
  final Folder folder;
  final bool expanded;
  const _FolderRow(this.folder, this.expanded);
}

class _NotebookRow extends _PickerRow {
  final Notebook notebook;
  const _NotebookRow(this.notebook);
}

class _CreateRow extends _PickerRow {
  final int folderId;
  const _CreateRow(this.folderId);
}
```

- [ ] **Step 2: 替换 note_editor_page.dart 中的 _showNotebookPicker**

在 `lib/pages/note_editor_page.dart` 中：

文件顶部 import 区域添加：
```dart
import '../widgets/editor/notebook_picker_popup.dart';
import '../providers/repository_providers.dart';
```

替换 `_showNotebookPicker` 方法（约第 457-484 行）为：

```dart
Future<void> _showNotebookPicker(NoteEditorNotifier notifier) async {
  final folderRepo = ref.read(folderRepositoryProvider);
  final notebookRepo = ref.read(notebookRepositoryProvider);
  final state = ref.read(noteEditorProvider(widget.noteId));
  final selected = await showNotebookPickerPopup(
    context,
    folderRepo: folderRepo,
    notebookRepo: notebookRepo,
    currentNotebookId: state.pendingNotebookId,
  );
  if (selected != null) {
    await notifier.setNotebook(selected);
  }
}
```

注意：`repository_providers.dart` 可能已经导入（检查现有 import）。如果已有 `import '../providers/repository_providers.dart';` 则不需重复添加。

- [ ] **Step 3: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze lib/widgets/editor/notebook_picker_popup.dart lib/pages/note_editor_page.dart
```

- [ ] **Step 4: 提交**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/editor/notebook_picker_popup.dart code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat: 编辑器笔记本选择器改为文件夹层级弹窗"
```

---

## 验证方案

每个 Task 完成后运行：

```bash
cd code/HuaweiNoteFlutter && flutter analyze
```

全部完成后整体检查：
- 卸载 APP 重装（获取新 seed 数据）
- 笔记列表：卡片显示标题+时间+摘要，有图片时右侧显示缩略图，有清单时显示图标
- 新建笔记本弹窗：显示 9 个颜色圆点
- 编辑器内点击"未分类▼"：弹出文件夹层级选择器
- 选择笔记本后 MetadataStrip 显示正确名称
