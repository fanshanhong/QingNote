# Phase 2B-4: 手写覆盖层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在编辑器上方实现透明手写覆盖层，支持 4 笔种绘制、笔画级橡皮擦除、撤销/重做，以及手写模式专属工具栏。

**Architecture:** Stack 覆盖层方案 — HandwritingOverlay（CustomPaint + Listener）覆盖在 AppFlowyEditor 上方，手写模式下拦截触摸，非手写模式下透传。HandwritingOverlayController 管理 strokes 和 undo/redo 栈。独立 HandwritingToolbar 替换 TextToolbar。

**Tech Stack:** Flutter CustomPaint + GestureDetector/Listener + ChangeNotifier

---

## File Structure

### 新建文件

| 文件 | 职责 |
|------|------|
| `lib/editor/handwriting/stroke_eraser.dart` | 纯函数：笔画级橡皮 hit-test（bbox 粗筛 + 点到线段距离细判） |
| `lib/editor/handwriting/brush_paint_factory.dart` | 纯函数：根据 BrushType/color/width 创建 Paint 对象 |
| `lib/editor/handwriting/handwriting_controller.dart` | HandwritingOverlayController：strokes + Add/Erase 撤销栈 |
| `lib/editor/handwriting/handwriting_painter.dart` | CustomPainter：绘制已完成 strokes + in-progress 轨迹 |
| `lib/editor/handwriting/handwriting_overlay.dart` | HandwritingOverlay widget：Listener 触摸处理 + RepaintBoundary + CustomPaint |
| `lib/widgets/editor/handwriting_toolbar.dart` | 手写模式底部工具栏 |
| `lib/widgets/editor/handwriting_style_picker_sheet.dart` | 笔种+颜色+粗细 BottomSheet |
| `lib/widgets/editor/brush_width_picker.dart` | 粗细选择 Popup（OverlayEntry） |
| `test/editor/handwriting/stroke_eraser_test.dart` | 橡皮 hit-test 单测 |
| `test/editor/handwriting/handwriting_controller_test.dart` | 撤销栈逻辑单测 |

### 修改文件

| 文件 | 改动 |
|------|------|
| `lib/providers/note_editor_provider.dart` | 新增 isHandwritingMode 等状态字段 + 切换方法 + saveNote 读取 controller strokes |
| `lib/pages/note_editor_page.dart` | Stack 布局 + HandwritingOverlay + 工具栏三态切换 + undo/redo 分发 |
| `lib/widgets/editor/text_toolbar.dart` | 手写按钮改为 active 色 + 调用 enterHandwritingMode |

---

### Task 1: StrokeEraser 纯函数 + 单测

**Files:**
- Create: `lib/editor/handwriting/stroke_eraser.dart`
- Create: `test/editor/handwriting/stroke_eraser_test.dart`

- [ ] **Step 1: 创建 stroke_eraser.dart**

```dart
// lib/editor/handwriting/stroke_eraser.dart
import '../../models/stroke.dart';

class StrokeEraser {
  StrokeEraser._();

  static List<Stroke> hitTest(
    double ex,
    double ey,
    double radiusPx,
    List<Stroke> strokes,
  ) {
    if (strokes.isEmpty) return const [];
    final out = <Stroke>[];
    for (final s in strokes) {
      if (s.points.isEmpty) continue;
      final threshold = radiusPx + s.width / 2.0;
      if (!_bboxIntersectsCircle(s, ex, ey, threshold)) continue;
      if (_anySegmentWithin(s, ex, ey, threshold)) out.add(s);
    }
    return out;
  }

  static bool _bboxIntersectsCircle(
    Stroke s,
    double ex,
    double ey,
    double r,
  ) {
    int minX = 0x7FFFFFFF, minY = 0x7FFFFFFF;
    int maxX = -0x7FFFFFFF, maxY = -0x7FFFFFFF;
    for (final p in s.points) {
      if (p.x < minX) minX = p.x;
      if (p.x > maxX) maxX = p.x;
      if (p.y < minY) minY = p.y;
      if (p.y > maxY) maxY = p.y;
    }
    final cx = ex.clamp(minX.toDouble(), maxX.toDouble());
    final cy = ey.clamp(minY.toDouble(), maxY.toDouble());
    final dx = ex - cx;
    final dy = ey - cy;
    return dx * dx + dy * dy <= r * r;
  }

  static bool _anySegmentWithin(
    Stroke s,
    double ex,
    double ey,
    double threshold,
  ) {
    final pts = s.points;
    if (pts.length == 1) {
      final p = pts[0];
      final dx = ex - p.x;
      final dy = ey - p.y;
      return dx * dx + dy * dy <= threshold * threshold;
    }
    for (int i = 0; i < pts.length - 1; i++) {
      final a = pts[i];
      final b = pts[i + 1];
      if (_pointToSegmentDistSq(
            ex,
            ey,
            a.x.toDouble(),
            a.y.toDouble(),
            b.x.toDouble(),
            b.y.toDouble(),
          ) <=
          threshold * threshold) {
        return true;
      }
    }
    return false;
  }

  static double _pointToSegmentDistSq(
    double px,
    double py,
    double ax,
    double ay,
    double bx,
    double by,
  ) {
    final abx = bx - ax;
    final aby = by - ay;
    final apx = px - ax;
    final apy = py - ay;
    final abLen2 = abx * abx + aby * aby;
    if (abLen2 == 0.0) {
      return apx * apx + apy * apy;
    }
    var t = (apx * abx + apy * aby) / abLen2;
    if (t < 0.0) {
      t = 0.0;
    } else if (t > 1.0) {
      t = 1.0;
    }
    final cx = ax + t * abx;
    final cy = ay + t * aby;
    final dx = px - cx;
    final dy = py - cy;
    return dx * dx + dy * dy;
  }
}
```

- [ ] **Step 2: 创建 stroke_eraser_test.dart**

