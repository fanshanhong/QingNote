import 'package:flutter/material.dart';
import '../../theme.dart';

class TodoListHeader extends StatelessWidget {
  final String title;
  final String subtitle;
  final bool filterPanelVisible;
  final bool isBatchMode;
  final int batchCount;
  final VoidCallback onTitleTap;
  final VoidCallback onOverflowTap;
  final VoidCallback? onBatchClose;

  const TodoListHeader({
    super.key,
    required this.title,
    required this.subtitle,
    required this.filterPanelVisible,
    this.isBatchMode = false,
    this.batchCount = 0,
    required this.onTitleTap,
    required this.onOverflowTap,
    this.onBatchClose,
  });

  @override
  Widget build(BuildContext context) {
    if (isBatchMode) return _buildBatchHeader();
    return _buildNormalHeader();
  }

  Widget _buildNormalHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppDimens.spacingL,
          AppDimens.spacingXl, AppDimens.spacingL, AppDimens.spacingS),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: GestureDetector(
              onTap: onTitleTap,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(title,
                            style: const TextStyle(
                              fontSize: AppDimens.headerTitleSize,
                              fontWeight: FontWeight.bold,
                              color: AppColors.textPrimary,
                            ),
                            overflow: TextOverflow.ellipsis),
                      ),
                      const SizedBox(width: 4),
                      AnimatedRotation(
                        turns: filterPanelVisible ? 0.5 : 0,
                        duration: const Duration(milliseconds: 200),
                        child: const Icon(Icons.arrow_drop_down,
                            color: AppColors.textPrimary),
                      ),
                    ],
                  ),
                  const SizedBox(height: 2),
                  Text(subtitle,
                      style: const TextStyle(
                        fontSize: AppDimens.textCaption,
                        color: AppColors.textHint,
                      )),
                ],
              ),
            ),
          ),
          if (!filterPanelVisible)
            GestureDetector(
              onTap: onOverflowTap,
              child: const Padding(
                padding: EdgeInsets.all(8),
                child: Icon(Icons.more_vert, color: AppColors.textSecondary),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildBatchHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppDimens.spacingL,
          AppDimens.spacingXl, AppDimens.spacingL, AppDimens.spacingS),
      child: Row(
        children: [
          GestureDetector(
            onTap: onBatchClose,
            child: const Icon(Icons.close, color: AppColors.textPrimary),
          ),
          const SizedBox(width: AppDimens.spacingM),
          Text('已选择 $batchCount 项',
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              )),
        ],
      ),
    );
  }
}
