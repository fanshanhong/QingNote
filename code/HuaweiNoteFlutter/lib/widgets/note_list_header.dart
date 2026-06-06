import 'package:flutter/material.dart';
import '../theme.dart';

class NoteListHeader extends StatelessWidget {
  final String title;
  final String subtitle;
  final bool isBatchMode;
  final int selectedCount;
  final bool filterPanelVisible;
  final VoidCallback onToggleFilterPanel;
  final VoidCallback onExitBatchMode;
  final VoidCallback onOverflowTap;

  const NoteListHeader({
    super.key,
    required this.title, required this.subtitle,
    required this.isBatchMode, required this.selectedCount,
    required this.filterPanelVisible,
    required this.onToggleFilterPanel, required this.onExitBatchMode,
    required this.onOverflowTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppDimens.spacingL, AppDimens.spacingXl, AppDimens.spacingL, AppDimens.spacingS,
      ),
      child: isBatchMode ? _buildBatchHeader() : _buildNormalHeader(),
    );
  }

  Widget _buildNormalHeader() {
    return Row(children: [
      Expanded(child: GestureDetector(
        onTap: onToggleFilterPanel,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Text(title, style: const TextStyle(
              fontSize: AppDimens.headerTitleSize, fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            )),
            const SizedBox(width: 4),
            AnimatedRotation(
              turns: filterPanelVisible ? 0.5 : 0,
              duration: const Duration(milliseconds: 200),
              child: const Icon(Icons.arrow_drop_down, color: AppColors.textHint, size: 24),
            ),
          ]),
          const SizedBox(height: 2),
          Text(subtitle, style: const TextStyle(
            fontSize: AppDimens.textCaption + 1, color: AppColors.textHint,
          )),
        ]),
      )),
      GestureDetector(
        onTap: onOverflowTap,
        child: const Padding(
          padding: EdgeInsets.all(AppDimens.spacingS),
          child: Icon(Icons.more_vert, color: AppColors.textSecondary, size: 24),
        ),
      ),
    ]);
  }

  Widget _buildBatchHeader() {
    return Row(children: [
      GestureDetector(
        onTap: onExitBatchMode,
        child: const Padding(
          padding: EdgeInsets.all(AppDimens.spacingS),
          child: Icon(Icons.close, color: AppColors.textSecondary, size: 24),
        ),
      ),
      const SizedBox(width: AppDimens.spacingS),
      Text('已选中 $selectedCount 项', style: const TextStyle(
        fontSize: 18, color: AppColors.textPrimary,
      )),
    ]);
  }
}
