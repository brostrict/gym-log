import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../core/providers.dart';
import '../../core/settings/sound_settings.dart';
import 'workout_api.dart';
import 'workout_audio.dart';
import 'workout_models.dart';

/// 跟练状态机。
///
/// 定义见 `docs/TIMER-SPEC.md` 第 1 节。这里实现的是**顺序执行版**——
/// 超级组的二维推进一步（3.10）。
///
/// ```
///   preparing ──开始本组──► exercising ──完成本组──► (落盘)
///       ▲                                               │
///       │                                    还有组 ────┴──── 没有更多组
///       │                                       │              │
///       │                                       ▼              ▼
///       └──────────── resting ◄─────────────────┘      exerciseDone
///            （倒计时 / ±15s / 跳过 / 暂停）                   │
///                                                  还有动作 ──┴── 没有动作
///                                                      │            │
///                                                      ▼            ▼
///                                                  preparing    sessionDone
/// ```
enum WorkoutPhase {
  /// 准备做本组：显示目标 + 「开始本组」
  preparing,

  /// 正在做本组：正计时
  exercising,

  /// 组间休息：倒计时
  resting,

  /// 休息暂停（接电话、去洗手间）
  paused,

  /// 当前动作做完了，动作间过渡
  exerciseDone,

  /// 全部完成
  sessionDone,
}

/// 状态机的全部状态。
///
/// **不可变**——每次转移产生一个新实例。这样「上一个状态是什么」
/// 不会被就地修改掉，调试和日后做撤销都容易得多。
class WorkoutState {
  const WorkoutState({
    required this.session,
    required this.phase,
    required this.exerciseIndex,
    required this.setNumber,
    this.restDeadlineAt,
    this.pausedAt,
    this.phaseStartedAt,
    this.restDurationSec = 0,
    this.lastCompleted,
    this.draftWeight,
    this.draftReps,
    this.error,
    this.saving = false,
  });

  final WorkoutSession session;
  final WorkoutPhase phase;

  /// 当前第几个动作（0-based）
  final int exerciseIndex;

  /// 当前第几组（1-based）
  final int setNumber;

  /// ★ **休息结束的绝对时刻（epoch ms 的 DateTime）。**
  ///
  /// 这是「还剩多久」的**唯一来源**——不存在第二个 `remaining` 变量。
  ///
  /// 为什么必须存绝对时刻而不是剩余秒数：
  /// 切后台、锁屏会让 Dart 的 Timer 冻结，存剩余秒数必然漂移——
  /// 锁屏 5 分钟回来会显示「还剩 40 秒」，而其实早该结束了。
  /// 存 deadline 则无论中断多久，`deadline - now` 永远是对的（AC-4-1）。
  final DateTime? restDeadlineAt;

  /// 暂停时刻。**仅 `paused` 状态有值。**
  final DateTime? pausedAt;

  final DateTime? phaseStartedAt;

  /// 本次休息的总时长，用于进度条百分比
  final int restDurationSec;

  /// 刚完成的那一组。
  ///
  /// 休息结束时要用它**再 PUT 一次**，把 `restActualSec` 补上——
  /// 因为「这一组之后歇了多久」在完成那一组的当下还不知道。
  /// PUT 是幂等的，重复发同一条不会产生重复记录。
  final CompletedSet? lastCompleted;

  /// 这一组即将记录的重量/次数**草稿**。
  ///
  /// 放在状态里而不是界面局部，是因为**休息时也要能改**——
  /// 用户做完一组坐下来，趁两分钟休息把下一组的重量调好是很自然的动作。
  /// 放在界面里的话，休息界面看不到它，只能等点了「开始本组」才能调。
  ///
  /// 计划值只是**初始默认**（见 [_draftWeightFor]），用户随时可以改成
  /// 和计划完全不一样的数——这是 M4-D-4「临时调整目标次数/重量」的要求。
  final double? draftWeight;
  final int? draftReps;

  /// 上一次操作的错误（记录失败等）。不阻塞流程，只提示。
  final String? error;

  final bool saving;

  WorkoutExercise get exercise => session.exercises[exerciseIndex];

  bool get isLastExercise => exerciseIndex >= session.exercises.length - 1;

  /// 当前这一组的目标。找不到时返回 null（用户临时加组）。
  SetTarget? get currentTarget {
    for (final t in exercise.sets) {
      if (t.setNumber == setNumber) return t;
    }
    return null;
  }

  /// 距离休息结束还有多久。
  ///
  /// **由 deadline 现算，不存字段。** 这是整个计时设计的关键。
  Duration get restRemaining {
    final deadline = restDeadlineAt;
    if (deadline == null) return Duration.zero;
    final base = pausedAt ?? DateTime.now();
    final remaining = deadline.difference(base);
    return remaining.isNegative ? Duration.zero : remaining;
  }

  bool get isRestOver =>
      restDeadlineAt != null && !restDeadlineAt!.isAfter(DateTime.now());

  // ==================================================================
  // 组内计时（时长类动作）
  // ==================================================================

