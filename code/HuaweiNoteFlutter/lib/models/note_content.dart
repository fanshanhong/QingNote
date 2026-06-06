import 'dart:convert';
import 'block.dart';
import 'stroke.dart';

class NoteContent {
  final List<Block> blocks;
  final List<Stroke> handwriting;

  const NoteContent({required this.blocks, required this.handwriting});

  factory NoteContent.empty() => const NoteContent(blocks: [], handwriting: []);

  String toPlainText() {
    final parts = <String>[];
    for (final b in blocks) {
      switch (b) {
        case TextBlock():
          if (b.text.isNotEmpty) parts.add(b.text);
        case ChecklistBlock():
          for (final item in b.items) {
            if (item.text.isNotEmpty) parts.add(item.text);
          }
        case ImageBlock():
          break;
        case AudioBlock():
          break;
      }
    }
    return parts.join('\n');
  }

  String toJson() {
    final root = <String, dynamic>{
      'blocks': blocks.map((b) => b.toJson()).toList(),
      'handwriting': {
        'strokes': handwriting.map((s) => s.toJson()).toList(),
      },
    };
    return jsonEncode(root);
  }

  static NoteContent fromJson(String s) {
    try {
      final root = jsonDecode(s) as Map<String, dynamic>;
      final blocksJson = root['blocks'] as List<dynamic>? ?? [];
      final blocks = blocksJson
          .map((b) => Block.fromJson(b as Map<String, dynamic>))
          .whereType<Block>()
          .toList();
      final hw = root['handwriting'] as Map<String, dynamic>? ?? {};
      final strokesJson = hw['strokes'] as List<dynamic>? ?? [];
      final strokes = strokesJson
          .map((s) => Stroke.fromJson(s as Map<String, dynamic>))
          .whereType<Stroke>()
          .toList();
      return NoteContent(blocks: blocks, handwriting: strokes);
    } catch (_) {
      return NoteContent.empty();
    }
  }
}
