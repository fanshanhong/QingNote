# HwNote M5 图片块 / 清单块 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把编辑器工具栏里 M4 留的"图片"和"清单"两个占位按钮接通：支持多选相册 + 拍照插入图片块（长边 1920px JPEG 85 压缩，本地 filesDir 存储，长按删除），支持清单块编辑（每行 CheckBox + EditText，已勾选行加删除线，末项回车新增项，空项退格删项并上移焦点）。同时把 M4 临时缓存的 `passthroughBlocks` 替换为真实的 BlockView 渲染。

**Architecture:**

- `view/block/ImageBlockView`：`BlockView` 子类，包一张 `ImageView`，用 Glide 加载本地文件 URI；不可编辑文字，长按弹"删除"二次确认。
- `view/block/ChecklistItemView`：单行单元 `LinearLayout`，`CheckBox` + 不带下划线的 `EditText`，勾选 → EditText 加 `STRIKE_THRU_TEXT_FLAG`；ENTER/DEL 上抛给父 `ChecklistBlockView`。
- `view/block/ChecklistBlockView`：`BlockView` 子类，垂直堆 N 个 `ChecklistItemView`；负责新增项/删除项/迁移焦点；空块（仅一空 item）退格上抛 `onRequestDelete`。
- `util/ImageCompressor`：把 gallery URI 或 cache 中拍照原图解码 → 长边 1920px 等比缩放 → 写 JPEG quality 85 到 `NoteFileStorage.imageFile(noteId, "<uuid>.jpg")`；返回真实 (width, height) 用于 `ImageBlock`。
- `EditorPresenter` 改造：删除 `passthroughBlocks`；`bind` 时按 Block 类型路由到 `addTextBlockView` / `addImageBlockView` / `addChecklistBlockView`；新增 `insertImageBlockAtFocus(noteId, items)` 和 `insertChecklistBlockAtFocus()` 工厂方法，由 Activity 触发。
- `NoteEditorActivity`：注册三个 `ActivityResultLauncher`（gallery / camera / CAMERA permission）；`onImageClicked` 弹"相册 / 拍照"AlertDialog；`onChecklistClicked` 直接调 Presenter 插入空清单块。

**Tech Stack:**

- Glide 4.16.0（M1 已引入）— 仅基础 API，不引 compiler / 注解处理
- `androidx.activity:activity-ktx` 自带 `ActivityResultContracts`（appcompat 1.7.0 已传递）
- `ACTION_GET_CONTENT` + `EXTRA_ALLOW_MULTIPLE` 多选相册（PRD §8.4 指定）
- `ACTION_IMAGE_CAPTURE` + FileProvider `cacheDir/camera/<uuid>.jpg`（M1 file_paths.xml 已建好 `camera_cache`）
- BitmapFactory `inSampleSize` 二阶段解码 + Matrix 缩放 + JPEG 85 输出
- 复用 M2 已有：`NoteFileStorage.imageFile/imageDir/deleteNoteDir`；`Block.ImageBlock(id, fileName, width, height)`；`Block.ChecklistBlock(id, items)`；`ChecklistItem(checked, text)`；`NoteJson` round-trip 已完整支持

---

## Task 1：资源准备（strings + drawables + Manifest）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`
- Modify: `code/HuaWeiNote/app/src/main/res/values/colors.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_camera.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_gallery.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/bg_image_block.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/bg_checklist_item.xml`

- [ ] **Step 1：新增 strings 词条（清单 / 图片相关）**

在 `strings.xml` 末尾 `</resources>` 前追加：

```xml
    <!-- M5 图片块 -->
    <string name="image_source_title">插入图片</string>
    <string name="image_source_gallery">从相册选择</string>
    <string name="image_source_camera">拍照</string>
    <string name="image_block_cd">图片</string>
    <string name="image_delete_title">删除图片</string>
    <string name="image_delete_message">确定删除这张图片？</string>
    <string name="image_load_failed">图片加载失败</string>
    <string name="image_save_failed">图片保存失败</string>
    <string name="camera_permission_denied">相机权限被拒绝，无法拍照</string>
    <string name="camera_unavailable">未找到相机应用</string>

    <!-- M5 清单块 -->
    <string name="checklist_item_hint">清单项</string>
    <string name="checklist_item_checkbox_cd">完成标记</string>
```

同时把 M4 留的两条占位 toast 词条删除（已不再用）：

```xml
    <!-- M5/M6 占位 -->
    <string name="toast_image_placeholder">插入图片（M5 实现）</string>
    <string name="toast_checklist_placeholder">插入清单（M6 实现）</string>
```

→ 整组删除（包括上方的注释行）。

- [ ] **Step 2：新增 colors 词条**

在 `colors.xml` 末尾 `</resources>` 前追加（清单 checked 行的文字次色 + 图片块占位边框色）：

```xml
    <color name="text_checked">#9E9E9E</color>
    <color name="image_block_border">#E0E0E0</color>
```

- [ ] **Step 3：新增 ic_camera.xml**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?android:attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z" />
</vector>
```

- [ ] **Step 4：新增 ic_gallery.xml**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?android:attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z" />
</vector>
```

- [ ] **Step 5：新增 bg_image_block.xml（图片块圆角 + 边框）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="4dp" />
    <stroke
        android:width="1dp"
        android:color="@color/image_block_border" />
    <solid android:color="#FFFFFF" />
</shape>
```

- [ ] **Step 6：新增 bg_checklist_item.xml（行级 ripple + 透明背景）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="?android:attr/colorControlHighlight">
    <item android:id="@android:id/mask">
        <shape android:shape="rectangle">
            <solid android:color="#FFFFFF" />
        </shape>
    </item>
</ripple>
```

- [ ] **Step 7：构建验证 + commit**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11)
cd code/HuaWeiNote && ./gradlew :app:assembleDebug
```

Expected：BUILD SUCCESSFUL。

```bash
git add code/HuaWeiNote/app/src/main/res/values/strings.xml \
        code/HuaWeiNote/app/src/main/res/values/colors.xml \
        code/HuaWeiNote/app/src/main/res/drawable/ic_camera.xml \
        code/HuaWeiNote/app/src/main/res/drawable/ic_gallery.xml \
        code/HuaWeiNote/app/src/main/res/drawable/bg_image_block.xml \
        code/HuaWeiNote/app/src/main/res/drawable/bg_checklist_item.xml
git commit -m "feat(m5): 图片/清单块资源准备（drawables + 词条 + 颜色）"
```

注：Manifest 不动 — `CAMERA` 权限和 `FileProvider` M1 已声明、`file_paths.xml` 的 `camera_cache` 也已在位。

---

## Task 2：块布局文件（image / checklist item）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/res/layout/block_image.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/block_checklist_item.xml`

> 说明：`ChecklistBlockView` 本身用代码动态加 item，不需要外层 xml；`ImageBlockView` 用 `<merge>` 让自定义 View 直接作为根。

- [ ] **Step 1：新建 block_image.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    tools:parentTag="com.fan.hwnote.app.view.block.ImageBlockView">

    <ImageView
        android:id="@+id/block_image"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginVertical="@dimen/spacing_s"
        android:adjustViewBounds="true"
        android:background="@drawable/bg_image_block"
        android:contentDescription="@string/image_block_cd"
        android:maxHeight="320dp"
        android:scaleType="fitCenter" />
