# HwNote 实施计划（高层 / 里程碑式）

- 配套 PRD：`docs/superpowers/specs/2026-05-22-hwnote-design.md`
- 文档版本：v1.0（高层）
- 日期：2026-05-22

> **说明**：本计划按 7 个里程碑组织，每节列出"目标 / 关键文件 / 关键技术点 / 验收标准"。每个里程碑完成后**人工跑一次手测**并独立提交（如果用 git）。开发顺序严格按里程碑序号，下一个里程碑依赖上一个。
>
> **非典型 TDD**：仅在 **数据层（M2）和算法（手写橡皮 M6）** 写自动化测试；UI 层（M3-M7）以"写完即手测"的方式推进。这是与 PRD 测试策略一致的取舍。

---

## 通用约定

- **包根**：`com.fan.hwnote.app`
- **构建系统**：Gradle Kotlin DSL（`build.gradle.kts`）
- **Kotlin**：2.0+
- **AGP**：8.5+
- **JDK**：17（编译期）；运行期 minSdk 24
- **依赖版本**：见 PRD §7.7
- **代码风格**：默认 ktlint，使用 IDEA 自带的 Reformat 即可
- **提交**：建议每个里程碑结束做一次 commit；如果在里程碑内部完成一个独立特性（例如 M3 的"搜索"），也可以单独提交
- **git**：项目目前尚未 `git init`，请在 M1 第一步初始化

---

## M1 · 基础工程

**目标**：搭建可在 Android Studio / 命令行 build 通过的最小 App 骨架。打开后只显示一个空白主屏 + FAB。

### 关键文件

```
HwNote/
├─ .gitignore                              ← 标准 Android 模板
├─ settings.gradle.kts                     ← 单 :app 模块声明
├─ build.gradle.kts                        ← 根级 Gradle 脚本
├─ gradle.properties                       ← AndroidX/Kotlin 配置
├─ gradle/wrapper/gradle-wrapper.properties
├─ gradlew / gradlew.bat
└─ app/
   ├─ build.gradle.kts                     ← 应用模块（依赖、签名、minSdk 24/targetSdk 34）
   ├─ proguard-rules.pro                   ← 占位即可，MVP 不开混淆
   └─ src/main/
      ├─ AndroidManifest.xml               ← Application 注册 + NoteListActivity 主入口 + FileProvider
      ├─ kotlin/com/fan/hwnote/app/App.kt  ← Application 子类（NoteRepository 单例初始化）
      ├─ kotlin/com/fan/hwnote/app/controller/list/NoteListActivity.kt   ← 占位
      └─ res/
         ├─ values/colors.xml              ← 主色板（PRD §5.1）
         ├─ values/strings.xml             ← app_name="备忘录" 等
         ├─ values/themes.xml              ← AppCompat.NoActionBar 主题，应用 colorPrimary
         ├─ values/dimens.xml              ← 间距/字号常量
         ├─ xml/file_paths.xml             ← FileProvider 配置（指向 filesDir/notes）
         ├─ mipmap-*/ic_launcher.png       ← 占位图标，可暂用 AS 默认
         └─ layout/activity_note_list.xml  ← 仅一个空白 LinearLayout + Toolbar + FAB 占位
```

### 关键技术点

- **Application**：`App : Application()`，在 `onCreate` 里 `NoteRepository.init(this)`（M2 完成后填充实现）。
- **FileProvider authority**：`com.fan.hwnote.app.fileprovider`；`xml/file_paths.xml` 暴露 `<files-path name="note_files" path="notes/" />`。
- **主题**：`Theme.MaterialComponents.Light.NoActionBar`，定义 `colorPrimary=#00897B` `colorPrimaryDark=#00695C` `colorAccent=#FFB300`。
- **FAB**：先用 `com.google.android.material.floatingactionbutton.FloatingActionButton`，点击 toast "新建（待实现）"。

### 验收标准

- [ ] `./gradlew :app:assembleDebug` 构建通过
- [ ] 在模拟器/真机安装后启动，显示空白主屏 + 右下绿色 FAB
- [ ] 应用名"备忘录"，状态栏色为 #00695C
- [ ] 完成 `git init` + `.gitignore` + 第一次提交（包含 PRD 和 STATUS.md）

**预估时长**：1-2 小时

---

