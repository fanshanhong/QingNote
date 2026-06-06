import 'package:flutter/material.dart';
import '../../theme.dart';

class BrushWidthPicker extends StatelessWidget {
  final int currentWidth;
  final String brushColor;
  final ValueChanged<int> onWidthSelected;

  const BrushWidthPicker({
    super.key,
    required this.currentWidth,
    required this.brushColor,
    required this.onWidthSelected,
  });

  @override
  Widget build(BuildContext context) {
    final color = _parseHex(brushColor);
    return Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        _buildDot(1, 8, color),
        const SizedBox(width: 16),
        _buildDot(3, 16, color),
        const SizedBox(width: 16),
        _buildDot(6, 24, color),
      ],
    );
  }

  Widget _buildDot(int widthDp, double dotDiameter, Color color) {
    final isSelected = widthDp == currentWidth;
    return GestureDetector(
      onTap: () => onWidthSelected(widthDp),
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
