import 'package:flutter/material.dart';
import '../../models/stroke.dart';
import 'handwriting_controller.dart';
import 'handwriting_painter.dart';
import 'stroke_eraser.dart';

class HandwritingOverlay extends StatefulWidget {
  final HandwritingOverlayController controller;
  final bool isActive;
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;
  final bool isErasing;

  const HandwritingOverlay({
    super.key,
    required this.controller,
    required this.isActive,
    required this.currentBrush,
    required this.currentColor,
    required this.currentWidth,
    required this.isErasing,
  });

  @override
  State<HandwritingOverlay> createState() => _HandwritingOverlayState();
}

class _HandwritingOverlayState extends State<HandwritingOverlay> {
  final List<Offset> _inProgressPoints = [];
  final Set<Stroke> _erasingHidden = {};
  List<Stroke> _gestureSnapshot = [];
  int _gestureStartMs = 0;

  static const double _eraserRadiusDp = 12.0;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onControllerChanged);
  }

  @override
  void didUpdateWidget(covariant HandwritingOverlay oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onControllerChanged);
      widget.controller.addListener(_onControllerChanged);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onControllerChanged);
    super.dispose();
  }

  void _onControllerChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final dpr = MediaQuery.of(context).devicePixelRatio;
    final visibleStrokes = _erasingHidden.isEmpty
        ? widget.controller.strokes
        : widget.controller.strokes
            .where((s) => !_erasingHidden.contains(s))
            .toList();
    return Listener(
      onPointerDown: widget.isActive ? _onPointerDown : null,
      onPointerMove: widget.isActive ? _onPointerMove : null,
      onPointerUp: widget.isActive ? _onPointerUp : null,
      onPointerCancel: widget.isActive ? _onPointerCancel : null,
      child: RepaintBoundary(
        child: CustomPaint(
          painter: HandwritingPainter(
            strokes: visibleStrokes,
            inProgressPoints: widget.isErasing ? const [] : _inProgressPoints,
            currentBrush: widget.currentBrush,
            currentColor: widget.currentColor,
            currentWidth: widget.currentWidth,
            devicePixelRatio: dpr,
          ),
          size: Size.infinite,
        ),
      ),
    );
  }

  void _onPointerDown(PointerDownEvent event) {
    _gestureStartMs = DateTime.now().millisecondsSinceEpoch;
    if (widget.isErasing) {
      _erasingHidden.clear();
      _gestureSnapshot = List.from(widget.controller.strokes);
      _eraseAt(event.localPosition);
    } else {
      _inProgressPoints.clear();
      _inProgressPoints.add(event.localPosition);
    }
    setState(() {});
  }

  void _onPointerMove(PointerMoveEvent event) {
    if (widget.isErasing) {
      _eraseAt(event.localPosition);
    } else {
      _inProgressPoints.add(event.localPosition);
    }
    setState(() {});
  }

  void _onPointerUp(PointerUpEvent event) {
    _finishGesture();
  }

  void _onPointerCancel(PointerCancelEvent event) {
    _finishGesture();
  }

  void _finishGesture() {
    if (widget.isErasing) {
      if (_erasingHidden.isNotEmpty) {
        final items = <IndexedStroke>[];
        for (int i = 0; i < _gestureSnapshot.length; i++) {
          if (_erasingHidden.contains(_gestureSnapshot[i])) {
            items.add(IndexedStroke(i, _gestureSnapshot[i]));
          }
        }
        widget.controller.eraseStrokes(items);
      }
      _erasingHidden.clear();
      _gestureSnapshot = [];
    } else if (_inProgressPoints.isNotEmpty) {
      final totalMs = DateTime.now().millisecondsSinceEpoch - _gestureStartMs;
      final points = <StrokePoint>[];
      for (int i = 0; i < _inProgressPoints.length; i++) {
        final o = _inProgressPoints[i];
        final t = _inProgressPoints.length == 1
            ? 0
            : (totalMs * i ~/ (_inProgressPoints.length - 1));
        points.add(StrokePoint(x: o.dx.toInt(), y: o.dy.toInt(), t: t));
      }
      widget.controller.addStroke(Stroke(
        brush: widget.currentBrush,
        color: widget.currentColor,
        width: widget.currentWidth,
        points: points,
      ));
      _inProgressPoints.clear();
    }
    setState(() {});
  }

  void _eraseAt(Offset position) {
    final dpr = MediaQuery.of(context).devicePixelRatio;
    final radiusPx = _eraserRadiusDp * dpr;
    final visibleStrokes = widget.controller.strokes
        .where((s) => !_erasingHidden.contains(s))
        .toList();
    final hits =
        StrokeEraser.hitTest(position.dx, position.dy, radiusPx, visibleStrokes);
    if (hits.isEmpty) return;
    _erasingHidden.addAll(hits);
  }
}
