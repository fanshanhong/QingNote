import 'package:flutter/material.dart';
import '../../theme.dart';

class NotebookSheetResult {
  final String name;
  final String color;
  const NotebookSheetResult({required this.name, required this.color});
}

const notebookPalette = [
  '#9E9E9E', '#E53935', '#FB8C00', '#FBC02D',
  '#43A047', '#00ACC1', '#1E88E5', '#8E24AA',
];

Future<NotebookSheetResult?> showNewNotebookSheet(
  BuildContext context, {
  String? initialName,
  String? initialColor,
}) async {
  return showModalBottomSheet<NotebookSheetResult>(
    context: context,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => _NewNotebookContent(
      initialName: initialName,
      initialColor: initialColor,
    ),
  );
}

class _NewNotebookContent extends StatefulWidget {
  final String? initialName;
  final String? initialColor;
  const _NewNotebookContent({this.initialName, this.initialColor});

  @override
  State<_NewNotebookContent> createState() => _NewNotebookContentState();
}

class _NewNotebookContentState extends State<_NewNotebookContent> {
  late final TextEditingController _controller;
  late String _selectedColor;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialName ?? '');
    _selectedColor = widget.initialColor ?? notebookPalette.first;
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

  Color _parseHex(String hex) {
    final cleaned = hex.replaceFirst('#', '');
    final value = int.tryParse(cleaned, radix: 16) ?? 0x9E9E9E;
    return Color(0xFF000000 | value);
  }

  @override
  Widget build(BuildContext context) {
    final isEditing = widget.initialName != null;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        AppDimens.spacingXl, AppDimens.spacingL,
        AppDimens.spacingXl, MediaQuery.of(context).viewInsets.bottom + AppDimens.spacingL,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            isEditing ? '编辑笔记本' : '新建笔记本',
            style: const TextStyle(fontSize: AppDimens.textTitle, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: AppDimens.spacingL),
          TextField(
            controller: _controller,
            autofocus: true,
            decoration: InputDecoration(
              hintText: '笔记本名称',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            ),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: AppDimens.spacingL),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: notebookPalette.map((hex) {
              final selected = hex == _selectedColor;
              return GestureDetector(
                onTap: () => setState(() => _selectedColor = hex),
                child: Container(
                  width: 32,
                  height: 32,
                  margin: const EdgeInsets.symmetric(horizontal: 6),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: _parseHex(hex),
                    border: selected
                        ? Border.all(color: AppColors.primary, width: 2.5)
                        : null,
                  ),
                  child: selected
                      ? const Icon(Icons.check, size: 16, color: Colors.white)
                      : null,
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: AppDimens.spacingL),
          Row(children: [
            Expanded(child: TextButton(
              onPressed: () => Navigator.pop(context),
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
              onPressed: _controller.text.trim().isEmpty
                  ? null
                  : () => Navigator.pop(context, NotebookSheetResult(
                      name: _controller.text.trim(),
                      color: _selectedColor,
                    )),
              style: TextButton.styleFrom(
                backgroundColor: AppColors.primary,
                disabledBackgroundColor: AppColors.primary.withValues(alpha: 0.3),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
              child: const Text('确认', style: TextStyle(
                color: Colors.white, fontSize: AppDimens.textBody,
              )),
            )),
          ]),
        ],
      ),
    );
  }
}
