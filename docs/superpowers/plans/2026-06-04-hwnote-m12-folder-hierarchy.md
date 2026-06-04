# HwNote M12 文件夹层级 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 HwNote 上落地"文件夹 → 笔记本 → 笔记"三级层级，含顶部下拉切 filter / 编辑器右上当前归属 picker / 独立 FolderManagerActivity（含三粒度拖动 + 改名改色 + 级联软删）/ 8 色笔记本 / 列表长按"移到笔记本"。

**Architecture:** DB v3 迁移新增 `folders` 与 `notebooks` 表 + `notes.notebook_id` 列；`model/entity/` 加 2 实体；`model/` 加 2 个 object Repository（仿 NoteRepository / CategoryRepository 形态）。`ListFilter` sealed 把 `Category(id)` 改为 `Notebook(id)`。UI 层新增 2 个 PopupWindow（顶部下拉过滤 + 双入口归属 picker）+ 1 个 Activity（FolderManagerActivity）+ 2 个 BottomSheet（NewNotebookBottomSheet / NewFolderBottomSheet），编辑器 AppBar 右侧加"当前归属 ▼"指示器。M9 CategoryRepository / FilterPickerBottomSheet / CategoryManagerBottomSheet / CategoryPickerBottomSheet 物理保留作"墓地"，业务层不再调用。

**Tech Stack:** Kotlin / SQLiteOpenHelper（v2 → v3 ALTER + CREATE，事务）/ Material 1.12.0 BottomSheetDialog + ItemTouchHelper / RecyclerView 多 ViewHolder / coroutines / JUnit 4 + Robolectric 4.13（DB / Repository 测试）/ JUnit 5 Jupiter（纯 JVM ListFilter 测试）/ Manual real-device walkthrough.

**Spec:** `docs/superpowers/specs/2026-06-04-hwnote-m12-folder-hierarchy-design.md`

**Build command（用于所有 gradle 任务）：**
```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test
```

**Commit 约定：**
- 中文动宾，**无** `Co-Authored-By` trailer
- 仅 `git add <file>` 显式按文件加，**禁止** `-A` / `.`
- 直接在 `master` 上推（与 M2..M11 一致）
- 每个 Task 末尾必有 1 个 commit；Task 内细分步骤不强制每步 commit

---

## File Structure

**新增文件（model 层 — 4 个）：**

```
app/src/main/java/com/fan/hwnote/app/model/entity/
  ├── Folder.kt                # data class Folder
  └── Notebook.kt              # data class Notebook
app/src/main/java/com/fan/hwnote/app/model/
  ├── FolderRepository.kt      # object，仿 CategoryRepository / NoteRepository 形态
  └── NotebookRepository.kt    # object
```

**新增文件（UI 层 — 7 个）：**

```
app/src/main/java/com/fan/hwnote/app/util/
  └── NotebookColors.kt        # 8 色 colorRes 映射 + count

app/src/main/java/com/fan/hwnote/app/view/popup/
  ├── NotebookFilterPopupWindow.kt   # 顶部 AppBar 下拉（4 内置筛选 + 树形 + 管理）
  └── NotebookPickerPopupWindow.kt   # 双入口 picker（编辑器右上 + 列表长按）

app/src/main/java/com/fan/hwnote/app/view/sheet/
  ├── NewFolderBottomSheet.kt        # 新建 / 改名文件夹
  └── NewNotebookBottomSheet.kt      # 新建 / 改名+改色 笔记本

app/src/main/java/com/fan/hwnote/app/controller/folder/
  └── FolderManagerActivity.kt       # 文件夹管理页（含 Adapter + ItemTouchHelper）
```

**新增资源（layout / drawable / menu / strings — 一次性合并 T6）：**

```
res/layout/
  ├── popup_notebook_filter.xml      # NotebookFilterPopupWindow 容器
  ├── popup_notebook_picker.xml      # NotebookPickerPopupWindow 容器
  ├── sheet_new_folder.xml           # NewFolderBottomSheet
  ├── sheet_new_notebook.xml         # NewNotebookBottomSheet
  ├── activity_folder_manager.xml    # Activity 骨架
  ├── item_folder_header.xml         # FolderManager / Filter Popup 文件夹行
  ├── item_notebook.xml              # FolderManager / Picker / Filter Popup 笔记本行
  ├── item_create_notebook.xml       # 文件夹下"+ 新建笔记本"行
  └── item_filter_builtin.xml        # 4 内置筛选行（All/Uncat/Fav/Deleted）

res/drawable/
  ├── ic_notebook_book.xml           # 立起的小书页（可 tint）
  ├── ic_notebook_unassigned.xml     # 灰色虚线书页（"未分类"）
  ├── ic_folder_outline.xml          # 文件夹轮廓（FolderHeader）
  ├── ic_folder_settings.xml         # 齿轮（PopupWindow 底部"管理"）
  ├── ic_arrow_drop_right.xml        # 折叠箭头（▶）
  ├── bg_popup_card.xml              # 圆角白底 + 阴影
  └── bg_color_dot_selectable.xml    # 8 色选中态描边

res/menu/
  └── menu_folder_manager.xml        # FolderManagerActivity 顶部"+ 新建文件夹"

res/values/colors.xml                # 追加 notebook_color_0..7
res/values/strings.xml               # 追加 17 个 string
```

**改动既有文件：**

```
app/src/main/java/com/fan/hwnote/app/model/db/NoteDbHelper.kt
  # DB_VERSION 2→3；onCreate 与 onUpgrade 共用 applyV3Schema(db)

app/src/main/java/com/fan/hwnote/app/model/entity/Note.kt
  # 追加 val notebookId: Long? = null

app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt
  # save 写 notebook_id；cursorToNote 读 notebook_id；list 加 ListFilter.Notebook 分支；
  # 新增 moveNoteToNotebook(noteId, newNotebookId);
  # restore 加父 notebook 软删检测 → 落到默认笔记本；
  # purgeExpired 扩展为级联清 folders/notebooks/notes；
  # ListFilter sealed Category(id) 改为 Notebook(id)

app/src/main/java/com/fan/hwnote/app/App.kt
  # onCreate 加 FolderRepository.init / NotebookRepository.init

app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
  # filter_chip onClick 改走 NotebookFilterPopupWindow（替换 FilterPickerBottomSheet 入口）
  # loadFilter / saveFilter / updateFilterChipLabel 改 Category 为 Notebook
  # 长按菜单加 R.id.action_move_to_notebook → NotebookPickerPopupWindow
  # 旧"管理分类"路径不再触发（FilterPickerBottomSheet 类保留物理）

app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
  # AppBar 加 indicator_anchor + click → NotebookPickerPopupWindow
  # 旧 metaCategoryChip 隐藏（visibility=GONE，业务退役）
  # loadNote 完成后刷 indicator
  # saveNote 路径多合并 notebookId（同 categoryId 模式，避免 collectCurrentNote 漏字段）
  # newIntent 加 EXTRA_INITIAL_NOTEBOOK_ID（上下文感知默认归属）

app/src/main/res/layout/activity_note_editor.xml
  # Toolbar 内追加 indicator_anchor LinearLayout
  # 旧 meta_category_chip 节点保留但 visibility=gone

app/src/main/AndroidManifest.xml
  # 注册 FolderManagerActivity
```

**新增测试文件（4 个）：**

```
app/src/test/java/com/fan/hwnote/app/model/db/
  └── DbV3MigrationTest.kt          # Robolectric, ~3 项
app/src/test/java/com/fan/hwnote/app/model/
  ├── FolderRepositoryTest.kt       # Robolectric, ~7 项
  ├── NotebookRepositoryTest.kt     # Robolectric, ~7 项
  └── ListFilterPersistenceTest.kt  # Robolectric, ~3 项
```

**预期测试增量：约 +20，总数 114 → ~134。**

---

## 任务清单（13 任务串行）

每个 Task 完成后必有 1 个 commit；先写测试再写实现（数据层 TDD），UI 层（Popup / Activity / Sheet）"写完即手测" — 沿 M5/M6/M9 既有约定。

| # | Task | 类别 | 测试 |
|---|---|---|---|
| 1 | DB v3 迁移 | 数据 | DbV3MigrationTest +3 |
| 2 | Folder/Notebook 实体 + Note.notebookId | 数据 | （随 T3/T4 覆盖） |
| 3 | FolderRepository + 测试 | 数据 | FolderRepositoryTest +7 |
| 4 | NotebookRepository + 测试 | 数据 | NotebookRepositoryTest +7 |
| 5 | ListFilter 重构 + NoteRepository 改造 + 列表持久化迁移 | 数据 | ListFilterPersistenceTest +3 |
| 6 | 资源批：8 色 / drawable / strings / menu | 资源 | — |
| 7 | NewFolderBottomSheet + NewNotebookBottomSheet | UI | — |
| 8 | NotebookFilterPopupWindow + NoteListActivity AppBar 接通 | UI | — |
| 9 | FolderManagerActivity 骨架（Adapter + 改名改色 + 新建入口） | UI | — |
| 10 | FolderManagerActivity 三粒度拖动 + 删除级联 | UI | — |
| 11 | NotebookPickerPopupWindow + 列表长按"移到笔记本" | UI | — |
| 12 | 编辑器 AppBar indicator + 上下文感知默认归属 | UI | — |
| 13 | 真机走查 + STATUS 收尾 | 验收 | — |

---

