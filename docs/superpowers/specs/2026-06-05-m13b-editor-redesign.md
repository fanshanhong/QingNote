# M13b 编辑器重设计 — 设计文档

## 1. 目标

将编辑器从"始终编辑态 + 蓝色 Material Toolbar"切换到华为备忘录的"白底双模式"风格：浏览态（只读 + 底部动作栏）和编辑态（可编辑 + 底部工具栏 + 顶栏 ↩↪✓）。M13b 聚焦**编辑器框架**，不改样式面板内容（M13c 处理）。

**参考截图：** `docs/superpowers/references/huawei-note/笔记-未编辑状态.jpg`（浏览态）、`笔记-编辑状态.jpg`（编辑态）、`笔记-编辑状态-弹出软键盘.jpg`（键盘弹起）、`笔记-编辑状态-弹出样式弹窗.jpg`（样式面板）。

**依赖：** M13a 色彩基础（#007DFF 蓝色强调、白色状态栏、bg_window 等已就绪）。

## 2. 双模式设计

### 2.1 浏览态（Browse Mode）

打开**已有笔记**时的默认状态。

**顶栏：**
- 白色背景，只有 ← 返回按钮（左侧）
- 无 undo/redo/done 按钮
- 去掉 AppBarLayout + Toolbar，改为普通 LinearLayout

**内容区：**
- 标题 `EditText` 设为不可编辑（`focusable=false` + `clickable=false`）
- 所有 BlockView（TextBlockView / ChecklistBlockView / ImageBlockView / AudioBlockView）设为只读
- 图片块不显示 ✕ 删除按钮
- 清单勾选框在浏览态仍可点击切换（华为参考行为：浏览态也能勾选待办）。实现方式：`EditorPresenter.setReadOnly(true)` 时 `ChecklistBlockView` 的 CheckBox 保持 enabled，仅禁用文本编辑和删除/分裂操作

**笔记本/分类指示器：**
- 从 AppBar 内部移到 metadata strip 右侧（与时间戳同行）
- 浏览态可点击打开 NotebookPickerPopupWindow

**底部动作栏（BrowseActionBar）：**
- 4 个按钮，图标+文字标签，白色背景：
  1. **分享** — Toast 占位（M13e 实现）
  2. **收藏 / 取消收藏** — 切换 `isFavorite`，图标+文字动态变化
  3. **删除** — 弹出 DeleteConfirmBottomSheet，确认后软删除 + 返回列表
  4. **更多** — PopupMenu：移动到笔记本 / 设置分类

### 2.2 编辑态（Edit Mode）

点击内容区进入，或**新建笔记**时直接进入。

**顶栏：**
- 白色背景
- 左侧 ← 返回
- 右侧 3 个按钮：↩ 撤销 / ↪ 重做 / ✓ 完成
- ↩↪ 根据 `history.canUndo/canRedo` 控制 enabled + alpha

**内容区：**
- 标题 `EditText` 恢复可编辑
- 所有 BlockView 恢复可编辑
- 图片块显示 ✕ 删除按钮

**底部工具栏（TextToolbarView 改造）：**
- 5 个按钮，图标+文字标签，白色背景：
  1. **☑ 清单** — 原有功能
  2. **A≡ 样式** — 弹出 StylePickerBottomSheet
  3. **🖼 图片** — 原有功能
  4. **🎤 语音** — 原有功能
  5. **✏ 手写** — 进入手写态

### 2.3 模式切换逻辑

```
列表页点击已有笔记 → 浏览态
列表页 FAB 新建笔记   → 编辑态（noteId == -1L）

浏览态 → 点击内容区  → 编辑态（弹出键盘聚焦）
编辑态 → 点击 ✓      → 保存 + 隐藏键盘 + 浏览态
编辑态 → 点击 ←      → 保存 + 返回列表
浏览态 → 点击 ←      → 返回列表（已在 onPause 自动保存）
```

**Activity 内部用 `var isEditing: Boolean` 状态字段驱动 UI 切换。**

切换到编辑态时：
- 显示顶栏右侧按钮组（↩↪✓）
- 隐藏底部动作栏，显示底部工具栏
- `titleInput.isFocusableInTouchMode = true`
- 通知 presenter 解除只读

切换到浏览态时：
- 隐藏顶栏右侧按钮组
- 隐藏底部工具栏，显示底部动作栏
- 隐藏键盘
- `titleInput.isFocusableInTouchMode = false`
- 通知 presenter 设为只读
- 刷新动作栏收藏状态
- 调用 `presenter.flushPendingTextEdits()` + `saveNote()` 落库当前编辑

### 2.4 手写态

手写态是编辑态的子状态，行为不变：
- 进入手写态：隐藏 TextToolbarView，显示 HandwritingToolbarView
- 退出手写态：恢复 TextToolbarView
- 手写态不影响顶栏（↩↪✓ 仍显示）

## 3. 布局结构变更

### 3.1 旧布局 → 新布局

