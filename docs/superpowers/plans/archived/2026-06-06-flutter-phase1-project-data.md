# Flutter Phase 1：项目搭建 + 数据层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 Flutter 多平台项目，迁移现有 Android 端的全部数据模型、数据库和 Repository 层到 Dart。

**Architecture:** 新建独立的 Flutter 项目（与现有 Android 项目并存于 `code/` 目录下）。数据模型从 Kotlin data class 直译为 Dart class，数据库从 Android SQLiteOpenHelper 迁移到 sqflite，Repository 层保持相同的 API 语义。所有代码配套单元测试。

**Tech Stack:** Flutter 3.27.x / Dart / sqflite / sqflite_common_ffi（桌面测试用）/ Riverpod / go_router

---

## 文件结构

### 新建文件

```
code/hwnote_flutter/
├── pubspec.yaml                                          ← 项目依赖配置
├── lib/
│   ├── main.dart                                         ← 入口（占位）
│   ├── models/
│   │   ├── note.dart                                     ← Note 数据模型
│   │   ├── folder.dart                                   ← Folder 数据模型
│   │   ├── notebook.dart                                 ← Notebook 数据模型
│   │   ├── category.dart                                 ← Category 数据模型
│   │   ├── todo.dart                                     ← Todo + RepeatType 数据模型
│   │   ├── block.dart                                    ← Block 类型层级 + ChecklistItem + 枚举
│   │   ├── text_span.dart                                ← TextSpan + SpanType
│   │   ├── stroke.dart                                   ← Stroke + StrokePoint + BrushType
│   │   └── note_content.dart                             ← NoteContent + toPlainText()
│   ├── db/
│   │   └── database_helper.dart                          ← sqflite 数据库初始化 + 建表 + 迁移
│   └── repositories/
│       ├── note_repository.dart                          ← 笔记 CRUD
│       ├── folder_repository.dart                        ← 文件夹 CRUD
│       ├── notebook_repository.dart                      ← 笔记本 CRUD
│       ├── category_repository.dart                      ← 分类 CRUD
│       └── todo_repository.dart                          ← 待办 CRUD
├── test/
│   ├── models/
│   │   ├── note_test.dart
│   │   ├── folder_test.dart
│   │   ├── notebook_test.dart
│   │   ├── category_test.dart
│   │   ├── todo_test.dart
│   │   ├── block_test.dart
│   │   ├── text_span_test.dart
│   │   ├── stroke_test.dart
│   │   └── note_content_test.dart
│   ├── db/
│   │   └── database_helper_test.dart
│   └── repositories/
│       ├── note_repository_test.dart
│       ├── folder_repository_test.dart
│       ├── notebook_repository_test.dart
│       ├── category_repository_test.dart
│       └── todo_repository_test.dart
```

---

### Task 1: Flutter 项目初始化

**Files:**
- Create: `code/hwnote_flutter/` (整个项目骨架)
- Create: `code/hwnote_flutter/pubspec.yaml`
- Create: `code/hwnote_flutter/lib/main.dart`

- [ ] **Step 1: 创建 Flutter 项目**

```bash
cd code
flutter create --org com.fan --project-name hwnote --platforms android,ios,macos,windows hwnote_flutter
```

注意：鸿蒙平台（ohos）需要 CPF-Flutter SDK，当前阶段先用标准 Flutter 创建 4 端，后续 Phase 4 再添加鸿蒙。

- [ ] **Step 2: 配置 pubspec.yaml 依赖**

替换 `code/hwnote_flutter/pubspec.yaml` 的 `dependencies` 和 `dev_dependencies` 部分：

```yaml
name: hwnote
description: HwNote - 华为风格备忘录
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: ^3.7.0

dependencies:
  flutter:
    sdk: flutter
  sqflite: ^2.4.2
  path_provider: ^2.1.5
  path: ^1.9.1
  flutter_riverpod: ^2.6.1
  go_router: ^14.8.1

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^5.0.0
  sqflite_common_ffi: ^2.3.4+4

flutter:
  uses-material-design: true
```

- [ ] **Step 3: 获取依赖**

```bash
cd code/hwnote_flutter
flutter pub get
```

Expected: 无错误，所有依赖解析成功。

- [ ] **Step 4: 验证项目构建**

```bash
cd code/hwnote_flutter
flutter analyze
```

Expected: No issues found.

- [ ] **Step 5: 提交**

```bash
git add code/hwnote_flutter
git commit -m "feat: 初始化 Flutter 多平台项目骨架"
```

---

### Task 2: 基础数据模型 — Block / TextSpan / Stroke / NoteContent

**Files:**
- Create: `code/hwnote_flutter/lib/models/text_span.dart`
- Create: `code/hwnote_flutter/lib/models/block.dart`
- Create: `code/hwnote_flutter/lib/models/stroke.dart`
- Create: `code/hwnote_flutter/lib/models/note_content.dart`
- Test: `code/hwnote_flutter/test/models/block_test.dart`
- Test: `code/hwnote_flutter/test/models/text_span_test.dart`
- Test: `code/hwnote_flutter/test/models/stroke_test.dart`
- Test: `code/hwnote_flutter/test/models/note_content_test.dart`

- [ ] **Step 1: 写 TextSpan + SpanType 的测试**

```dart
// test/models/text_span_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/text_span.dart';

void main() {
  group('SpanType', () {
    test('has all expected values', () {
      expect(SpanType.values.length, 6);
      expect(SpanType.values, contains(SpanType.bold));
      expect(SpanType.values, contains(SpanType.italic));
      expect(SpanType.values, contains(SpanType.underline));
      expect(SpanType.values, contains(SpanType.strikethrough));
      expect(SpanType.values, contains(SpanType.fontSize));
      expect(SpanType.values, contains(SpanType.color));
    });
  });

  group('NoteTextSpan', () {
    test('creates with required fields', () {
      final span = NoteTextSpan(start: 0, end: 5, type: SpanType.bold);
      expect(span.start, 0);
      expect(span.end, 5);
      expect(span.type, SpanType.bold);
      expect(span.value, isNull);
    });

    test('creates with optional value', () {
      final span = NoteTextSpan(
        start: 0, end: 5, type: SpanType.color, value: '#FF0000',
      );
      expect(span.value, '#FF0000');
    });

    test('equality works', () {
      final a = NoteTextSpan(start: 0, end: 5, type: SpanType.bold);
      final b = NoteTextSpan(start: 0, end: 5, type: SpanType.bold);
      expect(a, equals(b));
    });

    test('toJson and fromJson roundtrip', () {
      final span = NoteTextSpan(
        start: 2, end: 7, type: SpanType.fontSize, value: 'large',
      );
      final json = span.toJson();
      final restored = NoteTextSpan.fromJson(json);
      expect(restored, equals(span));
    });

    test('fromJson with unknown type returns null', () {
      final json = {'start': 0, 'end': 5, 'type': 'unknown_type'};
      expect(NoteTextSpan.fromJson(json), isNull);
    });
  });
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd code/hwnote_flutter
flutter test test/models/text_span_test.dart
```

Expected: FAIL — `package:hwnote/models/text_span.dart` not found.

- [ ] **Step 3: 实现 TextSpan + SpanType**

```dart
// lib/models/text_span.dart

enum SpanType {
  bold,
  italic,
  underline,
  strikethrough,
  fontSize,
  color;

  static SpanType? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
}

class NoteTextSpan {
  final int start;
  final int end;
  final SpanType type;
  final String? value;

  const NoteTextSpan({
    required this.start,
    required this.end,
    required this.type,
    this.value,
  });

  Map<String, dynamic> toJson() => {
    'start': start,
    'end': end,
    'type': type.name,
    if (value != null) 'value': value,
  };

  static NoteTextSpan? fromJson(Map<String, dynamic> json) {
    final type = SpanType.fromName(json['type'] as String? ?? '');
    if (type == null) return null;
    return NoteTextSpan(
      start: json['start'] as int? ?? 0,
      end: json['end'] as int? ?? 0,
      type: type,
      value: json['value'] as String?,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is NoteTextSpan &&
          start == other.start &&
          end == other.end &&
          type == other.type &&
          value == other.value;

  @override
  int get hashCode => Object.hash(start, end, type, value);

  @override
  String toString() => 'NoteTextSpan($start..$end, $type, value=$value)';
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd code/hwnote_flutter
flutter test test/models/text_span_test.dart
```

Expected: All tests passed.

- [ ] **Step 5: 写 Block 相关类型的测试**

```dart
// test/models/block_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/block.dart';
import 'package:hwnote/models/text_span.dart';

void main() {
  group('Heading', () {
    test('fromName returns correct value', () {
      expect(Heading.fromName('h1'), Heading.h1);
      expect(Heading.fromName('h6'), Heading.h6);
      expect(Heading.fromName('unknown'), isNull);
    });
  });

  group('NoteAlignment', () {
    test('has 3 values', () {
      expect(NoteAlignment.values.length, 3);
    });
  });

  group('ListType', () {
    test('has 4 values', () {
      expect(ListType.values.length, 4);
    });
  });

  group('TextBlock', () {
    test('creates with defaults', () {
      final b = TextBlock(id: 'b-001');
      expect(b.id, 'b-001');
      expect(b.text, '');
      expect(b.spans, isEmpty);
      expect(b.heading, isNull);
      expect(b.alignment, isNull);
      expect(b.listType, isNull);
      expect(b.indentLevel, 0);
    });

    test('toJson and fromJson roundtrip', () {
      final b = TextBlock(
        id: 'b-001',
        text: 'Hello',
        heading: Heading.h1,
        spans: [NoteTextSpan(start: 0, end: 5, type: SpanType.bold)],
        alignment: NoteAlignment.center,
        listType: ListType.bullet,
        indentLevel: 2,
      );
      final json = b.toJson();
      expect(json['type'], 'text');
      final restored = Block.fromJson(json);
      expect(restored, isA<TextBlock>());
      final rt = restored as TextBlock;
      expect(rt.id, 'b-001');
      expect(rt.text, 'Hello');
      expect(rt.heading, Heading.h1);
      expect(rt.spans.length, 1);
      expect(rt.alignment, NoteAlignment.center);
      expect(rt.listType, ListType.bullet);
      expect(rt.indentLevel, 2);
    });
  });

  group('ImageBlock', () {
    test('toJson and fromJson roundtrip', () {
      final b = ImageBlock(id: 'b-002', fileName: 'img.jpg', width: 800, height: 600);
      final json = b.toJson();
      expect(json['type'], 'image');
      final restored = Block.fromJson(json) as ImageBlock;
      expect(restored.fileName, 'img.jpg');
      expect(restored.width, 800);
      expect(restored.height, 600);
    });
  });

  group('ChecklistBlock', () {
    test('toJson and fromJson roundtrip', () {
      final b = ChecklistBlock(id: 'b-003', items: [
        ChecklistItem(checked: true, text: 'Done'),
        ChecklistItem(checked: false, text: 'Todo'),
      ]);
      final json = b.toJson();
      expect(json['type'], 'checklist');
      final restored = Block.fromJson(json) as ChecklistBlock;
      expect(restored.items.length, 2);
      expect(restored.items[0].checked, true);
      expect(restored.items[1].text, 'Todo');
    });
  });

  group('AudioBlock', () {
    test('toJson and fromJson roundtrip', () {
      final b = AudioBlock(id: 'b-004', fileName: 'rec.m4a', durationMs: 5000);
      final json = b.toJson();
      expect(json['type'], 'audio');
      final restored = Block.fromJson(json) as AudioBlock;
      expect(restored.fileName, 'rec.m4a');
      expect(restored.durationMs, 5000);
    });
  });

  group('Block.fromJson', () {
    test('returns null for unknown type', () {
      expect(Block.fromJson({'type': 'video', 'id': 'x'}), isNull);
    });
  });
}
```

