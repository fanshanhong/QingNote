# HwNote · 产品需求与设计文档（PRD + Tech Design）

- 文档版本：v1.0
- 日期：2026-05-22
- 状态：待评审
- 范围：MVP（v1.0）

---

## 1. 产品概述

### 1.1 产品定位
一款参考华为手机备忘录的 Android 端本地备忘录 App，提供完整的富文本/图片/清单/手写编辑能力。所有数据保存在设备本地，不联网、不同步。

### 1.2 显示名 / 内部名
- 应用显示名：**备忘录**
- 内部代号：**HwNote**
- Java 包名：`com.fan.hwnote.app`

### 1.3 目标平台
- Android only（不做 iOS / 平板专属适配 / Wear / Auto）
- minSdk 24（Android 7.0），targetSdk 34（Android 14）
- 屏幕：竖屏手机为主；横屏可显示但不专门优化布局

### 1.4 非目标（v1 不做）
分类/文件夹、置顶 pin、回收站、提醒/闹钟、笔记加锁、PDF/图片/纯文本导出、系统分享面板、录音/语音笔记、整库备份恢复、多端同步、深色模式、平板分屏。

---

## 2. 技术约束

| 约束 | 决策 |
|------|------|
| 语言 | Kotlin 单一语言 |
| 架构 | 基础 MVC（Activity 担任 Controller） |
| UI 框架 | XML + 自定义 View，**不使用 Jetpack Compose** |
| Jetpack 边界 | 允许：AppCompat / RecyclerView / ConstraintLayout / Material Components / Lifecycle（仅 `lifecycleScope` 用于协程）<br>不允许：ViewModel / LiveData / Room / Navigation / WorkManager / Hilt / Compose |
| 数据存储 | SQLite（直接用 `SQLiteOpenHelper`，不用 Room）+ App 私有目录文件 |
| 网络 | 无（本地优先） |
| 第三方依赖 | 仅 Glide（图片加载）；JSON 用 Android 内置 `org.json` |

---

## 3. MVP 功能清单

| 模块 | 范围 |
|------|------|
| 笔记 CRUD | 新建、编辑、删除、收藏切换 |
| 文本样式 | 加粗、斜体、下划线、删除线、字号（小/中/大）、字色（黑/红/黄/绿/蓝）、标题（H1/H2/正文） |
| 图片 | 相册多选 + 拍照插入；点击查看大图，长按删除 |
| 清单 | CheckBox + 文本，勾选后自动加删除线；回车在末尾新增一项；删除空项回到上一项 |
| 手写（Overlay） | 4 笔种（钢笔/画笔/粗细笔/铅笔）× 8 颜色 × 3 粗细，橡皮擦（笔画级），撤销/重做，清空 |
| 列表 | 单列卡片，标题 + 摘要 + 修改时间，按所选排序展示 |
| 搜索 | 顶部搜索框，匹配笔记标题与正文（plain_text） |
| 排序 | 修改时间 ↓（默认） / 创建时间 ↓ / 标题 A-Z 三种切换 |

---

## 4. 信息架构与导航

```
启动
  ↓
NoteListActivity        ← 主屏：笔记列表 + 搜索 + 排序 + 新建 FAB
  ↓ (点击笔记 / 点击新建 FAB → startActivityForResult / 返回刷新)
NoteEditorActivity      ← 编辑器：内容层 + 手写 Overlay
  ├ (点击图片) ImagePreviewActivity   ← 大图查看 / 长按删除
```

总共 3 个 Activity：`NoteListActivity` / `NoteEditorActivity` / `ImagePreviewActivity`。

---

## 5. 视觉设计

### 5.1 主色与色板
- 主色 Primary：`#00897B`（沉稳青绿）
- 主色深 Dark：`#00695C`
- 主色浅 Light：`#E0F2F1`
- 收藏标志色：与主色同色系；星标 `#FFB300`
- 错误/危险色：`#E53935`
- 文本 Primary：`#212121` · Secondary：`#666666` · Hint：`#9E9E9E`
- 背景：`#FAFAFA` · 卡片：`#FFFFFF` · 分隔线：`#EEEEEE`

