import 'text_span.dart';

enum Heading {
  h1, h2, h3, h4, h5, h6;

  static Heading? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
}

enum NoteAlignment {
  start, center, end;

  static NoteAlignment? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
}

enum ListType {
  bullet, hollowBullet, numbered, lettered;

  static ListType? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
}

sealed class Block {
  String get id;
  Map<String, dynamic> toJson();

  static Block? fromJson(Map<String, dynamic> json) {
    final type = json['type'] as String? ?? '';
    return switch (type) {
      'text' => TextBlock.fromJson(json),
      'image' => ImageBlock.fromJson(json),
      'checklist' => ChecklistBlock.fromJson(json),
      'audio' => AudioBlock.fromJson(json),
      _ => null,
    };
  }
}

class TextBlock extends Block {
  @override
  final String id;
  final Heading? heading;
  final String text;
  final List<NoteTextSpan> spans;
  final NoteAlignment? alignment;
  final ListType? listType;
  final int indentLevel;

  TextBlock({
    required this.id,
    this.heading,
    this.text = '',
    this.spans = const [],
    this.alignment,
    this.listType,
    this.indentLevel = 0,
  });

  @override
  Map<String, dynamic> toJson() => {
    'type': 'text',
    'id': id,
    'text': text,
    'spans': spans.map((s) => s.toJson()).toList(),
    if (heading != null) 'heading': heading!.name,
    if (alignment != null) 'alignment': alignment!.name,
    if (listType != null) 'listType': listType!.name,
    if (indentLevel > 0) 'indentLevel': indentLevel,
  };

  static TextBlock fromJson(Map<String, dynamic> json) {
    final spansList = (json['spans'] as List<dynamic>?)
        ?.map((s) => NoteTextSpan.fromJson(s as Map<String, dynamic>))
        .whereType<NoteTextSpan>()
        .toList() ?? [];
    return TextBlock(
      id: json['id'] as String? ?? '',
      heading: Heading.fromName(json['heading'] as String? ?? ''),
      text: json['text'] as String? ?? '',
      spans: spansList,
      alignment: NoteAlignment.fromName(json['alignment'] as String? ?? ''),
      listType: ListType.fromName(json['listType'] as String? ?? ''),
      indentLevel: json['indentLevel'] as int? ?? 0,
    );
  }
}

class ImageBlock extends Block {
  @override
  final String id;
  final String fileName;
  final int width;
  final int height;

  ImageBlock({
    required this.id,
    required this.fileName,
    required this.width,
    required this.height,
  });

  @override
  Map<String, dynamic> toJson() => {
    'type': 'image',
    'id': id,
    'fileName': fileName,
    'width': width,
    'height': height,
  };

  static ImageBlock fromJson(Map<String, dynamic> json) => ImageBlock(
    id: json['id'] as String? ?? '',
    fileName: json['fileName'] as String? ?? '',
    width: json['width'] as int? ?? 0,
    height: json['height'] as int? ?? 0,
  );
}

class ChecklistItem {
  final bool checked;
  final String text;

  const ChecklistItem({required this.checked, required this.text});

  Map<String, dynamic> toJson() => {'checked': checked, 'text': text};

  static ChecklistItem fromJson(Map<String, dynamic> json) => ChecklistItem(
    checked: json['checked'] as bool? ?? false,
    text: json['text'] as String? ?? '',
  );
}

class ChecklistBlock extends Block {
  @override
  final String id;
  final List<ChecklistItem> items;

  ChecklistBlock({required this.id, this.items = const []});

  @override
  Map<String, dynamic> toJson() => {
    'type': 'checklist',
    'id': id,
    'items': items.map((i) => i.toJson()).toList(),
  };

  static ChecklistBlock fromJson(Map<String, dynamic> json) {
    final itemsList = (json['items'] as List<dynamic>?)
        ?.map((i) => ChecklistItem.fromJson(i as Map<String, dynamic>))
        .toList() ?? [];
    return ChecklistBlock(id: json['id'] as String? ?? '', items: itemsList);
  }
}

class AudioBlock extends Block {
  @override
  final String id;
  final String fileName;
  final int durationMs;

  AudioBlock({
    required this.id,
    required this.fileName,
    required this.durationMs,
  });

  @override
  Map<String, dynamic> toJson() => {
    'type': 'audio',
    'id': id,
    'fileName': fileName,
    'durationMs': durationMs,
  };

  static AudioBlock fromJson(Map<String, dynamic> json) => AudioBlock(
    id: json['id'] as String? ?? '',
    fileName: json['fileName'] as String? ?? '',
    durationMs: json['durationMs'] as int? ?? 0,
  );
}
