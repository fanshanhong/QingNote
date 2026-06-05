# M8 体验小修 实施计划（2026-06-04）

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development —— per-task 串行 implementer + spec reviewer + code quality reviewer。

**Goal:** 修四个用户在 PRD §13 提出的轻量改动 —— 都是 UI 表现层或单文件逻辑层小改，不动数据模型，不影响 68 单测基线。

**Architecture:** 4 个独立改动彼此不耦合：1 改 AppBarLayout 属性；2 改 ImageBlockView 图层；3 改 EditorPresenter 焦点判断分支；4 把 AlertDialog 换 BottomSheetDialog + 删一个枚举值。

**Tech Stack:** 沿用既有 —— Material `ShapeableImageView` / `BottomSheetDialog` 都在 `com.google.android.material:material:1.12.0` 内置。

**预估：** 5 任务，半天工作量；最终 commit `docs(m8): 标记 M8 完成` 收尾。

---

## 边界（不动的东西）

- 数据层 / DB / JSON / Repository —— 零改动
- HandwritingOverlayView / BrushPainter / EditorPresenter 富文本逻辑 —— 零改动
- 现有 68 单测 —— 全部维持 PASS，不新增单测（4 项均为 UI 改动，沿"写完即手测"约定）
- `NoteRepository.SortBy.TITLE_ASC` 枚举值删除会引起 `loadSort()` 的 `valueOf` 失败 → 已有 `runCatching.getOrDefault(UPDATED_DESC)` 兜底，旧 SharedPreferences 自动降级安全

---

## Task 1 — 列表页状态栏 fitsSystemWindows（#1）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/layout/activity_note_list.xml`

**改动：** AppBarLayout 加 `android:fitsSystemWindows="true"`。Material AppBarLayout 自动消化 statusBar inset，并把 `@color/primary` 背景延伸进系统栏 —— 这是 M7 T12 编辑器侧已验证的同 pattern。

**Diff:**

```xml
<com.google.android.material.appbar.AppBarLayout
    android:id="@+id/appbar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/primary"
+   android:fitsSystemWindows="true"
    android:theme="@style/ThemeOverlay.MaterialComponents.Dark.ActionBar">
```

**验：** 真机打开列表页 → 系统时间不再被"备忘录"标题压住；statusBar 区呈现 `@color/primary` 青绿，与 Toolbar 连成一块。

**Commit:** `fix(m8): 列表页 AppBarLayout fitsSystemWindows，避免标题压系统时间`

---

## Task 2 — 图片块去黑边 + 12dp 圆角（#2）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/layout/block_image.xml`
- Delete or repurpose: `code/HuaWeiNote/app/src/main/res/drawable/bg_image_block.xml`
- Delete reference: `code/HuaWeiNote/app/src/main/res/values/colors.xml` 中的 `image_block_border` 颜色项（如无其他引用）
- 视情况新增（如 ShapeAppearanceOverlay 走 styles）：`code/HuaWeiNote/app/src/main/res/values/styles.xml`

**改动：** `ImageView` 换 `com.google.android.material.imageview.ShapeableImageView` + `shapeAppearanceOverlay` 设 12dp 圆角；删 `android:background`（黑边来源）。新增 `ShapeAppearance.Hwnote.ImageBlock12dp` 样式定义。

**Diff — block_image.xml：**

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
        app:shapeAppearanceOverlay="@style/ShapeAppearance.Hwnote.ImageBlock12dp" />
</merge>
```

**新增 styles.xml 条目：**

```xml
<style name="ShapeAppearance.Hwnote.ImageBlock12dp" parent="">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">12dp</item>
</style>
```

**清理：** `bg_image_block.xml` + `image_block_border` 颜色项若全局 grep 确认无其他引用即删除。

**注意：** `ImageBlockView.kt` 内 `findViewById<ImageView>(R.id.block_image)` 类型不会因换 `ShapeableImageView` 报错（`ShapeableImageView extends ImageView`）。Glide `.into(imageView)` 兼容。

**验：** 真机插入图片 → 圆角自然显示，无黑色描边，无白底；横竖图都正常 `fitCenter`。

**Commit:** `fix(m8): 图片块换 ShapeableImageView 12dp 圆角，删黑边白底`

---

## Task 3 — 清单 toggle 反向：当前 TextBlock 转 ChecklistBlock（#3）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`

