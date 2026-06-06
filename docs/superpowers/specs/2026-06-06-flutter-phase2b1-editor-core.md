# Phase 2B-1 设计文档 — 编辑器核心（文本 + 格式化）

> 对标 Android NoteEditorActivity + EditorPresenter + TextBlockView + TextToolbar + StylePickerBottomSheet

**目标：** 将 Android 原生编辑器的核心文本编辑能力迁移到 Flutter，使用 appflowy_editor 作为富文本引擎，实现浏览/编辑双模式、样式面板、保存/加载流程。

**架构：** 方案 A — 全面依赖 appflowy_editor。EditorState 作为文档的单一真相源，Riverpod NoteEditorNotifier 只管非编辑器状态（模式切换、保存、metadata）。

**技术栈：** Flutter + appflowy_editor + Riverpod (StateNotifier) + go_router + sqflite

---

## 1. 数据模型与集成架构

### 1.1 content_json 格式切换

DB 的 `content_json` 列存储格式从 Phase 1 自定义格式切换为 appflowy Document 包装格式。不需要新增列或改 DB schema。

**现有格式（Phase 1 自定义）：**

```json
{
  "blocks": [
    {"type": "text", "id": "b-xxx", "text": "Hello", "spans": [...], "heading": "h1"},
    {"type": "image", "id": "i-xxx", "fileName": "abc.jpg"},
    {"type": "checklist", "id": "c-xxx", "items": [...]}
  ],
  "handwriting": {
    "strokes": [...]
  }
}
```

**新格式（appflowy Document + 手写包装）：**

```json
{
  "document": {
    "type": "page",
    "children": [
      {
        "type": "heading",
        "data": {"level": 1, "delta": [{"insert": "Hello"}]}
      },
      {
        "type": "image",
        "data": {"url": "local://abc.jpg", "width": 800, "height": 600}
      },
      {
        "type": "todo_list",
        "data": {"checked": false, "delta": [{"insert": "Item"}]}
      }
    ]
  },
  "handwriting": {
    "strokes": [
      {"brush": "pen", "color": "#212121", "width": 3, "points": [[x, y, t], ...]}
    ]
  }
}
```

### 1.2 Model 层变更

**NoteContent 类重构：**

- `NoteContent.documentJson` — `Map<String, dynamic>`，appflowy Document 原始 JSON
- `NoteContent.handwriting` — `List<Stroke>`，保持不变
- `NoteContent.toPlainText()` — 遍历 appflowy Document 节点提取纯文本
- `NoteContent.toJson()` / `NoteContent.fromJson()` — 序列化包装格式

**移除的类（不再需要）：**

- `Block` sealed class 及其子类（TextBlock / ImageBlock / ChecklistBlock / AudioBlock）— appflowy 有自己的 Node 体系
- `NoteTextSpan` / `SpanType` — appflowy 用 Delta attributes

**保留的类：**

- `Stroke` / `StrokePoint` / `BrushType` — 手写功能自有模型
- `Note` — 仅 `content` 字段从现有 NoteContent 变为新 NoteContent
- `Heading` / `NoteAlignment` / `ListType` — 保留枚举定义（样式面板映射 appflowy attributes 时使用）

### 1.3 状态管理架构

**两层状态：**

```
┌─────────────────────────────────────────────┐
│  appflowy_editor EditorState               │
│  ├─ Document（Node 树）                     │
│  ├─ Selection（选区）                       │
│  ├─ UndoManager（撤销重做）                 │
│  └─ 格式化操作（通过 Transaction API）       │
├─────────────────────────────────────────────┤
│  NoteEditorNotifier (Riverpod StateNotifier)│
│  ├─ noteId: int                             │
│  ├─ isEditing: bool（浏览/编辑模式）         │
│  ├─ loadedNote: Note?（当前笔记元数据）      │
│  ├─ pendingNotebookId: int?                 │
│  ├─ pendingBackground: String               │
│  ├─ isSaving: bool                          │
│  └─ editorState: EditorState（appflowy）     │
└─────────────────────────────────────────────┘
```

**数据流：**

