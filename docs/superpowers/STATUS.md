# HwNote · 项目进度

最后更新：2026-06-04（M8 体验小修完成 — PRD §13 启动，M9/M10/M11 待执行）

## 阶段地图

| 阶段 | 状态 | 产出 |
|------|------|------|
| 1. 头脑风暴 / 需求澄清 | ✅ 完成 | 8 个核心需求决策（功能范围、Jetpack 边界、minSdk、富文本范围、手写笔种、图片来源、列表布局、主色） |
| 2. 架构方案 | ✅ 完成 | 选定 **Block 块组合 + 手写 Overlay 透明层** |
| 3. PRD / 技术设计 | ✅ 完成 | `docs/superpowers/specs/2026-05-22-hwnote-design.md`（12 节，~450 行） |
| 4. 规格自审 | ✅ 完成 | 修复 7 处一致性问题（heading 块属性归位、Span 6 种、笔效/橡皮算法详写、ACTION_GET_CONTENT 替换、Glide compiler 去除、plain_text 规则补、行内/块级样式分离） |
| 5. 用户审阅 PRD | ✅ 完成 | 用户确认无修改，进入下一步 |
| 6. **实施计划编写** | ✅ 完成 | `docs/superpowers/plans/2026-05-22-hwnote-implementation.md`（高层版，7 个里程碑各一节，685 行） |
| 7. 代码实施 | ✅ M1-M7 完成 + M8 完成 | M1+M2+M3+M4+M5+M6+M7 代码全部落地，**M8 (PRD §13 启动)** 4 改动全部落地；67 单测全绿（M7 基线 68 → M8 删 TITLE_ASC 同时连带删测试） |
| 8. 手测验收 | ✅ M3 / M5 / M6 / M7 / **M8** 用户真机走查全部通过 | M3 五条 + M5 七条 + M6 十三条 + M7 十六条 + **M8 六条**逐条手测通过；M4 SpanConverter 15 项 + M2 数据层 42 项 + M5 ImageCompressor 3 项 + M6 StrokeEraser 5 项 + M7 BrushPainter 3 项单测 PASSED |

## 里程碑进度

| 里程碑 | 状态 | 备注 |
|---|---|---|
| M1 基础工程 | ✅ 完成（2026-05-22） | 工程构建 + 真机启动 + git init |
| M2 数据层 | ✅ 完成（2026-05-22） | 数据层 + 42 单测 |
| M3 列表页 | ✅ 完成（2026-05-22） | RecyclerView + 搜索 + 排序 + 长按菜单 + 删除二次确认 |
| M4 编辑器骨架 | ✅ 完成（2026-05-23） | NoteEditorActivity + EditorPresenter + 块容器 + 12 键工具栏 + B/I/U/S + 字号 + 5 色 + H1/H2；SpanConverter 15 单测 |
| M5 图片块/清单块 | ✅ 完成（2026-05-23） | ImageBlockView + ChecklistBlockView + ImageCompressor（长边 1920/JPEG 85）+ 多选相册 / 拍照 + CAMERA 运行时权限；passthroughBlocks 已删 |
| M6 手写 Overlay | ✅ 完成（2026-05-23） | HandwritingOverlayView 透明层 + 4 笔种 BrushPainter + 笔画级 StrokeEraser + 撤销/重做/清空 + 6 键工具栏 + 8 色 + 3 粗细；StrokeEraser 5 单测 |
| M7 打磨 | ✅ 完成（2026-05-23） | 13 任务全部落地：手写性能（Paint 三键缓存 / Path 复用）+ 鲁棒（save-in-flight / Camera SavedInstanceState / 图片失败 Toast）+ UX（状态栏 inset / 清单 toggle / Camera 引导跳设置 / 文案规范）；68 单测 + 16 项真机走查全过 |
| M8 体验小修 | ✅ 完成（2026-06-04） | PRD §13 启动；4 改动：列表页 AppBarLayout fitsSystemWindows / 图片块 ShapeableImageView 12dp 圆角 / 清单按钮反向 toggle / 排序 BottomSheet 2 选项 + 删 TITLE_ASC；67 单测 + 6 项真机走查全过 |
| M9 分类 + 软删除 + metadata strip | ⏳ 待执行 | PRD §13 范围；DB v2 迁移；分类系统 + 最近删除 + 编辑器 metadata strip |
| M10 语音录入 | ⏳ 待执行 | PRD §13 范围；AudioBlock + 录制 / 播放 / 删除 + RECORD_AUDIO 权限 |
| M11 撤销 / 重做 | ⏳ 待执行 | PRD §13 范围；EditHistoryManager 命令模式 + 工具栏按钮 + 跨保存清栈 |

## M1 完成详情（2026-05-22）

**产出：**
- 工程目录：`code/HuaWeiNote/`（用户预先用 AS 创建，由我改造去 Compose）
- 改造内容：删 Compose 依赖、删 `MainActivity` + `ui/theme/`、加 PRD 要求依赖（appcompat / recyclerview / material / constraintlayout / lifecycle / coroutines / glide）
- 新代码：`App.kt`（Application 子类）+ `controller/list/NoteListActivity.kt`（入口 Activity）
- 新资源：`activity_note_list.xml` + 重写 `colors.xml/strings.xml/themes.xml` + 新建 `dimens.xml` + `xml/file_paths.xml`
- 新测试：`SmokeTest.kt`（JUnit 5）
- Manifest：替换入口为 `NoteListActivity` + 加 FileProvider + 移除 backup 引用

**版本（按用户工程为准）：**
- AGP 8.11.2 / Kotlin 2.0.21 / Gradle 8.14.3
- compileSdk 36 / targetSdk 36 / minSdk 24
- JDK 11（编译期）

**验收：**
- ✅ `./gradlew :app:assembleDebug` 通过（25s）
- ✅ `./gradlew :app:test` 通过（SmokeTest 1 项）
- ✅ 真机 (2211133C, Android 15) 安装并启动 NoteListActivity 成功
- ✅ UI：青绿 ActionBar + "备忘录" 标题 + "还没有笔记" 提示 + 绿色 FAB
- ✅ 仓库根 `git init` + 首次 commit（包含 `docs/` 全部 + `code/` 工程，排除 `.claude/.superpowers/build/.gradle/.idea`）

## M2 完成详情（2026-05-22）

**产出：**
- 实体层：`model/entity/Note.kt`、`NoteContent.kt`、`Block.kt`（sealed: TextBlock / ImageBlock / ChecklistBlock）、`TextSpan.kt`（6 种 span：bold/italic/underline/strikethrough/size/color）、`Stroke.kt`（4 笔种：pen/brush/marker/pencil）
- 序列化：`model/json/NoteJson.kt`（org.json，round-trip 安全；含未知类型/损坏 JSON 容错）
- 数据库：`model/db/NoteDbHelper.kt`（SQLiteOpenHelper 建表 notes + 索引：updated_at / is_favorite / plain_text）
- 文件存储：`model/storage/NoteFileStorage.kt`（笔记图片/手写目录布局 + 删除清理）
- 仓库：`model/NoteRepository.kt`（object 单例：init / save / get / list / sortBy / search / delete / setFavorite）
- App 接入：`App.onCreate` 调 `NoteRepository.init(this)`

