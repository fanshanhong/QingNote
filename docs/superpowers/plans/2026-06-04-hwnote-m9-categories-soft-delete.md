# M9 分类 + 软删除 + metadata strip 实施计划（2026-06-04）

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development —— per-task 串行 implementer + spec reviewer + code quality reviewer。

**Goal:** 按 PRD §13 把分类系统、软删除（30 天最近删除）、编辑器 metadata strip 落地，并完成 DB v1 → v2 平滑迁移。

**Architecture:**
- **数据层增量**：DB v2 给 `notes` 加 `category_id` / `deleted_at` 两列 + 新表 `categories(id, name, color, order_index)`；通过 `onUpgrade` ALTER 平滑升级旧装机；新加 `CategoryRepository` 作分类 CRUD 门面；`NoteRepository.list()` 拓展 `ListFilter` 参数 + 软删除 API（`softDelete` / `restore` / `deletePermanently` / `purgeExpired`）。
- **列表页**：Toolbar 中央 chip 显示当前 filter 名，点击弹 `FilterPickerBottomSheet`（4 内置项 + 用户分类 + 管理入口）；filter 持久化 SharedPreferences；长按菜单按 filter 切换（最近删除时菜单为"恢复 / 彻底删"）；删除走底部 `DeleteConfirmBottomSheet` 二次确认；`CategoryManagerBottomSheet` 提供分类 CRUD + 颜色选择 + ItemTouchHelper 长按拖动排序。
- **编辑器**：标题下加一行 metadata strip "X 前 · 分类名"，分类区可点击弹 `CategoryPickerBottomSheet`。
- **启动清理**：App.onCreate 触发协程 `purgeExpired(now, ttlMs = 30天)` 删除超期软删笔记的 DB 行 + 本地目录。

**Tech Stack:** 沿用既有 —— Kotlin / SQLiteOpenHelper / Material BottomSheetDialog + RecyclerView + ItemTouchHelper。零新增第三方依赖。

**预估：** 11 任务，1-2 天工作量；最终 commit `docs(m9): 标记 M9 完成` 收尾。

---

## 边界（不动的东西）

- HandwritingOverlayView / BrushPainter / EditorPresenter 富文本逻辑 —— 零改动
- 图片块 / 清单块 / 手写 / 工具栏 —— 零改动
- NoteJson / Block sealed class —— 零改动（content_json 结构不变）
- M8 之前 67 自动化单测 —— 全部维持 PASS；本里程碑新增 5-7 单测覆盖 DB migration + CategoryRepository + 软删除
- 排序 SortBy（UPDATED_DESC / CREATED_DESC）—— 不动
- 文本搜索 LIKE —— 不动（但 list() 内 WHERE 子句会叠加 deleted_at = 0 与 filter 条件）

---

## DB v2 升级语义（关键不可错）

- `DB_VERSION = 1` → `2`
- `onUpgrade(db, 1, 2)`：执行 3 条 SQL（ALTER notes ADD category_id / ALTER notes ADD deleted_at / CREATE TABLE categories）+ 2 条 CREATE INDEX（idx_notes_category / idx_notes_deleted）
- 旧 notes 行的 `category_id` 为 NULL（= 未分类）；`deleted_at` = 0（= 未删除，DEFAULT 0 兜底）
- `onCreate` 同步加上 v2 全部 schema（新装直接是 v2）

---

## ListFilter sealed class（位于 NoteRepository.kt）

```kotlin
sealed class ListFilter {
    object All : ListFilter()              // 全部（deleted_at = 0）
    object Uncategorized : ListFilter()    // 未分类（deleted_at = 0 AND category_id IS NULL）
    object Favorite : ListFilter()         // 我的收藏（deleted_at = 0 AND is_favorite = 1）
    object Deleted : ListFilter()          // 最近删除（deleted_at > 0）
    data class Category(val id: Long) : ListFilter()  // 用户分类（deleted_at = 0 AND category_id = id）
}
```

`list(filter, sortBy, query)` 把 filter 转为 SQL `WHERE` 子句（拼到 query LIKE 之后用 AND 连接）。

---

## Task 1 — DB v2 迁移（NoteDbHelper + onUpgrade 单测）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/db/NoteDbHelper.kt`
- Modify: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/db/NoteDbHelperTest.kt`

**改动：** bump `DB_VERSION` 到 2；`onCreate` 加 categories 表 + 2 新 INDEX + notes 含 2 新列；`onUpgrade` 加 v1→v2 路径（ALTER + CREATE TABLE + CREATE INDEX）。

**Diff — NoteDbHelper.kt 全文重写：**

```kotlin
package com.fan.hwnote.app.model.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite 表结构见 PRD §6.1 + §13.2。
 * v1 → v2（2026-06-04）：notes 加 category_id / deleted_at；新增 categories 表。
 */
class NoteDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_NOTES_V2)
        db.execSQL(SQL_INDEX_UPDATED)
        db.execSQL(SQL_INDEX_FAVORITE)
        db.execSQL(SQL_INDEX_CATEGORY)
        db.execSQL(SQL_INDEX_DELETED)
        db.execSQL(SQL_CREATE_CATEGORIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE notes ADD COLUMN category_id INTEGER")
            db.execSQL("ALTER TABLE notes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL(SQL_CREATE_CATEGORIES)
            db.execSQL(SQL_INDEX_CATEGORY)
            db.execSQL(SQL_INDEX_DELETED)
        }
    }

    companion object {
        const val DB_NAME = "hwnote.db"
        const val DB_VERSION = 2

        private const val SQL_CREATE_NOTES_V2 = """
            CREATE TABLE notes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL DEFAULT '',
              plain_text TEXT NOT NULL DEFAULT '',
              content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
              is_favorite INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              category_id INTEGER,
              deleted_at INTEGER NOT NULL DEFAULT 0
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

        private const val SQL_INDEX_UPDATED =
            "CREATE INDEX idx_notes_updated_at ON notes(updated_at DESC)"
        private const val SQL_INDEX_FAVORITE =
            "CREATE INDEX idx_notes_favorite ON notes(is_favorite)"
        private const val SQL_INDEX_CATEGORY =
            "CREATE INDEX idx_notes_category ON notes(category_id)"
        private const val SQL_INDEX_DELETED =
            "CREATE INDEX idx_notes_deleted ON notes(deleted_at)"
    }
}
```

**新增单测（NoteDbHelperTest.kt 末尾追加）：**

```kotlin
@Test
fun `onCreate v2 has new columns and tables`() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    ctx.deleteDatabase(NoteDbHelper.DB_NAME)
    val db = NoteDbHelper(ctx).writableDatabase
    // notes 必须含 category_id / deleted_at 两列
    val cursor = db.rawQuery("PRAGMA table_info(notes)", null)
    val cols = mutableSetOf<String>()
    cursor.use { while (it.moveToNext()) cols += it.getString(1) }
    assertTrue(cols.contains("category_id"))
    assertTrue(cols.contains("deleted_at"))
    // categories 表必须存在
    val tableCheck = db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='categories'", null,
    )
    tableCheck.use { assertTrue(it.moveToFirst()) }
    db.close()
}

@Test
fun `onUpgrade v1 to v2 alters notes and creates categories`() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    ctx.deleteDatabase(NoteDbHelper.DB_NAME)
    // 手工建 v1 schema 模拟旧装机
    val v1Helper = object : SQLiteOpenHelper(ctx, NoteDbHelper.DB_NAME, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '',
                plain_text TEXT NOT NULL DEFAULT '',
                content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
                is_favorite INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""")
            db.execSQL("INSERT INTO notes(title, created_at, updated_at) VALUES('old', 1, 1)")
        }
        override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
    }
    v1Helper.writableDatabase.close()
    // 用 v2 helper 触发 onUpgrade
    val v2Db = NoteDbHelper(ctx).writableDatabase
    val cursor = v2Db.rawQuery("SELECT title, category_id, deleted_at FROM notes WHERE title='old'", null)
    cursor.use {
        assertTrue(it.moveToFirst())
        assertEquals("old", it.getString(0))
        assertTrue(it.isNull(1))            // category_id NULL
        assertEquals(0L, it.getLong(2))     // deleted_at 默认 0
    }
    v2Db.close()
}
```

**验：** `./gradlew :app:testDebugUnitTest --tests "*NoteDbHelperTest*"` 全 PASS。

**Commit:** `feat(m9): DB v2 — notes 加 category_id/deleted_at + 新建 categories 表`

---

## Task 2 — Category entity + CategoryRepository CRUD（含单测）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Category.kt`
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/CategoryRepository.kt`
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/CategoryRepositoryTest.kt`

