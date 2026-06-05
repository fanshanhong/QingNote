# M13c 样式增强 — 设计文档

## 1. 目标

将 StylePickerBottomSheet 从 4 行简易面板升级为对齐华为备忘录的完整样式面板：新增文本对齐、列表/缩进、字号滑块、扩充颜色、H1-H6 六级标题、背景纹理。M13c 聚焦**样式体系完整化**，依赖 M13b 编辑器框架。

**参考截图：** `docs/superpowers/references/huawei-note/笔记-编辑状态-弹出样式弹窗.jpg`

**依赖：** M13b 编辑器重设计（双模式 + StylePickerBottomSheet 已存在）。

## 2. 新样式面板布局

### 2.1 面板结构（7 行）

```
StylePickerBottomSheet (BottomSheetDialog)
  ├─ 标题栏：「样式」文字 + ✕ 关闭按钮
  ├─ 行 1：B / I / U / S + 左对齐 / 居中 / 右对齐（7 按钮）
  ├─ 行 2：缩进+ / 缩进- / 数字列表 / 字母列表 / 实心圆点 / 空心圆点（6 按钮）
  ├─ 行 3：字号滑块（Aa 小 ← SeekBar 5 档 → Aa 大）
  ├─ 行 4：7 色圆点（红/橙/绿/浅蓝/蓝/紫/黑）
  ├─ 行 5：H1 / H2 / H3 / H4 / H5 / H6（6 按钮）
  └─ 行 6：4 背景纹理缩略图（白纸/亚麻/牛皮纸/网点）
```

### 2.2 行 1：内联样式 + 对齐

- **B / I / U / S**：沿用现有 `toggleInline(SpanType)` 逻辑，保留删除线
- **左对齐 / 居中 / 右对齐**：新增，调用 `toggleAlignment(Alignment)`
- 7 个按钮等宽平铺

### 2.3 行 2：列表 + 缩进

- **缩进+**：`indentLevel = min(indentLevel + 1, 3)`
- **缩进-**：`indentLevel = max(indentLevel - 1, 0)`
- **数字列表(1-2-3)**：toggle `listType = NUMBERED`（再点取消）
- **字母列表(a-b-c)**：toggle `listType = LETTERED`
- **实心圆点(●)**：toggle `listType = BULLET`
- **空心圆点(○)**：toggle `listType = HOLLOW_BULLET`
- 6 个按钮等宽平铺
- 列表类型互斥（选了 NUMBERED 再选 BULLET 会切换，不是叠加）

### 2.4 行 3：字号滑块

- `SeekBar` max=4，5 个档位吸附
- 档位映射：`0=xs(0.75x)` / `1=small(0.85x)` / `2=normal(1.0x)` / `3=large(1.25x)` / `4=xl(1.5x)`
- 左侧小 "Aa" + 右侧大 "Aa" 标签
- 拖动时实时预览（调用 `toggleSize(value)`）
- 滑块蓝色（`@color/primary`）

### 2.5 行 4：7 色圆点

颜色值与华为对齐：

| 序号 | 颜色 | Hex |
|------|------|-----|
| 1 | 红 | #E53935 |
| 2 | 橙 | #FB8C00 |
| 3 | 绿 | #43A047 |
| 4 | 浅蓝 | #29B6F6 |
| 5 | 蓝 | #1E88E5 |
| 6 | 紫 | #AB47BC |
| 7 | 黑 | #212121 |

- 选中态：外圈加 2dp ring（同华为参考）
- 点击调用现有 `pickColor(hex)`，无需改数据层

### 2.6 行 5：六级标题

- **H1 / H2 / H3 / H4 / H5 / H6**：6 个按钮等宽平铺
- 互斥 toggle（选中再点取消回正文）
- 调用 `toggleHeading(Heading.H1..H6)`
- Heading enum 从 2 值扩展到 6 值

标题字号 dimens：

| 级别 | 字号 |
|------|------|
| H1 | 28sp |
| H2 | 24sp |
| H3 | 20sp |
| H4 | 18sp |
| H5 | 16sp |
| H6 | 14sp |
| 正文 | 16sp |

### 2.7 行 6：背景纹理

- 4 个正方形缩略图（52dp × 52dp，圆角 8dp）
- **白纸（plain）**：纯白，默认选中蓝色边框
- **亚麻（linen）**：浅米色织物纹理 tile drawable
- **牛皮纸（kraft）**：暖棕色纸张纹理 tile drawable
- **网点（grid）**：白底灰色圆点阵列 tile drawable
- 选中态：2dp 蓝色边框
- 点击调用 `onBackgroundPicked(background)` 回调到 Activity

## 3. 数据模型变更

