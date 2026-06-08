# Phase 3C：Flutter 待办通知 设计文档

## 概述

实现待办提醒的本地通知功能，对标 Android 原版 `TodoAlarmManager` + `TodoAlarmReceiver`。当待办设置了提醒时间，到时间后弹出系统通知，支持"完成"和"稍后提醒"两个快捷操作。

**目标**：用户设置提醒时间后，即使 APP 不在前台，到时间也能收到通知提醒。

**前提**：
- `TodoDetailPage` 已实现提醒时间设置
- `TodoRepository` 已有 `listPendingAlarms()` 方法
- 路由 `/todo/:todoId` 已注册

---

## 1. 技术方案

使用 `flutter_local_notifications` 插件：
- 支持 Android/iOS 定时通知
- 支持通知操作按钮（actions）
- 支持点击通知跳转

配合 `timezone` 包处理时区调度。

---

## 2. 核心功能

### 2.1 调度通知
- 保存 todo 时，如果 `remindAt > now` 且 `!isCompleted` → 调度通知
- 保存 todo 时，如果 `remindAt <= now` 或 `isCompleted` 或 `remindAt == 0` → 取消已有通知

### 2.2 通知内容
- 标题："待办提醒"
- 内容：todo.title
- 优先级：HIGH
- 通知渠道：`todo_reminders`（名称："待办提醒"）

### 2.3 通知操作
- 点击通知 → 打开 APP 并导航到 `/todo/:todoId`
- "完成" 按钮 → 标记待办完成，取消通知
- "稍后" 按钮 → 10 分钟后再次提醒

### 2.4 应用启动时
- 初始化通知插件
- 创建通知渠道（Android）
- 重新调度所有 pending alarms（从 DB 查询）

### 2.5 权限处理
- Android 13+ 需请求 `POST_NOTIFICATIONS` 权限
- iOS 需请求通知权限
- 在设置提醒时间时触发权限请求

---

## 3. 文件结构

| 文件 | 职责 |
|---|---|
| `lib/services/todo_notification_service.dart` | 通知服务（初始化/调度/取消/处理操作） |
| `lib/main.dart` (modify) | 应用启动时初始化通知服务 |
| `lib/providers/todo_detail_provider.dart` (modify) | save() 后调度/取消通知 |
| `lib/providers/todo_list_provider.dart` (modify) | toggleComplete/quickAdd 后调度/取消通知 |

---

## 4. TodoNotificationService API

```dart
class TodoNotificationService {
  // 单例
  static final instance = TodoNotificationService._();

  // 初始化（应用启动时调用）
  Future<void> init();

  // 调度一个通知
  Future<void> scheduleReminder(int todoId, String title, int remindAtMs);

  // 取消一个通知
  Future<void> cancelReminder(int todoId);

  // 重新调度所有 pending（应用启动/恢复时）
  Future<void> rescheduleAll(TodoRepository repo);

  // 稍后提醒（10分钟后）
  Future<void> snooze(int todoId, String title);

  // 请求通知权限
  Future<bool> requestPermission();
}
```

---

## 5. 深度链接（Notification Tap）

通知点击时需要导航到对应 todo 的详情页。方案：
- 通知 payload 设为 todo ID 字符串
- 在 `onDidReceiveNotificationResponse` 回调中解析 payload
- 使用全局 navigatorKey 进行路由跳转：`router.push('/todo/$todoId')`

---

## 6. 与已有代码的集成

### TodoDetailProvider.save()
保存成功后：
```dart
if (state.remindAt > DateTime.now().millisecondsSinceEpoch && !state.isCompleted) {
  TodoNotificationService.instance.scheduleReminder(state.todoId, state.title, state.remindAt);
} else {
  TodoNotificationService.instance.cancelReminder(state.todoId);
}
```

### TodoDetailProvider.clearRemind()
清除提醒时取消通知。

### TodoListProvider.toggleComplete()
完成时取消通知。

### TodoListProvider.quickAdd()
新建带提醒的待办时调度通知。
