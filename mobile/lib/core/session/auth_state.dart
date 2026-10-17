import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'session.dart';

enum AuthenticationStatus { authenticated, unauthenticated }

final authStateProvider =
    AsyncNotifierProvider<AuthStateController, AuthenticationStatus>(
  AuthStateController.new,
);

/// In-memory fence for subject-owned UI/provider state. Token rotation for the
/// same subject does not change this value; logout or subject changes do.
final authSubjectEpochProvider = StateProvider<int>((ref) => 0);

class AuthStateController extends AsyncNotifier<AuthenticationStatus> {
  Session? _session;
  String? _subjectId;

  @override
  Future<AuthenticationStatus> build() async {
    _session = await ref.read(sessionRepositoryProvider).read();
    _subjectId = _session?.subjectId;
    return _statusFor(_session);
  }

  Future<void> setSession(Session session) async {
    await ref.read(sessionRepositoryProvider).save(session);
    final nextSubjectId = session.subjectId;
    if (nextSubjectId != null && nextSubjectId != _subjectId) {
      _subjectId = nextSubjectId;
      _advanceSubjectEpoch();
    }
    _session = session;
    state = const AsyncData(AuthenticationStatus.authenticated);
  }

  Future<Session?> sessionForApiRequest() async {
    await future;
    return _session;
  }

  Future<void> clearSession() async {
    if (_session != null || _subjectId != null) {
      _advanceSubjectEpoch();
    }
    _session = null;
    _subjectId = null;
    state = const AsyncData(AuthenticationStatus.unauthenticated);
    await ref.read(sessionRepositoryProvider).clear();
  }

  AuthenticationStatus _statusFor(Session? session) {
    return session == null
        ? AuthenticationStatus.unauthenticated
        : AuthenticationStatus.authenticated;
  }

  void _advanceSubjectEpoch() {
    ref.read(authSubjectEpochProvider.notifier).state++;
  }
}