**测试统计：** `./gradlew :app:test` 共 **42 项 PASSED**（debug + release 两轮各 42）：
- SmokeTest: 1
- NoteContentTest: 3
- NoteJsonTextBlockTest: 4
- NoteJsonImageChecklistTest: 3
- NoteJsonStrokeTest: 3
- NoteJsonResilienceTest: 6
- NoteDbHelperTest: 3
- NoteFileStorageTest: 5
- NoteRepositorySaveGetTest: 4
- NoteRepositoryListTest: 6
- NoteRepositoryDeleteFavoriteTest: 4

**测试基础设施：**
- JUnit 5 (Jupiter) + JUnit 4 Vintage（兼容 Robolectric @RunWith）
- Robolectric 4.13（`robolectric.properties` 锁 sdk=34，因 Robolectric 4.13 内置最高 34）
- `org.json` artifact（避免 Android stub "not mocked"）
- `androidx.test:core`（ApplicationProvider）

**M2 commit 列表（git log `8f16a2e..HEAD`，共 11 个 commit）：**
- `8f16a2e` feat(m2): 实体类骨架 + toPlainText
- `77963a4` feat(m2): NoteJson + TextBlock 序列化（heading + 6 spans）
- `094a57a` test(m2): NoteJson Stroke 4 笔种 round-trip
- `d2c6b5d` test(m2): NoteJson Image/Checklist 块覆盖
- `b46fadd` test(m2): NoteJson fromJson 容错（损坏/未知类型）
- `23f0912` feat(m2): NoteFileStorage 目录布局 + 删除
- `d7bb3ca` feat(m2): NoteDbHelper 建表 + 索引
- `0076051` chore(m2): 标注 23f0912 与 d7bb3ca 的依赖耦合
- `a7f67ff` feat(m2): NoteRepository save/get + 单例骨架
- `7f5240a` test(m2): NoteRepository delete + setFavorite 覆盖
- `c537b46` test(m2): NoteRepository list/sortBy/search 覆盖
- 收尾 commit：feat(m2): App 接入 NoteRepository.init + STATUS 收尾

**验收：**
- ✅ `./gradlew :app:test` 通过（debug 42 + release 42）
- ✅ `./gradlew :app:assembleDebug` 通过（8s）
- ⏳ 真机冒烟启动：留待用户手测（`NoteRepository.init(this)` 已接入）

## M3 完成详情（2026-05-22）

**产出：**
- 资源：drawables（ic_search/ic_sort/ic_star/ic_star_outline/ic_add/ic_delete/ic_empty_note + shape_note_card_bg）+ 2 个 menu xml + strings 列表页词条
- 布局：`activity_note_list.xml` 重构（Toolbar + 常驻搜索框 + RecyclerView + 空状态 + FAB）+ 新建 `item_note_card.xml`（MaterialCardView 卡片）
- 工具：`util/DateUtils.kt`（相对时间格式化）+ `util/TextUtils.kt`（摘要截断 / 空标题判定）
- Adapter：`controller/list/NoteListAdapter.kt`（VH 渲染 + 单击/长按回调签名）
- Activity：`NoteListActivity.kt` 完整实现（onCreate + onResume + 数据加载 + 空状态切换 + 搜索 debounce 200ms + 排序持久化 + 长按 PopupMenu + 收藏切换 + 删除 AlertDialog 二次确认）
- FAB 占位：直接 `NoteRepository.save(Note(0L,...))` + reload（M4 替换为跳编辑器）

**测试策略：** 按高层规划"非典型 TDD"约定，UI 层不写自动化测试，全部走"写完即手测"。

**M3 commit 列表（git log `e70de3b..HEAD`，共 11 个 commit）：**
- `1dee37f` feat(m3): 添加 DateUtils（相对时间）和 TextUtils（摘要截断）工具类
- `81ec738` feat(m3): 添加列表页所需 drawable / menu / strings 资源
- `f3a3c17` feat(m3): 重构列表页布局，加入常驻搜索框、RecyclerView、空状态容器
- `f97ee19` feat(m3): 添加单条笔记卡片布局 item_note_card.xml
- `41137ee` feat(m3): 添加 NoteListAdapter（卡片渲染 + 单击/长按回调）
- `55ff97b` feat(m3): NoteListActivity 接入 RecyclerView + 数据加载 + 空状态切换
- `adbc8f7` feat(m3): FAB 占位实现（直接 save 空 Note；M4 替换为跳编辑器）
- `aae9bc4` feat(m3): 搜索框 200ms debounce 过滤
- `2de4391` feat(m3): 排序对话框 + SharedPreferences 持久化
- `7299ba8` feat(m3): 长按弹 PopupMenu + 收藏切换（删除占位）
- `1b6e8bf` feat(m3): 删除二次确认 AlertDialog + 真删 + 列表刷新

**验收（用户手测通过）：**
- ✅ 新建：FAB → 列表多一条"无标题"
- ✅ 搜索："测试"实时过滤；清空 → 全量恢复
- ✅ 排序：3 种切换 → 顺序变化；杀进程重启保留上次选项
- ✅ 长按菜单：[收藏 / 删除]；收藏 → ⭐；删除 → 二次确认 → 移除
- ✅ 空状态：列表空时显示插图 + 文案

**执行模式：** Subagent-Driven Development with general-purpose agent；7 波次（W1: T1+T4 并行；W2: T2+T3 并行；W3: T5；W4: T6；W5: T7-T11 五合一），每 commit 后 BUILD SUCCESSFUL 验证。

## M4 完成详情（2026-05-23）

**产出：**
- 资源（Task 1）：编辑器/工具栏 drawables（ic_arrow_back / ic_more_vert / ic_color_dot / ic_image / ic_checklist / ic_format_bold/italic/underline/strikethrough/h1/h2 + shape_toolbar_btn_pressed）+ colors（toolbar_bg / toolbar_btn_selected / 5 色 palette）+ 词条（编辑器/工具栏/颜色名/M5-M6 占位）+ Manifest 注册 NoteEditorActivity
- 布局（Task 2-3）：activity_note_editor.xml（Toolbar + ScrollView + 标题 + 块容器 + 工具栏底栏）+ block_text.xml（merge + EditText#block_edit）+ toolbar_text.xml（HorizontalScrollView + 12 按钮 + 3 分隔线）+ themes 工具栏按钮样式（EditorToolbarBtn / EditorToolbarDivider）
- 工具（Task 4，TDD）：util/SpanConverter.kt（Spannable ↔ List&lt;TextSpan&gt; 双向转换；6 种 span 白名单：BOLD/ITALIC/UNDERLINE/STRIKETHROUGH/RelativeSizeSpan/ForegroundColorSpan；常量 SIZE_SMALL=0.85f / MEDIUM=1.0f / LARGE=1.25f；颜色 #RRGGBB 大写六位）+ SpanConverterTest 15 项 Robolectric 单测
- View 层（Task 5、Task 8）：view/block/BlockView.kt（abstract LinearLayout 基类 + Callback 三方法 onRequestSplitAfter/onRequestDelete/onFocusGained）+ view/block/TextBlockView.kt（EditText + heading 应用 + Spannable 渲染 + ENTER/DEL key 处理 + TextWatcher pendingApplier 钩子）+ view/toolbar/TextToolbarView.kt（12 按钮 lazy + 6 方法 Listener + setColorIndicator/clearColorIndicator）
- 控制层（Task 6、Task 8-10）：controller/editor/EditorPresenter.kt（currentBlocks 列表 + pendingInline/pendingSize/pendingColor 三态 + toggleInline / applyInlineToRange / applyPendingTo / toggleSize / pickColor / toggleHeading + onRequestSplitAfter / onRequestDelete + Image/Checklist 透传缓存 passthroughBlocks）+ NoteEditorActivity.kt（noteId 加载/保存 + Toolbar 接通 6 个 Listener callback + 5 色 AlertDialog 弹窗 + Color tint 反馈）
- 列表页接通（Task 7）：NoteListActivity 卡片单击 + FAB 都跳 NoteEditorActivity（替换 M3 两处 Toast 占位）

