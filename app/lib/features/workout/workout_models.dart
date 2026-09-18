// 跟练相关的数据模型。
//
// 全部来自后端的**会话快照**（`GET /sessions/{id}`），不是来自计划——
// 用户改计划不影响正在进行/已完成的训练。
//
// 手写解析而不是用 codegen（json_serializable）：
// 字段就这些，手写大约一百行，而 codegen 要引入 build_runner
// 和一整套生成产物。**字段多到手动维护会出错时才值得上 codegen。**

// 一次训练。
class WorkoutSession {
  const WorkoutSession({
    required this.id,
    required this.dayName,
    required this.weekNumber,
    required this.deload,
    required this.status,
    required this.startedAt,
    required this.exercises,
  });

  final int id;
  final String? dayName;
  final int? weekNumber;
  final bool deload;
  final String status;

  /// 服务端记录的开始时刻。
  ///
  /// **续训时必须用它，不能用 `DateTime.now()`** ——
  /// 否则重开 App 接着练，训练时长会从「重开那一刻」重新算，
  /// 前面练的几十分钟全部丢掉。
  final DateTime? startedAt;

  final List<WorkoutExercise> exercises;

  bool get isInProgress => status == 'IN_PROGRESS';

  factory WorkoutSession.fromJson(Map<String, dynamic> json) {
    return WorkoutSession(
      id: json['id'] as int,
      dayName: json['dayName'] as String?,
      weekNumber: json['weekNumber'] as int?,
      deload: json['deload'] as bool? ?? false,
      status: json['status'] as String? ?? 'IN_PROGRESS',
      startedAt: DateTime.tryParse(json['startedAt'] as String? ?? ''),
      exercises: ((json['exercises'] as List?) ?? const [])
          .map((e) => WorkoutExercise.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
    );
  }
}

// 快照里的一个动作。
class WorkoutExercise {
  const WorkoutExercise({
    required this.id,
    required this.exerciseName,
    required this.metricType,
    required this.orderIndex,
    required this.supersetGroup,
    required this.orderInGroup,
    required this.targetSets,
    required this.status,
    required this.sets,
    required this.records,
  });

  /// 快照行 id —— 记录组时用它，不是 exerciseId
  final int id;
  final String exerciseName;

  /// WEIGHT_REPS / REPS_ONLY / DURATION / DISTANCE_DURATION
  ///
  /// 决定跟练界面显示哪些录入控件：
  /// 负重动作要填重量，徒手动作不填，平板支撑填时长。
  final String? metricType;

  final int orderIndex;

  /// 超级组编号。null = 单独动作。
  ///
  /// ⚠️ 本步骤的界面**还没有实现超级组**——先做顺序执行版跑通。
  /// 字段先解析出来，是因为它已经在数据里了，
  /// 而且顺序执行的逻辑只要看到非 null 就应该知道自己还不支持。
  final int? supersetGroup;
  final int? orderInGroup;

  final int targetSets;
  final String status;

  /// 计划：每一组应该做多少
  final List<SetTarget> sets;

  /// 实际：每一组实际做了多少（断点续训时从这里恢复）
  final List<SetRecordData> records;

  bool get isSuperset => supersetGroup != null;

  /// 已记录的正式组数（不含热身）
  int get completedSets =>
      records.where((r) => r.setType != 'WARMUP').length;

  /// 下一次该做第几组。
  ///
  /// 用「已记录组数 + 1」而不是「最大组号 + 1」：
  /// 用户删掉第 2 组后，记录里是 [1, 3]，该做的仍然是第 2 组。
  int get nextSetNumber => completedSets + 1;

  bool get isFinished => status == 'COMPLETED' || status == 'SKIPPED';

  factory WorkoutExercise.fromJson(Map<String, dynamic> json) {
    return WorkoutExercise(
      id: json['id'] as int,
      exerciseName: json['exerciseName'] as String? ?? '未知动作',
      metricType: json['metricType'] as String?,
      orderIndex: json['orderIndex'] as int? ?? 0,
      supersetGroup: json['supersetGroup'] as int?,
      orderInGroup: json['orderInGroup'] as int?,
      targetSets: json['targetSets'] as int? ?? 0,
      status: json['status'] as String? ?? 'PENDING',
      sets: ((json['sets'] as List?) ?? const [])
          .map((s) => SetTarget.fromJson((s as Map).cast<String, dynamic>()))
          .toList(),
      records: ((json['records'] as List?) ?? const [])
          .map((s) => SetRecordData.fromJson((s as Map).cast<String, dynamic>()))
          .toList(),
    );
  }
}

// 一组的目标（后端已展开，客户端不做任何计算）。
class SetTarget {
  const SetTarget({
    required this.setNumber,
    required this.setType,
    required this.targetReps,
    required this.targetRepsMin,
    required this.targetRepsMax,
    required this.weight,
    required this.pct,
    required this.rpe,
    required this.restSec,
    this.targetDurationSec,
    this.announceIntervalSec = 0,
  });

