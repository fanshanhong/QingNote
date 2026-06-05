# HwNote · 项目进度

最后更新：2026-06-05（M13a 视觉基础+列表页重设计完成）

## 阶段地图

| 阶段 | 状态 | 产出 |
|------|------|------|
| 1. 头脑风暴 / 需求澄清 | ✅ 完成 | 8 个核心需求决策（功能范围、Jetpack 边界、minSdk、富文本范围、手写笔种、图片来源、列表布局、主色） |
| 2. 架构方案 | ✅ 完成 | 选定 **Block 块组合 + 手写 Overlay 透明层** |
| 3. PRD / 技术设计 | ✅ 完成 | `docs/superpowers/specs/2026-05-22-hwnote-design.md`（12 节，~450 行） |
| 4. 规格自审 | ✅ 完成 | 修复 7 处一致性问题（heading 块属性归位、Span 6 种、笔效/橡皮算法详写、ACTION_GET_CONTENT 替换、Glide compiler 去除、plain_text 规则补、行内/块级样式分离） |
| 5. 用户审阅 PRD | ✅ 完成 | 用户确认无修改，进入下一步 |
| 6. **实施计划编写** | ✅ 完成 | `docs/superpowers/plans/2026-05-22-hwnote-implementation.md`（高层版，7 个里程碑各一节，685 行） |
| 7. 代码实施 | ✅ M1-M9 完成 + **M10 完成** | M1+M2+M3+M4+M5+M6+M7+M8+M9 全部落地，**M10 (PRD §13 语音录入)** AudioBlock + 录制/播放/删除 + RECORD_AUDIO 全部落地；85 单测全绿（M9 基线 83 → M10 +2 NoteJsonAudioTest） |
| 8. 手测验收 | ✅ M3 / M5 / M6 / M7 / M8 / M9 / **M10** 用户真机走查全部通过 | M3 五条 + M5 七条 + M6 十三条 + M7 十六条 + M8 六条 + M9 十条 + **M10 十三条**逐条手测通过；M4 SpanConverter 15 + M2 数据层 42 + M5 ImageCompressor 3 + M6 StrokeEraser 5 + M7 BrushPainter 3 + M9 CategoryRepository 5 / NoteDbHelper +2 / SoftDelete +3 / ListFilter +3 / SaveGet +2 + **M10 NoteJsonAudio +2** 单测 PASSED |

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
| M9 分类 + 软删除 + metadata strip | ✅ 完成（2026-06-04） | PRD §13 主力；DB v2 迁移（categories 表 + notes 加 category_id/deleted_at）+ 4 内置筛选 / 分类管理（拖动排序）/ 软删除 30 天回收站 / 编辑器 metadata strip + CategoryPicker；83 单测 + 10 项真机走查全过 |
| M10 语音录入 | ✅ 完成（2026-06-04） | PRD §13 范围；`Block.AudioBlock` + `AudioRecorder`(MPEG_4/AAC/64kbps) + `AudioPlayer`(共享单例) + `AudioRecordingBottomSheet`(走表计时 + 停止/取消) + `AudioBlockView`(播放/暂停/长按删除) + 工具栏扩 5 键 + RECORD_AUDIO 运行时权限 + onPause 兜底停播/取消；85 单测 + 13 项真机走查全过 |
| M11 撤销 / 重做 | ✅ 完成（2026-06-04） | PRD §13 收口；`EditHistoryManager`(双栈+cap50+listener) + 5 Command 子类 (Add/Remove/Move/Replace/ApplySpan/ApplyHeading/ReplaceText) + `CompositeCommand` 多步打包 + Presenter 三 Mutator(silent) + TextBlockView 800ms 防抖 + 顶部 AppBar ↶↷ MenuItem + onPause flush + `NoteRepository.cleanOrphanFiles` 异步清孤 + onSaveSuccess 唯一清栈入口 |
| M12 文件夹层级 | ✅ 完成（2026-06-05） | DB v3 迁移 + Folder/Notebook 实体 + 三级层级 + FolderManagerActivity 三粒度拖动 + NotebookFilterPopupWindow + 编辑器 indicator；137 单测 |
| M13a 视觉基础+列表页重设计 | ✅ 完成（2026-06-05） | 色彩绿→蓝 + 白底大标题 + 筛选面板(FilterPanelAdapter) + 底部导航 + 卡片扁平化 + 笔记本颜色淡化 + Overflow PopupMenu + 排序/删除 Sheet 改版；140 单测 |

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

## M9 完成详情（2026-06-04）

**起因：** M8 四项轻量改动收官后，按 PRD §13 分阶段执行计划进入主力项 M9。本里程碑首次引入 DB schema 迁移（v1→v2），数据层与 UI 层都有结构性扩展；按"分类 + 软删除 + metadata strip"三块并联的 brainstorm 切成 11 任务串行执行。

**4 改动维度：**

1. **DB v2 迁移 + 数据层扩展（T1-T6）：**
   - **Schema 升级（T1，commit `e62c47a`）：** `NoteDbHelper.DATABASE_VERSION` 1→2；`notes` 表加 `category_id INTEGER`（NULL = 未分类）+ `deleted_at INTEGER NOT NULL DEFAULT 0`（0 = 未删，>0 = 软删时间戳）；新建 `categories` 表（id / name / color / display_order / created_at）；新增 `idx_notes_category`、`idx_notes_deleted` 两索引；`onUpgrade(v1→v2)` 仅 ALTER + CREATE，旧数据零损。
   - **Category 实体 + Repository（T2，commits `9886432` + `500cb3a`）：** `entity/Category.kt` + `model/CategoryRepository.kt`（object 单例：init / list（按 display_order）/ get / create / rename / setColor / delete（事务清空被删分类下笔记的 category_id）/ reorder（事务批量 UPDATE display_order）/ count）。
   - **Note 模型扩字段（T3，commit `408b226`）：** `Note` data class 加 `categoryId: Long?` + `deletedAt: Long`；`NoteRepository.save/get/list` 读写新字段，旧装机走默认值降级。
   - **软删除 API + purgeExpired（T4，commit `cf62772`）：** `softDelete(id)` 写 `deleted_at = now`；`restore(id)` 写 `deleted_at = 0`；`deletePermanently(id)` 走旧 `delete` 同语义（行删 + 文件目录清）；`purgeExpired(now)` 批量删 `deleted_at > 0 AND deleted_at < now - 30d` 的笔记 + 文件。
   - **list 加 ListFilter（T5，commit `323e540`）：** `sealed class ListFilter { All / Uncategorized / Favorite / Deleted / data class Category(id) }`；`list(filter, sortBy, query)` 按 filter 拼 WHERE：All 排除已删；Deleted 仅含已删；其他过滤已删 + 加分类/收藏条件。
   - **App.onCreate 启动清理（T6，commit `7249ee4`）：** `GlobalScope.launch(Dispatchers.IO) { runCatching { purgeExpired() } }`；单例 App 生命周期内执行一次，`runCatching` 兜底吞错。

2. **测试基础设施稳定（mid-M9）：**
   - `fix(test) 136f45a`：`NoteDbHelperTest` 跨用例 SQLite 残留导致 "table notes already exists" / "Can't downgrade" 偶发；加 `@Before { ctx.deleteDatabase }` + 每个 `@Test` 结尾 `db.close()`；v1→v2 升级测试改用 `SQLiteDatabase.openOrCreateDatabase(file, null)` 绕过 SQLiteOpenHelper 连接池。
   - `fix(test) e3bedb5`：根因发现 `App.onCreate` 内 `GlobalScope.launch { purgeExpired }` 永不关闭 SQLite 连接，跨用例污染；新建 `TestApp : Application()`（空实现），`robolectric.properties` 加 `application=com.fan.hwnote.app.TestApp` 让单测跳过启动清理。后续 3 次 `--rerun-tasks` 全绿验证。

