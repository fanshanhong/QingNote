import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'router.dart';
import 'theme.dart';
import 'services/todo_notification_service.dart';
import 'providers/repository_providers.dart';
import 'providers/theme_provider.dart';
import 'src/rust/frb_generated.dart';
import 'services/search_service.dart';
import 'db/database_helper.dart';
import 'repositories/note_repository.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await RustLib.init();
  await SearchService.instance.init();
  _rebuildSearchIndexIfNeeded();
  await TodoNotificationService.instance.init();
  final prefs = await SharedPreferences.getInstance();
  final savedTab = prefs.getInt('active_tab') ?? 0;
  runApp(ProviderScope(
    overrides: [initialTabProvider.overrideWith((ref) => savedTab)],
    child: const HwNoteApp(),
  ));
}

class HwNoteApp extends ConsumerStatefulWidget {
  const HwNoteApp({super.key});

  @override
  ConsumerState<HwNoteApp> createState() => _HwNoteAppState();
}

class _HwNoteAppState extends ConsumerState<HwNoteApp> {
  @override
  void initState() {
    super.initState();
    _setupNotifications();
    Future.microtask(() => ref.read(themeModeProvider.notifier).init());
  }

  Future<void> _setupNotifications() async {
    final router = ref.read(appRouterProvider);
    TodoNotificationService.instance.onNotificationTap = (payload) {
      if (payload != null && payload.isNotEmpty) {
        router.push('/todo/$payload');
      }
    };
    TodoNotificationService.instance.onCompleteAction = (todoId) async {
      final repo = ref.read(todoRepositoryProvider);
      await repo.completeTodo(todoId);
    };
    try {
      final todoRepo = ref.read(todoRepositoryProvider);
      await TodoNotificationService.instance.rescheduleAll(todoRepo);
    } catch (_) {
      // DB may not be ready in test environment
    }
  }

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(appRouterProvider);
    return MaterialApp.router(
      title: '备忘录',
      theme: buildAppTheme(),
      darkTheme: buildDarkTheme(),
      themeMode: ref.watch(themeModeProvider),
      routerConfig: router,
      debugShowCheckedModeBanner: false,
    );
  }
}

void _rebuildSearchIndexIfNeeded() async {
  try {
    final dbHelper = DatabaseHelper();
    final repo = NoteRepository(dbHelper);
    final notes = await repo.list();
    await SearchService.instance.rebuildIndex(notes);
    debugPrint('[Search] Index rebuilt with ${notes.length} notes');
  } catch (e) {
    debugPrint('[Search] rebuild failed: $e');
  }
}
