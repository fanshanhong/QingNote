# HwNote M7 打磨 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans。任务步骤用 `- [ ]` 复选框追踪。

**Goal:** 关闭 M5/M6/M6-UX 阶段 STATUS 留下的全部代码债（A 组），落实 PRD §7 体验与错误处理打磨清单（B 组），跑完 PRD §9 完整手测（C1）。M7 完成即关闭 MVP。

**Architecture:** 纯打磨，零功能新增。改动分布在编辑器层（NoteEditorActivity、EditorPresenter、ImageBlockView）、手写层（HandwritingOverlayView、BrushPainter）、字符串资源。不动数据层、PRD 架构、Block 模型。

**Tech Stack:** Kotlin + Android View + AndroidX；JUnit 5（A4 一处 TDD）；其余 UI 层沿用"写完即手测"约定。

**测试基线：** 65/65 PASS（M2 42 + M4 SpanConverter 15 + M5 ImageCompressor 3 + M6 StrokeEraser 5）。A4 加 3 项 → M7 完成后 68/68 PASS。

**执行约束（不可破，沿历史里程碑）：**
- 直接提交 master（与 M2/M3/M4/M5/M6 一致），不开 feature 分支。
- 严格串行 11 任务（subagent-driven-development），每任 implementer DONE → spec reviewer → code quality reviewer → fix（如需）→ re-review → 标完成。
- 提交信息中文动宾，不要 `Co-Authored-By` trailer。
- `git add <file path>` 按名加，不要 `git add -A`。
- Gradle 命令在 `code/HuaWeiNote/` 下，`JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11)` 前缀。
- 不动 AGP 8.11.2 / Kotlin 2.0.21 / Gradle 8.14.3 / compileSdk 36 / targetSdk 36 / minSdk 24 / JDK 11。

---

## 任务拆解

| # | 任务 | 来源 | 涉及文件 | 复杂度 |
|---|------|------|----------|--------|
| 1 | 删 `toast_handwriting_placeholder` 死资源 | A7 | strings.xml | ⭐ |
| 2 | 加"清空所有手写笔画？"专属 string + `onClearClicked` 用新文案 | A6 | strings.xml, NoteEditorActivity.kt | ⭐ |
| 3 | `HandwritingOverlayView` 加 `strokesMut()` 替代硬转 + `isHandwritingMode` setter 清 `gestureSnapshot` | A3 + A8 | HandwritingOverlayView.kt | ⭐⭐ |
| 4 | `BrushPainter` Paint 三键缓存（TDD） | A4 | BrushPainter.kt, BrushPainterTest.kt(新) | ⭐⭐ |
| 5 | `HandwritingOverlayView.onDraw` Path/StrokePoint 复用 | A5 | HandwritingOverlayView.kt | ⭐⭐ |
| 6 | Camera 跨进程死亡 `SavedInstanceState` 持久化 | A1 | NoteEditorActivity.kt | ⭐⭐ |
| 7 | 新笔记 `save-in-flight` 标志互斥 | A2 | NoteEditorActivity.kt | ⭐⭐ |
| 8 | `NoteRepository.save` 失败 Toast 提示 | B3 | NoteEditorActivity.kt, strings.xml | ⭐ |
| 9 | 图片加载失败 Toast + 自动移除块 | B2 | ImageBlockView.kt, EditorPresenter.kt | ⭐⭐⭐ |
| 10 | 相机被拒引导跳系统设置（AlertDialog） | B1 | NoteEditorActivity.kt, strings.xml | ⭐⭐ |
| 11 | PRD §9 完整手测 8 项 + 修发现的问题 + STATUS 收尾 | C1 | STATUS.md | ⭐⭐ |

---

### Task 1：删 `toast_handwriting_placeholder` 死资源（A7）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`

- [ ] **Step 1: 全工程搜索引用确认无残留**

Run: `grep -rn "toast_handwriting_placeholder" code/HuaWeiNote/app/src/main/`
Expected: 只有 strings.xml 自身一行命中，无 java/layout 引用。

- [ ] **Step 2: 删除 strings.xml 死字符串**

从 `code/HuaWeiNote/app/src/main/res/values/strings.xml` 删除：
```xml
<string name="toast_handwriting_placeholder">手写功能（M6 实现）</string>
```