- [ ] **Step 6: 运行测试确认失败**

```bash
cd code/hwnote_flutter
flutter test test/models/block_test.dart
```

Expected: FAIL — `package:hwnote/models/block.dart` not found.

- [ ] **Step 7: 实现 Block 类型层级**

```dart
// lib/models/block.dart
import 'text_span.dart';

enum Heading {
  h1, h2, h3, h4, h5, h6;

  static Heading? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
}

enum NoteAlignment {
  start, center, end;

  static NoteAlignment? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
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

sealed class Block {
  String get id;
  Map<String, dynamic> toJson();

  static Block? fromJson(Map<String, dynamic> json) {
    final type = json['type'] as String? ?? '';
    return switch (type) {
      'text' => TextBlock.fromJson(json),
      'image' => ImageBlock.fromJson(json),
      'checklist' => ChecklistBlock.fromJson(json),
      'audio' => AudioBlock.fromJson(json),
      _ => null,
    };
  }
}

class TextBlock extends Block {
  @override
  final String id;
  final Heading? heading;
  final String text;
  final List<NoteTextSpan> spans;
  final NoteAlignment? alignment;
  final ListType? listType;
  final int indentLevel;

  TextBlock({
    required this.id,
    this.heading,
    this.text = '',
    this.spans = const [],
    this.alignment,
    this.listType,
    this.indentLevel = 0,
  });

  @override
  Map<String, dynamic> toJson() => {
    'type': 'text',
    'id': id,
    'text': text,
    'spans': spans.map((s) => s.toJson()).toList(),
    if (heading != null) 'heading': heading!.name,
    if (alignment != null) 'alignment': alignment!.name,
    if (listType != null) 'listType': listType!.name,
    if (indentLevel > 0) 'indentLevel': indentLevel,
  };

  static TextBlock fromJson(Map<String, dynamic> json) {
    final spansList = (json['spans'] as List<dynamic>?)
        ?.map((s) => NoteTextSpan.fromJson(s as Map<String, dynamic>))
        .whereType<NoteTextSpan>()
        .toList() ?? [];
    return TextBlock(
      id: json['id'] as String? ?? '',
      heading: Heading.fromName(json['heading'] as String? ?? ''),
      text: json['text'] as String? ?? '',
      spans: spansList,
      alignment: NoteAlignment.fromName(json['alignment'] as String? ?? ''),
      listType: ListType.fromName(json['listType'] as String? ?? ''),
      indentLevel: json['indentLevel'] as int? ?? 0,
    );
  }
}

class ImageBlock extends Block {
  @override
  final String id;
  final String fileName;
  final int width;
  final int height;

  ImageBlock({
    required this.id,
    required this.fileName,
    required this.width,
    required this.height,
  });

  @override
  Map<String, dynamic> toJson() => {
    'type': 'image',
    'id': id,
    'fileName': fileName,
    'width': width,
    'height': height,
  };

  static ImageBlock fromJson(Map<String, dynamic> json) => ImageBlock(
    id: json['id'] as String? ?? '',
    fileName: json['fileName'] as String? ?? '',
    width: json['width'] as int? ?? 0,
    height: json['height'] as int? ?? 0,
  );
}

class ChecklistItem {
  final bool checked;
  final String text;

  const ChecklistItem({required this.checked, required this.text});

  Map<String, dynamic> toJson() => {'checked': checked, 'text': text};

  static ChecklistItem fromJson(Map<String, dynamic> json) => ChecklistItem(
    checked: json['checked'] as bool? ?? false,
    text: json['text'] as String? ?? '',
  );
}

class ChecklistBlock extends Block {
  @override
  final String id;
  final List<ChecklistItem> items;

  ChecklistBlock({required this.id, this.items = const []});

  @override
  Map<String, dynamic> toJson() => {
    'type': 'checklist',
    'id': id,
    'items': items.map((i) => i.toJson()).toList(),
  };

  static ChecklistBlock fromJson(Map<String, dynamic> json) {
    final itemsList = (json['items'] as List<dynamic>?)
        ?.map((i) => ChecklistItem.fromJson(i as Map<String, dynamic>))
        .toList() ?? [];
    return ChecklistBlock(id: json['id'] as String? ?? '', items: itemsList);
  }
}

class AudioBlock extends Block {
  @override
  final String id;
  final String fileName;
  final int durationMs;

  AudioBlock({
    required this.id,
    required this.fileName,
    required this.durationMs,
  });

  @override
  Map<String, dynamic> toJson() => {
    'type': 'audio',
    'id': id,
    'fileName': fileName,
    'durationMs': durationMs,
  };

  static AudioBlock fromJson(Map<String, dynamic> json) => AudioBlock(
    id: json['id'] as String? ?? '',
    fileName: json['fileName'] as String? ?? '',
    durationMs: json['durationMs'] as int? ?? 0,
  );
}
```

- [ ] **Step 8: 运行 Block 测试确认通过**

```bash
cd code/hwnote_flutter
flutter test test/models/block_test.dart
```

Expected: All tests passed.

- [ ] **Step 9: 写 Stroke 测试**

```dart
// test/models/stroke_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/stroke.dart';

void main() {
  group('BrushType', () {
    test('has 4 values', () {
      expect(BrushType.values.length, 4);
      expect(BrushType.values, contains(BrushType.pen));
      expect(BrushType.values, contains(BrushType.brush));
      expect(BrushType.values, contains(BrushType.marker));
      expect(BrushType.values, contains(BrushType.pencil));
    });
  });

  group('StrokePoint', () {
    test('creates correctly', () {
      final p = StrokePoint(x: 100, y: 200, t: 50);
      expect(p.x, 100);
      expect(p.y, 200);
      expect(p.t, 50);
    });
  });

  group('Stroke', () {
    test('toJson and fromJson roundtrip', () {
      final stroke = Stroke(
        brush: BrushType.pen,
        color: '#000000',
        width: 3,
        points: [
          StrokePoint(x: 10, y: 20, t: 0),
          StrokePoint(x: 30, y: 40, t: 16),
        ],
      );
      final json = stroke.toJson();
      expect(json['brush'], 'pen');
      final restored = Stroke.fromJson(json);
      expect(restored, isNotNull);
      expect(restored!.brush, BrushType.pen);
      expect(restored.points.length, 2);
      expect(restored.points[0].x, 10);
    });

    test('fromJson with unknown brush returns null', () {
      final json = {'brush': 'crayon', 'color': '#000', 'width': 1, 'points': []};
      expect(Stroke.fromJson(json), isNull);
    });

    test('fromJson with missing points returns null', () {
      final json = {'brush': 'pen', 'color': '#000', 'width': 1};
      expect(Stroke.fromJson(json), isNull);
    });
  });
}
```

- [ ] **Step 10: 实现 Stroke**

```dart
// lib/models/stroke.dart

enum BrushType {
  pen, brush, marker, pencil;

  static BrushType? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
}

class StrokePoint {
  final int x;
  final int y;
  final int t;

  const StrokePoint({required this.x, required this.y, required this.t});
}

class Stroke {
  final BrushType brush;
  final String color;
  final int width;
  final List<StrokePoint> points;

  const Stroke({
    required this.brush,
    required this.color,
    required this.width,
    required this.points,
  });

  Map<String, dynamic> toJson() => {
    'brush': brush.name,
    'color': color,
    'width': width,
    'points': points.map((p) => [p.x, p.y, p.t]).toList(),
  };

  static Stroke? fromJson(Map<String, dynamic> json) {
    final brush = BrushType.fromName(json['brush'] as String? ?? '');
    if (brush == null) return null;
    final ptsList = json['points'] as List<dynamic>?;
    if (ptsList == null) return null;
    final points = <StrokePoint>[];
    for (final p in ptsList) {
      if (p is List && p.length >= 3) {
        points.add(StrokePoint(x: p[0] as int, y: p[1] as int, t: p[2] as int));
      }
    }
    return Stroke(
      brush: brush,
      color: json['color'] as String? ?? '',
      width: json['width'] as int? ?? 1,
      points: points,
    );
  }
}
```

- [ ] **Step 11: 写 NoteContent 测试**

