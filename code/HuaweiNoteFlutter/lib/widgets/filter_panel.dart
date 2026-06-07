import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../models/folder.dart';
import '../models/notebook.dart';
import '../providers/note_list_provider.dart';
import '../providers/repository_providers.dart';
import '../repositories/note_repository.dart';
import '../theme.dart';

class FilterPanel extends ConsumerStatefulWidget {
  const FilterPanel({super.key});

  @override
  ConsumerState<FilterPanel> createState() => _FilterPanelState();
}

class _FilterPanelState extends ConsumerState<FilterPanel> {
  Future<List<_FilterRow>>? _rowsFuture;
  Set<int> _lastExpandedFolders = {};

  @override
  void initState() {
    super.initState();
    _rowsFuture = _buildRows();
  }

  void _refreshIfNeeded(NoteListState s) {
    if (!setEquals(s.expandedFolders, _lastExpandedFolders)) {
      _lastExpandedFolders = Set.from(s.expandedFolders);
      _rowsFuture = _buildRows();
    }
  }

  @override
  Widget build(BuildContext context) {
    final listState = ref.watch(noteListProvider);
    _refreshIfNeeded(listState);
    return FutureBuilder<List<_FilterRow>>(
      future: _rowsFuture,
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const SizedBox.shrink();
        return Container(
          color: Colors.white,
          child: ListView.builder(
            itemCount: snapshot.data!.length,
            itemBuilder: (context, index) =>
                _buildRowWidget(context, snapshot.data![index], listState.filter),
          ),
        );
      },
    );
  }

  Future<List<_FilterRow>> _buildRows() async {
    final noteRepo = ref.read(noteRepositoryProvider);
    final folderRepo = ref.read(folderRepositoryProvider);
    final notebookRepo = ref.read(notebookRepositoryProvider);
    final s = ref.read(noteListProvider);
    final rows = <_FilterRow>[];

    rows.add(_PseudoRow('全部笔记', Icons.note_outlined,
        await noteRepo.count(filter: NoteListFilter.all), NoteListFilter.all));
    rows.add(_PseudoRow('未分类', Icons.folder_off_outlined,
        await noteRepo.count(filter: NoteListFilter.uncategorized), NoteListFilter.uncategorized));
    rows.add(_PseudoRow('收藏', Icons.star_border,
        await noteRepo.count(filter: NoteListFilter.favorite), NoteListFilter.favorite));
    rows.add(_PseudoRow('最近删除', Icons.delete_outline,
        await noteRepo.count(filter: NoteListFilter.deleted), NoteListFilter.deleted));
    rows.add(const _DividerRow());
    rows.add(const _SectionHeaderRow());

    for (final folder in await folderRepo.list()) {
      final fCount = await noteRepo.count(filter: NoteListFilter.folder(folder.id));
      final expanded = s.expandedFolders.contains(folder.id);
      rows.add(_FolderHeadRow(folder, expanded, fCount));
      if (expanded) {
        for (final nb in await notebookRepo.listByFolder(folder.id)) {
          final nbCount = await noteRepo.count(filter: NoteListFilter.notebook(nb.id));
          rows.add(_NotebookRow(nb, nbCount));
        }
      }
    }
    return rows;
  }

  Widget _buildRowWidget(BuildContext ctx, _FilterRow row, NoteListFilter current) {
    return switch (row) {
      _PseudoRow r => _buildPseudoTile(ctx, r, current),
      _DividerRow _ => const Divider(height: 1, indent: 16, endIndent: 16),
      _SectionHeaderRow _ => _buildSectionHeader(ctx),
      _FolderHeadRow r => _buildFolderHead(r),
      _NotebookRow r => _buildNotebookTile(ctx, r, current),
    };
  }

  bool _filtersEqual(NoteListFilter a, NoteListFilter b) {
    if (a is AllFilter && b is AllFilter) return true;
    if (a is UncategorizedFilter && b is UncategorizedFilter) return true;
    if (a is FavoriteFilter && b is FavoriteFilter) return true;
    if (a is DeletedFilter && b is DeletedFilter) return true;
    if (a is FolderFilter && b is FolderFilter) return a.folderId == b.folderId;
    if (a is NotebookFilter && b is NotebookFilter) return a.notebookId == b.notebookId;
    return false;
  }

  Widget _buildPseudoTile(BuildContext ctx, _PseudoRow r, NoteListFilter current) {
    final sel = _filtersEqual(r.filter, current);
    return InkWell(
      onTap: () => ref.read(noteListProvider.notifier).setFilter(r.filter),
      child: Container(
        decoration: BoxDecoration(
          color: sel ? AppColors.primaryLight : Colors.transparent,
          border: sel ? const Border(left: BorderSide(color: AppColors.primary, width: 3)) : null,
        ),
        padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: 14),
        child: Row(children: [
          Icon(r.icon, size: 20, color: sel ? AppColors.primary : AppColors.textPrimary),
          const SizedBox(width: AppDimens.spacingM),
          Expanded(child: Text(r.label, style: TextStyle(
            fontSize: AppDimens.textBody,
            color: sel ? AppColors.primary : AppColors.textPrimary,
            fontWeight: sel ? FontWeight.w500 : FontWeight.normal,
          ))),
          Text('${r.count}', style: TextStyle(
            fontSize: AppDimens.textCaption + 1,
            color: sel ? AppColors.primary : AppColors.textHint,
          )),
        ]),
      ),
    );
  }

  Widget _buildSectionHeader(BuildContext ctx) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: AppDimens.spacingM),
      child: Row(children: [
        const Expanded(child: Text('文件夹', style: TextStyle(
          fontSize: AppDimens.textCaption + 1, color: AppColors.textSecondary, fontWeight: FontWeight.w500,
        ))),
        GestureDetector(
          onTap: () async {
            final result = await ctx.push<bool>('/folder-manager');
            if (result == true) {
              setState(() {
                _rowsFuture = _buildRows();
              });
            }
          },
          child: const Text('管理', style: TextStyle(
            fontSize: AppDimens.textCaption + 1, color: AppColors.primary,
          )),
        ),
      ]),
    );
  }

  Widget _buildFolderHead(_FolderHeadRow r) {
    return InkWell(
      onTap: () {
        ref.read(noteListProvider.notifier).toggleFolderExpand(r.folder.id);
        setState(() {
          _rowsFuture = _buildRows();
        });
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppDimens.spacingL, vertical: AppDimens.spacingM),
        child: Row(children: [
          const Icon(Icons.folder_outlined, size: 20, color: AppColors.textPrimary),
          const SizedBox(width: AppDimens.spacingS),
          Expanded(child: Text(r.folder.name, style: const TextStyle(
            fontSize: AppDimens.textBody, color: AppColors.textPrimary,
          ))),
          Text('${r.count}', style: const TextStyle(
            fontSize: AppDimens.textCaption + 1, color: AppColors.textHint,
          )),
          const SizedBox(width: AppDimens.spacingS),
          AnimatedRotation(
            turns: r.expanded ? 0.5 : 0,
            duration: const Duration(milliseconds: 200),
            child: const Icon(Icons.arrow_drop_down, size: 20, color: AppColors.textHint),
          ),
        ]),
      ),
    );
  }

  Widget _buildNotebookTile(BuildContext ctx, _NotebookRow r, NoteListFilter current) {
    final filter = NoteListFilter.notebook(r.notebook.id);
    final sel = _filtersEqual(filter, current);
    final cleaned = r.notebook.color.replaceFirst('#', '');
    final dotColor = int.tryParse(cleaned, radix: 16);
    return InkWell(
      onTap: () => ref.read(noteListProvider.notifier).setFilter(filter),
      child: Container(
        color: sel ? AppColors.primaryLight : Colors.transparent,
        padding: const EdgeInsets.fromLTRB(48, 10, AppDimens.spacingL, 10),
        child: Row(children: [
          Container(width: 10, height: 10, decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: dotColor != null ? Color(0xFF000000 | dotColor) : AppColors.textHint,
          )),
          const SizedBox(width: 10),
          Expanded(child: Text(r.notebook.name, style: TextStyle(
            fontSize: AppDimens.textBody,
            color: sel ? AppColors.primary : AppColors.textPrimary,
          ))),
          Text('${r.count}', style: TextStyle(
            fontSize: AppDimens.textCaption + 1,
            color: sel ? AppColors.primary : AppColors.textHint,
          )),
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
  final NoteListFilter filter;
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
  final int count;
  const _NotebookRow(this.notebook, this.count);
}
