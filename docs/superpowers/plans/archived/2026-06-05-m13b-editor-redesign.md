> **Status: COMPLETED**

# M13b 编辑器重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将编辑器从"始终编辑态 + 蓝色 Material Toolbar"切换到"白底双模式"：浏览态（只读 + 底部动作栏）⇄ 编辑态（可编辑 + 底部工具栏 + 顶栏 ↩↪✓）。

**Architecture:** Activity 内用 `var isEditing: Boolean` 驱动模式切换。去掉 AppBarLayout/Toolbar，换成白底 LinearLayout 顶栏。底部三套栏（TextToolbar / HandwritingToolbar / BrowseActionBar）互斥切换。EditorPresenter 新增 `setReadOnly(Boolean)` 控制各 BlockView 可编辑性。笔记本指示器从 AppBar 移入 metadata_strip。

**Tech Stack:** Android XML layout + Kotlin + Material Components（BottomSheetDialog / PopupMenu / DeleteConfirmBottomSheet 已有）

**Spec:** `docs/superpowers/specs/2026-06-05-m13b-editor-redesign.md`

---

## File Structure

| 文件 | 职责 | 操作 |
|------|------|------|
| `res/values/strings.xml` | 新增编辑器动作栏字符串 | 修改 |
| `res/drawable/ic_share.xml` | 分享图标 | 新建 |
| `res/drawable/ic_done.xml` | ✓ 完成图标 | 新建 |
| `res/drawable/ic_close_circle.xml` | 图片块 ✕ 删除覆盖按钮 | 新建 |
| `res/menu/menu_editor_browse_more.xml` | 浏览态"更多"PopupMenu | 新建 |
| `res/layout/toolbar_text.xml` | 5 按钮改图标+文字 | 重写 |
| `res/layout/bar_browse_action.xml` | 浏览态底部动作栏 | 新建 |
| `res/layout/block_image.xml` | merge → FrameLayout + ✕ 覆盖按钮 | 重写 |
| `view/toolbar/TextToolbarView.kt` | 适配新 toolbar_text 布局（View 替代 ImageView） | 修改 |
| `view/block/TextBlockView.kt` | 新增 `setEditable(Boolean)` | 修改 |
| `view/block/ImageBlockView.kt` | 新增 `btnDelete` + `setDeleteVisible(Boolean)` | 修改 |
| `view/block/ChecklistBlockView.kt` | 新增 `setEditable(Boolean)` | 修改 |
| `view/block/ChecklistItemView.kt` | 新增 `setEditable(Boolean)` | 修改 |
| `view/block/AudioBlockView.kt` | 新增 `setDeleteEnabled(Boolean)` | 修改 |
| `controller/editor/EditorPresenter.kt` | 新增 `setReadOnly(Boolean)` | 修改 |
| `res/layout/activity_note_editor.xml` | 去 AppBarLayout，换白底顶栏 + 三套底栏 | 重写 |
| `controller/editor/NoteEditorActivity.kt` | 双模式逻辑 + 动作栏 + 去 Toolbar | 大改 |
| `res/menu/menu_editor.xml` | 删除（undo/redo 迁到顶栏 ImageView） | 删除 |

---

## Tasks

### Task 1: 新增字符串 + Drawables

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_share.xml`
- Create: `app/src/main/res/drawable/ic_done.xml`
- Create: `app/src/main/res/drawable/ic_close_circle.xml`

- [ ] **Step 1: strings.xml 新增 M13b 编辑器字符串**

在 `<!-- M13a 列表页重设计 -->` 之后新增一节：

```xml
    <!-- M13b 编辑器重设计 -->
    <string name="editor_done_cd">完成</string>
    <string name="editor_share">分享</string>
    <string name="editor_favorite">收藏</string>
    <string name="editor_unfavorite">取消收藏</string>
    <string name="editor_delete">删除</string>
    <string name="editor_more">更多</string>
    <string name="editor_move_notebook">移动到笔记本</string>
    <string name="editor_set_category">设置分类</string>
    <string name="toast_share_placeholder">分享功能（M13e 实现）</string>
    <string name="editor_close_image_cd">删除图片</string>
    <string name="tb_checklist_label">清单</string>
    <string name="tb_style_label">样式</string>
    <string name="tb_image_label">图片</string>
    <string name="tb_record_label">语音</string>
    <string name="tb_handwriting_label">手写</string>
```

- [ ] **Step 2: 创建 ic_share.xml**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"/>
</vector>
```

- [ ] **Step 3: 创建 ic_done.xml**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M9,16.2L4.8,12l-1.4,1.4L9,19 21,7l-1.4,-1.4z"/>
</vector>
```

- [ ] **Step 4: 创建 ic_close_circle.xml（图片块 ✕ 覆盖按钮）**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#80000000"
        android:pathData="M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2z"/>
    <path
        android:fillColor="@android:color/white"
        android:pathData="M17,15.59L15.59,17 12,13.41 8.41,17 7,15.59 10.59,12 7,8.41 8.41,7 12,10.59 15.59,7 17,8.41 13.41,12z"/>
</vector>
```

