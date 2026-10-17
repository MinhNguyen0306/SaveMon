import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/core/session/auth_state.dart';
import 'package:savemon_mobile/core/session/session.dart';
import 'package:savemon_mobile/features/identity/data/profile_repository.dart';
import 'package:savemon_mobile/features/personal_finance/presentation/personal_finance_page.dart';

class _FakeSessionRepository implements SessionRepository {
  Session? session;

  @override
  Future<Session?> read() async => session;

  @override
  Future<void> save(Session value) async => session = value;

  @override
  Future<void> clear() async => session = null;
}

class _SubjectProfileRepository extends ProfileRepository {
  _SubjectProfileRepository(this.subjectId)
      : super(
          ApiClient(
            baseUri: Uri.parse('https://example.test/api/'),
            httpClient: http.Client(),
            readSession: () async => null,
            onUnauthorized: () async {},
          ),
        );

  String subjectId;
  int loadCount = 0;

  @override
  Future<UserProfile> getCurrentUser() async {
    loadCount++;
    return UserProfile(
      id: subjectId,
      email: '$subjectId@example.test',
      displayName: subjectId,
    );
  }
}

void main() {
  test(
      'A → signed out → B fences profile and finance state; same-user token '
      'rotation retains epoch', () async {
    final repository = _FakeSessionRepository();
    final profiles = _SubjectProfileRepository('subject-A');
    final container = ProviderContainer(overrides: [
      sessionRepositoryProvider.overrideWithValue(repository),
      profileRepositoryProvider.overrideWithValue(profiles),
    ]);
    addTearDown(container.dispose);
    await container.read(authStateProvider.future);

    final auth = container.read(authStateProvider.notifier);
    await auth.setSession(
      const Session(accessToken: 'token-A', subjectId: 'subject-A'),
    );
    final subjectAEpoch = container.read(authSubjectEpochProvider);
    container.read(personalCurrencyProvider.notifier).state = 'VND';
    expect((await container.read(currentUserProfileProvider.future)).id,
        'subject-A');

    await auth.setSession(
      const Session(accessToken: 'rotated-token-A', subjectId: 'subject-A'),
    );
    expect(container.read(authSubjectEpochProvider), subjectAEpoch);
    expect(container.read(personalCurrencyProvider), 'VND');

    await auth.clearSession();
    expect(container.read(personalCurrencyProvider), isEmpty);
    expect(container.read(authSubjectEpochProvider), subjectAEpoch + 1);

    profiles.subjectId = 'subject-B';
    await auth.setSession(
      const Session(accessToken: 'token-B', subjectId: 'subject-B'),
    );
    expect(container.read(authSubjectEpochProvider), subjectAEpoch + 2);
    expect(container.read(personalCurrencyProvider), isEmpty);
    expect((await container.read(currentUserProfileProvider.future)).id,
        'subject-B');
  });
}
