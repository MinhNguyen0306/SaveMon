import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/core/session/session.dart';

void main() {
  group('SecureSessionRepository', () {
    test(
      'reads and writes the session access token through secure storage',
      () async {
        final storage = FakeAccessTokenStorage();
        final repository = SecureSessionRepository(tokenStorage: storage);
        const session = Session(accessToken: 'test-access-token');

        await repository.save(session);

        expect(storage.accessToken, 'test-access-token');
        expect(
          (await repository.read())?.accessToken,
          session.accessToken,
        );
      },
    );

    test('clears the access token through secure storage', () async {
      final storage = FakeAccessTokenStorage()
        ..accessToken = 'test-access-token';
      final repository = SecureSessionRepository(tokenStorage: storage);

      await repository.clear();

      expect(storage.accessToken, isNull);
      expect(await repository.read(), isNull);
    });
  });
}

class FakeAccessTokenStorage implements AccessTokenStorage {
  String? accessToken;

  @override
  Future<String?> readAccessToken() async => accessToken;

  @override
  Future<void> writeAccessToken(String value) async {
    accessToken = value;
  }

  @override
  Future<void> clearAccessToken() async {
    accessToken = null;
  }
}
