import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import 'summary_models.dart';
import 'workout_controller.dart';

/// 训练总结 —— 练完之后那一屏（M4-B-7）。
///
/// **所有数字都来自后端**，客户端不做口径计算。
/// 理由见 `workout_api.summary()` 的注释。
class SummaryScreen extends ConsumerWidget {
  const SummaryScreen({super.key, required this.sessionId});
  final int sessionId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final summary = ref.watch(_summaryProvider(sessionId));

    return Scaffold(
      appBar: AppBar(
        title: const Text('训练总结'),
        automaticallyImplyLeading: false,
      ),
      body: summary.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(e is ApiException ? e.message : '$e'),
                const SizedBox(height: 16),
                OutlinedButton(
                  onPressed: () => ref.invalidate(_summaryProvider(sessionId)),
                  child: const Text('重试'),
                ),
                TextButton(
                  onPressed: () => _leave(context, ref),
                  child: const Text('回首页'),
                ),
              ],
            ),
          ),
        ),
        data: (data) => _SummaryView(summary: data, onDone: () => _leave(context, ref)),
      ),
    );
  }

  void _leave(BuildContext context, WidgetRef ref) {
    ref.read(workoutControllerProvider.notifier).reset();
    Navigator.of(context).popUntil((r) => r.isFirst);
  }
}

final _summaryProvider = FutureProvider.family<SessionSummary, int>((ref, id) async {
  return ref.watch(workoutApiProvider).summary(id);
});

class _SummaryView extends StatelessWidget {
  const _SummaryView({required this.summary, required this.onDone});
  final SessionSummary summary;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // ---------- 标题 ----------
        Center(
          child: Column(
            children: [
              Icon(Icons.emoji_events, size: 64, color: theme.colorScheme.primary),
              const SizedBox(height: 8),
              Text(summary.dayName ?? '训练完成',
                  style: theme.textTheme.headlineSmall),
              Text(summary.durationLabel, style: theme.textTheme.bodyMedium),
            ],
          ),
        ),
        const SizedBox(height: 24),

        // ---------- 核心指标 ----------
        //
        // ⚠️ **容量和组数必须并排显示，不能只留一个。**
        //
        // METRICS 4.0：两者回答的是不同问题——
        //   容量（kg）看「总负荷涨没涨」
        //   组数      看「练得够不够」
        //
        // 合成一个会被污染：同样是 15 组，用 60kg 做和 80kg 做，
        // 训练刺激完全不同——只看组数会漏掉「负荷翻倍」这个进步。
        Card(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _BigMetric(
                  label: '训练容量',
                  value: _formatVolume(summary.volume),
                  unit: 'kg',
                ),
                _BigMetric(
                  label: '正式组数',
                  value: '${summary.workingSets}',
                  unit: '组',
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),

        // ---------- 明细 ----------
        Card(
          child: Column(
            children: [
              _Row('完成动作',
                  '${summary.completedExercises} / ${summary.exerciseCount} 个'
                  '${summary.skippedExercises > 0 ? '（跳过 ${summary.skippedExercises} 个）' : ''}'),
              _Row('计划组数', '${summary.plannedSets} 组'
                  '${summary.workingSets != summary.plannedSets ? ' · 实际 ${summary.workingSets} 组' : ''}'),
              if (summary.warmupSets > 0)
                _Row('热身组', '${summary.warmupSets} 组（不计入容量）'),
              _Row('正式组总次数', '${summary.totalReps} 次'),
              if (summary.totalDurationSec > 0)
                _Row('时长类合计', _formatDuration(summary.totalDurationSec)),
            ],
          ),
        ),

        // ---------- 逐动作明细 ----------
        if (summary.exercises.isNotEmpty) ...[
          const SizedBox(height: 20),
          Text('本次训练', style: theme.textTheme.titleMedium),
          const SizedBox(height: 4),
          Text('点动作可以展开看计划与实际对照',
              style: theme.textTheme.bodySmall),
          const SizedBox(height: 8),
          ...summary.exercises.map((e) => _ExerciseRow(exercise: e)),
        ],

        // ---------- PR ----------
        if (summary.personalRecords.isNotEmpty) ...[
          const SizedBox(height: 20),
          Text('本次刷新纪录', style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          ...summary.personalRecords.map((pr) => _PrCard(pr: pr)),
        ],

        // ---------- 与上次对比 ----------
        if (summary.comparison != null) ...[
          const SizedBox(height: 20),
          Text('与上次对比', style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          Card(
            child: Column(
              children: [
                _DeltaRow('容量', summary.comparison!.volumeDelta, 'kg'),
                _DeltaRow('组数', summary.comparison!.workingSetsDelta?.toDouble(), '组'),
                if (summary.comparison!.durationSecDelta != null)
                  _DeltaRow('时长',
                      summary.comparison!.durationSecDelta!.toDouble() / 60, '分钟'),
              ],
            ),
          ),
          if (summary.comparison!.exercises.isNotEmpty)
            Card(
              child: Column(
                children: summary.comparison!.exercises
                    .map((e) => _ExerciseDeltaRow(delta: e))
                    .toList(),
              ),
            ),
        ] else ...[
          const SizedBox(height: 20),
          Center(
            child: Text('第一次练这个训练日，没有对比',
                style: theme.textTheme.bodySmall),
          ),
        ],

        const SizedBox(height: 28),
        FilledButton(
          onPressed: onDone,
          style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(56)),
          child: const Text('完成'),
        ),
        const SizedBox(height: 24),
      ],
    );
  }

  /// 容量通常四位数，去掉小数点后没意义的 0
  static String _formatVolume(double v) {
    if (v >= 1000) return v.round().toString();
    return v.toStringAsFixed(v.truncateToDouble() == v ? 0 : 1);
  }

  static String _formatDuration(int sec) {
    final m = sec ~/ 60;
    return m > 0 ? '$m 分 ${sec % 60} 秒' : '$sec 秒';
  }
}