- [ ] **Step 5: 构建验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/drawable/ic_share.xml app/src/main/res/drawable/ic_done.xml app/src/main/res/drawable/ic_close_circle.xml
git commit -m "feat(m13b): 加编辑器重设计资源（strings + drawables）"
```

---

### Task 2: 浏览态"更多"菜单资源

**Files:**
- Create: `app/src/main/res/menu/menu_editor_browse_more.xml`

- [ ] **Step 1: 创建菜单资源**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/action_move_notebook"
        android:title="@string/editor_move_notebook" />
    <item
        android:id="@+id/action_set_category"
        android:title="@string/editor_set_category" />
</menu>
```

- [ ] **Step 2: 构建验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/res/menu/menu_editor_browse_more.xml
git commit -m "feat(m13b): 加浏览态更多 PopupMenu 资源"
```

---

### Task 3: TextToolbarView 图标+文字改造

**Files:**
- Rewrite: `app/src/main/res/layout/toolbar_text.xml`
- Modify: `app/src/main/java/com/fan/hwnote/app/view/toolbar/TextToolbarView.kt`

- [ ] **Step 1: 重写 toolbar_text.xml — 5 个按钮改为图标+文字竖排**

完整新内容（替换整个文件）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center_vertical"
    android:orientation="horizontal">

    <LinearLayout android:id="@+id/btn_checklist"
        android:layout_width="0dp" android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true" android:focusable="true"
        android:contentDescription="@string/tb_checklist_cd"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:layout_width="20dp" android:layout_height="20dp"
            android:src="@drawable/ic_checklist"
            app:tint="@color/text_secondary"
            android:importantForAccessibility="no"/>
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/tb_checklist_label"
            android:textColor="@color/text_secondary"
            android:textSize="10sp"/>
    </LinearLayout>

    <LinearLayout android:id="@+id/btn_style"
        android:layout_width="0dp" android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true" android:focusable="true"
        android:contentDescription="@string/tb_style_cd"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:layout_width="20dp" android:layout_height="20dp"
            android:src="@drawable/ic_format_style"
            app:tint="@color/text_secondary"
            android:importantForAccessibility="no"/>
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/tb_style_label"
            android:textColor="@color/text_secondary"
            android:textSize="10sp"/>
    </LinearLayout>

    <LinearLayout android:id="@+id/btn_image"
        android:layout_width="0dp" android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true" android:focusable="true"
        android:contentDescription="@string/tb_image_cd"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:layout_width="20dp" android:layout_height="20dp"
            android:src="@drawable/ic_image"
            app:tint="@color/text_secondary"
            android:importantForAccessibility="no"/>
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/tb_image_label"
            android:textColor="@color/text_secondary"
            android:textSize="10sp"/>
    </LinearLayout>

    <LinearLayout android:id="@+id/btn_record"
        android:layout_width="0dp" android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true" android:focusable="true"
        android:contentDescription="@string/tb_record_cd"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:layout_width="20dp" android:layout_height="20dp"
            android:src="@drawable/ic_mic"
            app:tint="@color/text_secondary"
            android:importantForAccessibility="no"/>
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/tb_record_label"
            android:textColor="@color/text_secondary"
            android:textSize="10sp"/>
    </LinearLayout>

    <LinearLayout android:id="@+id/btn_handwriting"
        android:layout_width="0dp" android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true" android:focusable="true"
        android:contentDescription="@string/tb_handwriting_cd"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:layout_width="20dp" android:layout_height="20dp"
            android:src="@drawable/ic_handwriting"
            app:tint="@color/text_secondary"
            android:importantForAccessibility="no"/>
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/tb_handwriting_label"
            android:textColor="@color/text_secondary"
            android:textSize="10sp"/>
    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: 修改 TextToolbarView.kt — ImageView 改 View**

布局中按钮 ID 现在指向 LinearLayout 而非 ImageView，需将 5 个 lazy 属性的类型从 `ImageView` 改为 `android.view.View`。

```kotlin
// 旧
private val btnChecklist by lazy { findViewById<ImageView>(R.id.btn_checklist) }
private val btnStyle by lazy { findViewById<ImageView>(R.id.btn_style) }
private val btnImage by lazy { findViewById<ImageView>(R.id.btn_image) }
private val btnHandwriting by lazy { findViewById<ImageView>(R.id.btn_handwriting) }
private val btnRecord by lazy { findViewById<ImageView>(R.id.btn_record) }

// 新（5 处 ImageView → android.view.View）
private val btnChecklist by lazy { findViewById<android.view.View>(R.id.btn_checklist) }
private val btnStyle by lazy { findViewById<android.view.View>(R.id.btn_style) }
private val btnImage by lazy { findViewById<android.view.View>(R.id.btn_image) }
private val btnHandwriting by lazy { findViewById<android.view.View>(R.id.btn_handwriting) }
private val btnRecord by lazy { findViewById<android.view.View>(R.id.btn_record) }
```

删除不再需要的 `import android.widget.ImageView`。

- [ ] **Step 3: 构建验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/toolbar_text.xml app/src/main/java/com/fan/hwnote/app/view/toolbar/TextToolbarView.kt
git commit -m "refactor(m13b): TextToolbar 改图标+文字竖排布局"
```

---

### Task 4: 浏览态底部动作栏布局

**Files:**
- Create: `app/src/main/res/layout/bar_browse_action.xml`

