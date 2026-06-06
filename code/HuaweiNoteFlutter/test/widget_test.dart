import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:hwnote/main.dart';

void main() {
  testWidgets('App 启动 smoke test', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const ProviderScope(child: HwNoteApp()));
    await tester.pumpAndSettle();
    expect(find.text('笔记'), findsOneWidget);
    expect(find.text('待办'), findsOneWidget);
  });
}