**测试统计：** `./gradlew :app:test` 共 **57 项 PASSED**（M2 42 + M4 15）。M4 新增的 SpanConverterTest 覆盖：
- 6 种 span round-trip（applyTo → toTextSpans → applyTo 等价）
- 字号 0.85/1.0/1.25 阈值 0.01 容差
- 颜色 #RRGGBB 大写六位规范化
- 边界防御（start&lt;0 / end&gt;length / start&gt;=end 跳过）
- 非法颜色 runCatching 容错

**M4 commit 列表（git log `2767aed..9bf0316`，共 15 个 commit）：**
- `26b41bf` docs(m4): 提交 M4 编辑器骨架详细实施计划
- `bf57118` feat(m4): 编辑器资源准备（drawables + 颜色 + 词条 + Manifest）
- `e0c64d0` feat(m4): 编辑器主布局（Toolbar + ScrollView + 标题/块容器 + 底栏占位）
- `a4aa0bd` feat(m4): 文本块与编辑器工具栏布局
- `7df5ab1` fix(m4): 工具栏 3 个图标按钮补点击反馈与焦点态
- `b06e94c` feat(m4): SpanConverter（Spannable↔TextSpan 互转）+ Robolectric 单测
- `09c362a` feat(m4): BlockView 抽象基类 + TextBlockView（EditText + heading + Span 渲染）
- `0e1a2f4` feat(m4): EditorPresenter + NoteEditorActivity 骨架（加载/保存/标题/块容器）
- `0adb22d` fix(m4): EditorPresenter 缓存 Image/Checklist 块以避免重保存丢数据
- `ee826fb` feat(m4): 列表页卡片点击与 FAB 接入编辑器（替换 M3 占位）
- `2be5299` feat(m4): 编辑器工具栏 + 行内 B/I/U/S（选区生效 + 无选区 pending）
- `d0252a4` fix(m4): 行内样式清 span 时只清 CharacterStyle + 修正插入范围
- `52f02ed` feat(m4): 字号 A-/A/A+ + 颜色 5 色（弹窗 + 选区/pending 双模式）
- `87cf695` fix(m4): 颜色按钮 tint 跟随 pickColor 返回值（无 pending 时清除）
- `9bf0316` feat(m4): 块级 H1/H2 切换（光标所在 TextBlock 字号切换）
- 收尾 commit：docs(m4): 标记 M4 编辑器骨架完成

**验收：**
- ✅ `./gradlew :app:assembleDebug` 通过（约 1s 增量构建，无警告）
- ✅ `./gradlew :app:test` 全部 57 项 PASSED（debug + release 两轮）
- ✅ 每个任务完成后 Spec compliance review + Code quality review 双轨通过；其中 Task 8 / Task 9 各发现 1 条 Critical/Important 已修复并复审通过
- ✅ Task 11 静态走查 5 个 check（中段/末尾 Enter / 空块退格 / 第一块不可删 / split 后焦点 / Callback 签名）全部对齐 PRD §8.2
- ⏳ 真机 9 条手测留待用户走查（PRD §M4 高层验收 1-9：新建/选区加粗/pending 加粗/字号/颜色/H1/段中段末回车/空块退格/退出再进数据保留）

**执行模式：** Subagent-Driven Development（writing-plans → 用户审阅 → 严格串行 12 任务）；每个任务的 implementer DONE → spec reviewer → code quality reviewer → fix（如有）→ re-review → 标记完成。Task 11 仅做静态走查（无需 commit），Task 12 仅写 STATUS。

## 里程碑依赖关系

```
M1 ✅ → M2 数据层 → M3 列表页
                  └→ M4 编辑器骨架 → M5 图片清单
                                  └→ M6 手写 Overlay
                                                  └→ M7 打磨
```

## PRD 决策快照（供后续无上下文时回溯）

- 包名：`com.fan.hwnote.app`
- 显示名：备忘录
- minSdk 24 / targetSdk 36（按工程实际版本）
- Kotlin · MVC · XML View
- 允许：AppCompat / RecyclerView / Material / ConstraintLayout / Lifecycle（仅 lifecycleScope）
- 不允许：ViewModel / LiveData / Room / Navigation / WorkManager / Hilt / Compose
- DB：SQLiteOpenHelper（不用 Room）
- JSON：org.json（不引 Gson/Moshi）
- 图片：Glide（基础 API，不引 compiler）
- MVP 范围：增删改 + 收藏 + 富文本（B/I/U/S + 字号 + 颜色 + H1/H2）+ 图片（多选+拍照）+ 清单 + 手写（4 笔种 8 色 3 粗细 + 橡皮 + 撤销/重做 + 清空）+ 搜索 + 排序
- 不做：分类 / 置顶 / 回收站 / 提醒 / 加锁 / 导出 / 分享 / 录音 / 备份 / 深色模式
- UI：单列卡片列表，主色 #00897B（沉稳青绿）
- 编辑器架构：FrameLayout > ScrollView > [LinearLayout 内容层 + HandwritingOverlayView 手写层]
- 7 个实施里程碑：基础工程 → 数据层 → 列表页 → 编辑器骨架 → 图片/清单 → 手写 Overlay → 打磨

## M5 完成详情（2026-05-23）

