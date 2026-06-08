import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../models/folder.dart';
import '../models/notebook.dart';
import '../providers/folder_manager_provider.dart';
import '../providers/repository_providers.dart';
import '../theme.dart';
import '../widgets/delete_confirm_sheet.dart';
import '../widgets/folder/new_folder_sheet.dart';
import '../widgets/folder/new_notebook_sheet.dart';

class FolderManagerPage extends ConsumerStatefulWidget {
  const FolderManagerPage({super.key});

  @override
  ConsumerState<FolderManagerPage> createState() => _FolderManagerPageState();
}

class _FolderManagerPageState extends ConsumerState<FolderManagerPage> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(folderManagerProvider.notifier).load());
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(folderManagerProvider);
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        context.pop(ref.read(folderManagerProvider.notifier).hasChanges);
      },
      child: Scaffold(
        backgroundColor: Colors.white,
        appBar: AppBar(
          backgroundColor: Colors.white,
          elevation: 0,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back, color: AppColors.textPrimary),
            onPressed: () => context
                .pop(ref.read(folderManagerProvider.notifier).hasChanges),
          ),
          title: const Text('文件夹管理',
              style: TextStyle(
                color: AppColors.textPrimary,
                fontSize: AppDimens.textTitle,
                fontWeight: FontWeight.w600,
              )),
          centerTitle: false,
          actions: [
            IconButton(
              icon: const Icon(Icons.create_new_folder_outlined,
                  color: AppColors.primary),
              onPressed: _onCreateFolder,
            ),
          ],
        ),
        body: state.isLoading && state.rows.isEmpty
            ? const Center(child: CircularProgressIndicator())
            : ReorderableListView.builder(
                itemCount: state.rows.length,
                onReorder: (oldIndex, newIndex) => ref
                    .read(folderManagerProvider.notifier)
                    .onReorder(oldIndex, newIndex),
                buildDefaultDragHandles: false,
                itemBuilder: (context, index) {
                  final row = state.rows[index];
                  return switch (row) {
                    FolderHeadRow r => _buildFolderHead(r, index),
                    NotebookItemRow r => _buildNotebookItem(r, index),
                    CreateNotebookRow r => _buildCreateNotebook(r, index),
                  };
                },
              ),
      ),
    );
  }

  Widget _buildFolderHead(FolderHeadRow row, int index) {
    final canDrag = row.folder.id != 1;
    return ReorderableDragStartListener(
      key: ValueKey('folder_${row.folder.id}'),
      index: index,
      enabled: canDrag,
      child: InkWell(
        onTap: () => ref
            .read(folderManagerProvider.notifier)
            .toggleExpand(row.folder.id),
        child: Padding(
          padding: const EdgeInsets.symmetric(
              horizontal: AppDimens.spacingL, vertical: 14),
          child: Row(children: [
            if (canDrag)
              const Icon(Icons.drag_handle, size: 20, color: AppColors.textHint)
            else
              const SizedBox(width: 20),
            const SizedBox(width: AppDimens.spacingS),
            const Icon(Icons.folder_outlined,
                size: 20, color: AppColors.textPrimary),
            const SizedBox(width: AppDimens.spacingS),
            Expanded(
                child: Text(row.folder.name,
                    style: const TextStyle(
                      fontSize: AppDimens.textBody,
                      color: AppColors.textPrimary,
                      fontWeight: FontWeight.w500,
                    ))),
            AnimatedRotation(
              turns: row.expanded ? 0.5 : 0,
              duration: const Duration(milliseconds: 200),
              child: const Icon(Icons.arrow_drop_down,
                  size: 20, color: AppColors.textHint),
            ),
            if (row.folder.id != 1)
              _overflowButton(() => _showFolderMenu(row.folder))
            else
              const SizedBox(width: 40),
          ]),
        ),
      ),
    );
  }

  Widget _buildNotebookItem(NotebookItemRow row, int index) {
    final cleaned = row.notebook.color.replaceFirst('#', '');
    final dotColor = int.tryParse(cleaned, radix: 16);
    return ReorderableDragStartListener(
      key: ValueKey('notebook_${row.notebook.id}'),
      index: index,
      enabled: true,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(48, 10, AppDimens.spacingL, 10),
        child: Row(children: [
          const Icon(Icons.drag_handle, size: 18, color: AppColors.textHint),
          const SizedBox(width: AppDimens.spacingS),
          Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: dotColor != null
                    ? Color(0xFF000000 | dotColor)
                    : AppColors.textHint,
              )),
          const SizedBox(width: 10),
          Expanded(
              child: Text(row.notebook.name,
                  style: const TextStyle(
                    fontSize: AppDimens.textBody,
                    color: AppColors.textPrimary,
                  ))),
          _overflowButton(() => _showNotebookMenu(row.notebook)),
        ]),
      ),
    );
  }

  Widget _buildCreateNotebook(CreateNotebookRow row, int index) {
    return Container(
      key: ValueKey('create_${row.folderId}'),
      padding: const EdgeInsets.fromLTRB(76, 10, AppDimens.spacingL, 10),
      child: GestureDetector(
        onTap: () => _onCreateNotebook(row.folderId),
        child: const Row(children: [
          Icon(Icons.add, size: 18, color: AppColors.primary),
          SizedBox(width: AppDimens.spacingS),
          Text('新建笔记本',
              style: TextStyle(
                fontSize: AppDimens.textBody,
                color: AppColors.primary,
              )),
        ]),
      ),
    );
  }

  Widget _overflowButton(VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: const Padding(
        padding: EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        child: Icon(Icons.more_vert, size: 20, color: AppColors.textHint),
      ),
    );
  }

  void _showFolderMenu(Folder folder) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            leading: const Icon(Icons.edit_outlined),
            title: const Text('重命名'),
            onTap: () {
              Navigator.pop(ctx);
              _onRenameFolder(folder);
            },
          ),
          ListTile(
            leading: const Icon(Icons.delete_outline, color: AppColors.danger),
            title: const Text('删除', style: TextStyle(color: AppColors.danger)),
            onTap: () {
              Navigator.pop(ctx);
              _onDeleteFolder(folder);
            },
          ),
        ]),
      ),
    );
  }

  void _showNotebookMenu(Notebook nb) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            leading: const Icon(Icons.edit_outlined),
            title: const Text('编辑'),
            onTap: () {
              Navigator.pop(ctx);
              _onEditNotebook(nb);
            },
          ),
          if (!nb.isDefault)
            ListTile(
              leading: const Icon(Icons.drive_file_move_outlined),
              title: const Text('移动到'),
              onTap: () {
                Navigator.pop(ctx);
                _onMoveNotebook(nb);
              },
            ),
          if (!nb.isDefault)
            ListTile(
              leading:
                  const Icon(Icons.delete_outline, color: AppColors.danger),
              title:
                  const Text('删除', style: TextStyle(color: AppColors.danger)),
              onTap: () {
                Navigator.pop(ctx);
                _onDeleteNotebook(nb);
              },
            ),
        ]),
      ),
    );
  }

  Future<void> _onCreateFolder() async {
    final name = await showNewFolderSheet(context);
    if (name != null && name.isNotEmpty) {
      await ref.read(folderManagerProvider.notifier).createFolder(name);
    }
  }

  Future<void> _onRenameFolder(Folder folder) async {
    final name = await showNewFolderSheet(context, initialName: folder.name);
    if (name != null && name.isNotEmpty) {
      await ref
          .read(folderManagerProvider.notifier)
          .renameFolder(folder.id, name);
    }
  }

  Future<void> _onDeleteFolder(Folder folder) async {
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '删除文件夹「${folder.name}」将同时删除其中的笔记本和笔记，确定删除？',
      confirmLabel: '删除',
    );
    if (confirmed) {
      await ref.read(folderManagerProvider.notifier).deleteFolder(folder.id);
    }
  }

  Future<void> _onCreateNotebook(int folderId) async {
    final result = await showNewNotebookSheet(context);
    if (result != null) {
      await ref.read(folderManagerProvider.notifier).createNotebook(
            folderId,
            result.name,
            result.color,
          );
    }
  }

  Future<void> _onEditNotebook(Notebook nb) async {
    final result = await showNewNotebookSheet(
      context,
      initialName: nb.name,
      initialColor: nb.color,
    );
    if (result != null) {
      await ref.read(folderManagerProvider.notifier).renameNotebook(
            nb.id,
            result.name,
            result.color,
          );
    }
  }

  Future<void> _onMoveNotebook(Notebook nb) async {
    final folderRepo = ref.read(folderRepositoryProvider);
    final folders = await folderRepo.list();
    if (!mounted) return;
    final targetId = await showDialog<int>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('移动到'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: folders
              .map((f) => RadioListTile<int>(
                    title: Text(f.name),
                    value: f.id,
                    groupValue: nb.folderId,
                    onChanged: (value) => Navigator.pop(ctx, value),
                  ))
              .toList(),
        ),
      ),
    );
    if (targetId != null && targetId != nb.folderId) {
      await ref
          .read(folderManagerProvider.notifier)
          .moveNotebook(nb.id, targetId);
    }
  }

  Future<void> _onDeleteNotebook(Notebook nb) async {
    final confirmed = await showDeleteConfirmSheet(
      context,
      message: '删除笔记本「${nb.name}」将同时删除其中的笔记，确定删除？',
      confirmLabel: '删除',
    );
    if (confirmed) {
      await ref.read(folderManagerProvider.notifier).deleteNotebook(nb.id);
    }
  }
}
