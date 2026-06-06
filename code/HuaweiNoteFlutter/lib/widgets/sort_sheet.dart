import 'package:flutter/material.dart';
import '../repositories/note_repository.dart';
import '../theme.dart';

Future<NoteSortBy?> showSortSheet(BuildContext context, {required NoteSortBy current}) {
  return showModalBottomSheet<NoteSortBy>(
    context: context,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppDimens.spacingL, AppDimens.spacingL, AppDimens.spacingL, AppDimens.spacingM,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.only(bottom: AppDimens.spacingM),
              child: Text('排序方式', style: TextStyle(
                fontSize: AppDimens.textTitle, fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              )),
            ),
            RadioGroup<NoteSortBy>(
              groupValue: current,
              onChanged: (v) {
                if (v != null) Navigator.pop(context, v);
              },
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  RadioListTile<NoteSortBy>(
                    title: const Text('按编辑时间', style: TextStyle(
                      fontSize: AppDimens.textBody, color: AppColors.textPrimary)),
                    value: NoteSortBy.updatedDesc,
                    activeColor: AppColors.primary,
                  ),
                  RadioListTile<NoteSortBy>(
                    title: const Text('按创建时间', style: TextStyle(
                      fontSize: AppDimens.textBody, color: AppColors.textPrimary)),
                    value: NoteSortBy.createdDesc,
                    activeColor: AppColors.primary,
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            Center(child: TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('取消', style: TextStyle(
                color: AppColors.primary, fontSize: AppDimens.textBody,
              )),
            )),
          ],
        ),
      ),
    ),
  );
}
