import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/todo.dart';
import 'package:hwnote/providers/todo_detail_provider.dart';

void main() {
  group('TodoDetailState', () {
    test('initial state for new todo has default values', () {
      final state = TodoDetailState.initial(todoId: 0);
      expect(state.todoId, 0);
      expect(state.title, '');
      expect(state.memo, '');
      expect(state.isCompleted, false);
      expect(state.isImportant, false);
      expect(state.remindAt, 0);
      expect(state.repeatType, RepeatType.none);
      expect(state.folderId, null);
      expect(state.folderName, '未分类');
      expect(state.isLoaded, false);
    });

    test('initial state for existing todo has correct todoId', () {
      final state = TodoDetailState.initial(todoId: 42);
      expect(state.todoId, 42);
      expect(state.isLoaded, false);
    });

    test('remindTimeText returns placeholder when no remind', () {
      final state = TodoDetailState.initial(todoId: 0);
      expect(state.remindTimeText, '添加提醒');
    });

    test('remindTimeText shows formatted time when set', () {
      final dt = DateTime(2026, 6, 7, 14, 30);
      final state = TodoDetailState.initial(todoId: 0).copyWith(
        remindAt: dt.millisecondsSinceEpoch,
      );
      expect(state.remindTimeText, contains('2:30'));
    });

    test('repeatTypeText returns correct labels', () {
      final state = TodoDetailState.initial(todoId: 0);
      expect(state.copyWith(repeatType: RepeatType.none).repeatTypeText, '不重复');
      expect(state.copyWith(repeatType: RepeatType.daily).repeatTypeText, '每天');
      expect(
          state.copyWith(repeatType: RepeatType.weekly).repeatTypeText, '每周');
      expect(
          state.copyWith(repeatType: RepeatType.monthly).repeatTypeText, '每月');
      expect(
          state.copyWith(repeatType: RepeatType.yearly).repeatTypeText, '每年');
    });

    test('isOverdue returns true when remind time is past and not completed',
        () {
      final pastTime = DateTime.now()
          .subtract(const Duration(hours: 1))
          .millisecondsSinceEpoch;
      final state = TodoDetailState.initial(todoId: 0).copyWith(
        remindAt: pastTime,
        isCompleted: false,
      );
      expect(state.isOverdue, true);
    });

    test('isOverdue returns false when completed', () {
      final pastTime = DateTime.now()
          .subtract(const Duration(hours: 1))
          .millisecondsSinceEpoch;
      final state = TodoDetailState.initial(todoId: 0).copyWith(
        remindAt: pastTime,
        isCompleted: true,
      );
      expect(state.isOverdue, false);
    });
  });
}
