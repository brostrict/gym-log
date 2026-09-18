import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../core/providers.dart';
import 'auth_repository.dart';

/// 登录态。
sealed class AuthState {
  const AuthState();
}

/// 还没检查本地凭证。App 启动时的初始状态。
class AuthUnknown extends AuthState {
  const AuthUnknown();
}

class AuthLoggedOut extends AuthState {
  const AuthLoggedOut();
}

class AuthLoggedIn extends AuthState {
  const AuthLoggedIn();
}

/// 正在登录 / 注册。
class AuthBusy extends AuthState {
  const AuthBusy();
}

/// 上一次操作失败。
///
/// 错误信息**放在状态里而不是抛异常**：登录失败是很正常的路径
/// （密码打错），不该让调用方到处 try/catch。
class AuthFailed extends AuthState {
  const AuthFailed(this.message);
  final String message;
}

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    ref.watch(apiClientProvider),
    ref.watch(tokenStorageProvider),
  );
});

final authProvider = NotifierProvider<AuthNotifier, AuthState>(AuthNotifier.new);

class AuthNotifier extends Notifier<AuthState> {
  @override
  AuthState build() {
    // build 里不能 await，所以先返回 Unknown，再异步检查本地凭证。
    // 界面在这个瞬间显示启动画面。
    _restore();
    return const AuthUnknown();
  }

  AuthRepository get _repo => ref.read(authRepositoryProvider);

  Future<void> _restore() async {
    final has = await _repo.hasLocalToken();
    if (!ref.mounted) return;
    state = has ? const AuthLoggedIn() : const AuthLoggedOut();
  }

  Future<void> login({required String email, required String password}) async {
    state = const AuthBusy();
    try {
      await _repo.login(email: email, password: password);
      state = const AuthLoggedIn();
    } on ApiException catch (e) {
      state = AuthFailed(e.message);
    } catch (e) {
      state = AuthFailed('登录失败：$e');
    }
  }

  Future<void> register({
    required String email,
    required String password,
    required String nickname,
  }) async {
    state = const AuthBusy();
    try {
      // 注册接口不返回 token，所以注册完直接接着登录，
      // 用户不必自己再输一遍密码
      await _repo.register(email: email, password: password, nickname: nickname);
      await _repo.login(email: email, password: password);
      state = const AuthLoggedIn();
    } on ApiException catch (e) {
      state = AuthFailed(e.message);
    } catch (e) {
      state = AuthFailed('注册失败：$e');
    }
  }

  Future<void> logout() async {
    await _repo.logout();
    state = const AuthLoggedOut();
  }
}
