import 'dart:async';
import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/note_editor_provider.dart';
import '../theme.dart';
import '../utils/color_utils.dart';
import '../widgets/editor/editor_top_bar.dart';
import '../widgets/editor/metadata_strip.dart';
import '../widgets/editor/browse_bottom_bar.dart';
import '../widgets/editor/style_picker_sheet.dart';
import '../widgets/editor/text_toolbar.dart';
import '../editor/handwriting/handwriting_painter.dart';
import 'package:path_provider/path_provider.dart';
import '../editor/audio_block_component.dart';
import '../editor/image_block_component.dart';
import '../services/audio_player_service.dart';
import '../editor/handwriting/handwriting_controller.dart';
import '../models/stroke.dart';
import '../editor/handwriting/handwriting_overlay.dart';
import '../widgets/editor/handwriting_toolbar.dart';
import '../widgets/editor/handwriting_style_picker_sheet.dart';
import '../widgets/editor/brush_width_picker.dart';

class NoteEditorPage extends ConsumerStatefulWidget {
  final int noteId;
  const NoteEditorPage({super.key, required this.noteId});

  @override
  ConsumerState<NoteEditorPage> createState() => _NoteEditorPageState();
}

class _NoteEditorPageState extends ConsumerState<NoteEditorPage> {
  final _titleController = TextEditingController();
  final _titleFocusNode = FocusNode();
  final _editorFocusNode = FocusNode();
  EditorScrollController? _scrollController;
  StreamSubscription? _transactionSub;
  bool _editorReady = false;
  final _audioPlayer = AudioPlayerService();
  final _handwritingController = HandwritingOverlayController();
  String _appDocPath = '';
  bool _showStylePanel = false;
  Map<String, dynamic> _savedToggledStyle = {};
  EditorState? _editorStateRef;
  VoidCallback? _onToggledStyleChanged;
  VoidCallback? _onSelectionChanged;

  @override
  void initState() {
    super.initState();
    Future.microtask(() async {
      final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
      await notifier.loadNote();
      final appDir = await getApplicationDocumentsDirectory();
      _appDocPath = appDir.path;
      final state = ref.read(noteEditorProvider(widget.noteId));
      _titleController.text = state.title;
      _setupEditor(notifier);
      final loadedStrokes = ref.read(noteEditorProvider(widget.noteId)).loadedNote?.content.handwriting ?? [];
      _handwritingController.setStrokes(loadedStrokes);
      if (widget.noteId == 0) {
        _titleFocusNode.requestFocus();
      }
    });
  }

  void _setupEditor(NoteEditorNotifier notifier) {
    final editorState = notifier.editorState;
    if (editorState == null) return;
    _editorStateRef = editorState;
    _scrollController = EditorScrollController(editorState: editorState);
    _transactionSub = editorState.transactionStream.listen((_) {
      final doc = editorState.document;
      final hasContent = doc.root.children.any((node) {
        final delta = node.delta;
        return delta != null && delta.toPlainText().isNotEmpty;
      });
      notifier.updateDocumentHasContent(hasContent);
      notifier.updateUndoRedoState(
        editorState.undoManager.undoStack.isNonEmpty,
        editorState.undoManager.redoStack.isNonEmpty,
      );
      _carryFormatOnNewLine(editorState);
    });

    _onToggledStyleChanged = () {
      final style = editorState.toggledStyle;
      if (style.isNotEmpty) {
        _savedToggledStyle = Map.from(style);
      }
    };
    editorState.toggledStyleNotifier.addListener(_onToggledStyleChanged!);

    _onSelectionChanged = () {
      if (_savedToggledStyle.isNotEmpty && editorState.toggledStyle.isEmpty) {
        final styleCopy = Map<String, dynamic>.from(_savedToggledStyle);
        Future.microtask(() {
          if (!mounted) return;
          for (final entry in styleCopy.entries) {
            editorState.updateToggledStyle(entry.key, entry.value);
          }
        });
      }
    };
    editorState.selectionNotifier.addListener(_onSelectionChanged!);

    if (mounted) setState(() => _editorReady = true);
  }

  void _carryFormatOnNewLine(EditorState es) {
    if (_savedToggledStyle.isNotEmpty) return;

    final selection = es.selection;
    if (selection == null || !selection.isCollapsed) return;
    if (selection.start.offset != 0) return;

    final node = es.getNodeAtPath(selection.start.path);
    if (node == null || node.delta == null) return;
    if (node.delta!.isNotEmpty) return;

    const inlineKeys = {
      'bold', 'italic', 'underline', 'strikethrough', 'fontSize', 'textColor',
    };

    final prev = node.previous;
    if (prev != null && prev.delta != null && prev.delta!.isNotEmpty) {
      final lastAttrs = prev.delta!.last.attributes;
      if (lastAttrs != null && lastAttrs.isNotEmpty) {
        for (final entry in lastAttrs.entries) {
          if (inlineKeys.contains(entry.key) && entry.value != null) {
            es.updateToggledStyle(entry.key, entry.value);
          }
        }
      }
    }
  }

