import 'package:flutter/material.dart';
import '../../models/todo.dart';
import '../../theme.dart';

class TodoCard extends StatelessWidget {
  final Todo todo;
  final bool isBatchMode;
  final bool isSelected;
  final bool isDeletedView;
  final VoidCallback? onCheckToggle;
  final VoidCallback? onTap;
  final VoidCallback? onBatchToggle;
  final VoidCallback? onRestore;
  final VoidCallback? onDeletePermanently;

  const TodoCard({
    super.key,
    required this.todo,
    this.isBatchMode = false,
    this.isSelected = false,
    this.isDeletedView = false,
    this.onCheckToggle,
    this.onTap,
    this.onBatchToggle,
    this.onRestore,
    this.onDeletePermanently,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: isBatchMode ? onBatchToggle : (isDeletedView ? null : onTap),
      child: Container(
        margin: const EdgeInsets.symmetric(
            horizontal: AppDimens.spacingL, vertical: AppDimens.spacingXs),
        padding: const EdgeInsets.symmetric(
            horizontal: AppDimens.spacingL, vertical: AppDimens.spacingM),
        decoration: BoxDecoration(
          color: AppColors.bgCard,
          borderRadius: BorderRadius.circular(AppDimens.radiusCard),
        ),
        child: Row(
          children: [
            _buildLeading(),
            const SizedBox(width: AppDimens.spacingM),
            Expanded(child: _buildContent()),
            if (isDeletedView) _buildDeletedActions(),
          ],
        ),
      ),
    );
  }

  Widget _buildLeading() {
    if (isBatchMode) {
      return Icon(
        isSelected ? Icons.check_circle : Icons.radio_button_unchecked,
        size: 24,
        color: isSelected ? AppColors.primary : AppColors.textHint,
      );
    }
    if (isDeletedView) return const SizedBox.shrink();
    return GestureDetector(
      onTap: onCheckToggle,
      child: Icon(
        todo.isCompleted
            ? Icons.check_circle_outline
            : Icons.radio_button_unchecked,
        size: 24,
        color: todo.isCompleted ? AppColors.primary : AppColors.textHint,
      ),
    );
  }

  Widget _buildContent() {
    final subtitle = _buildSubtitle();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildTitle(),
        if (subtitle != null) ...[
          const SizedBox(height: 2),
          subtitle,
        ],
      ],
    );
  }

  Widget _buildTitle() {
    final title = todo.title.isEmpty ? '待办事项' : todo.title;
    if (todo.isImportant && !todo.isCompleted) {
      return RichText(
        text: TextSpan(children: [
          const TextSpan(
              text: '❗',
              style:
                  TextStyle(color: AppColors.danger, fontSize: AppDimens.textBody)),
          TextSpan(
            text: title,
            style: const TextStyle(
                fontSize: AppDimens.textBody, color: AppColors.textPrimary),
          ),
        ]),
      );
    }
    return Text(
      title,
      style: TextStyle(
        fontSize: AppDimens.textBody,
        color: todo.isCompleted ? AppColors.textHint : AppColors.textPrimary,
        decoration: todo.isCompleted ? TextDecoration.lineThrough : null,
      ),
    );
  }

  Widget? _buildSubtitle() {
    final parts = <String>[];
    if (todo.remindAt > 0) {
      final dt = DateTime.fromMillisecondsSinceEpoch(todo.remindAt);
      final amPm = dt.hour < 12 ? '上午' : '下午';
      final hour =
          dt.hour == 0 ? 12 : (dt.hour > 12 ? dt.hour - 12 : dt.hour);
      final minute = dt.minute.toString().padLeft(2, '0');
      parts.add('$amPm$hour:$minute');
    }
    if (todo.repeatType != RepeatType.none) {
      parts.add(switch (todo.repeatType) {
        RepeatType.daily => '每天',
        RepeatType.weekly => '每周',
        RepeatType.monthly => '每月',
        RepeatType.yearly => '每年',
        RepeatType.none => '',
      });
    }
    if (parts.isEmpty) return null;
    final isOverdue = todo.remindAt > 0 &&
        todo.remindAt < DateTime.now().millisecondsSinceEpoch &&
        !todo.isCompleted;
    return Row(
      children: [
        if (todo.repeatType != RepeatType.none)
          Padding(
            padding: const EdgeInsets.only(right: 4),
            child: Icon(Icons.repeat,
                size: 14,
                color: isOverdue ? AppColors.danger : AppColors.textHint),
          ),
        Text(
          parts.join(' | '),
          style: TextStyle(
              fontSize: AppDimens.textCaption,
              color: isOverdue ? AppColors.danger : AppColors.textHint),
        ),
      ],
    );
  }

  Widget _buildDeletedActions() {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        TextButton(
            onPressed: onRestore,
            child: const Text('恢复',
                style: TextStyle(fontSize: AppDimens.textCaption))),
        TextButton(
          onPressed: onDeletePermanently,
          child: Text('删除',
              style: TextStyle(
                  fontSize: AppDimens.textCaption, color: AppColors.danger)),
        ),
      ],
    );
  }
}
