import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/folder.dart';
import '../models/todo.dart';
import '../providers/todo_list_provider.dart';
import '../providers/repository_providers.dart';
import '../theme.dart';
import '../utils/todo_group_utils.dart';
import '../widgets/delete_confirm_sheet.dart';
import '../widgets/todo/todo_card.dart';
import '../widgets/todo/todo_filter_panel.dart';
import '../widgets/todo/todo_list_header.dart';
import '../widgets/todo/todo_quick_add_bar.dart';
import '../widgets/todo/todo_section_header.dart';

class TodoListPage extends ConsumerStatefulWidget {
  const TodoListPage({super.key});

  @override
  ConsumerState<TodoListPage> createState() => _TodoListPageState();
}

class _TodoListPageState extends ConsumerState<TodoListPage> {
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_initialized) {
        _initialized = true;
        ref.read(todoListProvider.notifier).init();
      }
    });
  }

  void _onQuickAddSave(
      String title, int remindAt, bool isImportant, RepeatType repeatType) {
    ref.read(todoListProvider.notifier).quickAdd(
          title: title,
          remindAt: remindAt,
          isImportant: isImportant,
          repeatType: repeatType,
        );
  }

  void _showOverflowMenu() {
    final state = ref.read(todoListProvider);
    final renderBox = context.findRenderObject() as RenderBox;
    final size = renderBox.size;
    showMenu(
      context: context,
      position: RelativeRect.fromLTRB(size.width - 60, 80, 16, 0),
      items: [
        PopupMenuItem(
          value: 'toggle_completed',
          child:
              Text(state.hideCompleted ? '显示已完成待办' : '隐藏已完成待办'),
        ),
        const PopupMenuItem(
            value: 'batch_delete', child: Text('批量删除')),
      ],
    ).then((value) {
      if (value == 'toggle_completed') {
        ref.read(todoListProvider.notifier).toggleHideCompleted();
      } else if (value == 'batch_delete') {
        ref.read(todoListProvider.notifier).enterBatchMode();
      }
    });
  }

  Future<void> _confirmBatchDelete() async {
    final count = ref.read(todoListProvider).selectedIds.length;
    if (count == 0) return;
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '确定删除选中的 $count 条待办？',
      confirmLabel: '删除',
    );
    if (confirmed) {
      ref.read(todoListProvider.notifier).batchDelete();
    }
  }

  Future<void> _confirmDeletePermanently(int todoId) async {
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '彻底删除后不可恢复，确定删除？',
      confirmLabel: '彻底删除',
    );
    if (confirmed) {
      ref.read(todoListProvider.notifier).deletePermanently(todoId);
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(todoListProvider);
    final isDeletedView = state.filter is TodoDeletedFilter;

    return Scaffold(
      backgroundColor: AppColors.bgWindow,
      body: SafeArea(
        child: Column(
          children: [
            TodoListHeader(
              title: state.headerTitle,
              subtitle: state.headerSubtitle,
              filterPanelVisible: state.filterPanelVisible,
              isBatchMode: state.isBatchMode,
              batchCount: state.selectedIds.length,
              onTitleTap: () =>
                  ref.read(todoListProvider.notifier).toggleFilterPanel(),
              onOverflowTap: _showOverflowMenu,
              onBatchClose: () =>
                  ref.read(todoListProvider.notifier).exitBatchMode(),
            ),
            Expanded(
              child: state.filterPanelVisible
                  ? _buildFilterPanel(state)
                  : _buildTodoList(state, isDeletedView),
            ),
            if (state.isBatchMode) _buildBatchBottomBar(state),
            if (state.isQuickAddVisible && !state.isBatchMode)
              TodoQuickAddBar(onSave: _onQuickAddSave),
          ],
        ),
      ),
      floatingActionButton: (!state.filterPanelVisible &&
              !state.isBatchMode &&
              !state.isQuickAddVisible &&
              !isDeletedView)
          ? FloatingActionButton(
              onPressed: () =>
                  ref.read(todoListProvider.notifier).showQuickAdd(),
              child: const Icon(Icons.add))
          : null,
    );
  }

  Widget _buildFilterPanel(TodoListState state) {
    final todoRepo = ref.read(todoRepositoryProvider);
    final folderRepo = ref.read(folderRepositoryProvider);
    return FutureBuilder(
      future: Future.wait([
        todoRepo.count(),
        todoRepo.countUncategorized(),
        todoRepo.count(includeDeleted: true),
        folderRepo.list(),
      ]),
      builder: (context, snapshot) {
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final data = snapshot.data!;
        final allCount = data[0] as int;
        final uncatCount = data[1] as int;
        final delCount = data[2] as int;
        final folders = data[3] as List<Folder>;
        return TodoFilterPanel(
          currentFilter: state.filter,
          allCount: allCount,
          uncategorizedCount: uncatCount,
          deletedCount: delCount,
          folders: folders,
          folderCounts: const {},
          onFilterSelected: (f) =>
              ref.read(todoListProvider.notifier).setFilter(f),
        );
      },
    );
  }

  Widget _buildTodoList(TodoListState state, bool isDeletedView) {
    if (state.todos.isEmpty) {
      return Center(
        child: Text(
          isDeletedView ? '暂无已删除待办' : '暂无待办',
          style: const TextStyle(
              color: AppColors.textHint, fontSize: AppDimens.textBody),
        ),
      );
    }
    final groups = groupTodos(state.todos);
    return ListView.builder(
      itemCount: _totalItemCount(groups),
      itemBuilder: (context, index) =>
          _buildItem(groups, index, state, isDeletedView),
    );
  }

  int _totalItemCount(List<TodoGroupItem> groups) {
    int count = 0;
    for (final g in groups) {
      count += 1 + g.todos.length;
    }
    return count;
  }

  Widget _buildItem(List<TodoGroupItem> groups, int index,
      TodoListState state, bool isDeletedView) {
    int offset = 0;
    for (final g in groups) {
      if (index == offset) {
        return TodoSectionHeader(title: g.label, isOverdue: g.isOverdue);
      }
      offset++;
      if (index < offset + g.todos.length) {
        final todo = g.todos[index - offset];
        return TodoCard(
          todo: todo,
          isBatchMode: state.isBatchMode,
          isSelected: state.selectedIds.contains(todo.id),
          isDeletedView: isDeletedView,
          onCheckToggle: () {
            if (todo.isCompleted) {
              ref.read(todoListProvider.notifier).uncomplete(todo.id);
            } else {
              ref.read(todoListProvider.notifier).toggleComplete(todo.id);
            }
          },
          onTap: () {},
          onBatchToggle: () => ref
              .read(todoListProvider.notifier)
              .toggleBatchSelection(todo.id),
          onRestore: () =>
              ref.read(todoListProvider.notifier).restore(todo.id),
          onDeletePermanently: () => _confirmDeletePermanently(todo.id),
        );
      }
      offset += g.todos.length;
    }
    return const SizedBox.shrink();
  }

  Widget _buildBatchBottomBar(TodoListState state) {
    return Container(
      padding: const EdgeInsets.all(AppDimens.spacingL),
      decoration: const BoxDecoration(
        color: AppColors.bgCard,
        border: Border(top: BorderSide(color: AppColors.divider)),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          width: double.infinity,
          child: TextButton(
            onPressed:
                state.selectedIds.isEmpty ? null : _confirmBatchDelete,
            style: TextButton.styleFrom(
              backgroundColor: state.selectedIds.isEmpty
                  ? AppColors.divider
                  : AppColors.danger,
              padding: const EdgeInsets.symmetric(vertical: 12),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8)),
            ),
            child: Text('删除 (${state.selectedIds.length})',
                style: TextStyle(
                    color: state.selectedIds.isEmpty
                        ? AppColors.textHint
                        : Colors.white)),
          ),
        ),
      ),
    );
  }
}
