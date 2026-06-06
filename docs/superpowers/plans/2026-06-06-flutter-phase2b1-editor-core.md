# Phase 2B-1 编辑器核心 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android 原生编辑器的核心文本编辑能力迁移到 Flutter，使用 appflowy_editor 实现浏览/编辑双模式、样式面板、保存/加载流程。

**Architecture:** 方案 A — appflowy_editor 的 EditorState 管理文档内容/选区/undo-redo，Riverpod NoteEditorNotifier 管理业务状态（模式切换、保存、metadata）。NoteContent JSON 格式从自定义 blocks 切换为 appflowy Document 包装格式。

**Tech Stack:** Flutter, appflowy_editor, flutter_riverpod, go_router, sqflite, share_plus

**Spec:** `docs/superpowers/specs/2026-06-06-flutter-phase2b1-editor-core.md`

---

## File Structure

### Files to Create

| File | Responsibility |
|------|---------------|
| `lib/pages/note_editor_page.dart` | 编辑器主页面 — Scaffold + Column 布局，浏览/编辑双模式 |
| `lib/providers/note_editor_provider.dart` | NoteEditorNotifier + NoteEditorState + providers |
| `lib/widgets/editor/editor_top_bar.dart` | 顶栏 — 返回、Undo、Redo、完成按钮 |
| `lib/widgets/editor/text_toolbar.dart` | 编辑模式底部 5 按钮工具栏 |
| `lib/widgets/editor/style_picker_sheet.dart` | 样式面板 BottomSheet — 6 行格式化控件 |
| `lib/widgets/editor/browse_bottom_bar.dart` | 浏览模式底部操作栏 — 分享/收藏/删除/更多 |
| `lib/widgets/editor/notebook_indicator.dart` | 笔记本指示器 + PopupMenu 选择器 |
| `lib/widgets/editor/metadata_strip.dart` | 元数据条 — 相对时间 + 分类 |
| `test/models/note_content_test.dart` | 覆写 — 新 appflowy 格式的 NoteContent 测试 |
| `test/utils/date_utils_test.dart` | 扩展 — 添加 timeAgo 测试 |
| `test/providers/note_editor_provider_test.dart` | NoteEditorNotifier 单元测试 |

### Files to Modify

| File | Changes |
|------|---------|
| `pubspec.yaml` | 添加 appflowy_editor、share_plus 依赖 |
| `lib/models/note_content.dart` | 重写：documentJson + handwriting 替代 blocks |
| `lib/models/block.dart` | 精简：仅保留 Heading、NoteAlignment、ListType 枚举 |
| `lib/models/note.dart` | 无需改动（content 字段类型不变，NoteContent 内部变了） |
| `lib/router.dart` | 新增 `/editor/:noteId` 路由 |
| `lib/pages/note_list_page.dart` | FAB 和 NoteCard.onTap 改为导航到编辑器 |
| `lib/utils/date_utils.dart` | 新增 `timeAgo()` 方法 |
| `lib/theme.dart` | 新增编辑器相关颜色常量 |
| `lib/repositories/note_repository.dart` | 无需改动（save 通过 NoteContent.toJson 序列化，格式变化对 repo 透明） |

### Files to Remove

| File | Reason |
|------|--------|
| `lib/models/text_span.dart` | appflowy_editor 用 Delta attributes，不再需要 |
| `test/models/text_span_test.dart` | 对应测试也移除 |
| `test/models/block_test.dart` | Block sealed class 已移除，仅保留枚举不需要复杂测试 |

---

### Task 1: 添加依赖

**Files:**
- Modify: `code/HuaweiNoteFlutter/pubspec.yaml`

- [ ] **Step 1: 添加 appflowy_editor 和 share_plus 依赖**

在 `pubspec.yaml` 的 `dependencies` 段追加：

```yaml
  appflowy_editor: ^4.0.0
  share_plus: ^10.1.4
```

> 注：appflowy_editor 版本号需以 `flutter pub add appflowy_editor` 获取的最新稳定版为准，上面仅为参考。如果 4.x 不存在则使用 `^3.0.0` 或实际可用版本。

- [ ] **Step 2: 运行 flutter pub get**

```bash
cd code/HuaweiNoteFlutter && flutter pub get
```

Expected: 依赖安装成功，无冲突。如有版本冲突，调整库版本适配现有工具链（**不要改 Flutter SDK 版本**）。

- [ ] **Step 3: 验证构建**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

Expected: 无新增 error。

- [ ] **Step 4: Commit**

```bash
git add code/HuaweiNoteFlutter/pubspec.yaml code/HuaweiNoteFlutter/pubspec.lock
git commit -m "deps: 添加 appflowy_editor 和 share_plus 依赖"
```

---

### Task 2: Model 层重构 — NoteContent + 精简 block.dart

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/models/note_content.dart`
- Modify: `code/HuaweiNoteFlutter/lib/models/block.dart`
- Remove: `code/HuaweiNoteFlutter/lib/models/text_span.dart`
- Remove: `code/HuaweiNoteFlutter/test/models/text_span_test.dart`
- Remove: `code/HuaweiNoteFlutter/test/models/block_test.dart`
- Rewrite: `code/HuaweiNoteFlutter/test/models/note_content_test.dart`

- [ ] **Step 1: 精简 block.dart — 仅保留枚举**

将 `lib/models/block.dart` 整个内容替换为：

```dart
enum Heading {
  h1, h2, h3, h4, h5, h6;

  static Heading? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }

  int get level => index + 1;
}

enum NoteAlignment {
  start, center, end;

  static NoteAlignment? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }

  String get appFlowyValue => switch (this) {
    NoteAlignment.start => 'left',
    NoteAlignment.center => 'center',
    NoteAlignment.end => 'right',
  };
}

enum ListType {
  bullet, hollowBullet, numbered, lettered;

  static ListType? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
}
```

- [ ] **Step 2: 重写 note_content.dart**

将 `lib/models/note_content.dart` 整个内容替换为：

```dart
import 'dart:convert';
import 'stroke.dart';

class NoteContent {
  final Map<String, dynamic> documentJson;
  final List<Stroke> handwriting;

  const NoteContent({required this.documentJson, required this.handwriting});

  factory NoteContent.empty() => NoteContent(
    documentJson: _emptyDocument(),
    handwriting: const [],
  );

  static Map<String, dynamic> _emptyDocument() => {
    'document': {
      'type': 'page',
      'children': [
        {
          'type': 'paragraph',
          'data': {'delta': []},
        },
      ],
    },
  };

  String toPlainText() {
    final buf = StringBuffer();
    final doc = documentJson['document'] as Map<String, dynamic>?;
    if (doc == null) return '';
    final children = doc['children'] as List<dynamic>? ?? [];
    for (final node in children) {
      _extractText(node as Map<String, dynamic>, buf);
    }
    return buf.toString().trim();
  }

  static void _extractText(Map<String, dynamic> node, StringBuffer buf) {
    final data = node['data'] as Map<String, dynamic>?;
    if (data != null) {
      final delta = data['delta'] as List<dynamic>?;
      if (delta != null) {
        for (final op in delta) {
          final insert = (op as Map<String, dynamic>)['insert'];
          if (insert is String) buf.write(insert);
        }
        if (buf.isNotEmpty && !buf.toString().endsWith('\n')) {
          buf.write('\n');
        }
      }
    }
    final children = node['children'] as List<dynamic>?;
    if (children != null) {
      for (final child in children) {
        _extractText(child as Map<String, dynamic>, buf);
      }
    }
  }

  String toJson() => jsonEncode({
    ...documentJson,
    'handwriting': {
      'strokes': handwriting.map((s) => s.toJson()).toList(),
    },
  });

  static NoteContent fromJson(String s) {
    try {
      final root = jsonDecode(s) as Map<String, dynamic>;
      final hw = root.remove('handwriting') as Map<String, dynamic>? ?? {};
      final strokesJson = hw['strokes'] as List<dynamic>? ?? [];
      final strokes = strokesJson
          .map((s) => Stroke.fromJson(s as Map<String, dynamic>))
          .whereType<Stroke>()
          .toList();

      final docJson = root.containsKey('document')
          ? root
          : _emptyDocument();

      return NoteContent(documentJson: docJson, handwriting: strokes);
    } catch (_) {
      return NoteContent.empty();
    }
  }

