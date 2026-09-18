// 推导指标的模型。对应后端 `/api/v1/body/derived` 与 `/api/v1/users/me`。
//
// ⚠️ **公式、分级标准、缺什么资料 —— 全部由服务端给。**
// 客户端不硬编码任何一个系数：分级标准会变（各国不同、会修订），
// 而它和算值是**同一份知识**，分开写就会出现「算出来 23.5 却标成超重」。

/// 一个推导指标。
class DerivedMetric {
  const DerivedMetric({
    required this.metricType,
    required this.label,
    required this.unit,
    this.value,
    this.level,
    this.rangeHint,
    this.formula,
    this.missing,
  });

  /// 枚举名，如 `BMI`
  final String metricType;
  final String label;
  final String unit;

  /// 推导出的值。**资料不全时为 null**——不是 0。
  final double? value;

  /// 解读：「正常」/「超重」/「健康」。没有值时为 null。
  final String? level;

  /// 分级参考文案，直接显示。
  final String? rangeHint;

  /// 这个数怎么来的（「体重 ÷ 身高²」）。
  ///
  /// **不是装饰**：去掉体脂秤五项的理由之一就是黑箱。
  /// 自己算的如果不写公式，和秤推的没有区别。
  final String? formula;

  /// 缺哪些资料才能算出来，如「身高、性别」。有值时该项为 null。
  final String? missing;

  bool get hasValue => value != null;

  factory DerivedMetric.fromJson(Map<String, dynamic> json) {
    return DerivedMetric(
      metricType: json['metricType'] as String,
      label: json['label'] as String? ?? '',
      unit: json['unit'] as String? ?? '',
      value: (json['value'] as num?)?.toDouble(),
      level: json['level'] as String?,
      rangeHint: json['rangeHint'] as String?,
      formula: json['formula'] as String?,
      missing: json['missing'] as String?,
    );
  }
}

/// 身体资料 —— 推导指标的输入。
class BodyProfile {
  const BodyProfile({this.gender, this.birthYear, this.heightCm});

  /// 0=未设置，1=男，2=女
  final int? gender;
  final int? birthYear;
  final double? heightCm;

  bool get isComplete =>
      gender != null && gender != 0 && birthYear != null && heightCm != null;

  factory BodyProfile.fromJson(Map<String, dynamic> json) {
    return BodyProfile(
      gender: json['gender'] as int?,
      birthYear: json['birthYear'] as int?,
      heightCm: (json['heightCm'] as num?)?.toDouble(),
    );
  }
}
