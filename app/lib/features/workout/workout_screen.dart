import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import '../../core/settings/sound_settings.dart';
import 'summary_screen.dart';
import 'workout_controller.dart';
import 'workout_models.dart';

/// 跟练界面。
///
/// 交互规格见 `docs/TIMER-SPEC.md` 第 5 节与 REQUIREMENTS M4-C：
/// - 主操作按钮在**屏幕下半部拇指热区**，高度 ≥ 64dp
/// - 重量用大号 ±2.5kg 步进器，次数用 ±1，尽量少弹键盘
/// - 屏幕常亮（AC-4-4）
class WorkoutScreen extends ConsumerStatefulWidget {
  const WorkoutScreen({super.key, this.startProgramId, this.startDayNumber});

  /// 有值 = 开始新训练；都为 null = 恢复进行中的会话
  final int? startProgramId;
  final int? startDayNumber;

  @override
  ConsumerState<WorkoutScreen> createState() => _WorkoutScreenState();
}

class _WorkoutScreenState extends ConsumerState<WorkoutScreen>
    with WidgetsBindingObserver {
  bool _starting = true;
  String? _startError;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _boot();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    // 离开跟练界面必须释放 Wake Lock——
    // 否则用户切到别的页面后屏幕还一直亮着，很耗电
    WakelockPlus.disable();
    super.dispose();
  }

  /// ★ 回到前台时重新对表。
  ///
  /// 切后台期间 Dart 定时器可能被系统挂起，倒计时不会自己走完。
  /// 但**我们存的是绝对 deadline**，所以回来时一算就知道过了多久——
  /// 这正是「不存剩余秒数」兑现价值的地方（AC-4-1：锁屏 5 分钟误差 ≤1 秒）。
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      final controller = ref.read(workoutControllerProvider.notifier);
      controller.syncRestTimer();
      // 组内计时也要重新对表。
      //
      // 显示本身是现算的（`phaseStartedAt` 推导），回来一定是对的；
      // 但「间隔播报」那个 periodic Timer 在后台可能被系统丢掉，
      // 不重排的话用户撑到目标时长都听不到一声——而那正是这个功能的价值。
      controller.syncHoldCues();
    }
  }

  Future<void> _boot() async {
    final controller = ref.read(workoutControllerProvider.notifier);
    try {
      if (widget.startProgramId != null) {
        await controller.start(
          programId: widget.startProgramId!,
          dayNumber: widget.startDayNumber!,
          // 幂等键：本次训练的标识。
          // 真实项目里应该持久化它以便崩溃后重试，本步骤先随机生成——
          // 离线队列那一步（3.13）会把它落盘。
          clientKey: 'w-${DateTime.now().microsecondsSinceEpoch}',
        );
      } else {
        await controller.restore();
      }
      await WakelockPlus.enable();
      if (mounted) setState(() => _starting = false);
    } catch (e) {
      if (mounted) {
        setState(() {
          _starting = false;
          _startError = '$e';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_starting) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (_startError != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('跟练')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(_startError!),
                const SizedBox(height: 16),
                OutlinedButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('返回'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final state = ref.watch(workoutControllerProvider);
    if (state == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(state.session.dayName ?? '训练'),
        actions: [
          const _SoundToggleButton(),
          TextButton(
            onPressed: () => _confirmFinish(context, state),
            child: const Text('结束'),
          ),
        ],
      ),
      body: SafeArea(
        child: switch (state.phase) {
          WorkoutPhase.sessionDone => _SessionDoneView(state: state),
          WorkoutPhase.resting || WorkoutPhase.paused =>
            _RestView(state: state),
          WorkoutPhase.exerciseDone => _ExerciseDoneView(state: state),
          _ => _ActiveView(state: state),
        },
      ),
    );
  }

  Future<void> _confirmFinish(BuildContext context, WorkoutState state) async {
    // M4-D-5：结束训练需要二次确认，避免误触丢掉整场数据
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('结束训练？'),
        content: const Text('还没完成的动作会被标记为跳过。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('继续练'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('结束'),
          ),
        ],
      ),
    );
    if (ok == true && context.mounted) {
      await ref.read(workoutControllerProvider.notifier).finishSession();
      if (context.mounted) await _goToSummary(context, ref);
    }
  }

  /// 结束之后进总结页。
  ///
  /// 用 `push` 而不是 `pushReplacement`：总结页的「完成」会
  /// `popUntil(isFirst)` 一路弹回首页，中间这层留着不影响。
  Future<void> _goToSummary(BuildContext context, WidgetRef ref) async {
    final sessionId = ref.read(workoutControllerProvider)?.session.id;
    if (sessionId == null) return;
    await Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => SummaryScreen(sessionId: sessionId),
    ));
  }
}

