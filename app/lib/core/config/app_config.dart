/// 应用级配置。
///
/// 所有值都可以在编译期用 `--dart-define` 覆盖：
///
/// ```bash
/// flutter run --dart-define=API_BASE_URL=http://192.168.1.10:8080/api/v1
/// ```
///
/// **为什么不写成配置文件**：Flutter 的 assets 是打包进 APK 的，
/// 改了要重新构建；而 `--dart-define` 也是编译期注入，但它有明确的类型
/// 和默认值，不用处理「文件读不到 / 格式不对」这些运行时分支。
class AppConfig {
  const AppConfig._();

  /// 后端地址。
  ///
  /// **默认值是 `localhost`，靠 `adb reverse` 让它成立**：
  ///
  /// ```bash
  /// adb reverse tcp:8080 tcp:8080
  /// ```
  ///
  /// 这条命令把手机上的 8080 转发到**开发机**的 8080。
  /// 于是手机访问 `localhost:8080` 就等于访问你电脑上的后端。
  ///
  /// **为什么不用局域网 IP**：那是另一条路（手机和电脑同网段 + 填 IP +
  /// 放行 Windows 防火墙入站规则）。能通，但每次换网络 IP 就变，
  /// 而且防火墙那条规则要单独配。`adb reverse` 走 USB，不依赖网络。
  ///
  /// ⚠️ **`adb reverse` 在设备重连后会失效**，需要重新执行。
  /// 真机调试连不上后端时，先想起这条命令。
  ///
  /// ⚠️ **明文 HTTP 有白名单。** Android 9 起默认禁明文，本项目只放行了
  /// `localhost` / `127.0.0.1` / `10.0.2.2`（见
  /// `android/app/src/main/res/xml/network_security_config.xml`）。
  /// **换成上面的局域网 IP 时，必须把那个 IP 也加进去**——否则同样是
  /// 「网络连接失败」，且看不出是明文被拦。
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080/api/v1',
  );

  /// 请求超时（秒）。
  ///
  /// 10 秒是权衡：健身房里信号可能很差，但用户不会等超过 10 秒——
  /// 超时快一点，客户端才能尽早走离线队列（Phase 3.9）。
  static const int connectTimeoutSec = 10;
  static const int receiveTimeoutSec = 15;
}
