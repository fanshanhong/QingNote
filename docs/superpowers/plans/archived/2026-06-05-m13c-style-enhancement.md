# M13c 样式增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 StylePickerBottomSheet 从 4 行升级为 7 行完整样式面板：对齐 / 列表+缩进 / 字号滑块 / 7 色 / H1-H6 / 背景纹理，对齐华为备忘录。

**Architecture:** TextBlock 新增 alignment / listType / indentLevel 段落级字段；Note 新增 background 字段（DB v4）；EditorPresenter 新增 toggleAlignment / toggleListType / indent / outdent / refreshListNumbers；TextBlockView 改造为水平 LinearLayout（listMarker + EditText）；StylePickerBottomSheet 完全重写为 7 行面板。

**Tech Stack:** Kotlin, Android XML layouts, SQLite, org.json, Material BottomSheetDialog

**Spec:** `docs/superpowers/specs/2026-06-05-m13c-style-enhancement.md`

---

## File Structure

### 新增文件

| 文件 | 职责 |
|------|------|
| `res/drawable/ic_align_left.xml` | 左对齐图标 |
| `res/drawable/ic_align_center.xml` | 居中图标 |
| `res/drawable/ic_align_right.xml` | 右对齐图标 |
| `res/drawable/ic_indent_increase.xml` | 缩进+图标 |
| `res/drawable/ic_indent_decrease.xml` | 缩进-图标 |
| `res/drawable/ic_list_numbered.xml` | 数字列表图标 |
| `res/drawable/ic_list_lettered.xml` | 字母列表图标 |
| `res/drawable/ic_list_bullet.xml` | 实心圆点图标 |
| `res/drawable/ic_list_hollow.xml` | 空心圆点图标 |
| `res/drawable/bg_linen_tile.xml` | 亚麻纹理 tile |
| `res/drawable/bg_kraft_tile.xml` | 牛皮纸纹理 tile |
| `res/drawable/bg_grid_tile.xml` | 网点纹理 tile |
| `res/drawable/bg_texture_thumb_plain.xml` | 白纸缩略图 |
| `res/drawable/bg_texture_thumb_linen.xml` | 亚麻缩略图 |
| `res/drawable/bg_texture_thumb_kraft.xml` | 牛皮纸缩略图 |
| `res/drawable/bg_texture_thumb_grid.xml` | 网点缩略图 |
| `model/history/commands/ParagraphCommands.kt` | 对齐/列表/缩进 3 个 Command |
| `test/.../json/NoteJsonParagraphTest.kt` | alignment/listType/indentLevel 序列化测试 |
| `test/.../db/DbV4MigrationTest.kt` | DB v4 迁移测试 |
| `test/.../util/SpanConverterFontSizeTest.kt` | 5 档字号测试 |

### 修改文件

| 文件 | 变更概要 |
|------|---------|
| `model/entity/Block.kt` | TextBlock 加 alignment/listType/indentLevel 字段；新增 Alignment/ListType 枚举；Heading 扩展 H1-H6 |
| `model/entity/Note.kt` | 加 background: String = "plain" |
| `model/db/NoteDbHelper.kt` | DB_VERSION → 4；onCreate 加 background 列；onUpgrade v3→v4 ALTER |
| `model/NoteRepository.kt` | save/cursorToNote 处理 background |
| `model/json/NoteJson.kt` | TextBlock 序列化/反序列化 alignment/listType/indentLevel |
| `util/SpanConverter.kt` | FONT_SIZE ratio 映射 3 档→5 档 |
| `model/history/commands/StyleCommands.kt` | StyleMutator 扩展 alignment/listType/indentLevel 接口方法 |
| `controller/editor/EditorPresenter.kt` | toggleAlignment/toggleListType/indent/outdent/refreshListNumbers + 3 silent mutators + 实现 StyleMutator 新方法 + pendingHeading + applyPendingTo 5 档 |
| `view/block/TextBlockView.kt` | 布局改造(listMarker + indentation) + applyAlignment + applyHeadingSize H3-H6 |
| `res/layout/block_text.xml` | 从 merge(EditText) 改为 merge(listMarker + EditText) 水平布局 |
| `res/layout/dialog_style_picker.xml` | 完全重写为 7 行面板 |
| `view/toolbar/StylePickerBottomSheet.kt` | 完全重写 |
| `controller/editor/NoteEditorActivity.kt` | 背景纹理渲染 + onBackgroundPicked 回调 |
| `controller/list/NoteListAdapter.kt` | 卡片背景纹理 |
| `res/values/strings.xml` | 新增样式面板相关字符串 |
| `res/values/dimens.xml` | H3-H6 字号 + 缩进单位 + 样式面板尺寸 |
| `res/values/colors.xml` | 7 色圆点颜色值 |

---

### Task 1: Block.kt 数据模型 — 枚举 + TextBlock 新字段

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Block.kt`

- [ ] **Step 1: 新增 Alignment 和 ListType 枚举，扩展 Heading**

在 `Block.kt` 底部，将现有的：

```kotlin
enum class Heading { H1, H2 }
```

替换为：

```kotlin
enum class Heading { H1, H2, H3, H4, H5, H6 }

enum class Alignment { START, CENTER, END }

enum class ListType { BULLET, HOLLOW_BULLET, NUMBERED, LETTERED }
```

- [ ] **Step 2: TextBlock 新增 alignment / listType / indentLevel 字段**

将 TextBlock data class 改为：

```kotlin
data class TextBlock(
    override val id: String,
    var heading: Heading? = null,
    var text: String = "",
    var spans: List<TextSpan> = emptyList(),
    var alignment: Alignment? = null,
    var listType: ListType? = null,
    var indentLevel: Int = 0,
) : Block()
```

- [ ] **Step 3: 构建验证**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:compileDebugKotlin 2>&1 | tail -30`

Expected: 编译失败 — `TextBlockView.applyHeadingSize()` 的 `when(heading)` 不再穷尽 H3-H6。这是预期的，Task 7 修复。

- [ ] **Step 4: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Block.kt
git commit -m "feat(m13c): TextBlock 加 alignment/listType/indentLevel + Heading 扩 H1-H6 + Alignment/ListType 枚举"
```

---

### Task 2: Note.kt + NoteDbHelper v4 + NoteRepository — background 字段

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Note.kt`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/db/NoteDbHelper.kt`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`

- [ ] **Step 1: Note.kt 加 background 字段**

在 `Note` data class 中 `val notebookId: Long? = null,` 之后加一行：

```kotlin
val background: String = "plain",
```

- [ ] **Step 2: NoteDbHelper — DB_VERSION 改为 4**

`const val DB_VERSION = 3` → `const val DB_VERSION = 4`

- [ ] **Step 3: NoteDbHelper — SQL_CREATE_NOTES 加 background 列**

将 `SQL_CREATE_NOTES_V3` 改名为 `SQL_CREATE_NOTES`（调用处 `onCreate` 也跟着改），在 SQL 中 `notebook_id INTEGER` 后加：

```sql
,
              background TEXT NOT NULL DEFAULT 'plain'
```

同时将 `onCreate` 中 `db.execSQL(SQL_CREATE_NOTES_V3)` 改为 `db.execSQL(SQL_CREATE_NOTES)`。

- [ ] **Step 4: NoteDbHelper — onUpgrade 加 v3→v4**

