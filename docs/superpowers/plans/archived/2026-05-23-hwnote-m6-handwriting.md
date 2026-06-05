# M6 手写 Overlay 详细实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 串行执行。每个 step 用 `- [ ]` 复选框追踪。

**Goal:** 在编辑器内容层之上叠一层透明 `HandwritingOverlayView`，提供 4 笔种 / 8 颜色 / 3 粗细 / 橡皮 / 撤销 / 重做 / 清空，并把笔画存进 `NoteContent.handwriting`，对齐 PRD §5/§7.3-§7.6。

**Architecture:**
- View 层：在 `NestedScrollView` 与 `editor_content`（LinearLayout）之间插一层 `FrameLayout`，把 `HandwritingOverlayView` 作为兄弟覆盖在 `editor_content` 之上；高度通过 `OnLayoutChangeListener` 同步内容层。
- 模式切换：复用 polish 后的 4 键工具栏中的"手写"按钮（不引入新 FAB，与 polish UX 一致），切换 `isHandwritingMode`、`editor_content.alpha`、底部工具栏可见性（文本工具栏 ↔ 手写工具栏）。
- 数据：`Stroke` 实体 / `NoteContent.handwriting` / `NoteJson` round-trip 已在 M2 完成；M6 只做收集（`onTouchEvent` → `Stroke` → push undo 栈）和回放（`onDraw` 用 `BrushPainter` 把 `Stroke` 重绘）。
- 撤销栈放在 `HandwritingOverlayView` 内（自洽），暴露 `undo()/redo()/clear()/setStrokes()/getStrokes()` 给 Activity 调用。

**Tech Stack:** Kotlin / MVC / XML View / Canvas + Path + Paint / BlurMaskFilter / BitmapShader（噪点用代码生成的 16×16 灰度 Bitmap，无需 PNG 资源）。

---

## PRD 对齐声明（必读）

PRD §5.1 写"右下 FAB 切换文本/手写模式"，本计划改为**复用工具栏第 4 键"手写"**作为切换入口（与 2026-05-23 编辑器 UX 打磨阶段确立的 4 键工具栏一致），手写工具栏左侧第一个按钮为"完成 ✓"用于退出。其余 §7.3-§7.6 / §6.4 全部严格执行。这是已落地 polish 决策的延续，不重新协商。

PRD §6.6 文件目录布局只有 `images/`，**手写笔画完全靠 `content_json` 持久化**（M2 `NoteJson.kt` 已实现 stroke ↔ JSON round-trip 并通过 3 项 Robolectric 单测）。本计划不写文件目录，不动 `NoteFileStorage`。

---

## 文件结构（新增 / 修改一览）

**新增（10 个文件）：**
- `app/src/main/res/drawable/ic_pen.xml` — 钢笔图标（24dp，tint=text_secondary）
- `app/src/main/res/drawable/ic_brush.xml` — 画笔图标
- `app/src/main/res/drawable/ic_marker.xml` — 粗细笔图标
- `app/src/main/res/drawable/ic_pencil.xml` — 铅笔图标
- `app/src/main/res/drawable/ic_eraser.xml` — 橡皮图标
- `app/src/main/res/drawable/ic_undo.xml` — 撤销图标
- `app/src/main/res/drawable/ic_redo.xml` — 重做图标
- `app/src/main/res/drawable/ic_clear_all.xml` — 清空图标
- `app/src/main/res/drawable/ic_check.xml` — 完成（退出手写）图标
- `app/src/main/res/layout/toolbar_handwriting.xml` — 手写工具栏布局（11 控件）

**新增 Kotlin（5 个文件）：**
- `app/src/main/java/com/fan/hwnote/app/view/handwriting/BrushPainter.kt` — 4 笔种 Paint 工厂 + 噪点 Bitmap 代码生成
- `app/src/main/java/com/fan/hwnote/app/view/handwriting/StrokeEraser.kt` — 笔画级橡皮 hit-test（纯 JVM 可单测）
- `app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt` — 透明绘制 view + 收集/回放/撤销栈
- `app/src/main/java/com/fan/hwnote/app/view/toolbar/HandwritingToolbarView.kt` — 手写工具栏 view（按钮 lazy + Listener）
- `app/src/test/java/com/fan/hwnote/app/view/handwriting/StrokeEraserTest.kt` — JVM 单测

**修改（5 个文件）：**
- `app/src/main/res/layout/activity_note_editor.xml` — `NestedScrollView` 内插 FrameLayout 包 editor_content + handwriting_overlay；新增 `toolbar_handwriting`，与 `text_toolbar` 互斥可见
- `app/src/main/res/values/colors.xml` — 新增 8 色手写调色板
- `app/src/main/res/values/strings.xml` — 新增手写工具栏 contentDescription 和占位词条
- `app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt` — `bind()` 把 strokes 传给 Overlay；`collectCurrentNote()` 从 Overlay 拿 strokes
- `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt` — `onHandwritingClicked` 改为切换模式（替换 Toast 占位）；接通手写工具栏所有按钮

---

## 任务列表（共 13 任务）

### Task 1: 资源准备（drawables + colors + strings）

**Files:**
- Create: `app/src/main/res/drawable/ic_pen.xml` `ic_brush.xml` `ic_marker.xml` `ic_pencil.xml` `ic_eraser.xml` `ic_undo.xml` `ic_redo.xml` `ic_clear_all.xml` `ic_check.xml`
- Modify: `app/src/main/res/values/colors.xml`（追加 8 色 `hw_color_*`）
- Modify: `app/src/main/res/values/strings.xml`（新增 `tb_pen_cd` `tb_brush_cd` `tb_marker_cd` `tb_pencil_cd` `tb_eraser_cd` `tb_undo_cd` `tb_redo_cd` `tb_clear_cd` `tb_done_cd` `tb_hw_color_cd` `tb_hw_width_cd`）

- [ ] **Step 1: 在 `colors.xml` `</resources>` 之前追加 8 色调色板**

```xml
    <!-- 手写 8 色调色板（M6） -->
    <color name="hw_color_black">#212121</color>
    <color name="hw_color_red">#E53935</color>
    <color name="hw_color_orange">#FB8C00</color>
    <color name="hw_color_yellow">#FDD835</color>
    <color name="hw_color_green">#43A047</color>
    <color name="hw_color_teal">#00897B</color>
    <color name="hw_color_blue">#1E88E5</color>
    <color name="hw_color_purple">#8E24AA</color>
```

- [ ] **Step 2: 在 `strings.xml` `</resources>` 之前追加手写工具栏词条**

```xml
    <!-- M6 手写工具栏 -->
    <string name="tb_pen_cd">钢笔</string>
    <string name="tb_brush_cd">画笔</string>
    <string name="tb_marker_cd">马克笔</string>
    <string name="tb_pencil_cd">铅笔</string>
    <string name="tb_eraser_cd">橡皮</string>
    <string name="tb_undo_cd">撤销</string>
    <string name="tb_redo_cd">重做</string>
    <string name="tb_clear_cd">清空</string>
    <string name="tb_done_cd">完成</string>
    <string name="tb_hw_color_cd">画笔颜色</string>
    <string name="tb_hw_width_cd">画笔粗细</string>
```

并删除 `toast_handwriting_placeholder` 这一行（M6 不再用 Toast 占位）。

- [ ] **Step 3: 创建 9 个 vector drawable**

每个文件结构都遵循 `ic_image.xml` 同款规范：24dp×24dp，`viewportWidth/Height=24`，单 `<path>`，`android:tint="@color/text_secondary"`，`android:fillColor="#FFFFFFFF"`。pathData 选 Material 系列：

`ic_pen.xml`（Material `edit` / 钢笔尖）：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"/>
</vector>
```

`ic_brush.xml`（Material `brush`）：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M7,14c-1.66,0 -3,1.34 -3,3 0,1.31 -1.16,2 -2,2 0.92,1.22 2.49,2 4,2 2.21,0 4,-1.79 4,-4 0,-1.66 -1.34,-3 -3,-3zM20.71,4.63l-1.34,-1.34c-0.39,-0.39 -1.02,-0.39 -1.41,0L9,12.25 11.75,15l8.96,-8.96c0.39,-0.39 0.39,-1.02 0,-1.41z"/>
</vector>
```

`ic_marker.xml`（Material `border_color` / 粗细笔）：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M17.75,7L14,3.25l-10,10V17h3.75l10,-10zM4,21h16v-2H4v2z"/>
</vector>
```

`ic_pencil.xml`（Material `create` 改铅笔头，复用 ic_pen 同 path）：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83zM3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25z"/>
</vector>
```