  /// ★ **不存 deadline，全部由 `phaseStartedAt` 现算。**
  ///
  /// 倒计时剩余和超时时长是**同一个数的两种符号**，不是两个状态：
  ///
  /// ```
  /// 已用   = now − phaseStartedAt
  /// 剩余   = targetDurationSec − 已用     （正数 = 倒计时中）
  /// 超时   = 已用 − targetDurationSec     （正数 = 已超目标）
  /// ```
  ///
  /// 好处是「到点」纯粹是一个**时间事实**，不是一个状态转移：
  /// 归零那一刻不需要改 state（改了就多一个必须清干净的字段，
  /// 而 `copyWith` 没法把它置回 null——上一组的残留会变成粘性标志）。
  /// Timer 被系统吞掉的最坏后果只是**少响一声**，显示永远是对的。

  /// 本组目标时长。null = 不是按时长做的动作
  int? get holdTargetSec {
    final t = currentTarget?.targetDurationSec;
    return (t != null && t > 0) ? t : null;
  }

  /// 本组已进行多久
  Duration get setElapsed {
    final start = phaseStartedAt;
    if (start == null) return Duration.zero;
    return DateTime.now().difference(start);
  }

  /// 倒计时剩余。已到点或没有时长目标时返回 [Duration.zero]
  Duration get holdRemaining {
    final target = holdTargetSec;
    if (target == null) return Duration.zero;
    final left = Duration(seconds: target) - setElapsed;
    return left.isNegative ? Duration.zero : left;
  }

  /// 是否已经撑过目标时长
  bool get isHoldOvertime {
    final target = holdTargetSec;
    if (target == null) return false;
    return setElapsed > Duration(seconds: target);
  }

  /// 超出目标多久。没超时返回 [Duration.zero]
  Duration get holdOvertime {
    final target = holdTargetSec;
    if (target == null) return Duration.zero;
    final over = setElapsed - Duration(seconds: target);
    return over.isNegative ? Duration.zero : over;
  }

  /// 倒计时进度 0.0 → 1.0（用于进度条）。没有目标时返回 0
  double get holdProgress {
    final target = holdTargetSec;
    if (target == null || target == 0) return 0;
    final ratio = setElapsed.inMilliseconds / (target * 1000);
    return ratio.clamp(0.0, 1.0);
  }

  WorkoutState copyWith({
    WorkoutSession? session,
    WorkoutPhase? phase,
    int? exerciseIndex,
    int? setNumber,
    DateTime? restDeadlineAt,
    DateTime? pausedAt,
    bool clearPausedAt = false,
    DateTime? phaseStartedAt,
    int? restDurationSec,
    CompletedSet? lastCompleted,
    double? draftWeight,
    int? draftReps,
    String? error,
    bool clearError = false,
    bool? saving,
  }) {
    return WorkoutState(
      session: session ?? this.session,
      phase: phase ?? this.phase,
      exerciseIndex: exerciseIndex ?? this.exerciseIndex,
      setNumber: setNumber ?? this.setNumber,
      restDeadlineAt: restDeadlineAt ?? this.restDeadlineAt,
      pausedAt: clearPausedAt ? null : (pausedAt ?? this.pausedAt),
      phaseStartedAt: phaseStartedAt ?? this.phaseStartedAt,
      restDurationSec: restDurationSec ?? this.restDurationSec,
      lastCompleted: lastCompleted ?? this.lastCompleted,
      draftWeight: draftWeight ?? this.draftWeight,
      draftReps: draftReps ?? this.draftReps,
      error: clearError ? null : (error ?? this.error),
      saving: saving ?? this.saving,
    );
  }
}

/// 刚完成的一组，用于休息结束后补写 `restActualSec`。
class CompletedSet {
  const CompletedSet({
    required this.sessionExerciseId,
    required this.setNumber,
    required this.setType,
    required this.weight,
    required this.reps,
    required this.rpe,
    required this.completedAt,
    this.durationSec,
  });

  final int sessionExerciseId;
  final int setNumber;
  final String setType;
  final double? weight;
  final int? reps;
  final double? rpe;
  final DateTime completedAt;

  /// 实际持续时长（秒）。时长类动作才有值。
  ///
  /// ⚠️ **这个字段必须跟着 [WorkoutController._recordActualRest] 一起回传。**
  ///
  /// 记录组是 `PUT`，而服务端是**全量覆盖**语义——没传的字段一律写 null。
  /// 休息结束时会用同一组再 PUT 一次（补写实际休息时长），
  /// 那次如果不带 `durationSec`，刚记好的时长就被**静默抹掉**了。
  ///
  /// 而且只有非最后一组会被抹（最后一组走 exerciseDone，不经过休息），
  /// 所以现象是「同一个动作里前几组没时长、最后一组有」——极难排查。
  final int? durationSec;
}

// ======================================================================
// Controller
// ======================================================================

final workoutApiProvider = Provider<WorkoutApi>(
  (ref) => WorkoutApi(ref.watch(apiClientProvider)),
);

/// 当前进行中的会话。null = 没在训练。
///
/// 首页用它决定要不要显示「继续上次训练」——
/// **用户杀进程、锁屏、切后台之后回来，得能接着练。**
/// 状态从**服务端**恢复（已记录的组数就是该做第几组），不依赖本地存储。
final activeSessionProvider = FutureProvider<WorkoutSession?>((ref) async {
  return ref.watch(workoutApiProvider).activeSession();
});