### 5.2 列表布局
**单列卡片**：每张卡片占一行，圆角 10dp，白底，阴影 elevation 2dp。
卡片内容：第一行（粗体标题 14sp + 收藏星标 + 修改时间 11sp 灰色），第二行（摘要 12sp 灰色，最多 2 行 ellipsize END）。

### 5.3 编辑器布局
- 顶部 ActionBar：返回 / 修改时间 / `⋯` 菜单（收藏 / 删除）
- 内容区：标题 + 块列表（垂直排列）
- 右下 FAB：文本模式时绿色 `✏️`（进入手写）；手写模式时红色 `✕`（退出手写）
- 底部工具栏（横向滚动）：
  - 文本模式：B / I / U / S / 字号 A⁻ A A⁺ / 颜色 / H1 H2 / 📷 / ☐
  - 手写模式：4 笔种切换 / 颜色选择器（8 色）/ 粗细 / 橡皮 / 撤销 / 重做 / 清空
- 手写模式下，下层内容 `alpha=0.5` 半透明显示

---

## 6. 数据模型

### 6.1 SQLite 表
```sql
CREATE TABLE notes (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  title        TEXT    NOT NULL DEFAULT '',
  plain_text   TEXT    NOT NULL DEFAULT '',           -- 用于搜索
  content_json TEXT    NOT NULL DEFAULT
                       '{"blocks":[],"handwriting":{"strokes":[]}}',
  is_favorite  INTEGER NOT NULL DEFAULT 0,             -- 0 / 1
  created_at   INTEGER NOT NULL,                       -- epoch millis
  updated_at   INTEGER NOT NULL
);
CREATE INDEX idx_notes_updated_at ON notes(updated_at DESC);
CREATE INDEX idx_notes_favorite   ON notes(is_favorite);
```

### 6.2 content_json 结构

```json
{
  "blocks": [
    {
      "type": "text",
      "id": "b-001",
      "heading": "h1",
      "text": "今天会议要点",
      "spans": []
    },
    {
      "type": "image",
      "id": "b-002",
      "fileName": "9c2f.jpg",
      "width": 1080,
      "height": 720
    },
    {
      "type": "checklist",
      "id": "b-003",
      "items": [
        { "checked": true,  "text": "确认排期" },
        { "checked": false, "text": "联系李雷" }
      ]
    }
  ],
  "handwriting": {
    "strokes": [
      {
        "brush": "pen",
        "color": "#000000",
        "width": 3,
        "points": [[120, 80, 17], [125, 82, 38], [130, 86, 55]]
      }
    ]
  }
}
```

### 6.3 文本块属性

**块级属性**：
- `heading`：`h1` / `h2` / 缺省（= 正文）。作用于整个文本块，不写在 spans 里。

**行内 Span 类型**（仅 6 种）：
- `bold` · `italic` · `underline` · `strikethrough` —— 无 value
- `fontSize` —— value: `small` / `medium` / `large`
- `color` —— value: `#RRGGBB`

注：H1/H2 是块级；点击 H1/H2 工具栏按钮时，当前文本块的 `heading` 字段被设置/清除，`TextBlockView` 据此渲染整段字号（如 H1 = 22sp / H2 = 18sp / 正文 = 16sp）。

### 6.4 Stroke / 笔画
- `brush`: `pen` / `brush` / `marker` / `pencil`
- `color`: `#RRGGBB`
- `width`: 1=细 / 3=中 / 6=粗（dp）
- `points`: `[[x, y, tMs], …]`，坐标系是"内容总坐标"（相对 ScrollView 内 LinearLayout 顶部）

### 6.5 plain_text 生成规则
保存笔记时，由 `NoteContent.toPlainText()` 拼接生成：
- 遍历 `blocks`：text 块取 `text` 字段、checklist 块取每项 `text`，用 `\n` 拼接
- 忽略 image 块（图片无文本）
- 忽略 handwriting（手写不可搜，作为已知简化）
- 用于 `WHERE plain_text LIKE '%关键词%'` 的搜索

