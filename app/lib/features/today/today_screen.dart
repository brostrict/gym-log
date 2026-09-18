import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../core/providers.dart';
import '../body/body_screen.dart';
import '../exercise/exercise_library_screen.dart';
import '../program/template_picker.dart';
import '../stats/stats_screen.dart';
import '../workout/workout_controller.dart';
import '../workout/workout_screen.dart';

/// 「今天练什么」。
///
/// ⚠️ **这一步是冒烟页**：它存在的意义是证明
/// 「flutter → dio → 带 token → 后端 → 数据库」这条链路在真机上通了。
///
/// 界面上没有任何跟练相关的东西——那部分要用状态机重写（TIMER-SPEC）。
/// 但**数据形状已经是对的**：它直接消费 `GET /workouts/today`，
/// 后面重写界面时这个 provider 可以原样保留。
class TodayScreen extends ConsumerWidget {
  const TodayScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final today = ref.watch(todayWorkoutProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('今天练什么'),
        actions: [
          // 趋势页入口。
          //
          // 用 AppBar 图标 + `Navigator.push`，**没有改 `_Root` 加底部导航**：
          // 底部导航需要一个保持状态的 shell（`IndexedStack` 或嵌套 Navigator），
          // 那是另一个量级的工作。等页面再多（历史 / 我的）再一起重构。
          IconButton(
            icon: const Icon(Icons.insights),
            tooltip: '趋势',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const StatsScreen()),
            ),
          ),
          // 身体数据入口。
          //
          // 和「趋势」并列而不是塞进趋势页里：两页回答的是**不同的问题**——
          // 那边是「我变强了吗」（训练数据），这边是「我变了吗」（身体数据）。
          // 塞进同一页的话，14 个身体指标会把训练图表挤走。
          IconButton(
            icon: const Icon(Icons.monitor_weight_outlined),
            tooltip: '身体数据',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const BodyScreen()),
            ),
          ),
          // 动作库入口。
          //
          // ⚠️ AppBar 现在有 4 个图标了，手机上已经偏挤。
          // 这是「该做底部导航了」的信号——4.4 时我把它推迟了
          // （底部导航需要一个保持状态的 shell，是另一个量级的工作）。
          // 等再加页面就必须做，见 DEVELOPMENT-PLAN 待办。
          IconButton(
            icon: const Icon(Icons.fitness_center),
            tooltip: '动作库',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const ExerciseLibraryScreen()),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(todayWorkoutProvider),
          ),
        ],
      ),
      body: Column(
        children: [
          // 有未完成的训练时，顶部挂一条「继续上次训练」。
          // 放在首页最上方而不是做成一个按钮——**它比「开始新训练」更优先**：
          // 上一场没结束，现在开始新的只会把旧的晾在那里。
          const _ActiveSessionBanner(),
          Expanded(
            child: today.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => _ErrorView(
                message: e is ApiException ? e.message : '$e',
                onRetry: () => ref.invalidate(todayWorkoutProvider),
              ),
              data: (data) => _TodayView(data: data),
            ),
          ),
        ],
      ),
    );
  }
}

/// 进入跟练界面。
///
/// 用 `push` 而不是替换路由：练完之后要能返回首页看更新后的状态。
///
/// ⚠️ **返回之后必须让 provider 失效。**
///
/// `activeSessionProvider` 是带缓存的 FutureProvider。用户点「开始训练」
/// 创建了会话，然后用返回箭头退出——如果不 invalidate，
/// 首页拿到的还是**开始训练之前**那份缓存（当时没有进行中的会话），
/// 于是「继续上次训练」横幅永远不出现。
///
/// 这个 bug 真机上验证过：数据库里会话是 IN_PROGRESS，界面却没有横幅。
Future<void> _startWorkout(BuildContext context, Map<String, dynamic> data) async {
  await Navigator.of(context).push(MaterialPageRoute(
    builder: (_) => WorkoutScreen(
      startProgramId: data['programId'] as int?,
      startDayNumber: data['dayNumber'] as int?,
    ),
  ));
  // 返回后刷新——理由见上面的注释
  if (context.mounted) {
    ProviderScope.containerOf(context).invalidate(activeSessionProvider);
    ProviderScope.containerOf(context).invalidate(todayWorkoutProvider);
  }
}

