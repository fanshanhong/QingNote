# M13a 视觉基础 + 列表页重设计 实施计划

> **Status: DONE** (2026-06-05)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 HwNote 列表页从"绿色 Material Toolbar"风格全面切换到华为备忘录"白底大标题 + 蓝色强调"风格，包含色彩体系、布局重写、筛选面板、卡片样式、底部导航、Sheet 微调。

**Architecture:** 纯 XML View + Activity 模式，不引入 Compose/Fragment/Navigation。筛选面板从 PopupWindow 改为同页内嵌 RecyclerView（VISIBLE/GONE 切换）。底部导航用 LinearLayout 2-tab 模拟。NoteRepository 新增 count() 和 ListFilter.Uncategorized。

**Tech Stack:** Android XML Layout, Kotlin, Material Components, SQLite, RecyclerView

**Spec:** `docs/superpowers/specs/2026-06-05-m13a-visual-list-redesign.md`

---

## 文件结构

| 类别 | 文件 | 动作 |
|------|------|------|
| 色彩 | `res/values/colors.xml` | 改 3 色 |
| 主题 | `res/values/themes.xml` | 改 statusBar + radioButton |
| 字符串 | `res/values/strings.xml` | 新增 ~15 条 |
| 尺寸 | `res/values/dimens.xml` | 新增 ~5 条 |
| 图标 | `res/drawable/ic_note_tab.xml` | 新建 |
| 图标 | `res/drawable/ic_todo_tab.xml` | 新建 |
| 图标 | `res/drawable/ic_uncategorized.xml` | 新建 |
| 数据层 | `model/NoteRepository.kt` | 加 Uncategorized + count() |
| 数据层测试 | `test/.../NoteRepositoryTest.kt` | 加 count + Uncategorized 测试 |
| 卡片布局 | `res/layout/item_note_card.xml` | elevation=0, stroke |
| 排序 Sheet | `res/layout/dialog_sort_picker.xml` | 加取消按钮 |
| 删除确认布局 | `res/layout/dialog_delete_confirm.xml` | 去 title, 按钮蓝化 |
| 删除确认 | `view/list/DeleteConfirmBottomSheet.kt` | 去 title 参数 |
| 筛选伪项 | `res/layout/item_filter_pseudo.xml` | 重写：加计数+蓝条 |
| 筛选文件夹头 | `res/layout/item_filter_folder_header.xml` | 加计数 |
| 筛选笔记本 | `res/layout/item_filter_notebook.xml` | 加计数 |
| 筛选分节头 | `res/layout/item_filter_section_header.xml` | 新建 |
| 筛选适配器 | `view/list/FilterPanelAdapter.kt` | 新建 |
| 列表布局 | `res/layout/activity_note_list.xml` | 整体重写 |
| Overflow 菜单 | `res/menu/menu_note_list_overflow.xml` | 新建 |
| 列表逻辑 | `controller/list/NoteListActivity.kt` | 大幅重写 |
| 列表适配器 | `controller/list/NoteListAdapter.kt` | 加颜色淡化 |
| 删除旧筛选 | `view/folder/NotebookFilterPopupWindow.kt` | 删除 |
| 删除旧布局 | `res/layout/popup_notebook_filter.xml` | 删除 |
| 删除旧菜单 | `res/menu/menu_note_list_toolbar.xml` | 删除 |

---

### Task 1: 色彩体系 + 主题（§2.1）

**Files:**
- Modify: `app/src/main/res/values/colors.xml:4-6`
- Modify: `app/src/main/res/values/themes.xml:13-14`

- [ ] **Step 1: colors.xml 改三色**

```xml
<!-- 旧 -->
<color name="primary">#00897B</color>
<color name="primary_dark">#00695C</color>
<color name="primary_light">#E0F2F1</color>

<!-- 新 -->
<color name="primary">#007DFF</color>
<color name="primary_dark">#0056B3</color>
<color name="primary_light">#E3F2FD</color>
```

- [ ] **Step 2: themes.xml 状态栏改白**

```xml
<!-- 旧 -->
<item name="android:statusBarColor">@color/primary_dark</item>
<item name="android:windowLightStatusBar" tools:targetApi="m">false</item>

<!-- 新 -->
<item name="android:statusBarColor">@android:color/white</item>
<item name="android:windowLightStatusBar" tools:targetApi="m">true</item>
```

- [ ] **Step 3: 构建验证**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values/themes.xml
git commit -m "feat(m13a): 色彩体系绿→蓝 + 状态栏白底深色图标"
```

---

### Task 2: strings + dimens 资源（§2.2/§2.3/§2.6/§2.8）

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/dimens.xml`

- [ ] **Step 1: strings.xml 新增条目**

在 `<!-- M12 文件夹层级 -->` 区块最后一行 `indicator_no_notebook` 之后追加：

```xml

<!-- M13a 列表页重设计 -->
<string name="filter_all_notes">全部笔记</string>
<string name="note_count_format">%d 条笔记</string>
<string name="note_count_with_folder_format">%1$d 条笔记 | %2$s</string>
<string name="bottom_nav_notes">笔记</string>
<string name="bottom_nav_todo">待办</string>
<string name="toast_todo_placeholder">待办功能（M14 实现）</string>
<string name="sort_cancel">取消</string>
<string name="filter_section_folders">文件夹</string>
<string name="filter_manage_action">管理</string>
```

- [ ] **Step 2: dimens.xml 新增条目**

在文件末尾 `</resources>` 前追加：

```xml

<!-- M13a 列表页重设计 -->
<dimen name="header_title_size">26sp</dimen>
<dimen name="header_subtitle_size">14sp</dimen>
<dimen name="search_bar_height">40dp</dimen>
<dimen name="bottom_nav_height">56dp</dimen>
<dimen name="filter_selected_bar_width">4dp</dimen>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values/dimens.xml
git commit -m "feat(m13a): 新增列表页重设计所需 strings + dimens"
```

---

### Task 3: 新建 vector drawables（§2.6 + §2.3）

**Files:**
- Create: `app/src/main/res/drawable/ic_note_tab.xml`
- Create: `app/src/main/res/drawable/ic_todo_tab.xml`
- Create: `app/src/main/res/drawable/ic_uncategorized.xml`

- [ ] **Step 1: ic_note_tab.xml — 线框文档图标（底部导航 + 筛选面板"全部笔记"）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M14,2H6C4.9,2 4,2.9 4,4v16c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8L14,2zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z"
        android:strokeWidth="0"/>
</vector>
```

- [ ] **Step 2: ic_todo_tab.xml — 勾选圆圈图标（底部导航"待办"）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"
        android:strokeWidth="0"/>
</vector>
```

- [ ] **Step 3: ic_uncategorized.xml — 散落文档图标（筛选面板"未分类"）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M20,6h-8l-2,-2H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8C22,6.9 21.1,6 20,6zM20,18H4V6h5.17l2,2H20V18zM18,12H6v-2h12V12zM14,16H6v-2h8V16z"
        android:strokeWidth="0"/>
