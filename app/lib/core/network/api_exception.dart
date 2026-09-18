/// 后端返回的业务错误。
///
/// 后端的统一响应格式是：
/// ```json
/// { "code": 40001, "message": "计划不存在" }
/// ```
/// `code` 是**分段的业务码**（1xxxx 通用、2xxxx 用户、4xxxx 计划…），
/// 客户端据此做分支判断，比依据 message 文本可靠得多——
/// 文案会改，码不会。
///
/// 所以这个异常同时带着两者：`message` 给人看，`code` 给程序判断。
class ApiException implements Exception {
  const ApiException({
    required this.code,
    required this.message,
    this.httpStatus,
  });

  /// 业务码。网络层错误（连不上、超时）用 [networkErrorCode]。
  final int code;

  /// 可直接展示给用户的文案。后端返回的就是中文。
  final String message;

  /// HTTP 状态码，仅用于排查。业务分支一律看 [code]。
  final int? httpStatus;

  /// 连不上、超时、DNS 失败等——**这些不是后端返回的**，
  /// 是我们自己造的码。用负数避免和后端的分段码冲突。
  static const int networkErrorCode = -1;
  static const int timeoutCode = -2;
  static const int parseErrorCode = -3;

  /// 是否属于「网络问题」而不是「业务拒绝」。
  ///
  /// 这个区分很关键：网络问题**可以重试、应该进离线队列**；
  /// 业务拒绝（比如「计划不存在」）重试一万次也没用。
  bool get isNetworkIssue => code < 0;

  /// 是否是登录态失效，需要跳登录页。
  ///
  /// 10001 = 未登录或登录已过期（后端 ErrorCode.UNAUTHORIZED）
  bool get isUnauthorized => code == 10001;

  static const ApiException networkUnreachable = ApiException(
    code: networkErrorCode,
    message: '网络连接失败，请检查网络后重试',
  );

  static const ApiException timeout = ApiException(
    code: timeoutCode,
    message: '请求超时，请稍后重试',
  );

  static const ApiException badResponse = ApiException(
    code: parseErrorCode,
    message: '服务器返回的数据无法解析',
  );

  @override
  String toString() => 'ApiException($code): $message';
}
