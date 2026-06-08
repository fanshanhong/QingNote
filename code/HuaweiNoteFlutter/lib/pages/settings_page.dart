import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/theme_provider.dart';
import '../providers/note_list_provider.dart';
import '../repositories/note_repository.dart';
import '../theme.dart';

class SettingsPage extends ConsumerWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeModeProvider);
    final noteListState = ref.watch(noteListProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('设置'),
        centerTitle: false,
      ),
      body: ListView(
        children: [
          _sectionHeader('外观'),
          _settingTile(
            context: context,
            icon: Icons.palette_outlined,
            title: '主题',
            value: _themeModeLabel(themeMode),
            onTap: () => _pickThemeMode(context, ref, themeMode),
          ),
          const Divider(height: 1, indent: 56),
          _sectionHeader('列表偏好'),
          _settingTile(
            context: context,
            icon: Icons.sort,
            title: '默认排序',
            value: noteListState.sortBy == NoteSortBy.updatedDesc
                ? '按更新时间'
                : '按创建时间',
            onTap: () => _pickSortBy(context, ref, noteListState.sortBy),
          ),
          const Divider(height: 1, indent: 56),
          _settingTile(
            context: context,
            icon: Icons.view_module_outlined,
            title: '默认视图',
            value: noteListState.isGridView ? '宫格' : '列表',
            onTap: () async {
              await ref.read(noteListProvider.notifier).toggleGridView();
            },
          ),
          const Divider(height: 1, indent: 56),
          _sectionHeader('关于'),
          _settingTile(
            context: context,
            icon: Icons.info_outline,
            title: '版本',
            value: '1.0.0',
            onTap: null,
          ),
        ],
      ),
    );
  }

  Widget _sectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppDimens.spacingL, AppDimens.spacingL,
          AppDimens.spacingL, AppDimens.spacingS),
      child: Text(title,
          style: const TextStyle(
            fontSize: AppDimens.textCaption + 1,
            color: AppColors.textSecondary,
            fontWeight: FontWeight.w500,
          )),
    );
  }

  Widget _settingTile({
    required BuildContext context,
    required IconData icon,
    required String title,
    required String value,
    required VoidCallback? onTap,
  }) {
    return ListTile(
      leading: Icon(icon, color: AppColors.textPrimary),
      title: Text(title, style: const TextStyle(fontSize: AppDimens.textBody)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(value,
              style: const TextStyle(
                fontSize: AppDimens.textBody,
                color: AppColors.textHint,
              )),
          if (onTap != null) const SizedBox(width: 4),
          if (onTap != null)
            const Icon(Icons.chevron_right,
                size: 20, color: AppColors.textHint),
        ],
      ),
      onTap: onTap,
    );
  }

  String _themeModeLabel(ThemeMode mode) => switch (mode) {
        ThemeMode.light => '浅色',
        ThemeMode.dark => '深色',
        ThemeMode.system => '跟随系统',
      };

  Future<void> _pickThemeMode(
      BuildContext context, WidgetRef ref, ThemeMode current) async {
    final result = await showDialog<ThemeMode>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('主题'),
        children: [
          _dialogOption(ctx, '跟随系统', ThemeMode.system, current),
          _dialogOption(ctx, '浅色', ThemeMode.light, current),
          _dialogOption(ctx, '深色', ThemeMode.dark, current),
        ],
      ),
    );
    if (result != null) {
      await ref.read(themeModeProvider.notifier).setMode(result);
    }
  }

  Future<void> _pickSortBy(
      BuildContext context, WidgetRef ref, NoteSortBy current) async {
    final result = await showDialog<NoteSortBy>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('默认排序'),
        children: [
          _dialogOption(ctx, '按更新时间', NoteSortBy.updatedDesc, current),
          _dialogOption(ctx, '按创建时间', NoteSortBy.createdDesc, current),
        ],
      ),
    );
    if (result != null) {
      await ref.read(noteListProvider.notifier).setSort(result);
    }
  }

  Widget _dialogOption<T>(
      BuildContext context, String label, T value, T current) {
    return SimpleDialogOption(
      onPressed: () => Navigator.pop(context, value),
      child: Row(children: [
        Expanded(child: Text(label)),
        if (value == current)
          const Icon(Icons.check, size: 20, color: AppColors.primary),
      ]),
    );
  }
}