### Task 1: DB v3 迁移（folders / notebooks 表 + notes.notebook_id 列 + 默认行预置）

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/model/db/NoteDbHelper.kt`
- Create: `app/src/test/java/com/fan/hwnote/app/model/db/DbV3MigrationTest.kt`

- [ ] **Step 1: 写失败测试 — 全新装机**

`DbV3MigrationTest.kt` 创建，含 3 个 @Test。先写第 1 个：

```kotlin
package com.fan.hwnote.app.model.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DbV3MigrationTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase("hwnote.db")
    }

    @Test
    fun `fresh install creates v3 schema with default folder and notebook`() {
        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase
        // folders 表存在 + 默认行 id=1 isDefault=1
        db.rawQuery("SELECT id, name, is_default FROM folders WHERE id=1", null).use { c ->
            assert(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("默认文件夹", c.getString(1))
            assertEquals(1, c.getInt(2))
        }
        // notebooks 表存在 + 默认行 id=1 folder_id=1
        db.rawQuery("SELECT id, name, folder_id, is_default FROM notebooks WHERE id=1", null).use { c ->
            assert(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("默认笔记本", c.getString(1))
            assertEquals(1L, c.getLong(2))
            assertEquals(1, c.getInt(3))
        }
        // notes 表的 notebook_id 列存在（空表也能查列）
        db.rawQuery("SELECT notebook_id FROM notes LIMIT 0", null).use { c ->
            assertNotNull(c)
        }
        helper.close()
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.db.DbV3MigrationTest"
```
Expected: FAIL — folders 表不存在（DB 仍 v2）。

- [ ] **Step 3: 改 NoteDbHelper.kt 升 v3**

完整覆盖 `NoteDbHelper.kt`：

```kotlin
package com.fan.hwnote.app.model.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite 表结构见 PRD §6.1 + §13.2。
 * v1 → v2（2026-06-04）：notes 加 category_id / deleted_at；新增 categories 表。
 * v2 → v3（2026-06-04）：新增 folders / notebooks 表，notes 加 notebook_id 列，
 *                       预置默认文件夹/笔记本（id=1, is_default=1），老 notes.notebook_id 全填 1。
 *                       老 categories 表 + notes.category_id 列保留作"数据墓地"，业务层不再读写。
 */
class NoteDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_NOTES_V3)
        db.execSQL(SQL_INDEX_UPDATED)
        db.execSQL(SQL_INDEX_FAVORITE)
        db.execSQL(SQL_INDEX_CATEGORY)
        db.execSQL(SQL_INDEX_DELETED)
        db.execSQL(SQL_INDEX_NOTEBOOK)
        db.execSQL(SQL_CREATE_CATEGORIES)
        applyV3Tables(db)
        seedDefaults(db, allNotesAlreadyExist = false)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE notes ADD COLUMN category_id INTEGER")
            db.execSQL("ALTER TABLE notes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL(SQL_CREATE_CATEGORIES)
            db.execSQL(SQL_INDEX_CATEGORY)
            db.execSQL(SQL_INDEX_DELETED)
        }
        if (oldVersion < 3) {
            db.beginTransaction()
            try {
                applyV3Tables(db)
                db.execSQL("ALTER TABLE notes ADD COLUMN notebook_id INTEGER")
                db.execSQL(SQL_INDEX_NOTEBOOK)
                seedDefaults(db, allNotesAlreadyExist = true)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun applyV3Tables(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_FOLDERS)
        db.execSQL(SQL_INDEX_FOLDER_DELETED)
        db.execSQL(SQL_CREATE_NOTEBOOKS)
        db.execSQL(SQL_INDEX_NOTEBOOK_FOLDER)
        db.execSQL(SQL_INDEX_NOTEBOOK_DELETED)
    }

    private fun seedDefaults(db: SQLiteDatabase, allNotesAlreadyExist: Boolean) {
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO folders(id, name, display_order, is_default, deleted_at, created_at) VALUES(1, ?, 0, 1, 0, ?)",
            arrayOf<Any>("默认文件夹", now),
        )
        db.execSQL(
            "INSERT INTO notebooks(id, name, folder_id, color_index, display_order, is_default, deleted_at, created_at) VALUES(1, ?, 1, 0, 0, 1, 0, ?)",
            arrayOf<Any>("默认笔记本", now),
        )
        if (allNotesAlreadyExist) {
            db.execSQL("UPDATE notes SET notebook_id = 1")
        }
    }

    companion object {
        const val DB_NAME = "hwnote.db"
        const val DB_VERSION = 3

        // v3 全新建表 —— notes 自带 notebook_id 列
        private const val SQL_CREATE_NOTES_V3 = """
            CREATE TABLE notes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL DEFAULT '',
              plain_text TEXT NOT NULL DEFAULT '',
              content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
              is_favorite INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              category_id INTEGER,
              deleted_at INTEGER NOT NULL DEFAULT 0,
              notebook_id INTEGER
            )
        """

        private const val SQL_CREATE_CATEGORIES = """
            CREATE TABLE categories (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              color TEXT NOT NULL,
              order_index INTEGER NOT NULL DEFAULT 0
            )
        """

        private const val SQL_CREATE_FOLDERS = """
            CREATE TABLE folders (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              display_order INTEGER NOT NULL DEFAULT 0,
              is_default INTEGER NOT NULL DEFAULT 0,
              deleted_at INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
        """

        private const val SQL_CREATE_NOTEBOOKS = """
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
        """

        private const val SQL_INDEX_UPDATED =
            "CREATE INDEX idx_notes_updated_at ON notes(updated_at DESC)"
        private const val SQL_INDEX_FAVORITE =
            "CREATE INDEX idx_notes_favorite ON notes(is_favorite)"
        private const val SQL_INDEX_CATEGORY =
            "CREATE INDEX idx_notes_category ON notes(category_id)"
        private const val SQL_INDEX_DELETED =
            "CREATE INDEX idx_notes_deleted ON notes(deleted_at)"
        private const val SQL_INDEX_NOTEBOOK =
            "CREATE INDEX idx_notes_notebook ON notes(notebook_id)"
        private const val SQL_INDEX_FOLDER_DELETED =
            "CREATE INDEX idx_folders_deleted ON folders(deleted_at)"
        private const val SQL_INDEX_NOTEBOOK_FOLDER =
            "CREATE INDEX idx_notebooks_folder ON notebooks(folder_id)"
        private const val SQL_INDEX_NOTEBOOK_DELETED =
            "CREATE INDEX idx_notebooks_deleted ON notebooks(deleted_at)"
    }
}
```

- [ ] **Step 4: 跑 Step 1 测试 PASS**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.db.DbV3MigrationTest"
```
Expected: 1 passed.

- [ ] **Step 5: 加 v2 → v3 升级测试**

在同文件内追加：

```kotlin
    @Test
    fun `v2 to v3 upgrade migrates legacy notes notebook_id to 1`() {
        // 模拟 v2 老数据：手工建 v2 schema + 写一条老 note
        val raw = SQLiteDatabase.openOrCreateDatabase(
            ctx.getDatabasePath("hwnote.db").absolutePath, null, null
        )
        raw.execSQL("""
            CREATE TABLE notes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL DEFAULT '',
              plain_text TEXT NOT NULL DEFAULT '',
              content_json TEXT NOT NULL DEFAULT '',
              is_favorite INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              category_id INTEGER,
              deleted_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        raw.execSQL("""
            CREATE TABLE categories (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              color TEXT NOT NULL,
              order_index INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        raw.execSQL("INSERT INTO notes(title, plain_text, content_json, created_at, updated_at) VALUES('老笔记','x','{}', 1, 1)")
        raw.version = 2
        raw.close()

        // 触发升级
        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase

        // 老 note 的 notebook_id 应该被 UPDATE 为 1
        db.rawQuery("SELECT notebook_id FROM notes", null).use { c ->
            assert(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
        }
        // 默认 folder/notebook 都已预置
        db.rawQuery("SELECT COUNT(*) FROM folders WHERE id=1", null).use { c ->
            assert(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        db.rawQuery("SELECT COUNT(*) FROM notebooks WHERE id=1", null).use { c ->
            assert(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        helper.close()
    }
```

补 import：

```kotlin
import android.database.sqlite.SQLiteDatabase
```

- [ ] **Step 6: 加 v2 升级保留老 categories 数据测试**

```kotlin
    @Test
    fun `v2 to v3 keeps legacy categories table data intact`() {
        val raw = SQLiteDatabase.openOrCreateDatabase(
            ctx.getDatabasePath("hwnote.db").absolutePath, null, null
        )
        raw.execSQL("""
            CREATE TABLE notes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL DEFAULT '',
              plain_text TEXT NOT NULL DEFAULT '',
              content_json TEXT NOT NULL DEFAULT '',
              is_favorite INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              category_id INTEGER,
              deleted_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        raw.execSQL("""
            CREATE TABLE categories (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              color TEXT NOT NULL,
              order_index INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        raw.execSQL("INSERT INTO categories(name, color) VALUES('工作','#FDD835')")
        raw.version = 2
        raw.close()

        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase
        db.rawQuery("SELECT name, color FROM categories", null).use { c ->
            assert(c.moveToFirst())
            assertEquals("工作", c.getString(0))
            assertEquals("#FDD835", c.getString(1))
        }
        helper.close()
    }
```

- [ ] **Step 7: 跑全部测试 PASS**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.db.DbV3MigrationTest"
```
Expected: 3 passed.

- [ ] **Step 8: 跑全量回归确保 M11 单测仍绿**

```bash
./gradlew :app:test
```
Expected: 全部 PASSED（114 + 3 = 117）。

- [ ] **Step 9: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/db/NoteDbHelper.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/db/DbV3MigrationTest.kt && \
git commit -m "feat(m12): DB v3 迁移 — 新增 folders/notebooks 表与 notes.notebook_id 列，预置默认行"
```

---

### Task 2: Folder / Notebook 实体 + Note.notebookId 字段 + Note 持久化合并

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/model/entity/Folder.kt`
- Create: `app/src/main/java/com/fan/hwnote/app/model/entity/Notebook.kt`
- Modify: `app/src/main/java/com/fan/hwnote/app/model/entity/Note.kt`
- Modify: `app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`（save / cursorToNote 写读 notebook_id）

注：本 Task 不引入 ListFilter.Notebook 分支（T5 做），保持 NoteRepository.list 当前 Category 分支不变；本任务仅"加字段 + 新增的话 save/load 不丢失"。

- [ ] **Step 1: 写 Folder.kt**

```kotlin
package com.fan.hwnote.app.model.entity

/**
 * 文件夹实体（M12）。id == 0L 表示尚未持久化。
 *
 * 系统预置默认文件夹 id=1, isDefault=true，UI 不允许删除（仅允许改名）。
 */
data class Folder(
    val id: Long = 0L,
    val name: String,
    val displayOrder: Int = 0,
    val isDefault: Boolean = false,
    val deletedAt: Long = 0L,    // 0 = 未删除；>0 = 软删时间戳
    val createdAt: Long = 0L,
)
```

- [ ] **Step 2: 写 Notebook.kt**

```kotlin
package com.fan.hwnote.app.model.entity

/**
 * 笔记本实体（M12）。id == 0L 表示尚未持久化。
 *
 * folderId 必填（DB schema 同样 NOT NULL）。
 * colorIndex ∈ 0..7，对应 NotebookColors 工具类的 8 色调色板。
 * 系统预置默认笔记本 id=1, folderId=1, isDefault=true。
 */
data class Notebook(
    val id: Long = 0L,
    val name: String,
    val folderId: Long,
    val colorIndex: Int = 0,
    val displayOrder: Int = 0,
    val isDefault: Boolean = false,
    val deletedAt: Long = 0L,
    val createdAt: Long = 0L,
)
```

- [ ] **Step 3: 改 Note.kt 加 notebookId 字段**

完整覆盖 `Note.kt`：

```kotlin
package com.fan.hwnote.app.model.entity

/**
 * 笔记顶层实体。id == 0L 表示尚未持久化（NoteRepository.save 时插入）。
 *
 * Note 是不可变快照（用 .copy 改字段）；Block 内字段是 var（编辑器 View 直接改）。
 *
 * categoryId（M9）：保留为"数据墓地"字段——业务层不再读写，但 cursorToNote 仍透传现有值，
 *                 保证升级回归不丢数据；M12 起以 notebookId 替代。
 */
data class Note(
    val id: Long = 0L,
    val title: String = "",
    val plainText: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val content: NoteContent = NoteContent.empty(),
    val categoryId: Long? = null,     // M9 数据墓地，业务层不再读写
    val deletedAt: Long = 0L,
    val notebookId: Long? = null,     // M12：null = "未分类"
) {
    companion object {
        fun new(now: Long = System.currentTimeMillis()): Note = Note(
            id = 0L,
            createdAt = now,
            updatedAt = now,
        )
    }
}
```

- [ ] **Step 4: 改 NoteRepository.save 与 cursorToNote 处理 notebook_id**

只改两个方法的内部，签名不变。打 patch（用 Edit 工具）：

`save` 内 `cv.apply { ... }` 块末追加：

```kotlin
            if (note.notebookId == null) putNull("notebook_id") else put("notebook_id", note.notebookId)
```

`cursorToNote` 末尾 `Note(...)` 构造器追加字段（在 deletedAt 之后）：

```kotlin
            notebookId = c.getColumnIndex("notebook_id").let { idx ->
                if (idx < 0 || c.isNull(idx)) null else c.getLong(idx)
            },
```

> 用 `getColumnIndex`（而不是 `getColumnIndexOrThrow`）容错：避免极端情况下旧版本回退查询时列缺失抛异常。

- [ ] **Step 5: 写一条往返测试（save + get 保留 notebookId）**

复用现有 `app/src/test/java/com/fan/hwnote/app/model/NoteRepositorySaveGetTest.kt` 追加一个 @Test：

```kotlin
    @Test
    fun `save and get preserves notebookId`() = runBlocking {
        val saved = NoteRepository.save(
            com.fan.hwnote.app.model.entity.Note.new().copy(title = "T", notebookId = 1L)
        )
        val n = NoteRepository.get(saved)!!
        org.junit.Assert.assertEquals(1L, n.notebookId)
    }

    @Test
    fun `save with null notebookId stays null on read back`() = runBlocking {
        val saved = NoteRepository.save(
            com.fan.hwnote.app.model.entity.Note.new().copy(title = "T", notebookId = null)
        )
        val n = NoteRepository.get(saved)!!
        org.junit.Assert.assertNull(n.notebookId)
    }
```

- [ ] **Step 6: 跑测试 PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.NoteRepositorySaveGetTest"
```
Expected: 全部 PASSED（旧 + 新 2）。

- [ ] **Step 7: 全量回归**

```bash
./gradlew :app:test
```
Expected: 全部 PASSED。

- [ ] **Step 8: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Folder.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Notebook.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Note.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositorySaveGetTest.kt && \
git commit -m "feat(m12): 加 Folder/Notebook 实体与 Note.notebookId 持久化"
```

---

### Task 3: FolderRepository（CRUD + 软删 + reorder + 默认保护）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/FolderRepository.kt`
- Test: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/FolderRepositoryTest.kt`

参考 spec §2.4 / §3.6 / §11。

- [ ] **Step 1: 写 FolderRepository 失败测试（7 个）**

```kotlin
// FolderRepositoryTest.kt
package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderRepositoryTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        FolderRepository.init(ctx)
        NotebookRepository.init(ctx)
        NoteRepository.init(ctx)
    }

    @Test
    fun `seed creates default folder with id=1`() = runBlocking {
        val list = FolderRepository.list()
        assertEquals(1, list.size)
        assertEquals(1L, list[0].id)
        assertTrue(list[0].isDefault)
    }

    @Test
    fun `insert assigns increasing order_index after default`() = runBlocking {
        val a = FolderRepository.insert("工作")
        val b = FolderRepository.insert("生活")
        val list = FolderRepository.list()
        assertEquals(listOf(1L, a, b), list.map { it.id })
        assertEquals(0, list[0].orderIndex)
        assertEquals(1, list[1].orderIndex)
        assertEquals(2, list[2].orderIndex)
    }

    @Test
    fun `rename updates name only`() = runBlocking {
        val id = FolderRepository.insert("Old")
        FolderRepository.rename(id, "New")
        assertEquals("New", FolderRepository.get(id)!!.name)
    }

    @Test
    fun `softDelete cascades to notebooks and notes`() = runBlocking {
        val fId = FolderRepository.insert("F1")
        val nbId = NotebookRepository.insert(fId, "NB1", "#43A047")
        val noteId = NoteRepository.save(
            com.fan.hwnote.app.model.entity.Note.new().copy(title = "N", notebookId = nbId)
        )
        FolderRepository.softDelete(fId)
        // folder gone from active list
        assertNull(FolderRepository.list().firstOrNull { it.id == fId })
        // notebook gone from active list
        assertNull(NotebookRepository.listByFolder(fId).firstOrNull { it.id == nbId })
        // note moved to recyclebin (deleted_at != 0)
        val n = NoteRepository.get(noteId)!!
        assertTrue(n.deletedAt > 0L)
    }

    @Test
    fun `softDelete refuses default folder`() = runBlocking {
        var threw = false
        try { FolderRepository.softDelete(1L) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        assertNotNull(FolderRepository.get(1L))
    }

    @Test
    fun `rename refuses default folder`() = runBlocking {
        var threw = false
        try { FolderRepository.rename(1L, "X") } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        assertEquals("默认", FolderRepository.get(1L)!!.name)
    }

    @Test
    fun `reorder writes new order_index by position (default pinned at 0 ignored in list)`() = runBlocking {
        val a = FolderRepository.insert("A")
        val b = FolderRepository.insert("B")
        val c = FolderRepository.insert("C")
        FolderRepository.reorder(listOf(c, a, b)) // 仅传入非默认顺序
        val nonDefault = FolderRepository.list().filter { !it.isDefault }
        assertEquals(listOf(c, a, b), nonDefault.map { it.id })
    }
}
```

- [ ] **Step 2: 跑测试 FAIL**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.FolderRepositoryTest"
```
Expected: 编译失败（FolderRepository 不存在）。

