import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/chart_axis.dart';
import '../../core/format.dart';
import '../../core/widgets/error_view.dart';
import 'stats_api.dart';
import 'stats_models.dart';

/// 单动作历史 —— **手机端的主力入口**。
///
/// > `M7-B`：手机的使用时机是「健身房里、刚练完」，
/// > 核心问题是「**我上次这个动作做多重？**」——
/// > 这是健身房里最高频的查询，PC 上没人会为了这个开电脑。
///
/// 所以这一页的重点不是「漂亮的趋势图」，而是**能一眼读到最近的数字**：
/// 顶部三个数字（最佳 e1RM / 最大重量 / 总组数）比曲线更重要，
/// 曲线放在下面做趋势参考。
class ExerciseHistoryScreen extends ConsumerWidget {
  const ExerciseHistoryScreen({
    super.key,
    required this.exerciseId,
    required this.exerciseName,
  });

  final int exerciseId;
  final String exerciseName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final range = ref.watch(statsRangeProvider);
    final arg = (exerciseId: exerciseId, range: range);
    final data = ref.watch(exerciseE1rmProvider(arg));

    return Scaffold(
      appBar: AppBar(title: Text(exerciseName)),
      body: data.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ErrorView(
          error: e,
          onRetry: () => ref.invalidate(exerciseE1rmProvider(arg)),
        ),
        data: (d) => d.supported
            ? _Supported(data: d, range: range)
            : const _NotSupported(),
      ),
    );
  }
}

/// 动作不支持 e1RM 时的说明。
///
/// `METRICS 3.6`：`metric_type != WEIGHT_REPS` 的动作不显示此图。
/// **要说明原因，不能给一张空图**——空图看起来像「还没练」，
/// 用户会以为是数据没同步。
class _NotSupported extends StatelessWidget {
  const _NotSupported();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.show_chart,
                size: 48, color: theme.colorScheme.outlineVariant),
            const SizedBox(height: 12),
            Text('这个动作没有 1RM 趋势', style: theme.textTheme.titleMedium),
            const SizedBox(height: 6),
            Text(
              '估算 1RM 只对「重量 × 次数」类动作有意义。\n'
              '自重和时长类动作没有「最大重量」这个概念。',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }
}

class _Supported extends StatelessWidget {
  const _Supported({required this.data, required this.range});
  final ExerciseE1rm data;
  final StatsRange range;

  @override
  Widget build(BuildContext context) {
    final points = data.points;
    if (points.isEmpty) {
      return const _Empty();
    }

    // 最近一次放最前——「我上次做多重」是高频问题
    final recent = points.reversed.toList();
    final maxWeight = points
        .expand((p) => p.sets)
        .fold<double>(0, (a, s) => s.weight > a ? s.weight : a);
    final totalSets =
        points.fold<int>(0, (a, p) => a + p.sets.length);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _SummaryRow(
          bestE1rm: data.allTimeBest,
          maxWeight: maxWeight,
          totalSets: totalSets,
        ),
        const SizedBox(height: 16),
        _Chart(data: data, range: range),
        const SizedBox(height: 16),
        for (final p in recent.take(20)) _SessionRow(point: p),
      ],
    );
  }
}

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({
    required this.bestE1rm,
    required this.maxWeight,
    required this.totalSets,
  });

  final double? bestE1rm;
  final double maxWeight;
  final int totalSets;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _Metric(label: '最佳 e1RM', value: bestE1rm == null
                ? '—' : '${formatWeight(bestE1rm!)} kg'),
            _Metric(label: '最大重量', value: '${formatWeight(maxWeight)} kg'),
            _Metric(label: '总组数', value: '$totalSets 组'),
          ],
        ),
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      children: [
        Text(label, style: theme.textTheme.labelSmall),
        const SizedBox(height: 4),
        Text(value, style: theme.textTheme.titleLarge),
      ],
    );
  }
}

class _Chart extends StatelessWidget {
  const _Chart({required this.data, required this.range});
  final ExerciseE1rm data;
  final StatsRange range;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final points = data.points;