- [ ] **Step 3: 构建验证**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add code/HuaWeiNote/app/src/main/res/values/strings.xml
git commit -m "chore(m7): 删 toast_handwriting_placeholder 死资源"
```

---

### Task 2：手写清空对话框加专属文案（A6）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**背景：** 当前 `onClearClicked` AlertDialog 标题 `tb_clear_cd`="清空"、消息复用 `dialog_delete_message`="确定删除？此操作不可恢复"。"删除"语义对手写清空不准。

- [ ] **Step 1: strings.xml 新增专属字符串**

在"M6 手写工具栏"段落（`tb_done_cd` 之后）追加：
```xml
<string name="dialog_clear_handwriting_message">清空当前所有手写笔画？此操作不可恢复</string>
```

- [ ] **Step 2: NoteEditorActivity.kt `onClearClicked` 替换消息引用**

找到 `onClearClicked` 内 `.setMessage(R.string.dialog_delete_message)`，改为：
```kotlin
.setMessage(R.string.dialog_clear_handwriting_message)
```

- [ ] **Step 3: 构建验证**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add code/HuaWeiNote/app/src/main/res/values/strings.xml code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "fix(m7): 手写清空对话框换专属文案，不再复用删除笔记文案"
```

---

### Task 3：`HandwritingOverlayView` 显式可变列表 + setter 清 gestureSnapshot（A3 + A8）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt`

**背景：** A3 — `eraseAt` 把 `strokesRef()` 硬转 `MutableList`，若日后 `strokesRef` 改返不可变拷贝会运行期 ClassCastException。A8 — `isHandwritingMode` setter 切换时只清 `inProgressPoints` 不清 `gestureSnapshot`（橡皮 snapshot），目前依赖 ACTION_UP 自清，纯一致性 nit。

- [ ] **Step 1: 先 Read 当前 HandwritingOverlayView.kt 找到 `strokesRef()` 与 `eraseAt`**

Run: `grep -n "strokesRef\|eraseAt\|gestureSnapshot\|isHandwritingMode" code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt`
记录行号。

- [ ] **Step 2: 加 `internal fun strokesMut(): MutableList<Stroke>` 并把 `eraseAt` 改为调它**

在原 `strokesRef()` 旁边新增（不删 strokesRef，外部可能仍读它做只读访问）：
```kotlin
/** 仅 Overlay 内部使用：返回 strokes 的可变引用。避免外部硬转 MutableList。 */
internal fun strokesMut(): MutableList<Stroke> = strokes
```
把 `eraseAt` 内原本 `(strokesRef() as MutableList<Stroke>).removeAt(idx)` 改为 `strokesMut().removeAt(idx)`。

- [ ] **Step 3: `isHandwritingMode` setter 增加 `gestureSnapshot.clear()`**

找到 `var isHandwritingMode: Boolean` 的 setter，在原本清 `inProgressPoints` 那块后追加：
```kotlin
gestureSnapshot.clear()
```
（仅在 `field != value` 短路分支之后执行，保持原结构。）

- [ ] **Step 4: 构建 + 跑 65 项单测**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug :app:test`
Expected: BUILD SUCCESSFUL，65/65 PASSED。

- [ ] **Step 5: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt
git commit -m "refactor(m7): Overlay 用 strokesMut() 显式可变引用 + setter 清 gestureSnapshot"
```

---

### Task 4：`BrushPainter` Paint 三键缓存 + 单测（A4，TDD）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/handwriting/BrushPainter.kt`
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/view/handwriting/BrushPainterTest.kt`

**背景：** `paintFor` 每次 `new Paint(...)`，热路径（50 strokes × 60fps）≈ 3000 allocs/s 触发 GC 抖动。缓存键 `(brush, color, widthPx)` 三元组：笔种枚举值小、颜色单笔记 ≤ 8、宽度单笔记 ≤ 3，缓存上限自然 ≤ 96 条 / 笔记，常驻无忧。

- [ ] **Step 1: 写失败测试 `BrushPainterTest`**

Create `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/view/handwriting/BrushPainterTest.kt`：
```kotlin
package com.fan.hwnote.app.view.handwriting

