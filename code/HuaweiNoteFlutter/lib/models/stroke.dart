enum BrushType {
  pen,
  brush,
  marker,
  pencil;

  static BrushType? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
}

class StrokePoint {
  final int x;
  final int y;
  final int t;

  const StrokePoint({required this.x, required this.y, required this.t});
}

class Stroke {
  final BrushType brush;
  final String color;
  final int width;
  final List<StrokePoint> points;

  const Stroke({
    required this.brush,
    required this.color,
    required this.width,
    required this.points,
  });

  Map<String, dynamic> toJson() => {
        'brush': brush.name,
        'color': color,
        'width': width,
        'points': points.map((p) => [p.x, p.y, p.t]).toList(),
      };

  static Stroke? fromJson(Map<String, dynamic> json) {
    final brush = BrushType.fromName(json['brush'] as String? ?? '');
    if (brush == null) return null;
    final ptsList = json['points'] as List<dynamic>?;
    if (ptsList == null) return null;
    final points = <StrokePoint>[];
    for (final p in ptsList) {
      if (p is List && p.length >= 3) {
        points.add(StrokePoint(x: p[0] as int, y: p[1] as int, t: p[2] as int));
      }
    }
    return Stroke(
      brush: brush,
      color: json['color'] as String? ?? '',
      width: json['width'] as int? ?? 1,
      points: points,
    );
  }
}