</vector>
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/drawable/ic_note_tab.xml app/src/main/res/drawable/ic_todo_tab.xml app/src/main/res/drawable/ic_uncategorized.xml
git commit -m "feat(m13a): 新建底部导航+筛选面板 vector 图标"
```

---

### Task 4: NoteRepository 扩展 — ListFilter.Uncategorized + count()（§2.3 数据层）

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`
- Modify: `app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryTest.kt`
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`（when 穷尽修复）

- [ ] **Step 1: ListFilter 添加 Uncategorized**

在 `NoteRepository.kt` 的 `sealed class ListFilter` 中，`object All` 之后添加 `object Uncategorized`：

```kotlin
sealed class ListFilter {
    object All : ListFilter()
    object Uncategorized : ListFilter()
    object Favorite : ListFilter()
    object Deleted : ListFilter()
    data class Folder(val folderId: Long) : ListFilter()
    data class Notebook(val notebookId: Long) : ListFilter()
}
```

- [ ] **Step 2: list() 方法 when 表达式添加 Uncategorized 分支**

在 `list()` 的 `when (filter)` 中，`ListFilter.All ->` 之后添加：

```kotlin
ListFilter.Uncategorized -> where += "deleted_at = 0 AND notebook_id IS NULL"
```

- [ ] **Step 3: 新增 count(filter) suspend 方法**

在 `setFavorite()` 方法前添加：

```kotlin
suspend fun count(filter: ListFilter): Int = withContext(Dispatchers.IO) {
    val where = mutableListOf<String>()
    val args = mutableListOf<String>()
    when (filter) {
        ListFilter.All -> where += "deleted_at = 0"
        ListFilter.Uncategorized -> where += "deleted_at = 0 AND notebook_id IS NULL"
        ListFilter.Favorite -> where += "deleted_at = 0 AND is_favorite = 1"
        ListFilter.Deleted -> where += "deleted_at != 0"
        is ListFilter.Folder -> {
            where += "deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)"
            args += filter.folderId.toString()
        }
        is ListFilter.Notebook -> {
            where += "deleted_at = 0 AND notebook_id = ?"
            args += filter.notebookId.toString()
        }
    }
    val selection = where.joinToString(" AND ")
    val cursor = dbHelper.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM notes WHERE $selection",
        args.toTypedArray(),
    )
    cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
}
```

- [ ] **Step 4: NoteListActivity when 穷尽修复**

在 `loadFilter()` 的 when 中添加（在 `"DELETED"` 分支后）：

```kotlin
"UNCATEGORIZED" -> NoteRepository.ListFilter.Uncategorized
```

在 `saveFilter()` 的 when 中添加（在 `ListFilter.Deleted` 分支后）：

```kotlin
NoteRepository.ListFilter.Uncategorized -> editor.putString(KEY_FILTER_TYPE, "UNCATEGORIZED")
```

在 `updateFilterChipLabel()` 的第一个 when 中添加：

```kotlin
NoteRepository.ListFilter.Uncategorized -> Unit
```

在第二个 when（`val label = ...`）中添加：

```kotlin
NoteRepository.ListFilter.Uncategorized -> getString(R.string.filter_uncategorized)
```

- [ ] **Step 5: 编写单测**

在 `NoteRepositoryTest.kt` 中添加：

```kotlin
@Test
fun `count returns correct number for All filter`() = runBlocking {
    val note1 = Note(title = "A", content = NoteContent(listOf(Block.TextBlock(""))))
    val note2 = Note(title = "B", content = NoteContent(listOf(Block.TextBlock(""))))
    NoteRepository.save(note1)
    NoteRepository.save(note2)
    val count = NoteRepository.count(NoteRepository.ListFilter.All)
    assertEquals(2, count)
}

@Test
fun `count returns zero for empty Favorite filter`() = runBlocking {
    val note = Note(title = "X", content = NoteContent(listOf(Block.TextBlock(""))))
    NoteRepository.save(note)
    val count = NoteRepository.count(NoteRepository.ListFilter.Favorite)
    assertEquals(0, count)
}

@Test
fun `list with Uncategorized filter returns notes without notebook`() = runBlocking {
    val n1 = Note(title = "No NB", content = NoteContent(listOf(Block.TextBlock(""))), notebookId = null)
    val n2 = Note(title = "Has NB", content = NoteContent(listOf(Block.TextBlock(""))), notebookId = 1L)
    NoteRepository.save(n1)
    NoteRepository.save(n2)
    val list = NoteRepository.list(NoteRepository.ListFilter.Uncategorized)
    assertEquals(1, list.size)
    assertEquals("No NB", list[0].title)
}
```

- [ ] **Step 6: 跑测**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:test 2>&1 | tail -10`
Expected: All tests PASSED

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryTest.kt app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m13a): 添加 ListFilter.Uncategorized + count() 方法 + 单测"
```

---

### Task 5: 笔记卡片扁平化（§2.4 布局）

**Files:**
- Modify: `app/src/main/res/layout/item_note_card.xml:11-14`

- [ ] **Step 1: MaterialCardView 属性改扁平**

```xml
<!-- 旧 -->
app:cardElevation="2dp"
app:rippleColor="@color/primary_light"

<!-- 新 -->
app:cardElevation="0dp"
app:strokeWidth="0.5dp"
app:strokeColor="#E8E8E8"
app:rippleColor="@color/primary_light"
```

同时将内部 LinearLayout 的 `android:padding="@dimen/spacing_m"` 改为：

```xml
android:paddingHorizontal="16dp"
android:paddingVertical="14dp"
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/layout/item_note_card.xml
git commit -m "feat(m13a): 笔记卡片扁平化 elevation=0 + 淡边框"
```

---

### Task 6: 排序 Sheet 加取消按钮 + RadioButton 蓝色（§2.8）

**Files:**
- Modify: `app/src/main/res/layout/dialog_sort_picker.xml`
- Modify: `app/src/main/res/values/themes.xml`（RadioButton 颜色）
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

- [ ] **Step 1: dialog_sort_picker.xml 加底部取消按钮**

在 `</RadioGroup>` 之后、`</LinearLayout>` 之前追加：

```xml

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:layout_marginTop="@dimen/spacing_s"
        android:background="@color/divider" />

    <TextView
        android:id="@+id/btn_sort_cancel"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="?attr/selectableItemBackground"
        android:gravity="center"
        android:text="@string/sort_cancel"
        android:textColor="@color/primary"
        android:textSize="@dimen/text_body" />
