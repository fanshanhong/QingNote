# HwNote · M4 编辑器骨架详细实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 HwNote 编辑器骨架（NoteEditorActivity）：进编辑器、输标题、输文本、应用富文本（B/I/U/S + 字号 A-/A/A+ + 颜色 5 色 + 块级 H1/H2）、末块回车 split、空块退格 delete、`onPause` 落库、再进数据完整恢复。**M4 仅文本块；图片、清单、手写在 M5/M6/M7。**

**Architecture:** XML 布局 + 自定义 `BlockView`（abstract `LinearLayout`）+ `TextBlockView`（含 `EditText`，Spannable 实时编辑）+ `EditorPresenter`（持有 `currentBlocks` 子 view 列表 + `pendingStyles` 待生效样式状态）+ `TextToolbarView`（横向 ScrollView 按钮排）+ `SpanConverter`（`Spannable` ↔ `List<TextSpan>` 互转）。`onCreate` 用 `lifecycleScope.launch` 拉 `NoteRepository.get(noteId) ?: Note.new()`，`onPause` 调 `presenter.collectCurrentNote()` → `NoteRepository.save(note)`。**不引 Compose / ViewModel / LiveData / Room / Hilt / Navigation。**

**Tech Stack:** Kotlin · AppCompat · Material Components · `android.text.Spannable` + `StyleSpan` / `UnderlineSpan` / `StrikethroughSpan` / `RelativeSizeSpan` / `ForegroundColorSpan` · `EditText` `TextWatcher` · 现有 `NoteRepository` / `Block.TextBlock` / `Heading` / `TextSpan` / `SpanType`。

**测试策略：** 高层规划"非典型 TDD：UI 层 M3-M7 写完即手测；纯函数才有自动化测试"。M4 只有 **`SpanConverter`** 是纯函数（带少量 Android Span 类型），用 **JUnit 4 + Robolectric**（沿用 M2 已就位的依赖与 `app/src/test/resources/robolectric.properties` sdk=34）。其他 11 个任务都是写完即装包手测。

**版本约束（不可改）：** AGP 8.11.2 / Kotlin 2.0.21 / Gradle 8.14.3 / compileSdk 36 / targetSdk 36 / minSdk 24 / JDK 11。本里程碑**不新增任何依赖**。

**两个 Toast 占位会被替换：**
- `NoteListActivity.kt:54-62` 卡片点击的 `Toast.makeText(...toast_open_editor_placeholder...)` → `startActivity(NoteEditorActivity.newIntent(this, note.id))`
- `NoteListActivity.kt:68-86` FAB 临时落空笔记的 16 行块 → `startActivity(NoteEditorActivity.newIntent(this, -1L))`

---

## 文件结构

**新建：**
- `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`
- `app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`
- `app/src/main/java/com/fan/hwnote/app/view/block/BlockView.kt`
- `app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt`
- `app/src/main/java/com/fan/hwnote/app/view/toolbar/TextToolbarView.kt`
- `app/src/main/java/com/fan/hwnote/app/util/SpanConverter.kt`
- `app/src/test/java/com/fan/hwnote/app/util/SpanConverterTest.kt`
- `app/src/main/res/layout/activity_note_editor.xml`
- `app/src/main/res/layout/block_text.xml`
- `app/src/main/res/layout/toolbar_text.xml`
- `app/src/main/res/drawable/ic_arrow_back.xml`
- `app/src/main/res/drawable/ic_more_vert.xml`
- `app/src/main/res/drawable/ic_image.xml`
- `app/src/main/res/drawable/ic_checklist.xml`
- `app/src/main/res/drawable/ic_color_dot.xml`

**修改：**
- `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`（替换 2 处 Toast 占位）
- `app/src/main/AndroidManifest.xml`（注册 `NoteEditorActivity`）
- `app/src/main/res/values/colors.xml`（新增 `text_color_red / yellow / green / blue`）
- `app/src/main/res/values/strings.xml`（新增编辑器/工具栏词条）

**不动：** `App.kt` · `themes.xml` · `dimens.xml`（`editor_text_h1/h2/normal` 已就位）· `gradle/libs.versions.toml` · `app/build.gradle.kts` · 所有 `model/*` · M2 / M3 测试代码。

---

## 通用执行约定

每个任务都按 4 步走：

1. **实现**：按本任务列出的 Create / Modify 改动文件。
2. **构建**：`./gradlew :app:assembleDebug`，预期 BUILD SUCCESSFUL；如失败先解决再继续。
3. **手测 / 单测**：装包到真机/模拟器走任务给出的"手测验收"；Task 4 是 `./gradlew :app:testDebugUnitTest`。
4. **提交**：按本任务给出的 commit message 生成提交（中文，动宾，不带 Co-Authored-By；与 M3 同款）。

**Gradle 运行环境（每个新 shell 都要 export）：**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote
```

**装包必做的任务**：Task 2 / 3 / 5 / 6 / 7 / 8 / 9 / 10 / 11 / 12（有视觉或交互改动）。Task 1 / 4 仅构建/单测，不必装包。

---

## Task 1: 资源准备 — drawables + colors + strings + AndroidManifest

**Files:**
- Create: `app/src/main/res/drawable/ic_arrow_back.xml`
- Create: `app/src/main/res/drawable/ic_more_vert.xml`
- Create: `app/src/main/res/drawable/ic_image.xml`
- Create: `app/src/main/res/drawable/ic_checklist.xml`
- Create: `app/src/main/res/drawable/ic_color_dot.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建 5 个 vector drawable**

`ic_arrow_back.xml`：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/white">
    <path android:fillColor="@android:color/white"
        android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"/>
</vector>
```

`ic_more_vert.xml`：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/white">
    <path android:fillColor="@android:color/white"
        android:pathData="M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"/>
</vector>
```

`ic_image.xml`：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="@android:color/white"
        android:pathData="M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z"/>
</vector>
```

`ic_checklist.xml`：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_secondary">
    <path android:fillColor="@android:color/white"
        android:pathData="M22,7l-1.41,-1.42L11,15.17l-3.59,-3.59L6,13l5,5zM2,5h7v2H2zM2,11h7v2H2zM2,17h7v2H2z"/>
</vector>
```

`ic_color_dot.xml`：（圆点，颜色由 tint 在按钮上指定）

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="20dp" android:height="20dp"
    android:viewportWidth="20" android:viewportHeight="20">
    <path android:fillColor="@android:color/white"
        android:pathData="M10,2 a8,8 0,1,0 0,16 a8,8 0,1,0 0,-16 z"/>
</vector>
```

- [ ] **Step 2: 在 `colors.xml` 末尾、`</resources>` 前追加色板**

```xml
    <!-- 编辑器文字色（M4 用） -->
    <color name="text_color_black">#212121</color>
    <color name="text_color_red">#E53935</color>
    <color name="text_color_yellow">#FB8C00</color>
    <color name="text_color_green">#43A047</color>
    <color name="text_color_blue">#1E88E5</color>

    <!-- 编辑器工具栏 -->
    <color name="toolbar_bg">#F5F5F5</color>
    <color name="toolbar_btn_selected">#B2DFDB</color>
```

- [ ] **Step 3: 在 `strings.xml` 末尾、`</resources>` 前追加词条**

```xml
    <!-- 编辑器 -->
    <string name="editor_title_hint">标题</string>
    <string name="editor_content_hint">开始记录…</string>
    <string name="editor_back_cd">返回</string>
    <string name="editor_more_cd">更多</string>

    <!-- 工具栏按钮 contentDescription 与文案 -->
    <string name="tb_bold">B</string>
    <string name="tb_italic">I</string>
    <string name="tb_underline">U</string>
    <string name="tb_strike">S</string>
    <string name="tb_size_small">A-</string>
    <string name="tb_size_normal">A</string>
    <string name="tb_size_large">A+</string>
    <string name="tb_color_cd">文字颜色</string>
    <string name="tb_h1">H1</string>
    <string name="tb_h2">H2</string>
    <string name="tb_image_cd">插入图片</string>
    <string name="tb_checklist_cd">插入清单</string>

    <!-- 颜色选择对话框 -->
    <string name="dialog_pick_color_title">文字颜色</string>
    <string name="color_black">黑</string>
    <string name="color_red">红</string>
    <string name="color_yellow">黄</string>
    <string name="color_green">绿</string>
    <string name="color_blue">蓝</string>

    <!-- M5/M6 占位 -->
    <string name="toast_image_placeholder">插入图片（M5 实现）</string>
    <string name="toast_checklist_placeholder">插入清单（M6 实现）</string>
```

- [ ] **Step 4: 在 `AndroidManifest.xml` 的 `<application>` 节点内、`<provider>` 之前追加编辑器 Activity 注册**

```xml
        <activity
            android:name=".controller.editor.NoteEditorActivity"
            android:exported="false"
            android:parentActivityName=".controller.list.NoteListActivity"
            android:theme="@style/Theme.HuaWeiNote"
            android:windowSoftInputMode="adjustResize">
            <meta-data
                android:name="android.support.PARENT_ACTIVITY"
                android:value=".controller.list.NoteListActivity" />
        </activity>
