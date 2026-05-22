# HwNote · M3 列表页详细实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 HwNote 列表页（NoteListActivity），实现笔记的展示、搜索、排序、长按菜单、收藏切换、删除二次确认，以及新建占位（FAB 临时直接落库，M4 替换为跳编辑器）。

**Architecture:** XML 布局 + RecyclerView + Adapter；`lifecycleScope.launch` 拉取 `NoteRepository.list`；`SharedPreferences("hwnote_settings")` 持久化排序；`TextWatcher` + `Handler.postDelayed(200ms)` 实现搜索 debounce；`PopupMenu` + `AlertDialog` 处理长按菜单和删除确认。**不引 Compose / ViewModel / LiveData / Room / Hilt / DiffUtil（用 `notifyDataSetChanged`）**。

**Tech Stack:** Kotlin · AppCompat · RecyclerView · Material Components · kotlinx-coroutines · SharedPreferences · 现有 `NoteRepository`。

**测试策略：** M3 是 UI 层，按高层规划"非典型 TDD：UI 层 M3-M7 写完即手测"。**本计划不写自动化测试**；每个任务的验收标准是"在真机/模拟器上跑一次，看到/操作出预期结果"。util 层（`DateUtils` / `TextUtils`）是纯函数，本可单测，但为了节奏一致也并入手测验收（通过卡片渲染的肉眼校验）。

**FAB 占位策略：** M4 之前 `NoteEditorActivity` 不存在。M3 的 FAB 不打开编辑器，而是直接 `NoteRepository.save(Note(id = 0L, ...))` 落一条空笔记再 reload；M4 时把 onClick 五行替换为 `startActivity(NoteEditorActivity.newIntent(this, noteId = -1L))`。这样 M3 能完整地走通"新建 → 列表多一条"的高层验收。

**版本约束（不可改）：** AGP 8.11.2 / Kotlin 2.0.21 / Gradle 8.14.3 / compileSdk 36 / targetSdk 36 / minSdk 24 / JDK 11。RecyclerView/AppCompat/Material 已在 `gradle/libs.versions.toml` 中，本里程碑**不新增依赖**。

---

## 文件结构

**新建：**
- `app/src/main/java/com/fan/hwnote/app/util/DateUtils.kt`
- `app/src/main/java/com/fan/hwnote/app/util/TextUtils.kt`
- `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListAdapter.kt`
- `app/src/main/res/layout/item_note_card.xml`
- `app/src/main/res/drawable/ic_search.xml`
- `app/src/main/res/drawable/ic_sort.xml`
- `app/src/main/res/drawable/ic_star.xml`
- `app/src/main/res/drawable/ic_star_outline.xml`
- `app/src/main/res/drawable/ic_add.xml`
- `app/src/main/res/drawable/ic_delete.xml`
- `app/src/main/res/drawable/ic_empty_note.xml`
- `app/src/main/res/drawable/shape_note_card_bg.xml`
- `app/src/main/res/menu/menu_note_list_toolbar.xml`
- `app/src/main/res/menu/menu_note_card_long_press.xml`

**修改：**
- `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`（M1 占位 → 完整实现）
- `app/src/main/res/layout/activity_note_list.xml`（重构 content 区）
- `app/src/main/res/values/strings.xml`（新增列表页词条）

**不动：** `App.kt`、`AndroidManifest.xml`、`themes.xml`、`colors.xml`、`dimens.xml`、`gradle/libs.versions.toml`、`app/build.gradle.kts`、所有 `model/*` 与测试代码。

---

## 通用执行约定

每个任务都按 4 步走：

1. **实现**：按本任务列出的代码改动 Create / Modify / Delete 文件。
2. **构建**：`./gradlew :app:assembleDebug`，预期 BUILD SUCCESSFUL；如失败先解决再继续。
3. **手测**：装到真机/模拟器（`./gradlew :app:installDebug`），按本任务"手测验收"小节操作并观察预期。
4. **提交**：按本任务给出的 commit message 生成提交。

**Gradle 运行环境（每个新 shell 都要 export）：**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaWeiNote
```

**装包仅 UI 改动有视觉影响的任务必做**（Task 2/3/5/6/7/8/9/10/11/12 必装；Task 1/4 仅构建即可，等下一任务一起验证）。

---

## Task 1: 资源准备 — drawables + menus + shape + strings

**Files:**
- Create: `app/src/main/res/drawable/ic_search.xml`
- Create: `app/src/main/res/drawable/ic_sort.xml`
- Create: `app/src/main/res/drawable/ic_star.xml`
- Create: `app/src/main/res/drawable/ic_star_outline.xml`
- Create: `app/src/main/res/drawable/ic_add.xml`
- Create: `app/src/main/res/drawable/ic_delete.xml`
- Create: `app/src/main/res/drawable/ic_empty_note.xml`
- Create: `app/src/main/res/drawable/shape_note_card_bg.xml`
- Create: `app/src/main/res/menu/menu_note_list_toolbar.xml`
- Create: `app/src/main/res/menu/menu_note_card_long_press.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 创建 7 个 vector drawable（Material Icons 路径）**