## M2 · 数据层（含自动化测试）

**目标**：完成 Note 实体、内容序列化、SQLite 表、文件存储、Repository。**全部用 JVM 单测覆盖**。

### 关键文件

```
app/src/main/kotlin/com/fan/hwnote/app/model/
├─ entity/
│  ├─ Note.kt                  ← data class（id/title/plainText/createdAt/updatedAt/isFavorite/content）
│  ├─ NoteContent.kt           ← data class（blocks: List<Block>, handwriting: List<Stroke>）
│  ├─ Block.kt                 ← sealed class: TextBlock | ImageBlock | ChecklistBlock
│  ├─ TextSpan.kt              ← data class（start/end/type/value）+ enum SpanType
│  └─ Stroke.kt                ← data class（brush/color/width/points）+ enum BrushType
├─ db/
│  └─ NoteDbHelper.kt          ← SQLiteOpenHelper，DB_NAME="hwnote.db", VERSION=1
├─ storage/
│  └─ NoteFileStorage.kt       ← 提供 noteDir(id) / imageDir(id) / saveImage / deleteNoteDir
├─ json/
│  └─ NoteJson.kt              ← toJson/fromJson（用 org.json）
└─ NoteRepository.kt           ← 单例，门面：list/get/save/delete/setFavorite/search

app/src/test/kotlin/com/fan/hwnote/app/model/
├─ json/NoteJsonTest.kt        ← round-trip：所有 block 类型 / 6 种 span / heading / strokes
├─ db/NoteDbHelperTest.kt      ← 用 Robolectric 跑：建表、约束、索引
└─ NoteRepositoryTest.kt       ← Robolectric：CRUD、search LIKE、sortBy
```

### 关键技术点

**`Block.kt` sealed 类**：

```kotlin
sealed class Block {
    abstract val id: String
    data class TextBlock(
        override val id: String,
        var heading: Heading? = null,        // h1 / h2 / null
        var text: String = "",
        var spans: List<TextSpan> = emptyList()
    ) : Block()
    data class ImageBlock(
        override val id: String,
        var fileName: String,
        var width: Int,
        var height: Int
    ) : Block()
    data class ChecklistBlock(
        override val id: String,
        var items: MutableList<ChecklistItem> = mutableListOf()
    ) : Block()
}
data class ChecklistItem(var checked: Boolean, var text: String)
enum class Heading { H1, H2 }
```

**`NoteJson`**：用 `org.json.JSONObject` / `JSONArray`。
- 入口：`fun toJson(content: NoteContent): String` / `fun fromJson(s: String): NoteContent`
- 容错：解析失败时返回 `NoteContent(emptyList(), emptyList())`，并打 warn 日志
- 兼容：未来加字段时旧数据可读（多余字段忽略，缺失字段用默认值）

**`NoteDbHelper`**：
```kotlin
class NoteDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, "hwnote.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_NOTES)
        db.execSQL(SQL_INDEX_UPDATED)
        db.execSQL(SQL_INDEX_FAVORITE)
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { /* MVP 暂无升级 */ }
}
```

**`NoteRepository`**：
- 单例：`object NoteRepository`，需要 `init(context)` 注入 Application
- 异步：所有 IO 方法 `suspend`，内部 `withContext(Dispatchers.IO)`
- 关键方法签名：

```kotlin
suspend fun list(sortBy: SortBy = UPDATED_DESC, query: String? = null): List<Note>
suspend fun get(id: Long): Note?
suspend fun save(note: Note): Long          // 新建返回新 id；更新返回原 id
suspend fun delete(id: Long)
suspend fun setFavorite(id: Long, favorite: Boolean)

enum class SortBy { UPDATED_DESC, CREATED_DESC, TITLE_ASC }
```

### 测试覆盖（关键用例）

| 测试 | 用 |
|------|-----|
| TextBlock round-trip：含 heading + spans 多种 | `NoteJsonTest` |
| ImageBlock round-trip | `NoteJsonTest` |
| ChecklistBlock round-trip：含勾选状态 | `NoteJsonTest` |
| Stroke round-trip：4 种 brush + 多点 | `NoteJsonTest` |
| 损坏 JSON 输入 → 返回空 NoteContent | `NoteJsonTest` |
| 建表后字段/索引正确 | `NoteDbHelperTest` |
| save 新笔记 → list 出现 → updatedAt 是新值 | `NoteRepositoryTest` |
| save 已有笔记 → updatedAt 更新，createdAt 不变 | `NoteRepositoryTest` |
| search "李雷" → 命中 plainText 含此字符的 | `NoteRepositoryTest` |
| sortBy 三种值返回顺序不同 | `NoteRepositoryTest` |
| delete 笔记 → list 不再出现，且对应 noteDir 被清空 | `NoteRepositoryTest`（需 mock Storage） |