// ======================================================================
// 训练中（preparing / exercising）
// ======================================================================

/// 训练中（preparing / exercising）。
///
/// 不再持有本地的重量/次数状态——**草稿在状态机里**，
/// 这样休息界面能改同一份数据（见 WorkoutState.draftWeight）。
class _ActiveView extends ConsumerWidget {
  const _ActiveView({required this.state});
  final WorkoutState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final exercise = state.exercise;
    final target = state.currentTarget;
    final controller = ref.read(workoutControllerProvider.notifier);

    final isExercising = state.phase == WorkoutPhase.exercising;
    final metric = exercise.metricType ?? 'WEIGHT_REPS';
    final needsWeight = metric == 'WEIGHT_REPS';
    final needsReps = metric == 'WEIGHT_REPS' || metric == 'REPS_ONLY';
    // 时长类动作（平板支撑）：既不需要重量也不需要次数，
    // 组内跑一个倒计时，撑过头转正计时
    final isTimed = target?.isTimed == true;
    // 其余动作（卧推这类）组内显示一个纯正计时。
    // TIMER-SPEC 1.1 里 exercising 状态本来就写了「已用时间（正计时）」，
    // 实现的时候漏了——这里补上。不发声，纯显示。
    final showCountUp = !isTimed && (needsWeight || needsReps);

    return Column(
      children: [
        _ExerciseHeader(state: state),

        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Column(
              children: [
                const SizedBox(height: 8),
                _TargetCard(target: target, metricType: exercise.metricType),
                const SizedBox(height: 20),

                if (!isExercising)
                  const Text('准备好后点下面的按钮开始这一组')
                else ...[
                  // 计时器单独包一层 _Ticker：只让它自己 250ms 重建一次，
                  // 下面的步进器不跟着每秒重建
                  if (isTimed)
                    _Ticker(builder: (context) => _HoldTimer(state: state))
                  else if (showCountUp)
                    _Ticker(builder: (context) => _CountUpTimer(state: state)),

                  if (needsWeight)
                    _Stepper(
                      label: '重量 (kg)',
                      value: state.draftWeight ?? 0,
                      step: 2.5,
                      decimals: 1,
                      onChanged: controller.setDraftWeight,
                    ),
                  if (needsReps)
                    _Stepper(
                      label: '次数',
                      value: (state.draftReps ?? 0).toDouble(),
                      step: 1,
                      decimals: 0,
                      onChanged: (v) => controller.setDraftReps(v.round()),
                    ),
                  if (needsWeight || needsReps) ...[
                    const SizedBox(height: 8),
                    Text('计划只是建议值，按实际状态改就行',
                        style: Theme.of(context).textTheme.bodySmall),
                  ],
                ],

                if (state.error != null) ...[
                  const SizedBox(height: 16),
                  _ErrorBanner(message: state.error!),
                ],
              ],
            ),
          ),
        ),

