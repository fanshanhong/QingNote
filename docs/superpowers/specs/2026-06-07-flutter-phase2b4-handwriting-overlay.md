# Phase 2B-4 设计文档 — 手写覆盖层 + 手写工具栏

> 对标 Android HandwritingOverlayView + BrushPainter + StrokeEraser + HandwritingToolbarView + HandwritingStylePickerBottomSheet + BrushWidthPickerPopup

**目标：** 在编辑器上方实现透明手写覆盖层，支持 4 笔种绘制、笔画级橡皮擦除、撤销/重做，以及手写模式专属工具栏。

**技术栈：** Flutter + CustomPaint + GestureDetector + 自定义撤销栈

---

## 1. 架构决策

### 1.1 Overlay 策略（非 Block）

手写笔迹横跨整个编辑器区域，不限于单个 block。与 Android 一致，采用 **Stack 覆盖层**：
- 手写 Canvas 覆盖在 AppFlowyEditor 上方
- 手写模式下拦截所有触摸事件（`IgnorePointer` 包裹 editor）
- 非手写模式下 overlay 不接收事件（`IgnorePointer` 包裹 overlay）

### 1.2 数据存储

复用已有 `NoteContent.handwriting: List<Stroke>`：
- Stroke/StrokePoint/BrushType 模型已在 `lib/models/stroke.dart` 定义
- 已有 toJson/fromJson 序列化
- saveNote 时从 overlay 读取当前 strokes，写入 NoteContent

### 1.3 绘制方案

`CustomPaint` + `CustomPainter`：
- 遍历 strokes 列表，使用 Path + lineTo 绘制（与 Android drawPath 对齐）
- 4 笔种通过不同 Paint 属性区分（alpha/strokeWidth/maskFilter/shader）
- 实时绘制：in-progress 点集额外绘制当前手指轨迹

### 1.4 撤销栈

与 Android 同设计：
- `sealed class HandwritingAction { Add(stroke), Erase(list) }`
- undoStack / redoStack
- undo：Add → 移除 stroke；Erase → 按原位置插入
- redo：反向操作

### 1.5 不做的事

- ❌ 贝塞尔平滑（远期 Rust FFI 实现，当前用 lineTo 直连）
- ❌ 压感适配（PointerEvent.pressure 远期支持）
- ❌ 波形纹理 pencil shader（简化为半透明直线）
- ❌ 手写内容导出为图片
- ❌ 手写区域自动扩展高度（v1 固定与编辑器等高）

---

## 2. 手写模式切换

### 2.1 进入手写模式

触发：用户点击 TextToolbar 的"手写"按钮（`Icons.draw_outlined`）

状态变化：
- `isHandwritingMode = true`
- editor 内容区域半透明（opacity 0.5）
- 隐藏软键盘
- TextToolbar → HandwritingToolbar
- overlay 开始接收触摸事件
- editor 停止接收触摸事件
- EditorTopBar 的 undo/redo 切换为操作 handwriting 撤销栈

### 2.2 退出手写模式

触发：EditorTopBar 的"完成"按钮（同文本编辑的完成流程）

状态变化：
- `isHandwritingMode = false`
- editor 恢复不透明
- HandwritingToolbar → TextToolbar
- overlay 停止接收事件
- editor 恢复接收事件
- undo/redo 切换回 editor 的 UndoManager

---

## 3. HandwritingOverlay Widget

### 3.1 结构

```dart
class HandwritingOverlay extends StatefulWidget {
  final List<Stroke> initialStrokes;
  final bool isActive;
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;
  final bool isErasing;
  final VoidCallback? onStrokesChanged;

  const HandwritingOverlay({...});
}
```

### 3.2 HandwritingOverlayController

独立控制器类，管理笔画状态和撤销栈：

```dart
class HandwritingOverlayController extends ChangeNotifier {
  final List<Stroke> _strokes = [];
  final List<HandwritingAction> _undoStack = [];
  final List<HandwritingAction> _redoStack = [];

  List<Stroke> get strokes => List.unmodifiable(_strokes);
  bool get canUndo => _undoStack.isNotEmpty;
  bool get canRedo => _redoStack.isNotEmpty;

  void setStrokes(List<Stroke> strokes);
  void addStroke(Stroke stroke);
  void eraseStrokes(List<IndexedStroke> items);
  void undo();
  void redo();
  void clear();
}
```

### 3.3 触摸处理

使用 `Listener` widget（比 GestureDetector 更底层，能拿到所有 pointer 事件）：