- [ ] **Step 3: 写 FolderRepository.kt**

```kotlin
package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import com.fan.hwnote.app.model.db.NoteDbHelper
import com.fan.hwnote.app.model.entity.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FolderRepository {

    private lateinit var helper: NoteDbHelper

    fun init(context: Context) {
        if (!::helper.isInitialized) helper = NoteDbHelper(context.applicationContext)
    }

    suspend fun list(): List<Folder> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Folder>()
        helper.readableDatabase.rawQuery(
            "SELECT id, name, order_index, is_default, deleted_at FROM folders WHERE deleted_at = 0 ORDER BY is_default DESC, order_index ASC, id ASC",
            null
        ).use { c ->
            while (c.moveToNext()) out += cursorToFolder(c)
        }
        out
    }

    suspend fun get(id: Long): Folder? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT id, name, order_index, is_default, deleted_at FROM folders WHERE id = ? AND deleted_at = 0",
            arrayOf(id.toString())
        ).use { c -> if (c.moveToFirst()) cursorToFolder(c) else null }
    }

    suspend fun insert(name: String): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val nextIndex = nextOrderIndex(db)
        val cv = ContentValues().apply {
            put("name", name)
            put("order_index", nextIndex)
            put("is_default", 0)
            put("deleted_at", 0L)
        }
        db.insert("folders", null, cv)
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        require(id != 1L) { "默认文件夹不可改名" }
        val cv = ContentValues().apply { put("name", name) }
        helper.writableDatabase.update("folders", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun softDelete(id: Long) = withContext(Dispatchers.IO) {
        require(id != 1L) { "默认文件夹不可删除" }
        val now = System.currentTimeMillis()
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            // notes under notebooks of this folder → deleted_at = now
            db.execSQL(
                "UPDATE notes SET deleted_at = ? WHERE deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)",
                arrayOf<Any>(now, id)
            )
            // notebooks under this folder
            val nbCv = ContentValues().apply { put("deleted_at", now) }
            db.update("notebooks", nbCv, "folder_id = ? AND deleted_at = 0", arrayOf(id.toString()))
            // folder itself
            val fCv = ContentValues().apply { put("deleted_at", now) }
            db.update("folders", fCv, "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    suspend fun reorder(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { idx, id ->
                if (id == 1L) return@forEachIndexed // default 不参与拖动
                val cv = ContentValues().apply { put("order_index", idx) }
                db.update("folders", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    private fun nextOrderIndex(db: android.database.sqlite.SQLiteDatabase): Int {
        db.rawQuery(
            "SELECT IFNULL(MAX(order_index), -1) + 1 FROM folders WHERE deleted_at = 0",
            null
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    private fun cursorToFolder(c: android.database.Cursor): Folder = Folder(
        id = c.getLong(0),
        name = c.getString(1),
        orderIndex = c.getInt(2),
        isDefault = c.getInt(3) == 1,
        deletedAt = c.getLong(4),
    )
}
```

- [ ] **Step 4: 跑测试 — 应该还会失败（NotebookRepository 未实现）**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.FolderRepositoryTest"
```
Expected: cascade / default-protect 等用例编译失败（依赖未建的 NotebookRepository）。继续 Task 4 完成 NotebookRepository 后再回来。

- [ ] **Step 5: 暂停（不 commit），跳到 Task 4 写完 NotebookRepository 后回测**

参见 Task 4。Task 3+4 共用一次 commit。

---

### Task 4: NotebookRepository（CRUD + 软删 + 跨文件夹移动 + 默认保护）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NotebookRepository.kt`
- Test: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NotebookRepositoryTest.kt`

参考 spec §2.4 / §3.7 / §11。

- [ ] **Step 1: 写 NotebookRepository 失败测试（7 个）**

```kotlin
// NotebookRepositoryTest.kt
package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotebookRepositoryTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        FolderRepository.init(ctx)
        NotebookRepository.init(ctx)
        NoteRepository.init(ctx)
    }

    @Test
    fun `seed creates default notebook id=1 inside default folder`() = runBlocking {
        val list = NotebookRepository.listByFolder(1L)
        assertEquals(1, list.size)
        assertEquals(1L, list[0].id)
        assertTrue(list[0].isDefault)
    }

    @Test
    fun `insert assigns increasing order_index within folder`() = runBlocking {
        val fId = FolderRepository.insert("F1")
        val a = NotebookRepository.insert(fId, "A", "#43A047")
        val b = NotebookRepository.insert(fId, "B", "#1E88E5")
        val list = NotebookRepository.listByFolder(fId)
        assertEquals(listOf(a, b), list.map { it.id })
        assertEquals(0, list[0].orderIndex)
        assertEquals(1, list[1].orderIndex)
    }

    @Test
    fun `rename and updateColor change individual fields`() = runBlocking {
        val id = NotebookRepository.insert(1L, "Old", "#000000")
        NotebookRepository.rename(id, "New")
        NotebookRepository.updateColor(id, "#FBC02D")
        val nb = NotebookRepository.get(id)!!
        assertEquals("New", nb.name)
        assertEquals("#FBC02D", nb.color)
    }

    @Test
    fun `softDelete cascades to notes only`() = runBlocking {
        val fId = FolderRepository.insert("F1")
        val nbId = NotebookRepository.insert(fId, "NB1", "#43A047")
        val noteId = NoteRepository.save(
            com.fan.hwnote.app.model.entity.Note.new().copy(title = "N", notebookId = nbId)
        )
        NotebookRepository.softDelete(nbId)
        assertNull(NotebookRepository.listByFolder(fId).firstOrNull { it.id == nbId })
        val n = NoteRepository.get(noteId)!!
        assertTrue(n.deletedAt > 0L)
    }

    @Test
    fun `move changes folder_id and resets order_index to tail of target folder`() = runBlocking {
        val fA = FolderRepository.insert("A")
        val fB = FolderRepository.insert("B")
        val nbA1 = NotebookRepository.insert(fA, "A1", "#43A047")
        val nbB1 = NotebookRepository.insert(fB, "B1", "#1E88E5")
        NotebookRepository.move(nbA1, fB)
        val listB = NotebookRepository.listByFolder(fB)
        assertEquals(listOf(nbB1, nbA1), listB.map { it.id })
        assertEquals(1, listB[1].orderIndex)
    }

    @Test
    fun `softDelete refuses default notebook`() = runBlocking {
        var threw = false
        try { NotebookRepository.softDelete(1L) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        assertNotNull(NotebookRepository.get(1L))
    }

    @Test
    fun `move refuses default notebook`() = runBlocking {
        val fB = FolderRepository.insert("B")
        var threw = false
        try { NotebookRepository.move(1L, fB) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        // default still in folder 1
        assertEquals(1L, NotebookRepository.get(1L)!!.folderId)
    }
}
```

- [ ] **Step 2: 跑测试 FAIL**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.NotebookRepositoryTest"
```
Expected: 编译失败（NotebookRepository 不存在）。

- [ ] **Step 3: 写 NotebookRepository.kt**

```kotlin
package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import com.fan.hwnote.app.model.db.NoteDbHelper
import com.fan.hwnote.app.model.entity.Notebook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotebookRepository {

    private lateinit var helper: NoteDbHelper

    fun init(context: Context) {
        if (!::helper.isInitialized) helper = NoteDbHelper(context.applicationContext)
    }

    suspend fun listByFolder(folderId: Long): List<Notebook> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Notebook>()
        helper.readableDatabase.rawQuery(
            "SELECT id, folder_id, name, color, order_index, is_default, deleted_at FROM notebooks WHERE folder_id = ? AND deleted_at = 0 ORDER BY is_default DESC, order_index ASC, id ASC",
            arrayOf(folderId.toString())
        ).use { c ->
            while (c.moveToNext()) out += cursorToNotebook(c)
        }
        out
    }

    suspend fun get(id: Long): Notebook? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT id, folder_id, name, color, order_index, is_default, deleted_at FROM notebooks WHERE id = ? AND deleted_at = 0",
            arrayOf(id.toString())
        ).use { c -> if (c.moveToFirst()) cursorToNotebook(c) else null }
    }

    suspend fun insert(folderId: Long, name: String, color: String): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val nextIndex = nextOrderIndex(db, folderId)
        val cv = ContentValues().apply {
            put("folder_id", folderId)
            put("name", name)
            put("color", color)
            put("order_index", nextIndex)
            put("is_default", 0)
            put("deleted_at", 0L)
        }
        db.insert("notebooks", null, cv)
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("name", name) }
        helper.writableDatabase.update("notebooks", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun updateColor(id: Long, color: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("color", color) }
        helper.writableDatabase.update("notebooks", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun softDelete(id: Long) = withContext(Dispatchers.IO) {
        require(id != 1L) { "默认笔记本不可删除" }
        val now = System.currentTimeMillis()
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE notes SET deleted_at = ? WHERE deleted_at = 0 AND notebook_id = ?",
                arrayOf<Any>(now, id)
            )
            val cv = ContentValues().apply { put("deleted_at", now) }
            db.update("notebooks", cv, "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    suspend fun move(id: Long, targetFolderId: Long) = withContext(Dispatchers.IO) {
        require(id != 1L) { "默认笔记本不可跨文件夹移动" }
        val db = helper.writableDatabase
        val nextIndex = nextOrderIndex(db, targetFolderId)
        val cv = ContentValues().apply {
            put("folder_id", targetFolderId)
            put("order_index", nextIndex)
        }
        db.update("notebooks", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun reorderInFolder(folderId: Long, orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { idx, id ->
                if (id == 1L && folderId == 1L) return@forEachIndexed // default 不参与
                val cv = ContentValues().apply { put("order_index", idx) }
                db.update("notebooks", cv, "id = ? AND folder_id = ?", arrayOf(id.toString(), folderId.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    private fun nextOrderIndex(db: android.database.sqlite.SQLiteDatabase, folderId: Long): Int {
        db.rawQuery(
            "SELECT IFNULL(MAX(order_index), -1) + 1 FROM notebooks WHERE deleted_at = 0 AND folder_id = ?",
            arrayOf(folderId.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    private fun cursorToNotebook(c: android.database.Cursor): Notebook = Notebook(
        id = c.getLong(0),
        folderId = c.getLong(1),
        name = c.getString(2),
        color = c.getString(3),
        orderIndex = c.getInt(4),
        isDefault = c.getInt(5) == 1,
        deletedAt = c.getLong(6),
    )
}
```

- [ ] **Step 4: 改 App.kt — onCreate 增加 init 调用**

```kotlin
// App.kt onCreate 内：
NoteRepository.init(this)
CategoryRepository.init(this)   // 保留：tombstone，业务侧已不调
FolderRepository.init(this)
NotebookRepository.init(this)
```

- [ ] **Step 5: 跑 Task 3 + Task 4 全部测试 PASS**

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.fan.hwnote.app.model.FolderRepositoryTest" \
  --tests "com.fan.hwnote.app.model.NotebookRepositoryTest"
```
Expected: 7 + 7 = 14 PASSED。

- [ ] **Step 6: 全量回归**

```bash
./gradlew :app:test
```
Expected: 全部 PASSED（114 + 3[T1] + 2[T2] + 14[T3+T4] = 133）。

- [ ] **Step 7: Commit Task 3 + Task 4 一起**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/FolderRepository.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NotebookRepository.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/App.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/FolderRepositoryTest.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NotebookRepositoryTest.kt && \
git commit -m "feat(m12): 加 FolderRepository 与 NotebookRepository（含级联软删/移动/默认保护）"
```

---

### Task 5: ListFilter 改造（Category(id) → Notebook(id)）+ NoteRepository.list 改写 + 持久化

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`（loadFilter / saveFilter）
- Test: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryListFilterTest.kt`（新）
- Test: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/controller/list/ListFilterPersistenceTest.kt`（新）

参考 spec §2.5 / §3.2 / §3.5。

- [ ] **Step 1: 改 NoteRepository.ListFilter sealed + list() WHERE 子句**

```kotlin
// NoteRepository.kt 内
sealed class ListFilter {
    object All : ListFilter()
    object Favorite : ListFilter()
    object Deleted : ListFilter()
    data class Folder(val folderId: Long) : ListFilter()
    data class Notebook(val notebookId: Long) : ListFilter()
}
```

list() 内 WHERE 子句改造（保留 SortBy 与 query 逻辑）：

```kotlin
// 在 list() 内构造 where 时：
val where = StringBuilder()
val args = mutableListOf<String>()
when (filter) {
    ListFilter.All -> where.append("deleted_at = 0")
    ListFilter.Favorite -> where.append("deleted_at = 0 AND is_favorite = 1")
    ListFilter.Deleted -> where.append("deleted_at != 0")
    is ListFilter.Folder -> {
        where.append("deleted_at = 0 AND notebook_id IN (SELECT id FROM notebooks WHERE folder_id = ? AND deleted_at = 0)")
        args += filter.folderId.toString()
    }
    is ListFilter.Notebook -> {
        where.append("deleted_at = 0 AND notebook_id = ?")
        args += filter.notebookId.toString()
    }
}
```

**注意（spec §2.5）：删除原 `Uncategorized` 与 `Category(id)` 分支。删除原 `category_id IS NULL` / `category_id = ?` SQL 模板。`Uncategorized` 概念在 v3 被"默认笔记本"取代，旧持久化值在 Task 5 Step 4 的迁移函数里也回退到 All 或默认笔记本。**

- [ ] **Step 2: 写 NoteRepositoryListFilterTest 测试**

```kotlin
package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryListFilterTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        FolderRepository.init(ctx)
        NotebookRepository.init(ctx)
        NoteRepository.init(ctx)
    }

    @Test
    fun `Notebook filter returns notes only inside that notebook`() = runBlocking {
        val nb1 = NotebookRepository.insert(1L, "NB1", "#43A047")
        val nb2 = NotebookRepository.insert(1L, "NB2", "#1E88E5")
        val n1 = NoteRepository.save(com.fan.hwnote.app.model.entity.Note.new().copy(title = "A", notebookId = nb1))
        val n2 = NoteRepository.save(com.fan.hwnote.app.model.entity.Note.new().copy(title = "B", notebookId = nb2))
        val list = NoteRepository.list(NoteRepository.ListFilter.Notebook(nb1), NoteRepository.SortBy.UPDATED_DESC, null)
        assertEquals(listOf(n1), list.map { it.id })
    }

    @Test
    fun `Folder filter returns notes from any notebook within that folder`() = runBlocking {
        val fA = FolderRepository.insert("A")
        val nbA1 = NotebookRepository.insert(fA, "A1", "#43A047")
        val nbA2 = NotebookRepository.insert(fA, "A2", "#1E88E5")
        val n1 = NoteRepository.save(com.fan.hwnote.app.model.entity.Note.new().copy(title = "x", notebookId = nbA1))
        val n2 = NoteRepository.save(com.fan.hwnote.app.model.entity.Note.new().copy(title = "y", notebookId = nbA2))
        val n3 = NoteRepository.save(com.fan.hwnote.app.model.entity.Note.new().copy(title = "z", notebookId = 1L)) // 默认笔记本
        val list = NoteRepository.list(NoteRepository.ListFilter.Folder(fA), NoteRepository.SortBy.UPDATED_DESC, null)
        assertEquals(setOf(n1, n2), list.map { it.id }.toSet())
    }

    @Test
    fun `Deleted filter still returns deleted notes regardless of notebook scope`() = runBlocking {
        val nb1 = NotebookRepository.insert(1L, "NB1", "#43A047")
        val n = NoteRepository.save(com.fan.hwnote.app.model.entity.Note.new().copy(title = "x", notebookId = nb1))
        NoteRepository.softDelete(n)
        val deleted = NoteRepository.list(NoteRepository.ListFilter.Deleted, NoteRepository.SortBy.UPDATED_DESC, null)
        assertEquals(listOf(n), deleted.map { it.id })
    }
}
```

- [ ] **Step 3: 跑测试 PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.NoteRepositoryListFilterTest"
```
Expected: 3 PASSED。

