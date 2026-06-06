import 'package:flutter/foundation.dart';
import '../../models/stroke.dart';

class IndexedStroke {
  final int index;
  final Stroke stroke;
  const IndexedStroke(this.index, this.stroke);
}

sealed class HandwritingAction {}

class AddAction extends HandwritingAction {
  final Stroke stroke;
  AddAction(this.stroke);
}

class EraseAction extends HandwritingAction {
  final List<IndexedStroke> items;
  EraseAction(this.items);
}

class HandwritingOverlayController extends ChangeNotifier {
  final List<Stroke> _strokes = [];
  final List<HandwritingAction> _undoStack = [];
  final List<HandwritingAction> _redoStack = [];

  List<Stroke> get strokes => List.unmodifiable(_strokes);
  bool get canUndo => _undoStack.isNotEmpty;
  bool get canRedo => _redoStack.isNotEmpty;

  void setStrokes(List<Stroke> list) {
    _strokes.clear();
    _strokes.addAll(list);
    _undoStack.clear();
    _redoStack.clear();
    notifyListeners();
  }

  void addStroke(Stroke stroke) {
    _strokes.add(stroke);
    _undoStack.add(AddAction(stroke));
    _redoStack.clear();
    notifyListeners();
  }

  void eraseStrokes(List<IndexedStroke> items) {
    if (items.isEmpty) return;
    final toRemove = items.map((e) => e.stroke).toSet();
    _strokes.removeWhere(toRemove.contains);
    _undoStack.add(EraseAction(items));
    _redoStack.clear();
    notifyListeners();
  }

  void undo() {
    if (_undoStack.isEmpty) return;
    final action = _undoStack.removeLast();
    switch (action) {
      case AddAction(:final stroke):
        _strokes.remove(stroke);
      case EraseAction(:final items):
        final sorted = List<IndexedStroke>.from(items)
          ..sort((a, b) => a.index.compareTo(b.index));
        for (final item in sorted) {
          final idx = item.index.clamp(0, _strokes.length);
          _strokes.insert(idx, item.stroke);
        }
    }
    _redoStack.add(action);
    notifyListeners();
  }

  void redo() {
    if (_redoStack.isEmpty) return;
    final action = _redoStack.removeLast();
    switch (action) {
      case AddAction(:final stroke):
        _strokes.add(stroke);
      case EraseAction(:final items):
        final toRemove = items.map((e) => e.stroke).toSet();
        _strokes.removeWhere(toRemove.contains);
    }
    _undoStack.add(action);
    notifyListeners();
  }

  void clear() {
    if (_strokes.isEmpty) return;
    final items = _strokes
        .asMap()
        .entries
        .map((e) => IndexedStroke(e.key, e.value))
        .toList();
    _strokes.clear();
    _undoStack.add(EraseAction(items));
    _redoStack.clear();
    notifyListeners();
  }
}