```

- [ ] **Step 2: themes.xml 新增 RadioButton 蓝色激活色**

在 `Theme.HuaWeiNote` style 中添加：

```xml
<item name="colorControlActivated">@color/primary</item>
```

- [ ] **Step 3: NoteListActivity showSortDialog() 绑定取消按钮**

在 `showSortDialog()` 中 `sheet.setContentView(view)` 之前添加：

```kotlin
view.findViewById<TextView>(R.id.btn_sort_cancel).setOnClickListener {
    sheet.dismiss()
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/dialog_sort_picker.xml app/src/main/res/values/themes.xml app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m13a): 排序 Sheet 加取消按钮 + RadioButton 蓝色"
```

---

### Task 7: 删除确认 Sheet 简化（§2.9）

**Files:**
- Modify: `app/src/main/res/layout/dialog_delete_confirm.xml`
- Modify: `app/src/main/java/com/fan/hwnote/app/view/list/DeleteConfirmBottomSheet.kt`
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

- [ ] **Step 1: dialog_delete_confirm.xml 简化 — 去 title 行，按钮蓝化**

整体重写为：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingTop="@dimen/spacing_l"
    android:paddingBottom="@dimen/spacing_s">

    <TextView
        android:id="@+id/confirm_message"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:paddingHorizontal="@dimen/spacing_l"
        android:textColor="@color/text_primary"
        android:textSize="@dimen/text_body" />

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:layout_marginTop="@dimen/spacing_l"
        android:background="@color/divider" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/btn_cancel"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_weight="1"
            android:background="?attr/selectableItemBackground"
            android:gravity="center"
            android:text="@string/action_cancel"
            android:textColor="@color/primary"
            android:textSize="@dimen/text_body" />

        <View
            android:layout_width="1dp"
            android:layout_height="48dp"
            android:background="@color/divider" />

        <TextView
            android:id="@+id/btn_confirm"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_weight="1"
            android:background="?attr/selectableItemBackground"
            android:gravity="center"
            android:textColor="@color/primary"
            android:textSize="@dimen/text_body" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: DeleteConfirmBottomSheet.kt 去掉 title 参数**

整体重写为：

```kotlin
package com.fan.hwnote.app.view.list