**Category 实体：**

```kotlin
package com.fan.hwnote.app.model.entity

data class Category(
    val id: Long = 0L,
    val name: String,
    val color: String,            // hex "#FDD835"
    val orderIndex: Int = 0,
)
```

**CategoryRepository：** 接 `NoteDbHelper`（复用 `NoteRepository.dbHelper` 还是自有？为了避免破坏 NoteRepository object 私有性，CategoryRepository 也做 object，自己持 helper。`init` 在 App.onCreate 调用，紧跟在 `NoteRepository.init` 之后）。

```kotlin
package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import com.fan.hwnote.app.model.db.NoteDbHelper
import com.fan.hwnote.app.model.entity.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CategoryRepository {

    private lateinit var dbHelper: NoteDbHelper

    fun init(context: Context) {
        dbHelper = NoteDbHelper(context.applicationContext)
    }

    suspend fun list(): List<Category> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Category>()
        val cursor = dbHelper.readableDatabase.query(
            "categories", null, null, null, null, null, "order_index ASC, id ASC",
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                out += Category(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    color = c.getString(c.getColumnIndexOrThrow("color")),
                    orderIndex = c.getInt(c.getColumnIndexOrThrow("order_index")),
                )
            }
        }
        out
    }

    suspend fun get(id: Long): Category? = withContext(Dispatchers.IO) {
        val cursor = dbHelper.readableDatabase.query(
            "categories", null, "id = ?", arrayOf(id.toString()), null, null, null,
        )
        cursor.use { c ->
            if (!c.moveToFirst()) return@use null
            Category(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                name = c.getString(c.getColumnIndexOrThrow("name")),
                color = c.getString(c.getColumnIndexOrThrow("color")),
                orderIndex = c.getInt(c.getColumnIndexOrThrow("order_index")),
            )
        }
    }

    /** 新建分类。order_index = 当前 MAX + 1（新分类追加到末尾）。返回新生成 id。 */
    suspend fun insert(name: String, color: String): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val maxOrder = db.rawQuery("SELECT COALESCE(MAX(order_index), -1) FROM categories", null)
            .use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }
        val cv = ContentValues().apply {
            put("name", name)
            put("color", color)
            put("order_index", maxOrder + 1)
        }
        db.insert("categories", null, cv)
    }

    suspend fun update(id: Long, name: String, color: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("name", name)
            put("color", color)
        }
        dbHelper.writableDatabase.update("categories", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    /** 删除分类。该分类下的 notes 自动 SET category_id = NULL（手动 UPDATE 实现，因为 SQLite 没开 FK 级联）。 */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE notes SET category_id = NULL WHERE category_id = ?", arrayOf<Any>(id))
            db.delete("categories", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 批量持久化排序：传 ordered id 列表，按位置写 order_index。 */
    suspend fun reorder(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { idx, id ->
                val cv = ContentValues().apply { put("order_index", idx) }
                db.update("categories", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
```

**单测（CategoryRepositoryTest.kt）：**

```kotlin
package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        CategoryRepository.init(ctx)
        NoteRepository.init(ctx)
    }

    @Test
    fun `insert assigns increasing order_index`() = runBlocking {
        val a = CategoryRepository.insert("工作", "#FDD835")
        val b = CategoryRepository.insert("个人", "#43A047")
        val list = CategoryRepository.list()
        assertEquals(listOf(a, b), list.map { it.id })
        assertEquals(0, list[0].orderIndex)
        assertEquals(1, list[1].orderIndex)
    }

    @Test
    fun `reorder writes new order_index by position`() = runBlocking {
        val a = CategoryRepository.insert("A", "#000000")
        val b = CategoryRepository.insert("B", "#000000")
        val c = CategoryRepository.insert("C", "#000000")
        CategoryRepository.reorder(listOf(c, a, b))
        val list = CategoryRepository.list()
        assertEquals(listOf(c, a, b), list.map { it.id })
    }

    @Test
    fun `delete sets associated notes category_id to NULL`() = runBlocking {
        val catId = CategoryRepository.insert("Temp", "#000000")
        val noteId = NoteRepository.save(
            com.fan.hwnote.app.model.entity.Note.new()
                .copy(title = "N1", categoryId = catId)
        )
        CategoryRepository.delete(catId)
        val n = NoteRepository.get(noteId)
        assertNull(n?.categoryId)
    }

    @Test
    fun `update changes name and color`() = runBlocking {
        val id = CategoryRepository.insert("Old", "#000000")
        CategoryRepository.update(id, "New", "#FFFFFF")
        val c = CategoryRepository.get(id)!!
        assertEquals("New", c.name)
        assertEquals("#FFFFFF", c.color)
    }
}
```

**注意：** 第三个测试依赖 Task 3 的 `Note.categoryId` 字段 + `NoteRepository.save` / `get` 写读 category_id 的能力 → Task 3 完成后此测试才会通过。Task 2 实施时先跳过 `delete sets associated notes...` 单测（标 @Ignore），Task 3 完成后再开启并补 commit。

**Commit:** `feat(m9): Category 实体 + CategoryRepository CRUD（含 reorder 事务）`

---

## Task 3 — Note 增字段 + NoteRepository read/write 适配

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Note.kt`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`
- Modify: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositorySaveGetTest.kt`
- Modify: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/CategoryRepositoryTest.kt`（解除 Task 2 标记的 @Ignore）

**Note 增 2 字段：**

```kotlin
data class Note(
    val id: Long = 0L,
    val title: String = "",
    val plainText: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val content: NoteContent = NoteContent.empty(),
    val categoryId: Long? = null,     // null = 未分类
    val deletedAt: Long = 0L,         // 0 = 未删除；正数 = 删除时间戳
) { ... }
```

**NoteRepository.cursorToNote 读 2 新列 + save 写 2 新列：**

```kotlin
suspend fun save(note: Note): Long = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val cv = ContentValues().apply {
        put("title", note.title)
        put("plain_text", note.content.toPlainText())
        put("content_json", NoteJson.toJson(note.content))
        put("is_favorite", if (note.isFavorite) 1 else 0)
        put("updated_at", now)
        if (note.categoryId == null) putNull("category_id") else put("category_id", note.categoryId)
        put("deleted_at", note.deletedAt)
    }
    // ... rest unchanged
}

private fun cursorToNote(c: Cursor): Note {
    val contentJson = c.getString(c.getColumnIndexOrThrow("content_json")) ?: ""
    val catIdx = c.getColumnIndexOrThrow("category_id")
    return Note(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
        plainText = c.getString(c.getColumnIndexOrThrow("plain_text")) ?: "",
        isFavorite = c.getInt(c.getColumnIndexOrThrow("is_favorite")) == 1,
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        content = NoteJson.fromJson(contentJson),
        categoryId = if (c.isNull(catIdx)) null else c.getLong(catIdx),
        deletedAt = c.getLong(c.getColumnIndexOrThrow("deleted_at")),
    )
}
```