**产出：**
- 资源（Task 1-2）：图片来源 / 删除二确认 / 清单项 hint 等 strings 词条 + ic_camera/ic_gallery vector + bg_image_block/bg_checklist_item 背景 + block_image.xml + block_checklist_item.xml
- 工具（Task 3，TDD）：`util/ImageCompressor.kt`（二阶段解码 inJustDecodeBounds + inSampleSize → Matrix.postScale；长边 ≤1920 / JPEG 85；返回 Result(width, height)；OOM-safe scale + IOException 收窄 + 半成品清理）+ 3 项 Robolectric 单测
- View 层（Task 4-6）：`view/block/ImageBlockView.kt`（Glide 加载 `NoteFileStorage.imageFile(noteId, fileName)` + 长按 AlertDialog 二确认 + `onDetachedFromWindow` 释放 dialog + `.dontAnimate()` 防闪烁）+ `view/block/ChecklistItemView.kt`（CheckBox + EditText + STRIKE_THRU paint flag + 抽提的 OnCheckedChangeListener + ENTER/DEL key listener）+ `view/block/ChecklistBlockView.kt`（多 item 编排：onEnterAtEnd 新增 / onBackspaceWhenEmpty 删项或整块）
- 控制层（Task 7-11）：`EditorPresenter` 删 `passthroughBlocks` 改真渲染（`bind` / `collectCurrentNote` / `addImageBlockView` / `addChecklistBlockView` / `insertImageBlocksAtFocus` / `insertChecklistBlockAtFocus` / `onRequestDelete` 图片清文件 + `var noteId` 字段）+ `NoteEditorActivity` 三 launcher（gallery / camera / camera-permission，均为 property initializer）+ `ensureNoteSavedAndThen`（新笔记落库后再插图）+ `showImageSourceDialog`（弹"相册/拍照"）+ `compressAndInsertImages`（IO 解压 + Main 插入；新增 `onDone` 参数让拍照清理跟 IO 完成排队）+ `launchCamera`（FileProvider authority `com.fan.hwnote.app.fileprovider`；`cacheDir/camera/<uuid>.jpg`）

**测试统计：** `./gradlew :app:test` 共 **60 项 PASSED**（M2 42 + M4 15 + M5 ImageCompressor 3）。Clean build：`./gradlew :app:clean :app:assembleDebug :app:test` BUILD SUCCESSFUL in 11s，0 skipped / 0 failures / 0 errors。

**M5 commit 列表（git log `2767aed..HEAD`，共 16 个 commit）：**
- `2af2530` docs(m5): 提交 M5 图片/清单块详细实施计划
- `fbfb8eb` feat(m5): 图片/清单块资源准备（drawables + 词条 + 颜色）
- `890b5e8` feat(m5): 图片块与清单项布局 xml
- `9f583a5` style(m5): block_checklist_item 字号改用 @dimen/editor_text_normal
- `003fc47` feat(m5): ImageCompressor 长边 1920 JPEG 85 压缩 + Robolectric 单测
- `93224e4` fix(m5): ImageCompressor 防 OOM 泄漏 + 收窄异常 + 清理半成品
- `b7c545c` feat(m5): ImageBlockView 用 Glide 加载本地图片 + 长按删除
- `f91196b` fix(m5): ImageBlockView 去重长按 + 对话框生命周期 + Glide 优化
- `666fb3a` feat(m5): ChecklistItemView 单行 CheckBox + EditText + 删除线
- `12d2681` fix(m5): ChecklistItemView 删除死代码 TextWatcher + 抽提勾选监听
- `0a3d02c` feat(m5): ChecklistBlockView 多项编排（新增/删除/焦点迁移）
- `77b3b08` style(m5): ChecklistBlockView 删除未使用的 LinearLayout 导入
- `5cbb9e7` feat(m5): EditorPresenter 接通真实图片/清单渲染（删 passthroughBlocks）
- `beb12f5` feat(m5): 清单工具栏按钮接通 Presenter + loadNote/saveNote 同步 noteId
- `7271b3d` feat(m5): 图片工具栏按钮 → 弹相册/拍照 + 多选解压 + CAMERA 权限
- `7c0cf6c` fix(m5): 拍照清理改到 IO 完成后执行（修复删除竞态）
- 收尾 commit：docs(m5): 标记 M5 图片/清单块完成

**验收：**
- ✅ `./gradlew :app:clean :app:assembleDebug` 通过（11s 全量构建，无 warning）
- ✅ `./gradlew :app:test` 全部 60 项 PASSED（debug + release 两轮）
- ✅ Task 12 静态走查 5 条 Check 全部对齐 PRD §8.4（图片插入焦点回 TextBlock / 长按删图清文件+块 / 清单 ENTER 仅末尾新增 / 空项退格唯一→整块上抛非唯一→删项 / CAMERA 拒绝不崩相册仍可用）
- ⏳ 真机手测留待用户走查（PRD §M5 高层验收 1-7：单图插入/多图插入/拍照插入/长按删图（含文件）/清单基础编辑/清单勾选删除线/清单空项退格删项 + 杀进程重启数据保留）

**执行模式：** Subagent-Driven Development（writing-plans → 用户审阅 → 严格串行 12 任务）。每个任务的 implementer DONE → spec reviewer → code quality reviewer → fix（如有）→ re-review → 标记完成。Task 9/10/11 合并为一次 commit（Activity 三段相互依赖编译），Task 12 仅做静态走查 + STATUS 收尾。code quality review 共触发 4 次 Important fix：T3 ImageCompressor OOM/exception、T4 ImageBlockView dialog 生命周期/Glide 闪烁、T5 ChecklistItemView 死 TextWatcher、T11 拍照清理竞态。

**M7（打磨）待办（M5 评审遗留的 Important，因超出 M5 计划范围未在 M5 内修）：**
- `pendingCameraOutputUri` / `pendingCameraOutputFile` 不走 `onSaveInstanceState`：相机触发系统级 process death 后重启会丢拍照结果 + 泄漏 cache 文件。M7 加 Parcelable 持久化。
- `ensureNoteSavedAndThen` 与 `onPause.saveNote` 在新笔记 0L→insert 路径上存在窄竞态窗口（用户点图标后立刻按 Home 可能 double-insert）。M7 加 "save-in-flight" flag 互斥。

## 编辑器 UX 打磨（2026-05-23）

**起因：** M5 真机走查暴露 3 个对齐华为 Note 的 UX 缺陷，用户给出明确改动方向后立项打磨。仅改 UI 表现层，逻辑层（EditorPresenter / BlockView / 数据层）零改动；60 项单测全部沿用通过。

**改动点：**
1. **空白区点击聚焦** — `blocks_container` 内层 LinearLayout 加 `clickable=true; focusable=false`；`EditorPresenter.focusLastTextBlock()` 倒序找最后一个 `TextBlockView` 聚焦并 `imm.showSoftInput()` 弹键盘；`importantForAccessibility="no"` 防 TalkBack 误读。
2. **软键盘顶起工具栏** — 根布局 `LinearLayout` 加 `id=editor_root` + `fitsSystemWindows=true`；`onCreate` 注册 `ViewCompat.setOnApplyWindowInsetsListener`，取 `Type.ime()` + `Type.systemBars()` 两类 inset 的 `bottom` 最大值，设为根 padding（修复 Android 15 + targetSdk 36 强制 edge-to-edge 后 `adjustResize` 不再自动缩布局的问题）。
3. **12 键工具栏 → 4 键 + 样式 BottomSheet** — `toolbar_text.xml` 重写为横排 4 个 `ImageView`（清单 / 样式 / 图片 / 手写），`weight=1` 平铺；`TextToolbarView` 简化为 4 callback；新增 `view/toolbar/StylePickerBottomSheet.kt`（`BottomSheetDialog`，4 行控件 = B/I/U/S + 字号 3 档 + 5 色圆点 + H1/H2，直调 Presenter 既有方法）；新增 `dialog_style_picker.xml`；新建 `ic_handwriting.xml` + `ic_format_style.xml`（Material `text_format` Aa 字形）；删 `tb_color_cd` / `dialog_pick_color_title` / `toolbar_btn_selected` 三个死资源；删 `NoteEditorActivity.showColorPickerDialog()`。