```
ACTION_DOWN:
  if erasing → 初始化 erasedThisGesture + gestureSnapshot
  else → 初始化 inProgressPoints，记录 gestureStartTime

ACTION_MOVE:
  if erasing → hitTest 并移除命中 strokes
  else → 添加点到 inProgressPoints

ACTION_UP:
  if erasing → 提交 Erase action
  else → 组装 Stroke，提交 Add action
```

### 3.4 CustomPainter

```dart
class HandwritingPainter extends CustomPainter {
  final List<Stroke> strokes;
  final List<Offset> inProgressPoints;
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;

  @override
  void paint(Canvas canvas, Size size) {
    for (final stroke in strokes) {
      _drawStroke(canvas, stroke);
    }
    if (inProgressPoints.isNotEmpty) {
      _drawInProgress(canvas);
    }
  }
}
```

---

## 4. BrushPainter 逻辑

### 4.1 4 笔种 Paint 配置

与 Android BrushPainter 对齐：

| 笔种 | strokeCap | alpha | 宽度系数 | 特效 |
|------|-----------|-------|---------|------|
| PEN | round | 255 | 1.0x | 无 |
| BRUSH | round | 255 | 1.3x | MaskFilter.blur(BlurStyle.normal, 2) |
| MARKER | round | 140 | 1.6x | 无 |
| PENCIL | round | 160 | 1.0x | 无（v1 简化，不做噪点纹理） |

### 4.2 宽度单位

与 Android 一致，存储值为 dp（1/3/6），绘制时乘以 devicePixelRatio 转 px。

---

## 5. StrokeEraser 逻辑

### 5.1 笔画级擦除

与 Android StrokeEraser 完全对齐：
1. 粗筛：stroke bounding box 是否与橡皮圆相交
2. 细判：橡皮圆心到 stroke 各线段的最短距离 ≤ threshold（eraserRadius + strokeWidth/2）
3. 单点 stroke 退化为点到点距离判断

### 5.2 橡皮半径

默认 12dp（与 Android 一致）。

### 5.3 一次手势聚合

从 pointer down 到 pointer up 命中的所有 strokes 聚合为一个 Erase action（撤销时一起恢复）。

---

## 6. HandwritingToolbar

### 6.1 布局

底部工具栏，替换 TextToolbar 显示：

```
[ 颜色圆点 ] [ 钢笔 ] [ 画笔 ] [ 铅笔 ] [ 马克笔 ] [ 橡皮 ]
```

- 颜色圆点：显示当前颜色，点击弹出颜色选择
- 4 笔种按钮：点击切换笔种 + 高亮；再次点击已选笔种弹出粗细选择
- 橡皮按钮：切换擦除模式

### 6.2 交互

| 操作 | 行为 |
|------|------|
| 点击未选笔种 | 切换笔种，退出橡皮模式 |
| 点击已选笔种 | 弹出 BrushWidthPicker popup |
| 点击颜色 | 弹出 HandwritingStylePickerSheet |
| 点击橡皮 | 切换 isErasing 状态 |

### 6.3 HandwritingStylePickerSheet

BottomSheet，包含：
- 4 笔种选择行
- 8 色选择行（#212121 / #E53935 / #FB8C00 / #FDD835 / #43A047 / #00897B / #1E88E5 / #8E24AA）
- 3 档粗细（1/3/6 dp）

### 6.4 BrushWidthPicker

Popup（OverlayEntry），显示在笔种按钮上方：
- 3 个圆点（大小分别 8/16/24 dp）代表 1/3/6 宽度
- 当前选中项加边框

---

## 7. NoteEditorNotifier 扩展

### 7.1 新增状态字段

```dart
class NoteEditorState {
  // ... existing fields
  final bool isHandwritingMode;
  final BrushType currentBrush;
  final String currentBrushColor;
  final int currentBrushWidth;
  final bool isErasing;
}
```

### 7.2 新增方法

```dart
void enterHandwritingMode();
void exitHandwritingMode();
void setBrush(BrushType type);
void setBrushColor(String hex);
void setBrushWidth(int width);
void toggleEraser();
```

### 7.3 saveNote 扩展

保存时从 HandwritingOverlayController 获取当前 strokes：

```dart
final content = NoteContent(
  documentJson: {'document': docJson},
  handwriting: _handwritingController.strokes,
);
```

---

## 8. NoteEditorPage 集成

### 8.1 布局结构