import android.content.Context
import android.widget.TextView
import com.fan.hwnote.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class DeleteConfirmBottomSheet(
    context: Context,
    private val message: String,
    private val confirmLabel: String,
    private val onConfirm: () -> Unit,
) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val view = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        setContentView(view)
        view.findViewById<TextView>(R.id.confirm_message).text = message
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)
        btnConfirm.text = confirmLabel
        var fired = false
        btnConfirm.setOnClickListener {
            if (fired) return@setOnClickListener
            fired = true
            dismiss()
            onConfirm()
        }
        btnCancel.setOnClickListener {
            if (fired) return@setOnClickListener
            fired = true
            dismiss()
        }
    }
}
```

- [ ] **Step 3: NoteListActivity 调用点去掉 title 参数**

所有 `DeleteConfirmBottomSheet(` 调用，删除 `title = ...` 行。共 2 处：

软删除处（`action_delete`）：
```kotlin
DeleteConfirmBottomSheet(
    this,
    message = getString(R.string.dialog_soft_delete_message),
    confirmLabel = getString(R.string.action_delete),
    onConfirm = {
        lifecycleScope.launch { NoteRepository.softDelete(note.id); reload() }
    },
).show()
```

彻底删除处（`action_delete_permanently`）：
```kotlin
DeleteConfirmBottomSheet(
    this,
    message = getString(R.string.dialog_delete_permanently_message),
    confirmLabel = getString(R.string.action_delete_permanently),
    onConfirm = {
        lifecycleScope.launch { NoteRepository.deletePermanently(note.id); reload() }
    },
).show()
```

- [ ] **Step 4: 全局搜索其他 DeleteConfirmBottomSheet 调用点**

Run: `grep -rn "DeleteConfirmBottomSheet" app/src/main/java/`

对每个调用点删除 `title` 和 `confirmIsDanger` 参数。已知调用点：
- `NoteListActivity.kt`（2 处，Step 3 已处理）
- `FolderManagerActivity.kt`（若有，同样删 title 参数）

- [ ] **Step 5: 构建验证**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/dialog_delete_confirm.xml app/src/main/java/com/fan/hwnote/app/view/list/DeleteConfirmBottomSheet.kt app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
# 如果 FolderManagerActivity 也改了，加入 git add
git commit -m "feat(m13a): 删除确认 Sheet 简化为单行提示+横排蓝色按钮"
```

---

### Task 8: 筛选面板布局改造（§2.3 布局）

**Files:**
- Modify: `app/src/main/res/layout/item_filter_pseudo.xml`
- Modify: `app/src/main/res/layout/item_filter_folder_header.xml`
- Modify: `app/src/main/res/layout/item_filter_notebook.xml`
- Create: `app/src/main/res/layout/item_filter_section_header.xml`

- [ ] **Step 1: item_filter_pseudo.xml — 加计数 + 左侧蓝色选中条**

整体重写为：

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="48dp">

    <View
        android:id="@+id/selected_bar"
        android:layout_width="@dimen/filter_selected_bar_width"
        android:layout_height="match_parent"
        android:background="@color/primary"
        android:visibility="gone" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:background="?attr/selectableItemBackground">

        <ImageView
            android:id="@+id/icon"
            android:layout_width="20dp"
            android:layout_height="20dp"
            android:layout_marginEnd="12dp" />

        <TextView
            android:id="@+id/label"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textSize="@dimen/text_body"
            android:textColor="@color/text_primary" />

        <TextView
            android:id="@+id/count"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="@dimen/text_caption"
            android:textColor="@color/text_hint" />
    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 2: item_filter_folder_header.xml — 加计数**

在 `expand_chevron` ImageView 之前添加计数 TextView：

```xml
<TextView
    android:id="@+id/folder_count"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginEnd="@dimen/spacing_s"
    android:textSize="@dimen/text_caption"
    android:textColor="@color/text_hint" />
```

- [ ] **Step 3: item_filter_notebook.xml — 加计数，替换 check 为 count**

把末尾的 `check` ImageView 替换为：

```xml
<TextView
    android:id="@+id/notebook_count"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textSize="@dimen/text_caption"
    android:textColor="@color/text_hint" />
```

- [ ] **Step 4: item_filter_section_header.xml — 新建**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="40dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="16dp"
    android:paddingEnd="16dp">

    <TextView
        android:id="@+id/section_title"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textSize="@dimen/text_caption"
        android:textColor="@color/text_hint"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/section_action"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:focusable="true"
        android:textSize="@dimen/text_caption"
        android:textColor="@color/primary" />
</LinearLayout>
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/item_filter_pseudo.xml app/src/main/res/layout/item_filter_folder_header.xml app/src/main/res/layout/item_filter_notebook.xml app/src/main/res/layout/item_filter_section_header.xml
git commit -m "feat(m13a): 筛选面板布局改造 — 计数+蓝条+分节头"
```

---

### Task 9: FilterPanelAdapter（§2.3 逻辑）

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/view/list/FilterPanelAdapter.kt`

- [ ] **Step 1: 创建 FilterPanelAdapter.kt**

```kotlin
package com.fan.hwnote.app.view.list

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.entity.Folder
import com.fan.hwnote.app.model.entity.Notebook

class FilterPanelAdapter(
    private val rows: List<Row>,
    private val selected: NoteRepository.ListFilter,
    private val onFilterPicked: (NoteRepository.ListFilter) -> Unit,
    private val onManageFolders: () -> Unit,
    private val onToggleFolder: (Long) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Pseudo(
            val kind: PseudoKind,
            val count: Int,
        ) : Row()

        object Divider : Row()

        data class SectionHeader(
            val title: String,
            val actionLabel: String,
        ) : Row()

        data class FolderHead(
            val folder: Folder,
            val expanded: Boolean,
            val count: Int,
        ) : Row()

        data class NotebookRow(
            val notebook: Notebook,
            val count: Int,
        ) : Row()
    }

    enum class PseudoKind { All, Uncategorized, Favorite, Deleted }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Pseudo -> 0
        is Row.Divider -> 1
        is Row.SectionHeader -> 2
        is Row.FolderHead -> 3
        is Row.NotebookRow -> 4
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> PseudoVH(inflater.inflate(R.layout.item_filter_pseudo, parent, false))
            1 -> DividerVH(View(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(parent.context, 1))
                setBackgroundColor(ContextCompat.getColor(parent.context, R.color.divider))
            })
            2 -> SectionVH(inflater.inflate(R.layout.item_filter_section_header, parent, false))
            3 -> FolderHeadVH(inflater.inflate(R.layout.item_filter_folder_header, parent, false))
            else -> NotebookVH(inflater.inflate(R.layout.item_filter_notebook, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Pseudo -> (holder as PseudoVH).bind(row)
            is Row.Divider -> Unit
            is Row.SectionHeader -> (holder as SectionVH).bind(row)
            is Row.FolderHead -> (holder as FolderHeadVH).bind(row)
            is Row.NotebookRow -> (holder as NotebookVH).bind(row)
        }
    }

    private fun pseudoFilter(kind: PseudoKind): NoteRepository.ListFilter = when (kind) {
        PseudoKind.All -> NoteRepository.ListFilter.All
        PseudoKind.Uncategorized -> NoteRepository.ListFilter.Uncategorized
        PseudoKind.Favorite -> NoteRepository.ListFilter.Favorite
        PseudoKind.Deleted -> NoteRepository.ListFilter.Deleted
    }

    private fun isSelected(filter: NoteRepository.ListFilter): Boolean = selected == filter

    private fun applySelectedState(view: View, bar: View, label: TextView, count: TextView, sel: Boolean) {
        val ctx = view.context
        val blue = ContextCompat.getColor(ctx, R.color.primary)
        val blueLight = ContextCompat.getColor(ctx, R.color.primary_light)
        bar.visibility = if (sel) View.VISIBLE else View.GONE
        view.setBackgroundColor(if (sel) blueLight else Color.TRANSPARENT)
        label.setTextColor(if (sel) blue else ContextCompat.getColor(ctx, R.color.text_primary))
        count.setTextColor(if (sel) blue else ContextCompat.getColor(ctx, R.color.text_hint))
    }

    private inner class PseudoVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: Row.Pseudo) {
            val ctx = itemView.context
            val icon = itemView.findViewById<ImageView>(R.id.icon)
            val label = itemView.findViewById<TextView>(R.id.label)
            val countTv = itemView.findViewById<TextView>(R.id.count)
            val bar = itemView.findViewById<View>(R.id.selected_bar)

            val iconRes = when (row.kind) {
                PseudoKind.All -> R.drawable.ic_note_tab
                PseudoKind.Uncategorized -> R.drawable.ic_uncategorized
                PseudoKind.Favorite -> R.drawable.ic_star_outline
                PseudoKind.Deleted -> R.drawable.ic_delete
            }
            icon.setImageResource(iconRes)
            label.text = when (row.kind) {
                PseudoKind.All -> ctx.getString(R.string.filter_all_notes)
                PseudoKind.Uncategorized -> ctx.getString(R.string.filter_uncategorized)
                PseudoKind.Favorite -> ctx.getString(R.string.filter_favorite)
                PseudoKind.Deleted -> ctx.getString(R.string.filter_deleted)
            }
            countTv.text = row.count.toString()

            val filter = pseudoFilter(row.kind)
            val sel = isSelected(filter)
            applySelectedState(itemView, bar, label, countTv, sel)
            val tintColor = if (sel) ContextCompat.getColor(ctx, R.color.primary) else ContextCompat.getColor(ctx, R.color.text_primary)
            icon.setColorFilter(tintColor)

            itemView.setOnClickListener { onFilterPicked(filter) }
        }
    }

    private class DividerVH(v: View) : RecyclerView.ViewHolder(v)

    private inner class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: Row.SectionHeader) {
            itemView.findViewById<TextView>(R.id.section_title).text = row.title
            val action = itemView.findViewById<TextView>(R.id.section_action)
            action.text = row.actionLabel
            action.setOnClickListener { onManageFolders() }
        }
    }

    private inner class FolderHeadVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: Row.FolderHead) {
            itemView.findViewById<TextView>(R.id.folder_name).text = row.folder.name
            itemView.findViewById<View>(R.id.expand_chevron).rotation = if (row.expanded) 180f else 0f
            itemView.findViewById<TextView>(R.id.folder_count).text = row.count.toString()
            itemView.setOnClickListener { onToggleFolder(row.folder.id) }
        }
    }

    private inner class NotebookVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: Row.NotebookRow) {
            itemView.findViewById<TextView>(R.id.notebook_name).text = row.notebook.name
            val dot = itemView.findViewById<View>(R.id.color_dot)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(row.notebook.color))
            }
            itemView.findViewById<TextView>(R.id.notebook_count).text = row.count.toString()

            val filter = NoteRepository.ListFilter.Notebook(row.notebook.id)
            val sel = isSelected(filter)
            if (sel) {
                val ctx = itemView.context
                itemView.setBackgroundColor(ContextCompat.getColor(ctx, R.color.primary_light))
                itemView.findViewById<TextView>(R.id.notebook_name)
                    .setTextColor(ContextCompat.getColor(ctx, R.color.primary))
                itemView.findViewById<TextView>(R.id.notebook_count)
                    .setTextColor(ContextCompat.getColor(ctx, R.color.primary))
            }
            itemView.setOnClickListener { onFilterPicked(filter) }
        }
    }

    companion object {
        private fun dpToPx(ctx: Context, dp: Int): Int =
            (dp * ctx.resources.displayMetrics.density + 0.5f).toInt()
    }
}
```

- [ ] **Step 2: 构建验证**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/fan/hwnote/app/view/list/FilterPanelAdapter.kt
git commit -m "feat(m13a): 新建 FilterPanelAdapter 筛选面板适配器"
```

---

### Task 10: 列表页布局重写（§2.2 + §2.6 + §2.7）

**Files:**
- Modify: `app/src/main/res/layout/activity_note_list.xml`（整体重写）
- Create: `app/src/main/res/drawable/shape_search_bar_bg.xml`
- Create: `app/src/main/res/menu/menu_note_list_overflow.xml`

布局结构（从上到下）：
1. LinearLayout(vertical, root) → fitsSystemWindows=true, bg=#FAFAFA
2.   ├─ LinearLayout(header) — 标题行+副标题+overflow ⋮
3.   ├─ LinearLayout(search_bar) — 圆角 pill 搜索栏 bg=#F5F5F5
4.   ├─ FrameLayout(content_area, weight=1) — 筛选面板/列表/空状态/FAB 叠放
5.   └─ LinearLayout(bottom_nav) — 底部 2-tab

- [ ] **Step 1: shape_search_bar_bg.xml 新建**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/toolbar_bg" />
    <corners android:radius="20dp" />
</shape>
```

- [ ] **Step 2: activity_note_list.xml 整体重写**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/root_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_window"
    android:fitsSystemWindows="true">

    <!-- header 区 -->
    <LinearLayout
        android:id="@+id/header"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="@dimen/spacing_l"
        android:paddingTop="@dimen/spacing_l"
        android:paddingBottom="@dimen/spacing_s">

        <LinearLayout
            android:id="@+id/header_title_area"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:clickable="true"
            android:focusable="true">

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <TextView
                    android:id="@+id/header_title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/filter_all_notes"
                    android:textColor="@color/text_primary"
                    android:textSize="@dimen/header_title_size"
                    android:textStyle="bold" />

                <ImageView
                    android:id="@+id/header_arrow"
                    android:layout_width="20dp"
                    android:layout_height="20dp"
                    android:layout_marginStart="@dimen/spacing_xs"
                    android:src="@drawable/ic_arrow_drop_down"
                    app:tint="@color/text_primary" />
            </LinearLayout>

            <TextView
                android:id="@+id/header_subtitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:textColor="@color/text_hint"
                android:textSize="@dimen/header_subtitle_size" />
        </LinearLayout>

        <ImageView
            android:id="@+id/btn_overflow"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:clickable="true"
            android:focusable="true"
            android:padding="@dimen/spacing_s"
            android:src="@drawable/ic_more_vert"
            app:tint="@color/text_primary"
            android:contentDescription="@string/editor_more_cd" />
    </LinearLayout>

    <!-- 搜索栏 -->
    <LinearLayout
        android:id="@+id/search_bar"
        android:layout_width="match_parent"
        android:layout_height="@dimen/search_bar_height"
        android:layout_marginHorizontal="@dimen/spacing_l"
        android:layout_marginBottom="@dimen/spacing_s"
        android:background="@drawable/shape_search_bar_bg"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingHorizontal="@dimen/spacing_m">

        <ImageView
            android:layout_width="20dp"
            android:layout_height="20dp"
            android:src="@drawable/ic_search"
            app:tint="@color/text_hint" />

        <EditText
            android:id="@+id/search_input"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@null"
            android:hint="@string/list_search_hint"
            android:imeOptions="actionSearch"
            android:inputType="text"
            android:maxLines="1"
            android:paddingStart="@dimen/spacing_s"
            android:paddingEnd="0dp"
            android:textColor="@color/text_primary"
            android:textColorHint="@color/text_hint"
            android:textSize="@dimen/text_body" />
    </LinearLayout>

    <!-- 内容区：筛选面板 / 列表 / 空状态 / FAB 叠放 -->
    <FrameLayout
        android:id="@+id/content_area"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/filter_panel"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:overScrollMode="never"
            android:visibility="gone" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recycler_notes"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:paddingHorizontal="@dimen/spacing_m"
            android:paddingVertical="@dimen/spacing_s"
            android:scrollbars="vertical" />

        <LinearLayout
            android:id="@+id/empty_state"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:orientation="vertical"
            android:visibility="gone">

            <ImageView
                android:layout_width="60dp"
                android:layout_height="60dp"
                android:src="@drawable/ic_empty_note" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_m"
                android:text="@string/list_empty_title"
                android:textColor="@color/text_secondary"
                android:textSize="@dimen/text_body" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_xs"
                android:text="@string/list_empty_subtitle"
                android:textColor="@color/text_hint"
                android:textSize="@dimen/text_caption" />
        </LinearLayout>

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/fab_new_note"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="@dimen/spacing_l"
            android:contentDescription="@string/fab_new_note_cd"
            android:src="@drawable/ic_add"
            app:backgroundTint="@color/primary"
            app:tint="@color/white" />
    </FrameLayout>

    <!-- 底部导航栏 -->
    <LinearLayout
        android:id="@+id/bottom_nav"
        android:layout_width="match_parent"
        android:layout_height="@dimen/bottom_nav_height"
        android:orientation="horizontal"
        android:background="@color/white"
        android:elevation="8dp">

        <LinearLayout
            android:id="@+id/nav_notes"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:gravity="center"
            android:orientation="vertical">

            <ImageView
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:src="@drawable/ic_note_tab"
                app:tint="@color/primary" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/bottom_nav_notes"
                android:textColor="@color/primary"
                android:textSize="@dimen/text_hint" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/nav_todo"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:gravity="center"
            android:orientation="vertical">

            <ImageView
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:src="@drawable/ic_todo_tab"
                app:tint="@color/text_hint" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/bottom_nav_todo"
                android:textColor="@color/text_hint"
                android:textSize="@dimen/text_hint" />
        </LinearLayout>
    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: menu_note_list_overflow.xml 新建**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/action_sort"
        android:title="@string/sort_picker_title" />
</menu>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_note_list.xml app/src/main/res/drawable/shape_search_bar_bg.xml app/src/main/res/menu/menu_note_list_overflow.xml
git commit -m "feat(m13a): 列表页布局重写 — 大标题+搜索栏+底部导航"
```

---

### Task 11: NoteListActivity 逻辑重写（§2.2 + §2.3 + §2.5 + §2.6 + §2.7）

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`（大幅重写）

这是最大的任务。NoteListActivity 从 311 行完全重写。关键改动：

1. 去掉 Toolbar / setSupportActionBar / onCreateOptionsMenu / onOptionsItemSelected
2. 新增 header 标题/副标题/▼▲ 切换
3. 筛选面板 RecyclerView 用 FilterPanelAdapter
4. overflow ⋮ → PopupMenu
5. 底部导航点击
6. 笔记本背景色

- [ ] **Step 1: 整体重写 NoteListActivity.kt**

```kotlin
package com.fan.hwnote.app.controller.list

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.controller.editor.NoteEditorActivity
import com.fan.hwnote.app.controller.folder.FolderManagerActivity
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.view.folder.NotebookPickerPopupWindow
import com.fan.hwnote.app.view.list.DeleteConfirmBottomSheet
import com.fan.hwnote.app.view.list.FilterPanelAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class NoteListActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var headerTitleArea: View
    private lateinit var headerTitle: TextView
    private lateinit var headerArrow: ImageView
    private lateinit var headerSubtitle: TextView
    private lateinit var btnOverflow: ImageView
    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var filterPanel: RecyclerView
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var fab: FloatingActionButton

    private lateinit var adapter: NoteListAdapter

    private var sortBy: NoteRepository.SortBy = NoteRepository.SortBy.UPDATED_DESC
    private var currentQuery: String? = null
    private var currentFilter: NoteRepository.ListFilter = NoteRepository.ListFilter.All
    private var filterPanelVisible = false

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private val expandedFolders = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        rootLayout = findViewById(R.id.root_layout)
        headerTitleArea = findViewById(R.id.header_title_area)
        headerTitle = findViewById(R.id.header_title)
        headerArrow = findViewById(R.id.header_arrow)
        headerSubtitle = findViewById(R.id.header_subtitle)
        btnOverflow = findViewById(R.id.btn_overflow)
        searchBar = findViewById(R.id.search_bar)
        searchInput = findViewById(R.id.search_input)
        filterPanel = findViewById(R.id.filter_panel)
        recycler = findViewById(R.id.recycler_notes)
        emptyState = findViewById(R.id.empty_state)
        fab = findViewById(R.id.fab_new_note)

        adapter = NoteListAdapter(
            onClick = { note ->
                startActivity(NoteEditorActivity.newIntent(this, note.id))
            },
            onLongClick = { note, anchor -> showCardMenu(note, anchor) },
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        sortBy = loadSort()
        currentFilter = loadFilter()

        headerTitleArea.setOnClickListener { toggleFilterPanel() }
        btnOverflow.setOnClickListener { showOverflowMenu() }
        fab.setOnClickListener {
            startActivity(NoteEditorActivity.newIntent(this, -1L))
        }

        findViewById<View>(R.id.nav_todo).setOnClickListener {
            Toast.makeText(this, R.string.toast_todo_placeholder, Toast.LENGTH_SHORT).show()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val text = s?.toString()?.trim().orEmpty()
                searchRunnable = Runnable {
                    currentQuery = text.ifEmpty { null }
                    reload()
                }
                searchHandler.postDelayed(searchRunnable!!, 200L)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { updateHeader() }
        reload()
    }

    override fun onDestroy() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun reload() {
        lifecycleScope.launch {
            val list = NoteRepository.list(currentFilter, sortBy, currentQuery)
            adapter.submit(list)
            renderEmpty(list.isEmpty())
        }
    }

    private fun renderEmpty(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    // -------- 标题 + 副标题 --------

    private suspend fun updateHeader() {
        val f = currentFilter
        when (f) {
            is NoteRepository.ListFilter.Folder ->
                if (FolderRepository.get(f.folderId) == null) {
                    currentFilter = NoteRepository.ListFilter.All
                    saveFilter(NoteRepository.ListFilter.All)
                }
            is NoteRepository.ListFilter.Notebook ->
                if (NotebookRepository.get(f.notebookId) == null) {
                    currentFilter = NoteRepository.ListFilter.All
                    saveFilter(NoteRepository.ListFilter.All)
                }
            NoteRepository.ListFilter.All,
            NoteRepository.ListFilter.Uncategorized,
            NoteRepository.ListFilter.Favorite,
            NoteRepository.ListFilter.Deleted -> Unit
        }
        val cur = currentFilter
        val title = when (cur) {
            NoteRepository.ListFilter.All -> getString(R.string.filter_all_notes)
            NoteRepository.ListFilter.Uncategorized -> getString(R.string.filter_uncategorized)
            NoteRepository.ListFilter.Favorite -> getString(R.string.filter_favorite)
            NoteRepository.ListFilter.Deleted -> getString(R.string.filter_deleted)
            is NoteRepository.ListFilter.Folder ->
                FolderRepository.get(cur.folderId)?.name ?: getString(R.string.filter_all_notes)
            is NoteRepository.ListFilter.Notebook ->
                NotebookRepository.get(cur.notebookId)?.name ?: getString(R.string.filter_all_notes)
        }
        headerTitle.text = title

        val count = NoteRepository.count(cur)
        val subtitle = when (cur) {
            is NoteRepository.ListFilter.Notebook -> {
                val nb = NotebookRepository.get(cur.notebookId)
                val folderName = nb?.folderId?.let { FolderRepository.get(it)?.name }
                if (folderName != null) {
                    getString(R.string.note_count_with_folder_format, count, folderName)
                } else {
                    getString(R.string.note_count_format, count)
                }
            }
            NoteRepository.ListFilter.All,
            NoteRepository.ListFilter.Uncategorized,
            NoteRepository.ListFilter.Favorite,
            NoteRepository.ListFilter.Deleted,
            is NoteRepository.ListFilter.Folder ->
                getString(R.string.note_count_format, count)
        }
        headerSubtitle.text = subtitle

        applyNotebookBackground(cur)
    }

    // -------- 笔记本背景色 (§2.5) --------

    private suspend fun applyNotebookBackground(filter: NoteRepository.ListFilter) {
        val bgColor = when (filter) {
            is NoteRepository.ListFilter.Notebook -> {
                val nb = NotebookRepository.get(filter.notebookId)
                if (nb != null) {
                    val c = Color.parseColor(nb.color)
                    Color.argb(25, Color.red(c), Color.green(c), Color.blue(c))
                } else {
                    getColor(R.color.bg_window)
                }
            }
            NoteRepository.ListFilter.All,
            NoteRepository.ListFilter.Uncategorized,
            NoteRepository.ListFilter.Favorite,
            NoteRepository.ListFilter.Deleted,
            is NoteRepository.ListFilter.Folder ->
                getColor(R.color.bg_window)
        }
        rootLayout.setBackgroundColor(bgColor)
    }

    // -------- 筛选面板 --------

    private fun toggleFilterPanel() {
        filterPanelVisible = !filterPanelVisible
        headerArrow.rotation = if (filterPanelVisible) 180f else 0f
        if (filterPanelVisible) {
            searchBar.visibility = View.GONE
            recycler.visibility = View.GONE
            emptyState.visibility = View.GONE
            fab.visibility = View.GONE
            filterPanel.visibility = View.VISIBLE
            filterPanel.layoutManager = LinearLayoutManager(this)
            rebuildFilterPanel()
        } else {
            filterPanel.visibility = View.GONE
            searchBar.visibility = View.VISIBLE
            fab.visibility = View.VISIBLE
            reload()
        }
    }

    private fun rebuildFilterPanel() {
        lifecycleScope.launch {
            val rows = mutableListOf<FilterPanelAdapter.Row>()

            val allCount = NoteRepository.count(NoteRepository.ListFilter.All)
            val uncatCount = NoteRepository.count(NoteRepository.ListFilter.Uncategorized)
            val favCount = NoteRepository.count(NoteRepository.ListFilter.Favorite)
            val delCount = NoteRepository.count(NoteRepository.ListFilter.Deleted)

            rows += FilterPanelAdapter.Row.Pseudo(FilterPanelAdapter.PseudoKind.All, allCount)
            rows += FilterPanelAdapter.Row.Pseudo(FilterPanelAdapter.PseudoKind.Uncategorized, uncatCount)
            rows += FilterPanelAdapter.Row.Pseudo(FilterPanelAdapter.PseudoKind.Favorite, favCount)
            rows += FilterPanelAdapter.Row.Pseudo(FilterPanelAdapter.PseudoKind.Deleted, delCount)
            rows += FilterPanelAdapter.Row.Divider
            rows += FilterPanelAdapter.Row.SectionHeader(
                getString(R.string.filter_section_folders),
                getString(R.string.filter_manage_action),
            )

            val folders = FolderRepository.list()
            for (f in folders) {
                val fCount = NoteRepository.count(NoteRepository.ListFilter.Folder(f.id))
                rows += FilterPanelAdapter.Row.FolderHead(f, expandedFolders.contains(f.id), fCount)
                if (expandedFolders.contains(f.id)) {
                    val nbs = NotebookRepository.listByFolder(f.id)
                    for (nb in nbs) {
                        val nbCount = NoteRepository.count(NoteRepository.ListFilter.Notebook(nb.id))
                        rows += FilterPanelAdapter.Row.NotebookRow(nb, nbCount)
                    }
                }
            }

            filterPanel.adapter = FilterPanelAdapter(
                rows = rows,
                selected = currentFilter,
                onFilterPicked = { picked ->
                    currentFilter = picked
                    saveFilter(picked)
                    lifecycleScope.launch { updateHeader() }
                    toggleFilterPanel()
                },
                onManageFolders = {
                    startActivity(Intent(this@NoteListActivity, FolderManagerActivity::class.java))
                },
                onToggleFolder = { folderId ->
                    if (expandedFolders.contains(folderId)) expandedFolders -= folderId
                    else expandedFolders += folderId
                    rebuildFilterPanel()
                },
            )
        }
    }

    // -------- Overflow 菜单 --------

    private fun showOverflowMenu() {
        val popup = PopupMenu(this, btnOverflow)
        popup.menuInflater.inflate(R.menu.menu_note_list_overflow, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sort -> { showSortDialog(); true }
                else -> false
            }
        }
        popup.show()
    }

    // -------- 卡片长按菜单 --------

    private fun showCardMenu(note: Note, anchor: View) {
        val popup = PopupMenu(this, anchor)
        val isDeletedView = currentFilter == NoteRepository.ListFilter.Deleted
        val menuRes = if (isDeletedView) R.menu.menu_note_card_deleted else R.menu.menu_note_card_long_press
        popup.menuInflater.inflate(menuRes, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_toggle_favorite -> {
                    lifecycleScope.launch {
                        NoteRepository.setFavorite(note.id, !note.isFavorite)
                        reload()
                    }
                    true
                }
                R.id.action_delete -> {
                    DeleteConfirmBottomSheet(
                        this,
                        message = getString(R.string.dialog_soft_delete_message),
                        confirmLabel = getString(R.string.action_delete),
                        onConfirm = {
                            lifecycleScope.launch { NoteRepository.softDelete(note.id); reload() }
                        },
                    ).show()
                    true
                }
                R.id.action_restore -> {
                    lifecycleScope.launch { NoteRepository.restore(note.id); reload() }
                    true
                }
                R.id.action_delete_permanently -> {
                    DeleteConfirmBottomSheet(
                        this,
                        message = getString(R.string.dialog_delete_permanently_message),
                        confirmLabel = getString(R.string.action_delete_permanently),
                        onConfirm = {
                            lifecycleScope.launch { NoteRepository.deletePermanently(note.id); reload() }
                        },
                    ).show()
                    true
                }
                R.id.action_move_notebook -> {
                    NotebookPickerPopupWindow(
                        context = this,
                        currentNotebookId = note.notebookId,
                        onPicked = { picked ->
                            lifecycleScope.launch { NoteRepository.moveNoteToNotebook(note.id, picked); reload() }
                        },
                    ).show(anchor)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // -------- 排序 --------

    private fun showSortDialog() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_sort_picker, null)
        val group = view.findViewById<RadioGroup>(R.id.sort_radio_group)
        val checkedId = when (sortBy) {
            NoteRepository.SortBy.UPDATED_DESC -> R.id.sort_updated
            NoteRepository.SortBy.CREATED_DESC -> R.id.sort_created
        }
        group.check(checkedId)
        group.setOnCheckedChangeListener { _, id ->
            sortBy = when (id) {
                R.id.sort_created -> NoteRepository.SortBy.CREATED_DESC
                else -> NoteRepository.SortBy.UPDATED_DESC
            }
            saveSort(sortBy)
            reload()
            sheet.dismiss()
        }
        view.findViewById<TextView>(R.id.btn_sort_cancel).setOnClickListener {
            sheet.dismiss()
        }
        sheet.setContentView(view)
        sheet.show()
    }

    // -------- 持久化 --------

    private fun loadSort(): NoteRepository.SortBy {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val name = prefs.getString(KEY_SORT, NoteRepository.SortBy.UPDATED_DESC.name)
        return runCatching { NoteRepository.SortBy.valueOf(name!!) }
            .getOrDefault(NoteRepository.SortBy.UPDATED_DESC)
    }

    private fun saveSort(sortBy: NoteRepository.SortBy) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_SORT, sortBy.name).apply()
    }

    private fun loadFilter(): NoteRepository.ListFilter {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val type = prefs.getString(KEY_FILTER_TYPE, "ALL") ?: "ALL"
        return when (type) {
            "ALL" -> NoteRepository.ListFilter.All
            "UNCATEGORIZED" -> NoteRepository.ListFilter.Uncategorized
            "FAVORITE" -> NoteRepository.ListFilter.Favorite
            "DELETED" -> NoteRepository.ListFilter.Deleted
            "FOLDER" -> {
                val id = prefs.getLong(KEY_FILTER_FOLDER_ID, -1L)
                if (id > 0) NoteRepository.ListFilter.Folder(id) else NoteRepository.ListFilter.All
            }
            "NOTEBOOK" -> {
                val id = prefs.getLong(KEY_FILTER_NOTEBOOK_ID, -1L)
                if (id > 0) NoteRepository.ListFilter.Notebook(id) else NoteRepository.ListFilter.All
            }
            "CATEGORY" -> NoteRepository.ListFilter.All
            else -> NoteRepository.ListFilter.All
        }
    }

    private fun saveFilter(filter: NoteRepository.ListFilter) {
        val editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .remove(KEY_FILTER_FOLDER_ID).remove(KEY_FILTER_NOTEBOOK_ID)
        when (filter) {
            NoteRepository.ListFilter.All -> editor.putString(KEY_FILTER_TYPE, "ALL")
            NoteRepository.ListFilter.Uncategorized -> editor.putString(KEY_FILTER_TYPE, "UNCATEGORIZED")
            NoteRepository.ListFilter.Favorite -> editor.putString(KEY_FILTER_TYPE, "FAVORITE")
            NoteRepository.ListFilter.Deleted -> editor.putString(KEY_FILTER_TYPE, "DELETED")
            is NoteRepository.ListFilter.Folder ->
                editor.putString(KEY_FILTER_TYPE, "FOLDER").putLong(KEY_FILTER_FOLDER_ID, filter.folderId)
            is NoteRepository.ListFilter.Notebook ->
                editor.putString(KEY_FILTER_TYPE, "NOTEBOOK").putLong(KEY_FILTER_NOTEBOOK_ID, filter.notebookId)
        }
        editor.apply()
    }

    companion object {
        private const val PREFS = "hwnote_settings"
        private const val KEY_SORT = "sort_by"
        private const val KEY_FILTER_TYPE = "filter_type"
        private const val KEY_FILTER_FOLDER_ID = "filter_folder_id"
        private const val KEY_FILTER_NOTEBOOK_ID = "filter_notebook_id"
    }
}
```

- [ ] **Step 2: 构建验证**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m13a): NoteListActivity 逻辑重写 — 大标题+筛选面板+底部导航+overflow"
```

---

### Task 12: NoteListAdapter 笔记本颜色淡化（§2.4 逻辑）

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListAdapter.kt`

