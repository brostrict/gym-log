import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/chart_axis.dart';
import '../../core/format.dart';
import '../../core/widgets/error_view.dart';
import 'exercise_history_screen.dart';
import 'stats_api.dart';
import 'stats_models.dart';

/// 「趋势」页 —— 回答「三个月我变强了吗」。
///
/// 按 `M7-B` 的平台适配做的减法（两条硬性约束写在各自组件上）：
///
/// | 约束 | 落点 |
/// |---|---|
/// | **单序列**（`M7-B-1`） | 不做多序列叠加；肌群组数用横条而不是堆叠柱 |
/// | 默认 **3 个月**（`M7-B-2`） | 范围切换器，服务端默认值只当兜底 |
/// | **长按读数**（`M7-B-3`） | 容量图的长按提示——**缺了它图表只是装饰** |
class StatsScreen extends ConsumerWidget {
  const StatsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final range = ref.watch(statsRangeProvider);
    final weekly = ref.watch(weeklyStatsProvider(range));

    return Scaffold(
      appBar: AppBar(title: const Text('趋势')),
      body: weekly.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ErrorView(
          error: e,
          onRetry: () => ref.invalidate(weeklyStatsProvider(range)),
        ),
        data: (data) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _RangeSelector(current: range),
            const SizedBox(height: 16),
            _VolumeCard(stats: data),
            const SizedBox(height: 16),
            _MuscleSetsCard(stats: data),
            const SizedBox(height: 16),
            _StreakCard(stats: data),
            const SizedBox(height: 16),
            _PrCardList(),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}

/// 时间范围切换。手机端默认 3 个月（`M7-B-2`）。
class _RangeSelector extends ConsumerWidget {
  const _RangeSelector({required this.current});
  final StatsRange current;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SegmentedButton<StatsRange>(
      segments: [
        for (final r in StatsRange.values)
          ButtonSegment(value: r, label: Text(r.label)),
      ],
      selected: {current},
      onSelectionChanged: (s) =>
          ref.read(statsRangeProvider.notifier).select(s.first),
      showSelectedIcon: false,
    );
  }
}

// ======================================================================
// 训练容量
// ======================================================================

class _VolumeCard extends StatelessWidget {
  const _VolumeCard({required this.stats});
  final WeeklyStats stats;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final weeks = stats.weeks;
    final labelIndices = axisLabelIndices(weeks.length);