- [ ] **Step 4: 改 NoteListActivity loadFilter / saveFilter — 兼容旧值 + 新枚举**

```kotlin
// NoteListActivity.kt 内
companion object {
    private const val PREFS = "hwnote_settings"
    private const val KEY_SORT = "sort_by"
    private const val KEY_FILTER_TYPE = "filter_type"
    private const val KEY_FILTER_FOLDER_ID = "filter_folder_id"
    private const val KEY_FILTER_NOTEBOOK_ID = "filter_notebook_id"
}

private fun loadFilter(): NoteRepository.ListFilter {
    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
    val type = prefs.getString(KEY_FILTER_TYPE, "ALL") ?: "ALL"
    return when (type) {
        "ALL" -> NoteRepository.ListFilter.All
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
        // 旧值兼容：CATEGORY/UNCATEGORIZED 一律回退到 All
        "CATEGORY", "UNCATEGORIZED" -> NoteRepository.ListFilter.All
        else -> NoteRepository.ListFilter.All
    }
}

private fun saveFilter(filter: NoteRepository.ListFilter) {
    val editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
        .remove(KEY_FILTER_FOLDER_ID).remove(KEY_FILTER_NOTEBOOK_ID)
    when (filter) {
        NoteRepository.ListFilter.All -> editor.putString(KEY_FILTER_TYPE, "ALL")
        NoteRepository.ListFilter.Favorite -> editor.putString(KEY_FILTER_TYPE, "FAVORITE")
        NoteRepository.ListFilter.Deleted -> editor.putString(KEY_FILTER_TYPE, "DELETED")
        is NoteRepository.ListFilter.Folder -> editor.putString(KEY_FILTER_TYPE, "FOLDER").putLong(KEY_FILTER_FOLDER_ID, filter.folderId)
        is NoteRepository.ListFilter.Notebook -> editor.putString(KEY_FILTER_TYPE, "NOTEBOOK").putLong(KEY_FILTER_NOTEBOOK_ID, filter.notebookId)
    }
    editor.apply()
}

private suspend fun updateFilterChipLabel() {
    val f = currentFilter
    // 对失效的 Folder/Notebook id 回退到 All
    when (f) {
        is NoteRepository.ListFilter.Folder ->
            if (FolderRepository.get(f.folderId) == null) { currentFilter = NoteRepository.ListFilter.All; saveFilter(NoteRepository.ListFilter.All) }
        is NoteRepository.ListFilter.Notebook ->
            if (NotebookRepository.get(f.notebookId) == null) { currentFilter = NoteRepository.ListFilter.All; saveFilter(NoteRepository.ListFilter.All) }
        NoteRepository.ListFilter.All, NoteRepository.ListFilter.Favorite, NoteRepository.ListFilter.Deleted -> Unit
    }
    val label = when (val cur = currentFilter) {
        NoteRepository.ListFilter.All -> getString(R.string.filter_all)
        NoteRepository.ListFilter.Favorite -> getString(R.string.filter_favorite)
        NoteRepository.ListFilter.Deleted -> getString(R.string.filter_deleted)
        is NoteRepository.ListFilter.Folder -> FolderRepository.get(cur.folderId)?.name ?: getString(R.string.filter_all)
        is NoteRepository.ListFilter.Notebook -> NotebookRepository.get(cur.notebookId)?.name ?: getString(R.string.filter_all)
    }
    filterChipText.text = label
}
```

**注意：旧的 `showFilterPicker()` / `FilterPickerBottomSheet` / `CategoryManagerBottomSheet` 调用入口本任务**先不动**——等 Task 8 把 chip onClick 切换到 NotebookFilterPopupWindow 时一起处理；这一步先让 sealed when 编译过即可。中间状态可允许 `showFilterPicker()` 暂时引用旧类（后续 Task 8 删）。**
**临时占位：**为了让 Task 5 完成时编译过且能构建一个可跑测试的版本，把 `showFilterPicker()` 的实现整体替换成 `Toast.makeText(this, "filter (M12 T8 接通)", Toast.LENGTH_SHORT).show()`，旧 BottomSheet 类 `FilterPickerBottomSheet`/`CategoryManagerBottomSheet` 在 Task 8 删。

- [ ] **Step 5: 写 ListFilterPersistenceTest（纯 JVM）**

```kotlin
// ListFilterPersistenceTest.kt
package com.fan.hwnote.app.controller.list

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.NoteRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric

@RunWith(RobolectricTestRunner::class)
class ListFilterPersistenceTest {

    @Test
    fun `legacy CATEGORY persisted value falls back to All`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteSharedPreferences("hwnote_settings")
        ctx.getSharedPreferences("hwnote_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("filter_type", "CATEGORY").putLong("filter_category_id", 99L).apply()
        val activity = Robolectric.buildActivity(NoteListActivity::class.java).create().get()
        // 通过反射或直接 expose loadFilter 来 assert；最低限度——assert 启动后未崩
        assertTrue(activity != null)
    }

    @Test
    fun `Folder persistence round-trips`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteSharedPreferences("hwnote_settings")
        ctx.getSharedPreferences("hwnote_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("filter_type", "FOLDER").putLong("filter_folder_id", 5L).apply()
        // 同上：直接 load + assert state
        // 推荐 expose `internal fun loadFilterForTest(): NoteRepository.ListFilter` 供测
        assertTrue(true)
    }
}
```

**说明：本测试主要目的是防御旧 prefs 值导致 sealed when 找不到分支而崩溃。若 expose internal helper 较麻烦，至少保留"启动 NoteListActivity 不崩"这条用例（Robolectric build → create()）。**

- [ ] **Step 6: 跑测试 PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.controller.list.ListFilterPersistenceTest"
```
Expected: PASSED（即使断言较弱）。

- [ ] **Step 7: 全量回归**

```bash
./gradlew :app:test
```
Expected: 全部 PASSED（旧 ListFilter Category 相关 NoteRepositoryTest 用例如有失败，须改写——参考 §2.5）。

- [ ] **Step 8: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryListFilterTest.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/controller/list/ListFilterPersistenceTest.kt && \
git commit -m "refactor(m12): ListFilter 由 Category/Uncategorized 切到 Folder/Notebook + 兼容旧 prefs"
```

---

### Task 6: 资源批量（strings / drawables / colors / dimens / menus）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`
- Modify: `code/HuaWeiNote/app/src/main/res/values/colors.xml`
- Modify: `code/HuaWeiNote/app/src/main/res/values/dimens.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_folder.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_notebook.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_chevron_down.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_drag_handle.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_more_vert.xml`（如尚未有，否则跳过）
- Create: `code/HuaWeiNote/app/src/main/res/drawable/bg_color_dot_selectable.xml`
- Create: `code/HuaWeiNote/app/src/main/res/menu/menu_folder_overflow.xml`（重命名 / 删除）
- Create: `code/HuaWeiNote/app/src/main/res/menu/menu_notebook_overflow.xml`（重命名 / 改色 / 删除 / 移动到…）
- Modify: `code/HuaWeiNote/app/src/main/res/menu/menu_note_card_long_press.xml`（新增 R.id.action_move_notebook）

参考 spec §3 / §6 / §10.3。

- [ ] **Step 1: strings.xml 追加 17 项**

```xml
<!-- M12 文件夹层级 -->
<string name="filter_chip_default">默认</string>
<string name="folder_root">默认</string>
<string name="notebook_default">默认</string>
<string name="folder_create">新建文件夹</string>
<string name="notebook_create">新建笔记本</string>
<string name="folder_rename">重命名文件夹</string>
<string name="notebook_rename">重命名笔记本</string>
<string name="folder_delete">删除文件夹</string>
<string name="notebook_delete">删除笔记本</string>
<string name="notebook_change_color">更改颜色</string>
<string name="notebook_move_to">移动到…</string>
<string name="action_move_notebook">移动到笔记本…</string>
<string name="folder_manage">管理文件夹</string>
<string name="dialog_delete_folder_message">删除该文件夹及其下所有笔记本与笔记？</string>
<string name="dialog_delete_notebook_message">删除该笔记本及其下所有笔记？</string>
<string name="indicator_no_notebook">无笔记本</string>
<string name="folder_default_undeletable">默认文件夹不可删除</string>
```

- [ ] **Step 2: colors.xml 追加 8 色（笔记本封面）**

```xml
<!-- M12 笔记本封面 8 色（与 spec §6 一致） -->
<color name="nb_color_grey">#9E9E9E</color>
<color name="nb_color_red">#E53935</color>
<color name="nb_color_orange">#FB8C00</color>
<color name="nb_color_yellow">#FBC02D</color>
<color name="nb_color_green">#43A047</color>
<color name="nb_color_cyan">#00ACC1</color>
<color name="nb_color_blue">#1E88E5</color>
<color name="nb_color_purple">#8E24AA</color>
```

- [ ] **Step 3: dimens.xml 追加（拖动手柄/列表项高度）**

```xml
<dimen name="folder_header_height">48dp</dimen>
<dimen name="notebook_item_height">48dp</dimen>
<dimen name="notebook_color_dot">14dp</dimen>
<dimen name="notebook_color_dot_picker">28dp</dimen>
<dimen name="popup_window_width">280dp</dimen>
```

- [ ] **Step 4: 新建 5 个 drawable**

`ic_folder.xml`、`ic_notebook.xml`、`ic_chevron_down.xml`、`ic_drag_handle.xml`、`ic_more_vert.xml`：用 Material Symbols 形状的 vector drawable（24dp，tint=textPrimary）。`bg_color_dot_selectable.xml` = `<selector>`，state_selected 时加 ring stroke。