```dart
// test/models/note_content_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/note_content.dart';
import 'package:hwnote/models/block.dart';
import 'package:hwnote/models/stroke.dart';

void main() {
  group('NoteContent', () {
    test('empty() creates empty content', () {
      final c = NoteContent.empty();
      expect(c.blocks, isEmpty);
      expect(c.handwriting, isEmpty);
    });

    test('toPlainText extracts text from TextBlock and ChecklistBlock', () {
      final c = NoteContent(
        blocks: [
          TextBlock(id: '1', text: 'Hello'),
          ImageBlock(id: '2', fileName: 'a.jpg', width: 1, height: 1),
          ChecklistBlock(id: '3', items: [
            ChecklistItem(checked: false, text: 'Buy milk'),
            ChecklistItem(checked: true, text: ''),
          ]),
          AudioBlock(id: '4', fileName: 'r.m4a', durationMs: 1000),
          TextBlock(id: '5', text: ''),
        ],
        handwriting: [],
      );
      expect(c.toPlainText(), 'Hello\nBuy milk');
    });

    test('toJson and fromJson roundtrip', () {
      final c = NoteContent(
        blocks: [TextBlock(id: '1', text: 'Hi')],
        handwriting: [
          Stroke(brush: BrushType.pen, color: '#000', width: 2, points: [
            StrokePoint(x: 1, y: 2, t: 0),
          ]),
        ],
      );
      final json = c.toJson();
      final restored = NoteContent.fromJson(json);
      expect(restored.blocks.length, 1);
      expect((restored.blocks[0] as TextBlock).text, 'Hi');
      expect(restored.handwriting.length, 1);
    });

    test('fromJson with invalid data returns empty', () {
      final c = NoteContent.fromJson('not json');
      expect(c.blocks, isEmpty);
      expect(c.handwriting, isEmpty);
    });
  });
}
```

- [ ] **Step 12: 实现 NoteContent**

```dart
// lib/models/note_content.dart
import 'dart:convert';
import 'block.dart';
import 'stroke.dart';

class NoteContent {
  final List<Block> blocks;
  final List<Stroke> handwriting;

  const NoteContent({required this.blocks, required this.handwriting});

  factory NoteContent.empty() => const NoteContent(blocks: [], handwriting: []);

  String toPlainText() {
    final parts = <String>[];
    for (final b in blocks) {
      switch (b) {
        case TextBlock():
          if (b.text.isNotEmpty) parts.add(b.text);
        case ChecklistBlock():
          for (final item in b.items) {
            if (item.text.isNotEmpty) parts.add(item.text);
          }
        case ImageBlock():
          break;
        case AudioBlock():
          break;
      }
    }
    return parts.join('\n');
  }

  String toJson() {
    final root = <String, dynamic>{
      'blocks': blocks.map((b) => b.toJson()).toList(),
      'handwriting': {
        'strokes': handwriting.map((s) => s.toJson()).toList(),
      },
    };
    return jsonEncode(root);
  }

  static NoteContent fromJson(String s) {
    try {
      final root = jsonDecode(s) as Map<String, dynamic>;
      final blocksJson = root['blocks'] as List<dynamic>? ?? [];
      final blocks = blocksJson
          .map((b) => Block.fromJson(b as Map<String, dynamic>))
          .whereType<Block>()
          .toList();
      final hw = root['handwriting'] as Map<String, dynamic>? ?? {};
      final strokesJson = hw['strokes'] as List<dynamic>? ?? [];
      final strokes = strokesJson
          .map((s) => Stroke.fromJson(s as Map<String, dynamic>))
          .whereType<Stroke>()
          .toList();
      return NoteContent(blocks: blocks, handwriting: strokes);
    } catch (_) {
      return NoteContent.empty();
    }
  }
}
```

- [ ] **Step 13: 运行全部模型测试**

```bash
cd code/hwnote_flutter
flutter test test/models/
```

Expected: All tests passed.

- [ ] **Step 14: 提交**

```bash
git add code/hwnote_flutter/lib/models/text_span.dart code/hwnote_flutter/lib/models/block.dart code/hwnote_flutter/lib/models/stroke.dart code/hwnote_flutter/lib/models/note_content.dart code/hwnote_flutter/test/models/block_test.dart code/hwnote_flutter/test/models/text_span_test.dart code/hwnote_flutter/test/models/stroke_test.dart code/hwnote_flutter/test/models/note_content_test.dart
git commit -m "feat: 迁移 Block/TextSpan/Stroke/NoteContent 数据模型到 Dart"
```

---

### Task 3: 业务实体模型 — Note / Folder / Notebook / Category / Todo

**Files:**
- Create: `code/hwnote_flutter/lib/models/note.dart`
- Create: `code/hwnote_flutter/lib/models/folder.dart`
- Create: `code/hwnote_flutter/lib/models/notebook.dart`
- Create: `code/hwnote_flutter/lib/models/category.dart`
- Create: `code/hwnote_flutter/lib/models/todo.dart`
- Test: `code/hwnote_flutter/test/models/note_test.dart`
- Test: `code/hwnote_flutter/test/models/folder_test.dart`
- Test: `code/hwnote_flutter/test/models/notebook_test.dart`
- Test: `code/hwnote_flutter/test/models/category_test.dart`
- Test: `code/hwnote_flutter/test/models/todo_test.dart`

- [ ] **Step 1: 写全部实体测试**

```dart
// test/models/note_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/note.dart';
import 'package:hwnote/models/note_content.dart';

void main() {
  group('Note', () {
    test('creates with defaults', () {
      final n = Note(id: 0, createdAt: 1000, updatedAt: 1000);
      expect(n.title, '');
      expect(n.isFavorite, false);
      expect(n.deletedAt, 0);
      expect(n.background, 'plain');
      expect(n.notebookId, isNull);
      expect(n.categoryId, isNull);
    });

    test('newNote() sets timestamps', () {
      final now = DateTime.now().millisecondsSinceEpoch;
      final n = Note.newNote(now: now);
      expect(n.id, 0);
      expect(n.createdAt, now);
      expect(n.updatedAt, now);
    });

    test('copyWith preserves unmodified fields', () {
      final n = Note(id: 1, title: 'A', createdAt: 100, updatedAt: 200);
      final n2 = n.copyWith(title: 'B');
      expect(n2.id, 1);
      expect(n2.title, 'B');
      expect(n2.createdAt, 100);
    });
  });
}
```

```dart
// test/models/folder_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/folder.dart';

void main() {
  group('Folder', () {
    test('creates with defaults', () {
      final f = Folder(id: 0, name: 'Test');
      expect(f.orderIndex, 0);
      expect(f.isDefault, false);
      expect(f.deletedAt, 0);
    });
  });
}
```

```dart
// test/models/notebook_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/notebook.dart';

void main() {
  group('Notebook', () {
    test('creates with defaults', () {
      final nb = Notebook(id: 0, name: 'Test', folderId: 1);
      expect(nb.color, '#9E9E9E');
      expect(nb.orderIndex, 0);
      expect(nb.isDefault, false);
      expect(nb.deletedAt, 0);
    });
  });
}
```

```dart
// test/models/category_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/category.dart';

void main() {
  group('Category', () {
    test('creates correctly', () {
      final c = Category(id: 1, name: 'Work', color: '#FDD835');
      expect(c.orderIndex, 0);
    });
  });
}
```

```dart
// test/models/todo_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/todo.dart';

void main() {
  group('RepeatType', () {
    test('fromValue returns correct type', () {
      expect(RepeatType.fromValue(0), RepeatType.none);
      expect(RepeatType.fromValue(1), RepeatType.daily);
      expect(RepeatType.fromValue(2), RepeatType.weekly);
      expect(RepeatType.fromValue(3), RepeatType.monthly);
      expect(RepeatType.fromValue(4), RepeatType.yearly);
      expect(RepeatType.fromValue(99), RepeatType.none);
    });
  });

  group('Todo', () {
    test('creates with defaults', () {
      final t = Todo(id: 0, createdAt: 1000, updatedAt: 1000);
      expect(t.title, '');
      expect(t.memo, '');
      expect(t.isCompleted, false);
      expect(t.isImportant, false);
      expect(t.remindAt, 0);
      expect(t.repeatType, RepeatType.none);
      expect(t.folderId, isNull);
      expect(t.deletedAt, 0);
    });

    test('newTodo() sets timestamps', () {
      final now = DateTime.now().millisecondsSinceEpoch;
      final t = Todo.newTodo(now: now);
      expect(t.id, 0);
      expect(t.createdAt, now);
    });

    test('copyWith preserves unmodified fields', () {
      final t = Todo(id: 5, title: 'Buy', createdAt: 100, updatedAt: 200, isImportant: true);
      final t2 = t.copyWith(title: 'Sell');
      expect(t2.id, 5);
      expect(t2.title, 'Sell');
      expect(t2.isImportant, true);
    });
  });
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd code/hwnote_flutter
flutter test test/models/
```

Expected: FAIL — model files not found.

- [ ] **Step 3: 实现全部业务实体**

```dart
// lib/models/note.dart
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

  const Note({
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
  }) : content = content ?? const NoteContent(blocks: [], handwriting: []);

  factory Note.newNote({int? now}) {
    final ts = now ?? DateTime.now().millisecondsSinceEpoch;
    return Note(id: 0, createdAt: ts, updatedAt: ts);
  }

  Note copyWith({
    int? id, String? title, String? plainText, bool? isFavorite,
    int? createdAt, int? updatedAt, NoteContent? content, int? categoryId,
    bool setCategoryIdNull = false, int? deletedAt, int? notebookId,
    bool setNotebookIdNull = false, String? background,
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
  );
}
```

```dart
// lib/models/folder.dart

class Folder {
  final int id;
  final String name;
  final int orderIndex;
  final bool isDefault;
  final int deletedAt;

  const Folder({
    required this.id,
    required this.name,
    this.orderIndex = 0,
    this.isDefault = false,
    this.deletedAt = 0,
  });
}
```

```dart
// lib/models/notebook.dart

class Notebook {
  final int id;
  final String name;
  final int folderId;
  final String color;
  final int orderIndex;
  final bool isDefault;
  final int deletedAt;

  const Notebook({
    required this.id,
    required this.name,
    required this.folderId,
    this.color = '#9E9E9E',
    this.orderIndex = 0,
    this.isDefault = false,
    this.deletedAt = 0,
  });
}
```

```dart
// lib/models/category.dart

class Category {
  final int id;
  final String name;
  final String color;
  final int orderIndex;

  const Category({
    required this.id,
    required this.name,
    required this.color,
    this.orderIndex = 0,
  });
}
```

