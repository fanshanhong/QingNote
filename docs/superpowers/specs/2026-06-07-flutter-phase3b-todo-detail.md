# Phase 3B：Flutter 待办详情编辑页 设计文档

## 概述

实现待办详情/编辑页面，对标 Android 原版 `TodoDetailActivity`。支持新建和编辑两种模式，包含标题、提醒时间、重复方式、重要标记、备注、文件夹分类等字段。

**目标**：用户可以从列表点击卡片进入详情页编辑所有字段，也可以新建空白待办；退出时自动保存。

**前提**：
- `Todo` model、`TodoRepository`、`FolderRepository` 已完成
- `DateTimePickerSheet`、`RepeatPickerSheet`、`DeleteConfirmSheet` 已在 Phase 3A 实现
- 路由使用 go_router，已有 `/editor/:noteId` 的先例

---

## 1. 页面布局

```
┌─────────────────────────────────┐
│ ←                               │ ← 返回按钮
├─────────────────────────────────┤
│ 📁 未分类 ▼                     │ ← 文件夹指示器（可点击）
├─────────────────────────────────┤
│ ○  [标题输入框]                  │ ← 完成勾选 + 标题
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
│ 🔔 添加提醒            [×]     │ ← 提醒时间行
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
│ 🔁 重复           不重复 >      │ ← 重复方式行
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
│ ❗ 重要              [Switch]   │ ← 重要开关行
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
│ ≡  备注                         │ ← 备注输入（多行）
│    [备注内容]                    │
│                                 │
│                                 │
├─────────────────────────────────┤
│     分享      |      删除       │ ← 底部操作栏
└─────────────────────────────────┘
```

---

## 2. 路由

- 路径：`/todo/:todoId`
- `todoId = 0` → 新建模式
- `todoId > 0` → 编辑模式（从 DB 加载）
- 从 TodoListPage 的 TodoCard `onTap` 进入
- 从 QuickAdd 创建后也可直接打开（Phase 3A 暂未实现此跳转，暂不添加）

---

## 3. 功能详解

### 3.1 返回按钮
- 点击 → 自动保存 → `context.pop()`
- 如果新建模式且标题为空 → 不保存，直接返回

### 3.2 文件夹指示器
- 显示当前所属文件夹名，默认 "未分类"
- 点击弹出文件夹选择器（AlertDialog 单选列表）
- 选项：["未分类", ...所有文件夹名称]

### 3.3 完成勾选 + 标题
- 左侧圆形 Checkbox（同 TodoCard 的 check 样式）
- 右侧 TextField，placeholder "待办事项"
- 新建时自动聚焦标题

### 3.4 提醒时间行
- 左侧闹钟图标 + 文本
- 未设置时：灰色 "添加提醒"
- 已设置时：
  - 今天："下午4:06"（蓝色，若过期则红色）
  - 非今天："6月8日 上午9:00"（蓝色，若过期则红色）
- 右侧清除按钮 ×（仅在有值时显示）
- 点击行 → 弹出 DateTimePickerSheet
- 清除 → 同时重置 repeatType 为 none

### 3.5 重复方式行
- 左侧重复图标 + "重复" 文本
- 右侧当前值 + 箭头：不重复/每天/每周/每月/每年
- 点击 → 弹出 RepeatPickerSheet

### 3.6 重要开关
- 左侧感叹号图标 + "重要" 文本
- 右侧 Switch 控件（Material 风格）

### 3.7 备注
- 左侧横线图标 + "备注" 文本
- 下方多行 TextField，placeholder "添加备注"
- 无边框，自然扩展高度

### 3.8 底部操作栏
- 两个按钮均等宽：分享 | 删除
- 分享：title + memo 组合为文本，调用系统分享
- 删除：弹出 DeleteConfirmSheet → 确认后软删除 → pop 返回

---

## 4. 保存逻辑

参照 Android 原版 `onPause` 保存策略：

- 使用 `WidgetsBindingObserver` 监听 `didChangeAppLifecycleState`
- `deactivate` / `pop` 时触发保存
- 保存条件：
  - 新建模式：标题非空才保存（避免空白记录）
  - 编辑模式：始终保存当前状态
- 保存内容：title, memo, isCompleted, isImportant, remindAt, repeatType, folderId

---

## 5. 状态管理

使用 Riverpod `StateNotifierProvider.family` 模式（参照 `NoteEditorProvider`）：

```dart
final todoDetailProvider = StateNotifierProvider.family<TodoDetailNotifier, TodoDetailState, int>(...)
```

State 字段：
- `todo: Todo?` — 加载后的原始数据
- `title: String`
- `memo: String`
- `isCompleted: bool`
- `isImportant: bool`
- `remindAt: int`
- `repeatType: RepeatType`
- `folderId: int?`
- `folderName: String` — 当前文件夹显示名
- `isLoaded: bool`

---

## 6. 可复用组件

| 组件 | 来源 | 备注 |
|---|---|---|
| DateTimePickerSheet | Phase 3A | 原样复用 |
| RepeatPickerSheet | Phase 3A | 原样复用 |
| DeleteConfirmSheet | 公共组件 | 原样复用 |

---

## 7. 新增文件

| 文件 | 职责 |
|---|---|
| `lib/pages/todo_detail_page.dart` | 详情页 UI |
| `lib/providers/todo_detail_provider.dart` | 状态管理 |

路由修改：`lib/router.dart` 添加 `/todo/:todoId` 路由。

---

## 8. 与列表页的联动

- TodoListPage 的 TodoCard `onTap` → `context.push('/todo/${todo.id}')`
- 返回时 TodoListPage 自动 reload（通过 `ref.invalidate` 或 `addPostFrameCallback`）
- 新建场景：FAB → QuickAdd 保存后可以打开详情（本期暂不实现，保持当前 QuickAdd 行为）