import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.Stroke
import com.fan.hwnote.app.model.entity.StrokePoint
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class BrushPainterTest {

    private fun stroke(
        brush: BrushType = BrushType.PEN,
        color: String = "#212121",
        width: Int = 3,
    ) = Stroke(
        brush = brush,
        color = color,
        width = width,
        points = listOf(StrokePoint(0f, 0f, 1f)),
    )

    @Test
    fun `同键复用同一个 Paint 实例`() {
        val painter = BrushPainter()
        val a = painter.paintFor(stroke())
        val b = painter.paintFor(stroke())
        assertSame(a, b)
    }

    @Test
    fun `不同 brush 返回不同 Paint`() {
        val painter = BrushPainter()
        val pen = painter.paintFor(stroke(brush = BrushType.PEN))
        val marker = painter.paintFor(stroke(brush = BrushType.MARKER))
        assertNotSame(pen, marker)
    }

    @Test
    fun `不同颜色返回不同 Paint`() {
        val painter = BrushPainter()
        val red = painter.paintFor(stroke(color = "#E53935"))
        val blue = painter.paintFor(stroke(color = "#1E88E5"))
        assertNotSame(red, blue)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.view.handwriting.BrushPainterTest"`
Expected: `同键复用同一个 Paint 实例` FAILED（assertSame 失败，实例不同）。

- [ ] **Step 3: BrushPainter 加缓存**

修改 `BrushPainter.kt`：
- 在 class 顶部加私有 map：
  ```kotlin
  private val cache = HashMap<Triple<BrushType, String, Int>, Paint>()
  ```
- 把 `paintFor` 改为：
  ```kotlin
  fun paintFor(stroke: Stroke): Paint {
      val key = Triple(stroke.brush, stroke.color, stroke.width)
      cache[key]?.let { return it }
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          // 原有 4 笔种 Paint 初始化逻辑，全部搬进来
          ...
      }
      cache[key] = p
      return p
  }
  ```
（不改 Paint 初始化细节，只把"`val p = Paint(...).apply { ... }`; return p"包装为缓存命中返回。）

- [ ] **Step 4: 测试转绿**

Run 同 Step 2 命令。
Expected: 3/3 PASSED。

- [ ] **Step 5: 跑全量测试确认无回归**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:test`
Expected: 68/68 PASSED（65 旧 + 3 新）。

- [ ] **Step 6: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/handwriting/BrushPainter.kt code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/view/handwriting/BrushPainterTest.kt
git commit -m "perf(m7): BrushPainter 按 (brush,color,width) 三键缓存 Paint 实例"
```

---

### Task 5：`HandwritingOverlayView.onDraw` Path/StrokePoint 复用（A5）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt`

**背景：** `onDraw` 内 `val path = android.graphics.Path()` 每帧每 stroke 重建；`inProgressPoints.map { StrokePoint(...) }` 每帧重建临时列表。改为类成员复用，且 in-progress 列表只在 size 变化时 rebuild。

- [ ] **Step 1: 类顶部加复用字段**

在 class 内私有字段区追加：
```kotlin
private val reusablePath = android.graphics.Path()
private val inProgressBuffer = mutableListOf<com.fan.hwnote.app.model.entity.StrokePoint>()
```

- [ ] **Step 2: `onDraw` 内 `val path = Path()` 改为 `reusablePath.reset()` 后用 `reusablePath`**

定位 `onDraw` 里：
```kotlin
val path = android.graphics.Path()
// ... strokeToPath(stroke, path) 之类的调用
canvas.drawPath(path, ...)
```
改为：
```kotlin
reusablePath.reset()
// ... strokeToPath(stroke, reusablePath)
canvas.drawPath(reusablePath, ...)
```
**注意**：原来如有"循环内每个 stroke 都 `Path()`"——同样统一改为 `reusablePath.reset()`。

- [ ] **Step 3: in-progress 渲染段把 `map { StrokePoint(...) }` 换成 buffer 重建**

定位 in-progress 渲染段类似：
```kotlin
val pts = inProgressPoints.map { StrokePoint(it.first, it.second, it.third) }
val stroke = Stroke(brush=..., color=..., width=..., points=pts)
```
改为：
```kotlin
if (inProgressBuffer.size != inProgressPoints.size) {
    inProgressBuffer.clear()
    for (p in inProgressPoints) {
        inProgressBuffer += com.fan.hwnote.app.model.entity.StrokePoint(p.first, p.second, p.third)
    }
} else {
    // size 相同：可能是新点 append，更新末尾若干；保守起见，全量覆写值
    for (i in inProgressPoints.indices) {
        val p = inProgressPoints[i]
        inProgressBuffer[i] = com.fan.hwnote.app.model.entity.StrokePoint(p.first, p.second, p.third)
    }
}
val stroke = Stroke(brush=..., color=..., width=..., points=inProgressBuffer)
```
**注意**：`Stroke.points` 类型若是 `List<StrokePoint>`，`inProgressBuffer` 作为 `List` 传入即可（MutableList 是 List）。

- [ ] **Step 4: ACTION_UP / clear 时清 buffer**

定位 `onTouchEvent` 内 ACTION_UP 把 in-progress 提交进 `strokes` 后清 `inProgressPoints` 的位置，**之后**追加：
```kotlin
inProgressBuffer.clear()
```
`clear()` 函数同样追加。

- [ ] **Step 5: 构建 + 跑测**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug :app:test`
Expected: BUILD SUCCESSFUL，68/68 PASSED。

- [ ] **Step 6: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt
git commit -m "perf(m7): onDraw 复用 Path + in-progress 点 buffer，去掉每帧分配"
```

---

### Task 6：Camera 跨进程死亡 `SavedInstanceState` 持久化（A1）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**背景：** 系统在 Camera Intent 占满前台时极易杀宿主进程；用户拍完返回 → 进程重建 → `pendingCameraOutputUri/File` 为 null → `cameraLauncher` 回调拿不到 uri 丢照片 + cache 文件常驻泄漏。

- [ ] **Step 1: 在 companion object 加 key 常量**

在 `NoteEditorActivity` 的 `companion object` 内追加：
```kotlin
private const val STATE_CAMERA_URI = "pendingCameraOutputUri"
private const val STATE_CAMERA_FILE = "pendingCameraOutputFile"
```

- [ ] **Step 2: 覆写 `onSaveInstanceState`**

在 `onPause` 旁边追加（任何顺序均可，按类内现有方法排版）：
```kotlin
override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    pendingCameraOutputUri?.let { outState.putParcelable(STATE_CAMERA_URI, it) }
    pendingCameraOutputFile?.let { outState.putString(STATE_CAMERA_FILE, it.absolutePath) }
}
```

- [ ] **Step 3: `onCreate` 内 `savedInstanceState != null` 时恢复**

在 `onCreate` 末尾（`loadNote()` 调用**之前**）追加：
```kotlin
if (savedInstanceState != null) {
    pendingCameraOutputUri =
        @Suppress("DEPRECATION") savedInstanceState.getParcelable(STATE_CAMERA_URI)
    pendingCameraOutputFile = savedInstanceState.getString(STATE_CAMERA_FILE)?.let { java.io.File(it) }
}
```

- [ ] **Step 4: 构建 + 跑测**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug :app:test`
Expected: BUILD SUCCESSFUL，68/68 PASSED。

- [ ] **Step 5: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "fix(m7): Camera 待回填 Uri/File 走 SavedInstanceState，避免进程死亡丢照片"
```

---

### Task 7：新笔记 `save-in-flight` 标志互斥（A2）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**背景：** 用户在新笔记（id==0L）下点图片图标 → `ensureNoteSavedAndThen` 走 IO INSERT；同瞬按 Home → `onPause.saveNote` 也走 IO INSERT，两条 INSERT 各自得到不同 newId，DB 里 double-insert。窄但真实。

**方案：** 加 `@Volatile var saveInFlight = false` 标志。`ensureNoteSavedAndThen` 与 `saveNote` 在新笔记分支判 + 上锁；任一进入 IO 块即立 true、回主线程恢复 false。`saveNote` 在 onPause 走到这里如果 saveInFlight==true 就直接 return（这次 onPause 不重复保存，ensureNoteSavedAndThen 的 save 已经替你做了）。

- [ ] **Step 1: 加字段**

在 `NoteEditorActivity` 类字段区追加：
```kotlin
@Volatile private var saveInFlight: Boolean = false
```

- [ ] **Step 2: 改写 `ensureNoteSavedAndThen` 加锁**

定位 `ensureNoteSavedAndThen` 内 `lifecycleScope.launch(Dispatchers.IO) { ... }` 块。改为：
```kotlin
if (saveInFlight) return
saveInFlight = true
val title = titleInput.text.toString()
val toSave = presenter.collectCurrentNote(title)
lifecycleScope.launch(Dispatchers.IO) {
    val newId = NoteRepository.save(toSave)
    withContext(Dispatchers.Main) {
        saveInFlight = false
        if (newId > 0L) {
            noteId = newId
            loadedNote = toSave.copy(id = newId)
            presenter.noteId = newId
            block()
        } else {
            android.widget.Toast.makeText(this@NoteEditorActivity,
                R.string.image_save_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
```

- [ ] **Step 3: 改写 `saveNote` 在新笔记分支跳过 if in-flight**

定位 `saveNote` 内：
```kotlin
val loaded = loadedNote ?: return
val title = titleInput.text.toString()
val toSave = presenter.collectCurrentNote(title).copy(id = loaded.id)
val isAllEmpty = title.isEmpty() && toSave.plainText.isEmpty()
if (loaded.id == 0L && isAllEmpty) return
```
之后、`lifecycleScope.launch(Dispatchers.IO)` 之前插入：
```kotlin
if (loaded.id == 0L && saveInFlight) return  // ensureNoteSavedAndThen 正在跑同一条 INSERT
```
**注意**：已落库笔记（id>0）走 UPDATE，无 double-insert 风险，不必判 flag。

- [ ] **Step 4: 构建 + 跑测**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug :app:test`
Expected: BUILD SUCCESSFUL，68/68 PASSED。

- [ ] **Step 5: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "fix(m7): 新笔记 save-in-flight 标志互斥，避免 ensureNoteSavedAndThen × onPause 双 INSERT"
```

---

### Task 8：`NoteRepository.save` 失败 Toast 提示（B3）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**背景：** `saveNote` 内若 `NoteRepository.save` 返回 0/负数（DB 写失败），当前静默吞掉，用户以为已存其实没存。`ensureNoteSavedAndThen` 已有 toast（复用 `image_save_failed`），但语义错位。本任务统一加专属 `note_save_failed`。

- [ ] **Step 1: strings.xml 新增**

在"通用"段落（`action_cancel` 之后）追加：
```xml
<string name="note_save_failed">笔记保存失败</string>
```

- [ ] **Step 2: `saveNote` 在 IO 失败分支加 Toast**

修改 `saveNote` 内 `lifecycleScope.launch(Dispatchers.IO) { ... }` 块。当前：
```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val newId = NoteRepository.save(toSave)
    if (loaded.id == 0L && newId > 0) {
        noteId = newId
        loadedNote = toSave.copy(id = newId)
        presenter.noteId = newId
    }
}
```
改为：
```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val newId = NoteRepository.save(toSave)
    withContext(Dispatchers.Main) {
        if (loaded.id == 0L && newId > 0) {
            noteId = newId
            loadedNote = toSave.copy(id = newId)
            presenter.noteId = newId
        } else if (newId <= 0L) {
            android.widget.Toast.makeText(this@NoteEditorActivity,
                R.string.note_save_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
```
**注意**：`UPDATE` 路径 `newId` 在 M2 Repository 实现里返回正数（同 id）即视为成功；`<=0` 才视为失败。

- [ ] **Step 3: `ensureNoteSavedAndThen` 失败分支换成 `note_save_failed`**

定位 `ensureNoteSavedAndThen` 内 `R.string.image_save_failed` Toast，改为 `R.string.note_save_failed`（"插入图片"流程里也是因为 INSERT 失败导致没拿到 noteId，根因是笔记保存失败）。

- [ ] **Step 4: 构建 + 跑测**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug :app:test`
Expected: BUILD SUCCESSFUL，68/68 PASSED。

- [ ] **Step 5: Commit**

```bash
git add code/HuaWeiNote/app/src/main/res/values/strings.xml code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "fix(m7): 笔记保存失败弹 Toast 提示，避免静默丢失"
```

---

### Task 9：图片加载失败 Toast + 自动移除块（B2）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/ImageBlockView.kt`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`

**背景：** 用户从相册插入的图片若中途被外部删除/损坏，再次进笔记时 Glide 加载失败，目前显示占位但块仍然占位永久驻留笔记里。期望：失败 → Toast `image_load_failed` + 自动从笔记移除该 ImageBlock + 把 jpg 从磁盘删掉。

- [ ] **Step 1: 先 Read 找到 `ImageBlockView` 当前 Glide 调用位置**

Run: `grep -n "Glide\|.into(\|.load(\|RequestListener" code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/ImageBlockView.kt`
记录 Glide 链调用行。

- [ ] **Step 2: `ImageBlockView.bind` 内 Glide 链加 `RequestListener`**

在 Glide 链上 `.into(...)` 之前插：
```kotlin
.listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
    override fun onLoadFailed(
        e: com.bumptech.glide.load.engine.GlideException?,
        model: Any?,
        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
        isFirstResource: Boolean,
    ): Boolean {
        // post 到下一帧再回调：避免在 Glide 内部回调里同步移除 view 触发 ConcurrentModification
        post {
            android.widget.Toast.makeText(context,
                com.fan.hwnote.app.R.string.image_load_failed,
                android.widget.Toast.LENGTH_SHORT).show()
            callback?.onRequestDelete(this@ImageBlockView)
        }
        return false // false → Glide 继续显示 error drawable（若有），不抢占
    }
    override fun onResourceReady(
        resource: android.graphics.drawable.Drawable,
        model: Any,
        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
        dataSource: com.bumptech.glide.load.DataSource,
        isFirstResource: Boolean,
    ): Boolean = false
})
```

- [ ] **Step 3: 确认 `EditorPresenter.onRequestDelete` 对 ImageBlockView 已会清磁盘 jpg**

Read `EditorPresenter.onRequestDelete` 已有逻辑：
```kotlin
override fun onRequestDelete(view: BlockView) {
    val idx = currentBlocks.indexOf(view)
    if (idx <= 0) return // 第一块不可删
    if (view is ImageBlockView) { ... 删 jpg ... }
    container.removeView(view)
    currentBlocks.removeAt(idx)
    ...
}
```
**问题**：`if (idx <= 0) return` ——若坏图刚好是笔记第一块（极端但可能），会无法移除。本任务范围内**不放开此约束**（笔记必须保留至少一个块，否则 UI 崩），但要在 ImageBlockView 的 onLoadFailed 加日志 + Toast 仍要给（即使没真删，用户也知道这张图坏了）。

具体改 Presenter 增加一个公有方法：
```kotlin
/** 图片加载失败的安全移除：第一块时退化为换成空 TextBlock，避免列表为空崩溃。 */
fun removeImageBlockOnLoadFailure(view: ImageBlockView) {
    val idx = currentBlocks.indexOf(view)
    if (idx < 0) return
    // 删 jpg
    val block = view.toBlock() as? Block.ImageBlock
    if (block != null && noteId > 0L) {
        runCatching {
            com.fan.hwnote.app.model.storage.NoteFileStorage(context)
                .imageFile(noteId, block.fileName).delete()
        }
    }
    container.removeView(view)
    currentBlocks.removeAt(idx)
    if (idx == 0 && currentBlocks.isEmpty()) {
        addTextBlockView(emptyTextBlock())
        (currentBlocks[0] as TextBlockView).focusEditEnd()
    }
}
```
然后 `ImageBlockView` 的 onLoadFailed 改成：
```kotlin
post {
    android.widget.Toast.makeText(context, com.fan.hwnote.app.R.string.image_load_failed,
        android.widget.Toast.LENGTH_SHORT).show()
    (callback as? com.fan.hwnote.app.controller.editor.EditorPresenter)
        ?.removeImageBlockOnLoadFailure(this@ImageBlockView)
        ?: callback?.onRequestDelete(this@ImageBlockView) // 兜底
}
```

- [ ] **Step 4: 构建 + 跑测**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug :app:test`
Expected: BUILD SUCCESSFUL，68/68 PASSED。

