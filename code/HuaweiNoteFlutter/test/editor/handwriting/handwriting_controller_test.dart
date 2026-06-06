import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/editor/handwriting/handwriting_controller.dart';
import 'package:hwnote/models/stroke.dart';

Stroke _makeStroke(int x) => Stroke(
  brush: BrushType.pen, color: '#000000', width: 3,
  points: [StrokePoint(x: x, y: 0, t: 0)],
);

void main() {
  late HandwritingOverlayController controller;
  setUp(() => controller = HandwritingOverlayController());

  group('addStroke', () {
    test('adds stroke to list', () {
      final s = _makeStroke(10);
      controller.addStroke(s);
      expect(controller.strokes, [s]);
      expect(controller.canUndo, isTrue);
      expect(controller.canRedo, isFalse);
    });
    test('clears redo stack', () {
      controller.addStroke(_makeStroke(1));
      controller.undo();
      expect(controller.canRedo, isTrue);
      controller.addStroke(_makeStroke(2));
      expect(controller.canRedo, isFalse);
    });
  });

  group('undo/redo', () {
    test('undo Add removes stroke', () {
      final s = _makeStroke(10);
      controller.addStroke(s);
      controller.undo();
      expect(controller.strokes, isEmpty);
      expect(controller.canRedo, isTrue);
    });
    test('redo Add restores stroke', () {
      final s = _makeStroke(10);
      controller.addStroke(s);
      controller.undo();
      controller.redo();
      expect(controller.strokes, [s]);
    });
    test('undo Erase restores at original indices', () {
      final s1 = _makeStroke(1);
      final s2 = _makeStroke(2);
      final s3 = _makeStroke(3);
      controller.addStroke(s1);
      controller.addStroke(s2);
      controller.addStroke(s3);
      controller.eraseStrokes([IndexedStroke(1, s2)]);
      expect(controller.strokes, [s1, s3]);
      controller.undo();
      expect(controller.strokes, [s1, s2, s3]);
    });
    test('redo Erase removes again', () {
      final s1 = _makeStroke(1);
      final s2 = _makeStroke(2);
      controller.addStroke(s1);
      controller.addStroke(s2);
      controller.eraseStrokes([IndexedStroke(0, s1)]);
      controller.undo();
      controller.redo();
      expect(controller.strokes, [s2]);
    });
    test('undo on empty does nothing', () {
      controller.undo();
      expect(controller.strokes, isEmpty);
    });
    test('redo on empty does nothing', () {
      controller.redo();
      expect(controller.strokes, isEmpty);
    });
  });

  group('setStrokes', () {
    test('replaces and clears stacks', () {
      controller.addStroke(_makeStroke(1));
      final s2 = _makeStroke(2);
      controller.setStrokes([s2]);
      expect(controller.strokes, [s2]);
      expect(controller.canUndo, isFalse);
      expect(controller.canRedo, isFalse);
    });
  });

  group('clear', () {
    test('clears all and pushes Erase', () {
      controller.addStroke(_makeStroke(1));
      controller.addStroke(_makeStroke(2));
      controller.clear();
      expect(controller.strokes, isEmpty);
      expect(controller.canUndo, isTrue);
    });
    test('undo after clear restores all', () {
      final s1 = _makeStroke(1);
      final s2 = _makeStroke(2);
      controller.addStroke(s1);
      controller.addStroke(s2);
      controller.clear();
      controller.undo();
      expect(controller.strokes, [s1, s2]);
    });
    test('clear on empty does nothing', () {
      controller.clear();
      expect(controller.canUndo, isFalse);
    });
  });

  group('eraseStrokes', () {
    test('empty items does nothing', () {
      controller.addStroke(_makeStroke(1));
      controller.eraseStrokes([]);
      expect(controller.strokes.length, 1);
    });
  });
}