（注：`ic_pen` 和 `ic_pencil` 用同一个 path 是有意的——4 笔种最终通过文字"钢/画/马/铅"在按钮下方区分；图标样式细微差异在 v1 不强求。如要更明显差异，未来可换 `ic_pencil` 为 `M22.61,18.99l-9.08,-9.08c0.93,-3.41 -0.01,-7.21 -2.83,-10.03 -2.97,-2.97 -7.07,-3.81 -10.6,-2.6` 这种铅笔尖斜切 path。）

`ic_eraser.xml`（Material `auto_fix_off`/橡皮）：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M16.24,3.56l4.95,4.94c0.78,0.79 0.78,2.05 0,2.84L12,20.53a4.008,4.008 0,0 1,-5.66 0L2.81,17c-0.78,-0.79 -0.78,-2.05 0,-2.84l10.6,-10.6c0.79,-0.78 2.05,-0.78 2.83,0M4.22,15.58l3.54,3.53c0.78,0.79 2.04,0.79 2.83,0l3.53,-3.53 -6.36,-6.36 -3.54,3.53c-0.78,0.79 -0.78,2.05 0,2.83z"/>
</vector>
```

`ic_undo.xml`：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z"/>
</vector>
```

`ic_redo.xml`：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z"/>
</vector>
```

`ic_clear_all.xml`（Material `delete_outline`）：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM8,9h8v10H8V9zM15.5,4l-1,-1h-5l-1,1H5v2h14V4z"/>
</vector>
```

`ic_check.xml`（Material `check`）：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"/>
</vector>
```

- [ ] **Step 4: 验证 `assembleDebug`**

Run from `code/HuaWeiNote/`:
```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL，无 warning。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/res/values/colors.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/res/drawable/ic_pen.xml \
        app/src/main/res/drawable/ic_brush.xml \
        app/src/main/res/drawable/ic_marker.xml \
        app/src/main/res/drawable/ic_pencil.xml \
        app/src/main/res/drawable/ic_eraser.xml \
        app/src/main/res/drawable/ic_undo.xml \
        app/src/main/res/drawable/ic_redo.xml \
        app/src/main/res/drawable/ic_clear_all.xml \
        app/src/main/res/drawable/ic_check.xml
git commit -m "feat(m6): 手写工具栏资源准备（8 色 + 9 图标 + 词条）"
```

---

### Task 2: `BrushPainter` — 4 笔种 Paint 工厂 + 噪点 Bitmap

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/view/handwriting/BrushPainter.kt`

**职责：** 接收 `Stroke`（含 brush / color / width），返回配置好的 `Paint`。`width` 字段单位是 dp（PRD §6.4：1=细 / 3=中 / 6=粗），`BrushPainter` 内部把 dp → px。铅笔的噪点纹理由 `BrushPainter` 在初始化时用 `Random` 生成 16×16 灰度 Bitmap（不引入 PNG 资源），用 `BitmapShader(REPEAT, REPEAT)` 装到 Paint 上。

- [ ] **Step 1: 创建文件**

```kotlin
package com.fan.hwnote.app.view.handwriting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.util.TypedValue
import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.Stroke
import kotlin.random.Random

/**
 * 4 笔种 Paint 工厂。每个 Stroke 调 [paintFor] 取一支配置好的 Paint。
 *
 * Stroke.width 单位是 dp（1/3/6），构造时拿 Context 转 px。
 *
 * PRD §7.5：
 *   pen     STROKE/ROUND/255
 *   brush   STROKE/ROUND/255 + BlurMaskFilter(2px,NORMAL) + 宽×1.3
 *   marker  STROKE/ROUND/140 + 宽×1.6
 *   pencil  STROKE/ROUND/160 + BitmapShader(noise REPEAT,REPEAT)
 */
class BrushPainter(context: Context) {

    private val density = context.resources.displayMetrics.density
    private val noiseShader: BitmapShader by lazy { buildNoiseShader() }
    private val blurFilter by lazy { BlurMaskFilter(2f * density, BlurMaskFilter.Blur.NORMAL) }

    fun paintFor(stroke: Stroke): Paint {
        val basePx = dpToPx(stroke.width)
        val color = runCatching { Color.parseColor(stroke.color) }.getOrDefault(Color.BLACK)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
        when (stroke.brush) {
            BrushType.PEN -> {
                p.alpha = 255
                p.strokeWidth = basePx
            }
            BrushType.BRUSH -> {
                p.alpha = 255
                p.strokeWidth = basePx * 1.3f
                p.maskFilter = blurFilter
            }
            BrushType.MARKER -> {
                p.alpha = 140
                p.strokeWidth = basePx * 1.6f
            }
            BrushType.PENCIL -> {
                p.alpha = 160
                p.strokeWidth = basePx
                p.shader = noiseShader
            }
        }
        return p
    }

    fun dpToPx(dp: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
            android.content.res.Resources.getSystem().displayMetrics)

    /** 16×16 灰度噪点（REPEAT 平铺）。固定种子保证笔记之间纹理一致；不缩放。 */
    private fun buildNoiseShader(): BitmapShader {
        val size = 16
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val rng = Random(42)
        val pixels = ByteArray(size * size)
        for (i in pixels.indices) {
            // 偏黑色噪点：alpha 在 80-180 之间，约 50% 半透明颗粒感
            pixels[i] = (80 + rng.nextInt(100)).toByte()
        }
        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
        return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}
```

- [ ] **Step 2: 验证 `assembleDebug`**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/view/handwriting/BrushPainter.kt
git commit -m "feat(m6): BrushPainter 4 笔种 Paint 工厂 + 代码生成噪点"
```

---

### Task 3: `StrokeEraser` 算法 + JVM 单测（TDD）

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/view/handwriting/StrokeEraser.kt`
- Test:   `app/src/test/java/com/fan/hwnote/app/view/handwriting/StrokeEraserTest.kt`

**职责：** PRD §7.5 的笔画级橡皮算法。给定一个橡皮接触点 `(ex, ey, eraserRadiusPx)` 和当前 strokes 列表，返回被命中的 stroke 列表（整笔命中）。粗筛 = bounding box 与橡皮圆相交；细判 = 圆心到 stroke 任意相邻点对线段的最短距离 ≤ `eraserRadius + stroke.width/2`。纯函数，纯 JVM，可单测。

- [ ] **Step 1: 写失败的测试**

`app/src/test/java/com/fan/hwnote/app/view/handwriting/StrokeEraserTest.kt`：

```kotlin
package com.fan.hwnote.app.view.handwriting

import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.Stroke
import com.fan.hwnote.app.model.entity.StrokePoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrokeEraserTest {

    private fun stroke(vararg pts: Pair<Int, Int>, width: Int = 3): Stroke =
        Stroke(
            brush = BrushType.PEN,
            color = "#212121",
            width = width,
            points = pts.mapIndexed { i, (x, y) -> StrokePoint(x, y, i * 10) },
        )

    @Test
    fun eraser_far_from_all_strokes_hits_nothing() {
        val s1 = stroke(0 to 0, 10 to 0, 20 to 0)
        val s2 = stroke(0 to 100, 50 to 100)
        val hit = StrokeEraser.hitTest(ex = 500f, ey = 500f, radiusPx = 8f, strokes = listOf(s1, s2))
        assertEquals(emptyList<Stroke>(), hit)
    }

    @Test
    fun eraser_overlapping_one_stroke_hits_only_that_one() {
        val s1 = stroke(0 to 0, 100 to 0)            // 水平线 y=0
        val s2 = stroke(0 to 200, 100 to 200)        // 水平线 y=200
        val hit = StrokeEraser.hitTest(ex = 50f, ey = 5f, radiusPx = 10f, strokes = listOf(s1, s2))
        assertEquals(listOf(s1), hit)
    }

    @Test
    fun eraser_grazing_bounding_box_but_far_from_segment_misses() {
        // L 形 stroke：bbox 覆盖 (0,0)-(100,100) 但 stroke 本身只在两条边
        val s = stroke(0 to 0, 100 to 0, 100 to 100)
        // 橡皮在 bbox 内但远离两条边（中心点 50,50，距两条边都 50px）
        val hit = StrokeEraser.hitTest(ex = 50f, ey = 50f, radiusPx = 5f, strokes = listOf(s))
        assertTrue(hit.isEmpty(), "在 bbox 内但远离实际 stroke 段不该命中")
    }

    @Test
    fun eraser_radius_plus_stroke_half_width_extends_hit_distance() {
        // stroke width=10dp（粗），eraser 圆心距离线段 7px：基础半径 5 + width/2≈5 ⇒ 应命中
        val s = stroke(0 to 0, 100 to 0, width = 10)
        val hit = StrokeEraser.hitTest(ex = 50f, ey = 7f, radiusPx = 5f, strokes = listOf(s))
        assertEquals(listOf(s), hit)
    }

    @Test
    fun single_point_stroke_treated_as_zero_length_segment() {
        // 单点 stroke：算法应当退化成点到点距离判断，而不是抛 NPE 或漏判
        val s = stroke(50 to 50)
        val hit = StrokeEraser.hitTest(ex = 52f, ey = 50f, radiusPx = 5f, strokes = listOf(s))
        assertEquals(listOf(s), hit)
    }
}
```