`ic_search.xml`：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?android:attr/textColorPrimary">
    <path android:fillColor="@android:color/white"
        android:pathData="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"/>
</vector>
```

`ic_sort.xml`：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?android:attr/textColorPrimary">
    <path android:fillColor="@android:color/white"
        android:pathData="M3,18h6v-2L3,16v2zM3,6v2h18L21,6L3,6zM3,13h12v-2L3,11v2z"/>
</vector>
```

`ic_star.xml`（实心，星色）：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="20dp" android:height="20dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/accent_star">
    <path android:fillColor="@android:color/white"
        android:pathData="M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z"/>
</vector>
```

`ic_star_outline.xml`（描边）：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="20dp" android:height="20dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_hint">
    <path android:fillColor="@android:color/white"
        android:pathData="M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z"/>
</vector>
```

`ic_add.xml`：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/white">
    <path android:fillColor="@android:color/white"
        android:pathData="M19,13h-6v6h-2v-6L5,13v-2h6L11,5h2v6h6v2z"/>
</vector>
```

`ic_delete.xml`：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/error">
    <path android:fillColor="@android:color/white"
        android:pathData="M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2L18,7L6,7v12zM19,4h-3.5l-1,-1h-5l-1,1L5,4v2h14L19,4z"/>
</vector>
```

`ic_empty_note.xml`（空状态插图，简笔便签图标，60dp）：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="60dp" android:height="60dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_hint">
    <path android:fillColor="@android:color/white"
        android:pathData="M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z"/>
</vector>
```

- [ ] **Step 2: 创建 `shape_note_card_bg.xml`（圆角白底，给 MaterialCardView 兜底用，本任务先建出来）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/bg_card" />
    <corners android:radius="@dimen/radius_card" />
</shape>
```

- [ ] **Step 3: 创建 `menu/menu_note_list_toolbar.xml`（顶部工具栏排序按钮）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <item
        android:id="@+id/action_sort"
        android:icon="@drawable/ic_sort"
        android:title="@string/action_sort"
        app:showAsAction="ifRoom" />
</menu>
```

- [ ] **Step 4: 创建 `menu/menu_note_card_long_press.xml`（长按弹菜单）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/action_toggle_favorite"
        android:title="@string/action_toggle_favorite" />
    <item
        android:id="@+id/action_delete"
        android:title="@string/action_delete" />
</menu>
```

- [ ] **Step 5: 修改 `strings.xml`，新增列表页词条**

把现有 `strings.xml` 整个替换为：

```xml
<resources>
    <string name="app_name">备忘录</string>

    <!-- 列表页 -->
    <string name="list_search_hint">搜索笔记</string>
    <string name="list_empty_title">还没有笔记</string>
    <string name="list_empty_subtitle">点击右下角 + 新建一条</string>
    <string name="fab_new_note_cd">新建笔记</string>
    <string name="card_favorite_cd">收藏标记</string>

    <!-- 排序选项 -->
    <string name="action_sort">排序</string>
    <string name="sort_updated_desc">按修改时间</string>
    <string name="sort_created_desc">按创建时间</string>
    <string name="sort_title_asc">按标题 A-Z</string>

    <!-- 长按菜单 -->
    <string name="action_toggle_favorite">收藏 / 取消收藏</string>
    <string name="action_delete">删除</string>

    <!-- 删除二次确认 -->
    <string name="dialog_delete_title">删除笔记</string>
    <string name="dialog_delete_message">确定删除？此操作不可恢复</string>

    <!-- 通用 -->
    <string name="action_ok">确定</string>
    <string name="action_cancel">取消</string>

    <!-- 占位 toast -->
    <string name="toast_open_editor_placeholder">打开编辑器（M4 实现）</string>
    <string name="untitled_note">无标题</string>
</resources>
```

- [ ] **Step 6: 构建**

```bash
./gradlew :app:assembleDebug
```

预期：`BUILD SUCCESSFUL`，无 lint 错误。

- [ ] **Step 7: 手测验收**

