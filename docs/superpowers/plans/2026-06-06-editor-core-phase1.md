# editor-core Phase 1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建 editor-core SDK 模块 — 纯 Kotlin、零平台依赖，定义 Block Editor 的文档模型、状态管理、操作系统与序列化。

**Architecture:** 借鉴 ProseMirror 架构——不可变 EditorState + 显式 Operation（自带 inverse）+ 声明式 Schema + Transaction 驱动状态转换。editor-core 对 UI 框架零感知，可被 Compose / SwiftUI / ArkUI 任意渲染层消费。

**Tech Stack:** Kotlin 2.0.21（KMP commonMain）、kotlinx-serialization-json 1.7.3

---

## 文件结构

```
code/HuaWeiNote/
├── editor-core/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/fan/blockeditor/core/
│       │   ├── document/
│       │   │   ├── BlockNode.kt          ← BlockNode 接口
│       │   │   ├── BuiltinNodes.kt       ← 4 种内置块类型 + ChecklistItem
│       │   │   ├── Document.kt           ← 文档容器
│       │   │   ├── Mark.kt               ← 行内标注
│       │   │   ├── Stroke.kt             ← 手写数据（Stroke/StrokePoint/BrushType）
│       │   │   └── Types.kt              ← NodeType, MarkType, 枚举（Heading 等）
│       │   ├── schema/
│       │   │   └── Schema.kt             ← NodeSpec, MarkSpec, Schema, BuiltinSchema
│       │   ├── operation/
│       │   │   └── Operation.kt          ← 4 种原子操作 + inverse()
│       │   ├── state/
│       │   │   ├── EditorState.kt        ← 不可变编辑器状态
│       │   │   ├── Selection.kt          ← 选区模型
│       │   │   └── Transaction.kt        ← 事务：构建操作 + 产出新状态
│       │   ├── history/
│       │   │   └── HistoryManager.kt     ← 撤销重做（基于 Operation 序列）
│       │   └── codec/
│       │       └── JsonCodec.kt          ← Document ↔ JSON（kotlinx-serialization）
│       └── commonTest/kotlin/com/fan/blockeditor/core/
│           ├── document/
│           │   ├── TypesTest.kt
│           │   ├── BuiltinNodesTest.kt
│           │   └── DocumentTest.kt
│           ├── schema/
│           │   └── SchemaTest.kt
│           ├── operation/
│           │   └── OperationTest.kt
│           ├── state/
│           │   ├── EditorStateTest.kt
│           │   └── TransactionTest.kt
│           ├── history/
│           │   └── HistoryManagerTest.kt
│           └── codec/
│               └── JsonCodecTest.kt
├── app/                                   ← 本阶段不修改
├── build.gradle.kts                       ← 修改：添加 KMP + serialization 插件声明
├── settings.gradle.kts                    ← 修改：include(":editor-core")
└── gradle/libs.versions.toml             ← 修改：添加新依赖版本
```

## 构建与测试命令

```bash
# 工作目录：code/HuaWeiNote/

# 构建 editor-core
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:build

# 跑全部测试
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest

# 跑单个测试类
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest --tests "com.fan.blockeditor.core.document.DocumentTest"

# 确保 app 模块不受影响
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:test
```

## 设计约定

- **范围标注**：Mark 和文本操作的 `start` / `end` 均为 **[start, end) 左闭右开**，与 `String.substring` 语义一致
- **BlockNode**：非 sealed 接口，允许未来扩展自定义块类型；`when` 匹配时使用 `is` 检查 + `else` 分支
- **Operation**：sealed class，所有 `when` 表达式必须显式列举每个子类，禁止 `else`
- **Selection**：sealed class，同上规则
- **不可变**：Document / BlockNode / EditorState / Operation 全部不可变（data class / val only）

---

### Task 1: 搭建 KMP editor-core 模块

**Files:**
- Modify: `gradle/libs.versions.toml` — 添加 KMP 插件 + kotlinx-serialization 依赖
- Modify: `build.gradle.kts`（root）— 声明新插件 apply false
- Modify: `settings.gradle.kts` — include(":editor-core")
- Create: `editor-core/build.gradle.kts`
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/Placeholder.kt`
- Test: `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/PlaceholderTest.kt`

- [ ] **Step 1: 添加版本目录条目**

在 `gradle/libs.versions.toml` 中：

```toml
# ── [versions] 区追加 ──
kotlinxSerialization = "1.7.3"

# ── [libraries] 区追加 ──
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

# ── [plugins] 区追加 ──
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: 根 build.gradle.kts 声明新插件**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 3: settings.gradle.kts 引入新模块**

在 `include(":app")` 后追加：

```kotlin
include(":editor-core")
```

- [ ] **Step 4: 创建 editor-core/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

- [ ] **Step 5: 创建占位源文件验证构建**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/Placeholder.kt`:

```kotlin
package com.fan.blockeditor.core

internal object Placeholder
```

`editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/PlaceholderTest.kt`:

```kotlin
package com.fan.blockeditor.core

import kotlin.test.Test
import kotlin.test.assertNotNull