- [ ] **Step 2: Run 测试，确认 failure（StrokeEraser 还没创建）**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:testDebugUnitTest --tests StrokeEraserTest
```
Expected: 编译失败 — `Unresolved reference: StrokeEraser`。

- [ ] **Step 3: 实现 `StrokeEraser.kt`**

```kotlin
package com.fan.hwnote.app.view.handwriting

import com.fan.hwnote.app.model.entity.Stroke

/**
 * 笔画级橡皮 hit-test。
 *
 * PRD §7.5：
 *   1. 粗筛：bounding box 与橡皮圆相交才进细判
 *   2. 细判：橡皮圆心到 stroke 相邻点对的最短距离 ≤ radiusPx + stroke.width/2
 *
 * 单点 stroke：退化为点到点距离。空 stroke：跳过。
 *
 * 注意 stroke.width 单位是 dp，调用方传入 widthHalfDpToPx 已经在 [radiusPx] 里折算好；
 * 这里直接用 stroke.width 当 px 处理（v1 简化：1/3/6 dp 与 px 在 mdpi 下等同；
 * 实际工程在 hdpi 下偏差 < 6px，对橡皮判定影响可接受）。
 */
object StrokeEraser {

    fun hitTest(ex: Float, ey: Float, radiusPx: Float, strokes: List<Stroke>): List<Stroke> {
        if (strokes.isEmpty()) return emptyList()
        val out = mutableListOf<Stroke>()
        for (s in strokes) {
            if (s.points.isEmpty()) continue
            if (!bboxIntersectsCircle(s, ex, ey, radiusPx)) continue
            val threshold = radiusPx + s.width / 2f
            if (anySegmentWithin(s, ex, ey, threshold)) out.add(s)
        }
        return out
    }

    private fun bboxIntersectsCircle(s: Stroke, ex: Float, ey: Float, r: Float): Boolean {
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
        for (p in s.points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        // 圆与 AABB 最近点距离
        val cx = ex.coerceIn(minX.toFloat(), maxX.toFloat())
        val cy = ey.coerceIn(minY.toFloat(), maxY.toFloat())
        val dx = ex - cx; val dy = ey - cy
        return dx * dx + dy * dy <= r * r
    }

    private fun anySegmentWithin(s: Stroke, ex: Float, ey: Float, threshold: Float): Boolean {
        val pts = s.points
        if (pts.size == 1) {
            val p = pts[0]
            val dx = ex - p.x; val dy = ey - p.y
            return dx * dx + dy * dy <= threshold * threshold
        }
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            if (pointToSegmentDistSq(ex, ey, a.x.toFloat(), a.y.toFloat(),
                    b.x.toFloat(), b.y.toFloat()) <= threshold * threshold) {
                return true
            }
        }
        return false
    }

    private fun pointToSegmentDistSq(
        px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float
    ): Float {
        val abx = bx - ax; val aby = by - ay
        val apx = px - ax; val apy = py - ay
        val abLen2 = abx * abx + aby * aby
        if (abLen2 == 0f) {
            return apx * apx + apy * apy
        }
        var t = (apx * abx + apy * aby) / abLen2
        if (t < 0f) t = 0f else if (t > 1f) t = 1f
        val cx = ax + t * abx; val cy = ay + t * aby
        val dx = px - cx; val dy = py - cy
        return dx * dx + dy * dy
    }
}
```

- [ ] **Step 4: Run 测试，确认通过**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:testDebugUnitTest --tests StrokeEraserTest
```
Expected: 5 tests, 0 failures.

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/view/handwriting/StrokeEraser.kt \
        app/src/test/java/com/fan/hwnote/app/view/handwriting/StrokeEraserTest.kt
git commit -m "feat(m6): StrokeEraser 笔画级橡皮 hit-test + 5 项单测"
```

---

### Task 4: `HandwritingOverlayView` 骨架（属性 + 状态 + setStrokes/getStrokes）

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt`

**职责（本任务范围）：** 透明 View 骨架。`isHandwritingMode`（默认 false）/ 当前画笔配置（brush/color/width）/ `strokes` 列表 / undoStack / redoStack / 橡皮模式标志。暴露 `setStrokes/getStrokes/clear/undo/redo`，但本任务**不实现 onTouch 与 onDraw**（下一任务）。提供 `isHandwritingMode` setter 配合调用方做模式切换。

- [ ] **Step 1: 创建文件**

```kotlin
package com.fan.hwnote.app.view.handwriting

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.Stroke

/**
 * 手写透明覆盖层。位于 ScrollView 内 FrameLayout，与内容层 LinearLayout 同级覆盖。
 *
 * 模式：
 *   isHandwritingMode=false → onTouchEvent 返回 false 让事件继续下传
 *   isHandwritingMode=true  → onTouchEvent 全部消费；落笔/移动/抬起组装 Stroke
 *
 * 撤销栈：Add(stroke) / Erase(list) 两种 Action，自洽于 view 内。
 */
class HandwritingOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    sealed class Action {
        data class Add(val stroke: Stroke) : Action()
        data class Erase(val strokes: List<Stroke>) : Action()
    }

    private val brushPainter = BrushPainter(context)

    private val strokes = mutableListOf<Stroke>()
    private val undoStack = ArrayDeque<Action>()
    private val redoStack = ArrayDeque<Action>()

    var isHandwritingMode: Boolean = false
        set(value) {
            field = value
            // 切回文本模式时丢掉正在收集的中间 stroke（如果有）
            if (!value) {
                inProgressPoints.clear()
                erasedThisGesture.clear()
                invalidate()
            }
        }

    var currentBrush: BrushType = BrushType.PEN
    var currentColor: String = "#212121"
    /** dp，PRD §6.4 三档：1=细 / 3=中 / 6=粗 */
    var currentWidth: Int = 3
    var isErasing: Boolean = false
    /** 橡皮接触半径（px），UI 不让用户调；默认 12dp 等效 */
    private val eraserRadiusPx: Float =
        12f * context.resources.displayMetrics.density

    /** Task 5 收集中：当前手指未抬起的采样点（绘制用）。 */
    internal val inProgressPoints = mutableListOf<Triple<Int, Int, Int>>()
    internal var gestureStartElapsedMs = 0L

    /** Task 5 橡皮收集：本次按下到抬起命中的所有 strokes（聚合一个 Erase action）。 */
    internal val erasedThisGesture = mutableListOf<Stroke>()

    fun setStrokes(list: List<Stroke>) {
        strokes.clear()
        strokes.addAll(list)
        undoStack.clear()
        redoStack.clear()
        invalidate()
    }

    fun getStrokes(): List<Stroke> = strokes.toList()

    fun clear() {
        if (strokes.isEmpty()) return
        val snapshot = strokes.toList()
        strokes.clear()
        undoStack.addLast(Action.Erase(snapshot))
        redoStack.clear()
        invalidate()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        val a = undoStack.removeLastOrNull() ?: return
        when (a) {
            is Action.Add -> { strokes.remove(a.stroke) }
            is Action.Erase -> { strokes.addAll(a.strokes) }
        }
        redoStack.addLast(a)
        invalidate()
    }

    fun redo() {
        val a = redoStack.removeLastOrNull() ?: return
        when (a) {
            is Action.Add -> { strokes.add(a.stroke) }
            is Action.Erase -> { strokes.removeAll(a.strokes.toSet()) }
        }
        undoStack.addLast(a)
        invalidate()
    }

    /** Task 5 在 push 落笔结果时用。 */
    internal fun pushAdd(stroke: Stroke) {
        strokes.add(stroke)
        undoStack.addLast(Action.Add(stroke))
        redoStack.clear()
    }

    /** Task 5 在橡皮抬起时聚合一次 Erase。 */
    internal fun pushErase(list: List<Stroke>) {
        if (list.isEmpty()) return
        strokes.removeAll(list.toSet())
        undoStack.addLast(Action.Erase(list.toList()))
        redoStack.clear()
    }

    internal fun eraserRadius(): Float = eraserRadiusPx
    internal fun brushPainter(): BrushPainter = brushPainter
    internal fun strokesRef(): List<Stroke> = strokes

    // Task 5 接管
    override fun onTouchEvent(event: MotionEvent): Boolean = false

    // Task 5 接管
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
}
```