```dart
// test/editor/handwriting/stroke_eraser_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:huawei_note_flutter/editor/handwriting/stroke_eraser.dart';
import 'package:huawei_note_flutter/models/stroke.dart';

void main() {
  group('StrokeEraser.hitTest', () {
    test('empty strokes returns empty', () {
      final result = StrokeEraser.hitTest(50, 50, 12, []);
      expect(result, isEmpty);
    });

    test('stroke with empty points is skipped', () {
      final s = Stroke(brush: BrushType.pen, color: '#000000', width: 3, points: []);
      final result = StrokeEraser.hitTest(50, 50, 12, [s]);
      expect(result, isEmpty);
    });

    test('single point stroke within radius is hit', () {
      final s = Stroke(
        brush: BrushType.pen,
        color: '#000000',
        width: 3,
        points: [StrokePoint(x: 50, y: 50, t: 0)],
      );
      final result = StrokeEraser.hitTest(52, 52, 12, [s]);
      expect(result, contains(s));
    });

    test('single point stroke outside radius is not hit', () {
      final s = Stroke(
        brush: BrushType.pen,
        color: '#000000',
        width: 1,
        points: [StrokePoint(x: 50, y: 50, t: 0)],
      );
      final result = StrokeEraser.hitTest(100, 100, 12, [s]);
      expect(result, isEmpty);
    });

    test('horizontal line stroke hit in middle', () {
      final s = Stroke(
        brush: BrushType.pen,
        color: '#000000',
        width: 3,
        points: [
          StrokePoint(x: 0, y: 50, t: 0),
          StrokePoint(x: 100, y: 50, t: 100),
        ],
      );
      // eraser at (50, 52) should hit (distance to line = 2, threshold = 12 + 1.5 = 13.5)
      final result = StrokeEraser.hitTest(50, 52, 12, [s]);
      expect(result, contains(s));
    });

    test('stroke far from eraser is not hit', () {
      final s = Stroke(
        brush: BrushType.pen,
        color: '#000000',
        width: 3,
        points: [
          StrokePoint(x: 0, y: 0, t: 0),
          StrokePoint(x: 10, y: 0, t: 100),
        ],
      );
      final result = StrokeEraser.hitTest(200, 200, 12, [s]);
      expect(result, isEmpty);
    });

    test('multiple strokes returns only hit ones', () {
      final hit = Stroke(
        brush: BrushType.pen,
        color: '#000000',
        width: 3,
        points: [
          StrokePoint(x: 50, y: 50, t: 0),
          StrokePoint(x: 60, y: 50, t: 100),
        ],
      );
      final miss = Stroke(
        brush: BrushType.pen,
        color: '#000000',
        width: 3,
        points: [
          StrokePoint(x: 200, y: 200, t: 0),
          StrokePoint(x: 210, y: 200, t: 100),
        ],
      );
      final result = StrokeEraser.hitTest(55, 50, 12, [hit, miss]);
      expect(result, [hit]);
    });

    test('stroke width affects threshold', () {
      final s = Stroke(
        brush: BrushType.marker,
        color: '#000000',
        width: 6,
        points: [
          StrokePoint(x: 50, y: 50, t: 0),
          StrokePoint(x: 60, y: 50, t: 100),
        ],
      );
      // distance from (55, 65) to line y=50 is 15
      // threshold = 12 + 6/2 = 15, so 15 <= 15 should hit
      final result = StrokeEraser.hitTest(55, 65, 12, [s]);
      expect(result, contains(s));
    });
  });
}
```

- [ ] **Step 3: 运行测试确认通过**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test test/editor/handwriting/stroke_eraser_test.dart`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaweiNoteFlutter/lib/editor/handwriting/stroke_eraser.dart code/HuaweiNoteFlutter/test/editor/handwriting/stroke_eraser_test.dart
git commit -m "feat(p2b4): 添加 StrokeEraser 笔画级橡皮 hit-test + 单测"
```

---

### Task 2: HandwritingOverlayController + 单测

**Files:**
- Create: `lib/editor/handwriting/handwriting_controller.dart`
- Create: `test/editor/handwriting/handwriting_controller_test.dart`

- [ ] **Step 1: 创建 handwriting_controller.dart**

```dart
// lib/editor/handwriting/handwriting_controller.dart
import 'package:flutter/foundation.dart';
import '../../models/stroke.dart';

class IndexedStroke {
  final int index;
  final Stroke stroke;
  const IndexedStroke(this.index, this.stroke);
}

sealed class HandwritingAction {}

class AddAction extends HandwritingAction {
  final Stroke stroke;
  AddAction(this.stroke);
}

class EraseAction extends HandwritingAction {
  final List<IndexedStroke> items;
  EraseAction(this.items);
}

class HandwritingOverlayController extends ChangeNotifier {
  final List<Stroke> _strokes = [];
  final List<HandwritingAction> _undoStack = [];
  final List<HandwritingAction> _redoStack = [];

  List<Stroke> get strokes => List.unmodifiable(_strokes);
  bool get canUndo => _undoStack.isNotEmpty;
  bool get canRedo => _redoStack.isNotEmpty;

  void setStrokes(List<Stroke> list) {
    _strokes.clear();
    _strokes.addAll(list);
    _undoStack.clear();
    _redoStack.clear();
    notifyListeners();
  }

  void addStroke(Stroke stroke) {
    _strokes.add(stroke);
    _undoStack.add(AddAction(stroke));
    _redoStack.clear();
    notifyListeners();
  }

  void eraseStrokes(List<IndexedStroke> items) {
    if (items.isEmpty) return;
    final toRemove = items.map((e) => e.stroke).toSet();
    _strokes.removeWhere(toRemove.contains);
    _undoStack.add(EraseAction(items));
    _redoStack.clear();
    notifyListeners();
  }

  void undo() {
    if (_undoStack.isEmpty) return;
    final action = _undoStack.removeLast();
    switch (action) {
      case AddAction(:final stroke):
        _strokes.remove(stroke);
      case EraseAction(:final items):
        final sorted = List<IndexedStroke>.from(items)
          ..sort((a, b) => a.index.compareTo(b.index));
        for (final item in sorted) {
          final idx = item.index.clamp(0, _strokes.length);
          _strokes.insert(idx, item.stroke);
        }
    }
    _redoStack.add(action);
    notifyListeners();
  }

  void redo() {
    if (_redoStack.isEmpty) return;
    final action = _redoStack.removeLast();
    switch (action) {
      case AddAction(:final stroke):
        _strokes.add(stroke);
      case EraseAction(:final items):
        final toRemove = items.map((e) => e.stroke).toSet();
        _strokes.removeWhere(toRemove.contains);
    }
    _undoStack.add(action);
    notifyListeners();
  }

  void clear() {
    if (_strokes.isEmpty) return;
    final items = _strokes
        .asMap()
        .entries
        .map((e) => IndexedStroke(e.key, e.value))
        .toList();
    _strokes.clear();
    _undoStack.add(EraseAction(items));
    _redoStack.clear();
    notifyListeners();
  }
}
```

- [ ] **Step 2: 创建 handwriting_controller_test.dart**