```dart
Expanded(
  child: Stack(
    children: [
      // 底层：编辑器
      IgnorePointer(
        ignoring: state.isHandwritingMode,
        child: Opacity(
          opacity: state.isHandwritingMode ? 0.5 : 1.0,
          child: _buildEditor(notifier, state),
        ),
      ),
      // 上层：手写覆盖层
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

### 8.2 工具栏切换

```dart
Widget _buildBottomBar(...) {
  if (state.isHandwritingMode) {
    return HandwritingToolbar(
      currentBrush: state.currentBrush,
      currentColor: state.currentBrushColor,
      currentWidth: state.currentBrushWidth,
      isErasing: state.isErasing,
      onBrushSelected: notifier.setBrush,
      onColorTap: () => _showHandwritingStylePicker(notifier),
      onBrushWidthTap: (type) => _showBrushWidthPicker(type, notifier),
      onEraserTap: notifier.toggleEraser,
    );
  }
  if (state.isEditing) {
    return TextToolbar(...);
  }
  return BrowseBottomBar(...);
}
```

### 8.3 EditorTopBar Undo/Redo

手写模式下，undo/redo 调用 `_handwritingController.undo()` / `.redo()` 而非 `editorState.undoManager`。

---

## 9. 文件清单

### 新建

| 文件 | 职责 |
|------|------|
| `lib/editor/handwriting/handwriting_overlay.dart` | HandwritingOverlay widget + HandwritingPainter |
| `lib/editor/handwriting/handwriting_controller.dart` | HandwritingOverlayController（strokes + 撤销栈） |
| `lib/editor/handwriting/brush_painter.dart` | 4 笔种 Paint 配置工厂 |
| `lib/editor/handwriting/stroke_eraser.dart` | 笔画级橡皮 hit-test |
| `lib/widgets/editor/handwriting_toolbar.dart` | 手写模式底部工具栏 |
| `lib/widgets/editor/handwriting_style_picker_sheet.dart` | 笔种+颜色+粗细 BottomSheet |
| `lib/widgets/editor/brush_width_picker.dart` | 粗细选择 Popup |
| `test/editor/handwriting/stroke_eraser_test.dart` | 橡皮 hit-test 单测 |
| `test/editor/handwriting/handwriting_controller_test.dart` | 撤销栈逻辑单测 |

### 修改

| 文件 | 改动 |
|------|------|
| `lib/providers/note_editor_provider.dart` | 新增手写状态字段 + 方法；saveNote 读取 handwriting controller |
| `lib/pages/note_editor_page.dart` | Stack 布局 + HandwritingOverlay + 工具栏切换 + undo/redo 分发 |
| `lib/widgets/editor/text_toolbar.dart` | 手写按钮从 inactive → active color + 调用 enterHandwritingMode |

---

## 10. 测试策略

### 单测

- `stroke_eraser_test.dart`：bounding box 粗筛、点到线段距离、单点 stroke、空 stroke、多 stroke 命中
- `handwriting_controller_test.dart`：addStroke → canUndo、undo/redo 往返、erase action 聚合、clear、setStrokes 重置

### 手动验证

- 进入手写模式 → 绘制笔迹 → 切换笔种 → 颜色变化 → 粗细变化
- 橡皮擦除 → 撤销恢复被擦除笔迹 → 重做再次擦除
- 退出手写模式 → 保存 → 重新打开 → 笔迹仍在
- 非手写模式点击编辑器 → 正常输入文字（overlay 不拦截）
- 手写模式下点击编辑器文字区 → 无反应（overlay 拦截）

---

## 11. 范围边界

### 包含
- ✅ 透明覆盖层（Stack + CustomPaint）
- ✅ 4 笔种绘制（PEN/BRUSH/MARKER/PENCIL）
- ✅ 笔画级橡皮擦除
- ✅ 撤销/重做（Add + Erase action）
- ✅ 手写模式进入/退出
- ✅ HandwritingToolbar（颜色+笔种+橡皮）
- ✅ HandwritingStylePickerSheet（完整面板）
- ✅ BrushWidthPicker（粗细 popup）
- ✅ 笔迹保存/加载（复用 NoteContent.handwriting）

### 不包含
- ❌ 贝塞尔曲线平滑（远期 Rust FFI）
- ❌ 压感适配
- ❌ Pencil 噪点纹理 shader
- ❌ 手写区域动态扩展高度
- ❌ 手写内容导出为图片
- ❌ 手写识别（OCR）