```

- [ ] **Step 5: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 BUILD SUCCESSFUL（仍未引用新资源，纯加资源不会报错）。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/res/drawable/ic_arrow_back.xml \
        app/src/main/res/drawable/ic_more_vert.xml \
        app/src/main/res/drawable/ic_image.xml \
        app/src/main/res/drawable/ic_checklist.xml \
        app/src/main/res/drawable/ic_color_dot.xml \
        app/src/main/res/values/colors.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/AndroidManifest.xml
git commit -m "feat(m4): 编辑器资源准备（drawables + 颜色 + 词条 + Manifest）"
```

---

## Task 2: `activity_note_editor.xml` — 编辑器主布局

**Files:**
- Create: `app/src/main/res/layout/activity_note_editor.xml`

- [ ] **Step 1: 写入主布局**

布局：垂直 `LinearLayout` → AppBarLayout(Toolbar 带返回 + ⋯) → ScrollView 含一个垂直 `LinearLayout`（`title_input` + `blocks_container`）→ 底部 `TextToolbarView` 占位（先用普通 `View` 高度 48dp 占位，Task 8 替换）。

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_window"
    android:orientation="vertical">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/primary"
        android:theme="@style/ThemeOverlay.MaterialComponents.Dark.ActionBar">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/editor_toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="@color/primary"
            app:navigationIcon="@drawable/ic_arrow_back"
            app:navigationContentDescription="@string/editor_back_cd"
            app:titleTextColor="@color/white" />
    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:id="@+id/editor_scroll"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:fillViewport="true"
        android:paddingHorizontal="@dimen/spacing_l"
        android:paddingTop="@dimen/spacing_m"
        android:paddingBottom="@dimen/spacing_l">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

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

    </androidx.core.widget.NestedScrollView>

    <com.fan.hwnote.app.view.toolbar.TextToolbarView
        android:id="@+id/text_toolbar"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="@color/toolbar_bg" />

</LinearLayout>
```

> **NOTE：** Task 8 之前 `TextToolbarView` 还不存在，本任务先把布局塞好；Task 5 / 6 完成 Activity 后会先把这一行 `<com.fan.hwnote...>` **临时改为普通 `<View>`**（构建期 unresolved class 会失败）。**因此本 Task 在 Step 2 中先改成普通 `<View>` 占位，等 Task 8 再改回。**

- [ ] **Step 2: 把 Step 1 中底部那行改为占位 `<View>`（Task 8 会改回 `TextToolbarView`）**

把：

```xml
    <com.fan.hwnote.app.view.toolbar.TextToolbarView
        android:id="@+id/text_toolbar"
        ... />
```

改为：

```xml
    <View
        android:id="@+id/text_toolbar"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="@color/toolbar_bg" />
```

- [ ] **Step 3: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 BUILD SUCCESSFUL。**还不能装包**（Activity 还没写）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/activity_note_editor.xml
git commit -m "feat(m4): 编辑器主布局（Toolbar + ScrollView + 标题/块容器 + 底栏占位）"
```

---

## Task 3: `block_text.xml` + `toolbar_text.xml` — 单块与工具栏布局

**Files:**
- Create: `app/src/main/res/layout/block_text.xml`
- Create: `app/src/main/res/layout/toolbar_text.xml`

- [ ] **Step 1: 写 `block_text.xml`（单个 `TextBlockView` 内含的 EditText）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:android="http://schemas.android.com/apk/res/android">

    <EditText
        android:id="@+id/block_edit"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@null"
        android:gravity="top|start"
        android:hint="@string/editor_content_hint"
        android:inputType="textMultiLine|textCapSentences"
        android:minHeight="40dp"
        android:paddingVertical="@dimen/spacing_xs"
        android:textColor="@color/text_primary"
        android:textColorHint="@color/text_hint"
        android:textSize="@dimen/editor_text_normal" />

</merge>
```

> `<merge>` 是因为 `TextBlockView` 自身就是一个 `LinearLayout`，子布局 `inflate` 时直接合并到父布局，避免多一层冗余 ViewGroup。

- [ ] **Step 2: 写 `toolbar_text.xml`（横向滑动按钮排）**

12 个按钮，**全部 `android:layout_width="48dp"`**，统一 `48dp x 48dp` 触摸区。Color 按钮叠 `ic_color_dot` + tint，先默认黑。

```xml
<?xml version="1.0" encoding="utf-8"?>
<HorizontalScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    android:scrollbars="none">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingHorizontal="@dimen/spacing_xs">

        <TextView android:id="@+id/btn_bold" style="@style/EditorToolbarBtn"
            android:text="@string/tb_bold" android:textStyle="bold" />

        <TextView android:id="@+id/btn_italic" style="@style/EditorToolbarBtn"
            android:text="@string/tb_italic" android:textStyle="italic" />

        <TextView android:id="@+id/btn_underline" style="@style/EditorToolbarBtn"
            android:text="@string/tb_underline" />

        <TextView android:id="@+id/btn_strike" style="@style/EditorToolbarBtn"
            android:text="@string/tb_strike" />

        <View style="@style/EditorToolbarDivider" />

        <TextView android:id="@+id/btn_size_small" style="@style/EditorToolbarBtn"
            android:text="@string/tb_size_small" android:textSize="12sp" />

        <TextView android:id="@+id/btn_size_normal" style="@style/EditorToolbarBtn"
            android:text="@string/tb_size_normal" />

        <TextView android:id="@+id/btn_size_large" style="@style/EditorToolbarBtn"
            android:text="@string/tb_size_large" android:textSize="20sp" />

        <ImageView android:id="@+id/btn_color"
            android:layout_width="48dp" android:layout_height="match_parent"
            android:contentDescription="@string/tb_color_cd"
            android:padding="12dp"
            android:src="@drawable/ic_color_dot"
            app:tint="@color/text_color_black"
            xmlns:app="http://schemas.android.com/apk/res-auto" />

        <View style="@style/EditorToolbarDivider" />

        <TextView android:id="@+id/btn_h1" style="@style/EditorToolbarBtn"
            android:text="@string/tb_h1" />

        <TextView android:id="@+id/btn_h2" style="@style/EditorToolbarBtn"
            android:text="@string/tb_h2" />

        <View style="@style/EditorToolbarDivider" />

        <ImageView android:id="@+id/btn_image"
            android:layout_width="48dp" android:layout_height="match_parent"
            android:contentDescription="@string/tb_image_cd"
            android:padding="12dp"
            android:src="@drawable/ic_image" />

        <ImageView android:id="@+id/btn_checklist"
            android:layout_width="48dp" android:layout_height="match_parent"
            android:contentDescription="@string/tb_checklist_cd"
            android:padding="12dp"
            android:src="@drawable/ic_checklist" />

    </LinearLayout>
</HorizontalScrollView>
```

- [ ] **Step 3: 在 `themes.xml` 末尾追加 toolbar 子样式**

打开 `app/src/main/res/values/themes.xml`，在主题 `</resources>` 前追加：

```xml
    <style name="EditorToolbarBtn">
        <item name="android:layout_width">48dp</item>
        <item name="android:layout_height">match_parent</item>
        <item name="android:gravity">center</item>
        <item name="android:textColor">@color/text_primary</item>
        <item name="android:textSize">16sp</item>
        <item name="android:background">?attr/selectableItemBackgroundBorderless</item>
        <item name="android:clickable">true</item>
        <item name="android:focusable">true</item>
    </style>

    <style name="EditorToolbarDivider">
        <item name="android:layout_width">1dp</item>
        <item name="android:layout_height">24dp</item>
        <item name="android:layout_marginHorizontal">@dimen/spacing_xs</item>
        <item name="android:background">@color/divider</item>
    </style>
```

- [ ] **Step 4: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/res/layout/block_text.xml \
        app/src/main/res/layout/toolbar_text.xml \
        app/src/main/res/values/themes.xml
git commit -m "feat(m4): 文本块与编辑器工具栏布局"
```

---

## Task 4: `SpanConverter.kt` + Robolectric 单测（TDD）

**Files:**
- Create: `app/src/test/java/com/fan/hwnote/app/util/SpanConverterTest.kt`
- Create: `app/src/main/java/com/fan/hwnote/app/util/SpanConverter.kt`

> **唯一带自动化测试的任务**。先写测试 → 跑红 → 写实现 → 跑绿 → 提交。

**字号映射约定（PRD §6.2）：**
| `TextSpan.value` | `RelativeSizeSpan.sizeChange` |
|---|---|
| `"small"` | `0.85f` |
| `"medium"` | `1.0f` |
| `"large"` | `1.25f` |

**色码约定**：`TextSpan(SpanType.COLOR, value = "#RRGGBB")` ↔ `ForegroundColorSpan(Color.parseColor(value))`。`Color.toString()` 反向用 `"#%06X".format(0xFFFFFF and color)`。

- [ ] **Step 1: 先写失败测试 `SpanConverterTest.kt`**