```dart
// test/editor/handwriting/handwriting_controller_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:huawei_note_flutter/editor/handwriting/handwriting_controller.dart';
import 'package:huawei_note_flutter/models/stroke.dart';

Stroke _makeStroke(int x) => Stroke(
      brush: BrushType.pen,
      color: '#000000',
      width: 3,
      points: [StrokePoint(x: x, y: 0, t: 0)],
    );

void main() {
  late HandwritingOverlayController controller;

  setUp(() {
    controller = HandwritingOverlayController();
  });

  group('addStroke', () {
    test('adds stroke to list', () {
      final s = _makeStroke(10);
      controller.addStroke(s);
      expect(controller.strokes, [s]);
      expect(controller.canUndo, isTrue);
      expect(controller.canRedo, isFalse);
    });

    test('clears redo stack', () {
      final s1 = _makeStroke(1);
      final s2 = _makeStroke(2);
      controller.addStroke(s1);
      controller.undo();
      expect(controller.canRedo, isTrue);
      controller.addStroke(s2);
      expect(controller.canRedo, isFalse);
    });
  });

  group('undo/redo', () {
    test('undo Add removes stroke', () {
      final s = _makeStroke(10);
      controller.addStroke(s);
      controller.undo();
      expect(controller.strokes, isEmpty);
      expect(controller.canRedo, isTrue);
    });

    test('redo Add restores stroke', () {
      final s = _makeStroke(10);
      controller.addStroke(s);
      controller.undo();
      controller.redo();
      expect(controller.strokes, [s]);
    });

    test('undo Erase restores strokes at original indices', () {
      final s1 = _makeStroke(1);
      final s2 = _makeStroke(2);
      final s3 = _makeStroke(3);
      controller.addStroke(s1);
      controller.addStroke(s2);
      controller.addStroke(s3);
      // erase s2 (index 1)
      controller.eraseStrokes([IndexedStroke(1, s2)]);
      expect(controller.strokes, [s1, s3]);
      controller.undo();
      expect(controller.strokes, [s1, s2, s3]);
    });

    test('redo Erase removes strokes again', () {
      final s1 = _makeStroke(1);
      final s2 = _makeStroke(2);
      controller.addStroke(s1);
      controller.addStroke(s2);
      controller.eraseStrokes([IndexedStroke(0, s1)]);
      controller.undo();
      controller.redo();
      expect(controller.strokes, [s2]);
    });

    test('undo on empty stack does nothing', () {
      controller.undo();
      expect(controller.strokes, isEmpty);
    });

    test('redo on empty stack does nothing', () {
      controller.redo();
      expect(controller.strokes, isEmpty);
    });
  });

  group('setStrokes', () {
    test('replaces strokes and clears stacks', () {
      final s1 = _makeStroke(1);
      controller.addStroke(s1);
      final s2 = _makeStroke(2);
      controller.setStrokes([s2]);
      expect(controller.strokes, [s2]);
      expect(controller.canUndo, isFalse);
      expect(controller.canRedo, isFalse);
    });
  });

  group('clear', () {
    test('clears all strokes and pushes Erase', () {
      controller.addStroke(_makeStroke(1));
      controller.addStroke(_makeStroke(2));
      controller.clear();
      expect(controller.strokes, isEmpty);
      expect(controller.canUndo, isTrue);
    });

    test('undo after clear restores all strokes', () {
      final s1 = _makeStroke(1);
      final s2 = _makeStroke(2);
      controller.addStroke(s1);
      controller.addStroke(s2);
      controller.clear();
      controller.undo();
      expect(controller.strokes, [s1, s2]);
    });

    test('clear on empty does nothing', () {
      controller.clear();
      expect(controller.canUndo, isFalse);
    });
  });

  group('eraseStrokes', () {
    test('empty items does nothing', () {
      controller.addStroke(_makeStroke(1));
      controller.eraseStrokes([]);
      expect(controller.strokes.length, 1);
      expect(controller.canUndo, isTrue);
    });
  });
}
```

- [ ] **Step 3: 运行测试确认通过**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test test/editor/handwriting/handwriting_controller_test.dart`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaweiNoteFlutter/lib/editor/handwriting/handwriting_controller.dart code/HuaweiNoteFlutter/test/editor/handwriting/handwriting_controller_test.dart
git commit -m "feat(p2b4): 添加 HandwritingOverlayController 撤销栈管理 + 单测"
```

---

### Task 3: BrushPaintFactory 纯函数

**Files:**
- Create: `lib/editor/handwriting/brush_paint_factory.dart`

- [ ] **Step 1: 创建 brush_paint_factory.dart**

```dart
// lib/editor/handwriting/brush_paint_factory.dart
import 'dart:ui';
import 'package:flutter/material.dart';
import '../../models/stroke.dart';

class BrushPaintFactory {
  BrushPaintFactory._();

  static Paint createPaint(Stroke stroke, double devicePixelRatio) {
    final basePx = stroke.width * devicePixelRatio;
    final color = _parseColor(stroke.color);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..isAntiAlias = true;

    switch (stroke.brush) {
      case BrushType.pen:
        paint.color = color.withAlpha(255);
        paint.strokeWidth = basePx;
      case BrushType.brush:
        paint.color = color.withAlpha(255);
        paint.strokeWidth = basePx * 1.3;
        paint.maskFilter = MaskFilter.blur(BlurStyle.normal, 2.0 * devicePixelRatio);
      case BrushType.marker:
        paint.color = color.withAlpha(140);
        paint.strokeWidth = basePx * 1.6;
      case BrushType.pencil:
        paint.color = color.withAlpha(160);
        paint.strokeWidth = basePx;
    }
    return paint;
  }

  static Color _parseColor(String hex) {
    if (hex.isEmpty) return Colors.black;
    var h = hex.replaceFirst('#', '');
    if (h.length == 6) h = 'FF$h';
    return Color(int.parse(h, radix: 16));
  }
}
```

- [ ] **Step 2: 验证文件无语法错误**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/editor/handwriting/brush_paint_factory.dart`
Expected: No issues found

- [ ] **Step 3: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaweiNoteFlutter/lib/editor/handwriting/brush_paint_factory.dart
git commit -m "feat(p2b4): 添加 BrushPaintFactory 4 笔种 Paint 配置"
```

---

### Task 4: HandwritingPainter（CustomPainter）

**Files:**
- Create: `lib/editor/handwriting/handwriting_painter.dart`

- [ ] **Step 1: 创建 handwriting_painter.dart**

```dart
// lib/editor/handwriting/handwriting_painter.dart
import 'dart:ui';
import 'package:flutter/material.dart';
import '../../models/stroke.dart';
import 'brush_paint_factory.dart';

class HandwritingPainter extends CustomPainter {
  final List<Stroke> strokes;
  final List<Offset> inProgressPoints;
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;
  final double devicePixelRatio;

  HandwritingPainter({
    required this.strokes,
    required this.inProgressPoints,
    required this.currentBrush,
    required this.currentColor,
    required this.currentWidth,
    required this.devicePixelRatio,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final clipRect = Offset.zero & size;
    for (final stroke in strokes) {
      if (stroke.points.isEmpty) continue;
      if (!_strokeIntersectsRect(stroke, clipRect)) continue;
      _drawStroke(canvas, stroke);
    }
    if (inProgressPoints.isNotEmpty) {
      _drawInProgress(canvas);
    }
  }

  void _drawStroke(Canvas canvas, Stroke stroke) {
    final paint = BrushPaintFactory.createPaint(stroke, devicePixelRatio);
    if (stroke.points.length == 1) {
      final p = stroke.points[0];
      canvas.drawPoints(
        PointMode.points,
        [Offset(p.x.toDouble(), p.y.toDouble())],
        paint,
      );
      return;
    }
    final path = Path();
    path.moveTo(
      stroke.points[0].x.toDouble(),
      stroke.points[0].y.toDouble(),
    );
    for (int i = 1; i < stroke.points.length; i++) {
      path.lineTo(
        stroke.points[i].x.toDouble(),
        stroke.points[i].y.toDouble(),
      );
    }
    canvas.drawPath(path, paint);
  }

  void _drawInProgress(Canvas canvas) {
    final tmpStroke = Stroke(
      brush: currentBrush,
      color: currentColor,
      width: currentWidth,
      points: inProgressPoints
          .map((o) => StrokePoint(x: o.dx.toInt(), y: o.dy.toInt(), t: 0))
          .toList(),
    );
    _drawStroke(canvas, tmpStroke);
  }

  bool _strokeIntersectsRect(Stroke s, Rect rect) {
    double minX = double.infinity, minY = double.infinity;
    double maxX = double.negativeInfinity, maxY = double.negativeInfinity;
    for (final p in s.points) {
      final px = p.x.toDouble();
      final py = p.y.toDouble();
      if (px < minX) minX = px;
      if (px > maxX) maxX = px;
      if (py < minY) minY = py;
      if (py > maxY) maxY = py;
    }
    final pad = s.width / 2.0 + 4.0;
    final strokeRect = Rect.fromLTRB(minX - pad, minY - pad, maxX + pad, maxY + pad);
    return rect.overlaps(strokeRect);
  }

  @override
  bool shouldRepaint(covariant HandwritingPainter oldDelegate) => true;
}
```