在 `if (oldVersion < 3)` 块之后加：

```kotlin
if (oldVersion < 4) {
    db.execSQL("ALTER TABLE notes ADD COLUMN background TEXT NOT NULL DEFAULT 'plain'")
}
```

- [ ] **Step 5: NoteRepository.save() 加 background**

在 `save()` 方法的 `ContentValues.apply` 中，`put("deleted_at", note.deletedAt)` 之后加：

```kotlin
put("background", note.background)
```

- [ ] **Step 6: NoteRepository.cursorToNote() 加 background**

在 `cursorToNote()` 的 `notebookId = ...` 行之后加：

```kotlin
background = c.getString(c.getColumnIndexOrThrow("background")) ?: "plain",
```

- [ ] **Step 7: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/entity/Note.kt code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/db/NoteDbHelper.kt code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt
git commit -m "feat(m13c): Note 加 background 字段 + DB v4 迁移 + Repository 读写"
```

---

### Task 3: NoteJson — alignment/listType/indentLevel 序列化

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/json/NoteJson.kt`

- [ ] **Step 1: 新增 import**

在文件顶部 import 区加：

```kotlin
import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.ListType
```

- [ ] **Step 2: blockToJson — TextBlock 分支加 3 个字段**

在 TextBlock 分支的 `put("spans", spansToJson(b.spans))` 之后加：

```kotlin
b.alignment?.let { put("alignment", it.name.lowercase()) }
b.listType?.let { put("listType", it.name.lowercase()) }
if (b.indentLevel > 0) put("indentLevel", b.indentLevel)
```

- [ ] **Step 3: blockFromJson — TextBlock 分支加 3 个字段**

在 "text" 分支的 `Block.TextBlock(...)` 构造中，`spans = spansFromJson(...)` 之后加：

```kotlin
alignment = o.optString("alignment", "")
    .takeIf { it.isNotEmpty() }
    ?.let { runCatching { Alignment.valueOf(it.uppercase()) }.getOrNull() },
listType = o.optString("listType", "")
    .takeIf { it.isNotEmpty() }
    ?.let { runCatching { ListType.valueOf(it.uppercase()) }.getOrNull() },
indentLevel = o.optInt("indentLevel", 0),
```

- [ ] **Step 4: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/json/NoteJson.kt
git commit -m "feat(m13c): NoteJson 序列化/反序列化 alignment/listType/indentLevel"
```

---

### Task 4: SpanConverter — FONT_SIZE 3 档→5 档

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/util/SpanConverter.kt`

- [ ] **Step 1: 更新常量**

将顶部 3 个常量：

```kotlin
private const val SIZE_SMALL = 0.85f
private const val SIZE_MEDIUM = 1.0f
private const val SIZE_LARGE = 1.25f
```

替换为 5 个：

```kotlin
private const val SIZE_XS = 0.75f
private const val SIZE_SMALL = 0.85f
private const val SIZE_MEDIUM = 1.0f
private const val SIZE_LARGE = 1.25f
private const val SIZE_XL = 1.5f
```

- [ ] **Step 2: 更新 applyTo 中 FONT_SIZE 映射**

将 `SpanType.FONT_SIZE -> { val ratio = when (s.value) { ... } }` 中的 when 替换为：

```kotlin
val ratio = when (s.value) {
    "xs" -> SIZE_XS
    "small" -> SIZE_SMALL
    "medium" -> SIZE_MEDIUM
    "large" -> SIZE_LARGE
    "xl" -> SIZE_XL
    else -> null
}
```

- [ ] **Step 3: 更新 toTextSpans 中 RelativeSizeSpan 映射**

将 `is RelativeSizeSpan -> { val v = when { ... } }` 替换为：

```kotlin
is RelativeSizeSpan -> {
    val v = when {
        kotlin.math.abs(s.sizeChange - SIZE_XS) < 0.01f -> "xs"
        kotlin.math.abs(s.sizeChange - SIZE_SMALL) < 0.01f -> "small"
        kotlin.math.abs(s.sizeChange - SIZE_MEDIUM) < 0.01f -> "medium"
        kotlin.math.abs(s.sizeChange - SIZE_LARGE) < 0.01f -> "large"
        kotlin.math.abs(s.sizeChange - SIZE_XL) < 0.01f -> "xl"
        else -> null
    }
    if (v != null) out += TextSpan(start, end, SpanType.FONT_SIZE, v)
}
```

- [ ] **Step 4: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/util/SpanConverter.kt
git commit -m "feat(m13c): SpanConverter FONT_SIZE 3 档扩展为 5 档"
```

---

### Task 5: 资源文件 — strings + dimens + colors + drawables

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`
- Modify: `code/HuaWeiNote/app/src/main/res/values/dimens.xml`
- Modify: `code/HuaWeiNote/app/src/main/res/values/colors.xml`（读取确认是否存在再决定）
- Create: 9 个 vector drawable（`res/drawable/ic_align_*.xml`, `ic_indent_*.xml`, `ic_list_*.xml`）
- Create: 3 个 tile drawable（`res/drawable/bg_*_tile.xml`）
- Create: 4 个 thumbnail drawable（`res/drawable/bg_texture_thumb_*.xml`）

- [ ] **Step 1: strings.xml 追加**

在 `</resources>` 之前追加：

```xml
<!-- M13c 样式面板 -->
<string name="style_panel_title">样式</string>
<string name="tb_align_left_cd">左对齐</string>
<string name="tb_align_center_cd">居中</string>
<string name="tb_align_right_cd">右对齐</string>
<string name="tb_indent_increase_cd">增加缩进</string>
<string name="tb_indent_decrease_cd">减少缩进</string>
<string name="tb_list_numbered_cd">数字列表</string>
<string name="tb_list_lettered_cd">字母列表</string>
<string name="tb_list_bullet_cd">圆点列表</string>
<string name="tb_list_hollow_cd">空心圆点列表</string>
<string name="tb_font_size_small_label">Aa</string>
<string name="tb_font_size_large_label">Aa</string>
<string name="tb_h3">H3</string>
<string name="tb_h4">H4</string>
<string name="tb_h5">H5</string>
<string name="tb_h6">H6</string>
<string name="color_light_blue">浅蓝</string>
<string name="color_purple">紫</string>
<string name="bg_plain">白纸</string>
<string name="bg_linen">亚麻</string>
<string name="bg_kraft">牛皮纸</string>
<string name="bg_grid">网点</string>
```

- [ ] **Step 2: dimens.xml 追加**

在 `</resources>` 之前追加：

```xml
<!-- M13c 编辑器字号 H3-H6（H1 28sp / H2 24sp 用新 dimen 名，旧 H1=22sp/H2=18sp 保留兼容） -->
<dimen name="editor_text_h1_v2">28sp</dimen>
<dimen name="editor_text_h2_v2">24sp</dimen>
<dimen name="editor_text_h3">20sp</dimen>
<dimen name="editor_text_h4">18sp</dimen>
<dimen name="editor_text_h5">16sp</dimen>
<dimen name="editor_text_h6">14sp</dimen>

<!-- M13c 列表缩进 -->
<dimen name="editor_indent_unit">24dp</dimen>
<dimen name="editor_list_marker_width">24dp</dimen>

<!-- M13c 样式面板 -->
<dimen name="style_row_height">48dp</dimen>
<dimen name="style_color_dot_size">28dp</dimen>
<dimen name="style_texture_thumb_size">52dp</dimen>
<dimen name="style_texture_thumb_radius">8dp</dimen>
```