无 UI 变化（资源还没接到布局），仅校验构建。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/res/drawable/ic_*.xml \
        app/src/main/res/drawable/shape_note_card_bg.xml \
        app/src/main/res/menu/menu_note_list_toolbar.xml \
        app/src/main/res/menu/menu_note_card_long_press.xml \
        app/src/main/res/values/strings.xml
git commit -m "feat(m3): 添加列表页所需 drawable / menu / strings 资源"
```

---

## Task 2: 重构 `activity_note_list.xml` — Toolbar + 搜索框 + RecyclerView + 空状态 + FAB

**Files:**
- Modify: `app/src/main/res/layout/activity_note_list.xml`

- [ ] **Step 1: 整文件替换**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_window">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/primary"
        android:theme="@style/ThemeOverlay.MaterialComponents.Dark.ActionBar">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="@color/primary"
            app:title="@string/app_name"
            app:titleTextColor="@color/white" />

        <!-- 搜索框：常驻在 Toolbar 下方，不放进 ActionBar -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@color/primary"
            android:orientation="horizontal"
            android:paddingHorizontal="@dimen/spacing_l"
            android:paddingBottom="@dimen/spacing_m">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="40dp"
                android:background="@drawable/shape_note_card_bg"
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
        </LinearLayout>

    </com.google.android.material.appbar.AppBarLayout>

    <FrameLayout
        android:id="@+id/content_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

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
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
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

    </FrameLayout>

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

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

注意：`empty_state` 默认 `visibility="gone"`（避免空数据态闪烁），由代码控制显隐；`R.id.empty_text`（M1 旧 id）已删除，下一任务的 Activity 代码不再引用。

- [ ] **Step 2: 构建**

```bash
./gradlew :app:assembleDebug
```

会因为 `NoteListActivity.kt` 还在引用旧的 `R.id.empty_text` 失败 → 临时修一下：把 `NoteListActivity.kt` 里 `findViewById<TextView>(R.id.empty_text)` 那行删除（没有就跳过）。再次构建至 `BUILD SUCCESSFUL`。

> 说明：M1 的 NoteListActivity 里只引用了 `R.id.fab_new_note`，无 `empty_text`，所以这一步通常不需要动 Kt。

- [ ] **Step 3: 装包并手测**

```bash
./gradlew :app:installDebug
```

手测验收：
- 打开"备忘录"app，看到顶部绿色 Toolbar，标题"备忘录"。
- Toolbar 下方有圆角白底搜索框，左侧 search 图标，hint "搜索笔记"。
- 点击搜索框 → 软键盘弹出，可输入字符（暂无过滤效果）。
- 主区域空白（没有列表，也没有空状态—因为 visibility=gone，下个任务会接逻辑）。
- 右下角 FAB 显示 + 图标，点击 → Toast "新建笔记（待实现）"（M1 占位行为还在）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/activity_note_list.xml
git commit -m "feat(m3): 重构列表页布局，加入常驻搜索框、RecyclerView、空状态容器"
```

---

## Task 3: `item_note_card.xml` — 单条卡片布局

**Files:**
- Create: `app/src/main/res/layout/item_note_card.xml`

- [ ] **Step 1: 创建 item_note_card.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/card_root"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginVertical="@dimen/spacing_xs"
    android:layout_marginHorizontal="@dimen/spacing_xs"
    app:cardBackgroundColor="@color/bg_card"
    app:cardCornerRadius="@dimen/radius_card"
    app:cardElevation="2dp"
    app:rippleColor="@color/primary_light">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="@dimen/spacing_m">

        <!-- 第一行：标题 + 星标 + 时间 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/card_title"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="@color/text_primary"
                android:textSize="@dimen/text_body"
                android:textStyle="bold"
                tools:text="今天会议要点" />

            <ImageView
                android:id="@+id/card_star"
                android:layout_width="20dp"
                android:layout_height="20dp"
                android:layout_marginStart="@dimen/spacing_s"
                android:contentDescription="@string/card_favorite_cd"
                android:src="@drawable/ic_star_outline" />

            <TextView
                android:id="@+id/card_time"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/spacing_s"
                android:textColor="@color/text_hint"
                android:textSize="@dimen/text_hint"
                tools:text="今天 14:30" />
        </LinearLayout>

        <!-- 第二行：摘要 -->
        <TextView
            android:id="@+id/card_summary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/spacing_xs"
            android:ellipsize="end"
            android:maxLines="2"
            android:textColor="@color/text_secondary"
            android:textSize="@dimen/text_caption"
            tools:text="项目排期需要重新审视，李雷负责…" />
    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

注意根标签需要加 `xmlns:tools="http://schemas.android.com/tools"`，但 `tools:text` 仅用于预览不会影响运行。如要严格 lint 干净，把 `xmlns:tools` 加进根 attribute；如不在意也可去掉所有 `tools:text="..."`。**推荐保留并加上 namespace**：把第一行替换为：