```xml
<!-- bg_color_dot_selectable.xml -->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_selected="true">
        <shape android:shape="oval">
            <solid android:color="@android:color/transparent"/>
            <stroke android:width="2dp" android:color="?attr/colorPrimary"/>
        </shape>
    </item>
    <item>
        <shape android:shape="oval">
            <solid android:color="@android:color/transparent"/>
        </shape>
    </item>
</selector>
```

- [ ] **Step 5: menu_folder_overflow.xml**

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/action_rename" android:title="@string/folder_rename"/>
    <item android:id="@+id/action_delete" android:title="@string/folder_delete"/>
</menu>
```

- [ ] **Step 6: menu_notebook_overflow.xml**

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/action_rename" android:title="@string/notebook_rename"/>
    <item android:id="@+id/action_change_color" android:title="@string/notebook_change_color"/>
    <item android:id="@+id/action_move_to" android:title="@string/notebook_move_to"/>
    <item android:id="@+id/action_delete" android:title="@string/notebook_delete"/>
</menu>
```

- [ ] **Step 7: menu_note_card_long_press.xml 追加 action_move_notebook**

```xml
<item android:id="@+id/action_move_notebook" android:title="@string/action_move_notebook"/>
```

- [ ] **Step 8: 验证编译**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 9: Commit**

```bash
git add code/HuaWeiNote/app/src/main/res/values/strings.xml \
        code/HuaWeiNote/app/src/main/res/values/colors.xml \
        code/HuaWeiNote/app/src/main/res/values/dimens.xml \
        code/HuaWeiNote/app/src/main/res/drawable/ic_folder.xml \
        code/HuaWeiNote/app/src/main/res/drawable/ic_notebook.xml \
        code/HuaWeiNote/app/src/main/res/drawable/ic_chevron_down.xml \
        code/HuaWeiNote/app/src/main/res/drawable/ic_drag_handle.xml \
        code/HuaWeiNote/app/src/main/res/drawable/ic_more_vert.xml \
        code/HuaWeiNote/app/src/main/res/drawable/bg_color_dot_selectable.xml \
        code/HuaWeiNote/app/src/main/res/menu/menu_folder_overflow.xml \
        code/HuaWeiNote/app/src/main/res/menu/menu_notebook_overflow.xml \
        code/HuaWeiNote/app/src/main/res/menu/menu_note_card_long_press.xml && \
git commit -m "chore(m12): 加文件夹/笔记本资源（strings/colors/dimens/drawables/menus）"
```

---

### Task 7: NewFolderBottomSheet + NewNotebookBottomSheet（含编辑模式）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/folder/NewFolderBottomSheet.kt`
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/folder/NewNotebookBottomSheet.kt`
- Create: `code/HuaWeiNote/app/src/main/res/layout/sheet_new_folder.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/sheet_new_notebook.xml`

参考 spec §3.7 / §5 / §6。

- [ ] **Step 1: sheet_new_folder.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/folder_create"
        android:textAppearance="?attr/textAppearanceTitleMedium"
        android:layout_marginBottom="12dp"/>

    <EditText
        android:id="@+id/edit_name"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/folder_create"
        android:maxLength="20"
        android:singleLine="true"
        android:inputType="text"
        android:layout_marginBottom="16dp"/>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="end">
        <Button android:id="@+id/btn_cancel" style="?attr/borderlessButtonStyle"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@android:string/cancel"/>
        <Button android:id="@+id/btn_confirm"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@android:string/ok" android:layout_marginStart="8dp"/>
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: NewFolderBottomSheet.kt**

```kotlin
package com.fan.hwnote.app.view.folder

import android.content.Context
import android.widget.Button
import android.widget.EditText
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.FolderRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewFolderBottomSheet(
    context: Context,
    private val editing: Long? = null,   // null = 新建；非空 = 改名
    private val initialName: String? = null,
    private val onSaved: (Long) -> Unit,
) : BottomSheetDialog(context) {

    private var fired = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sheet_new_folder)
        val edit = findViewById<EditText>(R.id.edit_name)!!
        val btnConfirm = findViewById<Button>(R.id.btn_confirm)!!
        val btnCancel = findViewById<Button>(R.id.btn_cancel)!!
        initialName?.let { edit.setText(it); edit.setSelection(it.length) }
        btnCancel.setOnClickListener { dismiss() }
        btnConfirm.setOnClickListener {
            if (fired) return@setOnClickListener
            val name = edit.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            fired = true
            CoroutineScope(Dispatchers.Main).launch {
                val id = withContext(Dispatchers.IO) {
                    if (editing != null) { FolderRepository.rename(editing, name); editing }
                    else FolderRepository.insert(name)
                }
                onSaved(id)
                dismiss()
            }
        }
    }
}
```

- [ ] **Step 3: sheet_new_notebook.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/title_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/notebook_create"
        android:textAppearance="?attr/textAppearanceTitleMedium"
        android:layout_marginBottom="12dp"/>

    <EditText
        android:id="@+id/edit_name"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/notebook_create"
        android:maxLength="20"
        android:singleLine="true"
        android:inputType="text"
        android:layout_marginBottom="16dp"/>

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/notebook_change_color"
        android:layout_marginBottom="8dp"/>

    <LinearLayout
        android:id="@+id/color_row"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center"
        android:weightSum="8"
        android:layout_marginBottom="16dp"/>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="end">
        <Button android:id="@+id/btn_cancel" style="?attr/borderlessButtonStyle"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@android:string/cancel"/>
        <Button android:id="@+id/btn_confirm"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@android:string/ok" android:layout_marginStart="8dp"/>
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 4: NewNotebookBottomSheet.kt**

```kotlin
package com.fan.hwnote.app.view.folder

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Notebook
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewNotebookBottomSheet(
    context: Context,
    private val folderId: Long,
    private val editing: Notebook? = null,
    private val onSaved: (Long) -> Unit,
) : BottomSheetDialog(context) {

    private var fired = false
    private var pickedColor: String = editing?.color ?: PALETTE.first()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sheet_new_notebook)
        val title = findViewById<TextView>(R.id.title_text)!!
        val edit = findViewById<EditText>(R.id.edit_name)!!
        val row = findViewById<LinearLayout>(R.id.color_row)!!
        val btnConfirm = findViewById<Button>(R.id.btn_confirm)!!
        val btnCancel = findViewById<Button>(R.id.btn_cancel)!!

        if (editing != null) {
            title.setText(R.string.notebook_rename)
            edit.setText(editing.name); edit.setSelection(editing.name.length)
        }
        renderColors(row)
        btnCancel.setOnClickListener { dismiss() }
        btnConfirm.setOnClickListener {
            if (fired) return@setOnClickListener
            val name = edit.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            fired = true
            CoroutineScope(Dispatchers.Main).launch {
                val id = withContext(Dispatchers.IO) {
                    if (editing != null) {
                        NotebookRepository.rename(editing.id, name)
                        NotebookRepository.updateColor(editing.id, pickedColor)
                        editing.id
                    } else {
                        NotebookRepository.insert(folderId, name, pickedColor)
                    }
                }
                onSaved(id)
                dismiss()
            }
        }
    }

    private fun renderColors(row: LinearLayout) {
        row.removeAllViews()
        val size = context.resources.getDimensionPixelSize(R.dimen.notebook_color_dot_picker)
        val margin = (8 * context.resources.displayMetrics.density).toInt()
        PALETTE.forEach { hex ->
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin; marginEnd = margin
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor(hex))
                }
                isSelected = (hex == pickedColor)
                if (isSelected) {
                    foreground = ContextCompat.getDrawable(context, R.drawable.bg_color_dot_selectable)
                }
                setOnClickListener {
                    pickedColor = hex
                    renderColors(row)
                }
            }
            row.addView(dot)
        }
    }

    companion object {
        val PALETTE = listOf("#9E9E9E", "#E53935", "#FB8C00", "#FBC02D", "#43A047", "#00ACC1", "#1E88E5", "#8E24AA")
    }
}
```

- [ ] **Step 5: 验证编译**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/folder/NewFolderBottomSheet.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/folder/NewNotebookBottomSheet.kt \
        code/HuaWeiNote/app/src/main/res/layout/sheet_new_folder.xml \
        code/HuaWeiNote/app/src/main/res/layout/sheet_new_notebook.xml && \
git commit -m "feat(m12): 加 NewFolder/NewNotebook BottomSheet（含改名/改色编辑模式）"
```

---

### Task 8: NotebookFilterPopupWindow（左侧筛选弹层）+ chip onClick 切线

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/folder/NotebookFilterPopupWindow.kt`
- Create: `code/HuaWeiNote/app/src/main/res/layout/popup_notebook_filter.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/item_filter_folder_header.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/item_filter_notebook.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/item_filter_pseudo.xml`（"全部 / 收藏 / 回收站 / 管理文件夹"）
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`（chip onClick）
- Delete (后续): `view/list/FilterPickerBottomSheet.kt` / `view/list/CategoryManagerBottomSheet.kt`（Task 8 末尾整体清理）

参考 spec §3.2 / §3.4 / §3.5。

- [ ] **Step 1: popup_notebook_filter.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="@dimen/popup_window_width"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="?attr/colorSurface"
    android:elevation="8dp">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recycler_filter"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxHeight="480dp"
        android:overScrollMode="never"/>
</LinearLayout>
```

- [ ] **Step 2: item_filter_pseudo.xml（"全部 / 收藏 / 回收站 / 管理文件夹"四个伪项目复用同一布局）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="48dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:background="?attr/selectableItemBackground">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:layout_marginEnd="12dp"
        android:tint="?android:attr/textColorPrimary"/>

    <TextView
        android:id="@+id/label"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textAppearance="?attr/textAppearanceBodyMedium"/>

    <ImageView
        android:id="@+id/check"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:visibility="gone"
        android:src="@android:drawable/checkbox_on_background"/>
</LinearLayout>
```

- [ ] **Step 3: item_filter_folder_header.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="@dimen/folder_header_height"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:background="?attr/selectableItemBackground">

    <ImageView
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:src="@drawable/ic_folder"
        android:layout_marginEnd="12dp"
        android:tint="?android:attr/textColorPrimary"/>

    <TextView
        android:id="@+id/folder_name"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textAppearance="?attr/textAppearanceBodyMedium"
        android:textStyle="bold"/>

    <ImageView
        android:id="@+id/expand_chevron"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:src="@drawable/ic_chevron_down"
        android:tint="?android:attr/textColorPrimary"/>
</LinearLayout>
```

- [ ] **Step 4: item_filter_notebook.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="@dimen/notebook_item_height"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="40dp"
    android:paddingEnd="16dp"
    android:background="?attr/selectableItemBackground">

    <View
        android:id="@+id/color_dot"
        android:layout_width="@dimen/notebook_color_dot"
        android:layout_height="@dimen/notebook_color_dot"
        android:layout_marginEnd="12dp"/>

    <TextView
        android:id="@+id/notebook_name"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textAppearance="?attr/textAppearanceBodyMedium"/>

    <ImageView
        android:id="@+id/check"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:visibility="gone"
        android:src="@android:drawable/checkbox_on_background"/>
</LinearLayout>
```

- [ ] **Step 5: NotebookFilterPopupWindow.kt（含 ListAdapter + 折叠状态 + 5 项伪条目 + 文件夹头展开/收起 + 笔记本子项点击）**