### 验收标准

- [ ] `./gradlew :app:test` 全部通过
- [ ] 测试覆盖至少 12 个用例（如上）
- [ ] 在 Application.onCreate 中调用 `NoteRepository.init(this)` 不报错

**预估时长**：4-6 小时（含写测试）

---

## M3 · 列表页

**目标**：完整笔记列表 UI，支持新建/打开/搜索/排序/长按菜单/删除。无图片块和手写仅显示标题和文本摘要。

### 关键文件

```
app/src/main/kotlin/com/fan/hwnote/app/controller/list/
├─ NoteListActivity.kt         ← 主屏（继承 AppCompatActivity）
└─ NoteListAdapter.kt          ← RecyclerView.Adapter

app/src/main/res/layout/
├─ activity_note_list.xml      ← Toolbar + SearchView + RecyclerView + FAB + 空状态
└─ item_note_card.xml          ← 单条卡片：MaterialCardView 包 标题/星标/时间/摘要

app/src/main/res/menu/
└─ menu_note_card_long_press.xml  ← PopupMenu：收藏切换 / 删除

app/src/main/res/drawable/
├─ ic_search.xml ic_sort.xml ic_star.xml ic_star_outline.xml ic_add.xml ic_delete.xml
└─ shape_note_card_bg.xml       ← 圆角白底
```

### 关键技术点

- **数据加载**：`onResume` 触发 `lifecycleScope.launch { val list = NoteRepository.list(sortBy, query); adapter.submit(list) }`，把 RecyclerView 数据替换。
- **搜索 debounce**：用 `TextWatcher` + `Handler.postDelayed(200)` 实现简易防抖（不引 RxJava）。
- **排序持久化**：`SharedPreferences("hwnote_settings", MODE_PRIVATE)` 存 `sort_by` 字段；`NoteListActivity` 启动时读、变更时写。
- **长按菜单**：`itemView.setOnLongClickListener { showPopupMenu(...) }`，用 `androidx.appcompat.widget.PopupMenu`。
- **删除二次确认**：`AlertDialog.Builder(this).setMessage("确定删除？此操作不可恢复").setPositiveButton(...)`。
- **新建跳编辑器**：FAB → `startActivity(NoteEditorActivity.newIntent(this, noteId = -1L))`，`onActivityResult` / `onResume` 时刷新列表。
- **空状态**：在布局里加一个 `LinearLayout(text="还没有笔记，点 + 新建一条" + 图标)`，列表为空时 visible。
- **卡片摘要**：从 `Note.plainText` 取前 60 字符（用 `take(60)`）。

### 验收标准（手测）

- [ ] 新建一条笔记（即使内容为空，进编辑器再返回，列表多一条 "无标题"）
- [ ] 搜索"测试" → 列表实时过滤；清空搜索框 → 全量恢复
- [ ] 排序切换 3 种 → 顺序明显变化；杀进程重启后保留上次选项
- [ ] 长按一条 → 菜单 [收藏 / 删除]；点收藏 → 出现 ⭐ 角标；点删除 → 二次确认 → 列表移除
- [ ] 列表为空时显示空状态文案

**预估时长**：3-4 小时

---

## M4 · 编辑器骨架（仅文本块）

**目标**：能进入编辑器，输入标题，输入文本，支持富文本工具栏（B/I/U/S + 字号 + 颜色 + H1/H2），保存返回，再进入数据完整恢复。**暂不接入图片、清单、手写。**

### 关键文件