1. 打开笔记 → DB 读取 → NoteContent.fromJson → 创建 appflowy Document → 构建 EditorState
2. 编辑中 → appflowy EditorState 直接管理文档变更 + undo/redo
3. 保存 → EditorState.document 序列化 + 手写 strokes → NoteContent.toJson → DB
4. 退出页面 → 自动保存（dispose 时触发）

### 1.4 路由集成

- 新路由：`/editor/:noteId`（noteId=0 表示新建笔记）
- 从 note_list_page 的 NoteCard.onTap 跳转（替换现有 SnackBar 占位）
- 新建笔记：从 FAB 跳转到 `/editor/0`
- go_router 配置新增 GoRoute

---

## 2. 编辑器页面 UI 布局

### 2.1 页面结构（浏览模式）

```
┌────────────────────────────────────┐
│  ← (返回按钮)                       │
├────────────────────────────────────┤
│  ● 工作笔记          (NotebookIndicator)
├────────────────────────────────────┤
│  笔记标题              (只读 Text)   │
├────────────────────────────────────┤
│  3 分钟前 · ○ 未分类  (MetadataStrip)│
├────────────────────────────────────┤
│                                    │
│  笔记正文内容                       │
│  浏览模式下不可编辑                  │
│  点击任意位置进入编辑模式            │
│                                    │
├────────────────────────────────────┤
│  📤分享  ☆收藏  🗑删除  ⋯更多     │
│         (BrowseBottomBar)          │
└────────────────────────────────────┘
```

### 2.2 页面结构（编辑模式）

```
┌────────────────────────────────────┐
│  ←     (空白)      ↶  ↷  ✓        │
│         (EditorTopBar)             │
├────────────────────────────────────┤
│  ● 工作笔记 ▼    (可点击切换笔记本) │
├────────────────────────────────────┤
│  笔记标题|          (可编辑 TextField)│
├────────────────────────────────────┤
│  刚刚 · ○ 未分类  (MetadataStrip)  │
├────────────────────────────────────┤
│                                    │
│  appflowy_editor 渲染区域           │
│  支持富文本编辑、标题、列表等        │
│                                    │
├────────────────────────────────────┤
│  ☑    Aa    🖼    ✏    🎙          │
│        (TextToolbar 5按钮)          │
└────────────────────────────────────┘
```

### 2.3 交互流程（对标 Android NoteEditorActivity）

**打开已有笔记（noteId > 0）：**

1. 进入 → 浏览模式（只读）
2. 底部显示「分享 | 收藏 | 删除 | 更多」栏
3. 标题不可聚焦，编辑器 readOnly = true
4. 点击内容区域 → 切换到编辑模式

**切换到编辑模式：**

1. 顶栏显示 Undo / Redo / ✓ 完成
2. 底部切换为文本工具栏（5 按钮）
3. 标题变为可编辑 TextField
4. appflowy_editor 切换 editable = true
5. 光标定位到文档末尾

**点击"完成"退出编辑模式：**

1. 隐藏键盘
2. 切换回浏览模式
3. 触发保存

**新建笔记（noteId = 0）：**

1. 直接进入编辑模式
2. 空文档 + 光标聚焦到标题

**退出页面（返回键 / back 按钮）：**

1. 自动保存（同 Android onPause）
2. 全空且新笔记 → 不保存、不创建记录
3. 返回列表页

### 2.4 Widget 结构

```
NoteEditorPage (ConsumerStatefulWidget)
├── Scaffold
│   └── Column
│       ├── _EditorTopBar（← Back | Undo | Redo | ✓ Done）
│       ├── _NotebookIndicator（彩色圆点 + 名称，编辑模式可点击选择）
│       ├── _TitleInput（TextField，浏览态禁用）
│       ├── _MetadataStrip（时间 · 分类，分类可点击弹 picker）
│       ├── Expanded
│       │   └── GestureDetector（空白区点击进入编辑）
│       │       └── AppFlowyEditor（appflowy_editor widget）
│       ├── if (isEditing) _TextToolbar（5 按钮）
│       └── if (!isEditing) _BrowseBottomBar（分享|收藏|删除|更多）
│
├── StylePickerBottomSheet（由工具栏"样式"按钮触发）
└── NotebookPickerPopup（由 indicator 点击触发）
```

---