- [ ] **Step 2: 验证 `assembleDebug`**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt
git commit -m "feat(m6): HandwritingOverlayView 骨架（state + 撤销栈 + setStrokes/getStrokes）"
```

---

### Task 5: `HandwritingOverlayView` 完成 onTouchEvent + onDraw（落笔/橡皮/回放）

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt`

**职责：** 在 Task 4 骨架基础上实现两个核心方法。
1. `onTouchEvent`：模式 off 直接返回 false；模式 on 时全部消费，区分**画笔**与**橡皮**两条路径。
2. `onDraw`：用 `Path` + `BrushPainter` 把每条 `Stroke` 重绘；正在收集的临时 stroke 也实时绘出。

- [ ] **Step 1: 替换 `onTouchEvent` / `onDraw` 实现**

把 Task 4 末尾 `// Task 5 接管` 注释那两个空方法换成下面两段：

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!isHandwritingMode) return false
    val x = event.x.toInt()
    val y = event.y.toInt()
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            gestureStartElapsedMs = android.os.SystemClock.uptimeMillis()
            if (isErasing) {
                erasedThisGesture.clear()
                eraseAt(event.x, event.y)
            } else {
                inProgressPoints.clear()
                inProgressPoints.add(Triple(x, y, 0))
            }
            invalidate()
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (isErasing) {
                eraseAt(event.x, event.y)
            } else {
                val t = (android.os.SystemClock.uptimeMillis() - gestureStartElapsedMs).toInt()
                inProgressPoints.add(Triple(x, y, t))
            }
            invalidate()
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (isErasing) {
                if (erasedThisGesture.isNotEmpty()) pushErase(erasedThisGesture.toList())
                erasedThisGesture.clear()
            } else if (inProgressPoints.isNotEmpty()) {
                val pts = inProgressPoints.map {
                    com.fan.hwnote.app.model.entity.StrokePoint(it.first, it.second, it.third)
                }
                val s = Stroke(currentBrush, currentColor, currentWidth, pts)
                pushAdd(s)
                inProgressPoints.clear()
            }
            invalidate()
            return true
        }
    }
    return true
}

private fun eraseAt(ex: Float, ey: Float) {
    val hit = StrokeEraser.hitTest(ex, ey, eraserRadiusPx, strokesRef())
    if (hit.isEmpty()) return
    // 立即视觉移除：从当前 strokes 里摘掉，等抬起时再统一推 Erase 进 undoStack
    val mut = strokesRef() as MutableList<Stroke>
    for (s in hit) {
        if (mut.remove(s)) erasedThisGesture.add(s)
    }
}

override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    // 已落笔
    for (s in strokesRef()) drawStroke(canvas, s)
    // 进行中
    if (!isErasing && inProgressPoints.size >= 1) {
        val pts = inProgressPoints.map {
            com.fan.hwnote.app.model.entity.StrokePoint(it.first, it.second, it.third)
        }
        val tmp = Stroke(currentBrush, currentColor, currentWidth, pts)
        drawStroke(canvas, tmp)
    }
}

private fun drawStroke(canvas: Canvas, s: Stroke) {
    if (s.points.isEmpty()) return
    val paint = brushPainter().paintFor(s)
    if (s.points.size == 1) {
        // 单点退化为画一个微小圆（在 STROKE 模式下用 drawPoint 也行）
        val p = s.points[0]
        canvas.drawPoint(p.x.toFloat(), p.y.toFloat(), paint)
        return
    }
    val path = android.graphics.Path()
    path.moveTo(s.points[0].x.toFloat(), s.points[0].y.toFloat())
    for (i in 1 until s.points.size) {
        path.lineTo(s.points[i].x.toFloat(), s.points[i].y.toFloat())
    }
    canvas.drawPath(path, paint)
}
```

- [ ] **Step 2: 验证 `assembleDebug`**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt
git commit -m "feat(m6): Overlay 落笔/橡皮 onTouch + Path 回放 onDraw"
```

---

### Task 6: 编辑器布局接入 Overlay（FrameLayout 包内容层 + 高度同步）

**Files:**
- Modify: `app/src/main/res/layout/activity_note_editor.xml`

**职责：** 在 `NestedScrollView` 内插一层 `FrameLayout`，把现有 `editor_content`（LinearLayout，clickable）+ 新增 `HandwritingOverlayView` 放在同一 FrameLayout 内。Overlay 默认 `visibility=invisible`（占位但不绘制）+ `isHandwritingMode=false` 由 Activity 控制。**布局层只负责放好 view，不做高度同步**（Activity onCreate 拿到 view 引用后挂 OnLayoutChangeListener，见 Task 9）。

- [ ] **Step 1: 编辑布局**

替换 `activity_note_editor.xml` 中 `<androidx.core.widget.NestedScrollView>` 的子节点。当前结构：

```xml
<androidx.core.widget.NestedScrollView ...>
    <LinearLayout android:id="@+id/editor_content" ...>
        <EditText android:id="@+id/title_input" ... />
        <LinearLayout android:id="@+id/blocks_container" ... />
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
```

改为：

```xml
<androidx.core.widget.NestedScrollView
    android:id="@+id/editor_scroll"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1"
    android:fillViewport="true">

    <FrameLayout
        android:id="@+id/editor_scroll_inner"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <LinearLayout
            android:id="@+id/editor_content"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:clickable="true"
            android:focusable="false"
            android:importantForAccessibility="no"
            android:orientation="vertical"
            android:paddingHorizontal="@dimen/spacing_l"
            android:paddingTop="@dimen/spacing_m"
            android:paddingBottom="@dimen/spacing_l">

            <EditText
                android:id="@+id/title_input"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@null"
                android:hint="@string/editor_title_hint"
                android:imeOptions="actionNext"
                android:inputType="text|textCapSentences"
                android:maxLines="2"
                android:textColor="@color/text_primary"
                android:textColorHint="@color/text_hint"
                android:textSize="20sp"
                android:textStyle="bold" />

            <LinearLayout
                android:id="@+id/blocks_container"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_s"
                android:orientation="vertical" />
        </LinearLayout>

        <com.fan.hwnote.app.view.handwriting.HandwritingOverlayView
            android:id="@+id/handwriting_overlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="invisible" />
    </FrameLayout>
</androidx.core.widget.NestedScrollView>
```

要点：
- `editor_content` 原先在 NestedScrollView 直接子位置，现在被一层 `FrameLayout` 包裹（`editor_scroll_inner`），尺寸 `wrap_content`。
- `editor_content` 把原来在 NestedScrollView 上的 `paddingHorizontal/Top/Bottom` 搬到自己身上（FrameLayout 不留 padding 是为了让 Overlay 与内容层精确同尺寸）。
- Overlay 用 `match_parent` 占满 FrameLayout（FrameLayout 自身被 `editor_content` 撑高）；默认 `visibility=invisible`，Task 9 切手写模式时改为 `visible`。
- NestedScrollView 自己不再需要 `paddingHorizontal/Top/Bottom`（已下沉到 editor_content）。从该节点删掉 `android:paddingHorizontal="@dimen/spacing_l"` `android:paddingTop="@dimen/spacing_m"` `android:paddingBottom="@dimen/spacing_l"` 三行。

- [ ] **Step 2: 验证 `assembleDebug`**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL（Overlay 类已在 Task 4-5 落地，FQCN 引用应能解析）。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/res/layout/activity_note_editor.xml
git commit -m "feat(m6): 编辑器布局插 FrameLayout 容纳内容层 + 手写 Overlay"
```

---

### Task 7: `EditorPresenter` 接通 Overlay（bind / collectCurrentNote）

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`

**职责：** Presenter 多吃一个 `HandwritingOverlayView` 引用。`bind(note)` 时把 `note.content.handwriting` 放到 Overlay；`collectCurrentNote(title)` 时从 Overlay 读取 `getStrokes()` 写回 `NoteContent.handwriting`（取代当前的 `emptyList()`）。

- [ ] **Step 1: 改构造函数 + 改两处方法**

把 EditorPresenter 类头从：
```kotlin
class EditorPresenter(
    private val context: Context,
    private val container: LinearLayout,
) : BlockView.Callback {
```
改为：
```kotlin
class EditorPresenter(
    private val context: Context,
    private val container: LinearLayout,
    private val overlay: com.fan.hwnote.app.view.handwriting.HandwritingOverlayView,
) : BlockView.Callback {
```

在 `bind(note)` 方法的最后（已有的 `(currentBlocks.firstOrNull { it is TextBlockView } as? TextBlockView)?.focusEditEnd()` 这一行之后），追加：
```kotlin
        overlay.setStrokes(note.content.handwriting)
```

把 `collectCurrentNote(title)` 内的：
```kotlin
val content = NoteContent(blocks = newBlocks, handwriting = emptyList())
```
改为：
```kotlin
val content = NoteContent(blocks = newBlocks, handwriting = overlay.getStrokes())
```