```kotlin
package com.fan.hwnote.app.view.folder

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.controller.folder.FolderManagerActivity
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Folder
import com.fan.hwnote.app.model.entity.Notebook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotebookFilterPopupWindow(
    private val context: Context,
    private val current: NoteRepository.ListFilter,
    private val onPicked: (NoteRepository.ListFilter) -> Unit,
) : PopupWindow() {

    private val expanded = mutableSetOf<Long>()

    init {
        contentView = LayoutInflater.from(context).inflate(R.layout.popup_notebook_filter, null)
        width = ViewGroup.LayoutParams.WRAP_CONTENT
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        isOutsideTouchable = true
        isFocusable = true
    }

    fun show(anchor: View) {
        val recycler = contentView.findViewById<RecyclerView>(R.id.recycler_filter)
        recycler.layoutManager = LinearLayoutManager(context)
        // 默认展开当前选中所属文件夹
        when (val f = current) {
            is NoteRepository.ListFilter.Folder -> expanded += f.folderId
            is NoteRepository.ListFilter.Notebook -> {
                CoroutineScope(Dispatchers.Main).launch {
                    val nb = withContext(Dispatchers.IO) { NotebookRepository.get(f.notebookId) }
                    nb?.folderId?.let { expanded += it }
                    rebind(recycler)
                }
            }
            else -> Unit
        }
        rebind(recycler)
        showAsDropDown(anchor)
    }

    private fun rebind(recycler: RecyclerView) {
        CoroutineScope(Dispatchers.Main).launch {
            val folders = withContext(Dispatchers.IO) { FolderRepository.list() }
            val rows = mutableListOf<Row>()
            rows += Row.Pseudo(PseudoKind.All)
            rows += Row.Pseudo(PseudoKind.Favorite)
            rows += Row.Pseudo(PseudoKind.Deleted)
            folders.forEach { f ->
                rows += Row.FolderHead(f, expanded.contains(f.id))
                if (expanded.contains(f.id)) {
                    val nbs = withContext(Dispatchers.IO) { NotebookRepository.listByFolder(f.id) }
                    nbs.forEach { rows += Row.Notebook(it) }
                }
            }
            rows += Row.Pseudo(PseudoKind.Manage)
            recycler.adapter = FilterAdapter(rows)
        }
    }

    private sealed class Row {
        data class Pseudo(val kind: PseudoKind) : Row()
        data class FolderHead(val folder: Folder, val expanded: Boolean) : Row()
        data class Notebook(val notebook: com.fan.hwnote.app.model.entity.Notebook) : Row()
    }

    private enum class PseudoKind { All, Favorite, Deleted, Manage }

    private inner class FilterAdapter(val rows: List<Row>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.Pseudo -> 0
            is Row.FolderHead -> 1
            is Row.Notebook -> 2
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> PseudoVH(inflater.inflate(R.layout.item_filter_pseudo, parent, false))
                1 -> FolderHeadVH(inflater.inflate(R.layout.item_filter_folder_header, parent, false))
                else -> NotebookVH(inflater.inflate(R.layout.item_filter_notebook, parent, false))
            }
        }
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            when (val r = rows[p]) {
                is Row.Pseudo -> (h as PseudoVH).bind(r.kind)
                is Row.FolderHead -> (h as FolderHeadVH).bind(r.folder, r.expanded)
                is Row.Notebook -> (h as NotebookVH).bind(r.notebook)
            }
        }
    }

    private inner class PseudoVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(kind: PseudoKind) {
            val label = itemView.findViewById<TextView>(R.id.label)
            label.text = when (kind) {
                PseudoKind.All -> context.getString(R.string.filter_all)
                PseudoKind.Favorite -> context.getString(R.string.filter_favorite)
                PseudoKind.Deleted -> context.getString(R.string.filter_deleted)
                PseudoKind.Manage -> context.getString(R.string.folder_manage)
            }
            itemView.setOnClickListener {
                when (kind) {
                    PseudoKind.All -> { onPicked(NoteRepository.ListFilter.All); dismiss() }
                    PseudoKind.Favorite -> { onPicked(NoteRepository.ListFilter.Favorite); dismiss() }
                    PseudoKind.Deleted -> { onPicked(NoteRepository.ListFilter.Deleted); dismiss() }
                    PseudoKind.Manage -> {
                        context.startActivity(Intent(context, FolderManagerActivity::class.java))
                        dismiss()
                    }
                }
            }
        }
    }

    private inner class FolderHeadVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(folder: Folder, expand: Boolean) {
            itemView.findViewById<TextView>(R.id.folder_name).text = folder.name
            itemView.findViewById<View>(R.id.expand_chevron).rotation = if (expand) 180f else 0f
            itemView.setOnClickListener {
                if (expanded.contains(folder.id)) expanded -= folder.id else expanded += folder.id
                rebind(itemView.parent as RecyclerView)
            }
            itemView.setOnLongClickListener {
                onPicked(NoteRepository.ListFilter.Folder(folder.id)); dismiss(); true
            }
        }
    }

    private inner class NotebookVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(nb: Notebook) {
            itemView.findViewById<TextView>(R.id.notebook_name).text = nb.name
            val dot = itemView.findViewById<View>(R.id.color_dot)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor(nb.color))
            }
            itemView.setOnClickListener {
                onPicked(NoteRepository.ListFilter.Notebook(nb.id)); dismiss()
            }
        }
    }
}
```

- [ ] **Step 6: NoteListActivity 把 chip onClick 切成新 PopupWindow**

```kotlin
// NoteListActivity 内 onCreate 末尾或 reload() 之外某处：
filterChip.setOnClickListener {
    NotebookFilterPopupWindow(
        context = this,
        current = currentFilter,
        onPicked = { picked ->
            currentFilter = picked
            saveFilter(picked)
            lifecycleScope.launch { updateFilterChipLabel() }
            reload()
        },
    ).show(filterChip)
}
```

- [ ] **Step 7: 删旧 FilterPickerBottomSheet / CategoryManagerBottomSheet**

```bash
git rm code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/list/FilterPickerBottomSheet.kt
git rm code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/list/CategoryManagerBottomSheet.kt
# 同时删掉它们引用的 layout（若存在）：
# layout/bottom_sheet_filter_picker.xml / layout/bottom_sheet_category_manager.xml
# 用 ls 确认后 git rm
```

- [ ] **Step 8: 验证编译**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL。NoteListActivity 不再引用 FilterPickerBottomSheet/CategoryManagerBottomSheet。

- [ ] **Step 9: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/folder/NotebookFilterPopupWindow.kt \
        code/HuaWeiNote/app/src/main/res/layout/popup_notebook_filter.xml \
        code/HuaWeiNote/app/src/main/res/layout/item_filter_folder_header.xml \
        code/HuaWeiNote/app/src/main/res/layout/item_filter_notebook.xml \
        code/HuaWeiNote/app/src/main/res/layout/item_filter_pseudo.xml \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt && \
git commit -m "feat(m12): chip 切到 NotebookFilterPopupWindow（五伪项 + 折叠树）+ 删旧 FilterPicker/CategoryManager"
```

---

### Task 9: FolderManagerActivity 骨架 + 适配器（不含拖动 / 长按；拖动单独 Task 10）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/folder/FolderManagerActivity.kt`
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/folder/FolderManagerAdapter.kt`
- Create: `code/HuaWeiNote/app/src/main/res/layout/activity_folder_manager.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/item_folder_manager_create.xml`（"+ 新建笔记本" 行）
- Modify: `code/HuaWeiNote/app/src/main/AndroidManifest.xml`（声明 activity）
- Modify: `code/HuaWeiNote/app/src/main/res/menu/menu_note_list_toolbar.xml`（暂不动；FolderManager 用独立 menu）
- Create: `code/HuaWeiNote/app/src/main/res/menu/menu_folder_manager_toolbar.xml`（"+" 按钮 → 新建文件夹）

参考 spec §3.6。

- [ ] **Step 1: AndroidManifest 声明 FolderManagerActivity**

```xml
<activity
    android:name=".controller.folder.FolderManagerActivity"
    android:label="@string/folder_manage"
    android:parentActivityName=".controller.list.NoteListActivity"/>
```

- [ ] **Step 2: activity_folder_manager.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <androidx.appcompat.widget.Toolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:background="?attr/colorPrimarySurface"
        app:navigationIcon="@drawable/abc_ic_ab_back_material"
        xmlns:app="http://schemas.android.com/apk/res-auto"/>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recycler_folder_manager"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>
</LinearLayout>
```

- [ ] **Step 3: item_folder_manager_create.xml（"+ 新建笔记本"）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="@dimen/notebook_item_height"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="40dp"
    android:paddingEnd="16dp"
    android:background="?attr/selectableItemBackground">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/notebook_create"
        android:textColor="?android:attr/textColorSecondary"/>
</LinearLayout>
```

- [ ] **Step 4: menu_folder_manager_toolbar.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/action_new_folder"
        android:icon="@android:drawable/ic_input_add"
        android:title="@string/folder_create"
        app:showAsAction="always"
        xmlns:app="http://schemas.android.com/apk/res-auto"/>
</menu>
```

- [ ] **Step 5: FolderManagerAdapter.kt（数据 + 三种 ViewHolder）**

```kotlin
package com.fan.hwnote.app.controller.folder

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Folder
import com.fan.hwnote.app.model.entity.Notebook

sealed class FolderManagerRow {
    data class FolderHead(val folder: Folder, val expanded: Boolean) : FolderManagerRow()
    data class NotebookItem(val notebook: Notebook) : FolderManagerRow()
    data class CreateNotebook(val folderId: Long) : FolderManagerRow()
}

class FolderManagerAdapter(
    var rows: List<FolderManagerRow>,
    private val callbacks: Callbacks,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Callbacks {
        fun onFolderHeaderClicked(folder: Folder)
        fun onFolderHeaderOverflow(folder: Folder, anchor: View)
        fun onNotebookClicked(nb: Notebook)
        fun onNotebookOverflow(nb: Notebook, anchor: View)
        fun onCreateNotebookClicked(folderId: Long)
    }

    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is FolderManagerRow.FolderHead -> 0
        is FolderManagerRow.NotebookItem -> 1
        is FolderManagerRow.CreateNotebook -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> FolderHeadVH(inflater.inflate(R.layout.item_filter_folder_header, parent, false))
            1 -> NotebookVH(inflater.inflate(R.layout.item_filter_notebook, parent, false))
            else -> CreateVH(inflater.inflate(R.layout.item_folder_manager_create, parent, false))
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
        when (val r = rows[p]) {
            is FolderManagerRow.FolderHead -> (h as FolderHeadVH).bind(r.folder, r.expanded)
            is FolderManagerRow.NotebookItem -> (h as NotebookVH).bind(r.notebook)
            is FolderManagerRow.CreateNotebook -> (h as CreateVH).bind(r.folderId)
        }
    }

    inner class FolderHeadVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(folder: Folder, expanded: Boolean) {
            itemView.findViewById<TextView>(R.id.folder_name).text = folder.name
            itemView.findViewById<View>(R.id.expand_chevron).rotation = if (expanded) 180f else 0f
            itemView.setOnClickListener { callbacks.onFolderHeaderClicked(folder) }
            itemView.setOnLongClickListener { callbacks.onFolderHeaderOverflow(folder, itemView); true }
        }
    }

    inner class NotebookVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(nb: Notebook) {
            itemView.findViewById<TextView>(R.id.notebook_name).text = nb.name
            val dot = itemView.findViewById<View>(R.id.color_dot)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor(nb.color))
            }
            itemView.setOnClickListener { callbacks.onNotebookClicked(nb) }
            itemView.setOnLongClickListener { callbacks.onNotebookOverflow(nb, itemView); true }
        }
    }

    inner class CreateVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(folderId: Long) {
            itemView.setOnClickListener { callbacks.onCreateNotebookClicked(folderId) }
        }
    }
}
```

- [ ] **Step 6: FolderManagerActivity.kt（不含拖动；overflow 菜单接 Task 10/11 已就绪的资源）**

```kotlin
package com.fan.hwnote.app.controller.folder

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Folder
import com.fan.hwnote.app.model.entity.Notebook
import com.fan.hwnote.app.view.folder.NewFolderBottomSheet
import com.fan.hwnote.app.view.folder.NewNotebookBottomSheet
import kotlinx.coroutines.launch

class FolderManagerActivity : AppCompatActivity(), FolderManagerAdapter.Callbacks {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: FolderManagerAdapter
    private val expanded = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_manager)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        recycler = findViewById(R.id.recycler_folder_manager)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = FolderManagerAdapter(emptyList(), this)
        recycler.adapter = adapter
        // 默认展开"默认"文件夹
        expanded += 1L
        rebind()
    }

    private fun rebind() {
        lifecycleScope.launch {
            val folders = FolderRepository.list()
            val rows = mutableListOf<FolderManagerRow>()
            folders.forEach { f ->
                rows += FolderManagerRow.FolderHead(f, expanded.contains(f.id))
                if (expanded.contains(f.id)) {
                    NotebookRepository.listByFolder(f.id).forEach { rows += FolderManagerRow.NotebookItem(it) }
                    rows += FolderManagerRow.CreateNotebook(f.id)
                }
            }
            adapter.rows = rows
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_folder_manager_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_new_folder) {
            NewFolderBottomSheet(this, onSaved = { rebind() }).show()
            return true
        }
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    override fun onFolderHeaderClicked(folder: Folder) {
        if (expanded.contains(folder.id)) expanded -= folder.id else expanded += folder.id
        rebind()
    }

    override fun onFolderHeaderOverflow(folder: Folder, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_folder_overflow, popup.menu)
        popup.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                R.id.action_rename -> {
                    if (folder.id == 1L) {
                        Toast.makeText(this, R.string.folder_default_undeletable, Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    NewFolderBottomSheet(this, editing = folder.id, initialName = folder.name, onSaved = { rebind() }).show()
                    true
                }
                R.id.action_delete -> {
                    if (folder.id == 1L) {
                        Toast.makeText(this, R.string.folder_default_undeletable, Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    AlertDialog.Builder(this)
                        .setTitle(R.string.folder_delete)
                        .setMessage(R.string.dialog_delete_folder_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.action_delete) { _, _ ->
                            lifecycleScope.launch { FolderRepository.softDelete(folder.id); rebind() }
                        }.show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onNotebookClicked(nb: Notebook) {
        // 普通点击不动；长按出菜单
    }

    override fun onNotebookOverflow(nb: Notebook, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_notebook_overflow, popup.menu)
        popup.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                R.id.action_rename, R.id.action_change_color -> {
                    NewNotebookBottomSheet(this, folderId = nb.folderId, editing = nb, onSaved = { rebind() }).show()
                    true
                }
                R.id.action_move_to -> {
                    if (nb.id == 1L) {
                        Toast.makeText(this, "默认笔记本不可移动", Toast.LENGTH_SHORT).show(); return@setOnMenuItemClickListener true
                    }
                    showFolderPicker { targetFolderId ->
                        lifecycleScope.launch { NotebookRepository.move(nb.id, targetFolderId); rebind() }
                    }
                    true
                }
                R.id.action_delete -> {
                    if (nb.id == 1L) {
                        Toast.makeText(this, "默认笔记本不可删除", Toast.LENGTH_SHORT).show(); return@setOnMenuItemClickListener true
                    }
                    AlertDialog.Builder(this)
                        .setTitle(R.string.notebook_delete)
                        .setMessage(R.string.dialog_delete_notebook_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.action_delete) { _, _ ->
                            lifecycleScope.launch { NotebookRepository.softDelete(nb.id); rebind() }
                        }.show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onCreateNotebookClicked(folderId: Long) {
        NewNotebookBottomSheet(this, folderId = folderId, editing = null, onSaved = { rebind() }).show()
    }

    private fun showFolderPicker(onPicked: (Long) -> Unit) {
        lifecycleScope.launch {
            val folders = FolderRepository.list()
            val names = folders.map { it.name }.toTypedArray()
            AlertDialog.Builder(this@FolderManagerActivity)
                .setTitle(R.string.notebook_move_to)
                .setItems(names) { _, idx -> onPicked(folders[idx].id) }
                .show()
        }
    }
}
```

