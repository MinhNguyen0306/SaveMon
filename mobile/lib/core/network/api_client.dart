import 'dart:convert';
import 'dart:typed_data';

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

  /// Sends one bounded in-memory multipart image without following redirects.
  /// The caller owns [bytes]; this method never writes the image to disk.
  Future<http.Response> sendMultipartFile({
    required String method,
    required String path,
    required String fieldName,
    required String filename,
    required String contentType,
    required Uint8List bytes,
    Map<String, String> headers = const {},
    Future<void>? abortTrigger,
    int maxBytes = 8 * 1024 * 1024,
  }) async {
    final uri = _relativeUri(path);
    if (bytes.isEmpty || bytes.length > maxBytes) {
      throw ArgumentError.value(
          bytes.length, 'bytes', 'Image size is invalid.');
    }
    if (!RegExp(r'^[a-zA-Z0-9_-]{1,64}$').hasMatch(fieldName) ||
        !RegExp(r'^[a-zA-Z0-9_.-]{1,128}$').hasMatch(filename) ||
        !const {'image/jpeg', 'image/png'}.contains(contentType)) {
      throw ArgumentError('Invalid multipart image metadata.');
    }
    final boundary = 'savemon-${DateTime.now().microsecondsSinceEpoch}';
    final prefix = '--$boundary\r\n'
        'Content-Disposition: form-data; name="$fieldName"; filename="$filename"\r\n'
        'Content-Type: $contentType\r\n\r\n';
    final suffix = '\r\n--$boundary--\r\n';
    final bodyBuilder = BytesBuilder(copy: false)
      ..add(ascii.encode(prefix))
      ..add(bytes)
      ..add(ascii.encode(suffix));
    final body = bodyBuilder.takeBytes();
    final session = await _readSession();
    final request = http.AbortableRequest(
      method,
      _baseUri.resolveUri(uri),
      abortTrigger: abortTrigger,
    )
      ..followRedirects = false
      ..headers.addAll(headers)
      ..headers['Content-Type'] = 'multipart/form-data; boundary=$boundary'
      ..bodyBytes = body;
    if (session != null) {
      request.headers['Authorization'] = 'Bearer ${session.accessToken}';
    }
    final streamed = await _httpClient.send(request);
    final response = await http.Response.fromStream(streamed);
    if (response.statusCode == 401 && session != null) await _onUnauthorized();
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(
          statusCode: response.statusCode, responseBody: response.body);
    }
    return response;
  }

  Uri _relativeUri(String path) {
    final relativeUri = Uri.parse(path);
    if (path.startsWith('/') ||
        relativeUri.hasScheme ||
        relativeUri.hasAuthority ||
        relativeUri.pathSegments.contains('..') ||
        relativeUri.hasFragment) {
      throw ArgumentError.value(
          path, 'path', 'Must be relative to the configured API base URL.');
    }
    return relativeUri;
  }

  /// Sends a bounded public/auth lifecycle request without attaching a bearer
  /// token or invoking the normal unauthorized-session handler.
  Future<http.Response> sendUnauthenticated({
    required String method,
    required String path,
    Map<String, String> headers = const {},
    String? body,
    Duration timeout = const Duration(seconds: 8),
  }) async {
    final relativeUri = Uri.parse(path);
    if (path.startsWith('/') ||
        relativeUri.hasScheme ||
        relativeUri.hasAuthority ||
        relativeUri.pathSegments.contains('..') ||
        relativeUri.hasFragment) {
      throw ArgumentError.value(
          path, 'path', 'Must be relative to the configured API base URL.');
    }
    final response = await (() async {
      final request = http.Request(method, _baseUri.resolveUri(relativeUri))
        ..headers.addAll(headers)
        ..followRedirects = false;
      if (body != null) request.body = body;
      final streamed = await _httpClient.send(request);
      return http.Response.fromStream(streamed);
    })()
        .timeout(timeout);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(
          statusCode: response.statusCode, responseBody: response.body);
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
