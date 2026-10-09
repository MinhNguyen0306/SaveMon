import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/app/app.dart';
import 'package:savemon_mobile/core/session/auth_state.dart';

class _UnauthenticatedState extends AuthStateController {
  @override
  Future<AuthenticationStatus> build() async => AuthenticationStatus.unauthenticated;
}

void main() {
  testWidgets('home exposes login and registration flows', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [authStateProvider.overrideWith(_UnauthenticatedState.new)],
      child: const SaveMonApp(),
    ));
    await tester.pumpAndSettle();

    expect(find.text('SaveMon'), findsOneWidget);
    expect(find.text('Create account'), findsOneWidget);
    expect(find.text('Log in'), findsOneWidget);
    await tester.tap(find.text('Create account'));
    await tester.pumpAndSettle();
    expect(find.text('Create your SaveMon account'), findsOneWidget);
    expect(find.text('Display name'), findsOneWidget);
    expect(find.text('Already have an account? Log in'), findsOneWidget);

    await tester.tap(find.text('Already have an account? Log in'));
    await tester.pumpAndSettle();
    expect(find.text('Welcome back'), findsOneWidget);
    expect(find.text('Email'), findsOneWidget);
    expect(find.text('Password'), findsOneWidget);
    await tester.tap(find.text('Log in').last);
    await tester.pump();
    expect(
      find.text('Password must be 8 to 128 characters and not blank.'),
      findsOneWidget,
    );
  });
}