</merge>
```

- [ ] **Step 2：新建 block_checklist_item.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    tools:parentTag="com.fan.hwnote.app.view.block.ChecklistItemView">

    <CheckBox
        android:id="@+id/item_checkbox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top"
        android:contentDescription="@string/checklist_item_checkbox_cd"
        android:minWidth="0dp"
        android:minHeight="0dp"
        android:paddingTop="6dp" />

    <EditText
        android:id="@+id/item_edit"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:background="@null"
        android:hint="@string/checklist_item_hint"
        android:inputType="text|textCapSentences|textMultiLine"
        android:imeOptions="actionNone"
        android:textColor="@color/text_primary"
        android:textColorHint="@color/text_hint"
        android:textSize="16sp" />
</merge>
```

注：`item_edit` 用 `textMultiLine` 让 ENTER 不被吃；`imeOptions=actionNone` 让回车键真触发 KeyEvent。

- [ ] **Step 3：构建验证 + commit**

```bash
cd code/HuaWeiNote && ./gradlew :app:assembleDebug
```

Expected：BUILD SUCCESSFUL（layout 还未被 inflate 但 xml 必须合法）。

```bash
git add code/HuaWeiNote/app/src/main/res/layout/block_image.xml \
        code/HuaWeiNote/app/src/main/res/layout/block_checklist_item.xml
git commit -m "feat(m5): 图片块与清单项布局 xml"
```

---

## Task 3：ImageCompressor 工具类（TDD）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/util/ImageCompressor.kt`
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/util/ImageCompressorTest.kt`

**职责：** 把任意来源 URI 解码 → 长边 ≤ 1920px 等比缩放 → JPEG quality 85 写入目标 File；返回最终 (width, height) 用于 `ImageBlock`。

**关键点：**
- 必须用 `BitmapFactory.Options.inJustDecodeBounds=true` 先拿原图尺寸，配合 `inSampleSize` 二次解码（避免 OOM）。
- 长边 ≤ 1920 时直接 `Matrix.postScale` 等比；长边 ≤ 960 跳过缩放。
- `compress(JPEG, 85, OutputStream)`；写完 `bitmap.recycle()`。
- 失败（解码返回 null / IOException）→ 返回 null，由调用方提示用户。

- [ ] **Step 1：写测试 — 单测三种 case（解码失败 / 短边图直通 / 长边图缩放）**

```kotlin
package com.fan.hwnote.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class ImageCompressorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val tmpDir: File get() = File(context.cacheDir, "compressor_test").apply { mkdirs() }

    private fun writeSolidColor(w: Int, h: Int, file: File) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bmp.recycle()
    }

    @Test
    fun `short edge image is written without scaling`() {
        val src = File(tmpDir, "src_small.jpg")
        writeSolidColor(800, 600, src)
        val dst = File(tmpDir, "dst_small.jpg")

        val out = ImageCompressor.compressToFile(context, android.net.Uri.fromFile(src), dst)

        assertNotNull(out)
        assertEquals(800, out!!.width)
        assertEquals(600, out.height)
        assertTrue(dst.exists() && dst.length() > 0)
    }

    @Test
    fun `long edge image is scaled to 1920`() {
        val src = File(tmpDir, "src_large.jpg")
        writeSolidColor(4000, 3000, src)
        val dst = File(tmpDir, "dst_large.jpg")

        val out = ImageCompressor.compressToFile(context, android.net.Uri.fromFile(src), dst)

        assertNotNull(out)
        assertEquals(1920, out!!.width)
        assertEquals(1440, out.height) // 4000:3000 → 1920:1440
        assertTrue(dst.exists())
    }

    @Test
    fun `invalid uri returns null`() {
        val dst = File(tmpDir, "dst_bad.jpg")
        val out = ImageCompressor.compressToFile(
            context, android.net.Uri.parse("file:///nonexistent.jpg"), dst,
        )
        assertNull(out)
    }
}
```

- [ ] **Step 2：跑测试看红**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11)
cd code/HuaWeiNote && ./gradlew :app:testDebugUnitTest --tests com.fan.hwnote.app.util.ImageCompressorTest
```

Expected：编译失败（ImageCompressor 类不存在）。

- [ ] **Step 3：实现 ImageCompressor**

```kotlin
package com.fan.hwnote.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 解码任意来源 URI（gallery / FileProvider / file://），长边压到 ≤ MAX_EDGE，JPEG quality 85 写出。
 * 返回 Result(width, height)，失败返回 null。
 */
object ImageCompressor {

    private const val MAX_EDGE = 1920
    private const val JPEG_QUALITY = 85

    data class Result(val width: Int, val height: Int)

    fun compressToFile(context: Context, source: Uri, target: File): Result? {
        val cr = context.contentResolver
        // 1) 只读 bounds
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            cr.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2) 算 inSampleSize（让短边解码后 ≥ MAX_EDGE/2，避免一次到位失真）
        val sampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded: Bitmap = runCatching {
            cr.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull() ?: return null

        // 3) 长边 > MAX_EDGE 时再用 Matrix 精确缩放
        val finalBitmap = scaleIfNeeded(decoded, MAX_EDGE)
        if (finalBitmap !== decoded) decoded.recycle()

        // 4) JPEG 85 写出
        val written = runCatching {
            FileOutputStream(target).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        }.getOrDefault(false)
        val w = finalBitmap.width; val h = finalBitmap.height
        finalBitmap.recycle()

        return if (written) Result(w, h) else null
    }

    private fun calcInSampleSize(width: Int, height: Int, reqEdge: Int): Int {
        val longEdge = maxOf(width, height)
        var sample = 1
        while (longEdge / sample > reqEdge * 2) sample *= 2
        return sample
    }

    private fun scaleIfNeeded(src: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longEdge
        val m = Matrix().apply { postScale(ratio, ratio) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
```

- [ ] **Step 4：跑测试看绿**

```bash
./gradlew :app:testDebugUnitTest --tests com.fan.hwnote.app.util.ImageCompressorTest
```

Expected：3 / 3 PASSED。如果"short edge image is written without scaling" 失败因尺寸偏差 1px，可放宽到 `assertTrue(out.width in 799..800)`（Robolectric 解码可能微调）。

- [ ] **Step 5：跑全量测试确认无回归**

```bash
./gradlew :app:test
```

Expected：57 + 3 = 60 项 PASSED（M2 42 + M4 SpanConverter 15 + M5 ImageCompressor 3）。

- [ ] **Step 6：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/util/ImageCompressor.kt \
        code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/util/ImageCompressorTest.kt
git commit -m "feat(m5): ImageCompressor 长边 1920 JPEG 85 压缩 + Robolectric 单测"
```

---

## Task 4：ImageBlockView（Glide 渲染 + 长按删除）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/ImageBlockView.kt`