## 3. 工具栏与样式面板

### 3.1 文本工具栏（TextToolbar — 编辑模式底部）

底部 5 按钮，左右等距排列：

| 按钮 | 图标 | Phase 2B-1 行为 |
|------|------|----------------|
| 清单 | ☑ | 在光标位置插入 todo_list 节点（2B-1 仅创建节点，2B-2 完善交互） |
| 样式 | **Aa** | 弹出 StylePickerBottomSheet |
| 图片 | 🖼 | SnackBar 占位（"图片功能将在后续版本实现"） |
| 手写 | ✏️ | SnackBar 占位（"手写功能将在后续版本实现"） |
| 录音 | 🎙 | SnackBar 占位（"录音功能将在后续版本实现"） |

### 3.2 样式面板 BottomSheet（StylePickerBottomSheet — 6 行）

对标 Android `StylePickerBottomSheet`（220 行），点击 Aa 按钮触发。

**Row 1 — 内联格式 + 对齐：**

- 左侧：**B** / *I* / U / ~~S~~（各一个 toggle 按钮）
- 右侧：左对齐 / 居中 / 右对齐（单选组）
- API：`toggleAttribute('bold' / 'italic' / 'underline' / 'strikethrough')`
- API：`updateNode: {'align': 'left' / 'center' / 'right'}`

**Row 2 — 缩进 + 列表：**

- 左侧：增加缩进 / 减少缩进
- 右侧：1. 数字 / a. 字母 / • 实心 / ○ 空心（4种列表类型）
- API：indent / outdent command
- API：`changeNodeType('numbered_list' / 'bulleted_list')`

**Row 3 — 字号滑块：**

- 5 档：xs (12px) / 小 (14px) / 中 (16px, 默认) / 大 (20px) / xl (24px)
- Slider 控件，选中后应用到当前选区
- API：`formatDelta({'fontSize': px})`

**Row 4 — 文字颜色（7 色）：**

- 红 #E53935 / 橙 #FB8C00 / 绿 #43A047 / 浅蓝 #29B6F6 / 蓝 #1E88E5 / 紫 #AB47BC / 黑 #212121（默认）
- 圆形色块，选中显示蓝色边框
- API：`formatDelta({'color': '#hex'})`

**Row 5 — 标题级别：**

- H1 / H2 / H3 / H4 / H5 / H6（6 个按钮，字号递减）
- 选中后切换当前段落为 heading 节点
- API：`changeNodeType('heading', {'level': 1..6})`

**Row 6 — 背景纹理：**