```
app/src/main/kotlin/com/fan/hwnote/app/controller/editor/
├─ NoteEditorActivity.kt        ← 编辑器主控（onCreate 加载、onPause 保存）
└─ EditorPresenter.kt           ← 协调 BlockView 列表与工具栏的辅助类

app/src/main/kotlin/com/fan/hwnote/app/view/block/
├─ BlockView.kt                 ← abstract LinearLayout，定义 bind/toBlock/onRequestDelete/onRequestSplitAfter
└─ TextBlockView.kt             ← EditText + heading 字号渲染 + Span 处理

app/src/main/kotlin/com/fan/hwnote/app/view/toolbar/
└─ TextToolbarView.kt           ← 横向 ScrollView 内的按钮排：B/I/U/S/A-/A/A+/Color/H1/H2/📷/☐

app/src/main/kotlin/com/fan/hwnote/app/util/
├─ TextUtils.kt                 ← Spannable ↔ List<TextSpan> 互转
└─ Dimen.kt                     ← Int.dp / Int.sp 扩展

app/src/main/res/layout/
├─ activity_note_editor.xml     ← Toolbar + ScrollView (FrameLayout container) + Toolbar 底栏 + FAB
├─ block_text.xml               ← 单个 TextBlockView 的 EditText 模板
└─ toolbar_text.xml             ← 文本工具栏布局
```

### 关键技术点

**Activity 数据流**：

```kotlin
// onCreate
val noteId = intent.getLongExtra("noteId", -1L)
lifecycleScope.launch {
    val note = if (noteId == -1L) Note.new() else NoteRepository.get(noteId) ?: Note.new()
    presenter.bind(note)   // 把 NoteContent.blocks 渲染成 BlockView 列表
}

// onPause
presenter.collectCurrentNote()      // 从所有 BlockView 收集 → NoteContent
val note = currentNote.copy(
    plainText = content.toPlainText(),
    content = content
)
lifecycleScope.launch(Dispatchers.IO) {
    NoteRepository.save(note)
}
```

**TextBlockView**（核心实现要点）：
- 内含一个 `EditText`，`background = null`，`padding = 8dp`
- 字号：`heading == H1 → 22sp` / `H2 → 18sp` / `null → 16sp`，通过 `setTextSize(COMPLEX_UNIT_SP, ...)` 应用
- Span 应用：`setText(SpannableString(text))` 后遍历 `spans` 调 `setSpan`
- 用户输入触发 `TextWatcher` → 把 EditText 的 `Editable` 当前 spans 收集为 `List<TextSpan>` 存内存
- 末尾按回车检测：监听 `setOnEditorActionListener` 或重写 `dispatchKeyEvent`，光标在末尾且 textBefore 非空 → `onRequestSplitAfter()`
- 空块退格检测：`text.isEmpty()` 且 keyCode == DEL → `onRequestDelete()`

**TextUtils.kt**（关键转换）：

```kotlin
// Spannable -> 我们的 TextSpan list
fun Spannable.toTextSpans(): List<TextSpan> {
    val result = mutableListOf<TextSpan>()
    for (s in getSpans(0, length, Object::class.java)) {
        val start = getSpanStart(s); val end = getSpanEnd(s)
        when (s) {
            is StyleSpan -> when (s.style) {
                Typeface.BOLD -> result += TextSpan(start, end, SpanType.BOLD)
                Typeface.ITALIC -> result += TextSpan(start, end, SpanType.ITALIC)
            }
            is UnderlineSpan -> result += TextSpan(start, end, SpanType.UNDERLINE)
            is StrikethroughSpan -> result += TextSpan(start, end, SpanType.STRIKETHROUGH)
            is RelativeSizeSpan -> result += TextSpan(start, end, SpanType.FONT_SIZE, sizeOf(s.sizeChange))
            is ForegroundColorSpan -> result += TextSpan(start, end, SpanType.COLOR, hexOf(s.foregroundColor))
        }
    }
    return result
}

// 反向：我们的 TextSpan list -> Spannable（apply 到 EditText）
fun List<TextSpan>.applyTo(editable: Editable) { /* 反向映射 */ }
```

**TextToolbarView 行为**：
- B/I/U/S：作用于"当前选区"。无选区时按钮变 `selected`，作为"输入下一字符将带样式"，在 EditText 的 InputFilter / TextWatcher 中应用到刚插入的字符上。
- 字号 / 颜色：同上，作用于选区或后续输入。
- H1/H2：作用于光标所在的 `TextBlockView`，调 `currentTextBlock.heading = H1`。
- 📷/☐：M5/M6 接入，先 toast 占位。