```dart
// lib/models/todo.dart

enum RepeatType {
  none(0), daily(1), weekly(2), monthly(3), yearly(4);

  final int value;
  const RepeatType(this.value);

  static RepeatType fromValue(int v) =>
      RepeatType.values.firstWhere((e) => e.value == v, orElse: () => none);
}

class Todo {
  final int id;
  final String title;
  final String memo;
  final bool isCompleted;
  final bool isImportant;
  final int remindAt;
  final RepeatType repeatType;
  final int? folderId;
  final int deletedAt;
  final int createdAt;
  final int updatedAt;

  const Todo({
    required this.id,
    this.title = '',
    this.memo = '',
    this.isCompleted = false,
    this.isImportant = false,
    this.remindAt = 0,
    this.repeatType = RepeatType.none,
    this.folderId,
    this.deletedAt = 0,
    required this.createdAt,
    required this.updatedAt,
  });

  factory Todo.newTodo({int? now}) {
    final ts = now ?? DateTime.now().millisecondsSinceEpoch;
    return Todo(id: 0, createdAt: ts, updatedAt: ts);
  }

  Todo copyWith({
    int? id, String? title, String? memo, bool? isCompleted,
    bool? isImportant, int? remindAt, RepeatType? repeatType,
    int? folderId, bool setFolderIdNull = false,
    int? deletedAt, int? createdAt, int? updatedAt,
  }) => Todo(
    id: id ?? this.id,
    title: title ?? this.title,
    memo: memo ?? this.memo,
    isCompleted: isCompleted ?? this.isCompleted,
    isImportant: isImportant ?? this.isImportant,
    remindAt: remindAt ?? this.remindAt,
    repeatType: repeatType ?? this.repeatType,
    folderId: setFolderIdNull ? null : (folderId ?? this.folderId),
    deletedAt: deletedAt ?? this.deletedAt,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
}
```

- [ ] **Step 4: 运行全部模型测试**

```bash
cd code/hwnote_flutter
flutter test test/models/
```

Expected: All tests passed.

- [ ] **Step 5: 提交**

```bash
git add code/hwnote_flutter/lib/models/note.dart code/hwnote_flutter/lib/models/folder.dart code/hwnote_flutter/lib/models/notebook.dart code/hwnote_flutter/lib/models/category.dart code/hwnote_flutter/lib/models/todo.dart code/hwnote_flutter/test/models/note_test.dart code/hwnote_flutter/test/models/folder_test.dart code/hwnote_flutter/test/models/notebook_test.dart code/hwnote_flutter/test/models/category_test.dart code/hwnote_flutter/test/models/todo_test.dart
git commit -m "feat: 迁移 Note/Folder/Notebook/Category/Todo 业务实体到 Dart"
```

---

### Task 4: 数据库层 — DatabaseHelper

**Files:**
- Create: `code/hwnote_flutter/lib/db/database_helper.dart`
- Test: `code/hwnote_flutter/test/db/database_helper_test.dart`

- [ ] **Step 1: 写 DatabaseHelper 测试**

测试使用 `sqflite_common_ffi` 在桌面环境运行内存数据库，不需要 Android 模拟器。

```dart
// test/db/database_helper_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  group('DatabaseHelper', () {
    late DatabaseHelper helper;

    setUp(() async {
      helper = DatabaseHelper(inMemory: true);
      await helper.database;
    });

    tearDown(() async {
      await helper.close();
    });

    test('creates all tables', () async {
      final db = await helper.database;
      final tables = await db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY name",
      );
      final names = tables.map((r) => r['name'] as String).toList();
      expect(names, containsAll(['notes', 'folders', 'notebooks', 'categories', 'todos']));
    });

    test('seeds default folder and notebook', () async {
      final db = await helper.database;
      final folders = await db.query('folders', where: 'id = 1');
      expect(folders.length, 1);
      expect(folders[0]['is_default'], 1);

      final notebooks = await db.query('notebooks', where: 'id = 1');
      expect(notebooks.length, 1);
      expect(notebooks[0]['is_default'], 1);
      expect(notebooks[0]['folder_id'], 1);
    });

    test('notes table has all expected columns', () async {
      final db = await helper.database;
      final now = DateTime.now().millisecondsSinceEpoch;
      final id = await db.insert('notes', {
        'title': 'Test',
        'plain_text': 'test',
        'content_json': '{}',
        'is_favorite': 0,
        'created_at': now,
        'updated_at': now,
        'deleted_at': 0,
        'notebook_id': 1,
        'background': 'plain',
      });
      expect(id, greaterThan(0));
    });

    test('todos table has all expected columns', () async {
      final db = await helper.database;
      final now = DateTime.now().millisecondsSinceEpoch;
      final id = await db.insert('todos', {
        'title': 'Test todo',
        'memo': '',
        'is_completed': 0,
        'is_important': 0,
        'remind_at': 0,
        'repeat_type': 0,
        'deleted_at': 0,
        'created_at': now,
        'updated_at': now,
      });
      expect(id, greaterThan(0));
    });
  });
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd code/hwnote_flutter
flutter test test/db/database_helper_test.dart
```

Expected: FAIL — `package:hwnote/db/database_helper.dart` not found.

- [ ] **Step 3: 实现 DatabaseHelper**

```dart
// lib/db/database_helper.dart
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

class DatabaseHelper {
  static const _dbName = 'hwnote.db';
  static const _dbVersion = 5;

  final bool inMemory;
  Database? _database;

  DatabaseHelper({this.inMemory = false});

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _open();
    return _database!;
  }

  Future<void> close() async {
    await _database?.close();
    _database = null;
  }

  Future<Database> _open() async {
    final path = inMemory ? inMemoryDatabasePath : join(await getDatabasesPath(), _dbName);
    return openDatabase(
      path,
      version: _dbVersion,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
  }

  Future<void> _onCreate(Database db, int version) async {
    await db.execute(_sqlCreateNotes);
    await db.execute(_sqlIndexUpdated);
    await db.execute(_sqlIndexFavorite);
    await db.execute(_sqlIndexCategory);
    await db.execute(_sqlIndexDeleted);
    await db.execute(_sqlIndexNotebook);
    await db.execute(_sqlCreateCategories);
    await _applyV3Tables(db);
    await _seedDefaults(db, allNotesExist: false);
    await db.execute(_sqlCreateTodos);
    await db.execute(_sqlIndexTodosRemind);
    await db.execute(_sqlIndexTodosDeleted);
  }

  Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    if (oldVersion < 2) {
      await db.execute('ALTER TABLE notes ADD COLUMN category_id INTEGER');
      await db.execute('ALTER TABLE notes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0');
      await db.execute(_sqlCreateCategories);
      await db.execute(_sqlIndexCategory);
      await db.execute(_sqlIndexDeleted);
    }
    if (oldVersion < 3) {
      await db.transaction((txn) async {
        await _applyV3Tables(txn);
        await txn.execute('ALTER TABLE notes ADD COLUMN notebook_id INTEGER');
        await txn.execute(_sqlIndexNotebook);
        await _seedDefaults(txn, allNotesExist: true);
      });
    }
    if (oldVersion < 4) {
      await db.execute("ALTER TABLE notes ADD COLUMN background TEXT NOT NULL DEFAULT 'plain'");
    }
    if (oldVersion < 5) {
      await db.execute(_sqlCreateTodos);
      await db.execute(_sqlIndexTodosRemind);
      await db.execute(_sqlIndexTodosDeleted);
    }
  }

  Future<void> _applyV3Tables(DatabaseExecutor db) async {
    await db.execute(_sqlCreateFolders);
    await db.execute(_sqlIndexFolderDeleted);
    await db.execute(_sqlCreateNotebooks);
    await db.execute(_sqlIndexNotebookFolder);
    await db.execute(_sqlIndexNotebookDeleted);
  }

  Future<void> _seedDefaults(DatabaseExecutor db, {required bool allNotesExist}) async {
    await db.insert('folders', {
      'id': 1, 'name': '默认', 'order_index': 0, 'is_default': 1, 'deleted_at': 0,
    });
    await db.insert('notebooks', {
      'id': 1, 'name': '默认', 'folder_id': 1, 'color': '#9E9E9E',
      'order_index': 0, 'is_default': 1, 'deleted_at': 0,
    });
    if (allNotesExist) {
      await db.execute('UPDATE notes SET notebook_id = 1');
    }
  }

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
      background TEXT NOT NULL DEFAULT 'plain'
    )
  ''';

  static const _sqlCreateCategories = '''
    CREATE TABLE categories (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      color TEXT NOT NULL,
      order_index INTEGER NOT NULL DEFAULT 0
    )
  ''';

  static const _sqlCreateFolders = '''
    CREATE TABLE folders (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      order_index INTEGER NOT NULL DEFAULT 0,
      is_default INTEGER NOT NULL DEFAULT 0,
      deleted_at INTEGER NOT NULL DEFAULT 0
    )
  ''';

  static const _sqlCreateNotebooks = '''
    CREATE TABLE notebooks (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      folder_id INTEGER NOT NULL,
      color TEXT NOT NULL DEFAULT '#9E9E9E',
      order_index INTEGER NOT NULL DEFAULT 0,
      is_default INTEGER NOT NULL DEFAULT 0,
      deleted_at INTEGER NOT NULL DEFAULT 0
    )
  ''';

  static const _sqlCreateTodos = '''
    CREATE TABLE todos (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      title TEXT NOT NULL DEFAULT '',
      memo TEXT NOT NULL DEFAULT '',
      is_completed INTEGER NOT NULL DEFAULT 0,
      is_important INTEGER NOT NULL DEFAULT 0,
      remind_at INTEGER NOT NULL DEFAULT 0,
      repeat_type INTEGER NOT NULL DEFAULT 0,
      folder_id INTEGER,
      deleted_at INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    )
  ''';

  static const _sqlIndexUpdated = 'CREATE INDEX idx_notes_updated_at ON notes(updated_at DESC)';
  static const _sqlIndexFavorite = 'CREATE INDEX idx_notes_favorite ON notes(is_favorite)';
  static const _sqlIndexCategory = 'CREATE INDEX idx_notes_category ON notes(category_id)';
  static const _sqlIndexDeleted = 'CREATE INDEX idx_notes_deleted ON notes(deleted_at)';
  static const _sqlIndexNotebook = 'CREATE INDEX idx_notes_notebook ON notes(notebook_id)';
  static const _sqlIndexFolderDeleted = 'CREATE INDEX idx_folders_deleted ON folders(deleted_at)';
  static const _sqlIndexNotebookFolder = 'CREATE INDEX idx_notebooks_folder ON notebooks(folder_id)';
  static const _sqlIndexNotebookDeleted = 'CREATE INDEX idx_notebooks_deleted ON notebooks(deleted_at)';
  static const _sqlIndexTodosRemind = 'CREATE INDEX idx_todos_remind_at ON todos(remind_at)';
  static const _sqlIndexTodosDeleted = 'CREATE INDEX idx_todos_deleted_at ON todos(deleted_at)';
}
```

