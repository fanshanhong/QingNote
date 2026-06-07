import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import '../../theme.dart';

class TextToolbar extends StatelessWidget {
  final EditorState? editorState;
  final VoidCallback? onStyleTap;
  final VoidCallback? onImageTap;
  final VoidCallback? onRecordTap;
  final VoidCallback? onHandwritingTap;

  const TextToolbar({
    super.key,
    this.editorState,
    this.onStyleTap,
    this.onImageTap,
    this.onRecordTap,
    this.onHandwritingTap,
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
            color: AppColors.editorIconActive,
            onTap: onStyleTap,
          ),
          _ToolbarButton(
            icon: Icons.image_outlined,
            color: AppColors.editorIconActive,
            onTap: onImageTap,
          ),
          _ToolbarButton(
            icon: Icons.draw_outlined,
            color: AppColors.editorIconActive,
            onTap: onHandwritingTap,
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

    final delta = node.delta ?? Delta();
    final offset = delta.toPlainText().length;
    if (node.type == TodoListBlockKeys.type) {
      final paragraph = paragraphNode(delta: delta);
      transaction
        ..insertNode(node.path, paragraph)
        ..deleteNode(node)
        ..afterSelection = Selection.collapsed(
          Position(path: node.path, offset: offset),
        );
    } else {
      final newNode = todoListNode(checked: false, delta: delta);
      transaction
        ..insertNode(node.path, newNode)
        ..deleteNode(node)
        ..afterSelection = Selection.collapsed(
          Position(path: node.path, offset: offset),
        );
    }
    es.apply(transaction);
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
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: SizedBox(
        width: 48, height: 48,
        child: Center(child: Icon(icon, color: color, size: 24)),
      ),
    );
  }
}
