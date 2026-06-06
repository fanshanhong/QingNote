class AppDateUtils {
  AppDateUtils._();

  static String _pad2(int n) => n.toString().padLeft(2, '0');

  static String formatRelative(int timeMs, {int? now}) {
    final nowMs = now ?? DateTime.now().millisecondsSinceEpoch;
    final target = DateTime.fromMillisecondsSinceEpoch(timeMs);
    final current = DateTime.fromMillisecondsSinceEpoch(nowMs);
    final hhmm = '${_pad2(target.hour)}:${_pad2(target.minute)}';

    if (target.year == current.year &&
        target.month == current.month &&
        target.day == current.day) {
      return hhmm;
    }

    final yesterday = current.subtract(const Duration(days: 1));
    if (target.year == yesterday.year &&
        target.month == yesterday.month &&
        target.day == yesterday.day) {
      return '昨天 $hhmm';
    }

    final diffDays = (nowMs - timeMs) ~/ (24 * 60 * 60 * 1000);
    if (diffDays >= 0 && diffDays <= 6) {
      const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
      return '${weekdays[target.weekday - 1]} $hhmm';
    }

    return '${target.year}/${_pad2(target.month)}/${_pad2(target.day)}';
  }
}
