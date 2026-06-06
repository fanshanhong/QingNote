import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'router.dart';
import 'theme.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  final savedTab = prefs.getInt('active_tab') ?? 0;
  runApp(ProviderScope(
    overrides: [initialTabProvider.overrideWith((ref) => savedTab)],
    child: const HwNoteApp(),
  ));
}

class HwNoteApp extends ConsumerWidget {
  const HwNoteApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);
    return MaterialApp.router(
      title: '备忘录',
      theme: buildAppTheme(),
      routerConfig: router,
      debugShowCheckedModeBanner: false,
    );
  }
}
