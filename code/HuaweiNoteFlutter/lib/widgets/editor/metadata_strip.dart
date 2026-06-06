import 'package:flutter/material.dart';
import '../../theme.dart';
import '../../utils/date_utils.dart';

class MetadataStrip extends StatelessWidget {
  final int? updatedAt;
  final String? categoryName;
  final VoidCallback? onCategoryTap;

  const MetadataStrip({
    super.key,
    this.updatedAt,
    this.categoryName,
    this.onCategoryTap,
  });

  @override
  Widget build(BuildContext context) {
    final parts = <InlineSpan>[];

    if (updatedAt != null && updatedAt! > 0) {
      parts.add(TextSpan(
        text: AppDateUtils.timeAgo(updatedAt!),
        style: const TextStyle(
          fontSize: AppDimens.textCaption,
          color: AppColors.textHint,
        ),
      ));
    }

    if (categoryName != null) {
      if (parts.isNotEmpty) {
        parts.add(const TextSpan(
          text: ' · ',
          style: TextStyle(
            fontSize: AppDimens.textCaption,
            color: AppColors.textHint,
          ),
        ));
      }
      parts.add(WidgetSpan(
        alignment: PlaceholderAlignment.middle,
        child: GestureDetector(
          onTap: onCategoryTap,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.circle_outlined, size: 10, color: AppColors.textHint),
              const SizedBox(width: 2),
              Text(
                categoryName!,
                style: const TextStyle(
                  fontSize: AppDimens.textCaption,
                  color: AppColors.textHint,
                ),
              ),
            ],
          ),
        ),
      ));
    }

    if (parts.isEmpty) return const SizedBox.shrink();

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppDimens.editorContentPadding,
      ),
      child: Align(
        alignment: Alignment.centerLeft,
        child: RichText(text: TextSpan(children: parts)),
      ),
    );
  }
}
