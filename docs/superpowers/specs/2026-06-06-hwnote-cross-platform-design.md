# HwNote 全平台架构设计文档

> 状态：Draft v2
> 日期：2026-06-06
> 范围：Android / iOS / Mac / Windows / HarmonyOS NEXT 五端全覆盖
> 变更记录：v1 → v2：从 KMP+CMP 混合方案转向全 Flutter 方案（见附录 A 决策记录）

---

## 1. 目标

将现有 HwNote（华为风格备忘录 App，Android 单端，Kotlin MVC + XML View）扩展为五平台应用，**一套 Dart 代码覆盖全部 5 端**。

| 平台 | 目标 |
|---|---|
| Android | Flutter 全量重写 |
| iOS | Flutter 全量（与 Android 共享 ~95% 代码） |
| Mac | Flutter Desktop（与移动端共享 ~90% 代码） |
| Windows | Flutter Desktop（同上） |
| HarmonyOS NEXT | Flutter 鸿蒙版（CPF-Flutter，华为主导适配） |

核心原则：**一套 Dart 代码，5 端共享；Block Editor 基于 appflowy_editor 二次开发或自建；架构预留 Rust FFI 通道供未来扩展。**

---

## 2. 方案选型依据

### 2.1 为什么不选 KMP + CMP

| 问题 | 详情 |
|---|---|
| CMP 平台能力缺口大 | 文件选择器、文件系统访问、右键菜单、系统通知、剪贴板等均无统一 API，需 expect/actual 各端独立实现 |
| CMP 不支持鸿蒙 | JetBrains 官方无鸿蒙支持计划，鸿蒙端需 ArkTS 全原生独立工程 |
| 外壳需 3 套技术栈 | Android XML View + SwiftUI (iOS/Mac) + CMP Desktop (Windows)，语言涉及 Kotlin + Swift + ArkTS |
| 实际代码复用率 ~60% | 仅 editor-core + shared-logic 可跨平台，外壳和平台能力各端独立 |

### 2.2 为什么选 Flutter

| 优势 | 详情 |
|---|---|
| 真正的一套代码 | UI + 逻辑 + 平台能力通过插件统一，5 端共享 ~95% 代码 |
| 鸿蒙支持成熟 | 华为主导的 CPF-Flutter，~200 个三方库已适配，标准 `flutter build hap` 构建 |
| 平台能力有插件 | file_picker / path_provider / window_manager / audio_players 等均有跨平台统一 API |
| Block Editor 生态 | appflowy_editor 提供类 ProseMirror 架构，可二次开发 |
| Rust FFI 通道 | flutter_rust_bridge（5.3k stars，Flutter Favorite）可在未来引入 Rust 层做计算密集型逻辑 |
| 单一语言 | 全 Dart（Rust 可选），开发和维护负担最低 |

---

## 3. 平台策略总览

```
┌───────────────────────────────────────────────────┐
│               Flutter App Shell                     │
│         一套 Dart 代码 → 5 端                        │
│  Android    iOS    macOS    Windows    HarmonyOS     │
│  (标准)    (标准)  (Desktop) (Desktop)  (CPF-Flutter)│
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────┴─────────────────────────────┐
│             Block Editor（Dart）                      │
│   appflowy_editor 二次开发 或 自建                    │
│   + AudioBlock / HandwritingBlock / 拖拽排序          │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────┴─────────────────────────────┐
│           Business Logic（Dart）                      │
│   NoteRepository / FolderRepository / TodoRepository  │
│   sqflite / drift                                     │
└───────────────────────┬─────────────────────────────┘
                        │ （可选，未来按需接入）
┌───────────────────────┴─────────────────────────────┐
│             Rust Core（预留通道）                      │
│   flutter_rust_bridge / 鸿蒙端 NAPI                   │
│   手写平滑 / 全文搜索 / 协同编辑                      │
└─────────────────────────────────────────────────────┘
```

---

## 4. 模块架构

### 4.1 Block Editor

**策略选择**：基于 appflowy_editor 二次开发。许可证已确认为 AGPL-3.0 + MPL-2.0 双许可可选，选择 MPL-2.0 即可商业闭源使用。

**appflowy_editor 已提供**：

