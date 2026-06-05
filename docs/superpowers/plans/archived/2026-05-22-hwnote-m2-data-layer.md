# M2 · 数据层 实施计划（详细版）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 HwNote 实现完整的数据层：实体（Note / NoteContent / Block / TextSpan / Stroke）、JSON 序列化（org.json）、SQLite 表与 Helper、文件存储、统一仓库门面 NoteRepository。**全部用 JVM 单测覆盖**（关键路径 ≥ 12 用例）。

**Architecture:**
- 实体放 `model/entity/`，纯数据类、无 Android 依赖
- 序列化 `model/json/NoteJson` 用 `org.json`（容错：损坏 JSON → 空内容）
- 存储两条线：DB（`SQLiteOpenHelper` 直连）+ 文件（`filesDir/notes/<id>/images/`）
- `NoteRepository` 是单例（`object`），`init(context)` 注入 Application；所有 IO 用 `suspend` + `Dispatchers.IO`
- 测试分层：
  - 纯 JVM（JUnit 5）：实体 + NoteJson 序列化（用独立 `org.json:json` artifact）
  - Robolectric + JUnit 4（经 Vintage engine 与 JUnit 5 共存）：DbHelper / FileStorage / Repository

**Tech Stack:** Kotlin · org.json（Android + 测试期 standalone artifact）· SQLiteOpenHelper · kotlinx-coroutines · Robolectric 4.13 · JUnit 5 + JUnit 4 (Vintage 桥接)

**配套文档：**
- PRD：`docs/superpowers/specs/2026-05-22-hwnote-design.md`（§6 数据模型 / §7 技术架构）
- 高层计划：`docs/superpowers/plans/2026-05-22-hwnote-implementation.md`（M2 节）
- M1 已完成：`docs/superpowers/plans/2026-05-22-hwnote-m1-foundation.md`（commit `a8a1adb`）
- 进度看板：`docs/superpowers/STATUS.md`

---

## 前置条件

- M1 已完成（工程能 `./gradlew :app:assembleDebug`，SmokeTest 通过）
- JDK 17 已就绪（执行 gradle 必须）：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`
- 项目根：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/`
- 工程目录：`code/HuaWeiNote/`
- **重要约束**：M1 中已锁定的版本（AGP 8.11.2 / Kotlin 2.0.21 / compileSdk 36 / JDK 11 编译期）一律不变。本计划只**追加**测试依赖。

---

## 文件结构总览

源代码（`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/`）：

```
model/
├─ entity/
│  ├─ TextSpan.kt          ← data class TextSpan + enum SpanType（6 种）
│  ├─ Stroke.kt            ← data class Stroke + StrokePoint + enum BrushType（4 种）
│  ├─ Block.kt             ← sealed Block: TextBlock | ImageBlock | ChecklistBlock + ChecklistItem + Heading
│  ├─ NoteContent.kt       ← data class（blocks + handwriting）+ toPlainText()
│  └─ Note.kt              ← data class Note + Note.new() 工厂
├─ json/
│  └─ NoteJson.kt          ← object，toJson/fromJson（org.json）
├─ db/
│  └─ NoteDbHelper.kt      ← SQLiteOpenHelper，DB="hwnote.db", VERSION=1
├─ storage/
│  └─ NoteFileStorage.kt   ← noteDir / imageDir / imageFile / deleteNoteDir
└─ NoteRepository.kt       ← object 单例：init / list / get / save / delete / setFavorite
```

测试（`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/`）：

```
model/
├─ entity/NoteContentTest.kt              ← JUnit 5：toPlainText 3 用例
├─ json/
│  ├─ NoteJsonTextBlockTest.kt            ← JUnit 5：TextBlock + heading + 6 spans round-trip
│  ├─ NoteJsonImageChecklistTest.kt       ← JUnit 5：Image / Checklist round-trip
│  ├─ NoteJsonStrokeTest.kt               ← JUnit 5：4 brush 类型 + 多点 round-trip
│  └─ NoteJsonResilienceTest.kt           ← JUnit 5：损坏 JSON → 空 NoteContent
├─ db/NoteDbHelperTest.kt                 ← JUnit 4 + Robolectric：建表 + 索引
├─ storage/NoteFileStorageTest.kt         ← JUnit 4 + Robolectric：noteDir / deleteNoteDir
├─ NoteRepositorySaveGetTest.kt           ← JUnit 4 + Robolectric：save 新/旧 + get
├─ NoteRepositoryListTest.kt              ← JUnit 4 + Robolectric：sortBy 三种 + search
└─ NoteRepositoryDeleteFavoriteTest.kt    ← JUnit 4 + Robolectric：delete + setFavorite

resources/robolectric.properties          ← sdk=34（锁定 Robolectric 版本兼容）
```

修改的文件：

- `gradle/libs.versions.toml`：加 `junitVintage` / `orgJsonArtifact` 两个版本与对应库别名
- `app/build.gradle.kts`：testImplementation 增加 `org.json:json` + `junit`（已有别名）；testRuntimeOnly 增加 `junit-vintage-engine`
- `app/src/main/java/com/fan/hwnote/app/App.kt`：在 `onCreate` 中 `NoteRepository.init(this)`
- `docs/superpowers/STATUS.md`：M2 状态从未开始 → 完成

---

## 任务概览

| # | 任务 | 产出 |
|---|------|------|
| 1 | 测试基础设施 | JUnit Vintage + org.json + robolectric.properties |
| 2 | 实体值类（TextSpan / Stroke / 枚举） | TextSpan.kt + Stroke.kt |
| 3 | Block sealed + Heading + ChecklistItem | Block.kt |
| 4 | NoteContent + Note + toPlainText 测试 | NoteContent.kt + Note.kt + NoteContentTest |
| 5 | NoteJson — TextBlock + heading + 6 行内 Span | NoteJson.kt（部分）+ NoteJsonTextBlockTest |
| 6 | NoteJson — Image + Checklist 块 | NoteJson.kt（增量）+ NoteJsonImageChecklistTest |
| 7 | NoteJson — Stroke 4 笔种 | NoteJson.kt（增量）+ NoteJsonStrokeTest |
| 8 | NoteJson — fromJson 容错 | NoteJsonResilienceTest |
| 9 | NoteDbHelper（建表 + 索引） | NoteDbHelper.kt + NoteDbHelperTest |
| 10 | NoteFileStorage | NoteFileStorage.kt + NoteFileStorageTest |
| 11 | NoteRepository — save + get | NoteRepository.kt（部分）+ NoteRepositorySaveGetTest |
| 12 | NoteRepository — list + sortBy + search | NoteRepository.kt（增量）+ NoteRepositoryListTest |
| 13 | NoteRepository — delete + setFavorite | NoteRepository.kt（增量）+ NoteRepositoryDeleteFavoriteTest |
| 14 | App.onCreate 接入 + 全测试 + STATUS + commit | App.kt + STATUS.md + git commit |

共 **14 任务**。预估总时长 **4-6 小时**。

---

## Task 1: 测试基础设施

**Files:**
- Modify: `code/HuaWeiNote/gradle/libs.versions.toml`
- Modify: `code/HuaWeiNote/app/build.gradle.kts`
- Create: `code/HuaWeiNote/app/src/test/resources/robolectric.properties`

