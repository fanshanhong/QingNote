import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/note_content.dart';
import 'package:hwnote/models/block.dart';
import 'package:hwnote/models/stroke.dart';

void main() {
  group('NoteContent', () {
    test('empty() creates empty content', () {
      final c = NoteContent.empty();
      expect(c.blocks, isEmpty);
      expect(c.handwriting, isEmpty);
    });

    test('toPlainText extracts text from TextBlock and ChecklistBlock', () {
      final c = NoteContent(
        blocks: [
          TextBlock(id: '1', text: 'Hello'),
          ImageBlock(id: '2', fileName: 'a.jpg', width: 1, height: 1),
          ChecklistBlock(id: '3', items: [
            ChecklistItem(checked: false, text: 'Buy milk'),
            ChecklistItem(checked: true, text: ''),
          ]),
          AudioBlock(id: '4', fileName: 'r.m4a', durationMs: 1000),
          TextBlock(id: '5', text: ''),
        ],
        handwriting: [],
      );
      expect(c.toPlainText(), 'Hello\nBuy milk');
    });

    test('toJson and fromJson roundtrip', () {
      final c = NoteContent(
        blocks: [TextBlock(id: '1', text: 'Hi')],
        handwriting: [
          Stroke(brush: BrushType.pen, color: '#000', width: 2, points: [
            StrokePoint(x: 1, y: 2, t: 0),
          ]),
        ],
      );
      final json = c.toJson();
      final restored = NoteContent.fromJson(json);
      expect(restored.blocks.length, 1);
      expect((restored.blocks[0] as TextBlock).text, 'Hi');
      expect(restored.handwriting.length, 1);
    });

    test('fromJson with invalid data returns empty', () {
      final c = NoteContent.fromJson('not json');
      expect(c.blocks, isEmpty);
      expect(c.handwriting, isEmpty);
    });
  });
}
