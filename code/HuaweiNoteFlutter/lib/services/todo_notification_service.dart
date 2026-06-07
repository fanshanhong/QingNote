import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/timezone.dart' as tz;
import 'package:timezone/data/latest_all.dart' as tz_data;
import '../repositories/todo_repository.dart';

class TodoNotificationService {
  static final instance = TodoNotificationService._();
  TodoNotificationService._();

  final _plugin = FlutterLocalNotificationsPlugin();
  void Function(String?)? onNotificationTap;
  void Function(int todoId)? onCompleteAction;

  static const _channelId = 'todo_reminders';
  static const _channelName = '待办提醒';

  Future<void> init() async {
    tz_data.initializeTimeZones();

    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const darwinSettings = DarwinInitializationSettings(
      requestAlertPermission: false,
      requestBadgePermission: false,
      requestSoundPermission: false,
    );
    const settings = InitializationSettings(
      android: androidSettings,
      iOS: darwinSettings,
      macOS: darwinSettings,
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
    final todoId = int.tryParse(todoIdStr);
    if (todoId == null || todoId <= 0) return;
    onCompleteAction?.call(todoId);
    cancelReminder(todoId);
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
      uiLocalNotificationDateInterpretation:
          UILocalNotificationDateInterpretation.absoluteTime,
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