**为什么：**
- `org.json.JSONObject` 在 Android 单元测试 JVM 环境下是 stub，会抛 "Stub! Stub! Stub!"。加 `org.json:json` 独立 artifact 提供真实实现。
- M2 的 DbHelper / FileStorage / Repository 测试需要 `Context` + `SQLiteDatabase`，必须用 Robolectric。Robolectric 4.13 没有原生 JUnit 5 支持，所以用 JUnit 4 写这些测试，加 `junit-vintage-engine` 让它们与 JUnit 5 测试一起跑。
- Robolectric 4.13 内置的 Android API 最高到 34；我们工程 `compileSdk=36`，必须用 `robolectric.properties` 强制 `sdk=34`，否则启动报错 "no SDK XX".

- [ ] **Step 1: 修改 `gradle/libs.versions.toml`，追加版本与库别名**

文件路径：`code/HuaWeiNote/gradle/libs.versions.toml`

在 `[versions]` 段已有 `junit = "4.13.2"` / `junitJupiter = "5.10.2"` 之后，**追加**：

```toml
junitVintage = "5.10.2"
orgJson = "20240303"
```

在 `[libraries]` 段已有 `junit = { ... }` / `junit-jupiter = { ... }` 之后，**追加**：

```toml
junit-vintage-engine = { group = "org.junit.vintage", name = "junit-vintage-engine", version.ref = "junitVintage" }
org-json = { group = "org.json", name = "json", version.ref = "orgJson" }
```

> 注意：`junit-vintage-engine` 版本必须与 `junit-jupiter` 同主版本（5.10.x），否则可能出现 BOM 冲突。

- [ ] **Step 2: 修改 `app/build.gradle.kts`，加测试依赖**

文件路径：`code/HuaWeiNote/app/build.gradle.kts`

在 `dependencies { ... }` 块的 `testImplementation` 下加两行，并新增 `testRuntimeOnly`：

```kotlin
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)                  // 新增：JUnit 4 (Robolectric 测试用)
    testImplementation(libs.org.json)               // 新增：org.json 真实实现
    testRuntimeOnly(libs.junit.vintage.engine)      // 新增：JUnit 4 桥接 JUnit Platform
```

- [ ] **Step 3: 创建 `robolectric.properties` 锁定 SDK**

文件路径：`code/HuaWeiNote/app/src/test/resources/robolectric.properties`

```properties
sdk=34
```

如果 `app/src/test/resources/` 目录还不存在，先 `mkdir -p`。

- [ ] **Step 4: 验证 `:app:test` 仍能跑（SmokeTest 通过）**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote
./gradlew :app:test
```

预期：`BUILD SUCCESSFUL`，`SmokeTest > arithmetic still works PASSED` 出现在输出。如果失败，先看是否依赖解析错（网络/版本）。

- [ ] **Step 5: 不 commit，与 Task 2 一起提交**

测试基础设施纯属配置变化，没有可单测的产出，留到 Task 4 一起 commit。

---

## Task 2: 实体值类 — TextSpan / Stroke / 枚举

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/TextSpan.kt`
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Stroke.kt`

> **为什么不用 `List<FloatArray>` 存 stroke 点**：`FloatArray.equals` 是引用比较，会破坏 `data class` 的 `equals`/`hashCode`，导致测试 round-trip 不通过。改用 `data class StrokePoint(x, y, t)` 让 `equals` 自动正确。坐标用 Int（PRD JSON 例子也是整数，手写精度足够）。

- [ ] **Step 1: 创建 `TextSpan.kt`**

文件路径：`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/TextSpan.kt`

```kotlin
package com.fan.hwnote.app.model.entity

/**
 * 行内文本样式 span。覆盖范围：[start, end)。
 * value 仅 FONT_SIZE / COLOR 用：FONT_SIZE 取 "small"/"medium"/"large"，COLOR 取 "#RRGGBB"。
 */
data class TextSpan(
    val start: Int,
    val end: Int,
    val type: SpanType,
    val value: String? = null,
)

enum class SpanType {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    FONT_SIZE,
    COLOR,
}
```

- [ ] **Step 2: 创建 `Stroke.kt`**

文件路径：`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Stroke.kt`

```kotlin
package com.fan.hwnote.app.model.entity

/**
 * 单条手写笔画。
 * - color 形如 "#RRGGBB"
 * - width 单位 dp：1=细 / 3=中 / 6=粗
 * - points 是按时间顺序的采样点；坐标系是"内容总坐标"（相对 LinearLayout 顶部）
 */
data class Stroke(
    val brush: BrushType,
    val color: String,
    val width: Int,
    val points: List<StrokePoint>,
)

/** 单个采样点：x/y 像素，t = 自落笔起的相对毫秒。 */
data class StrokePoint(val x: Int, val y: Int, val t: Int)

enum class BrushType {
    PEN,      // 钢笔
    BRUSH,    // 画笔
    MARKER,   // 粗细笔
    PENCIL,   // 铅笔
}
```

- [ ] **Step 3: 编译验证**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote
./gradlew :app:compileDebugKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 4: 不 commit，与 Task 4 一起**

---

## Task 3: Block sealed class + Heading + ChecklistItem

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Block.kt`

- [ ] **Step 1: 创建 `Block.kt`**

文件路径：`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Block.kt`

```kotlin
package com.fan.hwnote.app.model.entity

/**
 * 笔记内容块。三种变体：文本 / 图片 / 清单。
 * id 是块在笔记内的稳定标识（"b-001" 形式或 UUID 短串）；编辑器据此识别块。
 *
 * 字段标 var 是有意为之：编辑器 View 层会就地修改（用户输入触发 TextWatcher → 更新 text/spans）。
 * 数据类 equals/hashCode 仍按当前 var 值计算，不影响测试。
 */
sealed class Block {
    abstract val id: String

    data class TextBlock(
        override val id: String,
        var heading: Heading? = null,
        var text: String = "",
        var spans: List<TextSpan> = emptyList(),
    ) : Block()

    data class ImageBlock(
        override val id: String,
        var fileName: String,
        var width: Int,
        var height: Int,
    ) : Block()

    data class ChecklistBlock(
        override val id: String,
        var items: MutableList<ChecklistItem> = mutableListOf(),
    ) : Block()
}

/** 清单内单项。 */
data class ChecklistItem(var checked: Boolean, var text: String)

/** 文本块的标题级别（块级属性，不写在 spans 里）。 */
enum class Heading { H1, H2 }
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 3: 不 commit，与 Task 4 一起**

---

## Task 4: NoteContent + Note + `toPlainText` 单测

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/NoteContent.kt`
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Note.kt`
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/entity/NoteContentTest.kt`

- [ ] **Step 1: 先写失败的测试 `NoteContentTest.kt`**

文件路径：`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/entity/NoteContentTest.kt`

```kotlin
package com.fan.hwnote.app.model.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteContentTest {

    @Test
    fun `toPlainText returns empty when blocks are empty`() {
        val content = NoteContent(blocks = emptyList(), handwriting = emptyList())
        assertEquals("", content.toPlainText())
    }

    @Test
    fun `toPlainText concatenates text blocks with newlines and skips images`() {
        val content = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", text = "今天会议要点"),
                Block.ImageBlock(id = "b2", fileName = "x.jpg", width = 100, height = 100),
                Block.TextBlock(id = "b3", text = "结论：发版推迟"),
            ),
            handwriting = emptyList(),
        )
        assertEquals("今天会议要点\n结论：发版推迟", content.toPlainText())
    }

    @Test
    fun `toPlainText flattens checklist items and ignores empty blocks`() {
        val content = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", text = "TODO"),
                Block.TextBlock(id = "b2", text = ""), // 空块跳过
                Block.ChecklistBlock(
                    id = "b3",
                    items = mutableListOf(
                        ChecklistItem(checked = true, text = "确认排期"),
                        ChecklistItem(checked = false, text = "联系李雷"),
                    ),
                ),
            ),
            handwriting = emptyList(),
        )
        assertEquals("TODO\n确认排期\n联系李雷", content.toPlainText())
    }
}
```

