// 训练总结的模型。对应后端 `GET /sessions/{id}/summary`。
//
// 所有数值都是后端算好的——**客户端不做任何口径计算**。
// 容量公式（含自重、排除热身）只应该有一个实现，在后端。

class SessionSummary {
  const SessionSummary({
    required this.id,
    required this.dayName,
    required this.durationSec,
    required this.volume,
    required this.workingSets,
    required this.warmupSets,
    required this.totalReps,
    required this.totalDurationSec,
    required this.exerciseCount,
    required this.completedExercises,
    required this.skippedExercises,
    required this.plannedSets,
    required this.exercises,
    required this.personalRecords,
    required this.comparison,
  });

  final int id;
  final String? dayName;
  final int? durationSec;

  /// 训练容量（kg）：Σ(重量 × 次数)，仅正式组。
  /// 自重动作按「体重 × bw_factor × 次数」算，时长类动作不计入。
  final double volume;

  /// 正式组数（不含热身）
  final int workingSets;
  final int warmupSets;
  final int totalReps;

  /// 时长类动作的合计秒数（平板支撑等）
  final int totalDurationSec;

  final int exerciseCount;
  final int completedExercises;
  final int skippedExercises;
  final int plannedSets;

  /// 逐动作明细。聚合数字回答「练了多少」，这里回答「练的是什么」。
  final List<ExerciseSummary> exercises;

  final List<PersonalRecord> personalRecords;

  /// 与上次同训练日的对比。null = 第一次练这个训练日。
  final SessionComparison? comparison;

  String get durationLabel {
    final s = durationSec ?? 0;
    if (s < 60) return '${s}s';
    final h = s ~/ 3600;
    final m = (s % 3600) ~/ 60;
    return h > 0 ? '$h 小时 $m 分' : '$m 分钟';
  }

  factory SessionSummary.fromJson(Map<String, dynamic> json) {
    return SessionSummary(
      id: json['id'] as int,
      dayName: json['dayName'] as String?,
      durationSec: json['durationSec'] as int?,
      volume: (json['volume'] as num?)?.toDouble() ?? 0,
      workingSets: json['workingSets'] as int? ?? 0,
      warmupSets: json['warmupSets'] as int? ?? 0,
      totalReps: json['totalReps'] as int? ?? 0,
      totalDurationSec: json['totalDurationSec'] as int? ?? 0,
      exerciseCount: json['exerciseCount'] as int? ?? 0,
      completedExercises: json['completedExercises'] as int? ?? 0,
      skippedExercises: json['skippedExercises'] as int? ?? 0,
      plannedSets: json['plannedSets'] as int? ?? 0,
      exercises: ((json['exercises'] as List?) ?? const [])
          .map((e) => ExerciseSummary.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
      personalRecords: ((json['personalRecords'] as List?) ?? const [])
          .map((p) => PersonalRecord.fromJson((p as Map).cast<String, dynamic>()))
          .toList(),
      comparison: json['comparison'] == null
          ? null
          : SessionComparison.fromJson(
              (json['comparison'] as Map).cast<String, dynamic>()),
    );
  }
}

/// 一个动作刷新的个人纪录。
class PersonalRecord {
  const PersonalRecord({
    required this.exerciseName,
    required this.e1rm,
    required this.previousBest,
    required this.improvement,
  });

  final String exerciseName;

  /// 本次最佳组的估算 1RM（Epley：w × (1 + r/30)）
  final double e1rm;

  /// 历史最好成绩。null = 第一次做这个动作。
  final double? previousBest;

  /// 提升幅度（kg）。第一次做时为 null。
  final double? improvement;

  bool get isFirstTime => previousBest == null;

  factory PersonalRecord.fromJson(Map<String, dynamic> json) {
    return PersonalRecord(
      exerciseName: json['exerciseName'] as String? ?? '未知动作',
      e1rm: (json['e1rm'] as num?)?.toDouble() ?? 0,
      previousBest: (json['previousBest'] as num?)?.toDouble(),
      improvement: (json['improvement'] as num?)?.toDouble(),
    );
  }
}

/// 与上一次同训练日的对比。
class SessionComparison {
  const SessionComparison({
    required this.volumeDelta,
    required this.workingSetsDelta,
    required this.durationSecDelta,
    required this.exercises,
  });

  final double? volumeDelta;
  final int? workingSetsDelta;
  final int? durationSecDelta;
  final List<ExerciseDelta> exercises;

  factory SessionComparison.fromJson(Map<String, dynamic> json) {
    return SessionComparison(
      volumeDelta: (json['volumeDelta'] as num?)?.toDouble(),
      workingSetsDelta: json['workingSetsDelta'] as int?,
      durationSecDelta: json['durationSecDelta'] as int?,
      exercises: ((json['exercises'] as List?) ?? const [])
          .map((e) => ExerciseDelta.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
    );
  }
}

/// 单个动作的两次对比。
class ExerciseDelta {
  const ExerciseDelta({
    required this.exerciseName,
    required this.currentBestWeight,
    required this.previousBestWeight,
    required this.weightDelta,
  });

  final String exerciseName;
  final double? currentBestWeight;
  final double? previousBestWeight;
  final double? weightDelta;