- [ ] **Step 2: 验证文件无语法错误**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/editor/handwriting/handwriting_painter.dart`
Expected: No issues found

- [ ] **Step 3: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaweiNoteFlutter/lib/editor/handwriting/handwriting_painter.dart
git commit -m "feat(p2b4): 添加 HandwritingPainter 笔迹绘制"
```

---

### Task 5: HandwritingOverlay Widget

**Files:**
- Create: `lib/editor/handwriting/handwriting_overlay.dart`

- [ ] **Step 1: 创建 handwriting_overlay.dart**

```dart
// lib/editor/handwriting/handwriting_overlay.dart
import 'package:flutter/material.dart';
import '../../models/stroke.dart';
import 'brush_paint_factory.dart';
import 'handwriting_controller.dart';
import 'handwriting_painter.dart';
import 'stroke_eraser.dart';

class HandwritingOverlay extends StatefulWidget {
  final HandwritingOverlayController controller;
  final bool isActive;
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;
  final bool isErasing;

  const HandwritingOverlay({
    super.key,
    required this.controller,
    required this.isActive,
    required this.currentBrush,
    required this.currentColor,
    required this.currentWidth,
    required this.isErasing,
  });

  @override
  State<HandwritingOverlay> createState() => _HandwritingOverlayState();
}

class _HandwritingOverlayState extends State<HandwritingOverlay> {
  final List<Offset> _inProgressPoints = [];
  final List<Stroke> _erasedThisGesture = [];
  List<Stroke> _gestureSnapshot = [];
  int _gestureStartMs = 0;

  static const double _eraserRadiusDp = 12.0;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onControllerChanged);
  }

  @override
  void didUpdateWidget(covariant HandwritingOverlay oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onControllerChanged);
      widget.controller.addListener(_onControllerChanged);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onControllerChanged);
    super.dispose();
  }

  void _onControllerChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final dpr = MediaQuery.of(context).devicePixelRatio;
    return Listener(
      onPointerDown: widget.isActive ? _onPointerDown : null,
      onPointerMove: widget.isActive ? _onPointerMove : null,
      onPointerUp: widget.isActive ? _onPointerUp : null,
      onPointerCancel: widget.isActive ? _onPointerUp : null,
      child: RepaintBoundary(
        child: CustomPaint(
          painter: HandwritingPainter(
            strokes: widget.controller.strokes,
            inProgressPoints: _inProgressPoints,
            currentBrush: widget.currentBrush,
            currentColor: widget.currentColor,
            currentWidth: widget.currentWidth,
            devicePixelRatio: dpr,
          ),
          size: Size.infinite,
        ),
      ),
    );
  }

  void _onPointerDown(PointerDownEvent event) {
    _gestureStartMs = DateTime.now().millisecondsSinceEpoch;
    if (widget.isErasing) {
      _erasedThisGesture.clear();
      _gestureSnapshot = List.from(widget.controller.strokes);
      _eraseAt(event.localPosition);
    } else {
      _inProgressPoints.clear();
      _inProgressPoints.add(event.localPosition);
    }
    setState(() {});
  }

  void _onPointerMove(PointerMoveEvent event) {
    if (widget.isErasing) {
      _eraseAt(event.localPosition);
    } else {
      _inProgressPoints.add(event.localPosition);
    }
    setState(() {});
  }

  void _onPointerUp(PointerEvent event) {
    if (widget.isErasing) {
      if (_erasedThisGesture.isNotEmpty) {
        final erasedSet = _erasedThisGesture.toSet();
        final items = <IndexedStroke>[];
        for (int i = 0; i < _gestureSnapshot.length; i++) {
          if (erasedSet.contains(_gestureSnapshot[i])) {
            items.add(IndexedStroke(i, _gestureSnapshot[i]));
          }
        }
        widget.controller.eraseStrokes(items);
      }
      _erasedThisGesture.clear();
      _gestureSnapshot = [];
    } else if (_inProgressPoints.isNotEmpty) {
      final points = _inProgressPoints.map((o) {
        final t = DateTime.now().millisecondsSinceEpoch - _gestureStartMs;
        return StrokePoint(x: o.dx.toInt(), y: o.dy.toInt(), t: t);
      }).toList();
      // Recalculate relative timestamps properly
      final now = DateTime.now().millisecondsSinceEpoch;
      final correctedPoints = <StrokePoint>[];
      for (int i = 0; i < _inProgressPoints.length; i++) {
        final o = _inProgressPoints[i];
        final t = (i == 0) ? 0 : ((now - _gestureStartMs) * i ~/ (_inProgressPoints.length - 1));
        correctedPoints.add(StrokePoint(x: o.dx.toInt(), y: o.dy.toInt(), t: t));
      }
      final stroke = Stroke(
        brush: widget.currentBrush,
        color: widget.currentColor,
        width: widget.currentWidth,
        points: correctedPoints,
      );
      widget.controller.addStroke(stroke);
      _inProgressPoints.clear();
    }
    setState(() {});
  }

  void _eraseAt(Offset position) {
    final dpr = MediaQuery.of(context).devicePixelRatio;
    final radiusPx = _eraserRadiusDp * dpr;
    final hits = StrokeEraser.hitTest(
      position.dx,
      position.dy,
      radiusPx,
      widget.controller.strokes,
    );
    if (hits.isEmpty) return;
    for (final s in hits) {
      if (!_erasedThisGesture.contains(s)) {
        _erasedThisGesture.add(s);
      }
    }
    // Immediately remove from display (will be committed on pointer up)
    final currentStrokes = List<Stroke>.from(widget.controller.strokes);
    for (final s in hits) {
      currentStrokes.remove(s);
    }
    // We need a temporary visual removal - use controller's internal state
    // Actually, for immediate visual feedback, we directly modify via eraseStrokes
    // But we want to batch the undo action... Let's use a different approach:
    // Remove from the live list for visual feedback, batch the undo on pointer up
  }
}
```

Wait — the eraser visual feedback needs a different approach. Let me revise. The Android code removes strokes immediately from the mutable list for visual feedback, then batches the undo action on pointer up. In Flutter with an immutable controller, we need to handle this differently.

