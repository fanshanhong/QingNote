import 'package:flutter/material.dart';
import '../models/note.dart';
import '../theme.dart';
import '../utils/color_utils.dart';
import '../utils/date_utils.dart';
import '../utils/text_utils.dart';

class NoteCard extends StatelessWidget {
  final Note note;
  final bool isBatchMode;
  final bool isSelected;
  final String? notebookColor;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final ValueChanged<bool> onBatchToggle;

  const NoteCard({
    super.key,
    required this.note,
    required this.isBatchMode,
    required this.isSelected,
    this.notebookColor,
    required this.onTap,
    required this.onLongPress,
    required this.onBatchToggle,
  });

  Color _cardBackground() {
    if (note.background != 'plain') {
      return switch (note.background) {
        'linen' => AppColors.bgLinen,
        'kraft' => AppColors.bgKraft,
        'grid' => AppColors.bgGrid,
        _ => AppColors.bgCard,
      };
    }
    if (notebookColor != null) {
      final c = AppColorUtils.parseHex(notebookColor!);
      if (c != null) return AppColorUtils.tint(c, 20);
    }
    return AppColors.bgCard;
  }

  @override
  Widget build(BuildContext context) {
    final title = AppTextUtils.isBlankTitle(note.title) ? '无标题' : note.title;
    final summaryText = AppTextUtils.summary(note.plainText);
    final timeText = AppDateUtils.formatRelative(note.updatedAt);

    return Card(
      elevation: 0,
      margin: const EdgeInsets.symmetric(
        vertical: AppDimens.spacingXs, horizontal: AppDimens.spacingXs,
      ),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppDimens.radiusCard),
        side: const BorderSide(color: AppColors.cardStroke, width: 0.5),
      ),
      color: _cardBackground(),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppDimens.radiusCard),
        onTap: isBatchMode ? () => onBatchToggle(!isSelected) : onTap,
        onLongPress: isBatchMode ? null : onLongPress,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: 14),
          child: Row(children: [
            if (isBatchMode) ...[
              SizedBox(width: 24, height: 24, child: Checkbox(
                value: isSelected,
                onChanged: (v) => onBatchToggle(v ?? false),
                activeColor: AppColors.primary,
              )),
              const SizedBox(width: AppDimens.spacingS),
            ],
            Expanded(child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  Expanded(child: Text(title, maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: AppDimens.textBody, fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary,
                    ),
                  )),
                  if (!isBatchMode) ...[
                    const SizedBox(width: AppDimens.spacingS),
                    Icon(
                      note.isFavorite ? Icons.star : Icons.star_border,
                      size: 20,
                      color: note.isFavorite ? Colors.amber : AppColors.textHint,
                    ),
                  ],
                  const SizedBox(width: AppDimens.spacingS),
                  Text(timeText, style: const TextStyle(
                    fontSize: AppDimens.textHint, color: AppColors.textHint,
                  )),
                ]),
                if (summaryText.isNotEmpty) ...[
                  const SizedBox(height: AppDimens.spacingXs),
                  Text(summaryText, maxLines: 2, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: AppDimens.textCaption, color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ],
            )),
          ]),
        ),
      ),
    );
  }
}
