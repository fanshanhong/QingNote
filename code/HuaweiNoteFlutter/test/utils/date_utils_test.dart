import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/utils/date_utils.dart';

void main() {
  group('AppDateUtils.formatRelative', () {
    test('同一天只显示 HH:mm', () {
      final now = DateTime(2026, 6, 6, 15, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 6, 14, 30).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '14:30');
    });

    test('昨天显示 昨天 HH:mm', () {
      final now = DateTime(2026, 6, 6, 15, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 5, 9, 15).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '昨天 09:15');
    });

    test('7天内显示 周X HH:mm', () {
      final now = DateTime(2026, 6, 6, 15, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 3, 10, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '周三 10:00');
    });

    test('更早显示 yyyy/MM/dd', () {
      final now = DateTime(2026, 6, 6, 15, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 5, 1, 10, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '2026/05/01');
    });

    test('跨年显示 yyyy/MM/dd', () {
      final now = DateTime(2026, 1, 1, 0, 0).millisecondsSinceEpoch;
      final target = DateTime(2025, 12, 25, 14, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '2025/12/25');
    });
  });
}
