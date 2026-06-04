# HwNote M12 文件夹层级 设计稿

**日期：** 2026-06-04
**里程碑：** M12（新一轮 "PRD §13 之后" 扩展第 1/4 子项目）
**前置：** M1-M11 已交付（HEAD `b4c7e8b`，114 单测全绿）
**用户需求锚点：** 24 张华为 Note 参考图中 ③ 文件夹-笔记本-笔记三级 + 顶部下拉 + 文件夹管理 + 8 色笔记本；参考图 `docs/superpowers/references/huawei-note/editor-notebook-picker.png`
**子项目分解上下文：** M12（本子项目，文件夹层级）→ M13（笔记 Tab UI 打磨）→ M14（待办子系统）→ M15（UI 全面审查）

---

## 0. 范围与决策摘要

**核心决策（brainstorm 13 问全部确认）：**

| # | 决策点 | 选择 |
|---|---|---|
| 1 | 数据迁移 | **丢弃老 categories 业务**。物理保留 categories 表 + notes.category_id 列作墓地（避免 SQLite DROP COLUMN 兼容性问题），业务层不再读写 |
| 2 | 层级 | **folders → notebooks → notes**。notebook.folder_id NOT NULL（必属文件夹）；notes.notebook_id NULLABLE（NULL=「未分类」） |
| 3 | 顶部下拉 UI | **DropDown PopupWindow**（从 AppBar 标题下方弹出） |
| 4 | 笔记本颜色 | **预设固定 8 色**，INTEGER index 0..7 存 DB |
| 5 | 删除级联 | **软删到「最近删除」**。folders / notebooks / notes 三表都加 deleted_at；30 天后 purgeExpired |
| 6 | PopupWindow 内层级展示 | **点文件夹 → 原地展开二级（伸缩树）** |
| 7 | 拖动重排 | **三粒度**：文件夹互拖 + 同文件夹内笔记本互拖 + 跨文件夹拖笔记本 |
| 8 | 笔记归属切换入口 | **双入口**：列表长按 PopupMenu「移到笔记本」+ 编辑器顶部右侧"当前笔记本名 ▼"，复用同一 NotebookPickerPopupWindow |
| 9 | 创建+管理 UI | **独立 FolderManagerActivity**（含拖排序 + 改名 + 改色 + 删除 + 顶部「+ 新建文件夹」）；PopupWindow picker 内每文件夹底部「新建」按钮起 NewNotebookBottomSheet |
| 10 | 首次启动 | **预置「默认文件夹」+「默认笔记本」**，老笔记 notebook_id 全 UPDATE 为默认笔记本 id |
| 11 | 默认项保护 | **不能删，但能改名改色**。is_default 标记，FolderManagerActivity 隐藏其删除按钮 |
| 12 | 编辑器 picker 含「未分类」 | **含**，顶部一行项；选中后 notebook_id = NULL |
| 13 | 新笔记默认归属 | **上下文感知**：当前 filter 是具体笔记本则归该笔记本；其他 filter（全部/未分类/收藏/最近删除）归默认笔记本 |

**范围内：**
- DB v3 迁移 + 2 新实体 + 2 新 Repository
- ListFilter sealed 扩展（Category 改 Notebook）
- 列表页 AppBar 标题区改造 + NotebookFilterPopupWindow（顶部下拉）
- 编辑器顶部"当前归属 ▼"指示器 + NotebookPickerPopupWindow（双入口共用）
- FolderManagerActivity（含三粒度拖动 + 改名 + 改色 + 删除 + 顶部「+ 新建文件夹」）
- NewNotebookBottomSheet（输入名 + 8 色圆点）/ NewFolderBottomSheet（输入名）
- 列表页长按 PopupMenu 加「移到笔记本」项 → NotebookPickerPopupWindow
- 删除级联（事务）+ 恢复时孤儿笔记落点 + purgeExpired 扩展

**范围外（明确划入 M13/M14/M15）：**
- 笔记列表宫格视图 / 批量删除 / 首页双 Tab / 底部毛玻璃 / 分类背景色随笔记本变 / 分享 → M13
- 待办子系统 / Todo 实体 / AlarmManager 通知 → M14
- UI 字体/间距/颜色全面审查整改 → M15
- 老 categories 表硬删（DB v4 清理）—— 不做，永远墓地
- 跨设备同步 / 备份 / 导出 —— 不在 PRD 内

---

## 1. 架构总览

**新增包：** `model/entity/Folder.kt` / `model/entity/Notebook.kt` / `model/FolderRepository.kt` / `model/NotebookRepository.kt`

**关键不变量：**
1. **每个笔记本必属一个文件夹** — DB schema notebooks.folder_id NOT NULL；业务层创建笔记本必传 folder_id
2. **「未分类」是固定 sentinel，不是表中实体** — UI 用 `R.string.notebook_uncategorized`，业务表示 `notebook_id IS NULL`，不能改名
3. **删除级联走事务** — 同一个 SQLiteDatabase.beginTransaction()，要么全部成功，要么回滚
4. **恢复笔记时若父 notebook 已软删 → 笔记 notebook_id 改为默认笔记本 id**（保证用户能看到）
5. **NotebookPickerPopupWindow 编辑器版与列表长按版同一组件，仅 anchor / 是否含「管理」按钮不同**
6. **NoteJson 序列化不加 notebook_id**（元数据属于 DB 列，与内容解耦）
7. **NoteFileStorage 完全不动**（笔记本/文件夹无独立文件存储；仅元数据）