- [ ] **Step 4: 运行 DB 测试**

```bash
cd code/hwnote_flutter
flutter test test/db/database_helper_test.dart
```

Expected: All tests passed.

- [ ] **Step 5: 提交**

```bash
git add code/hwnote_flutter/lib/db/database_helper.dart code/hwnote_flutter/test/db/database_helper_test.dart
git commit -m "feat: 迁移数据库层到 sqflite DatabaseHelper"
```

---

### Task 5: NoteRepository

**Files:**
- Create: `code/hwnote_flutter/lib/repositories/note_repository.dart`
- Test: `code/hwnote_flutter/test/repositories/note_repository_test.dart`

- [ ] **Step 1: 写 NoteRepository 测试**

```dart
// test/repositories/note_repository_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';
import 'package:hwnote/repositories/note_repository.dart';
import 'package:hwnote/models/note.dart';
import 'package:hwnote/models/note_content.dart';
import 'package:hwnote/models/block.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late DatabaseHelper dbHelper;
  late NoteRepository repo;

  setUp(() async {
    dbHelper = DatabaseHelper(inMemory: true);
    await dbHelper.database;
    repo = NoteRepository(dbHelper);
  });

  tearDown(() async {
    await dbHelper.close();
  });

  group('NoteRepository', () {
    test('save inserts new note and returns id', () async {
      final note = Note.newNote();
      final id = await repo.save(note.copyWith(title: 'Test'));
      expect(id, greaterThan(0));
    });

    test('save updates existing note', () async {
      final note = Note.newNote();
      final id = await repo.save(note.copyWith(title: 'V1'));
      await repo.save(Note(id: id, title: 'V2', createdAt: note.createdAt, updatedAt: note.updatedAt));
      final loaded = await repo.get(id);
      expect(loaded, isNotNull);
      expect(loaded!.title, 'V2');
    });

    test('list returns non-deleted notes', () async {
      await repo.save(Note.newNote().copyWith(title: 'A'));
      await repo.save(Note.newNote().copyWith(title: 'B'));
      final notes = await repo.list();
      expect(notes.length, 2);
    });

    test('softDelete marks note as deleted', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'ToDelete'));
      await repo.softDelete(id);
      final all = await repo.list();
      expect(all, isEmpty);
      final deleted = await repo.list(filter: NoteListFilter.deleted);
      expect(deleted.length, 1);
    });

    test('restore brings back deleted note', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'Restore'));
      await repo.softDelete(id);
      await repo.restore(id);
      final notes = await repo.list();
      expect(notes.length, 1);
    });

    test('deletePermanently removes row', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'Gone'));
      await repo.deletePermanently(id);
      expect(await repo.get(id), isNull);
    });

    test('setFavorite toggles favorite', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'Fav'));
      await repo.setFavorite(id, true);
      final loaded = await repo.get(id);
      expect(loaded!.isFavorite, true);
    });

    test('list with search query', () async {
      await repo.save(Note.newNote().copyWith(
        title: 'Shopping',
        content: NoteContent(
          blocks: [TextBlock(id: '1', text: 'Buy apples')],
          handwriting: [],
        ),
      ));
      await repo.save(Note.newNote().copyWith(title: 'Work'));
      final results = await repo.list(query: 'apple');
      expect(results.length, 1);
    });

    test('count returns correct number', () async {
      await repo.save(Note.newNote().copyWith(title: 'A'));
      await repo.save(Note.newNote().copyWith(title: 'B'));
      expect(await repo.count(), 2);
    });

    test('moveNoteToNotebook updates notebook_id', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'Move'));
      await repo.moveNoteToNotebook(id, 1);
      final loaded = await repo.get(id);
      expect(loaded!.notebookId, 1);
    });

    test('purgeExpired removes old soft-deleted notes', () async {
      final id = await repo.save(Note.newNote().copyWith(title: 'Old'));
      await repo.softDelete(id);
      // 模拟 31 天前删除
      final db = await dbHelper.database;
      final longAgo = DateTime.now().millisecondsSinceEpoch - 31 * 24 * 60 * 60 * 1000;
      await db.update('notes', {'deleted_at': longAgo}, where: 'id = ?', whereArgs: [id]);
      final purged = await repo.purgeExpired();
      expect(purged, 1);
      expect(await repo.get(id), isNull);
    });
  });
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd code/hwnote_flutter
flutter test test/repositories/note_repository_test.dart
```

Expected: FAIL — `package:hwnote/repositories/note_repository.dart` not found.

- [ ] **Step 3: 实现 NoteRepository**

```dart
// lib/repositories/note_repository.dart
import 'package:sqflite/sqflite.dart';
import '../db/database_helper.dart';
import '../models/note.dart';
import '../models/note_content.dart';

enum NoteSortBy { updatedDesc, createdDesc }

sealed class NoteListFilter {
  static const all = _AllFilter();
  static const uncategorized = _UncategorizedFilter();
  static const favorite = _FavoriteFilter();
  static const deleted = _DeletedFilter();
  static NoteListFilter folder(int folderId) => _FolderFilter(folderId);
  static NoteListFilter notebook(int notebookId) => _NotebookFilter(notebookId);
}

class _AllFilter extends NoteListFilter { const _AllFilter(); }
class _UncategorizedFilter extends NoteListFilter { const _UncategorizedFilter(); }
class _FavoriteFilter extends NoteListFilter { const _FavoriteFilter(); }
class _DeletedFilter extends NoteListFilter { const _DeletedFilter(); }
class _FolderFilter extends NoteListFilter {
  final int folderId;
  const _FolderFilter(this.folderId);
}
class _NotebookFilter extends NoteListFilter {
  final int notebookId;
  const _NotebookFilter(this.notebookId);
}

class NoteRepository {
  final DatabaseHelper _dbHelper;

  NoteRepository(this._dbHelper);

  Future<List<Note>> list({
    NoteListFilter filter = const _AllFilter(),
    NoteSortBy sortBy = NoteSortBy.updatedDesc,
    String? query,
  }) async {
    final db = await _dbHelper.database;
    final orderBy = switch (sortBy) {
      NoteSortBy.updatedDesc => 'updated_at DESC',
      NoteSortBy.createdDesc => 'created_at DESC',
    };
    final where = <String>[];
    final args = <String>[];
    switch (filter) {
      case _AllFilter(): where.add('deleted_at = 0');
      case _UncategorizedFilter(): where.add('deleted_at = 0 AND notebook_id IS NULL');
      case _FavoriteFilter(): where.add('deleted_at = 0 AND is_favorite = 1');
      case _DeletedFilter(): where.add('deleted_at != 0');
      case _FolderFilter(:final folderId):
        where.add('deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)');
        args.add(folderId.toString());
      case _NotebookFilter(:final notebookId):
        where.add('deleted_at = 0 AND notebook_id = ?');
        args.add(notebookId.toString());
    }
    if (query != null && query.isNotEmpty) {
      final like = '%$query%';
      where.add('(title LIKE ? OR plain_text LIKE ?)');
      args.addAll([like, like]);
    }
    final rows = await db.query('notes',
      where: where.join(' AND '),
      whereArgs: args,
      orderBy: orderBy,
    );
    return rows.map(_rowToNote).toList();
  }

  Future<Note?> get(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.query('notes', where: 'id = ?', whereArgs: [id]);
    if (rows.isEmpty) return null;
    return _rowToNote(rows.first);
  }

  Future<int> save(Note note) async {
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final values = <String, Object?>{
      'title': note.title,
      'plain_text': note.content.toPlainText(),
      'content_json': note.content.toJson(),
      'is_favorite': note.isFavorite ? 1 : 0,
      'updated_at': now,
      'category_id': note.categoryId,
      'deleted_at': note.deletedAt,
      'notebook_id': note.notebookId,
      'background': note.background,
    };
    if (note.id == 0) {
      values['created_at'] = note.createdAt > 0 ? note.createdAt : now;
      return await db.insert('notes', values);
    } else {
      await db.update('notes', values, where: 'id = ?', whereArgs: [note.id]);
      return note.id;
    }
  }

  Future<void> softDelete(int id) async {
    final db = await _dbHelper.database;
    await db.update('notes',
      {'deleted_at': DateTime.now().millisecondsSinceEpoch},
      where: 'id = ?', whereArgs: [id],
    );
  }

  Future<void> softDeleteBatch(List<int> ids) async {
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.transaction((txn) async {
      for (final id in ids) {
        await txn.update('notes', {'deleted_at': now}, where: 'id = ?', whereArgs: [id]);
      }
    });
  }

  Future<void> restore(int id) async {
    final db = await _dbHelper.database;
    await db.update('notes', {'deleted_at': 0}, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> deletePermanently(int id) async {
    final db = await _dbHelper.database;
    await db.delete('notes', where: 'id = ?', whereArgs: [id]);
  }

  Future<int> count({NoteListFilter filter = const _AllFilter()}) async {
    final db = await _dbHelper.database;
    final where = <String>[];
    final args = <String>[];
    switch (filter) {
      case _AllFilter(): where.add('deleted_at = 0');
      case _UncategorizedFilter(): where.add('deleted_at = 0 AND notebook_id IS NULL');
      case _FavoriteFilter(): where.add('deleted_at = 0 AND is_favorite = 1');
      case _DeletedFilter(): where.add('deleted_at != 0');
      case _FolderFilter(:final folderId):
        where.add('deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)');
        args.add(folderId.toString());
      case _NotebookFilter(:final notebookId):
        where.add('deleted_at = 0 AND notebook_id = ?');
        args.add(notebookId.toString());
    }
    final result = await db.rawQuery(
      'SELECT COUNT(*) FROM notes WHERE ${where.join(' AND ')}', args,
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<void> setFavorite(int id, bool favorite) async {
    final db = await _dbHelper.database;
    await db.update('notes', {
      'is_favorite': favorite ? 1 : 0,
      'updated_at': DateTime.now().millisecondsSinceEpoch,
    }, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> moveNoteToNotebook(int noteId, int? targetNotebookId) async {
    final db = await _dbHelper.database;
    await db.update('notes', {
      'notebook_id': targetNotebookId,
      'updated_at': DateTime.now().millisecondsSinceEpoch,
    }, where: 'id = ?', whereArgs: [noteId]);
  }

  Future<int> purgeExpired({
    int? now,
    int ttlMs = 30 * 24 * 60 * 60 * 1000,
  }) async {
    final db = await _dbHelper.database;
    final cutoff = (now ?? DateTime.now().millisecondsSinceEpoch) - ttlMs;
    return db.delete('notes',
      where: 'deleted_at > 0 AND deleted_at < ?', whereArgs: [cutoff]);
  }

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
    );
  }
}
```