Better approach: track `_erasedThisGesture` for undo batching, but also maintain a `_hiddenStrokes` set for the painter to skip. Let me rewrite properly:

```dart
// lib/editor/handwriting/handwriting_overlay.dart
import 'package:flutter/material.dart';
import '../../models/stroke.dart';
import 'handwriting_controller.dart';
import 'handwriting_painter.dart';
import 'stroke_eraser.dart';

class HandwritingOverlay extends StatefulWidget {
  final HandwritingOverlayController controller;
  final bool isActive;
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;
  final bool isErasing;

  const HandwritingOverlay({
    super.key,
    required this.controller,
    required this.isActive,
    required this.currentBrush,
    required this.currentColor,
    required this.currentWidth,
    required this.isErasing,
  });

  @override
  State<HandwritingOverlay> createState() => _HandwritingOverlayState();
}

class _HandwritingOverlayState extends State<HandwritingOverlay> {
  final List<Offset> _inProgressPoints = [];
  final Set<Stroke> _erasingHidden = {};
  List<Stroke> _gestureSnapshot = [];
  int _gestureStartMs = 0;

  static const double _eraserRadiusDp = 12.0;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onControllerChanged);
  }

  @override
  void didUpdateWidget(covariant HandwritingOverlay oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onControllerChanged);
      widget.controller.addListener(_onControllerChanged);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onControllerChanged);
    super.dispose();
  }

  void _onControllerChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final dpr = MediaQuery.of(context).devicePixelRatio;
    final visibleStrokes = _erasingHidden.isEmpty
        ? widget.controller.strokes
        : widget.controller.strokes.where((s) => !_erasingHidden.contains(s)).toList();
    return Listener(
      onPointerDown: widget.isActive ? _onPointerDown : null,
      onPointerMove: widget.isActive ? _onPointerMove : null,
      onPointerUp: widget.isActive ? _onPointerUp : null,
      onPointerCancel: widget.isActive ? _onPointerUp : null,
      child: RepaintBoundary(
        child: CustomPaint(
          painter: HandwritingPainter(
            strokes: visibleStrokes,
            inProgressPoints: widget.isErasing ? const [] : _inProgressPoints,
            currentBrush: widget.currentBrush,
            currentColor: widget.currentColor,
            currentWidth: widget.currentWidth,
            devicePixelRatio: dpr,
          ),
          size: Size.infinite,
        ),
      ),
    );
  }

  void _onPointerDown(PointerDownEvent event) {
    _gestureStartMs = DateTime.now().millisecondsSinceEpoch;
    if (widget.isErasing) {
      _erasingHidden.clear();
      _gestureSnapshot = List.from(widget.controller.strokes);
      _eraseAt(event.localPosition);
    } else {
      _inProgressPoints.clear();
      _inProgressPoints.add(event.localPosition);
    }
    setState(() {});
  }

  void _onPointerMove(PointerMoveEvent event) {
    if (widget.isErasing) {
      _eraseAt(event.localPosition);
    } else {
      _inProgressPoints.add(event.localPosition);
    }
    setState(() {});
  }

  void _onPointerUp(PointerEvent event) {
    if (widget.isErasing) {
      if (_erasingHidden.isNotEmpty) {
        final items = <IndexedStroke>[];
        for (int i = 0; i < _gestureSnapshot.length; i++) {
          if (_erasingHidden.contains(_gestureSnapshot[i])) {
            items.add(IndexedStroke(i, _gestureSnapshot[i]));
          }
        }
        widget.controller.eraseStrokes(items);
      }
      _erasingHidden.clear();
      _gestureSnapshot = [];
    } else if (_inProgressPoints.length >= 2) {
      final totalMs = DateTime.now().millisecondsSinceEpoch - _gestureStartMs;
      final points = <StrokePoint>[];
      for (int i = 0; i < _inProgressPoints.length; i++) {
        final o = _inProgressPoints[i];
        final t = _inProgressPoints.length == 1
            ? 0
            : (totalMs * i ~/ (_inProgressPoints.length - 1));
        points.add(StrokePoint(x: o.dx.toInt(), y: o.dy.toInt(), t: t));
      }
      widget.controller.addStroke(Stroke(
        brush: widget.currentBrush,
        color: widget.currentColor,
        width: widget.currentWidth,
        points: points,
      ));
      _inProgressPoints.clear();
    } else if (_inProgressPoints.length == 1) {
      final o = _inProgressPoints[0];
      widget.controller.addStroke(Stroke(
        brush: widget.currentBrush,
        color: widget.currentColor,
        width: widget.currentWidth,
        points: [StrokePoint(x: o.dx.toInt(), y: o.dy.toInt(), t: 0)],
      ));
      _inProgressPoints.clear();
    }
    setState(() {});
  }

  void _eraseAt(Offset position) {
    final dpr = MediaQuery.of(context).devicePixelRatio;
    final radiusPx = _eraserRadiusDp * dpr;
    final visibleStrokes = widget.controller.strokes
        .where((s) => !_erasingHidden.contains(s))
        .toList();
    final hits = StrokeEraser.hitTest(position.dx, position.dy, radiusPx, visibleStrokes);
    if (hits.isEmpty) return;
    _erasingHidden.addAll(hits);
  }
}
```

- [ ] **Step 2: 验证编译通过**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/editor/handwriting/handwriting_overlay.dart`
Expected: No issues found

- [ ] **Step 3: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaweiNoteFlutter/lib/editor/handwriting/handwriting_overlay.dart
git commit -m "feat(p2b4): 添加 HandwritingOverlay widget（触摸处理+绘制+橡皮）"
```

---

### Task 6: HandwritingToolbar + BrushWidthPicker + HandwritingStylePickerSheet

**Files:**
- Create: `lib/widgets/editor/handwriting_toolbar.dart`
- Create: `lib/widgets/editor/brush_width_picker.dart`
- Create: `lib/widgets/editor/handwriting_style_picker_sheet.dart`

- [ ] **Step 1: 创建 brush_width_picker.dart**