/// 当前跟练的状态机。
///
/// `null` = 还没开始（首页状态）。
final workoutControllerProvider =
    NotifierProvider<WorkoutController, WorkoutState?>(WorkoutController.new);

/// 听觉提示。全局唯一——同时只会有一次跟练在跑。
final workoutAudioProvider = Provider<WorkoutAudio>((ref) {
  final audio = WorkoutAudio();
  ref.onDispose(audio.dispose);
  return audio;
});

class WorkoutController extends Notifier<WorkoutState?> {
  Timer? _restTimer;

  /// 剩余 3 秒的三声短音
  Timer? _warnTimer;

  /// 休息过半的那一声
  Timer? _halfwayTimer;

  // ---------- 组内计时（时长类动作）----------

  /// 撑到目标时长的那一刻（长音）
  Timer? _holdEndTimer;

  /// 组内剩余 3 秒的三声短音
  Timer? _holdWarnTimer;

  /// 组内间隔播报。
  ///
  /// 这一个**用 periodic**，和休息那三个单次 Timer 不同：
  /// 间隔播报天然是重复的，而且超时期间要一直响到封顶为止——
  /// 用单次 Timer 得预先排出几十个，还得为「撑过头多久」设上限。
  /// 每次 tick 都从 `phaseStartedAt` 现算，所以不会累积漂移。
  Timer? _holdIntervalTimer;

  /// 本次训练的开始时刻，用于算总时长
  DateTime? _sessionStartedAt;

  @override
  WorkoutState? build() {
    ref.onDispose(() {
      _cancelTimers();
    });
    return null;
  }

  WorkoutApi get _api => ref.read(workoutApiProvider);
  WorkoutAudio get _audio => ref.read(workoutAudioProvider);

  /// 把声音偏好应用到音频服务。
  ///
  /// ⚠️ 开了语音时 `setVoiceEnabled(true)` 会去初始化 TTS 引擎，
  /// 而 `flutter_tts` 在引擎就绪前会**挂起所有方法调用**——
  /// 设备没装中文引擎时那个 Future 可能永远不完成。
  ///
  /// 所以这里**刻意不 await**：提示音是核心功能（AC-4-8），
  /// 绝不能被语音的初始化拖住。语音晚几秒能用没关系。
  void applySoundSettings(SoundSettings settings) {
    final audio = _audio;
    audio.setSoundEnabled(settings.soundEnabled);
    unawaited(audio.setVoiceEnabled(settings.voiceEnabled));
  }

  void _cancelTimers() {
    _restTimer?.cancel();
    _warnTimer?.cancel();
    _halfwayTimer?.cancel();
    _cancelHoldTimers();
  }

  void _cancelHoldTimers() {
    _holdEndTimer?.cancel();
    _holdWarnTimer?.cancel();
    _holdIntervalTimer?.cancel();
  }

  /// 撑过目标时长后还播报多久（秒）。
  ///
  /// **必须封顶。** 平板支撑做到力竭直接趴地上、忘了点「完成本组」是常见场景，
  /// 而跟练页开着 Wakelock（屏幕常亮），不封顶的话手机会一直在旁边响下去。
  /// 封顶后显示照常计时，只是不再出声——用户看屏幕仍然知道超了多久。
  static const int _overtimeAnnounceCapSec = 60;

  // ==================================================================
  // 开始
  // ==================================================================

  /// 开始一次训练：创建会话，然后进入第一组的准备状态。
  Future<void> start({
    required int programId,
    required int dayNumber,
    required String clientKey,
  }) async {
    try {
      final session = await _api.startSession(
        programId: programId,
        dayNumber: dayNumber,
        clientKey: clientKey,
        startedAt: DateTime.now(),
      );
      // 复用服务端的开始时刻，而不是本地 now()——
      // 这条会话可能是几分钟前（甚至昨天）开的
      _sessionStartedAt = session.startedAt ?? DateTime.now();
      state = _initialState(session);
      // 在这里初始化音频：用户已经明确进入跟练，可以占音频通道了。
      // 放在 App 启动时初始化会白白占着播放器，而且没有任何声音要放。
      await _audio.init();
      applySoundSettings(ref.read(soundSettingsProvider));
    } on ApiException catch (e) {
      state = null;
      throw ApiException(code: e.code, message: e.message);
    }
  }

  /// 恢复进行中的会话（断点续训）。
  ///
  /// 恢复出来的位置**从服务端的记录推导**，不依赖本地状态：
  /// 「已记录几组」就是「该做第几组」，这个关系在任何设备上都成立。
  Future<bool> restore() async {
    final session = await _api.activeSession();
    if (session == null) return false;
    _sessionStartedAt = session.startedAt ?? DateTime.now();
    state = _initialState(session);
    await _audio.init();
    applySoundSettings(ref.read(soundSettingsProvider));
    return true;
  }

