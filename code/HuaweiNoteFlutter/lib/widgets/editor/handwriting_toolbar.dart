import 'package:flutter/material.dart';
import '../../models/stroke.dart';
import '../../theme.dart';

class HandwritingToolbar extends StatelessWidget {
  final BrushType currentBrush;
  final String currentColor;
  final int currentWidth;
  final bool isErasing;
  final ValueChanged<BrushType> onBrushSelected;
  final VoidCallback onColorTap;
  final ValueChanged<BrushType> onBrushWidthTap;
  final VoidCallback onEraserTap;

  const HandwritingToolbar({
    super.key,
    required this.currentBrush,
    required this.currentColor,
    required this.currentWidth,
    required this.isErasing,
    required this.onBrushSelected,
    required this.onColorTap,
    required this.onBrushWidthTap,
    required this.onEraserTap,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: AppDimens.editorToolbarHeight,
      decoration: const BoxDecoration(
        color: AppColors.editorToolbarBg,
        border: Border(top: BorderSide(color: AppColors.divider, width: 0.5)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _ColorButton(color: currentColor, onTap: onColorTap),
          _BrushButton(
            icon: Icons.edit,
            isSelected: !isErasing && currentBrush == BrushType.pen,
            onTap: () => _handleBrushTap(BrushType.pen),
          ),
          _BrushButton(
            icon: Icons.brush,
            isSelected: !isErasing && currentBrush == BrushType.brush,
            onTap: () => _handleBrushTap(BrushType.brush),
          ),
          _BrushButton(
            icon: Icons.create,
            isSelected: !isErasing && currentBrush == BrushType.pencil,
            onTap: () => _handleBrushTap(BrushType.pencil),
          ),
          _BrushButton(
            icon: Icons.highlight,
            isSelected: !isErasing && currentBrush == BrushType.marker,
            onTap: () => _handleBrushTap(BrushType.marker),
          ),
          _BrushButton(
            icon: Icons.auto_fix_normal,
            isSelected: isErasing,
            onTap: onEraserTap,
          ),
        ],
      ),
    );
  }

  void _handleBrushTap(BrushType type) {
    if (!isErasing && currentBrush == type) {
      onBrushWidthTap(type);
    } else {
      onBrushSelected(type);
    }
  }
}

class _ColorButton extends StatelessWidget {
  final String color;
  final VoidCallback onTap;

  const _ColorButton({required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: SizedBox(
        width: 44,
        height: 44,
        child: Center(
          child: Container(
            width: 22,
            height: 22,
            decoration: BoxDecoration(
              color: _parseHex(color),
              shape: BoxShape.circle,
              border: Border.all(color: AppColors.divider, width: 1),
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

class _BrushButton extends StatelessWidget {
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;

  const _BrushButton({
    required this.icon,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: SizedBox(
        width: 44,
        height: 44,
        child: Center(
          child: Icon(
            icon,
            size: 22,
            color: isSelected ? AppColors.primary : AppColors.editorIconActive,
          ),
        ),
      ),
    );
  }
}