### 6.6 文件存储布局
```
filesDir/
  notes/
    <noteId>/
      images/
        <uuid>.jpg          ← JPEG，长边 ≤ 1920px，质量 85
```
删除笔记时同时 `rm -rf filesDir/notes/<noteId>/`。

---

## 7. 技术架构

### 7.1 MVC 角色
| 角色 | 实现 |
|------|------|
| Model | `Note`、`NoteContent`、`Block`、`TextSpan`、`Stroke` 等数据类；`NoteRepository`、`NoteDbHelper`、`NoteFileStorage`、`NoteJson` |
| View | XML 布局；自定义 View：`HandwritingOverlayView`、`TextBlockView`、`ImageBlockView`、`ChecklistBlockView`、`TextToolbarView`、`HandwritingToolbarView` |
| Controller | `NoteListActivity`、`NoteEditorActivity`、`ImagePreviewActivity`，可选辅助类 `EditorPresenter` 协调编辑器内多 View 的状态 |

### 7.2 包结构
```
com.fan.hwnote.app/
├─ App.kt                              ← Application 子类（数据库初始化）
│
├─ model/
│  ├─ entity/
│  │   ├─ Note.kt
│  │   ├─ NoteContent.kt
│  │   ├─ Block.kt                     ← sealed class: TextBlock | ImageBlock | ChecklistBlock
│  │   ├─ TextSpan.kt
│  │   └─ Stroke.kt
│  ├─ db/
│  │   └─ NoteDbHelper.kt              ← SQLiteOpenHelper
│  ├─ storage/
│  │   └─ NoteFileStorage.kt
│  ├─ json/
│  │   └─ NoteJson.kt                  ← org.json 序列化
│  └─ NoteRepository.kt                ← 唯一对外门面
│
├─ controller/
│  ├─ list/
│  │   ├─ NoteListActivity.kt
│  │   └─ NoteListAdapter.kt           ← RecyclerView Adapter
│  └─ editor/
│      ├─ NoteEditorActivity.kt
│      └─ EditorPresenter.kt
│
├─ view/
│  ├─ block/
│  │   ├─ BlockView.kt                 ← 抽象基类
│  │   ├─ TextBlockView.kt
│  │   ├─ ImageBlockView.kt
│  │   └─ ChecklistBlockView.kt
│  ├─ handwriting/
│  │   ├─ HandwritingOverlayView.kt
│  │   ├─ BrushPainter.kt
│  │   └─ StrokeEraser.kt
│  ├─ toolbar/
│  │   ├─ TextToolbarView.kt
│  │   └─ HandwritingToolbarView.kt
│  └─ ImagePreviewActivity.kt
│
└─ util/
   ├─ Dimen.kt
   ├─ ImageUtils.kt
   ├─ DateUtils.kt
   └─ TextUtils.kt
```

### 7.3 编辑器 View 层级（关键架构）
```
NoteEditorActivity
└─ FrameLayout (root)
   ├─ ScrollView
   │   └─ FrameLayout (container)
   │       ├─ LinearLayout vertical (content layer)
   │       │   ├─ TitleEditText
   │       │   ├─ TextBlockView
   │       │   ├─ ImageBlockView
   │       │   ├─ ChecklistBlockView
   │       │   └─ TextBlockView (末尾占位)
   │       └─ HandwritingOverlayView (覆盖 LinearLayout，宽高同 LinearLayout)
   ├─ Toolbar (底部，模式切换内容)
   └─ ModeSwitchFab (右下)
```

要点：
- HandwritingOverlayView 直接放在 ScrollView 内，与内容层 LinearLayout 同级，由 FrameLayout 居中重叠 → 滚动天然同步，stroke 坐标无需 scrollY 转换。
- 监听内容层的 onLayoutChange，把 HandwritingOverlayView 的高度同步为内容层高度。

