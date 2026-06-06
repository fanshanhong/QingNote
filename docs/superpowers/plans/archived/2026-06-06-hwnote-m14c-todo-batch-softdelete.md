# M14c 待办批量操作+软删除回收站 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为待办列表加入批量选择删除模式 + "最近删除"回收站恢复/彻底删除功能

**Architecture:** TodoListFragment 新增 `isBatchMode` 状态驱动 UI 切换；TodoListAdapter 新增 selection set + checkbox ViewType；Deleted 筛选下卡片追加恢复/彻底删除按钮。数据层 `TodoRepository.softDeleteBatch / restore / deletePermanently` 已就绪，无需改动。

**Tech Stack:** Kotlin, XML View, RecyclerView, DeleteConfirmBottomSheet（M9 通用组件）

---

## 文件结构

```
修改:
  app/src/main/res/values/strings.xml                  — 新增 5 条字符串
  app/src/main/res/layout/item_todo_card.xml            — 加 CheckBox 多选框 (gone)
  app/src/main/java/.../controller/list/TodoListAdapter.kt — 批量模式 + 已删除视图
  app/src/main/java/.../controller/list/TodoListFragment.kt — 批量模式入口/退出 + 删除视图菜单
```

---

### Task 1: 新增字符串资源

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 追加 5 条 M14c 字符串**

在 strings.xml `todo_menu_batch_delete` 之后追加：

```xml
<string name="todo_batch_selected_count">已选择 %d 项</string>
<string name="todo_batch_delete_action">删除</string>
<string name="todo_batch_confirm_title">删除待办</string>
<string name="todo_batch_confirm_message">确定删除选中的 %d 项待办？</string>
<string name="todo_deleted_confirm_message">彻底删除后不可恢复，确定删除？</string>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(m14c): 加批量删除+回收站字符串资源"
```

---

### Task 2: item_todo_card 加多选 CheckBox + 恢复/删除按钮

**Files:**
- Modify: `app/src/main/res/layout/item_todo_card.xml`

- [ ] **Step 1: 读取当前 item_todo_card.xml**

- [ ] **Step 2: 在圆形 checkbox (todo_checkbox) 前面加一个方形 CheckBox**

```xml
<CheckBox
    android:id="@+id/batch_checkbox"
    android:layout_width="24dp"
    android:layout_height="24dp"
    android:layout_marginStart="12dp"
    android:layout_gravity="center_vertical"
    android:button="@null"
    android:background="?android:attr/listChoiceIndicatorMultiple"
    android:visibility="gone" />
```

在卡片末尾追加恢复/彻底删除按钮容器（用于 Deleted 视图）：

```xml
<LinearLayout
    android:id="@+id/deleted_actions"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="center_vertical"
    android:orientation="horizontal"
    android:visibility="gone">

    <TextView
        android:id="@+id/btn_restore"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingHorizontal="12dp"
        android:paddingVertical="8dp"
        android:text="@string/action_restore"
        android:textColor="@color/primary"
        android:textSize="14sp" />

    <TextView
        android:id="@+id/btn_delete_permanently"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingHorizontal="12dp"
        android:paddingVertical="8dp"
        android:text="@string/action_delete_permanently"
        android:textColor="#E53935"
        android:textSize="14sp" />
</LinearLayout>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/item_todo_card.xml
git commit -m "feat(m14c): item_todo_card 加批量多选框+回收站操作按钮"
```

---

### Task 3: TodoListAdapter 支持批量模式 + 已删除视图

**Files:**
- Modify: `app/src/main/java/.../controller/list/TodoListAdapter.kt`

- [ ] **Step 1: 读取当前 TodoListAdapter.kt**

- [ ] **Step 2: 增加批量模式和已删除模式字段与回调**

在构造参数中新增：
```kotlin
private val onRestore: ((Todo) -> Unit)? = null,
private val onDeletePermanently: ((Todo) -> Unit)? = null,
```

在类体新增：
```kotlin
var isBatchMode = false
    set(value) {
        field = value
        selectedIds.clear()
        notifyDataSetChanged()
    }
var isDeletedView = false
    set(value) {
        field = value
        notifyDataSetChanged()
    }
val selectedIds = mutableSetOf<Long>()
val selectedCount: Int get() = selectedIds.size
```

- [ ] **Step 3: TodoVH.bind 中根据 isBatchMode 切换 checkbox 显示**

