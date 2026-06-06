import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/text_span.dart';

void main() {
  group('SpanType', () {
    test('has all expected values', () {
      expect(SpanType.values.length, 6);
      expect(SpanType.values, contains(SpanType.bold));
      expect(SpanType.values, contains(SpanType.italic));
      expect(SpanType.values, contains(SpanType.underline));
      expect(SpanType.values, contains(SpanType.strikethrough));
      expect(SpanType.values, contains(SpanType.fontSize));
      expect(SpanType.values, contains(SpanType.color));
    });
  });

  group('NoteTextSpan', () {
    test('creates with required fields', () {
      final span = NoteTextSpan(start: 0, end: 5, type: SpanType.bold);
      expect(span.start, 0);
      expect(span.end, 5);
      expect(span.type, SpanType.bold);
      expect(span.value, isNull);
    });

    test('creates with optional value', () {
      final span = NoteTextSpan(
        start: 0, end: 5, type: SpanType.color, value: '#FF0000',
      );
      expect(span.value, '#FF0000');
    });

    test('equality works', () {
      final a = NoteTextSpan(start: 0, end: 5, type: SpanType.bold);
      final b = NoteTextSpan(start: 0, end: 5, type: SpanType.bold);
      expect(a, equals(b));
    });

    test('toJson and fromJson roundtrip', () {
      final span = NoteTextSpan(
        start: 2, end: 7, type: SpanType.fontSize, value: 'large',
      );
      final json = span.toJson();
      final restored = NoteTextSpan.fromJson(json);
      expect(restored, equals(span));
    });

    test('fromJson with unknown type returns null', () {
      final json = {'start': 0, 'end': 5, 'type': 'unknown_type'};
      expect(NoteTextSpan.fromJson(json), isNull);
    });
  });
}