class PlaceholderTest {
    @Test
    fun moduleLoads() {
        assertNotNull(Placeholder)
    }
}
```

- [ ] **Step 6: 验证 editor-core 构建**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: BUILD SUCCESSFUL, 1 test passed

- [ ] **Step 7: 确认 app 模块不受影响**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 提交**

```bash
git add gradle/libs.versions.toml build.gradle.kts settings.gradle.kts editor-core/
git commit -m "feat(editor-core): 搭建 KMP editor-core 模块骨架"
```

---

### Task 2: 核心值类型 — NodeType、MarkType、Mark、枚举

**Files:**
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/Types.kt`
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/Mark.kt`
- Test: `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/document/TypesTest.kt`

- [ ] **Step 1: 写失败测试**

`editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/document/TypesTest.kt`:

```kotlin
package com.fan.blockeditor.core.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TypesTest {

    @Test
    fun nodeTypeEquality() {
        assertEquals(NodeType("text"), NodeType("text"))
        assertNotEquals(NodeType("text"), NodeType("image"))
    }

    @Test
    fun markTypeEquality() {
        assertEquals(MarkType("bold"), MarkType("bold"))
        assertNotEquals(MarkType("bold"), MarkType("italic"))
    }

    @Test
    fun builtinNodeTypes() {
        assertEquals("text", BuiltinNodeTypes.TEXT.name)
        assertEquals("image", BuiltinNodeTypes.IMAGE.name)
        assertEquals("checklist", BuiltinNodeTypes.CHECKLIST.name)
        assertEquals("audio", BuiltinNodeTypes.AUDIO.name)
    }

    @Test
    fun builtinMarkTypes() {
        assertEquals("bold", BuiltinMarkTypes.BOLD.name)
        assertEquals("italic", BuiltinMarkTypes.ITALIC.name)
        assertEquals("underline", BuiltinMarkTypes.UNDERLINE.name)
        assertEquals("strikethrough", BuiltinMarkTypes.STRIKETHROUGH.name)
        assertEquals("fontSize", BuiltinMarkTypes.FONT_SIZE.name)
        assertEquals("color", BuiltinMarkTypes.COLOR.name)
    }

    @Test
    fun markConstruction() {
        val mark = Mark(type = BuiltinMarkTypes.BOLD, start = 0, end = 5)
        assertEquals(BuiltinMarkTypes.BOLD, mark.type)
        assertEquals(0, mark.start)
        assertEquals(5, mark.end)
        assertTrue(mark.attrs.isEmpty())
    }

    @Test
    fun markWithAttrs() {
        val mark = Mark(
            type = BuiltinMarkTypes.COLOR,
            start = 2,
            end = 8,
            attrs = mapOf("color" to "#FF0000"),
        )
        assertEquals("#FF0000", mark.attrs["color"])
    }

    @Test
    fun headingValues() {
        assertEquals(6, Heading.entries.size)
        assertEquals("H1", Heading.H1.name)
    }

    @Test
    fun alignmentValues() {
        assertEquals(3, Alignment.entries.size)
    }

    @Test
    fun listTypeValues() {
        assertEquals(4, ListType.entries.size)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: FAIL — 编译错误，NodeType 等类型不存在

- [ ] **Step 3: 实现核心值类型**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/Types.kt`:

```kotlin
package com.fan.blockeditor.core.document

data class NodeType(val name: String)

data class MarkType(val name: String)

object BuiltinNodeTypes {
    val TEXT = NodeType("text")
    val IMAGE = NodeType("image")
    val CHECKLIST = NodeType("checklist")
    val AUDIO = NodeType("audio")
}

object BuiltinMarkTypes {
    val BOLD = MarkType("bold")
    val ITALIC = MarkType("italic")
    val UNDERLINE = MarkType("underline")
    val STRIKETHROUGH = MarkType("strikethrough")
    val FONT_SIZE = MarkType("fontSize")
    val COLOR = MarkType("color")
}

enum class Heading { H1, H2, H3, H4, H5, H6 }

enum class Alignment { START, CENTER, END }

enum class ListType { BULLET, HOLLOW_BULLET, NUMBERED, LETTERED }
```

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/Mark.kt`:

```kotlin
package com.fan.blockeditor.core.document

/** 行内标注。范围 [start, end) 左闭右开。 */
data class Mark(
    val type: MarkType,
    val start: Int,
    val end: Int,
    val attrs: Map<String, String> = emptyMap(),
)
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest --tests "com.fan.blockeditor.core.document.TypesTest"
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add editor-core/src/
git commit -m "feat(editor-core): 添加核心值类型 NodeType/MarkType/Mark/枚举"
```

---

### Task 3: BlockNode 接口 + 内置块类型

**Files:**
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/BlockNode.kt`
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/BuiltinNodes.kt`
- Test: `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/document/BuiltinNodesTest.kt`

- [ ] **Step 1: 写失败测试**

`editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/document/BuiltinNodesTest.kt`:

```kotlin
package com.fan.blockeditor.core.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuiltinNodesTest {

    @Test
    fun textBlockNodeDefaults() {
        val node = TextBlockNode(key = "b1")
        assertEquals("b1", node.key)
        assertEquals(BuiltinNodeTypes.TEXT, node.type)
        assertEquals("", node.text)
        assertTrue(node.marks.isEmpty())
        assertNull(node.heading)
        assertNull(node.alignment)
        assertNull(node.listType)
        assertEquals(0, node.indentLevel)
    }

    @Test
    fun textBlockNodeWithContent() {
        val marks = listOf(Mark(BuiltinMarkTypes.BOLD, 0, 5))
        val node = TextBlockNode(
            key = "b2",
            text = "Hello World",
            marks = marks,
            heading = Heading.H1,
            alignment = Alignment.CENTER,
        )
        assertEquals("Hello World", node.text)
        assertEquals(1, node.marks.size)
        assertEquals(Heading.H1, node.heading)
        assertEquals(Alignment.CENTER, node.alignment)
    }

    @Test
    fun textBlockNodeWithList() {
        val node = TextBlockNode(
            key = "b3",
            text = "Item one",
            listType = ListType.BULLET,
            indentLevel = 2,
        )
        assertEquals(ListType.BULLET, node.listType)
        assertEquals(2, node.indentLevel)
    }

    @Test
    fun imageBlockNode() {
        val node = ImageBlockNode(
            key = "img1",
            fileName = "photo.jpg",
            width = 1920,
            height = 1080,
        )
        assertEquals(BuiltinNodeTypes.IMAGE, node.type)
        assertEquals("photo.jpg", node.fileName)
        assertEquals(1920, node.width)
        assertEquals(1080, node.height)
    }

    @Test
    fun checklistBlockNode() {
        val items = listOf(
            ChecklistItem(checked = false, text = "Buy milk"),
            ChecklistItem(checked = true, text = "Write code"),
        )
        val node = ChecklistBlockNode(key = "cl1", items = items)
        assertEquals(BuiltinNodeTypes.CHECKLIST, node.type)
        assertEquals(2, node.items.size)
        assertEquals(false, node.items[0].checked)
        assertEquals(true, node.items[1].checked)
        assertEquals("Write code", node.items[1].text)
    }

    @Test
    fun audioBlockNode() {
        val node = AudioBlockNode(
            key = "a1",
            fileName = "recording.m4a",
            durationMs = 30000L,
        )
        assertEquals(BuiltinNodeTypes.AUDIO, node.type)
        assertEquals("recording.m4a", node.fileName)
        assertEquals(30000L, node.durationMs)
    }

    @Test
    fun blockNodePolymorphism() {
        val nodes: List<BlockNode> = listOf(
            TextBlockNode(key = "1"),
            ImageBlockNode(key = "2", fileName = "x.png", width = 100, height = 100),
            ChecklistBlockNode(key = "3"),
            AudioBlockNode(key = "4", fileName = "x.m4a", durationMs = 1000),
        )
        assertEquals(4, nodes.size)
        assertEquals(BuiltinNodeTypes.TEXT, nodes[0].type)
        assertEquals(BuiltinNodeTypes.IMAGE, nodes[1].type)
        assertEquals(BuiltinNodeTypes.CHECKLIST, nodes[2].type)
        assertEquals(BuiltinNodeTypes.AUDIO, nodes[3].type)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: FAIL — 编译错误

- [ ] **Step 3: 实现 BlockNode 接口**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/BlockNode.kt`:

```kotlin
package com.fan.blockeditor.core.document

interface BlockNode {
    val key: String
    val type: NodeType
}
```

- [ ] **Step 4: 实现内置块类型**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/BuiltinNodes.kt`:

```kotlin
package com.fan.blockeditor.core.document

data class TextBlockNode(
    override val key: String,
    val text: String = "",
    val marks: List<Mark> = emptyList(),
    val heading: Heading? = null,
    val alignment: Alignment? = null,
    val listType: ListType? = null,
    val indentLevel: Int = 0,
) : BlockNode {
    override val type: NodeType = BuiltinNodeTypes.TEXT
}

data class ImageBlockNode(
    override val key: String,
    val fileName: String,
    val width: Int,
    val height: Int,
) : BlockNode {
    override val type: NodeType = BuiltinNodeTypes.IMAGE
}

data class ChecklistItem(val checked: Boolean, val text: String)

data class ChecklistBlockNode(
    override val key: String,
    val items: List<ChecklistItem> = emptyList(),
) : BlockNode {
    override val type: NodeType = BuiltinNodeTypes.CHECKLIST
}

data class AudioBlockNode(
    override val key: String,
    val fileName: String,
    val durationMs: Long,
) : BlockNode {
    override val type: NodeType = BuiltinNodeTypes.AUDIO
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest --tests "com.fan.blockeditor.core.document.BuiltinNodesTest"
```

Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add editor-core/src/
git commit -m "feat(editor-core): 添加 BlockNode 接口 + 4 种内置块类型"
```

---

### Task 4: Stroke + Document 容器

**Files:**
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/Stroke.kt`
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/Document.kt`
- Test: `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/document/DocumentTest.kt`

- [ ] **Step 1: 写失败测试**

`editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/document/DocumentTest.kt`:

```kotlin
package com.fan.blockeditor.core.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentTest {

    @Test
    fun emptyDocument() {
        val doc = Document.empty()
        assertTrue(doc.children.isEmpty())
        assertTrue(doc.handwriting.isEmpty())
    }

    @Test
    fun documentWithBlocks() {
        val blocks = listOf(
            TextBlockNode(key = "b1", text = "Hello"),
            ImageBlockNode(key = "b2", fileName = "pic.png", width = 800, height = 600),
        )
        val doc = Document(children = blocks)
        assertEquals(2, doc.children.size)
        assertEquals("b1", doc.children[0].key)
    }

    @Test
    fun documentWithHandwriting() {
        val strokes = listOf(
            Stroke(
                brush = BrushType.PEN,
                color = "#000000",
                width = 3,
                points = listOf(StrokePoint(0, 0, 0), StrokePoint(10, 10, 50)),
            ),
        )
        val doc = Document(children = emptyList(), handwriting = strokes)
        assertEquals(1, doc.handwriting.size)
        assertEquals(BrushType.PEN, doc.handwriting[0].brush)
        assertEquals(2, doc.handwriting[0].points.size)
    }

    @Test
    fun documentToPlainText() {
        val doc = Document(
            children = listOf(
                TextBlockNode(key = "1", text = "First line"),
                ImageBlockNode(key = "2", fileName = "x.png", width = 1, height = 1),
                TextBlockNode(key = "3", text = "Third line"),
                ChecklistBlockNode(
                    key = "4",
                    items = listOf(
                        ChecklistItem(false, "Todo A"),
                        ChecklistItem(true, "Todo B"),
                    ),
                ),
                AudioBlockNode(key = "5", fileName = "x.m4a", durationMs = 1000),
            ),
        )
        assertEquals("First line\nThird line\nTodo A\nTodo B", doc.toPlainText())
    }

    @Test
    fun emptyTextBlockSkippedInPlainText() {
        val doc = Document(
            children = listOf(
                TextBlockNode(key = "1", text = ""),
                TextBlockNode(key = "2", text = "Content"),
            ),
        )
        assertEquals("Content", doc.toPlainText())
    }

    @Test
    fun strokePointConstruction() {
        val point = StrokePoint(x = 100, y = 200, t = 50)
        assertEquals(100, point.x)
        assertEquals(200, point.y)
        assertEquals(50, point.t)
    }

    @Test
    fun brushTypeValues() {
        assertEquals(4, BrushType.entries.size)
    }

    @Test
    fun documentImmutability() {
        val doc1 = Document(children = listOf(TextBlockNode(key = "1", text = "A")))
        val doc2 = doc1.copy(children = doc1.children + TextBlockNode(key = "2", text = "B"))
        assertEquals(1, doc1.children.size)
        assertEquals(2, doc2.children.size)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: FAIL — Stroke / Document 不存在

- [ ] **Step 3: 实现 Stroke 类型**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/Stroke.kt`:

```kotlin
package com.fan.blockeditor.core.document

data class Stroke(
    val brush: BrushType,
    val color: String,
    val width: Int,
    val points: List<StrokePoint>,
)

data class StrokePoint(val x: Int, val y: Int, val t: Int)

enum class BrushType { PEN, BRUSH, MARKER, PENCIL }
```

- [ ] **Step 4: 实现 Document**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/document/Document.kt`:

```kotlin
package com.fan.blockeditor.core.document

data class Document(
    val children: List<BlockNode>,
    val handwriting: List<Stroke> = emptyList(),
) {
    fun toPlainText(): String {
        val parts = mutableListOf<String>()
        for (child in children) {
            when (child) {
                is TextBlockNode -> if (child.text.isNotEmpty()) parts.add(child.text)
                is ChecklistBlockNode -> child.items.forEach {
                    if (it.text.isNotEmpty()) parts.add(it.text)
                }
            }
        }
        return parts.joinToString("\n")
    }

    companion object {
        fun empty(): Document = Document(children = emptyList())
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest --tests "com.fan.blockeditor.core.document.DocumentTest"
```

Expected: PASS

- [ ] **Step 6: 删除 Task 1 的占位文件**

删除 `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/Placeholder.kt` 和 `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/PlaceholderTest.kt`。

- [ ] **Step 7: 提交**

```bash
git add editor-core/src/
git commit -m "feat(editor-core): 添加 Stroke/Document + 文档模型完成"
```

---

### Task 5: Schema 系统

**Files:**
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/schema/Schema.kt`
- Test: `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/schema/SchemaTest.kt`

- [ ] **Step 1: 写失败测试**

`editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/schema/SchemaTest.kt`:

```kotlin
package com.fan.blockeditor.core.schema

import com.fan.blockeditor.core.document.BuiltinMarkTypes
import com.fan.blockeditor.core.document.BuiltinNodeTypes
import com.fan.blockeditor.core.document.MarkType
import com.fan.blockeditor.core.document.NodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaTest {

    @Test
    fun nodeSpecConstruction() {
        val spec = NodeSpec(
            type = BuiltinNodeTypes.TEXT,
            isBlock = true,
            hasContent = true,
        )
        assertEquals(BuiltinNodeTypes.TEXT, spec.type)
        assertTrue(spec.isBlock)
        assertTrue(spec.hasContent)
    }

    @Test
    fun markSpecConstruction() {
        val spec = MarkSpec(type = BuiltinMarkTypes.BOLD)
        assertEquals(BuiltinMarkTypes.BOLD, spec.type)
    }

    @Test
    fun schemaNodeLookup() {
        val schema = Schema(
            nodeSpecs = mapOf(
                BuiltinNodeTypes.TEXT to NodeSpec(BuiltinNodeTypes.TEXT, isBlock = true, hasContent = true),
            ),
            markSpecs = emptyMap(),
        )
        assertNotNull(schema.nodeSpec(BuiltinNodeTypes.TEXT))
        assertNull(schema.nodeSpec(NodeType("unknown")))
    }

    @Test
    fun schemaMarkLookup() {
        val schema = Schema(
            nodeSpecs = emptyMap(),
            markSpecs = mapOf(
                BuiltinMarkTypes.BOLD to MarkSpec(BuiltinMarkTypes.BOLD),
            ),
        )
        assertNotNull(schema.markSpec(BuiltinMarkTypes.BOLD))
        assertNull(schema.markSpec(MarkType("unknown")))
    }

    @Test
    fun schemaHasType() {
        val schema = Schema(
            nodeSpecs = mapOf(
                BuiltinNodeTypes.TEXT to NodeSpec(BuiltinNodeTypes.TEXT),
            ),
            markSpecs = mapOf(
                BuiltinMarkTypes.BOLD to MarkSpec(BuiltinMarkTypes.BOLD),
            ),
        )
        assertTrue(schema.hasNodeType(BuiltinNodeTypes.TEXT))
        assertTrue(schema.hasMarkType(BuiltinMarkTypes.BOLD))
        assertTrue(!schema.hasNodeType(NodeType("custom")))
        assertTrue(!schema.hasMarkType(MarkType("custom")))
    }

    @Test
    fun builtinSchemaHasAllTypes() {
        val schema = BuiltinSchema.create()
        assertNotNull(schema.nodeSpec(BuiltinNodeTypes.TEXT))
        assertNotNull(schema.nodeSpec(BuiltinNodeTypes.IMAGE))
        assertNotNull(schema.nodeSpec(BuiltinNodeTypes.CHECKLIST))
        assertNotNull(schema.nodeSpec(BuiltinNodeTypes.AUDIO))
        assertNotNull(schema.markSpec(BuiltinMarkTypes.BOLD))
        assertNotNull(schema.markSpec(BuiltinMarkTypes.ITALIC))
        assertNotNull(schema.markSpec(BuiltinMarkTypes.UNDERLINE))
        assertNotNull(schema.markSpec(BuiltinMarkTypes.STRIKETHROUGH))
        assertNotNull(schema.markSpec(BuiltinMarkTypes.FONT_SIZE))
        assertNotNull(schema.markSpec(BuiltinMarkTypes.COLOR))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: FAIL — Schema 等类型不存在

- [ ] **Step 3: 实现 Schema**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/schema/Schema.kt`:

```kotlin
package com.fan.blockeditor.core.schema

import com.fan.blockeditor.core.document.BuiltinMarkTypes
import com.fan.blockeditor.core.document.BuiltinNodeTypes
import com.fan.blockeditor.core.document.MarkType
import com.fan.blockeditor.core.document.NodeType

data class NodeSpec(
    val type: NodeType,
    val isBlock: Boolean = true,
    val hasContent: Boolean = false,
)

data class MarkSpec(
    val type: MarkType,
)

class Schema(
    private val nodeSpecs: Map<NodeType, NodeSpec>,
    private val markSpecs: Map<MarkType, MarkSpec>,
) {
    fun nodeSpec(type: NodeType): NodeSpec? = nodeSpecs[type]
    fun markSpec(type: MarkType): MarkSpec? = markSpecs[type]
    fun hasNodeType(type: NodeType): Boolean = type in nodeSpecs
    fun hasMarkType(type: MarkType): Boolean = type in markSpecs
}

object BuiltinSchema {
    fun create(): Schema = Schema(
        nodeSpecs = mapOf(
            BuiltinNodeTypes.TEXT to NodeSpec(BuiltinNodeTypes.TEXT, isBlock = true, hasContent = true),
            BuiltinNodeTypes.IMAGE to NodeSpec(BuiltinNodeTypes.IMAGE, isBlock = true, hasContent = false),
            BuiltinNodeTypes.CHECKLIST to NodeSpec(BuiltinNodeTypes.CHECKLIST, isBlock = true, hasContent = true),
            BuiltinNodeTypes.AUDIO to NodeSpec(BuiltinNodeTypes.AUDIO, isBlock = true, hasContent = false),
        ),
        markSpecs = mapOf(
            BuiltinMarkTypes.BOLD to MarkSpec(BuiltinMarkTypes.BOLD),
            BuiltinMarkTypes.ITALIC to MarkSpec(BuiltinMarkTypes.ITALIC),
            BuiltinMarkTypes.UNDERLINE to MarkSpec(BuiltinMarkTypes.UNDERLINE),
            BuiltinMarkTypes.STRIKETHROUGH to MarkSpec(BuiltinMarkTypes.STRIKETHROUGH),
            BuiltinMarkTypes.FONT_SIZE to MarkSpec(BuiltinMarkTypes.FONT_SIZE),
            BuiltinMarkTypes.COLOR to MarkSpec(BuiltinMarkTypes.COLOR),
        ),
    )
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest --tests "com.fan.blockeditor.core.schema.SchemaTest"
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add editor-core/src/
git commit -m "feat(editor-core): 添加 Schema 系统（NodeSpec/MarkSpec/BuiltinSchema）"
```

---

### Task 6: Operation 原子操作

4 种操作类型：InsertNode / RemoveNode / MoveNode / UpdateNode。每种自带 `inverse()` 实现可逆。SplitNode / MergeNode 留到 Phase 2（editor-compose 实现 Enter/Backspace 行为时再加）。

**Files:**
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/operation/Operation.kt`
- Test: `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/operation/OperationTest.kt`

- [ ] **Step 1: 写失败测试**

`editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/operation/OperationTest.kt`:

```kotlin
package com.fan.blockeditor.core.operation

import com.fan.blockeditor.core.document.Document
import com.fan.blockeditor.core.document.Heading
import com.fan.blockeditor.core.document.TextBlockNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperationTest {

    private val baseDoc = Document(
        children = listOf(
            TextBlockNode(key = "b1", text = "Hello"),
            TextBlockNode(key = "b2", text = "World"),
        ),
    )

    // ── InsertNode ──

    @Test
    fun insertNodeAtIndex() {
        val newNode = TextBlockNode(key = "b3", text = "New")
        val op = InsertNode(index = 1, node = newNode)
        val result = op.apply(baseDoc)
        assertEquals(3, result.children.size)
        assertEquals("b3", result.children[1].key)
        assertEquals("b1", result.children[0].key)
        assertEquals("b2", result.children[2].key)
    }

    @Test
    fun insertNodeAtEnd() {
        val newNode = TextBlockNode(key = "b3", text = "End")
        val op = InsertNode(index = 99, node = newNode)
        val result = op.apply(baseDoc)
        assertEquals(3, result.children.size)
        assertEquals("b3", result.children[2].key)
    }

    @Test
    fun insertNodeInverseIsRemove() {
        val newNode = TextBlockNode(key = "b3", text = "New")
        val op = InsertNode(index = 1, node = newNode)
        val inverse = op.inverse()
        assertTrue(inverse is RemoveNode)
        assertEquals("b3", (inverse as RemoveNode).key)
    }

    @Test
    fun insertNodeRoundTrip() {
        val newNode = TextBlockNode(key = "b3", text = "New")
        val op = InsertNode(index = 1, node = newNode)
        val afterInsert = op.apply(baseDoc)
        val afterUndo = op.inverse().apply(afterInsert)
        assertEquals(baseDoc, afterUndo)
    }

    // ── RemoveNode ──

    @Test
    fun removeNodeByKey() {
        val op = RemoveNode(key = "b1", removedNode = baseDoc.children[0], removedIndex = 0)
        val result = op.apply(baseDoc)
        assertEquals(1, result.children.size)
        assertEquals("b2", result.children[0].key)
    }

    @Test
    fun removeNodeInverseIsInsert() {
        val node = baseDoc.children[0]
        val op = RemoveNode(key = "b1", removedNode = node, removedIndex = 0)
        val inverse = op.inverse()
        assertTrue(inverse is InsertNode)
        val insert = inverse as InsertNode
        assertEquals(0, insert.index)
        assertEquals("b1", insert.node.key)
    }

    @Test
    fun removeNodeRoundTrip() {
        val node = baseDoc.children[0]
        val op = RemoveNode(key = "b1", removedNode = node, removedIndex = 0)
        val afterRemove = op.apply(baseDoc)
        val afterUndo = op.inverse().apply(afterRemove)
        assertEquals(baseDoc, afterUndo)
    }

    // ── MoveNode ──

    @Test
    fun moveNodeSwap() {
        val op = MoveNode(fromIndex = 0, toIndex = 1)
        val result = op.apply(baseDoc)
        assertEquals("b2", result.children[0].key)
        assertEquals("b1", result.children[1].key)
    }

    @Test
    fun moveNodeRoundTrip() {
        val op = MoveNode(fromIndex = 0, toIndex = 1)
        val afterMove = op.apply(baseDoc)
        val afterUndo = op.inverse().apply(afterMove)
        assertEquals(baseDoc, afterUndo)
    }

    // ── UpdateNode ──

    @Test
    fun updateNodeContent() {
        val oldNode = baseDoc.children[0]
        val newNode = (oldNode as TextBlockNode).copy(text = "Updated", heading = Heading.H1)
        val op = UpdateNode(key = "b1", oldNode = oldNode, newNode = newNode)
        val result = op.apply(baseDoc)
        val updated = result.children[0] as TextBlockNode
        assertEquals("Updated", updated.text)
        assertEquals(Heading.H1, updated.heading)
        assertEquals("b2", result.children[1].key)
    }

    @Test
    fun updateNodeRoundTrip() {
        val oldNode = baseDoc.children[0]
        val newNode = (oldNode as TextBlockNode).copy(text = "Changed")
        val op = UpdateNode(key = "b1", oldNode = oldNode, newNode = newNode)
        val afterUpdate = op.apply(baseDoc)
        val afterUndo = op.inverse().apply(afterUpdate)
        assertEquals(baseDoc, afterUndo)
    }

    // ── 不可变性 ──

    @Test
    fun operationsDoNotMutateOriginal() {
        val op = InsertNode(index = 0, node = TextBlockNode(key = "new"))
        op.apply(baseDoc)
        assertEquals(2, baseDoc.children.size)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: FAIL — Operation 类型不存在

- [ ] **Step 3: 实现 Operation 封印类**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/operation/Operation.kt`:

```kotlin
package com.fan.blockeditor.core.operation

import com.fan.blockeditor.core.document.BlockNode
import com.fan.blockeditor.core.document.Document

sealed class Operation {
    abstract fun apply(document: Document): Document
    abstract fun inverse(): Operation
}

data class InsertNode(val index: Int, val node: BlockNode) : Operation() {
    override fun apply(document: Document): Document {
        val list = document.children.toMutableList()
        list.add(index.coerceIn(0, list.size), node)
        return document.copy(children = list)
    }

    override fun inverse(): Operation = RemoveNode(node.key, node, index)
}

data class RemoveNode(
    val key: String,
    val removedNode: BlockNode,
    val removedIndex: Int,
) : Operation() {
    override fun apply(document: Document): Document =
        document.copy(children = document.children.filter { it.key != key })

    override fun inverse(): Operation = InsertNode(removedIndex, removedNode)
}

data class MoveNode(val fromIndex: Int, val toIndex: Int) : Operation() {
    override fun apply(document: Document): Document {
        val list = document.children.toMutableList()
        val node = list.removeAt(fromIndex)
        list.add(toIndex.coerceIn(0, list.size), node)
        return document.copy(children = list)
    }

    override fun inverse(): Operation = MoveNode(toIndex, fromIndex)
}

data class UpdateNode(
    val key: String,
    val oldNode: BlockNode,
    val newNode: BlockNode,
) : Operation() {
    override fun apply(document: Document): Document =
        document.copy(children = document.children.map { if (it.key == key) newNode else it })

    override fun inverse(): Operation = UpdateNode(key, newNode, oldNode)
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest --tests "com.fan.blockeditor.core.operation.OperationTest"
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add editor-core/src/
git commit -m "feat(editor-core): 添加 Operation 封印类（Insert/Remove/Move/Update + inverse）"
```

---

### Task 7: Selection + EditorState

**Files:**
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/state/Selection.kt`
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/state/EditorState.kt`
- Test: `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/state/EditorStateTest.kt`

- [ ] **Step 1: 写失败测试**

`editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/state/EditorStateTest.kt`:

```kotlin
package com.fan.blockeditor.core.state

import com.fan.blockeditor.core.document.Document
import com.fan.blockeditor.core.document.TextBlockNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorStateTest {

    @Test
    fun emptyState() {
        val state = EditorState.empty()
        assertTrue(state.document.children.isEmpty())
        assertTrue(state.selection is Selection.None)
    }

    @Test
    fun stateWithDocAndCursor() {
        val doc = Document(children = listOf(TextBlockNode(key = "b1", text = "Hi")))
        val sel = Selection.Cursor(blockKey = "b1", offset = 2)
        val state = EditorState(document = doc, selection = sel)
        assertEquals(1, state.document.children.size)
        val cursor = state.selection as Selection.Cursor
        assertEquals("b1", cursor.blockKey)
        assertEquals(2, cursor.offset)
    }

    @Test
    fun selectionCursorEquality() {
        assertEquals(Selection.Cursor("b1", 3), Selection.Cursor("b1", 3))
    }

    @Test
    fun selectionTextRange() {
        val sel = Selection.TextRange(blockKey = "b1", start = 2, end = 7)
        assertEquals("b1", sel.blockKey)
        assertEquals(2, sel.start)
        assertEquals(7, sel.end)
    }

    @Test
    fun selectionBlockSelection() {
        val sel = Selection.BlockSelection(blockKeys = listOf("b1", "b2"))
        assertEquals(2, sel.blockKeys.size)
    }

    @Test
    fun stateImmutability() {
        val state1 = EditorState(
            document = Document(children = listOf(TextBlockNode(key = "1"))),
            selection = Selection.None,
        )
        val state2 = state1.copy(selection = Selection.Cursor("1", 0))
        assertTrue(state1.selection is Selection.None)
        assertTrue(state2.selection is Selection.Cursor)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: FAIL

- [ ] **Step 3: 实现 Selection**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/state/Selection.kt`:

```kotlin
package com.fan.blockeditor.core.state

sealed class Selection {
    data class Cursor(val blockKey: String, val offset: Int) : Selection()
    data class TextRange(val blockKey: String, val start: Int, val end: Int) : Selection()
    data class BlockSelection(val blockKeys: List<String>) : Selection()
    data object None : Selection()
}
```

- [ ] **Step 4: 实现 EditorState**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/state/EditorState.kt`:

```kotlin
package com.fan.blockeditor.core.state

import com.fan.blockeditor.core.document.Document

data class EditorState(
    val document: Document,
    val selection: Selection,
) {
    companion object {
        fun empty(): EditorState = EditorState(
            document = Document.empty(),
            selection = Selection.None,
        )
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest --tests "com.fan.blockeditor.core.state.EditorStateTest"
```

Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add editor-core/src/
git commit -m "feat(editor-core): 添加 Selection 选区模型 + EditorState 不可变状态"
```

---

### Task 8: Transaction

Transaction 是对外的编辑 API。调用链构建 Operation 序列，`commit()` 产出新 EditorState + 操作记录 + 逆操作（供 HistoryManager 使用）。

**Files:**
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/state/Transaction.kt`
- Test: `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/state/TransactionTest.kt`

- [ ] **Step 1: 写失败测试**

`editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/state/TransactionTest.kt`:

```kotlin
package com.fan.blockeditor.core.state

import com.fan.blockeditor.core.document.BuiltinMarkTypes
import com.fan.blockeditor.core.document.Document
import com.fan.blockeditor.core.document.Mark
import com.fan.blockeditor.core.document.TextBlockNode
import com.fan.blockeditor.core.operation.InsertNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionTest {

    private val baseState = EditorState(
        document = Document(
            children = listOf(
                TextBlockNode(key = "b1", text = "Hello"),
                TextBlockNode(key = "b2", text = "World"),
            ),
        ),
        selection = Selection.Cursor("b1", 5),
    )

    @Test
    fun insertNode() {
        val newNode = TextBlockNode(key = "b3", text = "New")
        val result = Transaction(baseState)
            .insertNode(1, newNode)
            .commit()
        assertEquals(3, result.state.document.children.size)
        assertEquals("b3", result.state.document.children[1].key)
        assertEquals(1, result.operations.size)
        assertTrue(result.operations[0] is InsertNode)
    }

    @Test
    fun removeNode() {
        val result = Transaction(baseState)
            .removeNode("b1")
            .commit()
        assertEquals(1, result.state.document.children.size)
        assertEquals("b2", result.state.document.children[0].key)
    }

    @Test
    fun removeNonexistentNodeIsNoop() {
        val result = Transaction(baseState)
            .removeNode("nonexistent")
            .commit()
        assertEquals(2, result.state.document.children.size)
        assertTrue(result.operations.isEmpty())
    }

    @Test
    fun moveNode() {
        val result = Transaction(baseState)
            .moveNode(0, 1)
            .commit()
        assertEquals("b2", result.state.document.children[0].key)
        assertEquals("b1", result.state.document.children[1].key)
    }

    @Test
    fun replaceTextEntireContent() {
        val result = Transaction(baseState)
            .replaceText("b1", 0, 5, "Hi")
            .commit()
        val textNode = result.state.document.children[0] as TextBlockNode
        assertEquals("Hi", textNode.text)
    }

    @Test
    fun replaceTextPartial() {
        val result = Transaction(baseState)
            .replaceText("b1", 0, 3, "Yo")
            .commit()
        val textNode = result.state.document.children[0] as TextBlockNode
        assertEquals("Yolo", textNode.text)
    }

    @Test
    fun replaceTextInsert() {
        val result = Transaction(baseState)
            .replaceText("b1", 5, 5, "!")
            .commit()
        val textNode = result.state.document.children[0] as TextBlockNode
        assertEquals("Hello!", textNode.text)
    }

    @Test
    fun replaceTextPreservesNonOverlappingMarks() {
        val state = EditorState(
            document = Document(
                children = listOf(
                    TextBlockNode(
                        key = "b1",
                        text = "Hello World",
                        marks = listOf(
                            Mark(BuiltinMarkTypes.BOLD, 0, 3),
                            Mark(BuiltinMarkTypes.ITALIC, 6, 11),
                        ),
                    ),
                ),
            ),
            selection = Selection.None,
        )
        val result = Transaction(state)
            .replaceText("b1", 4, 6, "")
            .commit()
        val node = result.state.document.children[0] as TextBlockNode
        assertEquals("HellWorld", node.text)
        assertEquals(2, node.marks.size)
        assertEquals(Mark(BuiltinMarkTypes.BOLD, 0, 3), node.marks[0])
        assertEquals(Mark(BuiltinMarkTypes.ITALIC, 4, 9), node.marks[1])
    }

    @Test
    fun toggleMarkAdd() {
        val result = Transaction(baseState)
            .toggleMark("b1", 0, 3, BuiltinMarkTypes.BOLD)
            .commit()
        val textNode = result.state.document.children[0] as TextBlockNode
        assertEquals(1, textNode.marks.size)
        assertEquals(BuiltinMarkTypes.BOLD, textNode.marks[0].type)
        assertEquals(0, textNode.marks[0].start)
        assertEquals(3, textNode.marks[0].end)
    }

    @Test
    fun toggleMarkRemovesExisting() {
        val state = EditorState(
            document = Document(
                children = listOf(
                    TextBlockNode(
                        key = "b1",
                        text = "Hello",
                        marks = listOf(Mark(BuiltinMarkTypes.BOLD, 0, 5)),
                    ),
                ),
            ),
            selection = Selection.None,
        )
        val result = Transaction(state)
            .toggleMark("b1", 0, 5, BuiltinMarkTypes.BOLD)
            .commit()
        val textNode = result.state.document.children[0] as TextBlockNode
        assertTrue(textNode.marks.isEmpty())
    }

    @Test
    fun chainedOperations() {
        val result = Transaction(baseState)
            .insertNode(2, TextBlockNode(key = "b3", text = "!"))
            .replaceText("b1", 0, 5, "Hi")
            .commit()
        assertEquals(3, result.state.document.children.size)
        assertEquals("Hi", (result.state.document.children[0] as TextBlockNode).text)
        assertEquals(2, result.operations.size)
    }

    @Test
    fun setSelection() {
        val result = Transaction(baseState)
            .setSelection(Selection.Cursor("b2", 3))
            .commit()
        assertEquals(Selection.Cursor("b2", 3), result.state.selection)
    }

    @Test
    fun inversesEnableUndo() {
        val result = Transaction(baseState)
            .replaceText("b1", 0, 5, "Hi")
            .commit()
        var doc = result.state.document
        for (inv in result.inverses.asReversed()) {
            doc = inv.apply(doc)
        }
        assertEquals(baseState.document, doc)
    }

    @Test
    fun multiOpInversesUndo() {
        val result = Transaction(baseState)
            .insertNode(2, TextBlockNode(key = "b3", text = "Three"))
            .replaceText("b1", 0, 5, "Hey")
            .commit()
        var doc = result.state.document
        for (inv in result.inverses.asReversed()) {
            doc = inv.apply(doc)
        }
        assertEquals(baseState.document, doc)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: FAIL — Transaction 不存在

- [ ] **Step 3: 实现 Transaction**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/state/Transaction.kt`:

```kotlin
package com.fan.blockeditor.core.state

import com.fan.blockeditor.core.document.BlockNode
import com.fan.blockeditor.core.document.Mark
import com.fan.blockeditor.core.document.MarkType
import com.fan.blockeditor.core.document.TextBlockNode
import com.fan.blockeditor.core.operation.InsertNode
import com.fan.blockeditor.core.operation.MoveNode
import com.fan.blockeditor.core.operation.Operation
import com.fan.blockeditor.core.operation.RemoveNode
import com.fan.blockeditor.core.operation.UpdateNode

class TransactionResult(
    val state: EditorState,
    val operations: List<Operation>,
    val inverses: List<Operation>,
)

class Transaction(private val baseState: EditorState) {

    private val operations = mutableListOf<Operation>()
    private val inverses = mutableListOf<Operation>()
    private var currentDoc = baseState.document
    private var currentSelection = baseState.selection

    fun insertNode(index: Int, node: BlockNode): Transaction {
        applyOp(InsertNode(index, node))
        return this
    }

    fun removeNode(key: String): Transaction {
        val index = currentDoc.children.indexOfFirst { it.key == key }
        if (index < 0) return this
        val node = currentDoc.children[index]
        applyOp(RemoveNode(key, node, index))
        return this
    }

    fun moveNode(fromIndex: Int, toIndex: Int): Transaction {
        applyOp(MoveNode(fromIndex, toIndex))
        return this
    }

    fun replaceText(nodeKey: String, start: Int, end: Int, newText: String): Transaction {
        val node = currentDoc.children.firstOrNull { it.key == nodeKey } as? TextBlockNode
            ?: return this
        val updatedText = node.text.substring(0, start) + newText + node.text.substring(end)
        val updatedMarks = adjustMarksForTextChange(node.marks, start, end, newText.length)
        applyOp(UpdateNode(nodeKey, node, node.copy(text = updatedText, marks = updatedMarks)))
        return this
    }

    fun toggleMark(nodeKey: String, start: Int, end: Int, markType: MarkType): Transaction {
        val node = currentDoc.children.firstOrNull { it.key == nodeKey } as? TextBlockNode
            ?: return this
        val existing = node.marks.find { it.type == markType && it.start == start && it.end == end }
        val newMarks = if (existing != null) node.marks - existing
        else node.marks + Mark(markType, start, end)
        applyOp(UpdateNode(nodeKey, node, node.copy(marks = newMarks)))
        return this
    }

    fun setSelection(selection: Selection): Transaction {
        currentSelection = selection
        return this
    }

    fun commit(): TransactionResult = TransactionResult(
        state = EditorState(currentDoc, currentSelection),
        operations = operations.toList(),
        inverses = inverses.toList(),
    )

    private fun applyOp(op: Operation) {
        inverses.add(op.inverse())
        currentDoc = op.apply(currentDoc)
        operations.add(op)
    }

    private fun adjustMarksForTextChange(
        marks: List<Mark>,
        start: Int,
        end: Int,
        newLength: Int,
    ): List<Mark> {
        val delta = newLength - (end - start)
        return marks.mapNotNull { mark ->
            when {
                mark.end <= start -> mark
                mark.start >= end -> mark.copy(start = mark.start + delta, end = mark.end + delta)
                else -> null
            }
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest --tests "com.fan.blockeditor.core.state.TransactionTest"
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add editor-core/src/
git commit -m "feat(editor-core): 添加 Transaction 事务系统"
```

---

### Task 9: HistoryManager

基于 Operation 序列的撤销重做。与现有 `EditHistoryManager` 设计一致（双栈 + 容量上限），但驱动方式不同：操作对象从 Command（持有 Mutator 引用、就地修改）变为 Operation（纯数据、作用于不可变 Document）。

**Files:**
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/history/HistoryManager.kt`
- Test: `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/history/HistoryManagerTest.kt`

- [ ] **Step 1: 写失败测试**

`editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/history/HistoryManagerTest.kt`:

```kotlin
package com.fan.blockeditor.core.history

import com.fan.blockeditor.core.document.Document
import com.fan.blockeditor.core.document.TextBlockNode
import com.fan.blockeditor.core.state.EditorState
import com.fan.blockeditor.core.state.Selection
import com.fan.blockeditor.core.state.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryManagerTest {

    private val emptyDoc = Document(children = listOf(TextBlockNode(key = "b1", text = "Hello")))
    private val baseState = EditorState(emptyDoc, Selection.None)

    private fun edit(state: EditorState, history: HistoryManager, newText: String): EditorState {
        val result = Transaction(state)
            .replaceText("b1", 0, (state.document.children[0] as TextBlockNode).text.length, newText)
            .commit()
        history.push(result.operations, result.inverses)
        return result.state
    }

    @Test
    fun initialState() {
        val history = HistoryManager()
        assertTrue(!history.canUndo())
        assertTrue(!history.canRedo())
    }

    @Test
    fun undoReturnsNullWhenEmpty() {
        val history = HistoryManager()
        assertNull(history.undo(baseState))
    }

    @Test
    fun redoReturnsNullWhenEmpty() {
        val history = HistoryManager()
        assertNull(history.redo(baseState))
    }

    @Test
    fun singleUndoRedo() {
        val history = HistoryManager()
        val afterEdit = edit(baseState, history, "World")
        assertEquals("World", (afterEdit.document.children[0] as TextBlockNode).text)
        assertTrue(history.canUndo())

        val afterUndo = history.undo(afterEdit)!!
        assertEquals("Hello", (afterUndo.document.children[0] as TextBlockNode).text)
        assertTrue(!history.canUndo())
        assertTrue(history.canRedo())

        val afterRedo = history.redo(afterUndo)!!
        assertEquals("World", (afterRedo.document.children[0] as TextBlockNode).text)
        assertTrue(history.canUndo())
        assertTrue(!history.canRedo())
    }

    @Test
    fun multipleUndos() {
        val history = HistoryManager()
        val s1 = edit(baseState, history, "One")
        val s2 = edit(s1, history, "Two")
        val s3 = edit(s2, history, "Three")

        val u1 = history.undo(s3)!!
        assertEquals("Two", (u1.document.children[0] as TextBlockNode).text)
        val u2 = history.undo(u1)!!
        assertEquals("One", (u2.document.children[0] as TextBlockNode).text)
        val u3 = history.undo(u2)!!
        assertEquals("Hello", (u3.document.children[0] as TextBlockNode).text)
        assertNull(history.undo(u3))
    }

    @Test
    fun pushClearsRedoStack() {
        val history = HistoryManager()
        val s1 = edit(baseState, history, "One")
        val afterUndo = history.undo(s1)!!
        assertTrue(history.canRedo())

        edit(afterUndo, history, "New")
        assertTrue(!history.canRedo())
    }

    @Test
    fun capacityLimit() {
        val history = HistoryManager(capacity = 3)
        var state = baseState
        state = edit(state, history, "A")
        state = edit(state, history, "B")
        state = edit(state, history, "C")
        state = edit(state, history, "D")

        val u1 = history.undo(state)!!
        assertEquals("C", (u1.document.children[0] as TextBlockNode).text)
        val u2 = history.undo(u1)!!
        assertEquals("B", (u2.document.children[0] as TextBlockNode).text)
        val u3 = history.undo(u2)!!
        assertEquals("A", (u3.document.children[0] as TextBlockNode).text)
        assertNull(history.undo(u3))
    }

    @Test
    fun clearResetsStacks() {
        val history = HistoryManager()
        val s1 = edit(baseState, history, "Edited")
        history.undo(s1)
        assertTrue(history.canRedo())

        history.clear()
        assertTrue(!history.canUndo())
        assertTrue(!history.canRedo())
    }

    @Test
    fun listener() {
        val history = HistoryManager()
        val calls = mutableListOf<Pair<Boolean, Boolean>>()
        history.listener = { canUndo, canRedo -> calls.add(canUndo to canRedo) }

        val s1 = edit(baseState, history, "A")
        assertEquals(listOf(true to false), calls)

        history.undo(s1)
        assertEquals(listOf(true to false, false to true), calls)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: FAIL — HistoryManager 不存在

- [ ] **Step 3: 实现 HistoryManager**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/history/HistoryManager.kt`:

```kotlin
package com.fan.blockeditor.core.history

import com.fan.blockeditor.core.operation.Operation
import com.fan.blockeditor.core.state.EditorState

class HistoryManager(private val capacity: Int = 50) {

    private data class Entry(
        val ops: List<Operation>,
        val inverses: List<Operation>,
    )

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    var listener: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun push(ops: List<Operation>, inverses: List<Operation>) {
        redoStack.clear()
        undoStack.addLast(Entry(ops, inverses))
        while (undoStack.size > capacity) undoStack.removeFirst()
        notifyListener()
    }

    fun undo(state: EditorState): EditorState? {
        if (undoStack.isEmpty()) return null
        val entry = undoStack.removeLast()
        var doc = state.document
        for (inv in entry.inverses.asReversed()) {
            doc = inv.apply(doc)
        }
        redoStack.addLast(entry)
        notifyListener()
        return state.copy(document = doc)
    }

    fun redo(state: EditorState): EditorState? {
        if (redoStack.isEmpty()) return null
        val entry = redoStack.removeLast()
        var doc = state.document
        for (op in entry.ops) {
            doc = op.apply(doc)
        }
        undoStack.addLast(entry)
        notifyListener()
        return state.copy(document = doc)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        notifyListener()
    }

    private fun notifyListener() {
        listener?.invoke(canUndo(), canRedo())
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest --tests "com.fan.blockeditor.core.history.HistoryManagerTest"
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add editor-core/src/
git commit -m "feat(editor-core): 添加 HistoryManager 撤销重做（基于 Operation 序列）"
```

---

### Task 10: JsonCodec + 集成验证

使用 `kotlinx.serialization.json` 的 JSON API（`JsonObject` / `buildJsonObject`）实现 Document ↔ JSON 编解码。域模型类型不加 `@Serializable` 注解，保持框架无关。

JSON 格式与现有 `NoteJson.kt` 保持语义兼容：block 有 `type` 字段区分类型，`spans` 重命名为 `marks`，`handwriting.strokes` 结构不变。

**Files:**
- Create: `editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/codec/JsonCodec.kt`
- Test: `editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/codec/JsonCodecTest.kt`

- [ ] **Step 1: 写失败测试**

`editor-core/src/commonTest/kotlin/com/fan/blockeditor/core/codec/JsonCodecTest.kt`:

```kotlin
package com.fan.blockeditor.core.codec

import com.fan.blockeditor.core.document.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonCodecTest {

    @Test
    fun emptyDocumentRoundTrip() {
        val doc = Document.empty()
        val json = JsonCodec.encode(doc)
        val decoded = JsonCodec.decode(json)
        assertEquals(doc, decoded)
    }

    @Test
    fun textBlockRoundTrip() {
        val doc = Document(
            children = listOf(
                TextBlockNode(
                    key = "b1",
                    text = "Hello World",
                    marks = listOf(
                        Mark(BuiltinMarkTypes.BOLD, 0, 5),
                        Mark(BuiltinMarkTypes.COLOR, 6, 11, mapOf("color" to "#FF0000")),
                    ),
                    heading = Heading.H1,
                    alignment = Alignment.CENTER,
                    listType = ListType.BULLET,
                    indentLevel = 2,
                ),
            ),
        )
        val json = JsonCodec.encode(doc)
        val decoded = JsonCodec.decode(json)
        assertEquals(doc, decoded)
    }

    @Test
    fun imageBlockRoundTrip() {
        val doc = Document(
            children = listOf(
                ImageBlockNode(key = "img1", fileName = "photo.jpg", width = 1920, height = 1080),
            ),
        )
        val decoded = JsonCodec.decode(JsonCodec.encode(doc))
        assertEquals(doc, decoded)
    }

    @Test
    fun checklistBlockRoundTrip() {
        val doc = Document(
            children = listOf(
                ChecklistBlockNode(
                    key = "cl1",
                    items = listOf(
                        ChecklistItem(false, "Buy milk"),
                        ChecklistItem(true, "Write code"),
                    ),
                ),
            ),
        )
        val decoded = JsonCodec.decode(JsonCodec.encode(doc))
        assertEquals(doc, decoded)
    }

    @Test
    fun audioBlockRoundTrip() {
        val doc = Document(
            children = listOf(
                AudioBlockNode(key = "a1", fileName = "rec.m4a", durationMs = 30000L),
            ),
        )
        val decoded = JsonCodec.decode(JsonCodec.encode(doc))
        assertEquals(doc, decoded)
    }

    @Test
    fun handwritingRoundTrip() {
        val doc = Document(
            children = emptyList(),
            handwriting = listOf(
                Stroke(
                    brush = BrushType.PEN,
                    color = "#000000",
                    width = 3,
                    points = listOf(
                        StrokePoint(10, 20, 0),
                        StrokePoint(30, 40, 50),
                    ),
                ),
                Stroke(
                    brush = BrushType.MARKER,
                    color = "#FF0000",
                    width = 6,
                    points = listOf(StrokePoint(100, 200, 0)),
                ),
            ),
        )
        val decoded = JsonCodec.decode(JsonCodec.encode(doc))
        assertEquals(doc, decoded)
    }

    @Test
    fun mixedDocumentRoundTrip() {
        val doc = Document(
            children = listOf(
                TextBlockNode(key = "1", text = "Title", heading = Heading.H1),
                ImageBlockNode(key = "2", fileName = "img.png", width = 800, height = 600),
                TextBlockNode(key = "3", text = "Body text"),
                ChecklistBlockNode(
                    key = "4",
                    items = listOf(ChecklistItem(false, "Todo")),
                ),
                AudioBlockNode(key = "5", fileName = "note.m4a", durationMs = 5000),
            ),
            handwriting = listOf(
                Stroke(BrushType.PENCIL, "#333333", 1, listOf(StrokePoint(0, 0, 0))),
            ),
        )
        val decoded = JsonCodec.decode(JsonCodec.encode(doc))
        assertEquals(doc, decoded)
    }

    @Test
    fun decodeUnknownBlockTypeSkipped() {
        val json = """{"blocks":[{"type":"unknown","key":"x"},{"type":"text","key":"b1","text":"Hi","marks":[]}],"handwriting":{"strokes":[]}}"""
        val doc = JsonCodec.decode(json)
        assertEquals(1, doc.children.size)
        assertEquals("b1", doc.children[0].key)
    }

    @Test
    fun decodeInvalidJsonReturnsEmpty() {
        val doc = JsonCodec.decode("not json")
        assertTrue(doc.children.isEmpty())
    }

    @Test
    fun textBlockDefaultsOmitted() {
        val doc = Document(children = listOf(TextBlockNode(key = "b1", text = "Simple")))
        val json = JsonCodec.encode(doc)
        assertTrue("heading" !in json)
        assertTrue("alignment" !in json)
        assertTrue("listType" !in json)
        assertTrue("indentLevel" !in json)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: FAIL — JsonCodec 不存在

- [ ] **Step 3: 实现 JsonCodec**

`editor-core/src/commonMain/kotlin/com/fan/blockeditor/core/codec/JsonCodec.kt`:

```kotlin
package com.fan.blockeditor.core.codec

import com.fan.blockeditor.core.document.*
import kotlinx.serialization.json.*

object JsonCodec {

    fun encode(document: Document): String {
        val json = buildJsonObject {
            putJsonArray("blocks") {
                for (block in document.children) add(encodeBlock(block))
            }
            put("handwriting", buildJsonObject {
                putJsonArray("strokes") {
                    for (s in document.handwriting) add(encodeStroke(s))
                }
            })
        }
        return Json.encodeToString(JsonObject.serializer(), json)
    }

    fun decode(jsonStr: String): Document {
        return try {
            val root = Json.parseToJsonElement(jsonStr).jsonObject
            val blocks = root["blocks"]?.jsonArray
                ?.mapNotNull { decodeBlock(it.jsonObject) }
                ?: emptyList()
            val strokes = root["handwriting"]?.jsonObject
                ?.get("strokes")?.jsonArray
                ?.mapNotNull { decodeStroke(it.jsonObject) }
                ?: emptyList()
            Document(blocks, strokes)
        } catch (_: Exception) {
            Document.empty()
        }
    }

    // ── Block ──

    private fun encodeBlock(block: BlockNode): JsonObject = buildJsonObject {
        put("type", block.type.name)
        put("key", block.key)
        when (block) {
            is TextBlockNode -> {
                put("text", block.text)
                putJsonArray("marks") { for (m in block.marks) add(encodeMark(m)) }
                block.heading?.let { put("heading", it.name.lowercase()) }
                block.alignment?.let { put("alignment", it.name.lowercase()) }
                block.listType?.let { put("listType", it.name.lowercase()) }
                if (block.indentLevel > 0) put("indentLevel", block.indentLevel)
            }
            is ImageBlockNode -> {
                put("fileName", block.fileName)
                put("width", block.width)
                put("height", block.height)
            }
            is ChecklistBlockNode -> {
                putJsonArray("items") {
                    for (item in block.items) {
                        add(buildJsonObject {
                            put("checked", item.checked)
                            put("text", item.text)
                        })
                    }
                }
            }
            is AudioBlockNode -> {
                put("fileName", block.fileName)
                put("durationMs", block.durationMs)
            }
            else -> {}
        }
    }

    private fun decodeBlock(obj: JsonObject): BlockNode? {
        val type = obj["type"]?.jsonPrimitive?.content ?: return null
        val key = obj["key"]?.jsonPrimitive?.content ?: return null
        return when (type) {
            "text" -> TextBlockNode(
                key = key,
                text = obj["text"]?.jsonPrimitive?.content ?: "",
                marks = obj["marks"]?.jsonArray?.mapNotNull { decodeMark(it.jsonObject) } ?: emptyList(),
                heading = obj["heading"]?.jsonPrimitive?.content
                    ?.let { runCatching { Heading.valueOf(it.uppercase()) }.getOrNull() },
                alignment = obj["alignment"]?.jsonPrimitive?.content
                    ?.let { runCatching { Alignment.valueOf(it.uppercase()) }.getOrNull() },
                listType = obj["listType"]?.jsonPrimitive?.content
                    ?.let { runCatching { ListType.valueOf(it.uppercase()) }.getOrNull() },
                indentLevel = obj["indentLevel"]?.jsonPrimitive?.int ?: 0,
            )
            "image" -> ImageBlockNode(
                key = key,
                fileName = obj["fileName"]?.jsonPrimitive?.content ?: "",
                width = obj["width"]?.jsonPrimitive?.int ?: 0,
                height = obj["height"]?.jsonPrimitive?.int ?: 0,
            )
            "checklist" -> ChecklistBlockNode(
                key = key,
                items = obj["items"]?.jsonArray?.map { item ->
                    val o = item.jsonObject
                    ChecklistItem(
                        checked = o["checked"]?.jsonPrimitive?.boolean ?: false,
                        text = o["text"]?.jsonPrimitive?.content ?: "",
                    )
                } ?: emptyList(),
            )
            "audio" -> AudioBlockNode(
                key = key,
                fileName = obj["fileName"]?.jsonPrimitive?.content ?: "",
                durationMs = obj["durationMs"]?.jsonPrimitive?.long ?: 0L,
            )
            else -> null
        }
    }

    // ── Mark ──

    private fun encodeMark(mark: Mark): JsonObject = buildJsonObject {
        put("type", mark.type.name)
        put("start", mark.start)
        put("end", mark.end)
        if (mark.attrs.isNotEmpty()) {
            put("attrs", buildJsonObject {
                for ((k, v) in mark.attrs) put(k, v)
            })
        }
    }

    private fun decodeMark(obj: JsonObject): Mark? {
        val typeName = obj["type"]?.jsonPrimitive?.content ?: return null
        val start = obj["start"]?.jsonPrimitive?.int ?: return null
        val end = obj["end"]?.jsonPrimitive?.int ?: return null
        val attrs = obj["attrs"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.content }
            ?: emptyMap()
        return Mark(MarkType(typeName), start, end, attrs)
    }

    // ── Stroke ──

    private fun encodeStroke(stroke: Stroke): JsonObject = buildJsonObject {
        put("brush", stroke.brush.name.lowercase())
        put("color", stroke.color)
        put("width", stroke.width)
        putJsonArray("points") {
            for (p in stroke.points) {
                add(buildJsonArray { add(p.x); add(p.y); add(p.t) })
            }
        }
    }

    private fun decodeStroke(obj: JsonObject): Stroke? {
        val brush = obj["brush"]?.jsonPrimitive?.content
            ?.let { runCatching { BrushType.valueOf(it.uppercase()) }.getOrNull() }
            ?: return null
        val color = obj["color"]?.jsonPrimitive?.content ?: return null
        val width = obj["width"]?.jsonPrimitive?.int ?: return null
        val points = obj["points"]?.jsonArray?.mapNotNull { ptArr ->
            val arr = ptArr.jsonArray
            if (arr.size < 3) return@mapNotNull null
            StrokePoint(arr[0].jsonPrimitive.int, arr[1].jsonPrimitive.int, arr[2].jsonPrimitive.int)
        } ?: return null
        return Stroke(brush, color, width, points)
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest --tests "com.fan.blockeditor.core.codec.JsonCodecTest"
```

Expected: PASS

- [ ] **Step 5: 跑全量测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :editor-core:jvmTest
```

Expected: ALL PASS

- [ ] **Step 6: 确认 app 模块不受影响**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add editor-core/src/
git commit -m "feat(editor-core): 添加 JsonCodec（Document ↔ JSON）+ Phase 1 完成"
```