  factory ExerciseDelta.fromJson(Map<String, dynamic> json) {
    return ExerciseDelta(
      exerciseName: json['exerciseName'] as String? ?? '未知动作',
      currentBestWeight: (json['currentBestWeight'] as num?)?.toDouble(),
      previousBestWeight: (json['previousBestWeight'] as num?)?.toDouble(),
      weightDelta: (json['weightDelta'] as num?)?.toDouble(),
    );
  }
}


// ======================================================================
// 逐动作明细
// ======================================================================

class ExerciseSummary {
  const ExerciseSummary({
    required this.exerciseName,
    required this.metricType,
    required this.status,
    required this.statusLabel,
    required this.plannedSets,
    required this.recordedSets,
    required this.volume,
    required this.sets,
  });

  final String exerciseName;
  final String? metricType;
  final String status;
  final String statusLabel;
  final int plannedSets;
  final int recordedSets;
  final double volume;
  final List<SetLine> sets;

  bool get isSkipped => status == 'SKIPPED';

  /// 实际做过的组（不等于计划组数——用户可能加组或少做）
  List<SetLine> get doneSets => sets.where((s) => s.done).toList();

  /// 紧凑记法：`60×8 · 60×8` 或折叠成 `20×10 ×3`。
  ///
  /// **连续的相同组折叠，不同的展开**——一套规则同时覆盖两种情况：
  /// <pre>
  ///   20×10, 20×10, 20×10   →  20×10 ×3
  ///   60×8,  60×8,  65×6    →  60×8 ×2 · 65×6
  ///   60×8,  65×6,  60×8    →  60×8 · 65×6 · 60×8   （不连续，不折叠）
  /// </pre>
  ///
  /// 不折叠的话，一个 4 组的动作要占满一行还写不下；
  /// 无条件折叠（只看第一组）又会把递减组写成假的。
  String get compactLabel {
    final done = doneSets;
    if (done.isEmpty) return isSkipped ? '已跳过' : '未做';

    final parts = <String>[];
    String? prev;
    var run = 0;

    void flush() {
      final label = prev;
      if (label == null) return;
      // 连续相同就折叠成 `20×10 ×3`，否则原样
      parts.add(run > 1 ? '$label ×$run' : label);
    }

    for (final s in done) {
      final label = s.actualLabel(metricType);
      if (label == prev) {
        run++;
      } else {
        flush();
        prev = label;
        run = 1;
      }
    }
    flush();

    return parts.join(' · ');
  }

  factory ExerciseSummary.fromJson(Map<String, dynamic> json) {
    return ExerciseSummary(
      exerciseName: json['exerciseName'] as String? ?? '未知动作',
      metricType: json['metricType'] as String?,
      status: json['status'] as String? ?? 'PENDING',
      statusLabel: json['statusLabel'] as String? ?? '',
      plannedSets: json['plannedSets'] as int? ?? 0,
      recordedSets: json['recordedSets'] as int? ?? 0,
      volume: (json['volume'] as num?)?.toDouble() ?? 0,
      sets: ((json['sets'] as List?) ?? const [])
          .map((s) => SetLine.fromJson((s as Map).cast<String, dynamic>()))
          .toList(),
    );
  }
}

/// 一组的计划与实际（按组号对齐，两边都可能为空）。
class SetLine {
  const SetLine({
    required this.setNumber,
    required this.setTypeLabel,
    required this.targetWeight,
    required this.targetReps,
    required this.targetRepsMin,
    required this.targetRepsMax,
    required this.targetDurationSec,
    required this.done,
    required this.actualWeight,
    required this.actualReps,
    required this.actualDurationSec,
  });

  final int setNumber;
  final String? setTypeLabel;

  final double? targetWeight;
  final int? targetReps;
  final int? targetRepsMin;
  final int? targetRepsMax;

  /// 计划的目标持续时长（秒）。null = 这一组不是按时间做的
  final int? targetDurationSec;

  final bool done;
  final double? actualWeight;
  final int? actualReps;
  final int? actualDurationSec;

  /// 实际做了多少，紧凑写法：`60×8`
  String actualLabel(String? metricType) {
    if (actualDurationSec != null) return '${actualDurationSec}s';
    final reps = actualReps ?? 0;
    if (metricType == 'REPS_ONLY') return '$reps 次';
    if (actualWeight == null) return '$reps 次';
    return '${_trim(actualWeight!)}×$reps';
  }

  /// 计划是多少，紧凑写法：`60×6-8`
  ///
  /// 时长类动作走另一条路：V14 把秒数从次数字段搬走之后，
  /// 平板支撑的 targetReps 是 null，原样拼会得到「—」。
  String get targetLabel {
    final dur = targetDurationSec;
    if (dur != null && dur > 0) return '${dur}s';
    final reps = targetReps != null
        ? '$targetReps'
        : (targetRepsMin != null && targetRepsMax != null
            ? '$targetRepsMin-$targetRepsMax'
            : '—');
    if (targetWeight == null) return reps;
    return '${_trim(targetWeight!)}×$reps';
  }

  static String _trim(double v) =>
      v.toStringAsFixed(v.truncateToDouble() == v ? 0 : 1);

  factory SetLine.fromJson(Map<String, dynamic> json) {
    return SetLine(
      setNumber: json['setNumber'] as int,
      setTypeLabel: json['setTypeLabel'] as String?,
      targetWeight: (json['targetWeight'] as num?)?.toDouble(),
      targetReps: json['targetReps'] as int?,
      targetRepsMin: json['targetRepsMin'] as int?,
      targetRepsMax: json['targetRepsMax'] as int?,
      targetDurationSec: json['targetDurationSec'] as int?,
      done: json['done'] as bool? ?? false,
      actualWeight: (json['actualWeight'] as num?)?.toDouble(),
      actualReps: json['actualReps'] as int?,
      actualDurationSec: json['actualDurationSec'] as int?,
    );
  }
}