- [ ] **Step 4: 运行 NoteRepository 测试**

```bash
cd code/hwnote_flutter
flutter test test/repositories/note_repository_test.dart
```

Expected: All tests passed.

- [ ] **Step 5: 提交**

```bash
git add code/hwnote_flutter/lib/repositories/note_repository.dart code/hwnote_flutter/test/repositories/note_repository_test.dart
git commit -m "feat: 迁移 NoteRepository 到 Dart sqflite"
```

---

### Task 6: FolderRepository

**Files:**
- Create: `code/hwnote_flutter/lib/repositories/folder_repository.dart`
- Test: `code/hwnote_flutter/test/repositories/folder_repository_test.dart`

- [ ] **Step 1: 写 FolderRepository 测试**

```dart
// test/repositories/folder_repository_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';
import 'package:hwnote/repositories/folder_repository.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late DatabaseHelper dbHelper;
  late FolderRepository repo;

  setUp(() async {
    dbHelper = DatabaseHelper(inMemory: true);
    await dbHelper.database;
    repo = FolderRepository(dbHelper);
  });

  tearDown(() async => await dbHelper.close());

  group('FolderRepository', () {
    test('list includes default folder', () async {
      final folders = await repo.list();
      expect(folders.length, 1);
      expect(folders[0].isDefault, true);
      expect(folders[0].name, '默认');
    });

    test('insert adds new folder', () async {
      final id = await repo.insert('Work');
      expect(id, greaterThan(1));
      final folders = await repo.list();
      expect(folders.length, 2);
    });

    test('get returns folder by id', () async {
      final id = await repo.insert('Personal');
      final folder = await repo.get(id);
      expect(folder, isNotNull);
      expect(folder!.name, 'Personal');
    });

    test('rename changes folder name', () async {
      final id = await repo.insert('Old');
      await repo.rename(id, 'New');
      final folder = await repo.get(id);
      expect(folder!.name, 'New');
    });

    test('softDelete marks folder and its notebooks/notes as deleted', () async {
      final id = await repo.insert('ToDelete');
      await repo.softDelete(id);
      final folders = await repo.list();
      expect(folders.every((f) => f.id != id), true);
    });

    test('reorder updates order_index', () async {
      final id1 = await repo.insert('A');
      final id2 = await repo.insert('B');
      await repo.reorder([id2, id1]);
      final folders = await repo.list();
      final nonDefault = folders.where((f) => !f.isDefault).toList();
      expect(nonDefault[0].id, id2);
    });
  });
}
```

- [ ] **Step 2: 实现 FolderRepository**

```dart
// lib/repositories/folder_repository.dart
import 'package:sqflite/sqflite.dart';
import '../db/database_helper.dart';
import '../models/folder.dart';

class FolderRepository {
  final DatabaseHelper _dbHelper;

  FolderRepository(this._dbHelper);

  Future<List<Folder>> list() async {
    final db = await _dbHelper.database;
    final rows = await db.rawQuery(
      'SELECT id, name, order_index, is_default, deleted_at FROM folders WHERE deleted_at = 0 ORDER BY is_default DESC, order_index ASC, id ASC',
    );
    return rows.map(_rowToFolder).toList();
  }

  Future<Folder?> get(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.rawQuery(
      'SELECT id, name, order_index, is_default, deleted_at FROM folders WHERE id = ? AND deleted_at = 0',
      [id],
    );
    if (rows.isEmpty) return null;
    return _rowToFolder(rows.first);
  }

  Future<int> insert(String name) async {
    final db = await _dbHelper.database;
    final nextIndex = await _nextOrderIndex(db);
    return db.insert('folders', {
      'name': name, 'order_index': nextIndex, 'is_default': 0, 'deleted_at': 0,
    });
  }

  Future<void> rename(int id, String name) async {
    assert(id != 1, '默认文件夹不可改名');
    final db = await _dbHelper.database;
    await db.update('folders', {'name': name}, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> softDelete(int id) async {
    assert(id != 1, '默认文件夹不可删除');
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.transaction((txn) async {
      await txn.rawUpdate(
        'UPDATE notes SET deleted_at = ? WHERE deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)',
        [now, id],
      );
      await txn.update('notebooks', {'deleted_at': now},
        where: 'folder_id = ? AND deleted_at = 0', whereArgs: [id]);
      await txn.update('folders', {'deleted_at': now},
        where: 'id = ?', whereArgs: [id]);
    });
  }

  Future<void> reorder(List<int> orderedIds) async {
    final db = await _dbHelper.database;
    await db.transaction((txn) async {
      for (var i = 0; i < orderedIds.length; i++) {
        final id = orderedIds[i];
        if (id == 1) continue;
        await txn.update('folders', {'order_index': i},
          where: 'id = ?', whereArgs: [id]);
      }
    });
  }

  Future<int> _nextOrderIndex(Database db) async {
    final result = await db.rawQuery(
      'SELECT IFNULL(MAX(order_index), -1) + 1 FROM folders WHERE deleted_at = 0',
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Folder _rowToFolder(Map<String, Object?> row) => Folder(
    id: row['id'] as int,
    name: row['name'] as String,
    orderIndex: row['order_index'] as int,
    isDefault: (row['is_default'] as int) == 1,
    deletedAt: row['deleted_at'] as int? ?? 0,
  );
}
```

- [ ] **Step 3: 运行测试**

```bash
cd code/hwnote_flutter
flutter test test/repositories/folder_repository_test.dart
```

Expected: All tests passed.

- [ ] **Step 4: 提交**

```bash
git add code/hwnote_flutter/lib/repositories/folder_repository.dart code/hwnote_flutter/test/repositories/folder_repository_test.dart
git commit -m "feat: 迁移 FolderRepository 到 Dart sqflite"
```

---

### Task 7: NotebookRepository

**Files:**
- Create: `code/hwnote_flutter/lib/repositories/notebook_repository.dart`
- Test: `code/hwnote_flutter/test/repositories/notebook_repository_test.dart`

- [ ] **Step 1: 写 NotebookRepository 测试**

```dart
// test/repositories/notebook_repository_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';
import 'package:hwnote/repositories/notebook_repository.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late DatabaseHelper dbHelper;
  late NotebookRepository repo;

  setUp(() async {
    dbHelper = DatabaseHelper(inMemory: true);
    await dbHelper.database;
    repo = NotebookRepository(dbHelper);
  });

  tearDown(() async => await dbHelper.close());

  group('NotebookRepository', () {
    test('listByFolder returns default notebook for folder 1', () async {
      final notebooks = await repo.listByFolder(1);
      expect(notebooks.length, 1);
      expect(notebooks[0].isDefault, true);
    });

    test('insert adds new notebook', () async {
      final id = await repo.insert(1, 'Science', '#FF0000');
      expect(id, greaterThan(1));
    });

    test('get returns notebook by id', () async {
      final id = await repo.insert(1, 'Math', '#00FF00');
      final nb = await repo.get(id);
      expect(nb, isNotNull);
      expect(nb!.name, 'Math');
      expect(nb.color, '#00FF00');
    });

    test('rename updates name', () async {
      final id = await repo.insert(1, 'Old', '#000');
      await repo.rename(id, 'New');
      final nb = await repo.get(id);
      expect(nb!.name, 'New');
    });

    test('updateColor updates color', () async {
      final id = await repo.insert(1, 'Nb', '#000');
      await repo.updateColor(id, '#FFF');
      final nb = await repo.get(id);
      expect(nb!.color, '#FFF');
    });

    test('softDelete marks notebook and its notes as deleted', () async {
      final id = await repo.insert(1, 'ToDelete', '#000');
      await repo.softDelete(id);
      final nb = await repo.get(id);
      expect(nb, isNull);
    });

    test('move changes folder_id', () async {
      final db = await dbHelper.database;
      await db.insert('folders', {
        'name': 'Folder2', 'order_index': 1, 'is_default': 0, 'deleted_at': 0,
      });
      final nbId = await repo.insert(1, 'Movable', '#000');
      await repo.move(nbId, 2);
      final nb = await repo.get(nbId);
      expect(nb!.folderId, 2);
    });
  });
}
```

- [ ] **Step 2: 实现 NotebookRepository**

