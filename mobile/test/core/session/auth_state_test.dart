import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/core/session/auth_state.dart';
import 'package:savemon_mobile/core/session/session.dart';

void main() {
  group('AuthStateController', () {
    test('restores an unauthenticated state when storage is empty', () async {
      final repository = FakeSessionRepository();
      final container = ProviderContainer(
        overrides: [
          sessionRepositoryProvider.overrideWithValue(repository),
        ],
      );
      addTearDown(container.dispose);

      final state = await container.read(authStateProvider.future);

      expect(state, AuthenticationStatus.unauthenticated);
      expect(
        container.read(authStateProvider).value,
        AuthenticationStatus.unauthenticated,
      );
    });

    test('persists a session before publishing authenticated state', () async {
      final repository = FakeSessionRepository();
      final container = ProviderContainer(
        overrides: [
          sessionRepositoryProvider.overrideWithValue(repository),
        ],
      );
      addTearDown(container.dispose);
      await container.read(authStateProvider.future);

      const session = Session(accessToken: 'test-access-token');
      await container.read(authStateProvider.notifier).setSession(session);

      expect(repository.session, session);
      expect(
        container.read(authStateProvider).value,
        AuthenticationStatus.authenticated,
      );
      expect(
        await container.read(authStateProvider.notifier).sessionForApiRequest(),
        session,
      );
    });

    test(
      'transitions to unauthenticated and clears the stored session',
      () async {
        final repository = FakeSessionRepository()
          ..session = const Session(accessToken: 'test-access-token');
        final container = ProviderContainer(
          overrides: [
            sessionRepositoryProvider.overrideWithValue(repository),
          ],
        );
        addTearDown(container.dispose);
        await container.read(authStateProvider.future);

        await container.read(authStateProvider.notifier).clearSession();

        expect(repository.session, isNull);
        expect(
          container.read(authStateProvider).value,
          AuthenticationStatus.unauthenticated,
        );
        expect(
          await container
              .read(authStateProvider.notifier)
              .sessionForApiRequest(),
          isNull,
        );
      },
    );

    test(
      '401 from an authenticated request clears the application auth state',
      () async {
        final repository = FakeSessionRepository()
          ..session = const Session(accessToken: 'test-access-token');
        final container = ProviderContainer(
          overrides: [
            sessionRepositoryProvider.overrideWithValue(repository),
          ],
        );
        addTearDown(container.dispose);
        await container.read(authStateProvider.future);
        final client = ApiClient(
          baseUri: Uri.parse('https://api.example.test/api/v1/'),
          httpClient: MockClient((_) async => http.Response('', 401)),
          readSession: () =>
              container.read(authStateProvider.notifier).sessionForApiRequest(),
          onUnauthorized: () =>
              container.read(authStateProvider.notifier).clearSession(),
        );

        await expectLater(
          client.send(method: 'GET', path: 'me'),
          throwsA(isA<ApiException>()),
        );

        expect(repository.session, isNull);
        expect(
          container.read(authStateProvider).value,
          AuthenticationStatus.unauthenticated,
        );
      },
    );
  });
}

class FakeSessionRepository implements SessionRepository {
  Session? session;

  @override
  Future<Session?> read() async => session;

  @override
  Future<void> save(Session value) async {
    session = value;
  }

  @override
  Future<void> clear() async {
    session = null;
  }
}
