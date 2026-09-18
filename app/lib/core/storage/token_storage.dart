import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// token 的本地存储。
///
/// **用 `flutter_secure_storage` 而不是 `SharedPreferences`**：
/// 后者存的是明文（Android 上是 XML 文件，root 过的设备直接可读）。
/// refresh token 有效期 30 天，泄露等于账号被长期接管。
///
/// 前者在 Android 上走 Keystore 加密，iOS 上走 Keychain。
class TokenStorage {
  TokenStorage({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;

  static const _keyAccess = 'access_token';
  static const _keyRefresh = 'refresh_token';
  static const _keyUserId = 'user_id';

  Future<String?> readAccessToken() => _storage.read(key: _keyAccess);

  Future<String?> readRefreshToken() => _storage.read(key: _keyRefresh);

  /// 登录 / 刷新成功后调用。
  Future<void> save({
    required String accessToken,
    required String refreshToken,
    int? userId,
  }) async {
    await _storage.write(key: _keyAccess, value: accessToken);
    await _storage.write(key: _keyRefresh, value: refreshToken);
    if (userId != null) {
      await _storage.write(key: _keyUserId, value: userId.toString());
    }
  }

  /// 登出时调用。
  ///
  /// **两个 token 一起清**——只清 access token 的话，
  /// 用户「登出」后客户端还能拿 refresh token 换回一个有效的 access token，
  /// 登出就是假的。
  Future<void> clear() async {
    await _storage.delete(key: _keyAccess);
    await _storage.delete(key: _keyRefresh);
    await _storage.delete(key: _keyUserId);
  }
}