**当前行为（M7 T13 实现）：** `toggleChecklistAtFocus()` 走 `container.findFocus()` 父链：
- 焦点在 ChecklistItemView → 把该项变 TextBlock（取消）
- 否则 → 调 `insertChecklistBlockAtFocus()` 在焦点 TextBlock **之后**插新清单块

**新行为：** 在 fallback 之前加一个分支 —— 焦点在 TextBlock（`focusedTextBlock != null`）→ 原地把该 TextBlock 转 ChecklistBlock（首项 = 该块当前文字；checked = false）。无焦点才走原来 `insertChecklistBlockAtFocus()` 追加到末尾。

**Diff（修改 `toggleChecklistAtFocus()`）：**

```kotlin
fun toggleChecklistAtFocus() {
    val focused = container.findFocus()
    var v: android.view.View? = focused
    var itemView: ChecklistItemView? = null
    var blockView: ChecklistBlockView? = null
    while (v != null) {
        if (itemView == null && v is ChecklistItemView) itemView = v
        if (v is ChecklistBlockView) { blockView = v; break }
        v = v.parent as? android.view.View
    }
    if (itemView != null && blockView != null) {
        convertChecklistItemToText(blockView, itemView)
        return
    }
    // 新增：焦点在 TextBlock → 原地转 ChecklistBlock（heading/spans 丢失，仅保文字）
    val tb = focusedTextBlock
    if (tb != null) {
        convertTextBlockToChecklist(tb)
        return
    }
    // 兜底：无焦点 → 末尾追加新清单块
    insertChecklistBlockAtFocus()
}

/** 原地把 TextBlock 转 ChecklistBlock：取该块当前文字（toString，丢 heading/spans）作首项；焦点交首项。 */
private fun convertTextBlockToChecklist(tb: TextBlockView) {
    val idx = currentBlocks.indexOf(tb)
    if (idx < 0) return
    val text = tb.edit.text.toString()
    container.removeView(tb)
    currentBlocks.removeAt(idx)
    if (focusedTextBlock === tb) focusedTextBlock = null
    val newBlock = Block.ChecklistBlock(
        id = "c-${UUID.randomUUID().toString().take(8)}",
        items = mutableListOf(com.fan.hwnote.app.model.entity.ChecklistItem(false, text)),
    )
    addChecklistBlockView(newBlock, insertAt = idx)
    (currentBlocks[idx] as ChecklistBlockView).focusLastItemEnd()
}
```

**注意：** `TextBlockView.edit` 字段已是 public（M4 既有），无需新加 accessor。`ChecklistBlockView.focusLastItemEnd()` M7 既有。

**验：**
- 真机：在 TextBlock 输入 "hello" → 点工具栏清单按钮 → 该块变成"☐ hello"，焦点在末尾，可继续输入
- 空 TextBlock 转：变成"☐ "空清单项
- H1/H2 文字转：heading 信息消失（符合数据模型简化语义），文字保留
- 富文本 (B/I) 文字转：spans 消失，纯文字保留

**Commit:** `feat(m8): 清单按钮反向 toggle —— 当前 TextBlock 原地转清单首项`

---

## Task 4 — 排序 BottomSheet 化 + 2 选项（#7）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/NoteRepository.kt`（删 `TITLE_ASC` 枚举值；若 list() / ORDER BY 还有相关分支同步删除）
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/NoteListActivity.kt`（`showSortDialog()` 重写为 BottomSheet）
- Create: `code/HuaWeiNote/app/src/main/res/layout/dialog_sort_picker.xml`（BottomSheet 内容）
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`（删 `sort_title_asc`；保 `sort_updated_desc` = "编辑时间"、`sort_created_desc` = "创建时间"；新增 `sort_picker_title` = "排序方式"）

**改动 NoteRepository.SortBy：** 从 `enum { UPDATED_DESC, CREATED_DESC, TITLE_ASC }` 改为 `enum { UPDATED_DESC, CREATED_DESC }`。`list()` 内 `ORDER BY` switch 的 `TITLE_ASC` 分支同步删除。

