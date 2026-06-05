# M14 待办子系统 · 设计文档

- 文档版本：v1.0
- 日期：2026-06-05
- 状态：待审阅
- 前置依赖：M13c 完成（当前 HEAD）
- 子里程碑：M14a / M14b / M14c

---

## 1. 功能概述

为 HwNote 备忘录 app 新增**待办（Todo）子系统**，对标华为备忘录的待办 tab。用户可在底部导航栏的"待办"tab 中创建、管理待办事项，支持提醒通知、重复规则、重要标记、文件夹分类、批量删除和软删除回收站。

### 1.1 范围内

| 功能 | 说明 |
|---|---|
| 待办 CRUD | 新建、编辑、完成、删除 |
| 列表页 | 按时间分组（已过期/今天/明天/无日期/已完成），圆形 checkbox |
| 详情编辑页 | 标题 + 提醒时间 + 重复 + 重要 + 备注（纯文本）|
| 新建待办栏 | 列表底部固定输入栏 + 2 个快捷按钮（时间/重要）|
| 自定义滚轮时间选择器 | 4 列 NumberPicker（日期/上午下午/小时/分钟）|
| 重复规则 | 不重复/每天/每周/每月/每年，完成后原地重置推进下一期 |
| 提醒通知 | AlarmManager 精确闹钟 + 系统通知（标记完成/10分钟后提醒）|
| 文件夹分类 | 复用现有 Folder 体系，筛选面板复用 FilterPanelAdapter 模式 |
| 批量删除 | 多选模式 + 二确认 |
| 隐藏已完成 | toggle 开关，持久化到 SharedPreferences |
| 软删除回收站 | 30 天保留，复用笔记的软删除模式 |
| Fragment 重构 | NoteListActivity 改为 Fragment 容器（NoteListFragment + TodoListFragment）|

### 1.2 范围外

| 功能 | 原因 |
|---|---|
| 位置提醒 | 需要地图/定位 SDK，本地离线 app 不做 |
| 语音输入 | 语音转文字需联网，不符合离线定位 |
| 农历 | 滚轮选择器不含农历切换 |
| 设置页 | overflow 菜单的"设置"项暂不做 |
| 分享功能 | 待办详情底部"分享"按钮 Toast 占位，与笔记分享功能统一后续实现 |

---

## 2. 数据模型

### 2.1 新表 todos（DB v5 迁移）

```sql
CREATE TABLE todos (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  title        TEXT    NOT NULL DEFAULT '',
  memo         TEXT    NOT NULL DEFAULT '',
  is_completed INTEGER NOT NULL DEFAULT 0,
  is_important INTEGER NOT NULL DEFAULT 0,
  remind_at    INTEGER NOT NULL DEFAULT 0,
  repeat_type  INTEGER NOT NULL DEFAULT 0,
  folder_id    INTEGER,
  deleted_at   INTEGER NOT NULL DEFAULT 0,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);
CREATE INDEX idx_todos_remind_at  ON todos(remind_at);
CREATE INDEX idx_todos_deleted_at ON todos(deleted_at);
```

字段说明：
- `remind_at`：epoch millis，0 = 无提醒
- `repeat_type`：0=不重复 / 1=每天 / 2=每周 / 3=每月 / 4=每年
- `folder_id`：外键指向 `folders.id`，NULL = 未分类
- `deleted_at`：0 = 未删除，正数 = 软删除时间戳

DB 迁移路径：`NoteDbHelper.onUpgrade(v4→v5)` 中 `CREATE TABLE todos` + 两个索引。

### 2.2 实体

```kotlin
data class Todo(
    val id: Long = 0L,
    val title: String = "",
    val memo: String = "",
    val isCompleted: Boolean = false,
    val isImportant: Boolean = false,
    val remindAt: Long = 0L,
    val repeatType: RepeatType = RepeatType.NONE,
    val folderId: Long? = null,
    val deletedAt: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long
)
```

```kotlin
enum class RepeatType(val value: Int) {
    NONE(0), DAILY(1), WEEKLY(2), MONTHLY(3), YEARLY(4);
    companion object {
        fun fromValue(v: Int): RepeatType =
            entries.firstOrNull { it.value == v } ?: NONE
    }
}
```

### 2.3 重复推进逻辑