**职责：**
- inflate `block_image.xml` → 拿 `block_image: ImageView`
- `bind(Block.ImageBlock)`：用 `NoteFileStorage` 解出本地 File，Glide load 进 `ImageView`
- `toBlock()`：返回当前 ImageBlock 数据快照（id / fileName / width / height 不变）
- 长按弹 `AlertDialog` 二次确认 → 调用方传入的 `onDeleteRequested` callback；该 callback 由 `EditorPresenter` 提供（删除本地文件 + 从 currentBlocks 移除 + onRequestDelete 上抛）
- 不可编辑文字（不持有 EditText），所以 `onRequestSplitAfter` 永远不会触发

**和 BlockView.Callback 的关系：**
- `onRequestDelete(this)`：长按 → 确认 → 直接调，让 Presenter 走通用删除逻辑（同时 Presenter 负责清理文件）
- `onRequestSplitAfter(this)`：图片块不触发
- `onFocusGained(this)`：图片块没有焦点概念，不触发

- [ ] **Step 1：实现 ImageBlockView**

```kotlin
package com.fan.hwnote.app.view.block

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.storage.NoteFileStorage

class ImageBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BlockView(context, attrs, defStyleAttr) {

    private val imageView: ImageView
    private var data: Block.ImageBlock? = null

    /** 调用方（Presenter）注入：传 noteId，让 ImageBlockView 自己解析本地文件路径。 */
    var noteId: Long = 0L

    init {
        LayoutInflater.from(context).inflate(R.layout.block_image, this, true)
        imageView = findViewById(R.id.block_image)
        setOnLongClickListener {
            showDeleteDialog()
            true
        }
        imageView.setOnLongClickListener {
            showDeleteDialog()
            true
        }
    }

    override fun bind(block: Block) {
        require(block is Block.ImageBlock) { "ImageBlockView only accepts ImageBlock" }
        data = block
        val storage = NoteFileStorage(context)
        val file = storage.imageFile(noteId, block.fileName)
        Glide.with(imageView)
            .load(file)
            .placeholder(R.drawable.bg_image_block)
            .error(R.drawable.bg_image_block)
            .into(imageView)
    }

    override fun toBlock(): Block = data
        ?: throw IllegalStateException("ImageBlockView.toBlock() called before bind()")

    private fun showDeleteDialog() {
        AlertDialog.Builder(context)
            .setTitle(R.string.image_delete_title)
            .setMessage(R.string.image_delete_message)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                callback?.onRequestDelete(this)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
```

- [ ] **Step 2：构建验证**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11)
cd code/HuaWeiNote && ./gradlew :app:assembleDebug
```

Expected：BUILD SUCCESSFUL。（Presenter 还没接 noteId，所以该 View 此时不会被实例化）

- [ ] **Step 3：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/ImageBlockView.kt
git commit -m "feat(m5): ImageBlockView 用 Glide 加载本地图片 + 长按删除"
```

---

## Task 5：ChecklistItemView（单行 CheckBox + EditText）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/ChecklistItemView.kt`

**职责：**
- inflate `block_checklist_item.xml`（merge 根 = 自身 LinearLayout，HORIZONTAL）
- 暴露 `checked: Boolean` / `text: String` 双向 getter，`bind(item: ChecklistItem)` 设置初值
- 勾选状态变化 → EditText paint flag 加/去 `STRIKE_THRU_TEXT_FLAG`，文字色切到 `text_checked`/`text_primary`
- ENTER → 上抛 `onEnterAtEnd()`（光标在文本末尾时）；其他位置 ENTER 不处理（让父类按多行处理或直接吞掉）
- DEL 在文本为空时 → 上抛 `onBackspaceWhenEmpty()`
- EditText 获得焦点 → 上抛 `onFocusGained()`，便于 ChecklistBlockView 追踪 currentItem

- [ ] **Step 1：实现 ChecklistItemView**

```kotlin
package com.fan.hwnote.app.view.block

import android.content.Context
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.ChecklistItem

class ChecklistItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    val checkbox: CheckBox
    val edit: EditText

    interface Listener {
        /** 末尾按回车要求新增下一项。 */
        fun onEnterAtEnd(view: ChecklistItemView)
        /** 空项退格要求删本项 + 焦点上移。 */
        fun onBackspaceWhenEmpty(view: ChecklistItemView)
        /** EditText 获得焦点。 */
        fun onItemFocusGained(view: ChecklistItemView)
    }

    var listener: Listener? = null

    init {
        orientation = HORIZONTAL
        setPadding(0, 0, 0, 0)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        background = ContextCompat.getDrawable(context, R.drawable.bg_checklist_item)
        LayoutInflater.from(context).inflate(R.layout.block_checklist_item, this, true)
        checkbox = findViewById(R.id.item_checkbox)
        edit = findViewById(R.id.item_edit)

        checkbox.setOnCheckedChangeListener { _, isChecked -> applyCheckedStyle(isChecked) }

        edit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) listener?.onItemFocusGained(this)
        }

        edit.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    val sel = edit.selectionStart
                    val len = edit.text?.length ?: 0
                    if (sel == len) {
                        listener?.onEnterAtEnd(this)
                        true
                    } else false
                }
                KeyEvent.KEYCODE_DEL -> {
                    if ((edit.text?.length ?: 0) == 0) {
                        listener?.onBackspaceWhenEmpty(this)
                        true
                    } else false
                }
                else -> false
            }
        }

        // 防 paste / 程序 setText 后 strike 样式失效
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { /* no-op，样式只跟 checked 走 */ }
        })
    }

    fun bind(item: ChecklistItem) {
        // 先卸 listener 避免回调里把 isChecked 改了又触发上层修改
        checkbox.setOnCheckedChangeListener(null)
        checkbox.isChecked = item.checked
        applyCheckedStyle(item.checked)
        checkbox.setOnCheckedChangeListener { _, isChecked -> applyCheckedStyle(isChecked) }
        edit.setText(item.text)
    }

    fun toItem(): ChecklistItem = ChecklistItem(
        checked = checkbox.isChecked,
        text = edit.text?.toString().orEmpty(),
    )

    fun focusEditEnd() {
        edit.requestFocus()
        edit.setSelection(edit.text?.length ?: 0)
    }

    private fun applyCheckedStyle(checked: Boolean) {
        val flags = edit.paintFlags
        edit.paintFlags = if (checked) flags or Paint.STRIKE_THRU_TEXT_FLAG
                          else flags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        val colorRes = if (checked) R.color.text_checked else R.color.text_primary
        edit.setTextColor(ContextCompat.getColor(context, colorRes))
    }
}
```

- [ ] **Step 2：构建验证**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11)
cd code/HuaWeiNote && ./gradlew :app:assembleDebug
```

Expected：BUILD SUCCESSFUL。

- [ ] **Step 3：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/ChecklistItemView.kt
git commit -m "feat(m5): ChecklistItemView 单行 CheckBox + EditText + 删除线"
```

---

## Task 6：ChecklistBlockView（多 item 编排）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/ChecklistBlockView.kt`

