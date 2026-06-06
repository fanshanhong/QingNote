import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import '../../theme.dart';

class TextToolbar extends StatelessWidget {
  final EditorState? editorState;
  final VoidCallback? onStyleTap;

  const TextToolbar({
    super.key,
    this.editorState,
    this.onStyleTap,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: AppDimens.editorToolbarHeight,
      decoration: const BoxDecoration(
        color: AppColors.editorToolbarBg,
        border: Border(top: BorderSide(color: AppColors.divider, width: 0.5)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _ToolbarButton(
            icon: Icons.checklist,
            color: AppColors.editorIconActive,
            onTap: () => _insertTodoList(context),
          ),
          _ToolbarButton(
            icon: Icons.text_format,
            color: AppColors.primary,
            onTap: onStyleTap,
          ),
          _ToolbarButton(
            icon: Icons.image_outlined,
            color: AppColors.editorIconInactive,
            onTap: () => _showSnackBar(context, '图片功能将在后续版本实现'),
          ),
          _ToolbarButton(
            icon: Icons.draw_outlined,
            color: AppColors.editorIconInactive,
            onTap: () => _showSnackBar(context, '手写功能将在后续版本实现'),
          ),
          _ToolbarButton(
            icon: Icons.mic_none,
            color: AppColors.editorIconInactive,
            onTap: () => _showSnackBar(context, '录音功能将在后续版本实现'),
          ),
        ],
      ),
    );
  }

  void _insertTodoList(BuildContext context) {
    final es = editorState;
    if (es == null) return;
    final selection = es.selection;
    if (selection == null) return;

    final node = es.getNodeAtPath(selection.end.path);
    if (node == null) return;

    final transaction = es.transaction;
    final newNode = todoListNode(checked: false);

    if (node.delta != null && node.delta!.toPlainText().isEmpty) {
      transaction
        ..insertNode(node.path, newNode)
        ..deleteNode(node)
        ..afterSelection = Selection.collapsed(
          Position(path: node.path, offset: 0),
        );
    } else {
      final nextPath = node.path.next;
      transaction
        ..insertNode(nextPath, newNode)
        ..afterSelection = Selection.collapsed(
          Position(path: nextPath, offset: 0),
        );
    }
    es.apply(transaction);
  }

  void _showSnackBar(BuildContext context, String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }
}

class _ToolbarButton extends StatelessWidget {
  final IconData icon;
  final Color color;
  final VoidCallback? onTap;

  const _ToolbarButton({
    required this.icon,
    required this.color,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return IconButton(
      icon: Icon(icon, color: color, size: 24),
      onPressed: onTap,
    );
  }
}