**EditorPresenter**：
- 持有 `currentBlocks: MutableList<BlockView>`，作为 LinearLayout 的子 view
- `splitTextBlockAfter(view)`：在该 view 后插入新 TextBlockView 并 requestFocus
- `removeBlock(view)`：移除 view，焦点回上一块末尾
- `currentFocusedTextBlock()`: 返回当前焦点所在 TextBlock
- `collectCurrentNote(): NoteContent`：遍历 currentBlocks，调 `view.toBlock()` 收集

### 验收标准（手测）

- [ ] 新建笔记 → 输入标题 + 一段文字 → 返回 → 列表显示新条目
- [ ] 选中一段文字 → 点 B → 文字加粗；再点 B → 取消加粗
- [ ] 输入文字时点 B（无选区）→ 此后输入带粗体；再点 B → 关闭
- [ ] 选中文字 → 点 A+ → 字号变大（large）
- [ ] 选中文字 → 点颜色 → 弹色板（5 色）→ 选红 → 文字变红
- [ ] 光标停在某段 → 点 H1 → 该段字号变大；再点 H1 → 恢复正文
- [ ] 段中按回车 → 同段内换行；段末按回车 → 新建空文本块
- [ ] 空文本块按退格 → 块被删，焦点回上一块末尾
- [ ] 退出再进 → 所有样式（粗/斜/下划/删除/字号/颜色/H1/H2）保留

**预估时长**：6-8 小时（这是最复杂的里程碑之一）

---

## M5 · 图片块 / 清单块

**目标**：编辑器接入图片插入（相册多选 + 拍照）和清单块；图片可大图查看 + 长按删除。

### 关键文件

```
app/src/main/kotlin/com/fan/hwnote/app/view/block/
├─ ImageBlockView.kt            ← ImageView + 删除按钮 overlay
└─ ChecklistBlockView.kt        ← LinearLayout 内动态加 ChecklistItemView

app/src/main/kotlin/com/fan/hwnote/app/view/
└─ ImagePreviewActivity.kt      ← 大图全屏 Activity

app/src/main/kotlin/com/fan/hwnote/app/util/
└─ ImageUtils.kt                ← 解码、缩放（长边 1920）、JPEG 压缩、保存

app/src/main/res/layout/
├─ block_image.xml              ← ImageView + 浮动删除按钮
├─ block_checklist.xml          ← LinearLayout vertical 容器
├─ block_checklist_item.xml     ← CheckBox + EditText + 删除按钮
└─ activity_image_preview.xml   ← 全屏 ImageView，黑底
```

### 关键技术点

**图片插入流程**：
1. 工具栏 📷 点击 → `BottomSheetDialog` 选择"相册" / "拍照"
2. 相册：`Intent(ACTION_GET_CONTENT, "image/*").putExtra(EXTRA_ALLOW_MULTIPLE, true)`，`startActivityForResult(REQ_PICK)`
3. 拍照：先检查 `CAMERA` 权限（`requestPermissions`），通过后用 `FileProvider.getUriForFile(this, "com.fan.hwnote.app.fileprovider", tempFile)`，`Intent(ACTION_IMAGE_CAPTURE).putExtra(EXTRA_OUTPUT, uri)`
4. `onActivityResult`：拿到 `Uri`，用 `ImageUtils.saveAsImageBlock(uri, noteId)`，返回 `ImageBlock`
5. 在当前光标位置插入 `ImageBlockView`（用 EditorPresenter.insertBlockAfterFocused(imageBlock)）

**ImageUtils**：

```kotlin
fun saveAsImageBlock(srcUri: Uri, noteId: Long): ImageBlock {
    val src = decodeBitmap(srcUri)              // BitmapFactory + ContentResolver.openInputStream
    val scaled = scaleToMaxLongSide(src, 1920)
    val (w, h) = scaled.width to scaled.height
    val fileName = UUID.randomUUID().toString().take(8) + ".jpg"
    val out = File(NoteFileStorage.imageDir(noteId), fileName)
    FileOutputStream(out).use { scaled.compress(JPEG, 85, it) }
    return ImageBlock(id = newBlockId(), fileName = fileName, width = w, height = h)
}
```

**ImageBlockView**：用 Glide 加载 `noteFileStorage.imageFile(noteId, fileName)`：
```kotlin
Glide.with(context).load(File(...)).into(imageView)
```