  /// 这一组的草稿初值（M4-C-3）。
  ///
  /// **优先用上一组的实际值，而不是计划值。**
  /// 用户按计划 60kg 做完第一组、发现状态好加到 65kg，
  /// 第二组默认还给他 60kg 的话，他每组都要重新加一遍。
  ///
  /// 但**只在同一个动作内延续**——换动作了当然要用新动作的目标。
  double _draftWeightFor(WorkoutExercise exercise, CompletedSet? last) {
    if (last != null && last.sessionExerciseId == exercise.id && last.weight != null) {
      return last.weight!;
    }
    for (final t in exercise.sets) {
      if (t.weight != null) return t.weight!;
    }
    return 0;
  }

  int _draftRepsFor(WorkoutExercise exercise, CompletedSet? last) {
    if (last != null && last.sessionExerciseId == exercise.id && last.reps != null) {
      return last.reps!;
    }
    for (final t in exercise.sets) {
      return t.targetReps ?? t.targetRepsMin ?? t.targetRepsMax ?? 8;
    }
    return 8;
  }

  /// 用户手动调整草稿。
  ///
  /// 不校验范围——用户可以在计划外做任何重量次数（M4-D-4）。
  /// 上限由后端和录入控件的下界（不出现负数）兜住。
  void setDraftWeight(double weight) {
    final s = state;
    if (s == null) return;
    state = s.copyWith(draftWeight: weight < 0 ? 0 : weight);
  }

  void setDraftReps(int reps) {
    final s = state;
    if (s == null) return;
    state = s.copyWith(draftReps: reps < 0 ? 0 : reps);
  }

  WorkoutState _initialState(WorkoutSession session) {
    // 跳过已经做完的动作，定位到第一个还有剩余组的
    var index = 0;
    while (index < session.exercises.length &&
        session.exercises[index].isFinished) {
      index++;
    }
    if (index >= session.exercises.length) {
      return WorkoutState(
        session: session,
        phase: WorkoutPhase.sessionDone,
        exerciseIndex: session.exercises.length - 1,
        setNumber: 1,
      );
    }
    final exercise = session.exercises[index];
    final last = _lastRecordOf(exercise);
    return WorkoutState(
      session: session,
      phase: WorkoutPhase.preparing,
      exerciseIndex: index,
      setNumber: exercise.nextSetNumber,
      draftWeight: _draftWeightFor(exercise, last),
      draftReps: _draftRepsFor(exercise, last),
      // 续训时把**最后一条记录**填进 lastCompleted，
      // 这样「下一组带出上一组实际值」在恢复之后同样生效。
      //
      // 不填的话，续训后默认值会退回计划值——用户上次特意加到 65kg，
      // 重开 App 后又变回 60kg。
      lastCompleted: _lastRecordOf(exercise),
    );
  }

  /// 取某个动作的最后一条记录，转成 CompletedSet。
  CompletedSet? _lastRecordOf(WorkoutExercise exercise) {
    if (exercise.records.isEmpty) return null;
    final last = exercise.records.reduce(
        (a, b) => a.setNumber >= b.setNumber ? a : b);
    return CompletedSet(
      sessionExerciseId: exercise.id,
      setNumber: last.setNumber,
      setType: last.setType,
      weight: last.weight,
      reps: last.reps,
      rpe: last.rpe,
      completedAt: DateTime.now(),
    );
  }

  // ==================================================================
  // 转移
  // ==================================================================

  void beginSet() {
    final s = state;
    if (s == null || s.phase != WorkoutPhase.preparing) return;
    state = s.copyWith(
      phase: WorkoutPhase.exercising,
      phaseStartedAt: DateTime.now(),
      clearError: true,
    );
    // 时长类动作：安排归零长音、剩 3 秒三声、以及间隔播报。
    // 次数类动作不会有任何定时器——用户选的「组内不打扰」。
    _scheduleHoldCues();
  }

