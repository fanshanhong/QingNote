# HwNote 全平台架构设计文档

> 状态：Draft
> 日期：2026-06-06
> 范围：Android / iOS / Mac / Windows / HarmonyOS NEXT 五端全覆盖

---

## 1. 目标

将现有 HwNote（华为风格备忘录 App，Android 单端，Kotlin MVC + XML View）扩展为五平台应用：

| 平台 | 目标 |
|---|---|
| Android | 保留现有外壳，编辑器升级为 CMP 共享版 |
| iOS | SwiftUI 原生外壳 + CMP 共享编辑器 |
| Mac | SwiftUI 原生外壳 + CMP 共享编辑器（ARM64 only） |
| Windows | CMP 整体应用（JVM Desktop） |
| HarmonyOS NEXT | ArkTS / ArkUI 全原生独立工程 |

核心原则：**Block Editor 写一次，4 端共享；editor-core 从第一天起按开源 SDK 标准设计。**

---

## 2. 平台策略总览

```
┌───────────────────────────────────────────────────┐
│               应用层（各端独立 UI）                  │
│                                                     │
│  Android       iOS        Mac        Windows  鸿蒙  │
│  XML View      SwiftUI    SwiftUI    CMP      ArkUI │
│  (现有外壳)   (原生外壳)  (原生外壳)  (整体)   (独立) │
└───┬────────────┬──────────┬──────────┬──────────────┘
    │            │          │          │
┌───┴────────────┴──────────┴──────────┴──────┐
│        editor-compose（CMP 模块）             │
│        Block Editor 渲染层 — 4 端共享          │
└─────────────────────┬───────────────────────┘
                      │
┌─────────────────────┴───────────────────────┐
│        editor-core（KMP commonMain）          │
│        纯 Kotlin，0 平台依赖                  │
│        SDK 核心 — 4 端 + 鸿蒙可参考           │
└─────────────────────┬───────────────────────┘
                      │
┌─────────────────────┴───────────────────────┐
│        shared-logic（KMP commonMain）         │
│        Repository / Utils                    │
└─────────────────────┬───────────────────────┘
                      │
┌─────────────────────┴───────────────────────┐
│        platform（expect/actual）              │
│        Audio / FileStorage / DB / ImageLoader │
└─────────────────────────────────────────────┘
```

---

## 3. 模块架构

### 3.1 editor-core（SDK 核心）

**定位**：纯 Kotlin 库，0 平台依赖，定义 Block Editor 的文档模型、状态管理、操作系统。未来独立发布到 Maven Central。

**包含**：

| 子包 | 职责 | 关键类型 |
|---|---|---|
| `document` | 文档模型 | `Document`, `BlockNode`, `TextNode`, `Mark` |
| `schema` | 类型注册表 | `Schema`, `NodeSpec`, `MarkSpec`, `Registry` |
| `operation` | 原子操作 | `Operation`（sealed class: InsertNode / RemoveNode / MoveNode / SetAttrs / InsertText / RemoveText / AddMark / RemoveMark / SplitNode / MergeNode） |
| `state` | 不可变状态 | `EditorState`, `Selection`, `Transaction` |
| `history` | 撤销重做 | `HistoryManager`（基于 Operation 序列 + inverse） |
| `normalize` | 自动修正 | `NormalizationRule` 接口 + 默认规则集 |
| `plugin` | 扩展机制 | `Plugin` 接口, `PluginHost` |
| `codec` | 序列化 | `JsonCodec`, `MarkdownCodec`（可选） |

**设计模式**：参考 ProseMirror 架构——

- **扁平 Inline + Mark 标注**：行内内容不嵌套，样式作为 Mark 附着在文本片段上
- **不可变 State + Transaction**：每次编辑产生新 EditorState，不修改旧 state
- **显式 Operation**：所有变更分解为原子 Operation，可序列化、可 inverse、未来可 OT
- **声明式 Schema**：定义合法的 BlockType / MarkType 及其嵌套规则
- **修复型 Normalization**：Operation 执行后自动跑规则修正不合法状态

**API 约束**：

- 只暴露必要的 public class / interface，内部实现全 `internal`
- 不可变优先：EditorState / Document / BlockNode 全部 immutable
- 渲染层可替换：editor-core 不知道 Compose / SwiftUI / ArkUI 的存在

### 3.2 editor-compose（CMP 渲染层）

**定位**：CMP（Compose Multiplatform）实现的 Block Editor UI，依赖 editor-core，运行于 Android / iOS / Mac / Windows。