/// 拉取「今天练什么」。
///
/// 用 `FutureProvider` 而不是 `Notifier`：这是一次纯粹的读请求，
/// 没有需要维护的状态转换。`ref.invalidate` 就能刷新。
final todayWorkoutProvider = FutureProvider<Map<String, dynamic>>((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/workouts/today');
  return (data as Map).cast<String, dynamic>();
});

class _TodayView extends ConsumerWidget {
  const _TodayView({required this.data});
  final Map<String, dynamic> data;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final programId = data['programId'];

    if (programId == null) {
      // 没有计划 → 直接给模板库，而不是一句「还没有计划」。
      // 新用户的第一屏必须能**直接做点什么**。
      return TemplatePicker(onCreated: () {
        ref.invalidate(todayWorkoutProvider);
        ref.invalidate(activeSessionProvider);
      });
    }

    final exercises = (data['exercises'] as List?) ?? const [];
    final theme = Theme.of(context);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // ---------- 计划与位置 ----------
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(data['programName'] as String? ?? '',
                    style: theme.textTheme.titleMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: [
                    Chip(label: Text('第 ${data['weekNumber']} 周')),
                    Chip(label: Text(data['scheduleStateLabel'] as String? ?? '')),
                    if (data['deloadWeek'] == true)
                      const Chip(label: Text('减量周')),
                    if (data['weightAdjustPct'] != null &&
                        (data['weightAdjustPct'] as num) != 0)
                      Chip(label: Text('重量 ${data['weightAdjustPct']}%')),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),

        // ---------- 今天练的训练日 ----------
        if (data['dayName'] == null)
          Card(
            child: ListTile(
              leading: const Icon(Icons.event_busy),
              title: Text(data['scheduleStateLabel'] as String? ?? '今天没有安排'),
              subtitle: const Text('计划还没开始，或已经结束'),
            ),
          )
        else ...[
          Text('今天 · ${data['dayName']}',
              style: theme.textTheme.titleLarge),
          const SizedBox(height: 8),
          ...exercises.map((e) => _ExerciseCard(exercise: e as Map)),
        ],

        // ---------- 开始训练 ----------
        if (data['dayName'] != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: FilledButton.icon(
              onPressed: () => _startWorkout(context, data),
              icon: const Icon(Icons.play_arrow),
              label: const Text('开始训练'),
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(64),
                textStyle: const TextStyle(
                    fontSize: 18, fontWeight: FontWeight.w600),
              ),
            ),
          ),

        const SizedBox(height: 16),
        // ---------- 冒烟信息：证明链路通了 ----------
        Card(
          color: theme.colorScheme.surfaceContainerHighest,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('链路自检', style: theme.textTheme.labelLarge),
                const SizedBox(height: 4),
                Text(
                  '已完成 ${data['completedSessions']} 次训练 · '
                  '可选训练日 ${(data['dayOptions'] as List?)?.length ?? 0} 个',
                  style: theme.textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _ExerciseCard extends StatelessWidget {
  const _ExerciseCard({required this.exercise});
  final Map exercise;

  @override
  Widget build(BuildContext context) {
    final sets = (exercise['sets'] as List?) ?? const [];

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('#${exercise['orderIndex']}',
                    style: Theme.of(context).textTheme.labelMedium),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    exercise['exerciseName'] as String? ?? '未知动作',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                if (exercise['primaryMuscleLabel'] != null)
                  Text(exercise['primaryMuscleLabel'] as String,
                      style: Theme.of(context).textTheme.labelSmall),
              ],
            ),
            const SizedBox(height: 8),
            // 每组的目标。这里显示的是**展开后**的值——
            // 「第 5 周 +5%」在后端已经算成 63kg 了，客户端不做乘法
            ...sets.map((s) {
              final set = s as Map;
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Text(
                  '第 ${set['setNumber']} 组 · '
                  '${_setTargetLabel(set, exercise['metricType'] as String?)}'
                  ' · 休息 ${set['restSec']}s',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              );
            }),
          ],
        ),
      ),
    );
  }
}