| 能力 | 支持情况 |
|---|---|
| Document 树状模型（Node + attributes + children） | 内置 |
| Operation（Insert / Delete / Update）+ invert() | 内置 |
| Transaction + Selection | 内置 |
| 撤销重做（UndoManager） | 内置 |
| 段落 / 标题 / 待办清单 / 有序无序列表 / 引用 / 分割线 / 图片 / 表格 | 内置 |
| 富文本（B / I / U / S / 高亮 / 颜色 / 链接 / 对齐） | 内置 |
| Markdown 快捷键（# → 标题，- → 列表，[] → 清单） | 内置 |
| 自定义 Block（BlockComponentBuilder 注册） | 内置 |
| JSON 序列化（Quill Delta 兼容） | 内置 |

**需二次开发**：

| 能力 | 方案 |
|---|---|
| 音频 Block | 自定义 `AudioBlockComponentBuilder`，集成 `audio_players` 插件 |
| 手写/画板 Block | 自定义 `HandwritingBlockComponentBuilder`，使用 Flutter `CustomPaint` + `GestureDetector`，复用现有贝塞尔平滑算法 |
| 字号调节 | 扩展 inline attribute，在工具栏增加字号选择器 |
| 块拖拽排序 | 基于 `LongPressDraggable` + `DragTarget` 自行实现 |
| 缩进层级 | 扩展 block attribute，支持列表嵌套缩进 |

### 4.2 App Shell

**一套代码适配 5 端**，通过 Flutter 的平台判断处理差异：

| 组件 | 移动端（Android / iOS / 鸿蒙） | 桌面端（Mac / Windows） |
|---|---|---|
| 导航 | BottomNavigationBar + push/pop | NavigationRail / Drawer + 三栏布局 |
| 笔记列表 | 单栏 ListView / GridView | 侧栏列表 + 右侧预览 |
| 编辑器 | 全屏 | 右侧面板 |
| 工具栏 | 底部 BottomSheet | 顶部 Toolbar |
| 设置 | 独立页面 | 独立窗口或面板 |

通过 `LayoutBuilder` + 断点判断实现响应式布局，而非 `Platform.isXxx` 硬编码。

### 4.3 Business Logic

| 组件 | 职责 | 技术选型 |
|---|---|---|
| NoteRepository | 笔记 CRUD | sqflite（鸿蒙已适配） |
| FolderRepository | 文件夹管理 | sqflite |
| NotebookRepository | 笔记本管理 | sqflite |
| CategoryRepository | 分类管理 | sqflite |
| TodoRepository | 待办管理 | sqflite |
| FileStorageService | 图片/音频文件存储 | path_provider + dart:io |
| AudioService | 录音/播放 | record + audio_players |
| ImageService | 拍照/选图 | image_picker |
| ShareService | 分享 | share_plus |

### 4.4 Rust Core（预留通道，当前阶段不实现）

**设计原则**：业务逻辑层用纯 Dart 实现，但保持模块接口清晰，未来可将计算密集型模块迁移到 Rust。

**未来候选模块**：

| 模块 | 迁移理由 |
|---|---|
| 手写笔迹平滑（贝塞尔拟合） | 计算密集，Rust 性能优势 10x+ |
| 全文搜索索引 | 大量笔记时性能关键 |
| OT/CRDT 协同编辑 | 算法复杂，Rust 类型系统更安全 |
| 音频波形生成 | 实时计算 |

**接入方式**：flutter_rust_bridge 代码生成（Android / iOS / Mac / Windows），鸿蒙端通过 NAPI 直接调用 Rust .so（Rust 官方 Tier 2 支持 `aarch64-unknown-linux-ohos`）。

---

## 5. 各端实现细节

### 5.1 Android

- 标准 Flutter Android 工程
- 最低版本：Android 7.0（API 24）
- 构建产物：APK / AAB

### 5.2 iOS

- 标准 Flutter iOS 工程
- 最低版本：iOS 13+
- 手写笔支持：Apple Pencil 通过 `PointerEvent.pressure` 获取压力

### 5.3 macOS

- Flutter Desktop（macOS）
- 最低版本：macOS 11+
- 差异处理：菜单栏（`PlatformMenuBar`）、多窗口（`window_manager`）、键盘快捷键（`Shortcuts`）
- 仅 ARM64（Apple Silicon），Intel Mac 可通过 Rosetta 2 运行

### 5.4 Windows

- Flutter Desktop（Windows）
- 最低版本：Windows 10+
- 构建产物：MSIX 或 exe 安装包

### 5.5 HarmonyOS NEXT

