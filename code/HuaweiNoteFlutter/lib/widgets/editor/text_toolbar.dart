import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import '../../theme.dart';

class TextToolbar extends StatelessWidget {
  final EditorState? editorState;
  final VoidCallback? onStyleTap;
  final VoidCallback? onImageTap;
  final VoidCallback? onRecordTap;

  const TextToolbar({
    super.key,
    this.editorState,
    this.onStyleTap,
    this.onImageTap,
    this.onRecordTap,
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
            onTap: () => _toggleTodoList(context),
          ),
          _ToolbarButton(
            icon: Icons.text_format,
            color: AppColors.primary,
            onTap: onStyleTap,
          ),
          _ToolbarButton(
            icon: Icons.image_outlined,
            color: AppColors.editorIconActive,
            onTap: onImageTap,
          ),
          _ToolbarButton(
            icon: Icons.draw_outlined,
            color: AppColors.editorIconInactive,
            onTap: () => _showSnackBar(context, '手写功能将在后续版本实现'),
          ),
          _ToolbarButton(
            icon: Icons.mic_none,
            color: AppColors.editorIconActive,
            onTap: onRecordTap,
          ),
        ],
      ),
    );
  }

  void _toggleTodoList(BuildContext context) {
    final es = editorState;
    if (es == null) return;
    final selection = es.selection;
    if (selection == null) return;

    final node = es.getNodeAtPath(selection.end.path);
    if (node == null) return;

    final transaction = es.transaction;

    if (node.type == TodoListBlockKeys.type) {
      // todo_list → paragraph（保留文字）
      final delta = node.delta ?? Delta();
      final paragraph = paragraphNode(delta: delta);
      transaction
        ..insertNode(node.path, paragraph)
        ..deleteNode(node)
        ..afterSelection = Selection.collapsed(
          Position(path: node.path, offset: delta.toPlainText().length),
        );
    } else if (node.delta != null && node.delta!.toPlainText().isEmpty) {
      // 空段落 → 原地替换为 todo_list
      final newNode = todoListNode(checked: false);
      transaction
        ..insertNode(node.path, newNode)
        ..deleteNode(node)
        ..afterSelection = Selection.collapsed(
          Position(path: node.path, offset: 0),
        );
    } else {
      // 非空段落 → 下方插入新 todo_list
      final nextPath = node.path.next;
      final newNode = todoListNode(checked: false);
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
