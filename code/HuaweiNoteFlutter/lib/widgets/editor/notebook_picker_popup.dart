import 'package:flutter/material.dart';
import '../../models/folder.dart';
import '../../models/notebook.dart';
import '../../repositories/folder_repository.dart';
import '../../repositories/notebook_repository.dart';
import '../../theme.dart';
import '../../utils/color_utils.dart';
import '../folder/new_notebook_sheet.dart';

Future<int?> showNotebookPickerPopup(
  BuildContext context, {
  required FolderRepository folderRepo,
  required NotebookRepository notebookRepo,
  int? currentNotebookId,
}) async {
  return showDialog<int>(
    context: context,
    barrierColor: Colors.transparent,
    builder: (ctx) => _NotebookPickerDialog(
      folderRepo: folderRepo,
      notebookRepo: notebookRepo,
      currentNotebookId: currentNotebookId,
    ),
  );
}

class _NotebookPickerDialog extends StatefulWidget {
  final FolderRepository folderRepo;
  final NotebookRepository notebookRepo;
  final int? currentNotebookId;

  const _NotebookPickerDialog({
    required this.folderRepo,
    required this.notebookRepo,
    this.currentNotebookId,
  });

  @override
  State<_NotebookPickerDialog> createState() => _NotebookPickerDialogState();
}

class _NotebookPickerDialogState extends State<_NotebookPickerDialog> {
  List<_PickerRow>? _rows;
  final Set<int> _expanded = {};

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final folders = await widget.folderRepo.list();
    if (_expanded.isEmpty) {
      _expanded.addAll(folders.map((f) => f.id));
    }
    await _buildRows(folders);
  }

  Future<void> _buildRows(List<Folder>? folders) async {
    folders ??= await widget.folderRepo.list();
    final rows = <_PickerRow>[];
    for (final folder in folders) {
      final expanded = _expanded.contains(folder.id);
      rows.add(_FolderRow(folder, expanded));
      if (expanded) {
        final notebooks = await widget.notebookRepo.listByFolder(folder.id);
        for (final nb in notebooks) {
          rows.add(_NotebookRow(nb));
        }
        rows.add(_CreateRow(folder.id));
      }
    }
    if (mounted) setState(() => _rows = rows);
  }

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.topRight,
      child: Padding(
        padding: const EdgeInsets.only(top: 100, right: 16),
        child: Material(
          elevation: 8,
          borderRadius: BorderRadius.circular(12),
          color: Colors.white,
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxWidth: 240,
              maxHeight: MediaQuery.of(context).size.height * 0.5,
            ),
            child: _rows == null
                ? const Padding(
                    padding: EdgeInsets.all(24),
                    child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
                  )
                : ListView.builder(
                    shrinkWrap: true,
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    itemCount: _rows!.length,
                    itemBuilder: (ctx, i) => _buildRow(_rows![i]),
                  ),
          ),
        ),
      ),
    );
  }

  Widget _buildRow(_PickerRow row) {
    return switch (row) {
      _FolderRow r => _buildFolderRow(r),
      _NotebookRow r => _buildNotebookRow(r),
      _CreateRow r => _buildCreateRow(r),
    };
  }

  Widget _buildFolderRow(_FolderRow row) {
    return InkWell(
      onTap: () {
        if (_expanded.contains(row.folder.id)) {
          _expanded.remove(row.folder.id);
        } else {
          _expanded.add(row.folder.id);
        }
        _buildRows(null);
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(children: [
          const Icon(Icons.folder_outlined, size: 18, color: AppColors.textPrimary),
          const SizedBox(width: 8),
          Expanded(child: Text(row.folder.name, style: const TextStyle(
            fontSize: AppDimens.textBody, color: AppColors.textPrimary,
            fontWeight: FontWeight.w500,
          ))),
          AnimatedRotation(
            turns: row.expanded ? 0.5 : 0,
            duration: const Duration(milliseconds: 200),
            child: const Icon(Icons.keyboard_arrow_down, size: 18, color: AppColors.textHint),
          ),
        ]),
      ),
    );
  }

  Widget _buildNotebookRow(_NotebookRow row) {
    final isSelected = row.notebook.id == widget.currentNotebookId;
    final dotColor = AppColorUtils.parseHex(row.notebook.color);
    return InkWell(
      onTap: () => Navigator.pop(context, row.notebook.id),
      child: Container(
        color: isSelected ? AppColors.primaryLight : null,
        padding: const EdgeInsets.fromLTRB(40, 10, 16, 10),
        child: Row(children: [
          Icon(Icons.menu_book, size: 18,
            color: dotColor ?? AppColors.textHint,
          ),
          const SizedBox(width: 8),
          Expanded(child: Text(row.notebook.name, style: TextStyle(
            fontSize: AppDimens.textBody,
            color: isSelected ? AppColors.primary : AppColors.textPrimary,
          ))),
        ]),
      ),
    );
  }

  Widget _buildCreateRow(_CreateRow row) {
    return InkWell(
      onTap: () async {
        final result = await showNewNotebookSheet(context);
        if (result != null) {
          final newId = await widget.notebookRepo.insert(
            row.folderId, result.name, result.color,
          );
          if (mounted) Navigator.pop(context, newId);
        }
      },
      child: const Padding(
        padding: EdgeInsets.fromLTRB(40, 10, 16, 10),
        child: Row(children: [
          Icon(Icons.add, size: 18, color: AppColors.primary),
          SizedBox(width: 8),
          Text('新建', style: TextStyle(
            fontSize: AppDimens.textBody, color: AppColors.primary,
          )),
        ]),
      ),
    );
  }
}

sealed class _PickerRow {
  const _PickerRow();
}

class _FolderRow extends _PickerRow {
  final Folder folder;
  final bool expanded;
  const _FolderRow(this.folder, this.expanded);
}

class _NotebookRow extends _PickerRow {
  final Notebook notebook;
  const _NotebookRow(this.notebook);
}

class _CreateRow extends _PickerRow {
  final int folderId;
  const _CreateRow(this.folderId);
}