当用户完成一个重复待办时（`repeatType != NONE`）：
1. **不标记完成**，保持 `isCompleted = false`
2. 将 `remindAt` 推进到下一期：
   - DAILY: +1 天
   - WEEKLY: +7 天
   - MONTHLY: Calendar.add(MONTH, 1)
   - YEARLY: Calendar.add(YEAR, 1)
3. 如果推进后的时间仍在过去，继续推进直到 ≥ 当前时间
4. 重新注册 AlarmManager 闹钟

非重复待办完成时：`isCompleted = true`，取消闹钟。

### 2.4 TodoRepository

```
TodoRepository (object 单例，与 NoteRepository 同级)
├── insert(todo): Long
├── update(todo)
├── getById(id): Todo?
├── list(filter, hideCompleted): List<Todo>
├── count(filter): Int
├── softDelete(id)
├── restore(id)
├── deletePermanently(id)
├── completeTodo(id)          -- 含重复推进逻辑
├── uncompleteTodo(id)
├── purgeExpired()            -- 清理 >30 天软删除
└── listPendingAlarms(): List<Todo>  -- remind_at > 0 且未完成未删除
```

---

## 3. M14a：数据层 + Fragment 重构 + 待办列表页

### 3.1 Fragment 重构

**现状：** NoteListActivity 直接管理笔记列表的全部 UI（大标题、搜索框、筛选面板、RecyclerView、FAB、底部导航）。

**重构方案：** NoteListActivity 保留为容器 Activity，管理底部导航 tab 切换。笔记列表逻辑整体搬入 NoteListFragment，新建 TodoListFragment 承载待办列表。

```
NoteListActivity (容器)
├── FrameLayout (fragment_container)  ← Fragment 切换区
├── BottomNavigationBar              ← 笔记 | 待办
│
├── NoteListFragment                 ← 原有笔记列表逻辑
│   ├── 大标题 + 搜索框
│   ├── 筛选面板
│   ├── RecyclerView
│   └── FAB (新建笔记)
│
└── TodoListFragment                 ← 新增待办列表
    ├── 大标题 ("全部待办 ▼") + 计数
    ├── 筛选面板 (复用 FilterPanelAdapter 模式)
    ├── RecyclerView (按时间分组)
    ├── FAB (新建待办)
    └── 底部新建栏 (QuickAddBar)
```

**切换方式：**
- `FragmentTransaction.replace(R.id.fragment_container, fragment)`
- 不用 ViewPager（不需要滑动切换）
- 当前 tab 持久化到 `SharedPreferences("active_tab")`，重启后恢复
- 底部导航高亮跟随当前 tab（笔记图标蓝色 / 待办图标蓝色）

**迁移要点：**
- NoteListActivity 中与笔记列表相关的字段、方法全部搬入 NoteListFragment
- Activity 保留：底部导航栏 + Fragment 切换逻辑 + onActivityResult 转发
- Fragment 使用 `requireActivity()` 获取 Context
- `startActivityForResult` 改为 Fragment 内发起，Activity 在 `onActivityResult` 中转发给当前 Fragment

### 3.2 TodoListFragment 列表页

**布局结构（与笔记列表页风格统一）：**

```
LinearLayout (vertical)
├── 标题区
│   ├── "全部待办 ▼" (大标题，点击展开筛选面板)
│   └── "N 条待办" (副标题)
├── 筛选面板 (RecyclerView, 默认隐藏)
├── RecyclerView (待办列表，按时间分组)
├── FAB (右下角 +)
└── QuickAddBar (底部新建栏，FAB 点击后显示)
```

**列表分组逻辑（TodoListAdapter）：**

adapter 使用多 ViewType 实现分组：
- `TYPE_SECTION_HEADER`：分组标题（"已过期" / "今天" / "明天" / "无日期" / "已完成"）
- `TYPE_TODO_ITEM`：待办卡片

分组排序规则：
1. **已过期**（remind_at > 0 且 remind_at < 今天开始，未完成）— 红色标题
2. **今天**（remind_at 在今天范围内，未完成）
3. **明天**（remind_at 在明天范围内，未完成）
4. **更晚**（remind_at > 明天结束，未完成）
5. **无日期**（remind_at == 0，未完成）
6. **已完成**（isCompleted == true，可隐藏）

每组内按 `remind_at ASC`（有时间的）或 `created_at DESC`（无时间的）排序。

**待办卡片布局（item_todo_card.xml）：**

