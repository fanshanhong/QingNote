import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/block.dart';
import 'package:hwnote/models/text_span.dart';

void main() {
  group('Heading', () {
    test('fromName returns correct value', () {
      expect(Heading.fromName('h1'), Heading.h1);
      expect(Heading.fromName('h6'), Heading.h6);
      expect(Heading.fromName('unknown'), isNull);
    });
  });

  group('NoteAlignment', () {
    test('has 3 values', () {
      expect(NoteAlignment.values.length, 3);
    });
  });

  group('ListType', () {
    test('has 4 values', () {
      expect(ListType.values.length, 4);
    });
  });

  group('TextBlock', () {
    test('creates with defaults', () {
      final b = TextBlock(id: 'b-001');
      expect(b.id, 'b-001');
      expect(b.text, '');
      expect(b.spans, isEmpty);
      expect(b.heading, isNull);
      expect(b.alignment, isNull);
      expect(b.listType, isNull);
      expect(b.indentLevel, 0);
    });

    test('toJson and fromJson roundtrip', () {
      final b = TextBlock(
        id: 'b-001',
        text: 'Hello',
        heading: Heading.h1,
        spans: [NoteTextSpan(start: 0, end: 5, type: SpanType.bold)],
        alignment: NoteAlignment.center,
        listType: ListType.bullet,
        indentLevel: 2,
      );
      final json = b.toJson();
      expect(json['type'], 'text');
      final restored = Block.fromJson(json);
      expect(restored, isA<TextBlock>());
      final rt = restored as TextBlock;
      expect(rt.id, 'b-001');
      expect(rt.text, 'Hello');
      expect(rt.heading, Heading.h1);
      expect(rt.spans.length, 1);
      expect(rt.alignment, NoteAlignment.center);
      expect(rt.listType, ListType.bullet);
      expect(rt.indentLevel, 2);
    });
  });

  group('ImageBlock', () {
    test('toJson and fromJson roundtrip', () {
      final b = ImageBlock(id: 'b-002', fileName: 'img.jpg', width: 800, height: 600);
      final json = b.toJson();
      expect(json['type'], 'image');
      final restored = Block.fromJson(json) as ImageBlock;
      expect(restored.fileName, 'img.jpg');
      expect(restored.width, 800);
      expect(restored.height, 600);
    });
  });

  group('ChecklistBlock', () {
    test('toJson and fromJson roundtrip', () {
      final b = ChecklistBlock(id: 'b-003', items: [
        ChecklistItem(checked: true, text: 'Done'),
        ChecklistItem(checked: false, text: 'Todo'),
      ]);
      final json = b.toJson();
      expect(json['type'], 'checklist');
      final restored = Block.fromJson(json) as ChecklistBlock;
      expect(restored.items.length, 2);
      expect(restored.items[0].checked, true);
      expect(restored.items[1].text, 'Todo');
    });
  });

  group('AudioBlock', () {
    test('toJson and fromJson roundtrip', () {
      final b = AudioBlock(id: 'b-004', fileName: 'rec.m4a', durationMs: 5000);
      final json = b.toJson();
      expect(json['type'], 'audio');
      final restored = Block.fromJson(json) as AudioBlock;
      expect(restored.fileName, 'rec.m4a');
      expect(restored.durationMs, 5000);
    });
  });

  group('Block.fromJson', () {
    test('returns null for unknown type', () {
      expect(Block.fromJson({'type': 'video', 'id': 'x'}), isNull);
    });
  });
}