- [ ] **Step 1: 添加笔记本颜色映射 + onBindViewHolder 淡化逻辑**

修改 NoteListAdapter，新增 `notebookColorMap` 字段和 `submitColors()` 方法：

```kotlin
package com.fan.hwnote.app.controller.list

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.util.DateUtils
import com.fan.hwnote.app.util.TextUtils
import com.google.android.material.card.MaterialCardView

class NoteListAdapter(
    private val onClick: (Note) -> Unit,
    private val onLongClick: (Note, View) -> Unit,
) : RecyclerView.Adapter<NoteListAdapter.VH>() {

    private val items = mutableListOf<Note>()
    private var notebookColorMap: Map<Long, String> = emptyMap()

    fun submit(list: List<Note>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun submitColors(map: Map<Long, String>) {
        notebookColorMap = map
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView as MaterialCardView
        private val title: TextView = itemView.findViewById(R.id.card_title)
        private val star: ImageView = itemView.findViewById(R.id.card_star)
        private val time: TextView = itemView.findViewById(R.id.card_time)
        private val summary: TextView = itemView.findViewById(R.id.card_summary)

        fun bind(note: Note) {
            title.text = if (TextUtils.isBlankTitle(note.title))
                itemView.context.getString(R.string.untitled_note)
            else note.title
            star.setImageResource(
                if (note.isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline
            )
            time.text = DateUtils.formatRelative(note.updatedAt)
            summary.text = TextUtils.summary(note.plainText)
            summary.visibility = if (summary.text.isNullOrEmpty()) View.GONE else View.VISIBLE

            val colorStr = note.notebookId?.let { notebookColorMap[it] }
            if (colorStr != null) {
                val c = Color.parseColor(colorStr)
                card.setCardBackgroundColor(Color.argb(20, Color.red(c), Color.green(c), Color.blue(c)))
            } else {
                card.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.bg_card))
            }

            itemView.setOnClickListener { onClick(note) }
            itemView.setOnLongClickListener {
                onLongClick(note, itemView)
                true
            }
        }
    }
}
```