  /// 完成本组：落盘，然后决定下一步去哪。
  Future<void> completeSet({
    double? weight,
    int? reps,
    int? durationSec,
    double? rpe,
  }) async {
    final s = state;
    if (s == null || s.phase != WorkoutPhase.exercising) return;

    final exercise = s.exercise;
    final target = s.currentTarget;
    final now = DateTime.now();

    // 时长类动作**始终**记下实际时长（含撑过目标的部分）。
    //
    // 之前这里是 `durationSec: durationSec`，而界面从来不传 —— 于是
    // 平板支撑的每一组都记成一条全空的记录，AC-7-9「单独统计总时长」
    // 一直拿不到数据。
    //
    // 用 `s.setElapsed` 而不是调用方传值：本组做了多久是**状态机的事实**，
    // 不该由界面决定。
    final recordedDuration = target?.isTimed == true
        ? s.setElapsed.inSeconds
        : durationSec;

    state = s.copyWith(saving: true, clearError: true);

    try {
      await _api.recordSet(
        sessionId: s.session.id,
        sessionExerciseId: exercise.id,
        setNumber: s.setNumber,
        setType: target?.setType ?? 'WORKING',
        weight: weight,
        reps: reps,
        durationSec: recordedDuration,
        rpe: rpe,
        completedAt: now,
      );
    } on ApiException catch (e) {
      // ⚠️ **记录失败不往下走。**
      //
      // 如果失败还继续推进，用户会以为这组记上了，
      // 而实际丢失——训练结束后对不上账，且无法补救
      //（他已经不记得那一组做了多少次了）。
      //
      // 停在这里让他重试，代价小得多。
      state = s.copyWith(saving: false, error: '这一组没记上：${e.message}');
      return;
    }

    final completed = CompletedSet(
      sessionExerciseId: exercise.id,
      setNumber: s.setNumber,
      setType: target?.setType ?? 'WORKING',
      weight: weight,
      reps: reps,
      rpe: rpe,
      completedAt: now,
      // 带着它，休息结束补写 restActualSec 时才不会把时长抹掉
      durationSec: recordedDuration,
    );

    // 本地也要更新记录数，否则「下一组是第几组」算不对
    final updatedExercise = _withRecord(exercise, s.setNumber);
    final updatedSession = _replaceExercise(s.session, s.exerciseIndex, updatedExercise);

    final nextSetNumber = s.setNumber + 1;
    final hasMoreSets = nextSetNumber <= exercise.targetSets;

    if (hasMoreSets) {
      // 进入休息
      final restSec = target?.restSec ?? 90;
      _enterRest(
        s.copyWith(
          session: updatedSession,
          saving: false,
          setNumber: nextSetNumber,
          restDurationSec: restSec,
          restDeadlineAt: now.add(Duration(seconds: restSec)),
          lastCompleted: completed,
        ),
      );
    } else {
      state = s.copyWith(
        session: updatedSession,
        saving: false,
        phase: WorkoutPhase.exerciseDone,
        lastCompleted: completed,
        clearPausedAt: true,
      );
    }
  }

  /// 休息结束（倒计时归零或被跳过）。
  void endRest({bool skipped = false}) {
    final s = state;
    if (s == null) return;
    if (s.phase != WorkoutPhase.resting && s.phase != WorkoutPhase.paused) return;

    _cancelTimers();

    // 补写「这一组之后实际歇了多久」。
    // 不 await：这是锦上添花的数据，失败不该卡住训练。
    _recordActualRest(s, skipped: skipped);

    final next = s.copyWith(
      phase: WorkoutPhase.preparing,
      clearPausedAt: true,
    );
    state = next;

    // 归零长音。**跳过休息也响**——「休息结束=响一声」是一条规则，
    // 加例外只会让用户搞不清这一声代表什么。
    _audio.restFinished();
    _announceNextSet(next);
  }

  /// ±15 秒调整休息。
  void adjustRest(int seconds) {
    final s = state;
    if (s == null || s.restDeadlineAt == null) return;
    if (s.phase != WorkoutPhase.resting && s.phase != WorkoutPhase.paused) return;

    // 调整的是 deadline 本身，不是某个 remaining 变量——
    // 保证「剩余时间」永远只有一个来源
    final newDeadline = s.restDeadlineAt!.add(Duration(seconds: seconds));

    state = s.copyWith(restDeadlineAt: newDeadline);

    // 加时间后可能已经过了新的 deadline（比如剩 5 秒时 -15 秒）
    if (s.phase == WorkoutPhase.resting) {
      if (!newDeadline.isAfter(DateTime.now())) {
        endRest();
      } else {
        _enterRest(state!);
      }
    }
  }

  /// 暂停休息。
  ///
  /// **只允许在休息中暂停**（TIMER-SPEC 决策 2）：
  /// 训练中是正计时，暂停没有意义——用户不会中途暂停深蹲。
  void pause() {
    final s = state;
    if (s == null || s.phase != WorkoutPhase.resting) return;
    // 三个提示音一起取消——暂停期间不该有任何声音
    _cancelTimers();
    state = s.copyWith(phase: WorkoutPhase.paused, pausedAt: DateTime.now());
  }

  /// 继续休息。
  ///
  /// **通过平移 deadline 实现，不引入 remaining 变量**（TIMER-SPEC 决策 3）：
  /// 暂停期间 deadline 不动，所以「剩余」会跟着暂停；
  /// 继续时把 deadline 往后推「暂停了多久」。
  void resume() {
    final s = state;
    if (s == null || s.phase != WorkoutPhase.paused || s.pausedAt == null) return;

    final pausedFor = DateTime.now().difference(s.pausedAt!);
    final newDeadline = s.restDeadlineAt!.add(pausedFor);

    state = s.copyWith(
      phase: WorkoutPhase.resting,
      restDeadlineAt: newDeadline,
      clearPausedAt: true,
    );
    _enterRest(state!);
  }

  /// 进入下一个动作。
  void nextExercise() {
    final s = state;
    if (s == null || s.phase != WorkoutPhase.exerciseDone) return;

    if (s.isLastExercise) {
      state = s.copyWith(phase: WorkoutPhase.sessionDone);
      _audio.announceSessionDone();
      return;
    }
    final nextIndex = s.exerciseIndex + 1;
    final next = s.session.exercises[nextIndex];
    final last = _lastRecordOf(next);
    final nextState = s.copyWith(
      phase: WorkoutPhase.preparing,
      exerciseIndex: nextIndex,
      setNumber: next.nextSetNumber,
      // 换动作了，草稿要按新动作的目标重算——
      // 沿用上一个动作的重量（比如深蹲的 100kg）会很危险
      draftWeight: _draftWeightFor(next, last),
      draftReps: _draftRepsFor(next, last),
      clearPausedAt: true,
    );
    state = nextState;

    // 换动作时报动作名，不报组数——用户连器械都还没走到
    _audio.announceNextExercise(next.exerciseName, next.targetSets);
  }

