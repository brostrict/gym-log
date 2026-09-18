import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_client.dart';
import '../../core/providers.dart';
import 'program_models.dart';

class ProgramApi {
  ProgramApi(this._api);

  final ApiClient _api;

  /// 内置计划模板列表。
  ///
  /// 不返回结构（`structure`）——列表只需要「够认出是哪个模板」的信息。
  /// 完整内容在模板详情里，或者直接创建出来看。
  Future<List<ProgramTemplate>> templates() async {
    final data = await _api.get('/program-templates') as List;
    return data
        .map((e) => ProgramTemplate.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
  }

  /// 从模板创建计划。
  ///
  /// 创建出来的计划是**模板的一份完整拷贝**——之后用户改它，
  /// 不影响模板，也不影响别人从同一模板创建的计划。
  ///
  /// 返回新计划的 id。
  Future<int> createFromTemplate({
    required String templateCode,
    DateTime? startDate,
  }) async {
    final data = await _api.post('/programs/from-template', body: {
      'templateCode': templateCode,
      if (startDate != null) 'startDate': _isoDate(startDate),
    });
    return data as int;
  }

  /// 后端用 `LocalDate`，只接受 `yyyy-MM-dd`。
  ///
  /// ⚠️ 不能传 `toIso8601String()` 的前 10 位——本地时区下
  /// `DateTime(2026, 9, 14)` 在某些时区会序列化成前一天。
  /// 直接拼年月日最稳。
  static String _isoDate(DateTime d) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${d.year}-${two(d.month)}-${two(d.day)}';
  }
}

final programApiProvider = Provider<ProgramApi>(
  (ref) => ProgramApi(ref.watch(apiClientProvider)),
);

/// 内置模板列表。
final templatesProvider = FutureProvider<List<ProgramTemplate>>((ref) async {
  return ref.watch(programApiProvider).templates();
});