**包含**：

| 组件 | 职责 |
|---|---|
| `BlockEditorView` | 顶层 Composable，LazyColumn 驱动 Block 列表 |
| `TextBlockRenderer` | 文本块渲染，集成富文本编辑能力 |
| `ImageBlockRenderer` | 图片块渲染（Coil 3 加载） |
| `ChecklistBlockRenderer` | 清单块渲染（Checkbox + TextField） |
| `AudioBlockRenderer` | 音频块渲染（播放条 + 时长） |
| `HandwritingCanvas` | 手写画板（Compose Canvas + pointerInput） |
| `EditorToolbar` | 格式化工具栏（B/I/U/S/H1/H2/颜色/字号） |
| `BlockDragHandle` | 块拖拽排序 |

**文本块内的富文本方案**：

editor-compose 内部使用 compose-rich-editor（1,786 星，Apache 2.0，rc14）处理 TextBlock 内的富文本编辑。但 SDK 对外不暴露 compose-rich-editor 的类型——editor-core 定义 `Mark` / `TextNode` 模型，editor-compose 负责将其与 `RichTextState` 双向映射。未来如果出现更好的文本编辑库，只需替换 editor-compose 内部实现，不影响 SDK API。

### 3.3 shared-logic（共享业务逻辑）

**定位**：KMP commonMain，App 级别的业务逻辑，不属于 SDK。

| 组件 | 职责 |
|---|---|
| `NoteRepository` | 笔记 CRUD（依赖 SQLDelight） |
| `FolderRepository` | 文件夹管理 |
| `NotebookRepository` | 笔记本管理 |
| `CategoryRepository` | 分类管理 |
| `TodoRepository` | 待办管理 |
| `DateUtils` / `TextUtils` | 工具函数（去 Android 依赖） |

### 3.4 platform（平台适配层）

**定位**：KMP expect/actual，每端独立实现。

| 能力 | Android | iOS | Mac | Windows |
|---|---|---|---|---|
| 音频录制 | MediaRecorder | AVAudioRecorder | AVAudioRecorder | javax.sound |
| 音频播放 | MediaPlayer | AVAudioPlayer | AVAudioPlayer | javax.sound |
| 文件存储 | scoped storage | sandbox | sandbox | 任意 FS |
| DB Driver | SQLDelight Android | SQLDelight Native | SQLDelight Native | SQLDelight JVM |
| 图片加载 | Coil 3 | Coil 3 | Coil 3 | Coil 3 |
| 手写笔压力 | MotionEvent | UITouch.force | — | — |

---

## 4. 各端实现细节

### 4.1 Android

- **外壳**：保留现有 XML View 体系（NoteListActivity + RecyclerView、设置页等不动）
- **编辑器**：`NoteEditorActivity` 内嵌 `ComposeView`，承载 `editor-compose` 的 `BlockEditorView`
- **迁移**：现有 TextBlockView / ImageBlockView / ChecklistBlockView / AudioBlockView 废弃，由 CMP 渲染器替代；现有编辑器作为功能参考和测试基准
- **构建**：现有 Gradle 项目加入 KMP 插件 + CMP 插件

### 4.2 iOS

- **外壳**：SwiftUI（NavigationStack + TabView）
- **编辑器**：通过 `ComposeUIViewController` → `UIViewControllerRepresentable` 嵌入
- **KMP 接入**：编译产出 `.xcframework`，通过 SPM 引入；推荐使用 SKIE 增强 Swift-Kotlin 互操作（sealed class → Swift enum，suspend → async/await）
- **最低版本**：iOS 15+（CMP 要求）

### 4.3 Mac

- **外壳**：SwiftUI（NavigationSplitView 三栏布局），与 iOS 共用 ~75% SwiftUI 代码
- **编辑器**：同 iOS 嵌入机制（Mac Catalyst 或 AppKit 桥接）
- **差异处理**：`#if os(macOS)` 处理菜单栏、多窗口、键盘快捷键
- **约束**：仅支持 Apple Silicon（ARM64），Intel Mac 不支持（Kotlin/Native macosX64 已 deprecated）
- **最低版本**：macOS 13+

### 4.4 Windows

- **整体**：CMP Desktop（JVM + Skia），App 外壳和编辑器都是 Compose
- **运行时**：需捆绑 JRE，安装包 ~80-120MB
- **打包**：jpackage 产出 MSI/EXE
- **最低版本**：Windows 10+

### 4.5 HarmonyOS NEXT

