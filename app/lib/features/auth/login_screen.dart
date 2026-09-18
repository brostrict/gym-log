import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'auth_notifier.dart';

/// 登录 / 注册。
///
/// **这一步的界面是临时的**——目的是把「登录 → 拿 token → 调受保护接口」
/// 这条链路在真机上跑通。视觉打磨在后面的步骤。
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _nickname = TextEditingController();

  /// 注册和登录共用一个表单，靠这个开关切换
  bool _registerMode = false;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _nickname.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final notifier = ref.read(authProvider.notifier);
    final email = _email.text.trim();
    final password = _password.text;

    if (_registerMode) {
      await notifier.register(
        email: email,
        password: password,
        nickname: _nickname.text.trim(),
      );
    } else {
      await notifier.login(email: email, password: password);
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = ref.watch(authProvider);
    final busy = auth is AuthBusy;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Icon(Icons.fitness_center, size: 64),
                const SizedBox(height: 16),
                Text(
                  _registerMode ? '注册 gym-log' : 'gym-log',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 32),

                TextField(
                  controller: _email,
                  keyboardType: TextInputType.emailAddress,
                  autocorrect: false,
                  enabled: !busy,
                  decoration: const InputDecoration(
                    labelText: '邮箱',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),

                TextField(
                  controller: _password,
                  obscureText: true,
                  enabled: !busy,
                  decoration: const InputDecoration(
                    labelText: '密码',
                    helperText: '至少 8 位',
                    border: OutlineInputBorder(),
                  ),
                ),

                if (_registerMode) ...[
                  const SizedBox(height: 12),
                  TextField(
                    controller: _nickname,
                    enabled: !busy,
                    decoration: const InputDecoration(
                      labelText: '昵称',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ],

                // 错误信息直接显示在表单下方——登录失败是常见路径，
                // 用 SnackBar 会一闪而过，用户来不及看清
                if (auth is AuthFailed) ...[
                  const SizedBox(height: 16),
                  _ErrorBanner(message: auth.message),
                ],

                const SizedBox(height: 24),
                FilledButton(
                  onPressed: busy ? null : _submit,
                  style: FilledButton.styleFrom(
                    // 64dp 是拇指热区下限（REQUIREMENTS M4-C-4）
                    minimumSize: const Size.fromHeight(56),
                  ),
                  child: busy
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : Text(_registerMode ? '注册' : '登录'),
                ),

                TextButton(
                  onPressed: busy
                      ? null
                      : () => setState(() => _registerMode = !_registerMode),
                  child: Text(_registerMode ? '已有账号？去登录' : '没有账号？去注册'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner({required this.message});
  final String message;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: scheme.errorContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(Icons.error_outline, color: scheme.onErrorContainer, size: 20),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: TextStyle(color: scheme.onErrorContainer),
            ),
          ),
        ],
      ),
    );
  }
}
