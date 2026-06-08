import 'package:flutter/material.dart';
import '../../theme.dart';
import '../../utils/color_utils.dart';

class NotebookIndicator extends StatelessWidget {
  final String? notebookName;
  final String? notebookColor;
  final bool isEditing;
  final Future<List<({int id, String name, String color})>> Function()
      onLoadNotebooks;
  final ValueChanged<int?> onNotebookSelected;

  const NotebookIndicator({
    super.key,
    this.notebookName,
    this.notebookColor,
    required this.isEditing,
    required this.onLoadNotebooks,
    required this.onNotebookSelected,
  });

  @override
  Widget build(BuildContext context) {
    final color = notebookColor != null
        ? AppColorUtils.parseHex(notebookColor!) ?? AppColors.primary
        : AppColors.primary;
    final name = notebookName ?? '全部笔记';

    return GestureDetector(
      onTap: isEditing ? () => _showNotebookMenu(context) : null,
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: AppDimens.editorContentPadding,
          vertical: AppDimens.spacingS,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(
                color: color,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: AppDimens.spacingXs),
            Text(
              name,
              style: const TextStyle(
                fontSize: AppDimens.textCaption,
                color: AppColors.textSecondary,
              ),
            ),
            if (isEditing) ...[
              const SizedBox(width: 2),
              const Icon(
                Icons.arrow_drop_down,
                size: 16,
                color: AppColors.textSecondary,
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _showNotebookMenu(BuildContext context) async {
    final notebooks = await onLoadNotebooks();
    if (!context.mounted) return;

    final renderBox = context.findRenderObject() as RenderBox;
    final offset = renderBox.localToGlobal(Offset.zero);
    final size = renderBox.size;

    final items = <PopupMenuEntry<int?>>[];
    items.add(const PopupMenuItem<int?>(
      value: null,
      child: Text('全部笔记'),
    ));
    for (final nb in notebooks) {
      final c = AppColorUtils.parseHex(nb.color) ?? AppColors.primary;
      items.add(PopupMenuItem<int?>(
        value: nb.id,
        child: Row(
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(color: c, shape: BoxShape.circle),
            ),
            const SizedBox(width: AppDimens.spacingS),
            Text(nb.name),
          ],
        ),
      ));
    }

    final selected = await showMenu<int?>(
      context: context,
      position: RelativeRect.fromLTRB(
        offset.dx,
        offset.dy + size.height,
        offset.dx + size.width,
        0,
      ),
      items: items,
    );
    if (selected != null || items.length > 1) {
      onNotebookSelected(selected);
    }
  }
}