class _BigMetric extends StatelessWidget {
  const _BigMetric({required this.label, required this.value, required this.unit});
  final String label;
  final String value;
  final String unit;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      children: [
        Text(label, style: theme.textTheme.labelMedium),
        const SizedBox(height: 6),
        Row(
          crossAxisAlignment: CrossAxisAlignment.baseline,
          textBaseline: TextBaseline.alphabetic,
          children: [
            Text(value, style: theme.textTheme.headlineMedium),
            const SizedBox(width: 4),
            Text(unit, style: theme.textTheme.labelMedium),
          ],
        ),
      ],
    );
  }
}

class _Row extends StatelessWidget {
  const _Row(this.label, this.value);
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: Theme.of(context).textTheme.bodyMedium),
          Flexible(
            child: Text(value,
                textAlign: TextAlign.right,
                style: Theme.of(context).textTheme.bodyMedium),
          ),
        ],
      ),
    );
  }
}

class _PrCard extends StatelessWidget {
  const _PrCard({required this.pr});
  final PersonalRecord pr;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      color: theme.colorScheme.primaryContainer,
      child: ListTile(
        leading: Icon(Icons.trending_up, color: theme.colorScheme.onPrimaryContainer),
        title: Text(pr.exerciseName,
            style: TextStyle(color: theme.colorScheme.onPrimaryContainer)),
        subtitle: Text(
          pr.isFirstTime
              ? '第一次记录 · 估算 1RM ${pr.e1rm.toStringAsFixed(1)} kg'
              : '${pr.previousBest!.toStringAsFixed(1)} → ${pr.e1rm.toStringAsFixed(1)} kg'
                  '（+${pr.improvement!.toStringAsFixed(1)}）',
          style: TextStyle(color: theme.colorScheme.onPrimaryContainer),
        ),
      ),
    );
  }
}

class _DeltaRow extends StatelessWidget {
  const _DeltaRow(this.label, this.delta, this.unit);
  final String label;
  final double? delta;
  final String unit;

  @override
  Widget build(BuildContext context) {
    if (delta == null) {
      return _Row(label, '—');
    }
    final positive = delta! > 0;
    return _Row(
      label,
      // 显式带符号：用户要一眼看出是涨了还是掉了
      '${positive ? '+' : ''}${delta!.toStringAsFixed(delta!.truncateToDouble() == delta ? 0 : 1)} $unit',
    );
  }
}

