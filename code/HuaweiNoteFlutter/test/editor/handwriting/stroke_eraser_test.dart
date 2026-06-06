import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/editor/handwriting/stroke_eraser.dart';
import 'package:hwnote/models/stroke.dart';

void main() {
  group('StrokeEraser.hitTest', () {
    test('empty strokes returns empty', () {
      expect(StrokeEraser.hitTest(50, 50, 12, []), isEmpty);
    });

    test('stroke with empty points is skipped', () {
      final s = Stroke(brush: BrushType.pen, color: '#000000', width: 3, points: []);
      expect(StrokeEraser.hitTest(50, 50, 12, [s]), isEmpty);
    });

    test('single point stroke within radius is hit', () {
      final s = Stroke(brush: BrushType.pen, color: '#000000', width: 3, points: [StrokePoint(x: 50, y: 50, t: 0)]);
      expect(StrokeEraser.hitTest(52, 52, 12, [s]), contains(s));
    });

    test('single point stroke outside radius is not hit', () {
      final s = Stroke(brush: BrushType.pen, color: '#000000', width: 1, points: [StrokePoint(x: 50, y: 50, t: 0)]);
      expect(StrokeEraser.hitTest(100, 100, 12, [s]), isEmpty);
    });

    test('horizontal line stroke hit in middle', () {
      final s = Stroke(brush: BrushType.pen, color: '#000000', width: 3, points: [
        StrokePoint(x: 0, y: 50, t: 0),
        StrokePoint(x: 100, y: 50, t: 100),
      ]);
      expect(StrokeEraser.hitTest(50, 52, 12, [s]), contains(s));
    });

    test('stroke far from eraser is not hit', () {
      final s = Stroke(brush: BrushType.pen, color: '#000000', width: 3, points: [
        StrokePoint(x: 0, y: 0, t: 0),
        StrokePoint(x: 10, y: 0, t: 100),
      ]);
      expect(StrokeEraser.hitTest(200, 200, 12, [s]), isEmpty);
    });

    test('multiple strokes returns only hit ones', () {
      final hit = Stroke(brush: BrushType.pen, color: '#000000', width: 3, points: [
        StrokePoint(x: 50, y: 50, t: 0), StrokePoint(x: 60, y: 50, t: 100),
      ]);
      final miss = Stroke(brush: BrushType.pen, color: '#000000', width: 3, points: [
        StrokePoint(x: 200, y: 200, t: 0), StrokePoint(x: 210, y: 200, t: 100),
      ]);
      expect(StrokeEraser.hitTest(55, 50, 12, [hit, miss]), [hit]);
    });

    test('stroke width affects threshold', () {
      final s = Stroke(brush: BrushType.marker, color: '#000000', width: 6, points: [
        StrokePoint(x: 50, y: 50, t: 0), StrokePoint(x: 60, y: 50, t: 100),
      ]);
      // distance from (55, 65) to line y=50 is 15; threshold = 12 + 3 = 15
      expect(StrokeEraser.hitTest(55, 65, 12, [s]), contains(s));
    });
  });
}
