import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_client.dart';
import '../../core/providers.dart';
import 'stats_models.dart';

/// 统计与图表接口。
///
/// **手机端和 PC 端调的是同一批路径**（`AC-1-4`「无端专属接口」）——
/// 数据形状完全一致，只是渲染方式不同（`AC-7B-1`）。
/// 所以这里对返回的数据**不做任何「为手机排版」的处理**。
class StatsApi {
  StatsApi(this._api);

  final ApiClient _api;

  /// 周序列：容量 + 组数（按肌群）+ 训练次数 + 符合率。**一个请求喂三张图。**
  Future<WeeklyStats> weekly({required DateTime from, required DateTime to}) async {
    final data = await _api.get('/stats/weekly', query: {
      'from': isoDate(from),
      'to': isoDate(to),
    });
    return WeeklyStats.fromJson((data as Map).cast<String, dynamic>());
  }

  /// 单动作的估算 1RM 曲线。
  Future<ExerciseE1rm> exerciseE1rm({
    required int exerciseId,
    required DateTime from,
    required DateTime to,
  }) async {
    final data = await _api.get('/stats/exercises/$exerciseId/e1rm', query: {
      'from': isoDate(from),
      'to': isoDate(to),
    });
    return ExerciseE1rm.fromJson((data as Map).cast<String, dynamic>());
  }

  /// PR 看板。
  ///
  /// **刻意不带时间范围**——PR 的语义是「历史最高」，
  /// 带范围会得到「本季度最佳」，而 `METRICS 7.3` 要显示「距今天数」。
  Future<List<PrCard>> prs() async {
    final data = await _api.get('/stats/prs');
    return ((data as Map)['records'] as List)
        .map((e) => PrCard.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
  }

  /// 后端用 `LocalDate`，只接受 `yyyy-MM-dd`。
  ///
  /// ⚠️ 不能传 `toIso8601String()` 的前 10 位——本地时区下
  /// `DateTime(2026, 9, 14)` 在某些时区会序列化成前一天。直接拼年月日最稳。
  static String isoDate(DateTime d) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${d.year}-${two(d.month)}-${two(d.day)}';
  }
}

final statsApiProvider = Provider<StatsApi>(
  (ref) => StatsApi(ref.watch(apiClientProvider)),
);

/// 时间范围。
///
/// **客户端每次都显式传 `from`/`to`**，不依赖服务端默认值——
/// `METRICS` 里各图的默认范围不一样（容量 12 周、频率 26 周、e1RM 6 个月），
/// 服务端给不出一个「对每张图都正确」的默认值。
///
/// 手机端按 `M7-B-2` 统一默认 **3 个月**（PC 端是 1 年）。
enum StatsRange {
  threeMonths('3 个月', 90),
  sixMonths('6 个月', 180),
  oneYear('1 年', 365);

  const StatsRange(this.label, this.days);

  final String label;
  final int days;

  DateTime from(DateTime today) =>
      DateTime(today.year, today.month, today.day).subtract(Duration(days: days));
}

/// 当前选中的时间范围。切换时下面的 provider 会自动重取。
final statsRangeProvider = NotifierProvider<StatsRangeNotifier, StatsRange>(
    StatsRangeNotifier.new);

class StatsRangeNotifier extends Notifier<StatsRange> {
  @override
  StatsRange build() => StatsRange.threeMonths;

  void select(StatsRange range) => state = range;
}

/// 周序列。`family` 带上范围，切换范围就是换一个 provider 实例。
final weeklyStatsProvider =
    FutureProvider.family<WeeklyStats, StatsRange>((ref, range) async {
  final today = DateTime.now();
  return ref.watch(statsApiProvider).weekly(
        from: range.from(today),
        to: today,
      );
});

/// 单动作 e1RM 曲线。
final exerciseE1rmProvider = FutureProvider.family<ExerciseE1rm,
    ({int exerciseId, StatsRange range})>((ref, arg) async {
  final today = DateTime.now();
  return ref.watch(statsApiProvider).exerciseE1rm(
        exerciseId: arg.exerciseId,
        from: arg.range.from(today),
        to: today,
      );
});

/// PR 看板（无范围参数）。
final prBoardProvider = FutureProvider<List<PrCard>>((ref) async {
  return ref.watch(statsApiProvider).prs();
});