- [ ] **Step 5: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/ImageBlockView.kt code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt
git commit -m "fix(m7): 图片加载失败弹 Toast 并自动移除坏块（含磁盘 jpg 清理）"
```

---

### Task 10：相机被拒引导跳系统设置（B1）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**背景：** 用户首次拒绝相机权限 → 当前只 Toast `camera_permission_denied`。下次再点拍照仍会再请求一次，但用户若选"不再询问"就再也弹不出系统弹窗，相机功能永远卡死。需要：被拒后弹 AlertDialog "权限已被拒绝，去系统设置授权？" → 跳 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`。

- [ ] **Step 1: strings.xml 新增 3 条**

在"M5 图片块"段落（`camera_unavailable` 之后）追加：
```xml
<string name="camera_permission_dialog_title">需要相机权限</string>
<string name="camera_permission_dialog_message">无法拍照。请在系统设置中授予相机权限。</string>
<string name="action_open_settings">去设置</string>
```

- [ ] **Step 2: 加 `showCameraPermissionDialog()` 私有方法**

在 `NoteEditorActivity` 内合适位置（`launchCameraWithPermission` 旁）追加：
```kotlin
private fun showCameraPermissionDialog() {
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(R.string.camera_permission_dialog_title)
        .setMessage(R.string.camera_permission_dialog_message)
        .setPositiveButton(R.string.action_open_settings) { _, _ ->
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
            runCatching { startActivity(intent) }
        }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}
```