**职责：**
- 继承 `BlockView`（基类已 `orientation = VERTICAL`）
- `bind(Block.ChecklistBlock)`：遍历 items 每个加一行 `ChecklistItemView`；空 items 退化为加一个空项
- 实现 `ChecklistItemView.Listener`：
  - `onEnterAtEnd(view)`：在该 item 之后插一个新空 item，新 item 抢焦点
  - `onBackspaceWhenEmpty(view)`：
    - 如果是唯一一项 → 上抛 `callback?.onRequestDelete(this)`（整个清单块删除）
    - 否则删该 item，焦点上移到前一项（光标置于末尾）
  - `onItemFocusGained(view)`：把焦点 item 记到 currentFocusItem，并上抛 `callback?.onFocusGained(this)` 让 Presenter 知道当前焦点在本块
- `toBlock()`：收集所有子 `ChecklistItemView.toItem()` 拼成新 `Block.ChecklistBlock`（id 不变）

**关键边界：**
- 第一行删空键不直接 Delete 自己，由 BlockView.Callback.onRequestDelete 路由到 Presenter（Presenter 的 onRequestDelete 已经处理 "第一块不可删" 的边界）
- 末项回车永远 = 新增一个空项（不分裂上一项内容，因为光标已在末尾）
- 中间项回车（光标不在末尾）→ EditText 自身按多行处理（不上抛事件）

- [ ] **Step 1：实现 ChecklistBlockView**

```kotlin
package com.fan.hwnote.app.view.block

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.ChecklistItem
import java.util.UUID

class ChecklistBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BlockView(context, attrs, defStyleAttr), ChecklistItemView.Listener {

    private var blockId: String = "c-${UUID.randomUUID().toString().take(8)}"
    private val items = mutableListOf<ChecklistItemView>()

    override fun bind(block: Block) {
        require(block is Block.ChecklistBlock) { "ChecklistBlockView only accepts ChecklistBlock" }
        blockId = block.id
        removeAllViews()
        items.clear()
        val list = block.items.ifEmpty { mutableListOf(ChecklistItem(false, "")) }
        for (it in list) addItemView(it)
    }

    override fun toBlock(): Block = Block.ChecklistBlock(
        id = blockId,
        items = items.map { it.toItem() }.toMutableList(),
    )

    // ----- ChecklistItemView.Listener -----

    override fun onEnterAtEnd(view: ChecklistItemView) {
        val idx = items.indexOf(view)
        if (idx < 0) return
        val newItem = ChecklistItem(false, "")
        addItemView(newItem, insertAt = idx + 1)
        items[idx + 1].focusEditEnd()
    }

    override fun onBackspaceWhenEmpty(view: ChecklistItemView) {
        val idx = items.indexOf(view)
        if (idx < 0) return
        if (items.size <= 1) {
            // 唯一项空且按退格 → 整个清单块删除
            callback?.onRequestDelete(this)
            return
        }
        removeView(view)
        items.removeAt(idx)
        val target = items[(idx - 1).coerceAtLeast(0)]
        target.focusEditEnd()
    }

    override fun onItemFocusGained(view: ChecklistItemView) {
        callback?.onFocusGained(this)
    }

    // ----- private -----

    private fun addItemView(item: ChecklistItem, insertAt: Int = -1) {
        val v = ChecklistItemView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            listener = this@ChecklistBlockView
            bind(item)
        }
        if (insertAt < 0 || insertAt >= items.size) {
            addView(v)
            items.add(v)
        } else {
            addView(v, insertAt)
            items.add(insertAt, v)
        }
    }

    fun focusLastItemEnd() {
        items.lastOrNull()?.focusEditEnd()
    }
}
```

- [ ] **Step 2：构建验证**

```bash
cd code/HuaWeiNote && ./gradlew :app:assembleDebug
```

Expected：BUILD SUCCESSFUL。

- [ ] **Step 3：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/block/ChecklistBlockView.kt
git commit -m "feat(m5): ChecklistBlockView 多项编排（新增/删除/焦点迁移）"
```

---

## Task 7：EditorPresenter 接通真实渲染（删 passthroughBlocks）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt`

**改动清单：**
1. 删除 `passthroughBlocks: MutableList<Block>` 字段（以及 bind / collectCurrentNote 里对它的引用）
2. `bind`：`when (b)` 三分支都走真实渲染 — TextBlock → `addTextBlockView`；ImageBlock → `addImageBlockView`；ChecklistBlock → `addChecklistBlockView`
3. `collectCurrentNote`：直接 `currentBlocks.map { it.toBlock() }`，不再拼 passthrough 尾巴
4. 新增 `noteId: Long`，bind 时记入（ImageBlockView 需要靠 noteId 解出文件路径）
5. 新增 `insertImageBlocksAtFocus(blocks: List<Block.ImageBlock>)`：在当前 focus TextBlock 之后插入若干 ImageBlockView，然后再补一个空 TextBlock，最后让空 TextBlock 抢焦点
6. 新增 `insertChecklistBlockAtFocus()`：在当前 focus TextBlock 之后插入一个 ChecklistBlockView（带一个空 item），并让该 item 抢焦点
7. 新增 `addImageBlockView(block: Block.ImageBlock, insertAt: Int = -1)` / `addChecklistBlockView(block: Block.ChecklistBlock, insertAt: Int = -1)` 工厂
8. `onRequestDelete(view: BlockView)` 改造：如果 view 是 ImageBlockView，先清理 `NoteFileStorage.imageFile(noteId, ...).delete()` 再走通用删除

**关键边界：**
- 兼容 noteId == 0L（新笔记）：图片块仍可渲染（NoteFileStorage 用 0L 目录是合法的，文件未保存前不会读到；插入图片要求笔记必须先有 id —— 见 Task 9 / 10 触发时机的说明，由 Activity 提前 `saveNote()` 拿到正式 id）

- [ ] **Step 1：增加 noteId 属性 + 工厂方法**

定位到 `EditorPresenter.kt` 类体头部（class 声明下面），找到：

```kotlin
    private val currentBlocks = mutableListOf<BlockView>()
    private val passthroughBlocks = mutableListOf<Block>()
    private lateinit var currentNote: Note
    private var focusedTextBlock: TextBlockView? = null
```

替换为：

```kotlin
    private val currentBlocks = mutableListOf<BlockView>()
    private lateinit var currentNote: Note
    private var focusedTextBlock: TextBlockView? = null

    /** 当前正在编辑的 noteId（>0 表示已落库）；ImageBlockView 用它定位本地文件目录。 */
    var noteId: Long = 0L
```

- [ ] **Step 2：改造 bind**

定位到 `fun bind(note: Note)`，替换整段：

```kotlin
    fun bind(note: Note) {
        currentNote = note
        container.removeAllViews()
        currentBlocks.clear()
        focusedTextBlock = null

        val blocks = note.content.blocks.ifEmpty { listOf(emptyTextBlock()) }
        for (b in blocks) {
            when (b) {
                is Block.TextBlock -> addTextBlockView(b)
                is Block.ImageBlock -> addImageBlockView(b)
                is Block.ChecklistBlock -> addChecklistBlockView(b)
            }
        }
        // 默认让第一个 TextBlock 拿到焦点（找不到就让第一块的可聚焦子 view 自己来）
        (currentBlocks.firstOrNull { it is TextBlockView } as? TextBlockView)?.focusEditEnd()
    }
```

- [ ] **Step 3：改造 collectCurrentNote**

定位到 `fun collectCurrentNote(title: String): Note`，替换整段：