```kotlin
package com.fan.hwnote.app.util

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpanConverterTest {

    // ----- TextSpan -> Spannable -----

    @Test fun bold_apply_to_spannable() {
        val sp = SpannableString("hello world")
        listOf(TextSpan(0, 5, SpanType.BOLD)).applyTo(sp)
        val spans = sp.getSpans(0, sp.length, StyleSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(Typeface.BOLD, spans[0].style)
        assertEquals(0, sp.getSpanStart(spans[0]))
        assertEquals(5, sp.getSpanEnd(spans[0]))
    }

    @Test fun italic_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.ITALIC)).applyTo(sp)
        val spans = sp.getSpans(0, sp.length, StyleSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(Typeface.ITALIC, spans[0].style)
    }

    @Test fun underline_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(1, 3, SpanType.UNDERLINE)).applyTo(sp)
        assertEquals(1, sp.getSpans(0, 3, UnderlineSpan::class.java).size)
    }

    @Test fun strike_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 2, SpanType.STRIKETHROUGH)).applyTo(sp)
        assertEquals(1, sp.getSpans(0, 3, StrikethroughSpan::class.java).size)
    }

    @Test fun fontsize_large_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.FONT_SIZE, "large")).applyTo(sp)
        val arr = sp.getSpans(0, 3, RelativeSizeSpan::class.java)
        assertEquals(1, arr.size)
        assertEquals(1.25f, arr[0].sizeChange, 0.001f)
    }

    @Test fun fontsize_small_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.FONT_SIZE, "small")).applyTo(sp)
        val arr = sp.getSpans(0, 3, RelativeSizeSpan::class.java)
        assertEquals(0.85f, arr[0].sizeChange, 0.001f)
    }

    @Test fun fontsize_unknown_value_skipped() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.FONT_SIZE, "huge")).applyTo(sp)
        assertEquals(0, sp.getSpans(0, 3, RelativeSizeSpan::class.java).size)
    }

    @Test fun color_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.COLOR, "#E53935")).applyTo(sp)
        val arr = sp.getSpans(0, 3, ForegroundColorSpan::class.java)
        assertEquals(1, arr.size)
        assertEquals(Color.parseColor("#E53935"), arr[0].foregroundColor)
    }

    @Test fun color_invalid_value_skipped() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.COLOR, "not-a-color")).applyTo(sp)
        assertEquals(0, sp.getSpans(0, 3, ForegroundColorSpan::class.java).size)
    }

    @Test fun multiple_spans_overlap_apply() {
        val sp = SpannableString("hello world")
        listOf(
            TextSpan(0, 5, SpanType.BOLD),
            TextSpan(3, 8, SpanType.UNDERLINE),
        ).applyTo(sp)
        assertEquals(1, sp.getSpans(0, 5, StyleSpan::class.java).size)
        assertEquals(1, sp.getSpans(3, 8, UnderlineSpan::class.java).size)
    }

    // ----- Spannable -> TextSpan -----

    @Test fun read_back_bold() {
        val sp = SpannableString("hi")
        sp.setSpan(StyleSpan(Typeface.BOLD), 0, 2, 0)
        val list = sp.toTextSpans()
        assertEquals(1, list.size)
        assertEquals(TextSpan(0, 2, SpanType.BOLD), list[0])
    }

    @Test fun read_back_color() {
        val sp = SpannableString("hi")
        sp.setSpan(ForegroundColorSpan(Color.parseColor("#1E88E5")), 0, 2, 0)
        val list = sp.toTextSpans()
        assertEquals(1, list.size)
        assertEquals(SpanType.COLOR, list[0].type)
        // 比较值时大小写无关
        assertEquals("#1E88E5".uppercase(), list[0].value!!.uppercase())
    }

    @Test fun read_back_fontsize_large() {
        val sp = SpannableString("hi")
        sp.setSpan(RelativeSizeSpan(1.25f), 0, 2, 0)
        val list = sp.toTextSpans()
        assertEquals(SpanType.FONT_SIZE, list[0].type)
        assertEquals("large", list[0].value)
    }

    @Test fun roundtrip_all_six_types() {
        val original = listOf(
            TextSpan(0, 2, SpanType.BOLD),
            TextSpan(2, 4, SpanType.ITALIC),
            TextSpan(0, 4, SpanType.UNDERLINE),
            TextSpan(4, 6, SpanType.STRIKETHROUGH),
            TextSpan(0, 6, SpanType.FONT_SIZE, "large"),
            TextSpan(0, 6, SpanType.COLOR, "#43A047"),
        )
        val sp = SpannableString("abcdef")
        original.applyTo(sp)
        val recovered = sp.toTextSpans()
        // 顺序无关，按 (start,end,type) 排序后比较
        fun List<TextSpan>.norm() = sortedWith(
            compareBy({ it.start }, { it.end }, { it.type.name })
        )
        // 颜色统一大写
        fun TextSpan.norm(): TextSpan =
            if (type == SpanType.COLOR) copy(value = value?.uppercase()) else this
        val originalN = original.map { it.norm() }.norm()
        val recoveredN = recovered.map { it.norm() }.norm()
        assertEquals(originalN, recoveredN)
    }

    @Test fun empty_input_returns_empty_list() {
        val sp = SpannableString("abc")
        assertTrue(sp.toTextSpans().isEmpty())
    }
}
```

- [ ] **Step 2: 跑测试，确认全部 fail（compile error 也算 fail）**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.util.SpanConverterTest"
```

预期：编译 fail（`applyTo` / `toTextSpans` 未定义）。

- [ ] **Step 3: 写实现 `SpanConverter.kt` 让测试通过**

```kotlin
package com.fan.hwnote.app.util

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan

/**
 * Spannable ↔ List<TextSpan> 互转。
 *
 * 字号档位（PRD §6.2）：small=0.85, medium=1.0, large=1.25。
 * 颜色：value 形如 "#RRGGBB"。读出时统一大写。
 *
 * 解析失败（未识别 value、非法颜色等）静默丢弃 — 编辑器优先保证不崩。
 */

private const val SIZE_SMALL = 0.85f
private const val SIZE_MEDIUM = 1.0f
private const val SIZE_LARGE = 1.25f

fun List<TextSpan>.applyTo(sp: Spannable) {
    val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    for (s in this) {
        if (s.start < 0 || s.end > sp.length || s.start >= s.end) continue
        when (s.type) {
            SpanType.BOLD -> sp.setSpan(StyleSpan(Typeface.BOLD), s.start, s.end, flag)
            SpanType.ITALIC -> sp.setSpan(StyleSpan(Typeface.ITALIC), s.start, s.end, flag)
            SpanType.UNDERLINE -> sp.setSpan(UnderlineSpan(), s.start, s.end, flag)
            SpanType.STRIKETHROUGH -> sp.setSpan(StrikethroughSpan(), s.start, s.end, flag)
            SpanType.FONT_SIZE -> {
                val ratio = when (s.value) {
                    "small" -> SIZE_SMALL
                    "medium" -> SIZE_MEDIUM
                    "large" -> SIZE_LARGE
                    else -> null
                }
                if (ratio != null) {
                    sp.setSpan(RelativeSizeSpan(ratio), s.start, s.end, flag)
                }
            }
            SpanType.COLOR -> {
                val c = runCatching { Color.parseColor(s.value) }.getOrNull()
                if (c != null) sp.setSpan(ForegroundColorSpan(c), s.start, s.end, flag)
            }
        }
    }
}

fun Spannable.toTextSpans(): List<TextSpan> {
    val out = mutableListOf<TextSpan>()
    for (s in getSpans(0, length, Any::class.java)) {
        val start = getSpanStart(s)
        val end = getSpanEnd(s)
        if (start < 0 || end <= start) continue
        when (s) {
            is StyleSpan -> when (s.style) {
                Typeface.BOLD -> out += TextSpan(start, end, SpanType.BOLD)
                Typeface.ITALIC -> out += TextSpan(start, end, SpanType.ITALIC)
                else -> Unit
            }
            is UnderlineSpan -> out += TextSpan(start, end, SpanType.UNDERLINE)
            is StrikethroughSpan -> out += TextSpan(start, end, SpanType.STRIKETHROUGH)
            is RelativeSizeSpan -> {
                val v = when {
                    kotlin.math.abs(s.sizeChange - SIZE_SMALL) < 0.01f -> "small"
                    kotlin.math.abs(s.sizeChange - SIZE_LARGE) < 0.01f -> "large"
                    kotlin.math.abs(s.sizeChange - SIZE_MEDIUM) < 0.01f -> "medium"
                    else -> null
                }
                if (v != null) out += TextSpan(start, end, SpanType.FONT_SIZE, v)
            }
            is ForegroundColorSpan -> {
                val hex = "#%06X".format(0xFFFFFF and s.foregroundColor)
                out += TextSpan(start, end, SpanType.COLOR, hex)
            }
        }
    }
    return out
}
```

- [ ] **Step 4: 跑测试，确认全部绿**

```bash
./gradlew :app:testDebugUnitTest --tests "com.fan.hwnote.app.util.SpanConverterTest"
```

预期：所有测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/util/SpanConverter.kt \
        app/src/test/java/com/fan/hwnote/app/util/SpanConverterTest.kt
git commit -m "feat(m4): SpanConverter（Spannable↔TextSpan 互转）+ Robolectric 单测"
```

---

## Task 5: `BlockView.kt` (abstract) + `TextBlockView.kt`

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/view/block/BlockView.kt`
- Create: `app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt`

- [ ] **Step 1: 写 `BlockView.kt`（抽象基类）**

```kotlin
package com.fan.hwnote.app.view.block

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.fan.hwnote.app.model.entity.Block