**新增 NoteRepositorySaveGetTest 用例：**

```kotlin
@Test
fun `save and get round-trip preserves categoryId and deletedAt`() = runBlocking {
    val id = NoteRepository.save(
        Note.new().copy(title = "X", categoryId = 7L, deletedAt = 123456L)
    )
    val n = NoteRepository.get(id)!!
    assertEquals(7L, n.categoryId)
    assertEquals(123456L, n.deletedAt)
}

@Test
fun `save with null categoryId persists as NULL`() = runBlocking {
    val id = NoteRepository.save(Note.new().copy(title = "Y", categoryId = null))
    val n = NoteRepository.get(id)!!
    assertNull(n.categoryId)
}
```

**解除 Task 2 中 `delete sets associated notes category_id to NULL` 的 @Ignore 标记。**

**验：** `./gradlew :app:testDebugUnitTest --tests "*NoteRepository*" --tests "*CategoryRepository*"` 全 PASS。

**Commit:** `feat(m9): Note 增 categoryId/deletedAt + Repository 读写适配`

---

## Task 4 — NoteRepository 软删除 API + purgeExpired

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`
- Modify: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryDeleteFavoriteTest.kt`（已有此文件 → 加新测）

**改动 NoteRepository.delete 语义 + 加 3 新 API：**

```kotlin
/** 软删除：标记 deleted_at = now。文件不动（restore 后还要用）。 */
suspend fun softDelete(id: Long) = withContext(Dispatchers.IO) {
    val cv = ContentValues().apply { put("deleted_at", System.currentTimeMillis()) }
    dbHelper.writableDatabase.update("notes", cv, "id = ?", arrayOf(id.toString()))
    Unit
}

/** 恢复：deleted_at = 0。 */
suspend fun restore(id: Long) = withContext(Dispatchers.IO) {
    val cv = ContentValues().apply { put("deleted_at", 0L) }
    dbHelper.writableDatabase.update("notes", cv, "id = ?", arrayOf(id.toString()))
    Unit
}

/** 彻底删除：DELETE 行 + 删本地文件目录。 */
suspend fun deletePermanently(id: Long) = withContext(Dispatchers.IO) {
    dbHelper.writableDatabase.delete("notes", "id = ?", arrayOf(id.toString()))
    fileStorage.deleteNoteDir(id)
}

/** 清理超 ttlMs 的软删笔记。返回清理的笔记数。 */
suspend fun purgeExpired(
    now: Long = System.currentTimeMillis(),
    ttlMs: Long = 30L * 24 * 60 * 60 * 1000,
): Int = withContext(Dispatchers.IO) {
    val cutoff = now - ttlMs
    val db = dbHelper.writableDatabase
    val ids = mutableListOf<Long>()
    db.query(
        "notes", arrayOf("id"),
        "deleted_at > 0 AND deleted_at < ?", arrayOf(cutoff.toString()),
        null, null, null,
    ).use { c -> while (c.moveToNext()) ids += c.getLong(0) }
    for (id in ids) {
        db.delete("notes", "id = ?", arrayOf(id.toString()))
        fileStorage.deleteNoteDir(id)
    }
    ids.size
}
```

**删 `delete(id)` 旧方法。** 调用方（NoteListActivity）会在 Task 9 切到新 API。

**新增单测（NoteRepositoryDeleteFavoriteTest 末尾追加）：**

```kotlin
@Test
fun `softDelete marks deleted_at and excludes from default list`() = runBlocking {
    val a = NoteRepository.save(Note.new().copy(title = "A"))
    val b = NoteRepository.save(Note.new().copy(title = "B"))
    NoteRepository.softDelete(a)
    val visible = NoteRepository.list()  // Task 5 后 list() 默认过滤 deleted_at = 0
    assertEquals(listOf(b), visible.map { it.id })
}

@Test
fun `restore brings note back to default list`() = runBlocking {
    val id = NoteRepository.save(Note.new().copy(title = "X"))
    NoteRepository.softDelete(id)
    NoteRepository.restore(id)
    val n = NoteRepository.get(id)!!
    assertEquals(0L, n.deletedAt)
}

@Test
fun `deletePermanently removes row and disk dir`() = runBlocking {
    val id = NoteRepository.save(Note.new().copy(title = "X"))
    val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val dir = java.io.File(ctx.filesDir, "notes/$id").apply { mkdirs() }
    java.io.File(dir, "marker.txt").writeText("x")
    NoteRepository.deletePermanently(id)
    assertNull(NoteRepository.get(id))
    assertEquals(false, dir.exists())
}

@Test
fun `purgeExpired deletes rows older than ttl and keeps fresh`() = runBlocking {
    val now = 1_000_000L
    val ttl = 30L * 24 * 60 * 60 * 1000
    val oldId = NoteRepository.save(Note.new().copy(title = "old"))
    val freshId = NoteRepository.save(Note.new().copy(title = "fresh"))
    // 直接改库：oldId 的 deleted_at 设为 now - ttl - 1（已超期）；freshId 设为 now - 1（未超期）
    val db = com.fan.hwnote.app.model.db.NoteDbHelper(
        androidx.test.core.app.ApplicationProvider.getApplicationContext()
    ).writableDatabase
    db.execSQL("UPDATE notes SET deleted_at = ? WHERE id = ?", arrayOf<Any>(now - ttl - 1, oldId))
    db.execSQL("UPDATE notes SET deleted_at = ? WHERE id = ?", arrayOf<Any>(now - 1, freshId))
    val purged = NoteRepository.purgeExpired(now = now, ttlMs = ttl)
    assertEquals(1, purged)
    assertNull(NoteRepository.get(oldId))
    assertEquals(now - 1, NoteRepository.get(freshId)!!.deletedAt)
}
```

**注意：** `softDelete marks ... and excludes from default list` 这条测试依赖 Task 5 的 list 过滤 → Task 5 完成后才会通过。Task 4 实施时此测试标 @Ignore，Task 5 完成后解除。

**验：** `./gradlew :app:testDebugUnitTest --tests "*NoteRepository*"` 除 @Ignore 外全 PASS。

**Commit:** `feat(m9): NoteRepository 软删除 API + purgeExpired（30 天过期清理）`

---

## Task 5 — NoteRepository.list 加 ListFilter 参数

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`
- Modify: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryListTest.kt`

**Diff — 加 ListFilter sealed class + list 新签名：**

```kotlin
sealed class ListFilter {
    object All : ListFilter()
    object Uncategorized : ListFilter()
    object Favorite : ListFilter()
    object Deleted : ListFilter()
    data class Category(val id: Long) : ListFilter()
}

suspend fun list(
    filter: ListFilter = ListFilter.All,
    sortBy: SortBy = SortBy.UPDATED_DESC,
    query: String? = null,
): List<Note> = withContext(Dispatchers.IO) {
    val orderBy = when (sortBy) {
        SortBy.UPDATED_DESC -> "updated_at DESC"
        SortBy.CREATED_DESC -> "created_at DESC"
    }
    val where = mutableListOf<String>()
    val args = mutableListOf<String>()
    when (filter) {
        ListFilter.All -> where += "deleted_at = 0"
        ListFilter.Uncategorized -> { where += "deleted_at = 0"; where += "category_id IS NULL" }
        ListFilter.Favorite -> { where += "deleted_at = 0"; where += "is_favorite = 1" }
        ListFilter.Deleted -> where += "deleted_at > 0"
        is ListFilter.Category -> {
            where += "deleted_at = 0"
            where += "category_id = ?"; args += filter.id.toString()
        }
    }
    if (!query.isNullOrEmpty()) {
        val like = "%$query%"
        where += "(title LIKE ? OR plain_text LIKE ?)"
        args += like; args += like
    }
    val selection = where.joinToString(" AND ")
    val out = mutableListOf<Note>()
    val cursor = dbHelper.readableDatabase.query(
        "notes", null, selection, args.toTypedArray(), null, null, orderBy,
    )
    cursor.use { c -> while (c.moveToNext()) out += cursorToNote(c) }
    out
}
```

