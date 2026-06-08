import 'package:flutter/material.dart';
import '../../models/stroke.dart';
import '../../theme.dart';

const _kColors = [
  '#212121',
  '#E53935',
  '#FB8C00',
  '#FDD835',
  '#43A047',
  '#00897B',
  '#1E88E5',
  '#8E24AA',
];

void showHandwritingStylePickerSheet(
  BuildContext context, {
  required BrushType currentBrush,
  required String currentColor,
  required int currentWidth,
  required ValueChanged<BrushType> onBrushSelected,
  required ValueChanged<String> onColorSelected,
  required ValueChanged<int> onWidthSelected,
}) {
  showModalBottomSheet(
    context: context,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (ctx) => _Content(
      currentBrush: currentBrush,
      currentColor: currentColor,
      currentWidth: currentWidth,
      onBrushSelected: onBrushSelected,
      onColorSelected: onColorSelected,
      onWidthSelected: onWidthSelected,
    ),
  );
}

class _Content extends StatefulWidget {
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;
  final ValueChanged<BrushType> onBrushSelected;
  final ValueChanged<String> onColorSelected;
  final ValueChanged<int> onWidthSelected;

  const _Content({
    required this.currentBrush,
    required this.currentColor,
    required this.currentWidth,
    required this.onBrushSelected,
    required this.onColorSelected,
    required this.onWidthSelected,
  });

  @override
  State<_Content> createState() => _ContentState();
}

class _ContentState extends State<_Content> {
  late BrushType _brush;
  late String _color;
  late int _width;

  @override
  void initState() {
    super.initState();
    _brush = widget.currentBrush;
    _color = widget.currentColor;
    _width = widget.currentWidth;
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppDimens.spacingL,
        vertical: AppDimens.spacingL,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.divider,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: AppDimens.spacingL),
          Text(
            '笔种',
            style: TextStyle(
              fontSize: AppDimens.textBody,
              color: AppColors.styleSheetHeader,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: AppDimens.spacingS),
          _buildBrushRow(),
          const SizedBox(height: AppDimens.spacingL),
          Text(
            '颜色',
            style: TextStyle(
              fontSize: AppDimens.textBody,
              color: AppColors.styleSheetHeader,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: AppDimens.spacingS),
          _buildColorRow(),
          const SizedBox(height: AppDimens.spacingL),
          Text(
            '粗细',
            style: TextStyle(
              fontSize: AppDimens.textBody,
              color: AppColors.styleSheetHeader,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: AppDimens.spacingS),
          _buildWidthRow(),
          const SizedBox(height: AppDimens.spacingL),
        ],
      ),
    );
  }

  Widget _buildBrushRow() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.start,
      children: [
        _buildBrushItem(BrushType.pen, Icons.edit, '钢笔'),
        const SizedBox(width: AppDimens.spacingL),
        _buildBrushItem(BrushType.brush, Icons.brush, '画笔'),
        const SizedBox(width: AppDimens.spacingL),
        _buildBrushItem(BrushType.pencil, Icons.create, '铅笔'),
        const SizedBox(width: AppDimens.spacingL),
        _buildBrushItem(BrushType.marker, Icons.highlight, '马克笔'),
      ],
    );
  }

  Widget _buildBrushItem(BrushType type, IconData icon, String label) {
    final isSelected = type == _brush;
    return GestureDetector(
      onTap: () {
        setState(() => _brush = type);
        widget.onBrushSelected(type);
      },
      child: Column(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: isSelected ? AppColors.primaryLight : Colors.transparent,
              shape: BoxShape.circle,
            ),
            child: Icon(
              icon,
              size: 22,
              color:
                  isSelected ? AppColors.primary : AppColors.editorIconActive,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: AppDimens.textCaption,
              color: isSelected ? AppColors.primary : AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildColorRow() {
    return Wrap(
      spacing: AppDimens.spacingS,
      runSpacing: AppDimens.spacingS,
      children: _kColors.map((hex) {
        final isSelected = hex == _color;
        final color = _parseHex(hex);
        return GestureDetector(
          onTap: () {
            setState(() => _color = hex);
            widget.onColorSelected(hex);
          },
          child: Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
              border: isSelected
                  ? Border.all(color: AppColors.primary, width: 2.5)
                  : null,
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildWidthRow() {
    final color = _parseHex(_color);
    return Row(
      mainAxisAlignment: MainAxisAlignment.start,
      children: [
        _buildWidthDot(1, 8, color),
        const SizedBox(width: 16),
        _buildWidthDot(3, 16, color),
        const SizedBox(width: 16),
        _buildWidthDot(6, 24, color),
      ],
    );
  }

  Widget _buildWidthDot(int widthDp, double dotDiameter, Color color) {
    final isSelected = widthDp == _width;
    return GestureDetector(
      onTap: () {
        setState(() => _width = widthDp);
        widget.onWidthSelected(widthDp);
      },
      child: SizedBox(
        width: 44,
        height: 44,
        child: Center(
          child: Container(
            width: dotDiameter,
            height: dotDiameter,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
              border: isSelected
                  ? Border.all(color: AppColors.primary, width: 2.5)
                  : null,
            ),
          ),
        ),
      ),
    );
  }

  static Color _parseHex(String hex) {
    if (hex.isEmpty) return Colors.black;
    var h = hex.replaceFirst('#', '');
    if (h.length == 6) h = 'FF$h';
    return Color(int.parse(h, radix: 16));
  }
}
