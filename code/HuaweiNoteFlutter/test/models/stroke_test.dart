import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/stroke.dart';

void main() {
  group('BrushType', () {
    test('has 4 values', () {
      expect(BrushType.values.length, 4);
      expect(BrushType.values, contains(BrushType.pen));
      expect(BrushType.values, contains(BrushType.brush));
      expect(BrushType.values, contains(BrushType.marker));
      expect(BrushType.values, contains(BrushType.pencil));
    });
  });

  group('StrokePoint', () {
    test('creates correctly', () {
      final p = StrokePoint(x: 100, y: 200, t: 50);
      expect(p.x, 100);
      expect(p.y, 200);
      expect(p.t, 50);
    });
  });

  group('Stroke', () {
    test('toJson and fromJson roundtrip', () {
      final stroke = Stroke(
        brush: BrushType.pen,
        color: '#000000',
        width: 3,
        points: [
          StrokePoint(x: 10, y: 20, t: 0),
          StrokePoint(x: 30, y: 40, t: 16),
        ],
      );
      final json = stroke.toJson();
      expect(json['brush'], 'pen');
      final restored = Stroke.fromJson(json);
      expect(restored, isNotNull);
      expect(restored!.brush, BrushType.pen);
      expect(restored.points.length, 2);
      expect(restored.points[0].x, 10);
    });

    test('fromJson with unknown brush returns null', () {
      final json = {'brush': 'crayon', 'color': '#000', 'width': 1, 'points': []};
      expect(Stroke.fromJson(json), isNull);
    });

    test('fromJson with missing points returns null', () {
      final json = {'brush': 'pen', 'color': '#000', 'width': 1};
      expect(Stroke.fromJson(json), isNull);
    });
  });
}
