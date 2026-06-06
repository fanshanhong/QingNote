import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import '../../theme.dart';

Future<int?> showDateTimePickerSheet(BuildContext context,
    {int initialEpochMs = 0}) {
  return showModalBottomSheet<int>(
    context: context,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (ctx) => _DateTimePickerContent(initialEpochMs: initialEpochMs),
  );
}

class _DateTimePickerContent extends StatefulWidget {
  final int initialEpochMs;
  const _DateTimePickerContent({required this.initialEpochMs});
  @override
  State<_DateTimePickerContent> createState() => _DateTimePickerContentState();
}

class _DateTimePickerContentState extends State<_DateTimePickerContent> {
  late DateTime _selected;

  @override
  void initState() {
    super.initState();
    if (widget.initialEpochMs > 0) {
      _selected = DateTime.fromMillisecondsSinceEpoch(widget.initialEpochMs);
    } else {
      final now = DateTime.now().add(const Duration(hours: 1));
      final minute = (now.minute / 5).ceil() * 5;
      _selected = DateTime(now.year, now.month, now.day, now.hour, minute);
    }
  }

  String _formatHeader() {
    const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
    final wd = weekdays[_selected.weekday - 1];
    return '${_selected.year}年${_selected.month}月${_selected.day}日星期$wd';
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: AppDimens.spacingL),
          Text(_formatHeader(),
              style: const TextStyle(
                  fontSize: AppDimens.textTitle, fontWeight: FontWeight.bold)),
          SizedBox(
            height: 200,
            child: CupertinoDatePicker(
              mode: CupertinoDatePickerMode.dateAndTime,
              initialDateTime: _selected,
              minimumDate:
                  DateTime.now().subtract(const Duration(days: 365)),
              use24hFormat: false,
              onDateTimeChanged: (dt) => setState(() => _selected = dt),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(
                horizontal: AppDimens.spacingXl, vertical: AppDimens.spacingL),
            child: Row(
              children: [
                Expanded(
                  child: TextButton(
                    onPressed: () => Navigator.pop(context),
                    style: TextButton.styleFrom(
                      backgroundColor: const Color(0xFFF5F5F5),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8)),
                    ),
                    child: const Text('取消',
                        style: TextStyle(color: AppColors.textSecondary)),
                  ),
                ),
                const SizedBox(width: AppDimens.spacingM),
                Expanded(
                  child: TextButton(
                    onPressed: () => Navigator.pop(
                        context, _selected.millisecondsSinceEpoch),
                    style: TextButton.styleFrom(
                      backgroundColor: AppColors.primary,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8)),
                    ),
                    child: const Text('确定',
                        style: TextStyle(color: Colors.white)),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