- 纯白 / 亚麻 (#f5e6d0) / 牛皮 (#d4b896) / 网格 (#f0f0f0)
- 方形预览块，选中显示蓝色边框
- 这是笔记级别属性，非 appflowy_editor 内部状态
- 更新 `pendingBackground` → 保存时写入 Note.background

### 3.3 格式化操作映射（Android → appflowy_editor）

| Android 操作 | appflowy_editor API |
|---|---|
| B / I / U / S | `toggleAttribute('bold' / 'italic' / 'underline' / 'strikethrough')` |
| 对齐 (左/中/右) | `updateNode: {'align': 'left' / 'center' / 'right'}` |
| 缩进 +/- | indent / outdent command |
| 列表 (1. a. • ○) | `changeNodeType('numbered_list' / 'bulleted_list')` |
| 字号 | `formatDelta({'fontSize': px})` |
| 颜色 | `formatDelta({'color': '#hex'})` |
| H1-H6 | `changeNodeType('heading', {'level': 1..6})` |
| 背景纹理 | Note.background 属性（非 editor 内部状态） |

> 注：appflowy_editor 的具体 API 名称需要在实施阶段查阅最新文档确认。上表为设计意图映射。

---

## 4. 保存流程与浏览模式

### 4.1 保存流程（对标 Android saveNote / onPause）

**触发时机：**

1. 点击 ✓ 完成按钮 → exitEditMode()
2. 按返回键 / 侧滑返回 → PopScope 拦截
3. 页面 dispose → 自动保存（同 Android onPause）

**保存逻辑：**

1. 从 EditorState.document 序列化为 JSON（appflowy 格式）
2. 合并 handwriting strokes → 组装 content_json 包装对象
3. 从 TitleInput 读取标题
4. 调用 NoteRepository.save(note)：
   - noteId == 0 → INSERT（返回新 ID）
   - noteId > 0 → UPDATE
5. 更新 Notifier 中的 noteId（新建笔记首次保存后）

**空笔记保护（对标 Android）：**

标题为空 且 文档内容为空 且 新建笔记（noteId == 0）→ **不保存、不创建记录**，直接返回列表页。

**保存状态控制：**

- `isSaving = true` → 防止重复保存（简单 flag，不做 UI 反馈）
- 保存完成 → 重置 isSaving

### 4.2 浏览模式底部操作栏（BrowseBottomBar）

4 个按钮，等距排列：

| 按钮 | Phase 2B-1 行为 |
|------|----------------|
| 📤 分享 | `Share.share(plainText)` — 调用系统分享面板 |
| ☆ 收藏 | toggle isFavorite → 更新 DB + 切换图标（☆ ↔ ★） |
| 🗑 删除 | 弹确认 Dialog → softDelete → 返回列表 |
| ⋯ 更多 | SnackBar 占位（Phase 3 实现菜单） |

### 4.3 笔记本指示器 + 选择器

**NotebookIndicator（编辑器顶部）：**

- 始终显示当前笔记所属笔记本（彩色圆点 + 名称 + ▼ 箭头）
- 仅编辑模式下可点击（浏览模式点击无效）
- 点击弹出 PopupMenu

**NotebookPickerPopup：**

- 使用 Flutter 内置 PopupMenuButton
- 列出所有笔记本（彩色圆点 + 名称），当前选中项显示 ✓
- 选择后 → 更新 pendingNotebookId（保存时写入 DB）

### 4.4 元数据条（MetadataStrip）

显示：`{相对时间} · ○ {分类名}`

**时间格式（对标 Android timeAgo 逻辑）：**

- <1分钟 → "刚刚"
- <1小时 → "X分钟前"
- <24小时 → "X小时前"
- <2天 → "昨天"
- <7天 → "X天前"
- ≥7天 → 具体日期（MM/dd）

**分类：**

- 显示 categoryName，无分类显示"未分类"
- 点击弹 BottomSheet 选择分类（复用列表页的分类选择器逻辑）
- 新建笔记 → "刚刚" + "未分类"

---

## 5. Phase 2B-1 范围边界

### 5.1 包含（本次实施范围）

- ✅ 编辑器页面 UI（浏览/编辑双模式）
- ✅ appflowy_editor 集成（文本编辑 + 格式化 + undo/redo）
- ✅ 标题输入 + 元数据条
- ✅ 文本工具栏（清单 + 样式按钮功能，图片/手写/录音占位）
- ✅ 样式面板 BottomSheet（全部 6 行）
- ✅ 保存/加载流程 + 空笔记保护
- ✅ 浏览模式底部栏（分享/收藏/删除 功能，更多占位）
- ✅ 笔记本指示器 + PopupMenu 选择器
- ✅ 分类选择（MetadataStrip 点击）
- ✅ 路由集成（/editor/:noteId）
- ✅ NoteContent JSON 格式切换（→ appflowy document 包装格式）

### 5.2 不包含（后续子阶段）

- ❌ 图片块渲染与插入（Phase 2B-2）
- ❌ 清单块完整交互 — 工具栏按钮可点击创建 todo_list 节点，但复杂交互（Enter 新建项、Backspace 退出等）在 2B-2（Phase 2B-2）
- ❌ 音频块录制与播放（Phase 2B-3）
- ❌ 手写覆盖层 + 手写工具栏（Phase 2B-4）

### 5.3 与 Phase 2A 的衔接

- Phase 2A 已实现：列表页 + 笔记本管理 + 分类管理 + 待办 + 路由框架
- Phase 2B-1 在 Phase 2A 基础上：
  - 新增 `/editor/:noteId` 路由
  - 列表页 NoteCard.onTap 从 SnackBar 改为跳转编辑器
  - FAB 从 SnackBar 改为跳转 `/editor/0`
  - pubspec.yaml 新增依赖：appflowy_editor、share_plus（分享功能）