```xml
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
```

- [ ] **Step 2: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 `BUILD SUCCESSFUL`。

- [ ] **Step 3: 手测验收**

在 Android Studio Layout Editor 里打开 `item_note_card.xml`，预览显示一张卡片：标题"今天会议要点"加粗，右上灰色描边星，再右是"今天 14:30"，下面两行灰色摘要。无运行时验证（Adapter 还没接）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/item_note_card.xml
git commit -m "feat(m3): 添加单条笔记卡片布局 item_note_card.xml"
```

---

## Task 4: util — `DateUtils.kt` + `TextUtils.kt`

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/util/DateUtils.kt`
- Create: `app/src/main/java/com/fan/hwnote/app/util/TextUtils.kt`

- [ ] **Step 1: 创建 `DateUtils.kt`**

```kotlin
package com.fan.hwnote.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val weekdayTimeFmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    /**
     * 列表卡片右上角时间显示规则：
     * - 同一天：HH:mm（如 14:30）
     * - 昨天：昨天 HH:mm
     * - 7 天内：周X HH:mm
     * - 更早：yyyy/MM/dd
     */
    fun formatRelative(timeMs: Long, now: Long = System.currentTimeMillis()): String {
        val target = Calendar.getInstance().apply { timeInMillis = timeMs }
        val current = Calendar.getInstance().apply { timeInMillis = now }
        val sameDay = target.get(Calendar.YEAR) == current.get(Calendar.YEAR)
            && target.get(Calendar.DAY_OF_YEAR) == current.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return timeFmt.format(Date(timeMs))

        // 昨天判定：把 current 减 1 天看 day_of_year
        val yesterday = (current.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
            && target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) return "昨天 " + timeFmt.format(Date(timeMs))

        val diffDays = (now - timeMs) / (24L * 60 * 60 * 1000)
        if (diffDays in 0..6) return weekdayTimeFmt.format(Date(timeMs))

        return dateFmt.format(Date(timeMs))
    }
}
```

- [ ] **Step 2: 创建 `TextUtils.kt`**

```kotlin
package com.fan.hwnote.app.util

object TextUtils {

    /**
     * 从 plain_text 生成卡片摘要：
     * - 把所有换行 \n 替换为空格
     * - 折叠连续空白
     * - 截断到 maxLen
     */
    fun summary(plainText: String, maxLen: Int = 60): String {
        val flattened = plainText.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        return if (flattened.length <= maxLen) flattened else flattened.take(maxLen)
    }

    /** 标题为空时显示"无标题"占位（文案在 Activity 内部决定，本函数只判空）。*/
    fun isBlankTitle(title: String): Boolean = title.trim().isEmpty()
}
```

- [ ] **Step 3: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 `BUILD SUCCESSFUL`。

- [ ] **Step 4: 手测验收**

无 UI 变化，等 Task 5/6 通过 Adapter 渲染时一并验证。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/util/DateUtils.kt \
        app/src/main/java/com/fan/hwnote/app/util/TextUtils.kt
git commit -m "feat(m3): 添加 DateUtils（相对时间）和 TextUtils（摘要截断）工具类"
```

---

## Task 5: `NoteListAdapter.kt` — RecyclerView Adapter（含 click / long-click 回调）

**Files:**
- Create: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListAdapter.kt`

- [ ] **Step 1: 创建 Adapter**

```kotlin
package com.fan.hwnote.app.controller.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Note
import com.fan.hwnote.app.util.DateUtils
import com.fan.hwnote.app.util.TextUtils

class NoteListAdapter(
    private val onClick: (Note) -> Unit,
    private val onLongClick: (Note, View) -> Unit,
) : RecyclerView.Adapter<NoteListAdapter.VH>() {

    private val items = mutableListOf<Note>()

    fun submit(list: List<Note>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.card_title)
        private val star: ImageView = itemView.findViewById(R.id.card_star)
        private val time: TextView = itemView.findViewById(R.id.card_time)
        private val summary: TextView = itemView.findViewById(R.id.card_summary)

        fun bind(note: Note) {
            title.text = if (TextUtils.isBlankTitle(note.title))
                itemView.context.getString(R.string.untitled_note)
            else note.title
            star.setImageResource(
                if (note.isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline
            )
            time.text = DateUtils.formatRelative(note.updatedAt)
            summary.text = TextUtils.summary(note.plainText)
            summary.visibility = if (summary.text.isNullOrEmpty()) View.GONE else View.VISIBLE

            itemView.setOnClickListener { onClick(note) }
            itemView.setOnLongClickListener {
                onLongClick(note, itemView)
                true
            }
        }
    }
}
```