/// 覆盖 _Row 的颜色逻辑，单独写一个是为了给数值上色
class _ExerciseDeltaRow extends StatelessWidget {
  const _ExerciseDeltaRow({required this.delta});
  final ExerciseDelta delta;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final d = delta.weightDelta;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Row(
        children: [
          Expanded(
            child: Text(delta.exerciseName,
                style: theme.textTheme.bodyMedium),
          ),
          Text(
            delta.currentBestWeight == null
                ? '本次未做'
                : '${delta.currentBestWeight!.toStringAsFixed(1)} kg',
            style: theme.textTheme.bodyMedium,
          ),
          if (d != null) ...[
            const SizedBox(width: 8),
            Text(
              '${d > 0 ? '+' : ''}${d.toStringAsFixed(d.truncateToDouble() == d ? 0 : 1)}',
              style: theme.textTheme.labelMedium?.copyWith(
                color: d > 0
                    ? theme.colorScheme.primary
                    : (d < 0 ? theme.colorScheme.error : theme.colorScheme.onSurfaceVariant),
              ),
            ),
          ],
        ],
      ),
    );
  }
}


// ======================================================================
// 逐动作明细（紧凑行 + 可展开）
// ======================================================================

/// 一个动作一行，点开看计划 vs 实际。
///
/// **默认紧凑**：练完当下用户最想看的是「我练了多少」，
/// 5 个动作各展开三行要滑好几屏。
/// 但「计划做 4 组我只做了 2 组」这种执行差异也必须能看到——
/// 所以放在展开里，需要时一点就有。
class _ExerciseRow extends StatelessWidget {
  const _ExerciseRow({required this.exercise});
  final ExerciseSummary exercise;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final skipped = exercise.isSkipped;

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ExpansionTile(
        shape: const Border(),
        collapsedShape: const Border(),
        title: Row(
          children: [
            Expanded(
              child: Text(
                exercise.exerciseName,
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: skipped ? theme.colorScheme.outline : null,
                  decoration: skipped ? TextDecoration.lineThrough : null,
                ),
              ),
            ),
            const SizedBox(width: 8),
            // 紧凑记法：连续相同的组折叠成 `20×10 ×3`
            Text(
              exercise.compactLabel,
              style: theme.textTheme.bodyMedium?.copyWith(
                fontFeatures: const [FontFeature.tabularFigures()],
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Row(
            children: [
              Text(
                '${exercise.doneSets.length}/${exercise.plannedSets} 组',
                style: theme.textTheme.labelSmall,
              ),
              if (skipped) ...[
                const SizedBox(width: 8),
                Text('已跳过',
                    style: theme.textTheme.labelSmall
                        ?.copyWith(color: theme.colorScheme.error)),
              ],
              const Spacer(),
              if (exercise.volume > 0)
                Text('${_fmt(exercise.volume)} kg',
                    style: theme.textTheme.labelSmall),
            ],
          ),
        ),
        children: [_ExpandedSets(exercise: exercise)],
      ),
    );
  }

  static String _fmt(double v) =>
      v.toStringAsFixed(v.truncateToDouble() == v ? 0 : 1);
}

/// 展开后的逐组对照表。
///
/// 左边是**计划**，右边是**实际**——两者的差值就是执行差异，
/// 也是「符合率」这个指标的全部意义。
class _ExpandedSets extends StatelessWidget {
  const _ExpandedSets({required this.exercise});
  final ExerciseSummary exercise;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        children: [
          Row(
            children: [
              _cell('组', theme, header: true, flex: 2),
              _cell('计划', theme, header: true, flex: 4),
              _cell('实际', theme, header: true, flex: 4),
              _cell('', theme, header: true, flex: 2),
            ],
          ),
          const Divider(height: 12),
          ...exercise.sets.map((s) {
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 3),
              child: Row(
                children: [
                  _cell('${s.setNumber}', theme, flex: 2),
                  _cell(s.targetLabel, theme, flex: 4,
                      muted: !s.done),
                  _cell(s.done ? s.actualLabel(exercise.metricType) : '未做',
                      theme, flex: 4,
                      muted: !s.done),
                  // 力竭组 / 递减组这类标记只在展开里显示，
                  // 紧凑行放不下，而且它们不是每次都有
                  _cell(
                      s.setTypeLabel != null && s.setTypeLabel != '正式组'
                          ? s.setTypeLabel!
                          : '',
                      theme,
                      flex: 2,
                      muted: true),
                ],
              ),
            );
          }),
        ],
      ),
    );
  }

  static Widget _cell(String text, ThemeData theme,
      {bool header = false, bool muted = false, int flex = 1}) {
    return Expanded(
      flex: flex,
      child: Text(
        text,
        style: header
            ? theme.textTheme.labelSmall
                ?.copyWith(color: theme.colorScheme.onSurfaceVariant)
            : theme.textTheme.bodySmall?.copyWith(
                color: muted ? theme.colorScheme.outline : null,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
      ),
    );
  }
}
