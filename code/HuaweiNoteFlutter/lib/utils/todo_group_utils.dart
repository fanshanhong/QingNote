import '../models/todo.dart';

enum TodoSection { overdue, today, tomorrow, later, noDate, completed }

class TodoGroupItem {
  final TodoSection section;
  final String label;
  final bool isOverdue;
  final List<Todo> todos;

  const TodoGroupItem({
    required this.section,
    required this.label,
    this.isOverdue = false,
    required this.todos,
  });
}

List<TodoGroupItem> groupTodos(List<Todo> todos) {
  final now = DateTime.now();
  final todayStart =
      DateTime(now.year, now.month, now.day).millisecondsSinceEpoch;
  final tomorrowStart = todayStart + 86400000;
  final dayAfterTomorrow = tomorrowStart + 86400000;

  final overdue = <Todo>[];
  final today = <Todo>[];
  final tomorrow = <Todo>[];
  final later = <Todo>[];
  final noDate = <Todo>[];
  final completed = <Todo>[];

  for (final t in todos) {
    if (t.isCompleted) {
      completed.add(t);
    } else if (t.remindAt == 0) {
      noDate.add(t);
    } else if (t.remindAt < todayStart) {
      overdue.add(t);
    } else if (t.remindAt < tomorrowStart) {
      today.add(t);
    } else if (t.remindAt < dayAfterTomorrow) {
      tomorrow.add(t);
    } else {
      later.add(t);
    }
  }

  final groups = <TodoGroupItem>[];
  if (overdue.isNotEmpty) {
    groups.add(TodoGroupItem(
        section: TodoSection.overdue,
        label: '已过期',
        isOverdue: true,
        todos: overdue));
  }
  if (today.isNotEmpty) {
    groups.add(TodoGroupItem(
        section: TodoSection.today, label: '今天', todos: today));
  }
  if (tomorrow.isNotEmpty) {
    groups.add(TodoGroupItem(
        section: TodoSection.tomorrow, label: '明天', todos: tomorrow));
  }
  if (later.isNotEmpty) {
    groups.add(TodoGroupItem(
        section: TodoSection.later, label: '更晚', todos: later));
  }
  if (noDate.isNotEmpty) {
    groups.add(TodoGroupItem(
        section: TodoSection.noDate, label: '无日期', todos: noDate));
  }
  if (completed.isNotEmpty) {
    groups.add(TodoGroupItem(
        section: TodoSection.completed, label: '已完成', todos: completed));
  }
  return groups;
}
