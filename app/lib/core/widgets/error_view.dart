import 'package:flutter/material.dart';

import '../network/api_exception.dart';

/// 请求失败时的整页错误态。
///
/// **这是第三份了**（`today_screen` 和 `summary_screen` 各有一份），
/// 所以提到共享位置——统计页会是第四份。
///
/// 只抽这一个，**没有顺手把 `_BigMetric` / `_DeltaRow` 也抽了**：
/// 那些 widget 现在的形状是为总结页长的，还不知道统计页要不要同样的形状。
/// 先抽再改会**两次改动已经工作的页面**。
class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.error, required this.onRetry});

  final Object error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.cloud_off,
                size: 48, color: Theme.of(context).colorScheme.error),
            const SizedBox(height: 12),
            Text(
              error is ApiException ? (error as ApiException).message : '$error',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            OutlinedButton(onPressed: onRetry, child: const Text('重试')),
          ],
        ),
      ),
    );
  }
}