- [ ] **Step 3: 改写 `cameraPermissionLauncher` 回调**

定位：
```kotlin
private val cameraPermissionLauncher = registerForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) launchCamera()
    else android.widget.Toast.makeText(this,
        R.string.camera_permission_denied, android.widget.Toast.LENGTH_SHORT).show()
}
```
改为：
```kotlin
private val cameraPermissionLauncher = registerForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) launchCamera()
    else showCameraPermissionDialog()
}
```
**说明**：原 Toast `camera_permission_denied` 仍保留在 strings.xml（短期内未被引用，可在 C1 章节顺手清理或保留）。

- [ ] **Step 4: 构建 + 跑测**

Run: `cd code/HuaWeiNote && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug :app:test`
Expected: BUILD SUCCESSFUL，68/68 PASSED。

- [ ] **Step 5: Commit**

```bash
git add code/HuaWeiNote/app/src/main/res/values/strings.xml code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "fix(m7): 相机被拒后弹引导对话框跳系统设置授权"
```

---

### Task 11：PRD §9 完整手测 8 项 + 修发现的问题 + STATUS 收尾（C1）

**Files:**
- Modify: `docs/superpowers/STATUS.md`
- Possibly Modify: 任何手测发现 bug 的文件（事先未知）

**手测清单（PRD §9 原文）：**