### 7.4 模式切换逻辑
| 模式 | HandwritingOverlayView | content 层 alpha | 工具栏 | FAB |
|------|------------------------|------------------|--------|-----|
| 文本（默认） | `isHandwritingMode = false`，touch 不拦截，透传到下层 EditText | 1.0 | 富文本工具栏 | ✏️ 进入手写 |
| 手写 | `isHandwritingMode = true`，touch 全部拦截 | 0.5 | 手写工具栏 | ✕ 退出手写 |

### 7.5 笔效与橡皮

**4 种笔效（通过 Paint 配置实现，不引第三方）**：

| 笔种 | Paint 关键设置 | 视觉效果 |
|------|---------------|---------|
| 钢笔 pen | `style=STROKE, strokeCap=ROUND, strokeJoin=ROUND, alpha=255` | 等粗实色硬边线 |
| 画笔 brush | 同钢笔 + `setMaskFilter(BlurMaskFilter(2px, NORMAL))` + 略宽（×1.3） | 边缘微化、模拟毛笔感 |
| 粗细笔 marker | `alpha=140` + 宽度 ×1.6 + `setXfermode(null)` | 半透明马克笔、叠涂处变深 |
| 铅笔 pencil | `alpha=160` + `setShader(BitmapShader(pencil_noise.png, REPEAT, REPEAT))` | 颗粒铅芯感 |

`pencil_noise.png` 是项目内置的小图（约 8KB，黑白噪点纹理，原尺寸不缩放）。`BrushPainter` 工具类负责按 `Stroke.brush` 选 Paint 并 `canvas.drawPath(path, paint)`。

**橡皮（笔画级）**：

```
StrokeEraser.tryErase(touchPath, strokes, eraserRadius) → erasedStrokes
```

- 用户手指按下后，每个 MOVE 事件取一个采样点 `(ex, ey)`；以该点为中心、半径 `eraserRadius` 形成检测圆。
- 对 `strokes` 中每条 stroke：
  1. **粗筛**：检测圆与 stroke 的 bounding box 是否相交（`RectF.intersect`），不相交直接跳过；
  2. **细判**：相交时，遍历 stroke 的相邻点对 `(p1, p2)`，计算检测圆心 `(ex, ey)` 到线段 `p1-p2` 的最短距离。若 ≤ `eraserRadius + stroke.width/2`，命中。
- 命中的 stroke 整笔从 `strokes` 移除，并作为一个 `EraseAction` 推入 `actions` 栈，便于撤销恢复。

**撤销 / 重做**：

```kotlin
sealed class Action {
    data class Add(val stroke: Stroke) : Action()
    data class Erase(val strokes: List<Stroke>) : Action()
}
val undoStack = ArrayDeque<Action>()    // 操作历史
val redoStack = ArrayDeque<Action>()
```

- 落笔完成（UP）→ push `Add(stroke)` 到 undoStack，redoStack 清空。
- 橡皮一次按下到抬起结束 → 该次擦除的 strokes 集合 push `Erase(list)` 到 undoStack，redoStack 清空。
- Undo：pop undoStack，反向应用（Add → 移除 stroke / Erase → 把 strokes 放回），push 到 redoStack。
- Redo：pop redoStack，正向重放，push 回 undoStack。
- 清空：作为单一 `Erase(allStrokes)` 推入历史，可被撤销。

### 7.6 数据加载与保存
- 列表页 `onResume`：`Repository.list(sortBy, query)` 后台协程 → 主线程刷新 RecyclerView。
- 编辑器 `onCreate`：取传入的 noteId（新建时为 -1）；若 -1 则创建空 Note 在内存中，`onPause` 触发首次写库。
- 编辑器 `onPause`（含返回键）：从所有 BlockView + HandwritingOverlayView 收集数据 → `NoteContent` → `NoteJson.toJson` → `Repository.save`。
- 不做实时增量保存、不做版本/草稿。

### 7.7 第三方依赖
```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // 不引 compiler/annotationProcessor：只用 Glide.with(...).load(...).into(...) 基础 API，无需生成 GlideApp 类

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.robolectric:robolectric:4.13")
}
```

---

