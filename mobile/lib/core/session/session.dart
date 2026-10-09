import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class Session {
  const Session({required this.accessToken});

  final String accessToken;
}

abstract interface class SessionRepository {
  Future<Session?> read();

  Future<void> save(Session session);

  Future<void> clear();
}

abstract interface class AccessTokenStorage {
  Future<String?> readAccessToken();

  Future<void> writeAccessToken(String accessToken);

  Future<void> clearAccessToken();
}

class SecureSessionRepository implements SessionRepository {
  const SecureSessionRepository({required AccessTokenStorage tokenStorage})
      : _tokenStorage = tokenStorage;

  final AccessTokenStorage _tokenStorage;

  @override
  Future<Session?> read() async {
    final accessToken = await _tokenStorage.readAccessToken();
    if (accessToken == null) {
      return null;
    }
    return Session(accessToken: accessToken);
  }

  @override
  Future<void> save(Session session) {
    return _tokenStorage.writeAccessToken(session.accessToken);
  }

  @override
  Future<void> clear() {
    return _tokenStorage.clearAccessToken();
  }
}

class FlutterSecureAccessTokenStorage implements AccessTokenStorage {
  FlutterSecureAccessTokenStorage(this._storage);

  static const _accessTokenKey = 'access_token';

  final FlutterSecureStorage _storage;

  @override
  Future<String?> readAccessToken() {
    return _storage.read(key: _accessTokenKey);
  }

  @override
  Future<void> writeAccessToken(String accessToken) {
    return _storage.write(key: _accessTokenKey, value: accessToken);
  }

  @override
  Future<void> clearAccessToken() {
    return _storage.delete(key: _accessTokenKey);
  }
}

final sessionRepositoryProvider = Provider<SessionRepository>((ref) {
  return SecureSessionRepository(
    tokenStorage: FlutterSecureAccessTokenStorage(
      const FlutterSecureStorage(),
    ),
  );
});
