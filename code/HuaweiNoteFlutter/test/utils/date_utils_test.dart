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

    test('跨年超过7天显示 yyyy/MM/dd', () {
      final now = DateTime(2026, 1, 10, 0, 0).millisecondsSinceEpoch;
      final target = DateTime(2025, 12, 25, 14, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '2025/12/25');
    });

    test('跨年但7天内显示 周X HH:mm', () {
      final now = DateTime(2026, 1, 3, 15, 0).millisecondsSinceEpoch;
      final target = DateTime(2025, 12, 31, 10, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.formatRelative(target, now: now), '周三 10:00');
    });
  });

  group('AppDateUtils.timeAgo', () {
    test('不到1分钟显示 刚刚', () {
      final now = DateTime(2026, 6, 6, 15, 0, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 6, 14, 59, 30).millisecondsSinceEpoch;
      expect(AppDateUtils.timeAgo(target, now: now), '刚刚');
    });

    test('1分钟前', () {
      final now = DateTime(2026, 6, 6, 15, 1, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 6, 15, 0, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.timeAgo(target, now: now), '1分钟前');
    });

    test('59分钟前', () {
      final now = DateTime(2026, 6, 6, 15, 59, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 6, 15, 0, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.timeAgo(target, now: now), '59分钟前');
    });

    test('1小时前', () {
      final now = DateTime(2026, 6, 6, 16, 0, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 6, 15, 0, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.timeAgo(target, now: now), '1小时前');
    });

    test('23小时前', () {
      final now = DateTime(2026, 6, 6, 14, 0, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 5, 15, 0, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.timeAgo(target, now: now), '23小时前');
    });

    test('1-2天显示 昨天', () {
      final now = DateTime(2026, 6, 6, 15, 0, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 5, 10, 0, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.timeAgo(target, now: now), '昨天');
    });

    test('3天前', () {
      final now = DateTime(2026, 6, 6, 15, 0, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 3, 15, 0, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.timeAgo(target, now: now), '3天前');
    });

    test('7天及以上显示 MM/dd', () {
      final now = DateTime(2026, 6, 15, 15, 0, 0).millisecondsSinceEpoch;
      final target = DateTime(2026, 6, 1, 10, 0, 0).millisecondsSinceEpoch;
      expect(AppDateUtils.timeAgo(target, now: now), '06/01');
    });
  });
}
