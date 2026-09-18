// 身体数据的模型。对应后端 `/api/v1/body/*`。
//
// ⚠️ **所有数值、量程、单位、中文名都是后端给的，客户端一个字都不硬编码。**
//
// 这不是「整洁」问题，是正确性问题：服务端把体重上限从 300 改成 400，
// 客户端输入框还卡在 300，用户填 350 被前端拦住、请求根本发不出去，
// 而后端其实是接受的。反过来更糟：客户端放行、后端拒绝，
// 用户看到一句看不懂的报错。
//
// 所以 `GET /body/metric-types` 拿到的元数据就是表单的全部依据。
//
// ⚠️ **可空字段可能整个不存在**（不只是值为 null）。
// 后端配了 `spring.jackson.default-property-inclusion: non_null`，
// null 字段会被整个丢掉。所以一律用 `json['x'] as T?`。

/// 一个身体指标的元数据 —— 表单据此生成。
class BodyMetricType {
  const BodyMetricType({
    required this.metricType,
    required this.label,
    required this.unit,
    required this.group,
    required this.groupLabel,
    required this.min,
    required this.max,
    required this.hasSites,
    required this.siteRequired,
    required this.sites,
    required this.conditionSupported,
  });

  /// 枚举名，如 `WEIGHT`
  final String metricType;

  /// 中文名，如「体重」
  final String label;

  final String unit;

  /// 分组枚举名，如 `COMPOSITION`
  final String group;
  final String groupLabel;

  final double min;
  final double max;

  /// 有没有部位概念。false → **不显示**部位选择器
  final bool hasSites;

  /// 必须选部位吗。围度是 true
  final bool siteRequired;

  final List<BodySiteOption> sites;

  final bool conditionSupported;


  factory BodyMetricType.fromJson(Map<String, dynamic> json) {
    return BodyMetricType(
      metricType: json['metricType'] as String,
      label: json['label'] as String? ?? '',
      unit: json['unit'] as String? ?? '',
      group: json['group'] as String? ?? '',
      groupLabel: json['groupLabel'] as String? ?? '',
      min: (json['min'] as num?)?.toDouble() ?? 0,
      max: (json['max'] as num?)?.toDouble() ?? 0,
      hasSites: json['hasSites'] as bool? ?? false,
      siteRequired: json['siteRequired'] as bool? ?? false,
      sites: ((json['sites'] as List?) ?? const [])
          .map((e) => BodySiteOption.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
      conditionSupported: json['conditionSupported'] as bool? ?? false,
    );
  }
}

/// 一个部位选项。
class BodySiteOption {
  const BodySiteOption({
    required this.site,
    required this.label,
    required this.paired,
  });

  /// 枚举名，如 `LEFT_UPPER_ARM`
  final String site;
  final String label;

  /// 是不是四肢（左右成对）。只有成对的部位才谈得上「不对称度」（METRICS 2.2）
  final bool paired;

  factory BodySiteOption.fromJson(Map<String, dynamic> json) {
    return BodySiteOption(
      site: json['site'] as String? ?? '',
      label: json['label'] as String? ?? '',
      paired: json['paired'] as bool? ?? false,
    );
  }
}

/// 一条身体数据记录。
class BodyMetricRecord {
  const BodyMetricRecord({
    required this.id,
    required this.metricType,
    required this.metricLabel,
    required this.unit,
    required this.site,
    required this.siteLabel,
    required this.value,
    required this.measuredAt,
    this.condition,
    this.conditionLabel,
    this.note,
  });

  final int id;
  final String metricType;
  final String metricLabel;
  final String unit;

  /// 没有部位概念的指标是 `NONE`
  final String site;
  final String siteLabel;

  final double value;
  final DateTime measuredAt;

  /// 可能为 null —— 该指标不支持条件，或用户没填
  final String? condition;
  final String? conditionLabel;

  final String? note;

  factory BodyMetricRecord.fromJson(Map<String, dynamic> json) {
    return BodyMetricRecord(
      id: json['id'] as int,
      metricType: json['metricType'] as String? ?? '',
      metricLabel: json['metricLabel'] as String? ?? '',
      unit: json['unit'] as String? ?? '',
      site: json['site'] as String? ?? 'NONE',
      siteLabel: json['siteLabel'] as String? ?? '',
      value: (json['value'] as num?)?.toDouble() ?? 0,
      measuredAt: DateTime.parse(json['measuredAt'] as String),
      condition: json['condition'] as String?,
      conditionLabel: json['conditionLabel'] as String?,
      note: json['note'] as String?,
    );
  }
}

/// 趋势序列 —— 一张图要的全部数据。
class BodySeries {
  const BodySeries({
    required this.metricType,
    required this.metricLabel,
    required this.unit,
    required this.site,
    required this.siteLabel,
    required this.availableSites,
    required this.rawPoints,
    required this.dailyPoints,
    required this.maPoints,
    required this.enoughDataForMa,
    this.latestAt,
    this.latestValue,
    this.changeValue,
    this.referenceLabel,
    this.conditionHint,
  });

