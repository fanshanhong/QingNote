# M14a 数据层 + Fragment 重构 + 待办列表页 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 HwNote app 新增 Todo 数据层（DB v5 + 实体 + Repository），将 NoteListActivity 重构为 Fragment 容器（NoteListFragment + TodoListFragment），实现待办列表页含分组、新建栏、筛选面板。

**Architecture:** 数据层沿用 NoteRepository 的 object 单例 + suspend + Dispatchers.IO 模式。NoteListActivity 拆为容器 Activity（仅管底部 tab 导航 + Fragment 切换），笔记列表逻辑搬入 NoteListFragment，新建 TodoListFragment 承载待办列表。TodoListAdapter 使用多 ViewType（section header + todo item）实现按时间分组。

**Tech Stack:** Kotlin / SQLiteOpenHelper / RecyclerView multi-ViewType / Fragment / SharedPreferences / lifecycleScope

**Spec 引用:** `docs/superpowers/specs/2026-06-05-m14-todo-system.md` §2 + §3

---

## 文件结构总览

| 操作 | 文件路径 | 职责 |
|------|----------|------|
| 新建 | `model/entity/Todo.kt` | Todo 数据类 |
| 新建 | `model/entity/RepeatType.kt` | 重复类型枚举 |
| 新建 | `model/TodoRepository.kt` | Todo 数据操作单例 |
| 新建 | `controller/list/TodoListFilter.kt` | 待办筛选条件 sealed class |
| 新建 | `controller/list/NoteListFragment.kt` | 笔记列表 Fragment（从 Activity 搬入） |
| 新建 | `controller/list/TodoListFragment.kt` | 待办列表 Fragment |
| 新建 | `controller/list/TodoListAdapter.kt` | 待办列表 Adapter（多 ViewType 分组） |
| 新建 | `controller/list/TodoFilterPanelAdapter.kt` | 待办筛选面板 Adapter |
| 修改 | `model/db/NoteDbHelper.kt` | DB v4→v5 迁移，新增 todos 表 |
| 修改 | `controller/list/NoteListActivity.kt` | 重构为 Fragment 容器 |
| 修改 | `App.kt` | 加 TodoRepository.init + purgeExpired |
| 修改 | `AndroidManifest.xml` | 声明 TodoDetailActivity 占位 |
| 新建 | `res/layout/fragment_note_list.xml` | 笔记列表 Fragment 布局 |
| 新建 | `res/layout/fragment_todo_list.xml` | 待办列表 Fragment 布局 |
| 新建 | `res/layout/item_todo_card.xml` | 待办卡片布局 |
| 新建 | `res/layout/item_todo_section_header.xml` | 待办分组标题布局 |
| 新建 | `res/layout/layout_quick_add_bar.xml` | 底部新建栏布局 |
| 修改 | `res/layout/activity_note_list.xml` | 改为 fragment_container + bottom_nav |
| 修改 | `res/values/strings.xml` | 待办相关字符串 |
| 新建 | `res/drawable/ic_todo_checkbox.xml` | 圆形未选中框 |
| 新建 | `res/drawable/ic_todo_checkbox_checked.xml` | 圆形已选中框 |
| 新建 | `res/drawable/ic_clock.xml` | 时间图标 |
| 新建 | `res/drawable/ic_important.xml` | 重要标记图标 |
| 新建 | `test/.../model/entity/RepeatTypeTest.kt` | RepeatType 枚举测试 |
| 新建 | `test/.../model/db/TodoDbMigrationTest.kt` | DB v5 迁移测试 |
| 新建 | `test/.../model/TodoRepositoryTest.kt` | TodoRepository CRUD + 重复推进测试 |

---

### Task 1: Todo 实体 + RepeatType 枚举 + 单测

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/model/entity/RepeatType.kt`
- Create: `app/src/main/java/com/fan/hwnote/app/model/entity/Todo.kt`
- Create: `app/src/test/java/com/fan/hwnote/app/model/entity/RepeatTypeTest.kt`

- [ ] **Step 1: 创建 RepeatType 枚举**

```kotlin
// app/src/main/java/com/fan/hwnote/app/model/entity/RepeatType.kt
package com.fan.hwnote.app.model.entity

enum class RepeatType(val value: Int) {
    NONE(0), DAILY(1), WEEKLY(2), MONTHLY(3), YEARLY(4);
    companion object {
        fun fromValue(v: Int): RepeatType =
            entries.firstOrNull { it.value == v } ?: NONE
    }
}
```

- [ ] **Step 2: 创建 Todo 数据类**

```kotlin
// app/src/main/java/com/fan/hwnote/app/model/entity/Todo.kt
package com.fan.hwnote.app.model.entity

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
    val updatedAt: Long,
) {
    companion object {
        fun new(now: Long = System.currentTimeMillis()): Todo = Todo(
            createdAt = now,
            updatedAt = now,
        )
    }
}
```

- [ ] **Step 3: 编写 RepeatType 单测**

```kotlin
// app/src/test/java/com/fan/hwnote/app/model/entity/RepeatTypeTest.kt
package com.fan.hwnote.app.model.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RepeatTypeTest {

    @Test
    fun `fromValue returns correct type for valid values`() {
        assertEquals(RepeatType.NONE, RepeatType.fromValue(0))
        assertEquals(RepeatType.DAILY, RepeatType.fromValue(1))
        assertEquals(RepeatType.WEEKLY, RepeatType.fromValue(2))
        assertEquals(RepeatType.MONTHLY, RepeatType.fromValue(3))
        assertEquals(RepeatType.YEARLY, RepeatType.fromValue(4))
    }

    @Test
    fun `fromValue returns NONE for unknown value`() {
        assertEquals(RepeatType.NONE, RepeatType.fromValue(99))
        assertEquals(RepeatType.NONE, RepeatType.fromValue(-1))
    }

    @Test
    fun `value property round-trips correctly`() {
        for (rt in RepeatType.entries) {
            assertEquals(rt, RepeatType.fromValue(rt.value))
        }
    }
}
```

- [ ] **Step 4: 运行测试验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.entity.RepeatTypeTest" -q
```

预期：3 tests PASSED

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/model/entity/RepeatType.kt \
       app/src/main/java/com/fan/hwnote/app/model/entity/Todo.kt \
       app/src/test/java/com/fan/hwnote/app/model/entity/RepeatTypeTest.kt
git commit -m "feat(m14a): 新增 Todo 实体 + RepeatType 枚举 + 单测"
```

---

### Task 2: DB v5 迁移 + 单测

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/model/db/NoteDbHelper.kt`
- Create: `app/src/test/java/com/fan/hwnote/app/model/db/TodoDbMigrationTest.kt`

- [ ] **Step 1: 编写 DB v5 迁移测试**

```kotlin
// app/src/test/java/com/fan/hwnote/app/model/db/TodoDbMigrationTest.kt
package com.fan.hwnote.app.model.db

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodoDbMigrationTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase(NoteDbHelper.DB_NAME)
    }

    @Test
    fun `onCreate v5 creates todos table with all columns`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = NoteDbHelper(ctx).writableDatabase

        val cursor = db.rawQuery("PRAGMA table_info(todos)", null)
        val columns = mutableSetOf<String>()
        cursor.use { while (it.moveToNext()) columns.add(it.getString(it.getColumnIndexOrThrow("name"))) }

        assertEquals(
            setOf("id", "title", "memo", "is_completed", "is_important", "remind_at",
                "repeat_type", "folder_id", "deleted_at", "created_at", "updated_at"),
            columns,
        )
        db.close()
    }

    @Test
    fun `onCreate v5 creates todos indexes`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = NoteDbHelper(ctx).writableDatabase

        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='todos'", null,
        )
        val indexes = mutableSetOf<String>()
        cursor.use { while (it.moveToNext()) indexes.add(it.getString(0)) }

        assertTrue("缺索引 idx_todos_remind_at: $indexes", indexes.contains("idx_todos_remind_at"))
        assertTrue("缺索引 idx_todos_deleted_at: $indexes", indexes.contains("idx_todos_deleted_at"))
        db.close()
    }

    @Test
    fun `onUpgrade v4 to v5 creates todos table`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

        val dbFile = ctx.getDatabasePath(NoteDbHelper.DB_NAME)
        dbFile.parentFile?.mkdirs()
        val v4Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v4Db.version = 4
        v4Db.execSQL("""CREATE TABLE notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL DEFAULT '',
            plain_text TEXT NOT NULL DEFAULT '',
            content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
            is_favorite INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            category_id INTEGER,
            deleted_at INTEGER NOT NULL DEFAULT 0,
            notebook_id INTEGER,
            background TEXT NOT NULL DEFAULT 'plain'
        )""")
        v4Db.execSQL("""CREATE TABLE folders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            order_index INTEGER NOT NULL DEFAULT 0,
            is_default INTEGER NOT NULL DEFAULT 0,
            deleted_at INTEGER NOT NULL DEFAULT 0
        )""")
        v4Db.execSQL("""CREATE TABLE notebooks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            folder_id INTEGER NOT NULL,
            color TEXT NOT NULL DEFAULT '#9E9E9E',
            order_index INTEGER NOT NULL DEFAULT 0,
            is_default INTEGER NOT NULL DEFAULT 0,
            deleted_at INTEGER NOT NULL DEFAULT 0
        )""")
        v4Db.execSQL("""CREATE TABLE categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            color TEXT NOT NULL,
            order_index INTEGER NOT NULL DEFAULT 0
        )""")
        v4Db.close()

        val v5Db = NoteDbHelper(ctx).writableDatabase

        val tableCheck = v5Db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='todos'", null,
        )
        tableCheck.use { assertTrue("todos 表缺失", it.moveToFirst()) }

        val now = System.currentTimeMillis()
        val cv = android.content.ContentValues().apply {
            put("title", "测试待办")
            put("created_at", now)
            put("updated_at", now)
        }
        val id = v5Db.insert("todos", null, cv)
        assertTrue("插入失败", id > 0)

        val cursor = v5Db.rawQuery("SELECT * FROM todos WHERE id = ?", arrayOf(id.toString()))
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("测试待办", it.getString(it.getColumnIndexOrThrow("title")))
            assertEquals("", it.getString(it.getColumnIndexOrThrow("memo")))
            assertEquals(0, it.getInt(it.getColumnIndexOrThrow("is_completed")))
            assertEquals(0, it.getInt(it.getColumnIndexOrThrow("is_important")))
            assertEquals(0L, it.getLong(it.getColumnIndexOrThrow("remind_at")))
            assertEquals(0, it.getInt(it.getColumnIndexOrThrow("repeat_type")))
            assertTrue(it.isNull(it.getColumnIndexOrThrow("folder_id")))
            assertEquals(0L, it.getLong(it.getColumnIndexOrThrow("deleted_at")))
        }
        v5Db.close()
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.db.TodoDbMigrationTest" -q
```

