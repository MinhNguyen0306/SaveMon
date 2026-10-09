import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'session.dart';

enum AuthenticationStatus { authenticated, unauthenticated }

final authStateProvider =
    AsyncNotifierProvider<AuthStateController, AuthenticationStatus>(
  AuthStateController.new,
);

class AuthStateController extends AsyncNotifier<AuthenticationStatus> {
  Session? _session;

  @override
  Future<AuthenticationStatus> build() async {
    _session = await ref.read(sessionRepositoryProvider).read();
    return _statusFor(_session);
  }

  Future<void> setSession(Session session) async {
    await ref.read(sessionRepositoryProvider).save(session);
    _session = session;
    state = const AsyncData(AuthenticationStatus.authenticated);
  }

  Future<Session?> sessionForApiRequest() async {
    await future;
    return _session;
  }

  Future<void> clearSession() async {
    _session = null;
    state = const AsyncData(AuthenticationStatus.unauthenticated);
    await ref.read(sessionRepositoryProvider).clear();
  }

  AuthenticationStatus _statusFor(Session? session) {
    return session == null
        ? AuthenticationStatus.unauthenticated
        : AuthenticationStatus.authenticated;
  }
}