        // ---------- 主操作按钮（拇指热区，高度 72dp）----------
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
          child: Column(
            children: [
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: state.saving ? null : controller.skipExercise,
                      style: OutlinedButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                      ),
                      child: const Text('跳过这个动作'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              FilledButton(
                onPressed: state.saving
                    ? null
                    : () {
                        if (isExercising) {
                          controller.completeSet(
                            weight: needsWeight ? state.draftWeight : null,
                            reps: needsReps ? state.draftReps : null,
                          );
                        } else {
                          controller.beginSet();
                        }
                      },
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(72),
                  textStyle: const TextStyle(
                      fontSize: 20, fontWeight: FontWeight.w600),
                ),
                child: Text(isExercising ? '完成本组' : '开始本组'),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _ExerciseHeader extends StatelessWidget {
  const _ExerciseHeader({required this.state});
  final WorkoutState state;

  @override
  Widget build(BuildContext context) {
    final exercise = state.exercise;
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(exercise.exerciseName,
                    style: theme.textTheme.headlineSmall),
              ),
              if (exercise.isSuperset)
                // 本步骤还没实现超级组。数据里已经有标记，
                // 所以**明确提示**而不是静默按顺序做——
                // 静默会让用户以为组间不休息的编排生效了
                Chip(
                  label: const Text('超级组 · 暂不支持'),
                  backgroundColor: theme.colorScheme.errorContainer,
                ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            '第 ${state.exerciseIndex + 1} / ${state.session.exercises.length} 个动作'
            ' · 第 ${state.setNumber} 组 / 共 ${exercise.targetSets} 组',
            style: theme.textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}

class _TargetCard extends StatelessWidget {
  const _TargetCard({required this.target, required this.metricType});
  final SetTarget? target;
  final String? metricType;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (target == null) {
      return Card(
        child: ListTile(
          title: Text('计划外的一组', style: theme.textTheme.titleMedium),
          subtitle: const Text('这一组没有预设目标，按自己状态来'),
        ),
      );
    }
    // 时长类动作要换一套指标。
    //
    // 原样显示的话平板支撑会变成「目标重量 **重量自定** / 目标次数 30-60」——
    // 它既没有重量，次数也不是次数（那是 V14 之前塞进去的秒数）。
    // 照着这个界面练会以为要负重做 30 次。
    if (target!.isTimed) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _Metric(label: '目标时长', value: target!.durationLabel),
              _Metric(label: '播报间隔', value: target!.announceLabel),
              _Metric(label: '组间休息', value: '${target!.restSec}s'),
            ],
          ),
        ),
      );
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _Metric(label: '目标重量', value: target!.weightLabel(metricType)),
            _Metric(label: '目标次数', value: target!.repsLabel),
            _Metric(label: '组间休息', value: '${target!.restSec}s'),
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

/// 大号步进器。
///
/// 为什么不用键盘输入：健身时手上有汗、可能戴手套，
/// 键盘输入又慢又容易错。±2.5kg / ±1 次覆盖了绝大多数调整（M4-C-1/2）。
class _Stepper extends StatelessWidget {
  const _Stepper({
    required this.label,
    required this.value,
    required this.step,
    required this.decimals,
    required this.onChanged,
  });

  final String label;
  final double value;
  final double step;
  final int decimals;
  final ValueChanged<double> onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final text = value.toStringAsFixed(decimals);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Column(
        children: [
          Text(label, style: theme.textTheme.labelMedium),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _RoundButton(
                icon: Icons.remove,
                onPressed: () {
                  final next = value - step;
                  onChanged(next < 0 ? 0 : next);
                },
              ),
              Container(
                width: 140,
                alignment: Alignment.center,
                child: Text(text, style: theme.textTheme.displaySmall),
              ),
              _RoundButton(
                icon: Icons.add,
                onPressed: () => onChanged(value + step),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// 紧凑步进器 —— 休息时用。
///
/// 比训练中的步进器小一号（48dp 而不是 64dp）：
/// 休息界面的主角是倒计时，录入控件不该抢视觉重心。
/// 但**功能完全一样**，都能改成和计划不同的值。
class _CompactStepper extends StatelessWidget {
  const _CompactStepper({
    required this.label,
    required this.value,
    required this.step,
    required this.decimals,
    required this.onChanged,
  });

  final String label;
  final double value;
  final double step;
  final int decimals;
  final ValueChanged<double> onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SizedBox(
            width: 64,
            child: Text(label, style: theme.textTheme.labelMedium),
          ),
          IconButton.filledTonal(
            onPressed: () {
              final next = value - step;
              onChanged(next < 0 ? 0 : next);
            },
            icon: const Icon(Icons.remove),
          ),
          SizedBox(
            width: 80,
            child: Text(
              value.toStringAsFixed(decimals),
              textAlign: TextAlign.center,
              style: theme.textTheme.titleLarge,
            ),
          ),
          IconButton.filledTonal(
            onPressed: () => onChanged(value + step),
            icon: const Icon(Icons.add),
          ),
        ],
      ),
    );
  }
}

class _RoundButton extends StatelessWidget {
  const _RoundButton({required this.icon, required this.onPressed});
  final IconData icon;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 64,
      height: 64,
      child: FilledButton.tonal(
        onPressed: onPressed,
        style: FilledButton.styleFrom(
          shape: const CircleBorder(),
          padding: EdgeInsets.zero,
        ),
        child: Icon(icon, size: 28),
      ),
    );
  }
}