- [ ] **Step 2: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 `BUILD SUCCESSFUL`。

- [ ] **Step 3: 手测验收**

无 UI 变化（Activity 还没接 Adapter）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListAdapter.kt
git commit -m "feat(m3): 添加 NoteListAdapter（卡片渲染 + 单击/长按回调）"
```

---

## Task 6: `NoteListActivity` 数据加载与渲染（onCreate / onResume / 空状态切换 / 卡片点击 Toast 占位）

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

- [ ] **Step 1: 整文件替换**

```kotlin
package com.fan.hwnote.app.controller.list

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.NoteRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class NoteListActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var searchInput: EditText
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: NoteListAdapter

    private var sortBy: NoteRepository.SortBy = NoteRepository.SortBy.UPDATED_DESC
    private var currentQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        toolbar = findViewById(R.id.toolbar)
        searchInput = findViewById(R.id.search_input)
        recycler = findViewById(R.id.recycler_notes)
        emptyState = findViewById(R.id.empty_state)
        fab = findViewById(R.id.fab_new_note)

        setSupportActionBar(toolbar)

        adapter = NoteListAdapter(
            onClick = { note ->
                Toast.makeText(
                    this, R.string.toast_open_editor_placeholder, Toast.LENGTH_SHORT
                ).show()
                // M4：替换为 startActivity(NoteEditorActivity.newIntent(this, note.id))
            },
            onLongClick = { _, _ -> /* Task 10 接 */ },
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fab.setOnClickListener {
            // M4：替换为 startActivity(NoteEditorActivity.newIntent(this, noteId = -1L))
            Toast.makeText(this, R.string.toast_new_note_placeholder, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val list = NoteRepository.list(sortBy, currentQuery)
            adapter.submit(list)
            renderEmpty(list.isEmpty())
        }
    }

    private fun renderEmpty(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }
}
```

注意：`R.string.toast_new_note_placeholder` 在 M1 strings 里有，Task 1 替换 strings.xml 时已**移除**。需要回填一句到 strings.xml：在 Task 1 的 strings.xml 末尾"占位 toast"那段加：

```xml
<string name="toast_new_note_placeholder">新建笔记（待 Task 7 实现）</string>
```

> 自查：Task 1 的 strings.xml 是否包含 `toast_new_note_placeholder`？回看 Task 1 step 5 — 当前未包含，只有 `toast_open_editor_placeholder`。**改 Task 6 step 1：把 `R.string.toast_new_note_placeholder` 改成 `R.string.toast_open_editor_placeholder`**（占位字符串先复用 open_editor 那条；Task 7 会把这五行换成 save Note 逻辑，本来也不留 toast）。

修正后的 fab.setOnClickListener：

```kotlin
fab.setOnClickListener {
    // M3 Task 7 会替换为 save 空 Note；M4 再替换为跳编辑器
    Toast.makeText(this, R.string.toast_open_editor_placeholder, Toast.LENGTH_SHORT).show()
}
```

- [ ] **Step 2: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 `BUILD SUCCESSFUL`。

- [ ] **Step 3: 装包并手测**

```bash
./gradlew :app:installDebug
```

手测验收：
- 启动 app → 看到空状态：60dp 便签插图 + "还没有笔记" + "点击右下角 + 新建一条"。
- 不点 FAB（FAB 暂时只 Toast）。
- 用 adb shell 注入测试数据来验列表（可选，下个任务 FAB 落库后会自动有数据；本任务也可跳过此步）。

> 想提前看到列表渲染：在 Android Studio Database Inspector 直接给 `notes` 表 INSERT 几行（id 留空，title="测试1"，plain_text="abc"，content_json='{"blocks":[],"handwriting":{"strokes":[]}}'，is_favorite=0，created_at/updated_at=now ms）。回到 app `onResume` 触发 reload，应看到卡片。验证完删掉测试行。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m3): NoteListActivity 接入 RecyclerView + 数据加载 + 空状态切换"
```

---

## Task 7: FAB 新建占位（直接落空 Note + reload）

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

- [ ] **Step 1: 替换 fab.setOnClickListener 段**

把 Task 6 里的 fab.setOnClickListener 整体替换为：

```kotlin
fab.setOnClickListener {
    // M3 占位：直接落一条空笔记，验证列表→数据→刷新 闭环
    // M4：替换为 startActivity(NoteEditorActivity.newIntent(this, noteId = -1L))
    lifecycleScope.launch {
        val now = System.currentTimeMillis()
        NoteRepository.save(
            com.fan.hwnote.app.model.entity.Note(
                id = 0L,
                title = "",
                plainText = "",
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
                content = com.fan.hwnote.app.model.entity.NoteContent.empty(),
            )
        )
        reload()
    }
}
```