3. **列表页分类筛选 + 管理（T7-T9）：**
   - **Toolbar filter chip + FilterPickerBottomSheet（T7，commit `067c806`）：** `activity_note_list.xml` Toolbar title 撤掉，中央放可点 chip（`filter_chip_text` + `ic_arrow_drop_down`）；新建 `view/list/FilterPickerBottomSheet.kt`（4 内置项 = 全部/未分类/我的收藏/最近删除 + 用户分类 RecyclerView + 管理分类入口）；`item_filter_row.xml` 复用（圆点 + 名称）；筛选状态持久化到 SharedPreferences（`KEY_FILTER_TYPE` + `KEY_FILTER_CATEGORY_ID`，ListFilter sealed 序列化/反序列化）。
   - **CategoryManagerBottomSheet（T8，commits `3119492` + `10cb3e7`）：** 新建 BottomSheetDialog 容纳"分类列表（拖动排序）+ 新建/编辑/删除"；`ItemTouchHelper.SimpleCallback(UP or DOWN, 0)` 实现长按拖动，`clearView` 回调 flush 新顺序到 DB；编辑器 AlertDialog 4 色选（黄/青/绿/红 hex）；删除 AlertDialog 二确认（T9 替换为 DeleteConfirmBottomSheet）。Fix `10cb3e7`：删除当前正被筛选的分类后 `currentFilter` 仍指向已删分类 id，导致 chip 文案降级"全部"但 reload 返回空列表；`updateFilterChipLabel()` 加 staleness 检测，若 `CategoryRepository.get(id) == null` 即复位 `currentFilter` 到 All 并持久化。
   - **DeleteConfirmBottomSheet + 最近删除菜单（T9，commits `6cd7d18` + `02a02ee`）：** 新建通用 `view/list/DeleteConfirmBottomSheet.kt`（ctor 参数化 title/message/confirmLabel/confirmIsDanger/onConfirm，danger 文案染 `@color/error`）+ `dialog_delete_confirm.xml`（标题 + 信息 + 取消/确认 48dp 横排）；`menu_note_card_deleted.xml` 新建（仅含"恢复 / 彻底删除"）；`NoteListActivity.showCardMenu` filter-aware 选 menu（Deleted 视图→deleted menu，其他→long_press menu）。同步把 T8 `CategoryManagerBottomSheet.onDeleteClicked` 的 AlertDialog 占位换成 DeleteConfirmBottomSheet。**清理：** 删除 `NoteRepository.delete(id)`（与 `deletePermanently` 同语义，0 调用方）+ 测试同步重命名。Fix `02a02ee`：双击守卫（`var fired = false` 共用于 cancel/confirm，防 dismiss 动画期间二次触发）+ `confirmIsDanger=false` 兜底 `text_primary` 默认色。

4. **编辑器 metadata strip + 分类切换（T10）：**
   - commits `93de5b7` + `cb4ab25`。`activity_note_editor.xml` 标题下追加 `metadata_strip`（时间 TextView · 分类 chip：dot ImageView + name TextView）；新建 `view/editor/CategoryPickerBottomSheet.kt`（仅列"未分类" + 用户分类，回调返回 `Long?`）+ `dialog_category_picker.xml` + `shape_circle.xml`；`NoteEditorActivity.refreshMetadataStrip()` 用 `DateUtils.formatRelative` 渲染时间，分类 dot 走 `ImageViewCompat.setImageTintList` + `runCatching Color.parseColor` 防御；`showCategoryPicker` 切换 `loadedNote.categoryId` 后立即 IO 持久化（gating `id > 0L` 避免新笔记重复 INSERT）。
   - **超规范但必要的修正：** `EditorPresenter.collectCurrentNote()` 只 copy title/plainText/content，会丢 bind 后 Activity 侧改的 categoryId；`saveNote` / `ensureNoteSavedAndThen` 都加 `.copy(categoryId = loadedNote.categoryId)` 守护。
   - Fix `cb4ab25`：`refreshMetadataStrip` 内层 `lifecycleScope.launch` 跨次调用未取消，用户快速切分类时旧 launch 后写入造成 last-write-wins race；加 `if (loadedNote !== n) return@launch` 守卫。

**涉及文件清单：**
- **新增（共 13 个）：** `model/entity/Category.kt`、`model/CategoryRepository.kt`、`view/list/FilterPickerBottomSheet.kt`、`view/list/CategoryManagerBottomSheet.kt`、`view/list/DeleteConfirmBottomSheet.kt`、`view/editor/CategoryPickerBottomSheet.kt`、`test/.../TestApp.kt`、`test/.../CategoryRepositoryTest.kt`、`res/layout/dialog_filter_picker.xml`、`res/layout/dialog_category_manager.xml`、`res/layout/dialog_category_editor.xml`、`res/layout/dialog_delete_confirm.xml`、`res/layout/dialog_category_picker.xml`、`res/layout/item_filter_row.xml`、`res/layout/item_category_manage.xml`、`res/menu/menu_note_card_deleted.xml`、`res/drawable/{shape_circle,ic_arrow_drop_down,ic_settings,ic_drag_handle,ic_edit}.xml`
- **修改（共 ~12 个）：** `model/db/NoteDbHelper.kt`、`model/entity/Note.kt`、`model/NoteRepository.kt`、`model/json/NoteJson.kt`、`App.kt`、`controller/list/NoteListActivity.kt`、`controller/editor/NoteEditorActivity.kt`、`controller/editor/EditorPresenter.kt`、`res/layout/activity_note_list.xml`、`res/layout/activity_note_editor.xml`、`res/values/{strings,colors}.xml`、`test/resources/robolectric.properties`、若干现有测试

**M9 commit 列表（git log `bb6ff76..HEAD`，共 17 个 commit）：**
- `40ba28e` docs(m9): 归档 M9 分类+软删除+metadata strip 实施计划
- `e62c47a` feat(m9): DB v2 — notes 加 category_id/deleted_at + 新建 categories 表
- `9886432` feat(m9): Category 实体 + CategoryRepository CRUD（含 reorder 事务）
- `500cb3a` test(m9): 补 CategoryRepository.delete 行删除断言
- `408b226` feat(m9): Note 增 categoryId/deletedAt + Repository 读写适配
- `cf62772` feat(m9): NoteRepository 软删除 API + purgeExpired（30 天过期清理）
- `323e540` feat(m9): NoteRepository.list 支持 ListFilter（All/Uncategorized/Favorite/Deleted/Category）
- `7249ee4` feat(m9): App.onCreate 启动协程清理 30 天过期软删笔记
- `136f45a` fix(test): 稳定 NoteDbHelperTest 跨用例数据库残留
- `067c806` feat(m9): Toolbar filter chip + FilterPickerBottomSheet（4 内置项 + 用户分类）
- `e3bedb5` fix(test): 单测注入 TestApp 跳过 purgeExpired GlobalScope
- `3119492` feat(m9): CategoryManagerBottomSheet — CRUD + 4 色选 + ItemTouchHelper 拖动排序
- `10cb3e7` fix(m9): 删除当前分类后复位 filter 到全部
- `6cd7d18` feat(m9): 删除走底部 BottomSheet 二次确认 + 最近删除菜单切恢复/彻底删
- `02a02ee` fix(m9): DeleteConfirmBottomSheet 加双击守卫 + 默认中性色
- `93de5b7` feat(m9): 编辑器 metadata strip — 时间 · 分类 + 点选 BottomSheet
- `cb4ab25` fix(m9): refreshMetadataStrip 加防竞态守卫
- 收尾 commit：docs(m9): 标记 M9 分类+软删除+metadata strip 完成

