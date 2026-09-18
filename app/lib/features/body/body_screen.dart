import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/chart_axis.dart';
import '../../core/format.dart';
import '../../core/widgets/error_view.dart';
import '../stats/stats_api.dart' show statsRangeProvider;
import 'body_api.dart';
import 'body_entry_sheet.dart';
import 'derived_card.dart';
import 'body_models.dart';

/// 「身体数据」页 —— 回答「我身体变了吗」。
///
/// 和「趋势」页（训练数据）并列，但**回答的是不同的问题**：
/// 那边是「我变强了吗」，这边是「我变了吗」。
///
/// 两条硬性约束写在各自组件上：
///
/// | 约束 | 落点 |
/// |---|---|
/// | **Y 轴不从 0 开始**（`AC-7-11`） | 体重变化幅度小，从 0 起会压成一条平线 |
/// | **一次一个部位**（`AC-7B-4`） | 围度用切换器，不做多序列叠加（`M7-B-1`） |
class BodyScreen extends ConsumerWidget {
  const BodyScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final types = ref.watch(bodyMetricTypesProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('身体数据'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            tooltip: '记录',
            onPressed: () => showBodyEntrySheet(context),
          ),
        ],
      ),
      body: types.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ErrorView(
          error: e,
          onRetry: () => ref.invalidate(bodyMetricTypesProvider),
        ),
        data: (list) => _BodyView(types: list),
      ),
    );
  }
}

class _BodyView extends ConsumerWidget {
  const _BodyView({required this.types});
  final List<BodyMetricType> types;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sel = ref.watch(bodySelectionProvider);
    final range = ref.watch(statsRangeProvider);
    final current = types.where((t) => t.metricType == sel.metricType).firstOrNull;

    // ⚠️ 围度没选部位时，自动用第一个**有数据的**部位。
    //
    // 不能只是「让选择器显示第一个」——请求得真的带上它，否则服务端返回空序列，
    // 用户看到「还没有记录」，以为数据没了。
    //
    // 也不能默认成元数据里的第一个部位（NECK）：它多半没数据，
    // 同样会让用户以为数据丢了。
    //
    // 第一次请求仍然不带 site，服务端会回一个**空的、但带 availableSites 的**响应
    // （见 BodyService.series），这里拿到列表后再发第二次。两次往返换掉死循环，值。
    final probed = ref.watch(bodySeriesProvider(
        (sel: (metricType: sel.metricType, site: sel.site), range: range)));
    final availableSites = probed.asData?.value.availableSites ?? const [];

    final effectiveSite = sel.site ??
        ((current?.siteRequired ?? false) && availableSites.isNotEmpty
            ? availableSites.first.site
            : null);

    final series = effectiveSite == sel.site
        ? probed
        : ref.watch(bodySeriesProvider(
            (sel: (metricType: sel.metricType, site: effectiveSite), range: range)));

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // 推导指标放在最上面：它是**结论**（「BMI 23.4，正常」），
        // 而下面的曲线是过程。用户打开这一页想知道的是「我怎么样了」。
        const DerivedCard(),
        const SizedBox(height: 16),

        _MetricPicker(types: types, selected: sel.metricType),

        // 围度的部位切换器（AC-7B-4）。放在指标选择器下面、图上面——
        // 它是**这张图的参数**，不是图的附属品。
        //
        // ⚠️ 用 series 里的 availableSites 而不是 types 里的 sites：
        // METRICS 2.5 要求「只显示有数据的部位」，列 12 个让用户点进去 7 个是空图，
        // 比不给选择器还糟。
        if (availableSites.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: _SitePicker(
              sites: availableSites,
              // 高亮的必须是**请求实际用的那个**，不是 sel.site ——
              // 自动选中的时候 sel.site 还是 null，高亮会空着
              selected: effectiveSite,
              onSelect: (s) =>
                  ref.read(bodySelectionProvider.notifier).selectSite(s),
            ),
          ),

        const SizedBox(height: 16),
        series.when(
          loading: () => const SizedBox(
              height: 240, child: Center(child: CircularProgressIndicator())),
          error: (e, _) => ErrorView(
            error: e,
            onRetry: () => ref.invalidate(bodySeriesProvider),
          ),
          data: (s) => _SeriesCard(series: s, type: current),
        ),
        const SizedBox(height: 16),
        _RecordsCard(metricType: sel.metricType, current: current),
        const SizedBox(height: 24),
      ],
    );
  }
}

