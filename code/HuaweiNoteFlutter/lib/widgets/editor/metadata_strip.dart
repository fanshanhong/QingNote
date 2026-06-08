import 'package:flutter/material.dart';
import '../../theme.dart';
import '../../utils/date_utils.dart';

class MetadataStrip extends StatelessWidget {
  final int? updatedAt;
  final String? notebookName;
  final String? notebookColor;
  final String? categoryName;
  final bool isEditing;
  final VoidCallback? onCategoryTap;
  final VoidCallback? onNotebookTap;

  const MetadataStrip({
    super.key,
    this.updatedAt,
    this.notebookName,
    this.notebookColor,
    this.categoryName,
    this.isEditing = false,
    this.onCategoryTap,
    this.onNotebookTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppDimens.editorContentPadding,
      ),
      child: Row(
        children: [
          if (updatedAt != null && updatedAt! > 0)
            Text(
              AppDateUtils.timeAgo(updatedAt!),
              style: const TextStyle(
                fontSize: AppDimens.textCaption,
                color: AppColors.textHint,
              ),
            ),
          const Spacer(),
          GestureDetector(
            onTap: isEditing ? onNotebookTap : null,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.library_books_outlined,
                    size: 14, color: AppColors.primary),
                const SizedBox(width: 4),
                Text(
                  _displayName(),
                  style: const TextStyle(
                    fontSize: AppDimens.textCaption,
                    color: AppColors.primary,
                  ),
                ),
                if (isEditing)
                  const Icon(Icons.arrow_drop_down,
                      size: 16, color: AppColors.primary),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _displayName() {
    if (notebookName != null) return notebookName!;
    if (categoryName != null) return categoryName!;
    return '未分类';
  }
}