- [ ] **Step 1: 创建布局 — 4 个图标+文字按钮**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="52dp"
    android:background="@color/bg_card"
    android:gravity="center_vertical"
    android:orientation="horizontal">

    <LinearLayout android:id="@+id/btn_browse_share"
        android:layout_width="0dp" android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true" android:focusable="true"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:layout_width="20dp" android:layout_height="20dp"
            android:src="@drawable/ic_share"
            app:tint="@color/text_secondary"
            android:importantForAccessibility="no"/>
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/editor_share"
            android:textColor="@color/text_secondary"
            android:textSize="10sp"/>
    </LinearLayout>

    <LinearLayout android:id="@+id/btn_browse_favorite"
        android:layout_width="0dp" android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true" android:focusable="true"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:id="@+id/browse_favorite_icon"
            android:layout_width="20dp" android:layout_height="20dp"
            android:src="@drawable/ic_star_outline"
            app:tint="@color/text_secondary"
            android:importantForAccessibility="no"/>
        <TextView android:id="@+id/browse_favorite_label"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/editor_favorite"
            android:textColor="@color/text_secondary"
            android:textSize="10sp"/>
    </LinearLayout>

    <LinearLayout android:id="@+id/btn_browse_delete"
        android:layout_width="0dp" android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true" android:focusable="true"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:layout_width="20dp" android:layout_height="20dp"
            android:src="@drawable/ic_delete"
            app:tint="@color/text_secondary"
            android:importantForAccessibility="no"/>
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/editor_delete"
            android:textColor="@color/text_secondary"
            android:textSize="10sp"/>
    </LinearLayout>

    <LinearLayout android:id="@+id/btn_browse_more"
        android:layout_width="0dp" android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:clickable="true" android:focusable="true"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:layout_width="20dp" android:layout_height="20dp"
            android:src="@drawable/ic_more_vert"
            app:tint="@color/text_secondary"
            android:importantForAccessibility="no"/>
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/editor_more"
            android:textColor="@color/text_secondary"
            android:textSize="10sp"/>
    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: 构建验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/res/layout/bar_browse_action.xml
git commit -m "feat(m13b): 加浏览态底部动作栏布局"
```

---

### Task 5: ImageBlockView ✕ 删除覆盖按钮

**Files:**
- Rewrite: `app/src/main/res/layout/block_image.xml`
- Modify: `app/src/main/java/com/fan/hwnote/app/view/block/ImageBlockView.kt`

- [ ] **Step 1: 重写 block_image.xml — merge 改 FrameLayout + ✕ 覆盖按钮**

完整新内容（替换整个文件）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginVertical="@dimen/spacing_s">

    <com.google.android.material.imageview.ShapeableImageView
        android:id="@+id/block_image"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:adjustViewBounds="true"
        android:contentDescription="@string/image_block_cd"
        android:maxHeight="320dp"
        android:scaleType="fitCenter"
        app:shapeAppearanceOverlay="@style/ShapeAppearance.HwNote.ImageBlock" />

    <ImageView
        android:id="@+id/btn_delete_image"
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:layout_gravity="top|end"
        android:layout_margin="6dp"
        android:contentDescription="@string/editor_close_image_cd"
        android:src="@drawable/ic_close_circle"
        android:visibility="gone" />
</FrameLayout>
```

注意：从 `<merge>` 改为 `<FrameLayout>` 后，`ImageBlockView` 的 `init` 中 `inflate(..., true)` 的第二参数 `attachToRoot` 需改为 `false`，并手动 `addView`——**或者**保持 `attachToRoot=true`，因为 FrameLayout 作为 root 会被 inflate 到 `ImageBlockView`（extends FrameLayout）内部（inflate 拿到的是 FrameLayout，但 `attachToRoot=true` 会将其子 views 加入 this）。

**但实际上 `inflate(layout, this, true)` 对于非 `<merge>` root 会把整个 FrameLayout 作为子 View 加进 `this`，导致双层 FrameLayout**。所以需要改为 `inflate(layout, this, false)` + 手动取子 view。

最简方案：改回 `<merge>` 根，这样 inflate 行为不变，ShapeableImageView 和 ImageView 直接成为 ImageBlockView（FrameLayout）的子 view：

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    tools:parentTag="com.fan.hwnote.app.view.block.ImageBlockView">

    <com.google.android.material.imageview.ShapeableImageView
        android:id="@+id/block_image"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginVertical="@dimen/spacing_s"
        android:adjustViewBounds="true"
        android:contentDescription="@string/image_block_cd"
        android:maxHeight="320dp"
        android:scaleType="fitCenter"
        app:shapeAppearanceOverlay="@style/ShapeAppearance.HwNote.ImageBlock" />

    <ImageView
        android:id="@+id/btn_delete_image"
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:layout_gravity="top|end"
        android:layout_marginTop="@dimen/spacing_s"
        android:layout_marginEnd="6dp"
        android:contentDescription="@string/editor_close_image_cd"
        android:src="@drawable/ic_close_circle"
        android:visibility="gone" />
</merge>
```

**用此 `<merge>` 版本**。inflate 行为与旧文件一致，不需改 Kotlin 代码的 inflate 调用。

- [ ] **Step 2: 修改 ImageBlockView.kt — 加 btnDelete + setDeleteVisible()**

在 `private val imageView: ImageView` 之后新增字段：

```kotlin
    private val btnDelete: ImageView
```

在 `init` 块中 `imageView = ...` 之后新增：

```kotlin
        btnDelete = findViewById(R.id.btn_delete_image)
        btnDelete.setOnClickListener { callback?.onRequestDelete(this) }
