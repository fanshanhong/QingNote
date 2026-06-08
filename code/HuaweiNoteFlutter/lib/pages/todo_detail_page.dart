// lib/pages/todo_detail_page.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:share_plus/share_plus.dart';
import '../providers/todo_detail_provider.dart';
import '../providers/repository_providers.dart';
import '../theme.dart';
import '../widgets/delete_confirm_sheet.dart';
import '../widgets/todo/date_time_picker_sheet.dart';
import '../widgets/todo/repeat_picker_sheet.dart';

class TodoDetailPage extends ConsumerStatefulWidget {
  final int todoId;
  const TodoDetailPage({super.key, required this.todoId});

  @override
  ConsumerState<TodoDetailPage> createState() => _TodoDetailPageState();
}

class _TodoDetailPageState extends ConsumerState<TodoDetailPage>
    with WidgetsBindingObserver {
  final _titleController = TextEditingController();
  final _memoController = TextEditingController();
  final _titleFocusNode = FocusNode();
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    Future.microtask(() async {
      final notifier = ref.read(todoDetailProvider(widget.todoId).notifier);
      await notifier.load();
      final state = ref.read(todoDetailProvider(widget.todoId));
      _titleController.text = state.title;
      _memoController.text = state.memo;
      _loaded = true;
      if (widget.todoId == 0) {
        _titleFocusNode.requestFocus();
      }
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _titleController.dispose();
    _memoController.dispose();
    _titleFocusNode.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState lifecycleState) {
    if (lifecycleState == AppLifecycleState.inactive && _loaded) {
      final notifier = ref.read(todoDetailProvider(widget.todoId).notifier);
      notifier.setTitle(_titleController.text);
      notifier.setMemo(_memoController.text);
      notifier.save();
    }
  }

  @override
  void deactivate() {
    _saveBeforePop();
    super.deactivate();
  }

  bool _savedBeforePop = false;

  void _saveBeforePop() {
    if (!_loaded || _savedBeforePop) return;
    _savedBeforePop = true;
    final notifier = ref.read(todoDetailProvider(widget.todoId).notifier);
    final title = _titleController.text;
    final memo = _memoController.text;
    Future.microtask(() {
      try {
        notifier.setTitle(title);
        notifier.setMemo(memo);
        notifier.save();
      } catch (_) {}
    });
  }

  void _onBack() {
    if (!_loaded) {
      context.pop(false);
      return;
    }
    _savedBeforePop = true;
    final notifier = ref.read(todoDetailProvider(widget.todoId).notifier);
    notifier.setTitle(_titleController.text);
    notifier.setMemo(_memoController.text);
    notifier.save();
    context.pop(true);
  }

  Future<void> _pickRemindTime() async {
    final state = ref.read(todoDetailProvider(widget.todoId));
    final result =
        await showDateTimePickerSheet(context, initialEpochMs: state.remindAt);
    if (result != null) {
      ref.read(todoDetailProvider(widget.todoId).notifier).setRemindAt(result);
    }
  }

  Future<void> _pickRepeatType() async {
    final state = ref.read(todoDetailProvider(widget.todoId));
    final result =
        await showRepeatPickerSheet(context, current: state.repeatType);
    if (result != null) {
      ref
          .read(todoDetailProvider(widget.todoId).notifier)
          .setRepeatType(result);
    }
  }

  Future<void> _pickFolder() async {
    final folders = await ref.read(folderRepositoryProvider).list();
    if (!mounted) return;
    final state = ref.read(todoDetailProvider(widget.todoId));
    final names = <String>['未分类'];
    final ids = <int?>[null];
    for (final f in folders) {
      names.add(f.name);
      ids.add(f.id);
    }
    final currentIdx = ids.indexOf(state.folderId).clamp(0, ids.length - 1);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('选择分类'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: List.generate(
                names.length,
                (i) => RadioListTile<int>(
                      title: Text(names[i]),
                      value: i,
                      groupValue: currentIdx,
                      onChanged: (val) {
                        ref
                            .read(todoDetailProvider(widget.todoId).notifier)
                            .setFolder(ids[val!]);
                        Navigator.pop(ctx);
                      },
                    )),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('取消'),
          ),
        ],
      ),
    );
  }

  void _shareTodo() {
    final title = _titleController.text.trim();
    final memo = _memoController.text.trim();
    final text = [
      if (title.isNotEmpty) title,
      if (memo.isNotEmpty) memo,
    ].join('\n\n');
    if (text.isEmpty) return;
    SharePlus.instance.share(ShareParams(text: text));
  }

  Future<void> _deleteTodo() async {
    final state = ref.read(todoDetailProvider(widget.todoId));
    if (state.todoId <= 0) {
      context.pop(false);
      return;
    }
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '确定删除该待办？',
      confirmLabel: '删除',
    );
    if (confirmed) {
      await ref.read(todoDetailProvider(widget.todoId).notifier).delete();
      if (mounted) context.pop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(todoDetailProvider(widget.todoId));
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _onBack();
      },
      child: Scaffold(
        backgroundColor: AppColors.bgCard,
        body: SafeArea(
          child: Column(
            children: [
              _buildTopBar(),
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.symmetric(
                      horizontal: AppDimens.spacingXl),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildFolderIndicator(state),
                      const SizedBox(height: AppDimens.spacingM),
                      _buildTitleRow(state),
                      const Divider(height: 32),
                      _buildRemindRow(state),
                      const Divider(height: 32),
                      _buildRepeatRow(state),
                      const Divider(height: 32),
                      _buildImportantRow(state),
                      const Divider(height: 32),
                      _buildMemoSection(),
                    ],
                  ),
                ),
              ),
              _buildBottomBar(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTopBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          AppDimens.spacingS, AppDimens.spacingS, AppDimens.spacingL, 0),
      child: Row(
        children: [
          IconButton(
            onPressed: _onBack,
            icon: const Icon(Icons.arrow_back, color: AppColors.textPrimary),
          ),
        ],
      ),
    );
  }

  Widget _buildFolderIndicator(TodoDetailState state) {
    return GestureDetector(
      onTap: _pickFolder,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.folder_outlined,
              size: 16, color: AppColors.textHint),
          const SizedBox(width: 4),
          Text(state.folderName,
              style: const TextStyle(
                  fontSize: AppDimens.textCaption, color: AppColors.textHint)),
          const SizedBox(width: 2),
          const Icon(Icons.arrow_drop_down,
              size: 16, color: AppColors.textHint),
        ],
      ),
    );
  }

  Widget _buildTitleRow(TodoDetailState state) {
    return Row(
      children: [
        GestureDetector(
          onTap: () {
            final notifier =
                ref.read(todoDetailProvider(widget.todoId).notifier);
            notifier.setCompleted(!state.isCompleted);
          },
          child: Icon(
            state.isCompleted
                ? Icons.check_circle_outline
                : Icons.radio_button_unchecked,
            size: 24,
            color: state.isCompleted ? AppColors.primary : AppColors.textHint,
          ),
        ),
        const SizedBox(width: AppDimens.spacingM),
        Expanded(
          child: TextField(
            controller: _titleController,
            focusNode: _titleFocusNode,
            decoration: const InputDecoration(
              hintText: '待办事项',
              border: InputBorder.none,
              hintStyle: TextStyle(color: AppColors.textHint),
            ),
            style: const TextStyle(fontSize: 18, color: AppColors.textPrimary),
          ),
        ),
      ],
    );
  }

  Widget _buildRemindRow(TodoDetailState state) {
    final hasRemind = state.remindAt > 0;
    final textColor = hasRemind
        ? (state.isOverdue ? AppColors.danger : AppColors.primary)
        : AppColors.textHint;
    return GestureDetector(
      onTap: _pickRemindTime,
      child: Row(
        children: [
          const Icon(Icons.alarm, size: 22, color: AppColors.textHint),
          const SizedBox(width: AppDimens.spacingM),
          Expanded(
            child: Text(state.remindTimeText,
                style:
                    TextStyle(fontSize: AppDimens.textBody, color: textColor)),
          ),
          if (hasRemind)
            GestureDetector(
              onTap: () => ref
                  .read(todoDetailProvider(widget.todoId).notifier)
                  .clearRemind(),
              child:
                  const Icon(Icons.close, size: 20, color: AppColors.textHint),
            ),
        ],
      ),
    );
  }

  Widget _buildRepeatRow(TodoDetailState state) {
    return GestureDetector(
      onTap: _pickRepeatType,
      child: Row(
        children: [
          const Icon(Icons.repeat, size: 22, color: AppColors.textHint),
          const SizedBox(width: AppDimens.spacingM),
          const Expanded(
            child: Text('重复',
                style: TextStyle(
                    fontSize: AppDimens.textBody,
                    color: AppColors.textPrimary)),
          ),
          Text(state.repeatTypeText,
              style: const TextStyle(
                  fontSize: AppDimens.textBody, color: AppColors.textHint)),
          const SizedBox(width: 4),
          const Icon(Icons.chevron_right, size: 20, color: AppColors.textHint),
        ],
      ),
    );
  }

  Widget _buildImportantRow(TodoDetailState state) {
    return Row(
      children: [
        const Icon(Icons.priority_high, size: 22, color: AppColors.textHint),
        const SizedBox(width: AppDimens.spacingM),
        const Expanded(
          child: Text('重要',
              style: TextStyle(
                  fontSize: AppDimens.textBody, color: AppColors.textPrimary)),
        ),
        Switch(
          value: state.isImportant,
          onChanged: (v) => ref
              .read(todoDetailProvider(widget.todoId).notifier)
              .setImportant(v),
        ),
      ],
    );
  }

  Widget _buildMemoSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.subject, size: 22, color: AppColors.textHint),
            const SizedBox(width: AppDimens.spacingM),
            const Text('备注',
                style: TextStyle(
                    fontSize: AppDimens.textBody,
                    color: AppColors.textPrimary)),
          ],
        ),
        Padding(
          padding: const EdgeInsets.only(left: 34),
          child: TextField(
            controller: _memoController,
            maxLines: null,
            decoration: const InputDecoration(
              hintText: '添加备注',
              border: InputBorder.none,
              hintStyle: TextStyle(color: AppColors.textHint),
            ),
            style: const TextStyle(
                fontSize: AppDimens.textBody, color: AppColors.textPrimary),
          ),
        ),
      ],
    );
  }

  Widget _buildBottomBar() {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.bgCard,
        border: Border(top: BorderSide(color: AppColors.divider)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            Expanded(
              child: TextButton.icon(
                onPressed: _shareTodo,
                icon: const Icon(Icons.share_outlined, size: 20),
                label: const Text('分享'),
              ),
            ),
            Expanded(
              child: TextButton.icon(
                onPressed: _deleteTodo,
                icon: const Icon(Icons.delete_outline, size: 20),
                label: const Text('删除'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
