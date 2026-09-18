// 计划的模型。目前只有「从模板创建」需要的部分。

/// 内置计划模板。
///
/// 模板是「零配置开始训练」的入口——用户不必自己想清楚
/// 「一周练几次、每次练什么」就能开始。
class ProgramTemplate {
  const ProgramTemplate({
    required this.code,
    required this.name,
    required this.description,
    required this.goal,
    required this.level,
    required this.sessionsPerWeek,
    required this.totalWeeks,
    required this.daysPerWeek,
    required this.equipmentSummary,
    required this.estimatedMinutes,
  });

  final String code;
  final String name;
  final String description;

  /// MUSCLE_GAIN / STRENGTH / FAT_LOSS / GENERAL
  final String? goal;

  /// BEGINNER / INTERMEDIATE / ADVANCED
  final String? level;

  final int? sessionsPerWeek;
  final int? totalWeeks;

  /// 训练日模板个数。**不等于**每周训练次数——
  /// 5×5 是 2 个训练日模板（A/B）但每周练 3 次，靠轮转交替。
  final int? daysPerWeek;

  final String? equipmentSummary;
  final int? estimatedMinutes;

  String get goalLabel => switch (goal) {
        'MUSCLE_GAIN' => '增肌',
        'STRENGTH' => '力量',
        'FAT_LOSS' => '减脂',
        'GENERAL' => '综合',
        _ => '综合',
      };

  String get levelLabel => switch (level) {
        'BEGINNER' => '新手',
        'INTERMEDIATE' => '进阶',
        'ADVANCED' => '高级',
        _ => '',
      };

  String get frequencyLabel {
    final s = sessionsPerWeek ?? 0;
    final w = totalWeeks ?? 0;
    if (w > 0) return '每周 $s 练 · 共 $w 周';
    return '每周 $s 练 · 不限期';
  }

  factory ProgramTemplate.fromJson(Map<String, dynamic> json) {
    return ProgramTemplate(
      code: json['code'] as String,
      name: json['name'] as String? ?? '',
      description: json['description'] as String? ?? '',
      goal: json['goal'] as String?,
      level: json['level'] as String?,
      sessionsPerWeek: json['sessionsPerWeek'] as int?,
      totalWeeks: json['totalWeeks'] as int?,
      daysPerWeek: json['daysPerWeek'] as int?,
      equipmentSummary: json['equipmentSummary'] as String?,
      estimatedMinutes: json['estimatedMinutes'] as int?,
    );
  }
}
