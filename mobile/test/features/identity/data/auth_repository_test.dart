import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/core/session/session.dart';
import 'package:savemon_mobile/features/identity/data/auth_repository.dart';

void main() {
  test('registers with the documented body, parses response and saves token', () async {
    http.Request? sent;
    Session? saved;
    final client = ApiClient(
      baseUri: Uri.parse('https://api.test/api/v1'),
      httpClient: MockClient((request) async {
        sent = request;
        return http.Response(jsonEncode({
          'user': {'id': 'u-1', 'email': 'a@example.test', 'displayName': 'A'},
          'accessToken': 'signed-token',
          'expiresIn': 900,
        }), 201);
      }),
      readSession: () async => null,
      onUnauthorized: () async {},
    );
    final repository = AuthRepository(client, (session) async {
      saved = session;
    });

    final result = await repository.register(
      email: ' A@example.test ',
      password: 'exactPass123',
      displayName: 'A',
    );

    expect(sent!.url.toString(), 'https://api.test/api/v1/auth/register');
    expect(sent!.headers['content-type'], 'application/json');
    expect(jsonDecode(sent!.body), {
      'email': ' A@example.test ',
      'password': 'exactPass123',
      'displayName': 'A',
    });
    expect(result.user.id, 'u-1');
    expect(result.expiresIn, 900);
    expect(result.accessToken, 'signed-token');
    expect(saved!.accessToken, 'signed-token');
  });

  test('rejects a response that does not satisfy registration schema', () async {
    final client = ApiClient(
      baseUri: Uri.parse('https://api.test/api/v1'),
      httpClient: MockClient((_) async => http.Response('{"user":{}}', 201)),
      readSession: () async => null,
      onUnauthorized: () async {},
    );
    final repository = AuthRepository(client, (_) async {});

    await expectLater(
      repository.register(email: 'a@b.test', password: 'password1', displayName: 'A'),
      throwsFormatException,
    );
  });

  test('logs in with the documented body and persists the access token', () async {
    http.Request? sent;
    Session? saved;
    final client = ApiClient(
      baseUri: Uri.parse('https://api.test/api/v1'),
      httpClient: MockClient((request) async {
        sent = request;
        return http.Response(
          jsonEncode({
            'user': {
              'id': 'u-2',
              'email': 'member@example.test',
              'displayName': 'Member',
            },
            'accessToken': 'login-token',
            'expiresIn': 900,
          }),
          200,
        );
      }),
      readSession: () async => null,
      onUnauthorized: () async {},
    );
    final repository = AuthRepository(client, (session) async {
      saved = session;
    });

    final result = await repository.login(
      email: 'member@example.test',
      password: 'exactPass123',
    );

    expect(sent!.method, 'POST');
    expect(sent!.url.toString(), 'https://api.test/api/v1/auth/login');
    expect(sent!.headers['content-type'], 'application/json');
    expect(jsonDecode(sent!.body), {
      'email': 'member@example.test',
      'password': 'exactPass123',
    });
    expect(result.user.id, 'u-2');
    expect(result.expiresIn, 900);
    expect(result.accessToken, 'login-token');
    expect(saved!.accessToken, 'login-token');
  });

  test('rejects a login response outside the authentication success schema', () async {
    final client = ApiClient(
      baseUri: Uri.parse('https://api.test/api/v1'),
      httpClient: MockClient((_) async => http.Response('{"accessToken":"x"}', 200)),
      readSession: () async => null,
      onUnauthorized: () async {},
    );
    final repository = AuthRepository(client, (_) async {});

    await expectLater(
      repository.login(email: 'a@b.test', password: 'password1'),
      throwsFormatException,
    );
  });
}