```

在 `showDeleteDialog()` 方法之后新增方法：

```kotlin
    fun setDeleteVisible(visible: Boolean) {
        btnDelete.visibility = if (visible) VISIBLE else GONE
        imageView.isClickable = visible
        imageView.isLongClickable = visible
    }
```

说明：浏览态 `setDeleteVisible(false)` 隐藏 ✕ 按钮，同时让 imageView 不消费触摸事件（触摸穿透到父 → 触发 enterEditMode()）。编辑态 `setDeleteVisible(true)` 显示 ✕ + 恢复 imageView 可交互。

- [ ] **Step 3: 构建验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/block_image.xml app/src/main/java/com/fan/hwnote/app/view/block/ImageBlockView.kt
git commit -m "feat(m13b): ImageBlockView 加 ✕ 删除覆盖按钮 + setDeleteVisible"
```

---

### Task 6: BlockView readOnly 方法

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt`
- Modify: `app/src/main/java/com/fan/hwnote/app/view/block/ChecklistBlockView.kt`
- Modify: `app/src/main/java/com/fan/hwnote/app/view/block/ChecklistItemView.kt`
- Modify: `app/src/main/java/com/fan/hwnote/app/view/block/AudioBlockView.kt`

- [ ] **Step 1: TextBlockView.setEditable(Boolean)**

在 `fun focusEditEnd()` 方法之后新增：

```kotlin
    fun setEditable(editable: Boolean) {
        edit.isFocusableInTouchMode = editable
        edit.isFocusable = editable
        edit.isCursorVisible = editable
        edit.isClickable = editable
        edit.isLongClickable = editable
    }
```

说明：浏览态所有属性 false → EditText 不消费触摸事件，触摸穿透到 editor_content → 触发 enterEditMode()。

- [ ] **Step 2: ChecklistItemView.setEditable(Boolean)**

在 `fun focusEditEnd()` 方法之后新增：

```kotlin
    fun setEditable(editable: Boolean) {
        edit.isFocusableInTouchMode = editable
        edit.isFocusable = editable
        edit.isCursorVisible = editable
        edit.isClickable = editable
        edit.isLongClickable = editable
    }
```

注意：CheckBox **不受影响**，浏览态仍可点击切换勾选（spec §2.1 明确要求）。

- [ ] **Step 3: ChecklistBlockView.setEditable(Boolean)**

在 `fun focusLastItemEnd()` 方法之后新增：

```kotlin
    fun setEditable(editable: Boolean) {
        for (item in items) item.setEditable(editable)
    }
```

- [ ] **Step 4: AudioBlockView.setDeleteEnabled(Boolean)**

在 `private fun showDeleteDialog()` 方法之前新增：

```kotlin
    fun setDeleteEnabled(enabled: Boolean) {
        setOnLongClickListener(if (enabled) { { showDeleteDialog(); true } } else null)
        isLongClickable = enabled
    }
```

说明：只禁用长按删除，播放按钮不受影响，浏览态仍可播放录音。

- [ ] **Step 5: 构建验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/view/block/TextBlockView.kt app/src/main/java/com/fan/hwnote/app/view/block/ChecklistBlockView.kt app/src/main/java/com/fan/hwnote/app/view/block/ChecklistItemView.kt app/src/main/java/com/fan/hwnote/app/view/block/AudioBlockView.kt
git commit -m "feat(m13b): BlockView 各子类加 readOnly 控制方法"
```

---

### Task 7: EditorPresenter.setReadOnly()

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`

- [ ] **Step 1: 新增 `isReadOnly` 字段**

在 `var noteId: Long = 0L` 之后新增：

```kotlin
    var isReadOnly: Boolean = false
        private set
```

- [ ] **Step 2: 新增 `setReadOnly(Boolean)` 方法**

在 `fun focusLastTextBlock()` 方法之后新增：

```kotlin
    fun setReadOnly(readOnly: Boolean) {
        isReadOnly = readOnly
        for (view in currentBlocks) {
            when (view) {
                is TextBlockView -> view.setEditable(!readOnly)
                is ImageBlockView -> view.setDeleteVisible(!readOnly)
                is ChecklistBlockView -> view.setEditable(!readOnly)
                is com.fan.hwnote.app.view.block.AudioBlockView -> view.setDeleteEnabled(!readOnly)
            }
        }
    }
```

- [ ] **Step 3: 新增块时根据 `isReadOnly` 设置初始状态**

在 `addTextBlockView()` 方法体末尾（`}` 之前，即 `container.addView` 的 if/else 之后）新增：

```kotlin
        if (isReadOnly) v.setEditable(false)
```

在 `addImageBlockView()` 方法体末尾新增：

```kotlin
        if (isReadOnly) v.setDeleteVisible(false)
```

在 `addChecklistBlockView()` 方法体末尾新增：

```kotlin
        if (isReadOnly) v.setEditable(false)
```

在 `addAudioBlockView()` 方法体末尾新增：

```kotlin
        if (isReadOnly) v.setDeleteEnabled(false)
```

- [ ] **Step 4: 构建验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt
git commit -m "feat(m13b): EditorPresenter 加 setReadOnly 控制 BlockView 可编辑性"
```

---

### Task 8: activity_note_editor.xml 布局重写

**Files:**
- Rewrite: `app/src/main/res/layout/activity_note_editor.xml`

- [ ] **Step 1: 完整重写布局**