    final values = points.map((p) => p.bestE1rm).toList();
    final best = data.allTimeBest;
    final labelIndices = axisLabelIndices(points.length);
    // 只算一次——上面两处 minY/maxY 各调一遍 niceRange 会算出同一个结果，
    // 但读起来像两个不同的范围
    final yRange = niceRange(
        values.reduce((a, b) => a < b ? a : b),
        values.reduce((a, b) => a > b ? a : b));

    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 16, 20, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.only(left: 4),
              child: Text('估算 1RM 趋势', style: theme.textTheme.titleMedium),
            ),
            const SizedBox(height: 2),
            Padding(
              padding: const EdgeInsets.only(left: 4),
              child: Text(
                // METRICS 3.6：数据点 = 1 时只显示单点，不画曲线
                points.length < 2
                    ? '数据点不足，再练一次就能看到趋势'
                    : 'Epley 公式 · 取每次训练的最佳组 · 长按看数值',
                style: theme.textTheme.bodySmall,
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              height: 200,
              child: LineChart(
                LineChartData(
                  minX: 0,
                  maxX: (points.length - 1).toDouble(),
                  // Y 轴不从 0 开始（METRICS 3.4）：力量变化幅度小，
                  // 从 0 起会把趋势压成一条平线
                  // 对齐到整齐刻度——不然 min 落在格子外时，
                  // fl_chart 会补一个额外刻度叠在相邻标签上（详见 chart_axis.dart）。
                  // 顺带把读数从「120/100/80/60/50」变成「120/100/80/60/40」
                  minY: yRange.min,
                  maxY: yRange.max,
                  lineBarsData: [
                    LineChartBarData(
                      spots: [
                        for (var i = 0; i < points.length; i++)
                          FlSpot(i.toDouble(), points[i].bestE1rm),
                      ],
                      isCurved: false,
                      color: theme.colorScheme.primary,
                      barWidth: 2.5,
                      // 散点：该次的全部组，展示当天的离散程度（METRICS 3.4）
                      dotData: FlDotData(
                        show: true,
                        getDotPainter: (s, _, _, _) => FlDotCirclePainter(
                          radius: 3,
                          color: theme.colorScheme.primary,
                          strokeWidth: 0,
                        ),
                      ),
                    ),
                  ],
                  // 历史最高参考虚线
                  extraLinesData: best == null
                      ? const ExtraLinesData()
                      : ExtraLinesData(horizontalLines: [
                          HorizontalLine(
                            y: best,
                            color: theme.colorScheme.tertiary,
                            strokeWidth: 1,
                            dashArray: [6, 4],
                            label: HorizontalLineLabel(
                              show: true,
                              alignment: Alignment.topRight,
                              style: theme.textTheme.labelSmall
                                  ?.copyWith(color: theme.colorScheme.tertiary),
                              labelResolver: (_) => '历史最高',
                            ),
                          ),
                        ]),
                  gridData: FlGridData(
                    show: true,
                    drawVerticalLine: false,
                    getDrawingHorizontalLine: (v) => FlLine(
                      color: theme.colorScheme.outlineVariant,
                      strokeWidth: 0.5,
                    ),
                  ),
                  borderData: FlBorderData(show: false),
                  titlesData: FlTitlesData(
                    topTitles: const AxisTitles(),
                    rightTitles: const AxisTitles(),
                    leftTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        reservedSize: 40,
                        interval: yRange.step,
                        getTitlesWidget: (v, meta) => Text(
                          _tickLabel(v, yRange.step),
                          style: theme.textTheme.labelSmall,
                        ),
                      ),
                    ),
                    bottomTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        reservedSize: 24,
                        // 为什么是 1、为什么不能只靠 interval —— 见 chart_axis.dart
                        interval: axisTickInterval,
                        getTitlesWidget: (v, meta) {
                          final i = v.round();
                          if (!labelIndices.contains(i) ||
                              (v - i).abs() > 0.001) {
                            return const SizedBox.shrink();
                          }
                          return Padding(
                            padding: const EdgeInsets.only(top: 6),
                            child: Text(formatShortDate(points[i].date),
                                style: theme.textTheme.labelSmall),
                          );
                        },
                      ),
                    ),
                  ),
                  // ★ 长按读数（M7-B-3）——图表上真正被需要的是那个数
                  lineTouchData: LineTouchData(
                    touchTooltipData: LineTouchTooltipData(
                      getTooltipColor: (_) => theme.colorScheme.inverseSurface,
                      getTooltipItems: (touched) => touched.map((s) {
                        final i = s.x.round();
                        if (i < 0 || i >= points.length) return null;
                        final p = points[i];
                        return LineTooltipItem(
                          '${formatShortDate(p.date)}\n'
                          '最佳 ${formatWeight(p.bestE1rm)} kg\n'
                          '${p.sets.length} 组',
                          TextStyle(
                            color: theme.colorScheme.onInverseSurface,
                            fontSize: 12,
                          ),
                        );
                      }).toList(),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 一次训练一行：日期 + 实际做的组。
class _SessionRow extends StatelessWidget {
  const _SessionRow({required this.point});
  final E1rmPoint point;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final labels = point.sets
        .map((s) => '${formatWeight(s.weight)}×${s.reps}')
        .toList();

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 48,
            child: Text(formatShortDate(point.date),
                style: theme.textTheme.labelMedium),
          ),
          Expanded(
            child: Text(labels.join(' · '),
                style: theme.textTheme.bodyMedium),
          ),
        ],
      ),
    );
  }
}

/// 刻度保留几位小数由**步长**决定，不是由数值大小决定。
/// 步长 >= 1 → 整数；否则按步长给 1–2 位。（和身体数据页同一规则）
String _tickLabel(double v, double step) {
  if (step >= 1) return v.round().toString();
  return v.toStringAsFixed(step >= 0.1 ? 1 : 2);
}

class _Empty extends StatelessWidget {
  const _Empty();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.history, size: 48, color: theme.colorScheme.outlineVariant),
            const SizedBox(height: 12),
            Text('这个动作还没有记录', style: theme.textTheme.titleMedium),
            const SizedBox(height: 6),
            Text('练过一次之后这里会显示每次的重量和趋势',
                style: theme.textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}