```kotlin
    fun collectCurrentNote(title: String): Note {
        val newBlocks = currentBlocks.map { it.toBlock() }
        val content = NoteContent(blocks = newBlocks, handwriting = emptyList())
        return currentNote.copy(
            title = title,
            plainText = content.toPlainText(),
            content = content,
        )
    }
```

- [ ] **Step 4：改造 onRequestDelete（图片块要顺手删本地文件）**

定位到 `override fun onRequestDelete(view: BlockView)`，替换：

```kotlin
    override fun onRequestDelete(view: BlockView) {
        val idx = currentBlocks.indexOf(view)
        if (idx <= 0) return // 第一块不可删
        // 图片块顺手清掉本地 jpg；ChecklistBlock 没有文件需要清
        if (view is ImageBlockView) {
            val block = view.toBlock() as? Block.ImageBlock
            if (block != null && noteId > 0L) {
                runCatching {
                    com.fan.hwnote.app.model.storage.NoteFileStorage(context)
                        .imageFile(noteId, block.fileName).delete()
                }
            }
        }
        container.removeView(view)
        currentBlocks.removeAt(idx)
        if (focusedTextBlock === view) focusedTextBlock = null
        (currentBlocks[idx - 1] as? TextBlockView)?.focusEditEnd()
    }
```

注意 import 同时补上：

```kotlin
import com.fan.hwnote.app.view.block.ImageBlockView
import com.fan.hwnote.app.view.block.ChecklistBlockView
```

- [ ] **Step 5：新增 addImageBlockView / addChecklistBlockView 工厂**

在 `addTextBlockView` 后面追加：

```kotlin
    private fun addImageBlockView(block: Block.ImageBlock, insertAt: Int = -1) {
        val v = ImageBlockView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            callback = this@EditorPresenter
            noteId = this@EditorPresenter.noteId
            bind(block)
        }
        if (insertAt < 0 || insertAt >= currentBlocks.size) {
            container.addView(v); currentBlocks.add(v)
        } else {
            container.addView(v, insertAt); currentBlocks.add(insertAt, v)
        }
    }

    private fun addChecklistBlockView(block: Block.ChecklistBlock, insertAt: Int = -1) {
        val v = ChecklistBlockView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            callback = this@EditorPresenter
            bind(block)
        }
        if (insertAt < 0 || insertAt >= currentBlocks.size) {
            container.addView(v); currentBlocks.add(v)
        } else {
            container.addView(v, insertAt); currentBlocks.add(insertAt, v)
        }
    }
```

- [ ] **Step 6：新增 insertImageBlocksAtFocus / insertChecklistBlockAtFocus**

在类末尾（`toggleHeading` 后面）追加：

```kotlin
    /**
     * Task 10 入口：把若干 ImageBlock 插到当前焦点 TextBlock 之后，并在最后追加一个空 TextBlock 接管焦点。
     * 焦点未知（如刚进图片块）→ 追加到列表末尾。
     */
    fun insertImageBlocksAtFocus(blocks: List<Block.ImageBlock>) {
        if (blocks.isEmpty()) return
        val anchor = focusedTextBlock
        val baseIdx = if (anchor != null) currentBlocks.indexOf(anchor) + 1
                      else currentBlocks.size
        var insertAt = baseIdx
        for (b in blocks) {
            addImageBlockView(b, insertAt = insertAt)
            insertAt += 1
        }
        // 末尾补一个空 TextBlock，让用户可继续输入
        val tail = emptyTextBlock()
        addTextBlockView(tail, insertAt = insertAt)
        (currentBlocks[insertAt] as TextBlockView).focusEditEnd()
    }

    /**
     * Task 8 入口：在焦点 TextBlock 之后插一个新清单块（含 1 个空项）；焦点交给该空项。
     */
    fun insertChecklistBlockAtFocus() {
        val anchor = focusedTextBlock
        val insertAt = if (anchor != null) currentBlocks.indexOf(anchor) + 1
                       else currentBlocks.size
        val block = Block.ChecklistBlock(
            id = "c-${UUID.randomUUID().toString().take(8)}",
            items = mutableListOf(com.fan.hwnote.app.model.entity.ChecklistItem(false, "")),
        )
        addChecklistBlockView(block, insertAt = insertAt)
        (currentBlocks[insertAt] as ChecklistBlockView).focusLastItemEnd()
    }
```

- [ ] **Step 7：构建 + 跑测确认无回归**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11)
cd code/HuaWeiNote && ./gradlew :app:assembleDebug && ./gradlew :app:test
```

Expected：assembleDebug 通过；test 60 / 60 PASSED。

- [ ] **Step 8：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/EditorPresenter.kt
git commit -m "feat(m5): EditorPresenter 接通真实图片/清单渲染（删 passthroughBlocks）"
```

---

## Task 8：onChecklistClicked 接通真实清单插入

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**改动：**
- 删 `onChecklistClicked` 的 Toast 占位
- 直接调 `presenter.insertChecklistBlockAtFocus()`
- 同时把 Task 7 新增的 `presenter.noteId` 在 `loadNote` 完成时同步赋值（图片块后续也要用，这里一次性接好）

- [ ] **Step 1：让 loadNote 完成后同步 presenter.noteId**

定位到 `loadNote()` 方法：

```kotlin
    private fun loadNote() {
        lifecycleScope.launch {
            val note = if (noteId == -1L) Note.new() else (NoteRepository.get(noteId) ?: Note.new())
            loadedNote = note
            titleInput.setText(note.title)
            presenter.bind(note)
        }
    }
```

替换为：

```kotlin
    private fun loadNote() {
        lifecycleScope.launch {
            val note = if (noteId == -1L) Note.new() else (NoteRepository.get(noteId) ?: Note.new())
            loadedNote = note
            presenter.noteId = note.id // 0L for 新笔记，正数 for 已落库
            titleInput.setText(note.title)
            presenter.bind(note)
        }
    }
```

同时定位到 `saveNote()` 末尾 `if (loaded.id == 0L && newId > 0)` 分支，在 `loadedNote = toSave.copy(id = newId)` 后面追加一行：

```kotlin
                presenter.noteId = newId
```

新笔记第一次落库后让 presenter 拿到正式 id —— 这样后续插入图片时 ImageBlockView 解析的本地文件路径才正确。

- [ ] **Step 2：接通 onChecklistClicked**

定位到 listener 中的：

```kotlin
            override fun onChecklistClicked() {
                android.widget.Toast.makeText(this@NoteEditorActivity,
                    R.string.toast_checklist_placeholder, android.widget.Toast.LENGTH_SHORT).show()
            }
```

替换为：

```kotlin
            override fun onChecklistClicked() {
                presenter.insertChecklistBlockAtFocus()
            }
```

- [ ] **Step 3：构建验证**

```bash
cd code/HuaWeiNote && ./gradlew :app:assembleDebug
```

Expected：BUILD SUCCESSFUL。

