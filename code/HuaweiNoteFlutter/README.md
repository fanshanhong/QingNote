# 轻记 / QingNote

一款华为风格的跨平台备忘录应用，一套 Dart 代码覆盖 **Android · iOS · macOS · Windows · HarmonyOS NEXT** 五端。

## 平台支持

| 平台 | 框架 | 最低版本 | 构建产物 |
|------|------|----------|----------|
| Android | Flutter 标准 | API 24 (Android 7.0) | APK / AAB |
| iOS | Flutter 标准 | iOS 13+ | .ipa |
| macOS | Flutter Desktop | macOS 11+ (ARM64) | .app |
| Windows | Flutter Desktop | Windows 10+ | MSIX / exe |
| HarmonyOS NEXT | CPF-Flutter | API 12+ (HarmonyOS NEXT 5.0) | .hap |

## 功能特性

- **富文本编辑** — 基于 appflowy_editor 的 Block Editor（树状 Node 文档模型 + Quill Delta 行内富文本），支持标题、段落、有序/无序列表、待办清单、引用、分割线、表格
- **富文本样式** — 粗体、斜体、下划线、删除线、高亮、文字颜色、链接、对齐
- **Markdown 快捷键** — `#` 标题、`-` 列表、`[]` 清单，所见即所得
- **图片插入** — 拍照或从相册选取，嵌入笔记
- **音频录制** — 录音并嵌入笔记，支持播放
- **手写涂鸦** — 画板 + 贝塞尔平滑笔迹
- **文件夹 / 笔记本** — 两级分类管理笔记
- **待办清单** — 独立待办页，支持提醒通知
- **收藏 / 软删除** — 收藏笔记，回收站 30 天自动清理
- **全文检索** — Rust 实现的 jieba 中文分词 + tantivy 倒排索引，支持高亮片段
- **深色模式** — 跟随系统或手动切换
- **响应式布局** — 移动端底栏导航，桌面端侧栏三栏布局

## 技术架构

### 五层分层设计

```
┌─────────────────────────────────────────────────────┐
│  Layer 1  表现层（Flutter / Dart）                     │
│  ├─ appflowy_editor（文档编辑 + 渲染 + Operation）     │
│  ├─ 业务页面（笔记列表/待办/设置/录音/文件夹管理）     │
│  └─ 平台适配（file_picker/share_plus/桌面窗口/布局）   │
├─────────────────────────────────────────────────────┤
│  Layer 2  业务逻辑层（Dart）                           │
│  ├─ NoteRepository / FolderRepository                  │
│  ├─ NotebookRepository / TodoRepository                │
│  └─ FileStorageService / AudioService / ImageService   │
├─────────────────────────────────────────────────────┤
│  Layer 3  本地持久化层（Dart）                         │
│  ├─ SQLite（sqflite）— 结构化数据                      │
│  └─ 文件系统（path_provider + dart:io）— 图片/音频     │
├──────────── flutter_rust_bridge（FFI 桥接）────────────┤
│  Layer 4  Rust 扩展层                                  │
│  ├─ 全文检索（tantivy + jieba-rs 中文分词）            │
│  ├─ 手写笔迹平滑（贝塞尔拟合，远期）                  │
│  └─ CRDT 协同编辑（Yrs，远期）                        │
├─────────────────────────────────────────────────────┤
│  Layer 5  云端同步（远期规划）                         │
│  └─ Rust 后端 + CRDT 增量同步                          │
└─────────────────────────────────────────────────────┘
```

### Dart / Rust 职责边界

| 职责 | 归属 | 理由 |
|------|------|------|
| 文档编辑（Operation / Undo / Redo） | Dart（appflowy_editor） | 编辑器内置，16ms 渲染帧内闭环 |
| 笔记/文件夹/笔记本/待办 CRUD | Dart（sqflite） | 标准 CRUD，Dart 完全胜任 |
| 文件存储、录音、拍照、分享 | Dart（Flutter 插件） | 跨平台插件成熟 |
| 全文搜索索引 | **Rust**（tantivy + jieba） | 中文分词 + 倒排索引，Dart 无成熟方案 |

### 数据流

```
编辑笔记：用户输入 → appflowy_editor Document → Repository.save() → sqflite → FRB → Rust 全文索引
搜索笔记：搜索词 → FRB → Rust tantivy 查询 → 返回 ID + 高亮片段 → Dart sqflite 按 ID 加载
```

## 技术栈

| 用途 | 选型 |
|------|------|
| 跨平台框架 | Flutter + CPF-Flutter（鸿蒙） |
| Block Editor | appflowy_editor |
| 状态管理 | Riverpod |
| 路由 | go_router |
| 数据库 | sqflite |
| Rust FFI | flutter_rust_bridge v2 |
| 全文检索 | tantivy 0.22 + jieba-rs 0.7 |
| 音频 | record + audio_players |
| 图片 | image_picker + cached_network_image |
| 窗口管理 | window_manager（桌面端） |

## 项目结构

```
HuaweiNoteFlutter/
├── lib/
│   ├── main.dart                  # 入口
│   ├── router.dart                # go_router 路由配置
│   ├── theme.dart                 # 主题（亮色/暗色）
│   ├── models/                    # 数据模型
│   ├── db/                        # SQLite 数据库
│   ├── repositories/              # 数据访问层
│   ├── providers/                 # Riverpod 状态管理
│   ├── pages/                     # 页面
│   ├── widgets/                   # 通用组件
│   ├── services/                  # 平台服务（搜索/通知/文件）
│   └── src/rust/                  # FRB 生成的 Dart 绑定
├── rust/                          # Rust 工作区
│   └── src/
│       ├── api/search.rs          # FRB 暴露的搜索 API
│       └── search/                # tantivy + jieba 搜索引擎
│           ├── indexer.rs         # 索引管理
│           ├── searcher.rs        # 搜索 + 高亮
│           └── tokenizer.rs       # jieba 分词器
├── android/                       # Android 平台配置
├── ios/                           # iOS 平台配置
├── macos/                         # macOS 平台配置
├── windows/                       # Windows 平台配置
└── ohos/                          # HarmonyOS 平台配置
```

## 快速开始

### 环境要求

- Flutter SDK 3.27+
- Rust 工具链（`rustup`）
- flutter_rust_bridge_codegen（`cargo install flutter_rust_bridge_codegen`）
- 鸿蒙端需 OHOS Flutter SDK（CPF-Flutter）

### 构建运行

```bash
# macOS
flutter run -d macos

# Android
flutter run -d <device-id>

# Windows
flutter run -d windows

# iOS
flutter run -d <ios-device-id>

# HarmonyOS NEXT
flutter build hap
```

### Rust 搜索引擎测试

```bash
cd rust
cargo test
```

## 许可证

appflowy_editor 使用 MPL-2.0 许可证。