**新增布局 dialog_sort_picker.xml：**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="@dimen/spacing_l"
    android:paddingTop="@dimen/spacing_l"
    android:paddingBottom="@dimen/spacing_m">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingBottom="@dimen/spacing_m"
        android:text="@string/sort_picker_title"
        android:textColor="@color/text_primary"
        android:textSize="@dimen/text_title"
        android:textStyle="bold" />

    <RadioGroup
        android:id="@+id/sort_radio_group"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <RadioButton
            android:id="@+id/sort_updated"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:paddingHorizontal="@dimen/spacing_s"
            android:text="@string/sort_updated_desc"
            android:textColor="@color/text_primary"
            android:textSize="@dimen/text_body" />

        <RadioButton
            android:id="@+id/sort_created"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:paddingHorizontal="@dimen/spacing_s"
            android:text="@string/sort_created_desc"
            android:textColor="@color/text_primary"
            android:textSize="@dimen/text_body" />
    </RadioGroup>
</LinearLayout>
```

**改 NoteListActivity.showSortDialog()：**

```kotlin
private fun showSortDialog() {
    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.dialog_sort_picker, null)
    val group = view.findViewById<android.widget.RadioGroup>(R.id.sort_radio_group)
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
    sheet.setContentView(view)
    sheet.show()
}
```

**注意：** `loadSort()` 已有 `runCatching.getOrDefault(UPDATED_DESC)` —— 旧装机的 SharedPreferences 存的 "TITLE_ASC" 在新枚举里 valueOf 抛异常 → 自动降到 UPDATED_DESC，无需 migration。

**验：**
- 真机：长按右上排序图标 → 底部弹 BottomSheet，2 选项 + 当前选中态正确
- 选 "创建时间" → sheet 收回 + 列表立即按 createdAt desc 重排
- 杀进程重启 → 选项保留
- 装新版本前若装过 `TITLE_ASC`：列表自动落 UPDATED_DESC，无崩溃

**Commit:** `feat(m8): 排序改 BottomSheet 2 选项（编辑时间/创建时间），删 TITLE_ASC`

---

## Task 5 — 全量构建 + 真机走查 6 条 + STATUS 收尾

**Files:**
- Modify: `docs/superpowers/STATUS.md`（追加 M8 完成详情节 + 顶部里程碑表加 M8 行）

**验：**
1. `./gradlew :app:clean :app:assembleDebug :app:test` 必须 BUILD SUCCESSFUL + 68/68 PASSED
2. 真机走查 6 条：
   - ✅ 列表页 statusBar 不压系统时间，绿色延伸进系统栏
   - ✅ 图片块圆角自然，无黑边
   - ✅ 焦点在带文字 TextBlock，点清单按钮 → 该块原地变清单首项，文字保留
   - ✅ 焦点在空 TextBlock，点清单按钮 → 该块原地变空清单项
   - ✅ 排序按钮 → BottomSheet 2 选项弹起，切换生效，杀进程保留
   - ✅ M7 已有功能回归：清单内退格 / 末尾空项回车退出 / 图片插入 / 拍照 / 手写 / 标题 / 收藏切换

**STATUS.md 追加节大纲：** "## M8 完成详情（2026-06-04）" 含：起因（PRD §13 启动）/ 4 改动点描述 / 涉及文件 / commit 列表 / 验收 6 条 / 执行模式。同时把"项目完成总览"表加 M8 行（commit 数 / 增量 / 关键产出）。

**Commit:** `docs(m8): 标记 M8 体验小修完成`

---

## 执行方式

Subagent-Driven Development，严格串行 T1→T2→T3→T4→T5。每任务 implementer → spec reviewer → code quality reviewer → fix → 标完成；T5 真机走查由用户完成后再写 STATUS 收尾 commit。

## 自审记录

- 4 改动彼此独立，不会出现交叉破坏
- DB / Repository SortBy 枚举改动有 `runCatching` 兜底，旧版本数据安全
- ImageView → ShapeableImageView 父类兼容，`findViewById<ImageView>` 不需要改类型
- 清单 toggle 反向走 `focusedTextBlock` 而非父链，与已有焦点跟踪机制对齐
- 测试基线 68/68 不动，不引入新单测（符合 UI 层"写完即手测"约定）