```dart
// lib/widgets/editor/brush_width_picker.dart
import 'package:flutter/material.dart';
import '../../theme.dart';

class BrushWidthPicker extends StatelessWidget {
  final int currentWidth;
  final String brushColor;
  final ValueChanged<int> onWidthSelected;

  const BrushWidthPicker({
    super.key,
    required this.currentWidth,
    required this.brushColor,
    required this.onWidthSelected,
  });

  static void show({
    required BuildContext context,
    required GlobalKey anchorKey,
    required int currentWidth,
    required String brushColor,
    required ValueChanged<int> onWidthSelected,
  }) {
    final renderBox = anchorKey.currentContext?.findRenderObject() as RenderBox?;
    if (renderBox == null) return;
    final offset = renderBox.localToGlobal(Offset.zero);
    final size = renderBox.size;

    final overlay = Overlay.of(context);
    late OverlayEntry entry;
    entry = OverlayEntry(builder: (ctx) {
      return Stack(
        children: [
          GestureDetector(
            onTap: () => entry.remove(),
            behavior: HitTestBehavior.opaque,
            child: const SizedBox.expand(),
          ),
          Positioned(
            left: offset.dx - 20,
            top: offset.dy - 56,
            child: Material(
              elevation: 8,
              borderRadius: BorderRadius.circular(12),
              child: BrushWidthPicker(
                currentWidth: currentWidth,
                brushColor: brushColor,
                onWidthSelected: (w) {
                  onWidthSelected(w);
                  entry.remove();
                },
              ),
            ),
          ),
        ],
      );
    });
    overlay.insert(entry);
  }

  @override
  Widget build(BuildContext context) {
    final color = _parseColor(brushColor);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildDot(1, 8, color),
          const SizedBox(width: 8),
          _buildDot(3, 16, color),
          const SizedBox(width: 8),
          _buildDot(6, 24, color),
        ],
      ),
    );
  }

  Widget _buildDot(int widthDp, double dotDiameter, Color color) {
    final isSelected = widthDp == currentWidth;
    return GestureDetector(
      onTap: () => onWidthSelected(widthDp),
      child: SizedBox(
        width: 40,
        height: 40,
        child: Center(
          child: Container(
            width: dotDiameter,
            height: dotDiameter,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
              border: isSelected
                  ? Border.all(color: AppColors.primary, width: 2)
                  : null,
            ),
          ),
        ),
      ),
    );
  }

  Color _parseColor(String hex) {
    if (hex.isEmpty) return Colors.black;
    var h = hex.replaceFirst('#', '');
    if (h.length == 6) h = 'FF$h';
    return Color(int.parse(h, radix: 16));
  }
}
```

- [ ] **Step 2: 创建 handwriting_style_picker_sheet.dart**

```dart
// lib/widgets/editor/handwriting_style_picker_sheet.dart
import 'package:flutter/material.dart';
import '../../models/stroke.dart';
import '../../theme.dart';

void showHandwritingStylePickerSheet(
  BuildContext context, {
  required BrushType currentBrush,
  required String currentColor,
  required int currentWidth,
  required ValueChanged<BrushType> onBrushSelected,
  required ValueChanged<String> onColorSelected,
  required ValueChanged<int> onWidthSelected,
}) {
  showModalBottomSheet(
    context: context,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (ctx) => _HandwritingStylePickerContent(
      currentBrush: currentBrush,
      currentColor: currentColor,
      currentWidth: currentWidth,
      onBrushSelected: onBrushSelected,
      onColorSelected: onColorSelected,
      onWidthSelected: onWidthSelected,
    ),
  );
}

class _HandwritingStylePickerContent extends StatefulWidget {
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;
  final ValueChanged<BrushType> onBrushSelected;
  final ValueChanged<String> onColorSelected;
  final ValueChanged<int> onWidthSelected;

  const _HandwritingStylePickerContent({
    required this.currentBrush,
    required this.currentColor,
    required this.currentWidth,
    required this.onBrushSelected,
    required this.onColorSelected,
    required this.onWidthSelected,
  });

  @override
  State<_HandwritingStylePickerContent> createState() =>
      _HandwritingStylePickerContentState();
}

class _HandwritingStylePickerContentState
    extends State<_HandwritingStylePickerContent> {
  late BrushType _brush;
  late String _color;
  late int _width;

  static const _colors = [
    '#212121', '#E53935', '#FB8C00', '#FDD835',
    '#43A047', '#00897B', '#1E88E5', '#8E24AA',
  ];

  @override
  void initState() {
    super.initState();
    _brush = widget.currentBrush;
    _color = widget.currentColor;
    _width = widget.currentWidth;
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(AppDimens.spacingL),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('笔种', style: TextStyle(
              fontSize: AppDimens.textBody,
              fontWeight: FontWeight.w600,
              color: AppColors.styleSheetHeader,
            )),
            const SizedBox(height: AppDimens.spacingS),
            _buildBrushRow(),
            const SizedBox(height: AppDimens.spacingL),
            const Text('颜色', style: TextStyle(
              fontSize: AppDimens.textBody,
              fontWeight: FontWeight.w600,
              color: AppColors.styleSheetHeader,
            )),
            const SizedBox(height: AppDimens.spacingS),
            _buildColorRow(),
            const SizedBox(height: AppDimens.spacingL),
            const Text('粗细', style: TextStyle(
              fontSize: AppDimens.textBody,
              fontWeight: FontWeight.w600,
              color: AppColors.styleSheetHeader,
            )),
            const SizedBox(height: AppDimens.spacingS),
            _buildWidthRow(),
            const SizedBox(height: AppDimens.spacingL),
          ],
        ),
      ),
    );
  }

  Widget _buildBrushRow() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        _brushButton(BrushType.pen, Icons.edit, '钢笔'),
        _brushButton(BrushType.brush, Icons.brush, '画笔'),
        _brushButton(BrushType.pencil, Icons.create, '铅笔'),
        _brushButton(BrushType.marker, Icons.highlight, '马克笔'),
      ],
    );
  }

  Widget _brushButton(BrushType type, IconData icon, String label) {
    final isSelected = _brush == type;
    return GestureDetector(
      onTap: () {
        setState(() => _brush = type);
        widget.onBrushSelected(type);
      },
      child: Column(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: isSelected ? AppColors.primaryLight : Colors.transparent,
              shape: BoxShape.circle,
              border: isSelected
                  ? Border.all(color: AppColors.primary, width: 2)
                  : Border.all(color: AppColors.divider),
            ),
            child: Icon(icon, size: 22, color: isSelected ? AppColors.primary : AppColors.textSecondary),
          ),
          const SizedBox(height: 4),
          Text(label, style: TextStyle(
            fontSize: AppDimens.textCaption,
            color: isSelected ? AppColors.primary : AppColors.textSecondary,
          )),
        ],
      ),
    );
  }

  Widget _buildColorRow() {
    return Wrap(
      spacing: 12,
      runSpacing: 8,
      children: _colors.map((hex) {
        final isSelected = _color.toLowerCase() == hex.toLowerCase();
        final color = _parseHex(hex);
        return GestureDetector(
          onTap: () {
            setState(() => _color = hex);
            widget.onColorSelected(hex);
          },
          child: Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
              border: isSelected
                  ? Border.all(color: AppColors.primary, width: 3)
                  : null,
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildWidthRow() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        _widthButton(1, 8),
        _widthButton(3, 16),
        _widthButton(6, 24),
      ],
    );
  }

  Widget _widthButton(int widthDp, double dotSize) {
    final isSelected = _width == widthDp;
    final dotColor = _parseHex(_color);
    return GestureDetector(
      onTap: () {
        setState(() => _width = widthDp);
        widget.onWidthSelected(widthDp);
      },
      child: SizedBox(
        width: 48,
        height: 48,
        child: Center(
          child: Container(
            width: dotSize,
            height: dotSize,
            decoration: BoxDecoration(
              color: dotColor,
              shape: BoxShape.circle,
              border: isSelected
                  ? Border.all(color: AppColors.primary, width: 2)
                  : null,
            ),
          ),
        ),
      ),
    );
  }

  Color _parseHex(String hex) {
    var h = hex.replaceFirst('#', '');
    if (h.length == 6) h = 'FF$h';
    return Color(int.parse(h, radix: 16));
  }
}
```

