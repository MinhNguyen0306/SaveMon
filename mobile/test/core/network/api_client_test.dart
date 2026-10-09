import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/core/session/session.dart';

void main() {
  group('ApiClient', () {
    test('sends a request relative to the configured API base URL', () async {
      Uri? requestedUri;
      final client = ApiClient(
        baseUri: Uri.parse('https://api.example.test/api/v1'),
        httpClient: MockClient((request) async {
          requestedUri = request.url;
          expect(request.method, 'GET');
          return http.Response('{"ok":true}', 200);
        }),
        readSession: () async => null,
        onUnauthorized: () async {},
      );

      final response = await client.send(method: 'GET', path: 'accounts');

      expect(
        requestedUri.toString(),
        'https://api.example.test/api/v1/accounts',
      );
      expect(response.body, '{"ok":true}');
    });

    test('surfaces unsuccessful HTTP status and response body', () async {
      final client = ApiClient(
        baseUri: Uri.parse('https://api.example.test/api/v1/'),
        httpClient: MockClient(
          (_) async => http.Response('error payload', 503),
        ),
        readSession: () async => null,
        onUnauthorized: () async {},
      );

      await expectLater(
        client.send(method: 'GET', path: 'resource'),
        throwsA(
          isA<ApiException>()
              .having((error) => error.statusCode, 'statusCode', 503)
              .having(
                (error) => error.responseBody,
                'responseBody',
                'error payload',
              ),
        ),
      );
    });

    test('sends a stored access token as a bearer token', () async {
      final client = ApiClient(
        baseUri: Uri.parse('https://api.example.test/api/v1/'),
        httpClient: MockClient((request) async {
          expect(
            request.headers['authorization'],
            'Bearer test-access-token',
          );
          return http.Response('', 200);
        }),
        readSession: () async =>
            const Session(accessToken: 'test-access-token'),
        onUnauthorized: () async {},
      );

      await client.send(method: 'GET', path: 'me');
    });

    test(
      'clears an authenticated session after 401 without retrying',
      () async {
        var requestCount = 0;
        var unauthorizedCount = 0;
        final client = ApiClient(
          baseUri: Uri.parse('https://api.example.test/api/v1/'),
          httpClient: MockClient((request) async {
            requestCount++;
            expect(
              request.headers['authorization'],
              'Bearer test-access-token',
            );
            return http.Response('', 401);
          }),
          readSession: () async =>
              const Session(accessToken: 'test-access-token'),
          onUnauthorized: () async {
            unauthorizedCount++;
          },
        );

        await expectLater(
          client.send(method: 'GET', path: 'me'),
          throwsA(
            isA<ApiException>().having(
              (error) => error.statusCode,
              'statusCode',
              401,
            ),
          ),
        );

        expect(requestCount, 1);
        expect(unauthorizedCount, 1);
      },
    );

    test('does not clear a session for an unauthenticated 401', () async {
      var unauthorizedCount = 0;
      final client = ApiClient(
        baseUri: Uri.parse('https://api.example.test/api/v1/'),
        httpClient: MockClient((_) async => http.Response('', 401)),
        readSession: () async => null,
        onUnauthorized: () async {
          unauthorizedCount++;
        },
      );

      await expectLater(
        client.send(method: 'GET', path: 'public-resource'),
        throwsA(isA<ApiException>()),
      );

      expect(unauthorizedCount, 0);
    });

    test('rejects paths that could bypass the API base path', () async {
      final client = ApiClient(
        baseUri: Uri.parse('https://api.example.test/api/v1/'),
        httpClient: MockClient((_) async => http.Response('', 200)),
        readSession: () async => null,
        onUnauthorized: () async {},
      );

      await expectLater(
        client.send(method: 'GET', path: '/accounts'),
        throwsArgumentError,
      );
      await expectLater(
        client.send(method: 'GET', path: 'https://other.example.test/accounts'),
        throwsArgumentError,
      );
      await expectLater(
        client.send(method: 'GET', path: '../accounts'),
        throwsArgumentError,
      );
    });
  });
}