/// 指标选择器。14 个指标平铺太长，所以横向滚动——
/// 竖排会让「图表」被挤到屏幕外，而图表才是这一页的主体。
class _MetricPicker extends ConsumerWidget {
  const _MetricPicker({required this.types, required this.selected});
  final List<BodyMetricType> types;
  final String selected;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SizedBox(
      height: 40,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: types.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (_, i) {
          final t = types[i];
          return ChoiceChip(
            label: Text(t.label),
            selected: t.metricType == selected,
            onSelected: (_) => ref
                .read(bodySelectionProvider.notifier)
                .selectMetric(t.metricType),
          );
        },
      ),
    );
  }
}

/// 部位切换器（`AC-7B-4`）。
class _SitePicker extends StatelessWidget {
  const _SitePicker({
    required this.sites,
    required this.selected,
    required this.onSelect,
  });

  final List<BodySiteOption> sites;
  final String? selected;
  final ValueChanged<String> onSelect;

  @override
  Widget build(BuildContext context) {
    // 没选过时默认第一个——服务端不会替我们选，
    // 而不传 site 请求围度会被拒（60005）
    final current = selected ?? sites.first.site;

    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final s in sites)
          ChoiceChip(
            label: Text(s.label),
            selected: s.site == current,
            onSelected: (_) => onSelect(s.site),
          ),
      ],
    );
  }
}

// ======================================================================
// 图表
// ======================================================================

class _SeriesCard extends ConsumerWidget {
  const _SeriesCard({required this.series, required this.type});
  final BodySeries series;
  final BodyMetricType? type;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final unit = series.unit;

    if (series.isEmpty) {
      return _Card(
        title: series.metricLabel,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 24),
          child: Center(
            child: Column(
              children: [
                Icon(Icons.monitor_weight_outlined,
                    size: 44, color: theme.colorScheme.outlineVariant),
                const SizedBox(height: 12),
                Text('还没有记录', style: theme.textTheme.titleSmall),
                const SizedBox(height: 6),
                Text('点右上角 + 记一次',
                    style: theme.textTheme.bodySmall),
              ],
            ),
          ),
        ),
      );
    }

    final latest = series.latestValue;
    final change = series.changeValue;

    return _Card(
      title: series.metricLabel +
          (series.siteLabel.isEmpty ? '' : ' · ${series.siteLabel}'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // ---------- 头条数字 ----------
          //
          // 先给数字再给图：这一页在健身房/早上称完体重时被打开，
          // 用户要的是「多少」和「比基准怎么样」，曲线是佐证。
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(formatWeight(latest ?? 0),
                  style: theme.textTheme.headlineMedium),
              const SizedBox(width: 4),
              Text(unit, style: theme.textTheme.bodyMedium),
              const Spacer(),
              // ⚠️ 变化量只在**有基准**时显示。
              // changeValue 为 null 表示「没有可比的基准」（第一次记录，
              // 或上次记录条件不同）——显示成 0 会让用户以为没变。
              if (change != null && series.referenceLabel != null)
                _ChangeChip(change: change, reference: series.referenceLabel!,
                    unit: unit),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            '${formatShortDate(series.latestAt!)} 测量'
            '${series.rawPoints.length > 1 ? ' · 共 ${series.dailyPoints.length} 天记录' : ''}',
            style: theme.textTheme.labelSmall,
          ),
          const SizedBox(height: 16),

          SizedBox(height: 200, child: _Chart(series: series)),

          // ---------- 提示 ----------
          if (series.conditionHint != null) ...[
            const SizedBox(height: 12),
            _Hint(icon: Icons.info_outline, text: series.conditionHint!),
          ],
          if (!series.enoughDataForMa && series.dailyPoints.length < 3) ...[
            const SizedBox(height: 12),
            const _Hint(
              icon: Icons.show_chart,
              text: '数据不足 3 天，暂不显示趋势线。继续记录就能看到趋势。',
            ),
          ],
        ],
      ),
    );
  }
}

/// 相对基准的变化。
///
/// ⚠️ **基准由服务端给**（`referenceLabel`），客户端不自己判断。
/// 服务端优先用 7 日均值（`METRICS 1.4` 明确要求「相对 7 日均值的偏差」），
/// 没有 MA 时退到「上一次同条件的测量」。
class _ChangeChip extends StatelessWidget {
  const _ChangeChip({
    required this.change,
    required this.reference,
    required this.unit,
  });

  final double change;
  final String reference;
  final String unit;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    // 体重下降用中性色而不是绿色——**掉秤不等于变好**。
    // 增肌期涨 0.3kg 是目标达成，减脂期掉 0.3kg 才是。
    // 用红绿会让「涨」在增肌期看着像坏事。
    final sign = change > 0 ? '+' : '';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Text('$sign${formatWeight(change)} $unit',
            style: theme.textTheme.titleMedium
                ?.copyWith(color: theme.colorScheme.primary)),
        Text('较 $reference', style: theme.textTheme.labelSmall),
      ],
    );
  }
}