- 基于 CPF-Flutter（华为主导维护的 Flutter 鸿蒙分支）
- 推荐稳定线：Flutter 3.27.4-ohos 1.0.6
- 最低版本：HarmonyOS NEXT 5.0.0（API 12+）
- 构建命令：`flutter build hap`
- 渲染引擎：Impeller + Vulkan
- 签名：通过 DevEco Studio 进行鸿蒙签名
- 混合开发：如需调用鸿蒙特有能力（分布式服务等），通过 Method Channel 桥接 ArkTS
- 关键已适配插件确认清单：sqflite / path_provider / audio_players / image_picker / share_plus / permission_handler

---

## 6. 现有代码迁移映射

### 6.1 模型层（Kotlin → Dart，逻辑直译）

| 现有文件 | 目标位置 | 改动 |
|---|---|---|
| `Block.kt`（sealed class） | 迁移为 appflowy_editor 的 Node 体系 | Block 类型映射为 Node type 字符串 |
| `TextSpan.kt` + `SpanType` | appflowy_editor 的 Delta attributes | 样式映射 |
| `Stroke.kt` + `StrokePoint` + `BrushType` | `lib/editor/handwriting/` | Dart data class 直译 |
| `NoteContent.kt` | appflowy_editor 的 Document | JSON 格式适配 |
| `Note.kt` / `Folder.kt` / `Notebook.kt` / `Category.kt` / `Todo.kt` | `lib/models/` | Dart data class 直译 |

### 6.2 Repository 层（SQLiteOpenHelper → sqflite）

| 现有文件 | 目标位置 | 改动 |
|---|---|---|
| `NoteRepository.kt` | `lib/repositories/note_repository.dart` | SQL 语句保留，API 适配 sqflite |
| `FolderRepository.kt` | `lib/repositories/folder_repository.dart` | 同上 |
| `NotebookRepository.kt` | `lib/repositories/notebook_repository.dart` | 同上 |
| `CategoryRepository.kt` | `lib/repositories/category_repository.dart` | 同上 |
| `TodoRepository.kt` | `lib/repositories/todo_repository.dart` | 同上 |
| `NoteDbHelper.kt` | `lib/db/database_helper.dart` | 迁移逻辑保留，API 适配 sqflite |

### 6.3 编辑器层（参考重写）

| 现有文件 | 目标 | 说明 |
|---|---|---|
| `EditorPresenter.kt`（676 行） | 编辑器状态管理 | 参考逻辑，用 appflowy_editor 的 EditorState 替代 |
| `EditHistoryManager.kt` | appflowy_editor 内置 UndoManager | 无需重写 |
| `Command.kt` 系列 | appflowy_editor 内置 Operation 体系 | 无需重写 |
| `NoteJson.kt` | appflowy_editor 内置 JSON codec | 需适配格式，保证数据迁移 |
| `TextBlockView.kt` | appflowy_editor 内置 | 无需重写 |
| `ImageBlockView.kt` | appflowy_editor 内置 | 无需重写 |
| `ChecklistBlockView.kt` | appflowy_editor 内置 TodoList | 无需重写 |
| `AudioBlockView.kt` | `lib/editor/blocks/audio_block.dart` | 自定义 BlockComponentBuilder |
| `HandwritingOverlayView.kt` + `BrushPainter.kt` | `lib/editor/blocks/handwriting_block.dart` | CustomPaint 重写，平滑算法可直译 |
| `TextToolbarView.kt` / `HandwritingToolbarView.kt` | `lib/editor/toolbar/` | Flutter Widget 重写 |

### 6.4 UI 层（XML View → Flutter Widget，全量重写）

| 现有文件 | 目标 | 说明 |
|---|---|---|
| `NoteListActivity.kt` + XML | `lib/pages/note_list_page.dart` | Flutter Widget 重写 |
| `NoteListAdapter.kt` | ListView.builder / GridView.builder | Flutter 原生列表 |
| `NoteEditorActivity.kt` | `lib/pages/note_editor_page.dart` | 嵌入 appflowy_editor |
| 各 BottomSheet / Dialog | `lib/widgets/` | Flutter BottomSheet / AlertDialog |
| 设置页 | `lib/pages/settings_page.dart` | Flutter Widget 重写 |

---

## 7. 技术选型