- [ ] **Step 2: NoteListActivity.reload() 中传入颜色映射**

在 `NoteListActivity.reload()` 中，在 `adapter.submit(list)` 之前加载颜色映射：

```kotlin
private fun reload() {
    lifecycleScope.launch {
        val list = NoteRepository.list(currentFilter, sortBy, currentQuery)
        val colorMap = mutableMapOf<Long, String>()
        val nbIds = list.mapNotNull { it.notebookId }.toSet()
        for (id in nbIds) {
            val nb = NotebookRepository.get(id)
            if (nb != null) colorMap[id] = nb.color
        }
        adapter.submitColors(colorMap)
        adapter.submit(list)
        renderEmpty(list.isEmpty())
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListAdapter.kt app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m13a): 笔记卡片根据笔记本颜色淡化背景"
```

---

### Task 13: 删旧文件 + 构建 + 全量测试

**Files:**
- Delete: `app/src/main/java/com/fan/hwnote/app/view/folder/NotebookFilterPopupWindow.kt`
- Delete: `app/src/main/res/layout/popup_notebook_filter.xml`
- Delete: `app/src/main/res/menu/menu_note_list_toolbar.xml`

- [ ] **Step 1: 删除旧文件**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote
rm app/src/main/java/com/fan/hwnote/app/view/folder/NotebookFilterPopupWindow.kt
rm app/src/main/res/layout/popup_notebook_filter.xml
rm app/src/main/res/menu/menu_note_list_toolbar.xml
```

- [ ] **Step 2: 全局搜索残留引用**

Run: `grep -rn "NotebookFilterPopupWindow\|popup_notebook_filter\|menu_note_list_toolbar" app/src/main/java/ app/src/main/res/ 2>/dev/null`

如果有残留引用，删除对应 import 行。NoteListActivity 在 Task 11 已移除 `import NotebookFilterPopupWindow`。

- [ ] **Step 3: 全量构建 + 测试**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:clean :app:assembleDebug :app:test 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL, all tests PASSED

- [ ] **Step 4: Commit**

```bash
git add -u
git commit -m "refactor(m13a): 删除旧 NotebookFilterPopupWindow + popup 布局 + toolbar 菜单"
```

---

### Task 14: STATUS 更新 + 计划归档

**Files:**
- Modify: `docs/superpowers/STATUS.md`
- Modify: `docs/superpowers/plans/2026-06-05-m13a-visual-list-redesign.md`（标记完成）

- [ ] **Step 1: STATUS.md 追加 M13a 行**

在里程碑表格中追加：

```
| M13a | 视觉基础+列表页重设计 | N | +3 | 色彩体系绿→蓝 + 白底大标题 + 筛选面板 + 底部导航 + 卡片扁平化 |
```

追加完成详情小节：

```markdown
## M13a 视觉基础+列表页重设计 完成详情（2026-06-XX）