  bool get isDocumentEmpty {
    final doc = documentJson['document'] as Map<String, dynamic>?;
    if (doc == null) return true;
    final children = doc['children'] as List<dynamic>? ?? [];
    if (children.isEmpty) return true;
    return toPlainText().isEmpty;
  }
}
```

- [ ] **Step 3: 删除 text_span.dart 及相关测试**

```bash
cd code/HuaweiNoteFlutter
rm lib/models/text_span.dart
rm test/models/text_span_test.dart
rm test/models/block_test.dart
```

- [ ] **Step 4: 修复编译错误 — 移除对 text_span.dart 的 import**

检查 `block.dart` 已不再 import `text_span.dart`（Step 1 的重写已处理）。检查项目中是否还有其他文件 import `text_span.dart` 或使用 `Block` sealed class / `TextBlock` / `ImageBlock` / `ChecklistBlock` / `AudioBlock`：

```bash
cd code/HuaweiNoteFlutter && grep -rn "text_span.dart\|TextBlock\|ImageBlock\|ChecklistBlock\|AudioBlock\|NoteTextSpan\|SpanType" lib/ test/ --include="*.dart"
```

对每个引用处进行修复（通常是测试文件或 note_content.dart 中已在 Step 2 处理的引用）。

- [ ] **Step 5: 重写 note_content_test.dart**

将 `test/models/note_content_test.dart` 整个内容替换为：

```dart
import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/note_content.dart';
import 'package:hwnote/models/stroke.dart';

void main() {
  group('NoteContent', () {
    test('empty() creates content with empty document', () {
      final c = NoteContent.empty();
      expect(c.documentJson, containsPair('document', isA<Map>()));
      expect(c.handwriting, isEmpty);
      expect(c.isDocumentEmpty, isTrue);
    });

    test('toPlainText extracts text from appflowy document nodes', () {
      final c = NoteContent(
        documentJson: {
          'document': {
            'type': 'page',
            'children': [
              {
                'type': 'heading',
                'data': {
                  'level': 1,
                  'delta': [{'insert': 'Title'}],
                },
              },
              {
                'type': 'paragraph',
                'data': {
                  'delta': [
                    {'insert': 'Hello '},
                    {'insert': 'world', 'attributes': {'bold': true}},
                  ],
                },
              },
              {
                'type': 'todo_list',
                'data': {
                  'checked': false,
                  'delta': [{'insert': 'Buy milk'}],
                },
              },
            ],
          },
        },
        handwriting: [],
      );
      expect(c.toPlainText(), 'Title\nHello world\nBuy milk');
    });

    test('toPlainText returns empty for empty document', () {
      final c = NoteContent.empty();
      expect(c.toPlainText(), isEmpty);
    });

    test('toJson and fromJson roundtrip', () {
      final original = NoteContent(
        documentJson: {
          'document': {
            'type': 'page',
            'children': [
              {
                'type': 'paragraph',
                'data': {
                  'delta': [{'insert': 'Test content'}],
                },
              },
            ],
          },
        },
        handwriting: [
          Stroke(brush: BrushType.pen, color: '#000', width: 2, points: [
            StrokePoint(x: 1, y: 2, t: 0),
          ]),
        ],
      );
      final json = original.toJson();
      final restored = NoteContent.fromJson(json);

      expect(restored.toPlainText(), 'Test content');
      expect(restored.handwriting.length, 1);
      expect(restored.handwriting[0].color, '#000');
    });

    test('fromJson with invalid data returns empty', () {
      final c = NoteContent.fromJson('not json');
      expect(c.isDocumentEmpty, isTrue);
      expect(c.handwriting, isEmpty);
    });

    test('fromJson preserves handwriting separate from document', () {
      final json = jsonEncode({
        'document': {
          'type': 'page',
          'children': [
            {'type': 'paragraph', 'data': {'delta': [{'insert': 'Hi'}]}},
          ],
        },
        'handwriting': {
          'strokes': [
            {'brush': 'pen', 'color': '#212121', 'width': 3, 'points': [
              {'x': 10.0, 'y': 20.0, 't': 100},
            ]},
          ],
        },
      });
      final c = NoteContent.fromJson(json);
      expect(c.toPlainText(), 'Hi');
      expect(c.handwriting.length, 1);
      expect(c.documentJson.containsKey('handwriting'), isFalse);
    });

    test('isDocumentEmpty returns false for non-empty content', () {
      final c = NoteContent(
        documentJson: {
          'document': {
            'type': 'page',
            'children': [
              {'type': 'paragraph', 'data': {'delta': [{'insert': 'Hi'}]}},
            ],
          },
        },
        handwriting: [],
      );
      expect(c.isDocumentEmpty, isFalse);
    });
  });
}
```

- [ ] **Step 6: 运行测试**

```bash
cd code/HuaweiNoteFlutter && flutter test test/models/note_content_test.dart -r expanded
```

Expected: 全部 PASS。

- [ ] **Step 7: 运行全部测试确认无回归**

```bash
cd code/HuaweiNoteFlutter && flutter test
```

Expected: 全部 PASS（已删除的 block_test.dart 和 text_span_test.dart 不会运行）。如有其他测试引用了 `TextBlock`、`NoteTextSpan` 等已删除的类型，需修复。

- [ ] **Step 8: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/models/block.dart code/HuaweiNoteFlutter/lib/models/note_content.dart code/HuaweiNoteFlutter/test/models/note_content_test.dart
git rm code/HuaweiNoteFlutter/lib/models/text_span.dart code/HuaweiNoteFlutter/test/models/text_span_test.dart code/HuaweiNoteFlutter/test/models/block_test.dart
git commit -m "refactor: NoteContent 切换到 appflowy Document 包装格式"
```

---

### Task 3: 添加 timeAgo 工具方法

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/utils/date_utils.dart`
- Modify: `code/HuaweiNoteFlutter/test/utils/date_utils_test.dart`

编辑器的 MetadataStrip 使用与列表页不同的时间格式。列表页用 `formatRelative`（显示 HH:mm / 昨天 HH:mm），编辑器用 `timeAgo`（显示"刚刚"/"X分钟前"等）。

- [ ] **Step 1: 写 timeAgo 测试**

在 `test/utils/date_utils_test.dart` 文件末尾、最后一个 `});` 之前添加新的 group：

```dart
  group('AppDateUtils.timeAgo', () {
    test('less than 1 minute returns 刚刚', () {
      final now = 1000000;
      expect(AppDateUtils.timeAgo(now - 30000, now: now), '刚刚');
      expect(AppDateUtils.timeAgo(now, now: now), '刚刚');
    });

    test('less than 1 hour returns X分钟前', () {
      final now = 10000000;
      expect(AppDateUtils.timeAgo(now - 60000, now: now), '1分钟前');
      expect(AppDateUtils.timeAgo(now - 5 * 60000, now: now), '5分钟前');
      expect(AppDateUtils.timeAgo(now - 59 * 60000, now: now), '59分钟前');
    });

    test('less than 24 hours returns X小时前', () {
      final now = 100000000;
      expect(AppDateUtils.timeAgo(now - 3600000, now: now), '1小时前');
      expect(AppDateUtils.timeAgo(now - 12 * 3600000, now: now), '12小时前');
    });

    test('less than 2 days returns 昨天', () {
      final now = 200000000;
      expect(AppDateUtils.timeAgo(now - 25 * 3600000, now: now), '昨天');
      expect(AppDateUtils.timeAgo(now - 47 * 3600000, now: now), '昨天');
    });

    test('less than 7 days returns X天前', () {
      final now = 1000000000;
      final day = 24 * 3600000;
      expect(AppDateUtils.timeAgo(now - 2 * day, now: now), '2天前');
      expect(AppDateUtils.timeAgo(now - 6 * day, now: now), '6天前');
    });

    test('7 days or more returns MM/dd', () {
      // 2026-03-15 12:00:00 UTC
      final now = DateTime.utc(2026, 3, 15, 12).millisecondsSinceEpoch;
      // 2026-03-01 12:00:00 UTC
      final target = DateTime.utc(2026, 3, 1, 12).millisecondsSinceEpoch;
      expect(AppDateUtils.timeAgo(target, now: now), '03/01');
    });
  });
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd code/HuaweiNoteFlutter && flutter test test/utils/date_utils_test.dart -r expanded
```

Expected: FAIL — `timeAgo` method not found。

- [ ] **Step 3: 实现 timeAgo 方法**

在 `lib/utils/date_utils.dart` 的 `AppDateUtils` 类中添加：

```dart
  static String timeAgo(int timeMs, {int? now}) {
    final nowMs = now ?? DateTime.now().millisecondsSinceEpoch;
    final diff = nowMs - timeMs;

    if (diff < 60 * 1000) return '刚刚';
    if (diff < 3600 * 1000) return '${diff ~/ (60 * 1000)}分钟前';
    if (diff < 24 * 3600 * 1000) return '${diff ~/ (3600 * 1000)}小时前';
    if (diff < 2 * 24 * 3600 * 1000) return '昨天';
    if (diff < 7 * 24 * 3600 * 1000) return '${diff ~/ (24 * 3600 * 1000)}天前';

    final target = DateTime.fromMillisecondsSinceEpoch(timeMs);
    return '${_pad2(target.month)}/${_pad2(target.day)}';
  }
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd code/HuaweiNoteFlutter && flutter test test/utils/date_utils_test.dart -r expanded
```

Expected: 全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/utils/date_utils.dart code/HuaweiNoteFlutter/test/utils/date_utils_test.dart
git commit -m "feat: 添加 timeAgo 工具方法（编辑器 MetadataStrip 用）"
```