**测试统计：** `:app:clean :app:assembleDebug :app:test` 全绿，**83 项 PASSED**（M8 基线 67 → M9 +16），0 failures / 0 errors / 0 skipped。增量明细：
- `CategoryRepositoryTest`（新文件，5）：list/create/rename/setColor/delete + reorder 事务
- `NoteDbHelperTest` +2（→5）：onCreate v2 含 categories + 索引；onUpgrade v1→v2 旧数据兼容
- `NoteRepositoryDeleteFavoriteTest` +3（→7）：softDelete / restore / deletePermanently
- `NoteRepositoryListTest` +4（→9）：ListFilter 4 sealed 子类 × 联合 sort/query
- `NoteRepositorySaveGetTest` +2（→6）：category_id + deleted_at 持久化 round-trip

**验收（用户 2026-06-04 真机走查 10 条全过）：**
1. ✅ 旧装机升级（M8 → M9）：旧笔记自动落"全部"，0 崩溃
2. ✅ Toolbar 中央 "全部 ▼" chip 可见可点
3. ✅ FilterPicker BottomSheet 4 内置项 + 管理入口可见
4. ✅ 新建 3 个分类（不同色）+ 拖动调序 → 重启 App 顺序保留
5. ✅ 删除一个含 1 笔记的分类 → 该笔记落"未分类"
6. ✅ 列表正常态删除笔记 → 底部 BottomSheet 二次确认 → 切"最近删除"能看到
7. ✅ 最近删除：长按 → 恢复 → 笔记回到"全部"
8. ✅ 最近删除：长按 → 彻底删 → 二次确认 → 列表 + 文件目录全清
9. ✅ 编辑器 metadata strip：时间 · 分类可见可点；切换分类 → 退出再进保留
10. ✅ 现有功能回归：搜索 / 排序 / 长按收藏 / 编辑器图片/清单/手写/样式

**执行模式：** Subagent-Driven Development，严格串行 11 任务（T1→T11）。每任务的 implementer DONE → spec reviewer → code quality reviewer → fix（如有）→ re-review → 标完成；fix commit 触发点：T6 测试残留 + GlobalScope 连接泄漏（2 commits）、T8 分类删除后 filter 复位、T9 DeleteConfirmBottomSheet 双击 + 默认色、T10 race 守卫。T11 真机走查由用户完成后再写本 STATUS 收尾 commit。

## M10 完成详情（2026-06-04）

**起因：** M9 分类/软删除/metadata strip 落地后，按 PRD §13 三阶段执行计划进入语音录入。用户 2026-06-04 二次确认三项 UX 决策：①录音流程走"工具栏按钮 → BottomSheet 计时 → 停止后插块"，不沿用对话框；②工具栏从 4 键扩 5 键 weight=1 平铺（拒绝二级菜单收纳）；③录音时长无上限，UI 仅展走表计时，退出编辑器/切后台自动停止。本里程碑首次引入媒体子系统（MediaRecorder + MediaPlayer），但严格隔离在 `model/audio/` 包内不渗透到 BlockView 体系；按"块结构 + 录音 + 播放 + UI 入口"四维度切 13 任务串行 subagent 执行。

**4 改动维度：**

1. **实体 / JSON / Storage 扩展（T1-T2）：**
   - **`Block.AudioBlock`（T1，commit `a56da90`）：** sealed class 新增第 4 个子类 `data class AudioBlock(id, fileName, durationMs)`；`NoteJson.blockToJson/blockFromJson` 增 `type="audio"` 分支，`optLong("durationMs")` 缺失默认 0；`NoteContent` `toPlainText` 加 `is Block.AudioBlock -> Unit // 音频不进搜索`。`NoteJsonAudioTest` 2 项 JUnit 5 单测：round-trip + 缺 durationMs 容错。**计划外修正：** `EditorPresenter.bind()` 内 `when(b)` 是 exhaustive，新增 sealed 子类导致编译失败；implementer 报 NEEDS_CONTEXT，决策为本任务内加 `is Block.AudioBlock -> Unit // T8 接通` 桩分支，T8 替换为真实分发。
   - **`NoteFileStorage` 加 audio 目录（T2，commit `d9bcec3`）：** 沿 M5 imageDir/imageFile 模式新增 `audioDir(noteId)` / `audioFile(noteId, fileName)`；目录布局 `filesDir/notes/<noteId>/audio/<uuid>.m4a`。`deleteNoteDir` 走 `deleteRecursively()` 自动覆盖 audio 子目录，T11 静态核查确认无需改 `NoteRepository.deletePermanently` / `purgeExpired`。

