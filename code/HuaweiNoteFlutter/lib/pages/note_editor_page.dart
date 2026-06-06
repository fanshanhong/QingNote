import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/note_editor_provider.dart';
import '../theme.dart';
import '../utils/color_utils.dart';
import '../widgets/editor/editor_top_bar.dart';
import '../widgets/editor/metadata_strip.dart';
import '../widgets/editor/notebook_indicator.dart';

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
                NotebookIndicator(
                  notebookName: state.loadedNote?.notebookId != null ? null : null,
                  notebookColor: null,
                  isEditing: state.isEditing,
                  onLoadNotebooks: notifier.loadNotebooks,
                  onNotebookSelected: notifier.setNotebook,
                ),
                MetadataStrip(
                  updatedAt: state.loadedNote?.updatedAt,
                  categoryName: null,
                  onCategoryTap: () => _showCategoryPicker(notifier),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppDimens.editorContentPadding,
                  ),
                  child: TextField(
                    controller: _titleController,
                    enabled: state.isEditing,
                    onChanged: notifier.updateTitle,
                    style: const TextStyle(
                      fontSize: AppDimens.editorTitleSize,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textPrimary,
                    ),
                    decoration: const InputDecoration(
                      hintText: '标题',
                      hintStyle: TextStyle(
                        fontSize: AppDimens.editorTitleSize,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textHint,
                      ),
                      border: InputBorder.none,
                      contentPadding: EdgeInsets.symmetric(vertical: AppDimens.spacingS),
                    ),
                  ),
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

  Future<void> _showCategoryPicker(NoteEditorNotifier notifier) async {
    final categories = await notifier.loadCategories();
    if (!mounted) return;
    await showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Padding(
              padding: EdgeInsets.all(AppDimens.spacingL),
              child: Text('选择分类', style: TextStyle(
                fontSize: AppDimens.textTitle,
                fontWeight: FontWeight.w600,
              )),
            ),
            ListTile(
              leading: const Icon(Icons.clear, size: 20),
              title: const Text('无分类'),
              onTap: () {
                notifier.setCategoryId(null);
                Navigator.pop(ctx);
              },
            ),
            ...categories.map((c) => ListTile(
              leading: Container(
                width: 12,
                height: 12,
                decoration: BoxDecoration(
                  color: AppColorUtils.parseHex(c.color) ?? AppColors.primary,
                  shape: BoxShape.circle,
                ),
              ),
              title: Text(c.name),
              onTap: () {
                notifier.setCategoryId(c.id);
                Navigator.pop(ctx);
              },
            )),
            const SizedBox(height: AppDimens.spacingL),
          ],
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