---

### Task 4: NoteEditorNotifier 状态管理

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/providers/note_editor_provider.dart`
- Create: `code/HuaweiNoteFlutter/test/providers/note_editor_provider_test.dart`

- [ ] **Step 1: 写 NoteEditorNotifier 测试**

创建 `test/providers/note_editor_provider_test.dart`：

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/providers/note_editor_provider.dart';

void main() {
  group('NoteEditorState', () {
    test('initial state is browse mode', () {
      final s = NoteEditorState.initial(noteId: 1);
      expect(s.noteId, 1);
      expect(s.isEditing, isFalse);
      expect(s.isSaving, isFalse);
      expect(s.title, isEmpty);
      expect(s.pendingNotebookId, isNull);
      expect(s.pendingBackground, 'plain');
    });

    test('new note starts in edit mode', () {
      final s = NoteEditorState.initial(noteId: 0);
      expect(s.noteId, 0);
      expect(s.isEditing, isTrue);
    });

    test('copyWith preserves unchanged fields', () {
      final s = NoteEditorState.initial(noteId: 1);
      final s2 = s.copyWith(isEditing: true, title: 'Hello');
      expect(s2.noteId, 1);
      expect(s2.isEditing, isTrue);
      expect(s2.title, 'Hello');
      expect(s2.isSaving, isFalse);
    });

    test('isNoteEmpty with empty title and no content', () {
      final s = NoteEditorState.initial(noteId: 0);
      expect(s.isNoteEmpty, isTrue);
    });

    test('isNoteEmpty with title is false', () {
      final s = NoteEditorState.initial(noteId: 0).copyWith(title: 'Hi');
      expect(s.isNoteEmpty, isFalse);
    });
  });
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd code/HuaweiNoteFlutter && flutter test test/providers/note_editor_provider_test.dart -r expanded
```

Expected: FAIL — provider file doesn't exist。

- [ ] **Step 3: 实现 NoteEditorState 和 NoteEditorNotifier**

创建 `lib/providers/note_editor_provider.dart`：

```dart
import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/note.dart';
import '../models/note_content.dart';
import '../repositories/note_repository.dart';
import '../repositories/notebook_repository.dart';
import '../repositories/category_repository.dart';
import 'repository_providers.dart';

class NoteEditorState {
  final int noteId;
  final bool isEditing;
  final bool isSaving;
  final String title;
  final Note? loadedNote;
  final int? pendingNotebookId;
  final String pendingBackground;
  final bool documentHasContent;

  const NoteEditorState({
    required this.noteId,
    this.isEditing = false,
    this.isSaving = false,
    this.title = '',
    this.loadedNote,
    this.pendingNotebookId,
    this.pendingBackground = 'plain',
    this.documentHasContent = false,
  });

  factory NoteEditorState.initial({required int noteId}) => NoteEditorState(
    noteId: noteId,
    isEditing: noteId == 0,
  );

  bool get isNoteEmpty => title.trim().isEmpty && !documentHasContent;

  NoteEditorState copyWith({
    int? noteId,
    bool? isEditing,
    bool? isSaving,
    String? title,
    Note? loadedNote,
    int? pendingNotebookId,
    bool clearPendingNotebookId = false,
    String? pendingBackground,
    bool? documentHasContent,
  }) => NoteEditorState(
    noteId: noteId ?? this.noteId,
    isEditing: isEditing ?? this.isEditing,
    isSaving: isSaving ?? this.isSaving,
    title: title ?? this.title,
    loadedNote: loadedNote ?? this.loadedNote,
    pendingNotebookId: clearPendingNotebookId ? null : (pendingNotebookId ?? this.pendingNotebookId),
    pendingBackground: pendingBackground ?? this.pendingBackground,
    documentHasContent: documentHasContent ?? this.documentHasContent,
  );
}

class NoteEditorNotifier extends StateNotifier<NoteEditorState> {
  final NoteRepository _noteRepo;
  final NotebookRepository _notebookRepo;
  final CategoryRepository _categoryRepo;
  EditorState? _editorState;

  NoteEditorNotifier(this._noteRepo, this._notebookRepo, this._categoryRepo, int noteId)
      : super(NoteEditorState.initial(noteId: noteId));

  EditorState? get editorState => _editorState;

  Future<void> loadNote() async {
    if (state.noteId == 0) {
      final doc = Document.blank();
      _editorState = EditorState(document: doc);
      return;
    }
    final note = await _noteRepo.get(state.noteId);
    if (note == null) return;
    final content = note.content;
    final docJson = content.documentJson['document'] as Map<String, dynamic>?;
    final doc = docJson != null
        ? Document.fromJson(docJson)
        : Document.blank();
    _editorState = EditorState(document: doc);
    state = state.copyWith(
      title: note.title,
      loadedNote: note,
      pendingNotebookId: note.notebookId,
      pendingBackground: note.background,
      documentHasContent: content.toPlainText().isNotEmpty,
    );
  }

  void enterEditMode() {
    state = state.copyWith(isEditing: true);
  }

  void exitEditMode() {
    state = state.copyWith(isEditing: false);
  }

  void updateTitle(String title) {
    state = state.copyWith(title: title);
  }

  void setNotebook(int? notebookId) {
    state = state.copyWith(
      pendingNotebookId: notebookId,
      clearPendingNotebookId: notebookId == null,
    );
  }

  void setBackground(String background) {
    state = state.copyWith(pendingBackground: background);
  }

  void updateDocumentHasContent(bool hasContent) {
    state = state.copyWith(documentHasContent: hasContent);
  }

  Future<void> toggleFavorite() async {
    final note = state.loadedNote;
    if (note == null) return;
    await _noteRepo.setFavorite(note.id, !note.isFavorite);
    final updated = note.copyWith(isFavorite: !note.isFavorite);
    state = state.copyWith(loadedNote: updated);
  }

  Future<bool> saveNote() async {
    if (state.isSaving) return false;
    if (state.noteId == 0 && state.isNoteEmpty) return false;

    state = state.copyWith(isSaving: true);
    try {
      final editorDoc = _editorState?.document;
      final docJson = editorDoc?.toJson() ?? {};
      final content = NoteContent(
        documentJson: {'document': docJson},
        handwriting: state.loadedNote?.content.handwriting ?? [],
      );
      final now = DateTime.now().millisecondsSinceEpoch;
      final note = (state.loadedNote ?? Note.newNote(now: now)).copyWith(
        title: state.title,
        plainText: content.toPlainText(),
        content: content,
        notebookId: state.pendingNotebookId,
        background: state.pendingBackground,
        updatedAt: now,
      );
      final id = await _noteRepo.save(note);
      if (state.noteId == 0) {
        state = state.copyWith(
          noteId: id,
          loadedNote: note.copyWith(id: id),
          isSaving: false,
        );
      } else {
        state = state.copyWith(
          loadedNote: note,
          isSaving: false,
        );
      }
      return true;
    } catch (_) {
      state = state.copyWith(isSaving: false);
      return false;
    }
  }

  Future<void> softDelete() async {
    if (state.noteId == 0) return;
    await _noteRepo.softDelete(state.noteId);
  }

  Future<List<({int id, String name, String color})>> loadNotebooks() async {
    final folders = await _notebookRepo.listByFolder(1);
    return folders.map((nb) => (id: nb.id, name: nb.name, color: nb.color)).toList();
  }

  Future<List<({int id, String name, String color})>> loadCategories() async {
    final cats = await _categoryRepo.list();
    return cats.map((c) => (id: c.id, name: c.name, color: c.color)).toList();
  }

  Future<void> setCategoryId(int? categoryId) async {
    if (state.noteId == 0) return;
    final note = state.loadedNote;
    if (note == null) return;
    final updated = note.copyWith(
      categoryId: categoryId,
      setCategoryIdNull: categoryId == null,
    );
    state = state.copyWith(loadedNote: updated);
  }
}

final noteEditorProvider = StateNotifierProvider.autoDispose
    .family<NoteEditorNotifier, NoteEditorState, int>((ref, noteId) {
  return NoteEditorNotifier(
    ref.watch(noteRepositoryProvider),
    ref.watch(notebookRepositoryProvider),
    ref.watch(categoryRepositoryProvider),
    noteId,
  );
});
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd code/HuaweiNoteFlutter && flutter test test/providers/note_editor_provider_test.dart -r expanded
```

Expected: 全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/providers/note_editor_provider.dart code/HuaweiNoteFlutter/test/providers/note_editor_provider_test.dart
git commit -m "feat: NoteEditorNotifier 状态管理（加载/保存/模式切换）"
```

---

### Task 5: 编辑器页面骨架 + 路由集成

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`
- Modify: `code/HuaweiNoteFlutter/lib/router.dart`
- Modify: `code/HuaweiNoteFlutter/lib/theme.dart`