/// 一组的目标整体怎么显示：`60.0 kg × 6-8` 或 `撑 30 秒`。
///
/// ⚠️ **时长类动作必须走单独一条路。**
///
/// V14 把平板支撑的秒数从 `targetRepsMin/Max` 搬到了 `targetDurationSec`，
/// 于是 reps 那一支拼出来是字符串 `"null-null"`——
/// 首页会显示成「重量自定 × null-null」，既没有重量也没有次数。
///
/// 这是「字段搬家」最容易漏的一类地方：后端改了契约，
/// **每一个曾经读过旧字段的界面都要跟着改**，而漏掉的地方不会报错，
/// 只会在界面上印出一个 `null`。
String _setTargetLabel(Map set, String? metricType) {
  final dur = set['targetDurationSec'];
  if (dur is int && dur > 0) {
    return '撑 ${dur >= 60 ? '${dur ~/ 60} 分${dur % 60 == 0 ? '' : ' ${dur % 60} 秒'}' : '$dur 秒'}';
  }

  final reps = set['targetReps'] ??
      (set['targetRepsMin'] != null && set['targetRepsMax'] != null
          ? '${set['targetRepsMin']}-${set['targetRepsMax']}'
          : '—');
  return '${_weightLabel(set['target'] as Map?, metricType)} × $reps';
}

/// 一组的目标重量怎么显示。
///
/// ⚠️ **不能简单地「没有重量就当自重」**。
///
/// 真机验证时发现的问题：从模板创建的计划里，杠铃卧推显示成了
/// 「自重 × 6-8」——而它显然不是自重动作。
///
/// 原因是 `target` 为 null 有**两种完全不同的含义**：
///
/// | 情况 | metricType | 含义 | 该显示 |
/// |---|---|---|---|
/// | 动作本身就不负重（引体、俯卧撑） | `REPS_ONLY` | 真的没有重量 | 自重 |
/// | 计划没填目标重量（模板创建的计划） | `WEIGHT_REPS` | 该用户自己决定用多重 | 重量自定 |
///
/// 混为一谈会让用户以为「杠铃卧推不用加片」——照着练是危险的。
String _weightLabel(Map? target, String? metricType) {
  final weight = target?['weight'];
  if (weight != null) {
    return '$weight kg';
  }
  final pct = target?['pct'];
  if (pct != null) {
    return '$pct% 1RM';
  }
  final rpe = target?['rpe'];
  if (rpe != null) {
    return 'RPE $rpe';
  }
  return metricType == 'REPS_ONLY' ? '自重' : '重量自定';
}

/// 有未完成的训练时挂在首页顶部。
///
/// ⚠️ 「未完成」不等于「练了一半就放弃」——用户锁屏、切后台、
/// 甚至杀掉 App，只要没点结束，会话就还是 IN_PROGRESS。
/// 训练场上被打断是常态，**这条横幅就是让他能接着练**。
class _ActiveSessionBanner extends ConsumerWidget {
  const _ActiveSessionBanner();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // 用 asData?.value 而不是 .value / .valueOrNull——
    // 后两者的名字在 Riverpod 2 和 3 之间变过，asData 是稳定的。
    //
    // 这里**刻意忽略 loading/error**：横幅是锦上添花的东西，
    // 拉取失败时安静地不显示就好，不该在首页上弹一个错误。
    final session = ref.watch(activeSessionProvider).asData?.value;
    if (session == null) return const SizedBox.shrink();

    final theme = Theme.of(context);
    final done = session.exercises
        .where((e) => e.status == 'COMPLETED' || e.status == 'SKIPPED')
        .length;

    return Material(
      color: theme.colorScheme.primaryContainer,
      child: InkWell(
        onTap: () async {
          await Navigator.of(context).push(MaterialPageRoute(
            // 不传 programId → 走「恢复进行中的会话」路径
            builder: (_) => const WorkoutScreen(),
          ));
          ref.invalidate(activeSessionProvider);
          ref.invalidate(todayWorkoutProvider);
        },
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          child: Row(
            children: [
              Icon(Icons.play_circle_fill,
                  color: theme.colorScheme.onPrimaryContainer),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('继续上次训练',
                        style: theme.textTheme.titleSmall?.copyWith(
                            color: theme.colorScheme.onPrimaryContainer)),
                    Text(
                      '${session.dayName ?? '训练'} · '
                      '已完成 $done/${session.exercises.length} 个动作',
                      style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onPrimaryContainer),
                    ),
                  ],
                ),
              ),
              Icon(Icons.chevron_right,
                  color: theme.colorScheme.onPrimaryContainer),
            ],
          ),
        ),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.cloud_off,
                size: 56, color: Theme.of(context).colorScheme.error),
            const SizedBox(height: 16),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            OutlinedButton(onPressed: onRetry, child: const Text('重试')),
            const SizedBox(height: 12),
            Text(
              '真机连不上后端时，先确认 adb reverse tcp:8080 tcp:8080 还在',
              style: Theme.of(context).textTheme.bodySmall,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