点击：跳 `ImagePreviewActivity`（传 noteId + fileName）。
长按：弹"删除图片？"二次确认 → 调 `onRequestDelete()` 让 EditorPresenter 移除该块。

**ChecklistBlockView**：
- 容器是 LinearLayout vertical
- 单项 `block_checklist_item.xml` 包含：CheckBox + EditText + 右侧 ⊗ 删除按钮（hover 显示）
- 勾选 → 给 EditText 文字加 `StrikethroughSpan`，颜色置灰
- 末项 EditText 按回车 → 在末尾追加新项，焦点跳到新项
- 空项按退格 → 删除该项，焦点回上一项末尾
- 当所有项都被删除 → 整个 ChecklistBlock 也被移除（调 `onRequestDelete()`）

**ImagePreviewActivity**：
- 全屏沉浸式（`window.setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN)` + `decorView.systemUiVisibility = HIDE_NAVIGATION`）
- ImageView + GestureDetector 实现简易双击放大（用 ImageView.matrix 缩放）
- 长按 → 删除按钮 → 二次确认 → 返回带 result，编辑器移除对应 block

### 验收标准（手测）

- [ ] 编辑器 📷 → 相册 → 多选 3 张 → 全部依次插入
- [ ] 编辑器 📷 → 拍照（首次弹相机权限）→ 拍 → 保存 → 插入
- [ ] 退出再进 → 图片仍能正确加载显示
- [ ] 点击图片 → 大图全屏 → 双击放大 → 再双击缩小 → 长按删除 → 返回后该块消失
- [ ] 编辑器 ☐ → 在光标位置插入清单块 → 输入项 1 → 回车 → 项 2 → 回车 → 勾掉项 1 → 项 1 文字变灰带删除线
- [ ] 清单中空项按退格 → 项被删
- [ ] 退出再进 → 清单和勾选状态保留

**预估时长**：5-7 小时

---

## M6 · 手写 Overlay

**目标**：实现 HandwritingOverlayView，4 种笔效，工具栏（笔种/颜色/粗细/橡皮/撤销/重做/清空），与 ScrollView 滚动同步。**这是技术上最关键的一节。**

### 关键文件

```
app/src/main/kotlin/com/fan/hwnote/app/view/handwriting/
├─ HandwritingOverlayView.kt    ← 自定义 View，承载 strokes + 触摸 + onDraw
├─ BrushPainter.kt              ← 4 种笔效的 Paint 配置 + draw 方法
└─ StrokeEraser.kt              ← 笔画相交检测算法

app/src/main/kotlin/com/fan/hwnote/app/view/toolbar/
└─ HandwritingToolbarView.kt    ← 笔种切换 + 颜色选择 + 粗细 + 橡皮 + 撤销 + 重做 + 清空

app/src/main/res/drawable/
├─ pencil_noise.png             ← 8KB 黑白噪点纹理（设计提供，或用代码生成 fallback）
├─ ic_brush_pen.xml ic_brush_brush.xml ic_brush_marker.xml ic_brush_pencil.xml
├─ ic_eraser.xml ic_undo.xml ic_redo.xml ic_clear.xml ic_handwriting.xml
└─ shape_color_circle.xml       ← 颜色选择小圆

app/src/main/res/layout/
└─ toolbar_handwriting.xml      ← 横向 ScrollView 内的按钮排

app/src/test/kotlin/com/fan/hwnote/app/view/handwriting/
└─ StrokeEraserTest.kt          ← JVM 单测：相交、不相交、bbox 粗筛、距离阈值
```

### 关键技术点

**HandwritingOverlayView**（核心）：