- [ ] **Step 1: 添加编辑器相关颜色常量到 theme.dart**

在 `lib/theme.dart` 的 `AppColors` 类中追加：

```dart
  static const editorToolbarBg = Color(0xFFFFFFFF);
  static const editorIconActive = Color(0xFF333333);
  static const editorIconInactive = Color(0xFF999999);
  static const styleSheetHeader = Color(0xFF333333);
```

在 `AppDimens` 类中追加：

```dart
  static const editorTitleSize = 22.0;
  static const editorToolbarHeight = 48.0;
  static const editorContentPadding = 16.0;
```

- [ ] **Step 2: 创建编辑器页面骨架**

创建 `lib/pages/note_editor_page.dart`：

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/note_editor_provider.dart';
import '../theme.dart';

class NoteEditorPage extends ConsumerStatefulWidget {
  final int noteId;
  const NoteEditorPage({super.key, required this.noteId});

  @override
  ConsumerState<NoteEditorPage> createState() => _NoteEditorPageState();
}

class _NoteEditorPageState extends ConsumerState<NoteEditorPage> {
  late final TextEditingController _titleController;

  @override
  void initState() {
    super.initState();
    _titleController = TextEditingController();
    Future.microtask(() async {
      final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
      await notifier.loadNote();
      final state = ref.read(noteEditorProvider(widget.noteId));
      _titleController.text = state.title;
    });
  }

  @override
  void dispose() {
    _titleController.dispose();
    super.dispose();
  }