> 注意：要确认 `NoteContent` 有无参数 `empty()` 静态方法。如没有，改写为 `NoteContent(blocks = emptyList(), handwriting = Handwriting(strokes = emptyList()))` 或类似的现有签名。**实现前先 Read 一下 `model/entity/NoteContent.kt` 与 `model/entity/Note.kt`**，按真实构造器签名调整。

- [ ] **Step 2: 构建**

```bash
./gradlew :app:assembleDebug
```

如类型对不上 → 修正构造器调用，再构建至 `BUILD SUCCESSFUL`。

- [ ] **Step 3: 装包并手测**

```bash
./gradlew :app:installDebug
```

手测验收：
- 启动 app → 空状态。
- 点 FAB → 列表上立刻多一条卡片，标题"无标题"，时间为当前 HH:mm，星标描边，摘要为空（应隐藏）。
- 再点 FAB → 第二条；按 updated_at DESC 默认排序，最新的在最上。
- 杀进程重启 → 列表里两条都还在（数据库持久化）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m3): FAB 占位实现（直接 save 空 Note；M4 替换为跳编辑器）"
```

---

## Task 8: 搜索 debounce（200ms）

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

- [ ] **Step 1: 在 Activity 增加 Handler + TextWatcher**

在 `class NoteListActivity` 内（顶部 properties 区）加：

```kotlin
private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
private var searchRunnable: Runnable? = null
```

在 `onCreate` 末尾（fab.setOnClickListener 之后）加：

```kotlin
searchInput.addTextChangedListener(object : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        val text = s?.toString()?.trim().orEmpty()
        searchRunnable = Runnable {
            currentQuery = text.ifEmpty { null }
            reload()
        }
        searchHandler.postDelayed(searchRunnable!!, 200L)
    }
})
```

`onDestroy` 里清理：

```kotlin
override fun onDestroy() {
    searchRunnable?.let { searchHandler.removeCallbacks(it) }
    super.onDestroy()
}
```

- [ ] **Step 2: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 `BUILD SUCCESSFUL`。

- [ ] **Step 3: 装包并手测**

```bash
./gradlew :app:installDebug
```

手测验收：
- 准备数据：先点 FAB 几次新建几条（标题都是"无标题"）；用 DB Inspector 把其中一条 `title` 改为 "测试笔记"，`plain_text` 改为 "包含关键字 abc"。重启列表。
- 在搜索框输入"测试" → 200ms 后列表只剩"测试笔记"，其它卡片消失。
- 清空搜索框 → 200ms 后所有卡片回来。
- 快速连击输入"a","b","c"（<200ms 之间） → 只触发一次 reload（可在 logcat 加临时 Log.d 观察；或观察 UI 不闪烁也算 OK）。
- 输入不存在的"xyz123" → 列表清空，**显示空状态**（reload 调用 renderEmpty(true)）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m3): 搜索框 200ms debounce 过滤"
```

---

## Task 9: 排序持久化 + 切换菜单

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

- [ ] **Step 1: 加 SharedPreferences 读写 + Toolbar 排序按钮**

在 `NoteListActivity` 加常量与方法：

```kotlin
companion object {
    private const val PREFS = "hwnote_settings"
    private const val KEY_SORT = "sort_by"
}

private fun loadSort(): NoteRepository.SortBy {
    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
    val name = prefs.getString(KEY_SORT, NoteRepository.SortBy.UPDATED_DESC.name)
    return runCatching { NoteRepository.SortBy.valueOf(name!!) }
        .getOrDefault(NoteRepository.SortBy.UPDATED_DESC)
}

private fun saveSort(sortBy: NoteRepository.SortBy) {
    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
        .putString(KEY_SORT, sortBy.name).apply()
}
```

在 `onCreate` 里 `recycler.adapter = adapter` 之后、`fab.setOnClickListener` 之前加：

```kotlin
sortBy = loadSort()
```

加 menu 处理：

```kotlin
override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_note_list_toolbar, menu)
    return true
}

override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
    if (item.itemId == R.id.action_sort) {
        showSortDialog()
        return true
    }
    return super.onOptionsItemSelected(item)
}

private fun showSortDialog() {
    val labels = arrayOf(
        getString(R.string.sort_updated_desc),
        getString(R.string.sort_created_desc),
        getString(R.string.sort_title_asc),
    )
    val values = arrayOf(
        NoteRepository.SortBy.UPDATED_DESC,
        NoteRepository.SortBy.CREATED_DESC,
        NoteRepository.SortBy.TITLE_ASC,
    )
    val checked = values.indexOf(sortBy)
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(R.string.action_sort)
        .setSingleChoiceItems(labels, checked) { d, which ->
            sortBy = values[which]
            saveSort(sortBy)
            reload()
            d.dismiss()
        }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}
```