- [ ] **Step 3: colors.xml 追加 7 色**

先读取 `res/values/colors.xml`，在 `</resources>` 之前追加（跳过已有的）：

```xml
<!-- M13c 样式面板 7 色 -->
<color name="style_color_red">#E53935</color>
<color name="style_color_orange">#FB8C00</color>
<color name="style_color_green">#43A047</color>
<color name="style_color_light_blue">#29B6F6</color>
<color name="style_color_blue">#1E88E5</color>
<color name="style_color_purple">#AB47BC</color>
<color name="style_color_black">#212121</color>
```

- [ ] **Step 4: 创建 3 个对齐 vector drawables**

`res/drawable/ic_align_left.xml`：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M3,3h18v2H3V3zM3,7h12v2H3V7zM3,11h18v2H3v-2zM3,15h12v2H3v-2zM3,19h18v2H3v-2z"/>
</vector>
```

`res/drawable/ic_align_center.xml`：同结构，pathData 改为居中线条。

`res/drawable/ic_align_right.xml`：同结构，pathData 改为右对齐线条。

- [ ] **Step 5: 创建 2 个缩进 vector drawables**

`res/drawable/ic_indent_increase.xml`：24dp, 向右箭头+线。Material "format_indent_increase" 图标 pathData。

`res/drawable/ic_indent_decrease.xml`：24dp, 向左箭头+线。Material "format_indent_decrease" 图标 pathData。

- [ ] **Step 6: 创建 4 个列表 vector drawables**

`res/drawable/ic_list_numbered.xml`：24dp, "format_list_numbered" pathData。

`res/drawable/ic_list_lettered.xml`：24dp, 类似 numbered 但标注 a/b/c。

`res/drawable/ic_list_bullet.xml`：24dp, "format_list_bulleted" pathData。

`res/drawable/ic_list_hollow.xml`：24dp, 空心圆点列表。

- [ ] **Step 7: 创建 3 个 tile drawables + 4 个 thumb drawables**

`res/drawable/bg_linen_tile.xml`：

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#F5F0E8"/>
</shape>
```

`res/drawable/bg_kraft_tile.xml`：

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#E8D5B7"/>
</shape>
```

`res/drawable/bg_grid_tile.xml`：

```xml
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item><shape><solid android:color="#F8F8F8"/></shape></item>
</layer-list>
```

4 个 thumb drawables 都是简单的圆角矩形 shape，对应颜色（plain=#FFFFFF, linen=#F5F0E8, kraft=#E8D5B7, grid=#F8F8F8），corners radius=8dp，stroke 1dp #DDDDDD。

- [ ] **Step 8: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaWeiNote/app/src/main/res/
git commit -m "feat(m13c): 新增样式面板资源（strings/dimens/colors/9 图标/7 纹理 drawable）"
```

---

### Task 6: ParagraphCommands — 对齐/列表/缩进 Undo/Redo + StyleMutator 扩展

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/commands/StyleCommands.kt`
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/commands/ParagraphCommands.kt`

- [ ] **Step 1: StyleMutator 接口扩展**

在 `StyleCommands.kt` 的 `StyleMutator` 接口中，在 `fun silentRequestFocus(blockId: String, cursorIndex: Int)` 之前加 6 个方法：

```kotlin
fun snapshotAlignment(blockId: String): Alignment?
fun setBlockAlignment(blockId: String, alignment: Alignment?)
fun snapshotListType(blockId: String): ListType?
fun setBlockListType(blockId: String, listType: ListType?)
fun snapshotIndentLevel(blockId: String): Int
fun setBlockIndentLevel(blockId: String, indentLevel: Int)
```

在文件顶部 import 区加：

```kotlin
import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.ListType
```

- [ ] **Step 2: 创建 ParagraphCommands.kt**

创建 `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/commands/ParagraphCommands.kt`：

```kotlin
package com.fan.hwnote.app.model.history.commands

import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.ListType
import com.fan.hwnote.app.model.history.Command

class ApplyAlignmentCommand(
    private val mutator: StyleMutator,
    private val blockId: String,
    private val before: Alignment?,
    private val after: Alignment?,
) : Command {
    override val label = "ApplyAlignment($blockId: $before -> $after)"
    override fun apply() {
        mutator.setBlockAlignment(blockId, after)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
    override fun revert() {
        mutator.setBlockAlignment(blockId, before)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
}

class ApplyListTypeCommand(
    private val mutator: StyleMutator,
    private val blockId: String,
    private val before: ListType?,
    private val after: ListType?,
) : Command {
    override val label = "ApplyListType($blockId: $before -> $after)"
    override fun apply() {
        mutator.setBlockListType(blockId, after)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
    override fun revert() {
        mutator.setBlockListType(blockId, before)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
}

class ApplyIndentCommand(
    private val mutator: StyleMutator,
    private val blockId: String,
    private val before: Int,
    private val after: Int,
) : Command {
    override val label = "ApplyIndent($blockId: $before -> $after)"
    override fun apply() {
        mutator.setBlockIndentLevel(blockId, after)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
    override fun revert() {
        mutator.setBlockIndentLevel(blockId, before)
        mutator.silentRequestFocus(blockId, Int.MAX_VALUE)
    }
}
```

- [ ] **Step 3: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/commands/StyleCommands.kt code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/history/commands/ParagraphCommands.kt
git commit -m "feat(m13c): 新增 ApplyAlignment/ApplyListType/ApplyIndent Command + StyleMutator 扩展"
```

---

### Task 7: TextBlockView 改造 — listMarker + 缩进 + 对齐 + H3-H6 字号

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/layout/block_text.xml`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt`

- [ ] **Step 1: block_text.xml 改为水平布局**

将现有的：

```xml
<merge xmlns:android="http://schemas.android.com/apk/res/android">
    <EditText
        android:id="@+id/block_edit"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@null"
        android:gravity="top|start"
        android:inputType="textMultiLine|textCapSentences"
        android:minHeight="40dp"
        android:paddingVertical="@dimen/spacing_xs"
        android:textColor="@color/text_primary"
        android:textColorHint="@color/text_hint"
        android:textSize="@dimen/editor_text_normal" />
</merge>
```

替换为：

```xml
<merge xmlns:android="http://schemas.android.com/apk/res/android">

    <TextView
        android:id="@+id/list_marker"
        android:layout_width="@dimen/editor_list_marker_width"
        android:layout_height="wrap_content"
        android:gravity="end"
        android:paddingEnd="4dp"
        android:paddingTop="@dimen/spacing_xs"
        android:textColor="@color/text_secondary"
        android:textSize="@dimen/editor_text_normal"
        android:visibility="gone" />

    <EditText
        android:id="@+id/block_edit"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:background="@null"
        android:gravity="top|start"
        android:inputType="textMultiLine|textCapSentences"
        android:minHeight="40dp"
        android:paddingVertical="@dimen/spacing_xs"
        android:textColor="@color/text_primary"
        android:textColorHint="@color/text_hint"
        android:textSize="@dimen/editor_text_normal" />