```
CardView (白底, elevation=0dp, 圆角 12dp)
└── LinearLayout (horizontal, 垂直居中)
    ├── CheckBox (圆形, 24dp)  ← 自定义 drawable: 圆形边框 + 勾选态
    ├── LinearLayout (vertical, weight=1)
    │   ├── TextView (标题, 16sp)
    │   │   └── 重要时前缀红色 "❗"
    │   └── TextView (副信息, 13sp, 灰色)
    │       └── "下午1:51 | 每天" 或 空
    └── (无右侧元素)
```

样式变化：
- 已完成：标题灰色 + 删除线，checkbox 灰色打勾
- 已过期：时间文字红色
- 重要：标题前显示红色 "❗"

**点击交互：**
- 点击 checkbox → `TodoRepository.completeTodo(id)` → 刷新列表
- 点击卡片区域 → `startActivity(TodoDetailActivity.newIntent(id))`

### 3.3 新建待办栏（QuickAddBar）

列表底部固定的输入栏，初始隐藏，点击 FAB 后显示（FAB 同时隐藏）。

```
FrameLayout (底部固定, 白底, 顶部阴影)
├── EditText (placeholder "待办事项", 单行)
├── ImageButton (⏰ 时间)
├── ImageButton (❗ 重要)
└── Button ("保存", 蓝色圆角)
```

**交互流程：**
1. 点击 FAB → FAB 隐藏 + QuickAddBar 滑入 + EditText 自动聚焦弹出键盘
2. 点击 ⏰ → 弹出 DateTimePickerDialog → 选择后时间显示在输入栏上方
3. 点击 ❗ → toggle 重要状态（按钮高亮/取消）
4. 点击保存 → 创建 Todo + 注册闹钟（如有时间）+ 刷新列表 + 清空输入 + QuickAddBar 隐藏 + FAB 恢复
5. 点击外部区域或返回键 → QuickAddBar 隐藏 + FAB 恢复

### 3.4 筛选面板

复用笔记列表的 FilterPanelAdapter 模式，待办版筛选项：

| ViewType | 内容 | 图标 |
|---|---|---|
| 全部待办 | 所有未删除待办计数 | 📋 列表图标 |
| 未分类 | folder_id IS NULL 计数 | 📄 文档图标 |
| 最近删除 | deleted_at > 0 计数 | 🗑️ 垃圾桶图标 |
| 文件夹 header | "文件夹" + "管理" | — |
| 文件夹 item | 各 Folder 名称 + 计数 | 文件夹图标 + 颜色 |

新增 `TodoListFilter` sealed class（与笔记的 `ListFilter` 同模式）：

```kotlin
sealed class TodoListFilter {
    object All : TodoListFilter()
    object Uncategorized : TodoListFilter()
    object Deleted : TodoListFilter()
    data class ByFolder(val folderId: Long) : TodoListFilter()
}
```

持久化到 SharedPreferences，与笔记筛选独立（key = `"todo_filter_type"` / `"todo_filter_folder_id"`）。

---

## 4. M14b：详情页 + 时间选择器 + 提醒通知

### 4.1 TodoDetailActivity

全页编辑页面，布局从上到下：

```
LinearLayout (vertical, 白底)
├── 顶栏
│   └── ImageButton (← 返回)
├── ScrollView
│   └── LinearLayout (vertical, padding 16dp)
│       ├── 笔记本指示器 ("📋 未分类 ▼", 点击弹 Folder 选择)
│       ├── 标题行
│       │   ├── CheckBox (圆形, 24dp)
│       │   └── EditText (标题, 18sp, 单行)
│       ├── Divider
│       ├── 提醒行 (点击弹 DateTimePickerDialog)
│       │   ├── ImageView (🔔 图标)
│       │   ├── TextView ("下午4:06", 蓝色/红色/灰色)
│       │   └── ImageButton (✕ 清除, 仅有提醒时显示)
│       ├── Divider
│       ├── 重复行 (点击弹 RepeatPickerBottomSheet)
│       │   ├── ImageView (🔄 图标)
│       │   ├── TextView ("重复")
│       │   └── TextView ("不重复 >", 灰色, 右对齐)
│       ├── Divider
│       ├── 重要行
│       │   ├── ImageView (❗ 图标)
│       │   ├── TextView ("重要")
│       │   └── Switch (右对齐)
│       ├── Divider
│       └── 备注区
│           ├── ImageView (☰ 图标)
│           ├── TextView ("备注")
│           └── EditText (多行, 灰色 hint "添加备注")
└── 底部动作栏
    ├── 分享 (图标+文字, Toast 占位)
    └── 删除 (图标+文字, 红色)
```