预期：3 tests FAIL（todos 表不存在）

- [ ] **Step 3: 实现 DB v5 迁移**

修改 `NoteDbHelper.kt`：

1. `DB_VERSION` 改为 `5`

2. companion object 新增 SQL 常量：

```kotlin
private const val SQL_CREATE_TODOS = """
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
    )
"""
private const val SQL_INDEX_TODOS_REMIND =
    "CREATE INDEX idx_todos_remind_at ON todos(remind_at)"
private const val SQL_INDEX_TODOS_DELETED =
    "CREATE INDEX idx_todos_deleted_at ON todos(deleted_at)"
```

3. `onCreate` 中 `seedDefaults(db, allNotesAlreadyExist = false)` 之后追加：

```kotlin
db.execSQL(SQL_CREATE_TODOS)
db.execSQL(SQL_INDEX_TODOS_REMIND)
db.execSQL(SQL_INDEX_TODOS_DELETED)
```

4. `onUpgrade` 末尾追加 `if (oldVersion < 5)` 分支：

```kotlin
if (oldVersion < 5) {
    db.execSQL(SQL_CREATE_TODOS)
    db.execSQL(SQL_INDEX_TODOS_REMIND)
    db.execSQL(SQL_INDEX_TODOS_DELETED)
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.db.TodoDbMigrationTest" -q
```

预期：3 tests PASSED

- [ ] **Step 5: 运行既有 NoteDbHelperTest 确认无回归**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.db.NoteDbHelperTest" -q
```

预期：5 tests PASSED

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/model/db/NoteDbHelper.kt \
       app/src/test/java/com/fan/hwnote/app/model/db/TodoDbMigrationTest.kt
git commit -m "feat(m14a): DB v4→v5 迁移，新增 todos 表 + 索引"
```

---