- [ ] **Step 3: 创建 handwriting_toolbar.dart**

```dart
// lib/widgets/editor/handwriting_toolbar.dart
import 'package:flutter/material.dart';
import '../../models/stroke.dart';
import '../../theme.dart';
import 'brush_width_picker.dart';

class HandwritingToolbar extends StatelessWidget {
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;
  final bool isErasing;
  final ValueChanged<BrushType> onBrushSelected;
  final VoidCallback onColorTap;
  final ValueChanged<BrushType> onBrushWidthTap;
  final VoidCallback onEraserTap;

  const HandwritingToolbar({
    super.key,
    required this.currentBrush,
    required this.currentColor,
    required this.currentWidth,
    required this.isErasing,
    required this.onBrushSelected,
    required this.onColorTap,
    required this.onBrushWidthTap,
    required this.onEraserTap,
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
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _ColorButton(color: currentColor, onTap: onColorTap),
          _BrushButton(
            icon: Icons.edit,
            type: BrushType.pen,
            isSelected: !isErasing && currentBrush == BrushType.pen,
            onTap: () => _handleBrushTap(BrushType.pen),
          ),
          _BrushButton(
            icon: Icons.brush,
            type: BrushType.brush,
            isSelected: !isErasing && currentBrush == BrushType.brush,
            onTap: () => _handleBrushTap(BrushType.brush),
          ),
          _BrushButton(
            icon: Icons.create,
            type: BrushType.pencil,
            isSelected: !isErasing && currentBrush == BrushType.pencil,
            onTap: () => _handleBrushTap(BrushType.pencil),
          ),
          _BrushButton(
            icon: Icons.highlight,
            type: BrushType.marker,
            isSelected: !isErasing && currentBrush == BrushType.marker,
            onTap: () => _handleBrushTap(BrushType.marker),
          ),
          _BrushButton(
            icon: Icons.auto_fix_normal,
            type: BrushType.pen, // unused, just for eraser icon
            isSelected: isErasing,
            onTap: onEraserTap,
            isEraser: true,
          ),
        ],
      ),
    );
  }

  void _handleBrushTap(BrushType type) {
    if (!isErasing && currentBrush == type) {
      onBrushWidthTap(type);
    } else {
      onBrushSelected(type);
    }
  }
}

class _ColorButton extends StatelessWidget {
  final String color;
  final VoidCallback onTap;

  const _ColorButton({required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final c = _parseHex(color);
    return IconButton(
      onPressed: onTap,
      icon: Container(
        width: 22,
        height: 22,
        decoration: BoxDecoration(
          color: c,
          shape: BoxShape.circle,
          border: Border.all(color: AppColors.divider),
        ),
      ),
    );
  }

  Color _parseHex(String hex) {
    var h = hex.replaceFirst('#', '');
    if (h.length == 6) h = 'FF$h';
    return Color(int.parse(h, radix: 16));
  }
}

class _BrushButton extends StatelessWidget {
  final IconData icon;
  final BrushType type;
  final bool isSelected;
  final VoidCallback onTap;
  final bool isEraser;

  const _BrushButton({
    required this.icon,
    required this.type,
    required this.isSelected,
    required this.onTap,
    this.isEraser = false,
  });

  @override
  Widget build(BuildContext context) {
    return IconButton(
      onPressed: onTap,
      icon: Icon(
        icon,
        color: isSelected ? AppColors.primary : AppColors.editorIconActive,
        size: 24,
      ),
    );
  }
}
```

- [ ] **Step 4: 验证编译**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/widgets/editor/handwriting_toolbar.dart lib/widgets/editor/brush_width_picker.dart lib/widgets/editor/handwriting_style_picker_sheet.dart`
Expected: No issues found

- [ ] **Step 5: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaweiNoteFlutter/lib/widgets/editor/handwriting_toolbar.dart code/HuaweiNoteFlutter/lib/widgets/editor/brush_width_picker.dart code/HuaweiNoteFlutter/lib/widgets/editor/handwriting_style_picker_sheet.dart
git commit -m "feat(p2b4): 添加 HandwritingToolbar + StylePickerSheet + BrushWidthPicker"
```

---

### Task 7: NoteEditorProvider 扩展手写状态

**Files:**
- Modify: `lib/providers/note_editor_provider.dart`

- [ ] **Step 1: 在 NoteEditorState 添加手写字段**

在 `NoteEditorState` class 中添加：

```dart
final bool isHandwritingMode;
final BrushType currentBrush;
final String currentBrushColor;
final int currentBrushWidth;
final bool isErasing;
```

构造函数默认值：
```dart
this.isHandwritingMode = false,
this.currentBrush = BrushType.pen,
this.currentBrushColor = '#212121',
this.currentBrushWidth = 3,
this.isErasing = false,
```

copyWith 对应字段：
```dart
bool? isHandwritingMode,
BrushType? currentBrush,
String? currentBrushColor,
int? currentBrushWidth,
bool? isErasing,
```

需要在文件顶部添加 import：`import '../models/stroke.dart';`

- [ ] **Step 2: 在 NoteEditorNotifier 添加手写方法**

```dart
void enterHandwritingMode() {
  state = state.copyWith(isHandwritingMode: true, isErasing: false);
}

void exitHandwritingMode() {
  state = state.copyWith(isHandwritingMode: false, isErasing: false);
}

void setBrush(BrushType type) {
  state = state.copyWith(currentBrush: type, isErasing: false);
}

void setBrushColor(String hex) {
  state = state.copyWith(currentBrushColor: hex);
}

void setBrushWidth(int width) {
  state = state.copyWith(currentBrushWidth: width);
}

void toggleEraser() {
  state = state.copyWith(isErasing: !state.isErasing);
}
```

- [ ] **Step 3: 修改 saveNote 读取 handwriting controller strokes**

将 `saveNote()` 中的 `handwriting: state.loadedNote?.content.handwriting ?? []` 改为接受外部传入的 strokes 参数：

```dart
Future<bool> saveNote({List<Stroke>? handwritingStrokes}) async {
  // ... existing code ...
  final content = NoteContent(
    documentJson: {'document': docJson},
    handwriting: handwritingStrokes ?? state.loadedNote?.content.handwriting ?? [],
  );
  // ... rest unchanged ...
}
```

- [ ] **Step 4: 验证编译**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/providers/note_editor_provider.dart`
Expected: No issues found

- [ ] **Step 5: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaweiNoteFlutter/lib/providers/note_editor_provider.dart
git commit -m "feat(p2b4): NoteEditorProvider 扩展手写模式状态 + 切换方法"
```

---

### Task 8: NoteEditorPage 集成

**Files:**
- Modify: `lib/pages/note_editor_page.dart`
- Modify: `lib/widgets/editor/text_toolbar.dart`

- [ ] **Step 1: 在 _NoteEditorPageState 添加 HandwritingOverlayController**

