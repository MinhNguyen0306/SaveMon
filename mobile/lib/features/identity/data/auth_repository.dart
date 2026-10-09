import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/network/api_client.dart';
import '../../../core/session/auth_state.dart';
import '../../../core/session/session.dart';

class AuthUser {
  const AuthUser({required this.id, required this.email, required this.displayName});

  factory AuthUser.fromJson(Map<String, dynamic> json) => AuthUser(
        id: _string(json, 'id'),
        email: _string(json, 'email'),
        displayName: _string(json, 'displayName'),
      );

  final String id;
  final String email;
  final String displayName;
}

class AuthenticationResult {
  const AuthenticationResult({
    required this.user,
    required this.expiresIn,
    required this.accessToken,
  });

  final AuthUser user;
  final int expiresIn;
  final String accessToken;
}

class AuthRepository {
  const AuthRepository(this._apiClient, this._saveSession);

  final ApiClient _apiClient;
  final Future<void> Function(Session session) _saveSession;

  Future<AuthenticationResult> register({
    required String email,
    required String password,
    required String displayName,
  }) async {
    final response = await _apiClient.send(
      method: 'POST',
      path: 'auth/register',
      headers: const {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': email,
        'password': password,
        'displayName': displayName,
      }),
    );
    final result = _parseAuthenticationResponse(response.body);
    await _saveSession(Session(accessToken: result.accessToken));
    return result;
  }

  Future<AuthenticationResult> login({
    required String email,
    required String password,
  }) async {
    final response = await _apiClient.send(
      method: 'POST',
      path: 'auth/login',
      headers: const {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': email,
        'password': password,
      }),
    );
    final result = _parseAuthenticationResponse(response.body);
    await _saveSession(Session(accessToken: result.accessToken));
    return result;
  }

  AuthenticationResult _parseAuthenticationResponse(String body) {
    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic> ||
        decoded['user'] is! Map<String, dynamic> ||
        decoded['accessToken'] is! String ||
        decoded['expiresIn'] is! int) {
      throw const FormatException(
        'Authentication response does not match the API contract.',
      );
    }
    final user = AuthUser.fromJson(decoded['user'] as Map<String, dynamic>);
    final token = decoded['accessToken'] as String;
    if (token.isEmpty) {
      throw const FormatException('Authentication response contains an empty token.');
    }
    final expiresIn = decoded['expiresIn'] as int;
    return AuthenticationResult(
      user: user,
      expiresIn: expiresIn,
      accessToken: token,
    );
  }
}

String _string(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String) return value;
  throw FormatException('Expected "$key" to be a string.');
}

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    ref.watch(apiClientProvider),
    (session) => ref.read(authStateProvider.notifier).setSession(session),
  );
});