/**
 * 编辑器内每一个内容块的视图基类。
 * 子类负责把 Block 数据渲染到 UI，并能反向把 UI 当前状态收集回一个新 Block。
 *
 * 通信通过 [callback] 上抛 — Presenter 持有 BlockView 列表，注入回调。
 */
abstract class BlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var callback: Callback? = null

    init {
        orientation = VERTICAL
    }

    abstract fun bind(block: Block)
    abstract fun toBlock(): Block

    /** 上抛给 Presenter 的事件。 */
    interface Callback {
        /** 用户在末尾按回车，要求在该块后面插入新块。 */
        fun onRequestSplitAfter(view: BlockView)
        /** 用户在空块按退格，要求删除该块并把焦点上移。 */
        fun onRequestDelete(view: BlockView)
        /** 用户聚焦到这块（用于 Presenter 记录 currentFocus）。 */
        fun onFocusGained(view: BlockView)
    }
}
```

- [ ] **Step 2: 写 `TextBlockView.kt`**

```kotlin
package com.fan.hwnote.app.view.block

import android.content.Context
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.EditText
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.util.applyTo
import com.fan.hwnote.app.util.toTextSpans

/**
 * 文本块视图：单个 EditText + heading 字号 + Span 应用。
 *
 * 不要直接在外部读 [edit].text 然后判断；Presenter 通过 [toBlock] 拿到结构化数据。
 */
class TextBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BlockView(context, attrs, defStyleAttr) {

    private lateinit var blockId: String
    private var heading: Heading? = null
    val edit: EditText

    init {
        LayoutInflater.from(context).inflate(R.layout.block_text, this, true)
        edit = findViewById(R.id.block_edit)
        wireListeners()
    }

    override fun bind(block: Block) {
        require(block is Block.TextBlock) { "TextBlockView only binds TextBlock" }
        blockId = block.id
        heading = block.heading
        applyHeadingSize()
        val spannable = SpannableString(block.text)
        block.spans.applyTo(spannable)
        edit.setText(spannable)
    }

    override fun toBlock(): Block.TextBlock {
        val spannable = SpannableString(edit.text)
        return Block.TextBlock(
            id = blockId,
            heading = heading,
            text = spannable.toString(),
            spans = spannable.toTextSpans(),
        )
    }

    /** Presenter 调：切换 H1/H2/正文。 */
    fun setHeading(h: Heading?) {
        heading = h
        applyHeadingSize()
    }

    fun currentHeading(): Heading? = heading

    /** 让 Presenter 把焦点交给这块（新块、删除上一块时上移焦点都用）。 */
    fun focusEditEnd() {
        edit.requestFocus()
        edit.setSelection(edit.text.length)
    }

    private fun applyHeadingSize() {
        val sp = when (heading) {
            Heading.H1 -> resources.getDimension(R.dimen.editor_text_h1)
            Heading.H2 -> resources.getDimension(R.dimen.editor_text_h2)
            null -> resources.getDimension(R.dimen.editor_text_normal)
        }
        edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp)
    }

    private fun wireListeners() {
        edit.setOnFocusChangeListener { _, focused ->
            if (focused) callback?.onFocusGained(this)
        }

        // 末尾按回车 → 上抛 split；中间按回车 → 让 EditText 自己换行
        edit.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                val sel = edit.selectionStart
                if (sel == edit.text.length) {
                    callback?.onRequestSplitAfter(this)
                    return@setOnKeyListener true
                }
            }
            // 空块按退格 → 上抛 delete
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                if (edit.text.isEmpty()) {
                    callback?.onRequestDelete(this)
                    return@setOnKeyListener true
                }
            }
            false
        }

        // TextWatcher 暂不做事，留给 Task 8（pendingStyles 应用到刚插入字符）
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
```

- [ ] **Step 3: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/view/block/BlockView.kt \
        app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt
git commit -m "feat(m4): BlockView 抽象基类 + TextBlockView（EditText + heading + Span 渲染）"
```

---

## Task 6: `EditorPresenter.kt` + `NoteEditorActivity.kt` 骨架

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`
- Create: `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

> 本任务**不接工具栏**（底栏仍然是 Task 2 的占位 `<View>`）。重点：进出编辑器闭环 — `onCreate` 从 Repository 取 / 新建空块；`onPause` 收集落库；`onBackPressed` 走系统默认（保留落库）。

- [ ] **Step 1: 写 `EditorPresenter.kt`**

```kotlin
package com.fan.hwnote.app.controller.editor

import android.content.Context
import android.widget.LinearLayout
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.entity.NoteContent
import com.fan.hwnote.app.view.block.BlockView
import com.fan.hwnote.app.view.block.TextBlockView
import java.util.UUID

/**
 * 编辑器协调器 — 不是 MVP 的 Presenter，而是把 BlockView 列表与待生效样式状态独立出来的辅助类。
 *
 * 状态：
 *  - currentBlocks: container 内的 BlockView 顺序快照（与 ViewGroup 子 view 顺序一致）
 *  - currentNote: onCreate 拿到的 Note 快照（保留 id / createdAt / isFavorite）
 *  - focusedTextBlock: 最近一次获得焦点的 TextBlockView（Presenter 通过 callback 维护）
 */
class EditorPresenter(
    private val context: Context,
    private val container: LinearLayout,
) : BlockView.Callback {

    private val currentBlocks = mutableListOf<BlockView>()
    private lateinit var currentNote: Note
    private var focusedTextBlock: TextBlockView? = null

    fun bind(note: Note) {
        currentNote = note
        container.removeAllViews()
        currentBlocks.clear()
        focusedTextBlock = null

        val blocks = note.content.blocks.ifEmpty { listOf(emptyTextBlock()) }
        for (b in blocks) {
            when (b) {
                is Block.TextBlock -> addTextBlockView(b)
                is Block.ImageBlock -> Unit // M5 接入
                is Block.ChecklistBlock -> Unit // M6 接入
            }
        }
        // 默认让第一块拿到焦点
        (currentBlocks.firstOrNull() as? TextBlockView)?.focusEditEnd()
    }

    fun currentFocusedTextBlock(): TextBlockView? = focusedTextBlock

    /**
     * 把 UI 当前内容收集成一份新 Note（保留原 id / createdAt / isFavorite，更新 title / content）。
     * 调用方负责 save 到 Repository。
     */
    fun collectCurrentNote(title: String): Note {
        val newBlocks = currentBlocks.map { it.toBlock() }
        val content = NoteContent(blocks = newBlocks, handwriting = emptyList())
        return currentNote.copy(
            title = title,
            plainText = content.toPlainText(),
            content = content,
        )
    }

    // ----- BlockView.Callback -----

    override fun onRequestSplitAfter(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx < 0) return
        val newBlock = emptyTextBlock()
        addTextBlockView(newBlock, insertAt = idx + 1)
        (currentBlocks[idx + 1] as TextBlockView).focusEditEnd()
    }

    override fun onRequestDelete(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx <= 0) return // 第一块不可删
        container.removeView(view)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === view) focusedTextBlock = null
        (currentBlocks[idx - 1] as? TextBlockView)?.focusEditEnd()
    }

    override fun onFocusGained(view: BlockView) {
        if (view is TextBlockView) focusedTextBlock = view
    }

    // ----- private -----

    private fun emptyTextBlock(): Block.TextBlock =
        Block.TextBlock(id = "b-${UUID.randomUUID().toString().take(8)}")

    private fun addTextBlockView(block: Block.TextBlock, insertAt: Int = -1) {
        val v = TextBlockView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            callback = this@EditorPresenter
            bind(block)
        }
        if (insertAt < 0 || insertAt >= currentBlocks.size) {
            container.addView(v)
            currentBlocks.add(v)
        } else {
            container.addView(v, insertAt)
            currentBlocks.add(insertAt, v)
        }
    }

    /** Task 10 H1/H2 用：对当前焦点 TextBlock 切 heading。 */
    fun toggleHeading(target: Heading) {
        val v = focusedTextBlock ?: return
        v.setHeading(if (v.currentHeading() == target) null else target)
    }
}
```

- [ ] **Step 2: 写 `NoteEditorActivity.kt`**

```kotlin
package com.fan.hwnote.app.controller.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.NoteRepository
import com.fan.hwnote.app.model.entity.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var titleInput: EditText
    private lateinit var blocksContainer: LinearLayout
    private lateinit var presenter: EditorPresenter

    private var noteId: Long = -1L
    private var loadedNote: Note? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        toolbar = findViewById(R.id.editor_toolbar)
        titleInput = findViewById(R.id.title_input)
        blocksContainer = findViewById(R.id.blocks_container)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        presenter = EditorPresenter(this, blocksContainer)

        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        loadNote()
    }

    override fun onPause() {
        super.onPause()
        // 退出（包括按返回 / Home / 横屏）都落库一次。
        saveNote()
    }

    private fun loadNote() {
        lifecycleScope.launch {
            val note = if (noteId == -1L) Note.new() else (NoteRepository.get(noteId) ?: Note.new())
            loadedNote = note
            titleInput.setText(note.title)
            presenter.bind(note)
        }
    }

    private fun saveNote() {
        val loaded = loadedNote ?: return // 还没加载完成，不存
        val title = titleInput.text.toString()
        val toSave = presenter.collectCurrentNote(title).copy(id = loaded.id)
        // 全空且是新笔记则不存
        val isAllEmpty = title.isEmpty() && toSave.plainText.isEmpty()
        if (loaded.id == 0L && isAllEmpty) return
        lifecycleScope.launch(Dispatchers.IO) {
            val newId = NoteRepository.save(toSave)
            // 更新 noteId / loadedNote，避免下次 onPause 再 insert 一条
            if (loaded.id == 0L && newId > 0) {
                noteId = newId
                loadedNote = toSave.copy(id = newId)
            }
        }
    }

    companion object {
        private const val EXTRA_NOTE_ID = "noteId"
        fun newIntent(context: Context, noteId: Long): Intent =
            Intent(context, NoteEditorActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
            }
    }
}
```

