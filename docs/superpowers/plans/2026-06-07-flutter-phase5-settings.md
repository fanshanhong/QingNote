# Phase 5: 设置页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a settings page with theme switching (light/dark/system), list preferences (sort/view), and about info.

**Architecture:** `themeModeProvider` (StateProvider) manages ThemeMode state, persisted via SharedPreferences. `SettingsPage` presents grouped ListTiles with dialog pickers. `MaterialApp` responds to themeMode changes. Dark theme added to `theme.dart`.

**Tech Stack:** Flutter, Riverpod, SharedPreferences, go_router

---

## File Structure

| File | Responsibility |
|---|---|
| `lib/providers/theme_provider.dart` (create) | ThemeMode state + SharedPreferences persistence |
| `lib/pages/settings_page.dart` (create) | Settings page UI |
| `lib/theme.dart` (modify) | Add `buildDarkTheme()` |
| `lib/main.dart` (modify) | Wire themeMode to MaterialApp |
| `lib/router.dart` (modify) | Register `/settings` route |
| `lib/pages/note_list_page.dart` (modify) | Add "设置" to overflow menu |

---

### Task 1: 主题 Provider + 深色主题

**Files:**
- Create: `lib/providers/theme_provider.dart`
- Modify: `lib/theme.dart`
- Modify: `lib/main.dart`

- [ ] **Step 1: Create ThemeProvider**

```dart
// lib/providers/theme_provider.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _key = 'theme_mode';

final themeModeProvider = StateNotifierProvider<ThemeModeNotifier, ThemeMode>((ref) {
  return ThemeModeNotifier();
});

class ThemeModeNotifier extends StateNotifier<ThemeMode> {
  ThemeModeNotifier() : super(ThemeMode.system);

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    final value = prefs.getString(_key) ?? 'system';
    state = _fromString(value);
  }

  Future<void> setMode(ThemeMode mode) async {
    state = mode;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, _toString(mode));
  }

  static ThemeMode _fromString(String value) => switch (value) {
    'light' => ThemeMode.light,
    'dark' => ThemeMode.dark,
    _ => ThemeMode.system,
  };

  static String _toString(ThemeMode mode) => switch (mode) {
    ThemeMode.light => 'light',
    ThemeMode.dark => 'dark',
    ThemeMode.system => 'system',
  };
}
```

- [ ] **Step 2: Add buildDarkTheme() to theme.dart**

Add after `buildAppTheme()` in `lib/theme.dart`:
```dart
ThemeData buildDarkTheme() {
  return ThemeData.dark().copyWith(
    colorScheme: ColorScheme.dark(
      primary: AppColors.primary,
      surface: const Color(0xFF121212),
    ),
    scaffoldBackgroundColor: const Color(0xFF121212),
    dividerColor: const Color(0xFF2C2C2C),
    cardColor: const Color(0xFF1E1E1E),
    floatingActionButtonTheme: const FloatingActionButtonThemeData(
      backgroundColor: AppColors.primary,
      foregroundColor: Colors.white,
    ),
  );
}
```

- [ ] **Step 3: Wire themeMode to MaterialApp in main.dart**

In `lib/main.dart`:
1. Add import: `import 'providers/theme_provider.dart';`
2. In `main()`, after getting SharedPreferences, initialize theme:
```dart
// After the existing prefs code, before runApp:
// We'll init theme inside the widget since we need ref
```
3. In `_HwNoteAppState.initState()`, add theme init:
```dart
Future.microtask(() => ref.read(themeModeProvider.notifier).init());
```
4. In `build()`, change the MaterialApp.router to add darkTheme and themeMode:
```dart
return MaterialApp.router(
  title: '备忘录',
  theme: buildAppTheme(),
  darkTheme: buildDarkTheme(),
  themeMode: ref.watch(themeModeProvider),
  routerConfig: router,
  debugShowCheckedModeBanner: false,
);
```

- [ ] **Step 4: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/providers/theme_provider.dart lib/theme.dart lib/main.dart`
Expected: No issues found

- [ ] **Step 5: Commit**

```bash
git add lib/providers/theme_provider.dart lib/theme.dart lib/main.dart
git commit -m "feat(p5): 添加主题 Provider + 深色主题 + MaterialApp 集成"
```

---

### Task 2: SettingsPage 设置页面

**Files:**
- Create: `lib/pages/settings_page.dart`

- [ ] **Step 1: Create SettingsPage**

```dart
// lib/pages/settings_page.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/theme_provider.dart';
import '../providers/note_list_provider.dart';
import '../theme.dart';

