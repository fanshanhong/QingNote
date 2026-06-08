import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../providers/note_list_provider.dart';
import '../providers/todo_list_provider.dart';
import '../theme.dart';

class AppShell extends StatelessWidget {
  final int currentIndex;
  final StatefulNavigationShell navigationShell;
  final Widget child;

  const AppShell({
    super.key,
    required this.currentIndex,
    required this.navigationShell,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: child,
      bottomNavigationBar: _BottomNav(
        currentIndex: currentIndex,
        navigationShell: navigationShell,
      ),
    );
  }
}

class _BottomNav extends ConsumerWidget {
  final int currentIndex;
  final StatefulNavigationShell navigationShell;

  const _BottomNav({
    required this.currentIndex,
    required this.navigationShell,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final noteBatch =
        ref.watch(noteListProvider.select((s) => s.isBatchMode));
    final todoBatch =
        ref.watch(todoListProvider.select((s) => s.isBatchMode));

    if (noteBatch || todoBatch) return const SizedBox.shrink();

    return BottomNavigationBar(
      currentIndex: currentIndex,
      onTap: (index) {
        navigationShell.goBranch(index);
        SharedPreferences.getInstance()
            .then((prefs) => prefs.setInt('active_tab', index));
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (ref.read(noteListProvider).filterPanelVisible) {
            ref.read(noteListProvider.notifier).toggleFilterPanel();
          }
          if (ref.read(todoListProvider).filterPanelVisible) {
            ref.read(todoListProvider.notifier).toggleFilterPanel();
          }
        });
      },
      selectedItemColor: AppColors.primary,
      unselectedItemColor: AppColors.textHint,
      selectedFontSize: AppDimens.textHint,
      unselectedFontSize: AppDimens.textHint,
      type: BottomNavigationBarType.fixed,
      items: const [
        BottomNavigationBarItem(
          icon: Icon(Icons.note_outlined),
          activeIcon: Icon(Icons.note),
          label: '笔记',
        ),
        BottomNavigationBarItem(
          icon: Icon(Icons.check_box_outlined),
          activeIcon: Icon(Icons.check_box),
          label: '待办',
        ),
      ],
    );
  }
}