- [ ] 1. 新建空笔记 → 输入标题 → 退出 → 列表能看到
- [ ] 2. 新建笔记输入文本 + 加粗 + 字号大 → 退出再进 → 样式保留
- [ ] 3. 插入相册图（多选 3 张）+ 拍照 1 张 → 退出再进 → 图片仍在
- [ ] 4. 创建清单 5 项，勾掉 2 项 → 退出再进 → 勾选状态保留，文字加删除线
- [ ] 5. 进入手写模式，画 → 撤销 → 重做 → 切笔种切颜色画 → 橡皮擦掉 → 退出再进 → 笔画保留
- [ ] 6. 笔记列表搜索 "李雷" → 命中含此字符的所有笔记
- [ ] 7. 收藏切换 → 列表中出现 ⭐ 角标 → 切换排序 → 顺序变化
- [ ] 8. 删除笔记 → 列表移除 → `filesDir/notes/<id>` 目录被清理（`adb shell run-as com.fan.hwnote.app ls files/notes/` 验证）

**M7 额外手测（本里程碑改动验证）：**

- [ ] 9. 空块/空行不再显示 "开始记录…" hint（M6-UX 阶段已修，回归确认）
- [ ] 10. 编辑区下方任意空白点击聚焦末尾文本块（M6-UX 阶段已修，回归确认）
- [ ] 11. 手写模式点"清空"→ 对话框显示"清空当前所有手写笔画？此操作不可恢复"（T2 验证）
- [ ] 12. 手写画 50+ 笔画后橡皮 + 撤销重做流畅，无明显卡顿（T4 T5 性能验证）
- [ ] 13. 拍照时强杀进程（adb shell am kill com.fan.hwnote.app）→ 重启进编辑器 → 拍照流程恢复，照片正常插入（T6 验证）
- [ ] 14. 新笔记点图片图标后立刻按 Home → 重进列表 → 只看到一条新笔记（不是 2 条，T7 验证）
- [ ] 15. 故意从笔记外删一张已插入的 jpg（`adb shell run-as com.fan.hwnote.app rm files/notes/<id>/images/<name>.jpg`）→ 重进笔记 → Toast "图片加载失败"，该块从笔记中消失（T9 验证）
- [ ] 16. 在系统设置里手动关闭相机权限 → 笔记里点拍照 → 弹"权限已被拒绝，去系统设置授权？"对话框，点"去设置"跳到本应用详情页（T10 验证）