**色彩：** primary #00897B → #007DFF (华为蓝), 状态栏白底深色图标
**列表页：** CoordinatorLayout+AppBarLayout → LinearLayout 大标题 + FrameLayout 内容区
**筛选面板：** PopupWindow → 同页内嵌 FilterPanelAdapter (伪项4个+分节头+折叠文件夹树+计数)
**卡片：** elevation=0dp, strokeWidth=0.5dp, strokeColor=#E8E8E8, 笔记本颜色淡化背景
**底部导航：** LinearLayout 2-tab (笔记蓝色固定选中 + 待办灰色占位)
**Overflow：** ⋮ PopupMenu 替代 Toolbar action menu
**排序 Sheet：** 加"取消"按钮 + RadioButton 蓝色
**删除确认：** 去 title → 单行居中提示 + 横排蓝色按钮
**数据层：** ListFilter.Uncategorized + NoteRepository.count()
**删除：** NotebookFilterPopupWindow + popup_notebook_filter.xml + menu_note_list_toolbar.xml
```

- [ ] **Step 2: 计划标记完成**

在本计划文件头部追加 `> **Status: DONE**`

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/STATUS.md docs/superpowers/plans/2026-06-05-m13a-visual-list-redesign.md
git commit -m "docs(m13a): STATUS 追加 M13a 完成详情 + 计划归档"
```