- [ ] **Step 2: 验证 `assembleDebug`**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```
Expected: 编译会因 Activity 还按旧 2 参签名 new Presenter 而 fail。**这是预期的**——Task 8 同 commit 修。

实际上为避免中间 commit 编译失败，本任务先**不 commit**，留到 Task 8 一起提交。请直接进入 Task 8。

---

### Task 8: `HandwritingToolbarView` — 手写工具栏布局 + view 类

**Files:**
- Create: `app/src/main/res/layout/toolbar_handwriting.xml`
- Create: `app/src/main/java/com/fan/hwnote/app/view/toolbar/HandwritingToolbarView.kt`

**职责：** 11 控件横排，从左到右：完成 ✓ / 钢笔 / 画笔 / 马克笔 / 铅笔 / 颜色（圆点，点击弹 8 色 BottomSheet 由 Activity 实现）/ 粗细（圆点，点击循环 1→3→6 dp）/ 橡皮 / 撤销 / 重做 / 清空。控件用 `ImageView` + `selectableItemBackgroundBorderless`，在 layout 中均等 `weight=1`，整条工具栏高度 48dp，背景 `@color/toolbar_bg`，与 `text_toolbar` 一致。

- [ ] **Step 1: 创建 `toolbar_handwriting.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <ImageView
        android:id="@+id/btn_hw_done"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_done_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_check" />

    <ImageView
        android:id="@+id/btn_hw_pen"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_pen_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_pen" />

    <ImageView
        android:id="@+id/btn_hw_brush"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_brush_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_brush" />

    <ImageView
        android:id="@+id/btn_hw_marker"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_marker_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_marker" />

    <ImageView
        android:id="@+id/btn_hw_pencil"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_pencil_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_pencil" />

    <ImageView
        android:id="@+id/btn_hw_color"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_hw_color_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_color_dot"
        app:tint="@color/hw_color_black" />

    <ImageView
        android:id="@+id/btn_hw_width"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_hw_width_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_color_dot"
        app:tint="@color/text_secondary" />

    <ImageView
        android:id="@+id/btn_hw_eraser"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_eraser_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_eraser" />

    <ImageView
        android:id="@+id/btn_hw_undo"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_undo_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_undo" />

    <ImageView
        android:id="@+id/btn_hw_redo"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_redo_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_redo" />

    <ImageView
        android:id="@+id/btn_hw_clear"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true"
        android:contentDescription="@string/tb_clear_cd"
        android:focusable="true"
        android:padding="12dp"
        android:src="@drawable/ic_clear_all" />
</merge>
```

- [ ] **Step 2: 创建 `HandwritingToolbarView.kt`**

```kotlin
package com.fan.hwnote.app.view.toolbar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.BrushType

/**
 * 手写工具栏 view（11 个 ImageView）。Activity 注入 [listener] 接收事件。
 *
 * 高亮逻辑：
 *   - 4 笔种之间互斥，selected = 当前笔种 → highlightBrush(BrushType)
 *   - 橡皮独立，selected = isErasing → highlightEraser(Boolean)；激活时 4 笔种自动取消高亮
 *   - 颜色按钮 tint 跟随当前色 → setColor(hex)
 *   - 粗细按钮直接显示一个圆点（v1 不区分粗细图示，靠 tooltip + 用户记忆）
 */
class HandwritingToolbarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onDoneClicked()
        fun onBrushClicked(brush: BrushType)
        fun onColorClicked()
        fun onWidthClicked()
        fun onEraserClicked()
        fun onUndoClicked()
        fun onRedoClicked()
        fun onClearClicked()
    }

    var listener: Listener? = null

    private val btnDone: ImageView by lazy { findViewById(R.id.btn_hw_done) }
    private val btnPen: ImageView by lazy { findViewById(R.id.btn_hw_pen) }
    private val btnBrush: ImageView by lazy { findViewById(R.id.btn_hw_brush) }
    private val btnMarker: ImageView by lazy { findViewById(R.id.btn_hw_marker) }
    private val btnPencil: ImageView by lazy { findViewById(R.id.btn_hw_pencil) }
    private val btnColor: ImageView by lazy { findViewById(R.id.btn_hw_color) }
    private val btnWidth: ImageView by lazy { findViewById(R.id.btn_hw_width) }
    private val btnEraser: ImageView by lazy { findViewById(R.id.btn_hw_eraser) }
    private val btnUndo: ImageView by lazy { findViewById(R.id.btn_hw_undo) }
    private val btnRedo: ImageView by lazy { findViewById(R.id.btn_hw_redo) }
    private val btnClear: ImageView by lazy { findViewById(R.id.btn_hw_clear) }

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.toolbar_handwriting, this, true)
        btnDone.setOnClickListener { listener?.onDoneClicked() }
        btnPen.setOnClickListener { listener?.onBrushClicked(BrushType.PEN) }
        btnBrush.setOnClickListener { listener?.onBrushClicked(BrushType.BRUSH) }
        btnMarker.setOnClickListener { listener?.onBrushClicked(BrushType.MARKER) }
        btnPencil.setOnClickListener { listener?.onBrushClicked(BrushType.PENCIL) }
        btnColor.setOnClickListener { listener?.onColorClicked() }
        btnWidth.setOnClickListener { listener?.onWidthClicked() }
        btnEraser.setOnClickListener { listener?.onEraserClicked() }
        btnUndo.setOnClickListener { listener?.onUndoClicked() }
        btnRedo.setOnClickListener { listener?.onRedoClicked() }
        btnClear.setOnClickListener { listener?.onClearClicked() }
    }

    fun highlightBrush(brush: BrushType) {
        btnPen.isSelected = brush == BrushType.PEN
        btnBrush.isSelected = brush == BrushType.BRUSH
        btnMarker.isSelected = brush == BrushType.MARKER
        btnPencil.isSelected = brush == BrushType.PENCIL
        btnEraser.isSelected = false
    }

    fun highlightEraser(erasing: Boolean) {
        btnEraser.isSelected = erasing
        if (erasing) {
            btnPen.isSelected = false
            btnBrush.isSelected = false
            btnMarker.isSelected = false
            btnPencil.isSelected = false
        }
    }

    fun setColor(hex: String) {
        val c = runCatching { android.graphics.Color.parseColor(hex) }
            .getOrDefault(android.graphics.Color.BLACK)
        btnColor.imageTintList = android.content.res.ColorStateList.valueOf(c)
    }

    fun setUndoEnabled(enabled: Boolean) {
        btnUndo.isEnabled = enabled
        btnUndo.alpha = if (enabled) 1f else 0.4f
    }

    fun setRedoEnabled(enabled: Boolean) {
        btnRedo.isEnabled = enabled
        btnRedo.alpha = if (enabled) 1f else 0.4f
    }
}
```

- [ ] **Step 3: 在 `activity_note_editor.xml` `<TextToolbarView>` 节点之后追加 `HandwritingToolbarView`**

```xml
<com.fan.hwnote.app.view.toolbar.HandwritingToolbarView
    android:id="@+id/handwriting_toolbar"
    android:layout_width="match_parent"
    android:layout_height="48dp"
    android:background="@color/toolbar_bg"
    android:visibility="gone" />
