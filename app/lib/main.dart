import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'features/auth/auth_notifier.dart';
import 'features/auth/login_screen.dart';
import 'features/today/today_screen.dart';

void main() {
  // ProviderScope 必须包在最外层——它是 Riverpod 存放所有 provider 的地方。
  // 少了它，任何 ref.watch 都会抛「No ProviderScope found」。
  runApp(const ProviderScope(child: GymLogApp()));
}

class GymLogApp extends StatelessWidget {
  const GymLogApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'gym-log',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF2E7D32)),
        useMaterial3: true,
      ),
      home: const _Root(),
    );
  }
}

/// 根据登录态决定显示哪个页面。
///
/// 用 switch 而不是路由表：现在只有两个页面。
/// 等页面多起来（首页 / 计划 / 历史 / 我的）再引入 go_router 和底部导航——
/// **现在加只是多一层要维护的间接**。
class _Root extends ConsumerWidget {
  const _Root();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authProvider);

    return switch (auth) {
      // 正在检查本地凭证——显示启动画面。
      // 这一步很快（读一次本地存储），但**不能省**：
      // 直接显示登录页的话，已登录用户每次打开 App 都会先闪一下登录页。
      AuthUnknown() => const _SplashScreen(),
      AuthLoggedIn() => const TodayScreen(),
      _ => const LoginScreen(),
    };
  }
}

class _SplashScreen extends StatelessWidget {
  const _SplashScreen();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(child: CircularProgressIndicator()),
    );
  }
}