- [ ] **Step 3: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 BUILD SUCCESSFUL。

- [ ] **Step 4: 装包并手测（验证编辑器骨架闭环）**

> 因为列表页还没替换跳转占位（Task 7 才换），此次手测**临时通过 adb 启动 Activity** 来验证：

```bash
./gradlew :app:installDebug
adb shell am start -n com.fan.hwnote.app/.controller.editor.NoteEditorActivity
```

**验收：**
- [ ] 编辑器打开，顶栏返回箭头可见，标题 EditText 显示 hint "标题"
- [ ] 标题下方有一个空文本块，hint "开始记录…"，光标在文本块
- [ ] 输入标题 "测试 1"，输入正文 "hello world"，按返回
- [ ] `adb shell run-as com.fan.hwnote.app sqlite3 databases/hwnote.db "select id,title,plain_text from notes order by id desc limit 1"` → 看到刚才那条
- [ ] 再次 `am start` 进入 + 手动启动 Activity 加上 `--el noteId <刚才的 id>`：

```bash
adb shell am start -n com.fan.hwnote.app/.controller.editor.NoteEditorActivity --el noteId <id>
```

- [ ] 标题与正文都正确恢复

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt \
        app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "feat(m4): EditorPresenter + NoteEditorActivity 骨架（加载/保存/标题/块容器）"
```

---

## Task 7: `NoteListActivity` 替换 2 处 Toast 占位 → 跳编辑器

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

> 列表页 M3 留下的两个占位都在这里替换。**FAB 不再临时落空笔记**，而是直接跳编辑器并由编辑器 onPause 自行决定要不要落库（Task 6 的 `isAllEmpty` 短路逻辑保证不会留垃圾）。

- [ ] **Step 1: 替换卡片点击占位（约 54-62 行）**

把：

```kotlin
        adapter = NoteListAdapter(
            onClick = { note ->
                Toast.makeText(
                    this, R.string.toast_open_editor_placeholder, Toast.LENGTH_SHORT
                ).show()
                // M4：替换为 startActivity(NoteEditorActivity.newIntent(this, note.id))
            },
            onLongClick = { note, anchor -> showCardMenu(note, anchor) },
        )
```

改为：

```kotlin
        adapter = NoteListAdapter(
            onClick = { note ->
                startActivity(NoteEditorActivity.newIntent(this, note.id))
            },
            onLongClick = { note, anchor -> showCardMenu(note, anchor) },
        )
```

- [ ] **Step 2: 替换 FAB 占位（约 68-86 行）**

把：

```kotlin
        fab.setOnClickListener {
            // M3 占位：直接落一条空笔记，验证 列表→数据→刷新 闭环
            // M4：替换为 startActivity(NoteEditorActivity.newIntent(this, noteId = -1L))
            lifecycleScope.launch {
                val now = System.currentTimeMillis()
                NoteRepository.save(
                    Note(
                        id = 0L,
                        title = "",
                        plainText = "",
                        isFavorite = false,
                        createdAt = now,
                        updatedAt = now,
                        content = NoteContent.empty(),
                    )
                )
                reload()
            }
        }
```

改为：

```kotlin
        fab.setOnClickListener {
            startActivity(NoteEditorActivity.newIntent(this, -1L))
        }
```

- [ ] **Step 3: 清理无用 import**

文件顶部把这些不再用到的 import 删掉：

```kotlin
import android.widget.Toast
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.model.entity.NoteContent
```

并新增：

```kotlin
import com.fan.hwnote.app.controller.editor.NoteEditorActivity
```

> 注意：`Note` 在 `showCardMenu(note: Note, ...)` 签名中仍然用到 — 如果 IDE 提示 unresolved，把 `Note` import 加回来。Toast / NoteContent 一定不再需要。

- [ ] **Step 4: 删除不再使用的字符串词条（可选，YAGNI）**

`strings.xml` 里 `toast_open_editor_placeholder` 和 `toast_new_note_placeholder` 两条不再被引用，**保留也无害**。如果要删，确认全工程 grep 无引用后再删。本步骤可跳过；建议保留以减少改动面。

- [ ] **Step 5: 构建 + 装包**

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

- [ ] **Step 6: 手测**

- [ ] 启动 app，点 FAB → 进入编辑器（空标题 + 空块）
- [ ] 输入标题 "M4 验证"，正文 "我是新笔记"，按返回 → 列表多一条
- [ ] 点列表中的这条卡片 → 编辑器打开，标题与内容正确恢复
- [ ] 点 FAB → 不输入任何东西直接返回 → 列表**不增加**空白笔记（Task 6 的 isAllEmpty 短路）

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m4): 列表页卡片点击与 FAB 接入编辑器（替换 M3 占位）"
```

---

## Task 8: `TextToolbarView` 骨架 + 行内 B/I/U/S

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/view/toolbar/TextToolbarView.kt`
- Modify: `app/src/main/res/layout/activity_note_editor.xml`（把占位 `<View>` 改回 `<com.fan.hwnote.app.view.toolbar.TextToolbarView>`）
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`（把工具栏接进来）
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`（增 `pendingStyles` 状态 + `applyInlineStyle` 接口）
- Modify: `app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt`（TextWatcher 中应用 pendingStyles 到刚插入的字符）

> 行为规范（PRD §8.2）：
> - **有选区** → 直接对选区应用样式（已有同类型 span 则取消，整段切换）
> - **无选区** → 按钮变 selected，记录到 `pendingStyles`；之后用户每输入一个字符，TextWatcher 把同类型 span 应用到刚插入的范围；用户再点一次按钮取消 pending。

- [ ] **Step 1: 写 `TextToolbarView.kt`**

```kotlin
package com.fan.hwnote.app.view.toolbar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.SpanType

class TextToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var listener: Listener? = null

    private val btnBold by lazy { findViewById<android.widget.TextView>(R.id.btn_bold) }
    private val btnItalic by lazy { findViewById<android.widget.TextView>(R.id.btn_italic) }
    private val btnUnderline by lazy { findViewById<android.widget.TextView>(R.id.btn_underline) }
    private val btnStrike by lazy { findViewById<android.widget.TextView>(R.id.btn_strike) }
    private val btnSizeSmall by lazy { findViewById<android.widget.TextView>(R.id.btn_size_small) }
    private val btnSizeNormal by lazy { findViewById<android.widget.TextView>(R.id.btn_size_normal) }
    private val btnSizeLarge by lazy { findViewById<android.widget.TextView>(R.id.btn_size_large) }
    private val btnColor by lazy { findViewById<android.widget.ImageView>(R.id.btn_color) }
    private val btnH1 by lazy { findViewById<android.widget.TextView>(R.id.btn_h1) }
    private val btnH2 by lazy { findViewById<android.widget.TextView>(R.id.btn_h2) }
    private val btnImage by lazy { findViewById<android.widget.ImageView>(R.id.btn_image) }
    private val btnChecklist by lazy { findViewById<android.widget.ImageView>(R.id.btn_checklist) }

    init {
        LayoutInflater.from(context).inflate(R.layout.toolbar_text, this, true)
        wireListeners()
    }

    /** 设置按钮的 selected 高亮（pending 样式时显示）。 */
    fun setInlineSelected(type: SpanType, selected: Boolean) {
        val btn = when (type) {
            SpanType.BOLD -> btnBold
            SpanType.ITALIC -> btnItalic
            SpanType.UNDERLINE -> btnUnderline
            SpanType.STRIKETHROUGH -> btnStrike
            else -> return
        }
        btn.isSelected = selected
        btn.setBackgroundColor(
            if (selected) context.getColor(R.color.toolbar_btn_selected)
            else android.graphics.Color.TRANSPARENT
        )
    }

    private fun wireListeners() {
        btnBold.setOnClickListener { listener?.onInlineToggle(SpanType.BOLD) }
        btnItalic.setOnClickListener { listener?.onInlineToggle(SpanType.ITALIC) }
        btnUnderline.setOnClickListener { listener?.onInlineToggle(SpanType.UNDERLINE) }
        btnStrike.setOnClickListener { listener?.onInlineToggle(SpanType.STRIKETHROUGH) }

        // Task 9 接入字号 / 颜色
        btnSizeSmall.setOnClickListener { listener?.onSizePicked("small") }
        btnSizeNormal.setOnClickListener { listener?.onSizePicked("medium") }
        btnSizeLarge.setOnClickListener { listener?.onSizePicked("large") }
        btnColor.setOnClickListener { listener?.onColorClicked() }

        // Task 10 接入 H1/H2
        btnH1.setOnClickListener { listener?.onHeadingToggle(true) }
        btnH2.setOnClickListener { listener?.onHeadingToggle(false) }

        // M5/M6 占位
        btnImage.setOnClickListener { listener?.onImageClicked() }
        btnChecklist.setOnClickListener { listener?.onChecklistClicked() }
    }

    interface Listener {
        fun onInlineToggle(type: SpanType)
        fun onSizePicked(value: String)              // "small" / "medium" / "large"
        fun onColorClicked()
        fun onHeadingToggle(isH1: Boolean)            // true=H1, false=H2
        fun onImageClicked()
        fun onChecklistClicked()
    }
}
```

- [ ] **Step 2: 把 `activity_note_editor.xml` 底部占位 `<View>` 改回 `TextToolbarView`**

```xml
    <com.fan.hwnote.app.view.toolbar.TextToolbarView
        android:id="@+id/text_toolbar"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="@color/toolbar_bg" />