**已有 NoteRepositoryListTest 现有 5 项测试用 `list()` 默认参数 —— `ListFilter.All` 默认过滤 `deleted_at = 0`；现有 seed 数据 deleted_at 默认 0 → 现有测试仍通过，无需改动。**

**新增 NoteRepositoryListTest 用例：**

```kotlin
@Test
fun `filter Uncategorized excludes notes with categoryId`() = runBlocking {
    val a = NoteRepository.save(Note.new().copy(title = "A", categoryId = null))
    val b = NoteRepository.save(Note.new().copy(title = "B", categoryId = 1L))
    val list = NoteRepository.list(filter = NoteRepository.ListFilter.Uncategorized)
    assertEquals(listOf(a), list.map { it.id })
}

@Test
fun `filter Favorite includes only is_favorite notes`() = runBlocking {
    val a = NoteRepository.save(Note.new().copy(title = "A", isFavorite = false))
    val b = NoteRepository.save(Note.new().copy(title = "B", isFavorite = true))
    val list = NoteRepository.list(filter = NoteRepository.ListFilter.Favorite)
    assertEquals(listOf(b), list.map { it.id })
}

@Test
fun `filter Deleted shows only soft-deleted notes`() = runBlocking {
    val a = NoteRepository.save(Note.new().copy(title = "A"))
    val b = NoteRepository.save(Note.new().copy(title = "B"))
    NoteRepository.softDelete(a)
    val list = NoteRepository.list(filter = NoteRepository.ListFilter.Deleted)
    assertEquals(listOf(a), list.map { it.id })
}

@Test
fun `filter Category matches categoryId`() = runBlocking {
    val a = NoteRepository.save(Note.new().copy(title = "A", categoryId = 1L))
    val b = NoteRepository.save(Note.new().copy(title = "B", categoryId = 2L))
    val list = NoteRepository.list(filter = NoteRepository.ListFilter.Category(1L))
    assertEquals(listOf(a), list.map { it.id })
}
```

**解除 Task 4 中 `softDelete marks ... excludes from default list` 的 @Ignore 标记。**

**验：** `./gradlew :app:testDebugUnitTest --tests "*NoteRepository*"` 全 PASS（67 + 6 = 73）。

**Commit:** `feat(m9): NoteRepository.list 支持 ListFilter（All/Uncategorized/Favorite/Deleted/Category）`

---

## Task 6 — App.onCreate 启动清理 30 天过期

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/App.kt`

**改动：** 在 `NoteRepository.init` 之后再调 `CategoryRepository.init`；额外 fire-and-forget 一个 `GlobalScope.launch(Dispatchers.IO) { NoteRepository.purgeExpired() }`（App 单例，无生命周期问题）。

**Diff：**

```kotlin
override fun onCreate() {
    super.onCreate()
    com.fan.hwnote.app.model.NoteRepository.init(this)
    com.fan.hwnote.app.model.CategoryRepository.init(this)
    // 启动清理：30 天过期软删笔记
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(Dispatchers.IO) {
        runCatching { NoteRepository.purgeExpired() }
    }
}
```

**注意：** 若 App.kt 没有现成 init 代码，先 Read 一次确认；可能要补 import。`@OptIn(DelicateCoroutinesApi::class)` 是因为 GlobalScope 标 Delicate（这里无生命周期边界，合理使用）。

**验：** `./gradlew :app:assembleDebug` BUILD SUCCESSFUL。

**Commit:** `feat(m9): App.onCreate 启动协程清理 30 天过期软删笔记`

---

## Task 7 — Toolbar chip + FilterPickerBottomSheet（filter 持久化）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/layout/activity_note_list.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/dialog_filter_picker.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/item_filter_row.xml`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`

**改动 activity_note_list.xml：** 把 Toolbar `app:title` 删掉，改在 Toolbar 内放一个居中 `LinearLayout`（点击区）含 chip 文案 + `▼` 图标。

```xml
<androidx.appcompat.widget.Toolbar
    android:id="@+id/toolbar"
    android:layout_width="match_parent"
    android:layout_height="?attr/actionBarSize"
    android:background="@color/primary">

    <LinearLayout
        android:id="@+id/filter_chip"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingHorizontal="@dimen/spacing_m"
        android:paddingVertical="@dimen/spacing_xs">

        <TextView
            android:id="@+id/filter_chip_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/filter_all"
            android:textColor="@color/white"
            android:textSize="@dimen/text_title"
            android:textStyle="bold"
            tools:text="全部" />

        <ImageView
            android:layout_width="16dp"
            android:layout_height="16dp"
            android:layout_marginStart="@dimen/spacing_xs"
            android:src="@drawable/ic_arrow_drop_down"
            app:tint="@color/white" />
    </LinearLayout>
</androidx.appcompat.widget.Toolbar>
```

**注意：** `ic_arrow_drop_down` 复用 Material vector "ic_arrow_drop_down_24"（在 Android Studio 中右键 res → New → Vector Asset 选 Material arrow_drop_down，名字定为 ic_arrow_drop_down，颜色不 tint 留 white）。

**新增 dialog_filter_picker.xml：** 顶部 LinearLayout vertical，含 4 内置项 + RecyclerView 装用户分类 + 分割线 + "管理分类" 入口。

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingVertical="@dimen/spacing_s">

    <TextView ... android:text="@string/filter_picker_title" .../>

    <LinearLayout android:id="@+id/row_all" .../>           <!-- 全部 -->
    <LinearLayout android:id="@+id/row_uncategorized" .../> <!-- 未分类 -->
    <LinearLayout android:id="@+id/row_favorite" .../>      <!-- 我的收藏 -->
    <LinearLayout android:id="@+id/row_deleted" .../>       <!-- 最近删除 -->

    <View android:layout_height="1dp" android:background="@color/divider" .../>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/categories_recycler"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <View android:layout_height="1dp" android:background="@color/divider" .../>

    <LinearLayout android:id="@+id/row_manage" ...>
        <ImageView android:src="@drawable/ic_settings" .../>
        <TextView android:text="@string/filter_manage_categories" .../>
    </LinearLayout>
</LinearLayout>
```

每行（row_xx 和 item_filter_row）布局：`圆点 / icon | 名称 | 计数`，高 48dp，含 selected 状态 indicator（左侧 4dp 主色条 当选中）。

**item_filter_row.xml：** 单个 user category 的行，含 `color_dot` ImageView + `name` TextView + `selected_indicator` View（左侧条）。

**改 NoteListActivity：**