class _Chart extends StatelessWidget {
  const _Chart({required this.series});
  final BodySeries series;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final daily = series.dailyPoints;
    final labelIndices = axisLabelIndices(daily.length);

    final values = daily.map((p) => p.value).toList();
    final lo = values.reduce((a, b) => a < b ? a : b);
    final hi = values.reduce((a, b) => a > b ? a : b);

    // ★ Y 轴**不从 0 开始**（AC-7-11）。
    // 体重变化幅度小（12 周可能只有 5kg），从 0 起会压成一条平线；
    // 这和容量图正好相反（那边是累加量，必须从 0 起才看得出比例）。
    //
    // 范围对齐到整齐刻度（含上下余量）——不对齐的话 min 会落在格子外，
    // fl_chart 补一个额外刻度叠在相邻标签上。详见 chart_axis.dart。
    final y = niceRange(lo, hi);

    // MA 线：只取 ma 非空的点，**遇到 null 就断开**。
    //
    // ⚠️ 不能把 null 当成 0，也不能连过去——AC-7-2 要求数据不足时不显示 MA 线，
    // 而窗口稀疏的点画出来比没有更误导。所以切成若干条连续段。
    final maSegments = _splitContinuous(series.maPoints);

    return LineChart(
      LineChartData(
        minX: 0,
        maxX: (daily.length - 1).toDouble(),
        minY: y.min,
        maxY: y.max,
        lineBarsData: [
          // ---------- 原始测量点（背景散点）----------
          //
          // METRICS 1.4 要求它低透明度、作为背景。
          // 它和主线同 X 轴但 Y 更"毛"——这正是要展示的对比：
          // 原始点是锯齿，MA 是趋势。
          if (series.rawPoints.length > 1)
            LineChartBarData(
              spots: _rawSpots(series),
              isCurved: false,
              color: theme.colorScheme.outline.withValues(alpha: 0.35),
              barWidth: 1,
              dotData: FlDotData(
                show: true,
                getDotPainter: (s, _, _, _) => FlDotCirclePainter(
                  radius: 2,
                  color: theme.colorScheme.outline.withValues(alpha: 0.45),
                  strokeWidth: 0,
                ),
              ),
            ),

          // ---------- 日值主线 ----------
          LineChartBarData(
            spots: [
              for (var i = 0; i < daily.length; i++)
                FlSpot(i.toDouble(), daily[i].value),
            ],
            isCurved: false,
            color: theme.colorScheme.primary.withValues(alpha: 0.45),
            barWidth: 1.5,
            dotData: const FlDotData(show: false),
          ),

          // ---------- 7 日移动平均（主序列）----------
          //
          // METRICS 1.4：实线、视觉权重最高。它才是「趋势」，
          // 上面那条日值是「我那天看到的数」。
          for (final seg in maSegments)
            LineChartBarData(
              spots: seg,
              isCurved: false,
              color: theme.colorScheme.primary,
              barWidth: 2.5,
              dotData: const FlDotData(show: false),
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
              // 间隔由 niceRange 给——范围已经对齐到格子上，
              // 所以不会再有「min 被额外补一个刻度」叠在相邻标签上
              interval: y.step,
              getTitlesWidget: (v, meta) => Text(
                _tickLabel(v, y.step),
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
                if (!labelIndices.contains(i) || (v - i).abs() > 0.001) {
                  return const SizedBox.shrink();
                }
                return Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text(formatShortDate(daily[i].date),
                      style: theme.textTheme.labelSmall),
                );
              },
            ),
          ),
        ),
        // ★ 长按读数（M7-B-3）——和趋势页一致，缺了它图表只是装饰
        lineTouchData: LineTouchData(
          touchTooltipData: LineTouchTooltipData(
            getTooltipColor: (_) => theme.colorScheme.inverseSurface,
            getTooltipItems: (touched) => touched.map((s) {
              final i = s.x.round();
              if (i < 0 || i >= daily.length) return null;
              final d = daily[i];
              final ma = series.maPoints.length > i ? series.maPoints[i].ma : null;
              return LineTooltipItem(
                '${formatShortDate(d.date)}\n'
                '${formatWeight(d.value)} ${series.unit}'
                // 只在真有 MA 的那条线上才显示 MA 读数
                '${ma != null && (s.y - ma).abs() < 0.001 ? '\n7 日均值' : ''}',
                TextStyle(
                  color: theme.colorScheme.onInverseSurface,
                  fontSize: 12,
                ),
              );
            }).toList(),
          ),
        ),
      ),
    );
  }

  /// 原始点要映射到「第几个自然日」的 X 坐标上，才能和对齐的主线共用轴。
  ///
  /// 直接用 `dailyPoints` 的下标——同一天的多条测量会落在同一个 X 上，
  /// 这正是 `METRICS 1.2` 第一步「单日取均值」的视觉说明。
  List<FlSpot> _rawSpots(BodySeries series) {
    final indexOfDay = <String, int>{};
    for (var i = 0; i < series.dailyPoints.length; i++) {
      indexOfDay[_dayKey(series.dailyPoints[i].date)] = i;
    }
    final spots = <FlSpot>[];
    for (final r in series.rawPoints) {
      final i = indexOfDay[_dayKey(r.measuredAt)];
      if (i != null) spots.add(FlSpot(i.toDouble(), r.value));
    }
    return spots;
  }

  static String _dayKey(DateTime d) => '${d.year}-${d.month}-${d.day}';

  /// 刻度保留几位小数由**步长**决定，不是由数值大小决定。
  ///
  /// 步长 0.5 → 「74.5」；步长 2 → 「74」。
  /// 按数值大小判断的话，体重的 73.3（步长 2）会印成「73.3」而相邻的是「74」，
  /// 位数不齐看着很乱。
  static String _tickLabel(double v, double step) {
    if (step >= 1) return v.round().toString();
    final decimals = step >= 0.1 ? 1 : 2;
    return v.toStringAsFixed(decimals);
  }

  /// 把 MA 序列切成若干段连续的点。
  ///
  /// `null` 处断开——`AC-7-2` 要求数据不足时不显示 MA 线，
  /// 而窗口稀疏的点画出来比没有更误导。**断线不是「值为 0」**。
  static List<List<FlSpot>> _splitContinuous(List<BodyMaPoint> points) {
    final segments = <List<FlSpot>>[];
    var current = <FlSpot>[];
    for (var i = 0; i < points.length; i++) {
      final ma = points[i].ma;
      if (ma == null) {
        if (current.length > 1) segments.add(current);
        current = <FlSpot>[];
        continue;
      }
      current.add(FlSpot(i.toDouble(), ma));
    }
    if (current.length > 1) segments.add(current);
    return segments;
  }
}

