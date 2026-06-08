# Phase 3A：Flutter 待办列表页 设计文档

## 概述

将 Flutter 端的 `TodoPlaceholderPage` 替换为完整的待办列表页，对标 Android 原版 `TodoListFragment` 的全部交互功能。

**目标**：用户可以在待办 tab 查看、新增、完成、筛选、批量删除待办事项，体验与 Android 原版一致。

**前提**：数据层（`Todo` model、`TodoRepository`、`FolderRepository`）已完成，本 Phase 只涉及 UI 层和状态管理。

---

## 1. 页面总体布局

```
┌─────────────────────────────────┐
│  全部待办 ▼         (⋮)        │ ← Header
│  3 条待办                       │
├─────────────────────────────────┤
│                                 │
│  已过期（红色标题）              │ ← Section Headers
│  ┌──────────────────────────┐  │
│  │ ○  ❗标题    下午4:09    │  │ ← TodoCard（过期时间红色）
│  └──────────────────────────┘  │
│                                 │
│  今天                           │
│  ┌──────────────────────────┐  │
│  │ ○  标题     下午2:30     │  │
│  └──────────────────────────┘  │
│                                 │
│  已完成                         │
│  ┌──────────────────────────┐  │
│  │ ✓  ~~标题~~              │  │ ← 灰色+删除线
│  └──────────────────────────┘  │
│                                 │
│                         [+]     │ ← FAB
├─────────────────────────────────┤
│   笔记    |    待办             │ ← BottomNav
└─────────────────────────────────┘
```

---

## 2. Header 区域

### 2.1 默认状态
- 左侧大标题："全部待办"（或当前筛选名称）
- 标题右侧下箭头图标 ▼
- 标题下方副标题："N 条待办"
- 右上角溢出菜单按钮 (⋮)

### 2.2 交互
- **点击标题区域** → 切换筛选面板显示/隐藏
- 展开时箭头旋转 180° 变为 ▲
- 展开筛选面板时隐藏列表、FAB、溢出按钮

---

## 3. 分组列表

### 3.1 分组逻辑（与 Android `TodoListAdapter.groupTodos()` 一致）

根据 `remindAt` 和 `isCompleted` 将待办分为 6 组：