</merge>
```

注意：TextBlockView 继承 BlockView（FrameLayout），需要改为 LinearLayout(horizontal)。但因为 BlockView 继承 FrameLayout，改基类影响太大。改用另一种方式：TextBlockView 的 init 中用代码设置 orientation=HORIZONTAL。

**实际做法：** 保持 `<merge>` 不变，但 TextBlockView 初始化时把自身 orientation 设为 horizontal。由于 BlockView 继承 FrameLayout 而非 LinearLayout，这里需要把 TextBlockView 改为直接继承 LinearLayout 而非 BlockView，或者在 TextBlockView 内部创建一个水平 LinearLayout 容器。

**推荐做法：** TextBlockView 内部创建一个水平 LinearLayout 容器包裹 listMarker + editText。由于 TextBlockView 继承 FrameLayout（通过 BlockView），inflate `block_text.xml` 后把 listMarker 和 block_edit 作为直接子 view。FrameLayout 的子 view 默认叠加。所以更好的做法是 **不改 block_text.xml 的 merge 结构**，而是：

1. 将 `block_text.xml` 改为非 merge 形式，根节点为水平 LinearLayout：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">

    <TextView
        android:id="@+id/list_marker"
        android:layout_width="@dimen/editor_list_marker_width"
        android:layout_height="wrap_content"
        android:gravity="end"
        android:paddingEnd="4dp"
        android:paddingTop="@dimen/spacing_xs"
        android:textColor="@color/text_secondary"
        android:textSize="@dimen/editor_text_normal"
        android:visibility="gone" />

    <EditText
        android:id="@+id/block_edit"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:background="@null"
        android:gravity="top|start"
        android:inputType="textMultiLine|textCapSentences"
        android:minHeight="40dp"
        android:paddingVertical="@dimen/spacing_xs"
        android:textColor="@color/text_primary"
        android:textColorHint="@color/text_hint"
        android:textSize="@dimen/editor_text_normal" />

</LinearLayout>
```

2. TextBlockView.init 中的 `LayoutInflater.from(context).inflate(R.layout.block_text, this, true)` 保持不变（true 表示 attach to parent FrameLayout）。这会在 FrameLayout 内添加一个水平 LinearLayout，内含 listMarker + editText。

- [ ] **Step 2: TextBlockView.kt — 新增 listMarker 引用 + alignment/listType/indentLevel 字段**

在 `val edit: EditText` 之后加：

```kotlin
private val listMarker: android.widget.TextView
```

在 `init` 块中 `edit = findViewById(R.id.block_edit)` 之后加：

```kotlin
listMarker = findViewById(R.id.list_marker)
```

新增字段（在 `heading` 之后）：

```kotlin
private var alignment: Alignment? = null
private var listType: ListType? = null
private var indentLevel: Int = 0
```

在文件顶部 import 加：

```kotlin
import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.ListType
```

- [ ] **Step 3: bind() — 处理新字段**

在 `bind()` 方法中 `applyHeadingSize()` 之后加：

```kotlin
alignment = block.alignment
listType = block.listType
indentLevel = block.indentLevel
applyAlignment()
applyIndentation()
```

- [ ] **Step 4: toBlock() — 输出新字段**

将 `toBlock()` 改为：

```kotlin
override fun toBlock(): Block.TextBlock {
    val spannable = SpannableString(edit.text)
    return Block.TextBlock(
        id = blockId,
        heading = heading,
        text = spannable.toString(),
        spans = spannable.toTextSpans(),
        alignment = alignment,
        listType = listType,
        indentLevel = indentLevel,
    )
}
```

- [ ] **Step 5: 新增 applyAlignment() 方法**

```kotlin
private fun applyAlignment() {
    val gravity = when (alignment) {
        Alignment.START, null -> android.view.Gravity.START
        Alignment.CENTER -> android.view.Gravity.CENTER_HORIZONTAL
        Alignment.END -> android.view.Gravity.END
    }
    edit.gravity = gravity or android.view.Gravity.TOP
}
```

- [ ] **Step 6: 新增 applyIndentation() 方法**

```kotlin
private fun applyIndentation() {
    val indentPx = (indentLevel * resources.getDimension(R.dimen.editor_indent_unit)).toInt()
    (parent as? android.view.View)?.setPadding(indentPx, 0, 0, 0)
        ?: setPadding(indentPx, 0, 0, 0)
}
```

实际上，因为布局是 FrameLayout > LinearLayout(horizontal) > [listMarker, editText]，缩进应该设在 FrameLayout（即 TextBlockView 本身）上：

```kotlin
private fun applyIndentation() {
    val indentPx = (indentLevel * resources.getDimension(R.dimen.editor_indent_unit)).toInt()
    setPadding(indentPx, paddingTop, paddingRight, paddingBottom)
}
```

- [ ] **Step 7: 新增 setListMarker() / hideListMarker() 公开方法**

```kotlin
fun setListMarker(text: String) {
    listMarker.text = text
    listMarker.visibility = android.view.View.VISIBLE
}

fun hideListMarker() {
    listMarker.visibility = android.view.View.GONE
}
```

- [ ] **Step 8: 新增 setAlignment() / setListType() / setIndentLevel() setter 方法**

```kotlin
fun setAlignment(a: Alignment?) {
    alignment = a
    applyAlignment()
}

fun currentAlignment(): Alignment? = alignment

fun setListType(lt: ListType?) {
    listType = lt
}

fun currentListType(): ListType? = listType

fun setIndentLevel(level: Int) {
    indentLevel = level
    applyIndentation()
}

fun currentIndentLevel(): Int = indentLevel
```

- [ ] **Step 9: 更新 applyHeadingSize() — 扩展 H3-H6**

将现有的：

```kotlin
private fun applyHeadingSize() {
    val sp = when (heading) {
        Heading.H1 -> resources.getDimension(R.dimen.editor_text_h1)
        Heading.H2 -> resources.getDimension(R.dimen.editor_text_h2)
        null -> resources.getDimension(R.dimen.editor_text_normal)
    }
    edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp)
}
```

替换为：

```kotlin
private fun applyHeadingSize() {
    val sp = when (heading) {
        Heading.H1 -> resources.getDimension(R.dimen.editor_text_h1_v2)
        Heading.H2 -> resources.getDimension(R.dimen.editor_text_h2_v2)
        Heading.H3 -> resources.getDimension(R.dimen.editor_text_h3)
        Heading.H4 -> resources.getDimension(R.dimen.editor_text_h4)
        Heading.H5 -> resources.getDimension(R.dimen.editor_text_h5)
        Heading.H6 -> resources.getDimension(R.dimen.editor_text_h6)
        null -> resources.getDimension(R.dimen.editor_text_normal)
    }
    edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp)
}
```

- [ ] **Step 10: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaWeiNote/app/src/main/res/layout/block_text.xml code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt
git commit -m "feat(m13c): TextBlockView 加 listMarker + 缩进 + 对齐 + H1-H6 字号"
```

---

### Task 8: EditorPresenter — 对齐/列表/缩进方法 + StyleMutator 实现 + applyPendingTo 5 档

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`

- [ ] **Step 1: 新增 import**

在文件顶部 import 区加：

