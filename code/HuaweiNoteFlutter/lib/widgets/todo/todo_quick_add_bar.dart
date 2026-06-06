import 'package:flutter/material.dart';
import '../../models/todo.dart';
import '../../theme.dart';
import 'date_time_picker_sheet.dart';
import 'repeat_picker_sheet.dart';

class TodoQuickAddBar extends StatefulWidget {
  final void Function(
      String title, int remindAt, bool isImportant, RepeatType repeatType)
      onSave;

  const TodoQuickAddBar({super.key, required this.onSave});

  @override
  State<TodoQuickAddBar> createState() => _TodoQuickAddBarState();
}

class _TodoQuickAddBarState extends State<TodoQuickAddBar> {
  final _controller = TextEditingController();
  final _focusNode = FocusNode();
  int _remindAt = 0;
  bool _isImportant = false;
  RepeatType _repeatType = RepeatType.none;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance
        .addPostFrameCallback((_) => _focusNode.requestFocus());
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _pickTime() async {
    final result =
        await showDateTimePickerSheet(context, initialEpochMs: _remindAt);
    if (result != null) {
      setState(() => _remindAt = result);
    }
  }

  void _pickRepeat() async {
    final result =
        await showRepeatPickerSheet(context, current: _repeatType);
    if (result != null) {
      setState(() => _repeatType = result);
    }
  }

  void _toggleImportant() {
    setState(() => _isImportant = !_isImportant);
  }

  void _save() {
    final title = _controller.text.trim();
    if (title.isEmpty) return;
    widget.onSave(title, _remindAt, _isImportant, _repeatType);
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(AppDimens.spacingL,
          AppDimens.spacingS, AppDimens.spacingL, AppDimens.spacingS),
      decoration: const BoxDecoration(
        color: AppColors.bgCard,
        border: Border(top: BorderSide(color: AppColors.divider)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _controller,
              focusNode: _focusNode,
              decoration: const InputDecoration(
                hintText: '待办事项',
                border: InputBorder.none,
                hintStyle: TextStyle(color: AppColors.textHint),
              ),
            ),
            Row(
              children: [
                IconButton(
                  icon: Icon(Icons.access_time,
                      color: _remindAt > 0
                          ? AppColors.primary
                          : AppColors.textHint),
                  onPressed: _pickTime,
                ),
                IconButton(
                  icon: Icon(Icons.priority_high,
                      color: _isImportant
                          ? AppColors.primary
                          : AppColors.textHint),
                  onPressed: _toggleImportant,
                ),
                if (_remindAt > 0)
                  GestureDetector(
                    onTap: _pickRepeat,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        border: Border.all(color: AppColors.divider),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        _repeatLabel(),
                        style: const TextStyle(
                            fontSize: AppDimens.textCaption,
                            color: AppColors.textSecondary),
                      ),
                    ),
                  ),
                const Spacer(),
                TextButton(
                  onPressed: _save,
                  style: TextButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    padding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 8),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16)),
                  ),
                  child: const Text('保存',
                      style: TextStyle(
                          color: Colors.white,
                          fontSize: AppDimens.textCaption)),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  String _repeatLabel() => switch (_repeatType) {
        RepeatType.none => '不重复',
        RepeatType.daily => '每天',
        RepeatType.weekly => '每周',
        RepeatType.monthly => '每月',
        RepeatType.yearly => '每年',
      };
}