**Activity / 组件关系：**
```
NoteListActivity
  ├── AppBar 标题区点击 → NotebookFilterPopupWindow
  │     ├── 4 内置筛选（All/Uncategorized/Favorite/Deleted）
  │     ├── 树形文件夹列表（▶/▼ 折叠，点笔记本切 filter）
  │     └── 底部「管理」按钮 → startActivity(FolderManagerActivity)
  ├── ItemView 长按 PopupMenu「移到笔记本」 → NotebookPickerPopupWindow（anchor=item 右侧）
  └── ItemView 长按 PopupMenu「删除」 → DeleteConfirmBottomSheet（保留 M9）

NoteEditorActivity（M11 已有，本里程碑追加）
  └── AppBar 右侧「<当前笔记本名> ▼」指示器
        └── 点击 → NotebookPickerPopupWindow（anchor=指示器，无管理按钮）

FolderManagerActivity（新）
  ├── 顶部「+ 新建文件夹」按钮 → NewFolderBottomSheet
  ├── RecyclerView（FolderHeader + NotebookItem 两 ViewHolder）
  │     ├── FolderHeader 右侧：[▼/▶ 折叠] [✎ 改名]（默认项隐藏 [✕ 删除]）
  │     ├── NotebookItem 右侧：[▼ 8 色] [✎ 改名]（默认项隐藏 [✕ 删除]）
  │     └── ItemTouchHelper 三粒度拖动
  └── 每个 FolderHeader 下展开后底部一行「+ 在此文件夹下新建笔记本」 → NewNotebookBottomSheet
```

---

## 2. 数据层

### 2.1 DB v3 schema 迁移

**`NoteDbHelper.onUpgrade(db, oldVersion, newVersion)`** 内追加 v2 → v3 分支：

```kotlin
if (oldVersion < 3) {
    db.beginTransaction()
    try {
        db.execSQL("""
            CREATE TABLE folders (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              display_order INTEGER NOT NULL DEFAULT 0,
              is_default INTEGER NOT NULL DEFAULT 0,
              deleted_at INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_folders_deleted ON folders(deleted_at)")

        db.execSQL("""
            CREATE TABLE notebooks (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              folder_id INTEGER NOT NULL,
              color_index INTEGER NOT NULL DEFAULT 0,
              display_order INTEGER NOT NULL DEFAULT 0,
              is_default INTEGER NOT NULL DEFAULT 0,
              deleted_at INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_notebooks_folder ON notebooks(folder_id)")
        db.execSQL("CREATE INDEX idx_notebooks_deleted ON notebooks(deleted_at)")

        db.execSQL("ALTER TABLE notes ADD COLUMN notebook_id INTEGER")
        db.execSQL("CREATE INDEX idx_notes_notebook ON notes(notebook_id)")

        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO folders(id, name, display_order, is_default, deleted_at, created_at) VALUES(1, ?, 0, 1, 0, ?)",
            arrayOf("默认文件夹", now)
        )
        db.execSQL(
            "INSERT INTO notebooks(id, name, folder_id, color_index, display_order, is_default, deleted_at, created_at) VALUES(1, ?, 1, 0, 0, 1, 0, ?)",
            arrayOf("默认笔记本", now)
        )
        db.execSQL("UPDATE notes SET notebook_id = 1")
        // 老 categories 表 + notes.category_id 列保留作墓地，业务层不再读写
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
```

**版本号：** `NoteDbHelper.DB_VERSION = 3`

**初次安装（onCreate）：** 不动 v1+v2 创建语句；追加 v3 上面那段创建+预置。统一抽方法 `applyV3Schema(db)` 供 onCreate 与 onUpgrade 共用。

### 2.2 实体类

**`model/entity/Folder.kt`：**
```kotlin
data class Folder(
    val id: Long = 0L,
    val name: String,
    val displayOrder: Int = 0,
    val isDefault: Boolean = false,
    val deletedAt: Long = 0L,
    val createdAt: Long = 0L,
)
```

**`model/entity/Notebook.kt`：**
```kotlin
data class Notebook(
    val id: Long = 0L,
    val name: String,
    val folderId: Long,
    val colorIndex: Int = 0,   // 0..7
    val displayOrder: Int = 0,
    val isDefault: Boolean = false,
    val deletedAt: Long = 0L,
    val createdAt: Long = 0L,
)
```

**`model/entity/Note.kt`** 追加：
```kotlin
val notebookId: Long? = null,  // NULL = "未分类"
```
- `categoryId: Long?` 字段保留兼容（数据墓地路径已不读写），但 cursorToNote 时直接传现值；后续可在 M13 / M14 清理。

### 2.3 Repository 新增

**`model/FolderRepository.kt`** — 仿 NoteRepository object 单例形态：