```dart
final _handwritingController = HandwritingOverlayController();
```

dispose 中添加 `_handwritingController.dispose();`

在 `_setupEditor` 末尾（或 `loadNote` 之后），初始化 controller 的 strokes：
```dart
final loadedStrokes = ref.read(noteEditorProvider(widget.noteId)).loadedNote?.content.handwriting ?? [];
_handwritingController.setStrokes(loadedStrokes);
```

- [ ] **Step 2: 修改 _buildEditor 为 Stack 布局**

将 `Expanded(child: _buildEditor(notifier, state))` 改为：

```dart
Expanded(
  child: Stack(
    children: [
      IgnorePointer(
        ignoring: state.isHandwritingMode,
        child: Opacity(
          opacity: state.isHandwritingMode ? 0.5 : 1.0,
          child: _buildEditor(notifier, state),
        ),
      ),
      if (_editorReady)
        IgnorePointer(
          ignoring: !state.isHandwritingMode,
          child: HandwritingOverlay(
            controller: _handwritingController,
            isActive: state.isHandwritingMode,
            currentBrush: state.currentBrush,
            currentColor: state.currentBrushColor,
            currentWidth: state.currentBrushWidth,
            isErasing: state.isErasing,
          ),
        ),
    ],
  ),
)
```

- [ ] **Step 3: 修改 _buildBottomBar 添加手写工具栏**

```dart
Widget _buildBottomBar(NoteEditorNotifier notifier, NoteEditorState state) {
  if (state.isHandwritingMode) {
    return HandwritingToolbar(
      currentBrush: state.currentBrush,
      currentColor: state.currentBrushColor,
      currentWidth: state.currentBrushWidth,
      isErasing: state.isErasing,
      onBrushSelected: notifier.setBrush,
      onColorTap: () => _showHandwritingStylePicker(notifier, state),
      onBrushWidthTap: (type) => _showBrushWidthPicker(type, notifier, state),
      onEraserTap: notifier.toggleEraser,
    );
  }
  if (state.isEditing) {
    return TextToolbar(
      editorState: notifier.editorState,
      onStyleTap: () => _showStylePicker(notifier),
      onImageTap: () => notifier.insertImage(),
      onRecordTap: () => notifier.startRecording(context),
      onHandwritingTap: () => notifier.enterHandwritingMode(),
    );
  }
  // ... browse bar unchanged
}
```

- [ ] **Step 4: 添加 _showHandwritingStylePicker 和 _showBrushWidthPicker 方法**

```dart
void _showHandwritingStylePicker(NoteEditorNotifier notifier, NoteEditorState state) {
  showHandwritingStylePickerSheet(
    context,
    currentBrush: state.currentBrush,
    currentColor: state.currentBrushColor,
    currentWidth: state.currentBrushWidth,
    onBrushSelected: notifier.setBrush,
    onColorSelected: notifier.setBrushColor,
    onWidthSelected: notifier.setBrushWidth,
  );
}

void _showBrushWidthPicker(BrushType type, NoteEditorNotifier notifier, NoteEditorState state) {
  // Use a simple dialog approach for v1
  showDialog(
    context: context,
    builder: (ctx) => AlertDialog(
      contentPadding: const EdgeInsets.all(16),
      content: BrushWidthPicker(
        currentWidth: state.currentBrushWidth,
        brushColor: state.currentBrushColor,
        onWidthSelected: (w) {
          notifier.setBrushWidth(w);
          Navigator.pop(ctx);
        },
      ),
    ),
  );
}
```

- [ ] **Step 5: 修改 EditorTopBar undo/redo 分发**

在 build 方法中修改 onUndo/onRedo：

```dart
onUndo: () {
  if (state.isHandwritingMode) {
    _handwritingController.undo();
  } else {
    notifier.editorState?.undoManager.undo();
  }
},
onRedo: () {
  if (state.isHandwritingMode) {
    _handwritingController.redo();
  } else {
    notifier.editorState?.undoManager.redo();
  }
},
```

- [ ] **Step 6: 修改 onDone 按钮逻辑**

```dart
onDone: () async {
  if (state.isHandwritingMode) {
    notifier.exitHandwritingMode();
  } else {
    await notifier.saveNote(handwritingStrokes: _handwritingController.strokes);
    notifier.exitEditMode();
  }
},
```

- [ ] **Step 7: 修改 saveNote 调用点传入 handwriting strokes**

`deactivate` 和 `_onBack` 中的 `saveNote()` 调用改为：
```dart
notifier.saveNote(handwritingStrokes: _handwritingController.strokes);
```

- [ ] **Step 8: 修改 text_toolbar.dart 添加 onHandwritingTap**

在 TextToolbar 中添加 `final VoidCallback? onHandwritingTap;` 参数，将手写按钮的 `onTap` 从 `_showSnackBar` 改为 `onHandwritingTap`。将按钮颜色从 `AppColors.editorIconInactive` 改为 `AppColors.editorIconActive`。

- [ ] **Step 9: 添加必要的 imports**

在 `note_editor_page.dart` 顶部添加：
```dart
import '../editor/handwriting/handwriting_controller.dart';
import '../editor/handwriting/handwriting_overlay.dart';
import '../models/stroke.dart';
import '../widgets/editor/handwriting_toolbar.dart';
import '../widgets/editor/handwriting_style_picker_sheet.dart';
import '../widgets/editor/brush_width_picker.dart';
```

- [ ] **Step 10: 验证整体编译**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze`
Expected: No issues found

- [ ] **Step 11: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaweiNoteFlutter/lib/pages/note_editor_page.dart code/HuaweiNoteFlutter/lib/widgets/editor/text_toolbar.dart
git commit -m "feat(p2b4): 集成 HandwritingOverlay 到编辑器（Stack+工具栏切换+undo/redo分发）"
```

---

### Task 9: 运行全量测试 + flutter analyze 修复

**Files:**
- Possibly any file with lint/analyze issues

- [ ] **Step 1: 运行 flutter analyze**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze`
Expected: No issues found (fix any that appear)

- [ ] **Step 2: 运行全量测试**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test`
Expected: All tests pass (117+ existing + new tests)

- [ ] **Step 3: 修复发现的问题（如果有）**

修复 lint、类型错误、missing imports 等。

- [ ] **Step 4: Commit fixes（如果有修复）**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add -u code/HuaweiNoteFlutter/
git commit -m "fix(p2b4): 修复 lint 和类型错误"
```

---

### Task 10: Spec 文档 + Plan 文档 commit

**Files:**
- `docs/superpowers/specs/2026-06-07-flutter-phase2b4-handwriting-overlay.md`
- `docs/superpowers/plans/2026-06-07-flutter-phase2b4-handwriting-overlay.md`

- [ ] **Step 1: Commit 文档**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add docs/superpowers/specs/2026-06-07-flutter-phase2b4-handwriting-overlay.md docs/superpowers/plans/2026-06-07-flutter-phase2b4-handwriting-overlay.md
git commit -m "docs(p2b4): 添加手写覆盖层 spec + 实施计划"
```
