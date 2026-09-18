import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_client.dart';
import '../../core/providers.dart';
import 'exercise_models.dart';

/// 动作库接口。
class ExerciseApi {
  ExerciseApi(this._api);

  final ApiClient _api;

  /// 筛选项（肌群 / 器械 / 动作模式 / 计量类型）。
  ///
  /// **静态定义，不因用户而异**——取一次就够，客户端可以放心缓存。
  Future<ExerciseFilters> filters() async {
    final data = await _api.get('/exercises/filters');
    return ExerciseFilters.fromJson((data as Map).cast<String, dynamic>());
  }

  /// 查动作。
  ///
  /// ⚠️ `size` 给到服务端上限（500）：动作库浏览**天然要一次看全**——
  /// 用户是在找某个动作，不是在翻页。默认 20 会让大部分动作在界面上
  /// 直接消失，而列表看起来完全正常（只是少了几个），不报任何错。
  ///
  /// ⚠️ **参数名拼错会被服务端拒绝**（`QueryParamGuard`），不会静默忽略——
  /// 所以这里传的 key 必须和后端的 `ExerciseQuery` 字段名逐字一致。
  Future<List<Exercise>> query({
    String? primaryMuscle,
    String? equipment,
    String? movementPattern,
    String? metricType,
    String? keyword,
    int page = 1,
    int size = 500,
  }) async {
    final data = await _api.get('/exercises', query: {
      'primaryMuscle': ?primaryMuscle,
      'equipment': ?equipment,
      'movementPattern': ?movementPattern,
      'metricType': ?metricType,
      'keyword': ?keyword,
      'page': '$page',
      'size': '$size',
    });
    final map = (data as Map).cast<String, dynamic>();
    return ((map['records'] as List?) ?? const [])
        .map((e) => Exercise.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
  }
}

final exerciseApiProvider = Provider<ExerciseApi>(
  (ref) => ExerciseApi(ref.watch(apiClientProvider)),
);

final exerciseFiltersProvider = FutureProvider<ExerciseFilters>((ref) async {
  return ref.watch(exerciseApiProvider).filters();
});

/// 当前的筛选条件。null = 不筛。
///
/// 四个条件放在一个 record 里而不是四个 provider：
/// 它们**总是一起变化**（用户切换筛选时要么改一个、要么清空全部），
/// 拆开会让「清空筛选」变成四次状态更新，中间态还会触发多余的请求。
typedef ExerciseFilter = ({
  String? muscle,
  String? equipment,
  String? movementPattern,
});

final exerciseFilterProvider =
    NotifierProvider<ExerciseFilterNotifier, ExerciseFilter>(
        ExerciseFilterNotifier.new);

class ExerciseFilterNotifier extends Notifier<ExerciseFilter> {
  @override
  ExerciseFilter build() => (muscle: null, equipment: null, movementPattern: null);

  /// 点同一个 chip 两次 = 取消筛选。这比「再提供一个『全部』chip」省位置，
  /// 也是筛选器最常见的交互约定。
  void toggleMuscle(String value) =>
      state = (muscle: state.muscle == value ? null : value,
               equipment: state.equipment,
               movementPattern: state.movementPattern);

  void toggleEquipment(String value) =>
      state = (muscle: state.muscle,
               equipment: state.equipment == value ? null : value,
               movementPattern: state.movementPattern);

  void toggleMovement(String value) =>
      state = (muscle: state.muscle,
               equipment: state.equipment,
               movementPattern: state.movementPattern == value ? null : value);

  void clear() => state = (muscle: null, equipment: null, movementPattern: null);
}

/// 动作列表。筛选条件进 family key，改条件就是换一个 provider 实例。
final exerciseListProvider =
    FutureProvider.family<List<Exercise>, ExerciseFilter>((ref, f) async {
  return ref.watch(exerciseApiProvider).query(
        primaryMuscle: f.muscle,
        equipment: f.equipment,
        movementPattern: f.movementPattern,
      );
});