- **独立工程**：全部 ArkTS / ArkUI 原生开发，不共享任何 KMP / CMP 代码
- **editor-core 作为参考**：Document 模型、Operation 定义、序列化格式可作为 ArkTS 实现的设计参考，保证数据格式兼容
- **最低版本**：HarmonyOS NEXT 5.0.0（API 12+）

### 4.6 iOS / Mac 项目结构

```
HwNote-Apple/
├── Shared/                        ← iOS + Mac 共用
│   ├── Views/
│   │   ├── NoteListView.swift
│   │   ├── NoteEditorHost.swift   ← 包裹 CMP Block Editor
│   │   ├── SettingsView.swift
│   │   └── FolderSidebarView.swift
│   ├── ViewModels/                ← 薄 VM，桥接 KMP
│   │   ├── NoteListViewModel.swift
│   │   └── EditorViewModel.swift
│   └── Extensions/
│       └── Block+Swift.swift
├── iOS/
│   ├── iOSApp.swift               ← @main, TabView
│   ├── Info.plist
│   └── Platform/
│       └── ShareSheet.swift
├── macOS/
│   ├── MacApp.swift               ← @main, WindowGroup
│   ├── MenuCommands.swift         ← 菜单栏
│   └── Platform/
│       └── KeyboardShortcuts.swift
└── Packages/
    └── HwNoteShared/              ← KMP .xcframework
```

---

## 5. 现有代码迁移映射

### 5.1 直接搬入 editor-core（纯 Kotlin，0 改动或极小改动）

| 现有文件 | 目标位置 | 改动 |
|---|---|---|
| `Block.kt`（sealed class） | `editor-core/document` | 泛化为开放 Registry |
| `TextSpan.kt` + `SpanType` | `editor-core/document` | 重命名为 Mark / MarkType |
| `Stroke.kt` + `StrokePoint` + `BrushType` | `editor-core/document` | 直接搬 |
| `NoteContent.kt` | `editor-core/document` | 重命名为 Document |
| `EditHistoryManager.kt` | `editor-core/history` | 改为基于 Operation 序列 |
| `Command.kt` + `CompositeCommand.kt` | `editor-core/operation` | 拆为细粒度 Operation + inverse |
| `BlockCommands.kt` / `TextCommands.kt` / `StyleCommands.kt` | `editor-core/operation` | 同上 |
| `Note.kt` / `Folder.kt` / `Notebook.kt` / `Category.kt` | `shared-logic/entity` | 直接搬 |

### 5.2 需要迁移适配

| 现有文件 | 目标位置 | 改动 |
|---|---|---|
| `NoteJson.kt` | `editor-core/codec` | `org.json` → `kotlinx-serialization` |
| `NoteRepository.kt` / `FolderRepository.kt` 等 | `shared-logic` | SQLiteOpenHelper → SQLDelight |
| `DateUtils.kt` / `TextUtils.kt` | `shared-logic/util` | 去 Android Context 依赖 |
| `SpanConverter.kt` | `editor-compose` 内部 | Android Spannable ↔ Mark 映射改为 RichTextState ↔ Mark 映射 |

### 5.3 重写为 Compose（写一次，4 端共享）

| 现有文件 | 目标位置 | 说明 |
|---|---|---|
| `EditorPresenter.kt`（676 行） | 拆分：状态管理 → editor-core/state，UI 驱动 → editor-compose | 现有交互逻辑作为参考 |
| `TextBlockView.kt` | `editor-compose/TextBlockRenderer` | EditText+Spannable → compose-rich-editor |
| `ImageBlockView.kt` | `editor-compose/ImageBlockRenderer` | Glide → Coil 3 |
| `ChecklistBlockView.kt` + `ChecklistItemView.kt` | `editor-compose/ChecklistBlockRenderer` | XML → Compose |
| `AudioBlockView.kt` | `editor-compose/AudioBlockRenderer` | XML → Compose |
| `HandwritingOverlayView.kt` + `BrushPainter.kt` + `StrokeEraser.kt` | `editor-compose/HandwritingCanvas` | Android Canvas → Compose Canvas；贝塞尔/擦除数学逻辑可提入 editor-core |
| `TextToolbarView.kt` / `HandwritingToolbarView.kt` | `editor-compose/EditorToolbar` | XML → Compose |
| 各 BottomSheet（StylePicker / HandwritingStylePicker 等） | `editor-compose` | XML → Compose BottomSheet |

### 5.4 保持不动（Android 端独有）