```dart
// lib/repositories/notebook_repository.dart
import 'package:sqflite/sqflite.dart';
import '../db/database_helper.dart';
import '../models/notebook.dart';

class NotebookRepository {
  final DatabaseHelper _dbHelper;

  NotebookRepository(this._dbHelper);

  Future<List<Notebook>> listByFolder(int folderId) async {
    final db = await _dbHelper.database;
    final rows = await db.rawQuery(
      'SELECT id, folder_id, name, color, order_index, is_default, deleted_at FROM notebooks WHERE folder_id = ? AND deleted_at = 0 ORDER BY is_default DESC, order_index ASC, id ASC',
      [folderId],
    );
    return rows.map(_rowToNotebook).toList();
  }

  Future<Notebook?> get(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.rawQuery(
      'SELECT id, folder_id, name, color, order_index, is_default, deleted_at FROM notebooks WHERE id = ? AND deleted_at = 0',
      [id],
    );
    if (rows.isEmpty) return null;
    return _rowToNotebook(rows.first);
  }

  Future<int> insert(int folderId, String name, String color) async {
    final db = await _dbHelper.database;
    final nextIndex = await _nextOrderIndex(db, folderId);
    return db.insert('notebooks', {
      'folder_id': folderId, 'name': name, 'color': color,
      'order_index': nextIndex, 'is_default': 0, 'deleted_at': 0,
    });
  }

  Future<void> rename(int id, String name) async {
    final db = await _dbHelper.database;
    await db.update('notebooks', {'name': name}, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> updateColor(int id, String color) async {
    final db = await _dbHelper.database;
    await db.update('notebooks', {'color': color}, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> softDelete(int id) async {
    assert(id != 1, '默认笔记本不可删除');
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.transaction((txn) async {
      await txn.rawUpdate(
        'UPDATE notes SET deleted_at = ? WHERE deleted_at = 0 AND notebook_id = ?',
        [now, id],
      );
      await txn.update('notebooks', {'deleted_at': now}, where: 'id = ?', whereArgs: [id]);
    });
  }

  Future<void> move(int id, int targetFolderId) async {
    assert(id != 1, '默认笔记本不可跨文件夹移动');
    final db = await _dbHelper.database;
    final nextIndex = await _nextOrderIndex(db, targetFolderId);
    await db.update('notebooks', {
      'folder_id': targetFolderId, 'order_index': nextIndex,
    }, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> reorderInFolder(int folderId, List<int> orderedIds) async {
    final db = await _dbHelper.database;
    await db.transaction((txn) async {
      for (var i = 0; i < orderedIds.length; i++) {
        final id = orderedIds[i];
        if (id == 1 && folderId == 1) continue;
        await txn.update('notebooks', {'order_index': i},
          where: 'id = ? AND folder_id = ?', whereArgs: [id, folderId]);
      }
    });
  }

  Future<int> _nextOrderIndex(Database db, int folderId) async {
    final result = await db.rawQuery(
      'SELECT IFNULL(MAX(order_index), -1) + 1 FROM notebooks WHERE deleted_at = 0 AND folder_id = ?',
      [folderId],
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Notebook _rowToNotebook(Map<String, Object?> row) => Notebook(
    id: row['id'] as int,
    folderId: row['folder_id'] as int,
    name: row['name'] as String,
    color: row['color'] as String,
    orderIndex: row['order_index'] as int,
    isDefault: (row['is_default'] as int) == 1,
    deletedAt: row['deleted_at'] as int? ?? 0,
  );
}
```

- [ ] **Step 3: 运行测试**

```bash
cd code/hwnote_flutter
flutter test test/repositories/notebook_repository_test.dart
```

Expected: All tests passed.

- [ ] **Step 4: 提交**

```bash
git add code/hwnote_flutter/lib/repositories/notebook_repository.dart code/hwnote_flutter/test/repositories/notebook_repository_test.dart
git commit -m "feat: 迁移 NotebookRepository 到 Dart sqflite"
```

---

### Task 8: CategoryRepository

**Files:**
- Create: `code/hwnote_flutter/lib/repositories/category_repository.dart`
- Test: `code/hwnote_flutter/test/repositories/category_repository_test.dart`

- [ ] **Step 1: 写 CategoryRepository 测试**

```dart
// test/repositories/category_repository_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';
import 'package:hwnote/repositories/category_repository.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late DatabaseHelper dbHelper;
  late CategoryRepository repo;

  setUp(() async {
    dbHelper = DatabaseHelper(inMemory: true);
    await dbHelper.database;
    repo = CategoryRepository(dbHelper);
  });

  tearDown(() async => await dbHelper.close());

  group('CategoryRepository', () {
    test('list is initially empty', () async {
      final list = await repo.list();
      expect(list, isEmpty);
    });

    test('insert adds category', () async {
      final id = await repo.insert('Work', '#FDD835');
      expect(id, greaterThan(0));
      final list = await repo.list();
      expect(list.length, 1);
      expect(list[0].name, 'Work');
      expect(list[0].color, '#FDD835');
    });

    test('get returns category by id', () async {
      final id = await repo.insert('Personal', '#FF0000');
      final cat = await repo.get(id);
      expect(cat, isNotNull);
      expect(cat!.name, 'Personal');
    });

    test('update changes name and color', () async {
      final id = await repo.insert('Old', '#000');
      await repo.update(id, 'New', '#FFF');
      final cat = await repo.get(id);
      expect(cat!.name, 'New');
      expect(cat.color, '#FFF');
    });

    test('delete removes category', () async {
      final id = await repo.insert('Gone', '#000');
      await repo.delete(id);
      expect(await repo.get(id), isNull);
    });

    test('order_index auto-increments', () async {
      await repo.insert('A', '#000');
      await repo.insert('B', '#000');
      final list = await repo.list();
      expect(list[0].orderIndex, 0);
      expect(list[1].orderIndex, 1);
    });
  });
}
```

- [ ] **Step 2: 实现 CategoryRepository**

```dart
// lib/repositories/category_repository.dart
import 'package:sqflite/sqflite.dart';
import '../db/database_helper.dart';
import '../models/category.dart';

class CategoryRepository {
  final DatabaseHelper _dbHelper;

  CategoryRepository(this._dbHelper);

  Future<List<Category>> list() async {
    final db = await _dbHelper.database;
    final rows = await db.query('categories', orderBy: 'order_index ASC, id ASC');
    return rows.map(_rowToCategory).toList();
  }

  Future<Category?> get(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.query('categories', where: 'id = ?', whereArgs: [id]);
    if (rows.isEmpty) return null;
    return _rowToCategory(rows.first);
  }

  Future<int> insert(String name, String color) async {
    final db = await _dbHelper.database;
    final maxOrder = Sqflite.firstIntValue(
      await db.rawQuery('SELECT COALESCE(MAX(order_index), -1) FROM categories'),
    ) ?? -1;
    return db.insert('categories', {
      'name': name, 'color': color, 'order_index': maxOrder + 1,
    });
  }

  Future<void> update(int id, String name, String color) async {
    final db = await _dbHelper.database;
    await db.update('categories', {'name': name, 'color': color},
      where: 'id = ?', whereArgs: [id]);
  }

  Future<void> delete(int id) async {
    final db = await _dbHelper.database;
    await db.transaction((txn) async {
      await txn.rawUpdate('UPDATE notes SET category_id = NULL WHERE category_id = ?', [id]);
      await txn.delete('categories', where: 'id = ?', whereArgs: [id]);
    });
  }

  Future<void> reorder(List<int> orderedIds) async {
    final db = await _dbHelper.database;
    await db.transaction((txn) async {
      for (var i = 0; i < orderedIds.length; i++) {
        await txn.update('categories', {'order_index': i},
          where: 'id = ?', whereArgs: [orderedIds[i]]);
      }
    });
  }

  Category _rowToCategory(Map<String, Object?> row) => Category(
    id: row['id'] as int,
    name: row['name'] as String,
    color: row['color'] as String,
    orderIndex: row['order_index'] as int? ?? 0,
  );
}
```

- [ ] **Step 3: 运行测试**

```bash
cd code/hwnote_flutter
flutter test test/repositories/category_repository_test.dart
```

Expected: All tests passed.

- [ ] **Step 4: 提交**

```bash
git add code/hwnote_flutter/lib/repositories/category_repository.dart code/hwnote_flutter/test/repositories/category_repository_test.dart
git commit -m "feat: 迁移 CategoryRepository 到 Dart sqflite"
```

---

### Task 9: TodoRepository

**Files:**
- Create: `code/hwnote_flutter/lib/repositories/todo_repository.dart`
- Test: `code/hwnote_flutter/test/repositories/todo_repository_test.dart`

- [ ] **Step 1: 写 TodoRepository 测试**

```dart
// test/repositories/todo_repository_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:hwnote/db/database_helper.dart';
import 'package:hwnote/repositories/todo_repository.dart';
import 'package:hwnote/models/todo.dart';

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late DatabaseHelper dbHelper;
  late TodoRepository repo;

  setUp(() async {
    dbHelper = DatabaseHelper(inMemory: true);
    await dbHelper.database;
    repo = TodoRepository(dbHelper);
  });

  tearDown(() async => await dbHelper.close());

  group('TodoRepository', () {
    test('insert and getById', () async {
      final todo = Todo.newTodo().copyWith(title: 'Buy milk');
      final id = await repo.insert(todo);
      expect(id, greaterThan(0));
      final loaded = await repo.getById(id);
      expect(loaded, isNotNull);
      expect(loaded!.title, 'Buy milk');
    });

    test('update changes fields', () async {
      final id = await repo.insert(Todo.newTodo().copyWith(title: 'Old'));
      final loaded = (await repo.getById(id))!;
      await repo.update(loaded.copyWith(title: 'New'));
      final updated = await repo.getById(id);
      expect(updated!.title, 'New');
    });

    test('list returns non-deleted todos', () async {
      await repo.insert(Todo.newTodo().copyWith(title: 'A'));
      await repo.insert(Todo.newTodo().copyWith(title: 'B'));
      final list = await repo.list();
      expect(list.length, 2);
    });

    test('softDelete marks as deleted', () async {
      final id = await repo.insert(Todo.newTodo().copyWith(title: 'Del'));
      await repo.softDelete(id);
      final list = await repo.list();
      expect(list, isEmpty);
    });

    test('restore brings back deleted todo', () async {
      final id = await repo.insert(Todo.newTodo().copyWith(title: 'Restore'));
      await repo.softDelete(id);
      await repo.restore(id);
      final list = await repo.list();
      expect(list.length, 1);
    });

    test('completeTodo sets is_completed for non-repeating', () async {
      final id = await repo.insert(Todo.newTodo().copyWith(title: 'Done'));
      await repo.completeTodo(id);
      final loaded = await repo.getById(id);
      expect(loaded!.isCompleted, true);
    });

    test('completeTodo advances remindAt for repeating', () async {
      final now = DateTime.now().millisecondsSinceEpoch;
      final id = await repo.insert(Todo.newTodo().copyWith(
        title: 'Repeat',
        remindAt: now - 1000,
        repeatType: RepeatType.daily,
      ));
      await repo.completeTodo(id);
      final loaded = await repo.getById(id);
      expect(loaded!.isCompleted, false);
      expect(loaded.remindAt, greaterThan(now));
    });

    test('count returns correct number', () async {
      await repo.insert(Todo.newTodo().copyWith(title: 'A'));
      await repo.insert(Todo.newTodo().copyWith(title: 'B'));
      expect(await repo.count(), 2);
    });

    test('advanceRemindAt advances past now', () {
      final past = DateTime.now().millisecondsSinceEpoch - 86400000 * 3;
      final result = TodoRepository.advanceRemindAt(past, RepeatType.daily);
      expect(result, greaterThan(DateTime.now().millisecondsSinceEpoch));
    });

    test('purgeExpired removes old soft-deleted todos', () async {
      final id = await repo.insert(Todo.newTodo().copyWith(title: 'Old'));
      await repo.softDelete(id);
      final db = await dbHelper.database;
      final longAgo = DateTime.now().millisecondsSinceEpoch - 31 * 24 * 60 * 60 * 1000;
      await db.update('todos', {'deleted_at': longAgo}, where: 'id = ?', whereArgs: [id]);
      final purged = await repo.purgeExpired();
      expect(purged, 1);
      expect(await repo.getById(id), isNull);
    });
  });
}
```

