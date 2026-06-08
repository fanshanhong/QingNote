import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/todo.dart';
import '../repositories/todo_repository.dart';
import '../repositories/folder_repository.dart';
import '../services/todo_notification_service.dart';
import 'repository_providers.dart';

class TodoDetailState {
  final int todoId;
  final String title;
  final String memo;
  final bool isCompleted;
  final bool isImportant;
  final int remindAt;
  final RepeatType repeatType;
  final int? folderId;
  final String folderName;
  final bool isLoaded;

  const TodoDetailState({
    required this.todoId,
    this.title = '',
    this.memo = '',
    this.isCompleted = false,
    this.isImportant = false,
    this.remindAt = 0,
    this.repeatType = RepeatType.none,
    this.folderId,
    this.folderName = '未分类',
    this.isLoaded = false,
  });

  factory TodoDetailState.initial({required int todoId}) =>
      TodoDetailState(todoId: todoId);

  String get remindTimeText {
    if (remindAt <= 0) return '添加提醒';
    final dt = DateTime.fromMillisecondsSinceEpoch(remindAt);
    final now = DateTime.now();
    final isToday =
        dt.year == now.year && dt.month == now.month && dt.day == now.day;
    final amPm = dt.hour < 12 ? '上午' : '下午';
    final hour = dt.hour == 0 ? 12 : (dt.hour > 12 ? dt.hour - 12 : dt.hour);
    final minute = dt.minute.toString().padLeft(2, '0');
    if (isToday) {
      return '$amPm$hour:$minute';
    }
    return '${dt.month}月${dt.day}日 $amPm$hour:$minute';
  }

  String get repeatTypeText => switch (repeatType) {
        RepeatType.none => '不重复',
        RepeatType.daily => '每天',
        RepeatType.weekly => '每周',
        RepeatType.monthly => '每月',
        RepeatType.yearly => '每年',
      };

  bool get isOverdue =>
      remindAt > 0 &&
      remindAt < DateTime.now().millisecondsSinceEpoch &&
      !isCompleted;

  TodoDetailState copyWith({
    int? todoId,
    String? title,
    String? memo,
    bool? isCompleted,
    bool? isImportant,
    int? remindAt,
    RepeatType? repeatType,
    int? folderId,
    bool setFolderIdNull = false,
    String? folderName,
    bool? isLoaded,
  }) =>
      TodoDetailState(
        todoId: todoId ?? this.todoId,
        title: title ?? this.title,
        memo: memo ?? this.memo,
        isCompleted: isCompleted ?? this.isCompleted,
        isImportant: isImportant ?? this.isImportant,
        remindAt: remindAt ?? this.remindAt,
        repeatType: repeatType ?? this.repeatType,
        folderId: setFolderIdNull ? null : (folderId ?? this.folderId),
        folderName: folderName ?? this.folderName,
        isLoaded: isLoaded ?? this.isLoaded,
      );
}

class TodoDetailNotifier extends StateNotifier<TodoDetailState> {
  final TodoRepository _todoRepo;
  final FolderRepository _folderRepo;

  TodoDetailNotifier(this._todoRepo, this._folderRepo, int todoId)
      : super(TodoDetailState.initial(todoId: todoId));

  Future<void> load() async {
    if (state.todoId == 0) {
      state = state.copyWith(isLoaded: true);
      return;
    }
    final todo = await _todoRepo.getById(state.todoId);
    if (todo == null) return;
    String folderName = '未分类';
    if (todo.folderId != null) {
      final folder = await _folderRepo.get(todo.folderId!);
      folderName = folder?.name ?? '未分类';
    }
    state = state.copyWith(
      title: todo.title,
      memo: todo.memo,
      isCompleted: todo.isCompleted,
      isImportant: todo.isImportant,
      remindAt: todo.remindAt,
      repeatType: todo.repeatType,
      folderId: todo.folderId,
      setFolderIdNull: todo.folderId == null,
      folderName: folderName,
      isLoaded: true,
    );
  }

  void setTitle(String value) => state = state.copyWith(title: value);
  void setMemo(String value) => state = state.copyWith(memo: value);
  void setCompleted(bool value) => state = state.copyWith(isCompleted: value);
  void setImportant(bool value) => state = state.copyWith(isImportant: value);

  void setRemindAt(int value) => state = state.copyWith(remindAt: value);

  void clearRemind() {
    state = state.copyWith(remindAt: 0, repeatType: RepeatType.none);
    if (state.todoId > 0) {
      TodoNotificationService.instance.cancelReminder(state.todoId);
    }
  }

  void setRepeatType(RepeatType value) =>
      state = state.copyWith(repeatType: value);

  Future<void> setFolder(int? folderId) async {
    if (folderId == null) {
      state = state.copyWith(setFolderIdNull: true, folderName: '未分类');
      return;
    }
    final folder = await _folderRepo.get(folderId);
    state = state.copyWith(
      folderId: folderId,
      folderName: folder?.name ?? '未分类',
    );
  }

  Future<bool> save() async {
    final currentState = state;
    if (currentState.todoId == 0 && currentState.title.trim().isEmpty) {
      return false;
    }
    final now = DateTime.now().millisecondsSinceEpoch;
    final todo = Todo(
      id: currentState.todoId,
      title: currentState.title.trim(),
      memo: currentState.memo,
      isCompleted: currentState.isCompleted,
      isImportant: currentState.isImportant,
      remindAt: currentState.remindAt,
      repeatType: currentState.repeatType,
      folderId: currentState.folderId,
      createdAt: now,
      updatedAt: now,
    );
    if (currentState.todoId == 0) {
      final newId = await _todoRepo.insert(todo);
      if (newId > 0) {
        if (mounted) state = state.copyWith(todoId: newId);
        _scheduleOrCancelWith(
            newId, currentState.remindAt, currentState.isCompleted,
            currentState.title.trim());
        return true;
      }
      return false;
    } else {
      await _todoRepo.update(todo);
      _scheduleOrCancelWith(
          currentState.todoId, currentState.remindAt, currentState.isCompleted,
          currentState.title.trim());
      return true;
    }
  }

  void _scheduleOrCancelWith(
      int todoId, int remindAt, bool isCompleted, String title) {
    if (remindAt > DateTime.now().millisecondsSinceEpoch && !isCompleted) {
      TodoNotificationService.instance
          .scheduleReminder(todoId, title, remindAt);
    } else {
      TodoNotificationService.instance.cancelReminder(todoId);
    }
  }

  Future<void> delete() async {
    if (state.todoId > 0) {
      TodoNotificationService.instance.cancelReminder(state.todoId);
      await _todoRepo.softDelete(state.todoId);
    }
  }
}

final todoDetailProvider = StateNotifierProvider.autoDispose
    .family<TodoDetailNotifier, TodoDetailState, int>((ref, todoId) {
  return TodoDetailNotifier(
    ref.watch(todoRepositoryProvider),
    ref.watch(folderRepositoryProvider),
    todoId,
  );
});