// ======================================================================
// 休息
// ======================================================================

class _RestView extends ConsumerWidget {
  const _RestView({required this.state});
  final WorkoutState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final controller = ref.read(workoutControllerProvider.notifier);
    final paused = state.phase == WorkoutPhase.paused;

    // 每秒重绘一次倒计时。
    //
    // ⚠️ 这个 ticker **只驱动显示**，不驱动状态转移——
    // 剩余时间永远由 `deadline - now` 现算（见 WorkoutState.restRemaining）。
    // 所以即使这个 ticker 被系统挂起，时间也不会算错。
    return _Ticker(
      builder: (context) {
        final remaining = state.restRemaining;
        final secs = remaining.inSeconds;
        final nextTarget = state.currentTarget;
        final nextExercise = state.exercise;
        final nextMetric = nextExercise.metricType ?? 'WEIGHT_REPS';

        return Column(
          children: [
            const Spacer(),
            Text(paused ? '已暂停' : '休息中',
                style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            Text(
              '${secs ~/ 60}:${(secs % 60).toString().padLeft(2, '0')}',
              style: theme.textTheme.displayLarge?.copyWith(
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
            const SizedBox(height: 16),
            Text(
              '下一组：${nextExercise.exerciseName} · 第 ${state.setNumber} 组',
              style: theme.textTheme.bodyLarge,
              textAlign: TextAlign.center,
            ),
            if (nextTarget != null)
              Text(
                // 时长类动作不能按「重量 × 次数」写，那会显示成
                // 「计划 重量自定 × 30-60」——平板支撑没有重量也没有次数
                nextTarget.isTimed
                    ? '计划 撑 ${nextTarget.durationLabel}'
                    : '计划 ${nextTarget.weightLabel(nextExercise.metricType)} × ${nextTarget.repsLabel}',
                style: theme.textTheme.bodySmall,
              ),
            const SizedBox(height: 20),

            // ---------- ★ 休息时也能改下一组的重量/次数 ----------
            //
            // 做完一组坐下来，趁两分钟休息把下一组的重量调好，
            // 是很自然的动作。之前只能在点了「开始本组」之后才调，
            // 而现在人正坐着看倒计时——那才是最想调的时候。
            //
            // 计划只是建议值，用户随时可以改成完全不一样的数（M4-D-4）。
            if (nextMetric == 'WEIGHT_REPS')
              _CompactStepper(
                label: '重量 (kg)',
                value: state.draftWeight ?? 0,
                step: 2.5,
                decimals: 1,
                onChanged: controller.setDraftWeight,
              ),
            if (nextMetric == 'WEIGHT_REPS' || nextMetric == 'REPS_ONLY')
              _CompactStepper(
                label: '次数',
                value: (state.draftReps ?? 0).toDouble(),
                step: 1,
                decimals: 0,
                onChanged: (v) => controller.setDraftReps(v.round()),
              ),

            const Spacer(),

            // ±15 秒。休息时长调错了很常见，
            // 重新开始一次休息比调时间麻烦得多（M4-B-4）
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                OutlinedButton(
                  onPressed: () => controller.adjustRest(-15),
                  style: OutlinedButton.styleFrom(
                      minimumSize: const Size(110, 56)),
                  child: const Text('-15s'),
                ),
                const SizedBox(width: 12),
                OutlinedButton(
                  onPressed: () => controller.adjustRest(15),
                  style: OutlinedButton.styleFrom(
                      minimumSize: const Size(110, 56)),
                  child: const Text('+15s'),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
              child: Column(
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          onPressed: paused
                              ? controller.resume
                              : controller.pause,
                          style: OutlinedButton.styleFrom(
                              minimumSize: const Size.fromHeight(48)),
                          child: Text(paused ? '继续' : '暂停'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  // 「跳过休息」是弱化按钮且远离主操作（M4-D-5）
                  TextButton(
                    onPressed: () => controller.endRest(skipped: true),
                    child: const Text('跳过休息'),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}

/// 每秒重建一次子树的计时器。
///
/// 用独立的 widget 而不是把整个页面设为 StatefulWidget：
/// **只让倒计时那部分重绘**，录入控件不跟着每秒重建。
/// 时长类动作的组内计时：倒计时 → 撑过头自动转正计时。
///
/// 全部由 `phaseStartedAt` 现算（[WorkoutState.holdRemaining] 等），
/// 到点不产生状态转移——所以切后台、锁屏、定时器被吞，显示都不会错。
class _HoldTimer extends StatelessWidget {
  const _HoldTimer({required this.state});
  final WorkoutState state;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final overtime = state.isHoldOvertime;
    final target = state.holdTargetSec ?? 0;

    // 倒计时显示剩余，超时显示超出部分
    final shown = overtime ? state.holdOvertime : state.holdRemaining;
    final secs = shown.inSeconds;

    return Column(
      children: [
        Text(
          overtime ? '已超目标' : '剩余',
          style: theme.textTheme.labelMedium?.copyWith(
            color: overtime ? theme.colorScheme.error : null,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          // 超时加个 + 号，一眼看出是在目标之外
          '${overtime ? '+' : ''}${_mmss(secs)}',
          style: theme.textTheme.displayLarge?.copyWith(
            // 等宽数字：不加的话数字跳动时整行会左右抖
            fontFeatures: const [FontFeature.tabularFigures()],
            color: overtime ? theme.colorScheme.error : null,
          ),
        ),
        const SizedBox(height: 12),
        // 进度条到 100% 就停住——超时的时间没有「进度」可言，
        // 让条满格溢出反而看不出已经超了
        ClipRRect(
          borderRadius: BorderRadius.circular(4),
          child: LinearProgressIndicator(
            value: state.holdProgress,
            minHeight: 6,
            backgroundColor: theme.colorScheme.surfaceContainerHighest,
            color: overtime ? theme.colorScheme.error : theme.colorScheme.primary,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          overtime ? '目标 $target 秒 · 撑住就是赚' : '目标 $target 秒',
          style: theme.textTheme.bodySmall,
        ),
      ],
    );
  }
}

/// 次数类动作的组内正计时。**纯显示，不发声**——用户选的是「组内不打扰」。
class _CountUpTimer extends StatelessWidget {
  const _CountUpTimer({required this.state});
  final WorkoutState state;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      children: [
        Text('本组已用', style: theme.textTheme.labelMedium),
        const SizedBox(height: 4),
        Text(
          _mmss(state.setElapsed.inSeconds),
          style: theme.textTheme.headlineMedium?.copyWith(
            fontFeatures: const [FontFeature.tabularFigures()],
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 8),
      ],
    );
  }
}

/// 跟练页的声音开关。
///
/// **为什么必须有**：休息结束播报下一组目标依赖语音，而语音默认关闭
/// （TIMER-SPEC 3.4）。没有这个入口的话，「开始前报目标」这个选择
/// 在界面上根本无法生效——`setVoiceEnabled` 之前是个零调用者的方法。
///
/// M9 会做完整的设置页（M9-3 要求提示音/语音/震动三个开关），
/// 这里先落跟练现场最需要的那两个 + 持久化，设置页到时把 UI 挪过去。
class _SoundToggleButton extends ConsumerWidget {
  const _SoundToggleButton();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(soundSettingsProvider);

    // 图标反映「能不能听见」的整体状态
    final IconData icon;
    if (!settings.soundEnabled) {
      icon = Icons.volume_off;
    } else if (settings.voiceEnabled) {
      icon = Icons.record_voice_over;
    } else {
      icon = Icons.volume_up;
    }

    return PopupMenuButton<void>(
      icon: Icon(icon),
      tooltip: '声音',
      itemBuilder: (context) => [
        PopupMenuItem<void>(
          enabled: false,
          // ⚠️ 这里必须是 Consumer，不能让外面 `build` 里的 `settings` 穿进来。
          //
          // 弹出菜单渲染在 **overlay 路由**里，是另一棵 widget 树。
          // 外层 `_SoundToggleButton` 确实 watch 了 provider、也确实重建了，
          // 但**已经打开的这个菜单不会跟着重建**——于是用户拨了开关、
          // 状态真的变了（TTS 都初始化了），开关却还停在原位。
          // 用户会以为没点上，再拨一次，又拨回去了。
          //
          // 真机上才看得见：日志显示 `setVoiceEnabled(true)` 执行了，
          // 而截图里开关还是关的。
          child: Consumer(
            builder: (context, ref, _) {
              final s = ref.watch(soundSettingsProvider);
              void apply() => ref
                  .read(workoutControllerProvider.notifier)
                  .applySoundSettings(ref.read(soundSettingsProvider));

              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    value: s.soundEnabled,
                    title: const Text('提示音'),
                    subtitle: const Text('倒计时与归零的提示音、震动'),
                    onChanged: (v) {
                      ref
                          .read(soundSettingsProvider.notifier)
                          .setSoundEnabled(v);
                      apply();
                    },
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    value: s.voiceEnabled,
                    title: const Text('语音播报'),
                    subtitle: const Text('报出下一组的目标，不用看屏幕'),
                    onChanged: (v) {
                      ref
                          .read(soundSettingsProvider.notifier)
                          .setVoiceEnabled(v);
                      apply();
                    },
                  ),
                ],
              );
            },
          ),
        ),
      ],
    );
  }
}

/// 秒 → `m:ss`（超过一小时只显示分钟，跟练不会那么久）
String _mmss(int totalSeconds) {
  final s = totalSeconds < 0 ? 0 : totalSeconds;
  return '${s ~/ 60}:${(s % 60).toString().padLeft(2, '0')}';
}

class _Ticker extends StatefulWidget {
  const _Ticker({required this.builder});
  final WidgetBuilder builder;

  @override
  State<_Ticker> createState() => _TickerState();
}

class _TickerState extends State<_Ticker> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(milliseconds: 250), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.builder(context);
}

// ======================================================================
// 动作过渡 / 完成
// ======================================================================

class _ExerciseDoneView extends ConsumerWidget {
  const _ExerciseDoneView({required this.state});
  final WorkoutState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final next = state.isLastExercise
        ? null
        : state.session.exercises[state.exerciseIndex + 1];

    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.check_circle,
              size: 72, color: theme.colorScheme.primary),
          const SizedBox(height: 16),
          Text('${state.exercise.exerciseName} 完成',
              style: theme.textTheme.headlineSmall),
          const SizedBox(height: 24),
          if (next != null) ...[
            Text('下一个动作', style: theme.textTheme.labelMedium),
            const SizedBox(height: 4),
            Text(next.exerciseName, style: theme.textTheme.titleLarge),
            const SizedBox(height: 4),
            Text('${next.targetSets} 组', style: theme.textTheme.bodyMedium),
          ] else
            const Text('这是最后一个动作'),
          const SizedBox(height: 40),
          FilledButton(
            onPressed: () =>
                ref.read(workoutControllerProvider.notifier).nextExercise(),
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(64)),
            child: Text(next == null ? '完成训练' : '下一个动作'),
          ),
        ],
      ),
    );
  }
}