**涉及文件（共 9 个 + 2 新增 drawable）：**
- 修改：`activity_note_editor.xml`、`NoteEditorActivity.kt`、`EditorPresenter.kt`、`toolbar_text.xml`、`TextToolbarView.kt`、`strings.xml`、`colors.xml`
- 新增：`dialog_style_picker.xml`、`StylePickerBottomSheet.kt`、`ic_handwriting.xml`、`ic_format_style.xml`

**polish commit 列表（git log，按时间序）：**
- `3e56276` feat(polish): 根布局接通 IME inset 顶起工具栏
- `907d85c` feat(polish): blocks_container 空白点击聚焦最后 TextBlock
- `d3963e1` fix(polish): 空白点击补 showSoftInput + a11y 收拢
- `aa068a3` feat(polish): 新增 ic_handwriting + 样式/手写词条
- `75f58d9` feat(polish): 新增 dialog_style_picker BottomSheet 布局
- `0b7c34a` feat(polish): 新增 StylePickerBottomSheet
- `6a2b548` fix(polish): StylePickerBottomSheet 字号 token 对齐 medium
- `2547032` feat(polish): 工具栏改 4 键 + 接通 StylePickerBottomSheet
- `47f3918` fix(polish): 工具栏样式按钮换 text_format 图标 + 清死资源

**验收：**
- ✅ `./gradlew :app:clean :app:assembleDebug :app:test` 全绿，60/60 PASSED（debug + release 两轮）
- ✅ 每个 polish 任务双轨 review 通过；T2 Important（`focusEditEnd` 不弹键盘）+ T5 Critical（size token 不一致）+ T6 Important（btn_style 图标白底白，且语义不准）已修
- ⏳ 真机手测留待用户走查（PRD 对齐：空白点焦 / IME 顶栏 / 4 键工具栏 / 样式 BottomSheet 行内+字号+颜色+H1/H2 / 手写按钮占位 Toast / 现有功能回归）

**执行模式：** Subagent-Driven Development，串行 7 任务（T1→T2→T3→T4→T5→T6→T7），每任务双轨 review；T6 因 Listener 接口变更跨 3 文件做合并 commit。

## M6 完成详情（2026-05-23）

**产出：**
- 资源（T1）：8 色调色板（hw_color_black/red/orange/yellow/green/teal/blue/purple）+ 9 vector drawables（ic_pen/brush/marker/pencil/eraser/undo/redo/clear_all/check）+ 11 个手写工具栏 strings 词条。`toast_handwriting_placeholder` 暂保留（T9 引用已替换为 `enterHandwritingMode()`，可在 M7 清理）。
- 笔效（T2）：`view/handwriting/BrushPainter.kt` — 4 笔种 Paint 工厂（pen / brush + BlurMaskFilter / marker alpha=140 + 宽×1.6 / pencil + 16×16 代码生成噪点 BitmapShader）。无 PNG 资源依赖。
- 橡皮（T3，TDD）：`view/handwriting/StrokeEraser.kt` — 笔画级 hit-test：bbox 粗筛 + 圆心到线段细判 + width/2 容差。**算法修正**：plan 原码 broad-phase 用裸 radiusPx 与 fine-phase（radius+width/2）不一致，对零高度/零宽度 bbox（水平/垂直笔画）误剔除；implementer 统一用 `threshold = radiusPx + s.width/2f`。`StrokeEraserTest` 5 项 JUnit 5 单测覆盖空集 / 单 stroke 命中 / bbox 内但远离段落 / 半径加 width/2 阈值 / 单点 stroke 退化。
- Overlay 骨架（T4）：`view/handwriting/HandwritingOverlayView.kt` — 透明 View，状态自洽（strokes / undoStack / redoStack / inProgressPoints / isErasing / currentBrush/Color/Width / isHandwritingMode）。**Z-order 修正**：`Action.Erase` 改持 `List<IndexedValue<Stroke>>`（commit `ec4cbe5`），undo 部分擦除时按原索引插回保留绘制顺序，避免 MARKER/PENCIL 半透明叠加错位。`isHandwritingMode` setter 增加 `field == value` 短路 + 双向清 in-progress 状态。
- Overlay 行为（T5）：onTouchEvent 区分画笔与橡皮路径；onDraw 把 stroke 转 Path 用 BrushPainter 重绘。**T4 桥接**：plan 原码 `pushErase(erasedThisGesture.toList())` 与 T4 修正后的 `pushErase(List<IndexedValue<Stroke>>)` 签名不兼容；implementer 在 ACTION_DOWN 用 `gestureSnapshot.addAll(strokesRef())` 抓快照，ACTION_UP 用 `gestureSnapshot.withIndex().filter { it.value in erasedSet }` 反查原索引，保证 Z-order 撤销正确。
- 布局接入（T6）：`activity_note_editor.xml` 在 NestedScrollView 与 editor_content 之间插一层 FrameLayout，Overlay 与内容层同尺寸覆盖；padding 从 NestedScrollView 下沉到 editor_content 让 Overlay 像素对齐内容区。
- 控制层 + 工具栏（T7+T8+T9 合并 commit `1c4732f`）：
  - `EditorPresenter` 增构造参数 `overlay`；`bind()` 把 strokes 给 Overlay；`collectCurrentNote()` 从 Overlay 收 strokes 写回 `NoteContent.handwriting`（替换 emptyList）。
  - `toolbar_handwriting.xml`（11 控件 merge）+ `view/toolbar/HandwritingToolbarView.kt`（4 笔种互斥 selected + 橡皮独立 selected + 颜色 tint + 撤销重做 enabled/alpha 反馈）。
  - `NoteEditorActivity` 新增 handwritingOverlay/handwritingToolbar/textToolbar 三字段；onCreate 接 `editor_content` 的 OnLayoutChangeListener 同步 Overlay 高度（实现内容增长时 Overlay 跟着撑高）；`onHandwritingClicked` 改为 `enterHandwritingMode()`（替换 Toast）；新增 enter/exit/showHandwritingColorDialog（8 色 AlertDialog）/ cycleHandwritingWidth（3 档 1→3→6 循环 Toast）/ refreshUndoRedoEnabled。`editor_content.setOnClickListener` 加 `!isHandwritingMode` 守卫。
  - **Eraser 状态泄漏修正**（commit `40efb78`）：code reviewer 发现 enterHandwritingMode 不重置 `isErasing`，会导致"上次退出时是橡皮，下次进入时 UI 显示笔但实际仍是橡皮"。一行修：进入时强制 `isErasing = false`。
