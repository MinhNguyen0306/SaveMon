import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/core/session/auth_state.dart';
import 'package:savemon_mobile/features/home/presentation/home_page.dart';

class _SignedOut extends AuthStateController {
  @override
  Future<AuthenticationStatus> build() async =>
      AuthenticationStatus.unauthenticated;
}

class _SignedIn extends AuthStateController {
  @override
  Future<AuthenticationStatus> build() async =>
      AuthenticationStatus.authenticated;
}

void main() {
  testWidgets('signed-out home offers authentication and hides demo finance',
      (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [authStateProvider.overrideWith(_SignedOut.new)],
      child: const MaterialApp(home: HomePage()),
    ));
    await tester.pumpAndSettle();

    expect(find.text('Chào mừng bạn đến với SaveMon'), findsOneWidget);
    expect(find.text('Đăng nhập'), findsOneWidget);
    expect(find.text('Đăng ký'), findsOneWidget);
    expect(find.text('Quỹ chung'), findsOneWidget);
    expect(find.text('8.250.000 ₫'), findsNothing);
  });

  testWidgets('signed-in demo finance is explicitly labeled as mock data',
      (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [authStateProvider.overrideWith(_SignedIn.new)],
      child: const MaterialApp(home: HomePage()),
    ));
    await tester.pumpAndSettle();

    expect(find.textContaining('Số liệu minh họa'), findsOneWidget);
    expect(find.text('8.250.000 ₫'), findsOneWidget);
  });
}