```kotlin
object FolderRepository {
    private lateinit var dbHelper: NoteDbHelper
    fun init(context: Context) { dbHelper = NoteDbHelper(context.applicationContext) }

    suspend fun list(): List<Folder>             // deleted_at=0，按 display_order
    suspend fun get(id: Long): Folder?
    suspend fun insert(name: String): Long       // 返回 newId；display_order = 当前最大+1
    suspend fun rename(id: Long, newName: String)
    suspend fun softDelete(id: Long)             // 级联软删下面 notebooks + notes，事务
    suspend fun restore(id: Long)                // 仅恢复自身；notebooks/notes 不自动恢复
    suspend fun reorder(orderedIds: List<Long>)  // 批量 UPDATE display_order
}
```

**`model/NotebookRepository.kt`：**

```kotlin
object NotebookRepository {
    private lateinit var dbHelper: NoteDbHelper
    fun init(context: Context) { dbHelper = NoteDbHelper(context.applicationContext) }

    suspend fun listByFolder(folderId: Long): List<Notebook>  // deleted_at=0
    suspend fun listAll(): List<Notebook>                     // 所有未删笔记本（picker 用）
    suspend fun get(id: Long): Notebook?
    suspend fun insert(name: String, folderId: Long, colorIndex: Int): Long
    suspend fun rename(id: Long, newName: String)
    suspend fun updateColor(id: Long, colorIndex: Int)
    suspend fun softDelete(id: Long)                          // 级联软删下面 notes，事务
    suspend fun restore(id: Long)
    suspend fun reorderWithinFolder(folderId: Long, orderedIds: List<Long>)
    suspend fun move(notebookId: Long, newFolderId: Long, newDisplayOrder: Int)  // 跨文件夹拖
}
```

**App.onCreate 注册：**
```kotlin
NoteRepository.init(this)
CategoryRepository.init(this)    // M9 保留（业务层墓地）
FolderRepository.init(this)      // 新
NotebookRepository.init(this)    // 新
```
注：M9 CategoryRepository 不删除（被 ListFilter / NoteListActivity 引用过的代码先维持编译；M13 起逐步清理）。

### 2.4 NoteRepository 改造

- `save(note)` 内 ContentValues 追加：`if (note.notebookId == null) putNull("notebook_id") else put("notebook_id", note.notebookId)`
- `cursorToNote(c)` 内追加：`notebookId = if (c.isNull(idx)) null else c.getLong(idx)`
- `list(filter, sortBy, query)` 内 ListFilter.Notebook 分支：`where += "notebook_id = ?"; args += filter.id.toString()`
- `purgeExpired()` 扩展为同时清 folders / notebooks / notes 三表：仍用 30 天 TTL，DELETE 时按"先 notes 后 notebooks 后 folders"顺序（无外键约束但保持语义清晰）
- 新增 `suspend fun moveNoteToNotebook(noteId: Long, newNotebookId: Long?)` —— 更新 notebook_id + updatedAt，单条 UPDATE

### 2.5 ListFilter 重构

```kotlin
sealed class ListFilter {
    object All : ListFilter()
    object Uncategorized : ListFilter()
    object Favorite : ListFilter()
    object Deleted : ListFilter()
    data class Notebook(val id: Long) : ListFilter()
}
```

- **去掉 Category(id)** —— 改名为 Notebook(id)
- SharedPreferences 序列化 tag 改为 `"notebook"`；反序列化时若 `Notebook(id)` 指向已软删 notebook → 复位 All
- `NoteRepository.list(ListFilter.Uncategorized)` SQL：`WHERE deleted_at=0 AND notebook_id IS NULL`（与 M9 `category_id IS NULL` 语义对称）

---

## 3. UI 层 — 列表页

### 3.1 AppBar 标题区改造

**`activity_note_list.xml`** —— MaterialToolbar 内 inflate custom title view：

```xml
<LinearLayout android:id="@+id/title_anchor"
              android:orientation="horizontal"
              android:gravity="center_vertical"
              android:clickable="true"
              android:focusable="true"
              android:background="?attr/selectableItemBackgroundBorderless">
    <TextView android:id="@+id/title_text"
              android:textSize="20sp"
              android:textStyle="bold"
              tools:text="全部笔记 9"/>
    <ImageView android:id="@+id/title_dropdown_arrow"
               android:src="@drawable/ic_arrow_drop_down"
               android:layout_marginStart="4dp"/>
</LinearLayout>
```

**`NoteListActivity`** 改造：
- onCreate 后 `supportActionBar?.setDisplayShowTitleEnabled(false)` + setCustomView(title_anchor)
- `title_anchor` 点击 → `NotebookFilterPopupWindow.show(title_anchor)`
- `refreshList()` 末尾计算笔记数：`titleText.text = "${currentFilterName()} ${notes.size}"`
  - `currentFilterName()`：All=「全部笔记」/ Uncategorized=「未分类」/ Favorite=「收藏」/ Deleted=「最近删除」/ Notebook(id)=查 NotebookRepository.get(id)?.name ?: "笔记本"

### 3.2 NotebookFilterPopupWindow（顶部下拉）

**新文件：** `view/popup/NotebookFilterPopupWindow.kt`