用以下内容替换整个文件。变更要点：
- 去掉 AppBarLayout / Toolbar，换白底 LinearLayout 顶栏（← + ↩↪✓）
- 笔记本指示器从 AppBar 移到 metadata_strip 右侧
- 底部三栏互斥：TextToolbarView（编辑态）/ HandwritingToolbarView（手写态）/ BrowseActionBar（浏览态）
- 背景色统一 bg_card（白色）

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/editor_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_card"
    android:orientation="vertical">

    <!-- ===== 顶栏：白底 ===== -->
    <LinearLayout
        android:id="@+id/top_bar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:background="@color/bg_card"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingHorizontal="4dp">

        <ImageView
            android:id="@+id/btn_back"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/editor_back_cd"
            android:padding="12dp"
            android:src="@drawable/ic_arrow_back"
            app:tint="@color/text_primary" />

        <View
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_weight="1" />

        <!-- 编辑态才可见的右侧按钮组 -->
        <LinearLayout
            android:id="@+id/edit_actions"
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:visibility="gone">

            <ImageView
                android:id="@+id/btn_undo"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/action_undo_cd"
                android:padding="12dp"
                android:src="@drawable/ic_undo"
                app:tint="@color/text_primary" />

            <ImageView
                android:id="@+id/btn_redo"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/action_redo_cd"
                android:padding="12dp"
                android:src="@drawable/ic_redo"
                app:tint="@color/text_primary" />

            <ImageView
                android:id="@+id/btn_done"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/editor_done_cd"
                android:padding="12dp"
                android:src="@drawable/ic_done"
                app:tint="@color/primary" />
        </LinearLayout>
    </LinearLayout>

    <!-- ===== 内容区 ===== -->
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

                <!-- metadata_strip：时间（左） + 笔记本指示器（右） -->
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

                    <View
                        android:layout_width="0dp"
                        android:layout_height="0dp"
                        android:layout_weight="1" />

                    <!-- 笔记本指示器（从 AppBar 移到这里） -->
                    <LinearLayout
                        android:id="@+id/notebook_indicator"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:background="?attr/selectableItemBackgroundBorderless"
                        android:gravity="center_vertical"
                        android:orientation="horizontal"
                        android:paddingHorizontal="@dimen/spacing_xs"
                        android:paddingVertical="2dp">

                        <View
                            android:id="@+id/indicator_dot"
                            android:layout_width="8dp"
                            android:layout_height="8dp" />

                        <TextView
                            android:id="@+id/indicator_text"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="4dp"
                            android:textColor="@color/primary"
                            android:textSize="@dimen/text_hint" />

                        <ImageView
                            android:layout_width="12dp"
                            android:layout_height="12dp"
                            android:layout_marginStart="2dp"
                            android:importantForAccessibility="no"
                            android:src="@drawable/ic_chevron_down"
                            app:tint="@color/primary" />
                    </LinearLayout>
                </LinearLayout>

                <!-- 分类 chip（时间戳下方第二行，保留现有行为） -->
                <LinearLayout
                    android:id="@+id/meta_category_chip"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="2dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:gravity="center_vertical"
                    android:orientation="horizontal"
                    android:paddingVertical="2dp"
                    android:visibility="gone">

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
                android:layout_height="match_parent" />
        </FrameLayout>
    </androidx.core.widget.NestedScrollView>

    <!-- ===== 底部工具栏：编辑态 ===== -->
    <com.fan.hwnote.app.view.toolbar.TextToolbarView
        android:id="@+id/text_toolbar"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="@color/bg_card"
        android:visibility="gone" />

    <!-- ===== 底部手写工具栏 ===== -->
    <com.fan.hwnote.app.view.toolbar.HandwritingToolbarView
        android:id="@+id/handwriting_toolbar"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="@color/bg_card"
        android:visibility="gone" />

    <!-- ===== 底部动作栏：浏览态 ===== -->
    <include
        android:id="@+id/browse_action_bar"
        layout="@layout/bar_browse_action"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:visibility="gone" />

</LinearLayout>
```

关键变更说明：
- TextToolbarView 初始 `visibility="gone"`（不再默认可见，由 Activity 根据模式控制）
- HandwritingToolbarView 背景改 `bg_card`（白色）
- BrowseActionBar 用 `<include>` 引入
- 笔记本指示器的 `indicatorText` textColor 改为 `@color/primary`（蓝色），与华为参考对齐
- 根布局背景改 `bg_card`（白色），不再是 `bg_window`

- [ ] **Step 2: 构建验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

构建预期会报错（Activity 中引用了已删除的 `editor_toolbar` ID），这是正常的——Task 9 修改 Activity 后修复。此步仅验证 XML 语法正确。若报的是 XML 解析错误，检查标签闭合。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/res/layout/activity_note_editor.xml
git commit -m "refactor(m13b): 编辑器布局去 AppBar 换白底顶栏 + 三底栏"
```

---

### Task 9: NoteEditorActivity 双模式逻辑

**Files:**
- Modify: `app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

这是最大的单任务。完整改动清单如下。

- [ ] **Step 1: 删除 Toolbar 相关代码**

删除以下代码：

```kotlin
// 删除 import
import androidx.appcompat.widget.Toolbar

// 删除字段
private lateinit var toolbar: Toolbar

// 删除 onCreate 中的 Toolbar 设置（4 行）
toolbar = findViewById(R.id.editor_toolbar)
setSupportActionBar(toolbar)
supportActionBar?.setDisplayHomeAsUpEnabled(true)
supportActionBar?.setDisplayShowTitleEnabled(false)
toolbar.setNavigationOnClickListener { finish() }