- 静态走查（T10）：4 项 PRD §10 行为通过——手写模式 Overlay 拦截全部 touch（NestedScrollView 不滚动）；进入即收键盘；退出后 Overlay 设 INVISIBLE 而非 GONE 且 onTouchEvent 返回 false 让事件下传滚动恢复；editor_content 空白点击有 `!isHandwritingMode` 守卫。
- 性能（T11）：onDraw 按 `canvas.clipBounds` 跳过 bbox 不相交的 stroke（per-stroke broad-phase culling，pad = width/2 + 4 含 BlurMaskFilter halo）。

**测试统计：** `:app:clean :app:assembleDebug :app:test` 全绿，**65 项 PASSED**（M2 42 + M4 SpanConverter 15 + M5 ImageCompressor 3 + M6 StrokeEraser 5），0 failures / 0 errors / 0 skipped。

**M6 commit 列表（按时间序）：**
- `feat(m6): 手写工具栏资源准备（8 色 + 9 图标 + 词条）`（T1, `be4e183`）
- `feat(m6): BrushPainter 4 笔种 Paint 工厂 + 代码生成噪点`（T2, `467e03e`）
- `feat(m6): StrokeEraser 笔画级橡皮 hit-test + 5 项单测`（T3, `d7427ec`）
- `feat(m6): HandwritingOverlayView 骨架（state + 撤销栈 + setStrokes/getStrokes）`（T4, `655d0e2`）
- `fix(m6): Erase 撤销保留原始 Z-order + isHandwritingMode 双向清状态`（T4 fix, `ec4cbe5`）
- `feat(m6): Overlay 落笔/橡皮 onTouch + Path 回放 onDraw`（T5, `2bf46a9`）
- `feat(m6): 编辑器布局插 FrameLayout 容纳内容层 + 手写 Overlay`（T6, `a17d868`）
- `feat(m6): 接通手写工具栏 + Overlay 模式切换 + 高度同步`（T7+T8+T9, `1c4732f`）
- `fix(m6): 进入手写模式重置橡皮状态防止 UI 与状态错位`（T9 fix, `40efb78`）
- `perf(m6): Overlay onDraw 按 clipRect 裁剪笔画绘制`（T11, `c1eea55`）

**M7（打磨）跟进项（仍来自 M5 + 本里程碑新增）：**
1. （M5 留存）`NoteEditorActivity.pendingCameraOutputUri/File` 不参与 `onSaveInstanceState`，相机进程死亡丢照片 + 缓存泄漏，需 Parcelable 持久化。
2. （M5 留存）新笔记 `ensureNoteSavedAndThen` × `onPause.saveNote` 窄竞态可能 double-insert，需 save-in-flight 标志。
3. （M6 新增）`HandwritingOverlayView.eraseAt` 用 `strokesRef() as MutableList` 硬转，若日后 `strokesRef()` 改返不可变拷贝会静默失效；建议加专属 `internal fun strokesMut(): MutableList<Stroke>`。
4. （M6 新增）`BrushPainter.paintFor` 每次返回新 Paint，onDraw 热路径 50 strokes × 60fps 会 alloc 3000 个 Paint/秒；建议在 BrushPainter 内做 (brush, color, widthDp) 三键缓存。
5. （M6 新增）onDraw 内 `Path` 与 `inProgressPoints.map { StrokePoint(...) }` 每帧重建，可改为类成员复用 `path.reset()` + 仅在 size 变化时 rebuild StrokePoint 列表。
6. （M6 新增）`isHandwritingMode` setter 未清 `gestureSnapshot`，目前依赖 ACTION_UP 自清；属一致性 nit。
7. （M6 新增）`handwriting` 模式下若被外部代码中途切换 `isErasing`，可能丢失 undo（pen DOWN 起始未抓 snapshot，UP 时 erase 分支拿不到原索引）；当前 UI 没有触发路径，留作 M7 加守卫。
8. （M6 新增）`onClearClicked` AlertDialog 复用 `R.string.dialog_delete_message`（"确定删除？此操作不可恢复"），文案与"清空手写"语义略错位；M7 加专属 string。
9. （M6 新增）颜色 8 色 hex 在 `showHandwritingColorDialog` 硬编码，与 `colors.xml` 的 hw_color_* 重复；M7 改为从 resources 读取。
10. （M6 新增）`cycleHandwritingWidth` Toast 文案"细/中/粗"硬编码 Chinese 字面量，未走 strings.xml；M7 抽取。

**PRD §M6 验收（13 条）：** M7 真机走查时一并通过。

## M7 完成详情（2026-05-23）

**起因：** M6 完成后立项打磨 — 把前序里程碑评审中累计下来的 Important / Critical 跟进项一次性收拢，覆盖性能（手写热路径）、鲁棒（进程死亡 / 重复 INSERT / 静默失败）、UX（状态栏遮挡 / 清单交互 / 权限引导 / 文案规范）三个维度。**计划 13 任务串行 subagent 执行；T11 真机手测 16 条全过。**

**改动维度：**

1. **手写性能优化（T3-T5）：**
   - `HandwritingOverlayView.strokesMut()`（T3，commit `f5e1da2`）：把 `as MutableList` 硬转换成内部专属可变引用，setter 同步清 `gestureSnapshot`，杜绝外部传不可变列表时静默失效。
   - `BrushPainter` 三键 Paint 缓存（T4，TDD，commit `be3caf7`）：按 `(brush, color, widthDp)` 复用 Paint 实例，避免 onDraw 热路径 50 strokes × 60fps = 3000 Paint/秒分配；BrushPainterTest 3 单测。
   - `onDraw` Path / 点 buffer 复用（T5，commit `1598c7e`）：类成员 `reusablePath.reset()` + `inProgressBuffer` 仅在 size 变化时 rebuild，去掉每帧两次大对象分配。

2. **鲁棒性修复（T6-T9）：**
   - Camera SavedInstanceState 持久化（T6，commit `cd127fa`）：`pendingCameraOutputUri/File` 进 `onSaveInstanceState`，进程死亡重启后回填，避免丢拍照结果 + 缓存泄漏。
   - 新笔记 save-in-flight 互斥（T7，commit `f3e2feb`）：`@Volatile var saveInFlight` 让 `ensureNoteSavedAndThen` 与 `onPause.saveNote` 在 id==0L 路径上不双 INSERT。
   - NoteRepository.save 失败 Toast（T8，commit `2a9fba7`）：`save()` 返回 -1 时 Main 线程弹 Toast，不再静默丢失。
   - 图片加载失败 Toast + 自动移除坏块（T9，commits `9594b31` + `9ad6ad6`）：`ImageBlockView` 加 Glide RequestListener；失败走 `BlockView.Callback.onImageLoadFailed(view)` 默认方法（默认退到 `onRequestDelete`，Presenter 覆写实现"第一块退化为空 TextBlock"），磁盘 jpg 同步清；抽 `EditorPresenter.purgeImageOnDisk(block)` 在删除/失败两路径复用。

