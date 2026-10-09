import 'package:http/http.dart' as http;

import '../session/session.dart';

class ApiClient {
  ApiClient({
    required Uri baseUri,
    required http.Client httpClient,
    required Future<Session?> Function() readSession,
    required Future<void> Function() onUnauthorized,
  })  : _baseUri = _withTrailingSlash(baseUri),
        _httpClient = httpClient,
        _readSession = readSession,
        _onUnauthorized = onUnauthorized;

  final Uri _baseUri;
  final http.Client _httpClient;
  final Future<Session?> Function() _readSession;
  final Future<void> Function() _onUnauthorized;

  Future<http.Response> send({
    required String method,
    required String path,
    Map<String, String> headers = const {},
    String? body,
  }) async {
    final relativeUri = Uri.parse(path);
    if (path.startsWith('/') ||
        relativeUri.hasScheme ||
        relativeUri.hasAuthority ||
        relativeUri.pathSegments.contains('..') ||
        relativeUri.hasFragment) {
      throw ArgumentError.value(
        path,
        'path',
        'Must be relative to the configured API base URL.',
      );
    }

    final session = await _readSession();
    final request = http.Request(method, _baseUri.resolveUri(relativeUri))
      ..headers.addAll(headers);
    if (session != null) {
      request.headers['Authorization'] = 'Bearer ${session.accessToken}';
    }
    if (body != null) {
      request.body = body;
    }

    final streamedResponse = await _httpClient.send(request);
    final response = await http.Response.fromStream(streamedResponse);
    if (response.statusCode == 401 && session != null) {
      await _onUnauthorized();
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(
        statusCode: response.statusCode,
        responseBody: response.body,
      );
    }
    return response;
  }

  static Uri _withTrailingSlash(Uri uri) {
    if (uri.path.endsWith('/')) {
      return uri;
    }
    return uri.replace(path: '${uri.path}/');
  }
}

class ApiException implements Exception {
  const ApiException({
    required this.statusCode,
    required this.responseBody,
  });

  final int statusCode;
  final String responseBody;

  @override
  String toString() => 'API request failed with status $statusCode.';
}
