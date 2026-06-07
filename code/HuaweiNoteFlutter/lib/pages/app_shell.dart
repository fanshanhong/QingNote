import 'package:flutter/material.dart';
import '../theme.dart';

class AppShell extends StatelessWidget {
  final int currentIndex;
  final Widget child;
  final ValueChanged<int> onTabChanged;
  final bool bottomNavVisible;

  const AppShell({
    super.key,
    required this.currentIndex,
    required this.child,
    required this.onTabChanged,
    this.bottomNavVisible = true,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: child,
      bottomNavigationBar: bottomNavVisible
          ? BottomNavigationBar(
              currentIndex: currentIndex,
              onTap: onTabChanged,
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
            )
          : null,
    );
  }
}