  final String metricType;
  final String metricLabel;
  final String unit;
  final String site;
  final String siteLabel;

  /// 有数据的部位，按解剖学顺序。
  ///
  /// `METRICS 2.5`：只测了部分部位时**只显示有数据的部位**——
  /// 所以切换器用这个列表，不是把 12 个部位全列出来。
  final List<BodySiteOption> availableSites;

  final List<BodyRawPoint> rawPoints;
  final List<BodyDailyPoint> dailyPoints;
  final List<BodyMaPoint> maPoints;

  /// 只决定要不要显示「数据不足」那句提示，**不决定画不画线**。
  /// 画不画线看每个点的 [BodyMaPoint.ma] 是不是 null。
  final bool enoughDataForMa;

  final DateTime? latestAt;
  final double? latestValue;

  /// 最新值相对基准的变化。**没有可用基准时为 null，不是 0**——
  /// 「第一次记录」和「和上次一样」必须能区分。
  final double? changeValue;

  /// 基准叫什么（「7 日均值」/「上次晨起空腹」）。
  /// **不要自己判断基准是什么**——那是服务端算的，两边判断会分叉。
  final String? referenceLabel;

  final String? conditionHint;

  bool get isEmpty => dailyPoints.isEmpty;

  factory BodySeries.fromJson(Map<String, dynamic> json) {
    final change = json['changeVsReference'] as Map?;
    return BodySeries(
      metricType: json['metricType'] as String? ?? '',
      metricLabel: json['metricLabel'] as String? ?? '',
      unit: json['unit'] as String? ?? '',
      site: json['site'] as String? ?? 'NONE',
      siteLabel: json['siteLabel'] as String? ?? '',
      availableSites: ((json['availableSites'] as List?) ?? const [])
          .map((e) => BodySiteOption.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
      rawPoints: ((json['rawPoints'] as List?) ?? const [])
          .map((e) => BodyRawPoint.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
      dailyPoints: ((json['dailyPoints'] as List?) ?? const [])
          .map((e) => BodyDailyPoint.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
      maPoints: ((json['maPoints'] as List?) ?? const [])
          .map((e) => BodyMaPoint.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
      enoughDataForMa: json['enoughDataForMa'] as bool? ?? false,
      latestAt: json['latestAt'] == null
          ? null
          : DateTime.parse(json['latestAt'] as String),
      latestValue: (json['latestValue'] as num?)?.toDouble(),
      changeValue: change == null ? null : (change['value'] as num?)?.toDouble(),
      referenceLabel: change?['referenceLabel'] as String?,
      conditionHint: json['conditionHint'] as String?,
    );
  }
}

/// 一次测量（背景散点）。带测量条件——`METRICS 1.4` 要求条件不同的点用不同形状。
class BodyRawPoint {
  const BodyRawPoint({
    required this.measuredAt,
    required this.value,
    this.condition,
    this.conditionLabel,
  });

  final DateTime measuredAt;
  final double value;
  final String? condition;
  final String? conditionLabel;

  factory BodyRawPoint.fromJson(Map<String, dynamic> json) {
    return BodyRawPoint(
      measuredAt: DateTime.parse(json['measuredAt'] as String),
      value: (json['value'] as num?)?.toDouble() ?? 0,
      condition: json['condition'] as String?,
      conditionLabel: json['conditionLabel'] as String?,
    );
  }
}

/// 一天一个点（`METRICS 1.2` 第一步：先合并单日多次测量）。
class BodyDailyPoint {
  const BodyDailyPoint({required this.date, required this.value});

  final DateTime date;
  final double value;

  factory BodyDailyPoint.fromJson(Map<String, dynamic> json) {
    return BodyDailyPoint(
      date: DateTime.parse(json['date'] as String),
      value: (json['value'] as num?)?.toDouble() ?? 0,
    );
  }
}

/// 移动平均点。
class BodyMaPoint {
  const BodyMaPoint({required this.date, required this.value, this.ma});

  final DateTime date;
  final double value;

  /// **为 null 表示这个点不该画**——两种原因：窗口内点不足，
  /// 或者整条序列点数不够（`AC-7-2`，此时服务端把所有 ma 都抹成了 null）。
  /// 遇到 null 要**断线**，不能连过去，更不能当成 0。
  final double? ma;

  factory BodyMaPoint.fromJson(Map<String, dynamic> json) {
    return BodyMaPoint(
      date: DateTime.parse(json['date'] as String),
      value: (json['value'] as num?)?.toDouble() ?? 0,
      ma: (json['ma'] as num?)?.toDouble(),
    );
  }
}
