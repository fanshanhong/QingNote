# HwNote · 项目进度

最后更新：2026-05-23（编辑器 UX 打磨完成）

## 阶段地图

| 阶段 | 状态 | 产出 |
|------|------|------|
| 1. 头脑风暴 / 需求澄清 | ✅ 完成 | 8 个核心需求决策（功能范围、Jetpack 边界、minSdk、富文本范围、手写笔种、图片来源、列表布局、主色） |
| 2. 架构方案 | ✅ 完成 | 选定 **Block 块组合 + 手写 Overlay 透明层** |
| 3. PRD / 技术设计 | ✅ 完成 | `docs/superpowers/specs/2026-05-22-hwnote-design.md`（12 节，~450 行） |
| 4. 规格自审 | ✅ 完成 | 修复 7 处一致性问题（heading 块属性归位、Span 6 种、笔效/橡皮算法详写、ACTION_GET_CONTENT 替换、Glide compiler 去除、plain_text 规则补、行内/块级样式分离） |
| 5. 用户审阅 PRD | ✅ 完成 | 用户确认无修改，进入下一步 |
| 6. **实施计划编写** | ✅ 完成 | `docs/superpowers/plans/2026-05-22-hwnote-implementation.md`（高层版，7 个里程碑各一节，685 行） |
| 7. 代码实施 | 🔵 **进行中（M5 完成）** | M1 + M2 + M3 + M4 + M5 详细计划 + 代码；M6-M7 未开始 |
| 8. 手测验收 | 🔵 进行中（M3 列表页通过；M4 自动化通过、真机 9 条留用户走查；M5 自动化通过、真机 7 条留用户走查） | M3 五条高层验收逐条手测通过；M4 SpanConverter 15 项单测 PASSED + 双轨 review 全过；M5 ImageCompressor 3 项单测 PASSED + 12 任务双轨 review 全过 |

## 里程碑进度

| 里程碑 | 状态 | 备注 |
|---|---|---|
| M1 基础工程 | ✅ 完成（2026-05-22） | 工程构建 + 真机启动 + git init |
| M2 数据层 | ✅ 完成（2026-05-22） | 数据层 + 42 单测 |
| M3 列表页 | ✅ 完成（2026-05-22） | RecyclerView + 搜索 + 排序 + 长按菜单 + 删除二次确认 |
| M4 编辑器骨架 | ✅ 完成（2026-05-23） | NoteEditorActivity + EditorPresenter + 块容器 + 12 键工具栏 + B/I/U/S + 字号 + 5 色 + H1/H2；SpanConverter 15 单测 |
| M5 图片块/清单块 | ✅ 完成（2026-05-23） | ImageBlockView + ChecklistBlockView + ImageCompressor（长边 1920/JPEG 85）+ 多选相册 / 拍照 + CAMERA 运行时权限；passthroughBlocks 已删 |
| M6 手写 Overlay | ⏳ 未开始 | — |
| M7 打磨 | ⏳ 未开始 | — |

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

## 下一步建议

M5 已完成，建议进入 **M6 手写 Overlay**：实现 `HandwritingOverlayView`（透明层覆盖在内容层之上）：
- **笔种**：4 种（pen / brush / marker / pencil）+ 笔粗细 3 档 + 8 色调色板 + 橡皮 + 撤销/重做 + 清空 — 来自 PRD §8.5
- **架构**：在 `activity_note_editor.xml` 的 FrameLayout 内已经预留 overlay 位置（M4 骨架时拉的层），M6 把它换成 `HandwritingOverlayView`；Stroke 数据沿用 M2 已序列化的 `Stroke` 实体（4 笔种 + 颜色 + 粗细 + 点序列）
- **存储复用**：M2 已完整支持 Stroke 的 JSON round-trip；笔迹文件目录 `filesDir/notes/<id>/handwriting/` 也已建好（M2 `NoteFileStorage`）；M6 仅做 UI 渲染层 + 工具栏切换
- **工具栏切换**：编辑器底部 Tab 已经有"文本/手写"两挡占位（M4 骨架时拉的），M6 把"手写"挡换成手写工具条（笔种/颜色/粗细 + 橡皮 + 撤销/重做 + 清空 7 个按钮）

执行节奏沿用 M2/M3/M4/M5 的 "writing-plans → 用户审阅 → Subagent-Driven 串行执行 → 双轨 review → STATUS 收尾" 模式。预估 8-12 小时（手写 path 算法 / 4 种笔效真实着色 / 撤销重做栈较繁琐）。

资源：浏览器 visual companion 仍在运行：http://localhost:61835
