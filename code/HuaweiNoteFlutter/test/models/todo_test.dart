import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/todo.dart';

void main() {
  group('RepeatType', () {
    test('fromValue returns correct type', () {
      expect(RepeatType.fromValue(0), RepeatType.none);
      expect(RepeatType.fromValue(1), RepeatType.daily);
      expect(RepeatType.fromValue(2), RepeatType.weekly);
      expect(RepeatType.fromValue(3), RepeatType.monthly);
      expect(RepeatType.fromValue(4), RepeatType.yearly);
      expect(RepeatType.fromValue(99), RepeatType.none);
    });
  });

  group('Todo', () {
    test('creates with defaults', () {
      final t = Todo(id: 0, createdAt: 1000, updatedAt: 1000);
      expect(t.title, '');
      expect(t.memo, '');
      expect(t.isCompleted, false);
      expect(t.isImportant, false);
      expect(t.remindAt, 0);
      expect(t.repeatType, RepeatType.none);
      expect(t.folderId, isNull);
      expect(t.deletedAt, 0);
    });

    test('newTodo() sets timestamps', () {
      final now = DateTime.now().millisecondsSinceEpoch;
      final t = Todo.newTodo(now: now);
      expect(t.id, 0);
      expect(t.createdAt, now);
    });

    test('copyWith preserves unmodified fields', () {
      final t = Todo(
          id: 5,
          title: 'Buy',
          createdAt: 100,
          updatedAt: 200,
          isImportant: true);
      final t2 = t.copyWith(title: 'Sell');
      expect(t2.id, 5);
      expect(t2.title, 'Sell');
      expect(t2.isImportant, true);
    });
  });
}