  Future<void> _onBack() async {
    final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
    final state = ref.read(noteEditorProvider(widget.noteId));
    if (state.isEditing) {
      await notifier.saveNote();
    }
    if (mounted) context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(noteEditorProvider(widget.noteId));

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) async {
        if (!didPop) await _onBack();
      },
      child: Scaffold(
        backgroundColor: Colors.white,
        body: SafeArea(
          child: Column(
            children: [
              // Placeholder: EditorTopBar (Task 6)
              _buildTempTopBar(state),
              // Placeholder: NotebookIndicator (Task 7)
              // Placeholder: TitleInput (Task 8)
              // Placeholder: MetadataStrip (Task 8)
              Expanded(
                child: GestureDetector(
                  onTap: () {
                    if (!state.isEditing) {
                      ref.read(noteEditorProvider(widget.noteId).notifier).enterEditMode();
                    }
                  },
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(AppDimens.editorContentPadding),
                    child: const Text('编辑器内容区域 (Task 9 集成 appflowy_editor)'),
                  ),
                ),
              ),
              // Placeholder bottom bars
              if (state.isEditing)
                Container(
                  height: AppDimens.editorToolbarHeight,
                  color: AppColors.editorToolbarBg,
                  child: const Center(child: Text('TextToolbar (Task 10)')),
                )
              else
                Container(
                  height: AppDimens.editorToolbarHeight,
                  color: AppColors.editorToolbarBg,
                  child: const Center(child: Text('BrowseBottomBar (Task 12)')),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTempTopBar(NoteEditorState state) {
    return Container(
      height: 48,
      padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingS),
      child: Row(
        children: [
          IconButton(icon: const Icon(Icons.arrow_back), onPressed: _onBack),
          const Spacer(),
          if (state.isEditing)
            IconButton(
              icon: const Icon(Icons.check, color: AppColors.primary),
              onPressed: () async {
                final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
                await notifier.saveNote();
                notifier.exitEditMode();
              },
            ),
        ],
      ),
    );
  }
}
```

- [ ] **Step 3: 添加路由到 router.dart**

在 `lib/router.dart` 中：

顶部添加 import：

```dart
import 'pages/note_editor_page.dart';
```

在 `GoRouter` 的 `routes:` 列表中，在 `StatefulShellRoute.indexedStack(...)` 之后添加新的 GoRoute：

```dart
      GoRoute(
        path: '/editor/:noteId',
        builder: (context, state) {
          final noteId = int.tryParse(state.pathParameters['noteId'] ?? '0') ?? 0;
          return NoteEditorPage(noteId: noteId);
        },
      ),
```

- [ ] **Step 4: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

Expected: 无 error。

- [ ] **Step 5: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart code/HuaweiNoteFlutter/lib/router.dart code/HuaweiNoteFlutter/lib/theme.dart
git commit -m "feat: 编辑器页面骨架 + /editor/:noteId 路由"
```

---

### Task 6: EditorTopBar 组件

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/widgets/editor/editor_top_bar.dart`
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`

- [ ] **Step 1: 创建 EditorTopBar**

创建 `lib/widgets/editor/editor_top_bar.dart`：

```dart
import 'package:flutter/material.dart';
import '../../theme.dart';

class EditorTopBar extends StatelessWidget {
  final bool isEditing;
  final bool canUndo;
  final bool canRedo;
  final VoidCallback onBack;
  final VoidCallback onUndo;
  final VoidCallback onRedo;
  final VoidCallback onDone;

  const EditorTopBar({
    super.key,
    required this.isEditing,
    required this.canUndo,
    required this.canRedo,
    required this.onBack,
    required this.onUndo,
    required this.onRedo,
    required this.onDone,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 48,
      padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingXs),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: AppColors.divider, width: 0.5)),
      ),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back, color: AppColors.textPrimary),
            onPressed: onBack,
          ),
          const Spacer(),
          if (isEditing) ...[
            IconButton(
              icon: Icon(Icons.undo,
                color: canUndo ? AppColors.textSecondary : AppColors.textHint),
              onPressed: canUndo ? onUndo : null,
            ),
            IconButton(
              icon: Icon(Icons.redo,
                color: canRedo ? AppColors.textSecondary : AppColors.textHint),
              onPressed: canRedo ? onRedo : null,
            ),
            IconButton(
              icon: const Icon(Icons.check, color: AppColors.primary),
              onPressed: onDone,
            ),
          ],
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: 在 NoteEditorPage 中使用 EditorTopBar 替换临时 top bar**

在 `note_editor_page.dart` 中：
- 添加 import: `import '../widgets/editor/editor_top_bar.dart';`
- 将 `_buildTempTopBar(state)` 调用替换为 `EditorTopBar` widget：

```dart
              EditorTopBar(
                isEditing: state.isEditing,
                canUndo: false, // Task 9 接入 appflowy_editor 后更新
                canRedo: false,
                onBack: _onBack,
                onUndo: () {},
                onRedo: () {},
                onDone: () async {
                  final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
                  await notifier.saveNote();
                  notifier.exitEditMode();
                  FocusScope.of(context).unfocus();
                },
              ),
```

- 删除 `_buildTempTopBar` 方法。

- [ ] **Step 3: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

Expected: 无 error。

- [ ] **Step 4: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/editor/editor_top_bar.dart code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat: EditorTopBar 组件（返回/撤销/重做/完成）"
```

---

### Task 7: NotebookIndicator 组件

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/widgets/editor/notebook_indicator.dart`
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`

- [ ] **Step 1: 创建 NotebookIndicator**

创建 `lib/widgets/editor/notebook_indicator.dart`：

```dart
import 'package:flutter/material.dart';
import '../../theme.dart';
import '../../utils/color_utils.dart';

class NotebookIndicator extends StatelessWidget {
  final String? notebookName;
  final String? notebookColor;
  final bool isEditing;
  final Future<List<({int id, String name, String color})>> Function() onLoadNotebooks;
  final ValueChanged<int?> onNotebookSelected;

  const NotebookIndicator({
    super.key,
    this.notebookName,
    this.notebookColor,
    required this.isEditing,
    required this.onLoadNotebooks,
    required this.onNotebookSelected,
  });

  @override
  Widget build(BuildContext context) {
    final name = notebookName ?? '未归类';
    final color = notebookColor != null
        ? (AppColorUtils.parseHex(notebookColor!) ?? AppColors.textHint)
        : AppColors.textHint;

    return GestureDetector(
      onTap: isEditing ? () => _showPicker(context) : null,
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: AppDimens.editorContentPadding,
          vertical: AppDimens.spacingS,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(shape: BoxShape.circle, color: color),
            ),
            const SizedBox(width: AppDimens.spacingS),
            Text(name, style: const TextStyle(
              fontSize: AppDimens.textCaption,
              color: AppColors.textSecondary,
            )),
            if (isEditing) ...[
              const SizedBox(width: 4),
              const Icon(Icons.arrow_drop_down, size: 16, color: AppColors.textHint),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _showPicker(BuildContext context) async {
    final notebooks = await onLoadNotebooks();
    if (!context.mounted || notebooks.isEmpty) return;

    final rBox = context.findRenderObject() as RenderBox;
    final overlay = Overlay.of(context).context.findRenderObject() as RenderBox;
    final position = RelativeRect.fromRect(
      Rect.fromPoints(
        rBox.localToGlobal(Offset.zero, ancestor: overlay),
        rBox.localToGlobal(rBox.size.bottomRight(Offset.zero), ancestor: overlay),
      ),
      Offset.zero & overlay.size,
    );

    final selected = await showMenu<int?>(
      context: context,
      position: position,
      items: notebooks.map((nb) {
        final c = AppColorUtils.parseHex(nb.color) ?? AppColors.textHint;
        return PopupMenuItem<int?>(
          value: nb.id,
          child: Row(children: [
            Container(width: 8, height: 8,
              decoration: BoxDecoration(shape: BoxShape.circle, color: c)),
            const SizedBox(width: AppDimens.spacingS),
            Text(nb.name),
          ]),
        );
      }).toList(),
    );

    if (selected != null) {
      onNotebookSelected(selected);
    }
  }
}
```

- [ ] **Step 2: 在 NoteEditorPage 中插入 NotebookIndicator**

在 `note_editor_page.dart` 中：
- 添加 import: `import '../widgets/editor/notebook_indicator.dart';`
- 在 `EditorTopBar` 之后、`Expanded` 之前插入：

```dart
              NotebookIndicator(
                notebookName: _getNotebookName(state),
                notebookColor: _getNotebookColor(state),
                isEditing: state.isEditing,
                onLoadNotebooks: () => ref.read(noteEditorProvider(widget.noteId).notifier).loadNotebooks(),
                onNotebookSelected: (id) => ref.read(noteEditorProvider(widget.noteId).notifier).setNotebook(id),
              ),
```

添加辅助方法：

```dart
  String? _getNotebookName(NoteEditorState state) {
    // 会在后续 task 中优化为从 notebookRepo 读取
    return state.loadedNote?.notebookId != null ? '笔记本' : null;
  }

  String? _getNotebookColor(NoteEditorState state) {
    return null; // 会在后续 task 中从 notebookColorMap 获取
  }
```

- [ ] **Step 3: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

Expected: 无 error。

- [ ] **Step 4: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/editor/notebook_indicator.dart code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat: NotebookIndicator 组件 + PopupMenu 笔记本选择"
```

---

### Task 8: TitleInput + MetadataStrip 组件

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/widgets/editor/metadata_strip.dart`
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`

- [ ] **Step 1: 创建 MetadataStrip**

创建 `lib/widgets/editor/metadata_strip.dart`：

```dart
import 'package:flutter/material.dart';
import '../../theme.dart';
import '../../utils/date_utils.dart';

class MetadataStrip extends StatelessWidget {
  final int updatedAt;
  final String? categoryName;
  final VoidCallback? onCategoryTap;

  const MetadataStrip({
    super.key,
    required this.updatedAt,
    this.categoryName,
    this.onCategoryTap,
  });

  @override
  Widget build(BuildContext context) {
    final timeText = updatedAt > 0 ? AppDateUtils.timeAgo(updatedAt) : '刚刚';
    final catText = categoryName ?? '未分类';

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppDimens.editorContentPadding,
        vertical: AppDimens.spacingXs,
      ),
      child: Row(
        children: [
          Text(timeText, style: const TextStyle(
            fontSize: AppDimens.textCaption, color: AppColors.textHint,
          )),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 6),
            child: Text('·', style: TextStyle(color: AppColors.textHint)),
          ),
          Container(
            width: 6, height: 6,
            decoration: const BoxDecoration(
              shape: BoxShape.circle, color: AppColors.textHint,
            ),
          ),
          const SizedBox(width: 4),
          GestureDetector(
            onTap: onCategoryTap,
            child: Text(catText, style: const TextStyle(
              fontSize: AppDimens.textCaption, color: AppColors.textHint,
            )),
          ),
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: 在 NoteEditorPage 中添加 TitleInput 和 MetadataStrip**

在 `note_editor_page.dart` 中：
- 添加 import: `import '../widgets/editor/metadata_strip.dart';`
- 在 `NotebookIndicator` 之后、`Expanded` 之前插入：

```dart
              // Title input
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: AppDimens.editorContentPadding),
                child: TextField(
                  controller: _titleController,
                  enabled: state.isEditing,
                  onChanged: (v) => ref.read(noteEditorProvider(widget.noteId).notifier).updateTitle(v),
                  style: const TextStyle(
                    fontSize: AppDimens.editorTitleSize,
                    fontWeight: FontWeight.w600,
                    color: AppColors.textPrimary,
                  ),
                  decoration: const InputDecoration(
                    hintText: '标题',
                    hintStyle: TextStyle(
                      fontSize: AppDimens.editorTitleSize,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textHint,
                    ),
                    border: InputBorder.none,
                    contentPadding: EdgeInsets.symmetric(vertical: 4),
                  ),
                ),
              ),
              // Metadata strip
              MetadataStrip(
                updatedAt: state.loadedNote?.updatedAt ?? 0,
                categoryName: null, // 后续 task 从 categoryRepo 加载
                onCategoryTap: state.isEditing ? () => _showCategoryPicker() : null,
              ),
```

添加分类选择器方法：

```dart
  Future<void> _showCategoryPicker() async {
    final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
    final categories = await notifier.loadCategories();
    if (!mounted) return;
    final currentCatId = ref.read(noteEditorProvider(widget.noteId)).loadedNote?.categoryId;

    final selected = await showModalBottomSheet<int?>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Text('选择分类', style: TextStyle(
                fontSize: 16, fontWeight: FontWeight.w600,
              )),
            ),
            ListTile(
              leading: const Icon(Icons.circle, size: 10, color: AppColors.textHint),
              title: const Text('未分类'),
              trailing: currentCatId == null ? const Icon(Icons.check, color: AppColors.primary) : null,
              onTap: () => Navigator.pop(ctx, -1),
            ),
            ...categories.map((c) {
              final color = AppColorUtils.parseHex(c.color) ?? AppColors.textHint;
              return ListTile(
                leading: Icon(Icons.circle, size: 10, color: color),
                title: Text(c.name),
                trailing: currentCatId == c.id ? const Icon(Icons.check, color: AppColors.primary) : null,
                onTap: () => Navigator.pop(ctx, c.id),
              );
            }),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );

    if (selected != null && mounted) {
      await notifier.setCategoryId(selected == -1 ? null : selected);
    }
  }
```

添加 import: `import '../utils/color_utils.dart';`

- [ ] **Step 3: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

Expected: 无 error。

- [ ] **Step 4: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/editor/metadata_strip.dart code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat: TitleInput + MetadataStrip 组件"
```

---

### Task 9: appflowy_editor 集成

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`
- Modify: `code/HuaweiNoteFlutter/lib/widgets/editor/editor_top_bar.dart`

这是核心任务：将 appflowy_editor 的 `AppFlowyEditor` widget 嵌入页面，连接 `EditorState`，实现 undo/redo 联动。

> **重要：** appflowy_editor 的具体 API 可能与下面代码有出入。实施时需要查阅 `appflowy_editor` 包的最新 README 和 API 文档（在 `code/HuaweiNoteFlutter/.dart_tool/` 或 pub.dev 页面），确认 `AppFlowyEditor`、`EditorState`、`Document` 等类的实际构造方式和参数。

- [ ] **Step 1: 在 NoteEditorPage 中集成 AppFlowyEditor**

在 `note_editor_page.dart` 中：
- 添加 import: `import 'package:appflowy_editor/appflowy_editor.dart';`
- 在 `_NoteEditorPageState` 中添加字段：

```dart
  EditorState? _editorState;
  EditorScrollController? _scrollController;
```

- 修改 `initState` 中 `loadNote()` 完成后的逻辑，获取 EditorState：

```dart
    Future.microtask(() async {
      final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
      await notifier.loadNote();
      final state = ref.read(noteEditorProvider(widget.noteId));
      _titleController.text = state.title;
      setState(() {
        _editorState = notifier.editorState;
        if (_editorState != null) {
          _scrollController = EditorScrollController(editorState: _editorState!);
        }
      });
    });
```

- 替换 `Expanded` 中的占位 `Container` 为 `AppFlowyEditor`：

```dart
              Expanded(
                child: GestureDetector(
                  behavior: HitTestBehavior.translucent,
                  onTap: () {
                    if (!state.isEditing) {
                      ref.read(noteEditorProvider(widget.noteId).notifier).enterEditMode();
                    }
                  },
                  child: _editorState != null
                      ? AppFlowyEditor(
                          editorState: _editorState!,
                          editorScrollController: _scrollController!,
                          editable: state.isEditing,
                        )
                      : const SizedBox.shrink(),
                ),
              ),
```

- [ ] **Step 2: 连接 Undo/Redo 到 EditorTopBar**

更新 `EditorTopBar` 的调用：

```dart
              EditorTopBar(
                isEditing: state.isEditing,
                canUndo: _editorState?.undoManager.undoStack.isNotEmpty ?? false,
                canRedo: _editorState?.undoManager.redoStack.isNotEmpty ?? false,
                onBack: _onBack,
                onUndo: () => _editorState?.undoManager.undo(),
                onRedo: () => _editorState?.undoManager.redo(),
                onDone: () async {
                  final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
                  await notifier.saveNote();
                  notifier.exitEditMode();
                  if (mounted) FocusScope.of(context).unfocus();
                },
              ),
```

> **注：** `undoManager.undoStack` 和 `undoManager.redoStack` 的具体属性名需查阅 appflowy_editor API。可能是 `undoManager.canUndo` / `undoManager.canRedo` 布尔值。如果是方法而非属性，需要用 `ValueListenableBuilder` 包裹以监听变化。

- [ ] **Step 3: 监听文档变化以更新 documentHasContent**

在 `initState` 的 editorState 创建后添加监听：

```dart
        _editorState?.transactionStream.listen((_) {
          final plainText = _editorState?.document.toJson();
          // 简单检查是否有内容
          final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
          final hasContent = _editorState?.document.root.children
              .any((node) {
                final delta = node.delta;
                return delta != null && delta.toPlainText().isNotEmpty;
              }) ?? false;
          notifier.updateDocumentHasContent(hasContent);
        });
```

> **注：** `transactionStream` 的实际名称需确认。可能是 `editorState.transactionStream` 或其他监听方式。

- [ ] **Step 4: 在 dispose 中清理**

```dart
  @override
  void dispose() {
    _scrollController?.dispose();
    _titleController.dispose();
    super.dispose();
  }
```

- [ ] **Step 5: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

Expected: 无 error。如有 API 不匹配，根据实际 appflowy_editor API 调整。

- [ ] **Step 6: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat: 集成 appflowy_editor（文本编辑 + undo/redo）"
```

---

### Task 10: TextToolbar 组件

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/widgets/editor/text_toolbar.dart`
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`

- [ ] **Step 1: 创建 TextToolbar**

创建 `lib/widgets/editor/text_toolbar.dart`：

```dart
import 'package:flutter/material.dart';
import '../../theme.dart';

class TextToolbar extends StatelessWidget {
  final VoidCallback onChecklist;
  final VoidCallback onStyle;
  final VoidCallback onImage;
  final VoidCallback onHandwriting;
  final VoidCallback onRecord;

  const TextToolbar({
    super.key,
    required this.onChecklist,
    required this.onStyle,
    required this.onImage,
    required this.onHandwriting,
    required this.onRecord,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: AppDimens.editorToolbarHeight,
      decoration: const BoxDecoration(
        color: AppColors.editorToolbarBg,
        border: Border(top: BorderSide(color: AppColors.divider, width: 0.5)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _ToolbarButton(
            icon: Icons.checklist,
            label: '清单',
            color: AppColors.editorIconActive,
            onTap: onChecklist,
          ),
          _ToolbarButton(
            icon: Icons.text_format,
            label: '样式',
            color: AppColors.primary,
            onTap: onStyle,
          ),
          _ToolbarButton(
            icon: Icons.image_outlined,
            label: '图片',
            color: AppColors.editorIconInactive,
            onTap: onImage,
          ),
          _ToolbarButton(
            icon: Icons.edit_outlined,
            label: '手写',
            color: AppColors.editorIconInactive,
            onTap: onHandwriting,
          ),
          _ToolbarButton(
            icon: Icons.mic_outlined,
            label: '录音',
            color: AppColors.editorIconInactive,
            onTap: onRecord,
          ),
        ],
      ),
    );
  }
}

class _ToolbarButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _ToolbarButton({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 22, color: color),
          const SizedBox(height: 2),
          Text(label, style: TextStyle(fontSize: 10, color: color)),
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: 在 NoteEditorPage 中使用 TextToolbar**

在 `note_editor_page.dart` 中：
- 添加 import: `import '../widgets/editor/text_toolbar.dart';`
- 替换编辑模式的占位 `Container` 为：

```dart
              if (state.isEditing)
                TextToolbar(
                  onChecklist: () => _insertTodoList(),
                  onStyle: () => _showStylePicker(),
                  onImage: () => ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('图片功能将在后续版本实现'))),
                  onHandwriting: () => ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('手写功能将在后续版本实现'))),
                  onRecord: () => ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('录音功能将在后续版本实现'))),
                ),
```

添加占位方法：

```dart
  void _insertTodoList() {
    if (_editorState == null) return;
    final selection = _editorState!.selection;
    if (selection == null) return;
    final transaction = _editorState!.transaction;
    final node = todoListNode(checked: false, delta: Delta()..insert(''));
    transaction.insertNode(selection.end.path.next, node);
    _editorState!.apply(transaction);
  }

  void _showStylePicker() {
    // Task 11 实现
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('样式面板 (Task 11)')),
    );
  }
```

> **注：** `todoListNode` 和 `Delta` 是 appflowy_editor 提供的工厂方法。具体 API 需查阅文档。如果 `todoListNode` 不存在，需用 `Node(type: 'todo_list', attributes: {...})` 手动构造。

- [ ] **Step 3: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

- [ ] **Step 4: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/editor/text_toolbar.dart code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat: TextToolbar 底部工具栏（5按钮，图片/手写/录音占位）"
```

---

### Task 11: StylePickerBottomSheet 样式面板

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/widgets/editor/style_picker_sheet.dart`
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`

这是最复杂的 UI 组件。6 行面板，每行独立功能。对标 Android `StylePickerBottomSheet`。

- [ ] **Step 1: 创建 StylePickerSheet**

创建 `lib/widgets/editor/style_picker_sheet.dart`：

```dart
import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import '../../theme.dart';

class StylePickerSheet extends StatefulWidget {
  final EditorState editorState;
  final String currentBackground;
  final ValueChanged<String> onBackgroundChanged;

  const StylePickerSheet({
    super.key,
    required this.editorState,
    required this.currentBackground,
    required this.onBackgroundChanged,
  });

  @override
  State<StylePickerSheet> createState() => _StylePickerSheetState();
}

class _StylePickerSheetState extends State<StylePickerSheet> {
  static const _fontSizes = [12.0, 14.0, 16.0, 20.0, 24.0];
  static const _fontSizeLabels = ['xs', '小', '中', '大', 'xl'];
  static const _colors = [
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFF43A047),
    Color(0xFF29B6F6),
    Color(0xFF1E88E5),
    Color(0xFFAB47BC),
    Color(0xFF212121),
  ];
  static const _colorHexes = [
    '#E53935', '#FB8C00', '#43A047', '#29B6F6', '#1E88E5', '#AB47BC', '#212121',
  ];
  static const _backgrounds = [
    ('plain', '纯白', Color(0xFFFFFFFF)),
    ('linen', '亚麻', Color(0xFFF5E6D0)),
    ('kraft', '牛皮', Color(0xFFD4B896)),
    ('grid', '网格', Color(0xFFF0F0F0)),
  ];

  double _currentFontSize = 16.0;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildHeader(),
            _buildInlineAndAlign(),
            _buildIndentAndLists(),
            _buildFontSize(),
            _buildColors(),
            _buildHeadings(),
            _buildBackgrounds(),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 8, 8),
      child: Row(
        children: [
          const Expanded(child: Text('样式',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600,
              color: AppColors.styleSheetHeader))),
          IconButton(
            icon: const Icon(Icons.close, color: AppColors.textHint),
            onPressed: () => Navigator.pop(context),
          ),
        ],
      ),
    );
  }