2. **录音 / 播放底层（T3-T4）：**
   - **`AudioRecorder`（T3，commit `4d91021`）：** 76 行，封装 MediaRecorder 生命周期；编码配置 `MIC + MPEG_4 + AAC + 64_000bps + 44_100Hz`；Android 12+ 走 `MediaRecorder(context)` 构造，向下用 no-arg + `@Suppress("DEPRECATION")` + 运行时 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` 分支；暴露 `start(File) / stop(): Long / cancel() / isRecording: Boolean` + 内部 `StartFailed` / `StopFailed` 异常；`cancel()` 释放 recorder + 删 outputFile，吞所有异常防雪崩。
   - **`AudioPlayer`（T4，commit `400e065`）：** 69 行，编辑器内共享单实例；`interface Listener { fun onIdle(token) }`；`play(file, token, listener): Boolean` 内部先 stop 旧 token、文件不存在/解码失败返 false；`isPlaying(token)` 用 `===` 身份比对；`MediaPlayer.setOnCompletionListener` + `setOnErrorListener` 都触发 `releaseInternal + Listener.onIdle(token)`，AudioBlockView 拿回调切 UI 回 ic_play。

3. **UI 层（T5-T7、T9-T10）：**
   - **资源（T5，commit `45a1a9a`）：** AndroidManifest 声明 `<uses-permission android:name="android.permission.RECORD_AUDIO" />`；strings 增 10 条（`tb_record_cd` / `record_audio_permission_dialog_title/message` / `audio_recording_title/stop/cancel` / `audio_record_failed` / `audio_play_failed` / `audio_delete_title/message`）；drawable 增 `ic_mic.xml` / `ic_play.xml` / `ic_pause.xml`（24dp vector，tint `?attr/colorControlNormal`）。
   - **`AudioRecordingBottomSheet`（T6，commit `639eb26`）：** 继承 `BottomSheetDialog`；ctor `(context, targetFile, onComplete: (Long) -> Unit, onCancel: () -> Unit)`；`setCancelable(false) + setCanceledOnTouchOutside(false)` 强制用户明确决策；`onCreate` 内 try-catch 启动 recorder，失败 Toast + dismiss + onCancel；`tickRunnable` 用 Handler.postDelayed 每 500ms 刷新 `formatMmSs`；btnStop/btnCancel 共用 `var terminated = false` 守卫（沿 M9 DeleteConfirmBottomSheet 双击防护模式）；公开 `forceCancel()` 供 Activity onPause 兜底取消。
   - **`AudioBlockView`（T7，commit `2b22b37`）：** 继承 BlockView + 实现 `AudioPlayer.Listener`；`bind` 渲染 "录音 · mm:ss"（走 `audio_label_format` string）；`togglePlay` 内 `isPlaying(this) → stop()` 否则 `NoteFileStorage.audioFile() → AudioPlayer.play()`，失败 Toast；`onDetachedFromWindow` 自停在播；`onIdle(token === this)` 切 UI 回 ic_play；长按弹 `DeleteConfirmBottomSheet`（"删除录音"）→ `callback?.onRequestDelete(this)`。
   - **工具栏扩 5 键（T9，合入 T10 commit）：** `toolbar_text.xml` 在 btn_handwriting 之后追加 btn_record（weight=1）；`TextToolbarView` 增 `btnRecord` lazy + `wireListeners` 行 + `Listener.onRecordClicked()`。
   - **NoteEditorActivity 接通（T10，合并 T9 三文件 commit `097ca81`）：** 新增 `recordAudioPermissionLauncher`（沿 cameraPermissionLauncher 模式）+ `currentRecordingSheet` 字段；`textToolbar.listener` 加第 5 个 override `onRecordClicked → ensureNoteSavedAndThen { launchRecordAudioWithPermission() }`；新增 audio 三件套 `launchRecordAudioWithPermission / showRecordAudioPermissionDialog / startAudioRecording`（仿 camera 三件套 shape）；`startAudioRecording` 内 UUID 生成 `.m4a` 文件名、构造 BottomSheet，onComplete 内 build `Block.AudioBlock(id="a-<8>", ...)` → `presenter.insertAudioBlockAtFocus()`；`onPause` 改造加 `presenter.stopAllPlayback() + currentRecordingSheet?.forceCancel() + currentRecordingSheet = null` 在 saveNote 之前。

4. **EditorPresenter 收口（T8）：**
   - commit `cc54a67`。`val audioPlayer = AudioPlayer()` 字段（编辑器内多块共用）；新增 `addAudioBlockView(block, insertAt)` 私有方法（注入 callback + noteId + audioPlayer 后 bind）；`bind(note)` 内 `when(b)` 桩分支替换为 `is Block.AudioBlock -> addAudioBlockView(b)`；`onRequestDelete` 增 AudioBlockView 分支调 `purgeAudioOnDisk(block)`（沿 purgeImageOnDisk pattern，noteId<=0 no-op）；新增 `insertAudioBlockAtFocus(block)`（仿 insertImageBlocksAtFocus 但插 1 块 + 补尾 TextBlock 抓焦点）+ `stopAllPlayback()` 给 Activity onPause 调。

**涉及文件清单：**
- **新增（共 10 个）：** `model/audio/AudioRecorder.kt`、`model/audio/AudioPlayer.kt`、`view/editor/AudioRecordingBottomSheet.kt`、`view/block/AudioBlockView.kt`、`res/layout/dialog_audio_recording.xml`、`res/layout/block_audio.xml`、`res/drawable/ic_mic.xml`、`res/drawable/ic_play.xml`、`res/drawable/ic_pause.xml`、`test/.../NoteJsonAudioTest.kt`
- **修改（共 8 个）：** `AndroidManifest.xml`、`model/entity/Block.kt`、`model/entity/NoteContent.kt`、`model/json/NoteJson.kt`、`model/storage/NoteFileStorage.kt`、`controller/editor/EditorPresenter.kt`、`controller/editor/NoteEditorActivity.kt`、`view/toolbar/TextToolbarView.kt`、`res/layout/toolbar_text.xml`、`res/values/strings.xml`（实际改动 ~10 个，含同一 commit 跨文件合并）

**M10 commit 列表（git log `d062ed9..HEAD`，共 9 个 commit）：**
- `a56da90` feat(m10): 增 Block.AudioBlock 实体与 NoteJson audio 分支
- `d9bcec3` feat(m10): NoteFileStorage 增 audio 目录与文件解析
- `4d91021` feat(m10): 新增 AudioRecorder（AAC/m4a 封装）
- `400e065` feat(m10): 新增 AudioPlayer（编辑器内共享单例）
- `45a1a9a` feat(m10): 声明 RECORD_AUDIO + 增录音相关 strings 与图标
- `639eb26` feat(m10): 新增 AudioRecordingBottomSheet（计时 + 停止/取消）
- `2b22b37` feat(m10): 新增 AudioBlockView（播放/暂停/长按删除）
- `cc54a67` feat(m10): EditorPresenter 接通 AudioBlock（bind/插入/删除/播放收口）
- `097ca81` feat(m10): 工具栏扩 5 键并接通录音按钮入口
- 收尾 commit：docs(m10): 标记 M10 语音录入完成

**测试统计：** `:app:clean :app:assembleDebug :app:test` 全绿，**85 项 PASSED**（M9 基线 83 → M10 +2 NoteJsonAudioTest），0 failures / 0 errors / 0 skipped。增量明细：
- `NoteJsonAudioTest`（新文件，2）：AudioBlock round-trip / 缺 durationMs 字段降级 0

**验收（用户 2026-06-04 真机走查 13 条全过）：**
1. ✅ 编辑器底部工具栏可见 5 个按钮（清单 / 样式 / 图片 / 手写 / 录音），无遮挡，间距均匀
2. ✅ 点录音（首次）→ 弹系统 RECORD_AUDIO 权限弹窗
3. ✅ 拒绝权限 → 弹自定义说明 dialog 引导跳系统设置；再次点录音不崩
4. ✅ 允许后 → 弹底部录音 Sheet，标题"正在录音"，时间从 00:00 走表
5. ✅ 录 ≥3 秒 → 点"停止" → Sheet 关闭，当前位置插录音块「▶ 录音 · 00:03」+ 下方空 TextBlock 拿焦点
6. ✅ 录音中按系统返回键不关 Sheet（cancelable=false）；点"取消" → Sheet 关闭无块插入，本地 m4a 已删
7. ✅ 录音中按 Home/切后台 → Sheet 自动 forceCancel；前台后无残留
8. ✅ 点录音块播放按钮 → 切 ic_pause 听到回放；播完自动回 ic_play
9. ✅ 播放中再点 → 立即停止，图标复位
10. ✅ 同笔记两条录音，点第二条播放时第一条自动停（共享 AudioPlayer）
11. ✅ 退出再进编辑器，录音块仍在仍可播
12. ✅ 长按录音块 → 弹"删除录音"BottomSheet 二确认；删 → 块消失 + 本地 m4a 已删；再进仍无
13. ✅ 在图片块/清单块/文本块/手写笔画都存在的笔记追加录音块 → 保存 → 再进所有块类型正确显示（NoteJson 多类型兼容）

**执行模式：** Subagent-Driven Development，严格串行 13 任务（T1→T13）。每任务的 implementer DONE → spec reviewer → code quality reviewer → fix（如有）→ re-review → 标完成；T1 因 sealed `when` exhaustive 触发 NEEDS_CONTEXT，决策为本任务加桩 + T8 替换；T11 静态核查（`deletePermanently` 已走 `deleteNoteDir.deleteRecursively()`）无代码改动跳 commit；T12 全量 gate `:app:clean :app:assembleDebug :app:test --no-daemon` 全绿；T13 真机走查 13 条由用户完成后再写本 STATUS 收尾 commit。

## M11 完成详情（2026-06-04）

**起因：** PRD §13 三阶段执行计划最后一步。M10 语音录入完成后，按规划进入撤销/重做。用户 2026-06-04 二次确认三项设计决策：①撤销粒度对齐"用户感知 1 步 = 1 次 ↶"——文字编辑 800ms 静默期内合并 1 条 `ReplaceTextCommand`；插图/录音/分类等"用户 1 操作触发多 mutator"用 `CompositeCommand` 包成 1 步；②工具栏入口走顶部 AppBar 的 MenuItem（`menu_editor.xml`）而非底栏，沿 Android 标准导航习惯；③栈策略为内存双 ArrayDeque 上限 50 FIFO，跨保存清栈（`onSaveSuccess` 唯一入口）、process death 即丢，不持久化到 DB / JSON。本里程碑首次引入"Inverse Op Command 模式 + silent mutator 边界"架构，model 层新增 `model/history/` 包独立装载 7 个 Command 子类 + Manager + 3 个 Mutator interface；UI 层只接通 1 个 menu xml + 2 个 Activity hook + TextBlockView 防抖；数据层只新增 1 个 `cleanOrphanFiles` 异步方法收口"删块不动文件、保存后扫差集"的遗孤模式。按"骨架 → Command 三组 → Presenter 收口 → View 防抖 → Menu 接通 → 清孤"五维度切 11 任务串行 subagent 执行，外加 T7-fix CompositeCommand 重构补丁、T8-fix bind 屏蔽幻影 push、T9-fix Menu import、T10-fix onSaveSuccess gate + 表达式 when 共 5 个修补 commit。

**5 改动维度：**

1. **`model/history/` 包骨架（T1）：**
   - **`Command` interface（commit `bf4bcf7`）：** 3 成员 `apply() / revert() / label`，Inverse Op 风格——每个 Command 自带正反操作，调用方不感知子类。`label` 仅用于调试日志，UI 不展示。
   - **`EditHistoryManager`（commit `bf4bcf7`）：** 双 `ArrayDeque<Command>` (undoStack / redoStack)，cap=50 FIFO 淘汰最老；`push(cmd)` 清空 redoStack（新操作发生 → 旧 redo 路径作废）；`undo()/redo()` 失败（`revert/apply` 抛异常）则丢弃该 cmd 不入对面栈，保持双栈一致；`clear()` 给 onSaveSuccess 调；`listener: ((Boolean, Boolean) -> Unit)?` 任一栈大小变化即回调（含 push / undo / redo / clear），Activity 借此触发 `invalidateOptionsMenu()`。
   - **`CompositeCommand`（commit `13c745a` + `280779f`）：** T7-fix 引入。包装 `List<Command>` 为"组合单元"——`apply` 顺序执行、`revert` 反序执行，整体作为 1 个 undo step。空 list 构造抛异常防误用；defensive copy via `toList()`、`asReversed()` 习惯写法；KDoc 显式说明"原子性边界"：中途 revert 失败不会自动回滚已 revert 的 sibling，调用方需保证 sibling 间无强耦合。
   - **测试（`EditHistoryManagerTest` 10 + `CompositeCommandTest` 3，commit `bf1932e` 补异常路径 + `13c745a` 新增 3 项）：** 双栈基础语义 / cap 淘汰 / push 清 redo / undo/redo 异常吞咽 / listener 触发 / CompositeCommand apply 顺序 / revert 反序 / 空 list 抛异常。

2. **三组 Command 子类（T2-T4，共 14 单测）：**
   - **`BlockMutator` interface + 4 Block Command（T2，commit `47e294f` + `475d5cd`）：** `BlockMutator { silentInsert/silentRemove/silentMove/silentReplace }`——Presenter 实现的"不入栈"块操作钩子。Command：`AddBlockCommand(block, index)` / `RemoveBlockCommand(index, preSnapshot)` / `MoveBlockCommand(from, to)` / `ReplaceBlockCommand(index, newBlock, preOldBlock)`。T2-fix 关键修复：`RemoveBlock.apply` 必须深拷贝 `block.copy()` 存为 snapshot 否则 `revert` 拿到的是后续被 mutate 的引用；`AddBlock.revert` 还原焦点到删除前 block 头。
   - **`StyleMutator` interface + 2 Style Command（T3，commit `790807c` + `2ed6248`）：** `StyleMutator { silentApplySpan(blockIdx, range, span) / silentSetHeading(blockIdx, heading) }`。Command：`ApplySpanCommand(blockIdx, range, newSpan, oldSpansInRange)`（revert 恢复 oldSpans）/ `ApplyHeadingCommand(blockIdx, newHeading, oldHeading)`（仅 TextBlock 有效）。T3-fix 补 4 项焦点/光标位置断言：apply/revert 后焦点必须回到改动块、光标位于 range.last。
   - **`TextMutator` interface + 1 Text Command（T4，commit `0ef9c64`）：** `TextMutator { silentReplaceText(blockIdx, newText, newSpans) }`。`ReplaceTextCommand(blockIdx, before, after, beforeSpans, afterSpans)`——配合 TextBlockView 800ms 防抖（T8）实现"暂停期合并 1 条"语义。4 单测覆盖 apply/revert/empty before/empty after。

3. **EditorPresenter 收口三 Mutator + history 字段（T5-T7）：**
   - **三 Mutator 实现 + history 字段 + undo/redo/flush 入口（T5，commit `bc311df`）：** Presenter 实现 `BlockMutator/StyleMutator/TextMutator` 全部 silent* 方法（不调 history.push 避免无限递归）；新增 `val history = EditHistoryManager()` 字段；公开 `undo() / redo() / flushPendingTextEdits()` 给 Activity；公开 `pendingInlineSet/pendingSize/pendingColor` 给 StylePickerBottomSheet 同步高亮。
   - **样式入口接 push（T6，commit `e012534`）：** `toggleInline / toggleSize / pickColor / toggleHeading` 内部走 "silentApplySpan/silentSetHeading + history.push(ApplySpanCommand/ApplyHeadingCommand)"。
   - **块入口接 push + 删 purge*（T7，commit `72a4b24`）：** `insertImageBlocksAtFocus / insertChecklistBlockAtFocus / insertAudioBlockAtFocus / onRequestDelete / onRequestSplitAfter / convertChecklistItemToText / onImageLoadFailed` 全部走 "silent* + history.push"；删除 `purgeImageOnDisk / purgeAudioOnDisk` 方法（文件清理改由 `cleanOrphanFiles` 异步收口，保证 undo 期间文件仍在磁盘可复活）。T7-fix（commit `13c745a` + `280779f`）：4 个多 push 站点（插图 / 录音 / 图片加载失败 idx=0 分支 / 清单转文本非空分支）改为 `history.push(CompositeCommand(listOf(cmd1, cmd2, ...)))`，保证用户 1 操作 = 1 次 ↶ 的体感。

4. **TextBlockView 800ms 防抖 + flush + suppressDebounceWhile（T8）：**
   - commit `1e1eeae` + `534390f`。新增字段 `textDebounceCallback: ((before, after) -> Unit)?` / `debounceHandler = Handler(Looper.getMainLooper())` / `debounceMs = 800L` / `debounceArmed` / `pendingBeforeText` / `pendingBeforeSpans` / `debounceRunnable` / `suppressDebounce: Boolean`。
   - `TextWatcher.beforeTextChanged` 在首次变化时抓 before-snapshot；`afterTextChanged` 走 `postDelayed(debounceRunnable, 800)`；`flushPendingTextEdit()` 取消 post 后立即 fire callback；`setOnFocusChangeListener` 焦点丢失也触发 flush；`suppressDebounceWhile { block }` try/finally 守卫——silentReplaceText 调 `view.edit.setText(sp)` 时必须包，否则会触发 TextWatcher 入栈无限循环。
   - T8-fix：`bind()` 内 `edit.setText(spannable)` 加 `suppressDebounceWhile { ... }`——否则笔记加载 800ms 后会冒一条 `ReplaceText(before="", after=loadedText)` 幻影 push，违反 spec §流 E "空栈、按钮初始 disabled"。同 commit 顺手删 4 处 `TODO(T8): wrap applyInlineToRange in suppressDebounceWhile` stale 注释（setSpan/removeSpan/setTextSize 不触发 TextWatcher.afterTextChanged）。

5. **Activity menu + 异步清孤 + onSaveSuccess 清栈（T9-T10）：**
   - **menu_editor.xml + presenter.history.listener + onPause flush（T9，commit `86a6fd2` + `9beae52`）：** 新增 `res/menu/menu_editor.xml` 含 action_undo / action_redo 两 MenuItem（icon + title + `showAsAction="always"`）；`NoteEditorActivity.onCreateOptionsMenu / onPrepareOptionsMenu / onOptionsItemSelected / applyMenuIconAlpha` 4 方法接通；`onPrepareOptionsMenu` 根据 `presenter.history.canUndo/canRedo` 控制 setEnabled + `icon?.mutate()?.alpha = if (enabled) 255 else 102`；`presenter.history.listener = { _, _ -> invalidateOptionsMenu() }` 在 loadNote 前注册；`onPause` 在 saveNote 前调 `presenter.flushPendingTextEdits()` 保证未落栈的暂停期编辑能持久化；T9-fix 把 `android.view.Menu`/`MenuItem` FQN 改 import，对齐 M8 commit `0096a0b` 收口约定。strings 增 `action_undo_cd` / `action_redo_cd`。
   - **`NoteRepository.cleanOrphanFiles` + saveNote 后清栈（T10，commit `d6b67c2` + `837aa7b` + `2348fa0`）：** Repository 新增 `suspend fun cleanOrphanFiles(noteId, note)`——扫 `imageDir(noteId) + audioDir(noteId)` 与 `note.content.blocks` 中的 `ImageBlock.fileName/AudioBlock.fileName` 差集，多余文件 `f.delete()`；外包 `runCatching {}.onFailure { Log.w }` 容错 IO 异常；`noteId <= 0L` 直接 `return@withContext` 防 insert 失败误清。`NoteEditorActivity.saveNote` 走 `lifecycleScope.launch(Dispatchers.IO)` 内 `NoteRepository.save(toSave)` → 仅 `newId > 0L` 才 cleanOrphanFiles + `presenter.history.clear()`——spec §4.5 "onSaveSuccess 是唯一清栈入口"。T10-fix 把 `else -> Unit` 改成 `is Block.TextBlock, is Block.ChecklistBlock -> Unit` 显式列举，sealed when 在编译期穷尽校验；Note / Block FQN 改 import。

**涉及文件清单：**
- **新增（共 7 个）：** `model/history/Command.kt`、`model/history/EditHistoryManager.kt`、`model/history/CompositeCommand.kt`、`model/history/commands/BlockCommands.kt`、`model/history/commands/StyleCommands.kt`、`model/history/commands/TextCommands.kt`、`res/menu/menu_editor.xml`
- **新增测试（共 5 个）：** `EditHistoryManagerTest`(10) / `CompositeCommandTest`(3) / `BlockCommandsTest`(8) / `StyleCommandsTest`(4) / `TextCommandsTest`(4) = **29 项**
- **修改（共 6 个）：** `controller/editor/EditorPresenter.kt`（三 Mutator 实现 + history + 公私方法接 push）、`controller/editor/NoteEditorActivity.kt`（menu 4 方法 + listener + onPause flush + saveNote 异步分支）、`view/block/TextBlockView.kt`（防抖 + flush + suppressDebounceWhile）、`model/NoteRepository.kt`（cleanOrphanFiles）、`res/values/strings.xml`（+2 cd 字串）

**M11 commit 列表（git log `369ca6a..HEAD`，共 21 个 commit）：**
- `f5f85cb` docs(spec): M11 撤销/重做设计稿落地（PRD §13 收口）
- `058a230` docs(m11): 落地撤销/重做 11 任务实施计划
- `bf4bcf7` feat(m11): 落地 Command 接口与 EditHistoryManager（双栈+50 上限+listener）
- `bf1932e` test(m11): 补 EditHistoryManager 异常路径测试 + 解耦 android.util.Log
- `47e294f` feat(m11): 实现 BlockMutator 接口与四个块级 Command（Add/Remove/Move/Replace）
- `475d5cd` refactor(m11): BlockCommand 修复 snapshot 深拷贝与 AddBlock revert 焦点
- `790807c` feat(m11): 实现 StyleMutator 与 ApplySpan/ApplyHeading Command
- `2ed6248` test(m11): StyleCommand 补充焦点与光标位置断言
- `0ef9c64` feat(m11): 实现 TextMutator 与 ReplaceTextCommand（防抖落栈）
- `bc311df` feat(m11): EditorPresenter 实现 BlockMutator/StyleMutator/TextMutator 与 history 入口
- `e012534` feat(m11): 样式入口（toggleInline/toggleSize/pickColor/toggleHeading）接 history.push
- `72a4b24` feat(m11): 块入口接 history.push，删 purge 改由 cleanOrphanFiles 收口
- `13c745a` feat(m11): 引入 CompositeCommand 统一多步骤为 1 步 undo
- `280779f` refactor(m11): CompositeCommand KDoc 措辞与防御性拷贝优化
- `1e1eeae` feat(m11): TextBlockView 加 800ms 文本防抖与 flush 接口，Presenter 接通
- `534390f` fix(m11): TextBlockView.bind 屏蔽防抖避免幻影 ReplaceText，清理 stale TODO
- `86a6fd2` feat(m11): 顶部 AppBar 加撤销/重做 MenuItem + 接通 Presenter
- `9beae52` chore(m11): NoteEditorActivity Menu/MenuItem 改 import（沿 M8 约定）
- `d6b67c2` feat(m11): NoteRepository.cleanOrphanFiles + saveNote 后异步清孤儿 + 清栈
- `837aa7b` fix(m11): cleanOrphan 与 history.clear 仅 onSaveSuccess 触发（spec §4.5）
- `2348fa0` chore(m11): cleanOrphan when 改表达式 + Block/Note 改 import
- 收尾 commit：docs(m11): 标记 M11 撤销/重做完成 + 归档实施计划（PRD §13 全部交付）

**测试统计：** `:app:clean :app:assembleDebug :app:test` 全绿，**114 项 PASSED**（M10 基线 85 → M11 +29），0 failures / 0 errors / 0 skipped。增量明细：
- `EditHistoryManagerTest`（10）：双栈基础语义 / cap 50 淘汰 / push 清 redo / undo+redo 异常吞咽不污染对面栈 / listener 在四种入口都触发
- `CompositeCommandTest`（3）：apply 顺序 / revert 反序 / 空 list 抛异常
- `BlockCommandsTest`（8）：Add/Remove/Move/Replace 各 apply+revert + Remove snapshot 深拷贝 + AddBlock revert 焦点
- `StyleCommandsTest`（4）：ApplySpan apply/revert + ApplyHeading apply/revert，含焦点与光标位置断言
- `TextCommandsTest`（4）：ReplaceText apply/revert + empty before/after 边界

**验收（用户 2026-06-04 真机走查 13 条 + 1 文件遗孤验证全过）：**
1. ✅ 输入"hello" → ↶ 文字消失为空块；↷ "hello" 回来
2. ✅ 输入一段文字、暂停 1s 后再输入 → 连按 ↶ 两次分两段回退（800ms 防抖切分）
3. ✅ 加粗某段 → ↶ 加粗撤销；↷ 加粗回来
4. ✅ 插入图片 → ↶ 图片消失；↷ 图片回来（CompositeCommand 包 Add+TextSplit 为 1 步）
5. ✅ 录音生成 AudioBlock → ↶ 消失；↷ 回来
6. ✅ 删除图片块 → ↶ 图片块复活，图片显示，本地文件未丢（删块不动文件由 cleanOrphanFiles 异步收口）
7. ✅ 转清单块 ↔ 文本块 → ↶/↷ 切回
8. ✅ 应用 H1 → ↶ H1 退回普通段
9. ✅ 切颜色到红色 → ↶ 颜色恢复
10. ✅ 连续 51 次操作 → 最老条目无法 undo（栈上限 50 FIFO 淘汰）
11. ✅ 保存后返回再进笔记 → ↶ 按钮灰（onSaveSuccess 清栈 + 重进 Manager = 空栈）
12. ✅ 切到别笔记再回来 → 撤销栈是新的（每笔记独立 Activity 实例独立 Manager）
13. ✅ 手写 Overlay 内画 → 退出 → 顶部 ↶ 不影响手写块（M6 Overlay 独立 undo 与 M11 顶栏栈隔离）
14. ✅ 删一张图后保存 → 重新进笔记 → `adb shell ls /data/user/0/com.fan.hwnote.app/files/notes/<id>/images/` 该 fileName 已被遗孤清理

**执行模式：** Subagent-Driven Development，严格串行 11 任务（T1→T11）+ 5 个 fix/refactor 修补 commit。每任务 implementer DONE → spec reviewer → code quality reviewer → fix（如有）→ re-review → 标完成。T7 spec reviewer 发现多 push 站点导致"1 ↶ 拿不掉用户感知 1 操作"，决策为引入 CompositeCommand 重构（T7-fix 13c745a + 280779f）；T8 code reviewer 发现 bind 加载 800ms 后幻影 push，决策为加 suppressDebounceWhile 守卫（T8-fix 534390f）；T9 reviewer 指出 FQN 违反 M8 约定（T9-fix 9beae52）；T10 spec reviewer 指出 saveNote 失败路径误清栈违反 spec §4.5，决策为 newId>0L gate（T10-fix 837aa7b + 2348fa0）。T11 全量 gate `:app:clean :app:assembleDebug :app:test` 全绿（114 单测）+ 用户 13 条真机走查 + 1 条文件遗孤 adb 验证全过。

## 项目完成总览

11 个里程碑（M1-M11）全部完成（2026-05-22 ~ 2026-06-04）；PRD §13 已全部交付：

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
| M9 | 分类 + 软删除 + metadata strip | 17 | 16 | DB v2 迁移（categories 表 + notes 扩字段）+ 4 内置筛选 + CategoryManagerBottomSheet 拖动排序 + DeleteConfirmBottomSheet 通用二确认 + 最近删除 30 天回收站 + 编辑器 metadata strip + CategoryPickerBottomSheet |
| M10 | 语音录入 | 9 | 2 | `Block.AudioBlock` sealed 新成员 + `AudioRecorder`(MPEG_4/AAC/64kbps) + `AudioPlayer`(共享单例) + `AudioRecordingBottomSheet`(走表计时 + 停止/取消 + forceCancel) + `AudioBlockView`(播放/暂停/长按删除) + 工具栏扩 5 键 + RECORD_AUDIO 运行时权限 + onPause 兜底停播/取消 |
| M11 | 撤销 / 重做 | 19 (+2 docs) | 29 | `model/history/` 包（Command interface + EditHistoryManager 双栈 cap50 listener + CompositeCommand 多步打包）+ 7 Command 子类 (Add/Remove/Move/Replace/ApplySpan/ApplyHeading/ReplaceText) + Presenter 三 Mutator(silent) 接口与 push 收口 + TextBlockView 800ms 防抖 + suppressDebounceWhile 守卫 + 顶部 AppBar ↶↷ MenuItem + onPause flush + `NoteRepository.cleanOrphanFiles` 异步清孤 + onSaveSuccess 唯一清栈入口 |
| M12 | 文件夹层级 | 12 (+3 docs) | 23 | DB v3 迁移（folders/notebooks 表 + notes.notebook_id）+ Folder/Notebook 实体 + FolderRepository/NotebookRepository（级联软删/移动/默认保护）+ ListFilter 改造（Folder/Notebook 二级 + 旧 prefs 兼容）+ NewFolder/NewNotebook BottomSheet（创建+编辑双模式 + 8 色选色）+ NotebookFilterPopupWindow（伪项+折叠树 chip 筛选）+ FolderManagerActivity（折叠树 + overflow 菜单 + 三粒度拖动排序）+ NotebookPickerPopupWindow（卡片长按移动笔记本）+ 编辑器 AppBar indicator（色点+名称+点击切换笔记本） |
| M13a | 视觉基础+列表页重设计 | 14 (+2 docs) | 3 | 色彩体系绿→蓝(#007DFF) + 白色状态栏 + 列表页 LinearLayout 大标题重写(去 CoordinatorLayout/AppBarLayout/Toolbar) + FilterPanelAdapter 内嵌筛选面板(伪项4+折叠文件夹树+计数) + 底部导航 2-tab + 卡片扁平化(elevation=0/stroke=0.5dp) + 笔记本颜色淡化(卡片 alpha=20/页面 alpha=25) + Overflow PopupMenu + 排序 Sheet 加取消 + 删除确认 Sheet 简化 + ListFilter.Uncategorized + NoteRepository.count() + 删除旧 NotebookFilterPopupWindow |

**累计：** ~200 个 commit（含 docs/计划 commit），140 项自动化单测全绿，**PRD MVP + §13 全部 100% 覆盖 + M12 文件夹层级 + M13a 视觉重设计完成**。架构守住"Block 块组合 + 手写 Overlay 透明层"原始决策，未引入 Compose / ViewModel / LiveData / Room / Hilt / Navigation；DB 二次迁移（v2→v3）走 SQLiteOpenHelper.onUpgrade 新增 folders/notebooks 表 + notes.notebook_id 列 + 预置默认行，旧装机数据无损；M12 引入二级分类体系（Folder→Notebook→Note），全面替换 M9 的 Category 平级模型，PopupWindow 复用折叠树 UI 模式，FolderManager 三粒度拖动排序（文件夹整体 / 笔记本同夹 / 笔记本跨夹），默认文件夹(id=1)与默认笔记本(id=1)受保护不可删除/移动。

**Pending：** M13b 编辑器重设计 / M13c 样式增强 / M13d 宫格+批量删除 / M13e 分享功能（均依赖 M13a 色彩基础）。

**后续可选方向：** M14 待办子系统 / M15 UI 全面审查 / 置顶 pin / 提醒 / 加锁 / 导出 / 备份 / 深色模式 / 多端同步。

---

## M12 完成详情（2026-06-05）

**目标：** 用二级 Folder → Notebook 层级替代 M9 的 Category 平级分类，对齐华为备忘录"文件夹 > 笔记本 > 笔记"三层组织架构。

**变更概要：**

| 层 | 新增/改动 | 说明 |
|---|---|---|
| 数据层 | DB v3 迁移 | 新增 `folders`(id/name/order_index/is_deleted) + `notebooks`(id/folder_id/name/color/order_index/is_deleted) 表；`notes` 表新增 `notebook_id` 列；预置默认文件夹"我的文件夹"(id=1) + 默认笔记本"默认笔记本"(id=1) |
| 数据层 | Folder / Notebook 实体 | data class，与 DB 列一一对应 |
| 数据层 | FolderRepository | list / get / create / rename / softDelete / reorder / count；默认文件夹(id=1)保护 |
| 数据层 | NotebookRepository | listByFolder / get / create / rename / updateColor / softDelete / reorder / moveToFolder / count；默认笔记本(id=1)保护 |
| 数据层 | NoteRepository.moveNoteToNotebook | 单条笔记移动到指定笔记本 |
| 列表层 | ListFilter 改造 | sealed class 从 All/Favorite/Deleted/Category/Uncategorized 改为 All/Favorite/Deleted/Folder/Notebook；SharedPreferences 兼容旧 "CATEGORY"/"UNCATEGORIZED" → All |
| 列表层 | NotebookFilterPopupWindow | 替代旧 FilterPickerPopupWindow，折叠树 UI（伪项"全部/收藏/已删除" + Folder→Notebook 树 + "管理"入口） |
| 列表层 | 卡片长按"移动到笔记本" | NotebookPickerPopupWindow 弹出折叠树，选中即 moveNoteToNotebook |
| 管理层 | FolderManagerActivity | 独立 Activity，折叠树 RecyclerView + overflow 菜单（改名/删除/移动/新建） |
| 管理层 | FolderManagerDragHelper | 三粒度拖动排序：文件夹整段移动 / 笔记本同夹移动 / 笔记本跨夹移动；默认文件夹与默认笔记本保护 |
| 管理层 | NewFolder/NewNotebook BottomSheet | 创建+编辑双模式，8 色选色（仅 Notebook） |
| 编辑器 | AppBar notebook indicator | 色点+笔记本名+下拉箭头；点击弹 NotebookPickerPopupWindow 切换；saveNote 保留 notebookId |

**测试：**

- M11 结束时：114 项 → M12 结束时：137 项（+23）
- 新增测试覆盖：DbV3MigrationTest(3) / FolderRepositoryTest(7) / NotebookRepositoryTest(7) / NoteRepositoryListFilterTest(3) / ListFilterPersistenceTest(2) / moveNoteToNotebook(1)
- 全部 137 项 `testDebugUnitTest` PASSED

**Commit 清单（12 个功能 commit + 3 个 docs commit）：**

```
9313e5a feat(m12): DB v3 迁移 — 新增 folders/notebooks 表与 notes.notebook_id 列，预置默认行
1741816 feat(m12): 加 Folder/Notebook 实体与 Note.notebookId 持久化
4219105 feat(m12): 加 FolderRepository 与 NotebookRepository（含级联软删/移动/默认保护）
a7f5f0b refactor(m12): ListFilter 由 Category/Uncategorized 切到 Folder/Notebook + 兼容旧 prefs
db12598 chore(m12): 加文件夹/笔记本资源（strings/colors/dimens/drawables/menus）
4898abb feat(m12): 加 NewFolder/NewNotebook BottomSheet（含改名/改色编辑模式）
44ed8d5 refactor(m12): NewFolder/NewNotebook 改用 lifecycleScope 并去重 IO 调度
5c161fb feat(m12): chip 切到 NotebookFilterPopupWindow + 删旧 FilterPicker/CategoryManager
d503023 feat(m12): 加 FolderManagerActivity 骨架（折叠树 + overflow 菜单 + 新建/改名/删除/移动）
1e6848e feat(m12): FolderManager 三粒度拖动（文件夹整体/笔记本同夹/笔记本跨夹）
20aacfd feat(m12): 卡片长按移动到笔记本（NotebookPickerPopupWindow + moveNoteToNotebook）
e5f9f68 feat(m12): 编辑器 AppBar indicator 接 NotebookPickerPopupWindow + saveNote 保留 notebookId
```

## M13a 完成详情（2026-06-05）

**目标：** 将 HwNote 视觉体系从"绿色 Material Toolbar"全面切换到华为备忘录"白底大标题 + 蓝色强调"风格。聚焦色彩体系和列表页，为 M13b-M13e 奠定视觉基础。

**10 改动维度：**

1. **色彩体系绿→蓝：** primary #00897B → #007DFF，primary_dark → #0056B3，primary_light → #E3F2FD；状态栏白色背景+深色系统图标（`windowLightStatusBar=true`）；`colorControlActivated` 跟随主色（RadioButton 等控件自动变蓝）
2. **列表页布局重写：** CoordinatorLayout+AppBarLayout+Toolbar → LinearLayout+header+FrameLayout 结构；26sp bold 大标题 + 副标题条数 + ▼▲ 箭头 + ⋮ overflow
3. **筛选面板：** PopupWindow → 同页内嵌 FilterPanelAdapter（5 种 ViewType：Pseudo 伪项 4 个 + Divider + SectionHeader + FolderHead 折叠 + NotebookRow），每项右侧显示笔记计数，选中态蓝色竖条+淡蓝背景
4. **底部导航：** LinearLayout 2-tab（笔记蓝色固定选中 + 待办灰色占位 Toast）
5. **笔记卡片扁平化：** elevation=0dp，strokeWidth=0.5dp，strokeColor=#E8E8E8
6. **笔记本颜色淡化：** 归属笔记本的卡片背景 Color.argb(20, r, g, b)；浏览特定笔记本时整页背景 Color.argb(25, r, g, b)
7. **Overflow 菜单：** 旧 Toolbar action menu → header 区 ⋮ ImageView + PopupMenu
8. **排序 Sheet：** 加底部"取消"蓝色文字按钮
9. **删除确认 Sheet：** 去独立 title → 单行居中提示 + 横排取消/删除蓝色按钮
10. **数据层：** ListFilter.Uncategorized 新增 + NoteRepository.count(filter) suspend 方法

**涉及文件：**
- **新增（共 7 个）：** `FilterPanelAdapter.kt`、`item_filter_section_header.xml`、`shape_search_bar_bg.xml`、`ic_note_tab.xml`、`ic_todo_tab.xml`、`ic_uncategorized.xml`、`menu_note_list_overflow.xml`
- **修改（共 12 个）：** `colors.xml`、`themes.xml`、`strings.xml`、`dimens.xml`、`activity_note_list.xml`、`NoteListActivity.kt`、`NoteListAdapter.kt`、`NoteRepository.kt`、`item_note_card.xml`、`dialog_sort_picker.xml`、`dialog_delete_confirm.xml`、`DeleteConfirmBottomSheet.kt`
- **删除（共 3 个）：** `NotebookFilterPopupWindow.kt`、`popup_notebook_filter.xml`、`menu_note_list_toolbar.xml`

**M13a commit 列表（git log `03610d6..HEAD`，共 14 个功能 commit + 2 个 docs commit）：**
```
121a252 feat(m13a): 色彩体系绿→蓝 + 状态栏白底深色图标
f51a998 feat(m13a): 新增列表页重设计所需 strings + dimens
e7cbd44 feat(m13a): 新建底部导航+筛选面板 vector 图标
78bc877 feat(m13a): 添加 ListFilter.Uncategorized + count() 方法 + 单测
3191ee2 feat(m13a): 笔记卡片扁平化 elevation=0 + 淡边框
955f319 feat(m13a): 排序 Sheet 加取消按钮 + RadioButton 蓝色
2f9f0ac feat(m13a): 删除确认 Sheet 简化为单行提示+横排蓝色按钮
71748be feat(m13a): 筛选面板布局改造 — 计数+蓝条+分节头
9fc4562 feat(m13a): 新建 FilterPanelAdapter 筛选面板适配器
9e4385b feat(m13a): 列表页布局重写 — 大标题+搜索栏+底部导航
93fee49 feat(m13a): NoteListActivity 逻辑重写 — 大标题+筛选面板+底部导航+overflow
0298bb3 fix(m13a): NoteListActivity sealed when 改为表达式形式
8497881 feat(m13a): 笔记卡片根据笔记本颜色淡化背景
29eae88 refactor(m13a): 删除旧 NotebookFilterPopupWindow + popup 布局 + toolbar 菜单
```

**测试统计：** `:app:clean :app:assembleDebug :app:test` 全绿，**140 项 PASSED**（M12 基线 137 → M13a +3 NoteRepository count/Uncategorized），0 failures / 0 errors / 0 skipped。

**验证（待真机走查）：**
- 列表页：白底大标题 + 副标题条数 + ⋮ 菜单，无绿色 AppBar
- 状态栏：白色背景 + 深色系统图标
- 筛选面板：点标题 ▼ 展开全屏面板，选中项蓝色高亮 + 计数，选中后收起
- 笔记卡片：扁平无阴影，淡边框
- 笔记本颜色：选中笔记本 → 整页背景淡化色 + 卡片淡化色
- 底部导航：笔记蓝色选中 + 待办灰色，待办点击 Toast
- FAB：蓝色
- 排序 Sheet：有"取消"按钮，RadioButton 蓝色
- 删除确认：单行提示 + 横排蓝色按钮
- 编辑器/手写/管理页：仅色彩跟随变蓝，布局不变

**执行模式：** Subagent-Driven Development，严格串行 14 任务。每任务 implementer DONE → spec reviewer → fix（如需）→ 标完成。T11 spec reviewer 发现 2 处 sealed when 为 statement form，手动修复并单独 commit。T12-T14 因改动明确由控制器直接实现跳过子 agent。