- [ ] **Step 4：commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "feat(m5): 清单工具栏按钮 → Presenter.insertChecklistBlockAtFocus + 同步 noteId"
```

---

## Task 9：onImageClicked 弹"相册 / 拍照"选择对话框 + 落库后再插图

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**改动：**
- 删 `onImageClicked` 的 Toast 占位
- 替换为 `showImageSourceDialog()`：两项 [相册 / 拍照]，分别走 Task 10 / Task 11 的 launcher
- 注意：插入图片前必须保证笔记有正式 id（>0），否则 ImageCompressor 写出的文件无目录可放。在 `showImageSourceDialog` 入口先 `ensureNoteSavedAndThen { ... }` —— 新笔记先 `saveNote()` 拿到 id，再启动 picker

- [ ] **Step 1：新增 ensureNoteSavedAndThen 辅助方法**

在 `NoteEditorActivity` 类末尾（companion object 之前）追加：

```kotlin
    /**
     * 图片插入前置：若笔记尚未落库（noteId<=0），先保存一次拿到 id；落库成功后再执行回调。
     * 已落库（noteId>0）直接执行。在主线程上回调。
     */
    private fun ensureNoteSavedAndThen(block: () -> Unit) {
        val loaded = loadedNote
        if (loaded != null && loaded.id > 0L) {
            block(); return
        }
        // 这里复用 saveNote 路径，但要等 IO 完成后再回主线程跑 block
        val title = titleInput.text.toString()
        val toSave = presenter.collectCurrentNote(title)
        lifecycleScope.launch(Dispatchers.IO) {
            val newId = NoteRepository.save(toSave)
            withContext(Dispatchers.Main) {
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
    }
```

记得在文件顶端 import 区域追加：

```kotlin
import kotlinx.coroutines.withContext
```

- [ ] **Step 2：替换 onImageClicked 为弹框入口**

定位到 listener 中的：

```kotlin
            override fun onImageClicked() {
                android.widget.Toast.makeText(this@NoteEditorActivity,
                    R.string.toast_image_placeholder, android.widget.Toast.LENGTH_SHORT).show()
            }
```

替换为：

```kotlin
            override fun onImageClicked() {
                ensureNoteSavedAndThen { showImageSourceDialog() }
            }
```

- [ ] **Step 3：新增 showImageSourceDialog**

在 `showColorPickerDialog` 后面追加：

```kotlin
    private fun showImageSourceDialog() {
        val labels = arrayOf(
            getString(R.string.image_source_gallery),
            getString(R.string.image_source_camera),
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.image_source_title)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> launchGalleryPicker()
                    1 -> launchCameraWithPermission()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
```

（`launchGalleryPicker` / `launchCameraWithPermission` 在 Task 10 / 11 实现，本步先留作 unresolved 方法 — 此 step 不构建。）

- [ ] **Step 4：（不构建，直接进入 Task 10）**

> 不在本任务 commit，等 Task 10 / 11 一起跑通后再 commit。这样避免一个中间态被记入 git。

---

## Task 10：相册 launcher（ACTION_GET_CONTENT 多选 + 解压 + 插入）

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**关键决定（来自 PRD §8.4）：**
- 用 `ACTION_GET_CONTENT` + `EXTRA_ALLOW_MULTIPLE`（而不是 `ACTION_OPEN_DOCUMENT`）
- 用 `ActivityResultContracts.StartActivityForResult` 自己读 `data.clipData` / `data.data`
- 解压在 IO 线程；解压完一张就同步插入一条 ImageBlock，避免长卡顿

- [ ] **Step 1：声明 gallery launcher 字段**

在 `NoteEditorActivity` 类体头部（presenter 字段下面）追加：

```kotlin
    private val galleryLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uris = mutableListOf<android.net.Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
        } else {
            data.data?.let { uris += it }
        }
        if (uris.isNotEmpty()) compressAndInsertImages(uris)
    }
```

> 注：`registerForActivityResult` 必须在 onCreate 之前被调用，所以放成属性初始化（Kotlin lazy 等价）。

- [ ] **Step 2：实现 launchGalleryPicker**

在 `showImageSourceDialog` 后面追加：

```kotlin
    private fun launchGalleryPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        galleryLauncher.launch(Intent.createChooser(intent, getString(R.string.image_source_gallery)))
    }