  @override
  void deactivate() {
    final state = ref.read(noteEditorProvider(widget.noteId));
    if (state.isEditing && !state.isNoteEmpty) {
      ref.read(noteEditorProvider(widget.noteId).notifier).saveNote(handwritingStrokes: _handwritingController.strokes);
    }
    super.deactivate();
  }

  @override
  void dispose() {
    _audioPlayer.dispose();
    _handwritingController.dispose();
    _transactionSub?.cancel();
    if (_editorStateRef != null) {
      if (_onToggledStyleChanged != null) {
        _editorStateRef!.toggledStyleNotifier.removeListener(_onToggledStyleChanged!);
      }
      if (_onSelectionChanged != null) {
        _editorStateRef!.selectionNotifier.removeListener(_onSelectionChanged!);
      }
    }
    _scrollController?.dispose();
    _editorFocusNode.dispose();
    _titleFocusNode.dispose();
    _titleController.dispose();
    super.dispose();
  }

  Future<void> _onBack() async {
    final state = ref.read(noteEditorProvider(widget.noteId));
    if (state.isEditing && !state.isNoteEmpty) {
      await ref.read(noteEditorProvider(widget.noteId).notifier).saveNote(handwritingStrokes: _handwritingController.strokes);
    }
    if (mounted) context.pop();
  }

