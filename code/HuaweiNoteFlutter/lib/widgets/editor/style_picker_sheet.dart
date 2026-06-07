import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import '../../theme.dart';

Future<void> showStylePickerSheet(
  BuildContext context, {
  required EditorState editorState,
  Selection? savedSelection,
  required ValueChanged<String> onBackgroundChanged,
}) async {
  await showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (ctx) => _StylePickerContent(
      editorState: editorState,
      savedSelection: savedSelection,
      onBackgroundChanged: onBackgroundChanged,
    ),
  );
}

class _StylePickerContent extends StatefulWidget {
  final EditorState editorState;
  final Selection? savedSelection;
  final ValueChanged<String> onBackgroundChanged;

  const _StylePickerContent({
    required this.editorState,
    this.savedSelection,
    required this.onBackgroundChanged,
  });

  @override
  State<_StylePickerContent> createState() => _StylePickerContentState();
}

class _StylePickerContentState extends State<_StylePickerContent> {
  int _fontSizeIndex = 2;
  String _selectedBg = 'plain';

  static const _fontSizes = [12.0, 14.0, 16.0, 20.0, 24.0];
  static const _fontSizeLabels = ['小', '标准', '中', '大', '特大'];

  static const _textColors = [
    ('黑', Color(0xFF000000)),
    ('红', Color(0xFFFF0000)),
    ('橙', Color(0xFFFF8000)),
    ('黄', Color(0xFFFFCC00)),
    ('绿', Color(0xFF00CC00)),
    ('蓝', Color(0xFF0066FF)),
    ('紫', Color(0xFF9900CC)),
  ];

  static const _backgrounds = [
    ('纯白', 'plain', Colors.white),
    ('亚麻', 'linen', AppColors.bgLinen),
    ('牛皮', 'kraft', AppColors.bgKraft),
    ('网格', 'grid', AppColors.bgGrid),
  ];

  void _ensureSelection() {
    if (widget.editorState.selection == null && widget.savedSelection != null) {
      widget.editorState.updateSelectionWithReason(
        widget.savedSelection,
        reason: SelectionUpdateReason.uiEvent,
      );
    }
  }