  // Row 1: B/I/U/S + alignment
  Widget _buildInlineAndAlign() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Row(
        children: [
          _inlineButton('B', 'bold', fontWeight: FontWeight.bold),
          const SizedBox(width: 8),
          _inlineButton('I', 'italic', fontStyle: FontStyle.italic),
          const SizedBox(width: 8),
          _inlineButton('U', 'underline', decoration: TextDecoration.underline),
          const SizedBox(width: 8),
          _inlineButton('S', 'strikethrough', decoration: TextDecoration.lineThrough),
          const Spacer(),
          _alignButton(Icons.format_align_left, 'left'),
          const SizedBox(width: 4),
          _alignButton(Icons.format_align_center, 'center'),
          const SizedBox(width: 4),
          _alignButton(Icons.format_align_right, 'right'),
        ],
      ),
    );
  }

  Widget _inlineButton(String label, String attribute, {
    FontWeight? fontWeight,
    FontStyle? fontStyle,
    TextDecoration? decoration,
  }) {
    return GestureDetector(
      onTap: () => _toggleAttribute(attribute),
      child: Container(
        width: 36, height: 36,
        decoration: BoxDecoration(
          border: Border.all(color: AppColors.divider),
          borderRadius: BorderRadius.circular(4),
        ),
        alignment: Alignment.center,
        child: Text(label, style: TextStyle(
          fontSize: 18, fontWeight: fontWeight, fontStyle: fontStyle,
          decoration: decoration,
        )),
      ),
    );
  }

  Widget _alignButton(IconData icon, String alignment) {
    return GestureDetector(
      onTap: () => _setAlignment(alignment),
      child: Container(
        width: 36, height: 36,
        decoration: BoxDecoration(
          border: Border.all(color: AppColors.divider),
          borderRadius: BorderRadius.circular(4),
        ),
        alignment: Alignment.center,
        child: Icon(icon, size: 18),
      ),
    );
  }

  // Row 2: indent + lists
  Widget _buildIndentAndLists() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Row(
        children: [
          _actionButton(Icons.format_indent_increase, () => _indent()),
          const SizedBox(width: 8),
          _actionButton(Icons.format_indent_decrease, () => _outdent()),
          const Spacer(),
          _listButton('1.', 'numbered_list'),
          const SizedBox(width: 4),
          _listButton('a.', 'numbered_list'),
          const SizedBox(width: 4),
          _listButton('•', 'bulleted_list'),
          const SizedBox(width: 4),
          _listButton('○', 'bulleted_list'),
        ],
      ),
    );
  }

  Widget _actionButton(IconData icon, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 36, height: 36,
        decoration: BoxDecoration(
          border: Border.all(color: AppColors.divider),
          borderRadius: BorderRadius.circular(4),
        ),
        alignment: Alignment.center,
        child: Icon(icon, size: 18),
      ),
    );
  }

  Widget _listButton(String label, String nodeType) {
    return GestureDetector(
      onTap: () => _toggleListType(nodeType),
      child: Container(
        width: 36, height: 36,
        decoration: BoxDecoration(
          border: Border.all(color: AppColors.divider),
          borderRadius: BorderRadius.circular(4),
        ),
        alignment: Alignment.center,
        child: Text(label, style: const TextStyle(fontSize: 14)),
      ),
    );
  }

  // Row 3: font size slider
  Widget _buildFontSize() {
    final index = _fontSizes.indexOf(_currentFontSize);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        children: [
          Row(
            children: [
              const Text('A', style: TextStyle(fontSize: 11, color: AppColors.textSecondary)),
              Expanded(
                child: Slider(
                  value: (index >= 0 ? index : 2).toDouble(),
                  min: 0, max: 4, divisions: 4,
                  activeColor: AppColors.primary,
                  onChanged: (v) {
                    final size = _fontSizes[v.round()];
                    setState(() => _currentFontSize = size);
                    _setFontSize(size);
                  },
                ),
              ),
              const Text('A', style: TextStyle(fontSize: 18, color: AppColors.textSecondary)),
            ],
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: _fontSizeLabels.map((l) =>
              SizedBox(width: 40, child: Center(
                child: Text(l, style: const TextStyle(fontSize: 10, color: AppColors.textHint)),
              )),
            ).toList(),
          ),
        ],
      ),
    );
  }

  // Row 4: 7 colors
  Widget _buildColors() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: List.generate(_colors.length, (i) {
          return GestureDetector(
            onTap: () => _setColor(_colorHexes[i]),
            child: Container(
              width: 28, height: 28,
              margin: const EdgeInsets.symmetric(horizontal: 6),
              decoration: BoxDecoration(
                shape: BoxShape.circle, color: _colors[i],
              ),
            ),
          );
        }),
      ),
    );
  }

  // Row 5: H1-H6
  Widget _buildHeadings() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Row(
        children: List.generate(6, (i) {
          final level = i + 1;
          final fontSize = 20.0 - (i * 1.5);
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: GestureDetector(
              onTap: () => _setHeading(level),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  border: Border.all(color: AppColors.divider),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text('H$level', style: TextStyle(
                  fontSize: fontSize, fontWeight: FontWeight.bold,
                )),
              ),
            ),
          );
        }),
      ),
    );
  }

  // Row 6: backgrounds
  Widget _buildBackgrounds() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: _backgrounds.map((bg) {
          final isSelected = widget.currentBackground == bg.$1;
          return GestureDetector(
            onTap: () => widget.onBackgroundChanged(bg.$1),
            child: Container(
              width: 48, height: 48,
              margin: const EdgeInsets.symmetric(horizontal: 6),
              decoration: BoxDecoration(
                color: bg.$3,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: isSelected ? AppColors.primary : AppColors.divider,
                  width: isSelected ? 2 : 1,
                ),
              ),
              alignment: Alignment.center,
              child: Text(bg.$2, style: TextStyle(
                fontSize: 10,
                color: isSelected ? AppColors.primary : AppColors.textHint,
              )),
            ),
          );
        }).toList(),
      ),
    );
  }

  // --- Formatting actions ---

  void _toggleAttribute(String attribute) {
    final selection = widget.editorState.selection;
    if (selection == null) return;
    widget.editorState.toggleAttribute(attribute);
  }

  void _setAlignment(String alignment) {
    final selection = widget.editorState.selection;
    if (selection == null) return;
    final node = widget.editorState.getNodeAtPath(selection.start.path);
    if (node == null) return;
    final transaction = widget.editorState.transaction;
    transaction.updateNode(node, {'align': alignment});
    widget.editorState.apply(transaction);
  }

  void _indent() {
    // Use appflowy_editor indent command
    final selection = widget.editorState.selection;
    if (selection == null) return;
    indentCommand.execute(widget.editorState);
  }

  void _outdent() {
    final selection = widget.editorState.selection;
    if (selection == null) return;
    outdentCommand.execute(widget.editorState);
  }

  void _toggleListType(String nodeType) {
    final selection = widget.editorState.selection;
    if (selection == null) return;
    final node = widget.editorState.getNodeAtPath(selection.start.path);
    if (node == null) return;
    final transaction = widget.editorState.transaction;
    if (node.type == nodeType) {
      // Toggle off: convert back to paragraph
      transaction.updateNode(node, {'type': 'paragraph'});
    } else {
      transaction.updateNode(node, {'type': nodeType});
    }
    widget.editorState.apply(transaction);
  }

  void _setFontSize(double size) {
    final selection = widget.editorState.selection;
    if (selection == null) return;
    widget.editorState.formatDelta(selection, {'fontSize': size});
  }

  void _setColor(String hex) {
    final selection = widget.editorState.selection;
    if (selection == null) return;
    widget.editorState.formatDelta(selection, {'color': hex});
  }

  void _setHeading(int level) {
    final selection = widget.editorState.selection;
    if (selection == null) return;
    final node = widget.editorState.getNodeAtPath(selection.start.path);
    if (node == null) return;
    final transaction = widget.editorState.transaction;
    if (node.type == 'heading' && node.attributes['level'] == level) {
      // Toggle off
      transaction.updateNode(node, {'type': 'paragraph'});
    } else {
      transaction.updateNode(node, {'type': 'heading', 'level': level});
    }
    widget.editorState.apply(transaction);
  }
}
```

> **重要：** 上面的格式化操作（`toggleAttribute`、`formatDelta`、`indentCommand`、`outdentCommand` 等）的 API 名称基于 appflowy_editor 的设计意图。实际实施时**必须**查阅 appflowy_editor 的最新 API 文档确认。常见差异包括：
> - `toggleAttribute` 可能是 `editorState.toggleAttribute(key)` 或需要通过 `formatDelta` 实现
> - `indentCommand` / `outdentCommand` 可能是通过 `CharacterShortcutEvent` 或专用方法
> - `formatDelta` 可能是 `editorState.formatDelta(selection, attributes)` 或需要构造 Transaction
> - 列表类型切换可能需要 `convertToType` 或 `updateNodeType` 方法

- [ ] **Step 2: 在 NoteEditorPage 中连接 StylePickerSheet**

更新 `_showStylePicker()` 方法：

```dart
  void _showStylePicker() {
    if (_editorState == null) return;
    final state = ref.read(noteEditorProvider(widget.noteId));
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => StylePickerSheet(
        editorState: _editorState!,
        currentBackground: state.pendingBackground,
        onBackgroundChanged: (bg) {
          ref.read(noteEditorProvider(widget.noteId).notifier).setBackground(bg);
        },
      ),
    );
  }
