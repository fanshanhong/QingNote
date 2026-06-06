import 'dart:ui';

import 'package:flutter/material.dart';

import '../../models/stroke.dart';
import 'brush_paint_factory.dart';

class HandwritingPainter extends CustomPainter {
  final List<Stroke> strokes;
  final List<Offset> inProgressPoints;
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;
  final double devicePixelRatio;

  HandwritingPainter({
    required this.strokes,
    required this.inProgressPoints,
    required this.currentBrush,
    required this.currentColor,
    required this.currentWidth,
    required this.devicePixelRatio,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final clipRect = Offset.zero & size;
    for (final stroke in strokes) {
      if (stroke.points.isEmpty) continue;
      if (!_strokeIntersectsRect(stroke, clipRect)) continue;
      _drawStroke(canvas, stroke);
    }
    if (inProgressPoints.isNotEmpty) {
      _drawInProgress(canvas);
    }
  }

  void _drawStroke(Canvas canvas, Stroke stroke) {
    final paint = BrushPaintFactory.createPaint(stroke, devicePixelRatio);
    if (stroke.points.length == 1) {
      final p = stroke.points[0];
      canvas.drawPoints(
        PointMode.points,
        [Offset(p.x.toDouble(), p.y.toDouble())],
        paint,
      );
      return;
    }
    final path = Path();
    path.moveTo(stroke.points[0].x.toDouble(), stroke.points[0].y.toDouble());
    for (int i = 1; i < stroke.points.length; i++) {
      path.lineTo(
        stroke.points[i].x.toDouble(),
        stroke.points[i].y.toDouble(),
      );
    }
    canvas.drawPath(path, paint);
  }

  void _drawInProgress(Canvas canvas) {
    final tmpStroke = Stroke(
      brush: currentBrush,
      color: currentColor,
      width: currentWidth,
      points: inProgressPoints
          .map((o) => StrokePoint(x: o.dx.toInt(), y: o.dy.toInt(), t: 0))
          .toList(),
    );
    _drawStroke(canvas, tmpStroke);
  }

  bool _strokeIntersectsRect(Stroke s, Rect rect) {
    double minX = double.infinity, minY = double.infinity;
    double maxX = double.negativeInfinity, maxY = double.negativeInfinity;
    for (final p in s.points) {
      final px = p.x.toDouble();
      final py = p.y.toDouble();
      if (px < minX) minX = px;
      if (px > maxX) maxX = px;
      if (py < minY) minY = py;
      if (py > maxY) maxY = py;
    }
    final pad = s.width / 2.0 + 4.0;
    final strokeRect =
        Rect.fromLTRB(minX - pad, minY - pad, maxX + pad, maxY + pad);
    return rect.overlaps(strokeRect);
  }

  @override
  bool shouldRepaint(covariant HandwritingPainter oldDelegate) => true;
}