### 3.1 TextBlock 新增字段

```kotlin
data class TextBlock(
    val id: String,
    val text: String,
    val spans: List<TextSpan>,
    val heading: Heading? = null,
    // ---- M13c 新增 ----
    val alignment: Alignment? = null,   // null = START
    val listType: ListType? = null,     // null = 无列表
    val indentLevel: Int = 0,           // 0-3
)
```

### 3.2 新增枚举

```kotlin
enum class Alignment { START, CENTER, END }

enum class ListType { BULLET, HOLLOW_BULLET, NUMBERED, LETTERED }
```

### 3.3 Heading 扩展

```kotlin
enum class Heading { H1, H2, H3, H4, H5, H6 }
```

从 2 值扩展到 6 值。现有 `toggleHeading` / `applyHeadingSize` / `snapshotHeading` / `setBlockHeading` 自然兼容（sealed when 列举所有分支即可）。

### 3.4 SpanType.FONT_SIZE 值域扩展

现有 3 档 `"small"` / `"medium"` / `"large"` 扩展为 5 档：

| 档位 | value 字符串 | RelativeSizeSpan ratio |
|------|-------------|----------------------|
| xs | "xs" | 0.75f |
| small | "small" | 0.85f |
| normal | "medium" | 1.0f |
| large | "large" | 1.25f |
| xl | "xl" | 1.5f |

`SpanConverter` 中 `FONT_SIZE` 的 ratio 映射表更新。

### 3.5 Note 新增 background 字段

```kotlin
data class Note(
    // ... 现有字段 ...
    val background: String = "plain",  // "plain" / "linen" / "kraft" / "grid"
)
```

DB `notes` 表 ALTER TABLE 加 `background TEXT NOT NULL DEFAULT 'plain'`。APP 未上线，无需版本迁移，直接改 `NoteDbHelper.onCreate` 的 CREATE TABLE 语句 + 在 `onUpgrade` v3→v4 中加 ALTER。

### 3.6 NoteJson 序列化扩展

TextBlock JSON 新增 3 个可选字段：
```json
{
  "type": "text",
  "id": "t-xxxx",
  "text": "hello",
  "spans": [...],
  "heading": "H1",
  "alignment": "CENTER",
  "listType": "NUMBERED",
  "indentLevel": 1
}
```

反序列化用 `optString` 容错（缺失 = 默认值）。

## 4. 渲染

### 4.1 对齐渲染

`TextBlockView.applyAlignment(alignment)` 设置 EditText 的 `gravity`：
- START → `Gravity.START`
- CENTER → `Gravity.CENTER_HORIZONTAL`
- END → `Gravity.END`

在 `bind()` 和 `toggleAlignment()` 后调用。

### 4.2 列表/缩进渲染

TextBlockView 布局改造：

```
TextBlockView (horizontal LinearLayout)
  ├─ listMarker (TextView, 24dp 宽, gravity=end)
  │    内容：BULLET → "•" / HOLLOW_BULLET → "○" / NUMBERED → "1." / LETTERED → "a."
  │    listType=null 时 GONE
  └─ editText (原有 EditText, weight=1)
整体 paddingStart = indentLevel * 24dp
```

编号由 Presenter 设置：`TextBlockView.setListMarker(text)` 公开方法。

### 4.3 列表编号协调

`EditorPresenter.refreshListNumbers()` 逻辑：

```
遍历 currentBlocks：
  对每个 TextBlockView：
    if listType == NUMBERED：
      在它之前找连续同 listType 的 TextBlock 数量 → 编号 = count + 1
      view.setListMarker("${count + 1}.")
    if listType == LETTERED：
      同理 → view.setListMarker("${'a' + count}.")
    if listType == BULLET → view.setListMarker("•")
    if listType == HOLLOW_BULLET → view.setListMarker("○")
    if listType == null → view.hideListMarker()
```

在 `bind()` / `addTextBlockView()` / `onRequestDelete()` / `onRequestSplitAfter()` / block 移动后调用。

### 4.4 背景纹理渲染

**编辑器：** `NoteEditorActivity` 在 `loadNote()` 后根据 `note.background` 设置 `editorRoot` 背景：
- "plain" → `@color/bg_card`（白色）
- "linen" / "kraft" / "grid" → 对应 tile drawable（`BitmapDrawable` + `REPEAT` tileMode）

**列表卡片：** `NoteListAdapter` 绑定时若 `note.background != "plain"` 设置卡片背景 tile。

### 4.5 字号滑块渲染

SeekBar 与当前焦点 block 的 pendingSize 同步：
- 面板打开时读 `presenter.pendingSize()` 映射到 0-4 档位
- 拖动时调 `presenter.toggleSize(档位名)`
- thumb 蓝色（`@color/primary`），track 灰色