- [ ] **Step 2: 实现 TodoRepository**

```dart
// lib/repositories/todo_repository.dart
import 'package:sqflite/sqflite.dart';
import '../db/database_helper.dart';
import '../models/todo.dart';

class TodoRepository {
  final DatabaseHelper _dbHelper;

  TodoRepository(this._dbHelper);

  Future<int> insert(Todo todo) async {
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final values = _toMap(todo);
    values['created_at'] = todo.createdAt > 0 ? todo.createdAt : now;
    values['updated_at'] = now;
    return db.insert('todos', values);
  }

  Future<void> update(Todo todo) async {
    final db = await _dbHelper.database;
    final values = _toMap(todo);
    values['updated_at'] = DateTime.now().millisecondsSinceEpoch;
    await db.update('todos', values, where: 'id = ?', whereArgs: [todo.id]);
  }

  Future<Todo?> getById(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.query('todos', where: 'id = ?', whereArgs: [id]);
    if (rows.isEmpty) return null;
    return _rowToTodo(rows.first);
  }

  Future<List<Todo>> list({
    int? folderId,
    bool includeDeleted = false,
    bool hideCompleted = false,
  }) async {
    final db = await _dbHelper.database;
    final where = <String>[];
    final args = <Object>[];
    where.add(includeDeleted ? 'deleted_at > 0' : 'deleted_at = 0');
    if (folderId != null) { where.add('folder_id = ?'); args.add(folderId); }
    if (hideCompleted) { where.add('is_completed = 0'); }
    final rows = await db.query('todos',
      where: where.join(' AND '), whereArgs: args,
      orderBy: 'remind_at ASC, created_at DESC',
    );
    return rows.map(_rowToTodo).toList();
  }

  Future<List<Todo>> listUncategorized({bool hideCompleted = false}) async {
    final db = await _dbHelper.database;
    final where = <String>['deleted_at = 0', 'folder_id IS NULL'];
    if (hideCompleted) where.add('is_completed = 0');
    final rows = await db.query('todos',
      where: where.join(' AND '), orderBy: 'remind_at ASC, created_at DESC',
    );
    return rows.map(_rowToTodo).toList();
  }

  Future<int> count({int? folderId, bool includeDeleted = false}) async {
    final db = await _dbHelper.database;
    final where = <String>[];
    final args = <Object>[];
    where.add(includeDeleted ? 'deleted_at > 0' : 'deleted_at = 0');
    if (folderId != null) { where.add('folder_id = ?'); args.add(folderId); }
    final result = await db.rawQuery(
      'SELECT COUNT(*) FROM todos WHERE ${where.join(' AND ')}', args,
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<int> countUncategorized() async {
    final db = await _dbHelper.database;
    final result = await db.rawQuery(
      'SELECT COUNT(*) FROM todos WHERE deleted_at = 0 AND folder_id IS NULL',
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<void> softDelete(int id) async {
    final db = await _dbHelper.database;
    await db.update('todos', {'deleted_at': DateTime.now().millisecondsSinceEpoch},
      where: 'id = ?', whereArgs: [id]);
  }

  Future<void> softDeleteBatch(List<int> ids) async {
    final db = await _dbHelper.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.transaction((txn) async {
      for (final id in ids) {
        await txn.update('todos', {'deleted_at': now}, where: 'id = ?', whereArgs: [id]);
      }
    });
  }

  Future<void> restore(int id) async {
    final db = await _dbHelper.database;
    await db.update('todos', {'deleted_at': 0}, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> deletePermanently(int id) async {
    final db = await _dbHelper.database;
    await db.delete('todos', where: 'id = ?', whereArgs: [id]);
  }

  Future<void> completeTodo(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.query('todos', where: 'id = ?', whereArgs: [id]);
    if (rows.isEmpty) return;
    final todo = _rowToTodo(rows.first);
    if (todo.repeatType != RepeatType.none && todo.remindAt > 0) {
      final nextRemind = advanceRemindAt(todo.remindAt, todo.repeatType);
      await db.update('todos', {
        'remind_at': nextRemind,
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      }, where: 'id = ?', whereArgs: [id]);
    } else {
      await db.update('todos', {
        'is_completed': 1,
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      }, where: 'id = ?', whereArgs: [id]);
    }
  }

  Future<void> uncompleteTodo(int id) async {
    final db = await _dbHelper.database;
    await db.update('todos', {
      'is_completed': 0,
      'updated_at': DateTime.now().millisecondsSinceEpoch,
    }, where: 'id = ?', whereArgs: [id]);
  }

  Future<List<Todo>> listPendingAlarms() async {
    final db = await _dbHelper.database;
    final rows = await db.query('todos',
      where: 'remind_at > 0 AND is_completed = 0 AND deleted_at = 0',
      orderBy: 'remind_at ASC',
    );
    return rows.map(_rowToTodo).toList();
  }

  Future<int> purgeExpired({
    int? now,
    int ttlMs = 30 * 24 * 60 * 60 * 1000,
  }) async {
    final db = await _dbHelper.database;
    final cutoff = (now ?? DateTime.now().millisecondsSinceEpoch) - ttlMs;
    return db.delete('todos',
      where: 'deleted_at > 0 AND deleted_at < ?', whereArgs: [cutoff]);
  }

  static int advanceRemindAt(int remindAt, RepeatType repeatType) {
    if (repeatType == RepeatType.none) return remindAt;
    final now = DateTime.now().millisecondsSinceEpoch;
    var dt = DateTime.fromMillisecondsSinceEpoch(remindAt);
    do {
      dt = switch (repeatType) {
        RepeatType.daily => dt.add(const Duration(days: 1)),
        RepeatType.weekly => dt.add(const Duration(days: 7)),
        RepeatType.monthly => DateTime(dt.year, dt.month + 1, dt.day, dt.hour, dt.minute, dt.second),
        RepeatType.yearly => DateTime(dt.year + 1, dt.month, dt.day, dt.hour, dt.minute, dt.second),
        RepeatType.none => dt,
      };
    } while (dt.millisecondsSinceEpoch < now);
    return dt.millisecondsSinceEpoch;
  }

  Map<String, Object?> _toMap(Todo todo) => {
    'title': todo.title,
    'memo': todo.memo,
    'is_completed': todo.isCompleted ? 1 : 0,
    'is_important': todo.isImportant ? 1 : 0,
    'remind_at': todo.remindAt,
    'repeat_type': todo.repeatType.value,
    'folder_id': todo.folderId,
    'deleted_at': todo.deletedAt,
  };

  Todo _rowToTodo(Map<String, Object?> row) => Todo(
    id: row['id'] as int,
    title: row['title'] as String? ?? '',
    memo: row['memo'] as String? ?? '',
    isCompleted: (row['is_completed'] as int) == 1,
    isImportant: (row['is_important'] as int) == 1,
    remindAt: row['remind_at'] as int? ?? 0,
    repeatType: RepeatType.fromValue(row['repeat_type'] as int? ?? 0),
    folderId: row['folder_id'] as int?,
    deletedAt: row['deleted_at'] as int? ?? 0,
    createdAt: row['created_at'] as int,
    updatedAt: row['updated_at'] as int,
  );
}
```

- [ ] **Step 3: 运行测试**

```bash
cd code/hwnote_flutter
flutter test test/repositories/todo_repository_test.dart
```

Expected: All tests passed.

- [ ] **Step 4: 提交**

```bash
git add code/hwnote_flutter/lib/repositories/todo_repository.dart code/hwnote_flutter/test/repositories/todo_repository_test.dart
git commit -m "feat: 迁移 TodoRepository 到 Dart sqflite"
```

---

### Task 10: 全量集成验证

**Files:**
- 无新文件

- [ ] **Step 1: 运行全部测试**

```bash
cd code/hwnote_flutter
flutter test
```

Expected: All tests passed（models + db + repositories 共 ~50+ 测试用例）。

- [ ] **Step 2: 静态分析**

```bash
cd code/hwnote_flutter
flutter analyze
```

Expected: No issues found.

- [ ] **Step 3: 验证 JSON 格式兼容性**

在 test/ 下已有的 NoteContent roundtrip 测试覆盖了与现有 Android 端 NoteJson.kt 相同的 JSON 格式。确认序列化格式一致：
- blocks 数组中每个 block 有 `type` / `id` 字段
- text block 有 `heading` / `text` / `spans` / `alignment` / `listType` / `indentLevel`
- spans 数组中每个 span 有 `start` / `end` / `type` / `value`
- handwriting.strokes 中每个 stroke 的 points 是 `[[x,y,t], ...]` 格式

- [ ] **Step 4: 提交（如有修复）**

如果 Step 1-3 发现问题并修复了代码，提交修复：

```bash
git add -u code/hwnote_flutter
git commit -m "fix: 修复集成验证中发现的问题"
```
