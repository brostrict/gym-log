/// 展示层的数字格式化。
///
/// 这几个函数原来**重复了四份**（`summary_screen` 的 `_formatVolume` /
/// `_formatDuration` / `_ExerciseRow._fmt`，以及 `summary_models` 的 `_trim`），
/// 逻辑基本一样。统计页会是第五处，所以收敛到这里。
///
/// ⚠️ 这里只做**显示**，不做任何口径计算——容量怎么算、e1RM 怎么算
/// 只应该有一份实现，在后端。
library;

/// 容量：`12,450 kg`。四舍五入到整数——小数点后的公斤数没有意义。
String formatVolume(double kg) {
  final rounded = kg.round();
  return '${_thousands(rounded)} kg';
}

/// 大数字加千分位。`12450` → `12,450`
String _thousands(int n) {
  final s = n.abs().toString();
  final buf = StringBuffer(n < 0 ? '-' : '');
  for (var i = 0; i < s.length; i++) {
    if (i > 0 && (s.length - i) % 3 == 0) buf.write(',');
    buf.write(s[i]);
  }
  return buf.toString();
}

/// 时长：`45s` / `3 分 20 秒` / `1 小时 5 分`
String formatDuration(int seconds) {
  if (seconds < 60) return '${seconds}s';
  final h = seconds ~/ 3600;
  final m = (seconds % 3600) ~/ 60;
  if (h > 0) return '$h 小时 $m 分';
  final s = seconds % 60;
  return s == 0 ? '$m 分钟' : '$m 分 $s 秒';
}

/// 重量：整数不带小数点（`60` 而不是 `60.0`），否则保留一位
String formatWeight(double kg) =>
    kg.truncateToDouble() == kg ? kg.toStringAsFixed(0) : kg.toStringAsFixed(1);

/// 日期：`9-21`
String formatShortDate(DateTime d) => '${d.month}-${d.day}';

/// 相对天数：`今天` / `3 天前` / `2 个月前`
String formatDaysAgo(int days) {
  if (days <= 0) return '今天';
  if (days < 30) return '$days 天前';
  final months = days ~/ 30;
  return '$months 个月前';
}