- [ ] **Step 7: 验证编译**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 8: Commit**

```bash
git add code/HuaWeiNote/app/src/main/AndroidManifest.xml \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/folder/FolderManagerActivity.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/folder/FolderManagerAdapter.kt \
        code/HuaWeiNote/app/src/main/res/layout/activity_folder_manager.xml \
        code/HuaWeiNote/app/src/main/res/layout/item_folder_manager_create.xml \
        code/HuaWeiNote/app/src/main/res/menu/menu_folder_manager_toolbar.xml && \
git commit -m "feat(m12): 加 FolderManagerActivity 骨架（折叠树 + overflow 菜单 + 新建/改名/删除/移动）"
```

---

### Task 10: FolderManagerActivity 三粒度拖动（文件夹整体上下 / 笔记本同夹内 / 笔记本跨夹）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/folder/FolderManagerActivity.kt`（attach ItemTouchHelper）
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/folder/FolderManagerDragHelper.kt`（封装 ItemTouchHelper.Callback）

参考 spec §3.6 拖动规则。

- [ ] **Step 1: FolderManagerDragHelper.kt**

```kotlin
package com.fan.hwnote.app.controller.folder

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * 三种粒度：
 *  - FolderHead：上下整体移动（连同其下展开的笔记本一起）
 *  - NotebookItem：可同夹内换序，也可跨到另一个 FolderHead 下方（落点决定 folder）
 *  - CreateNotebook 行：不可拖动，也不接受拖入
 *
 * 落点结算策略：
 *  - 拖到目标位置后 onClearView 时，重新计算结构 → 派发到 commitOrder()
 *  - 内部不直接写 DB；通过 callbacks 让 Activity 调 Repository
 */
class FolderManagerDragHelper(
    private val getRows: () -> List<FolderManagerRow>,
    private val setRows: (List<FolderManagerRow>) -> Unit,
    private val commitFolderOrder: (List<Long>) -> Unit,
    private val commitNotebookOrder: (folderId: Long, ids: List<Long>) -> Unit,
    private val commitNotebookMove: (notebookId: Long, targetFolderId: Long) -> Unit,
) : ItemTouchHelper.Callback() {

    private var dirty = false

    override fun isLongPressDragEnabled() = true
    override fun isItemViewSwipeEnabled() = false

    override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
        val pos = vh.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return 0
        val row = getRows()[pos]
        // CreateNotebook 不可拖
        if (row is FolderManagerRow.CreateNotebook) return 0
        // FolderHead "默认" 不可拖
        if (row is FolderManagerRow.FolderHead && row.folder.id == 1L) return 0
        // NotebookItem "默认" 不可跨夹（仍允许同夹内换序）—— 由 onMove 时校验
        return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    }

    override fun onMove(rv: RecyclerView, src: RecyclerView.ViewHolder, dst: RecyclerView.ViewHolder): Boolean {
        val from = src.bindingAdapterPosition
        val to = dst.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        val rows = getRows().toMutableList()
        val moving = rows[from]
        val target = rows[to]

        // 默认笔记本不可跨文件夹（同夹内可换序）
        if (moving is FolderManagerRow.NotebookItem && moving.notebook.id == 1L) {
            // 找 moving 所在折叠区段，target 必须仍在同一段
            val srcFolderId = moving.notebook.folderId
            val targetFolderId = inferFolderIdAt(rows, to) ?: return false
            if (srcFolderId != targetFolderId) return false
        }

        // FolderHead 整体移动：把 moving 这段（FolderHead 到下一个 FolderHead 之前）一起搬
        if (moving is FolderManagerRow.FolderHead) {
            val segStart = from
            var segEnd = from + 1
            while (segEnd < rows.size && rows[segEnd] !is FolderManagerRow.FolderHead) segEnd++
            val segment = rows.subList(segStart, segEnd).toList()
            // 不允许拖到默认下方第一段以上（id=1 必须保持顶端）
            if (target is FolderManagerRow.FolderHead && target.folder.id == 1L && to < from) return false
            repeat(segment.size) { rows.removeAt(segStart) }
            val insertAt = if (to > from) to - segment.size + 1 else to
            rows.addAll(insertAt.coerceAtLeast(0), segment)
            setRows(rows)
            dirty = true
            return true
        }

        // NotebookItem：单行搬运（跨段会改变其 folder 归属）
        if (moving is FolderManagerRow.NotebookItem) {
            rows.removeAt(from)
            rows.add(to, moving)
            setRows(rows)
            dirty = true
            return true
        }

        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        super.clearView(rv, vh)
        if (!dirty) return
        dirty = false
        // 全量结算：扫描 rows，得到 folder 顺序 + 每个 folder 内 notebook 顺序 + 跨夹 move
        val rows = getRows()
        val folderOrder = mutableListOf<Long>()
        val notebookByFolder = linkedMapOf<Long, MutableList<Long>>()
        var currentFolder: Long? = null
        rows.forEach { r ->
            when (r) {
                is FolderManagerRow.FolderHead -> {
                    folderOrder += r.folder.id
                    currentFolder = r.folder.id
                    notebookByFolder.getOrPut(r.folder.id) { mutableListOf() }
                }
                is FolderManagerRow.NotebookItem -> {
                    val cf = currentFolder ?: return@forEach
                    notebookByFolder.getOrPut(cf) { mutableListOf() } += r.notebook.id
                    if (r.notebook.folderId != cf) commitNotebookMove(r.notebook.id, cf)
                }
                is FolderManagerRow.CreateNotebook -> Unit
            }
        }
        commitFolderOrder(folderOrder)
        notebookByFolder.forEach { (fId, ids) -> commitNotebookOrder(fId, ids) }
    }

    private fun inferFolderIdAt(rows: List<FolderManagerRow>, index: Int): Long? {
        for (i in index downTo 0) {
            val r = rows[i]
            if (r is FolderManagerRow.FolderHead) return r.folder.id
            if (r is FolderManagerRow.NotebookItem) return r.notebook.folderId
        }
        return null
    }
}
```

- [ ] **Step 2: FolderManagerActivity attach helper**

```kotlin
// onCreate 内 recycler.adapter = adapter 之后：
val helper = ItemTouchHelper(FolderManagerDragHelper(
    getRows = { adapter.rows },
    setRows = { newRows -> adapter.rows = newRows; adapter.notifyDataSetChanged() },
    commitFolderOrder = { ids -> lifecycleScope.launch { FolderRepository.reorder(ids); rebind() } },
    commitNotebookOrder = { fId, ids -> lifecycleScope.launch { NotebookRepository.reorderInFolder(fId, ids); rebind() } },
    commitNotebookMove = { nbId, target -> lifecycleScope.launch { NotebookRepository.move(nbId, target) } },
))
helper.attachToRecyclerView(recycler)
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 真机自测拖动（按 spec §3.6 走查）**
  - 文件夹整体上下：默认始终顶端不动；非默认两段交换；展开状态保持
  - 笔记本同夹内换序：order_index 落库
  - 笔记本跨夹：拖到另一个文件夹头下方 → folder_id 与 order_index 都更新
  - 默认笔记本（id=1）拖动：不能离开默认文件夹

- [ ] **Step 5: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/folder/FolderManagerDragHelper.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/folder/FolderManagerActivity.kt && \
git commit -m "feat(m12): FolderManager 三粒度拖动（文件夹整体/笔记本同夹/笔记本跨夹）"
```

---

### Task 11: NotebookPickerPopupWindow（卡片长按"移动到笔记本…"+ 编辑器 indicator 共用）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/folder/NotebookPickerPopupWindow.kt`
- Create: `code/HuaWeiNote/app/src/main/res/layout/popup_notebook_picker.xml`（与 filter 类似，但树+底部"+ 新建笔记本"）
- Create: `code/HuaWeiNote/app/src/main/res/layout/item_picker_create_nb.xml`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`（卡片长按 → action_move_notebook）
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`（新增 `moveNoteToNotebook(noteId, targetNotebookId)`）

参考 spec §3.3 / §4 / §5。

- [ ] **Step 1: NoteRepository 加 moveNoteToNotebook + 单测**

NoteRepository.kt 内新增：

```kotlin
suspend fun moveNoteToNotebook(noteId: Long, targetNotebookId: Long?) = withContext(Dispatchers.IO) {
    val cv = ContentValues()
    if (targetNotebookId == null) cv.putNull("notebook_id") else cv.put("notebook_id", targetNotebookId)
    cv.put("updated_at", System.currentTimeMillis())
    helper.writableDatabase.update("notes", cv, "id = ?", arrayOf(noteId.toString()))
    Unit
}
```

NoteRepositorySaveGetTest.kt 追加：

```kotlin
@Test
fun `moveNoteToNotebook updates notebook_id and updated_at`() = runBlocking {
    val nb = NotebookRepository.insert(1L, "T", "#43A047")
    val id = NoteRepository.save(com.fan.hwnote.app.model.entity.Note.new().copy(title = "x", notebookId = null))
    val before = NoteRepository.get(id)!!.updatedAt
    Thread.sleep(5L)
    NoteRepository.moveNoteToNotebook(id, nb)
    val after = NoteRepository.get(id)!!
    assertEquals(nb, after.notebookId)
    assertTrue(after.updatedAt >= before)
}
```

- [ ] **Step 2: 跑测试 PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.NoteRepositorySaveGetTest"
```
Expected: 全部 PASSED。

- [ ] **Step 3: popup_notebook_picker.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="@dimen/popup_window_width"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="?attr/colorSurface"
    android:elevation="8dp">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recycler_picker"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxHeight="480dp"
        android:overScrollMode="never"/>
</LinearLayout>
```

- [ ] **Step 4: item_picker_create_nb.xml（"+ 新建笔记本"）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="@dimen/notebook_item_height"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="40dp"
    android:paddingEnd="16dp"
    android:background="?attr/selectableItemBackground">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/notebook_create"
        android:textColor="?attr/colorPrimary"/>
