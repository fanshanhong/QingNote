import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import '../../theme.dart';

class StylePickerPanel extends StatefulWidget {
  final EditorState editorState;
  final ValueChanged<String> onBackgroundChanged;
  final VoidCallback onClose;

  const StylePickerPanel({
    super.key,
    required this.editorState,
    required this.onBackgroundChanged,
    required this.onClose,
  });

  @override
  State<StylePickerPanel> createState() => _StylePickerPanelState();
}

class _StylePickerPanelState extends State<StylePickerPanel> {
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

  EditorState get _es => widget.editorState;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: AppColors.divider, width: 0.5)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeader(),
                const SizedBox(height: 12),
                _buildFormatRow(),
                const SizedBox(height: 12),
                _buildListRow(),
                const SizedBox(height: 12),
                _buildFontSizeRow(),
                const SizedBox(height: 12),
                _buildTextColorRow(),
                const SizedBox(height: 12),
                _buildBackgroundRow(),
                const SizedBox(height: 4),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Row(
      children: [
        const Text('样式', style: TextStyle(
          fontSize: 16, fontWeight: FontWeight.w500,
          color: AppColors.textPrimary,
        )),
        const Spacer(),
        GestureDetector(
          onTap: widget.onClose,
          child: const Icon(Icons.close, size: 22, color: AppColors.textSecondary),
        ),
      ],
    );
  }

  Widget _buildFormatRow() {
    return Row(
      children: [
        _FormatToggleButton(
          label: 'B',
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
          onTap: () => _es.toggleAttribute(AppFlowyRichTextKeys.bold),
        ),
        _FormatToggleButton(
          label: 'I',
          style: const TextStyle(fontStyle: FontStyle.italic, fontSize: 18),
          onTap: () => _es.toggleAttribute(AppFlowyRichTextKeys.italic),
        ),
        _FormatToggleButton(
          label: 'U',
          style: const TextStyle(decoration: TextDecoration.underline, fontSize: 18),
          onTap: () => _es.toggleAttribute(AppFlowyRichTextKeys.underline),
        ),
        _FormatToggleButton(
          label: 'S',
          style: const TextStyle(decoration: TextDecoration.lineThrough, fontSize: 18),
          onTap: () => _es.toggleAttribute(AppFlowyRichTextKeys.strikethrough),
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
    final selection = _es.selection;
    if (selection == null) return;
    final nodes = _es.getNodesInSelection(selection);
    final transaction = _es.transaction;
    for (final node in nodes) {
      transaction.updateNode(node, {blockComponentAlign: align});
    }
    transaction.afterSelection = selection;
    _es.apply(transaction);
  }

  void _indent() {
    indentCommand.handler(_es);
  }

  void _outdent() {
    outdentCommand.handler(_es);
  }

  void _toggleBlockType(String type) {
    final selection = _es.selection;
    if (selection == null) return;
    final nodes = _es.getNodesInSelection(selection);
    if (nodes.isEmpty) return;

    final transaction = _es.transaction;
    for (final node in nodes) {
      if (node.type == type) {
        final newNode = paragraphNode(delta: node.delta);
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
    _es.apply(transaction);
  }

  void _applyFontSize(double size) {
    final selection = _es.selection;
    if (selection == null) return;
    if (selection.isCollapsed) {
      _es.updateToggledStyle(AppFlowyRichTextKeys.fontSize, size);
    } else {
      _es.formatDelta(selection, {AppFlowyRichTextKeys.fontSize: size});
    }
  }

  void _applyTextColor(Color color) {
    final selection = _es.selection;
    if (selection == null) return;
    final a = (color.a * 255).round();
    final r = (color.r * 255).round();
    final g = (color.g * 255).round();
    final b = (color.b * 255).round();
    final hex = '#${((a << 24) | (r << 16) | (g << 8) | b).toRadixString(16).padLeft(8, '0')}';
    if (selection.isCollapsed) {
      _es.updateToggledStyle(AppFlowyRichTextKeys.textColor, hex);
    } else {
      _es.formatDelta(selection, {AppFlowyRichTextKeys.textColor: hex});
    }
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
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 36, height: 36,
        alignment: Alignment.center,
        child: Icon(icon, size: 20, color: AppColors.editorIconActive),
      ),
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
