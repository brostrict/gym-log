import '../../core/network/api_client.dart';
import '../../core/storage/token_storage.dart';

/// 认证相关的接口调用。
///
/// 只负责「发请求 + 存 token」，不持有状态——
/// 状态在 [AuthNotifier] 里，两者分开是为了让这个类能单测。
class AuthRepository {
  // 同 ApiClient：私有字段不能做命名参数，位置参数可以
  AuthRepository(this._api, this._tokenStorage);

  final ApiClient _api;
  final TokenStorage _tokenStorage;

  /// 注册。成功返回用户 id。
  ///
  /// ⚠️ 后端注册接口**不返回 token**（`data` 是用户 id），
  /// 所以注册完还要再登录一次。这不是缺陷——
  /// 注册和登录是两个语义，硬合并会让「注册后自动登录」这种产品决策
  /// 埋进接口里，改起来要动客户端。
  Future<int> register({
    required String email,
    required String password,
    required String nickname,
  }) async {
    final data = await _api.post('/auth/register', body: {
      'email': email,
      'password': password,
      'nickname': nickname,
    });
    return data as int;
  }

  /// 登录。成功后 token 已经写进 [TokenStorage]，调用方不用管。
  Future<void> login({required String email, required String password}) async {
    final data = await _api.post('/auth/login', body: {
      'email': email,
      'password': password,
    }) as Map;

    await _tokenStorage.save(
      accessToken: data['accessToken'] as String,
      refreshToken: data['refreshToken'] as String,
      userId: (data['user'] as Map?)?['id'] as int?,
    );
  }

  /// 登出。
  ///
  /// **无论后端调用成功与否，本地凭证都要清掉。**
  /// 网络不通时如果不清，用户点了登出却还是登录状态——
  /// 这既是体验问题也是安全问题（共用设备时下一个人能直接用）。
  Future<void> logout() async {
    try {
      await _api.post('/auth/logout');
    } catch (_) {
      // 忽略：本地清理才是关键
    } finally {
      await _tokenStorage.clear();
    }
  }

  /// 本地是否已有登录凭证（不校验是否过期）。
  Future<bool> hasLocalToken() async {
    final token = await _tokenStorage.readAccessToken();
    return token != null && token.isNotEmpty;
  }
}