```kotlin
import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.ListType
import com.fan.hwnote.app.model.history.commands.ApplyAlignmentCommand
import com.fan.hwnote.app.model.history.commands.ApplyListTypeCommand
import com.fan.hwnote.app.model.history.commands.ApplyIndentCommand
```

- [ ] **Step 2: 新增 toggleAlignment()**

在 `toggleHeading()` 方法之后加：

```kotlin
fun toggleAlignment(alignment: Alignment) {
    val v = focusedTextBlock ?: return
    val blockId = v.toBlock().id
    val before = v.currentAlignment()
    val after = if (before == alignment) null else alignment
    v.setAlignment(after)
    history.push(ApplyAlignmentCommand(this, blockId, before, after))
}
```

- [ ] **Step 3: 新增 toggleListType()**

```kotlin
fun toggleListType(listType: ListType) {
    val v = focusedTextBlock ?: return
    val blockId = v.toBlock().id
    val before = v.currentListType()
    val after = if (before == listType) null else listType
    v.setListType(after)
    history.push(ApplyListTypeCommand(this, blockId, before, after))
    refreshListNumbers()
}
```

- [ ] **Step 4: 新增 indent() / outdent()**

```kotlin
fun indent() {
    val v = focusedTextBlock ?: return
    val current = v.currentIndentLevel()
    if (current >= 3) return
    val blockId = v.toBlock().id
    v.setIndentLevel(current + 1)
    history.push(ApplyIndentCommand(this, blockId, current, current + 1))
}

fun outdent() {
    val v = focusedTextBlock ?: return
    val current = v.currentIndentLevel()
    if (current <= 0) return
    val blockId = v.toBlock().id
    v.setIndentLevel(current - 1)
    history.push(ApplyIndentCommand(this, blockId, current, current - 1))
}
```

- [ ] **Step 5: 新增 refreshListNumbers()**

```kotlin
fun refreshListNumbers() {
    for (i in currentBlocks.indices) {
        val view = currentBlocks[i]
        if (view !is TextBlockView) continue
        val lt = view.currentListType()
        when (lt) {
            ListType.BULLET -> view.setListMarker("•")
            ListType.HOLLOW_BULLET -> view.setListMarker("○")
            ListType.NUMBERED -> {
                var count = 0
                for (j in 0 until i) {
                    val prev = currentBlocks[j]
                    if (prev is TextBlockView && prev.currentListType() == ListType.NUMBERED) count++
                }
                view.setListMarker("${count + 1}.")
            }
            ListType.LETTERED -> {
                var count = 0
                for (j in 0 until i) {
                    val prev = currentBlocks[j]
                    if (prev is TextBlockView && prev.currentListType() == ListType.LETTERED) count++
                }
                view.setListMarker("${('a' + count)}.")
            }
            null -> view.hideListMarker()
        }
    }
}
```

- [ ] **Step 6: 在 bind() 末尾调 refreshListNumbers()**

在 `bind()` 方法中 `overlay.setStrokes(note.content.handwriting)` 之后加：

```kotlin
refreshListNumbers()
```

- [ ] **Step 7: 在 onRequestSplitAfter / onRequestDelete 末尾调 refreshListNumbers()**

在 `onRequestSplitAfter()` 的 `history.push(...)` 之后加 `refreshListNumbers()`。

在 `onRequestDelete()` 的 `history.push(...)` 之后加 `refreshListNumbers()`。

- [ ] **Step 8: 实现 StyleMutator 新增的 6 个接口方法**

在现有的 `setBlockHeading` 之后加：

```kotlin
override fun snapshotAlignment(blockId: String): Alignment? =
    (currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView)?.currentAlignment()

override fun setBlockAlignment(blockId: String, alignment: Alignment?) {
    val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
    view.setAlignment(alignment)
}

override fun snapshotListType(blockId: String): ListType? =
    (currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView)?.currentListType()

override fun setBlockListType(blockId: String, listType: ListType?) {
    val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
    view.setListType(listType)
    refreshListNumbers()
}

override fun snapshotIndentLevel(blockId: String): Int =
    (currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView)?.currentIndentLevel() ?: 0

override fun setBlockIndentLevel(blockId: String, indentLevel: Int) {
    val view = currentBlocks.firstOrNull { it.toBlock().id == blockId } as? TextBlockView ?: return
    view.setIndentLevel(indentLevel)
}
```

- [ ] **Step 9: 更新 applyPendingTo() — 支持 5 档字号**

将 `applyPendingTo()` 中的 `pendingSize?.let { v -> val r = when (v) { "small" -> 0.85f; "large" -> 1.25f; else -> 1.0f } ... }` 改为：

```kotlin
pendingSize?.let { v ->
    val r = when (v) {
        "xs" -> 0.75f
        "small" -> 0.85f
        "large" -> 1.25f
        "xl" -> 1.5f
        else -> 1.0f
    }
    sp.setSpan(RelativeSizeSpan(r), start, end, flag)
}
```

- [ ] **Step 10: 新增 pendingHeading() 查询方法**

```kotlin
fun pendingHeading(): Heading? = focusedTextBlock?.currentHeading()
```

- [ ] **Step 11: 构建验证**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: BUILD SUCCESSFUL（此时所有 Heading when 都已扩展）。

- [ ] **Step 12: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt
git commit -m "feat(m13c): EditorPresenter 加 toggleAlignment/toggleListType/indent/outdent/refreshListNumbers + StyleMutator 实现"
```

---

### Task 9: NoteEditorActivity — collectCurrentNote 保留 background + 背景纹理渲染

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

- [ ] **Step 1: collectCurrentNote 中合并 background**

在 `saveNote()` 方法中，`val toSave = presenter.collectCurrentNote(title).copy(...)` 的 `.copy()` 参数中加：

```kotlin
background = loaded.background,
```

同理，`ensureNoteSavedAndThen` 中若也有 `.copy()`，一样加上。

实际上，`collectCurrentNote` 已经从 `currentNote.copy(...)` 构建，而 `currentNote` 是 bind 时设置的，包含 background。但 `saveNote()` 中的 `.copy(id = loaded.id, ...)` 会覆盖，需要确保 background 不丢失。检查代码：`collectCurrentNote` 返回 `currentNote.copy(title=..., plainText=..., content=...)`，background 字段保留。然后 `saveNote` 中 `.copy(id = loaded.id, categoryId = loaded.categoryId, notebookId = ...)` 不涉及 background，所以 background 从 currentNote 继承。**无需改动。**

但如果用户在面板中切换了背景，需要一个 `pendingBackground` 变量存储。参考 `pendingNotebookId` 模式：

在 Activity 中加字段：

```kotlin
private var pendingBackground: String? = null
```

- [ ] **Step 2: 新增 applyEditorBackground() 方法**

```kotlin
private fun applyEditorBackground(background: String) {
    val editorRoot = findViewById<android.view.View>(R.id.editor_root)
    when (background) {
        "plain" -> editorRoot.setBackgroundResource(R.color.bg_card)
        "linen" -> editorRoot.setBackgroundResource(R.drawable.bg_linen_tile)
        "kraft" -> editorRoot.setBackgroundResource(R.drawable.bg_kraft_tile)
        "grid" -> editorRoot.setBackgroundResource(R.drawable.bg_grid_tile)
    }
}
```

- [ ] **Step 3: loadNote() 中调用 applyEditorBackground()**

在 `loadNote()` 的 `presenter.bind(note)` 之后加：

```kotlin
applyEditorBackground(note.background)
```

- [ ] **Step 4: StylePickerBottomSheet 回调 — onBackgroundPicked**

需要让 StylePickerBottomSheet 能回调 Activity 切换背景。在 Activity 的 `onStyleClicked` lambda 中，把 StylePickerBottomSheet 的创建改为传入一个 `onBackgroundPicked` 回调：

```kotlin
override fun onStyleClicked() {
    com.fan.hwnote.app.view.toolbar.StylePickerBottomSheet(
        this@NoteEditorActivity, presenter
    ) { bg ->
        pendingBackground = bg
        applyEditorBackground(bg)
    }.show()
}
```

- [ ] **Step 5: saveNote() 中合并 pendingBackground**

在 `saveNote()` 的 `val toSave = presenter.collectCurrentNote(title).copy(...)` 中加：

```kotlin
background = pendingBackground ?: loaded.background,
```

同理 `ensureNoteSavedAndThen` 中也加。

- [ ] **Step 6: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "feat(m13c): NoteEditorActivity 背景纹理渲染 + pendingBackground 保存"
```