  /// 跳过当前动作（M4-D-1）。
  Future<void> skipExercise() async {
    final s = state;
    if (s == null) return;

    final exercise = s.exercise;
    try {
      await _api.skipExercise(
        sessionId: s.session.id,
        sessionExerciseId: exercise.id,
      );
    } on ApiException catch (e) {
      state = s.copyWith(error: '跳过失败：${e.message}');
      return;
    }

    final updated = _copyExerciseWithStatus(exercise, 'SKIPPED');
    final updatedSession = _replaceExercise(s.session, s.exerciseIndex, updated);

    // 跳过之后直接进入「下一个动作」的过渡，让用户确认
    state = s.copyWith(
      session: updatedSession,
      phase: WorkoutPhase.exerciseDone,
      clearError: true,
      clearPausedAt: true,
    );
  }

  /// 结束训练（需要二次确认，由界面负责）。
  Future<void> finishSession() async {
    final s = state;
    if (s == null) return;

    _cancelTimers();

    // ⚠️ 这个值**通常会被服务端忽略**。
    //
    // 训练时长由服务端从组记录推算（最后一组的完成时刻 − 开始时刻），
    // 因为「点结束的时刻」不等于「练完的时刻」——用户可能把手机
    // 揣兜里两小时才想起来点。
    //
    // 这里上报的值只在**一条组记录都没有**时作兜底
    // （比如点开跟练页发现不对，立刻退出）。
    final elapsed = _sessionStartedAt == null
        ? 0
        : DateTime.now().difference(_sessionStartedAt!).inSeconds;

    try {
      await _api.finishSession(sessionId: s.session.id, durationSec: elapsed);
    } on ApiException catch (e) {
      state = s.copyWith(error: '结束失败：${e.message}');
      return;
    }
    state = s.copyWith(phase: WorkoutPhase.sessionDone);
    _audio.announceSessionDone();
  }

  /// 清空状态（离开跟练界面）。
  void reset() {
    _cancelTimers();
    state = null;
  }

  // ==================================================================
  // 计时器
  // ==================================================================

  /// 安排一个**到点才触发**的定时器。
  ///
  /// 不是每秒轮询比对——那样既费电又不准。
  /// 定时器只是「叫醒」用，真正的判断永远是 `deadline - now`。
  ///
  /// ⚠️ App 切后台时 Dart 定时器可能被系统挂起。
  /// 所以界面回到前台时会调 [`syncRestTimer`] 重新对表——
  /// 这才是「存绝对时间戳」真正兑现价值的地方（AC-4-1）。
  void _enterRest(WorkoutState s) {
    // ⚠️ **必须在这里把 phase 改成 resting。**
    //
    // 原来只写了 `state = s`，而调用方传进来的 s 还是 exercising 的拷贝——
    // 结果完成一组后组号推进了、倒计时也安排了，**但界面还停在录入态**，
    // 用户以为没记上，会再点一次「完成本组」。
    //
    // 这个 bug 只有真机跑一遍才看得见：单元层面 `restDeadlineAt` 是对的，
    // `setNumber` 也是对的，只有「当前该显示哪一屏」错了。
    // ⚠️ 这里必须重置 phaseStartedAt。
    //
    // 它原本只在 beginSet 里写、从不更新，于是 `_recordActualRest` 里那句
    // `now − phaseStartedAt`（本意是「这一组之后歇了多久」）
    // 算出来的是 **组内用时 + 休息时长**——整组的时长被多算进了休息。
    //
    // 真机数据坐实过：计划休息 180 秒，实际记了 196 秒，
    // 而那组前后正好隔了 16 秒。计划 150 记成 155 的也是同一回事。
    //
    // 重置之后这个字段的语义变成「**当前阶段**的起点」，
    // 对休息计时和组内计时（setElapsed 用它）都成立。
    state = s.copyWith(
      phase: WorkoutPhase.resting,
      phaseStartedAt: DateTime.now(),
      clearPausedAt: true,
    );
    _scheduleRestCues();
  }

  // ==================================================================
  // 组内计时（时长类动作）
  // ==================================================================

