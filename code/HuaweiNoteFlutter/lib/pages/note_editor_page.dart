import 'dart:async';
import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/note_editor_provider.dart';
import '../theme.dart';
import '../utils/color_utils.dart';
import '../widgets/editor/editor_top_bar.dart';
import '../widgets/editor/metadata_strip.dart';
import '../widgets/editor/notebook_indicator.dart';
import '../widgets/editor/style_picker_sheet.dart';
import '../widgets/editor/text_toolbar.dart';

class NoteEditorPage extends ConsumerStatefulWidget {
  final int noteId;
  const NoteEditorPage({super.key, required this.noteId});

  @override
  ConsumerState<NoteEditorPage> createState() => _NoteEditorPageState();
}

class _NoteEditorPageState extends ConsumerState<NoteEditorPage> {
  final _titleController = TextEditingController();
  EditorScrollController? _scrollController;
  StreamSubscription? _transactionSub;
  bool _editorReady = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(() async {
      final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
      await notifier.loadNote();
      final state = ref.read(noteEditorProvider(widget.noteId));
      _titleController.text = state.title;
      _setupEditor(notifier);
    });
  }

  void _setupEditor(NoteEditorNotifier notifier) {
    final editorState = notifier.editorState;
    if (editorState == null) return;
    _scrollController = EditorScrollController(editorState: editorState);
    _transactionSub = editorState.transactionStream.listen((_) {
      final doc = editorState.document;
      final hasContent = doc.root.children.any((node) {
        final delta = node.delta;
        return delta != null && delta.toPlainText().isNotEmpty;
      });
      notifier.updateDocumentHasContent(hasContent);
    });
    if (mounted) setState(() => _editorReady = true);
  }

  @override
  void dispose() {
    _transactionSub?.cancel();
    _scrollController?.dispose();
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
                Expanded(child: _buildEditor(notifier, state)),
                _buildBottomBar(notifier, state),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildEditor(NoteEditorNotifier notifier, NoteEditorState state) {
    final editorState = notifier.editorState;
    if (!_editorReady || editorState == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return AppFlowyEditor(
      editorState: editorState,
      editable: state.isEditing,
      editorScrollController: _scrollController,
      editorStyle: EditorStyle.mobile(
        padding: const EdgeInsets.symmetric(
          horizontal: AppDimens.editorContentPadding,
        ),
        textStyleConfiguration: TextStyleConfiguration(
          text: const TextStyle(fontSize: 16, color: AppColors.textPrimary),
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

  Widget _buildBottomBar(NoteEditorNotifier notifier, NoteEditorState state) {
    if (state.isEditing) {
      return TextToolbar(
        editorState: notifier.editorState,
        onStyleTap: () => _showStylePicker(notifier),
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

  void _showStylePicker(NoteEditorNotifier notifier) {
    final es = notifier.editorState;
    if (es == null) return;
    showStylePickerSheet(
      context,
      editorState: es,
      onBackgroundChanged: notifier.setBackground,
    );
  }
}
