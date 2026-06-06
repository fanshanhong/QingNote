enum SpanType {
  bold,
  italic,
  underline,
  strikethrough,
  fontSize,
  color;

  static SpanType? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
}

class NoteTextSpan {
  final int start;
  final int end;
  final SpanType type;
  final String? value;

  const NoteTextSpan({
    required this.start,
    required this.end,
    required this.type,
    this.value,
  });

  Map<String, dynamic> toJson() => {
    'start': start,
    'end': end,
    'type': type.name,
    if (value != null) 'value': value,
  };

  static NoteTextSpan? fromJson(Map<String, dynamic> json) {
    final type = SpanType.fromName(json['type'] as String? ?? '');
    if (type == null) return null;
    return NoteTextSpan(
      start: json['start'] as int? ?? 0,
      end: json['end'] as int? ?? 0,
      type: type,
      value: json['value'] as String?,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is NoteTextSpan &&
          start == other.start &&
          end == other.end &&
          type == other.type &&
          value == other.value;

  @override
  int get hashCode => Object.hash(start, end, type, value);

  @override
  String toString() => 'NoteTextSpan($start..$end, $type, value=$value)';
}