**布局 `popup_notebook_filter.xml`：**
```xml
<LinearLayout orientation="vertical" background="@drawable/bg_popup_card">
    <!-- 4 内置筛选区 -->
    <RecyclerView id="@+id/builtin_filter_list" /> <!-- 4 项固定 -->
    <View height="1dp" background="@color/divider" />
    <!-- 文件夹+笔记本树形区 -->
    <RecyclerView id="@+id/folder_tree_list" />
    <View height="1dp" background="@color/divider" />
    <!-- 底部管理按钮 -->
    <TextView id="@+id/btn_manage" text="@string/folder_manage_entry" 
              drawableStart="@drawable/ic_folder_settings" />
</LinearLayout>
```

**关键设计：**
- `width = anchor.width`（与标题区等宽）
- `height = WRAP_CONTENT`，但 `maxHeight = screenHeight × 0.6`，超出内部 scroll
- 弹出位置：`showAsDropDown(anchor, 0, 4dp)`
- 触发后调 `loadFoldersAsync` → suspend list + listAll notebooks → 内存按 folderId 分组 → 默认展开第 1 个文件夹
- 内置 4 项 selected 高亮当前 filter
- 点笔记本项 → `onFilterPicked(ListFilter.Notebook(id))` + dismiss
- 点「管理」→ `startActivity(FolderManagerActivity)` + dismiss

**Adapter 设计：** `FolderTreeAdapter`，FlatItem sealed：
```kotlin
sealed class FlatItem {
    data class FolderHeader(val folder: Folder, val expanded: Boolean, val notebookCount: Int) : FlatItem()
    data class NotebookItem(val notebook: Notebook) : FlatItem()
}
```
点 FolderHeader 切 expanded → 重新生成 flat list → notifyDataSetChanged。

### 3.3 列表 ItemView 长按菜单扩展

**`NoteListActivity.showItemPopupMenu(view, note)`**（沿 M9 模式）追加项：
- 已有：删除 / 移到分类（M9，删）
- 新增：**移到笔记本** → `NotebookPickerPopupWindow(context, currentNotebookId = note.notebookId, onPicked = { picked -> launch { NoteRepository.moveNoteToNotebook(note.id, picked) }; refreshList() }).show(view)`
- M9 的"移到分类"项 → 去除（业务层已废弃）

---

## 4. UI 层 — 编辑器

### 4.1 顶部"当前归属 ▼"指示器

**`activity_note_editor.xml`** —— MaterialToolbar 内 inflate 右侧 custom view：

```xml
<LinearLayout android:id="@+id/notebook_indicator_anchor"
              android:orientation="horizontal"
              android:gravity="center_vertical"
              android:clickable="true"
              android:focusable="true"
              android:padding="8dp"
              android:layout_gravity="end"
              android:background="?attr/selectableItemBackgroundBorderless">
    <ImageView android:id="@+id/indicator_icon" src="@drawable/ic_notebook_book" />
    <TextView android:id="@+id/indicator_text"
              android:textColor="?attr/colorPrimary"
              android:textSize="14sp"
              tools:text="未分类"/>
    <ImageView src="@drawable/ic_arrow_drop_down"
               android:tint="?attr/colorPrimary"/>
</LinearLayout>
```

**`NoteEditorActivity`：**
- onCreate 内 `setSupportActionBar(toolbar)` 后注入 indicator 到 toolbar
- `bindNote(note)` 末尾刷 indicator 文本：若 note.notebookId == null → "未分类"；否则 NotebookRepository.get(notebookId)?.name ?: "未分类"
- indicator 点击 → `NotebookPickerPopupWindow(context, currentNotebookId = note.notebookId, onPicked = { picked -> presenter.updateNotebookId(picked); refreshIndicator(picked) }).show(indicator_anchor)`
- 选中后 presenter 持有的 note copy 更新 notebookId；下次 saveNote 写入

**`EditorPresenter.collectCurrentNote()`** 改造：必须把 notebookId 加进 copy 出的 Note（M9 同模式踩过坑：collectCurrentNote 漏字段 → saveNote 清掉用户改动）。

### 4.2 新笔记默认归属（上下文感知）

**`NoteListActivity.onNewNoteClicked()`** 内决策：
```kotlin
val defaultNotebookId = when (val f = currentFilter) {
    is ListFilter.Notebook -> f.id
    else -> 1L  // 默认笔记本固定 id=1
}
// startActivity 时通过 intent extra 传给 EditorActivity
intent.putExtra(EXTRA_INITIAL_NOTEBOOK_ID, defaultNotebookId)
```

**`NoteEditorActivity.onCreate`**：若 intent 含 EXTRA_INITIAL_NOTEBOOK_ID 且当前 note.id == 0L → presenter.initialNote.copy(notebookId = it)

---

## 5. UI 层 — NotebookPickerPopupWindow（双入口共用）

**新文件：** `view/popup/NotebookPickerPopupWindow.kt`