// ======================================================================
// 最近记录
// ======================================================================

class _RecordsCard extends ConsumerWidget {
  const _RecordsCard({required this.metricType, required this.current});
  final String metricType;
  final BodyMetricType? current;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final records = ref.watch(bodyRecordsProvider(metricType));

    return records.when(
      loading: () => const _Card(title: '最近记录', child: _Empty()),
      error: (_, _) => const _Card(title: '最近记录', child: _Empty()),
      data: (list) {
        if (list.isEmpty) {
          return const _Card(title: '最近记录', child: _Empty());
        }
        return _Card(
          title: '最近记录',
          subtitle: '删掉一条不会改变历史训练的容量——那用的是当时的快照',
          child: Column(
            children: [
              for (final r in list.take(10))
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  dense: true,
                  title: Text(
                    '${formatWeight(r.value)} ${r.unit}'
                    '${r.siteLabel.isEmpty ? '' : ' · ${r.siteLabel}'}',
                    style: theme.textTheme.bodyLarge,
                  ),
                  subtitle: Text(
                    [
                      formatShortDate(r.measuredAt),
                      if (r.conditionLabel != null) r.conditionLabel!,
                      if (r.note != null) r.note!,
                    ].join(' · '),
                    style: theme.textTheme.bodySmall,
                  ),
                  trailing: IconButton(
                    icon: const Icon(Icons.delete_outline),
                    onPressed: () => _confirmDelete(context, ref, r),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }

  /// ⚠️ 二次确认。删数据是不可逆的，而这个按钮在一行列表的最右边，
  /// 手指划过很容易误触。
  Future<void> _confirmDelete(
      BuildContext context, WidgetRef ref, BodyMetricRecord r) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除这条记录？'),
        content: Text(
          '${formatShortDate(r.measuredAt)} · '
          '${formatWeight(r.value)} ${r.unit}\n\n'
          '已经完成的训练**不会**因此改变——它们用的是训练当天的体重快照。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (ok != true) return;

    await ref.read(bodyApiProvider).delete(r.id);
    ref.invalidate(bodyRecordsProvider(r.metricType));
    // 曲线也要重取——删掉的点可能正是线上的一个
    ref.invalidate(bodySeriesProvider);
  }
}

// ======================================================================

class _Hint extends StatelessWidget {
  const _Hint({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 16, color: theme.colorScheme.outline),
        const SizedBox(width: 6),
        Expanded(
          child: Text(text,
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.outline)),
        ),
      ],
    );
  }
}

class _Card extends StatelessWidget {
  const _Card({required this.title, this.subtitle, required this.child});

  final String title;
  final String? subtitle;
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
            Text(title, style: theme.textTheme.titleMedium),
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
        child: Text('还没有记录',
            style: Theme.of(context).textTheme.bodySmall),
      ),
    );
  }
}