```

- [ ] **Step 3: 在 `EditorPresenter.kt` 加行内样式应用接口**

在 `EditorPresenter` 类内部新增字段与方法（放在 `currentBlocks` 后面）：

```kotlin
    /** 无选区时，下次输入应套用的 span 类型集合。同 type 第二次点表示取消。 */
    private val pendingInline = mutableSetOf<com.fan.hwnote.app.model.entity.SpanType>()
    /** 待生效字号 / 颜色（互斥单值，null 表示未启用 pending）。 */
    private var pendingSize: String? = null
    private var pendingColor: String? = null

    fun pendingInlineSet(): Set<com.fan.hwnote.app.model.entity.SpanType> = pendingInline
    fun pendingSize(): String? = pendingSize
    fun pendingColor(): String? = pendingColor

    /**
     * 行内 B/I/U/S 按钮入口。
     *  - 有选区：对选区翻转该类型 span（已有则全删，没有则加一条覆盖整段）
     *  - 无选区：翻转 pendingInline 中的标志位（UI 由调用方设置 selected 高亮）
     * 返回 true 表示 pending（高亮按钮），false 表示已对选区直接生效（不需要高亮）。
     */
    fun toggleInline(type: com.fan.hwnote.app.model.entity.SpanType): Boolean {
        val v = focusedTextBlock ?: return false
        val edit = v.edit
        val start = edit.selectionStart
        val end = edit.selectionEnd
        if (start in 0 until end) {
            applyInlineToRange(edit.text as android.text.Spannable, type, start, end, null)
            return false
        }
        if (pendingInline.contains(type)) pendingInline.remove(type) else pendingInline.add(type)
        return pendingInline.contains(type)
    }

    /**
     * 对范围 [start, end) 翻转 type 类样式：已有同类型 span 全部删除；否则添加一条整段覆盖。
     * 字号 / 颜色由 [value] 提供（type=FONT_SIZE / COLOR 时必填）。
     */
    fun applyInlineToRange(
        sp: android.text.Spannable,
        type: com.fan.hwnote.app.model.entity.SpanType,
        start: Int,
        end: Int,
        value: String?,
    ) {
        // 复用 SpanConverter：先转出 list，操作后再清空 + 重新 apply
        val existing = (sp as android.text.SpannableString).run {
            this // 占位，保持类型
        }
        val current = sp.run { com.fan.hwnote.app.util.toTextSpans.let { fn -> fn(this) } }
        // 上面这种隐式调用会编不过；改成直接展开：
        // 我们改用更直接的方式：
        val raw: List<com.fan.hwnote.app.model.entity.TextSpan> = run {
            val out = mutableListOf<com.fan.hwnote.app.model.entity.TextSpan>()
            // 直接读取 sp 的 spans
            for (s in sp.getSpans(0, sp.length, Any::class.java)) {
                val st = sp.getSpanStart(s); val en = sp.getSpanEnd(s)
                if (st < 0 || en <= st) continue
                when (s) {
                    is android.text.style.StyleSpan -> when (s.style) {
                        android.graphics.Typeface.BOLD ->
                            out += com.fan.hwnote.app.model.entity.TextSpan(st, en,
                                com.fan.hwnote.app.model.entity.SpanType.BOLD)
                        android.graphics.Typeface.ITALIC ->
                            out += com.fan.hwnote.app.model.entity.TextSpan(st, en,
                                com.fan.hwnote.app.model.entity.SpanType.ITALIC)
                    }
                    is android.text.style.UnderlineSpan ->
                        out += com.fan.hwnote.app.model.entity.TextSpan(st, en,
                            com.fan.hwnote.app.model.entity.SpanType.UNDERLINE)
                    is android.text.style.StrikethroughSpan ->
                        out += com.fan.hwnote.app.model.entity.TextSpan(st, en,
                            com.fan.hwnote.app.model.entity.SpanType.STRIKETHROUGH)
                    is android.text.style.RelativeSizeSpan -> {
                        val v = when {
                            kotlin.math.abs(s.sizeChange - 0.85f) < 0.01f -> "small"
                            kotlin.math.abs(s.sizeChange - 1.25f) < 0.01f -> "large"
                            kotlin.math.abs(s.sizeChange - 1.0f) < 0.01f -> "medium"
                            else -> null
                        }
                        if (v != null) out += com.fan.hwnote.app.model.entity.TextSpan(st, en,
                            com.fan.hwnote.app.model.entity.SpanType.FONT_SIZE, v)
                    }
                    is android.text.style.ForegroundColorSpan -> {
                        val hex = "#%06X".format(0xFFFFFF and s.foregroundColor)
                        out += com.fan.hwnote.app.model.entity.TextSpan(st, en,
                            com.fan.hwnote.app.model.entity.SpanType.COLOR, hex)
                    }
                }
            }
            out
        }
        // 清掉所有 span（不会动文字本身）
        for (s in sp.getSpans(0, sp.length, Any::class.java)) sp.removeSpan(s)
        // 翻转：删除范围内同 type；如本来无任何同 type 覆盖该段，加一条整段
        val sameType = raw.filter { it.type == type && rangeOverlaps(it.start, it.end, start, end) }
        val kept = raw.filter { it !in sameType }
        val newList = if (sameType.isEmpty()) kept + com.fan.hwnote.app.model.entity.TextSpan(start, end, type, value) else kept
        com.fan.hwnote.app.util.applyTo(newList, sp)
    }

    private fun rangeOverlaps(a1: Int, a2: Int, b1: Int, b2: Int): Boolean =
        a1 < b2 && b1 < a2

    /** TextWatcher 调：刚插入的范围 [start, start+count)，应用 pendingInline / pendingSize / pendingColor。 */
    fun applyPendingTo(sp: android.text.Spannable, start: Int, count: Int) {
        if (count <= 0) return
        val end = start + count
        val flag = android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        if (pendingInline.contains(com.fan.hwnote.app.model.entity.SpanType.BOLD)) {
            sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, flag)
        }
        if (pendingInline.contains(com.fan.hwnote.app.model.entity.SpanType.ITALIC)) {
            sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), start, end, flag)
        }
        if (pendingInline.contains(com.fan.hwnote.app.model.entity.SpanType.UNDERLINE)) {
            sp.setSpan(android.text.style.UnderlineSpan(), start, end, flag)
        }
        if (pendingInline.contains(com.fan.hwnote.app.model.entity.SpanType.STRIKETHROUGH)) {
            sp.setSpan(android.text.style.StrikethroughSpan(), start, end, flag)
        }
        pendingSize?.let { v ->
            val r = when (v) { "small" -> 0.85f; "large" -> 1.25f; else -> 1.0f }
            sp.setSpan(android.text.style.RelativeSizeSpan(r), start, end, flag)
        }
        pendingColor?.let { hex ->
            val c = runCatching { android.graphics.Color.parseColor(hex) }.getOrNull() ?: return@let
            sp.setSpan(android.text.style.ForegroundColorSpan(c), start, end, flag)
        }
    }

    /** Task 9 入口：字号 pending（再点同档取消）。返回新 pending 值（null 表示已取消）。 */
    fun toggleSize(value: String): String? {
        val v = focusedTextBlock ?: return null
        val edit = v.edit
        val start = edit.selectionStart; val end = edit.selectionEnd
        if (start in 0 until end) {
            applyInlineToRange(edit.text as android.text.Spannable,
                com.fan.hwnote.app.model.entity.SpanType.FONT_SIZE, start, end, value)
            return null
        }
        pendingSize = if (pendingSize == value) null else value
        return pendingSize
    }

    /** Task 9 入口：颜色 pending 或选区直接生效。 */
    fun pickColor(hex: String): String? {
        val v = focusedTextBlock ?: return null
        val edit = v.edit
        val start = edit.selectionStart; val end = edit.selectionEnd
        if (start in 0 until end) {
            applyInlineToRange(edit.text as android.text.Spannable,
                com.fan.hwnote.app.model.entity.SpanType.COLOR, start, end, hex)
            return null
        }
        pendingColor = if (pendingColor == hex) null else hex
        return pendingColor
    }