---

### Task 10: dialog_style_picker.xml — 面板布局重写（7 行）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/layout/dialog_style_picker.xml`

- [ ] **Step 1: 完全重写布局**

将整个文件替换为 7 行面板布局（标题栏 + 6 行内容），详细结构：

```
ScrollView (vertical, 不超过屏幕 70% 高)
  └─ LinearLayout (vertical)
       ├─ 标题栏：FrameLayout
       │    ├─ TextView "样式" (居中)
       │    └─ ImageView ✕ (end, id=btn_close)
       ├─ Divider 1dp
       ├─ 行 1 (48dp)：LinearLayout horizontal, 7 children weight=1
       │    B / I / U / S / alignLeft / alignCenter / alignRight
       │    id: btn_bold, btn_italic, btn_underline, btn_strike,
       │        btn_align_left, btn_align_center, btn_align_right
       ├─ Divider 1dp
       ├─ 行 2 (48dp)：LinearLayout horizontal, 6 children weight=1
       │    indentIncrease / indentDecrease / numbered / lettered / bullet / hollow
       │    id: btn_indent_inc, btn_indent_dec, btn_list_numbered,
       │        btn_list_lettered, btn_list_bullet, btn_list_hollow
       ├─ Divider 1dp
       ├─ 行 3 (48dp)：LinearLayout horizontal
       │    TextView "Aa" (小) / SeekBar (max=4, weight=1) / TextView "Aa" (大)
       │    id: label_size_small, seek_font_size, label_size_large
       ├─ Divider 1dp
       ├─ 行 4 (48dp)：LinearLayout horizontal, 7 ImageView weight=1
       │    7 色圆点 (ic_color_dot + tint)
       │    id: color_0..color_6
       ├─ Divider 1dp
       ├─ 行 5 (48dp)：LinearLayout horizontal, 6 children weight=1
       │    H1 / H2 / H3 / H4 / H5 / H6
       │    id: btn_h1..btn_h6
       ├─ Divider 1dp
       └─ 行 6 (68dp padding)：LinearLayout horizontal, gap=12dp
            4 个 ImageView 52x52dp 圆角 8dp (背景纹理缩略图)
            id: bg_plain, bg_linen, bg_kraft, bg_grid
```

**注意：**
- B/I/U/S 用 `TextView` + `android:textStyle="bold"` 等
- 对齐/缩进/列表按钮用 `ImageView` + 对应 vector drawable
- SeekBar 用 `android:progressTint="@color/primary"` / `android:thumbTint="@color/primary"`
- 颜色圆点复用 `@drawable/ic_color_dot`（已存在），用 `android:tint` 着色
- H1-H6 用 `TextView`
- 背景纹理用 `ImageView` + `src=@drawable/bg_texture_thumb_*`

- [ ] **Step 2: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaWeiNote/app/src/main/res/layout/dialog_style_picker.xml
git commit -m "feat(m13c): dialog_style_picker.xml 重写为 7 行样式面板"
```

---

### Task 11: StylePickerBottomSheet.kt — 完全重写

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/toolbar/StylePickerBottomSheet.kt`

- [ ] **Step 1: 完全重写 StylePickerBottomSheet**

构造参数改为：

```kotlin
class StylePickerBottomSheet(
    context: Context,
    private val presenter: EditorPresenter,
    private val onBackgroundPicked: (String) -> Unit = {},
) : BottomSheetDialog(context)
```

`onCreate` 中：

1. `setContentView(R.layout.dialog_style_picker)`
2. findViewById 拿到所有按钮（行 1: btn_bold..btn_align_right, 行 2: btn_indent_inc..btn_list_hollow, 行 3: seek_font_size, 行 4: color_0..color_6, 行 5: btn_h1..btn_h6, 行 6: bg_plain..bg_grid, 标题栏: btn_close）
3. `btn_close.setOnClickListener { dismiss() }`

**行 1 — 内联样式 + 对齐：**

```kotlin
btn_bold.setOnClickListener { presenter.toggleInline(SpanType.BOLD); refreshSelected() }
btn_italic.setOnClickListener { presenter.toggleInline(SpanType.ITALIC); refreshSelected() }
btn_underline.setOnClickListener { presenter.toggleInline(SpanType.UNDERLINE); refreshSelected() }
btn_strike.setOnClickListener { presenter.toggleInline(SpanType.STRIKETHROUGH); refreshSelected() }
btn_align_left.setOnClickListener { presenter.toggleAlignment(Alignment.START); refreshSelected() }
btn_align_center.setOnClickListener { presenter.toggleAlignment(Alignment.CENTER); refreshSelected() }
btn_align_right.setOnClickListener { presenter.toggleAlignment(Alignment.END); refreshSelected() }
```

**行 2 — 缩进 + 列表：**

```kotlin
btn_indent_inc.setOnClickListener { presenter.indent(); refreshSelected() }
btn_indent_dec.setOnClickListener { presenter.outdent(); refreshSelected() }
btn_list_numbered.setOnClickListener { presenter.toggleListType(ListType.NUMBERED); refreshSelected() }
btn_list_lettered.setOnClickListener { presenter.toggleListType(ListType.LETTERED); refreshSelected() }
btn_list_bullet.setOnClickListener { presenter.toggleListType(ListType.BULLET); refreshSelected() }
btn_list_hollow.setOnClickListener { presenter.toggleListType(ListType.HOLLOW_BULLET); refreshSelected() }
```

**行 3 — 字号滑块：**

```kotlin
val sizeNames = arrayOf("xs", "small", "medium", "large", "xl")
seek_font_size.max = 4
seek_font_size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) presenter.toggleSize(sizeNames[progress])
    }
    override fun onStartTrackingTouch(sb: SeekBar) {}
    override fun onStopTrackingTouch(sb: SeekBar) {}
})
```

**行 4 — 7 色：**

```kotlin
val colorHexes = arrayOf("#E53935", "#FB8C00", "#43A047", "#29B6F6", "#1E88E5", "#AB47BC", "#212121")
val colorViews = arrayOf(color_0, color_1, color_2, color_3, color_4, color_5, color_6)
for (i in colorViews.indices) {
    colorViews[i].setOnClickListener {
        presenter.pickColor(colorHexes[i])
        refreshSelected()
    }
}
```