```kotlin
// 类字段
private lateinit var filterChip: View
private lateinit var filterChipText: TextView
private var currentFilter: NoteRepository.ListFilter = NoteRepository.ListFilter.All

// onCreate 新增
filterChip = findViewById(R.id.filter_chip)
filterChipText = findViewById(R.id.filter_chip_text)
currentFilter = loadFilter()
updateFilterChipLabel()
filterChip.setOnClickListener { showFilterPicker() }

// reload 改为带 filter
private fun reload() {
    lifecycleScope.launch {
        val list = NoteRepository.list(currentFilter, sortBy, currentQuery)
        adapter.submit(list)
        renderEmpty(list.isEmpty())
    }
}

private suspend fun updateFilterChipLabel() {
    val label = when (val f = currentFilter) {
        NoteRepository.ListFilter.All -> getString(R.string.filter_all)
        NoteRepository.ListFilter.Uncategorized -> getString(R.string.filter_uncategorized)
        NoteRepository.ListFilter.Favorite -> getString(R.string.filter_favorite)
        NoteRepository.ListFilter.Deleted -> getString(R.string.filter_deleted)
        is NoteRepository.ListFilter.Category -> CategoryRepository.get(f.id)?.name
            ?: getString(R.string.filter_all)
    }
    filterChipText.text = label
}

private fun showFilterPicker() {
    FilterPickerBottomSheet(this, currentFilter,
        onPick = { f ->
            currentFilter = f
            saveFilter(f)
            lifecycleScope.launch {
                updateFilterChipLabel()
                reload()
            }
        },
        onManage = { CategoryManagerBottomSheet(this) { lifecycleScope.launch { updateFilterChipLabel(); reload() } }.show() },
    ).show()
}

// 持久化（SharedPreferences）
private fun loadFilter(): NoteRepository.ListFilter {
    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
    val type = prefs.getString(KEY_FILTER_TYPE, "ALL") ?: "ALL"
    val catId = prefs.getLong(KEY_FILTER_CATEGORY_ID, -1L)
    return when (type) {
        "UNCATEGORIZED" -> NoteRepository.ListFilter.Uncategorized
        "FAVORITE" -> NoteRepository.ListFilter.Favorite
        "DELETED" -> NoteRepository.ListFilter.Deleted
        "CATEGORY" -> if (catId > 0) NoteRepository.ListFilter.Category(catId) else NoteRepository.ListFilter.All
        else -> NoteRepository.ListFilter.All
    }
}
private fun saveFilter(f: NoteRepository.ListFilter) {
    val edit = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
    val (type, catId) = when (f) {
        NoteRepository.ListFilter.All -> "ALL" to -1L
        NoteRepository.ListFilter.Uncategorized -> "UNCATEGORIZED" to -1L
        NoteRepository.ListFilter.Favorite -> "FAVORITE" to -1L
        NoteRepository.ListFilter.Deleted -> "DELETED" to -1L
        is NoteRepository.ListFilter.Category -> "CATEGORY" to f.id
    }
    edit.putString(KEY_FILTER_TYPE, type).putLong(KEY_FILTER_CATEGORY_ID, catId).apply()
}

companion object {
    private const val PREFS = "hwnote_settings"
    private const val KEY_SORT = "sort_by"
    private const val KEY_FILTER_TYPE = "filter_type"
    private const val KEY_FILTER_CATEGORY_ID = "filter_category_id"
}
```

**新建 view/list/FilterPickerBottomSheet.kt：**

```kotlin
package com.fan.hwnote.app.view.list

import android.content.Context
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.CategoryRepository
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.entity.Category
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class FilterPickerBottomSheet(
    private val activity: androidx.appcompat.app.AppCompatActivity,
    private val current: NoteRepository.ListFilter,
    private val onPick: (NoteRepository.ListFilter) -> Unit,
    private val onManage: () -> Unit,
) : BottomSheetDialog(activity) {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val view = layoutInflater.inflate(R.layout.dialog_filter_picker, null)
        setContentView(view)
        // 4 内置项
        bindRow(view.findViewById(R.id.row_all), NoteRepository.ListFilter.All)
        bindRow(view.findViewById(R.id.row_uncategorized), NoteRepository.ListFilter.Uncategorized)
        bindRow(view.findViewById(R.id.row_favorite), NoteRepository.ListFilter.Favorite)
        bindRow(view.findViewById(R.id.row_deleted), NoteRepository.ListFilter.Deleted)
        view.findViewById<View>(R.id.row_manage).setOnClickListener {
            dismiss()
            onManage()
        }
        // 用户分类列表
        val recycler = view.findViewById<RecyclerView>(R.id.categories_recycler)
        recycler.layoutManager = LinearLayoutManager(activity)
        activity.lifecycleScope.launch {
            val cats = CategoryRepository.list()
            recycler.adapter = CategoryRowAdapter(cats, current) { cat ->
                dismiss(); onPick(NoteRepository.ListFilter.Category(cat.id))
            }
        }
    }

    private fun bindRow(row: View, filter: NoteRepository.ListFilter) {
        // selected 状态：用 row.isSelected = (current == filter)，左侧条通过 selector drawable 控制
        row.isSelected = filter == current
        row.setOnClickListener { dismiss(); onPick(filter) }
    }
}

private class CategoryRowAdapter(
    private val items: List<Category>,
    private val current: NoteRepository.ListFilter,
    private val onClick: (Category) -> Unit,
) : RecyclerView.Adapter<CategoryRowAdapter.VH>() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter_row, parent, false)
        return VH(v)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val dot = v.findViewById<android.widget.ImageView>(R.id.color_dot)
        private val name = v.findViewById<android.widget.TextView>(R.id.name)
        fun bind(c: Category) {
            dot.setColorFilter(android.graphics.Color.parseColor(c.color))
            name.text = c.name
            itemView.isSelected = (current is NoteRepository.ListFilter.Category && current.id == c.id)
            itemView.setOnClickListener { onClick(c) }
        }
    }
}
```

**strings.xml 新增：**

```xml
<string name="filter_all">全部</string>
<string name="filter_uncategorized">未分类</string>
<string name="filter_favorite">我的收藏</string>
<string name="filter_deleted">最近删除</string>
<string name="filter_picker_title">筛选</string>
<string name="filter_manage_categories">管理分类</string>
```

**验：** 真机
- 进入列表 → Toolbar 中央可见 "全部 ▼"
- 点击 → BottomSheet 弹起，4 内置项可见，无用户分类时 RecyclerView 区为空
- 点 "我的收藏" → BottomSheet 收回，chip 文字变 "我的收藏"，列表只剩收藏笔记
- 杀进程重启 → 选项保留

**Commit:** `feat(m9): Toolbar filter chip + FilterPickerBottomSheet（4 内置项 + 用户分类）`

---

## Task 8 — CategoryManagerBottomSheet（CRUD + 颜色 + 拖动排序）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/list/CategoryManagerBottomSheet.kt`
- Create: `code/HuaWeiNote/app/src/main/res/layout/dialog_category_manager.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/item_category_manage.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/dialog_category_editor.xml`（新建/重命名 表单）
- Modify: `code/HuaWeiNote/app/src/main/res/values/colors.xml`（加 4 分类色）
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`

**新增颜色（colors.xml）：**

```xml
<!-- M9 分类色（PRD §13.2 4 色起步） -->
<color name="category_color_yellow">#FDD835</color>
<color name="category_color_teal">#00897B</color>
<color name="category_color_green">#43A047</color>
<color name="category_color_red">#E53935</color>
```

**dialog_category_manager.xml：** 顶部标题 + RecyclerView + 底部 "新建分类" 按钮。

```xml
<LinearLayout ... vertical>
    <TextView android:text="@string/category_manager_title" .../>
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/manager_recycler"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
    <LinearLayout android:id="@+id/btn_new_category" ...>
        <ImageView android:src="@drawable/ic_add" .../>
        <TextView android:text="@string/category_new" .../>
    </LinearLayout>