| 现有文件 | 说明 |
|---|---|
| `NoteListActivity.kt` | Android 外壳，保留 XML View |
| `NoteListAdapter.kt` | RecyclerView Adapter，保留 |
| `NoteEditorActivity.kt` | 改造：加入 ComposeView 嵌入 CMP 编辑器 |
| `NoteDbHelper.kt` | 被 SQLDelight 替代后废弃 |
| 各 XML 布局文件（列表页/设置页） | 保留不动 |

---

## 6. 技术选型

| 用途 | 选型 | 版本/说明 |
|---|---|---|
| 跨平台逻辑共享 | Kotlin Multiplatform (KMP) | Kotlin 2.0.21 |
| 跨平台编辑器 UI | Compose Multiplatform (CMP) | 最新稳定版 |
| 文本块内富文本 | compose-rich-editor | 1.0.0-rc14（Apache 2.0） |
| 数据库 | SQLDelight | commonMain 写 SQL，各端 Driver |
| JSON 序列化 | kotlinx-serialization | 替代 org.json |
| 图片加载 | Coil 3.x（CMP 版） | 替代 Glide |
| iOS/Mac 外壳 | SwiftUI | iOS 15+ / macOS 13+ |
| Swift-Kotlin 互操作 | SKIE (TouchLab) | sealed class → enum, suspend → async |
| Windows 打包 | jpackage | JDK 17+ |
| 鸿蒙 | ArkTS / ArkUI | HarmonyOS NEXT API 12+ |

---

## 7. SDK 设计规范

### 7.1 模块发布路径

```
com.fan.blockeditor:editor-core             ← Maven Central, 纯 Kotlin
com.fan.blockeditor:editor-compose          ← Maven Central, 依赖 CMP
com.fan.blockeditor:editor-compose-richtext ← 可选，集成 compose-rich-editor

未来扩展：
com.fan.blockeditor:editor-swiftui          ← Swift Package
com.fan.blockeditor:editor-arkui            ← 鸿蒙渲染层
```

### 7.2 API 设计原则

| 原则 | 约束 |
|---|---|
| 最小 API 表面 | 只暴露 public class/interface，内部全 internal |
| 不可变优先 | EditorState / Document / BlockNode 全部 immutable |
| 渲染层可替换 | editor-core 不 import 任何 UI 框架 |
| 自定义块类型 | 通过 Registry 注册 NodeSpec + Renderer，不 fork SDK |
| 序列化协议 | Document ↔ JSON 有标准 codec，格式有版本号 |
| 操作可逆 | 每个 Operation 必须实现 inverse()，undo/redo 由 Operation 序列驱动 |

### 7.3 editor-core 关键 API 草案

```kotlin
// --- Document Model ---
data class Document(val children: List<BlockNode>)

interface BlockNode {
    val key: String
    val type: NodeType
    val attrs: Map<String, Any>
}

data class TextBlockNode(
    override val key: String,
    override val type: NodeType = BuiltinTypes.TEXT,
    override val attrs: Map<String, Any> = emptyMap(),
    val text: String,
    val marks: List<Mark>,
) : BlockNode

data class Mark(val type: MarkType, val start: Int, val end: Int, val attrs: Map<String, Any> = emptyMap())

// --- Schema ---
class Schema(val nodeSpecs: Map<NodeType, NodeSpec>, val markSpecs: Map<MarkType, MarkSpec>)

// --- EditorState ---
data class EditorState(val document: Document, val selection: Selection)

// --- Transaction ---
class Transaction(base: EditorState) {
    fun insertNode(index: Int, node: BlockNode): Transaction
    fun removeNode(key: String): Transaction
    fun moveNode(fromIndex: Int, toIndex: Int): Transaction
    fun replaceText(nodeKey: String, range: IntRange, newText: String): Transaction
    fun toggleMark(nodeKey: String, range: IntRange, markType: MarkType): Transaction
    fun apply(): Pair<EditorState, List<Operation>>  // 返回新 state + operation 序列
}

// --- Operation ---
sealed class Operation {
    abstract fun inverse(): Operation
    // InsertNode, RemoveNode, MoveNode, SetAttrs,
    // InsertText, RemoveText, AddMark, RemoveMark,
    // SplitNode, MergeNode
}

// --- History ---
class HistoryManager(capacity: Int = 50) {
    fun push(ops: List<Operation>)
    fun undo(state: EditorState): EditorState?
    fun redo(state: EditorState): EditorState?
}
```

### 7.4 editor-compose 关键 API 草案

