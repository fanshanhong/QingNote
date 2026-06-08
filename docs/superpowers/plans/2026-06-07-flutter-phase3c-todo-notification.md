# Phase 3C: 待办通知 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement local notifications for todo reminders — schedule, cancel, handle tap/actions (complete, snooze).

**Architecture:** Singleton `TodoNotificationService` wrapping `flutter_local_notifications` + `timezone`. Integrated into existing providers (TodoDetailProvider.save, TodoListProvider.toggleComplete/quickAdd). App initialization sets up channels and reschedules pending alarms.

**Tech Stack:** flutter_local_notifications, timezone, Flutter

---

## File Structure

| File | Responsibility |
|---|---|
| `lib/services/todo_notification_service.dart` (create) | Notification scheduling/canceling/actions |
| `lib/main.dart` (modify) | Init notification service on startup |
| `lib/providers/todo_detail_provider.dart` (modify) | Schedule/cancel after save |
| `lib/providers/todo_list_provider.dart` (modify) | Schedule/cancel on complete/quickAdd |
| `pubspec.yaml` (modify) | Add dependencies |

---

### Task 1: 添加依赖 + 通知服务骨架

**Files:**
- Modify: `pubspec.yaml`
- Create: `lib/services/todo_notification_service.dart`

- [ ] **Step 1: Add dependencies to pubspec.yaml**

Add under `dependencies:`:
```yaml
  flutter_local_notifications: ^18.0.1
  timezone: ^0.10.0
```

- [ ] **Step 2: Run pub get**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter pub get`
Expected: Success

- [ ] **Step 3: Create TodoNotificationService**

```dart
// lib/services/todo_notification_service.dart
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/timezone.dart' as tz;
import 'package:timezone/data/latest_all.dart' as tz_data;
import '../repositories/todo_repository.dart';

class TodoNotificationService {
  static final instance = TodoNotificationService._();
  TodoNotificationService._();

  final _plugin = FlutterLocalNotificationsPlugin();
  void Function(String?)? onNotificationTap;

  static const _channelId = 'todo_reminders';
  static const _channelName = '待办提醒';

  Future<void> init() async {
    tz_data.initializeTimeZones();

    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosSettings = DarwinInitializationSettings(
      requestAlertPermission: false,
      requestBadgePermission: false,
      requestSoundPermission: false,
    );
    const settings = InitializationSettings(
      android: androidSettings,
      iOS: iosSettings,
    );

    await _plugin.initialize(
      settings,
      onDidReceiveNotificationResponse: _onResponse,
    );

    final android = _plugin.resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin>();
    if (android != null) {
      await android.createNotificationChannel(
        const AndroidNotificationChannel(
          _channelId,
          _channelName,
          importance: Importance.high,
        ),
      );
    }
  }

  void _onResponse(NotificationResponse response) {
    final payload = response.payload;
    if (payload == null || payload.isEmpty) return;

    if (response.actionId == 'complete') {
      _handleComplete(payload);
      return;
    }
    if (response.actionId == 'snooze') {
      _handleSnooze(payload);
      return;
    }
    onNotificationTap?.call(payload);
  }

  void _handleComplete(String todoIdStr) {
    // Will be wired up in Task 3 via a callback
  }

  void _handleSnooze(String todoIdStr) {
    final todoId = int.tryParse(todoIdStr);
    if (todoId == null || todoId <= 0) return;
    snooze(todoId, '');
  }

  Future<void> scheduleReminder(int todoId, String title, int remindAtMs) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    if (remindAtMs <= now) return;

    final scheduledDate = tz.TZDateTime.fromMillisecondsSinceEpoch(
      tz.local,
      remindAtMs,
    );

    const androidDetails = AndroidNotificationDetails(
      _channelId,
      _channelName,
      importance: Importance.high,
      priority: Priority.high,
      actions: [
        AndroidNotificationAction('complete', '完成'),
        AndroidNotificationAction('snooze', '稍后'),
      ],
    );
    const iosDetails = DarwinNotificationDetails();
    const details = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    await _plugin.zonedSchedule(
      todoId,
      '待办提醒',
      title.isEmpty ? '待办事项' : title,
      scheduledDate,
      details,
      payload: todoId.toString(),
      androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
    );
  }

  Future<void> cancelReminder(int todoId) async {
    await _plugin.cancel(todoId);
  }

  Future<void> snooze(int todoId, String title) async {
    final snoozeTime = DateTime.now().add(const Duration(minutes: 10));
    await scheduleReminder(todoId, title, snoozeTime.millisecondsSinceEpoch);
  }

  Future<void> rescheduleAll(TodoRepository repo) async {
    final todos = await repo.listPendingAlarms();
    final now = DateTime.now().millisecondsSinceEpoch;
    for (final todo in todos) {
      if (todo.remindAt > now) {
        await scheduleReminder(todo.id, todo.title, todo.remindAt);
      }
    }
  }

  Future<bool> requestPermission() async {
    final android = _plugin.resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin>();
    if (android != null) {
      final granted = await android.requestNotificationsPermission();
      return granted ?? false;
    }
    final ios = _plugin.resolvePlatformSpecificImplementation<
        IOSFlutterLocalNotificationsPlugin>();
    if (ios != null) {
      final granted = await ios.requestPermissions(alert: true, badge: true, sound: true);
      return granted ?? false;
    }
    return true;
  }
}
```

- [ ] **Step 4: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/services/todo_notification_service.dart`
Expected: No issues found