| 组名 | 条件 | 标题颜色 |
|------|------|----------|
| 已过期 | `remindAt > 0 && remindAt < todayStart && !isCompleted` | 红色 (#E53935) |
| 今天 | `remindAt >= todayStart && remindAt < tomorrowStart && !isCompleted` | 默认灰色 |
| 明天 | `remindAt >= tomorrowStart && remindAt < dayAfterTomorrow && !isCompleted` | 默认灰色 |
| 更晚 | `remindAt >= dayAfterTomorrow && !isCompleted` | 默认灰色 |
| 无日期 | `remindAt == 0 && !isCompleted` | 默认灰色 |
| 已完成 | `isCompleted == true` | 默认灰色 |

- `todayStart`：当天 00:00:00 的毫秒时间戳
- 空组不显示 header

### 3.2 排序
数据库查询按 `remind_at ASC, created_at DESC` 排序。

---

## 4. TodoCard 组件

### 4.1 布局
```
┌─────────────────────────────────────────┐
│  ○   ❗标题文字                          │
│      ⏰ 下午1:51 | 每天                  │
└─────────────────────────────────────────┘
```

- 左侧：圆形 checkbox
  - 未完成：空心灰色圆圈
  - 已完成：蓝色实心对勾圆圈
- 中间上方：标题
  - 重要项：标题前加红色 "❗" 前缀
  - 已完成：灰色文字 + 删除线
- 中间下方（可选）：副标题
  - 有 `remindAt`：显示时间（格式 "下午1:51"）
  - 有 `repeatType`：显示 "| 每天/每周/每月/每年"
  - 过期项：时间文字为红色
- 卡片背景：白色圆角矩形，有轻微阴影

### 4.2 交互
- **点击 checkbox** → 切换完成状态
  - 未完成→完成：淡出 + 右移动画（300ms），动画结束后刷新列表
  - 已完成→未完成：直接刷新列表
  - 有重复的待办：完成时不标记完成，而是推进 `remindAt` 到下一个周期
- **点击卡片** → 进入详情页（Phase 3B，暂时无操作或显示 TODO toast）

---

## 5. QuickAddBar

### 5.1 触发
- 点击 FAB → 隐藏 FAB、隐藏底部导航栏、显示 QuickAddBar、输入框获取焦点、弹出键盘

### 5.2 布局
```
┌─────────────────────────────────────────────┐
│  [ 待办事项_______________]                  │
│  🕐   ❗   [重复]            [保存]          │
└─────────────────────────────────────────────┘
```

- 输入框：单行，hint "待办事项"
- 功能行：
  - 🕐 时间图标（点击弹出时间选择器）
  - ❗ 重要图标（点击切换重要状态，选中时变蓝色）
  - "重复" 文字标签（仅在已选择时间后显示，点击弹出重复选择器）
  - "保存" 蓝色按钮（右对齐）

### 5.3 状态
- `quickAddRemindAt`：选中的提醒时间（0 = 未设置）
- `quickAddIsImportant`：是否标记重要
- `quickAddRepeatType`：重复类型（默认 none）

### 5.4 保存逻辑
1. 标题为空时不保存
2. 根据当前筛选确定 `folderId`（筛选为文件夹时归入该文件夹，否则 null）
3. 调用 `TodoRepository.insert()`
4. 保存成功后隐藏 QuickAddBar，恢复 FAB 和底部导航，刷新列表

### 5.5 关闭
- 保存后自动关闭
- 点击外部区域不关闭（保持 Android 原版行为）

---

## 6. 筛选面板

### 6.1 触发
点击 Header 标题区域展开/收起。

### 6.2 内容
```
┌─────────────────────────────────────────┐
│  📋 全部待办                     3      │ ← 蓝色高亮当前选中
│  📄 未分类                       3      │
│  🗑️ 最近删除                     0      │
│  ─────────────────────────────────────  │
│  文件夹                        管理      │ ← 标题 + 蓝色"管理"按钮
│  📁 我的待办                     ∧      │
│     🔖 工作                      0      │
│     🔖 个人                      0      │
│     🔖 购物                      0      │
│     📄+ 新建                            │
└─────────────────────────────────────────┘
```

### 6.3 筛选类型（对标 `TodoListFilter` sealed class）
- `All`：全部待办（deleted_at = 0）
- `Uncategorized`：未分类（deleted_at = 0 AND folder_id IS NULL）
- `Deleted`：最近删除（deleted_at > 0）
- `ByFolder(folderId)`：按文件夹筛选

### 6.4 交互
- 点击任一筛选项 → 切换当前筛选，收起面板，刷新列表
- 点击"管理" → 打开文件夹管理页（Phase 3A 暂不实现管理页，仅预留跳转）
- 当前选中项蓝色高亮背景
- 每项右侧显示待办计数

### 6.5 持久化
筛选状态保存到 SharedPreferences：
- `todo_filter_type`：ALL / UNCATEGORIZED / DELETED / FOLDER
- `todo_filter_folder_id`：文件夹 ID（仅 FOLDER 类型时使用）

---

## 7. 溢出菜单

### 7.1 触发
点击右上角 (⋮) 按钮。

### 7.2 菜单项
- **隐藏已完成待办** / **显示已完成待办**（切换状态）
- **批量删除**

### 7.3 实现
使用 `PopupMenuButton` 或自定义 `showMenu()`。

---

## 8. 批量模式

### 8.1 进入
通过溢出菜单"批量删除"触发。

### 8.2 UI 变化
- Header 标题变为 "已选择 N 项"（N 实时更新）
- Header 左侧箭头变为 ✕ 关闭图标
- 隐藏：溢出按钮、FAB、QuickAddBar、底部导航
- 显示：每行出现圆形 checkbox、底部"删除"按钮
- TodoCard 点击变为切换选中状态

### 8.3 删除确认
点击底部"删除"按钮 → 弹出 `DeleteConfirmSheet`（复用已有组件）：
- 消息："确定删除选中的 N 条待办？"
- 确认按钮："删除"
- 确认后：批量软删除 → 退出批量模式 → 刷新列表

### 8.4 退出
点击 ✕ 图标退出批量模式，恢复正常 UI。

---

## 9. 删除视图（最近删除）

### 9.1 触发
筛选面板选择"最近删除"。

### 9.2 UI 差异
- 无 checkbox（不可切换完成状态）
- 无 FAB
- 每行右侧显示两个操作按钮：
  - "恢复"：调用 `TodoRepository.restore(id)` → 刷新
  - "彻底删除"：弹出确认 → 调用 `TodoRepository.deletePermanently(id)` → 刷新

---

## 10. DateTimePicker（时间选择弹窗）

### 10.1 样式
- BottomSheet 形式弹出
- 顶部标题行显示选中的完整日期："2026年6月4日星期四"
- 使用 `CupertinoDatePicker`（mode: dateAndTime）
- 底部：取消 / 确定 按钮

### 10.2 默认值
- 如果已有提醒时间，默认定位到已选时间
- 如果是新选择，默认定位到当前时间往后 1 小时（分钟取整到 5 的倍数）

---

## 11. RepeatPicker（重复选择弹窗）

### 11.1 样式
- BottomSheet + 单选列表
- 标题："重复"
- 选项：不重复 / 每天 / 每周 / 每月 / 每年
- 选中项右侧蓝色实心圆点
- 底部"取消"按钮

---

## 12. 空状态

当筛选结果为空时：
- 隐藏列表
- 居中显示提示文案（如 "暂无待办" 或 "暂无已删除待办"）

---

## 13. 状态管理

### 13.1 TodoListProvider（Riverpod StateNotifier）

```
State:
  - currentFilter: TodoListFilter (All/Uncategorized/Deleted/ByFolder)
  - hideCompleted: bool
  - todos: List<Todo>
  - isBatchMode: bool
  - selectedIds: Set<int>
  - isQuickAddVisible: bool
  - quickAddRemindAt: int
  - quickAddIsImportant: bool
  - quickAddRepeatType: RepeatType
  - filterPanelVisible: bool
```

### 13.2 方法
- `reload()` — 根据 currentFilter + hideCompleted 重新查询
- `setFilter(filter)` — 切换筛选并持久化
- `toggleHideCompleted()` — 切换隐藏已完成
- `toggleComplete(todoId)` — 切换完成状态（处理重复逻辑）
- `enterBatchMode()` / `exitBatchMode()`
- `toggleBatchSelection(todoId)`
- `batchDelete()` — 批量软删除
- `saveQuickAdd(title)` — 快速新增
- `restore(todoId)` / `deletePermanently(todoId)`

---

## 14. 文件结构

| 文件 | 职责 |
|------|------|
| `lib/pages/todo_list_page.dart` | 待办列表页主体（替换 placeholder） |
| `lib/providers/todo_list_provider.dart` | 状态管理 |
| `lib/widgets/todo/todo_card.dart` | 单条待办卡片 |
| `lib/widgets/todo/todo_section_header.dart` | 分组标题 |
| `lib/widgets/todo/todo_quick_add_bar.dart` | 快速新增栏 |
| `lib/widgets/todo/todo_filter_panel.dart` | 筛选面板 |
| `lib/widgets/todo/date_time_picker_sheet.dart` | 时间选择器 BottomSheet |
| `lib/widgets/todo/repeat_picker_sheet.dart` | 重复选择器 BottomSheet |
| `lib/widgets/todo/todo_list_header.dart` | Header 区域 |
| `lib/utils/todo_group_utils.dart` | 分组算法（纯函数，可单测） |

---

## 15. 依赖

- 已有：`TodoRepository`、`FolderRepository`、`Todo` model、`Folder` model、`DeleteConfirmSheet`
- 新增依赖：`shared_preferences`（筛选状态持久化）— 检查 pubspec 是否已有
- Flutter 内置：`CupertinoDatePicker`

---

## 16. 不在本 Phase 范围

- 待办详情编辑页（Phase 3B）
- 本地通知提醒（Phase 3C）
- 文件夹管理页（后续 Phase）
- 语音输入、位置功能