class _SessionDoneView extends ConsumerWidget {
  const _SessionDoneView({required this.state});
  final WorkoutState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final done = state.session.exercises
        .where((e) => e.status == 'COMPLETED')
        .length;
    final skipped =
        state.session.exercises.where((e) => e.status == 'SKIPPED').length;

    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.emoji_events, size: 80, color: theme.colorScheme.primary),
          const SizedBox(height: 16),
          Text('训练完成', style: theme.textTheme.headlineMedium),
          const SizedBox(height: 8),
          Text('完成 $done 个动作${skipped > 0 ? '，跳过 $skipped 个' : ''}',
              style: theme.textTheme.bodyLarge),
          const SizedBox(height: 32),
          const SizedBox(height: 40),
          FilledButton(
            onPressed: () async {
              // 全部动作做完之后**还要调一次结束接口**——
              // 不调的话会话一直是 IN_PROGRESS，训练日轮转不会推进，
              // 下次打开首页还推荐同一个训练日。
              await ref.read(workoutControllerProvider.notifier).finishSession();
              if (context.mounted) {
                final sessionId =
                    ref.read(workoutControllerProvider)?.session.id;
                if (sessionId != null) {
                  await Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) => SummaryScreen(sessionId: sessionId),
                  ));
                }
              }
            },
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(64)),
            child: const Text('查看训练总结'),
          ),
        ],
      ),
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner({required this.message});
  final String message;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: scheme.errorContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(message, style: TextStyle(color: scheme.onErrorContainer)),
    );
  }
}