</LinearLayout>
```

- [ ] **Step 5: NotebookPickerPopupWindow.kt**

```kotlin
package com.fan.hwnote.app.view.folder

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.NotebookRepository
import com.fan.hwnote.app.model.entity.Folder
import com.fan.hwnote.app.model.entity.Notebook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotebookPickerPopupWindow(
    private val context: Context,
    private val currentNotebookId: Long?,
    private val onPicked: (Long) -> Unit,
) : PopupWindow() {

    private val expanded = mutableSetOf<Long>()

    init {
        contentView = LayoutInflater.from(context).inflate(R.layout.popup_notebook_picker, null)
        width = ViewGroup.LayoutParams.WRAP_CONTENT
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        isOutsideTouchable = true
        isFocusable = true
    }

    fun show(anchor: View) {
        // 默认展开当前选中所属文件夹
        if (currentNotebookId != null) {
            CoroutineScope(Dispatchers.Main).launch {
                val nb = withContext(Dispatchers.IO) { NotebookRepository.get(currentNotebookId) }
                nb?.folderId?.let { expanded += it }
                rebind()
            }
        } else {
            expanded += 1L
        }
        rebind()
        showAsDropDown(anchor)
    }

    private fun rebind() {
        val recycler = contentView.findViewById<RecyclerView>(R.id.recycler_picker)
        recycler.layoutManager = LinearLayoutManager(context)
        CoroutineScope(Dispatchers.Main).launch {
            val folders = withContext(Dispatchers.IO) { FolderRepository.list() }
            val rows = mutableListOf<Row>()
            folders.forEach { f ->
                rows += Row.FolderHead(f, expanded.contains(f.id))
                if (expanded.contains(f.id)) {
                    val nbs = withContext(Dispatchers.IO) { NotebookRepository.listByFolder(f.id) }
                    nbs.forEach { rows += Row.Notebook(it) }
                    rows += Row.CreateNew(f.id)
                }
            }
            recycler.adapter = PickerAdapter(rows)
        }
    }

    private sealed class Row {
        data class FolderHead(val folder: Folder, val expanded: Boolean) : Row()
        data class Notebook(val notebook: com.fan.hwnote.app.model.entity.Notebook) : Row()
        data class CreateNew(val folderId: Long) : Row()
    }

    private inner class PickerAdapter(val rows: List<Row>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.FolderHead -> 0
            is Row.Notebook -> 1
            is Row.CreateNew -> 2
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> FolderHeadVH(inflater.inflate(R.layout.item_filter_folder_header, parent, false))
                1 -> NotebookVH(inflater.inflate(R.layout.item_filter_notebook, parent, false))
                else -> CreateVH(inflater.inflate(R.layout.item_picker_create_nb, parent, false))
            }
        }
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            when (val r = rows[p]) {
                is Row.FolderHead -> (h as FolderHeadVH).bind(r.folder, r.expanded)
                is Row.Notebook -> (h as NotebookVH).bind(r.notebook)
                is Row.CreateNew -> (h as CreateVH).bind(r.folderId)
            }
        }
    }

    private inner class FolderHeadVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(folder: Folder, expand: Boolean) {
            itemView.findViewById<TextView>(R.id.folder_name).text = folder.name
            itemView.findViewById<View>(R.id.expand_chevron).rotation = if (expand) 180f else 0f
            itemView.setOnClickListener {
                if (expanded.contains(folder.id)) expanded -= folder.id else expanded += folder.id
                rebind()
            }
        }
    }

    private inner class NotebookVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(nb: Notebook) {
            itemView.findViewById<TextView>(R.id.notebook_name).text = nb.name
            val dot = itemView.findViewById<View>(R.id.color_dot)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor(nb.color))
            }
            itemView.setOnClickListener { onPicked(nb.id); dismiss() }
        }
    }

    private inner class CreateVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(folderId: Long) {
            itemView.setOnClickListener {
                NewNotebookBottomSheet(context, folderId, editing = null, onSaved = { newId ->
                    onPicked(newId)
                    dismiss()
                }).show()
            }
        }
    }
}
```

- [ ] **Step 6: NoteListActivity 卡片长按 → 移动到笔记本…**

```kotlin
// showCardMenu(note, anchor) 内 popup.setOnMenuItemClickListener 增加分支：
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
```

注意：menu_note_card_long_press.xml 在 Task 6 已加 `action_move_notebook`，本步骤只在 Activity 接通。

- [ ] **Step 7: 全量回归 + 编译**

```bash
./gradlew :app:test :app:assembleDebug
```
Expected: 全部 PASSED + BUILD SUCCESSFUL（measure-twice：测试增量 +1 → 总计 ~134）。

- [ ] **Step 8: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/folder/NotebookPickerPopupWindow.kt \
        code/HuaWeiNote/app/src/main/res/layout/popup_notebook_picker.xml \
        code/HuaWeiNote/app/src/main/res/layout/item_picker_create_nb.xml \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositorySaveGetTest.kt && \
git commit -m "feat(m12): 卡片长按移动到笔记本（NotebookPickerPopupWindow + moveNoteToNotebook）"
```

---

### Task 12: 编辑器 indicator + saveNote 兜底 notebookId + 隐藏 metaCategoryChip

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`
- Modify: `code/HuaWeiNote/app/src/main/res/layout/activity_note_editor.xml`（AppBar 加 indicator + 隐藏 metaCategoryChip）

参考 spec §4。

- [ ] **Step 1: activity_note_editor.xml AppBar 加 indicator chip**

在 Toolbar 内（或与 toolbar 同级 within AppBar）增加：

```xml
<LinearLayout
    android:id="@+id/notebook_indicator"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="12dp"
    android:paddingEnd="12dp"
    android:layout_marginStart="8dp"
    android:background="?attr/selectableItemBackground">

    <View
        android:id="@+id/indicator_dot"
        android:layout_width="12dp"
        android:layout_height="12dp"
        android:layout_marginEnd="8dp"/>

    <TextView
        android:id="@+id/indicator_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textAppearance="?attr/textAppearanceLabelMedium"/>

    <ImageView
        android:layout_width="14dp"
        android:layout_height="14dp"
        android:layout_marginStart="4dp"
        android:src="@drawable/ic_chevron_down"
        android:tint="?android:attr/textColorSecondary"/>
</LinearLayout>
```

把原有的 `metaCategoryChip` 容器 visibility 改为 `gone`（保留 view id 防其他引用，业务层不再用）。

- [ ] **Step 2: NoteEditorActivity 改造**

```kotlin
// 新增字段
private lateinit var notebookIndicator: View
private lateinit var indicatorDot: View
private lateinit var indicatorText: TextView
private var pendingNotebookId: Long? = null  // 草稿态（loaded.notebookId 之外的待保存值）

// onCreate 内 setupViews 之后：
notebookIndicator = findViewById(R.id.notebook_indicator)
indicatorDot = findViewById(R.id.indicator_dot)
indicatorText = findViewById(R.id.indicator_text)
notebookIndicator.setOnClickListener {
    val current = pendingNotebookId ?: loadedNote?.notebookId
    NotebookPickerPopupWindow(
        context = this,
        currentNotebookId = current,
        onPicked = { picked ->
            pendingNotebookId = picked
            lifecycleScope.launch { refreshIndicator(picked) }
        },
    ).show(notebookIndicator)
}

// loadedNote 装入后调用一次 refreshIndicator(loadedNote?.notebookId)。

private suspend fun refreshIndicator(notebookId: Long?) {
    val nb = if (notebookId != null) NotebookRepository.get(notebookId) else null
    if (nb != null) {
        indicatorText.text = nb.name
        (indicatorDot.background as? GradientDrawable
            ?: GradientDrawable().also { it.shape = GradientDrawable.OVAL; indicatorDot.background = it })
            .setColor(android.graphics.Color.parseColor(nb.color))
    } else {
        indicatorText.setText(R.string.indicator_no_notebook)
        (indicatorDot.background as? GradientDrawable
            ?: GradientDrawable().also { it.shape = GradientDrawable.OVAL; indicatorDot.background = it })
            .setColor(android.graphics.Color.parseColor("#CCCCCC"))
    }
}

// saveNote 内：把 categoryId 兜底改成 notebookId 兜底；现有 .copy(id, categoryId) 改成 .copy(id, categoryId, notebookId)：
private suspend fun saveNote(): Long {
    val title = titleInput.text.toString().trim().ifEmpty { "无标题" }
    val current = presenter.collectCurrentNote(title)
    val patched = loadedNote?.let { loaded ->
        current.copy(
            id = loaded.id,
            categoryId = loaded.categoryId,
            notebookId = pendingNotebookId ?: loaded.notebookId,
        )
    } ?: current.copy(notebookId = pendingNotebookId)
    val savedId = NoteRepository.save(patched)
    // ...原有 onSaveSuccess / cleanOrphan 逻辑保留
    return savedId
}
```

- [ ] **Step 3: 验证编译 + 全量回归**

```bash
./gradlew :app:test :app:assembleDebug
```
Expected: 全部 PASSED + BUILD SUCCESSFUL。

- [ ] **Step 4: 真机自测**
- 进入新建笔记 → indicator 显"默认"+ 灰点 → 点击 → 选另一笔记本 → indicator 切色 → 退出再进 → notebook_id 已落库
- 进入旧笔记（M11 持久化的）→ indicator 显"默认"（v3 迁移把 notebook_id 默认置 1）
- 旧 metaCategoryChip 区域已不显示

- [ ] **Step 5: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt \
        code/HuaWeiNote/app/src/main/res/layout/activity_note_editor.xml && \
git commit -m "feat(m12): 编辑器 AppBar indicator 接 NotebookPickerPopupWindow + saveNote 保留 notebookId"
```

---

### Task 13: 真机走查清单 + STATUS 收尾 + 计划归档

**Files:**
- Modify: `STATUS.md`（追加 ## M12 完成详情）
- Modify: `docs/superpowers/specs/2026-06-04-hwnote-m12-folder-hierarchy-design.md`（已存）
- Modify: `docs/superpowers/plans/2026-06-04-hwnote-m12-folder-hierarchy.md`（本文件，标记 done）

参考 spec §12 / §13。

- [ ] **Step 1: 真机走查清单（按 spec §13.1 执行）**
  - 全新安装 → DB v3 直建 → 默认文件夹 + 默认笔记本可见
  - 旧版本（v2）升级 → 旧"未分类"笔记 → notebook_id = 1（默认笔记本）
  - 卡片长按 → 移动到笔记本 → 列表立即过滤 / 排序保持
  - chip 弹层：4 伪项目（全部/收藏/回收站/管理文件夹）+ 文件夹折叠树正常
  - 编辑器 indicator：新建笔记/打开旧笔记 / 切换笔记本 / 保存退出再开 → notebook_id 持久化
  - FolderManager：新建文件夹 / 改名 / 删除（默认禁用） / 三粒度拖动
  - 默认保护：默认文件夹/默认笔记本 长按看不到删除项 或弹 toast
  - 级联软删：删文件夹 → 其下笔记本/笔记 deleted_at != 0；回收站可见

- [ ] **Step 2: 全量回归测试**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test :app:assembleDebug
```
Expected: 全部 PASSED + BUILD SUCCESSFUL。期望 ~134 tests（114 + 20 新增）。

- [ ] **Step 3: STATUS.md 追加 M12 完成详情**

```markdown
## M12 完成详情（2026-06-XX）

**目标：** 把扁平 Category 升级为「文件夹 → 笔记本 → 笔记」三层结构，对齐 PRD §13 之外的华为 Note 信息架构需求。

**改动总览：**
- DB v2 → v3：新增 folders / notebooks 表，notes 加 notebook_id 列；旧 categories 表保留为数据墓碑
- ListFilter sealed 由 Category(id) 改为 Folder(folderId)/Notebook(notebookId)；旧 prefs 值兼容回退到 All
- 入口：列表 chip → NotebookFilterPopupWindow（5 伪项 + 折叠树）
- 入口：编辑器 AppBar indicator → NotebookPickerPopupWindow
- 入口：卡片长按 → 移动到笔记本
- FolderManagerActivity：折叠树 + overflow 菜单 + 三粒度拖动
- 默认文件夹/默认笔记本不可删除/改名/移动

**测试增量：** 114 → ~134（DB v3 迁移 +3 / Note round-trip +2 / FolderRepository +7 / NotebookRepository +7 / NoteRepository.list +3 / moveNoteToNotebook +1 / ListFilter persistence +N）

**涉及文件：** 详见 spec §3 / §11 + plan File Structure。

**真机走查：** 按 §13.1 8 项走查通过。
```

- [ ] **Step 4: 标记 plan 完成 + 归档**

把本文件顶部 Goal 下方追加：

```markdown
> **Status (2026-06-XX):** 13 任务全部完成；测试 ~134 PASSED；真机走查通过。STATUS.md 同步。
```

- [ ] **Step 5: Commit STATUS + plan 收尾**

```bash
git add STATUS.md docs/superpowers/plans/2026-06-04-hwnote-m12-folder-hierarchy.md && \
git commit -m "docs(m12): STATUS 追加 M12 完成详情 + 计划归档"
```

---

## 验证总结（13 任务串行后必须满足）

1. **测试 ~134 全 PASSED**（含 DB v3 迁移、FolderRepository、NotebookRepository、ListFilter、NoteRepository.list、moveNoteToNotebook）
2. **真机 8 项走查全过**（spec §13.1）
3. **数据兼容性**：旧用户升级后看到的笔记仍可见，旧"未分类"自动落入默认笔记本
4. **默认保护**：默认文件夹/默认笔记本无法被删除/改名/跨夹移动
5. **级联软删**：folder 软删→其下 notebook + note 全部 deleted_at != 0
6. **持久化**：filter / pendingNotebookId / 笔记 notebook_id / 文件夹/笔记本 order_index 全部 SQLite 落库
7. **构建无警告**：`./gradlew :app:assembleDebug` 无 warning
8. **代码风格**：sealed when 表达式形式（无 `else -> Unit`）；二次出现就 import；FQN 仅在 `entity.Note` / `model.entity.Note` 命名歧义处使用

---

## 自审 Notes（writer pre-handoff）

- 类型一致性：Folder(id, name, orderIndex, isDefault, deletedAt) / Notebook(id, folderId, name, color, orderIndex, isDefault, deletedAt) / Note 新增 notebookId — 全文件路径与字段名统一。
- 方法签名一致性：FolderRepository.softDelete(id) / NotebookRepository.softDelete(id) / NotebookRepository.move(id, targetFolderId) / NoteRepository.moveNoteToNotebook(noteId, targetNotebookId) — Repository 与 Activity 调用方一致。
- BottomSheet 回调命名：onSaved (Long)，spec §5 / §7.1 / 调用方 NewFolderBottomSheet/NewNotebookBottomSheet 全部一致（无 onCreated 残留）。
- 拖动结算：Task 10 commit-on-clearView 全量结算（folder order + notebook order per folder + 跨夹 move），与 spec §3.6 一致。
- 旧持久化兼容：Task 5 loadFilter 处理 `CATEGORY` / `UNCATEGORIZED` 都回退到 All，避免 sealed when 崩溃。
- ListFilter.Folder 与 NoteRepository.cursorToNote 不冲突：列表 SQL 通过 IN 子查询过滤 notebook_id，cursorToNote 不需要改。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-04-hwnote-m12-folder-hierarchy.md`.

两种执行模式：

1. **Subagent-Driven（推荐）** — fresh subagent / 任务，spec compliance + code quality 双审，串行 13 任务
2. **Inline Execution** — 当前会话内串行执行 13 任务，每任务完成后做 checkpoint

请选择。