- [ ] **Step 2: 跑测试，预期编译失败**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.entity.NoteContentTest"
```

预期：编译错（unresolved reference: NoteContent）。OK，进入实现。

- [ ] **Step 3: 实现 `NoteContent.kt`**

文件路径：`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/NoteContent.kt`

```kotlin
package com.fan.hwnote.app.model.entity

/**
 * 笔记的完整内容：内容块列表 + 手写笔画列表。
 * 持久化为 content_json 字段（详见 PRD §6.2）。
 */
data class NoteContent(
    val blocks: List<Block>,
    val handwriting: List<Stroke>,
) {
    /**
     * 生成 plain_text（搜索字段）：
     * - TextBlock：取 text，非空才计入
     * - ChecklistBlock：每项 text，非空才计入
     * - ImageBlock：忽略
     * - handwriting：忽略
     * 多段用 '\n' 拼接。
     */
    fun toPlainText(): String {
        val sb = StringBuilder()
        for (b in blocks) {
            when (b) {
                is Block.TextBlock -> appendIfNotEmpty(sb, b.text)
                is Block.ChecklistBlock -> b.items.forEach { appendIfNotEmpty(sb, it.text) }
                is Block.ImageBlock -> Unit // 图片不进搜索
            }
        }
        return sb.toString()
    }

    private fun appendIfNotEmpty(sb: StringBuilder, text: String) {
        if (text.isEmpty()) return
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(text)
    }

    companion object {
        fun empty(): NoteContent = NoteContent(emptyList(), emptyList())
    }
}
```

- [ ] **Step 4: 实现 `Note.kt`**

文件路径：`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Note.kt`

```kotlin
package com.fan.hwnote.app.model.entity

/**
 * 笔记顶层实体。id == 0L 表示尚未持久化（NoteRepository.save 时插入）。
 *
 * Note 是不可变快照（用 .copy 改字段）；Block 内字段是 var（编辑器 View 直接改）。
 */
data class Note(
    val id: Long = 0L,
    val title: String = "",
    val plainText: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val content: NoteContent = NoteContent.empty(),
) {
    companion object {
        /** 新建一条笔记的内存对象。createdAt/updatedAt 都填 now，等待 save 时入库。 */
        fun new(now: Long = System.currentTimeMillis()): Note = Note(
            id = 0L,
            createdAt = now,
            updatedAt = now,
        )
    }
}
```

- [ ] **Step 5: 跑测试，预期通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.entity.NoteContentTest"
```

预期：3 项 PASSED。

- [ ] **Step 6: 第一次 commit（Tasks 1-4 合并提交）**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote
git add code/HuaWeiNote/gradle/libs.versions.toml \
        code/HuaWeiNote/app/build.gradle.kts \
        code/HuaWeiNote/app/src/test/resources/robolectric.properties \
        code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/