```kotlin
class HandwritingOverlayView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private val strokes = mutableListOf<Stroke>()
    private var currentStroke: MutableList<FloatArray>? = null   // [[x,y,t],...]
    private val undoStack = ArrayDeque<Action>()
    private val redoStack = ArrayDeque<Action>()
    private val brushPainter = BrushPainter(context)

    var brushType: BrushType = BrushType.PEN
    var color: Int = Color.BLACK
    var strokeWidth: Int = 3                  // dp
    var isEraser: Boolean = false
    var isHandwritingMode: Boolean = false    // 控制是否拦截 touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isHandwritingMode) return false
        when (e.action) {
            ACTION_DOWN -> if (isEraser) startErase(e) else startStroke(e)
            ACTION_MOVE -> if (isEraser) continueErase(e) else continueStroke(e)
            ACTION_UP   -> if (isEraser) endErase() else endStroke()
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        for (s in strokes) brushPainter.draw(canvas, s)
        currentStroke?.let { /* 把当前进行中的笔画也画出来 */ }
    }

    fun setStrokes(initial: List<Stroke>) { strokes.clear(); strokes.addAll(initial); invalidate() }
    fun getStrokes(): List<Stroke> = strokes.toList()
    fun undo() { /* 反向应用 undoStack 顶 */ }
    fun redo() { /* 正向重放 redoStack 顶 */ }
    fun clear() { val all = strokes.toList(); strokes.clear(); push(EraseAction(all)); invalidate() }
}
```

**Action sealed**：

```kotlin
sealed class Action {
    data class Add(val stroke: Stroke) : Action()
    data class Erase(val strokes: List<Stroke>) : Action()
}
```

**BrushPainter**：

```kotlin
class BrushPainter(ctx: Context) {
    private val pencilShader: BitmapShader = ...        // 解码 pencil_noise.png

    fun draw(canvas: Canvas, stroke: Stroke) {
        val paint = paintFor(stroke)
        canvas.drawPath(buildPath(stroke.points), paint)
    }

    private fun paintFor(s: Stroke): Paint = Paint().apply {
        isAntiAlias = true
        color = parseColor(s.color)
        strokeWidth = s.width.dp
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        when (s.brush) {
            BrushType.PEN -> { /* default */ }
            BrushType.BRUSH -> {
                maskFilter = BlurMaskFilter(2f.dp, BlurMaskFilter.Blur.NORMAL)
                strokeWidth *= 1.3f
            }
            BrushType.MARKER -> { alpha = 140; strokeWidth *= 1.6f }
            BrushType.PENCIL -> { alpha = 160; shader = pencilShader }
        }
    }
}
```

**StrokeEraser**：

```kotlin
object StrokeEraser {
    fun strokesHitByPoint(
        cx: Float, cy: Float, eraserRadius: Float,
        strokes: List<Stroke>
    ): List<Stroke> {
        return strokes.filter { hits(cx, cy, eraserRadius, it) }
    }

    private fun hits(cx: Float, cy: Float, r: Float, s: Stroke): Boolean {
        if (!bboxOverlap(cx, cy, r, s)) return false           // 粗筛
        for (i in 0 until s.points.size - 1) {
            val p1 = s.points[i]; val p2 = s.points[i+1]
            val dist = pointToSegmentDistance(cx, cy, p1[0], p1[1], p2[0], p2[1])
            if (dist <= r + s.width / 2f) return true
        }
        return false
    }
}
```

**与编辑器集成**（NoteEditorActivity 中）：

- `onCreate` 加载 Note 后，调 `overlayView.setStrokes(content.handwriting)`
- 点 FAB → `enterHandwritingMode()`：`overlayView.isHandwritingMode = true`，`contentLayer.alpha = 0.5f`，工具栏切换为 handwriting
- 退出手写：`overlayView.isHandwritingMode = false`，`contentLayer.alpha = 1f`，工具栏切回 text
- `onPause`：`content.handwriting = overlayView.getStrokes()`，与文本块一并保存

**Overlay 高度同步**：

```kotlin
contentLayer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
    overlayView.layoutParams = (overlayView.layoutParams).apply {
        height = contentLayer.height
    }
    overlayView.requestLayout()
}
```

### 测试覆盖

| 测试 | 用 |
|------|-----|
| 一笔在 bbox 内被命中 | StrokeEraserTest |
| 一笔在 bbox 外不被命中（粗筛） | StrokeEraserTest |
| 一笔 bbox 重叠但实际距离超阈值不命中 | StrokeEraserTest |
| 多笔同时命中返回多笔 | StrokeEraserTest |

### 验收标准（手测）