---

## 不动的东西（明确边界）

- **数据层核心：** Folder/Notebook/Note 实体、FolderRepository、NotebookRepository（仅 NoteRepository 加 count + Uncategorized）
- **编辑器：** NoteEditorActivity、EditorPresenter、所有 BlockView — M13b 处理
- **工具栏：** TextToolbarView、HandwritingToolbarView — M13b 处理
- **样式 Sheet：** StylePickerBottomSheet — M13c 处理
- **手写/音频/撤销：** 完全不动
- **FolderManagerActivity：** 不动（仅色彩跟随主题自动变蓝）
- **NotebookPickerPopupWindow：** 保留（编辑器用），M13b 再处理
- **NewFolder/NewNotebook BottomSheet：** 按钮颜色跟随主题自动变蓝，无代码改动

## 验证（真机）

- 列表页：白底大标题 + 副标题条数 + ⋮ 菜单，无绿色 AppBar
- 状态栏：白色背景 + 深色系统图标
- 筛选面板：点标题 ▼ 展开全屏面板，选中项蓝色高亮 + 计数，选中后收起
- 笔记卡片：扁平无阴影，淡边框
- 笔记本颜色：选中笔记本 → 整页背景淡化色 + 卡片淡化色
- 底部导航：笔记蓝色选中 + 待办灰色，待办点击 Toast
- FAB：蓝色
- 排序 Sheet：有"取消"按钮，RadioButton 蓝色
- 删除确认：单行提示 + 横排蓝色按钮
- 编辑器/手写/管理页：仅色彩跟随变蓝，布局不变
- 全部测试 PASSED（137 + 新增 count/Uncategorized 测试）
