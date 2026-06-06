import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/todo.dart';
import 'package:hwnote/utils/todo_group_utils.dart';

void main() {
  group('groupTodos', () {
    final now = DateTime.now().millisecondsSinceEpoch;
    final todayStart = DateTime(
      DateTime.now().year,
      DateTime.now().month,
      DateTime.now().day,
    ).millisecondsSinceEpoch;
    final tomorrowStart = todayStart + 86400000;
    final dayAfterTomorrow = tomorrowStart + 86400000;

    test('empty list returns empty groups', () {
      expect(groupTodos([]), isEmpty);
    });

    test('completed todos go to completed section', () {
      final todos = [
        Todo(id: 1, isCompleted: true, createdAt: now, updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups.length, 1);
      expect(groups[0].section, TodoSection.completed);
      expect(groups[0].label, '已完成');
    });

    test('no-date todos go to noDate section', () {
      final todos = [
        Todo(id: 1, remindAt: 0, createdAt: now, updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups.length, 1);
      expect(groups[0].section, TodoSection.noDate);
      expect(groups[0].label, '无日期');
    });

    test('overdue todos marked isOverdue', () {
      final todos = [
        Todo(
            id: 1,
            remindAt: todayStart - 86400000,
            createdAt: now,
            updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups[0].isOverdue, true);
      expect(groups[0].section, TodoSection.overdue);
      expect(groups[0].label, '已过期');
    });

    test('today todos grouped correctly', () {
      final todos = [
        Todo(
            id: 1,
            remindAt: todayStart + 3600000,
            createdAt: now,
            updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups[0].section, TodoSection.today);
    });

    test('tomorrow todos grouped correctly', () {
      final todos = [
        Todo(
            id: 1,
            remindAt: tomorrowStart + 1000,
            createdAt: now,
            updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups[0].section, TodoSection.tomorrow);
    });

    test('later todos grouped correctly', () {
      final todos = [
        Todo(
            id: 1,
            remindAt: dayAfterTomorrow + 1000,
            createdAt: now,
            updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups[0].section, TodoSection.later);
    });

    test('multiple sections ordered correctly', () {
      final todos = [
        Todo(
            id: 1,
            remindAt: todayStart - 1000,
            createdAt: now,
            updatedAt: now),
        Todo(
            id: 2,
            remindAt: todayStart + 1000,
            createdAt: now,
            updatedAt: now),
        Todo(id: 3, remindAt: 0, createdAt: now, updatedAt: now),
        Todo(id: 4, isCompleted: true, createdAt: now, updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups[0].section, TodoSection.overdue);
      expect(groups[1].section, TodoSection.today);
      expect(groups[2].section, TodoSection.noDate);
      expect(groups[3].section, TodoSection.completed);
    });

    test('empty sections are not included', () {
      final todos = [
        Todo(id: 1, remindAt: 0, createdAt: now, updatedAt: now),
        Todo(id: 2, isCompleted: true, createdAt: now, updatedAt: now),
      ];
      final groups = groupTodos(todos);
      expect(groups.length, 2);
      expect(groups[0].section, TodoSection.noDate);
      expect(groups[1].section, TodoSection.completed);
    });
  });
}
