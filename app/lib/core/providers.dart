import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'network/api_client.dart';
import 'storage/token_storage.dart';

/// 全局依赖的装配点。
///
/// **所有单例都在这里声明**，其他地方只能通过 `ref.watch` 拿。
/// 这样测试里可以用 `ProviderScope(overrides: [...])` 换成假的，
/// 不必给每个类都留一个「注入用的构造函数参数」。
///
/// 这也是 Riverpod 相比手写单例的主要价值：**依赖关系显式可见**。
/// 打开这个文件就知道整个 app 依赖了哪些外部资源。

final tokenStorageProvider = Provider<TokenStorage>((ref) => TokenStorage());

final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(ref.watch(tokenStorageProvider));
});
