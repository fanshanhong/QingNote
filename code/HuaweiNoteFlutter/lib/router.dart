import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'pages/app_shell.dart';
import 'pages/note_editor_page.dart';
import 'pages/note_list_page.dart';
import 'pages/todo_detail_page.dart';
import 'pages/folder_manager_page.dart';
import 'pages/settings_page.dart';
import 'pages/todo_list_page.dart';

final initialTabProvider = StateProvider<int>((ref) => 0);

final appRouterProvider = Provider<GoRouter>((ref) {
  final initialTab = ref.read(initialTabProvider);
  return GoRouter(
    initialLocation: initialTab == 1 ? '/todos' : '/notes',
    routes: [
      GoRoute(
        path: '/editor/:noteId',
        builder: (context, state) {
          final noteId =
              int.tryParse(state.pathParameters['noteId'] ?? '0') ?? 0;
          return NoteEditorPage(noteId: noteId);
        },
      ),
      GoRoute(
        path: '/todo/:todoId',
        builder: (context, state) {
          final todoId =
              int.tryParse(state.pathParameters['todoId'] ?? '0') ?? 0;
          return TodoDetailPage(todoId: todoId);
        },
      ),
      GoRoute(
        path: '/folder-manager',
        builder: (context, state) => const FolderManagerPage(),
      ),
      GoRoute(
        path: '/settings',
        builder: (context, state) => const SettingsPage(),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return AppShell(
            currentIndex: navigationShell.currentIndex,
            navigationShell: navigationShell,
            child: navigationShell,
          );
        },
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/notes',
              builder: (context, state) => const NoteListPage(),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/todos',
              builder: (context, state) => const TodoListPage(),
            ),
          ]),
        ],
      ),
    ],
  );
});