```

> **NOTE：** 上面 `applyInlineToRange` 中那一段 `existing` / `current` 的占位代码是保留的"思路注释"，最终代码里**不要**保留 — 直接用展开后的 `raw` 块即可。复制时请删除：
>
> ```kotlin
> val existing = (sp as android.text.SpannableString).run { this }
> val current = sp.run { com.fan.hwnote.app.util.toTextSpans.let { fn -> fn(this) } }
> ```
>
> 这两行删掉，从 `val raw: List<...> = run {` 开始即可。

- [ ] **Step 4: 在 `TextBlockView.kt` 给 TextWatcher 接 pendingStyles**

把 `wireListeners()` 中那个空的 `addTextChangedListener` 改为：

```kotlin
        edit.addTextChangedListener(object : TextWatcher {
            private var insertStart = 0
            private var insertCount = 0
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                insertStart = start
                insertCount = count - before  // 净插入量；正常打字 count=1 before=0
            }
            override fun afterTextChanged(s: Editable?) {
                if (insertCount > 0 && s is android.text.Spannable) {
                    pendingApplier?.invoke(s, insertStart, insertCount)
                }
            }
        })
    }

    /** Presenter 注入：把 pending 样式应用到刚插入的文字范围。 */
    var pendingApplier: ((android.text.Spannable, Int, Int) -> Unit)? = null
```

> 注意把 `pendingApplier` 字段放在 `wireListeners()` 之外，类成员位置（与 `edit`、`heading` 同级）。

- [ ] **Step 5: 在 `EditorPresenter.addTextBlockView` 中注入 `pendingApplier`**

把现有 `addTextBlockView` 内创建 `TextBlockView` 的 apply 块改为：

```kotlin
    private fun addTextBlockView(block: Block.TextBlock, insertAt: Int = -1) {
        val v = TextBlockView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            callback = this@EditorPresenter
            pendingApplier = { sp, start, count -> applyPendingTo(sp, start, count) }
            bind(block)
        }
        if (insertAt < 0 || insertAt >= currentBlocks.size) {
            container.addView(v)
            currentBlocks.add(v)
        } else {
            container.addView(v, insertAt)
            currentBlocks.add(insertAt, v)
        }
    }