```
旧：
LinearLayout (editor_root)
  ├─ AppBarLayout (蓝色, fitsSystemWindows)
  │    ├─ Toolbar (蓝色)
  │    └─ notebook_indicator (蓝色内部)
  ├─ NestedScrollView (weight=1)
  │    └─ FrameLayout
  │         ├─ editor_content (title + metadata + blocks)
  │         └─ HandwritingOverlayView
  ├─ TextToolbarView (48dp, 灰色bg)
  └─ HandwritingToolbarView (48dp, 灰色bg, GONE)

新：
LinearLayout (editor_root, fitsSystemWindows=true)
  ├─ top_bar (LinearLayout horizontal, 白色)
  │    ├─ btn_back (ImageView)
  │    └─ edit_actions (LinearLayout horizontal, 编辑态才可见)
  │         ├─ btn_undo (ImageView)
  │         ├─ btn_redo (ImageView)
  │         └─ btn_done (ImageView, #007DFF tint)
  ├─ NestedScrollView (weight=1)
  │    └─ FrameLayout
  │         ├─ editor_content
  │         │    ├─ title_input
  │         │    ├─ metadata_strip (时间 + 笔记本指示器)  ← 指示器移到这里
  │         │    └─ blocks_container
  │         └─ HandwritingOverlayView
  ├─ TextToolbarView (图标+文字, 白色bg, 编辑态可见)
  ├─ HandwritingToolbarView (白色bg, GONE)
  └─ BrowseActionBar (图标+文字, 白色bg, 浏览态可见)
```

### 3.2 metadata_strip 改造

将笔记本指示器从 AppBar 移入 metadata_strip，与时间戳同行：

```
metadata_strip (LinearLayout horizontal)
  ├─ meta_time (TextView, 左对齐)
  ├─ spacer (weight=1)
  └─ notebook_indicator (LinearLayout horizontal, 右对齐)
       ├─ indicator_dot (View, 圆形)
       ├─ indicator_text (TextView, #007DFF)
       └─ indicator_arrow (ImageView, ▼)
```

分类指示器（category chip）保留在 metadata_strip 内但移到第二行或与笔记本合并。鉴于 M9 已有分类体系且编辑器已显示分类，保持现有 category chip（时间戳下方第二行），浏览态"更多"菜单也可改分类。

## 4. 组件变更清单

### 4.1 新增

| 文件 | 说明 |
|------|------|
| `res/layout/bar_browse_action.xml` | 浏览态底部动作栏布局（4 按钮图标+文字） |
| `res/menu/menu_editor_browse_more.xml` | "更多"PopupMenu（移动笔记本 / 设置分类） |
| `res/layout/bar_top_editor.xml` | 编辑器顶栏（←返回 + ↩↪✓） |

### 4.2 修改

| 文件 | 变更 |
|------|------|
| `activity_note_editor.xml` | 去 AppBarLayout/Toolbar，换 top_bar + 底部三栏（text/handwriting/browse） |
| `NoteEditorActivity.kt` | 新增 `isEditing` 状态 + `enterEditMode()` / `exitEditMode()` + 浏览态按钮逻辑 + 去 Toolbar `setSupportActionBar` / `onCreateOptionsMenu` / `onPrepareOptionsMenu` / `onOptionsItemSelected` / `applyMenuIconAlpha`；保留 `ViewCompat.setOnApplyWindowInsetsListener` IME inset 处理 |
| `toolbar_text.xml` | 5 按钮改为图标+文字（LinearLayout vertical per button） |
| `TextToolbarView.kt` | 适配新布局（找 id 变化） |
| `EditorPresenter.kt` | 新增 `var isReadOnly: Boolean` + `setReadOnly(Boolean)` 方法，遍历已有 BlockView 设置可编辑性；新增块时根据 `isReadOnly` 决定初始状态。ChecklistBlockView 特殊处理：checkbox 始终 enabled，仅文本/删除/分裂受 readOnly 控制 |
| `res/values/strings.xml` | 新增动作栏相关字符串（分享/收藏/取消收藏/删除/更多/移动笔记本/设置分类等） |
| `res/values/colors.xml` | 新增 `toolbar_bg_white` 若需要 |

### 4.3 删除

| 文件 | 原因 |
|------|------|
| `res/menu/menu_editor.xml` | undo/redo 从 AppBar options menu 迁到顶栏专用 ImageView |

## 5. 不动的东西（明确边界）

- **EditorPresenter 核心逻辑** — toggleInline / toggleSize / pickColor / toggleHeading / insert* / onRequestDelete / onRequestSplitAfter / history / focusLastTextBlock — 一行不改
- **StylePickerBottomSheet** — M13c 处理增强，M13b 不动
- **HandwritingOverlayView / BrushPainter** — 不动
- **HandwritingStylePickerBottomSheet** — 不动
- **AudioRecordingBottomSheet / AudioBlockView** — 不动
- **数据层（NoteRepository / FolderRepository / NotebookRepository / NoteDbHelper）** — 不动
- **NoteListActivity / NoteListAdapter / FilterPanelAdapter** — 不动
- **测试** — 140 项保持 PASS，无新增单测（UI 表现层改动）

## 6. 后续里程碑

| 轮次 | 范围 | 依赖 |
|------|------|------|
| M13c | 样式增强（对齐 + 列表 + 字号滑块 + 背景纹理） | M13b 编辑器 |
| M13d | 宫格视图 + 批量删除 | M13a 列表页 |
| M13e | 分享功能（分享为图片/文本/文档） | M13b 编辑器 |