- [ ] **Step A: 真机走查（顺序无强约束，按 1-16 跑一遍）**

逐项验证，记录是否通过。**任何不通过的视为 M7 范围内问题，回到对应文件修复**（小修不限 task 数；大问题先评估再决定是否纳入本里程碑）。

- [ ] **Step B: STATUS.md 追加"## M7 完成详情"小节**

在"## M6 完成详情（2026-05-23）"小节**之前**插入"## M7 完成详情（2026-05-23）"：
```markdown
## M7 完成详情（2026-05-23）

**产出（11 任务 + 多个 fix commit）：**
- **资源/文案清理**（T1 T2）：删 `toast_handwriting_placeholder` 死资源；手写清空对话框新增专属文案 `dialog_clear_handwriting_message`。
- **代码债**（T3 T6 T7）：HandwritingOverlayView 加 `strokesMut()` 替代硬转 + `isHandwritingMode` setter 清 `gestureSnapshot`；Camera 待回填 `Uri/File` 走 `SavedInstanceState`；新笔记 `@Volatile var saveInFlight` 标志互斥 `ensureNoteSavedAndThen` × `onPause.saveNote`。
- **热路径性能**（T4 T5）：BrushPainter 按 `(brush,color,width)` 三键缓存 Paint 实例（+3 项单测）；HandwritingOverlayView.onDraw 复用 `reusablePath` + `inProgressBuffer`，去掉每帧分配。
- **错误处理**（T8 T9 T10）：`NoteRepository.save<=0L` 弹 Toast；图片 Glide 加载失败 Toast + 自动移除块（含磁盘 jpg 清理）；相机被拒后弹 AlertDialog 引导跳 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`。