// 删除 history.listener 这一行
presenter.history.listener = { _, _ -> invalidateOptionsMenu() }

// 删除整个 onCreateOptionsMenu 方法
override fun onCreateOptionsMenu(menu: Menu): Boolean { ... }

// 删除整个 onPrepareOptionsMenu 方法
override fun onPrepareOptionsMenu(menu: Menu): Boolean { ... }

// 删除整个 onOptionsItemSelected 方法
override fun onOptionsItemSelected(item: MenuItem): Boolean { ... }

// 删除整个 applyMenuIconAlpha 方法
private fun applyMenuIconAlpha(item: MenuItem) { ... }

// 删除不再需要的 import
import android.view.Menu
import android.view.MenuItem
```

- [ ] **Step 2: 新增字段**

在 `private lateinit var textToolbar` 之后新增：

```kotlin
    private lateinit var btnBack: android.widget.ImageView
    private lateinit var btnUndo: android.widget.ImageView
    private lateinit var btnRedo: android.widget.ImageView
    private lateinit var btnDone: android.widget.ImageView
    private lateinit var editActions: LinearLayout
    private lateinit var browseActionBar: android.view.View
    private lateinit var browseFavoriteIcon: android.widget.ImageView
    private lateinit var browseFavoriteLabel: android.widget.TextView

    private var isEditing: Boolean = false
```

- [ ] **Step 3: 重写 onCreate 顶栏 + 底栏绑定**

在 `setContentView(R.layout.activity_note_editor)` 之后、`val editorRoot = ...` 之前，新增：

```kotlin
        // 顶栏
        btnBack = findViewById(R.id.btn_back)
        btnBack.setOnClickListener { finish() }
        editActions = findViewById(R.id.edit_actions)
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        btnDone = findViewById(R.id.btn_done)
        btnUndo.setOnClickListener { presenter.undo(); updateUndoRedoButtons() }
        btnRedo.setOnClickListener { presenter.redo(); updateUndoRedoButtons() }
        btnDone.setOnClickListener { exitEditMode() }
```

在 `presenter = EditorPresenter(...)` 之后、空白点击聚焦代码之前，新增 history listener：

```kotlin
        presenter.history.listener = { _, _ -> updateUndoRedoButtons() }
```

修改 `editorScrollInner.setOnClickListener` 为：

```kotlin
        editorScrollInner.setOnClickListener {
            if (handwritingOverlay.isHandwritingMode) return@setOnClickListener
            if (!isEditing) enterEditMode()
            else presenter.focusLastTextBlock()
        }
```

同时给 `editor_content` 加点击监听（让内容区域内的触摸也能触发进入编辑态）：

```kotlin
        val editorContent = findViewById<android.view.View>(R.id.editor_content)
        editorContent.setOnClickListener {
            if (handwritingOverlay.isHandwritingMode) return@setOnClickListener
            if (!isEditing) enterEditMode()
            else presenter.focusLastTextBlock()
        }
