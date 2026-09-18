// 统计与图表的模型。对应后端 `/api/v1/stats/*`。
//
// ⚠️ **所有数值都是后端算好的，客户端不做任何口径计算。**
// 容量公式（含自重、排除热身）、e1RM、移动平均、符合率的 clamp——
// 只应该有一份实现，在后端。客户端自己算一遍的话，
// 训练总结和趋势图会给出不同的数字，而用户会开始怀疑整个 App。
//
// ⚠️ **可空字段可能整个不存在**（不只是值为 null）。
// 后端配了 `spring.jackson.default-property-inclusion: non_null`，
// null 字段会被整个丢掉。所以一律用 `json['x'] as T?`。

/// 一周的统计。
class WeekBucket {
  const WeekBucket({
    required this.weekStart,
    required this.currentWeek,
    required this.volume,
    required this.workingSets,
    required this.sessionCount,
    required this.complianceRate,
    required this.muscleSets,
  });

  final DateTime weekStart;

  /// 是不是当前这一周。`METRICS 5.5` 要求半透明显示——
  /// 用户需要一眼看出「这周才周三，柱子矮是正常的」
  final bool currentWeek;

  /// 训练容量 kg：Σ(重量×次数)，仅正式组
  final double volume;

  /// 正式组数（不含热身）
  final int workingSets;

  final int sessionCount;

  /// 符合率 0–100。**该周没有计划内的组时为 null**（不是 0）
  final double? complianceRate;

  /// 固定 6 项、含 0——缺项会让颜色映射错位
  final List<MuscleSets> muscleSets;

  factory WeekBucket.fromJson(Map<String, dynamic> json) {
    return WeekBucket(
      weekStart: DateTime.parse(json['weekStart'] as String),
      currentWeek: json['currentWeek'] as bool? ?? false,
      volume: (json['volume'] as num?)?.toDouble() ?? 0,
      workingSets: json['workingSets'] as int? ?? 0,
      sessionCount: json['sessionCount'] as int? ?? 0,
      complianceRate: (json['complianceRate'] as num?)?.toDouble(),
      muscleSets: ((json['muscleSets'] as List?) ?? const [])
          .map((e) => MuscleSets.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
    );
  }

  /// 有组数的肌群（过滤掉 0）
  List<MuscleSets> get trainedMuscles =>
      muscleSets.where((m) => m.sets > 0).toList();
}

/// 某个肌群在一周里的组数。中文名由后端给——避免客户端硬编码第二份词表。
class MuscleSets {
  const MuscleSets({required this.muscle, required this.label, required this.sets});

  /// 枚举名，如 `CHEST`
  final String muscle;

  /// 显示名，如「胸」
  final String label;

  final int sets;

  factory MuscleSets.fromJson(Map<String, dynamic> json) {
    return MuscleSets(
      muscle: json['muscle'] as String? ?? '',
      label: json['label'] as String? ?? '',
      sets: json['sets'] as int? ?? 0,
    );
  }
}

/// 周序列响应。
class WeeklyStats {
  const WeeklyStats({
    required this.from,
    required this.to,
    required this.currentStreak,
    required this.weeks,
  });

  final DateTime from;
  final DateTime to;

  /// 连续训练周数。**由服务端算**——「连续几周」是一条口径规则
  /// （当前周怎么算、缺一周算不算断都有定义），不是把数组长度一数就完事的。
  final int currentStreak;

  final List<WeekBucket> weeks;

  factory WeeklyStats.fromJson(Map<String, dynamic> json) {
    return WeeklyStats(
      from: DateTime.parse(json['from'] as String),
      to: DateTime.parse(json['to'] as String),
      currentStreak: json['currentStreak'] as int? ?? 0,
      weeks: ((json['weeks'] as List?) ?? const [])
          .map((e) => WeekBucket.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
    );
  }
}

// ======================================================================
// 单动作 e1RM
// ======================================================================

/// 单动作的估算 1RM 曲线。
class ExerciseE1rm {
  const ExerciseE1rm({
    required this.exerciseId,
    required this.exerciseName,
    required this.metricType,
    required this.supported,
    required this.allTimeBest,
    required this.points,
  });

  final int exerciseId;
  final String exerciseName;
  final String? metricType;

  /// 动作是否支持 e1RM。`false` 时 [points] 为空，
  /// 应显示「这个动作没有 1RM 概念」而不是一张空图
  /// ——空图看起来像「还没练」。
  final bool supported;

  /// 历史最高，画参考虚线用
  final double? allTimeBest;

  final List<E1rmPoint> points;

  factory ExerciseE1rm.fromJson(Map<String, dynamic> json) {
    return ExerciseE1rm(
      exerciseId: json['exerciseId'] as int,
      exerciseName: json['exerciseName'] as String? ?? '未知动作',
      metricType: json['metricType'] as String?,
      supported: json['supported'] as bool? ?? false,
      allTimeBest: (json['allTimeBest'] as num?)?.toDouble(),
      points: ((json['points'] as List?) ?? const [])
          .map((e) => E1rmPoint.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
    );
  }
}

/// 一次训练的数据点。
class E1rmPoint {
  const E1rmPoint({required this.date, required this.bestE1rm, required this.sets});

  final DateTime date;

  /// 该次训练的最佳组（主线）
  final double bestE1rm;

  /// 该次全部有效组（散点）
  final List<SetPoint> sets;

  factory E1rmPoint.fromJson(Map<String, dynamic> json) {
    return E1rmPoint(
      date: DateTime.parse(json['date'] as String),
      bestE1rm: (json['bestE1rm'] as num).toDouble(),
      sets: ((json['sets'] as List?) ?? const [])
          .map((e) => SetPoint.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
    );
  }
}

/// 一组的数据（散点 + 长按读数）。
class SetPoint {
  const SetPoint({required this.weight, required this.reps, required this.e1rm});

  final double weight;
  final int reps;
  final double e1rm;

  factory SetPoint.fromJson(Map<String, dynamic> json) {
    return SetPoint(
      weight: (json['weight'] as num?)?.toDouble() ?? 0,
      reps: json['reps'] as int? ?? 0,
      e1rm: (json['e1rm'] as num?)?.toDouble() ?? 0,
    );
  }
}

// ======================================================================
// PR 看板
// ======================================================================

class PrCard {
  const PrCard({
    required this.exerciseId,
    required this.exerciseName,
    required this.metric,
    required this.metricLabel,
    required this.value,
    required this.unit,
    required this.achievedOn,
    required this.daysAgo,
    required this.firstTime,
  });

  final int exerciseId;
  final String exerciseName;

  /// `MAX_WEIGHT` / `BEST_E1RM`
  final String metric;
  final String metricLabel;

  final double value;
  final String unit;
  final DateTime achievedOn;
  final int daysAgo;

  /// 首次记录即 PR。`METRICS 7.4` 要求标「首次记录」且用**中性样式**
  /// ——「第一次练」不是突破，渲染成庆祝会让真正的进步贬值
  final bool firstTime;

  factory PrCard.fromJson(Map<String, dynamic> json) {
    return PrCard(
      exerciseId: json['exerciseId'] as int,
      exerciseName: json['exerciseName'] as String? ?? '未知动作',
      metric: json['metric'] as String? ?? '',
      metricLabel: json['metricLabel'] as String? ?? '',
      value: (json['value'] as num?)?.toDouble() ?? 0,
      unit: json['unit'] as String? ?? '',
      achievedOn: DateTime.parse(json['achievedOn'] as String),
      daysAgo: json['daysAgo'] as int? ?? 0,
      firstTime: json['firstTime'] as bool? ?? false,
    );
  }
}
