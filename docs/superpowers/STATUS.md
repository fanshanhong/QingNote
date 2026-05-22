# HwNote · 项目进度

最后更新：2026-05-22

## 阶段地图

| 阶段 | 状态 | 产出 |
|------|------|------|
| 1. 头脑风暴 / 需求澄清 | ✅ 完成 | 8 个核心需求决策（功能范围、Jetpack 边界、minSdk、富文本范围、手写笔种、图片来源、列表布局、主色） |
| 2. 架构方案 | ✅ 完成 | 选定 **Block 块组合 + 手写 Overlay 透明层** |
| 3. PRD / 技术设计 | ✅ 完成 | `docs/superpowers/specs/2026-05-22-hwnote-design.md`（12 节，~450 行） |
| 4. 规格自审 | ✅ 完成 | 修复 7 处一致性问题（heading 块属性归位、Span 6 种、笔效/橡皮算法详写、ACTION_GET_CONTENT 替换、Glide compiler 去除、plain_text 规则补、行内/块级样式分离） |
| 5. 用户审阅 PRD | ✅ 完成 | 用户确认无修改，进入下一步 |
| 6. **实施计划编写** | ✅ 完成 | `docs/superpowers/plans/2026-05-22-hwnote-implementation.md`（高层版，7 个里程碑各一节，685 行） |
| 7. 代码实施 | 🔵 **进行中（M3 完成）** | M1 + M2 + M3 详细计划 + 代码；M4-M7 未开始 |
| 8. 手测验收 | 🔵 进行中（M3 列表页通过） | M3 五条高层验收逐条手测通过 |

## 里程碑进度

| 里程碑 | 状态 | 备注 |
|---|---|---|
| M1 基础工程 | ✅ 完成（2026-05-22） | 工程构建 + 真机启动 + git init |
| M2 数据层 | ✅ 完成（2026-05-22） | 数据层 + 42 单测 |
| M3 列表页 | ✅ 完成（2026-05-22） | RecyclerView + 搜索 + 排序 + 长按菜单 + 删除二次确认 |
| M4 编辑器骨架 | ⏳ 未开始 | — |
| M5 图片块/清单块 | ⏳ 未开始 | — |
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

## 下一步建议

M3 已完成，建议进入 **M4 编辑器骨架**：创建 `NoteEditorActivity` + `EditorPresenter`，标题 EditText，文本块 LinearLayout 容器，富文本工具栏（B/I/U/S + 字号 + 颜色 + H1/H2），保存返回数据完整恢复。**暂不接图片/清单/手写**。预估 6-8 小时。

完成 M4 后还需把 M3 留下的两处 Toast 占位换成真跳转：
- `NoteListAdapter` 单击卡片：`startActivity(NoteEditorActivity.newIntent(this, note.id))`
- FAB 新建：`startActivity(NoteEditorActivity.newIntent(this, noteId = -1L))`

执行节奏沿用 M2/M3 的 "writing-plans → 用户审阅 → Subagent-Driven 并行执行 → 用户手测验收 → STATUS 收尾" 模式。

资源：浏览器 visual companion 仍在运行：http://localhost:61835