| 用途 | 选型 | 版本/说明 |
|---|---|---|
| 跨平台框架 | Flutter | 标准版 + CPF-Flutter（鸿蒙） |
| Block Editor | appflowy_editor | 需确认 MPL-2.0 许可证可选 |
| 数据库 | sqflite | 鸿蒙已适配 |
| 文件存储 | path_provider + dart:io | 跨平台统一 |
| 图片加载 | cached_network_image | 跨平台统一 |
| 图片选择 | image_picker | 鸿蒙已适配 |
| 音频录制 | record | 需确认鸿蒙适配 |
| 音频播放 | audio_players / just_audio | 需确认鸿蒙适配 |
| 分享 | share_plus | 鸿蒙已适配 |
| 权限 | permission_handler | 鸿蒙已适配 |
| 窗口管理（桌面） | window_manager | macOS + Windows |
| 状态管理 | Riverpod | Flutter 社区主流方案 |
| 路由 | go_router | 跨平台统一 |
| JSON 序列化 | dart:convert + json_serializable | 标准方案 |
| Rust FFI（预留） | flutter_rust_bridge | 5.3k stars，Flutter Favorite |

---

## 8. 项目结构

```
hwnote_flutter/
├── lib/
│   ├── main.dart                      ← 入口
│   ├── app.dart                       ← MaterialApp / 路由配置
│   ├── models/                        ← 数据模型
│   │   ├── note.dart
│   │   ├── folder.dart
│   │   ├── notebook.dart
│   │   ├── category.dart
│   │   ├── todo.dart
│   │   └── stroke.dart
│   ├── db/                            ← 数据库
│   │   └── database_helper.dart
│   ├── repositories/                  ← 数据访问
│   │   ├── note_repository.dart
│   │   ├── folder_repository.dart
│   │   ├── notebook_repository.dart
│   │   ├── category_repository.dart
│   │   └── todo_repository.dart
│   ├── editor/                        ← Block Editor 扩展
│   │   ├── blocks/
│   │   │   ├── audio_block.dart       ← 自定义音频块
│   │   │   └── handwriting_block.dart ← 自定义手写块
│   │   ├── toolbar/
│   │   │   ├── editor_toolbar.dart
│   │   │   └── handwriting_toolbar.dart
│   │   └── utils/
│   │       ├── stroke_smoother.dart   ← 贝塞尔平滑（从 Kotlin 直译）
│   │       └── stroke_eraser.dart     ← 擦除逻辑
│   ├── pages/                         ← 页面
│   │   ├── note_list_page.dart
│   │   ├── note_editor_page.dart
│   │   ├── todo_list_page.dart
│   │   ├── settings_page.dart
│   │   └── folder_manage_page.dart
│   ├── widgets/                       ← 通用组件
│   │   ├── note_card.dart
│   │   ├── folder_sidebar.dart
│   │   ├── responsive_layout.dart     ← 响应式布局（移动 vs 桌面）
│   │   └── confirm_dialog.dart
│   └── services/                      ← 平台服务
│       ├── file_storage_service.dart
│       ├── audio_service.dart
│       ├── image_service.dart
│       └── share_service.dart
├── test/                              ← 单元测试 + Widget 测试
├── integration_test/                  ← 集成测试
├── android/                           ← Android 平台配置
├── ios/                               ← iOS 平台配置
├── macos/                             ← macOS 平台配置
├── windows/                           ← Windows 平台配置
├── ohos/                              ← HarmonyOS 平台配置（CPF-Flutter 生成）
└── pubspec.yaml
```

---

## 9. 数据迁移策略

Android 现有用户（如有）需要数据迁移：

| 数据 | 迁移方式 |
|---|---|
| SQLite 数据库 | Flutter 版直接读取现有 .db 文件（sqflite 兼容 SQLite 3），表结构不变 |
| 笔记 JSON 内容 | 编写一次性转换器：现有 NoteJson 格式 → appflowy_editor Document JSON |
| 图片/音频文件 | 文件路径不变，直接读取 |

APP 未上线，当前不需要考虑历史数据兼容，但保留迁移设计以备将来需要。

---

## 10. 风险与缓解

| 风险 | 严重度 | 缓解措施 |
|---|---|---|
| ~~appflowy_editor 许可证~~ | ~~高~~ | 已确认 AGPL+MPL 双许可可选，选 MPL-2.0，风险消除 |
| CPF-Flutter 版本滞后于官方 Flutter | 中 | 选择 CPF 首推稳定线（3.27.x），不追最新特性 |
| 鸿蒙端插件缺失（音频录制等） | 中 | 关键插件在开发前逐一确认；缺失的通过 Method Channel 桥接 ArkTS 原生 |
| Flutter 自绘引擎的平台一致性问题 | 低 | Impeller 渲染已成熟，各端视觉一致是 Flutter 的核心优势 |
| 手写笔压力感应在鸿蒙端 | 中 | 需验证 Flutter PointerEvent.pressure 在鸿蒙端是否可用；不可用则降级为无压感 |
| flutter_rust_bridge 不支持鸿蒙 | 低 | 当前阶段不依赖 Rust；未来鸿蒙端可通过 NAPI 直接调用 Rust .so |
| appflowy_editor 维护节奏放缓 | 中 | 活跃度需持续观察；必要时 fork 自行维护或切换到 super_editor |