**数据加载与保存：**
- `onCreate`：通过 Intent extra `"todo_id"` 加载 Todo（-1L = 新建）
- 新建时自动聚焦标题 EditText
- `onPause`：收集所有字段 → `TodoRepository.update()` 或 `insert()`
- 与笔记编辑器的 onPause 保存模式一致

**提醒时间显示规则：**
- 无提醒：灰色 "添加提醒"
- 有提醒且未过期：蓝色，格式 "6月5日 下午4:05"
- 有提醒且已过期：红色，同格式
- 点击 ✕ → 清除提醒时间 + 取消闹钟

**Folder 选择：**
复用笔记编辑器的 notebook indicator 点击模式，弹出 Folder 列表 PopupWindow（仅 Folder 层级，不含 Notebook）。

### 4.2 自定义滚轮时间选择器（DateTimePickerDialog）

BottomSheetDialog，内嵌 4 列 NumberPicker 滚轮：

```
BottomSheetDialog
└── LinearLayout (vertical)
    ├── TextView (顶部日期文字: "2026年6月5日星期四")
    ├── LinearLayout (horizontal, 4 列等宽)
    │   ├── NumberPicker (日期列: 今天 / 6月6日 / 6月7日 / ...)
    │   ├── NumberPicker (上午/下午)
    │   ├── NumberPicker (小时: 1-12)
    │   └── NumberPicker (分钟: 00-59)
    └── LinearLayout (horizontal, 底部按钮)
        ├── Button ("取消", 蓝色文字)
        └── Button ("确定", 蓝色文字)
```

**日期列实现：**
- 使用 NumberPicker + `setDisplayedValues()`
- 生成未来 365 天的日期字符串数组
- 第 0 项显示 "今天"，其余显示 "M月d日"
- `setWrapSelectorWheel(false)`（日期不循环）

**上午/下午列：**
- NumberPicker, min=0, max=1
- displayedValues = ["上午", "下午"]
- `setWrapSelectorWheel(false)`

**小时列：**
- NumberPicker, min=1, max=12
- `setWrapSelectorWheel(true)`（循环）

**分钟列：**
- NumberPicker, min=0, max=59
- `setFormatter { String.format("%02d", it) }`
- `setWrapSelectorWheel(true)`（循环）

**联动逻辑：**
- 日期列滚动时 → 更新顶部日期文字（含星期几）
- 点击确定 → 将 4 列值组合为 epoch millis 回调给调用者
- 回调接口：`onDateTimeSelected(epochMillis: Long)`

**初始值：**
- 编辑已有提醒：解析 `remindAt` 填充各列
- 新建提醒：默认当前时间向后取整到下一个 5 分钟

### 4.3 重复选择器（RepeatPickerBottomSheet）

简单的单选列表 BottomSheetDialog：

```
BottomSheetDialog
└── LinearLayout (vertical)
    ├── TextView ("重复", 18sp, 粗体, padding 16dp)
    ├── RadioGroup
    │   ├── RadioButton ("不重复")  ← 蓝色圆形选中态
    │   ├── RadioButton ("每天")
    │   ├── RadioButton ("每周")
    │   ├── RadioButton ("每月")
    │   └── RadioButton ("每年")
    ├── Divider
    └── Button ("取消", 蓝色文字, 居中)
```

- 打开时根据当前 `repeatType` 预选
- 点击任一选项 → 立即回调 `onRepeatSelected(RepeatType)` + dismiss
- 点击取消 → dismiss（不改变）

### 4.4 提醒通知系统（AlarmManager）

**架构：**

```
TodoAlarmManager (object 工具类, model/alarm/ 包)
├── scheduleAlarm(context, todo)
│   └── AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, remindAt, pendingIntent)
├── cancelAlarm(context, todoId)
│   └── AlarmManager.cancel(pendingIntent)
└── rescheduleAll(context)
    └── TodoRepository.listPendingAlarms() → forEach { scheduleAlarm }

TodoAlarmReceiver (BroadcastReceiver, controller/todo/ 包)
├── action = "com.fan.hwnote.ACTION_TODO_REMIND"
│   └── 发送 Notification
├── action = "com.fan.hwnote.ACTION_TODO_COMPLETE"
│   └── TodoRepository.completeTodo(id) + cancel notification
└── action = "com.fan.hwnote.ACTION_TODO_SNOOZE"
    └── scheduleAlarm(now + 10min) + cancel notification

TodoBootReceiver (BroadcastReceiver)
└── action = BOOT_COMPLETED → rescheduleAll()
```