  /// 安排时长类动作**组内**的提示音。
  ///
  /// | 时刻 | 声音 | 作用 |
  /// |---|---|---|
  /// | 每 `announceIntervalSec` | 一声短音（+ 语音） | 知道还剩多久 |
  /// | 剩余 3 秒 | 三声短音 | 收尾 |
  /// | 归零 | 一声长音 | 目标达成，转入超时 |
  /// | 超时期间每 `announceIntervalSec` | 一声短音（+ 语音） | 知道超了多久，**最多 60 秒** |
  ///
  /// 和 `_scheduleRestCues` 的关键差别：**这里的定时器一个都不改 state**。
  /// 到点是时间事实，`holdRemaining` / `isHoldOvertime` 现算就得到了，
  /// 所以定时器被系统吞掉最多是少响一声，显示不会错。
  void _scheduleHoldCues() {
    _cancelHoldTimers();

    final s = state;
    if (s == null || s.phase != WorkoutPhase.exercising) return;

    final target = s.holdTargetSec;
    final start = s.phaseStartedAt;
    if (target == null || start == null) return;

    final deadline = start.add(Duration(seconds: target));
    final remaining = deadline.difference(DateTime.now());

    // ---------- 归零：一声长音 ----------
    if (remaining > Duration.zero) {
      _holdEndTimer = Timer(remaining, () {
        if (state?.phase == WorkoutPhase.exercising) _audio.restFinished();
      });
    }

    // ---------- 剩余 3 秒：三声短音 ----------
    const warnLead = Duration(seconds: 3);
    if (remaining > warnLead) {
      _holdWarnTimer = Timer(remaining - warnLead, () {
        if (state?.phase == WorkoutPhase.exercising) _audio.warnCountdown();
      });
    }

    // ---------- 间隔播报 ----------
    final interval = s.currentTarget?.announceIntervalSec ?? 0;
    if (interval <= 0) return;

    _holdIntervalTimer =
        Timer.periodic(Duration(seconds: interval), (timer) {
      // 回调里再验状态：用户可能已经点了「完成本组」或「跳过」
      final cur = state;
      if (cur == null || cur.phase != WorkoutPhase.exercising) {
        timer.cancel();
        return;
      }

      final elapsed = DateTime.now().difference(start);
      final left = Duration(seconds: target) - elapsed;

      if (left > warnLead) {
        // 倒计时期间：「还剩 N 秒」
        _audio.holdTick();
        _audio.announceHoldRemaining(left.inSeconds);
      } else if (left.isNegative) {
        // 超时期间：「已超 N 秒」，封顶后不再出声
        final over = -left.inSeconds;
        if (over > _overtimeAnnounceCapSec) {
          timer.cancel();
          return;
        }
        _audio.holdTick();
        _audio.announceHoldOvertime(over);
      }
      // left 落在 [0, 3] 这一段交给「剩 3 秒三声」和「归零长音」，
      // 这里不响——否则三个声音会挤在一起变成一团噪音
    });
  }

  /// 回到前台时重新对表（组内计时）。
  ///
  /// 和 [syncRestTimer] 的**关键差别：不补播错过的提示音**。
  /// 切后台 5 分钟回来不该连响一串。而因为显示是现算的，
  /// 什么都不做显示也是对的——只有「间隔播报」这条周期性的需要重新起。
  void syncHoldCues() {
    final s = state;
    if (s == null || s.phase != WorkoutPhase.exercising) return;
    _scheduleHoldCues();
  }

  /// 安排休息期间的**三个**提示音。
  ///
  /// | 时刻 | 声音 | 作用 |
  /// |---|---|---|
  /// | 过半 | 一声短低音 | 提醒「可以开始准备了」 |
  /// | 剩余 3 秒 | 三声短音 | 站到器械前 |
  /// | 归零 | 一声长高音 + 语音 | 立刻开始 |
  ///
  /// 三个都从 `restDeadlineAt` **现算**，不存「还剩几秒」。
  /// 于是 ±15 秒调整、暂停后继续，只要重新调一次本方法就自动对表——
  /// 不需要为每种操作单独算一次时间。
  void _scheduleRestCues() {
    _cancelTimers();

    final s = state;
    final deadline = s?.restDeadlineAt;
    if (s == null || deadline == null) return;

    final remaining = deadline.difference(DateTime.now());

    // ---- 归零 ----
    _restTimer = Timer(
      remaining.isNegative ? Duration.zero : remaining,
      () {
        if (state?.phase == WorkoutPhase.resting) endRest();
      },
    );

    // ---- 剩余 3 秒 ----
    //
    // 只在真的还够 3 秒时才安排。剩余不到 3 秒时（比如剩 5 秒时按了 −15 秒），
    // 三声短音会和归零长音挤在一起，听起来就是一团噪音。
    const warnLead = Duration(seconds: 3);
    if (remaining > warnLead) {
      _warnTimer = Timer(remaining - warnLead, () {
        if (state?.phase == WorkoutPhase.resting) _audio.warnCountdown();
      });
    }

    // ---- 过半 ----
    //
    // 休息 30 秒以内没有「过半」可言，响了只是吵。
    // 阈值取 45 秒而不是 30 秒：休息 40 秒时过半只剩 20 秒，
    // 用户刚坐下就得起来，这一声没有信息量。
    const minRestForHalfway = 45;
    final half = Duration(seconds: s.restDurationSec ~/ 2);
    if (s.restDurationSec >= minRestForHalfway && remaining > half) {
      _halfwayTimer = Timer(remaining - half, () {
        if (state?.phase == WorkoutPhase.resting) _audio.halfwayHint();
      });
    }
  }

