import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_client.dart';
import '../../core/providers.dart';
import '../stats/stats_api.dart' show StatsApi, StatsRange;
import 'body_models.dart';
import 'derived_models.dart';

/// 身体数据接口。
class BodyApi {
  BodyApi(this._api);

  final ApiClient _api;

  /// 所有指标的元数据。**表单的唯一依据**——客户端不硬编码量程和单位。
  ///
  /// 不带用户维度：这是静态定义，不因用户而异，可以放心缓存。
  Future<List<BodyMetricType>> metricTypes() async {
    final data = await _api.get('/body/metric-types');
    return (data as List)
        .map((e) => BodyMetricType.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
  }

  /// 记录一次测量。**幂等**：同一 (指标, 部位, 测量时刻) 重复提交是覆盖。
  ///
  /// 服务端靠唯一键 + UPSERT 保证，所以离线重试重放同一条不会产生两条。
  Future<BodyMetricRecord> record({
    required String metricType,
    String? site,
    required double value,
    required DateTime measuredAt,
    String? condition,
    String? device,
    String? note,
  }) async {
    final data = await _api.post('/body/metrics', body: {
      'metricType': metricType,
      if (site != null && site != 'NONE') 'site': site,
      'value': value,
      'measuredAt': isoDateTime(measuredAt),
      'condition': ?condition,
      if (device != null && device.isNotEmpty) 'device': device,
      if (note != null && note.isNotEmpty) 'note': note,
    });
    return BodyMetricRecord.fromJson((data as Map).cast<String, dynamic>());
  }

  /// 趋势序列。
  ///
  /// 围度**必须带 `site`**——不带会被服务端拒（60005）。
  /// 「围度趋势」如果是 12 个部位混在一起的一条线，那个数没有任何意义。
  Future<BodySeries> series({
    required String metricType,
    String? site,
    required DateTime from,
    required DateTime to,
  }) async {
    final data = await _api.get('/body/series', query: {
      'metricType': metricType,
      if (site != null && site != 'NONE') 'site': site,
      'from': StatsApi.isoDate(from),
      'to': StatsApi.isoDate(to),
    });
    return BodySeries.fromJson((data as Map).cast<String, dynamic>());
  }

  /// 某指标的原始记录，倒序（最近的在最前）。
  Future<List<BodyMetricRecord>> list({
    required String metricType,
    String? site,
    int limit = 30,
  }) async {
    final data = await _api.get('/body/metrics', query: {
      'metricType': metricType,
      if (site != null && site != 'NONE') 'site': site,
      'limit': '$limit',
    });
    return (data as List)
        .map((e) => BodyMetricRecord.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
  }

  /// 从自测数据推导出的指标（BMI / 体脂率估算 / BMR / 腰高比）。
  ///
  /// **不接受参数**：这是「此刻的四个数」，不是序列。
  /// 派生值的趋势和体重曲线完全等价（BMI = 体重/身高²，身高是常数），
  /// 出一条曲线只是装饰——见后端 `BodyDerived` 的类注释。
  Future<List<DerivedMetric>> derived() async {
    final data = await _api.get('/body/derived');
    return (data as List)
        .map((e) => DerivedMetric.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
  }

  /// 读取身体资料（身高 / 出生年 / 性别）——推导指标的输入。
  Future<BodyProfile> bodyProfile() async {
    final data = await _api.get('/users/me');
    return BodyProfile.fromJson((data as Map).cast<String, dynamic>());
  }

  /// 更新身体资料。**只传要改的字段**，未传的保持不变。
  Future<BodyProfile> updateBodyProfile({
    int? gender,
    int? birthYear,
    double? heightCm,
  }) async {
    final data = await _api.put('/users/me/body-profile', body: {
      'gender': ?gender,
      'birthYear': ?birthYear,
      'heightCm': ?heightCm,
    });
    return BodyProfile.fromJson((data as Map).cast<String, dynamic>());
  }

  /// 删除一条记录。
  ///
  /// **不会改变任何历史训练会话的容量**——会话在创建时就快照了当时的体重
  /// （`REQUIREMENTS 3.3` 不变量 1）。用户会以为会，所以界面上要说明。
  Future<void> delete(int id) async {
    await _api.delete('/body/metrics/$id');
  }

  /// 后端用 `LocalDateTime`，接受 `yyyy-MM-ddTHH:mm:ss`。
  ///
  /// ⚠️ **不能用 `toIso8601String()`**：它带毫秒和 `Z`/时区后缀，
  /// 而服务端按**本地墙上时间**解释（全链路零转换）。
  /// 传 `2026-09-18T07:30:00.000Z` 会被当成 `2026-09-18 07:30:00` 的墙上时间，
  /// 或者在严格解析下直接 400。拼字符串最稳，和 `isoDate` 同理。
  static String isoDateTime(DateTime d) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${d.year}-${two(d.month)}-${two(d.day)}'
        'T${two(d.hour)}:${two(d.minute)}:${two(d.second)}';
  }
}

final bodyApiProvider = Provider<BodyApi>(
  (ref) => BodyApi(ref.watch(apiClientProvider)),
);

/// 指标元数据。**静态定义，取一次就够**，所以不带 family。
final bodyMetricTypesProvider = FutureProvider<List<BodyMetricType>>((ref) async {
  return ref.watch(bodyApiProvider).metricTypes();
});

/// 当前在趋势页选中的指标 + 部位。
///
/// 部位放在这里而不是各自的 `StatefulWidget` 里：切换指标时旧指标的部位
/// 必须失效（`WAIST` 对体重没有意义），而两个控件各存各的话就会出现
/// 「指标是体重、部位还是腰」的组合，请求直接 400。
typedef BodySelection = ({String metricType, String? site});

final bodySelectionProvider =
    NotifierProvider<BodySelectionNotifier, BodySelection>(
        BodySelectionNotifier.new);

class BodySelectionNotifier extends Notifier<BodySelection> {
  @override
  BodySelection build() => (metricType: 'WEIGHT', site: null);

  /// 换指标时**部位清空**——理由见 typedef 上的注释
  void selectMetric(String metricType) =>
      state = (metricType: metricType, site: null);

  void selectSite(String? site) =>
      state = (metricType: state.metricType, site: site);
}

/// 趋势序列。范围复用统计页的切换器，两端默认都是 3 个月（`M7-B-2`）。
final bodySeriesProvider =
    FutureProvider.family<BodySeries, ({BodySelection sel, StatsRange range})>(
        (ref, arg) async {
  final today = DateTime.now();
  return ref.watch(bodyApiProvider).series(
        metricType: arg.sel.metricType,
        site: arg.sel.site,
        from: arg.range.from(today),
        to: today,
      );
});

/// 推导指标。**没有 family**——它不依赖选中的指标，也不是序列。
final derivedMetricsProvider = FutureProvider<List<DerivedMetric>>((ref) async {
  return ref.watch(bodyApiProvider).derived();
});

/// 身体资料（推导指标的输入）。
final bodyProfileProvider = FutureProvider<BodyProfile>((ref) async {
  return ref.watch(bodyApiProvider).bodyProfile();
});

/// 某指标的最近记录（趋势页下方的列表 + 删除入口）。
final bodyRecordsProvider =
    FutureProvider.family<List<BodyMetricRecord>, String>((ref, metricType) async {
  return ref.watch(bodyApiProvider).list(metricType: metricType);
});
