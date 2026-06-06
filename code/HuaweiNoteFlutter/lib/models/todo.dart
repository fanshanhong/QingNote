enum RepeatType {
  none(0), daily(1), weekly(2), monthly(3), yearly(4);

  final int value;
  const RepeatType(this.value);

  static RepeatType fromValue(int v) =>
      RepeatType.values.firstWhere((e) => e.value == v, orElse: () => none);
}

class Todo {
  final int id;
  final String title;
  final String memo;
  final bool isCompleted;
  final bool isImportant;
  final int remindAt;
  final RepeatType repeatType;
  final int? folderId;
  final int deletedAt;
  final int createdAt;
  final int updatedAt;

  const Todo({
    required this.id,
    this.title = '',
    this.memo = '',
    this.isCompleted = false,
    this.isImportant = false,
    this.remindAt = 0,
    this.repeatType = RepeatType.none,
    this.folderId,
    this.deletedAt = 0,
    required this.createdAt,
    required this.updatedAt,
  });

  factory Todo.newTodo({int? now}) {
    final ts = now ?? DateTime.now().millisecondsSinceEpoch;
    return Todo(id: 0, createdAt: ts, updatedAt: ts);
  }

  Todo copyWith({
    int? id, String? title, String? memo, bool? isCompleted,
    bool? isImportant, int? remindAt, RepeatType? repeatType,
    int? folderId, bool setFolderIdNull = false,
    int? deletedAt, int? createdAt, int? updatedAt,
  }) => Todo(
    id: id ?? this.id,
    title: title ?? this.title,
    memo: memo ?? this.memo,
    isCompleted: isCompleted ?? this.isCompleted,
    isImportant: isImportant ?? this.isImportant,
    remindAt: remindAt ?? this.remindAt,
    repeatType: repeatType ?? this.repeatType,
    folderId: setFolderIdNull ? null : (folderId ?? this.folderId),
    deletedAt: deletedAt ?? this.deletedAt,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
}