## 8. 关键交互细节

### 8.1 列表页
- 顶部搜索框始终可见；输入时实时过滤（debounce 200ms）。
- 右上排序图标 → 弹出选项：修改时间 ↓ / 创建时间 ↓ / 标题 A-Z；持久化到 `SharedPreferences`。
- 收藏笔记不分组置顶（保留排序），仅显示星标 ⭐。
- 长按卡片：弹出菜单（收藏 / 删除）。删除前 `AlertDialog` 二次确认。
- 空状态：插画 + "还没有笔记，点 + 新建一条"。

### 8.2 编辑器
- 进入时若是新建笔记，自动聚焦标题；用户输入后第一个 Enter → 焦点移到第一个文本块。
- 文本块按回车：在该块末尾按回车，新建一个空文本块；段中间回车正常插入换行（同块内多行）。
- 文本块为空时退格：删除该块，焦点回到上一块末尾。
- **行内样式**（B / I / U / S / 字号 / 颜色）：作用于"当前选区"；如无选区则切换为"光标后输入的字符将带此样式"待应用模式（用 TextWatcher 在新输入到来时把 span 应用到刚输入的字符上）。
- **块级样式**（H1 / H2 / 正文）：作用于光标所在的整个文本块。点击 H1 → 当前块的 `heading` 属性设为 `h1`，`TextBlockView` 通过 `setTextSize` 重新渲染字号（H1=22sp / H2=18sp / 正文=16sp）。再次点击同一按钮 → 取消（设为 `null`）。
- 插入图片：调系统 `ACTION_GET_CONTENT`（多选）/ `ACTION_IMAGE_CAPTURE`（拍照，配 FileProvider）；获取后保存到 `filesDir/notes/<id>/images/<uuid>.jpg`，长边压缩到 1920px、JPEG 质量 85，再插入 ImageBlockView。详见 8.4。
- 插入清单：当前光标若在文本块末尾 → 在其后插入 ChecklistBlockView；若在中间 → 拆分文本块再插入。
- 清单内：每行一个 CheckBox + EditText；末项按回车新增一项；空项按退格删除当前项。
- 手写 Overlay 滚动：用户在手写模式下，单指拖动是画线；不可滚动。退出手写后才能滚动。
- 大图预览：ImagePreviewActivity，全屏显示，单指双击放大（简易缩放，使用 ImageView 自带 matrix），长按 → 删除按钮。

### 8.3 删除流程
- 列表长按 → 删除 → 二次确认 → `Repository.delete(id)`：DELETE 行 + 删除 `filesDir/notes/<id>` 整个目录。

### 8.4 权限与系统 Intent

**权限**：
- `CAMERA` —— 仅在用户首次点击"拍照"时通过 `requestPermissions` 请求；拒绝后允许相册流程继续。
- 存储：使用 App 私有目录 `filesDir`，无需 WRITE/READ_EXTERNAL_STORAGE。

**系统 Intent**：
- 选图：`Intent(Intent.ACTION_GET_CONTENT)` + `setType("image/*")` + `putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)`，用 `startActivityForResult`（或新版 `registerForActivityResult(ActivityResultContracts.GetMultipleContents())`，但本项目不依赖 Activity Result API 时手写 result code）。
- 拍照：`Intent(MediaStore.ACTION_IMAGE_CAPTURE)`，用 `FileProvider` 把目标文件路径作为 `EXTRA_OUTPUT` 传出（需要在 `AndroidManifest.xml` 配 `FileProvider` 和 `xml/file_paths.xml`）。

---

## 9. 测试策略

| 层级 | 是否做自动化 | 工具 / 范围 |
|------|--------------|-------------|
| 数据层（Model）| ✅ 是 | JUnit5 + JVM 单测：`NoteJson` 序列化、`StrokeEraser` 相交、`TextUtils` Span ↔ TextSpan 互转 |
| 数据库层 | ✅ 是 | Robolectric：建表、升级、Repository CRUD、search LIKE |
| View 层 | ❌ 不做 | 手测 |
| Activity 端到端 | ❌ 不做 | 手测 |

