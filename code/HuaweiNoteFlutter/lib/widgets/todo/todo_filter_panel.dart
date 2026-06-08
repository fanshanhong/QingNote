import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../models/folder.dart';
import '../../models/notebook.dart';
import '../../providers/todo_list_provider.dart';
import '../../providers/repository_providers.dart';
import '../../theme.dart';

class TodoFilterPanel extends ConsumerStatefulWidget {
  const TodoFilterPanel({super.key});

  @override
  ConsumerState<TodoFilterPanel> createState() => _TodoFilterPanelState();
}

class _TodoFilterPanelState extends ConsumerState<TodoFilterPanel> {
  Future<List<_FilterRow>>? _rowsFuture;
  Set<int> _expandedFolders = {};

  @override
  void initState() {
    super.initState();
    _rowsFuture = _buildRows();
  }

  @override
  Widget build(BuildContext context) {
    final listState = ref.watch(todoListProvider);
    return FutureBuilder<List<_FilterRow>>(
      future: _rowsFuture,
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const SizedBox.shrink();
        final rows = snapshot.data!;
        return Material(
          color: Colors.white,
          borderRadius:
              const BorderRadius.vertical(bottom: Radius.circular(12)),
          elevation: 4,
          shadowColor: Colors.black26,
          child: ListView.builder(
            shrinkWrap: true,
            padding: EdgeInsets.zero,
            itemCount: rows.length,
            itemBuilder: (context, index) =>
                _buildRowWidget(context, rows[index], listState.filter),
          ),
        );
      },
    );
  }

  Future<List<_FilterRow>> _buildRows() async {
    final todoRepo = ref.read(todoRepositoryProvider);
    final folderRepo = ref.read(folderRepositoryProvider);
    final notebookRepo = ref.read(notebookRepositoryProvider);
    final rows = <_FilterRow>[];

    final allCount = await todoRepo.count();
    final uncatCount = await todoRepo.countUncategorized();
    final delCount = await todoRepo.count(includeDeleted: true);

    rows.add(_PseudoRow(
        '全部待办', Icons.list_alt, allCount, TodoListFilter.all));
    rows.add(_PseudoRow('未分类', Icons.folder_off_outlined, uncatCount,
        TodoListFilter.uncategorized));
    rows.add(_PseudoRow(
        '最近删除', Icons.delete_outline, delCount, TodoListFilter.deleted));
    rows.add(const _DividerRow());
    rows.add(const _SectionHeaderRow());

    final folders = await folderRepo.list();

    if (_expandedFolders.isEmpty && folders.isNotEmpty) {
      _expandedFolders = folders.map((f) => f.id).toSet();
    }

    for (final folder in folders) {
      final fCount = await todoRepo.count(folderId: folder.id);
      final expanded = _expandedFolders.contains(folder.id);
      rows.add(_FolderHeadRow(folder, expanded, fCount));
      if (expanded) {
        final notebooks = await notebookRepo.listByFolder(folder.id);
        for (final nb in notebooks) {
          rows.add(_NotebookRow(nb));
        }
      }
    }
    return rows;
  }

  Widget _buildRowWidget(
      BuildContext ctx, _FilterRow row, TodoListFilter current) {
    return switch (row) {
      _PseudoRow r => _buildPseudoTile(r, current),
      _DividerRow _ => const Divider(
          height: 1,
          thickness: 0.5,
          color: AppColors.divider,
          indent: 16,
          endIndent: 16),
      _SectionHeaderRow _ => _buildSectionHeader(ctx),
      _FolderHeadRow r => _buildFolderHead(r),
      _NotebookRow r => _buildNotebookTile(r, current),
    };
  }

  bool _filtersEqual(TodoListFilter a, TodoListFilter b) {
    if (a is TodoAllFilter && b is TodoAllFilter) return true;
    if (a is TodoUncategorizedFilter && b is TodoUncategorizedFilter) {
      return true;
    }
    if (a is TodoDeletedFilter && b is TodoDeletedFilter) return true;
    if (a is TodoFolderFilter && b is TodoFolderFilter) {
      return a.folderId == b.folderId;
    }
    return false;
  }

  Widget _buildPseudoTile(_PseudoRow r, TodoListFilter current) {
    final sel = _filtersEqual(r.filter, current);
    return InkWell(
      onTap: () => ref.read(todoListProvider.notifier).setFilter(r.filter),
      child: Container(
        decoration: BoxDecoration(
          color: sel ? AppColors.primaryLight : Colors.transparent,
          border: sel
              ? const Border(
                  left: BorderSide(color: AppColors.primary, width: 3))
              : null,
        ),
        padding: const EdgeInsets.symmetric(
            horizontal: AppDimens.spacingL, vertical: 14),
        child: Row(children: [
          Icon(r.icon,
              size: 20,
              color: sel ? AppColors.primary : AppColors.textPrimary),
          const SizedBox(width: AppDimens.spacingM),
          Expanded(
              child: Text(r.label,
                  style: TextStyle(
                    fontSize: AppDimens.textBody,
                    color: sel ? AppColors.primary : AppColors.textPrimary,
                    fontWeight: sel ? FontWeight.w500 : FontWeight.normal,
                  ))),
          Text('${r.count}',
              style: TextStyle(
                fontSize: AppDimens.textCaption + 1,
                color: sel ? AppColors.primary : AppColors.textHint,
              )),
        ]),
      ),
    );
  }

  Widget _buildSectionHeader(BuildContext ctx) {
    return Padding(
      padding: const EdgeInsets.symmetric(
          horizontal: AppDimens.spacingL, vertical: AppDimens.spacingM),
      child: Row(children: [
        const Expanded(
            child: Text('文件夹',
                style: TextStyle(
                  fontSize: AppDimens.textCaption + 1,
                  color: AppColors.textSecondary,
                  fontWeight: FontWeight.w500,
                ))),
        GestureDetector(
          onTap: () async {
            ref.read(todoListProvider.notifier).toggleFilterPanel();
            final result = await ctx.push<bool>('/folder-manager');
            if (result == true && mounted) {
              setState(() {
                _rowsFuture = _buildRows();
              });
            }
          },
          child: const Text('管理',
              style: TextStyle(
                fontSize: AppDimens.textCaption + 1,
                color: AppColors.primary,
              )),
        ),
      ]),
    );
  }

  Widget _buildFolderHead(_FolderHeadRow r) {
    return InkWell(
      onTap: () {
        setState(() {
          if (_expandedFolders.contains(r.folder.id)) {
            _expandedFolders.remove(r.folder.id);
          } else {
            _expandedFolders.add(r.folder.id);
          }
          _rowsFuture = _buildRows();
        });
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(
            horizontal: AppDimens.spacingL, vertical: AppDimens.spacingM),
        child: Row(children: [
          const Icon(Icons.folder_outlined,
              size: 20, color: AppColors.textPrimary),
          const SizedBox(width: AppDimens.spacingS),
          Expanded(
              child: Text(r.folder.name,
                  style: const TextStyle(
                    fontSize: AppDimens.textBody,
                    color: AppColors.textPrimary,
                  ))),
          Text('${r.count}',
              style: const TextStyle(
                fontSize: AppDimens.textCaption + 1,
                color: AppColors.textHint,
              )),
          const SizedBox(width: AppDimens.spacingS),
          AnimatedRotation(
            turns: r.expanded ? 0.5 : 0,
            duration: const Duration(milliseconds: 200),
            child: const Icon(Icons.arrow_drop_down,
                size: 20, color: AppColors.textHint),
          ),
        ]),
      ),
    );
  }

  Widget _buildNotebookTile(_NotebookRow r, TodoListFilter current) {
    final filter = TodoListFilter.folder(r.notebook.folderId);
    final sel = _filtersEqual(filter, current);
    final cleaned = r.notebook.color.replaceFirst('#', '');
    final dotColor = int.tryParse(cleaned, radix: 16);
    return InkWell(
      onTap: () => ref.read(todoListProvider.notifier).setFilter(filter),
      child: Container(
        color: sel ? AppColors.primaryLight : Colors.transparent,
        padding: const EdgeInsets.fromLTRB(48, 10, AppDimens.spacingL, 10),
        child: Row(children: [
          Icon(Icons.menu_book,
              size: 20,
              color: dotColor != null
                  ? Color(0xFF000000 | dotColor)
                  : AppColors.textHint),
          const SizedBox(width: 10),
          Expanded(
              child: Text(r.notebook.name,
                  style: TextStyle(
                    fontSize: AppDimens.textBody,
                    color: sel ? AppColors.primary : AppColors.textPrimary,
                  ))),
        ]),
      ),
    );
  }
}

sealed class _FilterRow {
  const _FilterRow();
}

class _PseudoRow extends _FilterRow {
  final String label;
  final IconData icon;
  final int count;
  final TodoListFilter filter;
  const _PseudoRow(this.label, this.icon, this.count, this.filter);
}

class _DividerRow extends _FilterRow {
  const _DividerRow();
}

class _SectionHeaderRow extends _FilterRow {
  const _SectionHeaderRow();
}

class _FolderHeadRow extends _FilterRow {
  final Folder folder;
  final bool expanded;
  final int count;
  const _FolderHeadRow(this.folder, this.expanded, this.count);
}

class _NotebookRow extends _FilterRow {
  final Notebook notebook;
  const _NotebookRow(this.notebook);
}