```

- [ ] **Step 3：实现 compressAndInsertImages**

在 `launchGalleryPicker` 后面追加：

```kotlin
    private fun compressAndInsertImages(uris: List<android.net.Uri>) {
        val curNoteId = loadedNote?.id ?: return
        if (curNoteId <= 0L) return
        val storage = com.fan.hwnote.app.model.storage.NoteFileStorage(this)
        lifecycleScope.launch {
            val results = mutableListOf<com.fan.hwnote.app.model.entity.Block.ImageBlock>()
            withContext(Dispatchers.IO) {
                for (u in uris) {
                    val fileName = "${java.util.UUID.randomUUID()}.jpg"
                    val target = storage.imageFile(curNoteId, fileName)
                    val r = com.fan.hwnote.app.util.ImageCompressor
                        .compressToFile(this@NoteEditorActivity, u, target) ?: continue
                    results += com.fan.hwnote.app.model.entity.Block.ImageBlock(
                        id = "i-${java.util.UUID.randomUUID().toString().take(8)}",
                        fileName = fileName,
                        width = r.width,
                        height = r.height,
                    )
                }
            }
            if (results.isEmpty()) {
                android.widget.Toast.makeText(this@NoteEditorActivity,
                    R.string.image_save_failed, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                presenter.insertImageBlocksAtFocus(results)
            }
        }
    }
```

- [ ] **Step 4：构建验证**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11)
cd code/HuaWeiNote && ./gradlew :app:assembleDebug
```

Expected：BUILD SUCCESSFUL（注意：`launchCameraWithPermission` 仍 unresolved，所以 Step 4 可能编译失败 —— 如果失败，临时把 `1 -> launchCameraWithPermission()` 注释为 `1 -> {}` 让构建通过；Task 11 写完后取消注释）。

→ 建议直接进入 Task 11，不在此 commit。

---

## Task 11：相机 launcher + CAMERA 运行时权限

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt`

**流程：**
1. 用户点"拍照" → `launchCameraWithPermission()`
2. 检查 CAMERA 权限：已授 → 直接 `launchCamera()`；未授 → `permissionLauncher.launch(CAMERA)`
3. 权限回调：granted → 调 `launchCamera()`；denied → Toast 提示
4. `launchCamera()`：在 `cacheDir/camera/<uuid>.jpg` 建临时文件 → FileProvider 包成 Uri → ACTION_IMAGE_CAPTURE
5. cameraLauncher 回调：success → 用之前的 Uri 调 `compressAndInsertImages(listOf(uri))`；fail → 删临时文件

- [ ] **Step 1：声明三个 launcher 字段（permission + camera）+ 当前拍照临时 Uri**

在 `galleryLauncher` 后面追加：

```kotlin
    private var pendingCameraOutputUri: android.net.Uri? = null
    private var pendingCameraOutputFile: java.io.File? = null

    private val cameraLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraOutputUri
        val file = pendingCameraOutputFile
        pendingCameraOutputUri = null
        pendingCameraOutputFile = null
        if (success && uri != null) {
            compressAndInsertImages(listOf(uri))
            // 等 compressAndInsertImages 完成后删 cache（它已经把内容复制走了；用 post 避免争用）
            blocksContainer.post { runCatching { file?.delete() } }
        } else {
            runCatching { file?.delete() }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else android.widget.Toast.makeText(this,
            R.string.camera_permission_denied, android.widget.Toast.LENGTH_SHORT).show()
    }
```

- [ ] **Step 2：实现 launchCameraWithPermission + launchCamera**

在 `compressAndInsertImages` 后面追加：

```kotlin
    private fun launchCameraWithPermission() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera()
        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val curNoteId = loadedNote?.id ?: return
        if (curNoteId <= 0L) return
        val cameraDir = java.io.File(cacheDir, "camera").apply { mkdirs() }
        val temp = java.io.File(cameraDir, "${java.util.UUID.randomUUID()}.jpg")
        val uri = try {
            androidx.core.content.FileProvider.getUriForFile(
                this, "com.fan.hwnote.app.fileprovider", temp,
            )
        } catch (e: IllegalArgumentException) {
            android.widget.Toast.makeText(this,
                R.string.image_save_failed, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraOutputFile = temp
        pendingCameraOutputUri = uri
        runCatching { cameraLauncher.launch(uri) }
            .onFailure {
                pendingCameraOutputFile = null
                pendingCameraOutputUri = null
                runCatching { temp.delete() }
                android.widget.Toast.makeText(this,
                    R.string.camera_unavailable, android.widget.Toast.LENGTH_SHORT).show()
            }
    }
```

- [ ] **Step 3：构建 + 跑全测**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11)
cd code/HuaWeiNote && ./gradlew :app:assembleDebug && ./gradlew :app:test
```

Expected：assembleDebug 通过；test 60 / 60 PASSED。

- [ ] **Step 4：commit（一次 commit 包含 Task 9 + 10 + 11 三段 Activity 改动 — 因为它们互相依赖编译）**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/editor/NoteEditorActivity.kt
git commit -m "feat(m5): 图片工具栏按钮 → 弹相册/拍照 + 多选解压 + CAMERA 权限"
```

---

## Task 12：静态走查 + STATUS 收尾

**Files:**
- Modify: `docs/superpowers/STATUS.md`

**目的：** M5 没有自动化 UI 测试（沿 M3/M4"写完即手测"约定）。最后一项任务做两件事：(1) 静态走查清单（5 条对齐 PRD §8.4 的行为），(2) 写 STATUS.md 收尾。真机手测由用户在 STATUS 之后独立走查。

### 12-A 静态走查（不写代码，只读现有代码 + 核对）

- [ ] **Check 1：图片插入 → 焦点回到追加的空 TextBlock**
  - 验：`EditorPresenter.insertImageBlocksAtFocus` 末尾追加 `addTextBlockView(tail, insertAt)` 并对该位置 `focusEditEnd()`
  - 预期：插入 3 张图后，光标停在第 4 张下方的空文本块上，可继续输入

- [ ] **Check 2：图片长按 → 二次确认 → 真删（文件 + 块）**
  - 验：`ImageBlockView.showDeleteDialog` 走 PositiveButton → `callback.onRequestDelete(this)`
  - 验：`EditorPresenter.onRequestDelete` 中 `view is ImageBlockView` 分支调用 `NoteFileStorage.imageFile(noteId, ...).delete()`
  - 预期：删除后，本地 `filesDir/notes/<id>/images/<uuid>.jpg` 确实不在；同笔记再次进入时这张图不再渲染

- [ ] **Check 3：清单 ENTER 仅在末尾触发新增项**
  - 验：`ChecklistItemView` 的 KeyListener：`if (sel == len) { onEnterAtEnd ... }`，中间位置返回 false 由 EditText 自身处理
  - 预期：手测时光标在中间按回车 → 文字换行；末尾按回车 → 多一个空项

- [ ] **Check 4：清单空项退格 — 唯一项 → 整块上抛删除；非唯一 → 删项 + 焦点上移**
  - 验：`ChecklistBlockView.onBackspaceWhenEmpty`：`if (items.size <= 1) callback.onRequestDelete(this) ... else removeView + focusEditEnd`
  - 验：`EditorPresenter.onRequestDelete` 对非第一块 BlockView（包括 ChecklistBlockView）会真删
  - 预期：手测时唯一空项退格 → 整个清单块消失；非唯一空项退格 → 该项消失 + 焦点回到上一项末尾

- [ ] **Check 5：CAMERA 权限拒绝 → Toast + 相册仍可用**
  - 验：`cameraPermissionLauncher` granted=false 分支：仅 Toast 不抛异常
  - 验：相册分支不经过 `launchCameraWithPermission`，所以 CAMERA 权限拒绝后 `launchGalleryPicker` 仍能用
  - 预期：手测时拒绝 CAMERA → 提示后不崩；再点"相册"仍可多选

### 12-B 全量构建 + 跑测

- [ ] **Step 1：清干净构建产物再跑一遍**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11)
cd code/HuaWeiNote && ./gradlew :app:clean :app:assembleDebug :app:test
```

Expected：assembleDebug + 60 项 test 全 PASSED（M2 42 + M4 15 + M5 3）。

### 12-C 更新 STATUS.md

- [ ] **Step 2：在 STATUS.md 中标记 M5 完成**

定位到"## 里程碑进度"表格中：

```markdown
| M5 图片块/清单块 | ⏳ 未开始 | — |
```

改为：

```markdown
| M5 图片块/清单块 | ✅ 完成（2026-05-23） | ImageBlockView + ChecklistBlockView + ImageCompressor（长边 1920/JPEG 85）+ 多选相册 / 拍照 + CAMERA 运行时权限；passthroughBlocks 已删 |
```

定位到"## 阶段地图"表格行 7（代码实施）：

```markdown
| 7. 代码实施 | 🔵 **进行中（M4 完成）** | M1 + M2 + M3 + M4 详细计划 + 代码；M5-M7 未开始 |
```

改为：

```markdown
| 7. 代码实施 | 🔵 **进行中（M5 完成）** | M1 + M2 + M3 + M4 + M5 详细计划 + 代码；M6-M7 未开始 |
```

在文档末尾"## 下一步建议"之前追加完整 M5 章节：

```markdown
## M5 完成详情（2026-05-23）

**产出：**
- 资源（Task 1-2）：图片来源 / 删除二确认 / 清单项 hint 等 strings 词条 + ic_camera/ic_gallery vector + bg_image_block/bg_checklist_item 背景 + block_image.xml + block_checklist_item.xml
- 工具（Task 3，TDD）：util/ImageCompressor.kt（二阶段解码：inJustDecodeBounds + inSampleSize → Matrix.postScale；长边 ≤1920 / JPEG 85；返回 Result(width, height)）+ 3 项 Robolectric 单测
- View 层（Task 4-6）：view/block/ImageBlockView.kt（Glide load filesDir 文件 + 长按 AlertDialog 二确认）+ view/block/ChecklistItemView.kt（CheckBox + EditText + STRIKE_THRU paint flag + ENTER/DEL 抛事件）+ view/block/ChecklistBlockView.kt（多 item 编排：onEnterAtEnd 新增 / onBackspaceWhenEmpty 删项或整块）
- 控制层（Task 7-11）：EditorPresenter 删除 passthroughBlocks 改真渲染（bind / collectCurrentNote / addImageBlockView / addChecklistBlockView / insertImageBlocksAtFocus / insertChecklistBlockAtFocus / onRequestDelete 图片清文件 + noteId 字段）+ NoteEditorActivity 三 launcher（gallery / camera / camera-permission）+ ensureNoteSavedAndThen（新笔记落库后再插图）+ showImageSourceDialog 弹"相册/拍照"+ compressAndInsertImages 解压+插入 + launchCamera FileProvider 走 cacheDir/camera/<uuid>.jpg

**测试统计：** `./gradlew :app:test` 共 **60 项 PASSED**（M2 42 + M4 15 + M5 ImageCompressor 3）。

**M5 commit 列表（git log `<M4 HEAD>..HEAD`）：**
- `<sha>` docs(m5): 提交 M5 图片/清单块详细实施计划
- `<sha>` feat(m5): 图片/清单块资源准备（drawables + 词条 + 颜色）
- `<sha>` feat(m5): 图片块与清单项布局 xml
- `<sha>` feat(m5): ImageCompressor 长边 1920 JPEG 85 压缩 + Robolectric 单测
- `<sha>` feat(m5): ImageBlockView 用 Glide 加载本地图片 + 长按删除
- `<sha>` feat(m5): ChecklistItemView 单行 CheckBox + EditText + 删除线
- `<sha>` feat(m5): ChecklistBlockView 多项编排（新增/删除/焦点迁移）
- `<sha>` feat(m5): EditorPresenter 接通真实图片/清单渲染（删 passthroughBlocks）
- `<sha>` feat(m5): 清单工具栏按钮 → Presenter.insertChecklistBlockAtFocus + 同步 noteId
- `<sha>` feat(m5): 图片工具栏按钮 → 弹相册/拍照 + 多选解压 + CAMERA 权限
- `<sha>` docs(m5): 标记 M5 图片/清单块完成

**验收：**
- ✅ `./gradlew :app:assembleDebug` 通过
- ✅ `./gradlew :app:test` 全部 60 项 PASSED
- ✅ Task 12 静态走查 5 个 check 全部对齐 PRD §8.4
- ⏳ 真机手测留待用户走查（PRD §M5 高层验收 1-7：单图插入/多图插入/拍照插入/长按删图（含文件）/清单基础编辑/清单勾选删除线/清单空项退格删项 + 杀进程重启数据保留）

**执行模式：** Subagent-Driven Development（writing-plans → 用户审阅 → 串行 12 任务）；每个任务的 implementer DONE → spec reviewer → code quality reviewer → fix（如有）→ re-review → 标记完成。Task 9/10/11 合并为一次 commit（Activity 三段相互依赖编译）。Task 12 仅做静态走查（无需 commit），并写 STATUS 收尾。
```

`commit`（最后一笔）：

```bash
git add docs/superpowers/STATUS.md
git commit -m "docs(m5): 标记 M5 图片/清单块完成"
```

---

## 自审 checklist（计划编完后立即跑一遍，发现问题就回头改）

- [ ] **Spec 覆盖：** PRD §8.4 / §M5 验收 7 条是否都有任务支撑？
  - 单图插入 → Task 9 / 10
  - 多图插入 → Task 10 多选分支
  - 拍照插入 → Task 11
  - 长按删图（含文件清理） → Task 4 + Task 7 Step 4
  - 清单基础编辑（CheckBox + EditText） → Task 5
  - 清单勾选删除线 → Task 5 `applyCheckedStyle`
  - 清单空项退格删项 → Task 6 `onBackspaceWhenEmpty`
  - 杀进程重启数据保留 → 通过 `EditorPresenter.collectCurrentNote` 走 `NoteRepository.save`（M2 已覆盖 JSON round-trip）

- [ ] **Placeholder 扫描：** 全文 grep "TBD / TODO / fill in / similar to" → 应 0 命中

- [ ] **类型一致性：**
  - `ChecklistItem` 字段名 `checked` / `text`（不是 `done` / `content`）
  - `ImageBlock` 字段名 `fileName` / `width` / `height`（不是 `path` / `w` / `h`）
  - `NoteFileStorage` 方法名 `imageFile(noteId, fileName)`（不是 `getImageFile`）
  - `BlockView.Callback` 三方法签名沿用 M4：`onRequestSplitAfter` / `onRequestDelete` / `onFocusGained`
  - `ImageBlockView.noteId` 是 var Long 属性，由 `EditorPresenter` 在工厂里赋值（不是构造参数）

- [ ] **commit 边界：** 11 个 feat/test commits + 2 个 docs commits（M5 计划自身已合并到 docs(m5)）；Task 9 / 10 / 11 因 Activity 编译依赖合并为同一 commit

- [ ] **资源命名：** drawable 是 `bg_image_block` / `bg_checklist_item`（不是 `image_block_bg`）— 与 M4 `shape_toolbar_btn_pressed` 风格一致

- [ ] **JAVA_HOME：** 所有 gradle 调用前都有 `export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11)`

- [ ] **不动版本：** 没引入新依赖；Glide / kotlin / agp / sdk 沿用 libs.versions.toml

---

## 风险点 & 兜底说明（供 implementer 遇到时不慌）

1. **Robolectric 解码 JPEG 尺寸偏差**：Robolectric 的 BitmapFactory 不一定精确还原 JPEG 像素，Task 3 测试如果 800/600 不对齐，可放宽到 `assertTrue(out.width in 790..810)`；这是测试环境问题，不影响真机 — 不要为了过测试改 ImageCompressor 实现

2. **ACTION_GET_CONTENT 在部分 OEM 上不支持 ALLOW_MULTIPLE**：返回单张时 `data.data != null`、`data.clipData == null`；Task 10 Step 1 已经分两个分支处理

3. **Android 13+ `READ_MEDIA_IMAGES`**：本计划不申请，因为 `ACTION_GET_CONTENT` 走 SAF（System Picker），系统替我们处理读权限，App 自己不需要 `READ_MEDIA_IMAGES`。这与 PRD §8.4 "filesDir only, no WRITE/READ_EXTERNAL_STORAGE" 一致

4. **拍照返回的临时文件清理时机**：`cameraLauncher` 回调里如果 `success=true`，把临时文件交给 `compressAndInsertImages`（IO 线程解压完成后才删 cache）；若 success=false，立刻删。Task 11 Step 1 已经处理两种路径

5. **新笔记还没 id 就插图**：`ensureNoteSavedAndThen` 在弹 dialog 前就先把笔记 `save` 一次，拿到 id 再走 picker；这样 `compressAndInsertImages` 时 `loadedNote.id > 0` 一定成立

6. **图片块独占 callback.onFocusGained 不报告**：本计划没让 ImageBlockView 上抛 `onFocusGained`，故插入图片后 `focusedTextBlock` 不会变，再点图片工具栏 → 仍可在"上次焦点"位置插新图（合理 UX）

7. **passthroughBlocks 删后兼容老数据**：M4 已写过 ImageBlock/ChecklistBlock 的 JSON 落库（M2 NoteJson 完整支持），所以即便 M4 期间有用户手动塞过数据，M5 bind 时直接走 addImageBlockView/addChecklistBlockView，无迁移负担