- [ ] **Step 2: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 `BUILD SUCCESSFUL`。

- [ ] **Step 3: 装包并手测**

```bash
./gradlew :app:installDebug
```

准备数据：手工构造 3 条标题不同、created_at 顺序明显与 updated_at 不同的笔记（可借 DB Inspector 直接调字段）。

手测验收：
- 启动 → 默认按修改时间倒序，新改的在上。
- 点 Toolbar 右上角排序图标 → 弹出对话框 [按修改时间 / 按创建时间 / 按标题 A-Z]，当前选项是修改时间。
- 切到"按创建时间" → 列表顺序变化（创建早的在下）。
- 切到"按标题 A-Z" → 顺序按标题字母升序（"无标题" 用空字符比较，会排到顶或底，看 SQL `COLLATE NOCASE`）。
- 杀进程重启 → 仍然是上次选的"按标题 A-Z"（持久化生效）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m3): 排序对话框 + SharedPreferences 持久化"
```

---

## Task 10: 长按菜单 + 收藏切换

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

- [ ] **Step 1: 实装 onLongClick → PopupMenu**

把 Task 6 里 Adapter 构造的 `onLongClick = { _, _ -> /* Task 10 接 */ }` 替换为：

```kotlin
onLongClick = { note, anchor -> showCardMenu(note, anchor) },
```

加方法：

```kotlin
private fun showCardMenu(note: com.fan.hwnote.app.model.entity.Note, anchor: View) {
    val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
    popup.menuInflater.inflate(R.menu.menu_note_card_long_press, popup.menu)
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
                // Task 11 接二次确认；本任务先 Toast 占位
                Toast.makeText(this, "删除（Task 11 实现）", Toast.LENGTH_SHORT).show()
                true
            }
            else -> false
        }
    }
    popup.show()
}
```

- [ ] **Step 2: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 `BUILD SUCCESSFUL`。

- [ ] **Step 3: 装包并手测**

```bash
./gradlew :app:installDebug
```

手测验收：
- 任意一条卡片长按 → 弹出菜单 [收藏 / 取消收藏，删除]。
- 点"收藏 / 取消收藏" → 卡片右上星标变实心黄色，再次长按 + 同操作 → 变回描边灰色。
- 点"删除" → Toast "删除（Task 11 实现）"，列表无变化。
- 长按其它卡片 → 菜单锚点在该卡片上方，互不干扰。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m3): 长按弹 PopupMenu + 收藏切换（删除占位）"
```

---

## Task 11: 删除二次确认 + 真删

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

- [ ] **Step 1: 替换 Task 10 的删除占位**

把 `R.id.action_delete` 分支改成：

```kotlin
R.id.action_delete -> {
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(R.string.dialog_delete_title)
        .setMessage(R.string.dialog_delete_message)
        .setPositiveButton(R.string.action_ok) { _, _ ->
            lifecycleScope.launch {
                NoteRepository.delete(note.id)
                reload()
            }
        }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
    true
}
```

- [ ] **Step 2: 构建**

```bash
./gradlew :app:assembleDebug
```

预期 `BUILD SUCCESSFUL`。

- [ ] **Step 3: 装包并手测**

```bash
./gradlew :app:installDebug
```