  final int setNumber;
  final String? setType;
  final int? targetReps;
  final int? targetRepsMin;
  final int? targetRepsMax;
  final double? weight;
  final double? pct;
  final double? rpe;
  final int restSec;

  /// 目标持续时长（秒）。null = 这一组不是按时间做的。
  ///
  /// ⚠️ 在 V14 之前，平板支撑的秒数是**塞在 [targetRepsMin] 里**的。
  /// 现在它是独立字段，没值的动作就是 null。
  final int? targetDurationSec;

  /// 倒计时期间的播报间隔（秒）。0 = 不间隔播报。
  final int announceIntervalSec;

  /// 这一组是否是「按时长做」的（平板支撑这类等长收缩）。
  bool get isTimed => targetDurationSec != null && targetDurationSec! > 0;

  /// 目标次数的展示文本
  String get repsLabel {
    if (targetReps != null) return '$targetReps';
    if (targetRepsMin != null && targetRepsMax != null) {
      return '$targetRepsMin-$targetRepsMax';
    }
    return '—';
  }

  /// 目标时长的展示文本：`30 秒`
  String get durationLabel {
    final sec = targetDurationSec;
    if (sec == null || sec <= 0) return '—';
    if (sec < 60) return '$sec 秒';
    final m = sec ~/ 60;
    final s = sec % 60;
    return s == 0 ? '$m 分' : '$m 分 $s 秒';
  }

  /// 播报间隔的展示文本
  String get announceLabel =>
      announceIntervalSec <= 0 ? '关闭' : '每 $announceIntervalSec 秒';

  /// 目标重量的展示文本。
  ///
  /// ⚠️ **没有重量不等于自重**。两种含义必须分开：
  /// - 动作本身不负重（引体、俯卧撑）→ `REPS_ONLY` → 自重
  /// - 计划没填目标重量（模板创建的计划）→ `WEIGHT_REPS` → 重量自定
  ///
  /// 混为一谈会让用户以为「杠铃卧推不用加片」——照着练是危险的。
  String weightLabel(String? metricType) {
    if (weight != null) return '$weight kg';
    if (pct != null) return '$pct% 1RM';
    if (rpe != null) return 'RPE $rpe';
    return metricType == 'REPS_ONLY' ? '自重' : '重量自定';
  }

  factory SetTarget.fromJson(Map<String, dynamic> json) {
    final target = (json['target'] as Map?)?.cast<String, dynamic>();
    return SetTarget(
      setNumber: json['setNumber'] as int,
      setType: json['setType'] as String?,
      targetReps: json['targetReps'] as int?,
      targetRepsMin: json['targetRepsMin'] as int?,
      targetRepsMax: json['targetRepsMax'] as int?,
      weight: (target?['weight'] as num?)?.toDouble(),
      pct: (target?['pct'] as num?)?.toDouble(),
      rpe: (target?['rpe'] as num?)?.toDouble(),
      restSec: json['restSec'] as int? ?? 90,
      // 后端展开时已解析，时长类动作才有值
      targetDurationSec: json['targetDurationSec'] as int?,
      announceIntervalSec: json['announceIntervalSec'] as int? ?? 0,
    );
  }
}

// 一组实际记录。
class SetRecordData {
  const SetRecordData({
    required this.setNumber,
    required this.setType,
    required this.weight,
    required this.reps,
    required this.rpe,
  });

  final int setNumber;
  final String setType;
  final double? weight;
  final int? reps;
  final double? rpe;

  factory SetRecordData.fromJson(Map<String, dynamic> json) {
    return SetRecordData(
      setNumber: json['setNumber'] as int,
      setType: json['setType'] as String? ?? 'WORKING',
      weight: (json['weight'] as num?)?.toDouble(),
      reps: json['reps'] as int?,
      rpe: (json['rpe'] as num?)?.toDouble(),
    );
  }
}