**测试统计：** `:app:clean :app:assembleDebug :app:test` 全绿，**68 项 PASSED**（M2 42 + M4 SpanConverter 15 + M5 ImageCompressor 3 + M6 StrokeEraser 5 + M7 BrushPainter 3）。

**M7 commit 列表（按时间序，待执行后回填）：**
- `<sha1>` chore(m7): 删 toast_handwriting_placeholder 死资源
- ...

**PRD §9 完整手测 8 项 + M7 额外手测 8 项：** 16/16 PASS（真机走查 2026-05-23）。

**MVP 收官。** 7 个里程碑全部完成；STATUS 顶部状态标 "✅ MVP 完成"。
```

- [ ] **Step C: STATUS.md 顶部里程碑表 M7 行改为 ✅**

定位顶部"## 当前状态"小节里的 7 里程碑表，把 `| M7 打磨 | ⏳ 未开始 | — |` 改为：
```markdown
| M7 打磨 | ✅ **完成** | 11 任务，68/68 测试 PASSED，16/16 手测 PASS |
```
同步把"最后更新"日期改为 `2026-05-23（M7 完成 / MVP 收官）`。把"7. 代码实施"行改为 `🟢 **完成（MVP）**`。

- [ ] **Step D: Commit STATUS**

```bash
git add docs/superpowers/STATUS.md
git commit -m "docs(m7): 记录 M7 打磨完成，MVP 收官"
```

---

## 不动的东西（明确边界）

- **数据层（M2 NoteRepository / NoteDbHelper / NoteJson / NoteFileStorage）** — 零改动。
- **Block 模型 / Stroke 模型 / SpanConverter** — 零改动。
- **PRD 架构 / 7 里程碑 / 7 笔种 / 8 色 / MVP scope** — 不重新协商。
- **AGP/Kotlin/Gradle/SDK 版本** — 不动。
- **gallery / picker / share / 深色模式 / 分类 / 加锁 / 回收站** — 仍然不做（PRD 明确 out-of-scope）。

## 验证（自动 + 真机）

**自动（每任务后）：**
- ✅ `./gradlew :app:assembleDebug` 绿
- ✅ `./gradlew :app:test` 68 项 PASSED（前 3 任务保持 65；T4 起到 68）

**真机（T11 集中走查）：**
- ✅ PRD §9 完整手测 8 项
- ✅ M7 额外手测 8 项

## 执行方式

Subagent-Driven Development（同 M5/M6/M6-UX）：每任务 implementer DONE → spec reviewer → code quality reviewer → fix（如需）→ re-review → 标完成。

**T1-T2 是最小热身（< 30min）；T3-T7 是核心改动；T8-T10 是错误处理打磨；T11 真机走查 + STATUS 收尾。**

预估 4-6 小时。