手测验收：
- 长按卡片 → 删除 → 弹出 AlertDialog "删除笔记 / 确定删除？此操作不可恢复 / 确定 / 取消"。
- 点"取消" → dialog 消失，列表不变。
- 再次长按 → 删除 → 点"确定" → 该卡片立即从列表移除，杀进程重启不会再回来。
- 删除最后一条 → 列表回到空状态。
- 删除当前选中排序为 TITLE_ASC 时也 OK（reload 用当前 sortBy）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt
git commit -m "feat(m3): 删除二次确认 AlertDialog + 真删 + 列表刷新"
```

---

## Task 12: 真机走查 + 收尾文档

**Files:**
- Modify: `docs/superpowers/STATUS.md`（标记 M3 完成）
- 可能 Modify: 上述任意小问题修复

- [ ] **Step 1: 真机/模拟器走 5 条高层验收**

按高层规划 `2026-05-22-hwnote-implementation.md` 第 224-230 行：

1. **新建一条笔记** → 点 FAB → 列表多一条"无标题"卡片，时间是当前。✅
2. **搜索"测试"** → 列表实时过滤；清空搜索 → 全量恢复。✅
3. **排序切换 3 种** → 顺序明显变化；杀进程重启后保留上次选项。✅
4. **长按一条** → 菜单 [收藏 / 删除]；点收藏 → 出现 ⭐ 角标；点删除 → 二次确认 → 列表移除。✅
5. **列表为空时显示空状态** → 删除所有笔记后看到便签插图 + 文案。✅

附加冒烟：
- **横屏旋转**：旋转设备，列表保留显示（Activity 重建走 onResume → reload）。
- **Toolbar 标题**：始终是"备忘录"。
- **搜索 + 排序组合**：先搜索"测试" → 切换排序 → 仍然过滤 + 新顺序。
- **搜索后退出 app**：杀进程重启，搜索框是空的（不持久化搜索词），列表全量；排序持久化保留。
- **debounce 边界**：连续输入快速字符，只在最后一次停顿 200ms 后触发；中途不闪烁。

- [ ] **Step 2: 修复走查发现的小问题**

如卡片间距过密 / 标题居中错位 / 时间字段太挤 / 星标位置异常 / 空状态偏左等。原则：仅 layout 微调，不改业务逻辑。每个微调单独 commit。

- [ ] **Step 3: 更新 STATUS.md**

把 `docs/superpowers/STATUS.md` 里 M3 状态从 "进行中 / 待开始" 改为 "已完成 (YYYY-MM-DD)"，写一行 commit 概览（HEAD 哈希、任务数）。

- [ ] **Step 4: 最终提交**

```bash
git add docs/superpowers/STATUS.md
git commit -m "docs(m3): 标记 M3 列表页完成"
```

---

## 自查清单

写完代码并完成 Task 12 后，按这个清单 review：

- [ ] **PRD 覆盖**：
  - §5.1 主色 / 文本 / 背景色 → 用了 colors.xml 的命名常量，**未硬编码 #00897B 等十六进制**。
  - §5.2 卡片 → 圆角 10dp（用 `radius_card`），白底，elevation 2dp，标题 14sp bold，时间 11sp 灰，摘要 12sp 灰 maxLines=2 ellipsize END。
  - §7.2 包结构 → `controller/list/NoteListActivity.kt` + `NoteListAdapter.kt`，`util/DateUtils.kt` + `TextUtils.kt` 全部就位。
  - §8.1 列表交互 → 顶部搜索框常驻 + 200ms debounce + 排序持久化 + 收藏不分组置顶仅显星 + 长按菜单 + AlertDialog 二次确认 + 空状态。

- [ ] **不允许的依赖**：grep `import com.airbnb.epoxy` / `import androidx.lifecycle.ViewModel` / `import androidx.compose` / `import androidx.room` → 应无任何匹配。

- [ ] **类型一致**：
  - `NoteListAdapter.onClick: (Note) -> Unit`、`onLongClick: (Note, View) -> Unit` 与 Activity 调用点签名匹配。
  - `NoteRepository.SortBy` 三枚举与 dialog 三选项一一对应。
  - `NoteContent.empty()`（或等效构造）真实存在；如不存在请按 Note 实际构造器写。

- [ ] **资源命名**：
  - 所有 drawable 用 `ic_*` 或 `shape_*` 前缀。
  - menu 用 `menu_*` 前缀。
  - 所有字符串走 `R.string.xxx`，**Activity 里无中文字面量**（除非 logcat 调试）。

- [ ] **没有半成品**：
  - 所有 Toast 占位都被覆盖（仅 onClick 卡片那一处保留 `toast_open_editor_placeholder`，注释里写明 M4 替换）。
  - FAB onClick 注释明确"M4 替换为 startActivity(NoteEditorActivity...)"。
  - 没有 `// TODO` 不带后续任务编号的孤立 TODO。

- [ ] **commit 边界**：每个任务一个或多个 commit；commit message 中文、动宾结构（"feat(m3): xxx"）、不带 Co-Authored-By（按用户偏好或 M2 同款风格）。

---

## 预估时长

12 个任务，平均 15-20 分钟一个，含构建装包手测：**3-4 小时**（与高层规划估时一致）。

## 后续衔接

完成 M3 后进入 **M4 编辑器骨架**：
- 创建 `NoteEditorActivity` + `EditorPresenter` + 标题 EditText + 文本块 LinearLayout 容器。
- 把 M3 留下的两个 Toast 占位（卡片点击、FAB）替换为 `startActivity(NoteEditorActivity.newIntent(this, noteId))`。
- M4 详细计划在执行 M3 完成后另写。
