import '../../models/stroke.dart';

class StrokeEraser {
  StrokeEraser._();

  /// 返回被橡皮命中的 strokes
  static List<Stroke> hitTest(
    double ex, double ey, double radiusPx, List<Stroke> strokes,
  ) {
    if (strokes.isEmpty) return const [];
    final out = <Stroke>[];
    for (final s in strokes) {
      if (s.points.isEmpty) continue;
      final threshold = radiusPx + s.width / 2.0;
      if (!_bboxIntersectsCircle(s, ex, ey, threshold)) continue;
      if (_anySegmentWithin(s, ex, ey, threshold)) out.add(s);
    }
    return out;
  }

  static bool _bboxIntersectsCircle(Stroke s, double ex, double ey, double r) {
    int minX = 0x7FFFFFFF, minY = 0x7FFFFFFF;
    int maxX = -0x7FFFFFFF, maxY = -0x7FFFFFFF;
    for (final p in s.points) {
      if (p.x < minX) minX = p.x;
      if (p.x > maxX) maxX = p.x;
      if (p.y < minY) minY = p.y;
      if (p.y > maxY) maxY = p.y;
    }
    final cx = ex.clamp(minX.toDouble(), maxX.toDouble());
    final cy = ey.clamp(minY.toDouble(), maxY.toDouble());
    final dx = ex - cx;
    final dy = ey - cy;
    return dx * dx + dy * dy <= r * r;
  }

  static bool _anySegmentWithin(Stroke s, double ex, double ey, double threshold) {
    final pts = s.points;
    if (pts.length == 1) {
      final p = pts[0];
      final dx = ex - p.x;
      final dy = ey - p.y;
      return dx * dx + dy * dy <= threshold * threshold;
    }
    for (int i = 0; i < pts.length - 1; i++) {
      final a = pts[i];
      final b = pts[i + 1];
      if (_pointToSegmentDistSq(ex, ey, a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble()) <= threshold * threshold) {
        return true;
      }
    }
    return false;
  }

  static double _pointToSegmentDistSq(double px, double py, double ax, double ay, double bx, double by) {
    final abx = bx - ax;
    final aby = by - ay;
    final apx = px - ax;
    final apy = py - ay;
    final abLen2 = abx * abx + aby * aby;
    if (abLen2 == 0.0) return apx * apx + apy * apy;
    var t = (apx * abx + apy * aby) / abLen2;
    if (t < 0.0) t = 0.0;
    else if (t > 1.0) t = 1.0;
    final cx = ax + t * abx;
    final cy = ay + t * aby;
    final dx = px - cx;
    final dy = py - cy;
    return dx * dx + dy * dy;
  }
}
