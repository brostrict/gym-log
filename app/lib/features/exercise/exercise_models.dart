// 动作库的模型。对应后端 `/api/v1/exercises`。
//
// ⚠️ **筛选项和中文名都由服务端给**（`GET /exercises/filters`），客户端不硬编码。
// 服务端加一个 `KETTLEBELL`，客户端筛选器里没有 → 用户**筛不到**「壶铃」，
// 而动作库里明明有；反过来客户端留着已删的值 → 点了返回空列表，
// 看起来像「没有这个动作」。两种失效都不报错。
//
// ⚠️ 可空字段可能整个不存在（后端配了 `non_null`），一律 `as T?`。

/// 一个动作。
class Exercise {
  const Exercise({
    required this.id,
    required this.name,
    required this.primaryMuscle,
    required this.primaryMuscleLabel,
    required this.equipment,
    required this.equipmentLabel,
    required this.metricType,
    required this.metricTypeLabel,
    required this.unilateral,
    required this.builtIn,
    this.alias,
    this.secondaryMuscles,
    this.movementPattern,
    this.movementPatternLabel,
    this.bwFactor,
    this.instructions,
    this.commonMistakes,
  });

  final int id;
  final String name;

  /// 别名，逗号分隔（含英文名）。用于搜索——「bench press」要能搜到「杠铃卧推」
  final String? alias;

  final String primaryMuscle;
  final String primaryMuscleLabel;

  /// 次要肌群，逗号分隔的枚举名。后端不拆成数组——它只用于展示
  final String? secondaryMuscles;

  final String equipment;
  final String equipmentLabel;

  final String? movementPattern;
  final String? movementPatternLabel;

  final String metricType;
  final String metricTypeLabel;

  /// 自重系数。只有 `REPS_ONLY` 动作有值
  final double? bwFactor;

  /// 单侧动作（左右各做一次）。影响跟练时的组数语义
  final bool unilateral;

  /// 内置动作（所有人可见）还是用户自建
  final bool builtIn;

  final String? instructions;
  final String? commonMistakes;

  bool get hasGuide =>
      (instructions?.isNotEmpty ?? false) || (commonMistakes?.isNotEmpty ?? false);

  factory Exercise.fromJson(Map<String, dynamic> json) {
    return Exercise(
      id: json['id'] as int,
      name: json['name'] as String? ?? '',
      alias: json['alias'] as String?,
      primaryMuscle: json['primaryMuscle'] as String? ?? '',
      primaryMuscleLabel: json['primaryMuscleLabel'] as String? ?? '',
      secondaryMuscles: json['secondaryMuscles'] as String?,
      equipment: json['equipment'] as String? ?? '',
      equipmentLabel: json['equipmentLabel'] as String? ?? '',
      movementPattern: json['movementPattern'] as String?,
      movementPatternLabel: json['movementPatternLabel'] as String?,
      metricType: json['metricType'] as String? ?? '',
      metricTypeLabel: json['metricTypeLabel'] as String? ?? '',
      bwFactor: (json['bwFactor'] as num?)?.toDouble(),
      unilateral: json['unilateral'] as bool? ?? false,
      builtIn: json['builtIn'] as bool? ?? true,
      instructions: json['instructions'] as String?,
      commonMistakes: json['commonMistakes'] as String?,
    );
  }
}

/// 一个筛选项。
class FilterOption {
  const FilterOption({required this.value, required this.label});

  /// 枚举名，回传时用它
  final String value;
  final String label;

  factory FilterOption.fromJson(Map<String, dynamic> json) {
    return FilterOption(
      value: json['value'] as String? ?? '',
      label: json['label'] as String? ?? '',
    );
  }
}

/// 动作库的全部筛选项。
///
/// `muscles` 里**包含**热身和拉伸——它们不是肌群（不参与统计），
/// 但用户要能在动作库里筛出来。这是 `MuscleGroup.muscles()` 之外的全量。
class ExerciseFilters {
  const ExerciseFilters({
    required this.muscles,
    required this.equipment,
    required this.movementPatterns,
    required this.metricTypes,
  });

  final List<FilterOption> muscles;
  final List<FilterOption> equipment;
  final List<FilterOption> movementPatterns;
  final List<FilterOption> metricTypes;

  factory ExerciseFilters.fromJson(Map<String, dynamic> json) {
    List<FilterOption> of(String key) => ((json[key] as List?) ?? const [])
        .map((e) => FilterOption.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
    return ExerciseFilters(
      muscles: of('muscles'),
      equipment: of('equipment'),
      movementPatterns: of('movementPatterns'),
      metricTypes: of('metricTypes'),
    );
  }
}