**布局 `popup_notebook_picker.xml`：**
```xml
<LinearLayout orientation="vertical" background="@drawable/bg_popup_card">
    <!-- 「未分类」单独一行 -->
    <TextView id="@+id/item_uncategorized" 
              text="@string/notebook_uncategorized"
              drawableStart="@drawable/ic_notebook_unassigned"/>
    <View height="1dp" background="@color/divider"/>
    <!-- 树形：FolderHeader + NotebookItem + 「+ 新建」 -->
    <RecyclerView id="@+id/tree_list"/>
</LinearLayout>
```

**API：**
```kotlin
class NotebookPickerPopupWindow(
    private val context: Context,
    private val currentNotebookId: Long?,
    private val onPicked: (notebookId: Long?) -> Unit
)
fun show(anchor: View)
```

**Adapter `NotebookPickerAdapter`** FlatItem 类型：
```kotlin
sealed class FlatItem {
    data class FolderHeader(val folder: Folder, val expanded: Boolean) : FlatItem()
    data class NotebookItem(val notebook: Notebook) : FlatItem()
    data class CreateNotebookRow(val folderId: Long) : FlatItem()
}
```

- 「未分类」行 selected 高亮当对应 `currentNotebookId == null`
- NotebookItem selected 高亮当 `notebook.id == currentNotebookId`
- 点 NotebookItem 或「未分类」→ `onPicked(id 或 null)` + dismiss
- 点 CreateNotebookRow → `NewNotebookBottomSheet(context, folderId, editing = null, onSaved = { newId -> onPicked(newId); dismiss() })` 起 + 不立即 dismiss（等 sheet 回调）
- 默认展开第 1 个文件夹；其余折叠
- 若 currentNotebookId 指向某文件夹下的笔记本 → 默认该文件夹展开

**编辑器版与列表长按版差异：** 仅 anchor 位置不同；本组件统一 includeManageButton = false（不显示管理按钮）。

---

## 6. UI 层 — FolderManagerActivity

**新 Activity：** `controller/folder/FolderManagerActivity.kt`

**布局 `activity_folder_manager.xml`：**
```
MaterialToolbar 标题「文件夹管理」+ ← 返回
[+ 新建文件夹] 按钮（toolbar 右侧 menu item）
RecyclerView（FolderHeader / NotebookItem / CreateNotebookRow 多类型）
```

**Adapter `FolderManagerAdapter`：**
- 3 ViewHolder type：FolderHeaderVH / NotebookItemVH / CreateRowVH
- FolderHeaderVH 内：folder name + ▼/▶ 折叠图标 + 右侧 [✎ 改名] +（非默认时）[✕ 删除]
- NotebookItemVH 内：8 色圆点 + 名称 + 右侧 [✎ 改名+改色（一起入 NewNotebookBottomSheet 复用 edit 模式）] +（非默认时）[✕ 删除] + 拖动 handle
- CreateRowVH 在每文件夹展开末尾「+ 新建笔记本」

**ItemTouchHelper 三粒度：**
```kotlin
val callback = object : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
) {
    override fun getDragDirs(rv, vh) = when (vh) {
        is FolderHeaderVH, is NotebookItemVH -> ItemTouchHelper.UP or ItemTouchHelper.DOWN
        else -> 0  // CreateRow 不拖
    }
    override fun onMove(rv, src, target): Boolean {
        // 同类互拖 → swap + 标脏 displayOrder
        // NotebookItem 拖到 FolderHeader → 改 folder_id + 重置 display_order
        // FolderHeader 拖到 NotebookItem → 拒绝（return false）
        ...
    }
    override fun clearView(rv, vh) {
        // 批量 flush 顺序到 DB（事务）+ refreshList
    }
}
```

**改名 UI（两种走向）：**
- **文件夹改名：** Material AlertDialog + EditText（沿 M9 风格，单字段无需 BottomSheet）
- **笔记本改名+改色：** 复用 §7.1 NewNotebookBottomSheet 的 edit 模式（`editing != null` 路径），名 + 色统一在一个 sheet 内修改

**删除：** 点 [✕] → DeleteConfirmBottomSheet（M9 通用化已有），文案：
- 文件夹：「删除后下面 N 个笔记本与 M 篇笔记将一同进入「最近删除」」
- 笔记本：「删除后下面 M 篇笔记将一同进入「最近删除」」

**Activity 启动：** `NotebookFilterPopupWindow` 底部「管理」按钮 → `startActivity(Intent(this, FolderManagerActivity::class.java))`

---

## 7. UI 层 — NewNotebookBottomSheet / NewFolderBottomSheet

### 7.1 NewNotebookBottomSheet

**新文件：** `view/sheet/NewNotebookBottomSheet.kt`

**布局 `sheet_new_notebook.xml`：**
```
顶部标题「新建笔记本」/「编辑笔记本」
EditText 输入名（hint「笔记本名称」）
8 色圆点横排（btn_color_0..7，单选 selected 描边）
[取消] [确定] 双按钮
```

**API：**
```kotlin
class NewNotebookBottomSheet(
    context: Context,
    private val folderId: Long,
    private val editing: Notebook? = null,
    private val onSaved: (notebookId: Long) -> Unit
) : BottomSheetDialog(context)
```