    return _Card(
      title: '训练容量',
      subtitle: 'Σ(重量 × 次数)，仅正式组',
      child: weeks.isEmpty
          ? const _Empty()
          : SizedBox(
              height: 200,
              child: LineChart(
                LineChartData(
                  // ★ Y 轴从 0 开始（AC-7-11）。
                  // 容量是**累加量**，「8000 和 8500 的差别」只有从 0 起才看得出比例；
                  // 不从 0 会放大微小差异，制造虚假的进步感。
                  minY: 0,
                  minX: 0,
                  maxX: (weeks.length - 1).toDouble(),
                  lineBarsData: [
                    LineChartBarData(
                      spots: [
                        for (var i = 0; i < weeks.length; i++)
                          FlSpot(i.toDouble(), weeks[i].volume),
                      ],
                      isCurved: false,
                      color: theme.colorScheme.primary,
                      barWidth: 2.5,
                      dotData: FlDotData(
                        show: true,
                        getDotPainter: (s, _, _, _) => FlDotCirclePainter(
                          radius: weeks[s.x.toInt()].currentWeek ? 4 : 2.5,
                          color: theme.colorScheme.primary,
                          strokeWidth: 0,
                        ),
                      ),
                      belowBarData: BarAreaData(
                        show: true,
                        color: theme.colorScheme.primary.withValues(alpha: 0.10),
                      ),
                    ),
                  ],
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
                        reservedSize: 44,
                        getTitlesWidget: (v, meta) => Text(
                          v >= 1000 ? '${(v / 1000).round()}k' : v.round().toString(),
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
                            child: Text(formatShortDate(weeks[i].weekStart),
                                style: theme.textTheme.labelSmall),
                          );
                        },
                      ),
                    ),
                  ),
                  // ★ 长按读数（M7-B-3）。**缺了它图表就只是装饰**——
                  // PC 上鼠标悬停是默认能力，手机上必须显式实现。
                  // （fl_chart 在 handleBuiltInTouches 下同时支持点按和长按。）
                  lineTouchData: LineTouchData(
                    touchTooltipData: LineTouchTooltipData(
                      getTooltipColor: (_) => theme.colorScheme.inverseSurface,
                      getTooltipItems: (touched) => touched.map((s) {
                        final i = s.x.round();
                        if (i < 0 || i >= weeks.length) return null;
                        final w = weeks[i];
                        return LineTooltipItem(
                          '${formatShortDate(w.weekStart)} 那周\n'
                          '${formatVolume(w.volume)}\n'
                          '${w.workingSets} 组 · ${w.sessionCount} 次',
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
    );
  }
}

// ======================================================================
// 肌群周组数
// ======================================================================

/// 肌群周组数 —— **手机端必须用横向条形**（`M7-B`）。
///
/// > 6 个肌群 × 12 周的堆叠柱，在 360dp 宽的屏幕上每周只有 30dp——
/// > 柱子细到看不出堆叠分层，肌群颜色也挤在一起。
/// > **横向条形把分类轴放在纵向**，每个肌群独占一行，宽度充足，
/// > 且肌群名称能完整显示（堆叠柱的 X 轴标签只能放缩写）。
///
/// **不是用 fl_chart 画的**：它的 `BarChartData` 不支持横向
/// （1.2 版没有 `rotated` 参数）。而横向条形本质就是「一行一个分类的定宽矩形」，
/// 用普通 widget 反而更好控制——尤其是标签宽度。
class _MuscleSetsCard extends StatefulWidget {
  const _MuscleSetsCard({required this.stats});
  final WeeklyStats stats;

  @override
  State<_MuscleSetsCard> createState() => _MuscleSetsCardState();
}

class _MuscleSetsCardState extends State<_MuscleSetsCard> {
  /// 一次显示一周，用左右按钮切换（`M7-B` 明确要求）。
  ///
  /// ⚠️ **存的是那一周的日期，不是下标。**
  ///
  /// 下标只在某一个时间范围里有意义。真机上踩到：在「3 个月」里翻到 8-3
  /// （下标 7），一切到「1 年」，下标 7 变成了 **11-3** ——
  /// 用户没碰过切换器，卡片上那一周却换了，而且落在整段没数据的地方。
  ///
  /// 存日期之后，切范围时要么还显示同一周，要么那一周不在新范围内了
  /// （退回最新一周），两种结果都能解释。
  ///
  /// `null` = 最新一周，也就是「用户没手动翻过」。
  DateTime? _weekStart;

  /// 按日期比较，不比较时刻。
  ///
  /// 现在 `weekStart` 都是 `DateTime.parse('2026-09-14')` 出来的本地零点，
  /// 直接 `==` 也对；但后端哪天把日期换成带时区的时间戳，
  /// `==` 就会静默失配（表现是「翻页永远跳回最新一周」），而这里不会。
  static bool _sameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final weeks = widget.stats.weeks;
    if (weeks.isEmpty) {
      return const _Card(title: '肌群周组数', child: _Empty());
    }

    // 默认最新一周；手动翻过就按日期找回来，找不到（换范围了）也退回最新一周
    var i = weeks.length - 1;
    if (_weekStart != null) {
      final found =
          weeks.indexWhere((w) => _sameDay(w.weekStart, _weekStart!));
      if (found >= 0) i = found;
    }
    final week = weeks[i];
    final muscles = week.muscleSets;
    final maxSets = muscles.fold<int>(1, (a, m) => m.sets > a ? m.sets : a);

    return _Card(
      title: '肌群周组数',
      subtitle: '10–20 组/周是增肌参考区间，不是及格线',
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            icon: const Icon(Icons.chevron_left),
            onPressed: i > 0
                ? () => setState(() => _weekStart = weeks[i - 1].weekStart)
                : null,
            visualDensity: VisualDensity.compact,
          ),
          Text(formatShortDate(week.weekStart),
              style: theme.textTheme.labelLarge),
          IconButton(
            icon: const Icon(Icons.chevron_right),
            onPressed: i < weeks.length - 1
                ? () => setState(() => _weekStart = weeks[i + 1].weekStart)
                : null,
            visualDensity: VisualDensity.compact,
          ),
        ],
      ),
      child: Column(
        children: [
          for (final m in muscles)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 3),
              child: Row(
                children: [
                  // 固定标签宽度：肌群名能完整显示，条形起点也对齐
                  SizedBox(
                    width: 32,
                    child: Text(m.label, style: theme.textTheme.labelMedium),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Stack(
                      children: [
                        // 10–20 组的参考带（METRICS 4.5：是提示不是评判，
                        // 所以用中性色，且界面上不出现「未达标」这类措辞）
                        Positioned.fill(
                          child: FractionallySizedBox(
                            alignment: Alignment.centerLeft,
                            widthFactor: 1.0,
                            child: Container(
                              decoration: BoxDecoration(
                                color: theme.colorScheme.surfaceContainerHighest
                                    .withValues(alpha: 0.55),
                                borderRadius: BorderRadius.circular(4),
                              ),
                            ),
                          ),
                        ),
                        FractionallySizedBox(
                          alignment: Alignment.centerLeft,
                          widthFactor: maxSets == 0 ? 0 : m.sets / maxSets,
                          child: Container(
                            height: 18,
                            decoration: BoxDecoration(
                              color: theme.colorScheme.primary,
                              borderRadius: BorderRadius.circular(4),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  SizedBox(
                    width: 24,
                    child: Text('${m.sets}',
                        textAlign: TextAlign.right,
                        style: theme.textTheme.labelMedium),
                  ),
                ],
              ),
            ),
          if (week.trainedMuscles.isEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text('这一周没有训练记录',
                  style: theme.textTheme.bodySmall),
            ),
        ],
      ),
    );
  }
}

// ======================================================================
// 坚持情况
// ======================================================================

class _StreakCard extends StatelessWidget {
  const _StreakCard({required this.stats});
  final WeeklyStats stats;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final weeks = stats.weeks;
    final maxCount = weeks.fold<int>(1, (a, w) => w.sessionCount > a ? w.sessionCount : a);

    return _Card(
      title: '坚持情况',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text('${stats.currentStreak}',
                  style: theme.textTheme.headlineMedium
                      ?.copyWith(color: theme.colorScheme.primary)),
              const SizedBox(width: 6),
              Text('连续周', style: theme.textTheme.bodyMedium),
            ],
          ),
          const SizedBox(height: 12),
          // 周条带：空周显示为**空柱**，不跳过（METRICS 4.6）
          SizedBox(
            height: 48,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                for (final w in weeks)
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 1.5),
                      child: Container(
                        height: w.sessionCount == 0
                            // 空周也占位，画一个 2px 的底线——
                            // 跳过的话「中断三周」会被画成「连续三周」
                            ? 2
                            : 8 + 32 * (w.sessionCount / maxCount),
                        decoration: BoxDecoration(
                          color: w.sessionCount == 0
                              ? theme.colorScheme.outlineVariant
                              : theme.colorScheme.primary
                                  // 当前周半透明（METRICS 5.5：这周还没过完）
                                  .withValues(alpha: w.currentWeek ? 0.45 : 1),
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          Text('最近 ${weeks.length} 周 · 每周训练次数',
              style: theme.textTheme.labelSmall),
        ],
      ),
    );
  }
}