3. **UX 对齐（T1-T2 + T10 + T12-T13）：**
   - 删 `toast_handwriting_placeholder` 死资源（T1，commit `b778914`）：M6 已替换为 `enterHandwritingMode()`，资源不再被引用。
   - 手写清空对话框专属文案（T2，commit `30ce6ba`）：从复用 `dialog_delete_message`（"确定删除？此操作不可恢复"）换成 `dialog_clear_handwriting_message`，语义对齐"清空手写"。
   - Camera 被拒后弹引导对话框跳系统设置（T10，commits `c0d4efa` + `0b7c1ec`）：替代静默拒绝；`runCatching` 跳系统设置失败兜底 Toast；删 `camera_permission_denied` 死资源。
   - 状态栏 inset top padding 修复（T12，commits `72b7a5c` + `623cdff`）：Android 15 + targetSdk 36 强制 edge-to-edge 下 Toolbar 压住系统时间。最终方案：给 `AppBarLayout` 加 `fitsSystemWindows="true"`，Material 自动处理 statusBar inset 并把绿色背景延伸进系统栏；根布局 inset listener 仅管 bottom（IME + 导航栏）。
   - 清单交互对齐华为 Note（T13，commits `f2c1611` + `52a1eeb` + `a0f6148`）：①「点击工具栏清单按钮 + 当前焦点已在某清单项」 → 仅 toggle 当前项（取出文字插成 TextBlock，原项移除）；②「清单最后空项按回车」 → 退出清单（清空则整块替换 TextBlock，否则块后追加 TextBlock）；③「清单中间空项按回车」按用户选择"保持原"仍新增下一项；④「唯一项空且按退格」改走新 Callback `onChecklistConvertBlockToText` 实现 in-place 替换为空 TextBlock（修复光标跳标题 bug）。`BlockView.Callback` 接口加 2 个 default 方法保持基类对清单不可知；`EditorPresenter.toggleChecklistAtFocus()` 走 `container.findFocus()` 父链识别清单项。

**M7 commit 列表（git log `c1eea55..HEAD`，共 17 个 commit）：**
- `b778914` chore(m7): 删 toast_handwriting_placeholder 死资源
- `30ce6ba` fix(m7): 手写清空对话框换专属文案，不再复用删除笔记文案
- `f5e1da2` refactor(m7): Overlay 用 strokesMut() 显式可变引用 + setter 清 gestureSnapshot
- `be3caf7` perf(m7): BrushPainter 按 (brush,color,width) 三键缓存 Paint 实例
- `1598c7e` perf(m7): onDraw 复用 Path + in-progress 点 buffer，去掉每帧分配
- `cd127fa` fix(m7): Camera 待回填 Uri/File 走 SavedInstanceState，避免进程死亡丢照片
- `f3e2feb` fix(m7): 新笔记 save-in-flight 标志互斥，避免 ensureNoteSavedAndThen × onPause 双 INSERT
- `2a9fba7` fix(m7): 笔记保存失败弹 Toast 提示，避免静默丢失
- `9594b31` fix(m7): 图片加载失败弹 Toast 并自动移除坏块（含磁盘 jpg 清理）
- `9ad6ad6` refactor(m7): 图片加载失败走 Callback.onImageLoadFailed + 抽 purgeImageOnDisk 复用
- `c0d4efa` fix(m7): 相机被拒后弹引导对话框跳系统设置授权
- `0b7c1ec` chore(m7): 删 camera_permission_denied 死资源 + 设置跳转失败兜底 Toast
- `72b7a5c` fix(m7): 编辑器根布局 inset 顶部 padding 取 statusBar 高度，避免压住系统时间
- `623cdff` fix(m7): AppBarLayout fitsSystemWindows 自处理 statusBar，绿色延伸进系统栏
- `f2c1611` feat(m7): 清单 toggle 化 + 空项退格/回车自动退出清单
- `52a1eeb` fix(m7): 清单中间空项回车保持原行为，仅末尾空项退出清单
- `a0f6148` chore(m7): EditorPresenter 内 ChecklistItemView 三处 FQN 改 import

**测试统计：** `:app:test` 共 **68 项 PASSED**（M2 42 + M4 SpanConverter 15 + M5 ImageCompressor 3 + M6 StrokeEraser 5 + M7 BrushPainter 3），0 failures / 0 errors / 0 skipped。`:app:assembleDebug` 全绿。

**验收：**
- ✅ `./gradlew :app:test` 全部 68 项 PASSED（debug + release 两轮）
- ✅ `./gradlew :app:assembleDebug` 通过
- ✅ 每个 M7 任务双轨 review 通过；多次触发 Important fix：T9 抽象泄漏（`cb is EditorPresenter` → Callback 默认方法）、T10 orphaned string + 跳设置静默失败、T12 状态栏绿色延伸方案、T13 spec compliance Q2 漏 `idx == items.size - 1` 守卫 + nice-to-have FQN 清理
- ✅ 真机手测 16 条全过（用户 2026-05-23 完成走查），含 4 条 T12 / T13 回归：①状态栏不再压住系统时间；②清单空项退格转 TextBlock 不再跳标题；③清单作为最后块，末尾空项回车成功退出到 TextBlock；④焦点在清单项时点工具栏清单按钮仅取消当前项

**执行模式：** Subagent-Driven Development，严格串行 13 任务（T1→T13）。每任务的 implementer DONE → spec reviewer → code quality reviewer → fix（如有）→ re-review → 标完成；T11 仅做真机手测 + STATUS 收尾，无 commit；多个 fix commit 由 reviewer 反馈触发。

## M8 完成详情（2026-06-04）

**起因：** M7 完成 + 全 7 里程碑真机走查通过后，用户参考华为 Note 实机使用反馈，发起 9 项体验改动需求。先做 PRD §13 scope expansion 把 9 项收入新 baseline 并拆 M8-M11 四个里程碑，再启动 M8 — **4 项轻量 UI 表现层改动**，不动数据模型也不引入 DB 迁移。

**4 改动点：**

1. **列表页状态栏 inset（T1，commit `57e7306`）** — `activity_note_list.xml` 的 AppBarLayout 加 `android:fitsSystemWindows="true"`，与 M7 T12 编辑器侧同 pattern；Material 自动消化 statusBar inset 并把 `@color/primary` 背景延伸进系统栏，修"备忘录"标题压住系统时间的 bug。
2. **图片块 ShapeableImageView 12dp 圆角（T2，commits `1679cf6` + `7802169`）** — `block_image.xml` 把 `ImageView` 换成 `com.google.android.material.imageview.ShapeableImageView`，删 `android:background`（黑边来源），加 `app:shapeAppearanceOverlay`；`themes.xml` 新增 `ShapeAppearance.HwNote.ImageBlock`（`parent=""` + `cornerFamily=rounded` + `cornerSize=@dimen/radius_image`）；`dimens.xml` 新增 `radius_image=12dp`。`bg_image_block.xml` 因仍被 Glide `.error()` 作占位图引用未删（非阻塞 note，可后续单独处理）。视觉对齐华为 Note 自然样式。
3. **清单按钮反向 toggle（T3，commit `62649dc`）** — `EditorPresenter.toggleChecklistAtFocus()` 加新分支：焦点在 TextBlock 时 → 原地把该 TextBlock 转 ChecklistBlock（首项 = 该块当前文字，heading/spans 丢失仅保文字，符合数据模型简化语义）；保留既有"焦点在清单项 → 取消该项"和"无焦点 → 末尾追加新清单块"两分支。修用户抱怨的"清单按钮总是从下一行开始"反直觉行为。
4. **排序 BottomSheet 2 选项 + 删 TITLE_ASC（T4，commits `b9d08fb` + `bb6ff76` + `0096a0b`）** — `NoteRepository.SortBy` 删 `TITLE_ASC` 枚举值 + `list()` 同分支删；`NoteListActivity.showSortDialog()` 由 `AlertDialog.setSingleChoiceItems` 改 `BottomSheetDialog` + `RadioGroup`；新增 `dialog_sort_picker.xml`；`strings.xml` 文案改"编辑时间"/"创建时间"对齐用户原话，新增 `sort_picker_title="排序方式"`，删 `sort_title_asc`。旧装机 SharedPreferences 存的 "TITLE_ASC" 由 `loadSort()` 既有 `runCatching.getOrDefault(UPDATED_DESC)` 自动降级，无需 migration 代码。视觉对齐华为 Note 截图样式。