**手测用例（实施完成后必跑）**：
1. 新建空笔记 → 输入标题 → 退出 → 列表能看到
2. 新建笔记输入文本 + 加粗 + 字号大 → 退出再进 → 样式保留
3. 插入相册图（多选 3 张）+ 拍照 1 张 → 退出再进 → 图片仍在
4. 创建清单 5 项，勾掉 2 项 → 退出再进 → 勾选状态保留，文字加删除线
5. 进入手写模式，画 → 撤销 → 重做 → 切笔种切颜色画 → 橡皮擦掉 → 退出再进 → 笔画保留
6. 笔记列表搜索 "李雷" → 命中含此字符的所有笔记
7. 收藏切换 → 列表中出现 ⭐ 角标 → 切换排序 → 顺序变化
8. 删除笔记 → 列表移除 → `filesDir/notes/<id>` 目录被清理

---

## 10. 风险与已知限制

| 风险 | 缓解 |
|------|------|
| HandwritingOverlayView 高度大时 `invalidate` 重绘卡顿 | onDraw 时只绘可见区域（`canvas.clipRect`）；笔画多时分批 |
| 多块编辑器输入法焦点切换有兼容问题 | 在 EditorPresenter 中显式管理焦点；输入法回退用 `requestFocus + showSoftInput` |
| Span 序列化 / 反序列化遗漏 | 单测覆盖 6 种 span 类型 + heading 块属性的 round-trip 转换 |
| 大量手写笔画导致 content_json 单笔记过大 | v1 不做拆分，v2 评估超过 200KB 后拆 `handwriting.json` |
| 铅笔噪点纹理在不同分辨率显示不一致 | 用 `BitmapShader.tileMode=REPEAT` + 像素 1:1 不缩放 |
| 笔画级橡皮无法局部擦除一笔的中间 | 这是已知简化；如需精修可拆笔 |

---

## 11. 实施里程碑（建议给 writing-plans 的输入）

1. **基础工程** —— Gradle 配置、AndroidManifest、Application、主题与色板。
2. **数据层** —— Note 实体、Block sealed class、NoteJson、NoteDbHelper、NoteFileStorage、NoteRepository（含搜索/排序）。
3. **列表页** —— NoteListActivity + Adapter + 卡片布局 + 搜索 + 排序 + 长按菜单 + 删除。
4. **编辑器骨架** —— NoteEditorActivity + 块视图基类 + 三种 BlockView + 文本工具栏（不含手写）+ 保存/加载。
5. **图片块 / 清单块** —— 完整接通 + 图片预览。
6. **手写 Overlay** —— HandwritingOverlayView + 4 种笔效 + 工具栏 + 撤销/重做 + 橡皮 + 清空 + 保存/恢复。
7. **细节打磨** —— 空状态、动画、错误处理、手测清单一遍。

每个里程碑独立可验收。

---

## 12. 附录：开放的 v2 待办

- 分类 / 文件夹 ← **2026-06-04 已晋升 MVP，见 §13**
- 置顶 pin
- 回收站（30 天保留）← **2026-06-04 已晋升 MVP，见 §13**
- 提醒 / 闹钟
- 笔记加锁（PIN）
- 导出 PDF / 长图 / .txt
- 系统分享面板
- 录音 / 语音笔记 ← **2026-06-04 已晋升 MVP，见 §13**
- 整库备份恢复（zip）
- 深色模式
- 多端同步（如做服务端）

---

## 13. Scope Expansion（2026-06-04）

PRD 2026-05-22 冻结版交付完成（M1..M7）并真机走查通过后，用户基于参考华为 Note 实机使用反馈，决定把以下原 §12 v2 待办晋升 MVP，作为新 baseline：

### 13.1 新增范围

