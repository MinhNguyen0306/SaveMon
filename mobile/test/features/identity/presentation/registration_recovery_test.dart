import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/features/identity/data/auth_repository.dart';
import 'package:savemon_mobile/features/identity/presentation/login_page.dart';
import 'package:savemon_mobile/features/identity/presentation/registration_page.dart';

void main() {
  testWidgets('token issuer failure tells user to recover by logging in', (
    tester,
  ) async {
    final api = ApiClient(
      baseUri: Uri.parse('https://api.example.test/api/v1/'),
      httpClient: MockClient(
        (_) async => http.Response(
          '{"code":"TOKEN_ISSUER_UNAVAILABLE",'
          '"message":"Token issuer unavailable."}',
          503,
        ),
      ),
      readSession: () async => null,
      onUnauthorized: () async {},
    );
    final router = GoRouter(
      initialLocation: '/register',
      routes: [
        GoRoute(
            path: '/register',
            builder: (context, state) => const RegistrationPage()),
        GoRoute(path: '/login', builder: (context, state) => const LoginPage()),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authRepositoryProvider.overrideWithValue(
            AuthRepository(api, (_) async {}),
          ),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.enterText(find.byType(TextField).at(0), 'Person');
    await tester.enterText(find.byType(TextField).at(1), 'person@example.com');
    await tester.enterText(find.byType(TextField).at(2), 'a-password-123');
    await tester.enterText(find.byType(TextField).at(3), 'a-password-123');
    await tester
        .ensureVisible(find.widgetWithText(FilledButton, 'Tạo tài khoản'));
    await tester.tap(find.widgetWithText(FilledButton, 'Tạo tài khoản'));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('Tài khoản có thể đã được tạo'),
      findsOneWidget,
    );
    expect(find.text('Thử đăng nhập'), findsOneWidget);
    await tester.tap(find.text('Thử đăng nhập'));
    await tester.pumpAndSettle();
    expect(find.byType(LoginPage), findsOneWidget);
  });
}
