import 'package:flutter/material.dart';
import '../../models/todo.dart';
import '../../theme.dart';

Future<RepeatType?> showRepeatPickerSheet(BuildContext context,
    {RepeatType current = RepeatType.none}) {
  return showModalBottomSheet<RepeatType>(
    context: context,
    backgroundColor: AppColors.bgCard,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (ctx) => _RepeatPickerContent(current: current),
  );
}

class _RepeatPickerContent extends StatelessWidget {
  final RepeatType current;
  const _RepeatPickerContent({required this.current});

  @override
  Widget build(BuildContext context) {
    final options = [
      (RepeatType.none, '不重复'),
      (RepeatType.daily, '每天'),
      (RepeatType.weekly, '每周'),
      (RepeatType.monthly, '每月'),
      (RepeatType.yearly, '每年'),
    ];
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(AppDimens.spacingXl,
                AppDimens.spacingL, AppDimens.spacingXl, AppDimens.spacingS),
            child: Text('重复',
                style: TextStyle(
                    fontSize: AppDimens.textTitle,
                    fontWeight: FontWeight.bold)),
          ),
          ...options.map((opt) => ListTile(
                title: Text(opt.$2),
                trailing: current == opt.$1
                    ? const Icon(Icons.circle,
                        size: 16, color: AppColors.primary)
                    : null,
                onTap: () => Navigator.pop(context, opt.$1),
              )),
          Center(
            child: TextButton(
              onPressed: () => Navigator.pop(context),
              child:
                  const Text('取消', style: TextStyle(color: AppColors.primary)),
            ),
          ),
          const SizedBox(height: AppDimens.spacingS),
        ],
      ),
    );
  }
}
