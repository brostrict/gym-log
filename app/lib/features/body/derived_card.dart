import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'body_api.dart';
import 'derived_models.dart';
import 'profile_sheet.dart';

/// 「推导指标」卡片 —— BMI / 体脂率估算 / BMR / 腰高比。
///
/// ## 为什么只有当前值，没有趋势图
///
/// `METRICS 0.1` 的准入标准是「答不出『看完这张图我会改变什么行为』的图，不做」。
///
/// `BMI = 体重 / 身高²`，而身高是常数——所以 **BMI 曲线和体重曲线形状完全一样**，
/// 只是纵轴刻度不同。体脂率估算在年龄不变的短期内同理，BMR 也由体重决定。
/// 三条曲线提供的信息和体重曲线**完全等价**，属于装饰。
///
/// 而「BMI 23.4，正常」是体重曲线给不了的东西：那是**一层判断**。
///
/// ## 为什么每条都写公式
///
/// 去掉体脂秤五项的理由之一是**黑箱**——用户不知道秤上的 18.7% 是怎么来的。
/// 自己算的如果不写公式，和秤推的没有区别。所以每条下面都带一行「怎么算的」。
class DerivedCard extends ConsumerWidget {
  const DerivedCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final derived = ref.watch(derivedMetricsProvider);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text('推导指标', style: theme.textTheme.titleMedium),
                ),
                TextButton.icon(
                  onPressed: () => showProfileSheet(context),
                  icon: const Icon(Icons.tune, size: 16),
                  label: const Text('资料'),
                  style: TextButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 2),
            Text('由你记录的身高、体重、腰围算出来的，不是量的',
                style: theme.textTheme.bodySmall),
            const SizedBox(height: 12),

            derived.when(
              loading: () => const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(child: CircularProgressIndicator()),
              ),
              // 推导失败不该在身体数据页上弹一个错误——它只是这一页的一部分
              error: (_, _) => Text('暂时算不出来',
                  style: theme.textTheme.bodySmall),
              data: (list) => Column(
                children: [
                  for (final m in list) _DerivedRow(metric: m),
                  if (list.any((m) => m.missing != null))
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        '补上资料就能多算几项——点右上角「资料」',
                        style: theme.textTheme.labelSmall
                            ?.copyWith(color: theme.colorScheme.outline),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DerivedRow extends StatelessWidget {
  const _DerivedRow({required this.metric});

  final DerivedMetric metric;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              SizedBox(
                width: 108,
                child: Text(metric.label, style: theme.textTheme.bodyMedium),
              ),
              if (metric.hasValue) ...[
                Text(_fmt(metric.value!), style: theme.textTheme.titleMedium),
                const SizedBox(width: 3),
                Text(metric.unit, style: theme.textTheme.labelSmall),
                const Spacer(),
                if (metric.level != null)
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      // ⚠️ 用中性色而不是红绿。
                      // 「超重」不是错误、「偏瘦」也不是成就——它们只是
                      // 一个区间的名字。染成红绿会让用户把标签当成评判，
                      // 而这恰恰是 METRICS 4.5 对「参考带」明确反对的用法。
                      color: theme.colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(metric.level!,
                        style: theme.textTheme.labelSmall),
                  ),
              ] else
                Expanded(
                  child: Text(
                    // 缺什么就直接说，这一行本身就成了补资料的入口
                    '需要${metric.missing ?? '更多资料'}',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.outline),
                  ),
                ),
            ],
          ),
          if (metric.hasValue && metric.formula != null)
            Padding(
              padding: const EdgeInsets.only(left: 108, top: 1),
              child: Text(metric.formula!,
                  style: theme.textTheme.labelSmall
                      ?.copyWith(color: theme.colorScheme.outline)),
            ),
        ],
      ),
    );
  }

  /// ⚠️ **不做四舍五入。**
  ///
  /// 第一版用了 `toStringAsFixed(1)`，于是腰高比 0.466 显示成 **0.5**——
  /// 而 0.5 正好是这个指标的**阈值**（服务端判 ≥0.5 是风险）。
  /// 结果就是「0.5 健康」，一个自相矛盾的显示。
  ///
  /// 服务端已经按各指标的语义定好了小数位（BMI 一位、腰高比三位、
  /// BMR 取整），客户端照原样印就行。想「优化」精度就说明你不该动它。
  static String _fmt(double v) =>
      v == v.roundToDouble() ? v.toInt().toString() : v.toString();
}