  /// 播报下一组的目标（M4-B-5）。
  ///
  /// 归零时用户通常**没在看屏幕**——正在擦汗、走回器械、调座椅。
  /// 语音是这时唯一有效的信息通道。
  void _announceNextSet(WorkoutState s) {
    if (s.phase != WorkoutPhase.preparing) return;

    // ⚠️ 时长类动作**不能报次数**。
    //
    // 它没有 targetReps（V14 把秒数从次数字段搬走了），
    // 而 draftReps 在 DURATION 下是个从没被用过的默认值——
    // 照原样播出去就是「平板支撑，第 2 组，共 3 组，8 次」，
    // 而实际要做的是撑 30 秒。
    final target = s.currentTarget;
    final timed = target?.isTimed == true;

    _audio.announceNextSet(
      exerciseName: s.exercise.exerciseName,
      setNumber: s.setNumber,
      totalSets: s.exercise.targetSets,
      weightLabel: timed ? target!.durationLabel : _spokenWeight(s),
      repsLabel: timed ? null : s.draftReps?.toString(),
    );
  }

  /// 播报用的重量文本。
  ///
  /// ⚠️ 和界面上的 `SetTarget.weightLabel` 是**两种介质，不是两套口径**：
  /// 屏幕写 `60.0 kg`，嘴里要念「60 公斤」——TTS 念小数点后的 0 很别扭。
  ///
  /// 但「没有重量 ≠ 自重」这条判断必须一致，所以 `REPS_ONLY` 的判据
  /// 和 `weightLabel` 用的是同一个。
  String? _spokenWeight(WorkoutState s) {
    final metric = s.exercise.metricType;
    // 时长类动作报秒数，不报重量
    if (metric == 'DURATION') return null;

    final w = s.draftWeight;
    if (w == null || w <= 0) {
      return metric == 'REPS_ONLY' ? '自重' : null;
    }
    // 整数就不念小数点：60 而不是 60.0
    final text = w.truncateToDouble() == w
        ? w.toStringAsFixed(0)
        : w.toStringAsFixed(1);
    return '$text 公斤';
  }

  /// 回到前台时重新对表。
  ///
  /// 如果后台期间 deadline 已经过了，立刻结束休息——
  /// 而不是从「还剩 40 秒」继续倒数。
  void syncRestTimer() {
    final s = state;
    if (s == null || s.phase != WorkoutPhase.resting) return;

    if (s.isRestOver) {
      endRest();
    } else {
      _enterRest(s);
    }
  }

  // ==================================================================
  // 辅助
  // ==================================================================

  /// 补写实际休息时长。
  ///
  /// 复用 PUT 的幂等性：同一个 `(动作, 组号)` 再发一次是**覆盖**，
  /// 不会产生第二条记录。这是 3.3 那个唯一索引的直接回报。
  Future<void> _recordActualRest(WorkoutState s, {required bool skipped}) async {
    final last = s.lastCompleted;
    if (last == null || s.phaseStartedAt == null) return;

    final actual = DateTime.now().difference(s.phaseStartedAt!).inSeconds;

    try {
      await _api.recordSet(
        sessionId: s.session.id,
        sessionExerciseId: last.sessionExerciseId,
        setNumber: last.setNumber,
        setType: last.setType,
        weight: last.weight,
        reps: last.reps,
        rpe: last.rpe,
        // ⚠️ 必须带上，否则这次 PUT 会把刚记好的时长覆盖成 null。
        // 服务端是全量覆盖语义，不是 PATCH。
        durationSec: last.durationSec,
        restActualSec: actual,
        completedAt: last.completedAt,
      );
    } catch (_) {
      // 忽略：实际休息时长是「锦上添花」的数据（M4-C-7，Should 级），
      // 记不上不该影响训练本身
    }
  }

  WorkoutExercise _withRecord(WorkoutExercise e, int setNumber) {
    return _copyExercise(e, records: [
      ...e.records,
      SetRecordData(
        setNumber: setNumber,
        setType: 'WORKING',
        weight: null,
        reps: null,
        rpe: null,
      ),
    ]);
  }

  WorkoutExercise _copyExerciseWithStatus(WorkoutExercise e, String status) =>
      _copyExercise(e, status: status);

  WorkoutExercise _copyExercise(
    WorkoutExercise e, {
    List<SetRecordData>? records,
    String? status,
  }) {
    return WorkoutExercise(
      id: e.id,
      exerciseName: e.exerciseName,
      metricType: e.metricType,
      orderIndex: e.orderIndex,
      supersetGroup: e.supersetGroup,
      orderInGroup: e.orderInGroup,
      targetSets: e.targetSets,
      status: status ?? e.status,
      sets: e.sets,
      records: records ?? e.records,
    );
  }

  WorkoutSession _replaceExercise(
      WorkoutSession s, int index, WorkoutExercise e) {
    final list = [...s.exercises];
    list[index] = e;
    return WorkoutSession(
      id: s.id,
      dayName: s.dayName,
      weekNumber: s.weekNumber,
      deload: s.deload,
      status: s.status,
      startedAt: s.startedAt,
      exercises: list,
    );
  }
}