git add code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/
git commit -m "feat(m2): 实体类骨架 + toPlainText"
```

---

## Task 5: NoteJson — TextBlock + heading + 6 行内 Span

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/json/NoteJson.kt`
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonTextBlockTest.kt`

**测试覆盖（用例）：**
1. 空 NoteContent round-trip
2. 单 TextBlock（无 heading 无 spans）round-trip
3. TextBlock with heading=H1 / H2
4. TextBlock with 6 种 span（每种各一例）

- [ ] **Step 1: 写失败的测试 `NoteJsonTextBlockTest.kt`**

文件路径：`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonTextBlockTest.kt`

```kotlin
package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.NoteContent
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteJsonTextBlockTest {

    private fun roundTrip(content: NoteContent): NoteContent =
        NoteJson.fromJson(NoteJson.toJson(content))

    @Test
    fun `empty content round-trips`() {
        val src = NoteContent.empty()
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `plain text block round-trips`() {
        val src = NoteContent(
            blocks = listOf(Block.TextBlock(id = "b1", text = "hello")),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `heading h1 and h2 round-trip`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", heading = Heading.H1, text = "标题一"),
                Block.TextBlock(id = "b2", heading = Heading.H2, text = "标题二"),
                Block.TextBlock(id = "b3", heading = null, text = "正文"),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `all six span types round-trip`() {
        val spans = listOf(
            TextSpan(0, 2, SpanType.BOLD),
            TextSpan(2, 4, SpanType.ITALIC),
            TextSpan(4, 6, SpanType.UNDERLINE),
            TextSpan(6, 8, SpanType.STRIKETHROUGH),
            TextSpan(8, 10, SpanType.FONT_SIZE, value = "large"),
            TextSpan(10, 12, SpanType.COLOR, value = "#FF0000"),
        )
        val src = NoteContent(
            blocks = listOf(Block.TextBlock(id = "b1", text = "粗斜下划线删字号颜色 ", spans = spans)),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }
}
```

- [ ] **Step 2: 跑测试，预期编译失败**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.json.NoteJsonTextBlockTest"
```

预期：unresolved reference: NoteJson。

- [ ] **Step 3: 实现 `NoteJson.kt`（含 Image / Checklist / Stroke 桩 — 一次成型，后续 Task 5/6/7 加测试）**

> 实现包含全部三种 Block 类型 + Stroke 的序列化。后续 Task 6 和 7 加更多测试，但代码本身一次写完，避免拆得太碎。

文件路径：`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/json/NoteJson.kt`

```kotlin
package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.ChecklistItem
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.NoteContent
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.Stroke
import com.fan.hwnote.app.model.entity.StrokePoint
import com.fan.hwnote.app.model.entity.TextSpan
import org.json.JSONArray
import org.json.JSONObject

/**
 * NoteContent ↔ JSON 序列化。
 *
 * 容错策略：
 * - fromJson 解析失败（顶层 JSON 异常 / 非对象）：返回 NoteContent.empty()
 * - block 类型未知 / 必填字段缺失：跳过该块
 * - span 类型未知：跳过该 span
 * - stroke brush 未知 / points 缺失：跳过该 stroke
 *
 * 持久化格式见 PRD §6.2。
 */
object NoteJson {

    fun toJson(content: NoteContent): String {
        val root = JSONObject()
        val blocksArr = JSONArray()
        for (b in content.blocks) blocksArr.put(blockToJson(b))
        root.put("blocks", blocksArr)

        val hw = JSONObject()
        val strokesArr = JSONArray()
        for (s in content.handwriting) strokesArr.put(strokeToJson(s))
        hw.put("strokes", strokesArr)
        root.put("handwriting", hw)

        return root.toString()
    }

    fun fromJson(s: String): NoteContent {
        return try {
            val root = JSONObject(s)
            val blocks = mutableListOf<Block>()
            val arr = root.optJSONArray("blocks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                blockFromJson(obj)?.let { blocks.add(it) }
            }
            val strokes = mutableListOf<Stroke>()
            val hw = root.optJSONObject("handwriting") ?: JSONObject()
            val sArr = hw.optJSONArray("strokes") ?: JSONArray()
            for (i in 0 until sArr.length()) {
                val obj = sArr.optJSONObject(i) ?: continue
                strokeFromJson(obj)?.let { strokes.add(it) }
            }
            NoteContent(blocks, strokes)
        } catch (_: Exception) {
            NoteContent.empty()
        }
    }

    // ----- Block -----

    private fun blockToJson(b: Block): JSONObject = when (b) {
        is Block.TextBlock -> JSONObject().apply {
            put("type", "text")
            put("id", b.id)
            b.heading?.let { put("heading", it.name.lowercase()) }
            put("text", b.text)
            put("spans", spansToJson(b.spans))
        }
        is Block.ImageBlock -> JSONObject().apply {
            put("type", "image")
            put("id", b.id)
            put("fileName", b.fileName)
            put("width", b.width)
            put("height", b.height)
        }
        is Block.ChecklistBlock -> JSONObject().apply {
            put("type", "checklist")
            put("id", b.id)
            val items = JSONArray()
            for (it in b.items) {
                items.put(JSONObject().apply {
                    put("checked", it.checked)
                    put("text", it.text)
                })
            }
            put("items", items)
        }
    }

    private fun blockFromJson(o: JSONObject): Block? = when (o.optString("type")) {
        "text" -> Block.TextBlock(
            id = o.optString("id"),
            heading = o.optString("heading", "")
                .takeIf { it.isNotEmpty() }
                ?.let { runCatching { Heading.valueOf(it.uppercase()) }.getOrNull() },
            text = o.optString("text"),
            spans = spansFromJson(o.optJSONArray("spans") ?: JSONArray()),
        )
        "image" -> Block.ImageBlock(
            id = o.optString("id"),
            fileName = o.optString("fileName"),
            width = o.optInt("width"),
            height = o.optInt("height"),
        )
        "checklist" -> {
            val items = mutableListOf<ChecklistItem>()
            val arr = o.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                items.add(ChecklistItem(
                    checked = it.optBoolean("checked"),
                    text = it.optString("text"),
                ))
            }
            Block.ChecklistBlock(id = o.optString("id"), items = items)
        }
        else -> null
    }

    // ----- TextSpan -----

    private fun spansToJson(spans: List<TextSpan>): JSONArray {
        val arr = JSONArray()
        for (sp in spans) {
            arr.put(JSONObject().apply {
                put("start", sp.start)
                put("end", sp.end)
                put("type", sp.type.name.lowercase())
                sp.value?.let { put("value", it) }
            })
        }
        return arr
    }

    private fun spansFromJson(arr: JSONArray): List<TextSpan> {
        val out = mutableListOf<TextSpan>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = runCatching {
                SpanType.valueOf(o.optString("type").uppercase())
            }.getOrNull() ?: continue
            out.add(TextSpan(
                start = o.optInt("start"),
                end = o.optInt("end"),
                type = type,
                value = if (o.has("value")) o.optString("value") else null,
            ))
        }
        return out
    }

    // ----- Stroke -----

    private fun strokeToJson(s: Stroke): JSONObject = JSONObject().apply {
        put("brush", s.brush.name.lowercase())
        put("color", s.color)
        put("width", s.width)
        val pts = JSONArray()
        for (p in s.points) {
            pts.put(JSONArray().apply {
                put(p.x); put(p.y); put(p.t)
            })
        }
        put("points", pts)
    }

    private fun strokeFromJson(o: JSONObject): Stroke? {
        val brush = runCatching {
            BrushType.valueOf(o.optString("brush").uppercase())
        }.getOrNull() ?: return null
        val arr = o.optJSONArray("points") ?: return null
        val pts = mutableListOf<StrokePoint>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONArray(i) ?: continue
            if (p.length() < 3) continue
            pts.add(StrokePoint(
                x = p.optInt(0),
                y = p.optInt(1),
                t = p.optInt(2),
            ))
        }
        return Stroke(
            brush = brush,
            color = o.optString("color"),
            width = o.optInt("width"),
            points = pts,
        )
    }
}
```

- [ ] **Step 4: 跑测试，预期 4 项通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.json.NoteJsonTextBlockTest"
```

预期：4 PASSED。

- [ ] **Step 5: commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/json/ \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonTextBlockTest.kt
git commit -m "feat(m2): NoteJson + TextBlock 序列化（heading + 6 spans）"
```

---

## Task 6: NoteJson — Image + Checklist 块（验证已写实现）

**Files:**
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonImageChecklistTest.kt`

> NoteJson.kt 在 Task 5 已经写完整了，这里只补测试覆盖到 Image / Checklist 路径。

- [ ] **Step 1: 写测试**

文件路径：`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonImageChecklistTest.kt`

```kotlin
package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.ChecklistItem
import com.fan.hwnote.app.model.entity.NoteContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteJsonImageChecklistTest {

    private fun roundTrip(content: NoteContent): NoteContent =
        NoteJson.fromJson(NoteJson.toJson(content))

    @Test
    fun `image block round-trips`() {
        val src = NoteContent(
            blocks = listOf(
                Block.ImageBlock(id = "b1", fileName = "9c2f.jpg", width = 1080, height = 720),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `checklist block round-trips with mixed checked states`() {
        val src = NoteContent(
            blocks = listOf(
                Block.ChecklistBlock(
                    id = "b1",
                    items = mutableListOf(
                        ChecklistItem(checked = true, text = "确认排期"),
                        ChecklistItem(checked = false, text = "联系李雷"),
                        ChecklistItem(checked = false, text = ""),  // 空项也保留
                    ),
                ),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `mixed block types preserve ordering`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "t1", text = "首段"),
                Block.ImageBlock(id = "i1", fileName = "a.jpg", width = 100, height = 100),
                Block.ChecklistBlock(
                    id = "c1",
                    items = mutableListOf(ChecklistItem(checked = true, text = "x")),
                ),
                Block.TextBlock(id = "t2", text = "末段"),
            ),
            handwriting = emptyList(),
        )
        val rt = roundTrip(src)
        assertEquals(src, rt)
        assertEquals(listOf("t1", "i1", "c1", "t2"), rt.blocks.map { it.id })
    }
}
```

- [ ] **Step 2: 跑测试，预期 3 项通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.json.NoteJsonImageChecklistTest"
```

预期：3 PASSED。

- [ ] **Step 3: commit**

```bash
git add code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonImageChecklistTest.kt
git commit -m "test(m2): NoteJson Image/Checklist 块覆盖"
```

---

## Task 7: NoteJson — Stroke 4 笔种序列化

**Files:**
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonStrokeTest.kt`

- [ ] **Step 1: 写测试**

文件路径：`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonStrokeTest.kt`

```kotlin
package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.NoteContent
import com.fan.hwnote.app.model.entity.Stroke
import com.fan.hwnote.app.model.entity.StrokePoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteJsonStrokeTest {

    private fun roundTrip(content: NoteContent): NoteContent =
        NoteJson.fromJson(NoteJson.toJson(content))

    @Test
    fun `single stroke round-trips`() {
        val src = NoteContent(
            blocks = emptyList(),
            handwriting = listOf(
                Stroke(
                    brush = BrushType.PEN,
                    color = "#000000",
                    width = 3,
                    points = listOf(
                        StrokePoint(120, 80, 17),
                        StrokePoint(125, 82, 38),
                        StrokePoint(130, 86, 55),
                    ),
                ),
            ),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `all four brush types round-trip`() {
        val src = NoteContent(
            blocks = emptyList(),
            handwriting = listOf(
                Stroke(BrushType.PEN, "#000000", 1, listOf(StrokePoint(0, 0, 0), StrokePoint(10, 10, 5))),
                Stroke(BrushType.BRUSH, "#FF0000", 3, listOf(StrokePoint(0, 0, 0), StrokePoint(20, 5, 8))),
                Stroke(BrushType.MARKER, "#00FF00", 6, listOf(StrokePoint(50, 50, 0), StrokePoint(60, 55, 12))),
                Stroke(BrushType.PENCIL, "#0000FF", 1, listOf(StrokePoint(100, 100, 0), StrokePoint(110, 105, 9))),
            ),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `multiple strokes preserve order`() {
        val src = NoteContent(
            blocks = emptyList(),
            handwriting = listOf(
                Stroke(BrushType.PEN, "#111111", 1, listOf(StrokePoint(0, 0, 0))),
                Stroke(BrushType.PEN, "#222222", 1, listOf(StrokePoint(0, 0, 0))),
                Stroke(BrushType.PEN, "#333333", 1, listOf(StrokePoint(0, 0, 0))),
            ),
        )
        val rt = roundTrip(src)
        assertEquals(listOf("#111111", "#222222", "#333333"), rt.handwriting.map { it.color })
    }
}
```

- [ ] **Step 2: 跑测试，预期 3 项通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.json.NoteJsonStrokeTest"
```

预期：3 PASSED。

- [ ] **Step 3: commit**

```bash
git add code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonStrokeTest.kt
git commit -m "test(m2): NoteJson Stroke 4 笔种 round-trip"
```

---

## Task 8: NoteJson — fromJson 容错

**Files:**
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonResilienceTest.kt`

- [ ] **Step 1: 写测试**

文件路径：`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonResilienceTest.kt`

```kotlin
package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.NoteContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteJsonResilienceTest {

    @Test
    fun `malformed json returns empty content`() {
        val rt = NoteJson.fromJson("not a json {")
        assertEquals(NoteContent.empty(), rt)
    }

    @Test
    fun `empty string returns empty content`() {
        val rt = NoteJson.fromJson("")
        assertEquals(NoteContent.empty(), rt)
    }

    @Test
    fun `missing blocks field defaults to empty list`() {
        val rt = NoteJson.fromJson("""{"handwriting":{"strokes":[]}}""")
        assertTrue(rt.blocks.isEmpty())
    }

    @Test
    fun `missing handwriting field defaults to empty list`() {
        val rt = NoteJson.fromJson("""{"blocks":[]}""")
        assertTrue(rt.handwriting.isEmpty())
    }

    @Test
    fun `unknown block type is skipped`() {
        val json = """
            {
              "blocks": [
                { "type": "text", "id": "b1", "text": "kept" },
                { "type": "weird", "id": "b2" },
                { "type": "text", "id": "b3", "text": "also kept" }
              ],
              "handwriting": { "strokes": [] }
            }
        """.trimIndent()
        val rt = NoteJson.fromJson(json)
        assertEquals(listOf("b1", "b3"), rt.blocks.map { it.id })
    }

    @Test
    fun `unknown span type is dropped but block survives`() {
        val json = """
            {
              "blocks": [
                {
                  "type": "text", "id": "b1", "text": "hello",
                  "spans": [
                    { "start": 0, "end": 1, "type": "bold" },
                    { "start": 1, "end": 2, "type": "rainbow" }
                  ]
                }
              ],
              "handwriting": { "strokes": [] }
            }
        """.trimIndent()
        val rt = NoteJson.fromJson(json)
        assertEquals(1, rt.blocks.size)
        val tb = rt.blocks[0] as com.fan.hwnote.app.model.entity.Block.TextBlock
        assertEquals(1, tb.spans.size)
        assertEquals(com.fan.hwnote.app.model.entity.SpanType.BOLD, tb.spans[0].type)
    }
}
```

- [ ] **Step 2: 跑测试，预期 6 项通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.json.NoteJsonResilienceTest"
```

预期：6 PASSED。如某用例失败，重读 `NoteJson.fromJson` 容错代码。

- [ ] **Step 3: commit**

```bash
git add code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonResilienceTest.kt
git commit -m "test(m2): NoteJson fromJson 容错（损坏/未知类型）"
```

---

## Task 9: NoteDbHelper — 建表 + 索引（Robolectric）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/db/NoteDbHelper.kt`
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/db/NoteDbHelperTest.kt`

> 这是第一个用 Robolectric 的测试。**注意用 `org.junit.Test`（JUnit 4）+ `@RunWith(RobolectricTestRunner::class)`**。Vintage engine 会让它和 JUnit 5 测试一起跑。

- [ ] **Step 1: 写失败的测试**

文件路径：`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/db/NoteDbHelperTest.kt`

```kotlin
package com.fan.hwnote.app.model.db

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteDbHelperTest {

    @Test
    fun `creates notes table with all columns`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase

        val cursor = db.rawQuery("PRAGMA table_info(notes)", null)
        val columns = mutableSetOf<String>()
        cursor.use {
            while (it.moveToNext()) columns.add(it.getString(it.getColumnIndexOrThrow("name")))
        }

        assertEquals(
            setOf("id", "title", "plain_text", "content_json", "is_favorite", "created_at", "updated_at"),
            columns,
        )
    }

    @Test
    fun `creates updated_at and is_favorite indexes`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase

        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='notes'", null,
        )
        val indexes = mutableSetOf<String>()
        cursor.use {
            while (it.moveToNext()) indexes.add(it.getString(0))
        }

        assertTrue("缺索引 idx_notes_updated_at: $indexes", indexes.contains("idx_notes_updated_at"))
        assertTrue("缺索引 idx_notes_favorite: $indexes",  indexes.contains("idx_notes_favorite"))
    }

    @Test
    fun `id is auto-increment primary key`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = NoteDbHelper(ctx)
        val db = helper.writableDatabase

        val now = System.currentTimeMillis()
        val cv1 = android.content.ContentValues().apply {
            put("created_at", now); put("updated_at", now)
        }
        val cv2 = android.content.ContentValues().apply {
            put("created_at", now); put("updated_at", now)
        }
        val id1 = db.insert("notes", null, cv1)
        val id2 = db.insert("notes", null, cv2)

        assertTrue("expected id1 > 0, got $id1", id1 > 0)
        assertTrue("expected id2 > id1, got id1=$id1 id2=$id2", id2 > id1)
    }
}
```

- [ ] **Step 2: 跑测试，预期 unresolved**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.db.NoteDbHelperTest"
```

预期：编译错（unresolved reference: NoteDbHelper）。

- [ ] **Step 3: 实现 `NoteDbHelper.kt`**

文件路径：`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/db/NoteDbHelper.kt`

```kotlin
package com.fan.hwnote.app.model.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite 表结构见 PRD §6.1。
 * MVP v1：单一版本，没有 onUpgrade 路径。
 */
class NoteDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_NOTES)
        db.execSQL(SQL_INDEX_UPDATED)
        db.execSQL(SQL_INDEX_FAVORITE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // MVP v1：尚无升级路径。后续如要加列，在此 ALTER TABLE。
    }

    companion object {
        const val DB_NAME = "hwnote.db"
        const val DB_VERSION = 1

        private const val SQL_CREATE_NOTES = """
            CREATE TABLE notes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL DEFAULT '',
              plain_text TEXT NOT NULL DEFAULT '',
              content_json TEXT NOT NULL DEFAULT '{"blocks":[],"handwriting":{"strokes":[]}}',
              is_favorite INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            )
        """

        private const val SQL_INDEX_UPDATED =
            "CREATE INDEX idx_notes_updated_at ON notes(updated_at DESC)"
        private const val SQL_INDEX_FAVORITE =
            "CREATE INDEX idx_notes_favorite ON notes(is_favorite)"
    }
}
```

- [ ] **Step 4: 跑测试，预期 3 项通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.db.NoteDbHelperTest"
```

预期：3 PASSED。

> **若报错 "no SDK 36"**：检查 `app/src/test/resources/robolectric.properties` 是否存在且内容为 `sdk=34`。
>
> **若报错 "ApplicationProvider not found"**：Robolectric 4.13 应该自带。如缺，加 `testImplementation("androidx.test:core:1.5.0")`。这种情况罕见，先试 Robolectric 自带的。

- [ ] **Step 5: commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/db/ \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/db/
git commit -m "feat(m2): NoteDbHelper 建表 + 索引"
```

---

## Task 10: NoteFileStorage — 文件目录布局

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/storage/NoteFileStorage.kt`
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/storage/NoteFileStorageTest.kt`

- [ ] **Step 1: 写失败的测试**

文件路径：`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/storage/NoteFileStorageTest.kt`

```kotlin
package com.fan.hwnote.app.model.storage

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NoteFileStorageTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `noteDir creates filesDir notes id`() {
        val storage = NoteFileStorage(ctx)
        val dir = storage.noteDir(42L)

        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
        assertEquals(File(ctx.filesDir, "notes/42").absolutePath, dir.absolutePath)
    }

    @Test
    fun `imageDir creates noteDir images and is child of noteDir`() {
        val storage = NoteFileStorage(ctx)
        val imgDir = storage.imageDir(7L)

        assertTrue(imgDir.exists())
        assertEquals("images", imgDir.name)
        assertEquals(File(ctx.filesDir, "notes/7").absolutePath, imgDir.parentFile!!.absolutePath)
    }

    @Test
    fun `imageFile returns child of imageDir`() {
        val storage = NoteFileStorage(ctx)
        val f = storage.imageFile(99L, "abc.jpg")
        assertEquals("abc.jpg", f.name)
        assertEquals("images", f.parentFile!!.name)
    }

    @Test
    fun `deleteNoteDir removes the directory and all contents`() {
        val storage = NoteFileStorage(ctx)
        // setup：创建一些文件
        val imgDir = storage.imageDir(123L)
        File(imgDir, "a.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(imgDir, "b.jpg").writeBytes(byteArrayOf(4, 5, 6))
        assertTrue(File(ctx.filesDir, "notes/123").exists())

        storage.deleteNoteDir(123L)

        assertFalse(File(ctx.filesDir, "notes/123").exists())
    }

    @Test
    fun `deleteNoteDir on nonexistent id is a no-op`() {
        val storage = NoteFileStorage(ctx)
        // 不应抛异常
        storage.deleteNoteDir(99999L)
    }
}
```

- [ ] **Step 2: 跑测试预期 unresolved**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.storage.NoteFileStorageTest"
```

- [ ] **Step 3: 实现 `NoteFileStorage.kt`**

文件路径：`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/storage/NoteFileStorage.kt`

```kotlin
package com.fan.hwnote.app.model.storage

import android.content.Context
import java.io.File

/**
 * 笔记文件目录布局：
 *   filesDir/notes/<noteId>/images/<uuid>.jpg
 *
 * 所有 noteDir / imageDir 调用都是 mkdirs 幂等的，即已存在不创建。
 */
class NoteFileStorage(context: Context) {

    private val filesDir: File = context.applicationContext.filesDir

    fun noteDir(noteId: Long): File {
        val dir = File(filesDir, "notes/$noteId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun imageDir(noteId: Long): File {
        val dir = File(noteDir(noteId), "images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun imageFile(noteId: Long, fileName: String): File =
        File(imageDir(noteId), fileName)

    fun deleteNoteDir(noteId: Long) {
        val dir = File(filesDir, "notes/$noteId")
        if (dir.exists()) dir.deleteRecursively()
    }
}
```

- [ ] **Step 4: 跑测试预期 5 项通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.storage.NoteFileStorageTest"
```

预期：5 PASSED。

- [ ] **Step 5: commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/storage/ \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/storage/
git commit -m "feat(m2): NoteFileStorage 目录布局 + 删除"
```

---

## Task 11: NoteRepository — init + save (insert) + get

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositorySaveGetTest.kt`

> 用 Robolectric + JUnit 4。`runBlocking` 用于在测试中跑 suspend 方法。

- [ ] **Step 1: 写失败的测试**

文件路径：`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositorySaveGetTest.kt`

```kotlin
package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.entity.NoteContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositorySaveGetTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // 确保每个测试拿到干净的 DB（Robolectric 每个测试方法默认新 Context）
        ctx.deleteDatabase("hwnote.db")
        NoteRepository.init(ctx)
    }

    @Test
    fun `save inserts new note and returns positive id`() = runBlocking {
        val note = Note.new(now = 1_000L).copy(
            title = "我的第一条笔记",
            content = NoteContent(
                blocks = listOf(Block.TextBlock(id = "b1", text = "hello world")),
                handwriting = emptyList(),
            ),
        )
        val id = NoteRepository.save(note)
        assertTrue("expected id > 0, got $id", id > 0)
    }

    @Test
    fun `get returns null for unknown id`() = runBlocking {
        assertNull(NoteRepository.get(9999L))
    }

    @Test
    fun `save then get returns note with same content and computed plainText`() = runBlocking {
        val note = Note.new(now = 1_000L).copy(
            title = "T",
            content = NoteContent(
                blocks = listOf(
                    Block.TextBlock(id = "b1", heading = Heading.H1, text = "标题段"),
                    Block.TextBlock(id = "b2", text = "正文段"),
                ),
                handwriting = emptyList(),
            ),
        )
        val id = NoteRepository.save(note)
        val loaded = NoteRepository.get(id)

        assertNotNull(loaded)
        assertEquals("T", loaded!!.title)
        assertEquals("标题段\n正文段", loaded.plainText)
        assertEquals(note.content, loaded.content)
        assertEquals(1_000L, loaded.createdAt)
    }

    @Test
    fun `save existing note preserves createdAt and bumps updatedAt`() = runBlocking {
        val original = Note.new(now = 1_000L).copy(title = "v1")
        val id = NoteRepository.save(original)
        val firstLoad = NoteRepository.get(id)!!

        // 模拟时间过去：update 之前等一点（系统时钟）
        Thread.sleep(5)

        val updated = firstLoad.copy(title = "v2")
        NoteRepository.save(updated)
        val secondLoad = NoteRepository.get(id)!!

        assertEquals("v2", secondLoad.title)
        assertEquals(firstLoad.createdAt, secondLoad.createdAt)  // 不变
        assertNotEquals(firstLoad.updatedAt, secondLoad.updatedAt)  // 变
        assertTrue(secondLoad.updatedAt > firstLoad.updatedAt)
    }
}
```

- [ ] **Step 2: 跑测试预期 unresolved**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.NoteRepositorySaveGetTest"
```

- [ ] **Step 3: 实现 `NoteRepository.kt`（含完整签名 / list / delete / setFavorite —— 一次写完，避免反复改 object 内部）**

文件路径：`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`

```kotlin
package com.fan.hwnote.app.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.fan.hwnote.app.model.db.NoteDbHelper
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.json.NoteJson
import com.fan.hwnote.app.model.storage.NoteFileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据层统一门面。所有 IO 都是 suspend，内部已切到 Dispatchers.IO。
 *
 * 用法：在 App.onCreate 调一次 NoteRepository.init(this)；之后从任何地方 NoteRepository.list() 等。
 *
 * 单例形态：object，状态用 lateinit 注入 — init 可重复调（覆盖之前的 dbHelper / fileStorage）。
 */
object NoteRepository {

    private lateinit var dbHelper: NoteDbHelper
    private lateinit var fileStorage: NoteFileStorage

    fun init(context: Context) {
        val app = context.applicationContext
        dbHelper = NoteDbHelper(app)
        fileStorage = NoteFileStorage(app)
    }

    suspend fun list(
        sortBy: SortBy = SortBy.UPDATED_DESC,
        query: String? = null,
    ): List<Note> = withContext(Dispatchers.IO) {
        val orderBy = when (sortBy) {
            SortBy.UPDATED_DESC -> "updated_at DESC"
            SortBy.CREATED_DESC -> "created_at DESC"
            SortBy.TITLE_ASC -> "title COLLATE NOCASE ASC"
        }
        val (selection, args) = if (!query.isNullOrEmpty()) {
            val like = "%$query%"
            "title LIKE ? OR plain_text LIKE ?" to arrayOf(like, like)
        } else {
            null to null
        }
        val out = mutableListOf<Note>()
        val db = dbHelper.readableDatabase
        val cursor = db.query("notes", null, selection, args, null, null, orderBy)
        cursor.use { c ->
            while (c.moveToNext()) out.add(cursorToNote(c))
        }
        out
    }

    suspend fun get(id: Long): Note? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query("notes", null, "id = ?", arrayOf(id.toString()), null, null, null)
        cursor.use { c ->
            if (!c.moveToFirst()) null else cursorToNote(c)
        }
    }

    /**
     * 保存笔记。
     * - 若 note.id == 0L：插入新行，使用 note.createdAt（>0 时）或 now；返回新生成的 id。
     * - 若 note.id  > 0：UPDATE 该行，createdAt 不动，updatedAt = now；返回原 id。
     */
    suspend fun save(note: Note): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("title", note.title)
            put("plain_text", note.content.toPlainText())
            put("content_json", NoteJson.toJson(note.content))
            put("is_favorite", if (note.isFavorite) 1 else 0)
            put("updated_at", now)
        }
        val db = dbHelper.writableDatabase
        if (note.id == 0L) {
            val createdAt = if (note.createdAt > 0) note.createdAt else now
            cv.put("created_at", createdAt)
            db.insert("notes", null, cv)
        } else {
            db.update("notes", cv, "id = ?", arrayOf(note.id.toString()))
            note.id
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("notes", "id = ?", arrayOf(id.toString()))
        fileStorage.deleteNoteDir(id)
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("is_favorite", if (favorite) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.update("notes", cv, "id = ?", arrayOf(id.toString()))
        Unit
    }

    enum class SortBy { UPDATED_DESC, CREATED_DESC, TITLE_ASC }

    // ----- private -----

    private fun cursorToNote(c: Cursor): Note {
        val contentJson = c.getString(c.getColumnIndexOrThrow("content_json")) ?: ""
        return Note(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
            plainText = c.getString(c.getColumnIndexOrThrow("plain_text")) ?: "",
            isFavorite = c.getInt(c.getColumnIndexOrThrow("is_favorite")) == 1,
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            content = NoteJson.fromJson(contentJson),
        )
    }
}
```

- [ ] **Step 4: 跑测试预期 4 项通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.NoteRepositorySaveGetTest"
```

预期：4 PASSED。

> **若 `Thread.sleep(5)` 之后 updatedAt 仍相等**：Robolectric 的 SystemClock 默认会推进真实时间，5ms 应足够。如不够稳，改成 `Thread.sleep(20)`。

- [ ] **Step 5: commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositorySaveGetTest.kt
git commit -m "feat(m2): NoteRepository save/get + 单例骨架"
```

---

## Task 12: NoteRepository — list + sortBy + search

**Files:**
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryListTest.kt`

> NoteRepository 在 Task 11 已写完整，这里只补测试。

- [ ] **Step 1: 写测试**

文件路径：`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryListTest.kt`

```kotlin
package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.entity.NoteContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryListTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("hwnote.db")
        NoteRepository.init(ctx)
    }

    /** 准备 3 条笔记，标题 / 创建时间 / 修改时间 / plainText 各异 */
    private suspend fun seed(): Triple<Long, Long, Long> {
        val nA = Note.new(now = 1_000L).copy(
            title = "Alpha", isFavorite = false,
            content = NoteContent(
                blocks = listOf(Block.TextBlock("b", text = "李雷今天来了")),
                handwriting = emptyList(),
            ),
        )
        val nB = Note.new(now = 2_000L).copy(
            title = "Beta", isFavorite = true,
            content = NoteContent(
                blocks = listOf(Block.TextBlock("b", text = "韩梅梅明天到")),
                handwriting = emptyList(),
            ),
        )
        val nC = Note.new(now = 3_000L).copy(
            title = "Charlie",
            content = NoteContent(
                blocks = listOf(Block.TextBlock("b", text = "李雷与韩梅梅")),
                handwriting = emptyList(),
            ),
        )
        // 顺序保存 — id 自增；createdAt 通过 Note.new(now) 控制
        // 但 updatedAt 由 save 内部 = System.currentTimeMillis()
        // 为了让 updatedAt 也分散，每次插入间稍 sleep
        val idA = NoteRepository.save(nA); Thread.sleep(5)
        val idB = NoteRepository.save(nB); Thread.sleep(5)
        val idC = NoteRepository.save(nC)
        return Triple(idA, idB, idC)
    }

    @Test
    fun `list default sort is updated_at desc`() = runBlocking {
        val (idA, idB, idC) = seed()
        val list = NoteRepository.list()
        assertEquals(listOf(idC, idB, idA), list.map { it.id })
    }

    @Test
    fun `sort by created_at desc uses createdAt field`() = runBlocking {
        val (idA, idB, idC) = seed()
        val list = NoteRepository.list(sortBy = NoteRepository.SortBy.CREATED_DESC)
        // C(3000) > B(2000) > A(1000)
        assertEquals(listOf(idC, idB, idA), list.map { it.id })
    }

    @Test
    fun `sort by title ascending uses title order`() = runBlocking {
        val (idA, idB, idC) = seed()
        val list = NoteRepository.list(sortBy = NoteRepository.SortBy.TITLE_ASC)
        assertEquals(listOf(idA, idB, idC), list.map { it.id })  // Alpha / Beta / Charlie
    }

    @Test
    fun `search matches title`() = runBlocking {
        seed()
        val list = NoteRepository.list(query = "Alpha")
        assertEquals(1, list.size)
        assertEquals("Alpha", list[0].title)
    }

    @Test
    fun `search matches plain_text`() = runBlocking {
        seed()
        val list = NoteRepository.list(query = "李雷")
        // A 和 C 的 plainText 含李雷
        assertEquals(2, list.size)
        assertTrue(list.all { it.plainText.contains("李雷") })
    }

    @Test
    fun `search with no hit returns empty`() = runBlocking {
        seed()
        val list = NoteRepository.list(query = "找不到的关键词")
        assertTrue(list.isEmpty())
    }
}
```

- [ ] **Step 2: 跑测试预期 6 项通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.NoteRepositoryListTest"
```

预期：6 PASSED。

- [ ] **Step 3: commit**

```bash
git add code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryListTest.kt
git commit -m "test(m2): NoteRepository list/sortBy/search 覆盖"
```

---

## Task 13: NoteRepository — delete + setFavorite

**Files:**
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryDeleteFavoriteTest.kt`

- [ ] **Step 1: 写测试**

文件路径：`code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryDeleteFavoriteTest.kt`

```kotlin
package com.fan.hwnote.app.model

import androidx.test.core.app.ApplicationProvider
import com.fan.hwnote.app.model.entity.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryDeleteFavoriteTest {

    private lateinit var ctx: android.content.Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase("hwnote.db")
        // 清残留文件
        File(ctx.filesDir, "notes").deleteRecursively()
        NoteRepository.init(ctx)
    }

    @Test
    fun `delete removes db row`() = runBlocking {
        val id = NoteRepository.save(Note.new(now = 1_000L).copy(title = "to delete"))
        assertTrue(NoteRepository.list().any { it.id == id })

        NoteRepository.delete(id)

        assertNull(NoteRepository.get(id))
        assertFalse(NoteRepository.list().any { it.id == id })
    }

    @Test
    fun `delete also removes filesDir notes id directory`() = runBlocking {
        val id = NoteRepository.save(Note.new(now = 1_000L).copy(title = "with image"))
        // 模拟图片：手动创建目录 + 文件
        val noteDir = File(ctx.filesDir, "notes/$id")
        File(noteDir, "images").mkdirs()
        File(noteDir, "images/x.jpg").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(noteDir.exists())

        NoteRepository.delete(id)

        assertFalse(noteDir.exists())
    }

    @Test
    fun `setFavorite true makes note favorite`() = runBlocking {
        val id = NoteRepository.save(Note.new(now = 1_000L).copy(title = "n", isFavorite = false))
        NoteRepository.setFavorite(id, true)
        val loaded = NoteRepository.get(id)!!
        assertTrue(loaded.isFavorite)
    }

    @Test
    fun `setFavorite false unmarks favorite`() = runBlocking {
        val id = NoteRepository.save(Note.new(now = 1_000L).copy(title = "n", isFavorite = true))
        NoteRepository.setFavorite(id, false)
        val loaded = NoteRepository.get(id)!!
        assertFalse(loaded.isFavorite)
    }
}
```

- [ ] **Step 2: 跑测试预期 4 项通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.model.NoteRepositoryDeleteFavoriteTest"
```

预期：4 PASSED。

- [ ] **Step 3: commit**

```bash
git add code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/NoteRepositoryDeleteFavoriteTest.kt
git commit -m "test(m2): NoteRepository delete + setFavorite 覆盖"
```

---

## Task 14: 接入 App.onCreate + 全测试 + STATUS + 收尾 commit

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/App.kt`
- Modify: `docs/superpowers/STATUS.md`

- [ ] **Step 1: 修改 `App.kt`，调 `NoteRepository.init(this)`**

文件路径：`code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/App.kt`

```kotlin
package com.fan.hwnote.app

import android.app.Application
import com.fan.hwnote.app.model.NoteRepository

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        NoteRepository.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
```

- [ ] **Step 2: 全测试套跑通**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote
./gradlew :app:test
```

预期：BUILD SUCCESSFUL。统计应该 ≥ **42 个测试通过**：
- SmokeTest: 1
- NoteContentTest: 3
- NoteJsonTextBlockTest: 4
- NoteJsonImageChecklistTest: 3
- NoteJsonStrokeTest: 3
- NoteJsonResilienceTest: 6
- NoteDbHelperTest: 3
- NoteFileStorageTest: 5
- NoteRepositorySaveGetTest: 4
- NoteRepositoryListTest: 6
- NoteRepositoryDeleteFavoriteTest: 4
- **合计：42 项**

- [ ] **Step 3: 顺手跑构建确保 release 不破**

```bash
./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 4: 真机安装一次（不必跑测试，仅冒烟启动 App，确认 NoteRepository.init 不崩）**

```bash
./gradlew :app:installDebug
adb shell am start -W -S -n com.fan.hwnote.app/com.fan.hwnote.app.controller.list.NoteListActivity
```

预期：App 启动，主屏 "还没有笔记" 仍出现，无 ANR / 崩溃。可顺带 `adb logcat | grep -i "fatal\|noterepo"` 验证。

- [ ] **Step 5: 更新 `docs/superpowers/STATUS.md`**

把 M2 阶段从 ⏳ 改为 ✅，并加 "M2 完成详情" 节（参考 M1 完成详情节的格式）。具体改动：

- 阶段地图表中 7. 进行中（M1 完成）→ 进行中（M2 完成）
- 里程碑进度表中 M2 数据层 → ✅ 完成（2026-MM-DD）
- 加 "## M2 完成详情" 节列出测试统计 + commit hash 范围
- 下一步建议改为 M3 列表页

- [ ] **Step 6: 收尾 commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/App.kt \
        docs/superpowers/STATUS.md
git commit -m "feat(m2): App 接入 NoteRepository.init + STATUS 收尾"
git log --oneline -10  # 确认 M2 全部 commit 都在
```

---

## 完成定义（DoD）

- [ ] `./gradlew :app:test` 全部通过（≥ 42 项）
- [ ] `./gradlew :app:assembleDebug` 通过
- [ ] App 真机启动不崩（`NoteRepository.init` OK）
- [ ] `docs/superpowers/STATUS.md` 已更新
- [ ] 所有 M2 提交已 commit（git log 可见）
- [ ] 用户验收

---

## 已知技术备注 / 后续可能的复审点

1. **Block 内字段是 `var`**：编辑器 View 直接改。M4 接入后若发现并发问题（多个 View 共享同一 Block 引用），届时考虑改 val + .copy。当前 M2 阶段暂不动。
2. **Note 的 `id == 0L` 表示未持久化**：SQLite AUTOINCREMENT 起始为 1，所以 0L 永远不冲突。这是惯用约定。
3. **search 是 LIKE %query% 的双向匹配**：标题 + plain_text。性能上 SQLite 在 plain_text 上没建 FTS，行数大时（>1000 条）可能慢。MVP 不优化，v2 视情况加 FTS5 表。
4. **Robolectric 锁 sdk=34**：因为我们 compileSdk=36 但 Robolectric 4.13 内置最高 34。等 Robolectric 升级后可移除 `robolectric.properties` 或改 sdk=36。
5. **NoteRepository 是 object 单例**：测试时通过 `init(ctx)` 重新注入；在 setUp 调用 `ctx.deleteDatabase("hwnote.db")` 保证测试间隔离。
6. **`updatedAt` 由 save 内部统一设为 `System.currentTimeMillis()`**：传入的 Note.updatedAt 字段被忽略，避免上层错传脏值。createdAt 同理但只在新建时取值。
7. **NoteJson 是 round-trip 安全的**：toJson 输出 → fromJson → 与原 NoteContent equals。所有 6 种 span / 4 种 brush / heading / 三种 block 都已测试覆盖。
