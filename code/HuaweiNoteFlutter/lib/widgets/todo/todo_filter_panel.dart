import 'package:flutter/material.dart';
import '../../models/folder.dart';
import '../../providers/todo_list_provider.dart';
import '../../theme.dart';

class TodoFilterPanel extends StatelessWidget {
  final TodoListFilter currentFilter;
  final int allCount;
  final int uncategorizedCount;
  final int deletedCount;
  final List<Folder> folders;
  final Map<int, int> folderCounts;
  final ValueChanged<TodoListFilter> onFilterSelected;

  const TodoFilterPanel({
    super.key,
    required this.currentFilter,
    required this.allCount,
    required this.uncategorizedCount,
    required this.deletedCount,
    required this.folders,
    required this.folderCounts,
    required this.onFilterSelected,
  });

  bool _isSelected(TodoListFilter filter) {
    return switch ((currentFilter, filter)) {
      (TodoAllFilter(), TodoAllFilter()) => true,
      (TodoUncategorizedFilter(), TodoUncategorizedFilter()) => true,
      (TodoDeletedFilter(), TodoDeletedFilter()) => true,
      (TodoFolderFilter(folderId: final a), TodoFolderFilter(folderId: final b))
          => a == b,
      _ => false,
    };
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.symmetric(vertical: AppDimens.spacingS),
      children: [
        _buildRow(
            Icons.list_alt, '全部待办', allCount, TodoListFilter.all),
        _buildRow(Icons.article_outlined, '未分类', uncategorizedCount,
            TodoListFilter.uncategorized),
        _buildRow(Icons.delete_outline, '最近删除', deletedCount,
            TodoListFilter.deleted),
        const Divider(height: 1),
        Padding(
          padding: const EdgeInsets.fromLTRB(AppDimens.spacingL,
              AppDimens.spacingL, AppDimens.spacingL, AppDimens.spacingS),
          child: Row(
            children: [
              const Text('文件夹',
                  style: TextStyle(
                      fontSize: AppDimens.textBody,
                      color: AppColors.textSecondary)),
              const Spacer(),
              GestureDetector(
                onTap: () {},
                child: const Text('管理',
                    style: TextStyle(
                        fontSize: AppDimens.textBody,
                        color: AppColors.primary)),
              ),
            ],
          ),
        ),
        ...folders.map((f) => _buildRow(Icons.folder_outlined, f.name,
            folderCounts[f.id] ?? 0, TodoListFilter.folder(f.id))),
      ],
    );
  }

  Widget _buildRow(
      IconData icon, String title, int count, TodoListFilter filter) {
    final selected = _isSelected(filter);
    return ListTile(
      leading: Icon(icon,
          color: selected ? AppColors.primary : AppColors.textSecondary),
      title: Text(title,
          style: TextStyle(
            color: selected ? AppColors.primary : AppColors.textPrimary,
            fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
          )),
      trailing: Text('$count',
          style: const TextStyle(
              color: AppColors.textHint, fontSize: AppDimens.textBody)),
      selected: selected,
      selectedTileColor: AppColors.primaryLight,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      onTap: () => onFilterSelected(filter),
    );
  }
}