**PendingIntent 策略：**
- requestCode = `todoId.toInt()`（区分不同待办的闹钟）
- `PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE`
- Intent extra 携带 `"todo_id"` 和 `"todo_title"`

**Notification 配置：**
- Channel ID: `"todo_reminders"`
- Channel 名称: "待办提醒"
- 重要性: `IMPORTANCE_HIGH`（弹出横幅）
- 内容：标题 = "备忘录"，文字 = todo.title
- 2 个 Action 按钮：
  - "标记完成" → PendingIntent → TodoAlarmReceiver(ACTION_TODO_COMPLETE)
  - "10分钟后提醒" → PendingIntent → TodoAlarmReceiver(ACTION_TODO_SNOOZE)
- 点击通知正文 → 打开 TodoDetailActivity

**AndroidManifest 新增声明：**

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<receiver android:name=".controller.todo.TodoAlarmReceiver"
    android:exported="false" />

<receiver android:name=".controller.todo.TodoBootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>

<activity android:name=".controller.todo.TodoDetailActivity" />
```

**权限处理：**
- `SCHEDULE_EXACT_ALARM`：Android 12+ 需要，通过 `AlarmManager.canScheduleExactAlarms()` 检查。如不可用，引导用户到设置页（`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`）
- `POST_NOTIFICATIONS`：Android 13+ 需运行时请求，首次设置提醒时触发

**App 启动时初始化：**
- `App.onCreate()` 中创建 NotificationChannel
- `App.onCreate()` 中调用 `TodoRepository.purgeExpiredTodos()` 清理过期软删除
- Boot 后 `TodoBootReceiver.onReceive()` 调用 `rescheduleAll()` 恢复所有闹钟

---

## 5. M14c：批量操作 + 软删除 + 打磨

### 5.1 批量删除模式

右上角 overflow 菜单点击"批量删除" → 进入多选模式：

**UI 变化：**
- 顶栏：大标题区隐藏 → 显示 "已选择 N 项" + ✕ 退出按钮
- 卡片：圆形 checkbox 变为方形多选框（`CheckBox` 替换，或叠加 UI 状态）
- 底部：新建栏隐藏 → 显示 "删除" 按钮（红色）
- FAB 隐藏

**交互：**
- 点击卡片 → toggle 选中状态，更新计数
- 点击 "删除" → `DeleteConfirmBottomSheet`（复用 M9 组件）二确认
- 确认后 → 批量 `softDelete(ids)` + 退出多选模式 + 刷新列表
- 点击 ✕ → 退出多选模式，恢复正常 UI

### 5.2 隐藏已完成待办

overflow 菜单中的 toggle 项：

- 状态持久化到 `SharedPreferences("hide_completed_todos", false)`
- 开启时：TodoListAdapter 过滤掉 `isCompleted == true` 的项，不显示"已完成"分组
- 关闭时：正常显示"已完成"分组
- 菜单文字动态切换："隐藏已完成待办" ↔ "显示已完成待办"

### 5.3 软删除回收站

复用笔记系统已有的软删除模式：

| 操作 | 行为 |
|---|---|
| 删除 | `deleted_at = now()`，取消闹钟 |
| 恢复 | `deleted_at = 0`，如有未过期提醒则重新注册闹钟 |
| 彻底删除 | `DELETE FROM todos WHERE id = ?` |
| 自动清理 | App 启动时 `purgeExpiredTodos()` 清理 `deleted_at > 0 且 > 30天` 的记录 |

筛选面板"最近删除"入口 → 列表显示已软删除的待办，卡片右侧显示"恢复"按钮。

### 5.4 Overflow 菜单

TodoListFragment 右上角 ⋮ 按钮 → PopupMenu：

| 菜单项 | 行为 |
|---|---|
| 隐藏/显示已完成待办 | toggle + 刷新列表 |
| 批量删除 | 进入多选模式 |

---

## 6. 包结构增量

```
com.fan.hwnote.app/
├─ model/
│  ├─ entity/
│  │   ├─ Todo.kt                  ← 新增
│  │   └─ RepeatType.kt            ← 新增 (enum)
│  ├─ alarm/
│  │   └─ TodoAlarmManager.kt      ← 新增
│  └─ TodoRepository.kt            ← 新增
│
├─ controller/
│  ├─ list/
│  │   ├─ NoteListActivity.kt      ← 改造为 Fragment 容器
│  │   ├─ NoteListFragment.kt      ← 新增 (从 Activity 搬入)
│  │   ├─ NoteListAdapter.kt       ← 不变
│  │   ├─ TodoListFragment.kt      ← 新增
│  │   └─ TodoListAdapter.kt       ← 新增
│  └─ todo/
│      ├─ TodoDetailActivity.kt    ← 新增
│      ├─ TodoAlarmReceiver.kt     ← 新增
│      └─ TodoBootReceiver.kt      ← 新增
│
├─ view/
│  └─ picker/
│      ├─ DateTimePickerDialog.kt  ← 新增
│      └─ RepeatPickerBottomSheet.kt ← 新增
```

---

## 7. 测试策略

| 层级 | 范围 | 工具 |
|---|---|---|
| Todo 实体 | RepeatType 枚举转换 | JUnit 5 |
| TodoRepository | CRUD + 软删除 + purge + completeTodo 重复推进 | Robolectric |
| DB 迁移 | v4→v5 升级 | Robolectric |
| TodoAlarmManager | scheduleAlarm / cancelAlarm 逻辑 | Robolectric (ShadowAlarmManager) |
| DateTimePickerDialog | 日期生成 + epoch 组合逻辑 | JUnit 5 (纯计算) |
| Fragment / Activity | 手测 | 真机 |

**手测用例（每个子里程碑完成后必跑）：**

M14a：
1. 底部 tab 切换笔记/待办，重启后恢复上次 tab
2. 新建待办（仅标题）→ 列表出现在"无日期"分组
3. 新建待办（标题 + 时间）→ 列表出现在正确的日期分组
4. 新建待办（标题 + 重要）→ 列表显示 ❗ 标记
5. 点击 checkbox 完成待办 → 移入"已完成"分组
6. 筛选面板切换 → 列表正确过滤

M14b：
7. 进入详情页 → 所有字段正确展示
8. 修改标题/备注 → 返回列表 → 数据保留
9. 设置提醒时间 → 到时收到系统通知
10. 通知"标记完成" → 待办被标记完成
11. 通知"10分钟后提醒" → 10 分钟后再次通知
12. 设置重复(每天) + 完成 → 时间推进到明天，状态重置为未完成
13. 清除提醒 → 闹钟取消

M14c：
14. 批量删除 → 多选 → 确认 → 进入最近删除
15. 最近删除 → 恢复 → 回到列表
16. 最近删除 → 彻底删除 → 永久消失
17. 隐藏已完成 → 已完成分组消失 → 再次点击 → 恢复

---

## 8. 里程碑分解

| 子里程碑 | 范围 | 预估 |
|---|---|---|
| **M14a** | DB v5 + Todo 实体 + TodoRepository + Fragment 重构 + TodoListFragment + TodoListAdapter + 新建栏 + 筛选面板 | 1-2 天 |
| **M14b** | TodoDetailActivity + DateTimePickerDialog + RepeatPickerBottomSheet + AlarmManager 通知 + 重复推进 + 权限 | 1-2 天 |
| **M14c** | 批量删除模式 + 隐藏已完成 + 软删除回收站 + overflow 菜单 + 打磨 | 1 天 |

---

## 9. 风险与已知限制

| 风险 | 缓解 |
|---|---|
| Fragment 重构影响面大，可能破坏现有笔记列表功能 | M14a 第一步先做 Fragment 搬迁 + 全量回归测试，确保笔记功能不退化后再建待办 |
| AlarmManager 在不同厂商 ROM 上行为不一致（省电策略） | 使用 `setExactAndAllowWhileIdle()`，文档注明已知限制 |
| NumberPicker 在部分 ROM 上样式不统一 | 接受默认系统样式，不做深度自定义主题 |
| Boot 后 rescheduleAll 在待办数量大时耗时 | 用 `lifecycleScope.launch(Dispatchers.IO)` 异步执行 |
| Android 13+ POST_NOTIFICATIONS 权限被拒 | 优雅降级：提醒时间照存但不弹通知，用户可在系统设置中手动开启 |

—— 文档结束 ——