// ======================================================================
// PR 看板
// ======================================================================

class _PrCardList extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final prs = ref.watch(prBoardProvider);
    final theme = Theme.of(context);

    return prs.when(
      loading: () => const _Card(title: '个人纪录', child: _Empty()),
      error: (_, _) => const _Card(title: '个人纪录', child: _Empty()),
      data: (list) => _Card(
        title: '个人纪录',
        subtitle: '最大重量与最佳 e1RM 分开记录，不合并',
        child: list.isEmpty
            ? const _Empty()
            : Column(
                children: [
                  for (final p in list.take(8))
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      leading: Icon(
                        // 「首次记录」用中性色——METRICS 7.4：
                        // 第一次练不是突破，渲染成庆祝会让真正的进步贬值
                        p.firstTime ? Icons.flag_outlined : Icons.emoji_events,
                        color: p.firstTime
                            ? theme.colorScheme.outline
                            : theme.colorScheme.primary,
                      ),
                      title: Text('${p.exerciseName} · ${p.metricLabel}'),
                      subtitle: Text(
                        p.firstTime
                            ? '首次记录 · ${formatDaysAgo(p.daysAgo)}'
                            : '${formatWeight(p.value)} ${p.unit} · ${formatDaysAgo(p.daysAgo)}',
                        style: theme.textTheme.bodySmall,
                      ),
                      trailing: p.firstTime
                          ? null
                          : Text('${formatWeight(p.value)} ${p.unit}',
                              style: theme.textTheme.titleSmall),
                      onTap: () => Navigator.of(context).push(MaterialPageRoute(
                        builder: (_) => ExerciseHistoryScreen(
                          exerciseId: p.exerciseId,
                          exerciseName: p.exerciseName,
                        ),
                      )),
                    ),
                ],
              ),
      ),
    );
  }
}

// ======================================================================

class _Card extends StatelessWidget {
  const _Card({required this.title, this.subtitle, this.trailing, required this.child});

  final String title;
  final String? subtitle;
  final Widget? trailing;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(title, style: theme.textTheme.titleMedium),
                ),
                ?trailing,
              ],
            ),
            if (subtitle != null) ...[
              const SizedBox(height: 2),
              Text(subtitle!, style: theme.textTheme.bodySmall),
            ],
            const SizedBox(height: 12),
            child,
          ],
        ),
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 20),
      child: Center(
        child: Text('还没有数据，先练一场',
            style: Theme.of(context).textTheme.bodySmall),
      ),
    );
  }
}
