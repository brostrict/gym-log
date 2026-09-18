import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../config/app_config.dart';
import '../storage/token_storage.dart';
import 'api_exception.dart';

/// HTTP 客户端 —— 全应用唯一的出口。
///
/// 三件事在这里统一处理，业务代码不用管：
///
/// 1. **拆包**：后端返回 `{code, message, data}`，这里只把 `data` 交出去。
///    `code != 0` 时抛 [ApiException]，业务代码写 try/catch 而不是判断 code。
/// 2. **带 token**：自动附上 `Authorization`，业务代码不碰它。
/// 3. **自动续期**：access token 只有 1 小时，过期时用 refresh token 换新的
///    并**重放原请求**，用户无感知（AC-1-2）。
class ApiClient {
  // 用**位置参数**的 `this._` 形式：Dart 不允许私有字段做命名参数，
  // 但位置参数可以。这样既满足 prefer_initializing_formals，
  // 又不用写 ignore 注释。
  ApiClient(this._tokenStorage, {Dio? dio}) : _dio = dio ?? Dio() {
    _dio.options
      ..baseUrl = AppConfig.apiBaseUrl
      ..connectTimeout = const Duration(seconds: AppConfig.connectTimeoutSec)
      ..receiveTimeout = const Duration(seconds: AppConfig.receiveTimeoutSec)
      ..contentType = Headers.jsonContentType;

    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: _attachToken,
      onError: _handleError,
    ));

    if (kDebugMode) {
      _dio.interceptors.add(LogInterceptor(requestBody: true, responseBody: false));
    }
  }

  final Dio _dio;
  final TokenStorage _tokenStorage;

  /// 是否正在刷新 token。
  ///
  /// 并发的多个请求同时收到 401 时，**只刷新一次**，其余等它完成。
  /// 不加这个的话会同时发 N 个刷新请求，而后端的 refresh token 是
  /// **一次性轮换**的——第一个刷新成功、旧的 refresh token 立即失效，
  /// 剩下 N-1 个全部失败，用户被踢下线。
  Future<bool>? _refreshing;

  // ==================================================================
  // 公开方法（返回拆包后的 data）
  // ==================================================================

  Future<dynamic> get(String path, {Map<String, dynamic>? query}) =>
      _request(() => _dio.get(path, queryParameters: query));

  Future<dynamic> post(String path, {Object? body}) =>
      _request(() => _dio.post(path, data: body));

  Future<dynamic> put(String path, {Object? body}) =>
      _request(() => _dio.put(path, data: body));

  Future<dynamic> patch(String path, {Object? body, Map<String, dynamic>? query}) =>
      _request(() => _dio.patch(path, data: body, queryParameters: query));

  Future<dynamic> delete(String path) => _request(() => _dio.delete(path));

  // ==================================================================
  // 内部
  // ==================================================================

  Future<dynamic> _request(Future<Response<dynamic>> Function() send) async {
    try {
      final response = await send();
      return _unwrap(response);
    } on DioException catch (e) {
      throw _toApiException(e);
    }
  }

  /// 拆开统一响应体，只返回 `data`。
  dynamic _unwrap(Response<dynamic> response) {
    final body = response.data;
    if (body is! Map) {
      // 后端所有接口都返回 {code, message, data}。
      // 不是这个形状说明请求打到了别的地方（比如被网关/代理拦截）。
      throw ApiException(
        code: ApiException.parseErrorCode,
        message: '服务器返回格式异常',
        httpStatus: response.statusCode,
      );
    }

    final code = body['code'] as int?;
    if (code == null) {
      throw ApiException(
        code: ApiException.parseErrorCode,
        message: '服务器返回格式异常',
        httpStatus: response.statusCode,
      );
    }
    if (code != 0) {
      throw ApiException(
        code: code,
        message: (body['message'] as String?) ?? '请求失败',
        httpStatus: response.statusCode,
      );
    }
    return body['data'];
  }

  /// 附上 Authorization。
  Future<void> _attachToken(RequestOptions options, RequestInterceptorHandler handler) async {
    // 登录 / 刷新接口自己不需要 token
    if (!_isAuthEndpoint(options.path)) {
      final token = await _tokenStorage.readAccessToken();
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }
    }
    handler.next(options);
  }

  /// 错误处理 —— 核心是「access token 过期时自动续期并重放请求」。
  Future<void> _handleError(DioException e, ErrorInterceptorHandler handler) async {
    final response = e.response;

    // ---------- 是不是「登录态过期」 ----------
    //
    // 后端在两种情况下返回 10001：
    //   ① access token 过期或无效
    //   ② 压根没带 token
    //
    // ⚠️ 必须排除「刷新接口自己返回 10001」——否则会用 refresh token
    // 去刷新 refresh token，无限循环。这也是 `_isAuthEndpoint` 的作用。
    final body = response?.data;
    final code = (body is Map) ? body['code'] as int? : null;

    final shouldRefresh = code == 10001
        && !_isAuthEndpoint(e.requestOptions.path)
        && e.requestOptions.extra['retried'] != true;

    if (!shouldRefresh) {
      handler.next(e);
      return;
    }

    final refreshed = await _refreshToken();
    if (!refreshed) {
      // 刷新失败（refresh token 也过期了）→ 清掉本地凭证，
      // 让上层跳登录页。留着无效 token 只会让每个请求都失败一次。
      await _tokenStorage.clear();
      handler.next(e);
      return;
    }

    // ---------- 重放原请求 ----------
    try {
      final options = e.requestOptions;
      // 标记已重试，避免刷新成功但接口仍返回 10001 时又刷一次
      options.extra['retried'] = true;

      final token = await _tokenStorage.readAccessToken();
      options.headers['Authorization'] = 'Bearer $token';

      final response = await _dio.fetch(options);
      handler.resolve(response);
    } on DioException catch (retryError) {
      handler.next(retryError);
    }
  }

  /// 用 refresh token 换新的 access token。成功返回 true。
  Future<bool> _refreshToken() {
    // 已有刷新在跑 → 复用它，不要并发再发一个（见 _refreshing 的注释）
    return _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);
  }

  Future<bool> _doRefresh() async {
    final refreshToken = await _tokenStorage.readRefreshToken();
    if (refreshToken == null) {
      return false;
    }
    try {
      // 用独立的 Dio，不经过本客户端的拦截器——
      // 否则这个请求自己失败时又会触发一次刷新
      final response = await Dio(BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        connectTimeout: const Duration(seconds: AppConfig.connectTimeoutSec),
        receiveTimeout: const Duration(seconds: AppConfig.receiveTimeoutSec),
        contentType: Headers.jsonContentType,
      )).post('/auth/refresh', data: {'refreshToken': refreshToken});

      final data = response.data['data'];
      await _tokenStorage.save(
        accessToken: data['accessToken'] as String,
        // 后端会**轮换** refresh token：旧的立刻失效，必须保存新的。
        // 不保存的话下次刷新会失败，用户被迫重新登录。
        refreshToken: data['refreshToken'] as String,
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  bool _isAuthEndpoint(String path) =>
      path.contains('/auth/login') ||
      path.contains('/auth/register') ||
      path.contains('/auth/refresh');

  /// 把 Dio 的异常翻译成 [ApiException]。
  ApiException _toApiException(DioException e) {
    // ---------- 后端确实返回了响应 → 业务错误 ----------
    //
    // 这条路径覆盖绝大多数「正常的失败」：邮箱已注册、计划不存在、
    // 乐观锁冲突……它们的 HTTP 状态码不是 2xx，所以 Dio 会当成异常抛，
    // 但响应体里有完整的 {code, message}，要原样带出去。
    final response = e.response;
    if (response != null && response.data is Map) {
      final body = response.data as Map;
      final code = body['code'] as int?;
      if (code != null) {
        return ApiException(
          code: code,
          message: (body['message'] as String?) ?? '请求失败',
          httpStatus: response.statusCode,
        );
      }
    }

    // ---------- 压根没拿到响应 → 网络问题 ----------
    return switch (e.type) {
      DioExceptionType.connectionTimeout ||
      DioExceptionType.sendTimeout ||
      DioExceptionType.receiveTimeout =>
        ApiException.timeout,
      DioExceptionType.badResponse => ApiException(
          code: ApiException.parseErrorCode,
          message: '服务器返回异常（${response?.statusCode}）',
          httpStatus: response?.statusCode,
        ),
      _ => ApiException.networkUnreachable,
    };
  }
}