</LinearLayout>
```

**item_category_manage.xml：** `拖动 handle | color dot | name | edit icon | delete icon`，高 56dp。

```xml
<LinearLayout ... horizontal gravity=center_vertical>
    <ImageView android:id="@+id/drag_handle" android:src="@drawable/ic_drag_handle" .../>
    <ImageView android:id="@+id/color_dot" .../>
    <TextView android:id="@+id/name" android:layout_weight="1" .../>
    <ImageView android:id="@+id/btn_edit" android:src="@drawable/ic_edit" .../>
    <ImageView android:id="@+id/btn_delete" android:src="@drawable/ic_delete" .../>
</LinearLayout>
```

**ic_drag_handle / ic_edit / ic_delete：** Material vector "drag_handle_24" / "edit_24" / "delete_24"。

**dialog_category_editor.xml：** 单 EditText（name） + 4 色选 RadioGroup（横排圆点）。

```xml
<LinearLayout ... vertical>
    <EditText android:id="@+id/name_input" android:hint="@string/category_name_hint" .../>
    <LinearLayout android:id="@+id/color_picker" android:orientation="horizontal" ...>
        <!-- 4 个 color dot ImageView，android:id=@+id/color_0..color_3 -->
        <ImageView android:id="@+id/color_0" .../>
        <ImageView android:id="@+id/color_1" .../>
        <ImageView android:id="@+id/color_2" .../>
        <ImageView android:id="@+id/color_3" .../>
    </LinearLayout>