- 新建路径：editing=null → 输入名 + 选色 → 确定 → `NotebookRepository.insert(name, folderId, colorIndex)` → onSaved(newId)
- 编辑路径：editing!=null → 字段预填 → 确定 → `NotebookRepository.rename + updateColor`
- 空名拦截（disable 确定按钮）+ 双触守卫（`var fired = false` 跨 cancel/confirm）

### 7.2 NewFolderBottomSheet

**新文件：** `view/sheet/NewFolderBottomSheet.kt`

简化版：仅 EditText 输入名 + [取消] [确定]。新建路径 `FolderRepository.insert(name)`。

---

## 8. 颜色资源

### 8.1 8 色 hex（color_index 0..7 → res 映射）

**`res/values/colors.xml` 新增：**
```xml
<color name="notebook_color_0">#9E9E9E</color>  <!-- 默认灰 -->
<color name="notebook_color_1">#E53935</color>  <!-- 红 -->
<color name="notebook_color_2">#FB8C00</color>  <!-- 橙 -->
<color name="notebook_color_3">#FBC02D</color>  <!-- 黄 -->
<color name="notebook_color_4">#43A047</color>  <!-- 绿 -->
<color name="notebook_color_5">#00ACC1</color>  <!-- 青 -->
<color name="notebook_color_6">#1E88E5</color>  <!-- 蓝 -->
<color name="notebook_color_7">#8E24AA</color>  <!-- 紫 -->
```

`NotebookColors.kt` 工具：
```kotlin
object NotebookColors {
    fun resForIndex(idx: Int): Int = when (idx) {
        0 -> R.color.notebook_color_0
        ...
        else -> R.color.notebook_color_0
    }
    fun count(): Int = 8
}
```

### 8.2 笔记本图标 drawable

- `ic_notebook_book.xml`：立起的小书页 vector（参考图样），可 tint 着色
- `ic_notebook_unassigned.xml`：灰色虚线书页（"未分类"用）
- `ic_folder_outline.xml`：文件夹轮廓 vector（FolderHeader 用）
- `ic_arrow_drop_down.xml` / `ic_arrow_drop_right.xml`：折叠箭头
- `ic_folder_settings.xml`：齿轮（PopupWindow 底部「管理」用）
- `bg_popup_card.xml`：圆角白底 + 阴影 9-patch 风格 drawable

---

## 9. 字符串资源

**`res/values/strings.xml` 新增：**
```xml
<string name="filter_all">全部笔记</string>
<string name="filter_uncategorized">未分类</string>
<string name="filter_favorite">收藏</string>
<string name="filter_deleted">最近删除</string>
<string name="notebook_uncategorized">未分类</string>
<string name="notebook_default">默认笔记本</string>
<string name="folder_default">默认文件夹</string>
<string name="folder_manage_entry">管理</string>
<string name="folder_manage_title">文件夹管理</string>
<string name="action_new_folder">+ 新建文件夹</string>
<string name="action_new_notebook">+ 新建笔记本</string>
<string name="action_move_to_notebook">移到笔记本</string>
<string name="sheet_title_new_notebook">新建笔记本</string>
<string name="sheet_title_edit_notebook">编辑笔记本</string>
<string name="sheet_title_new_folder">新建文件夹</string>
<string name="hint_notebook_name">笔记本名称</string>
<string name="hint_folder_name">文件夹名称</string>
<string name="confirm_delete_folder_cascade">删除「%1$s」后，下面 %2$d 个笔记本与 %3$d 篇笔记将一同进入「最近删除」</string>
<string name="confirm_delete_notebook_cascade">删除「%1$s」后，下面 %2$d 篇笔记将一同进入「最近删除」</string>
```

---

## 10. 数据流（关键场景）

### 10.1 用户切换 filter

```
点 AppBar 标题区
  → NotebookFilterPopupWindow.show(anchor)
  → 异步 loadFolders + loadAllNotebooks + groupByFolderId → UI
  → 用户点某笔记本（如「工作」id=5）
  → onFilterPicked(ListFilter.Notebook(5))
  → currentFilter = Notebook(5); SharedPreferences 存 tag="notebook" id=5
  → refreshList(): NoteRepository.list(Notebook(5)) → notes
  → titleText.text = "工作 ${notes.size}"
```

### 10.2 用户在编辑器切归属

```
点编辑器右上「未分类 ▼」
  → NotebookPickerPopupWindow.show(indicator_anchor, currentNotebookId=null, includeManageButton=false)
  → 用户点某笔记本（如「旅游」id=3）
  → onPicked(3)
  → presenter.updateNotebookId(3)  // 更新 internal copy
  → indicator_text 立即刷为「旅游」
  → 下次 saveNote 时持久化（M11 onSaveSuccess 路径触发）
```

### 10.3 用户跨文件夹拖笔记本

```
FolderManagerActivity 内长按某笔记本 → 拖到另一 FolderHeader 下方
  → ItemTouchHelper.onMove 内：
       src=NotebookItem(notebook=N, folderId=A)
       target=NotebookItem 或 FolderHeader(folder=B)
       新 folderId=B, 新 displayOrder=target.displayOrder+1
       内存 list 重新排
       notifyItemMoved
  → 拖完 clearView：
       事务批量 UPDATE notebooks SET folder_id=?, display_order=?
       refreshList 刷新内存数据
```

