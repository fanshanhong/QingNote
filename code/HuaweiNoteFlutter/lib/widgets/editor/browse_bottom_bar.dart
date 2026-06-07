import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import '../../theme.dart';
import '../delete_confirm_sheet.dart';

class BrowseBottomBar extends StatelessWidget {
  final bool isFavorite;
  final String shareText;
  final VoidCallback onToggleFavorite;
  final VoidCallback onDelete;
  final VoidCallback? onMore;

  const BrowseBottomBar({
    super.key,
    required this.isFavorite,
    required this.shareText,
    required this.onToggleFavorite,
    required this.onDelete,
    this.onMore,
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
          IconButton(
            icon: const Icon(Icons.share_outlined, size: 22),
            color: AppColors.editorIconActive,
            onPressed: () =>
                SharePlus.instance.share(ShareParams(text: shareText)),
          ),
          IconButton(
            icon: Icon(
              isFavorite ? Icons.star : Icons.star_border,
              size: 22,
              color: isFavorite ? Colors.amber : AppColors.editorIconActive,
            ),
            onPressed: onToggleFavorite,
          ),
          IconButton(
            icon: const Icon(Icons.delete_outline, size: 22),
            color: AppColors.editorIconActive,
            onPressed: () => _confirmDelete(context),
          ),
          IconButton(
            icon: const Icon(Icons.more_horiz, size: 22),
            color: AppColors.editorIconActive,
            onPressed: onMore ??
                () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('更多功能将在后续版本实现')),
                  );
                },
          ),
        ],
      ),
    );
  }

  Future<void> _confirmDelete(BuildContext context) async {
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '确定要删除这条笔记吗？\n删除后可在"最近删除"中恢复',
      confirmLabel: '删除',
    );
    if (confirmed) onDelete();
  }
}