## 5. EditorPresenter 新增方法

### 5.1 对齐

```kotlin
fun toggleAlignment(alignment: Alignment) {
    val block = focusedTextBlock ?: return
    val current = block.alignment
    val newAlign = if (current == alignment) null else alignment
    // silentSetAlignment + history.push(ApplyAlignmentCommand)
}
```

### 5.2 列表/缩进

```kotlin
fun toggleListType(listType: ListType) {
    val block = focusedTextBlock ?: return
    val current = block.listType
    val newType = if (current == listType) null else listType
    // silentSetListType + history.push + refreshListNumbers()
}

fun indent() {
    val block = focusedTextBlock ?: return
    if (block.indentLevel >= 3) return
    // silentSetIndent(level + 1) + history.push
}

fun outdent() {
    val block = focusedTextBlock ?: return
    if (block.indentLevel <= 0) return
    // silentSetIndent(level - 1) + history.push
}
```

### 5.3 Undo/Redo Command

新增 3 个 Command 子类：
- `ApplyAlignmentCommand(blockIndex, oldAlignment, newAlignment)`
- `ApplyListTypeCommand(blockIndex, oldListType, newListType)`
- `ApplyIndentCommand(blockIndex, oldLevel, newLevel)`

沿用现有 `ApplySpanCommand` / `ApplyHeadingCommand` 的模式。

## 6. 组件变更清单

### 6.1 新增

| 文件 | 说明 |
|------|------|
| `res/layout/dialog_style_picker_v2.xml` | 新面板布局（7 行） |
| `res/drawable/bg_linen.xml` | 亚麻纹理 tile |
| `res/drawable/bg_kraft.xml` | 牛皮纸纹理 tile |
| `res/drawable/bg_grid.xml` | 网点纹理 tile |
| `model/history/commands/ApplyAlignmentCommand.kt` | 对齐 undo/redo |
| `model/history/commands/ApplyListTypeCommand.kt` | 列表类型 undo/redo |
| `model/history/commands/ApplyIndentCommand.kt` | 缩进 undo/redo |
| 6 个列表/对齐/缩进相关 vector drawable | 面板按钮图标 |

### 6.2 修改

| 文件 | 变更 |
|------|------|
| `Block.kt` | TextBlock 加 alignment / listType / indentLevel 字段；新增 Alignment / ListType 枚举；Heading 扩展到 H1-H6 |
| `NoteJson.kt` | 序列化/反序列化 alignment / listType / indentLevel |
| `Note.kt` | 加 background 字段 |
| `NoteDbHelper.kt` | CREATE TABLE 加 background 列；onUpgrade v3→v4 ALTER |
| `NoteRepository.kt` | save/get 处理 background 字段 |
| `SpanConverter.kt` | FONT_SIZE ratio 映射 3 档→5 档 |
| `TextBlockView.kt` | 布局加 listMarker + marginStart；applyAlignment()；setListMarker()；applyHeadingSize() 扩 H3-H6 |
| `EditorPresenter.kt` | toggleAlignment / toggleListType / indent / outdent / refreshListNumbers + 3 个 silent mutator + Heading when 扩展 |
| `StylePickerBottomSheet.kt` | 完全重写，6 行 + 标题栏 |
| `dialog_style_picker.xml` | 重写为 v2 布局 |
| `NoteEditorActivity.kt` | 背景纹理渲染 + onBackgroundPicked 回调 |
| `NoteListAdapter.kt` | 卡片背景纹理 |
| `strings.xml` | 新增样式面板相关字符串 |
| `dimens.xml` | H3-H6 字号 + 缩进单位 |

### 6.3 删除

无。

## 7. 不动的东西（明确边界）

- **EditorPresenter 核心逻辑** — insertImageBlocksAtFocus / insertChecklistBlockAtFocus / onRequestDelete / onRequestSplitAfter / focusLastTextBlock — 不改（仅新增对齐/列表/缩进相关方法）
- **ImageBlockView / ChecklistBlockView / AudioBlockView** — 不动
- **HandwritingOverlayView / BrushPainter** — 不动
- **NoteEditorActivity 双模式逻辑** — enterEditMode / exitEditMode — 不动
- **NoteListActivity / FilterPanelAdapter** — 不动
- **测试** — 140 项保持 PASS + 新增对齐/列表/字号/heading 序列化单测

## 8. 后续里程碑

| 轮次 | 范围 | 依赖 |
|------|------|------|
| M13d | 宫格视图 + 批量删除 | M13a 列表页 |
| M13e | 分享功能（分享为图片/文本/文档） | M13b 编辑器 |