### Task 3: TodoRepository 基本 CRUD + 单测

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/model/TodoRepository.kt`
- Create: `app/src/test/java/com/fan/hwnote/app/model/TodoRepositoryTest.kt`

**Context:** 沿用 `NoteRepository` 的 object 单例 + `lateinit dbHelper` + `suspend withContext(Dispatchers.IO)` 模式。

- [ ] **Step 1: 编写 TodoRepository CRUD 测试**

```kotlin
// app/src/test/java/com/fan/hwnote/app/model/TodoRepositoryTest.kt
package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.RepeatType
import com.fan.hwnote.app.model.entity.Todo
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
class TodoRepositoryTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        TodoRepository.init(ctx)
    }

    @Test
    fun `insert returns positive id and getById retrieves it`() = runBlocking {
        val todo = Todo.new().copy(title = "买牛奶")
        val id = TodoRepository.insert(todo)
        assertTrue(id > 0)
        val fetched = TodoRepository.getById(id)
        assertNotNull(fetched)
        assertEquals("买牛奶", fetched!!.title)
        assertEquals(false, fetched.isCompleted)
    }

    @Test
    fun `update modifies fields`() = runBlocking {
        val id = TodoRepository.insert(Todo.new().copy(title = "旧标题"))
        val old = TodoRepository.getById(id)!!
        TodoRepository.update(old.copy(title = "新标题", isImportant = true))
        val updated = TodoRepository.getById(id)!!
        assertEquals("新标题", updated.title)
        assertEquals(true, updated.isImportant)
    }

    @Test
    fun `list returns non-deleted todos`() = runBlocking {
        TodoRepository.insert(Todo.new().copy(title = "正常"))
        TodoRepository.insert(Todo.new().copy(title = "已删", deletedAt = 1L))
        val list = TodoRepository.list()
        assertEquals(1, list.size)
        assertEquals("正常", list[0].title)
    }

    @Test
    fun `count returns correct number`() = runBlocking {
        TodoRepository.insert(Todo.new().copy(title = "A"))
        TodoRepository.insert(Todo.new().copy(title = "B"))
        assertEquals(2, TodoRepository.count())
    }

    @Test
    fun `softDelete sets deletedAt`() = runBlocking {
        val id = TodoRepository.insert(Todo.new().copy(title = "待删"))
        TodoRepository.softDelete(id)
        val todo = TodoRepository.getById(id)!!
        assertTrue(todo.deletedAt > 0)
    }

    @Test
    fun `restore clears deletedAt`() = runBlocking {
        val id = TodoRepository.insert(Todo.new().copy(title = "待恢复"))
        TodoRepository.softDelete(id)
        TodoRepository.restore(id)
        val todo = TodoRepository.getById(id)!!
        assertEquals(0L, todo.deletedAt)
    }

    @Test
    fun `deletePermanently removes from database`() = runBlocking {
        val id = TodoRepository.insert(Todo.new().copy(title = "彻底删"))
        TodoRepository.deletePermanently(id)
        assertNull(TodoRepository.getById(id))
    }

    @Test
    fun `purgeExpired deletes old soft-deleted todos`() = runBlocking {
        val now = System.currentTimeMillis()
        val oldTime = now - 31L * 24 * 60 * 60 * 1000
        TodoRepository.insert(Todo.new().copy(title = "旧删除", deletedAt = oldTime))
        TodoRepository.insert(Todo.new().copy(title = "新删除", deletedAt = now))
        val purged = TodoRepository.purgeExpired(now)
        assertEquals(1, purged)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.TodoRepositoryTest" -q
```

预期：FAIL（TodoRepository 不存在）

- [ ] **Step 3: 实现 TodoRepository**

```kotlin
// app/src/main/java/com/fan/hwnote/app/model/TodoRepository.kt
package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.fan.hwnote.app.model.db.NoteDbHelper
import com.fan.hwnote.app.model.entity.RepeatType
import com.fan.hwnote.app.model.entity.Todo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object TodoRepository {

    private lateinit var dbHelper: NoteDbHelper

    fun init(context: Context) {
        dbHelper = NoteDbHelper(context.applicationContext)
    }

    suspend fun insert(todo: Todo): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cv = toContentValues(todo).apply {
            put("created_at", if (todo.createdAt > 0) todo.createdAt else now)
            put("updated_at", now)
        }
        dbHelper.writableDatabase.insert("todos", null, cv)
    }

    suspend fun update(todo: Todo) = withContext(Dispatchers.IO) {
        val cv = toContentValues(todo).apply {
            put("updated_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(todo.id.toString()))
        Unit
    }

    suspend fun getById(id: Long): Todo? = withContext(Dispatchers.IO) {
        val cursor = dbHelper.readableDatabase.query(
            "todos", null, "id = ?", arrayOf(id.toString()), null, null, null,
        )
        cursor.use { c -> if (c.moveToFirst()) cursorToTodo(c) else null }
    }

    suspend fun list(
        folderId: Long? = null,
        includeDeleted: Boolean = false,
        hideCompleted: Boolean = false,
    ): List<Todo> = withContext(Dispatchers.IO) {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (!includeDeleted) {
            where += "deleted_at = 0"
        } else {
            where += "deleted_at > 0"
        }
        if (folderId != null) {
            where += "folder_id = ?"
            args += folderId.toString()
        }
        if (hideCompleted) {
            where += "is_completed = 0"
        }
        val selection = where.joinToString(" AND ")
        val out = mutableListOf<Todo>()
        val cursor = dbHelper.readableDatabase.query(
            "todos", null, selection, args.toTypedArray(),
            null, null, "remind_at ASC, created_at DESC",
        )
        cursor.use { c -> while (c.moveToNext()) out += cursorToTodo(c) }
        out
    }

    suspend fun listUncategorized(hideCompleted: Boolean = false): List<Todo> =
        withContext(Dispatchers.IO) {
            val where = mutableListOf("deleted_at = 0", "folder_id IS NULL")
            if (hideCompleted) where += "is_completed = 0"
            val selection = where.joinToString(" AND ")
            val out = mutableListOf<Todo>()
            val cursor = dbHelper.readableDatabase.query(
                "todos", null, selection, null, null, null,
                "remind_at ASC, created_at DESC",
            )
            cursor.use { c -> while (c.moveToNext()) out += cursorToTodo(c) }
            out
        }

    suspend fun count(
        folderId: Long? = null,
        includeDeleted: Boolean = false,
    ): Int = withContext(Dispatchers.IO) {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (!includeDeleted) {
            where += "deleted_at = 0"
        } else {
            where += "deleted_at > 0"
        }
        if (folderId != null) {
            where += "folder_id = ?"
            args += folderId.toString()
        }
        val selection = where.joinToString(" AND ")
        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM todos WHERE $selection", args.toTypedArray(),
        )
        cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    suspend fun countUncategorized(): Int = withContext(Dispatchers.IO) {
        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM todos WHERE deleted_at = 0 AND folder_id IS NULL", null,
        )
        cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    suspend fun softDelete(id: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("deleted_at", System.currentTimeMillis()) }
        dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun softDeleteBatch(ids: List<Long>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply { put("deleted_at", now) }
            for (id in ids) {
                db.update("todos", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    suspend fun restore(id: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("deleted_at", 0L) }
        dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun deletePermanently(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("todos", "id = ?", arrayOf(id.toString()))
    }

    suspend fun purgeExpired(
        now: Long = System.currentTimeMillis(),
        ttlMs: Long = 30L * 24 * 60 * 60 * 1000,
    ): Int = withContext(Dispatchers.IO) {
        val cutoff = now - ttlMs
        dbHelper.writableDatabase.delete(
            "todos", "deleted_at > 0 AND deleted_at < ?", arrayOf(cutoff.toString()),
        )
    }

    suspend fun completeTodo(id: Long) = withContext(Dispatchers.IO) {
        val todo = getByIdSync(id) ?: return@withContext
        if (todo.repeatType != RepeatType.NONE && todo.remindAt > 0) {
            val nextRemind = advanceRemindAt(todo.remindAt, todo.repeatType)
            val cv = ContentValues().apply {
                put("remind_at", nextRemind)
                put("updated_at", System.currentTimeMillis())
            }
            dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(id.toString()))
        } else {
            val cv = ContentValues().apply {
                put("is_completed", 1)
                put("updated_at", System.currentTimeMillis())
            }
            dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(id.toString()))
        }
    }

    suspend fun uncompleteTodo(id: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("is_completed", 0)
            put("updated_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.update("todos", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun listPendingAlarms(): List<Todo> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Todo>()
        val cursor = dbHelper.readableDatabase.query(
            "todos", null,
            "remind_at > 0 AND is_completed = 0 AND deleted_at = 0",
            null, null, null, "remind_at ASC",
        )
        cursor.use { c -> while (c.moveToNext()) out += cursorToTodo(c) }
        out
    }

    // ----- internal -----

    internal fun advanceRemindAt(remindAt: Long, repeatType: RepeatType): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = remindAt }
        do {
            when (repeatType) {
                RepeatType.DAILY -> cal.add(Calendar.DAY_OF_MONTH, 1)
                RepeatType.WEEKLY -> cal.add(Calendar.DAY_OF_MONTH, 7)
                RepeatType.MONTHLY -> cal.add(Calendar.MONTH, 1)
                RepeatType.YEARLY -> cal.add(Calendar.YEAR, 1)
                RepeatType.NONE -> return remindAt
            }
        } while (cal.timeInMillis < now)
        return cal.timeInMillis
    }

    private fun getByIdSync(id: Long): Todo? {
        val cursor = dbHelper.readableDatabase.query(
            "todos", null, "id = ?", arrayOf(id.toString()), null, null, null,
        )
        return cursor.use { c -> if (c.moveToFirst()) cursorToTodo(c) else null }
    }

    private fun toContentValues(todo: Todo): ContentValues = ContentValues().apply {
        put("title", todo.title)
        put("memo", todo.memo)
        put("is_completed", if (todo.isCompleted) 1 else 0)
        put("is_important", if (todo.isImportant) 1 else 0)
        put("remind_at", todo.remindAt)
        put("repeat_type", todo.repeatType.value)
        if (todo.folderId == null) putNull("folder_id") else put("folder_id", todo.folderId)
        put("deleted_at", todo.deletedAt)
    }

    private fun cursorToTodo(c: Cursor): Todo {
        val folderIdx = c.getColumnIndexOrThrow("folder_id")
        return Todo(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
            memo = c.getString(c.getColumnIndexOrThrow("memo")) ?: "",
            isCompleted = c.getInt(c.getColumnIndexOrThrow("is_completed")) == 1,
            isImportant = c.getInt(c.getColumnIndexOrThrow("is_important")) == 1,
            remindAt = c.getLong(c.getColumnIndexOrThrow("remind_at")),
            repeatType = RepeatType.fromValue(
                c.getInt(c.getColumnIndexOrThrow("repeat_type")),
            ),
            folderId = if (c.isNull(folderIdx)) null else c.getLong(folderIdx),
            deletedAt = c.getLong(c.getColumnIndexOrThrow("deleted_at")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        )
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.TodoRepositoryTest" -q
```

预期：8 tests PASSED

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/model/TodoRepository.kt \
       app/src/test/java/com/fan/hwnote/app/model/TodoRepositoryTest.kt
git commit -m "feat(m14a): 新增 TodoRepository CRUD + 软删除 + 单测"
```

---

### Task 4: TodoRepository completeTodo 重复推进 + 单测

**Files:**
- Modify: `app/src/test/java/com/fan/hwnote/app/model/TodoRepositoryTest.kt`

**Context:** completeTodo 的重复推进逻辑已在 Task 3 实现，此 task 补充专项测试。

- [ ] **Step 1: 在 TodoRepositoryTest 追加重复推进测试**

在 `TodoRepositoryTest.kt` 文件末尾（最后一个 `}` 之前）追加以下测试方法，并在文件顶部追加 `import java.util.Calendar`：

```kotlin
@Test
fun `completeTodo marks non-repeat as completed`() = runBlocking {
    val id = TodoRepository.insert(Todo.new().copy(
        title = "一次性",
        repeatType = RepeatType.NONE,
    ))
    TodoRepository.completeTodo(id)
    val todo = TodoRepository.getById(id)!!
    assertTrue(todo.isCompleted)
}

@Test
fun `completeTodo advances daily repeat remindAt`() = runBlocking {
    val now = System.currentTimeMillis()
    val remindAt = now + 60_000L
    val id = TodoRepository.insert(Todo.new().copy(
        title = "每天",
        repeatType = RepeatType.DAILY,
        remindAt = remindAt,
    ))
    TodoRepository.completeTodo(id)
    val todo = TodoRepository.getById(id)!!
    assertEquals(false, todo.isCompleted)
    assertTrue("remindAt 应推进", todo.remindAt > remindAt)
}

@Test
fun `completeTodo advances past-due repeat to future`() = runBlocking {
    val now = System.currentTimeMillis()
    val pastRemind = now - 3L * 24 * 60 * 60 * 1000
    val id = TodoRepository.insert(Todo.new().copy(
        title = "过期重复",
        repeatType = RepeatType.DAILY,
        remindAt = pastRemind,
    ))
    TodoRepository.completeTodo(id)
    val todo = TodoRepository.getById(id)!!
    assertEquals(false, todo.isCompleted)
    assertTrue("remindAt 应推进到未来", todo.remindAt >= now)
}

@Test
fun `uncompleteTodo clears completed flag`() = runBlocking {
    val id = TodoRepository.insert(Todo.new().copy(title = "取消完成"))
    TodoRepository.completeTodo(id)
    TodoRepository.uncompleteTodo(id)
    val todo = TodoRepository.getById(id)!!
    assertEquals(false, todo.isCompleted)
}

@Test
fun `advanceRemindAt weekly adds 7 days`() {
    val cal = Calendar.getInstance().apply {
        set(2026, Calendar.JUNE, 5, 14, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val futureBase = cal.timeInMillis + 100L * 24 * 60 * 60 * 1000
    val advanced = TodoRepository.advanceRemindAt(futureBase, RepeatType.WEEKLY)
    assertTrue(advanced > futureBase)
}

@Test
fun `listPendingAlarms returns only active reminders`() = runBlocking {
    val future = System.currentTimeMillis() + 60_000L
    TodoRepository.insert(Todo.new().copy(title = "有提醒", remindAt = future))
    TodoRepository.insert(Todo.new().copy(title = "无提醒", remindAt = 0L))
    TodoRepository.insert(Todo.new().copy(title = "已完成", remindAt = future, isCompleted = true))
    val pending = TodoRepository.listPendingAlarms()
    assertEquals(1, pending.size)
    assertEquals("有提醒", pending[0].title)
}
```

- [ ] **Step 2: 运行测试验证通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test --tests "com.fan.hwnote.app.model.TodoRepositoryTest" -q
```

预期：14 tests PASSED

- [ ] **Step 3: 提交**

```bash
git add app/src/test/java/com/fan/hwnote/app/model/TodoRepositoryTest.kt
git commit -m "test(m14a): TodoRepository completeTodo 重复推进 + listPendingAlarms 测试"
```

---

### Task 5: TodoListFilter + App.kt 初始化

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/controller/list/TodoListFilter.kt`
- Modify: `app/src/main/java/com/fan/hwnote/app/App.kt`

- [ ] **Step 1: 创建 TodoListFilter sealed class**

```kotlin
// app/src/main/java/com/fan/hwnote/app/controller/list/TodoListFilter.kt
package com.fan.hwnote.app.controller.list

sealed class TodoListFilter {
    object All : TodoListFilter()
    object Uncategorized : TodoListFilter()
    object Deleted : TodoListFilter()
    data class ByFolder(val folderId: Long) : TodoListFilter()
}
```

- [ ] **Step 2: 修改 App.kt 初始化 TodoRepository**

在 `App.kt` 的 `import` 区追加：
```kotlin
import com.fan.hwnote.app.model.TodoRepository
```

在 `onCreate()` 中 `NotebookRepository.init(this)` 之后追加：
```kotlin
TodoRepository.init(this)
```

在 `GlobalScope.launch(Dispatchers.IO)` 块中 `runCatching { NoteRepository.purgeExpired() }` 之后追加：
```kotlin
runCatching { TodoRepository.purgeExpired() }
```

最终 `App.kt` 的 `onCreate` 如下：

```kotlin
@OptIn(DelicateCoroutinesApi::class)
override fun onCreate() {
    super.onCreate()
    instance = this
    NoteRepository.init(this)
    CategoryRepository.init(this)
    FolderRepository.init(this)
    NotebookRepository.init(this)
    TodoRepository.init(this)
    GlobalScope.launch(Dispatchers.IO) {
        runCatching { NoteRepository.purgeExpired() }
        runCatching { TodoRepository.purgeExpired() }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/TodoListFilter.kt \
       app/src/main/java/com/fan/hwnote/app/App.kt
git commit -m "feat(m14a): 新增 TodoListFilter + App 初始化 TodoRepository"
```

---

### Task 6: 资源文件批量新增（strings + drawables + layouts）

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_todo_checkbox.xml`
- Create: `app/src/main/res/drawable/ic_todo_checkbox_checked.xml`
- Create: `app/src/main/res/drawable/ic_clock.xml`
- Create: `app/src/main/res/drawable/ic_important.xml`
- Create: `app/src/main/res/layout/fragment_note_list.xml`
- Create: `app/src/main/res/layout/fragment_todo_list.xml`
- Create: `app/src/main/res/layout/item_todo_card.xml`
- Create: `app/src/main/res/layout/item_todo_section_header.xml`
- Create: `app/src/main/res/layout/layout_quick_add_bar.xml`
- Modify: `app/src/main/res/layout/activity_note_list.xml`

- [ ] **Step 1: strings.xml 追加待办字符串**

在 `strings.xml` 的 `</resources>` 前追加：

```xml
<!-- M14a: 待办 -->
<string name="filter_all_todos">全部待办</string>
<string name="todo_count_format">%d 条待办</string>
<string name="todo_section_overdue">已过期</string>
<string name="todo_section_today">今天</string>
<string name="todo_section_tomorrow">明天</string>
<string name="todo_section_later">更晚</string>
<string name="todo_section_no_date">无日期</string>
<string name="todo_section_completed">已完成</string>
<string name="todo_quick_add_hint">待办事项</string>
<string name="todo_quick_add_save">保存</string>
<string name="todo_empty_title">还没有待办</string>
<string name="todo_empty_subtitle">点 + 新建一条</string>
<string name="todo_repeat_daily">每天</string>
<string name="todo_repeat_weekly">每周</string>
<string name="todo_repeat_monthly">每月</string>
<string name="todo_repeat_yearly">每年</string>
<string name="todo_time_format">%1$s %2$s</string>
<string name="todo_menu_hide_completed">隐藏已完成待办</string>
<string name="todo_menu_show_completed">显示已完成待办</string>
<string name="todo_menu_batch_delete">批量删除</string>
```

- [ ] **Step 2: 创建 drawable 资源**

**ic_todo_checkbox.xml** — 圆形未选中框：
```xml
<!-- app/src/main/res/drawable/ic_todo_checkbox.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#9E9E9E"
        android:strokeWidth="2"
        android:pathData="M12,12m-10,0a10,10 0,1 1,20 0a10,10 0,1 1,-20 0" />
</vector>
```

**ic_todo_checkbox_checked.xml** — 圆形已选中框：
```xml
<!-- app/src/main/res/drawable/ic_todo_checkbox_checked.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#9E9E9E"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8z" />
</vector>
```

**ic_clock.xml** — 时间图标：
```xml
<!-- app/src/main/res/drawable/ic_clock.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#9E9E9E"
        android:pathData="M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z" />
</vector>
```

**ic_important.xml** — 重要标记（感叹号）：
```xml
<!-- app/src/main/res/drawable/ic_important.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#9E9E9E"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-2h2v2zM13,13h-2V7h2v6z" />
</vector>
```

- [ ] **Step 3: 创建 item_todo_card.xml**

```xml
<!-- app/src/main/res/layout/item_todo_card.xml -->
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="@dimen/spacing_m"
    android:layout_marginVertical="2dp"
    app:cardBackgroundColor="@color/bg_card"
    app:cardCornerRadius="12dp"
    app:cardElevation="0dp"
    app:strokeWidth="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="@dimen/spacing_m">

        <ImageView
            android:id="@+id/todo_checkbox"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_todo_checkbox"
            android:contentDescription="@string/todo_quick_add_save" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="@dimen/spacing_m"
            android:orientation="vertical">

            <TextView
                android:id="@+id/todo_title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textColor="@color/text_primary"
                android:textSize="16sp"
                android:maxLines="2"
                android:ellipsize="end" />

            <TextView
                android:id="@+id/todo_subtitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:textColor="@color/text_hint"
                android:textSize="13sp"
                android:maxLines="1"
                android:visibility="gone" />
        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 4: 创建 item_todo_section_header.xml**

```xml
<!-- app/src/main/res/layout/item_todo_section_header.xml -->
<?xml version="1.0" encoding="utf-8"?>
<TextView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/section_title"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingHorizontal="@dimen/spacing_l"
    android:paddingTop="@dimen/spacing_m"
    android:paddingBottom="@dimen/spacing_xs"
    android:textColor="@color/text_secondary"
    android:textSize="14sp"
    android:textStyle="bold" />
```

- [ ] **Step 5: 创建 layout_quick_add_bar.xml**

```xml
<!-- app/src/main/res/layout/layout_quick_add_bar.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/quick_add_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@color/white"
    android:elevation="8dp"
    android:visibility="gone">

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="@color/divider" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="@dimen/spacing_m">

        <EditText
            android:id="@+id/quick_add_input"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@null"
            android:hint="@string/todo_quick_add_hint"
            android:inputType="text"
            android:maxLines="1"
            android:textColor="@color/text_primary"
            android:textColorHint="@color/text_hint"
            android:textSize="@dimen/text_body" />

        <ImageView
            android:id="@+id/quick_add_time"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:padding="6dp"
            android:src="@drawable/ic_clock"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:clickable="true"
            android:focusable="true"
            android:contentDescription="@string/todo_section_today" />

        <ImageView
            android:id="@+id/quick_add_important"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:padding="6dp"
            android:src="@drawable/ic_important"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:clickable="true"
            android:focusable="true"
            android:contentDescription="@string/todo_quick_add_hint" />

        <TextView
            android:id="@+id/quick_add_save"
            android:layout_width="wrap_content"
            android:layout_height="32dp"
            android:layout_marginStart="@dimen/spacing_xs"
            android:background="@drawable/shape_search_bar_bg"
            android:backgroundTint="@color/primary"
            android:gravity="center"
            android:paddingHorizontal="@dimen/spacing_m"
            android:text="@string/todo_quick_add_save"
            android:textColor="@color/white"
            android:textSize="14sp" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 6: 创建 fragment_note_list.xml**

将现有 `activity_note_list.xml` 的内容（去掉 bottom_nav 部分）提取为 Fragment 布局。顶层从 LinearLayout 改为不含 `fitsSystemWindows`（由 Activity 处理）：

```xml
<!-- app/src/main/res/layout/fragment_note_list.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/root_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_window">

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

    <!-- 内容区 -->
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
</LinearLayout>
```

- [ ] **Step 7: 创建 fragment_todo_list.xml**

```xml
<!-- app/src/main/res/layout/fragment_todo_list.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_window">

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
                    android:text="@string/filter_all_todos"
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

    <!-- 内容区 -->
    <FrameLayout
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
            android:id="@+id/recycler_todos"
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
                android:src="@drawable/ic_todo_tab" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_m"
                android:text="@string/todo_empty_title"
                android:textColor="@color/text_secondary"
                android:textSize="@dimen/text_body" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_xs"
                android:text="@string/todo_empty_subtitle"
                android:textColor="@color/text_hint"
                android:textSize="@dimen/text_caption" />
        </LinearLayout>

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/fab_new_todo"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="@dimen/spacing_l"
            android:contentDescription="@string/filter_all_todos"
            android:src="@drawable/ic_add"
            app:backgroundTint="@color/primary"
            app:tint="@color/white" />
    </FrameLayout>

    <!-- 底部新建栏 -->
    <include layout="@layout/layout_quick_add_bar" />
</LinearLayout>
```

- [ ] **Step 8: 重写 activity_note_list.xml 为 Fragment 容器**

```xml
<!-- app/src/main/res/layout/activity_note_list.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_window"
    android:fitsSystemWindows="true">

    <!-- Fragment 容器 -->
    <FrameLayout
        android:id="@+id/fragment_container"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

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
                android:id="@+id/nav_notes_icon"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:src="@drawable/ic_note_tab"
                app:tint="@color/primary" />

            <TextView
                android:id="@+id/nav_notes_label"
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
                android:id="@+id/nav_todo_icon"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:src="@drawable/ic_todo_tab"
                app:tint="@color/text_hint" />

            <TextView
                android:id="@+id/nav_todo_label"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/bottom_nav_todo"
                android:textColor="@color/text_hint"
                android:textSize="@dimen/text_hint" />
        </LinearLayout>
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 9: 提交**

```bash
git add app/src/main/res/values/strings.xml \
       app/src/main/res/drawable/ic_todo_checkbox.xml \
       app/src/main/res/drawable/ic_todo_checkbox_checked.xml \
       app/src/main/res/drawable/ic_clock.xml \
       app/src/main/res/drawable/ic_important.xml \
       app/src/main/res/layout/fragment_note_list.xml \
       app/src/main/res/layout/fragment_todo_list.xml \
       app/src/main/res/layout/item_todo_card.xml \
       app/src/main/res/layout/item_todo_section_header.xml \
       app/src/main/res/layout/layout_quick_add_bar.xml \
       app/src/main/res/layout/activity_note_list.xml
git commit -m "feat(m14a): 资源批量新增 + Fragment 布局拆分"
```

---

### Task 7: NoteListFragment — 搬迁笔记列表逻辑

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListFragment.kt`

**Context:** 将 NoteListActivity 现有的全部笔记列表逻辑（header、搜索、筛选面板、RecyclerView、FAB、reload、overflow 菜单、排序、卡片长按菜单、持久化）整体搬入 Fragment。Fragment 使用 `fragment_note_list.xml` 布局。Activity 侧仅保留底部导航 + Fragment 容器。

**关键改动点：**
- `this` → `requireContext()` / `requireActivity()`
- `setContentView` → `onCreateView` inflate
- `lifecycleScope` 可直接用（Fragment 有 `viewLifecycleOwner.lifecycleScope`，但直接用 Fragment 的 `lifecycleScope` 也可）
- `startActivity` → 直接用（Fragment 继承自 `Fragment`，有 `startActivity` 方法）
- `getSharedPreferences` → `requireContext().getSharedPreferences`
- `layoutInflater` → `layoutInflater`（Fragment 有）
- `getString` → 直接用（Fragment 有）
- `getColor` → `ContextCompat.getColor(requireContext(), ...)`
- `onResume` → `onResume`（Fragment 有）

- [ ] **Step 1: 创建 NoteListFragment**

```kotlin
// app/src/main/java/com/fan/hwnote/app/controller/list/NoteListFragment.kt
package com.fan.hwnote.app.controller.list

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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

class NoteListFragment : Fragment() {

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_note_list, container, false)

        rootLayout = view.findViewById(R.id.root_layout)
        headerTitleArea = view.findViewById(R.id.header_title_area)
        headerTitle = view.findViewById(R.id.header_title)
        headerArrow = view.findViewById(R.id.header_arrow)
        headerSubtitle = view.findViewById(R.id.header_subtitle)
        btnOverflow = view.findViewById(R.id.btn_overflow)
        searchBar = view.findViewById(R.id.search_bar)
        searchInput = view.findViewById(R.id.search_input)
        filterPanel = view.findViewById(R.id.filter_panel)
        recycler = view.findViewById(R.id.recycler_notes)
        emptyState = view.findViewById(R.id.empty_state)
        fab = view.findViewById(R.id.fab_new_note)

        adapter = NoteListAdapter(
            onClick = { note ->
                startActivity(NoteEditorActivity.newIntent(requireContext(), note.id))
            },
            onLongClick = { note, anchor -> showCardMenu(note, anchor) },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        sortBy = loadSort()
        currentFilter = loadFilter()

        headerTitleArea.setOnClickListener { toggleFilterPanel() }
        btnOverflow.setOnClickListener { showOverflowMenu() }
        fab.setOnClickListener {
            startActivity(NoteEditorActivity.newIntent(requireContext(), -1L))
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

        return view
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { updateHeader() }
        reload()
    }

    override fun onDestroyView() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        super.onDestroyView()
    }

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

    private fun renderEmpty(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private suspend fun updateHeader() {
        val f = currentFilter
        val _ensureValid = when (f) {
            is NoteRepository.ListFilter.Folder ->
                if (FolderRepository.get(f.folderId) == null) {
                    currentFilter = NoteRepository.ListFilter.All
                    saveFilter(NoteRepository.ListFilter.All)
                } else Unit
            is NoteRepository.ListFilter.Notebook ->
                if (NotebookRepository.get(f.notebookId) == null) {
                    currentFilter = NoteRepository.ListFilter.All
                    saveFilter(NoteRepository.ListFilter.All)
                } else Unit
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

    private suspend fun applyNotebookBackground(filter: NoteRepository.ListFilter) {
        val ctx = requireContext()
        val bgColor = when (filter) {
            is NoteRepository.ListFilter.Notebook -> {
                val nb = NotebookRepository.get(filter.notebookId)
                if (nb != null) {
                    val c = Color.parseColor(nb.color)
                    Color.argb(25, Color.red(c), Color.green(c), Color.blue(c))
                } else {
                    ContextCompat.getColor(ctx, R.color.bg_window)
                }
            }
            NoteRepository.ListFilter.All,
            NoteRepository.ListFilter.Uncategorized,
            NoteRepository.ListFilter.Favorite,
            NoteRepository.ListFilter.Deleted,
            is NoteRepository.ListFilter.Folder ->
                ContextCompat.getColor(ctx, R.color.bg_window)
        }
        rootLayout.setBackgroundColor(bgColor)
    }

    private fun toggleFilterPanel() {
        filterPanelVisible = !filterPanelVisible
        headerArrow.rotation = if (filterPanelVisible) 180f else 0f
        if (filterPanelVisible) {
            searchBar.visibility = View.GONE
            recycler.visibility = View.GONE
            emptyState.visibility = View.GONE
            fab.visibility = View.GONE
            filterPanel.visibility = View.VISIBLE
            filterPanel.layoutManager = LinearLayoutManager(requireContext())
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
                    startActivity(Intent(requireContext(), FolderManagerActivity::class.java))
                },
                onToggleFolder = { folderId ->
                    if (expandedFolders.contains(folderId)) expandedFolders -= folderId
                    else expandedFolders += folderId
                    rebuildFilterPanel()
                },
            )
        }
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(requireContext(), btnOverflow)
        popup.menuInflater.inflate(R.menu.menu_note_list_overflow, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sort -> { showSortDialog(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showCardMenu(note: Note, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
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
                        requireContext(),
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
                        requireContext(),
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
                        context = requireContext(),
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

    private fun showSortDialog() {
        val sheet = BottomSheetDialog(requireContext())
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
        val prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_SORT, NoteRepository.SortBy.UPDATED_DESC.name)
        return runCatching { NoteRepository.SortBy.valueOf(name!!) }
            .getOrDefault(NoteRepository.SortBy.UPDATED_DESC)
    }

    private fun saveSort(sortBy: NoteRepository.SortBy) {
        requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString(KEY_SORT, sortBy.name).apply()
    }

    private fun loadFilter(): NoteRepository.ListFilter {
        val prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
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
        val editor = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .remove(KEY_FILTER_FOLDER_ID).remove(KEY_FILTER_NOTEBOOK_ID)
        val _save = when (filter) {
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

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListFragment.kt
git commit -m "feat(m14a): NoteListFragment 搬迁笔记列表逻辑"
```

---

### Task 8: NoteListActivity → Fragment 容器 + Tab 切换

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

**Context:** 将 NoteListActivity 从"管理所有笔记列表 UI"重构为"Fragment 容器 + 底部 tab 切换"。所有笔记逻辑已搬入 NoteListFragment（Task 7），此 task 把 Activity 精简到只负责 Fragment 切换 + 底部导航高亮。

- [ ] **Step 1: 重写 NoteListActivity**

用以下内容**完全替换** `NoteListActivity.kt`：

```kotlin
// app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
package com.fan.hwnote.app.controller.list

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fan.hwnote.app.R

class NoteListActivity : AppCompatActivity() {

    private lateinit var navNotesIcon: ImageView
    private lateinit var navNotesLabel: TextView
    private lateinit var navTodoIcon: ImageView
    private lateinit var navTodoLabel: TextView

    private var activeTab: Int = TAB_NOTES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        navNotesIcon = findViewById(R.id.nav_notes_icon)
        navNotesLabel = findViewById(R.id.nav_notes_label)
        navTodoIcon = findViewById(R.id.nav_todo_icon)
        navTodoLabel = findViewById(R.id.nav_todo_label)

        findViewById<android.view.View>(R.id.nav_notes).setOnClickListener { switchTab(TAB_NOTES) }
        findViewById<android.view.View>(R.id.nav_todo).setOnClickListener { switchTab(TAB_TODO) }

        if (savedInstanceState != null) {
            activeTab = savedInstanceState.getInt(KEY_ACTIVE_TAB, loadActiveTab())
        } else {
            activeTab = loadActiveTab()
        }
        switchTab(activeTab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_TAB, activeTab)
    }

    private fun switchTab(tab: Int) {
        if (activeTab == tab && supportFragmentManager.findFragmentById(R.id.fragment_container) != null) {
            return
        }
        activeTab = tab
        saveActiveTab(tab)

        val fragment: Fragment = when (tab) {
            TAB_TODO -> TodoListFragment()
            else -> NoteListFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()

        updateNavHighlight(tab)
    }

    private fun updateNavHighlight(tab: Int) {
        val primary = ContextCompat.getColor(this, R.color.primary)
        val hint = ContextCompat.getColor(this, R.color.text_hint)

        val notesActive = tab == TAB_NOTES
        navNotesIcon.setColorFilter(if (notesActive) primary else hint)
        navNotesLabel.setTextColor(if (notesActive) primary else hint)

        val todoActive = tab == TAB_TODO
        navTodoIcon.setColorFilter(if (todoActive) primary else hint)
        navTodoLabel.setTextColor(if (todoActive) primary else hint)
    }

    private fun loadActiveTab(): Int {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        return prefs.getInt(KEY_ACTIVE_TAB, TAB_NOTES)
    }

    private fun saveActiveTab(tab: Int) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_ACTIVE_TAB, tab).apply()
    }

    companion object {
        private const val PREFS = "hwnote_settings"
        private const val KEY_ACTIVE_TAB = "active_tab"
        const val TAB_NOTES = 0
        const val TAB_TODO = 1
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "refactor(m14a): NoteListActivity 重构为 Fragment 容器 + Tab 切换"
```

---

### Task 9: TodoListAdapter — 分组列表

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/controller/list/TodoListAdapter.kt`

**Context:** TodoListAdapter 使用多 ViewType 实现按时间分组：TYPE_SECTION_HEADER（分组标题）和 TYPE_TODO_ITEM（待办卡片）。分组顺序：已过期 → 今天 → 明天 → 更晚 → 无日期 → 已完成。

- [ ] **Step 1: 创建 TodoListAdapter**

```kotlin
// app/src/main/java/com/fan/hwnote/app/controller/list/TodoListAdapter.kt
package com.fan.hwnote.app.controller.list

import android.graphics.Color
import android.graphics.Paint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.RepeatType
import com.fan.hwnote.app.model.entity.Todo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TodoListAdapter(
    private val onCheckToggle: (Todo) -> Unit,
    private val onClick: (Todo) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Item {
        data class Header(val title: String, val isOverdue: Boolean = false) : Item()
        data class TodoItem(val todo: Todo) : Item()
    }

    private val items = mutableListOf<Item>()

    fun submit(todos: List<Todo>) {
        items.clear()
        items.addAll(groupTodos(todos))
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Header -> TYPE_SECTION_HEADER
        is Item.TodoItem -> TYPE_TODO_ITEM
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION_HEADER -> SectionVH(
                inflater.inflate(R.layout.item_todo_section_header, parent, false),
            )
            else -> TodoVH(inflater.inflate(R.layout.item_todo_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as SectionVH).bind(item)
            is Item.TodoItem -> (holder as TodoVH).bind(item.todo)
        }
    }

    private inner class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.section_title)
        fun bind(header: Item.Header) {
            title.text = header.title
            title.setTextColor(
                if (header.isOverdue) Color.parseColor("#E53935")
                else ContextCompat.getColor(itemView.context, R.color.text_secondary),
            )
        }
    }

    private inner class TodoVH(v: View) : RecyclerView.ViewHolder(v) {
        private val checkbox: ImageView = v.findViewById(R.id.todo_checkbox)
        private val titleTv: TextView = v.findViewById(R.id.todo_title)
        private val subtitle: TextView = v.findViewById(R.id.todo_subtitle)

        fun bind(todo: Todo) {
            // checkbox
            checkbox.setImageResource(
                if (todo.isCompleted) R.drawable.ic_todo_checkbox_checked
                else R.drawable.ic_todo_checkbox,
            )
            checkbox.setOnClickListener { onCheckToggle(todo) }

            // title
            if (todo.isImportant && !todo.isCompleted) {
                val sp = SpannableString("❗${todo.title}")
                sp.setSpan(
                    ForegroundColorSpan(Color.parseColor("#E53935")),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                titleTv.text = sp
            } else {
                titleTv.text = todo.title.ifEmpty {
                    itemView.context.getString(R.string.todo_quick_add_hint)
                }
            }

            // completed style
            if (todo.isCompleted) {
                titleTv.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_hint),
                )
                titleTv.paintFlags = titleTv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                titleTv.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_primary),
                )
                titleTv.paintFlags = titleTv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            // subtitle (time + repeat)
            val parts = mutableListOf<String>()
            if (todo.remindAt > 0) {
                val fmt = SimpleDateFormat("a h:mm", Locale.getDefault())
                parts += fmt.format(todo.remindAt)
            }
            if (todo.repeatType != RepeatType.NONE) {
                val ctx = itemView.context
                parts += when (todo.repeatType) {
                    RepeatType.DAILY -> ctx.getString(R.string.todo_repeat_daily)
                    RepeatType.WEEKLY -> ctx.getString(R.string.todo_repeat_weekly)
                    RepeatType.MONTHLY -> ctx.getString(R.string.todo_repeat_monthly)
                    RepeatType.YEARLY -> ctx.getString(R.string.todo_repeat_yearly)
                    RepeatType.NONE -> ""
                }
            }
            if (parts.isNotEmpty()) {
                subtitle.text = parts.joinToString(" | ")
                subtitle.visibility = View.VISIBLE
                // overdue time = red
                val isOverdue = todo.remindAt > 0 && todo.remindAt < System.currentTimeMillis()
                    && !todo.isCompleted
                subtitle.setTextColor(
                    if (isOverdue) Color.parseColor("#E53935")
                    else ContextCompat.getColor(itemView.context, R.color.text_hint),
                )
            } else {
                subtitle.visibility = View.GONE
            }

            // click
            itemView.setOnClickListener { onClick(todo) }
        }
    }

    companion object {
        private const val TYPE_SECTION_HEADER = 0
        private const val TYPE_TODO_ITEM = 1

        fun groupTodos(todos: List<Todo>): List<Item> {
            val now = System.currentTimeMillis()
            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayStart = todayCal.timeInMillis
            val tomorrowStart = todayStart + 24 * 60 * 60 * 1000L
            val dayAfterTomorrow = tomorrowStart + 24 * 60 * 60 * 1000L

            val overdue = mutableListOf<Todo>()
            val today = mutableListOf<Todo>()
            val tomorrow = mutableListOf<Todo>()
            val later = mutableListOf<Todo>()
            val noDate = mutableListOf<Todo>()
            val completed = mutableListOf<Todo>()

            for (t in todos) {
                if (t.isCompleted) {
                    completed += t
                } else if (t.remindAt == 0L) {
                    noDate += t
                } else if (t.remindAt < todayStart) {
                    overdue += t
                } else if (t.remindAt < tomorrowStart) {
                    today += t
                } else if (t.remindAt < dayAfterTomorrow) {
                    tomorrow += t
                } else {
                    later += t
                }
            }

            val items = mutableListOf<Item>()
            fun addSection(title: String, list: List<Todo>, isOverdue: Boolean = false) {
                if (list.isNotEmpty()) {
                    items += Item.Header(title, isOverdue)
                    for (t in list) items += Item.TodoItem(t)
                }
            }

            addSection("已过期", overdue, isOverdue = true)
            addSection("今天", today)
            addSection("明天", tomorrow)
            addSection("更晚", later)
            addSection("无日期", noDate)
            addSection("已完成", completed)

            return items
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/TodoListAdapter.kt
git commit -m "feat(m14a): 新增 TodoListAdapter 多 ViewType 分组列表"
```

---

### Task 10: TodoFilterPanelAdapter — 待办筛选面板

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/controller/list/TodoFilterPanelAdapter.kt`

**Context:** 复用笔记的 FilterPanelAdapter 模式，但筛选项不同：全部待办 / 未分类 / 最近删除 / 文件夹列表。无"我的收藏"项（待办没有收藏功能）。

- [ ] **Step 1: 创建 TodoFilterPanelAdapter**

```kotlin
// app/src/main/java/com/fan/hwnote/app/controller/list/TodoFilterPanelAdapter.kt
package com.fan.hwnote.app.controller.list

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Folder

class TodoFilterPanelAdapter(
    private val rows: List<Row>,
    private val selected: TodoListFilter,
    private val onFilterPicked: (TodoListFilter) -> Unit,
    private val onManageFolders: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Pseudo(val kind: PseudoKind, val count: Int) : Row()
        object Divider : Row()
        data class SectionHeader(val title: String, val actionLabel: String) : Row()
        data class FolderRow(val folder: Folder, val count: Int) : Row()
    }

    enum class PseudoKind { All, Uncategorized, Deleted }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Pseudo -> 0
        is Row.Divider -> 1
        is Row.SectionHeader -> 2
        is Row.FolderRow -> 3
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
            else -> FolderVH(inflater.inflate(R.layout.item_filter_pseudo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Pseudo -> (holder as PseudoVH).bind(row)
            is Row.Divider -> Unit
            is Row.SectionHeader -> (holder as SectionVH).bind(row)
            is Row.FolderRow -> (holder as FolderVH).bind(row)
        }
    }

    private fun pseudoFilter(kind: PseudoKind): TodoListFilter = when (kind) {
        PseudoKind.All -> TodoListFilter.All
        PseudoKind.Uncategorized -> TodoListFilter.Uncategorized
        PseudoKind.Deleted -> TodoListFilter.Deleted
    }

    private fun isSelected(filter: TodoListFilter): Boolean = selected == filter

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
                PseudoKind.All -> R.drawable.ic_todo_tab
                PseudoKind.Uncategorized -> R.drawable.ic_uncategorized
                PseudoKind.Deleted -> R.drawable.ic_delete
            }
            icon.setImageResource(iconRes)
            label.text = when (row.kind) {
                PseudoKind.All -> ctx.getString(R.string.filter_all_todos)
                PseudoKind.Uncategorized -> ctx.getString(R.string.filter_uncategorized)
                PseudoKind.Deleted -> ctx.getString(R.string.filter_deleted)
            }
            countTv.text = row.count.toString()

            val filter = pseudoFilter(row.kind)
            val sel = isSelected(filter)
            applySelectedState(itemView, bar, label, countTv, sel)
            val tintColor = if (sel) ContextCompat.getColor(ctx, R.color.primary)
                else ContextCompat.getColor(ctx, R.color.text_primary)
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

    private inner class FolderVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: Row.FolderRow) {
            val ctx = itemView.context
            val icon = itemView.findViewById<ImageView>(R.id.icon)
            val label = itemView.findViewById<TextView>(R.id.label)
            val countTv = itemView.findViewById<TextView>(R.id.count)
            val bar = itemView.findViewById<View>(R.id.selected_bar)

            icon.setImageResource(R.drawable.ic_folder)
            label.text = row.folder.name
            countTv.text = row.count.toString()

            val filter = TodoListFilter.ByFolder(row.folder.id)
            val sel = isSelected(filter)
            applySelectedState(itemView, bar, label, countTv, sel)
            val tintColor = if (sel) ContextCompat.getColor(ctx, R.color.primary)
                else ContextCompat.getColor(ctx, R.color.text_primary)
            icon.setColorFilter(tintColor)

            itemView.setOnClickListener { onFilterPicked(filter) }
        }
    }

    companion object {
        private fun dpToPx(ctx: Context, dp: Int): Int =
            (dp * ctx.resources.displayMetrics.density + 0.5f).toInt()
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/TodoFilterPanelAdapter.kt
git commit -m "feat(m14a): 新增 TodoFilterPanelAdapter 待办筛选面板"
```

---

### Task 11: TodoListFragment — 待办列表页

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/controller/list/TodoListFragment.kt`

**Context:** TodoListFragment 承载待办列表的全部 UI 逻辑：header、筛选面板、RecyclerView（TodoListAdapter）、FAB、QuickAddBar、overflow 菜单。布局使用 `fragment_todo_list.xml`。

- [ ] **Step 1: 创建 TodoListFragment**

```kotlin
// app/src/main/java/com/fan/hwnote/app/controller/list/TodoListFragment.kt
package com.fan.hwnote.app.controller.list

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.controller.folder.FolderManagerActivity
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.TodoRepository
import com.fan.hwnote.app.model.entity.Todo
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class TodoListFragment : Fragment() {

    private lateinit var headerTitleArea: View
    private lateinit var headerTitle: TextView
    private lateinit var headerArrow: ImageView
    private lateinit var headerSubtitle: TextView
    private lateinit var btnOverflow: ImageView
    private lateinit var filterPanel: RecyclerView
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var fab: FloatingActionButton

    // QuickAddBar
    private lateinit var quickAddBar: View
    private lateinit var quickAddInput: EditText
    private lateinit var quickAddTime: ImageView
    private lateinit var quickAddImportant: ImageView
    private lateinit var quickAddSave: TextView

    private lateinit var adapter: TodoListAdapter

    private var currentFilter: TodoListFilter = TodoListFilter.All
    private var filterPanelVisible = false
    private var hideCompleted = false

    // QuickAddBar state
    private var quickAddRemindAt = 0L
    private var quickAddIsImportant = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_todo_list, container, false)

        headerTitleArea = view.findViewById(R.id.header_title_area)
        headerTitle = view.findViewById(R.id.header_title)
        headerArrow = view.findViewById(R.id.header_arrow)
        headerSubtitle = view.findViewById(R.id.header_subtitle)
        btnOverflow = view.findViewById(R.id.btn_overflow)
        filterPanel = view.findViewById(R.id.filter_panel)
        recycler = view.findViewById(R.id.recycler_todos)
        emptyState = view.findViewById(R.id.empty_state)
        fab = view.findViewById(R.id.fab_new_todo)

        quickAddBar = view.findViewById(R.id.quick_add_bar)
        quickAddInput = view.findViewById(R.id.quick_add_input)
        quickAddTime = view.findViewById(R.id.quick_add_time)
        quickAddImportant = view.findViewById(R.id.quick_add_important)
        quickAddSave = view.findViewById(R.id.quick_add_save)

        adapter = TodoListAdapter(
            onCheckToggle = { todo ->
                lifecycleScope.launch {
                    if (todo.isCompleted) {
                        TodoRepository.uncompleteTodo(todo.id)
                    } else {
                        TodoRepository.completeTodo(todo.id)
                    }
                    reload()
                }
            },
            onClick = { todo ->
                // M14b: TodoDetailActivity
                Toast.makeText(requireContext(), todo.title, Toast.LENGTH_SHORT).show()
            },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        currentFilter = loadFilter()
        hideCompleted = loadHideCompleted()

        headerTitleArea.setOnClickListener { toggleFilterPanel() }
        btnOverflow.setOnClickListener { showOverflowMenu() }
        fab.setOnClickListener { showQuickAddBar() }

        quickAddTime.setOnClickListener {
            // M14b: DateTimePickerDialog
            Toast.makeText(requireContext(), "时间选择（M14b 实现）", Toast.LENGTH_SHORT).show()
        }
        quickAddImportant.setOnClickListener { toggleQuickAddImportant() }
        quickAddSave.setOnClickListener { saveQuickAdd() }

        return view
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val todos = when (val f = currentFilter) {
                TodoListFilter.All -> TodoRepository.list(hideCompleted = hideCompleted)
                TodoListFilter.Uncategorized -> TodoRepository.listUncategorized(hideCompleted)
                TodoListFilter.Deleted -> TodoRepository.list(includeDeleted = true)
                is TodoListFilter.ByFolder -> TodoRepository.list(
                    folderId = f.folderId, hideCompleted = hideCompleted,
                )
            }
            adapter.submit(todos)
            renderEmpty(todos.isEmpty())
            updateHeader()
        }
    }

    private fun renderEmpty(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private suspend fun updateHeader() {
        val cur = currentFilter
        val title = when (cur) {
            TodoListFilter.All -> getString(R.string.filter_all_todos)
            TodoListFilter.Uncategorized -> getString(R.string.filter_uncategorized)
            TodoListFilter.Deleted -> getString(R.string.filter_deleted)
            is TodoListFilter.ByFolder ->
                FolderRepository.get(cur.folderId)?.name ?: getString(R.string.filter_all_todos)
        }
        headerTitle.text = title

        val count = when (cur) {
            TodoListFilter.All -> TodoRepository.count()
            TodoListFilter.Uncategorized -> TodoRepository.countUncategorized()
            TodoListFilter.Deleted -> TodoRepository.count(includeDeleted = true)
            is TodoListFilter.ByFolder -> TodoRepository.count(folderId = cur.folderId)
        }
        headerSubtitle.text = getString(R.string.todo_count_format, count)
    }

    // -------- 筛选面板 --------

    private fun toggleFilterPanel() {
        filterPanelVisible = !filterPanelVisible
        headerArrow.rotation = if (filterPanelVisible) 180f else 0f
        if (filterPanelVisible) {
            recycler.visibility = View.GONE
            emptyState.visibility = View.GONE
            fab.visibility = View.GONE
            quickAddBar.visibility = View.GONE
            filterPanel.visibility = View.VISIBLE
            filterPanel.layoutManager = LinearLayoutManager(requireContext())
            rebuildFilterPanel()
        } else {
            filterPanel.visibility = View.GONE
            fab.visibility = View.VISIBLE
            reload()
        }
    }

    private fun rebuildFilterPanel() {
        lifecycleScope.launch {
            val rows = mutableListOf<TodoFilterPanelAdapter.Row>()

            val allCount = TodoRepository.count()
            val uncatCount = TodoRepository.countUncategorized()
            val delCount = TodoRepository.count(includeDeleted = true)

            rows += TodoFilterPanelAdapter.Row.Pseudo(TodoFilterPanelAdapter.PseudoKind.All, allCount)
            rows += TodoFilterPanelAdapter.Row.Pseudo(TodoFilterPanelAdapter.PseudoKind.Uncategorized, uncatCount)
            rows += TodoFilterPanelAdapter.Row.Pseudo(TodoFilterPanelAdapter.PseudoKind.Deleted, delCount)
            rows += TodoFilterPanelAdapter.Row.Divider
            rows += TodoFilterPanelAdapter.Row.SectionHeader(
                getString(R.string.filter_section_folders),
                getString(R.string.filter_manage_action),
            )

            val folders = FolderRepository.list()
            for (f in folders) {
                val fCount = TodoRepository.count(folderId = f.id)
                rows += TodoFilterPanelAdapter.Row.FolderRow(f, fCount)
            }

            filterPanel.adapter = TodoFilterPanelAdapter(
                rows = rows,
                selected = currentFilter,
                onFilterPicked = { picked ->
                    currentFilter = picked
                    saveFilter(picked)
                    toggleFilterPanel()
                },
                onManageFolders = {
                    startActivity(Intent(requireContext(), FolderManagerActivity::class.java))
                },
            )
        }
    }

    // -------- Overflow 菜单 --------

    private fun showOverflowMenu() {
        val popup = PopupMenu(requireContext(), btnOverflow)
        popup.menu.add(0, MENU_TOGGLE_COMPLETED, 0,
            if (hideCompleted) R.string.todo_menu_show_completed
            else R.string.todo_menu_hide_completed,
        )
        popup.menu.add(0, MENU_BATCH_DELETE, 1, R.string.todo_menu_batch_delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_TOGGLE_COMPLETED -> {
                    hideCompleted = !hideCompleted
                    saveHideCompleted(hideCompleted)
                    reload()
                    true
                }
                MENU_BATCH_DELETE -> {
                    // M14c: 批量删除模式
                    Toast.makeText(requireContext(), "批量删除（M14c 实现）", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // -------- QuickAddBar --------

    private fun showQuickAddBar() {
        fab.visibility = View.GONE
        quickAddBar.visibility = View.VISIBLE
        quickAddInput.text.clear()
        quickAddRemindAt = 0L
        quickAddIsImportant = false
        updateQuickAddImportantIcon()
        quickAddInput.requestFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.showSoftInput(quickAddInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideQuickAddBar() {
        quickAddBar.visibility = View.GONE
        fab.visibility = View.VISIBLE
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.hideSoftInputFromWindow(quickAddInput.windowToken, 0)
    }

    private fun toggleQuickAddImportant() {
        quickAddIsImportant = !quickAddIsImportant
        updateQuickAddImportantIcon()
    }

    private fun updateQuickAddImportantIcon() {
        val color = if (quickAddIsImportant)
            ContextCompat.getColor(requireContext(), R.color.primary)
        else
            ContextCompat.getColor(requireContext(), R.color.text_hint)
        quickAddImportant.setColorFilter(color)
    }

    private fun saveQuickAdd() {
        val title = quickAddInput.text.toString().trim()
        if (title.isEmpty()) return

        val folderId = when (val f = currentFilter) {
            is TodoListFilter.ByFolder -> f.folderId
            TodoListFilter.All, TodoListFilter.Uncategorized, TodoListFilter.Deleted -> null
        }

        lifecycleScope.launch {
            TodoRepository.insert(Todo.new().copy(
                title = title,
                remindAt = quickAddRemindAt,
                isImportant = quickAddIsImportant,
                folderId = folderId,
            ))
            hideQuickAddBar()
            reload()
        }
    }

    // -------- 持久化 --------

    private fun loadFilter(): TodoListFilter {
        val prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val type = prefs.getString(KEY_TODO_FILTER_TYPE, "ALL") ?: "ALL"
        return when (type) {
            "ALL" -> TodoListFilter.All
            "UNCATEGORIZED" -> TodoListFilter.Uncategorized
            "DELETED" -> TodoListFilter.Deleted
            "FOLDER" -> {
                val id = prefs.getLong(KEY_TODO_FILTER_FOLDER_ID, -1L)
                if (id > 0) TodoListFilter.ByFolder(id) else TodoListFilter.All
            }
            else -> TodoListFilter.All
        }
    }

    private fun saveFilter(filter: TodoListFilter) {
        val editor = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .remove(KEY_TODO_FILTER_FOLDER_ID)
        val _save = when (filter) {
            TodoListFilter.All -> editor.putString(KEY_TODO_FILTER_TYPE, "ALL")
            TodoListFilter.Uncategorized -> editor.putString(KEY_TODO_FILTER_TYPE, "UNCATEGORIZED")
            TodoListFilter.Deleted -> editor.putString(KEY_TODO_FILTER_TYPE, "DELETED")
            is TodoListFilter.ByFolder ->
                editor.putString(KEY_TODO_FILTER_TYPE, "FOLDER")
                    .putLong(KEY_TODO_FILTER_FOLDER_ID, filter.folderId)
        }
        editor.apply()
    }

    private fun loadHideCompleted(): Boolean {
        val prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_COMPLETED, false)
    }

    private fun saveHideCompleted(hide: Boolean) {
        requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_HIDE_COMPLETED, hide).apply()
    }

    companion object {
        private const val PREFS = "hwnote_settings"
        private const val KEY_TODO_FILTER_TYPE = "todo_filter_type"
        private const val KEY_TODO_FILTER_FOLDER_ID = "todo_filter_folder_id"
        private const val KEY_HIDE_COMPLETED = "hide_completed_todos"
        private const val MENU_TOGGLE_COMPLETED = 1001
        private const val MENU_BATCH_DELETE = 1002
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/TodoListFragment.kt
git commit -m "feat(m14a): 新增 TodoListFragment 待办列表页"
```

---

### Task 12: AndroidManifest + 构建验证 + 全量测试

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: AndroidManifest 声明 TodoDetailActivity 占位**

在 `AndroidManifest.xml` 的 `<application>` 内（`</provider>` 闭标签之后，`</application>` 之前）追加：

```xml
<!-- M14b: 待办详情页（占位声明，M14b 实现） -->
<activity
    android:name=".controller.todo.TodoDetailActivity"
    android:exported="false"
    android:parentActivityName=".controller.list.NoteListActivity" />
```

注意：TodoDetailActivity 类还不存在，但 Manifest 声明不影响编译（除非启动它）。Task 9 的 TodoListAdapter 点击跳转目前是 Toast 占位，不会触发。

**但如果构建报错**（Manifest 引用不存在的 Activity 可能在 lint 检查时报 warning 但不应阻止编译），可改为注释掉此声明，等 M14b 再加。

实际上，更安全的做法是**不在此 task 声明**，等 M14b 创建 TodoDetailActivity 时再添加 Manifest。所以此步骤改为：**仅构建验证，不改 Manifest**。

- [ ] **Step 2: 构建验证**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:assembleDebug -q
```

预期：BUILD SUCCESSFUL

- [ ] **Step 3: 全量单测**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && \
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
./gradlew :app:test -q
```

预期：所有测试 PASSED（158 原有 + 20 新增 = ~178 tests）

如果编译或测试失败，修复后再提交。

- [ ] **Step 4: 提交（如果有修复）**

```bash
# 仅在有修复时提交
git add -A  # 仅在确认无敏感文件时
git commit -m "fix(m14a): 修复构建/测试问题"
```

---

### Task 13: STATUS 更新 + 计划归档

**Files:**
- Modify: `docs/superpowers/STATUS.md`
- Move: `docs/superpowers/plans/2026-06-05-hwnote-m14a-todo-data-fragment-list.md` → `docs/superpowers/plans/archived/`

- [ ] **Step 1: 更新 STATUS.md**

在 STATUS.md 的里程碑表中追加 M14a 行：

```markdown
| M14a | 待办数据层+Fragment 重构+列表页 | N | M | DB v5(todos 表) + Todo 实体/RepeatType + TodoRepository(CRUD+重复推进+软删除) + NoteListActivity→Fragment 容器(NoteListFragment+TodoListFragment) + TodoListAdapter(分组) + QuickAddBar + TodoFilterPanelAdapter |
```

更新 HEAD commit hash、总测试数。

- [ ] **Step 2: 归档计划**

```bash
mv docs/superpowers/plans/2026-06-05-hwnote-m14a-todo-data-fragment-list.md \
   docs/superpowers/plans/archived/
```

- [ ] **Step 3: 更新 memory**

更新 `project_hwnote_context.md` 中的 "Current state" 描述，反映 M14a 完成。

- [ ] **Step 4: 提交**

```bash
git add docs/superpowers/STATUS.md \
       docs/superpowers/plans/archived/2026-06-05-hwnote-m14a-todo-data-fragment-list.md
git commit -m "docs(m14a): STATUS 更新 + 计划归档"
```

---

## 自检清单

| 检查项 | 状态 |
|--------|------|
| Spec §2 所有字段都在 Todo.kt + DB schema 中 | ✅ |
| RepeatType.fromValue 有单测 | ✅ Task 1 |
| DB v5 迁移有 onCreate + onUpgrade 两条路径 | ✅ Task 2 |
| TodoRepository CRUD + completeTodo + purgeExpired 全部有单测 | ✅ Task 3+4 |
| TodoListFilter 4 种子类对应 spec §3.4 | ✅ Task 5 |
| Fragment 重构：NoteListActivity → NoteListFragment + container | ✅ Task 7+8 |
| TodoListAdapter 6 个分组按 spec §3.2 排序 | ✅ Task 9 |
| QuickAddBar 有 FAB toggle + 保存 + 时间/重要按钮 | ✅ Task 11 |
| TodoFilterPanelAdapter 复用 item_filter_pseudo.xml 布局 | ✅ Task 10 |
| App.kt 初始化 TodoRepository + purgeExpired | ✅ Task 5 |
| 所有 when 表达式显式列举子类 | ✅ 无 else |
| 总任务数 ≤ 15 | ✅ 13 tasks |
| M14b 依赖项（TodoDetailActivity / DateTimePickerDialog）均为 Toast 占位 | ✅ |
