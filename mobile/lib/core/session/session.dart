import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class Session {
  const Session({required this.accessToken, this.subjectId});

  final String accessToken;

  /// In-memory identity supplied by the authenticated API response.
  /// Secure storage intentionally persists only the access token.
  final String? subjectId;
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
  Future<String?> readAccessToken() async {
    try {
      return await _storage.read(key: _accessTokenKey);
    } on PlatformException catch (error) {
      if (!_isUnreadableCiphertext(error)) rethrow;

      // A token encrypted with an invalidated/lost Android key cannot be
      // recovered. Remove only this auth key; never clear the app's offline DB.
      try {
        await _storage.delete(key: _accessTokenKey);
      } on PlatformException {
        // Fail closed for this launch even if cleanup itself is unavailable.
      }
      return null;
    }
  }

  bool _isUnreadableCiphertext(PlatformException error) {
    final details = '${error.message ?? ''} ${error.details ?? ''}';
    return details.contains('IllegalBlockSizeException') ||
        details.contains('WRONG_FINAL_BLOCK_LENGTH') ||
        details.contains('BadPaddingException');
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
