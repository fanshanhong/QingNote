import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:go_router/go_router.dart';
import '../models/note.dart';
import '../providers/note_list_provider.dart';
import '../repositories/note_repository.dart';
import '../theme.dart';
import '../utils/color_utils.dart';
import '../widgets/delete_confirm_sheet.dart';
import '../widgets/filter_panel.dart';
import '../widgets/note_card.dart';
import '../widgets/note_list_header.dart';
import '../widgets/note_search_bar.dart';
import '../widgets/sort_sheet.dart';

class NoteListPage extends ConsumerStatefulWidget {
  const NoteListPage({super.key});

  @override
  ConsumerState<NoteListPage> createState() => _NoteListPageState();
}

class _NoteListPageState extends ConsumerState<NoteListPage> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(noteListProvider.notifier).init());
  }

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(noteListProvider);
    final notifier = ref.read(noteListProvider.notifier);

    return Scaffold(
      backgroundColor: _pageBackground(s),
      body: SafeArea(
        child: Column(children: [
          NoteListHeader(
            title: s.headerTitle,
            subtitle: s.headerSubtitle,
            isBatchMode: s.isBatchMode,
            selectedCount: s.selectedIds.length,
            filterPanelVisible: s.filterPanelVisible,
            onToggleFilterPanel: notifier.toggleFilterPanel,
            onExitBatchMode: () => notifier.exitBatchMode(),
            onOverflowTap: () => _showOverflowMenu(context, s, notifier),
          ),
          if (!s.isBatchMode && !s.filterPanelVisible)
            NoteSearchBar(
              initialQuery: s.query,
              onQueryChanged: (q) => notifier.setQuery(q),
            ),
          Expanded(
            child: Stack(
              children: [
                (s.notes.isEmpty && !s.isLoading)
                    ? _buildEmptyState()
                    : _buildNoteList(s, notifier),
                if (s.filterPanelVisible) ...[
                  GestureDetector(
                    onTap: notifier.toggleFilterPanel,
                    child: Container(
                      color: Colors.black.withValues(alpha: 0.3),
                    ),
                  ),
                  const FilterPanel(),
                ],
              ],
            ),
          ),
          if (s.isBatchMode) _buildBatchBottomBar(s, notifier),
        ]),
      ),
      floatingActionButton:
          (s.isBatchMode || s.filterPanelVisible) ? null : FloatingActionButton(
            onPressed: () async {
              await context.push('/editor/0');
              if (mounted) ref.read(noteListProvider.notifier).reload();
            },
            child: const Icon(Icons.add),
          ),
    );
  }

  Color _pageBackground(NoteListState s) {
    if (s.filter is NotebookFilter) {
      final nbId = (s.filter as NotebookFilter).notebookId;
      final hex = s.notebookColorMap[nbId];
      if (hex != null) {
        final c = AppColorUtils.parseHex(hex);
        if (c != null) return AppColorUtils.tint(c, 25);
      }
    }
    return AppColors.bgWindow;
  }

  Widget _buildNoteList(NoteListState s, NoteListNotifier notifier) {
    if (s.isGridView) {
      return MasonryGridView.count(
        crossAxisCount: 2,
        itemCount: s.notes.length,
        itemBuilder: (context, i) => _buildCard(s, notifier, s.notes[i]),
      );
    }
    return ListView.builder(
      itemCount: s.notes.length,
      itemBuilder: (context, i) => _buildCard(s, notifier, s.notes[i]),
    );
  }

  Widget _buildCard(NoteListState s, NoteListNotifier notifier, Note note) {
    return NoteCard(
      key: ValueKey(note.id),
      note: note,
      isBatchMode: s.isBatchMode,
      isSelected: s.selectedIds.contains(note.id),
      notebookColor: note.notebookId != null ? s.notebookColorMap[note.notebookId] : null,
      onTap: () async {
        await context.push('/editor/${note.id}');
        if (mounted) ref.read(noteListProvider.notifier).reload();
      },
      onLongPress: () => _showCardMenu(context, note, s, notifier),
      onBatchToggle: (_) => notifier.toggleSelection(note.id),
    );
  }

  Widget _buildEmptyState() {
    return const Center(child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.note_outlined, size: 64, color: AppColors.textHint),
        SizedBox(height: AppDimens.spacingS),
        Text('暂无笔记', style: TextStyle(fontSize: AppDimens.textBody, color: AppColors.textHint)),
      ],
    ));
  }

  Widget _buildBatchBottomBar(NoteListState s, NoteListNotifier notifier) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppDimens.spacingL),
      child: ElevatedButton(
        onPressed: s.selectedIds.isEmpty ? null : () async {
          final confirmed = await showDeleteConfirmSheet(context,
            message: '确定要删除选中的 ${s.selectedIds.length} 条笔记吗？\n删除后可在"最近删除"中恢复',
            confirmLabel: '删除');
          if (confirmed) await notifier.batchDelete();
        },
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.danger, foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(vertical: 14),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
        child: const Text('删除', style: TextStyle(fontSize: AppDimens.textBody)),
      ),
    );
  }

  Future<void> _showOverflowMenu(BuildContext ctx, NoteListState s, NoteListNotifier notifier) async {
    final value = await showMenu<String>(
      context: ctx,
      position: RelativeRect.fromLTRB(MediaQuery.of(ctx).size.width - 48, 80, 8, 0),
      items: [
        const PopupMenuItem(value: 'sort', child: Text('排序方式')),
        PopupMenuItem(value: 'toggle_view',
          child: Text(s.isGridView ? '切换为列表视图' : '切换为宫格视图')),
        const PopupMenuItem(value: 'batch', child: Text('批量删除')),
        const PopupMenuItem(value: 'settings', child: Text('设置')),
      ],
    );
    if (value == null || !mounted) return;
    switch (value) {
      case 'sort':
        final result = await showSortSheet(context, current: s.sortBy);
        if (result != null) await notifier.setSort(result);
      case 'toggle_view': await notifier.toggleGridView();
      case 'batch': notifier.enterBatchMode();
      case 'settings': context.push('/settings');
    }
  }

  Future<void> _showCardMenu(BuildContext ctx, Note note, NoteListState s, NoteListNotifier notifier) async {
    final isDeleted = s.filter is DeletedFilter;
    final value = await showMenu<String>(
      context: ctx,
      position: RelativeRect.fromLTRB(MediaQuery.of(ctx).size.width / 2, 300, 50, 0),
      items: isDeleted
          ? [const PopupMenuItem(value: 'restore', child: Text('恢复')),
             const PopupMenuItem(value: 'perm_delete',
               child: Text('永久删除', style: TextStyle(color: Color(0xFFFF4444))))]
          : [PopupMenuItem(value: 'fav', child: Text(note.isFavorite ? '取消收藏' : '收藏')),
             const PopupMenuItem(value: 'delete', child: Text('删除')),
             const PopupMenuItem(value: 'move', child: Text('移入笔记本'))],
    );
    if (value == null || !mounted) return;
    switch (value) {
      case 'fav': await notifier.toggleFavorite(note.id);
      case 'delete':
        final ok = await showDeleteConfirmSheet(context,
          message: '确定要删除这条笔记吗？\n删除后可在"最近删除"中恢复', confirmLabel: '删除');
        if (ok) await notifier.softDelete(note.id);
      case 'restore': await notifier.restore(note.id);
      case 'perm_delete':
        final ok = await showDeleteConfirmSheet(context,
          message: '确定要永久删除这条笔记吗？\n此操作不可恢复', confirmLabel: '永久删除');
        if (ok) await notifier.deletePermanently(note.id);
      case 'move':
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('移入笔记本将在 Phase 2E 实现')));
    }
  }
}