**涉及文件：**
- 修改：`activity_note_list.xml`、`block_image.xml`、`themes.xml`、`dimens.xml`、`EditorPresenter.kt`、`NoteRepository.kt`、`NoteListActivity.kt`、`strings.xml`
- 新增：`dialog_sort_picker.xml`
- 删除：`styles.xml`（短暂存在 1 commit 后并入 themes.xml）、`NoteRepositoryListTest.sort by title ascending uses title order`（TITLE_ASC 连带删除）

**M8 commit 列表（git log `dc149cb..HEAD`，共 7 个 commit）：**
- `57e7306` fix(m8): 列表页 AppBarLayout fitsSystemWindows，避免标题压系统时间
- `1679cf6` fix(m8): 图片块换 ShapeableImageView 12dp 圆角，删黑边白底
- `7802169` refactor(m8): 图片块 ShapeAppearance 合入 themes + 抽 dimen + 重命名
- `62649dc` feat(m8): 清单按钮反向 toggle —— 当前 TextBlock 原地转清单首项
- `b9d08fb` feat(m8): 排序改 BottomSheet 2 选项（编辑时间/创建时间），删 TITLE_ASC
- `bb6ff76` fix(m8): 排序文案对齐用户原话（编辑时间 / 创建时间）
- `0096a0b` chore(m8): NoteListActivity 2 处 FQN 改 import

**测试统计：** `:app:clean :app:assembleDebug :app:test` 全绿，**67 项 PASSED**（M7 基线 68 → M8 删 TITLE_ASC 枚举强制连带删除 `NoteRepositoryListTest.sort by title ascending uses title order`，新基线 67），0 failures / 0 errors / 0 skipped。UPDATED_DESC + CREATED_DESC 两枚举值原有单测保留，覆盖未失守。

**验收：**
- ✅ `./gradlew :app:clean :app:assembleDebug :app:test` 全绿，67/67 PASSED（debug + release 两轮）
- ✅ 每个 M8 任务双轨 review 通过；多次触发 fix：T2 nice-to-have（合入 themes + 抽 dimen + 重命名）、T4 spec violation（文案"按修改时间"未对齐"编辑时间"）+ nice-to-have（FQN 改 import）
- ✅ 真机手测 6 条全过（用户 2026-06-04 完成走查）：①列表页 statusBar 不压系统时间，绿色延伸进系统栏；②图片块圆角自然，无黑边；③焦点在带文字 TextBlock，点清单按钮 → 该块原地变清单首项，文字保留；④焦点在空 TextBlock 同样原位转空清单项；⑤排序 BottomSheet 弹起，2 选项切换生效，杀进程保留；⑥M7 既有功能回归（清单内退格 / 末尾空项回车退出 / 图片插入 / 拍照 / 手写 / 标题 / 收藏切换）

**执行模式：** Subagent-Driven Development，严格串行 4 任务（T1→T2→T3→T4）+ T5 全量验证 + STATUS。每任务 implementer DONE → spec reviewer → code quality reviewer → fix（如有）→ 标完成；T2/T4 各引入 1 个 fix commit + 1 个 chore/refactor commit。

## 项目完成总览

8 个里程碑（M1-M8）完成（2026-05-22 ~ 2026-06-04）；M9-M11 待执行：

| # | 里程碑 | commit 数 | 单测增量 | 关键产出 |
|---|---|---|---|---|
| M1 | 基础工程 | — | 1 | 工程改造去 Compose + Application + 入口 Activity |
| M2 | 数据层 | 12 | 41 | sealed Block + NoteJson + SQLiteOpenHelper + NoteFileStorage + Repository |
| M3 | 列表页 | 11 | 0（UI 层） | RecyclerView + 搜索 + 排序持久化 + 长按 PopupMenu + 删除二确认 |
| M4 | 编辑器骨架 | 16 | 15 | SpanConverter + BlockView + TextBlockView + EditorPresenter + 12 键工具栏 |
| M5 | 图片块/清单块 | 16 | 3 | ImageCompressor + ImageBlockView + ChecklistBlockView + 多选相册/拍照 |
| 编辑器 UX 打磨 | UI 表现层 | 10 | 0 | 空白点焦 + IME 顶栏 + 4 键工具栏 + StylePickerBottomSheet |
| M6 | 手写 Overlay | 10 | 5 | HandwritingOverlayView + 4 笔种 BrushPainter + 笔画级 StrokeEraser + 6 键工具栏 + 8 色 + 3 粗细 |
| M7 | 打磨 | 17 | 3 | 手写性能（Paint 缓存 / Path 复用）+ 鲁棒（save-in-flight / SavedInstanceState / 图片 Toast）+ UX（状态栏 / 清单 toggle / Camera 引导） |
| M8 | 体验小修 | 7 | −1（删 TITLE_ASC 测试） | 列表 statusBar / 图片圆角 / 清单 toggle 反向 / 排序 BottomSheet 2 选项 |

**累计：** 99 个 commit（不含 docs/计划 commit），67 项自动化单测全绿，PRD MVP + §13 已晋升的"分类 + 录音 + 撤销重做"中 M8 部分（图片圆角 / 排序 / 清单反向 / 状态栏 4 项轻量 UI）100% 覆盖。架构守住"Block 块组合 + 手写 Overlay 透明层"原始决策，未引入 Compose / ViewModel / LiveData / Room / Hilt / Navigation。

**M9-M11 待执行（PRD §13 范围）：**
- **M9 分类 + 软删除 + metadata strip** — DB v2 迁移（ALTER TABLE notes ADD COLUMN category_id + deleted_at；CREATE TABLE categories）+ 顶部下拉切换 + 最近删除页（30 天保留）+ 编辑器 metadata strip
- **M10 语音录入** — `Block.AudioBlock` sealed 新成员 + 编辑器内录制/播放/删除 + m4a/AAC 编码 + RECORD_AUDIO 权限
- **M11 撤销 / 重做** — `EditHistoryManager` 命令模式 + 工具栏按钮 + 跨保存清栈

**后续可选方向（仍超出 PRD §13 范围，需用户重新决策）：** 置顶 pin / 提醒 / 加锁 / 导出 / 分享 / 备份 / 深色模式 / 多端同步。