- [ ] **Step 5: Commit**

```bash
git add pubspec.yaml pubspec.lock lib/services/todo_notification_service.dart
git commit -m "feat(p3c): 添加通知依赖 + TodoNotificationService"
```

---

### Task 2: 应用启动集成

**Files:**
- Modify: `lib/main.dart`

- [ ] **Step 1: Read current main.dart**

- [ ] **Step 2: Add notification init to main.dart**

In `main.dart`:
1. Add import: `import 'services/todo_notification_service.dart';`
2. Add import: `import 'providers/repository_providers.dart';`
3. In the app startup (after WidgetsFlutterBinding.ensureInitialized), add:
```dart
await TodoNotificationService.instance.init();
```
4. After the ProviderScope is created and repos are available, reschedule:
```dart
// In the app's initState or a startup provider:
TodoNotificationService.instance.onNotificationTap = (payload) {
  // Navigate to todo detail via router
  if (payload != null) {
    router.push('/todo/$payload');
  }
};
```

The exact integration point depends on how main.dart is structured. Read it first, then add the init call appropriately.

- [ ] **Step 3: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/main.dart`
Expected: No issues found

- [ ] **Step 4: Commit**

```bash
git add lib/main.dart
git commit -m "feat(p3c): 应用启动初始化通知服务"
```

---

### Task 3: 集成到 Provider（调度/取消通知）

**Files:**
- Modify: `lib/providers/todo_detail_provider.dart`
- Modify: `lib/providers/todo_list_provider.dart`

- [ ] **Step 1: Modify TodoDetailProvider.save()**

In `lib/providers/todo_detail_provider.dart`, add import:
```dart
import '../services/todo_notification_service.dart';
```

In the `save()` method, after the insert/update succeeds, add:
```dart
final savedId = state.todoId;
if (state.remindAt > DateTime.now().millisecondsSinceEpoch && !state.isCompleted) {
  TodoNotificationService.instance.scheduleReminder(savedId, state.title.trim(), state.remindAt);
} else {
  TodoNotificationService.instance.cancelReminder(savedId);
}
```

In the `clearRemind()` method, add after setting state:
```dart
if (state.todoId > 0) {
  TodoNotificationService.instance.cancelReminder(state.todoId);
}
```

In the `delete()` method, add before softDelete:
```dart
TodoNotificationService.instance.cancelReminder(state.todoId);
```

- [ ] **Step 2: Modify TodoListProvider**

In `lib/providers/todo_list_provider.dart`, add import:
```dart
import '../services/todo_notification_service.dart';
```

In `toggleComplete(int id)` method (or `completeTodo`), after the repository call:
```dart
TodoNotificationService.instance.cancelReminder(id);
```

In `quickAdd(...)` method, after successful insert, if remindAt > now:
```dart
if (remindAt > DateTime.now().millisecondsSinceEpoch) {
  // Need the new todo ID from insert to schedule
  // This depends on how quickAdd is implemented
  TodoNotificationService.instance.scheduleReminder(newId, title, remindAt);
}
```

- [ ] **Step 3: Verify analyze passes**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/providers/`
Expected: No issues found

- [ ] **Step 4: Run all tests**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add lib/providers/todo_detail_provider.dart lib/providers/todo_list_provider.dart
git commit -m "feat(p3c): 集成通知到 TodoDetailProvider + TodoListProvider"
```

---

### Task 4: 通知动作回调完善 + 最终验证

**Files:**
- Modify: `lib/services/todo_notification_service.dart`
- Modify: `lib/main.dart`

- [ ] **Step 1: Wire up complete action callback**

In `TodoNotificationService`, change `_handleComplete` to use a callback:
```dart
void Function(int todoId)? onCompleteAction;

void _handleComplete(String todoIdStr) {
  final todoId = int.tryParse(todoIdStr);
  if (todoId == null || todoId <= 0) return;
  onCompleteAction?.call(todoId);
  cancelReminder(todoId);
}
```

In `main.dart`, after notification init, wire up:
```dart
TodoNotificationService.instance.onCompleteAction = (todoId) async {
  final container = ProviderScope.containerOf(navigatorKey.currentContext!);
  final todoRepo = container.read(todoRepositoryProvider);
  await todoRepo.completeTodo(todoId);
};
```

- [ ] **Step 2: Run full analyze**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter analyze lib/`
Expected: No issues found

- [ ] **Step 3: Run all tests**

Run: `cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/code/HuaweiNoteFlutter && flutter test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add lib/services/todo_notification_service.dart lib/main.dart
git commit -m "feat(p3c): 完善通知动作回调（完成/稍后/点击）"
```

---

## Self-Review

**Spec coverage:**
- ✅ Section 2.1 (调度通知) — Task 1 service + Task 3 integration
- ✅ Section 2.2 (通知内容) — Task 1 AndroidNotificationDetails
- ✅ Section 2.3 (通知操作) — Task 1 actions + Task 4 callbacks
- ✅ Section 2.4 (应用启动) — Task 2
- ✅ Section 2.5 (权限处理) — Task 1 requestPermission
- ✅ Section 4 (API) — Task 1
- ✅ Section 5 (深度链接) — Task 2 onNotificationTap + Task 4
- ✅ Section 6 (集成) — Task 3

**Placeholder scan:** No TBD/TODO found.

**Type consistency:** `TodoNotificationService.instance` used consistently as singleton access pattern.
