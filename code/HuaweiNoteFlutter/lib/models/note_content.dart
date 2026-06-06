import 'dart:convert';
import 'stroke.dart';

class NoteContent {
  final Map<String, dynamic> documentJson;
  final List<Stroke> handwriting;

  const NoteContent({required this.documentJson, required this.handwriting});

  factory NoteContent.empty() => NoteContent(
    documentJson: _emptyDocument(),
    handwriting: const [],
  );

  static Map<String, dynamic> _emptyDocument() => {
    'document': {
      'type': 'page',
      'children': [
        {
          'type': 'paragraph',
          'data': {'delta': []},
        },
      ],
    },
  };

  String toPlainText() {
    final buf = StringBuffer();
    final doc = documentJson['document'] as Map<String, dynamic>?;
    if (doc == null) return '';
    final children = doc['children'] as List<dynamic>? ?? [];
    for (final node in children) {
      _extractText(node as Map<String, dynamic>, buf);
    }
    return buf.toString().trim();
  }

  static void _extractText(Map<String, dynamic> node, StringBuffer buf) {
    final data = node['data'] as Map<String, dynamic>?;
    if (data != null) {
      final delta = data['delta'] as List<dynamic>?;
      if (delta != null) {
        for (final op in delta) {
          final insert = (op as Map<String, dynamic>)['insert'];
          if (insert is String) buf.write(insert);
        }
        if (buf.isNotEmpty && !buf.toString().endsWith('\n')) {
          buf.write('\n');
        }
      }
    }
    final children = node['children'] as List<dynamic>?;
    if (children != null) {
      for (final child in children) {
        _extractText(child as Map<String, dynamic>, buf);
      }
    }
  }

  String toJson() => jsonEncode({
    ...documentJson,
    'handwriting': {
      'strokes': handwriting.map((s) => s.toJson()).toList(),
    },
  });

  static NoteContent fromJson(String s) {
    try {
      final root = jsonDecode(s) as Map<String, dynamic>;
      final hw = root.remove('handwriting') as Map<String, dynamic>? ?? {};
      final strokesJson = hw['strokes'] as List<dynamic>? ?? [];
      final strokes = strokesJson
          .map((s) => Stroke.fromJson(s as Map<String, dynamic>))
          .whereType<Stroke>()
          .toList();

      final docJson = root.containsKey('document')
          ? root
          : _emptyDocument();

      return NoteContent(documentJson: docJson, handwriting: strokes);
    } catch (_) {
      return NoteContent.empty();
    }
  }

  bool get isDocumentEmpty {
    final doc = documentJson['document'] as Map<String, dynamic>?;
    if (doc == null) return true;
    final children = doc['children'] as List<dynamic>? ?? [];
    if (children.isEmpty) return true;
    return toPlainText().isEmpty;
  }
}