  Selection? get _activeSelection =>
      widget.editorState.selection ?? widget.savedSelection;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 36, height: 4,
                decoration: BoxDecoration(
                  color: AppColors.divider,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            _buildFormatRow(),
            const SizedBox(height: 12),
            _buildListRow(),
            const SizedBox(height: 12),
            _buildFontSizeRow(),
            const SizedBox(height: 12),
            _buildTextColorRow(),
            const SizedBox(height: 12),
            _buildHeadingRow(),
            const SizedBox(height: 12),
            _buildBackgroundRow(),
          ],
        ),
      ),
    );
  }

  Widget _buildFormatRow() {
    return Row(
      children: [
        _FormatToggleButton(
          label: 'B',
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
          onTap: () { _ensureSelection(); widget.editorState.toggleAttribute(AppFlowyRichTextKeys.bold); },
        ),
        _FormatToggleButton(
          label: 'I',
          style: const TextStyle(fontStyle: FontStyle.italic, fontSize: 18),
          onTap: () { _ensureSelection(); widget.editorState.toggleAttribute(AppFlowyRichTextKeys.italic); },
        ),
        _FormatToggleButton(
          label: 'U',
          style: const TextStyle(decoration: TextDecoration.underline, fontSize: 18),
          onTap: () { _ensureSelection(); widget.editorState.toggleAttribute(AppFlowyRichTextKeys.underline); },
        ),
        _FormatToggleButton(
          label: 'S',
          style: const TextStyle(decoration: TextDecoration.lineThrough, fontSize: 18),
          onTap: () { _ensureSelection(); widget.editorState.toggleAttribute(AppFlowyRichTextKeys.strikethrough); },
        ),
        const Spacer(),
        _AlignButton(
          icon: Icons.format_align_left,
          onTap: () => _setAlign('left'),
        ),
        _AlignButton(
          icon: Icons.format_align_center,
          onTap: () => _setAlign('center'),
        ),
        _AlignButton(
          icon: Icons.format_align_right,
          onTap: () => _setAlign('right'),
        ),
      ],
    );
  }

  Widget _buildListRow() {
    return Row(
      children: [
        _ActionButton(
          icon: Icons.format_indent_increase,
          label: '缩进+',
          onTap: _indent,
        ),
        _ActionButton(
          icon: Icons.format_indent_decrease,
          label: '缩进-',
          onTap: _outdent,
        ),
        const SizedBox(width: 8),
        _ActionButton(
          icon: Icons.format_list_bulleted,
          label: '无序',
          onTap: () => _toggleBlockType(BulletedListBlockKeys.type),
        ),
        _ActionButton(
          icon: Icons.format_list_numbered,
          label: '有序',
          onTap: () => _toggleBlockType(NumberedListBlockKeys.type),
        ),
        _ActionButton(
          icon: Icons.checklist,
          label: '清单',
          onTap: () => _toggleBlockType(TodoListBlockKeys.type),
        ),
        _ActionButton(
          icon: Icons.format_quote,
          label: '引用',
          onTap: () => _toggleBlockType(QuoteBlockKeys.type),
        ),
      ],
    );
  }

  Widget _buildFontSizeRow() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('字号', style: TextStyle(
          fontSize: AppDimens.textCaption,
          color: AppColors.textSecondary,
        )),
        const SizedBox(height: 4),
        Row(
          children: List.generate(_fontSizes.length, (i) {
            final isSelected = i == _fontSizeIndex;
            return Expanded(
              child: GestureDetector(
                onTap: () {
                  setState(() => _fontSizeIndex = i);
                  _applyFontSize(_fontSizes[i]);
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  decoration: BoxDecoration(
                    color: isSelected ? AppColors.primary.withAlpha(25) : null,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Center(
                    child: Text(
                      _fontSizeLabels[i],
                      style: TextStyle(
                        fontSize: 13,
                        color: isSelected ? AppColors.primary : AppColors.textSecondary,
                        fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
                      ),
                    ),
                  ),
                ),
              ),
            );
          }),
        ),
      ],
    );
  }

  Widget _buildTextColorRow() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: _textColors.map((entry) {
        final (label, color) = entry;
        return GestureDetector(
          onTap: () => _applyTextColor(color),
          child: Column(
            children: [
              Container(
                width: 28, height: 28,
                decoration: BoxDecoration(
                  color: color,
                  shape: BoxShape.circle,
                  border: color == const Color(0xFF000000)
                    ? null
                    : Border.all(color: AppColors.divider, width: 0.5),
                ),
              ),
              const SizedBox(height: 2),
              Text(label, style: const TextStyle(
                fontSize: 10, color: AppColors.textHint,
              )),
            ],
          ),
        );
      }).toList(),
    );
  }

  Widget _buildHeadingRow() {
    return Row(
      children: List.generate(6, (i) {
        final level = i + 1;
        return Expanded(
          child: GestureDetector(
            onTap: () => _toggleHeading(level),
            child: Container(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Center(
                child: Text(
                  'H$level',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.styleSheetHeader,
                  ),
                ),
              ),
            ),
          ),
        );
      }),
    );
  }

  Widget _buildBackgroundRow() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('背景纹理', style: TextStyle(
          fontSize: AppDimens.textCaption,
          color: AppColors.textSecondary,
        )),
        const SizedBox(height: 4),
        Row(
          children: _backgrounds.map((entry) {
            final (label, key, color) = entry;
            final isSelected = _selectedBg == key;
            return Expanded(
              child: GestureDetector(
                onTap: () {
                  setState(() => _selectedBg = key);
                  widget.onBackgroundChanged(key);
                },
                child: Container(
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  decoration: BoxDecoration(
                    color: color,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: isSelected ? AppColors.primary : AppColors.divider,
                      width: isSelected ? 2 : 0.5,
                    ),
                  ),
                  child: Center(
                    child: Text(label, style: TextStyle(
                      fontSize: 12,
                      color: isSelected ? AppColors.primary : AppColors.textSecondary,
                    )),
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ],
    );
  }

  void _setAlign(String align) {
    _ensureSelection();
    final es = widget.editorState;
    final selection = _activeSelection;
    if (selection == null) return;
    final nodes = es.getNodesInSelection(selection);
    final transaction = es.transaction;
    for (final node in nodes) {
      transaction.updateNode(node, {blockComponentAlign: align});
    }
    transaction.afterSelection = selection;
    es.apply(transaction);
  }

  void _indent() {
    _ensureSelection();
    indentCommand.handler(widget.editorState);
  }

  void _outdent() {
    _ensureSelection();
    outdentCommand.handler(widget.editorState);
  }

  void _toggleBlockType(String type) {
    _ensureSelection();
    final es = widget.editorState;
    final selection = _activeSelection;
    if (selection == null) return;
    final nodes = es.getNodesInSelection(selection);
    if (nodes.isEmpty) return;

    final transaction = es.transaction;
    for (final node in nodes) {
      if (node.type == type) {
        final newNode = paragraphNode(
          delta: node.delta,
        );
        transaction
          ..insertNode(node.path, newNode)
          ..deleteNode(node);
      } else {
        Node newNode;
        switch (type) {
          case 'bulleted_list':
            newNode = bulletedListNode(delta: node.delta);
          case 'numbered_list':
            newNode = numberedListNode(delta: node.delta);
          case 'todo_list':
            newNode = todoListNode(checked: false, delta: node.delta);
          case 'quote':
            newNode = quoteNode(delta: node.delta);
          default:
            newNode = paragraphNode(delta: node.delta);
        }
        transaction
          ..insertNode(node.path, newNode)
          ..deleteNode(node);
      }
    }
    transaction.afterSelection = selection;
    es.apply(transaction);
  }

  void _toggleHeading(int level) {
    _ensureSelection();
    final es = widget.editorState;
    final selection = _activeSelection;
    if (selection == null) return;
    final nodes = es.getNodesInSelection(selection);
    if (nodes.isEmpty) return;

    final transaction = es.transaction;
    for (final node in nodes) {
      if (node.type == HeadingBlockKeys.type &&
          node.attributes[HeadingBlockKeys.level] == level) {
        final newNode = paragraphNode(delta: node.delta);
        transaction
          ..insertNode(node.path, newNode)
          ..deleteNode(node);
      } else {
        final newNode = headingNode(level: level, delta: node.delta);
        transaction
          ..insertNode(node.path, newNode)
          ..deleteNode(node);
      }
    }
    transaction.afterSelection = selection;
    es.apply(transaction);
  }

  void _applyFontSize(double size) {
    _ensureSelection();
    final es = widget.editorState;
    final selection = _activeSelection;
    if (selection == null) return;
    es.formatDelta(selection, {AppFlowyRichTextKeys.fontSize: size});
  }

  void _applyTextColor(Color color) {
    _ensureSelection();
    final es = widget.editorState;
    final selection = _activeSelection;
    if (selection == null) return;
    final a = (color.a * 255).round();
    final r = (color.r * 255).round();
    final g = (color.g * 255).round();
    final b = (color.b * 255).round();
    final hex = '#${((a << 24) | (r << 16) | (g << 8) | b).toRadixString(16).padLeft(8, '0')}';
    es.formatDelta(selection, {AppFlowyRichTextKeys.textColor: hex});
  }
}

class _FormatToggleButton extends StatelessWidget {
  final String label;
  final TextStyle style;
  final VoidCallback onTap;

  const _FormatToggleButton({
    required this.label,
    required this.style,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 40, height: 40,
        alignment: Alignment.center,
        child: Text(label, style: style),
      ),
    );
  }
}

class _AlignButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const _AlignButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      icon: Icon(icon, size: 20, color: AppColors.editorIconActive),
      onPressed: onTap,
      constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
      padding: EdgeInsets.zero,
    );
  }
}

class _ActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _ActionButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: Column(
          children: [
            Icon(icon, size: 20, color: AppColors.editorIconActive),
            const SizedBox(height: 2),
            Text(label, style: const TextStyle(
              fontSize: 10, color: AppColors.textSecondary,
            )),
          ],
        ),
      ),
    );
  }
}
