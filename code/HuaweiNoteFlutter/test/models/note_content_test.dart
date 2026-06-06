import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/note_content.dart';
import 'package:hwnote/models/stroke.dart';

void main() {
  group('NoteContent', () {
    test('empty() creates content with empty document', () {
      final c = NoteContent.empty();
      expect(c.documentJson, containsPair('document', isA<Map>()));
      expect(c.handwriting, isEmpty);
      expect(c.isDocumentEmpty, isTrue);
    });

    test('toPlainText extracts text from appflowy document nodes', () {
      final c = NoteContent(
        documentJson: {
          'document': {
            'type': 'page',
            'children': [
              {
                'type': 'heading',
                'data': {
                  'level': 1,
                  'delta': [{'insert': 'Title'}],
                },
              },
              {
                'type': 'paragraph',
                'data': {
                  'delta': [
                    {'insert': 'Hello '},
                    {'insert': 'world', 'attributes': {'bold': true}},
                  ],
                },
              },
              {
                'type': 'todo_list',
                'data': {
                  'checked': false,
                  'delta': [{'insert': 'Buy milk'}],
                },
              },
            ],
          },
        },
        handwriting: [],
      );
      expect(c.toPlainText(), 'Title\nHello world\nBuy milk');
    });

    test('toPlainText returns empty for empty document', () {
      final c = NoteContent.empty();
      expect(c.toPlainText(), isEmpty);
    });

    test('toJson and fromJson roundtrip', () {
      final original = NoteContent(
        documentJson: {
          'document': {
            'type': 'page',
            'children': [
              {
                'type': 'paragraph',
                'data': {
                  'delta': [{'insert': 'Test content'}],
                },
              },
            ],
          },
        },
        handwriting: [
          Stroke(brush: BrushType.pen, color: '#000', width: 2, points: [
            StrokePoint(x: 1, y: 2, t: 0),
          ]),
        ],
      );
      final json = original.toJson();
      final restored = NoteContent.fromJson(json);

      expect(restored.toPlainText(), 'Test content');
      expect(restored.handwriting.length, 1);
      expect(restored.handwriting[0].color, '#000');
    });

    test('fromJson with invalid data returns empty', () {
      final c = NoteContent.fromJson('not json');
      expect(c.isDocumentEmpty, isTrue);
      expect(c.handwriting, isEmpty);
    });

    test('fromJson preserves handwriting separate from document', () {
      final json = jsonEncode({
        'document': {
          'type': 'page',
          'children': [
            {'type': 'paragraph', 'data': {'delta': [{'insert': 'Hi'}]}},
          ],
        },
        'handwriting': {
          'strokes': [
            {'brush': 'pen', 'color': '#212121', 'width': 3, 'points': [
              {'x': 10.0, 'y': 20.0, 't': 100},
            ]},
          ],
        },
      });
      final c = NoteContent.fromJson(json);
      expect(c.toPlainText(), 'Hi');
      expect(c.handwriting.length, 1);
      expect(c.documentJson.containsKey('handwriting'), isFalse);
    });

    test('isDocumentEmpty returns false for non-empty content', () {
      final c = NoteContent(
        documentJson: {
          'document': {
            'type': 'page',
            'children': [
              {'type': 'paragraph', 'data': {'delta': [{'insert': 'Hi'}]}},
            ],
          },
        },
        handwriting: [],
      );
      expect(c.isDocumentEmpty, isFalse);
    });
  });
}
