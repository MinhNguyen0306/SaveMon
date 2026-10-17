import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/features/identity/data/auth_repository.dart';
import 'package:savemon_mobile/features/identity/presentation/login_page.dart';
import 'package:savemon_mobile/features/identity/presentation/registration_page.dart';

void main() {
  final api = ApiClient(
    baseUri: Uri.parse('https://api.example.test/api/v1/'),
    httpClient: MockClient((_) async => http.Response('{}', 500)),
    readSession: () async => null,
    onUnauthorized: () async {},
  );

  testWidgets('login follows the Vietnamese SaveMon auth visual hierarchy', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authRepositoryProvider.overrideWithValue(
            AuthRepository(api, (_) async {}),
          ),
        ],
        child: const MaterialApp(home: LoginPage()),
      ),
    );

    expect(find.text('SaveMon'), findsOneWidget);
    expect(find.text('Chào mừng trở lại 👋'), findsOneWidget);
    expect(find.text('Email của bạn'), findsOneWidget);
    expect(find.text('Mật khẩu'), findsOneWidget);
    expect(find.text('Đăng nhập'), findsOneWidget);
    expect(find.text('Tạo tài khoản'), findsOneWidget);
    expect(find.textContaining('Google'), findsNothing);
    expect(find.textContaining('Quên mật khẩu'), findsNothing);
  });

  testWidgets('login allows existing passwords below signup minimum', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authRepositoryProvider.overrideWithValue(
            AuthRepository(api, (_) async {}),
          ),
        ],
        child: const MaterialApp(home: LoginPage()),
      ),
    );
    await tester.enterText(find.byType(TextField).at(0), 'person@example.com');
    await tester.enterText(find.byType(TextField).at(1), '12345678');
    await tester.tap(find.text('Đăng nhập'));
    await tester.pumpAndSettle();

    expect(find.text('Vui lòng nhập mật khẩu.'), findsNothing);
    expect(find.textContaining('Chưa thể đăng nhập'), findsOneWidget);
  });

  testWidgets('signup navigation is disabled while login is submitting', (
    tester,
  ) async {
    final response = Completer<http.Response>();
    final pendingApi = ApiClient(
      baseUri: Uri.parse('https://api.example.test/api/v1/'),
      httpClient: MockClient((_) => response.future),
      readSession: () async => null,
      onUnauthorized: () async {},
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authRepositoryProvider.overrideWithValue(
            AuthRepository(pendingApi, (_) async {}),
          ),
        ],
        child: const MaterialApp(home: LoginPage()),
      ),
    );
    await tester.enterText(find.byType(TextField).at(0), 'person@example.com');
    await tester.enterText(find.byType(TextField).at(1), '12345678');
    await tester.tap(find.text('Đăng nhập'));
    await tester.pump();

    final signupButton = tester.widget<TextButton>(
      find.ancestor(
        of: find.text('Đăng ký'),
        matching: find.byType(TextButton),
      ),
    );
    expect(signupButton.onPressed, isNull);

    response.complete(http.Response('{}', 500));
    await tester.pumpAndSettle();
  });

  testWidgets('signup provides matching-password field and approved copy', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authRepositoryProvider.overrideWithValue(
            AuthRepository(api, (_) async {}),
          ),
        ],
        child: const MaterialApp(home: RegistrationPage()),
      ),
    );

    expect(find.text('Tạo tài khoản mới 🌱'), findsOneWidget);
    expect(find.text('Họ và tên'), findsOneWidget);
    expect(find.text('Email'), findsOneWidget);
    expect(find.text('Xác nhận mật khẩu'), findsOneWidget);
    expect(find.textContaining('Ít nhất 12 ký tự'), findsOneWidget);
    expect(find.textContaining('Điều khoản'), findsNothing);
  });
}
