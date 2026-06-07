import 'dart:io';
import 'package:flutter/material.dart';
import '../models/note.dart';
import '../theme.dart';
import '../utils/color_utils.dart';
import '../utils/date_utils.dart';
import '../utils/text_utils.dart';

class NoteCard extends StatelessWidget {
  final Note note;
  final bool isGridView;
  final bool isBatchMode;
  final bool isSelected;
  final String? notebookColor;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final ValueChanged<bool> onBatchToggle;

  const NoteCard({
    super.key,
    required this.note,
    this.isGridView = false,
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

  String _displayTitle() {
    if (!AppTextUtils.isBlankTitle(note.title)) return note.title;
    final firstLine = note.plainText.split('\n').firstWhere(
      (l) => l.trim().isNotEmpty,
      orElse: () => '',
    );
    return firstLine.isNotEmpty ? firstLine : '无标题';
  }

  @override
  Widget build(BuildContext context) {
    final title = _displayTitle();
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
          child: isGridView
              ? _buildGridContent(title, summaryText, timeText)
              : _buildListContent(title, summaryText, timeText),
        ),
      ),
    );
  }

  Widget _buildListContent(String title, String summaryText, String timeText) {
    return Row(children: [
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
          Text(title, maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: AppDimens.textBody, fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: AppDimens.spacingXs),
          Row(children: [
            if (note.hasTodo) ...[
              const Icon(Icons.check_circle_outline, size: 14, color: AppColors.textHint),
              const SizedBox(width: 4),
            ],
            Text(timeText, style: const TextStyle(
              fontSize: AppDimens.textHint, color: AppColors.textHint,
            )),
            if (summaryText.isNotEmpty) ...[
              const Text(' | ', style: TextStyle(
                fontSize: AppDimens.textHint, color: AppColors.textHint,
              )),
              Expanded(child: Text(summaryText, maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: AppDimens.textHint, color: AppColors.textHint,
                ),
              )),
            ],
          ]),
        ],
      )),
      if (!isBatchMode && note.firstImagePath != null) ...[
        const SizedBox(width: AppDimens.spacingS),
        ClipRRect(
          borderRadius: BorderRadius.circular(4),
          child: Image.file(
            File(note.firstImagePath!),
            width: 48,
            height: 48,
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) => const SizedBox.shrink(),
          ),
        ),
      ],
    ]);
  }

  Widget _buildGridContent(String title, String summaryText, String timeText) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            fontSize: AppDimens.textBody, fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        if (summaryText.isNotEmpty) ...[
          const SizedBox(height: AppDimens.spacingXs),
          Text(summaryText, maxLines: 5, overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: AppDimens.textCaption, color: AppColors.textSecondary,
            ),
          ),
        ],
        const SizedBox(height: AppDimens.spacingS),
        Row(children: [
          if (note.hasTodo) ...[
            const Icon(Icons.check_circle_outline, size: 14, color: AppColors.textHint),
            const SizedBox(width: 4),
          ],
          if (note.isFavorite) ...[
            const Icon(Icons.star, size: 14, color: Colors.amber),
            const SizedBox(width: 4),
          ],
          Text(timeText, style: const TextStyle(
            fontSize: AppDimens.textHint, color: AppColors.textHint,
          )),
        ]),
      ],
    );
  }
}
