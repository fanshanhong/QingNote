import 'package:flutter/material.dart';

import '../../models/stroke.dart';

class BrushPaintFactory {
  BrushPaintFactory._();

  static Paint createPaint(Stroke stroke, double devicePixelRatio) {
    final basePx = stroke.width * devicePixelRatio;
    final color = _parseColor(stroke.color);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..isAntiAlias = true;

    switch (stroke.brush) {
      case BrushType.pen:
        paint.color = color.withAlpha(255);
        paint.strokeWidth = basePx;
      case BrushType.brush:
        paint.color = color.withAlpha(255);
        paint.strokeWidth = basePx * 1.3;
        paint.maskFilter =
            MaskFilter.blur(BlurStyle.normal, 2.0 * devicePixelRatio);
      case BrushType.marker:
        paint.color = color.withAlpha(140);
        paint.strokeWidth = basePx * 1.6;
      case BrushType.pencil:
        paint.color = color.withAlpha(160);
        paint.strokeWidth = basePx;
    }
    return paint;
  }

  static Color _parseColor(String hex) {
    if (hex.isEmpty) return Colors.black;
    var h = hex.replaceFirst('#', '');
    if (h.length == 6) h = 'FF$h';
    return Color(int.parse(h, radix: 16));
  }
}