**行 5 — H1-H6：**

```kotlin
val headingViews = arrayOf(btn_h1, btn_h2, btn_h3, btn_h4, btn_h5, btn_h6)
val headingValues = Heading.values()
for (i in headingViews.indices) {
    headingViews[i].setOnClickListener {
        presenter.toggleHeading(headingValues[i])
        refreshSelected()
    }
}
```

**行 6 — 背景纹理：**

```kotlin
val bgNames = arrayOf("plain", "linen", "kraft", "grid")
val bgViews = arrayOf(bg_plain, bg_linen, bg_kraft, bg_grid)
for (i in bgViews.indices) {
    bgViews[i].setOnClickListener {
        onBackgroundPicked(bgNames[i])
        refreshSelected()
    }
}
```

**refreshSelected() 同步高亮：**

```kotlin
private var currentBackground: String = "plain"

private fun refreshSelected() {
    val pending = presenter.pendingInlineSet()
    btn_bold.isSelected = SpanType.BOLD in pending
    btn_italic.isSelected = SpanType.ITALIC in pending
    btn_underline.isSelected = SpanType.UNDERLINE in pending
    btn_strike.isSelected = SpanType.STRIKETHROUGH in pending

    val alignment = presenter.currentFocusedTextBlock()
        ?.let { (it as? TextBlockView)?.currentAlignment() }
    btn_align_left.isSelected = alignment == Alignment.START
    btn_align_center.isSelected = alignment == Alignment.CENTER
    btn_align_right.isSelected = alignment == Alignment.END

    val listType = presenter.currentFocusedTextBlock()
        ?.let { (it as? TextBlockView)?.currentListType() }
    btn_list_numbered.isSelected = listType == ListType.NUMBERED
    btn_list_lettered.isSelected = listType == ListType.LETTERED
    btn_list_bullet.isSelected = listType == ListType.BULLET
    btn_list_hollow.isSelected = listType == ListType.HOLLOW_BULLET

    // 字号滑块同步
    val size = presenter.pendingSize()
    val sizeIdx = when (size) {
        "xs" -> 0; "small" -> 1; "large" -> 3; "xl" -> 4; else -> 2
    }
    seek_font_size.progress = sizeIdx

    // 颜色高亮
    val colorHexes = arrayOf("#E53935", "#FB8C00", "#43A047", "#29B6F6", "#1E88E5", "#AB47BC", "#212121")
    val colorViews = arrayOf(color_0, color_1, color_2, color_3, color_4, color_5, color_6)
    val pc = presenter.pendingColor()
    for (i in colorViews.indices) {
        colorViews[i].isSelected = (pc != null && pc.equals(colorHexes[i], ignoreCase = true))
    }

    // H1-H6 高亮
    val heading = presenter.pendingHeading()
    val headingViews = arrayOf(btn_h1, btn_h2, btn_h3, btn_h4, btn_h5, btn_h6)
    val headingValues = Heading.values()
    for (i in headingViews.indices) {
        headingViews[i].isSelected = heading == headingValues[i]
    }

    // 背景纹理高亮
    val bgNames = arrayOf("plain", "linen", "kraft", "grid")
    val bgViews = arrayOf(bg_plain, bg_linen, bg_kraft, bg_grid)
    for (i in bgViews.indices) {
        bgViews[i].isSelected = currentBackground == bgNames[i]
    }
}
```

`refreshSelected()` 在 `onCreate` 末尾调一次同步初始状态。

注意：`currentFocusedTextBlock()` 返回 `TextBlockView?`（EditorPresenter 已有此方法）。需要 import `TextBlockView`：

```kotlin
import com.fan.hwnote.app.view.block.TextBlockView
```

- [ ] **Step 2: 构建验证**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/toolbar/StylePickerBottomSheet.kt
git commit -m "feat(m13c): StylePickerBottomSheet 重写为 7 行完整样式面板"
```

---

### Task 12: NoteListAdapter — 卡片背景纹理

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListAdapter.kt`

- [ ] **Step 1: bind() 中处理 background**

在 `bind()` 方法的 notebook color 处理逻辑之后，加一段：若 `note.background != "plain"`，覆盖卡片背景色为对应纹理色：

```kotlin
when (note.background) {
    "linen" -> card.setCardBackgroundColor(Color.parseColor("#F5F0E8"))
    "kraft" -> card.setCardBackgroundColor(Color.parseColor("#E8D5B7"))
    "grid" -> card.setCardBackgroundColor(Color.parseColor("#F8F8F8"))
    else -> { /* plain 已在上面 notebook color 逻辑中处理 */ }
}
```

注意：background 优先级高于 notebook color。若 `background != "plain"`，不再用 notebook color；否则保持原逻辑。

改法：将 notebook color 和 background 合并判断：

```kotlin
val bg = note.background
if (bg != "plain") {
    val bgColor = when (bg) {
        "linen" -> Color.parseColor("#F5F0E8")
        "kraft" -> Color.parseColor("#E8D5B7")
        "grid" -> Color.parseColor("#F8F8F8")
        else -> ContextCompat.getColor(itemView.context, R.color.bg_card)
    }
    card.setCardBackgroundColor(bgColor)
} else {
    val colorStr = note.notebookId?.let { notebookColorMap[it] }
    if (colorStr != null) {
        val c = Color.parseColor(colorStr)
        card.setCardBackgroundColor(Color.argb(20, Color.red(c), Color.green(c), Color.blue(c)))
    } else {
        card.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.bg_card))
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListAdapter.kt
git commit -m "feat(m13c): NoteListAdapter 卡片背景纹理渲染"
```

---

### Task 13: 单元测试 — 序列化 + DB 迁移 + 字号