---

## 11. 开发阶段规划

### Phase 1：项目搭建 + 数据层

- 创建 Flutter 多平台项目（android / ios / macos / windows / ohos）
- 迁移数据模型（Note / Folder / Notebook / Category / Todo / Stroke）
- 迁移数据库（NoteDbHelper → sqflite DatabaseHelper）
- 迁移 Repository 层
- 单元测试覆盖数据层

### Phase 2：Block Editor 集成

- 集成 appflowy_editor（或确认许可证后决定替代方案）
- 开发自定义 AudioBlockComponentBuilder
- 开发自定义 HandwritingBlockComponentBuilder（CustomPaint + 贝塞尔平滑）
- 开发编辑器工具栏（格式化 + 手写工具）
- 开发块拖拽排序
- 实现字号调节扩展
- NoteJson 格式转换器

### Phase 3：App Shell + 页面

- 笔记列表页（ListView + GridView 切换）
- 笔记编辑页（集成 Block Editor）
- 待办列表页
- 文件夹/笔记本管理页
- 设置页
- 响应式布局（移动端 vs 桌面端断点适配）

### Phase 4：平台适配 + 测试

- Android 端功能走查
- iOS 端功能走查
- macOS 桌面端适配（菜单栏 / 多窗口 / 快捷键）
- Windows 桌面端适配
- HarmonyOS 端适配（CPF-Flutter 构建 + 签名 + 插件验证）
- 五端联合测试

### Phase 5：打磨 + 发布准备

- 性能优化（大笔记渲染、长列表滚动）
- 主题（深色/浅色）
- 动画和转场
- 各平台打包和发布配置
- Rust FFI 通道预留（接口设计，不实现）

---

## 12. 工作量评估

| 工作项 | 占比 | 说明 |
|---|---|---|
| 项目搭建 + 数据层迁移 | 10% | 模型/DB/Repository 从 Kotlin 直译到 Dart |
| Block Editor 集成与扩展 | 30% | 核心工作量：集成 + 音频/手写/拖拽/字号自定义 |
| App Shell + 全部页面 | 25% | 列表/编辑/待办/设置/管理页面 |
| 响应式布局（移动 vs 桌面） | 10% | 断点适配、导航差异、工具栏位置 |
| 5 端适配与测试 | 15% | 各端特有配置、插件验证、功能走查 |
| 打磨 + 性能 + 发布准备 | 10% | 主题/动画/打包/Rust 通道预留 |

---

## 附录 A：方案决策记录

### A.1 v1 方案（KMP + CMP，已放弃）

- **架构**：KMP 共享逻辑 + CMP 共享编辑器 + 各端原生外壳
- **语言**：Kotlin + Swift + ArkTS
- **放弃原因**：
  1. CMP 平台能力缺口大（文件选择、右键、通知等无统一 API）
  2. CMP 不支持鸿蒙，JetBrains 无计划
  3. 外壳需 3 套技术栈，实际代码复用率仅 ~60%
  4. KMP + 鸿蒙仅有社区 hack，无官方支持、无生产验证

### A.2 v2 方案（全 Flutter，当前采用）

- **架构**：Flutter 全量覆盖 5 端
- **语言**：Dart（Rust 可选）
- **选择原因**：
  1. 一套代码 ~95% 复用率
  2. 鸿蒙有华为主导的 CPF-Flutter，~200 个插件已适配
  3. 平台能力通过插件统一 API
  4. appflowy_editor 提供 Block Editor 基础
  5. Rust FFI 通道成熟（flutter_rust_bridge，Flutter Favorite）
  6. 单一语言，维护负担最低

### A.3 调研数据源

| 主题 | 来源 |
|---|---|
| CMP 跨平台限制 | JetBrains 官方文档、GitHub CHANGELOG |
| Flutter 鸿蒙适配 | CPF-Flutter/flutter_flutter（gitcode.com）、openharmony-tpc |
| appflowy_editor | GitHub 仓库源码、pub.dev |
| flutter_rust_bridge | GitHub 仓库、crates.io |
| KMP + 鸿蒙可行性 | CSDN 社区文章（4 篇，同一作者） |