```

添加 import: `import '../widgets/editor/style_picker_sheet.dart';`

- [ ] **Step 3: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

Expected: 无 error。如有 appflowy_editor API 不匹配，根据实际 API 调整。

- [ ] **Step 4: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/editor/style_picker_sheet.dart code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat: StylePickerBottomSheet 样式面板（6行格式化控件）"
```

---

### Task 12: BrowseBottomBar 组件

**Files:**
- Create: `code/HuaweiNoteFlutter/lib/widgets/editor/browse_bottom_bar.dart`
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`

- [ ] **Step 1: 创建 BrowseBottomBar**

创建 `lib/widgets/editor/browse_bottom_bar.dart`：

```dart
import 'package:flutter/material.dart';
import '../../theme.dart';

class BrowseBottomBar extends StatelessWidget {
  final VoidCallback onShare;
  final VoidCallback onFavorite;
  final VoidCallback onDelete;
  final VoidCallback onMore;
  final bool isFavorite;

  const BrowseBottomBar({
    super.key,
    required this.onShare,
    required this.onFavorite,
    required this.onDelete,
    required this.onMore,
    required this.isFavorite,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFFFAFAFA),
        border: Border(top: BorderSide(color: AppColors.divider, width: 0.5)),
      ),
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _BarButton(icon: Icons.share_outlined, label: '分享', onTap: onShare),
          _BarButton(
            icon: isFavorite ? Icons.star : Icons.star_border,
            label: '收藏',
            onTap: onFavorite,
            iconColor: isFavorite ? Colors.amber : null,
          ),
          _BarButton(icon: Icons.delete_outline, label: '删除', onTap: onDelete),
          _BarButton(icon: Icons.more_horiz, label: '更多', onTap: onMore),
        ],
      ),
    );
  }
}