### 10.4 用户在文件夹管理页删除某文件夹

```
点文件夹右侧 [✕]
  → DeleteConfirmBottomSheet（M9 通用），文案带级联数量
  → 用户确认
  → FolderRepository.softDelete(folderId) 事务：
       UPDATE folders SET deleted_at=now WHERE id=?
       UPDATE notebooks SET deleted_at=now WHERE folder_id=? AND deleted_at=0
       UPDATE notes SET deleted_at=now WHERE notebook_id IN (该 folder 下所有 notebook ids) AND deleted_at=0
  → adapter.refresh()
```

### 10.5 用户从最近删除恢复一篇笔记，父 notebook 已软删

```
最近删除页点恢复
  → NoteRepository.restore(noteId)
       // 现 M9 行为：UPDATE notes SET deleted_at=0
       // M12 新增：先检查父 notebook 是否仍软删
       val pnb = note.notebookId?.let { NotebookRepository.get(it) }
       if (pnb == null || pnb.deletedAt > 0) {
           UPDATE notes SET deleted_at=0, notebook_id=1 WHERE id=?  // 1=默认笔记本
       } else {
           UPDATE notes SET deleted_at=0 WHERE id=?
       }
  → refreshList()
```

---

## 11. 边界 + 错误处理

### 11.1 默认项保护

- FolderManagerActivity 内：渲染 FolderHeader/NotebookItem 时 `if (item.isDefault) deleteBtn.visibility = GONE`
- Repository 层防御：FolderRepository.softDelete(1L) 或 NotebookRepository.softDelete(1L) 直接 throw IllegalStateException("默认项不可删")（万一 UI 漏判）
- 改名改色无限制（默认项 isDefault 不变）

### 11.2 「未分类」是 sentinel

- 不在 notebooks 表里实例化；编辑器 picker 与列表 PopupWindow 内 UI 渲染时单独一行
- 不能改名（无管理入口）；NotebookRepository.rename(0L) 等无效调用做安全 return

### 11.3 跨文件夹拖最后一个笔记本

- 不限制（折叠状态下文件夹也合法存在）
- 文件夹只能由用户主动删除才消失

### 11.4 创建笔记本时所选文件夹刚被人删

- NewNotebookBottomSheet 创建前再次校验 folder 存在且 deleted_at=0
- 不存在 / 已软删 → Toast「该文件夹已被删除」+ dismiss

### 11.5 选了的 filter 笔记本被软删

- NoteListActivity onResume 时检查 SharedPreferences 存的 filter 是否仍有效
- 无效 → 复位 ListFilter.All；更新 SharedPreferences

### 11.6 编辑器 indicator 当前显示的笔记本被另一处操作软删

- 简化：不做实时观察。下次 onResume 时 bindNote 重新查 → 若软删 → 显示「未分类」+ 笔记 notebookId 改 NULL（持久化）
- 提示：下次 save 触发；用户感知"刚才挪到的笔记本不见了"是接受的简化

### 11.7 拖动后 DB 写失败

- ItemTouchHelper.clearView 内的批量 UPDATE 用事务；catch 失败 → Toast「保存顺序失败」+ 重新 refreshList 从 DB 取真实顺序
- 不影响数据完整性

### 11.8 老 categories 数据残留

- categories 表 + notes.category_id 列保留，业务层完全不读写
- 后续 M13/M14 视情况清理（DB v4），本里程碑不做

---

## 12. 测试策略

### 12.1 单元测试（纯 JVM, JUnit 5 + Robolectric for DB）

**`FolderRepositoryTest`**（Robolectric，~7 项）：
- insert 返回 newId > 0 + list 能取回
- rename / softDelete / restore / reorder 基础
- softDelete 级联：建文件夹+笔记本+笔记三层 → softDelete 文件夹 → 三层 deleted_at 都被设
- 默认项保护：softDelete(1L) throw

**`NotebookRepositoryTest`**（Robolectric，~7 项）：
- insert / listByFolder / rename / updateColor / softDelete 级联到 notes
- move 跨文件夹：notebook.folder_id 改变 + display_order 重置
- 默认项保护

**`DbV3MigrationTest`**（Robolectric，~3 项）：
- 模拟 v2 → v3 升级：执行迁移后 folders/notebooks 表存在 + 各 1 条默认行 + 老 notes.notebook_id 全 = 1
- 已 v3 全新安装：仅创建表 + 预置默认行
- v2 升级时若已有 categories 数据：保留不动，notes.category_id 列存活但 notebook_id 全填默认

**`ListFilterTest`**（纯 JVM，~3 项）：
- Notebook(id) 序列化/反序列化对称
- 反序列化 Notebook(99) 但 NotebookRepository 没这条 → 复位 All
- 旧 SharedPreferences tag="category" → 安全降级 All（兼容老数据）

**预期增量：约 +20 单测，总数 114 → ~134**

### 12.2 不写自动化（沿"写完即手测"约定）

- NotebookFilterPopupWindow / NotebookPickerPopupWindow（PopupWindow + RecyclerView 行为）
- FolderManagerActivity 三粒度拖动
- 编辑器右上 indicator 同步刷新
- 颜色圆点选中态描边
- BottomSheet 输入校验
- 这些都进真机走查清单

