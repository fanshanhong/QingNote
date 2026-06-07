import 'package:flutter/material.dart';
import '../../theme.dart';

Future<String?> showNewFolderSheet(
  BuildContext context, {
  String? initialName,
}) async {
  return showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => _NewFolderContent(initialName: initialName),
  );
}

class _NewFolderContent extends StatefulWidget {
  final String? initialName;
  const _NewFolderContent({this.initialName});

  @override
  State<_NewFolderContent> createState() => _NewFolderContentState();
}

class _NewFolderContentState extends State<_NewFolderContent> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialName ?? '');
    if (widget.initialName != null) {
      _controller.selection = TextSelection(
        baseOffset: 0,
        extentOffset: widget.initialName!.length,
      );
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isEditing = widget.initialName != null;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        AppDimens.spacingXl,
        AppDimens.spacingL,
        AppDimens.spacingXl,
        MediaQuery.of(context).viewInsets.bottom + AppDimens.spacingL,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            isEditing ? '重命名文件夹' : '新建文件夹',
            style: const TextStyle(
                fontSize: AppDimens.textTitle, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: AppDimens.spacingL),
          TextField(
            controller: _controller,
            autofocus: true,
            decoration: InputDecoration(
              hintText: '文件夹名称',
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            ),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: AppDimens.spacingL),
          Row(children: [
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
                  style: TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: AppDimens.textBody,
                  )),
            )),
            const SizedBox(width: AppDimens.spacingM),
            Expanded(
                child: TextButton(
              onPressed: _controller.text.trim().isEmpty
                  ? null
                  : () => Navigator.pop(context, _controller.text.trim()),
              style: TextButton.styleFrom(
                backgroundColor: AppColors.primary,
                disabledBackgroundColor:
                    AppColors.primary.withValues(alpha: 0.3),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
              ),
              child: const Text('确认',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: AppDimens.textBody,
                  )),
            )),
          ]),
        ],
      ),
    );
  }
}