**Files:**
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/json/NoteJsonParagraphTest.kt`
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/model/db/DbV4MigrationTest.kt`
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/util/SpanConverterFontSizeTest.kt`

- [ ] **Step 1: NoteJsonParagraphTest — alignment/listType/indentLevel 序列化测试**

创建 `NoteJsonParagraphTest.kt`：

```kotlin
package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.Alignment
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.ListType
import com.fan.hwnote.app.model.entity.NoteContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NoteJsonParagraphTest {

    private fun roundTrip(content: NoteContent): NoteContent =
        NoteJson.fromJson(NoteJson.toJson(content))

    @Test
    fun `alignment round-trips`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", text = "left", alignment = Alignment.START),
                Block.TextBlock(id = "b2", text = "center", alignment = Alignment.CENTER),
                Block.TextBlock(id = "b3", text = "right", alignment = Alignment.END),
                Block.TextBlock(id = "b4", text = "default"),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `listType round-trips`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", text = "bullet", listType = ListType.BULLET),
                Block.TextBlock(id = "b2", text = "hollow", listType = ListType.HOLLOW_BULLET),
                Block.TextBlock(id = "b3", text = "numbered", listType = ListType.NUMBERED),
                Block.TextBlock(id = "b4", text = "lettered", listType = ListType.LETTERED),
                Block.TextBlock(id = "b5", text = "none"),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `indentLevel round-trips`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", text = "indent0"),
                Block.TextBlock(id = "b2", text = "indent1", indentLevel = 1),
                Block.TextBlock(id = "b3", text = "indent3", indentLevel = 3),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `heading H3-H6 round-trip`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(id = "b1", heading = Heading.H3, text = "h3"),
                Block.TextBlock(id = "b2", heading = Heading.H4, text = "h4"),
                Block.TextBlock(id = "b3", heading = Heading.H5, text = "h5"),
                Block.TextBlock(id = "b4", heading = Heading.H6, text = "h6"),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `missing alignment and listType deserialize as null`() {
        val json = """{"blocks":[{"type":"text","id":"b1","text":"hi","spans":[]}],"handwriting":{"strokes":[]}}"""
        val content = NoteJson.fromJson(json)
        val block = content.blocks.first() as Block.TextBlock
        assertNull(block.alignment)
        assertNull(block.listType)
        assertEquals(0, block.indentLevel)
    }

    @Test
    fun `combined paragraph fields round-trip`() {
        val src = NoteContent(
            blocks = listOf(
                Block.TextBlock(
                    id = "b1", text = "styled",
                    alignment = Alignment.CENTER,
                    listType = ListType.NUMBERED,
                    indentLevel = 2,
                    heading = Heading.H2,
                ),
            ),
            handwriting = emptyList(),
        )
        assertEquals(src, roundTrip(src))
    }
}
```

- [ ] **Step 2: DbV4MigrationTest — DB v4 迁移测试**

创建 `DbV4MigrationTest.kt`（Robolectric JUnit4 测试，沿用现有 DbV3MigrationTest 模式）：

```kotlin
package com.fan.hwnote.app.model.db

import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DbV4MigrationTest {

    private val helper = NoteDbHelper(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun `fresh db has background column with default plain`() {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("title", "test")
            put("plain_text", "")
            put("content_json", "{}")
            put("created_at", 1L)
            put("updated_at", 1L)
        }
        val id = db.insert("notes", null, cv)
        val cursor = db.query("notes", null, "id = ?", arrayOf(id.toString()), null, null, null)
        cursor.use { c ->
            c.moveToFirst()
            val bg = c.getString(c.getColumnIndexOrThrow("background"))
            assertEquals("plain", bg)
        }
    }

    @Test
    fun `background can be set to linen`() {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("title", "test")
            put("plain_text", "")
            put("content_json", "{}")
            put("created_at", 1L)
            put("updated_at", 1L)
            put("background", "linen")
        }
        val id = db.insert("notes", null, cv)
        val cursor = db.query("notes", null, "id = ?", arrayOf(id.toString()), null, null, null)
        cursor.use { c ->
            c.moveToFirst()
            assertEquals("linen", c.getString(c.getColumnIndexOrThrow("background")))
        }
    }
}
```

- [ ] **Step 3: SpanConverterFontSizeTest — 5 档字号测试**

创建 `SpanConverterFontSizeTest.kt`（纯 JUnit5 测试）：

```kotlin
package com.fan.hwnote.app.util

import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SpanConverterFontSizeTest {

    @ParameterizedTest
    @CsvSource("xs,0.75", "small,0.85", "medium,1.0", "large,1.25", "xl,1.5")
    fun `applyTo sets correct RelativeSizeSpan ratio`(value: String, expectedRatio: Float) {
        val text = "hello"
        val sp = SpannableString(text)
        val spans = listOf(TextSpan(0, text.length, SpanType.FONT_SIZE, value))
        spans.applyTo(sp)
        val applied = sp.getSpans(0, sp.length, RelativeSizeSpan::class.java)
        assertEquals(1, applied.size)
        assertEquals(expectedRatio, applied[0].sizeChange, 0.01f)
    }

    @ParameterizedTest
    @CsvSource("xs,0.75", "small,0.85", "medium,1.0", "large,1.25", "xl,1.5")
    fun `toTextSpans reverse-maps RelativeSizeSpan correctly`(expectedValue: String, ratio: Float) {
        val text = "hello"
        val sp = SpannableString(text)
        sp.setSpan(RelativeSizeSpan(ratio), 0, text.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val result = sp.toTextSpans()
        assertEquals(1, result.size)
        assertEquals(SpanType.FONT_SIZE, result[0].type)
        assertEquals(expectedValue, result[0].value)
    }
}
```

- [ ] **Step 4: 跑测试**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:test 2>&1 | tail -30`

Expected: 所有测试 PASSED（140 项现有 + 新增 ~12 项 ≈ 152 项）。

- [ ] **Step 5: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add code/HuaWeiNote/app/src/test/
git commit -m "test(m13c): 新增 alignment/listType/indentLevel 序列化 + DB v4 + 5 档字号测试"
```

---

### Task 14: 构建验证 + 全量测试

**Files:** 无新增/修改，纯验证。

- [ ] **Step 1: 全量构建**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:clean :app:assembleDebug :app:test 2>&1 | tail -40`

Expected: BUILD SUCCESSFUL + 所有测试 PASSED。

- [ ] **Step 2: 若有编译或测试失败，修复并重跑**

修复后 commit：

```bash
git commit -m "fix(m13c): 修复构建/测试问题"
```

---

### Task 15: STATUS 更新 + 计划归档

**Files:**
- Modify: `docs/superpowers/STATUS.md`

- [ ] **Step 1: STATUS.md 追加 M13c 章节**

在 STATUS.md 末尾追加：

```markdown
## M13c 样式增强（2026-06-05）

**范围：** StylePickerBottomSheet 从 4 行升级为 7 行完整样式面板。

**新增功能：**
- 文本对齐（左/居中/右）
- 列表+缩进（数字/字母/实心圆点/空心圆点 + 3 级缩进）
- 字号滑块（5 档：xs/small/medium/large/xl）
- 颜色扩充（5 色→7 色，加浅蓝/紫）
- H1-H6 六级标题
- 背景纹理（白纸/亚麻/牛皮纸/网点）

**涉及文件：** Block.kt / Note.kt / NoteDbHelper(v4) / NoteRepository / NoteJson / SpanConverter / EditorPresenter / TextBlockView / StylePickerBottomSheet / NoteEditorActivity / NoteListAdapter / 资源文件

**测试：** ~152 项 PASSED（140 项旧 + ~12 项新）
```

- [ ] **Step 2: Commit**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote && git add docs/superpowers/STATUS.md
git commit -m "docs(m13c): 更新 STATUS + 标记 M13c 完成"
```

---

## 不动的东西（明确边界）

- **EditorPresenter 核心逻辑** — insertImageBlocksAtFocus / insertChecklistBlockAtFocus / onRequestDelete / onRequestSplitAfter / focusLastTextBlock — 仅在末尾加 `refreshListNumbers()` 调用，逻辑不改
- **ImageBlockView / ChecklistBlockView / AudioBlockView** — 不动
- **HandwritingOverlayView / BrushPainter** — 不动
- **NoteEditorActivity 双模式逻辑** — enterEditMode / exitEditMode — 不改（仅加 background 渲染）
- **NoteListActivity / FilterPanelAdapter** — 不动
- **TextToolbarView / HandwritingToolbarView** — 不动
- **测试** — 140 项保持 PASS + 新增 ~12 项

## 执行方式

Subagent-Driven Development：每任务 implementer DONE → spec reviewer → code quality reviewer → fix（如需）→ re-review → 标记完成。预估 5-8 小时。