class _BarButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final Color? iconColor;

  const _BarButton({
    required this.icon,
    required this.label,
    required this.onTap,
    this.iconColor,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 22, color: iconColor ?? AppColors.textSecondary),
          const SizedBox(height: 2),
          Text(label, style: const TextStyle(
            fontSize: AppDimens.textHint, color: AppColors.textSecondary,
          )),
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: 在 NoteEditorPage 中使用 BrowseBottomBar**

在 `note_editor_page.dart` 中：
- 添加 import: `import '../widgets/editor/browse_bottom_bar.dart';`
- 添加 import: `import 'package:share_plus/share_plus.dart';`
- 替换浏览模式的占位 `Container` 为：

```dart
              if (!state.isEditing && state.noteId > 0)
                BrowseBottomBar(
                  isFavorite: state.loadedNote?.isFavorite ?? false,
                  onShare: () {
                    final text = state.loadedNote?.content.toPlainText() ?? '';
                    final title = state.title.isNotEmpty ? state.title : '';
                    final shareText = title.isNotEmpty ? '$title\n$text' : text;
                    if (shareText.isNotEmpty) {
                      SharePlus.instance.share(ShareParams(text: shareText));
                    }
                  },
                  onFavorite: () => ref.read(noteEditorProvider(widget.noteId).notifier).toggleFavorite(),
                  onDelete: () => _confirmDelete(),
                  onMore: () => ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('更多功能将在后续版本实现'))),
                ),
```

添加删除确认方法：

```dart
  Future<void> _confirmDelete() async {
    final confirmed = await showDeleteConfirmSheet(context,
      message: '确定要删除这条笔记吗？\n删除后可在"最近删除"中恢复',
      confirmLabel: '删除');
    if (!confirmed || !mounted) return;
    await ref.read(noteEditorProvider(widget.noteId).notifier).softDelete();
    if (mounted) context.pop();
  }
```

添加 import: `import '../widgets/delete_confirm_sheet.dart';`

- [ ] **Step 3: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

- [ ] **Step 4: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/widgets/editor/browse_bottom_bar.dart code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart
git commit -m "feat: BrowseBottomBar（分享/收藏/删除/更多）"
```

---

### Task 13: 完善保存/加载流程 + 模式切换

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart`
- Modify: `code/HuaweiNoteFlutter/lib/providers/note_editor_provider.dart`

- [ ] **Step 1: 完善 NoteEditorPage 的 auto-save 和模式切换**

在 `note_editor_page.dart` 中更新 `_onBack` 方法，确保保存逻辑正确：

```dart
  Future<void> _onBack() async {
    final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
    final editorState = ref.read(noteEditorProvider(widget.noteId));

    if (editorState.isEditing) {
      // 自动保存
      final saved = await notifier.saveNote();
      // 新建笔记且为空 → 不保存，直接返回
      if (!saved && editorState.noteId == 0) {
        if (mounted) context.pop();
        return;
      }
    }
    if (mounted) context.pop();
  }
```

- [ ] **Step 2: 新建笔记时光标聚焦标题**

在 `initState` 的 `Future.microtask` 中，加载完成后：

```dart
      if (widget.noteId == 0) {
        // 新建笔记 → 聚焦标题
        WidgetsBinding.instance.addPostFrameCallback((_) {
          _titleController.selection = TextSelection.collapsed(offset: 0);
          // Request focus on title field
          _titleFocusNode.requestFocus();
        });
      }
```

在 `_NoteEditorPageState` 中添加 `_titleFocusNode`：

```dart
  late final FocusNode _titleFocusNode;
```

在 `initState` 中初始化：`_titleFocusNode = FocusNode();`

在 `dispose` 中释放：`_titleFocusNode.dispose();`

给 Title 的 `TextField` 添加 `focusNode: _titleFocusNode`。

- [ ] **Step 3: 已有笔记进入编辑模式时光标定位到文档末尾**

在 `enterEditMode` 调用后，定位光标：

```dart
  void _handleEnterEditMode() {
    ref.read(noteEditorProvider(widget.noteId).notifier).enterEditMode();
    if (_editorState != null) {
      final lastNode = _editorState!.document.root.children.lastOrNull;
      if (lastNode != null) {
        final delta = lastNode.delta;
        if (delta != null) {
          _editorState!.updateSelectionWithReason(
            Selection.collapsed(Position(path: lastNode.path, offset: delta.length)),
          );
        }
      }
    }
  }
```

更新 `GestureDetector` 的 `onTap` 使用新方法：

```dart
                  onTap: () {
                    if (!state.isEditing) _handleEnterEditMode();
                  },
```

> **注：** `updateSelectionWithReason` 的实际 API 名称需确认。可能是 `editorState.selection = ...` 或 `editorState.updateCursorSelection(...)`.

- [ ] **Step 4: dispose 时自动保存**

在 `dispose` 方法中添加保存逻辑：

```dart
  @override
  void dispose() {
    // 在 dispose 之前触发保存（同 Android onPause）
    // 注意：dispose 中不能使用 ref.read，需要在 deactivate 中处理
    _scrollController?.dispose();
    _titleFocusNode.dispose();
    _titleController.dispose();
    super.dispose();
  }

  @override
  void deactivate() {
    // 页面即将被移除时自动保存
    final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
    notifier.saveNote();
    super.deactivate();
  }
```

- [ ] **Step 5: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

- [ ] **Step 6: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart code/HuaweiNoteFlutter/lib/providers/note_editor_provider.dart
git commit -m "feat: 完善保存流程（auto-save/空笔记保护/光标定位）"
```

---

### Task 14: 列表页对接 — FAB 和 NoteCard 导航到编辑器

**Files:**
- Modify: `code/HuaweiNoteFlutter/lib/pages/note_list_page.dart`

- [ ] **Step 1: 添加 go_router import**

在 `note_list_page.dart` 顶部添加：

```dart
import 'package:go_router/go_router.dart';
```

- [ ] **Step 2: 修改 FAB onPressed**

将：
```dart
            onPressed: () => ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('新建笔记将在 Phase 2B 实现'))),
```

替换为：
```dart
            onPressed: () => context.push('/editor/0'),
```

- [ ] **Step 3: 修改 NoteCard onTap**

在 `_buildCard` 方法中，将：
```dart
      onTap: () => ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('编辑器将在 Phase 2B 实现'))),
```

替换为：
```dart
      onTap: () => context.push('/editor/${note.id}'),
```

- [ ] **Step 4: 列表页返回时刷新列表**

确保从编辑器返回后列表数据刷新。由于使用 `context.push` / `context.pop`，列表页不会被销毁。可以利用 `RouteAware` 或在 `build` 中检测。

最简单的方案是在列表页的 `build` 中监听路由变化，或者在 FAB / onTap 中使用 `await` 等待编辑器 pop：

```dart
            onPressed: () async {
              await context.push('/editor/0');
              if (mounted) ref.read(noteListProvider.notifier).reload();
            },
```

对 NoteCard.onTap 同理：

```dart
      onTap: () async {
        await context.push('/editor/${note.id}');
        if (mounted) ref.read(noteListProvider.notifier).reload();
      },
```

> **注：** `context.push` 返回 `Future`，当编辑器 pop 时 Future 完成。如果 go_router 版本不支持 await push，可以使用 `ref.listen` 或在 `didChangeDependencies` 中 reload。

- [ ] **Step 5: 验证编译**

```bash
cd code/HuaweiNoteFlutter && flutter analyze --no-fatal-infos
```

- [ ] **Step 6: 运行全部测试**

```bash
cd code/HuaweiNoteFlutter && flutter test
```

Expected: 全部 PASS。

- [ ] **Step 7: Commit**

```bash
git add code/HuaweiNoteFlutter/lib/pages/note_list_page.dart
git commit -m "feat: 列表页 FAB 和 NoteCard 导航到编辑器"
```