  void _requestEditorFocus() {
    final es = ref.read(noteEditorProvider(widget.noteId).notifier).editorState;
    if (es == null) return;
    Node? lastTextNode;
    for (final node in es.document.root.children.reversed) {
      if (node.delta != null) {
        lastTextNode = node;
        break;
      }
    }
    if (lastTextNode == null) return;
    final offset = lastTextNode.delta!.toPlainText().length;
    es.updateSelectionWithReason(
      Selection.collapsed(Position(path: lastTextNode.path, offset: offset)),
      reason: SelectionUpdateReason.uiEvent,
    );
    _editorFocusNode.requestFocus();
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
      child: Scaffold(
        backgroundColor: _bgColorForKey(state.pendingBackground),
        body: SafeArea(
          child: Column(
            children: [
              EditorTopBar(
                isEditing: state.isEditing,
                canUndo: state.canUndo,
                canRedo: state.canRedo,
                onBack: _onBack,
                onUndo: () {
                  if (state.isHandwritingMode) {
                    _handwritingController.undo();
                  } else {
                    notifier.editorState?.undoManager.undo();
                  }
                },
                onRedo: () {
                  if (state.isHandwritingMode) {
                    _handwritingController.redo();
                  } else {
                    notifier.editorState?.undoManager.redo();
                  }
                },
                onDone: () async {
                  if (state.isHandwritingMode) {
                    notifier.exitHandwritingMode();
                    WidgetsBinding.instance.addPostFrameCallback((_) {
                      _requestEditorFocus();
                    });
                  } else {
                    await notifier.saveNote(handwritingStrokes: _handwritingController.strokes);
                    notifier.exitEditMode();
                  }
                },
              ),
              Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppDimens.editorContentPadding,
                ),
                child: TextField(
                  controller: _titleController,
                  focusNode: _titleFocusNode,
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
              MetadataStrip(
                updatedAt: state.loadedNote?.updatedAt,
                notebookName: state.notebookName,
                notebookColor: state.notebookColor,
                categoryName: state.categoryName,
                isEditing: state.isEditing,
                onNotebookTap: () => _showNotebookPicker(notifier),
                onCategoryTap: () => _showCategoryPicker(notifier),
              ),
              Expanded(
                child: Stack(
                  children: [
                    if (state.isHandwritingMode)
                      IgnorePointer(
                        ignoring: true,
                        child: Opacity(
                          opacity: 0.5,
                          child: _buildEditor(notifier, state),
                        ),
                      )
                    else
                      _buildEditor(notifier, state),
                    if (_editorReady && state.isHandwritingMode)
                      ClipRect(
                        child: HandwritingOverlay(
                          controller: _handwritingController,
                          isActive: state.isHandwritingMode,
                          currentBrush: state.currentBrush,
                          currentColor: state.currentBrushColor,
                          currentWidth: state.currentBrushWidth,
                          isErasing: state.isErasing,
                          scrollOffset: _scrollController?.offsetNotifier.value ?? 0,
                        ),
                      ),
                    if (!state.isHandwritingMode && _handwritingController.strokes.isNotEmpty && _scrollController != null)
                      ClipRect(
                        child: ValueListenableBuilder<double>(
                          valueListenable: _scrollController!.offsetNotifier,
                          builder: (context, scrollOffset, _) {
                            return IgnorePointer(
                              child: CustomPaint(
                                painter: HandwritingPainter(
                                  strokes: _handwritingController.strokes,
                                  inProgressPoints: const [],
                                  currentBrush: BrushType.pen,
                                  currentColor: '#000000',
                                  currentWidth: 3,
                                  devicePixelRatio: MediaQuery.of(context).devicePixelRatio,
                                  scrollOffset: scrollOffset,
                                ),
                                size: Size.infinite,
                              ),
                            );
                          },
                        ),
                      ),
                    if (!state.isEditing)
                      Positioned.fill(
                        child: GestureDetector(
                          onTap: () {
                            notifier.enterEditMode();
                            WidgetsBinding.instance.addPostFrameCallback((_) => _requestEditorFocus());
                          },
                        ),
                      ),
                  ],
                ),
              ),
              _buildBottomBar(notifier, state),
            ],
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
      editable: true,
      focusNode: _editorFocusNode,
      editorScrollController: _scrollController,
      blockComponentBuilders: _buildBlockComponentBuilders(),
      commandShortcutEvents: [
        _backspaceDeleteMediaCommand(editorState),
        ...standardCommandShortcutEvents,
      ],
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

  CommandShortcutEvent _backspaceDeleteMediaCommand(EditorState es) {
    return CommandShortcutEvent(
      key: 'backspace delete media',
      getDescription: () => 'Delete media block on backspace at line start',
      command: 'backspace',
      handler: (editorState) {
        final selection = editorState.selection;
        if (selection == null || !selection.isCollapsed) {
          return KeyEventResult.ignored;
        }
        if (selection.start.offset != 0) return KeyEventResult.ignored;

        final node = editorState.getNodeAtPath(selection.start.path);
        if (node == null || node.delta == null) return KeyEventResult.ignored;

        final prev = node.previous;
        if (prev == null) return KeyEventResult.ignored;

        if (prev.type == ImageBlockKeys.type ||
            prev.type == AudioBlockKeys.type) {
          final transaction = editorState.transaction;
          transaction.deleteNode(prev);
          transaction.afterSelection = selection;
          editorState.apply(transaction);
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
    );
  }

  Map<String, BlockComponentBuilder> _buildBlockComponentBuilders() {
    final state = ref.read(noteEditorProvider(widget.noteId));
    final notifier = ref.read(noteEditorProvider(widget.noteId).notifier);
    void deleteNode(Node node) {
      final es = notifier.editorState;
      if (es == null) return;
      final nextTextNode = node.next ?? node.previous;
      final transaction = es.transaction;
      transaction.deleteNode(node);
      if (nextTextNode != null && nextTextNode.delta != null) {
        transaction.afterSelection = Selection.collapsed(
          Position(path: nextTextNode.path, offset: 0),
        );
      }
      es.apply(transaction);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _editorFocusNode.requestFocus();
      });
    }
    return {
      ...standardBlockComponentBuilderMap,
      TodoListBlockKeys.type: TodoListBlockComponentBuilder(
        configuration: const BlockComponentConfiguration(),
        textStyleBuilder: (checked) => TextStyle(
          decoration: checked ? TextDecoration.lineThrough : null,
          color: checked ? AppColors.textHint : AppColors.textPrimary,
        ),
      ),
      ImageBlockKeys.type: CustomImageBlockComponentBuilder(
        editable: state.isEditing,
        onDelete: deleteNode,
      ),
      AudioBlockKeys.type: AudioBlockComponentBuilder(
        noteId: state.noteId,
        audioPlayer: _audioPlayer,
        editable: state.isEditing,
        basePath: _appDocPath,
        onDelete: deleteNode,
      ),
    };
  }

  Future<void> _showNotebookPicker(NoteEditorNotifier notifier) async {
    final notebooks = await notifier.loadNotebooks();
    if (!mounted) return;
    final selected = await showMenu<int?>(
      context: context,
      position: RelativeRect.fromLTRB(
        MediaQuery.of(context).size.width - 200,
        200, 16, 0,
      ),
      items: [
        const PopupMenuItem<int?>(value: null, child: Text('全部笔记')),
        ...notebooks.map((nb) => PopupMenuItem<int?>(
          value: nb.id,
          child: Row(children: [
            Container(width: 10, height: 10, decoration: BoxDecoration(
              color: AppColorUtils.parseHex(nb.color) ?? AppColors.primary,
              shape: BoxShape.circle,
            )),
            const SizedBox(width: 8),
            Text(nb.name),
          ]),
        )),
      ],
    );
    if (selected != null || notebooks.isNotEmpty) {
      await notifier.setNotebook(selected);
    }
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
    if (state.isHandwritingMode) {
      return HandwritingToolbar(
        currentBrush: state.currentBrush,
        currentColor: state.currentBrushColor,
        currentWidth: state.currentBrushWidth,
        isErasing: state.isErasing,
        onBrushSelected: notifier.setBrush,
        onColorTap: () => _showHandwritingStylePicker(notifier, state),
        onBrushWidthTap: (type) => _showBrushWidthPicker(notifier, state),
        onEraserTap: notifier.toggleEraser,
      );
    }
    if (state.isEditing && _showStylePanel) {
      final es = notifier.editorState;
      if (es != null) {
        return StylePickerPanel(
          editorState: es,
          onBackgroundChanged: notifier.setBackground,
          onClose: _closeStylePanel,
        );
      }
    }
    if (state.isEditing) {
      return TextToolbar(
        editorState: notifier.editorState,
        onStyleTap: () {
          final es = notifier.editorState;
          if (es != null && es.selection == null) {
            final lastNode = es.document.root.children.lastOrNull;
            if (lastNode != null) {
              final offset = lastNode.delta?.toPlainText().length ?? 0;
              es.updateSelectionWithReason(
                Selection.collapsed(Position(path: lastNode.path, offset: offset)),
                reason: SelectionUpdateReason.uiEvent,
              );
            }
          }
          SystemChannels.textInput.invokeMethod('TextInput.hide');
          setState(() => _showStylePanel = true);
        },
        onImageTap: () => _showImageSourcePicker(notifier),
        onRecordTap: () => notifier.startRecording(context),
        onHandwritingTap: () => notifier.enterHandwritingMode(),
      );
    }
    final note = state.loadedNote;
    final plainText = [state.title, note?.plainText ?? '']
        .where((s) => s.isNotEmpty)
        .join('\n');
    return BrowseBottomBar(
      isFavorite: note?.isFavorite ?? false,
      shareText: plainText,
      onToggleFavorite: () => notifier.toggleFavorite(),
      onDelete: () async {
        await notifier.softDelete();
        if (mounted) context.pop();
      },
    );
  }

  void _closeStylePanel() {
    setState(() => _showStylePanel = false);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _requestEditorFocus();
      _reapplySavedStyles();
    });
  }

  void _reapplySavedStyles() {
    if (_savedToggledStyle.isEmpty) return;
    final es = _editorStateRef;
    if (es == null) return;
    for (final entry in _savedToggledStyle.entries) {
      es.updateToggledStyle(entry.key, entry.value);
    }
  }

  Future<void> _showImageSourcePicker(NoteEditorNotifier notifier) async {
    final source = await showModalBottomSheet<String>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('从相册选择'),
              onTap: () => Navigator.pop(ctx, 'gallery'),
            ),
            ListTile(
              leading: const Icon(Icons.camera_alt_outlined),
              title: const Text('拍照'),
              onTap: () => Navigator.pop(ctx, 'camera'),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (source == null) return;
    await notifier.insertImage(useCamera: source == 'camera');
  }

  Color _bgColorForKey(String key) {
    switch (key) {
      case 'linen': return AppColors.bgLinen;
      case 'kraft': return AppColors.bgKraft;
      case 'grid': return AppColors.bgGrid;
      default: return Colors.white;
    }
  }

  void _showHandwritingStylePicker(NoteEditorNotifier notifier, NoteEditorState state) {
    showHandwritingStylePickerSheet(
      context,
      currentBrush: state.currentBrush,
      currentColor: state.currentBrushColor,
      currentWidth: state.currentBrushWidth,
      onBrushSelected: notifier.setBrush,
      onColorSelected: notifier.setBrushColor,
      onWidthSelected: notifier.setBrushWidth,
    );
  }

  void _showBrushWidthPicker(NoteEditorNotifier notifier, NoteEditorState state) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        contentPadding: const EdgeInsets.all(16),
        content: BrushWidthPicker(
          currentWidth: state.currentBrushWidth,
          brushColor: state.currentBrushColor,
          onWidthSelected: (w) {
            notifier.setBrushWidth(w);
            Navigator.pop(ctx);
          },
        ),
      ),
    );
  }
}
