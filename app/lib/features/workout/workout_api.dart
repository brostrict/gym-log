import '../../core/network/api_client.dart';
import 'summary_models.dart';
import 'workout_models.dart';

/// 会话相关接口。
class WorkoutApi {
  WorkoutApi(this._api);

  final ApiClient _api;

  /// 开始一次训练。
  ///
  /// [clientKey] 是幂等键：离线重试时后端靠它去重。
  /// 每次「开始训练」生成一个并**一直带着**它重试，
  /// 不要每次重试都重新生成——那样幂等就失效了。
  Future<WorkoutSession> startSession({
    required int programId,
    required int dayNumber,
    required String clientKey,
    DateTime? startedAt,
  }) async {
    final data = await _api.post('/sessions', body: {
      'programId': programId,
      'dayNumber': dayNumber,
      'clientKey': clientKey,
      if (startedAt != null) 'startedAt': _isoLocal(startedAt),
    });
    return WorkoutSession.fromJson((data as Map).cast<String, dynamic>());
  }

  /// 恢复进行中的会话（断点续训）。
  ///
  /// 没有进行中的会话时后端返回 `data: null`——**这不是错误**。
  Future<WorkoutSession?> activeSession() async {
    final data = await _api.get('/sessions/active');
    if (data == null) return null;
    return WorkoutSession.fromJson((data as Map).cast<String, dynamic>());
  }

  Future<WorkoutSession> sessionDetail(int sessionId) async {
    final data = await _api.get('/sessions/$sessionId');
    return WorkoutSession.fromJson((data as Map).cast<String, dynamic>());
  }

  /// 记录一组。
  ///
  /// **PUT 而不是 POST**：路径已经唯一确定了「哪个动作的第几组」，
  /// 所以重复调用结果相同。离线重试不会产生重复记录。
  Future<void> recordSet({
    required int sessionId,
    required int sessionExerciseId,
    required int setNumber,
    required String setType,
    double? weight,
    int? reps,
    int? durationSec,
    double? rpe,
    int? restActualSec,
    required DateTime completedAt,
  }) async {
    await _api.put('/sessions/$sessionId/exercises/$sessionExerciseId/sets/$setNumber',
        body: {
          'setType': setType,
          // `?value` 是 Dart 的 null-aware 元素：值为 null 时这一项**整个不出现**，
          // 而不是写成 null。
          //
          // ⚠️ 但**别以为这样就能保住原值**——服务端 `SetRecordService`
          // 是**全量覆盖**语义：没传的字段一律写成 null，和显式传 null 没有区别。
          // 这里没有 PATCH 语义。
          //
          // 所以任何「同一个 (动作, 组号) 再 PUT 一次」的地方，都必须
          // 把这一组**所有**字段带上。踩过：休息结束补写 restActualSec 时
          // 没带 durationSec，刚记好的时长被静默抹成 null，而且只有非最后
          // 一组会被抹（最后一组不经过休息），现象极难排查。
          'weight': ?weight,
          'reps': ?reps,
          'durationSec': ?durationSec,
          'rpe': ?rpe,
          'restActualSec': ?restActualSec,
          'completedAt': _isoLocal(completedAt),
        });
  }

  /// 跳过某个动作（M4-D-1）。
  Future<void> skipExercise({
    required int sessionId,
    required int sessionExerciseId,
  }) async {
    await _api.patch(
      '/sessions/$sessionId/exercises/$sessionExerciseId/status',
      query: {'status': 'SKIPPED'},
    );
  }

  /// 结束训练。
  ///
  /// [durationSec] **通常会被服务端忽略**——它自己从组记录推算
  /// （最后一组的完成时刻 − 开始时刻），因为「点结束的时刻」
  /// 不等于「练完的时刻」。这个值只在没有组记录时兜底。
  Future<void> finishSession({
    required int sessionId,
    required int durationSec,
    String? note,
  }) async {
    await _api.patch('/sessions/$sessionId/finish', query: {
      'durationSec': durationSec,
      if (note != null && note.isNotEmpty) 'note': note,
    });
  }

  /// 训练总结。
  ///
  /// 容量 / PR / 与上次对比**全部由后端算好**——
  /// 口径（含自重、排除热身、e1RM 只取最佳组且 r ≤ 12）只应该有一份实现。
  /// 客户端自己算一遍的话，训练总结和周报图会给出不同的数字。
  Future<SessionSummary> summary(int sessionId) async {
    final data = await _api.get('/sessions/$sessionId/summary');
    return SessionSummary.fromJson((data as Map).cast<String, dynamic>());
  }

  /// 后端用的是 `LocalDateTime`（无时区），序列化成 `yyyy-MM-ddTHH:mm:ss`。
  ///
  /// ⚠️ 不能直接用 `DateTime.toIso8601String()`——它带毫秒和 `Z`，
  /// 后端的 `LocalDateTime` 解析不了带时区的字符串。
  static String _isoLocal(DateTime t) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${t.year}-${two(t.month)}-${two(t.day)}'
        'T${two(t.hour)}:${two(t.minute)}:${two(t.second)}';
  }
}