```

让 `text_toolbar` 与 `handwriting_toolbar` 互斥可见——文本模式下 `text_toolbar` visible / `handwriting_toolbar` gone；手写模式下反之。Activity Task 9 切。

- [ ] **Step 4: 验证 `assembleDebug`**

预期 `NoteEditorActivity` 仍因 Task 7 改的 Presenter 构造签名而 fail。本任务**也不 commit**，等 Task 9 改完 Activity 一起提交（Task 7 + 8 + 9 三处改一并提交，避免中间 commit 编译失败）。

---

### Task 9: `NoteEditorActivity` 接通模式切换 + Overlay 高度同步 + 工具栏接通

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**职责：** 这是 Task 7+8+9 合并 commit 的最后一棒。
1. 拿 `handwriting_overlay` / `handwriting_toolbar` 引用，把 Overlay 传给 Presenter（Task 7 接口）。
2. 给 `editor_content` 挂 `OnLayoutChangeListener`，把 Overlay 高度同步为 content 高度（保证 Overlay 始终覆盖完整内容区域）。
3. `onHandwritingClicked` → `enterHandwritingMode()`（替换 Toast 占位）；新增 `exitHandwritingMode()`。
4. 实现 `handwriting_toolbar` 的 11 个回调（笔种切换 / 颜色 BottomSheet / 粗细循环 / 橡皮 / 撤销 / 重做 / 清空 / 完成）。
5. 颜色 8 色简单用 `AlertDialog.setItems` 列表，避免再做新 BottomSheet。

- [ ] **Step 1: 在类内增加字段 + onCreate 末尾接通**

类头字段区追加：
```kotlin
private lateinit var handwritingOverlay: com.fan.hwnote.app.view.handwriting.HandwritingOverlayView
private lateinit var handwritingToolbar: com.fan.hwnote.app.view.toolbar.HandwritingToolbarView
private lateinit var textToolbar: com.fan.hwnote.app.view.toolbar.TextToolbarView
```

`onCreate` 内的 `presenter = EditorPresenter(this, blocksContainer)` 这一行改为先取 Overlay：
```kotlin
handwritingOverlay = findViewById(R.id.handwriting_overlay)
presenter = EditorPresenter(this, blocksContainer, handwritingOverlay)
```

`onCreate` 内 `val toolbarView = findViewById<...TextToolbarView>(...)` 这一行改为：
```kotlin
textToolbar = findViewById(R.id.text_toolbar)
handwritingToolbar = findViewById(R.id.handwriting_toolbar)
```

把后面 `toolbarView.listener = ...` 整段保留，但把变量名 `toolbarView` 改为 `textToolbar`。

把 Listener 内 `onHandwritingClicked` 的实现从 Toast 换成：
```kotlin
override fun onHandwritingClicked() {
    enterHandwritingMode()
}
```

在 `editor_content` 的 OnClickListener 之后挂高度同步：
```kotlin
val editorContentView = editorContent
editorContentView.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
    val h = bottom - top
    val lp = handwritingOverlay.layoutParams
    if (lp.height != h) {
        lp.height = h
        handwritingOverlay.layoutParams = lp
    }
}
```

挂手写工具栏 listener（追加在文本 toolbar.listener 设置之后）：
```kotlin
handwritingToolbar.listener = object : com.fan.hwnote.app.view.toolbar.HandwritingToolbarView.Listener {
    override fun onDoneClicked() = exitHandwritingMode()
    override fun onBrushClicked(brush: com.fan.hwnote.app.model.entity.BrushType) {
        handwritingOverlay.isErasing = false
        handwritingOverlay.currentBrush = brush
        handwritingToolbar.highlightBrush(brush)
    }
    override fun onColorClicked() = showHandwritingColorDialog()
    override fun onWidthClicked() = cycleHandwritingWidth()
    override fun onEraserClicked() {
        handwritingOverlay.isErasing = !handwritingOverlay.isErasing
        if (handwritingOverlay.isErasing) {
            handwritingToolbar.highlightEraser(true)
        } else {
            handwritingToolbar.highlightBrush(handwritingOverlay.currentBrush)
        }
    }
    override fun onUndoClicked() {
        handwritingOverlay.undo()
        refreshUndoRedoEnabled()
    }
    override fun onRedoClicked() {
        handwritingOverlay.redo()
        refreshUndoRedoEnabled()
    }
    override fun onClearClicked() {
        androidx.appcompat.app.AlertDialog.Builder(this@NoteEditorActivity)
            .setTitle(R.string.tb_clear_cd)
            .setMessage(R.string.dialog_delete_message)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                handwritingOverlay.clear()
                refreshUndoRedoEnabled()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
```

- [ ] **Step 2: 加四个新方法到 Activity 类内**

```kotlin
private fun enterHandwritingMode() {
    handwritingOverlay.isHandwritingMode = true
    handwritingOverlay.visibility = android.view.View.VISIBLE
    blocksContainer.alpha = 0.5f
    titleInput.alpha = 0.5f
    textToolbar.visibility = android.view.View.GONE
    handwritingToolbar.visibility = android.view.View.VISIBLE
    handwritingToolbar.highlightBrush(handwritingOverlay.currentBrush)
    handwritingToolbar.setColor(handwritingOverlay.currentColor)
    refreshUndoRedoEnabled()
    // 收起软键盘
    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
    imm.hideSoftInputFromWindow(blocksContainer.windowToken, 0)
}

private fun exitHandwritingMode() {
    handwritingOverlay.isHandwritingMode = false
    handwritingOverlay.visibility = android.view.View.INVISIBLE
    blocksContainer.alpha = 1f
    titleInput.alpha = 1f
    textToolbar.visibility = android.view.View.VISIBLE
    handwritingToolbar.visibility = android.view.View.GONE
}

private fun showHandwritingColorDialog() {
    val labels = arrayOf("黑", "红", "橙", "黄", "绿", "青", "蓝", "紫")
    val hexes = arrayOf(
        "#212121", "#E53935", "#FB8C00", "#FDD835",
        "#43A047", "#00897B", "#1E88E5", "#8E24AA",
    )
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(R.string.tb_hw_color_cd)
        .setItems(labels) { _, which ->
            handwritingOverlay.currentColor = hexes[which]
            handwritingToolbar.setColor(hexes[which])
        }
        .show()
}

private fun cycleHandwritingWidth() {
    val next = when (handwritingOverlay.currentWidth) {
        1 -> 3
        3 -> 6
        else -> 1
    }
    handwritingOverlay.currentWidth = next
    android.widget.Toast.makeText(this,
        "粗细：${if (next == 1) "细" else if (next == 3) "中" else "粗"}",
        android.widget.Toast.LENGTH_SHORT).show()
}

private fun refreshUndoRedoEnabled() {
    handwritingToolbar.setUndoEnabled(handwritingOverlay.canUndo())
    handwritingToolbar.setRedoEnabled(handwritingOverlay.canRedo())
}
```

`onCreate` 在 `editorContent.setOnClickListener { ... }` 之后改为只在文本模式下响应（防手写模式下点空白被路由到 Presenter）：
```kotlin
editorContent.setOnClickListener {
    if (!handwritingOverlay.isHandwritingMode) presenter.focusLastTextBlock()
}
```

返回键处理：保持 onPause 落库（已有）。**不**自动退出手写模式——若用户在手写模式按返回键直接退出 Activity，onPause 会触发 `presenter.collectCurrentNote()`，从 Overlay 拿到当前 strokes 落库，行为正确。

- [ ] **Step 3: 验证 `assembleDebug` + 跑测**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug :app:test
```
Expected: BUILD SUCCESSFUL，**65 项 PASSED**（M2 42 + M4 SpanConverter 15 + M5 ImageCompressor 3 + M6 StrokeEraser 5）。

- [ ] **Step 4: 一次提交 Task 7+8+9 三处改动**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt \
        app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt \
        app/src/main/res/layout/activity_note_editor.xml \
        app/src/main/res/layout/toolbar_handwriting.xml \
        app/src/main/java/com/fan/hwnote/app/view/toolbar/HandwritingToolbarView.kt
git commit -m "feat(m6): 接通手写工具栏 + Overlay 模式切换 + 高度同步"
```

---

### Task 10: 手写模式下软键盘 / 滚动行为静态走查（无代码改动 = 0 commit）

**Files:** （仅审视，不动文件）
- `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`
- `app/src/main/res/layout/activity_note_editor.xml`

**职责：** PRD §10 "手写 Overlay 滚动：用户在手写模式下，单指拖动是画线；不可滚动。退出手写后才能滚动。" 走查 4 条：

- [ ] **Check 1: 手写模式下 Overlay 拦截全部 touch**
确认 `HandwritingOverlayView.onTouchEvent` 在 `isHandwritingMode=true` 时所有 action 返回 `true`（已在 Task 5 实现）。这会让父 NestedScrollView 拿不到 down 事件，自然不能滚 — 符合 PRD。

- [ ] **Check 2: 手写模式下软键盘已收起**
确认 `enterHandwritingMode()` 调用了 `imm.hideSoftInputFromWindow(blocksContainer.windowToken, 0)`（已在 Task 9 实现）。

- [ ] **Check 3: 退出手写模式后 NestedScrollView 恢复滚动**
退出手写时 `Overlay.isHandwritingMode = false` → onTouchEvent 返回 false → 事件继续下传 → NestedScrollView 正常滚动。Overlay `visibility=invisible` 让 Overlay 不参与 hit-test（注：`invisible` 仍参与 onTouch dispatching；这里靠 onTouchEvent 返回 false 拒收事件，等价于让父接管）。

- [ ] **Check 4: 文本模式下点空白进 `focusLastTextBlock`**
确认 onClickListener 用了 `if (!handwritingOverlay.isHandwritingMode)` 守卫（已在 Task 9 实现）。手写模式下点空白被 Overlay 拦截，不会触发 click。

走查无 issues 时本任务零 commit；如果 Check 失败，修后回到对应 Task 重 commit。

---

### Task 11: Overlay onDraw 性能优化（clipRect 仅画可见区域）

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt`

**职责：** PRD §11 风险表 "HandwritingOverlayView 高度大时 invalidate 重绘卡顿" 的 mitigation。在 `onDraw` 起始处加一段：跳过 bbox 与 `canvas.clipBounds` 不相交的 stroke。这是在 Task 5 的实现外加的便宜优化，绝大多数情况下生效（笔画在屏外时不调 `drawPath`）。

- [ ] **Step 1: 改 `onDraw` 与 `drawStroke`**

替换 Task 5 写入的 `onDraw` 为：

```kotlin
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val clip = canvas.clipBounds
    for (s in strokesRef()) {
        if (!strokeIntersectsClip(s, clip)) continue
        drawStroke(canvas, s)
    }
    if (!isErasing && inProgressPoints.size >= 1) {
        val pts = inProgressPoints.map {
            com.fan.hwnote.app.model.entity.StrokePoint(it.first, it.second, it.third)
        }
        val tmp = Stroke(currentBrush, currentColor, currentWidth, pts)
        drawStroke(canvas, tmp)
    }
}

private fun strokeIntersectsClip(s: Stroke, clip: android.graphics.Rect): Boolean {
    if (s.points.isEmpty()) return false
    var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
    for (p in s.points) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    // 给 width 留点余量
    val pad = (s.width / 2 + 4)
    return !(maxX + pad < clip.left || minX - pad > clip.right
          || maxY + pad < clip.top  || minY - pad > clip.bottom)
}
```

- [ ] **Step 2: 验证 `assembleDebug` + 跑测**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug :app:test
```
Expected: BUILD SUCCESSFUL，65 项 PASSED（无新增测试，跑一遍确认 Task 4-5 改动不破回归）。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/view/handwriting/HandwritingOverlayView.kt
git commit -m "perf(m6): Overlay onDraw 按 clipRect 裁剪笔画绘制"
```

---

### Task 12: 全量 clean 构建 + 跑测 + STATUS 收尾

**Files:**
- Modify: `docs/superpowers/STATUS.md`

**职责：** M6 全部代码任务结束。跑 clean 构建确认无残留 cache 影响；STATUS 追加"M6 完成详情"小节。

- [ ] **Step 1: clean 构建 + 跑测**

```
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:clean :app:assembleDebug :app:test
```
Expected: BUILD SUCCESSFUL，65 项 PASSED（debug 65 + release 65），0 skipped / 0 failures / 0 errors。

- [ ] **Step 2: 在 `STATUS.md` 中"## 编辑器 UX 打磨（2026-05-23）"小节之后、"## 下一步建议"小节之前插入：**

```markdown
## M6 完成详情（2026-05-23）

**产出：**
- 实体复用（Task 0）：M2 已完成的 `Stroke` / `BrushType` / `StrokePoint` / `NoteContent.handwriting` / `NoteJson` round-trip，本里程碑零改动直接套用。
- 资源（Task 1）：8 色调色板（hw_color_black/red/orange/yellow/green/teal/blue/purple）+ 9 vector drawables（ic_pen/brush/marker/pencil/eraser/undo/redo/clear_all/check）+ 11 个手写工具栏 strings 词条；删除 toast_handwriting_placeholder。
- 笔效（Task 2）：`view/handwriting/BrushPainter.kt` — 4 笔种 Paint 配置（pen / brush + BlurMaskFilter / marker alpha=140 + 宽×1.6 / pencil + 16×16 代码生成噪点 BitmapShader）。无 PNG 资源依赖。
- 橡皮（Task 3，TDD）：`view/handwriting/StrokeEraser.kt` — 笔画级 hit-test：bbox 粗筛 + 圆心到线段细判 + width/2 容差；`StrokeEraserTest` 5 项 Robolectric-free JVM 单测覆盖空集 / 单 stroke 命中 / bbox 内但远离段落 / 半径加 width/2 阈值 / 单点 stroke 退化。
- Overlay（Task 4-5）：`view/handwriting/HandwritingOverlayView.kt` — 透明 View，状态自洽（strokes / undoStack / redoStack / inProgressPoints / isErasing / currentBrush/Color/Width / isHandwritingMode）；onTouchEvent 区分画笔与橡皮路径；onDraw 把 stroke 转 Path 用 BrushPainter 重绘。
- 布局接入（Task 6）：`activity_note_editor.xml` 在 NestedScrollView 与 editor_content 之间插一层 FrameLayout，Overlay 与内容层同尺寸覆盖；padding 从 NestedScrollView 下沉到 editor_content。
- 工具栏（Task 8）：`toolbar_handwriting.xml`（11 控件 merge）+ `view/toolbar/HandwritingToolbarView.kt`（4 笔种互斥 selected + 橡皮独立 selected + 颜色 tint + 撤销重做 enabled/alpha 反馈）。
- 控制层（Task 7+9）：`EditorPresenter` 增构造参数 overlay；`bind()` 把 strokes 给 Overlay；`collectCurrentNote()` 从 Overlay 收 strokes 写回 `NoteContent.handwriting`（替换 emptyList）。`NoteEditorActivity` 字段加 handwritingOverlay/handwritingToolbar/textToolbar；onCreate 接 OnLayoutChangeListener 同步 Overlay 高度；`onHandwritingClicked` 改为 `enterHandwritingMode()`（替换 Toast）；新增 enter/exit/showHandwritingColorDialog/cycleHandwritingWidth/refreshUndoRedoEnabled。8 色 AlertDialog；3 档粗细循环 Toast。`editor_content.setOnClickListener` 加 `!isHandwritingMode` 守卫。
- 性能（Task 11）：onDraw 按 `canvas.clipBounds` 跳过 bbox 不相交的 stroke。

**测试统计：** `./gradlew :app:test` 共 **65 项 PASSED**（M2 42 + M4 15 + M5 3 + M6 5）。Clean build 通过。

**M6 commit 列表：**
- feat(m6): 手写工具栏资源准备（8 色 + 9 图标 + 词条）
- feat(m6): BrushPainter 4 笔种 Paint 工厂 + 代码生成噪点
- feat(m6): StrokeEraser 笔画级橡皮 hit-test + 5 项单测
- feat(m6): HandwritingOverlayView 骨架（state + 撤销栈 + setStrokes/getStrokes）
- feat(m6): Overlay 落笔/橡皮 onTouch + Path 回放 onDraw
- feat(m6): 编辑器布局插 FrameLayout 容纳内容层 + 手写 Overlay
- feat(m6): 接通手写工具栏 + Overlay 模式切换 + 高度同步（合并 Task 7+8+9）
- perf(m6): Overlay onDraw 按 clipRect 裁剪笔画绘制
- 收尾：docs(m6): 标记 M6 手写 Overlay 完成

**验收：**
- ✅ `./gradlew :app:clean :app:assembleDebug :app:test` 通过（65 项 PASSED，debug + release 两轮）
- ✅ Task 10 静态走查 4 条对齐 PRD §7.4/§10
- ⏳ 真机手测留待用户走查（PRD §M6 高层验收：进入手写 / 4 笔种切换 / 颜色切换 / 粗细循环 / 橡皮笔画级擦除 / 撤销 / 重做 / 清空 + 二次确认 / 完成退出 / 手写期间 content alpha=0.5 / 手写期间不可滚动 / 退出后笔画继续显示 / 杀进程重启笔画保留）

**PRD §5.1 微调记录：** PRD 写"右下 FAB 切换文本/手写模式"，本里程碑沿 polish 阶段已落地的 4 键工具栏布局，**复用第 4 键"手写"做模式切换**（不引入新 FAB）；手写工具栏内左侧第一个按钮为"完成 ✓"用于退出。其余 PRD §7.3-§7.6 / §6.4 全部严格执行。

**执行模式：** Subagent-Driven Development（writing-plans → 用户审阅 → 严格串行 13 任务）。每任务双轨 review；Task 7+8+9 因 EditorPresenter 构造签名改变跨三处必须合并 commit；Task 10 仅静态走查零 commit；Task 12 仅 STATUS 收尾。
```

并把文件顶部的"最后更新"行改为 `最后更新：2026-05-23（M6 手写 Overlay 完成）`。

把"## 里程碑进度"表里 M6 那一行的"⏳ 未开始"改为"✅ 完成（2026-05-23）"，备注栏写"HandwritingOverlayView + 4 笔种 + 8 色 + 3 粗细 + 橡皮 + 撤销/重做 + 清空；StrokeEraser 5 单测"。

并把"## 下一步建议"小节内容改为：

```markdown
## 下一步建议

M6 已完成，建议进入 **M7 打磨**：
- 修 M5 评审遗留两条 Important：相机 process death 持久化 + ensureNoteSavedAndThen 与 onPause 竞态
- 大笔记笔画分批渲染（>200 笔时考虑 v2 拆 handwriting.json）
- 真机回归全量 PRD 用例并按用户反馈调优
```

- [ ] **Step 3: 提交**

```bash
git add docs/superpowers/STATUS.md
git commit -m "docs(m6): 标记 M6 手写 Overlay 完成"
```

---

### Task 13: PRD 验收对齐复核（仅静态读 = 0 commit）

**Files:** （仅审视）
- `docs/superpowers/specs/2026-05-22-hwnote-design.md` 第 9 节"高层验收"（如有）/ 第 14 节"测试策略"

**职责：** 用 PRD 的高层验收语句对每条产出做一次自审，列在 STATUS 验收 ⏳ 行后供后续真机走查用。本任务是**自审清单**，无代码 / 无 commit；通过即结束 M6。

- [ ] **Check 1：进入手写模式 → editor_content.alpha = 0.5 + Overlay 可见 + 手写工具栏可见 + 文本工具栏 gone + 软键盘收起。**（Task 9 `enterHandwritingMode` 实现）

- [ ] **Check 2：4 笔种点击切换 → Overlay.currentBrush 改变，工具栏对应按钮 selected 高亮、其它取消，橡皮自动取消。**（Task 9 `onBrushClicked` + Task 8 `highlightBrush`）

- [ ] **Check 3：颜色按钮 → 弹 8 色 AlertDialog，选择后 Overlay.currentColor 改变，按钮 imageTintList 跟随。**（Task 9 `showHandwritingColorDialog`）

- [ ] **Check 4：粗细按钮 → 1→3→6→1 循环，Toast 反馈"细/中/粗"。**（Task 9 `cycleHandwritingWidth`）

- [ ] **Check 5：橡皮按钮 → Overlay.isErasing toggle，笔种按钮全部取消 selected。**（Task 9 `onEraserClicked` + Task 8 `highlightEraser`）

- [ ] **Check 6：撤销 / 重做 → Overlay.undo()/redo()，工具栏 enabled/alpha 跟着 canUndo/canRedo 刷新。**（Task 9 `refreshUndoRedoEnabled` + Task 4 `canUndo/canRedo`）

- [ ] **Check 7：清空 → 弹 AlertDialog 二次确认，确认后 Overlay.clear()（整体 push 一个 Erase action 可被撤销）。**（Task 9 `onClearClicked` + Task 4 `clear`）

- [ ] **Check 8：完成 ✓ → exitHandwritingMode → editor_content.alpha 复位 1.0、Overlay invisible、文本工具栏 visible、内容层重新可滚动、可点击聚焦。**（Task 9 `exitHandwritingMode`）

- [ ] **Check 9：手写期间不可滚动**（Task 10 Check 1）。

- [ ] **Check 10：退出 / 杀进程 / 重进 → 笔画从 `NoteContent.handwriting` 恢复显示。**（Task 7 `bind` 调 `setStrokes` + M2 NoteJson round-trip 已过单测）

- [ ] **Check 11：橡皮跨多个 strokes 一次按下 → 抬起聚合一个 Erase action，按一次撤销恢复全部。**（Task 5 `eraseAt` 摘掉 + ACTION_UP 时 `pushErase(erasedThisGesture.toList())`）

- [ ] **Check 12：单点 stroke 也能被橡皮命中。**（Task 3 单测 `single_point_stroke_treated_as_zero_length_segment` 覆盖）

- [ ] **Check 13：onDraw 大量笔画时按 clipBounds 裁剪。**（Task 11 `strokeIntersectsClip`）

任意 Check 失败 → 回退到对应 Task 修复 + 重 commit + 重 review。13 条全过 → M6 关闭，进入 M7。

---

## 不动的东西（明确边界）

- **数据层** — Stroke / BrushType / StrokePoint / NoteContent / NoteJson / NoteRepository / NoteDbHelper / NoteFileStorage 全部零改动。
- **现有 Block 渲染** — TextBlockView / ImageBlockView / ChecklistBlockView / ChecklistItemView 不动。
- **文本工具栏** — TextToolbarView / StylePickerBottomSheet 不动（只在 Activity 里改 visibility 切换）。
- **EditorPresenter 文本处理** — toggleInline / applyInlineToRange / applyPendingTo / toggleSize / pickColor / toggleHeading / focusLastTextBlock / onRequestSplitAfter / onRequestDelete / 三个 add*BlockView 一行不改。
- **测试基础设施** — 不引新依赖，沿用 JUnit 5 + JUnit 4 Vintage + Robolectric 4.13。M6 新增的 5 项单测是纯 JVM（无 Robolectric），无需 robolectric.properties 改动。
- **Manifest / build.gradle / 版本** — 一行不改。

---

## 真机手测验收清单（用户走查时用）

1. 文本模式 → 点工具栏"手写" → 进入手写模式：内容层变淡、工具栏切换、键盘收起。
2. 钢笔 / 画笔 / 马克笔 / 铅笔 各画一笔，视觉上 4 种笔效有差异（pen 等粗硬边 / brush 边缘虚化 / marker 半透明叠加变深 / pencil 颗粒纹理）。
3. 选 8 色之一 → 画一笔颜色对应。
4. 粗细按钮点 3 次 → 1→3→6 dp 循环，画出来粗细可见。
5. 橡皮 → 用力擦过若干笔 → 那些笔整体消失。撤销一次 → 全部恢复。
6. 撤销 / 重做能反复来回。
7. 清空 → 弹"删除笔记"复用文案确认 → 全部消失；再撤销 → 全部恢复。
8. 完成 ✓ → 回到文本模式，内容层 alpha 恢复，可滚动，可点空白聚焦最后 TextBlock。
9. 写完笔记按返回键 → 重新打开列表 → 进入该笔记 → 笔画完整显示。
10. 长内容（超一屏）滚动 + 在不同位置画笔 → 滚动时笔画跟着内容滚（坐标系正确）。
11. 长按 Home 杀掉 app → 重启 → 笔画仍在。

---

## 自审小结（控制器在写完后跑）

**1. 规格覆盖：**
- PRD §6.4 Stroke 字段 → Task 4 `pushAdd` 用 `Stroke(currentBrush, currentColor, currentWidth, pts)` 构造 ✓
- PRD §7.3 View 层级 → Task 6 NestedScrollView > FrameLayout > [LinearLayout 内容层 + Overlay] ✓
- PRD §7.4 模式切换四列 → Task 9 enterHandwritingMode/exitHandwritingMode ✓
- PRD §7.5 4 笔种 Paint 配置 → Task 2 BrushPainter ✓
- PRD §7.5 橡皮 hit-test → Task 3 StrokeEraser ✓
- PRD §7.5 撤销 / 重做 / 清空 → Task 4 undoStack/redoStack/clear ✓
- PRD §7.6 onPause 写库 → 已有 NoteEditorActivity.onPause + Task 7 collectCurrentNote 拿 strokes ✓
- PRD §10 手写期间不可滚动 → Task 5 onTouchEvent 全部消费 + Task 10 Check 1 ✓
- PRD §11 风险 onDraw 大数据卡顿 → Task 11 clipRect ✓
- PRD §14 测试策略：StrokeEraser 单测 → Task 3 ✓
- PRD §5.1 FAB 模式切换 → 微调为工具栏第 4 键，已在"PRD 对齐声明"显式记录 ✓

**2. 占位扫描：** 0 处。每个 step 都有完整代码或精确命令。

**3. 类型一致性：**
- `HandwritingOverlayView.setStrokes(List<Stroke>)` / `getStrokes(): List<Stroke>` 在 Task 4 / Task 7 一致 ✓
- `currentBrush: BrushType` / `currentColor: String` / `currentWidth: Int` 在 Task 4 / Task 9 一致 ✓
- `BrushType.PEN/BRUSH/MARKER/PENCIL` 与 Stroke.kt 现有定义一致 ✓
- `StrokeEraser.hitTest(ex, ey, radiusPx, strokes)` 在 Task 3 测试与 Task 5 调用方一致 ✓
- `EditorPresenter` 构造从 2 参 → 3 参，Task 7 改 + Task 9 调，**全部用合并 commit** 避免编译中断 ✓
- `HandwritingToolbarView.Listener` 8 方法（done/brush/color/width/eraser/undo/redo/clear），Task 8 定义 + Task 9 实现一致 ✓

---

## 执行交接

Plan complete and saved to `docs/superpowers/plans/2026-05-23-hwnote-m6-handwriting.md`.

按照已建立的项目惯例（M2/M3/M4/M5/polish 都用同模式），**建议 Subagent-Driven Development**：每任务 implementer DONE → spec compliance review → code quality review → fix → re-review → 标记完成；Task 7+8+9 合并提交，Task 10 / Task 13 静态走查零 commit，Task 12 仅 STATUS。

请先审阅本计划。审阅通过后告诉我"开始执行"，我用 superpowers:subagent-driven-development 开干。