| 项 | 范围 | 原因 |
|---|---|---|
| **分类 / 文件夹** | 顶部下拉切换（全部 / 未分类 / 我的收藏 / 最近删除 + 用户文件夹）；文件夹 CRUD + 颜色 + 排序 | 笔记数量增长后必备的组织维度 |
| **最近删除（软删除）** | 删除 → 进最近删除（30 天保留）；支持恢复 / 彻底删；过期自动清理 | 防误删 |
| **语音录入** | 编辑器内 `Block.AudioBlock`，录制 / 播放 / 删除；m4a + AAC 编码 | 提升录入效率 |
| **撤销 / 重做** | 编辑器内全操作可撤销（文本输入 / 样式 / 块增删 / 图片清单语音操作）；跨保存边界清栈 | 高频编辑场景必备 |
| **首页排序对话框** | 由 `AlertDialog` 改 `BottomSheetDialog`，2 选项（编辑时间 / 创建时间） | 视觉对齐华为 Note；收藏访问改走顶部入口 |
| **图片块视觉** | 去边框 + 12dp 圆角 `ShapeableImageView` | 对齐华为 Note 自然样式 |
| **清单 toggle 反向** | 焦点在 TextBlock 时点清单按钮 → 把本块整块原地转 ChecklistBlock（首项 = 原文字） | 修正既有"另起一行"反直觉行为 |
| **列表页状态栏 inset** | AppBarLayout 加 `fitsSystemWindows="true"` | bug fix，复用 M7 T12 pattern |
| **编辑器 metadata strip** | 标题下方一行："2 分钟前 · 未分类"，点分类可切换 | 时间反馈 + 分类入口 |

### 13.2 数据模型增量

**Note 增列**（DB v2 迁移）：
- `category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL` — null = 未分类
- `deleted_at INTEGER DEFAULT 0` — 0 = 未删除；正数 = 删除时间戳

**新表 categories（DB v2 新建）**：
- `id INTEGER PRIMARY KEY AUTOINCREMENT`
- `name TEXT NOT NULL`
- `color TEXT NOT NULL` —— hex（参考 4 色：黄 / 青 / 绿 / 红，可扩展）
- `order_index INTEGER NOT NULL DEFAULT 0` —— 排序权重

**Block sealed 新成员**：
- `AudioBlock(id, fileName, durationMs)` —— fileName 指向 `filesDir/notes/<noteId>/audio/<uuid>.m4a`

**Edit history（运行时，不入库）**：
- `EditHistoryManager` 维护 undo / redo 栈；操作模型按 command pattern；保存时清栈

### 13.3 不在本轮范围

仍保留 §12 v2 待办的：置顶 pin / 提醒 / 加锁 / 导出 / 分享 / 备份 / 深色模式 / 多端同步。

### 13.4 新里程碑列表

| 里程碑 | 范围 | 预估 |
|---|---|---|
| **M8 体验小修** | 13.1 中的：列表状态栏 / 图片圆角 / 清单 toggle 反向 / 排序 2 选项 BottomSheet | 半天 |
| **M9 分类 + 软删除 + metadata strip** | 13.1 中的：分类系统 / 文件夹管理 / 软删除 + 最近删除页 / 编辑器 metadata strip；伴随 DB v2 迁移 | 1-2 天 |
| **M10 语音录入** | 13.1 中的：AudioBlock + 录制 / 播放 / 删除 + 权限 + 文件存储 | 1 天 |
| **M11 撤销 / 重做** | 13.1 中的：EditHistoryManager + 工具栏按钮 + 跨保存清栈 | 1-2 天 |

### 13.5 兼容性约定

- DB 升级：`NoteDbHelper` 版本 1 → 2，`onUpgrade` 加 `ALTER TABLE notes ADD COLUMN ...` + `CREATE TABLE categories`；旧笔记 `category_id` 默认 NULL（=未分类），`deleted_at` 默认 0（=未删除）。
- JSON 序列化：旧 JSON 不含 `AudioBlock` 类型 → `NoteJson.fromJson` 已有未知类型容错（M2 测过），无需特殊处理。
- 测试基线：68 单测继续全绿；新加 M8-M11 范围对应单测（DB migration / Category CRUD / AudioBlock JSON round-trip / undo-redo command 模型）。

—— 文档结束 ——