- [ ] 文本笔记输入完成 → FAB → 进入手写 → 内容半透明 → 工具栏切换
- [ ] 用钢笔画一笔 → 切画笔 → 切粗细笔 → 切铅笔，每种笔效视觉不同
- [ ] 切 8 种颜色，每种颜色画线
- [ ] 切粗 / 中 / 细，3 种线宽明显不同
- [ ] 撤销 → 最后一笔消失；重做 → 笔回来；连续 5 步均正确
- [ ] 切橡皮 → 触摸某条线 → 整笔消失；撤销 → 笔回来
- [ ] 清空 → 所有笔画消失；撤销 → 全部回来
- [ ] 退出手写 → 笔画仍在但内容恢复正常透明度
- [ ] 编辑下层文本 → 不受手写干扰
- [ ] 退出 Activity → 再进 → 笔画完整恢复
- [ ] 在内容很长（多屏）时上下滚动 → 笔画跟随滚动
- [ ] 进入手写后笔画在屏幕外的部分滚动后能看到

**预估时长**：8-12 小时（最复杂里程碑）

---

## M7 · 细节打磨 + 完整手测

**目标**：完成所有 PRD 的小特性、错误处理、性能、跑完手测清单。

### 涉及内容

- **空状态**：列表为空时的友好提示文案（M3 中已实现，这里检查视觉）
- **权限处理**：相机被拒绝后的提示（"无法拍照，请在设置中授予权限"），允许相册流程不受影响
- **错误处理**：
  - 图片解码失败（损坏文件 / OOM）：toast "图片打开失败"，从笔记中移除该 ImageBlock
  - 数据库写失败：toast "保存失败"
  - JSON 解析失败：使用空 Note 兜底（M2 已实现）
- **性能**：
  - 列表加载图片缩略图（如果 v2 支持卡片缩略图，目前 v1 不需要）
  - HandwritingOverlayView onDraw 时用 `canvas.clipRect` 仅绘可见区域
- **小修小补**：
  - 卡片删除二次确认的措辞
  - FAB 进入手写图标 / 退出手写图标
  - 颜色选择器小圆点的当前选中态高亮
  - 工具栏按钮按下态视觉反馈

### 完整手测清单（PRD §9 复制）

1. [ ] 新建空笔记 → 输入标题 → 退出 → 列表能看到
2. [ ] 新建笔记输入文本 + 加粗 + 字号大 → 退出再进 → 样式保留
3. [ ] 插入相册图（多选 3 张）+ 拍照 1 张 → 退出再进 → 图片仍在
4. [ ] 创建清单 5 项，勾掉 2 项 → 退出再进 → 勾选状态保留，文字加删除线
5. [ ] 进入手写模式，画 → 撤销 → 重做 → 切笔种切颜色画 → 橡皮擦掉 → 退出再进 → 笔画保留
6. [ ] 笔记列表搜索 "李雷" → 命中含此字符的所有笔记
7. [ ] 收藏切换 → 列表中出现 ⭐ 角标 → 切换排序 → 顺序变化
8. [ ] 删除笔记 → 列表移除 → `filesDir/notes/<id>` 目录被清理（用 adb shell 验证）

### 验收标准

- [ ] 完整手测 8 项全部通过
- [ ] `./gradlew :app:test :app:assembleDebug` 都通过
- [ ] 在真机（非模拟器）上完整跑一遍以上 8 项
- [ ] 没有未处理的崩溃（无 logcat ERROR / FATAL）

**预估时长**：3-5 小时

---

## 实施顺序与依赖

```
M1 基础工程
   ↓
M2 数据层（含测试）         ← 与 M1 编译可并行写代码，但测试要等 M1 完才能跑
   ↓
M3 列表页                  ← 依赖 M2 的 NoteRepository
   ↓
M4 编辑器骨架（仅文本块）   ← 依赖 M2 + M3 启动入口
   ↓
M5 图片块 / 清单块         ← 依赖 M4
   ↓
M6 手写 Overlay            ← 依赖 M4（与 M5 可并行，但建议先 M5 后 M6）
   ↓
M7 打磨 + 手测
```

**总预估**：30-44 小时（不含调试、设计图标资源）

---

## 不在本计划内的事

- **CI/CD**：暂无（用户自己跑 gradle 即可）
- **代码混淆**：MVP 不开 ProGuard
- **签名 / 上架**：MVP 用 debug 签名足够
- **i18n**：仅中文，所有文案直接写 strings.xml
- **深色模式**：v2
- **平板适配**：v2

—— 计划结束 ——