### 12.3 真机走查清单（≥12 条，验收门）

1. 首次安装 → 顶部下拉显示「默认文件夹 > 默认笔记本」+ 4 内置筛选；老笔记全在「默认笔记本」下
2. 顶部下拉点 + 在某文件夹下新建笔记本 → 输入名 + 选红色 → 出现于该文件夹下，红色图标
3. 顶部下拉点「管理」→ 进入 FolderManagerActivity → + 新建文件夹「学习」
4. 在 FolderManagerActivity 把笔记本「工作」从「默认文件夹」拖到「学习」→ 上方下拉树形更新
5. 在 FolderManagerActivity 拖文件夹「学习」到「默认文件夹」上方 → display_order 持久化
6. 列表长按某笔记 → 「移到笔记本」→ 选「学习 > 工作」→ 列表刷新（已切到「工作」filter 才看得到）
7. 切到「工作」filter，标题显示「工作 N」N 准确
8. 进编辑器 → 右上「工作 ▼」→ 点 → 选「未分类」→ indicator 即变「未分类」→ 返回后笔记从「工作」filter 消失，「未分类」filter 出现
9. 删除一个非默认文件夹 → 二确认含级联数 → 文件夹/下属笔记本/笔记全进「最近删除」
10. 最近删除恢复其中一篇笔记 → 笔记出现在「默认笔记本」filter（因父 notebook 仍软删，落到默认）
11. FolderManagerActivity 看「默认文件夹」「默认笔记本」无删除按钮，改名改色正常
12. 切语言/转屏 → AppBar 标题区不变形；PopupWindow 关闭并能再次弹出
13. 在某笔记本 filter 下点 + 新建笔记 → 自动归该笔记本；在「全部」filter 下点 + 新建 → 归默认笔记本
14. process death 后 SharedPreferences 存的 filter（Notebook(5)）若 id=5 已被软删 → 重启自动落到「全部」filter

---

## 13. 不变量小结

- folders → notebooks → notes 三级，notebooks.folder_id NOT NULL，notes.notebook_id NULLABLE
- 「未分类」是 sentinel，不在表中
- 默认文件夹/笔记本 is_default=1，不能删、能改名改色
- 删除走软删 + 级联事务；30 天后 purgeExpired
- 恢复笔记时若父 notebook 软删 → 笔记 notebook_id 改默认（id=1）
- 列表 filter 持久化无效时复位 All
- 拖动三粒度，跨文件夹拖通过 onMove 检测 target type 切换 folder_id
- NotebookPickerPopupWindow 双入口共用，含「未分类」选项
- NoteJson 不变（不加 notebookId）
- 老 categories 数据墓地保留

---

## 14. 与用户原始需求的对齐

| 原始条目 | 本设计如何覆盖 |
|---|---|
| ③ 新层级 文件夹-笔记本-笔记 | §2 DB v3 + folders/notebooks 表 + notebook_id 列 |
| ③ 顶部下拉展开 | §3.1 + §3.2 NotebookFilterPopupWindow |
| ③ 文件夹管理（新建/编辑/拖动） | §6 FolderManagerActivity |
| ③ 新建笔记本 8 色选择 | §7 NewNotebookBottomSheet + §8 NotebookColors |
| 编辑器顶部右侧"当前笔记本名 ▼" | §4 + §5 NotebookPickerPopupWindow |
| 列表长按"移到笔记本" | §3.3 |
| 内置筛选 全部/未分类/收藏/最近删除 | §2.5 ListFilter sealed + §3.2 NotebookFilterPopupWindow |

---

## 15. 工作量估算

- 数据层：DB v3 迁移 + 2 实体 + 2 Repository + NoteRepository 改造 + ListFilter 重构 → 5 任务
- UI 层：AppBar 标题改造 + 2 PopupWindow + FolderManagerActivity + 2 BottomSheet + 编辑器 indicator + 列表长按菜单扩展 → 6 任务
- 测试：DB 迁移 + 2 Repository + ListFilter → 1 任务
- 真机走查 + STATUS 收尾 → 1 任务
- ≤ 13 任务实施计划（下一步 writing-plans 阶段细化）
- 预估 2-3 天

---

## 16. 不动的东西（明确边界）

- M1-M11 全部代码、布局、资源 —— 一行不改，除非 §3-§9 明确列出
- 手写 Overlay / BrushPainter / StrokeEraser / AudioRecorder / AudioPlayer / EditHistoryManager / Command / TextBlockView 等 M6-M11 既有组件 —— 完全保留
- M9 DeleteConfirmBottomSheet —— 复用
- M9 CategoryRepository / CategoryManagerBottomSheet / CategoryPickerBottomSheet —— 物理保留，业务层不再调（M13/M14 渐进清理）
- NoteFileStorage —— 完全不动
- NoteJson 序列化 —— 完全不动
- 编辑器 M11 撤销重做 —— 完全保留（笔记本切换不入 history 栈，因元数据切换不属于内容编辑）

---

**审稿状态：** brainstorm 13 问全部确认，进入 spec 自审 + 用户审阅环节。