class SettingsPage extends ConsumerWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeModeProvider);
    final noteListState = ref.watch(noteListProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('设置'),
        centerTitle: false,
      ),
      body: ListView(
        children: [
          _sectionHeader('外观'),
          _settingTile(
            context: context,
            icon: Icons.palette_outlined,
            title: '主题',
            value: _themeModeLabel(themeMode),
            onTap: () => _pickThemeMode(context, ref, themeMode),
          ),
          const Divider(height: 1, indent: 56),
          _sectionHeader('列表偏好'),
          _settingTile(
            context: context,
            icon: Icons.sort,
            title: '默认排序',
            value: noteListState.sortBy == NoteSortBy.updatedDesc ? '按更新时间' : '按创建时间',
            onTap: () => _pickSortBy(context, ref, noteListState.sortBy),
          ),
          const Divider(height: 1, indent: 56),
          _settingTile(
            context: context,
            icon: Icons.view_module_outlined,
            title: '默认视图',
            value: noteListState.isGridView ? '宫格' : '列表',
            onTap: () async {
              await ref.read(noteListProvider.notifier).toggleGridView();
            },
          ),
          const Divider(height: 1, indent: 56),
          _sectionHeader('关于'),
          _settingTile(
            context: context,
            icon: Icons.info_outline,
            title: '版本',
            value: '1.0.0',
            onTap: null,
          ),
        ],
      ),
    );
  }

  Widget _sectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppDimens.spacingL, AppDimens.spacingL, AppDimens.spacingL, AppDimens.spacingS),
      child: Text(title, style: const TextStyle(
        fontSize: AppDimens.textCaption + 1,
        color: AppColors.textSecondary,
        fontWeight: FontWeight.w500,
      )),
    );
  }

  Widget _settingTile({
    required BuildContext context,
    required IconData icon,
    required String title,
    required String value,
    required VoidCallback? onTap,
  }) {
    return ListTile(
      leading: Icon(icon, color: AppColors.textPrimary),
      title: Text(title, style: const TextStyle(fontSize: AppDimens.textBody)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(value, style: const TextStyle(
            fontSize: AppDimens.textBody, color: AppColors.textHint,
          )),
          if (onTap != null)
            const SizedBox(width: 4),
          if (onTap != null)
            const Icon(Icons.chevron_right, size: 20, color: AppColors.textHint),
        ],
      ),
      onTap: onTap,
    );
  }

  String _themeModeLabel(ThemeMode mode) => switch (mode) {
    ThemeMode.light => '浅色',
    ThemeMode.dark => '深色',
    ThemeMode.system => '跟随系统',
  };

  Future<void> _pickThemeMode(BuildContext context, WidgetRef ref, ThemeMode current) async {
    final result = await showDialog<ThemeMode>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('主题'),
        children: [
          _dialogOption(ctx, '跟随系统', ThemeMode.system, current),
          _dialogOption(ctx, '浅色', ThemeMode.light, current),
          _dialogOption(ctx, '深色', ThemeMode.dark, current),
        ],
      ),
    );
    if (result != null) {
      await ref.read(themeModeProvider.notifier).setMode(result);
    }
  }

  Future<void> _pickSortBy(BuildContext context, WidgetRef ref, NoteSortBy current) async {
    final result = await showDialog<NoteSortBy>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('默认排序'),
        children: [
          _dialogOption(ctx, '按更新时间', NoteSortBy.updatedDesc, current),
          _dialogOption(ctx, '按创建时间', NoteSortBy.createdDesc, current),
        ],
      ),
    );
    if (result != null) {
      await ref.read(noteListProvider.notifier).setSort(result);
    }
  }

  Widget _dialogOption<T>(BuildContext context, String label, T value, T current) {
    return SimpleDialogOption(
      onPressed: () => Navigator.pop(context, value),
      child: Row(children: [
        Expanded(child: Text(label)),
        if (value == current)
          const Icon(Icons.check, size: 20, color: AppColors.primary),
      ]),
    );
  }
}
```

- [ ] **Step 2: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/pages/settings_page.dart`
Expected: No issues found

- [ ] **Step 3: Commit**

```bash
git add lib/pages/settings_page.dart
git commit -m "feat(p5): 添加 SettingsPage 设置页面"
```

---

### Task 3: 路由注册 + 溢出菜单入口

**Files:**
- Modify: `lib/router.dart`
- Modify: `lib/pages/note_list_page.dart`

- [ ] **Step 1: Register route in router.dart**

In `lib/router.dart`:
1. Add import: `import 'pages/settings_page.dart';`
2. Add a new `GoRoute` after the existing `/folder-manager` route and before the `StatefulShellRoute`:
```dart
GoRoute(
  path: '/settings',
  builder: (context, state) => const SettingsPage(),
),
```

- [ ] **Step 2: Add "设置" to overflow menu in note_list_page.dart**

In `lib/pages/note_list_page.dart`, find the `_showOverflowMenu` method. In the `items` list, add after the existing items:
```dart
const PopupMenuItem(value: 'settings', child: Text('设置')),
```

And in the switch statement below, add a case:
```dart
case 'settings': context.push('/settings');
```

Also add import if not present: `import 'package:go_router/go_router.dart';` (likely already imported).

- [ ] **Step 3: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/router.dart lib/pages/note_list_page.dart`
Expected: No issues found

- [ ] **Step 4: Run all tests**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add lib/router.dart lib/pages/note_list_page.dart
git commit -m "feat(p5): 注册 /settings 路由 + 笔记列表溢出菜单增加设置入口"
```

---

## Self-Review

**Spec coverage:**
- ✅ Section 1.1 (主题切换) — Task 1 ThemeProvider + Task 2 picker
- ✅ Section 1.2 (列表偏好) — Task 2 sort/view settings
- ✅ Section 1.3 (关于) — Task 2 version tile
- ✅ Section 2 (文件结构) — All 6 files covered
- ✅ Section 3 (页面布局) — Task 2 grouped ListTile layout
- ✅ Section 4.1 (ThemeProvider) — Task 1
- ✅ Section 4.2 (深色主题) — Task 1 buildDarkTheme
- ✅ Section 4.3 (MaterialApp 集成) — Task 1

**Placeholder scan:** No TBD/TODO found.

**Type consistency:** `themeModeProvider`, `ThemeModeNotifier`, `buildDarkTheme()` used consistently across all tasks.
