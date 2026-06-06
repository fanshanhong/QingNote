import 'package:flutter/material.dart';
import '../theme.dart';

Future<bool> showDeleteConfirmSheet(
  BuildContext context, {
  required String message,
  required String confirmLabel,
}) async {
  final result = await showModalBottomSheet<bool>(
    context: context,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppDimens.spacingXl, AppDimens.spacingL,
          AppDimens.spacingXl, AppDimens.spacingL,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message, style: const TextStyle(
              fontSize: AppDimens.textBody, color: AppColors.textSecondary, height: 1.6,
            )),
            const SizedBox(height: AppDimens.spacingL),
            Row(children: [
              Expanded(child: TextButton(
                onPressed: () => Navigator.pop(context, false),
                style: TextButton.styleFrom(
                  backgroundColor: const Color(0xFFF5F5F5),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                child: const Text('取消', style: TextStyle(
                  color: AppColors.textSecondary, fontSize: AppDimens.textBody,
                )),
              )),
              const SizedBox(width: AppDimens.spacingM),
              Expanded(child: TextButton(
                onPressed: () => Navigator.pop(context, true),
                style: TextButton.styleFrom(
                  backgroundColor: const Color(0xFFFF4444),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                child: Text(confirmLabel, style: const TextStyle(
                  color: Colors.white, fontSize: AppDimens.textBody,
                )),
              )),
            ]),
          ],
        ),
      ),
    ),
  );
  return result ?? false;
}
