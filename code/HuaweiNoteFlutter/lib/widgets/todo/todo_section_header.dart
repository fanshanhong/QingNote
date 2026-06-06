import 'package:flutter/material.dart';
import '../../theme.dart';

class TodoSectionHeader extends StatelessWidget {
  final String title;
  final bool isOverdue;

  const TodoSectionHeader(
      {super.key, required this.title, this.isOverdue = false});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppDimens.spacingL,
          AppDimens.spacingXl, AppDimens.spacingL, AppDimens.spacingS),
      child: Text(
        title,
        style: TextStyle(
          fontSize: AppDimens.textBody,
          fontWeight: FontWeight.bold,
          color: isOverdue ? AppColors.danger : AppColors.textSecondary,
        ),
      ),
    );
  }
}