```kotlin
// --- 顶层编辑器 ---
@Composable
fun BlockEditor(
    state: EditorState,
    schema: Schema,
    onTransaction: (Transaction) -> Unit,
    rendererRegistry: RendererRegistry = RendererRegistry.default(),
    toolbar: @Composable () -> Unit = { DefaultEditorToolbar() },
)

// --- 自定义块渲染器注册 ---
class RendererRegistry {
    fun register(type: NodeType, renderer: BlockRenderer)
    companion object {
        fun default(): RendererRegistry  // 包含 Text/Image/Checklist/Audio/Handwriting
    }
}

interface BlockRenderer {
    @Composable
    fun Render(node: BlockNode, selection: Selection, onTransaction: (Transaction) -> Unit)
}
```

---

## 8. 风险与缓解

| 风险 | 严重度 | 缓解措施 |
|---|---|---|
| compose-rich-editor 仍是 rc 版，API 可能变 | 中 | editor-compose 内部封装，不对外暴露其类型；关注上游 release |
| CMP iOS 嵌入 SwiftUI 的交互问题（键盘、手势冲突） | 中 | 早期做 PoC 验证嵌入效果；CMP 官方持续改进中 |
| CMP macOS 仅 ARM64 | 低 | Intel Mac 用户群快速萎缩，已知约束 |
| CMP Windows JVM 启动慢 / 包大 | 低 | 备忘录 App 用户可接受；未来 GraalVM native-image 可优化 |
| 鸿蒙端零共享，功能同步双倍工作 | 中 | 数据格式（JSON codec）保持一致；优先级可降低 |
| SDK API 设计前期耗时多 | 中 | 分阶段：先做最小可用 API，迭代完善 |
| Swift/SwiftUI 学习曲线 | 中 | AI 辅助开发 + SwiftUI 与 Compose 思路相同（声明式 UI） |
| Kotlin/Native 编译慢（iOS/Mac） | 低 | 开发期 Android 为主，iOS 定期验证 |

---

## 9. 开发阶段规划

### Phase 1：基础设施 + editor-core

- 搭建 KMP 多模块项目结构
- 实现 editor-core：Document 模型、Schema、Operation、EditorState、HistoryManager
- 迁移现有 entity（Block → BlockNode、TextSpan → Mark）
- 迁移 NoteJson → kotlinx-serialization JsonCodec
- 单元测试覆盖 editor-core

### Phase 2：editor-compose + Android 集成

- 实现 editor-compose：BlockEditorView、各 BlockRenderer、EditorToolbar
- 集成 compose-rich-editor 处理 TextBlock 富文本
- Android 端 NoteEditorActivity 嵌入 ComposeView
- 迁移 HandwritingOverlayView → Compose Canvas
- Android 端功能走查，与现有实现对齐

### Phase 3：shared-logic 迁移 + iOS 端

- SQLDelight 替代 NoteDbHelper
- Repository 层迁移到 commonMain
- iOS SwiftUI 外壳开发（列表页/设置页/导航）
- CMP Block Editor 嵌入 iOS（ComposeUIViewController）
- iOS 端功能走查

### Phase 4：Mac + Windows

- Mac SwiftUI 外壳（复用 iOS 75%+ 代码）
- Mac 端差异处理（菜单栏/多窗口/快捷键）
- Windows CMP 整体应用
- 两端功能走查

### Phase 5：鸿蒙 + 收尾

- 鸿蒙独立工程，ArkTS 全原生开发
- 数据格式与其他端对齐（JSON codec 兼容）
- 五端联合测试
- SDK 文档编写 + 开源准备

---

## 10. 工作量评估

| 工作项 | 占比 | 说明 |
|---|---|---|
| editor-core（SDK 核心设计 + 实现） | 20% | 含 API 设计、Operation 体系、测试 |
| editor-compose（编辑器 UI） | 25% | 核心工作量，写一次 4 端共享 |
| Android 适配 | 10% | 外壳不动，嵌入 ComposeView + 验证 |
| iOS（SwiftUI 外壳 + 集成） | 15% | 列表/设置/导航 + CMP 嵌入 |
| Mac（SwiftUI 复用 + 差异） | 5% | 复用 iOS 代码 + Mac 特有 |
| Windows（CMP 外壳） | 5% | 编辑器已共享，外壳简单 |
| shared-logic 迁移 | 5% | DB/Repository/Utils 迁移 |
| 鸿蒙端 | 15% | 独立工程，UI + 逻辑全部 ArkTS |
