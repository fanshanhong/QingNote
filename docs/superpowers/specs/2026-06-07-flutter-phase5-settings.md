# Phase 5：Flutter 设置页 设计文档

## 概述

新增设置页面，提供主题切换（浅色/深色/跟随系统）、列表偏好（默认排序/默认视图）、关于信息（版本号/开源许可）。

**目标**：用户可以在设置页调整应用外观和列表偏好。

**前提**：
- `NoteListProvider` 已通过 `SharedPreferences` 持久化排序和视图偏好
- `go_router` 路由已配置
- 笔记列表页有溢出菜单（`_showOverflowMenu`）
- `buildAppTheme()` 在 `lib/theme.dart` 中定义

---

## 1. 功能

### 1.1 主题切换

| 选项 | 行为 |
|---|---|
| 浅色 | 固定使用浅色主题 |
| 深色 | 固定使用深色主题 |
| 跟随系统（默认） | 跟随系统设置 |

- 存储键：`theme_mode`，值：`light` / `dark` / `system`
- 通过 Riverpod `StateProvider` 管理，`MaterialApp` 的 `themeMode` 参数响应变化
- 需同时提供 `ThemeData` 和 `ThemeData.dark()` 配置

### 1.2 列表偏好

| 设置项 | 当前状态 | 行为 |
|---|---|---|
| 默认排序 | 已在 `NoteListProvider` 中通过 `SharedPreferences` 管理 | 设置页修改后同步到 `NoteListProvider` |
| 默认视图 | 已在 `NoteListProvider` 中通过 `SharedPreferences` 管理 | 设置页修改后同步到 `NoteListProvider` |

设置页直接调用 `NoteListNotifier` 的 `setSort()` 和 `toggleGridView()` 方法。

### 1.3 关于

- 版本号：通过 `package_info_plus` 获取或硬编码
- 简单的版本信息展示即可，不需要独立页面

---

## 2. 文件结构

| 文件 | 职责 |
|---|---|
| `lib/pages/settings_page.dart` (create) | 设置页面 UI |
| `lib/providers/theme_provider.dart` (create) | 主题状态管理（ThemeMode + SharedPreferences） |
| `lib/theme.dart` (modify) | 增加深色主题配置 `buildDarkTheme()` |
| `lib/main.dart` (modify) | `MaterialApp` 响应 themeMode |
| `lib/router.dart` (modify) | 注册 `/settings` 路由 |
| `lib/pages/note_list_page.dart` (modify) | 溢出菜单增加 "设置" 入口 |

---

## 3. 页面布局

分组列表，每组有标题和设置项：

```
┌─────────────────────────┐
│ ← 设置                  │
├─────────────────────────┤
│ 外观                    │
│ ┌─────────────────────┐ │
│ │ 主题  ───  跟随系统  │ │  ← 点击弹出选择对话框
│ └─────────────────────┘ │
│                         │
│ 列表偏好                 │
│ ┌─────────────────────┐ │
│ │ 默认排序  ── 按更新  │ │  ← 点击弹出选择对话框
│ ├─────────────────────┤ │
│ │ 默认视图  ── 列表    │ │  ← 点击弹出选择对话框
│ └─────────────────────┘ │
│                         │
│ 关于                    │
│ ┌─────────────────────┐ │
│ │ 版本  ───  1.0.0     │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

每个设置项为 `ListTile`：左侧标题，右侧当前值文本。点击弹出 `SimpleDialog` 或 `AlertDialog` 供选择。

---

## 4. 主题管理

### 4.1 ThemeProvider

```dart
// SharedPreferences key: 'theme_mode'
// Values: 'light', 'dark', 'system'
final themeModeProvider = StateProvider<ThemeMode>((ref) => ThemeMode.system);
```

应用启动时从 `SharedPreferences` 读取并设置初始值。

### 4.2 深色主题

在 `theme.dart` 增加 `buildDarkTheme()` 方法，提供深色模式的配色方案：
- `scaffoldBackgroundColor`: 深色背景
- `cardColor`: 深色卡片
- 文字颜色反转
- 保持 `primary` 蓝色不变

### 4.3 MaterialApp 集成

```dart
MaterialApp.router(
  theme: buildAppTheme(),
  darkTheme: buildDarkTheme(),
  themeMode: ref.watch(themeModeProvider),
  ...
)
```
