import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/note_editor_provider.dart';
import '../theme.dart';
import '../widgets/editor/editor_top_bar.dart';

class NoteEditorPage extends ConsumerStatefulWidget {
  final int noteId;
  const NoteEditorPage({super.key, required this.noteId});

  @override
  ConsumerState<NoteEditorPage> createState() => _NoteEditorPageState();
}

class _NoteEditorPageState extends ConsumerState<NoteEditorPage> {
  final _titleController = TextEditingController();

  @override
  void initState() {
    super.initState();
    Future.microtask(() async {
      final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
      await notifier.loadNote();
      final state = ref.read(noteEditorProvider(widget.noteId));
      _titleController.text = state.title;
    });
  }

  @override
  void dispose() {
    _titleController.dispose();
    super.dispose();
  }

  Future<void> _onBack() async {
    final state = ref.read(noteEditorProvider(widget.noteId));
    if (state.isEditing) {
      await ref.read(noteEditorProvider(widget.noteId).notifier).saveNote();
    }
    if (mounted) context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(noteEditorProvider(widget.noteId));
    final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _onBack();
      },
      child: GestureDetector(
        onTap: () {
          if (!state.isEditing) notifier.enterEditMode();
        },
        child: Scaffold(
          backgroundColor: Colors.white,
          body: SafeArea(
            child: Column(
              children: [
                EditorTopBar(
                  isEditing: state.isEditing,
                  onBack: _onBack,
                  onUndo: () => notifier.editorState?.undoManager.undo(),
                  onRedo: () => notifier.editorState?.undoManager.redo(),
                  onDone: () async {
                    await notifier.saveNote();
                    notifier.exitEditMode();
                  },
                ),
                Expanded(
                  child: Center(
                    child: Text(
                      '编辑器内容区域',
                      style: TextStyle(
                        color: AppColors.textHint,
                        fontSize: AppDimens.textBody,
                      ),
                    ),
                  ),
                ),
                _buildBottomBar(state),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBottomBar(NoteEditorState state) {
    if (state.isEditing) {
      return Container(
        height: AppDimens.editorToolbarHeight,
        decoration: const BoxDecoration(
          color: AppColors.editorToolbarBg,
          border: Border(top: BorderSide(color: AppColors.divider, width: 0.5)),
        ),
        child: Center(
          child: Text(
            '编辑工具栏占位',
            style: TextStyle(color: AppColors.textHint, fontSize: AppDimens.textCaption),
          ),
        ),
      );
    }
    return Container(
      height: AppDimens.editorToolbarHeight,
      decoration: const BoxDecoration(
        color: AppColors.editorToolbarBg,
        border: Border(top: BorderSide(color: AppColors.divider, width: 0.5)),
      ),
      child: Center(
        child: Text(
          '浏览工具栏占位',
          style: TextStyle(color: AppColors.textHint, fontSize: AppDimens.textCaption),
        ),
      ),
    );
  }
}