```

- [ ] **Step 6: 在 `NoteEditorActivity.kt` 把工具栏接进来（仅 B/I/U/S 部分；字号/颜色/H1/H2 留 Task 9-10 实现）**

`onCreate` 末尾追加：

```kotlin
        val toolbarView = findViewById<com.fan.hwnote.app.view.toolbar.TextToolbarView>(R.id.text_toolbar)
        toolbarView.listener = object : com.fan.hwnote.app.view.toolbar.TextToolbarView.Listener {
            override fun onInlineToggle(type: com.fan.hwnote.app.model.entity.SpanType) {
                val pending = presenter.toggleInline(type)
                toolbarView.setInlineSelected(type, pending)
            }
            override fun onSizePicked(value: String) { /* Task 9 */ }
            override fun onColorClicked() { /* Task 9 */ }
            override fun onHeadingToggle(isH1: Boolean) { /* Task 10 */ }
            override fun onImageClicked() {
                android.widget.Toast.makeText(this@NoteEditorActivity,
                    R.string.toast_image_placeholder, android.widget.Toast.LENGTH_SHORT).show()
            }
            override fun onChecklistClicked() {
                android.widget.Toast.makeText(this@NoteEditorActivity,
                    R.string.toast_checklist_placeholder, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
```

- [ ] **Step 7: 构建 + 装包**

```bash
./gradlew :app:assembleDebug && ./gradlew :app:installDebug
```

- [ ] **Step 8: 手测**

- [ ] 进编辑器，输入 "hello world"
- [ ] 选中 "hello" → 点 B → 文字加粗；再点 B → 取消加粗
- [ ] 不选区，光标停在末尾，点 B（按钮高亮）→ 输入 "abc" → "abc" 三字为粗体；再点 B（按钮取消高亮）→ 输入 "def" → "def" 普通
- [ ] 同样测 I / U / S，每种各做一次"选区 + pending"
- [ ] 同时点 B + I（按钮俩都高亮）→ 输入 → 文字粗+斜
- [ ] 返回 → 重进 → 所有样式保留

- [ ] **Step 9: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/view/toolbar/TextToolbarView.kt \
        app/src/main/res/layout/activity_note_editor.xml \
        app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt \
        app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt \
        app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt
git commit -m "feat(m4): 编辑器工具栏 + 行内 B/I/U/S（选区生效 + 无选区 pending）"
```

---

## Task 9: 字号 A-/A/A+ + 颜色 5 色

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`（接 `onSizePicked` / `onColorClicked`）
- Modify: `app/src/main/java/com/fan/hwnote/app/view/toolbar/TextToolbarView.kt`（颜色按钮 tint 反映当前 pending）

> 字号沿用 Task 8 已实现的 `EditorPresenter.toggleSize`；颜色沿用 `pickColor`。本任务只接 UI。

- [ ] **Step 1: 在 `NoteEditorActivity` 中实现 `onSizePicked` / `onColorClicked`**

把 Task 8 留空的两个回调改为：

```kotlin
            override fun onSizePicked(value: String) {
                presenter.toggleSize(value)
                // 字号档位无需高亮（用户能直接看到字大小变化），可省略 selected 反馈
            }
            override fun onColorClicked() {
                showColorPickerDialog()
            }
```

并新增方法：

```kotlin
    private fun showColorPickerDialog() {
        val labels = arrayOf(
            getString(R.string.color_black),
            getString(R.string.color_red),
            getString(R.string.color_yellow),
            getString(R.string.color_green),
            getString(R.string.color_blue),
        )
        val hexes = arrayOf("#212121", "#E53935", "#FB8C00", "#43A047", "#1E88E5")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_pick_color_title)
            .setItems(labels) { _, which ->
                val hex = hexes[which]
                presenter.pickColor(hex)
                // 更新颜色按钮 tint 反映"当前选择"
                val toolbarView = findViewById<com.fan.hwnote.app.view.toolbar.TextToolbarView>(R.id.text_toolbar)
                toolbarView.setColorIndicator(android.graphics.Color.parseColor(hex))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
```

- [ ] **Step 2: 在 `TextToolbarView` 加 `setColorIndicator(int)` 方法**

```kotlin
    /** 把颜色按钮的圆点 tint 改为指定色。 */
    fun setColorIndicator(color: Int) {
        btnColor.setColorFilter(color)
    }
```

- [ ] **Step 3: 构建 + 装包**

```bash
./gradlew :app:assembleDebug && ./gradlew :app:installDebug
```

- [ ] **Step 4: 手测**

- [ ] 选中 "world" → 点 A+ → 字号变大；再点 A+ → 取消（恢复正文 16sp）
- [ ] 不选区 → 点 A- → 后续输入字号偏小；再点 A- → 取消
- [ ] 选中 "hello" → 点颜色 → 弹色板（5 项：黑/红/黄/绿/蓝）→ 选红 → 文字变红
- [ ] 不选区 → 点颜色 → 选蓝 → 颜色按钮圆点变蓝 → 输入字符 → 字符为蓝色
- [ ] 同时 B + 颜色：选中文字 → 点 B（粗）→ 点颜色选红 → 红粗共存
- [ ] 返回 → 重进 → 字号 / 颜色全部保留

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt \
        app/src/main/java/com/fan/hwnote/app/view/toolbar/TextToolbarView.kt
git commit -m "feat(m4): 字号 A-/A/A+ + 颜色 5 色（弹窗 + 选区/pending 双模式）"
```

---

## Task 10: 块级 H1/H2 切换

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`（接 `onHeadingToggle`）

> 块级属性，不是行内 span。沿用 Task 6 已实现的 `EditorPresenter.toggleHeading(Heading)`。

- [ ] **Step 1: 实现 `onHeadingToggle`**

把 Task 8 留空的回调改为：

```kotlin
            override fun onHeadingToggle(isH1: Boolean) {
                val target = if (isH1) com.fan.hwnote.app.model.entity.Heading.H1
                             else com.fan.hwnote.app.model.entity.Heading.H2
                presenter.toggleHeading(target)
            }
```

- [ ] **Step 2: 构建 + 装包**

```bash
./gradlew :app:assembleDebug && ./gradlew :app:installDebug
```

- [ ] **Step 3: 手测**

- [ ] 进编辑器，输入两段文字 "段一" 与 "段二"（中间按回车 → 两个 TextBlock）
- [ ] 光标停在 "段一" → 点 H1 → "段一" 字号变 22sp；再点 H1 → 恢复正文 16sp
- [ ] 光标停在 "段一" → 点 H2 → 字号变 18sp
- [ ] 光标停在 "段一"（H2 状态）→ 再点 H1 → 切换为 H1（22sp）
- [ ] "段二" 不受影响（始终正文）
- [ ] 返回 → 重进 → H1/H2 保留

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "feat(m4): 块级 H1/H2 切换（光标所在 TextBlock 字号切换）"
```

---

## Task 11: 末块 Enter split + 空块 Backspace delete 真机走查

**Files:**
- 一般无需改动；Task 5 已经在 `TextBlockView.wireListeners` 内挂好了 `setOnKeyListener` 处理 ENTER / DEL。
- 如手测发现回车在中段也被吞、或退格删错块，再回到 `TextBlockView` 微调。

> 本任务以**手测验收**为主。前面 Task 5 / 6 已经写完逻辑，本步只确认行为符合 PRD §8.2。

- [ ] **Step 1: 手测 — Enter 行为**

- [ ] 编辑器中输入 "abcdef"
- [ ] 光标移到 "c" 与 "d" 之间（不在末尾）→ 按回车 → 同段内换行（变两行 "abc" / "def" 仍在同一 TextBlock）
- [ ] 光标移到末尾 → 按回车 → **新增一块空 TextBlock，焦点在新块**
- [ ] 在新块输入 "xyz" → 返回 → 重进 → 看到两块（"abc\ndef" + "xyz"）

> 期望：`onRequestSplitAfter` 仅在光标位于 EditText 末尾时触发；中段回车走 EditText 默认换行行为。

- [ ] **Step 2: 手测 — Backspace 行为**

- [ ] 末尾新块（空块）按退格 → 块被删，焦点回上一块末尾
- [ ] 上一块退格 → 删字符（不会删块，因为非空）
- [ ] 第一块退格到空 → 再按退格 → **不删第一块**（PRD：第一块不可删）
- [ ] 回车新建一空块 → 退格 → 删块 → 焦点回上一块末尾，可继续输入

- [ ] **Step 3: 如有偏差，按需修正 `TextBlockView.kt` 的 `setOnKeyListener`**

常见偏差与对应修正：
- 中段回车也跳新块 → 检查 `sel == edit.text.length` 条件
- 第一块按退格被删 → 已由 `EditorPresenter.onRequestDelete` 中 `idx <= 0` 短路保护，不应发生；如发生，复查那段
- 中文输入法（IME）下回车不触发 → 这是 IME 的"组合输入"特性；本里程碑可接受（PRD 未要求 IME 兼容）；记入"已知限制"

- [ ] **Step 4: 提交（如有微调）**

```bash
git add app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt
git commit -m "fix(m4): 末块回车 split / 空块退格 delete 真机走查微调"
```

> 如果 Step 1-2 全部一次过、无任何修改，跳过 Step 3-4，本任务无 commit；下一任务的 commit 自然推进。

---

## Task 12: 全量回归走查（9 条手测）+ STATUS.md 更新

**Files:**
- Modify: `docs/superpowers/STATUS.md`（标记 M4 完成）

- [ ] **Step 1: 真机/模拟器走 PRD §M4 高层验收的 9 条**

按 `2026-05-22-hwnote-implementation.md` 第 336-344 行：

1. **新建笔记**：FAB → 输入标题 + 一段文字 → 返回 → 列表多一条。✅
2. **选区加粗**：选中一段文字 → 点 B → 加粗；再点 B → 取消。✅
3. **pending 加粗**：无选区 → 点 B → 后续输入带粗体；再点 B → 关闭。✅
4. **字号 A+**：选中文字 → 点 A+ → 字号变大；再点 A+ → 恢复。✅
5. **颜色**：选中文字 → 点颜色 → 弹色板 → 选红 → 文字变红。✅
6. **H1**：光标停在某段 → 点 H1 → 该段字号变大；再点 H1 → 恢复正文。✅
7. **段中回车**：段中按回车 → 同段内换行；段末按回车 → 新建空文本块。✅
8. **空块退格**：空文本块按退格 → 块被删，焦点回上一块末尾。✅
9. **退出再进数据保留**：退出再进 → 所有样式（粗/斜/下划/删除/字号/颜色/H1/H2）保留。✅

附加冒烟：
- **横屏旋转**：旋转设备，编辑器内容不丢（onPause 已落库；旋转重建走 onCreate 重新加载）
- **多块场景**：5 段以上交错样式，全部正确恢复
- **空笔记**：FAB → 不输入任何内容直接返回 → 列表不增加（Task 6 isAllEmpty 短路）
- **图片/清单按钮**：点击后弹"M5/M6 实现"占位 toast，不崩
- **从列表点开已有笔记**：标题、正文、所有样式、H1/H2 都正确显示

- [ ] **Step 2: 修复走查发现的小问题**

如：
- 字号档位 sp 实际显示偏差太大 → 微调 SpanConverter 的 0.85 / 1.25 比例
- 颜色按钮圆点 tint 重启后回退默认（按钮没保留状态） → 接受（无持久化要求）
- TextBlock 之间间距过密 → 在 `block_text.xml` 加 `paddingVertical`
- toolbar 在小屏被遮挡 → 改 `HorizontalScrollView` 行为或减小按钮宽度

每个微调单独 commit，commit message 形如 `fix(m4): toolbar 按钮宽度 48dp 在小屏拥挤 → 调整为 44dp`。

- [ ] **Step 3: 更新 `docs/superpowers/STATUS.md`**

把 M4 状态从 "进行中 / 待开始" 改为 "已完成 (2026-MM-DD)"，写一行 commit 概览（HEAD 哈希、任务数、单测数）。

- [ ] **Step 4: 最终提交**

```bash
git add docs/superpowers/STATUS.md
git commit -m "docs(m4): 标记 M4 编辑器骨架完成"
```

---

## 自查清单

写完代码并完成 Task 12 后，按这个清单 review：

- [ ] **PRD 覆盖**：
  - §5.1 主色 / 文本 / 颜色档位 → 编辑器配色用 colors.xml 命名常量；5 色色板与 PRD 一致（黑/红/黄/绿/蓝）。
  - §6.2 NoteContent JSON 形态 → `Block.TextBlock` / `TextSpan(start, end, type, value)` 完整保留；存读 round-trip 自动化测试覆盖（Task 4 SpanConverterTest）。
  - §7.2 包结构 → `controller/editor/` + `view/block/` + `view/toolbar/` + `util/SpanConverter.kt` 全部就位。
  - §8.2 编辑器交互 → 工具栏 12 键齐全；行内 B/I/U/S 双模式；字号 3 档；颜色 5 色弹窗；H1/H2 块级；末块回车新建；空块退格删除并上移焦点；图片/清单占位 toast。
  - §9 测试策略 → SpanConverter 必有自动化测试（已含 14 条用例）。

- [ ] **不允许的依赖**：grep `import androidx.lifecycle.ViewModel` / `import androidx.compose` / `import androidx.room` / `import androidx.navigation` → 应无任何匹配。

- [ ] **类型一致**：
  - `BlockView.Callback` 三方法（onRequestSplitAfter / onRequestDelete / onFocusGained）在 EditorPresenter 全部实现。
  - `TextToolbarView.Listener` 6 方法在 NoteEditorActivity 全部实现。
  - `EditorPresenter.toggleInline / toggleSize / pickColor / toggleHeading` 与 Activity 调用点签名匹配。
  - `Block.TextBlock(id, heading, text, spans)` 字段全部走构造；不在编辑器中 new 不带 id 的 TextBlock。
  - `Heading.H1 / H2` 与 `dimen/editor_text_h1 / h2 / normal` 对应正确。
  - `SpanType.FONT_SIZE` value 仅在 `"small" / "medium" / "large"` 三选一；`SpanType.COLOR` value 必为 `"#RRGGBB"` 大写六位。

- [ ] **资源命名**：
  - 所有 drawable 用 `ic_*` 或 `shape_*` 前缀。
  - 所有字符串走 `R.string.xxx`，编辑器 Activity 内**无中文字面量**（除 logcat 调试）。
  - 所有色码用 `colors.xml` 命名常量；编辑器代码内**仅** `showColorPickerDialog` 一处硬编码 5 色 hex（与 colors.xml 同步）。

- [ ] **没有半成品**：
  - M5 / M6 占位（图片 / 清单）有明确 toast 提示，不留无响应按钮。
  - `// TODO` 仅允许带 `(M5)` / `(M6)` / `(M7)` 后缀，且与 `2026-05-22-hwnote-implementation.md` 后续里程碑对应；无孤立 TODO。
  - `EditorPresenter.applyInlineToRange` 内"思路注释"行（`val existing = ...` / `val current = ...`）已删除。

- [ ] **commit 边界**：每个任务 1-2 个 commit；commit message 中文、动宾结构（`feat(m4): xxx` / `fix(m4): xxx` / `docs(m4): xxx`）；不带 Co-Authored-By（与 M2/M3 同款风格）。

---

## 预估时长

12 个任务，平均 30-40 分钟一个（Task 4 / 5 / 6 / 8 是大头，约各 1 小时；Task 1 / 7 / 10 / 11 / 12 较快约 20 分钟）：**6-8 小时**（与高层规划估时一致）。

## 后续衔接

完成 M4 后进入 **M5 拍照 / 相册插图**：
- 在 `view/block/` 新增 `ImageBlockView`（继承 `BlockView`）。
- `TextToolbarView` 的 📷 按钮接入实际逻辑：调相机或相册 → 文件落 `notes/<noteId>/img-*.jpg` → 通过 `EditorPresenter` 在当前 TextBlock 后插入 `ImageBlock`。
- 复用 `EditorPresenter.bind` 的 `is Block.ImageBlock -> { ... }` 分支（M4 已留好，M5 实现）。
- `AndroidManifest.xml` 已声明 CAMERA 权限 + FileProvider，M5 接入运行时权限请求即可。
- M5 详细计划在 M4 完成后另写。