</LinearLayout>
```

**CategoryManagerBottomSheet.kt 主要结构：**

```kotlin
class CategoryManagerBottomSheet(
    private val activity: androidx.appcompat.app.AppCompatActivity,
    private val onChanged: () -> Unit,
) : BottomSheetDialog(activity) {

    private val items = mutableListOf<Category>()
    private lateinit var adapter: ManageAdapter

    override fun onCreate(s: android.os.Bundle?) {
        super.onCreate(s)
        val view = layoutInflater.inflate(R.layout.dialog_category_manager, null)
        setContentView(view)
        val recycler = view.findViewById<RecyclerView>(R.id.manager_recycler)
        recycler.layoutManager = LinearLayoutManager(activity)
        adapter = ManageAdapter(items, ::onEditClicked, ::onDeleteClicked)
        recycler.adapter = adapter
        // ItemTouchHelper：长按拖动
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition; val to = target.adapterPosition
                java.util.Collections.swap(items, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                activity.lifecycleScope.launch {
                    CategoryRepository.reorder(items.map { it.id })
                    onChanged()
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recycler)
        view.findViewById<View>(R.id.btn_new_category).setOnClickListener {
            showEditorDialog(existing = null)
        }
        reload()
    }

    private fun reload() {
        activity.lifecycleScope.launch {
            val list = CategoryRepository.list()
            items.clear(); items.addAll(list)
            adapter.notifyDataSetChanged()
        }
    }

    private fun onEditClicked(cat: Category) = showEditorDialog(existing = cat)
    private fun onDeleteClicked(cat: Category) {
        DeleteConfirmBottomSheet(
            activity,
            title = activity.getString(R.string.category_delete_title),
            message = activity.getString(R.string.category_delete_message, cat.name),
            confirmLabel = activity.getString(R.string.action_delete),
            onConfirm = {
                activity.lifecycleScope.launch {
                    CategoryRepository.delete(cat.id)
                    reload(); onChanged()
                }
            },
        ).show()
    }

    private fun showEditorDialog(existing: Category?) {
        val view = layoutInflater.inflate(R.layout.dialog_category_editor, null)
        val nameInput = view.findViewById<EditText>(R.id.name_input)
        val colorIds = listOf(R.id.color_0, R.id.color_1, R.id.color_2, R.id.color_3)
        val colorHexs = listOf("#FDD835", "#00897B", "#43A047", "#E53935")
        var pickedColor = existing?.color ?: colorHexs[0]
        nameInput.setText(existing?.name ?: "")
        colorIds.forEachIndexed { idx, id ->
            val iv = view.findViewById<android.widget.ImageView>(id)
            iv.setColorFilter(android.graphics.Color.parseColor(colorHexs[idx]))
            iv.isSelected = (colorHexs[idx] == pickedColor)
            iv.setOnClickListener {
                pickedColor = colorHexs[idx]
                colorIds.forEach { view.findViewById<View>(it).isSelected = (it == id) }
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(if (existing == null) R.string.category_new else R.string.category_edit)
            .setView(view)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                activity.lifecycleScope.launch {
                    if (existing == null) CategoryRepository.insert(name, pickedColor)
                    else CategoryRepository.update(existing.id, name, pickedColor)
                    reload(); onChanged()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}

private class ManageAdapter(
    private val items: List<Category>,
    private val onEdit: (Category) -> Unit,
    private val onDelete: (Category) -> Unit,
) : RecyclerView.Adapter<ManageAdapter.VH>() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_manage, parent, false)
        return VH(v)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val dot = v.findViewById<android.widget.ImageView>(R.id.color_dot)
        private val name = v.findViewById<android.widget.TextView>(R.id.name)
        fun bind(c: Category) {
            dot.setColorFilter(android.graphics.Color.parseColor(c.color))
            name.text = c.name
            v.findViewById<View>(R.id.btn_edit).setOnClickListener { onEdit(c) }
            v.findViewById<View>(R.id.btn_delete).setOnClickListener { onDelete(c) }
        }
    }
}
```

（`DeleteConfirmBottomSheet` 在 Task 9 实现 —— Task 8 实施时可暂用 AlertDialog 占位，Task 9 完成后切回。）

**strings.xml 新增：**

```xml
<string name="category_manager_title">管理分类</string>
<string name="category_new">新建分类</string>
<string name="category_edit">编辑分类</string>
<string name="category_name_hint">分类名称</string>
<string name="category_delete_title">删除分类</string>
<string name="category_delete_message">"删除分类\"%1$s\"？该分类下的笔记将变为\"未分类\"。"</string>
```

**验：** 真机
- 顶部 chip → BottomSheet → "管理分类" → 弹分类管理 sheet
- 点 "新建分类" → AlertDialog 弹起，输入名 + 选色 → 确定 → 列表多出新分类
- 长按某个分类拖动 → 顺序变化 + 跳到 filter 选择 sheet 顺序一致
- 点编辑 → 同对话框预填，可改名/改色
- 点删除 → 底部确认弹起 → 确定 → 该分类消失，原属此分类的笔记落到"未分类"

**Commit:** `feat(m9): CategoryManagerBottomSheet — CRUD + 4 色选 + ItemTouchHelper 拖动排序`

---

## Task 9 — 长按菜单 filter 适配 + DeleteConfirmBottomSheet + 软删/彻底删调用

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/list/DeleteConfirmBottomSheet.kt`
- Create: `code/HuaWeiNote/app/src/main/res/layout/dialog_delete_confirm.xml`
- Modify: `code/HuaWeiNote/app/src/main/res/menu/menu_note_card_long_press.xml`
- Create: `code/HuaWeiNote/app/src/main/res/menu/menu_note_card_deleted.xml`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`

**DeleteConfirmBottomSheet.kt：**

```kotlin
package com.fan.hwnote.app.view.list

import android.content.Context
import android.view.View
import android.widget.TextView
import com.fan.hwnote.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class DeleteConfirmBottomSheet(
    context: Context,
    private val title: String,
    private val message: String,
    private val confirmLabel: String,
    private val confirmIsDanger: Boolean = true,
    private val onConfirm: () -> Unit,
) : BottomSheetDialog(context) {

    override fun onCreate(s: android.os.Bundle?) {
        super.onCreate(s)
        val view = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        setContentView(view)
        view.findViewById<TextView>(R.id.confirm_title).text = title
        view.findViewById<TextView>(R.id.confirm_message).text = message
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)
        btnConfirm.text = confirmLabel
        if (confirmIsDanger) btnConfirm.setTextColor(
            androidx.core.content.ContextCompat.getColor(context, R.color.error)
        )
        btnConfirm.setOnClickListener { dismiss(); onConfirm() }
        view.findViewById<TextView>(R.id.btn_cancel).setOnClickListener { dismiss() }
    }
}
```

**dialog_delete_confirm.xml：**

```xml
<LinearLayout ... vertical paddingVertical=spacing_l>
    <TextView android:id="@+id/confirm_title" android:textSize="@dimen/text_title" android:textStyle="bold" .../>
    <TextView android:id="@+id/confirm_message" android:layout_marginTop="@dimen/spacing_s" .../>
    <View android:layout_height="1dp" android:background="@color/divider" android:layout_marginTop="@dimen/spacing_l" />
    <LinearLayout android:orientation="horizontal" android:layout_marginTop="@dimen/spacing_s">
        <TextView android:id="@+id/btn_cancel"
            android:layout_width="0dp" android:layout_weight="1" android:layout_height="48dp"
            android:gravity="center" android:text="@string/action_cancel"
            android:textColor="@color/text_secondary" android:textSize="@dimen/text_body"
            android:background="?attr/selectableItemBackground" />
        <View android:layout_width="1dp" android:layout_height="48dp" android:background="@color/divider" />
        <TextView android:id="@+id/btn_confirm"
            android:layout_width="0dp" android:layout_weight="1" android:layout_height="48dp"
            android:gravity="center" android:text="@string/action_delete"
            android:textColor="@color/error" android:textSize="@dimen/text_body" android:textStyle="bold"
            android:background="?attr/selectableItemBackground" />
    </LinearLayout>
</LinearLayout>
```

**menu_note_card_deleted.xml（新建）：** 最近删除态长按菜单 = 恢复 / 彻底删除。

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/action_restore" android:title="@string/action_restore" />
    <item android:id="@+id/action_delete_permanently" android:title="@string/action_delete_permanently" />
</menu>
```

**改 NoteListActivity.showCardMenu：** 按 currentFilter 选 menu，按 itemId 路由。

```kotlin
private fun showCardMenu(note: Note, anchor: View) {
    val popup = PopupMenu(this, anchor)
    val isDeletedView = currentFilter == NoteRepository.ListFilter.Deleted
    if (isDeletedView) popup.menuInflater.inflate(R.menu.menu_note_card_deleted, popup.menu)
    else popup.menuInflater.inflate(R.menu.menu_note_card_long_press, popup.menu)
    popup.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            R.id.action_toggle_favorite -> {
                lifecycleScope.launch { NoteRepository.setFavorite(note.id, !note.isFavorite); reload() }
                true
            }
            R.id.action_delete -> {
                DeleteConfirmBottomSheet(
                    this,
                    title = getString(R.string.dialog_delete_title),
                    message = getString(R.string.dialog_soft_delete_message),
                    confirmLabel = getString(R.string.action_delete),
                    onConfirm = { lifecycleScope.launch { NoteRepository.softDelete(note.id); reload() } },
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
                    title = getString(R.string.dialog_delete_permanently_title),
                    message = getString(R.string.dialog_delete_permanently_message),
                    confirmLabel = getString(R.string.action_delete_permanently),
                    onConfirm = { lifecycleScope.launch { NoteRepository.deletePermanently(note.id); reload() } },
                ).show()
                true
            }
            else -> false
        }
    }
    popup.show()
}
```

**strings.xml 新增：**

```xml
<string name="action_restore">恢复</string>
<string name="action_delete_permanently">彻底删除</string>
<string name="dialog_soft_delete_message">该笔记将移入"最近删除"，30 天后自动清理。</string>
<string name="dialog_delete_permanently_title">彻底删除笔记</string>
<string name="dialog_delete_permanently_message">彻底删除后不可恢复，确定删除？</string>
```

**改原 dialog_delete_message 用途已变 —— 保留不再引用，新文案走 `dialog_soft_delete_message`。**

**注意：** `Task 8` 中 `onDeleteClicked` 用到的 `DeleteConfirmBottomSheet` 此时正式可用，回切 Task 8 占位（如果用了 AlertDialog 兜底，要改回 DeleteConfirmBottomSheet）。

**验：** 真机
- 列表正常态长按笔记 → 弹"收藏 / 删除"PopupMenu → 删除 → 底部 BottomSheet 二次确认 → 确定 → 笔记从列表消失（落最近删除）
- 切到"最近删除" → 长按 → "恢复 / 彻底删" → 恢复 → 笔记重新出现在原 filter
- 最近删除 → 彻底删除 → 底部 BottomSheet 二次确认 → 确定 → 笔记 + 本地图片目录都被删

**Commit:** `feat(m9): 删除走底部 BottomSheet 二次确认 + 最近删除菜单切恢复/彻底删`

---

## Task 10 — 编辑器 metadata strip（时间 · 分类）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/layout/activity_note_editor.xml`
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/editor/CategoryPickerBottomSheet.kt`
- Create: `code/HuaWeiNote/app/src/main/res/layout/dialog_category_picker.xml`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`

**activity_note_editor.xml 标题下加 strip（titleInput 后、blocksContainer 前）：**

```xml
<LinearLayout
    android:id="@+id/metadata_strip"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="@dimen/spacing_xs"
    android:gravity="center_vertical"
    android:orientation="horizontal">

    <TextView
        android:id="@+id/meta_time"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/text_hint"
        android:textSize="@dimen/text_hint"
        tools:text="2 分钟前" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingHorizontal="@dimen/spacing_xs"
        android:text=" · "
        android:textColor="@color/text_hint"
        android:textSize="@dimen/text_hint" />

    <LinearLayout
        android:id="@+id/meta_category_chip"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingHorizontal="@dimen/spacing_xs"
        android:paddingVertical="2dp">

        <ImageView
            android:id="@+id/meta_category_dot"
            android:layout_width="8dp"
            android:layout_height="8dp"
            android:src="@drawable/shape_circle"
            app:tint="@color/text_hint" />

        <TextView
            android:id="@+id/meta_category_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/spacing_xs"
            android:text="@string/filter_uncategorized"
            android:textColor="@color/text_hint"
            android:textSize="@dimen/text_hint" />
    </LinearLayout>
</LinearLayout>
```

**shape_circle.xml（新建 drawable）：**

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/text_hint" />
</shape>
```

**CategoryPickerBottomSheet.kt：** 类似 FilterPickerBottomSheet，但只列 "未分类" + 用户分类，回调返回 `Long?`（null = 未分类）。

```kotlin
class CategoryPickerBottomSheet(
    private val activity: androidx.appcompat.app.AppCompatActivity,
    private val currentCategoryId: Long?,
    private val onPick: (Long?) -> Unit,
) : BottomSheetDialog(activity) {

    override fun onCreate(s: android.os.Bundle?) {
        super.onCreate(s)
        val view = layoutInflater.inflate(R.layout.dialog_category_picker, null)
        setContentView(view)
        view.findViewById<View>(R.id.row_uncategorized).apply {
            isSelected = currentCategoryId == null
            setOnClickListener { dismiss(); onPick(null) }
        }
        val recycler = view.findViewById<RecyclerView>(R.id.categories_recycler)
        recycler.layoutManager = LinearLayoutManager(activity)
        activity.lifecycleScope.launch {
            val cats = CategoryRepository.list()
            recycler.adapter = PickerAdapter(cats, currentCategoryId) {
                dismiss(); onPick(it)
            }
        }
    }
}

private class PickerAdapter(
    private val items: List<Category>,
    private val current: Long?,
    private val onClick: (Long) -> Unit,
) : RecyclerView.Adapter<PickerAdapter.VH>() {
    // 类似 CategoryRowAdapter 实现，复用 item_filter_row.xml 布局
    // ...
}
```

**dialog_category_picker.xml：** 标题 + "未分类" 行 + RecyclerView。

```xml
<LinearLayout ... vertical>
    <TextView android:text="@string/category_picker_title" .../>
    <LinearLayout android:id="@+id/row_uncategorized" ...>
        <ImageView .../>  <!-- 灰色圆点 -->
        <TextView android:text="@string/filter_uncategorized" .../>
    </LinearLayout>
    <View android:layout_height="1dp" android:background="@color/divider" .../>
    <androidx.recyclerview.widget.RecyclerView android:id="@+id/categories_recycler" .../>
</LinearLayout>
```

**改 NoteEditorActivity：**

```kotlin
private lateinit var metaTime: android.widget.TextView
private lateinit var metaCategoryDot: android.widget.ImageView
private lateinit var metaCategoryName: android.widget.TextView
private lateinit var metaCategoryChip: View

// onCreate 末尾
metaTime = findViewById(R.id.meta_time)
metaCategoryDot = findViewById(R.id.meta_category_dot)
metaCategoryName = findViewById(R.id.meta_category_name)
metaCategoryChip = findViewById(R.id.meta_category_chip)
metaCategoryChip.setOnClickListener { showCategoryPicker() }

// loadNote 内 presenter.bind 之后追加
refreshMetadataStrip()

// 新增
private fun refreshMetadataStrip() {
    val n = loadedNote ?: return
    metaTime.text = com.fan.hwnote.app.util.DateUtils.formatRelative(
        if (n.updatedAt > 0) n.updatedAt else System.currentTimeMillis()
    )
    lifecycleScope.launch {
        val cat = n.categoryId?.let { CategoryRepository.get(it) }
        if (cat == null) {
            metaCategoryName.text = getString(R.string.filter_uncategorized)
            metaCategoryDot.setColorFilter(
                androidx.core.content.ContextCompat.getColor(this@NoteEditorActivity, R.color.text_hint)
            )
        } else {
            metaCategoryName.text = cat.name
            metaCategoryDot.setColorFilter(android.graphics.Color.parseColor(cat.color))
        }
    }
}

private fun showCategoryPicker() {
    val cur = loadedNote ?: return
    com.fan.hwnote.app.view.editor.CategoryPickerBottomSheet(
        this, cur.categoryId,
        onPick = { newCatId ->
            loadedNote = cur.copy(categoryId = newCatId)
            refreshMetadataStrip()
            // 即时持久化 categoryId（其他字段维持原状）—— 走 save 即可，onPause 也会再 save 一次
            lifecycleScope.launch(Dispatchers.IO) {
                NoteRepository.save(loadedNote!!)
            }
        },
    ).show()
}
```

**strings.xml 新增：**

```xml
<string name="category_picker_title">移动到</string>
```

**验：** 真机
- 进入笔记 → 标题下方一行可见 "X 前 · 未分类"，时间随上次编辑刷新
- 点 "未分类" → BottomSheet 弹起，含 "未分类" + 用户分类列表
- 选 "工作" → strip 立即变 "X 前 · 工作"（黄色圆点）
- 退出再进 → 分类保留
- 杀进程后回到列表 → "工作" filter 下能看到此笔记

**Commit:** `feat(m9): 编辑器 metadata strip — 时间 · 分类 + 点选 BottomSheet`

---

## Task 11 — 全量构建 + 真机走查 + STATUS 收尾

**Files:**
- Modify: `docs/superpowers/STATUS.md`

**验：**
1. `JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:clean :app:assembleDebug :app:test` 必须 BUILD SUCCESSFUL + 67 + 6 = 73 项单测全 PASS

2. 真机走查 10 条：
   - ✅ 旧装机升级：装 M8 版本写 2 条笔记 → 安装 M9 版本 → 旧笔记自动落"全部"，0 崩溃
   - ✅ Toolbar 中央 "全部 ▼" chip 可见可点
   - ✅ FilterPicker BottomSheet：4 内置项 + 管理入口可见，无分类时 RecyclerView 空
   - ✅ 新建 3 个分类（不同色）+ 拖动调序 → 重启 App 顺序保留
   - ✅ 删除一个分类（含 1 笔记）→ 该笔记落 "未分类"
   - ✅ 列表正常态删除笔记 → 底部 BottomSheet 二次确认 → 确定 → 切 "最近删除" 能看到
   - ✅ 最近删除：长按 → 恢复 → 笔记回到 "全部"
   - ✅ 最近删除：长按 → 彻底删 → 底部 BottomSheet 二次确认 → 确定 → 列表 + 文件目录全清
   - ✅ 编辑器 metadata strip：进入笔记可见时间 · 分类；点分类 → BottomSheet 选 → 切换生效；退出再进保留
   - ✅ 30 天清理：可改本地系统时间往后 31 天 → 杀 App 重开 → 已超期软删笔记自动清理（手测可选，本地难复现可在 Repository 测覆盖）

**STATUS.md 追加节大纲：** "## M9 完成详情（2026-06-04）"
- 起因（PRD §13 范围）
- 4 改动点：分类系统 / 软删除 / metadata strip / DB v2 迁移
- 涉及文件清单
- commit 列表（11 commits）
- 单测变化（67 → 73，增量 +6）
- 验收 10 条
- 执行模式
- 同时把"项目完成总览"表加 M9 行

**Commit:** `docs(m9): 标记 M9 分类+软删除+metadata strip 完成`

---

## 执行方式

Subagent-Driven Development，严格串行 T1→T11。每任务 implementer → spec reviewer → code quality reviewer → fix → 标完成。T11 真机走查由用户完成后再写 STATUS 收尾 commit。

## 自审记录

- **DB 迁移安全**：onUpgrade 仅追加列 + 建表，不删原列；ALTER 是 SQLite 安全操作；旧数据 category_id=NULL / deleted_at=0 自动兼容。
- **enum/sealed 互斥**：删 ListFilter 中任一项都不会破坏现有调用方（when 是 exhaustive）。
- **CategoryRepository.delete 事务**：UPDATE notes SET category_id=NULL + DELETE categories 走同一事务，避免半成功状态。
- **purgeExpired 性能**：30 天清理一次启动跑一次，n=超期数；批量 DELETE 即可，无索引也不慢（SQLite 主键 PK 索引天然存在）。
- **DeleteConfirmBottomSheet 复用**：4 个删除场景（笔记软删/笔记彻底删/分类删/...）共用同一控件，参数化 title / message / confirmLabel / onConfirm。
- **filter 持久化兼容性**：旧装机第一次启动读取不到 KEY_FILTER_TYPE，默认 "ALL"，正确降级。
- **测试基线**：M8 基线 67 → M9 +6 = 73；DB v1→v2 迁移测覆盖旧数据安全。
- **UI 改动一致性**：所有底部 sheet（FilterPicker / CategoryManager / CategoryPicker / DeleteConfirm）都用 Material BottomSheetDialog，与 M8 排序 sheet + StylePickerBottomSheet 风格统一。
