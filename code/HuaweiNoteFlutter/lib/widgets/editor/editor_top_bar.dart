import 'package:flutter/material.dart';
import '../../theme.dart';

class EditorTopBar extends StatelessWidget {
  final bool isEditing;
  final bool canUndo;
  final bool canRedo;
  final VoidCallback onBack;
  final VoidCallback? onUndo;
  final VoidCallback? onRedo;
  final VoidCallback? onDone;

  const EditorTopBar({
    super.key,
    required this.isEditing,
    this.canUndo = false,
    this.canRedo = false,
    required this.onBack,
    this.onUndo,
    this.onRedo,
    this.onDone,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: AppDimens.editorToolbarHeight,
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: AppColors.divider, width: 0.5)),
      ),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back_ios_new, size: 20),
            onPressed: onBack,
          ),
          const Spacer(),
          if (isEditing) ...[
            IconButton(
              icon: Icon(
                Icons.undo,
                size: 22,
                color: canUndo ? AppColors.editorIconActive : AppColors.editorIconInactive,
              ),
              onPressed: canUndo ? onUndo : null,
            ),
            IconButton(
              icon: Icon(
                Icons.redo,
                size: 22,
                color: canRedo ? AppColors.editorIconActive : AppColors.editorIconInactive,
              ),
              onPressed: canRedo ? onRedo : null,
            ),
            TextButton(
              onPressed: onDone,
              child: const Text(
                '完成',
                style: TextStyle(
                  color: AppColors.primary,
                  fontSize: AppDimens.textBody,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