```kotlin
// batch mode
val batchCheckbox = itemView.findViewById<android.widget.CheckBox>(R.id.batch_checkbox)
val deletedActions = itemView.findViewById<View>(R.id.deleted_actions)

if (isBatchMode) {
    batchCheckbox.visibility = View.VISIBLE
    checkbox.visibility = View.GONE
    deletedActions.visibility = View.GONE
    batchCheckbox.isChecked = selectedIds.contains(todo.id)
    batchCheckbox.setOnCheckedChangeListener(null)
    batchCheckbox.setOnClickListener {
        if (selectedIds.contains(todo.id)) selectedIds.remove(todo.id)
        else selectedIds.add(todo.id)
        onBatchSelectionChanged?.invoke()
    }
    itemView.setOnClickListener {
        batchCheckbox.isChecked = !batchCheckbox.isChecked
        if (selectedIds.contains(todo.id)) selectedIds.remove(todo.id)
        else selectedIds.add(todo.id)
        onBatchSelectionChanged?.invoke()
    }
} else if (isDeletedView) {
    batchCheckbox.visibility = View.GONE
    checkbox.visibility = View.GONE
    deletedActions.visibility = View.VISIBLE
    itemView.findViewById<View>(R.id.btn_restore).setOnClickListener { onRestore?.invoke(todo) }
    itemView.findViewById<View>(R.id.btn_delete_permanently).setOnClickListener { onDeletePermanently?.invoke(todo) }
    itemView.setOnClickListener(null)
} else {
    batchCheckbox.visibility = View.GONE
    checkbox.visibility = View.VISIBLE
    deletedActions.visibility = View.GONE
    // 保留原有 checkbox / itemView 点击逻辑
}
```

新增回调字段：
```kotlin
var onBatchSelectionChanged: (() -> Unit)? = null
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/TodoListAdapter.kt
git commit -m "feat(m14c): TodoListAdapter 支持批量选择模式+已删除视图"
```

---

### Task 4: TodoListFragment 批量模式入口/退出 + 已删除回收站

**Files:**
- Modify: `app/src/main/java/.../controller/list/TodoListFragment.kt`

- [ ] **Step 1: 读取当前 TodoListFragment.kt**

- [ ] **Step 2: 加入批量模式状态与 UI 管理**

新增字段：
```kotlin
private var isBatchMode = false
private lateinit var batchHeader: View       // 将动态创建或复用 header 区域
private lateinit var batchDeleteBtn: View     // 底部删除按钮
```

新增方法 `enterBatchMode()`:
- 设 `isBatchMode = true`
- 隐藏 headerTitleArea / headerArrow / headerSubtitle / fab / quickAddBar / btnOverflow
- 显示 "已选择 0 项" 文字 + ✕ 退出按钮（复用 headerTitle 改文案 + headerArrow 改为 ✕ 图标或新建）
- `adapter.isBatchMode = true`
- 底部显示红色"删除"按钮
- 底部导航隐藏

新增方法 `exitBatchMode()`:
- 设 `isBatchMode = false`
- 恢复原 header
- `adapter.isBatchMode = false`
- 底部导航恢复
- fab 恢复

修改 `showOverflowMenu()` 的 `MENU_BATCH_DELETE` 分支：
```kotlin
MENU_BATCH_DELETE -> {
    enterBatchMode()
    true
}
```

`adapter.onBatchSelectionChanged` 回调更新"已选择 N 项"文案。

点击"删除"按钮 → `DeleteConfirmBottomSheet` 二确认 → `softDeleteBatch(selectedIds)` + 取消闹钟 + `exitBatchMode()` + `reload()`。

- [ ] **Step 3: 已删除视图恢复/彻底删除**

修改 adapter 构造，传入 `onRestore` 和 `onDeletePermanently` 回调：

```kotlin
adapter = TodoListAdapter(
    onCheckToggle = { ... },
    onClick = { ... },
    onRestore = { todo ->
        lifecycleScope.launch {
            TodoRepository.restore(todo.id)
            if (todo.remindAt > System.currentTimeMillis()) {
                TodoAlarmManager.scheduleAlarm(requireContext(), todo)
            }
            reload()
        }
    },
    onDeletePermanently = { todo ->
        DeleteConfirmBottomSheet(
            requireContext(),
            title = getString(R.string.dialog_delete_permanently_title),
            message = getString(R.string.todo_deleted_confirm_message),
            confirmLabel = getString(R.string.action_delete_permanently),
            confirmIsDanger = true,
        ) {
            lifecycleScope.launch {
                TodoRepository.deletePermanently(todo.id)
                reload()
            }
        }.show()
    },
)
```

在 `reload()` 中，根据 `currentFilter == TodoListFilter.Deleted` 设置 `adapter.isDeletedView`：
```kotlin
adapter.isDeletedView = currentFilter is TodoListFilter.Deleted
```

- [ ] **Step 4: 批量删除时取消相关闹钟**

在批量删除确认后：
```kotlin
for (id in adapter.selectedIds) {
    TodoAlarmManager.cancelAlarm(requireContext(), id)
}
TodoRepository.softDeleteBatch(adapter.selectedIds.toList())
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/TodoListFragment.kt
git commit -m "feat(m14c): 待办批量选择删除+最近删除恢复/彻底删除"
```

---

### Task 5: 构建验证

- [ ] **Step 1: 构建并测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
  ./gradlew :app:assembleDebug :app:test
```

预期：全绿，0 failures。

---

## 涉及文件总览

| 文件 | 变更 | 说明 |
|------|------|------|
| `strings.xml` | 修改 | +5 条字符串 |
| `item_todo_card.xml` | 修改 | +批量 CheckBox + 回收站操作按钮 |
| `TodoListAdapter.kt` | 修改 | 批量模式 + 已删除视图 |
| `TodoListFragment.kt` | 修改 | 批量模式入口/退出 + 回收站操作 |