```

在 `textToolbar = findViewById(...)` 之后、textToolbar.listener 之前新增浏览态动作栏绑定：

```kotlin
        browseActionBar = findViewById(R.id.browse_action_bar)
        browseFavoriteIcon = findViewById(R.id.browse_favorite_icon)
        browseFavoriteLabel = findViewById(R.id.browse_favorite_label)
        findViewById<android.view.View>(R.id.btn_browse_share).setOnClickListener {
            android.widget.Toast.makeText(this, R.string.toast_share_placeholder,
                android.widget.Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btn_browse_favorite).setOnClickListener {
            toggleFavorite()
        }
        findViewById<android.view.View>(R.id.btn_browse_delete).setOnClickListener {
            softDeleteAndFinish()
        }
        findViewById<android.view.View>(R.id.btn_browse_more).setOnClickListener { anchor ->
            showBrowseMoreMenu(anchor)
        }
```

- [ ] **Step 4: 修改 `titleInput` 绑定位置**

将 `titleInput = findViewById(R.id.title_input)` 保留在 `setContentView` 之后（已有）。不需变动。

将 `blocksContainer = findViewById(R.id.blocks_container)` 保留（已有）。不需变动。

- [ ] **Step 5: 新增 `enterEditMode()` 方法**

在 `exitHandwritingMode()` 方法之后新增：

```kotlin
    private fun enterEditMode() {
        if (isEditing) return
        isEditing = true
        editActions.visibility = android.view.View.VISIBLE
        textToolbar.visibility = android.view.View.VISIBLE
        browseActionBar.visibility = android.view.View.GONE
        titleInput.isFocusableInTouchMode = true
        titleInput.isFocusable = true
        titleInput.isClickable = true
        titleInput.isLongClickable = true
        presenter.setReadOnly(false)
        updateUndoRedoButtons()
        presenter.focusLastTextBlock()
    }
```

- [ ] **Step 6: 新增 `exitEditMode()` 方法**

在 `enterEditMode()` 方法之后新增：

```kotlin
    private fun exitEditMode() {
        if (!isEditing) return
        isEditing = false
        editActions.visibility = android.view.View.GONE
        textToolbar.visibility = android.view.View.GONE
        browseActionBar.visibility = android.view.View.VISIBLE
        // 隐藏键盘
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(blocksContainer.windowToken, 0)
        titleInput.isFocusableInTouchMode = false
        titleInput.isFocusable = false
        titleInput.isClickable = false
        titleInput.isLongClickable = false
        titleInput.clearFocus()
        presenter.setReadOnly(true)
        // 落库当前编辑
        presenter.flushPendingTextEdits()
        saveNote()
        refreshFavoriteButton()
    }
```

- [ ] **Step 7: 新增辅助方法**

在 `exitEditMode()` 之后新增：

```kotlin
    private fun updateUndoRedoButtons() {
        val canUndo = presenter.history.canUndo()
        val canRedo = presenter.history.canRedo()
        btnUndo.isEnabled = canUndo
        btnRedo.isEnabled = canRedo
        btnUndo.alpha = if (canUndo) 1.0f else 0.4f
        btnRedo.alpha = if (canRedo) 1.0f else 0.4f
    }

    private fun refreshFavoriteButton() {
        val fav = loadedNote?.isFavorite == true
        browseFavoriteIcon.setImageResource(
            if (fav) R.drawable.ic_star else R.drawable.ic_star_outline
        )
        browseFavoriteLabel.setText(
            if (fav) R.string.editor_unfavorite else R.string.editor_favorite
        )
    }

    private fun toggleFavorite() {
        val note = loadedNote ?: return
        val updated = note.copy(isFavorite = !note.isFavorite)
        loadedNote = updated
        refreshFavoriteButton()
        if (note.id > 0L) {
            lifecycleScope.launch(Dispatchers.IO) {
                NoteRepository.save(updated)
            }
        }
    }

    private fun softDeleteAndFinish() {
        val note = loadedNote ?: return
        if (note.id <= 0L) { finish(); return }
        com.fan.hwnote.app.view.list.DeleteConfirmBottomSheet(
            context = this,
            message = getString(R.string.dialog_soft_delete_message),
            confirmLabel = getString(R.string.action_delete),
            onConfirm = {
                lifecycleScope.launch(Dispatchers.IO) {
                    NoteRepository.softDelete(note.id)
                    withContext(Dispatchers.Main) { finish() }
                }
            },
        ).show()
    }

    private fun showBrowseMoreMenu(anchor: android.view.View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_editor_browse_more, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_move_notebook -> {
                    val current = pendingNotebookId ?: loadedNote?.notebookId
                    NotebookPickerPopupWindow(
                        context = this,
                        currentNotebookId = current,
                        onPicked = { picked ->
                            pendingNotebookId = picked
                            lifecycleScope.launch { refreshIndicator(picked) }
                        },
                    ).show(anchor)
                    true
                }
                R.id.action_set_category -> {
                    showCategoryPicker()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
```

- [ ] **Step 8: 修改 `loadNote()` 设置初始模式**

在 `loadNote()` 方法末尾（`refreshIndicator(note.notebookId)` 之后）新增模式初始化：

```kotlin
            // 新建笔记（noteId == -1L）直接进入编辑态；已有笔记进入浏览态
            if (noteId == -1L) {
                enterEditMode()
            } else {
                // 浏览态：设只读 + 显示动作栏
                presenter.setReadOnly(true)
                browseActionBar.visibility = android.view.View.VISIBLE
                titleInput.isFocusableInTouchMode = false
                titleInput.isFocusable = false
                titleInput.isClickable = false
                titleInput.isLongClickable = false
                refreshFavoriteButton()
            }
```

- [ ] **Step 9: 修改 `onPause` 中 `saveNote` 调用（保持不变，两个模式都走 onPause save）**

无需改动，现有 `onPause` 中的 `presenter.flushPendingTextEdits()` + `saveNote()` 在两个模式下都正确工作。

- [ ] **Step 10: 修改 `enterHandwritingMode()` — 适配双模式**

现有 `enterHandwritingMode()` 已经隐藏 textToolbar 显示 handwritingToolbar，不需改动。但需确保手写模式不影响 browseActionBar 状态——由于手写态只在编辑态下触发（按钮在编辑态工具栏），browseActionBar 已经是 GONE，不冲突。不需改动。

- [ ] **Step 11: 修改 `exitHandwritingMode()` — 根据 `isEditing` 决定恢复哪个底栏**

当前 `exitHandwritingMode()` 无条件显示 textToolbar：

```kotlin
textToolbar.visibility = android.view.View.VISIBLE
```

如果将来手写态可以从浏览态进入（目前不可以，但为防御性编码），改为：

```kotlin
textToolbar.visibility = if (isEditing) android.view.View.VISIBLE else android.view.View.GONE
```

但由于手写按钮只在编辑态可见，这步是**防御性**的。保持改动以确保逻辑完备。

- [ ] **Step 12: 修改 WindowInsets 处理 — 加顶部 status bar padding**

旧 AppBarLayout 的 `fitsSystemWindows="true"` 处理了顶部 status bar inset。去掉 AppBarLayout 后，需要在自定义 listener 中也处理顶部 inset。

修改现有的 `ViewCompat.setOnApplyWindowInsetsListener` 回调：

```kotlin
// 旧
v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, kotlin.math.max(ime.bottom, bars.bottom))

// 新（加 bars.top 处理 status bar）
v.setPadding(v.paddingLeft, bars.top, v.paddingRight, kotlin.math.max(ime.bottom, bars.bottom))
```

- [ ] **Step 13: 构建验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 14: 提交**

```bash
git add app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "feat(m13b): NoteEditorActivity 实现浏览/编辑双模式切换"
```

---

### Task 10: 删除 menu_editor.xml + 全量构建测试

**Files:**
- Delete: `app/src/main/res/menu/menu_editor.xml`

- [ ] **Step 1: 删除旧菜单资源**

```bash
rm app/src/main/res/menu/menu_editor.xml
```

（undo/redo 已迁移到顶栏 ImageView，不再需要 options menu。）

- [ ] **Step 2: 全量构建 + 运行所有测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) ./gradlew :app:clean :app:assembleDebug :app:test
```

Expected: BUILD SUCCESSFUL + 140 tests PASSED

若测试失败，检查是否有对 `R.menu.menu_editor` 或 `R.id.action_undo` / `R.id.action_redo` 的残余引用（Task 9 应已全部删除）。

- [ ] **Step 3: 提交**

```bash
git add -u app/src/main/res/menu/menu_editor.xml
git commit -m "chore(m13b): 删旧 menu_editor（undo/redo 迁到顶栏）"
```

---

### Task 11: STATUS 更新 + 计划归档

**Files:**
- Modify: `STATUS.md`
- Modify: `docs/superpowers/plans/2026-06-05-m13b-editor-redesign.md`

- [ ] **Step 1: STATUS.md 追加 M13b 章节**

在 STATUS.md 的 M13a 章节之后追加：

```markdown
## M13b 编辑器重设计（2026-06-05）

**目标：** 编辑器从"始终编辑态 + 蓝色 Toolbar"切换到"白底双模式"（浏览态 + 编辑态）

**改动：**
- 去掉 AppBarLayout/Toolbar，换白底 LinearLayout 顶栏（← + ↩↪✓）
- 新增 `isEditing` 状态驱动浏览/编辑模式切换
- 浏览态底部动作栏（分享/收藏/删除/更多）
- 编辑态底部工具栏改图标+文字
- EditorPresenter.setReadOnly() 控制各 BlockView 可编辑性
- ImageBlockView 新增 ✕ 可视删除按钮
- 笔记本指示器从 AppBar 移到 metadata_strip

**涉及文件：** NoteEditorActivity.kt, EditorPresenter.kt, activity_note_editor.xml, toolbar_text.xml, TextToolbarView.kt, block_image.xml, ImageBlockView.kt, TextBlockView.kt, ChecklistBlockView.kt, ChecklistItemView.kt, AudioBlockView.kt, strings.xml, 3 个新 drawable, 2 个新 layout, 1 个新 menu, 删除 menu_editor.xml

**测试：** 140 项 PASSED
```

- [ ] **Step 2: 标记计划已完成**

在本计划文件 `docs/superpowers/plans/2026-06-05-m13b-editor-redesign.md` 顶部 `---` 之前新增：

```markdown
> **Status: COMPLETED**
```

- [ ] **Step 3: 提交**

```bash
git add STATUS.md docs/superpowers/plans/2026-06-05-m13b-editor-redesign.md
git commit -m "docs(m13b): 更新 STATUS + 标记计划完成"
```

---

## 不动的东西（明确边界）

- **EditorPresenter 核心逻辑** — toggleInline / toggleSize / pickColor / toggleHeading / insert* / onRequestDelete / onRequestSplitAfter / history / focusLastTextBlock — 一行不改（仅新增 `isReadOnly` + `setReadOnly`）
- **StylePickerBottomSheet** — M13c 处理增强，M13b 不动
- **HandwritingOverlayView / BrushPainter** — 不动
- **HandwritingStylePickerBottomSheet** — 不动
- **AudioRecordingBottomSheet / CategoryPickerBottomSheet** — 不动
- **数据层（NoteRepository / FolderRepository / NotebookRepository / NoteDbHelper）** — 不动
- **NoteListActivity / NoteListAdapter / FilterPanelAdapter** — 不动
- **测试** — 140 项保持 PASS，无新增单测（UI 表现层改动）

## 验证（真机）

- ✅ 打开已有笔记 → 浏览态（只读 + 底部动作栏 4 按钮 + 顶栏仅 ←）
- ✅ 点击内容区 → 进入编辑态（键盘弹起 + 底部工具栏 5 按钮 + 顶栏 ←↩↪✓）
- ✅ 点击 ✓ → 保存 + 隐藏键盘 + 回到浏览态
- ✅ 浏览态点收藏 → 图标+文字切换（收藏⇄取消收藏）
- ✅ 浏览态点删除 → DeleteConfirmBottomSheet → 确认 → 软删除 + 返回列表
- ✅ 浏览态点更多 → PopupMenu（移动笔记本 / 设置分类）
- ✅ 浏览态点分享 → Toast 占位
- ✅ 新建笔记 → 直接编辑态
- ✅ 图片块编辑态显示 ✕ 按钮，浏览态隐藏
- ✅ 清单勾选框浏览态可点击切换
- ✅ 录音浏览态可播放
- ✅ 笔记本指示器在 metadata_strip 右侧（蓝色），可点击切换
- ✅ 手写态正常（进入/退出不影响双模式逻辑）
- ✅ 撤销/重做按钮 alpha 跟随 canUndo/canRedo
- ✅ 白色状态栏 + 顶栏无蓝色背景

## 执行方式

Subagent-Driven Development（同 M13a）：每任务 implementer DONE → spec reviewer → code quality reviewer → fix（如需）→ re-review → 标记完成。

预估 4-6 小时。
