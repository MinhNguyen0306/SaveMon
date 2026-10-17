import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/app/app.dart';
import 'package:savemon_mobile/app/router/app_router.dart';
import 'package:savemon_mobile/core/session/auth_state.dart';

class _SignedInState extends AuthStateController {
  @override
  Future<AuthenticationStatus> build() async =>
      AuthenticationStatus.authenticated;
}

class _SignedOutState extends AuthStateController {
  @override
  Future<AuthenticationStatus> build() async =>
      AuthenticationStatus.unauthenticated;
}

void main() {
  testWidgets('signed-in shell exposes five ordered navigation destinations',
      (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [authStateProvider.overrideWith(_SignedInState.new)],
      child: const SaveMonApp(),
    ));
    await tester.pumpAndSettle();

    final bar = tester.widget<NavigationBar>(find.byType(NavigationBar));
    expect(bar.destinations.cast<NavigationDestination>().map((d) => d.label), [
      'T\u{1ED5}ng quan',
      'Giao d\u{1ECB}ch',
      'T\u{00E0}i kho\u{1EA3}n',
      'Qu\u{1EF9} chung',
      'H\u{1ED3} s\u{01A1}',
    ]);
    expect(bar.selectedIndex, 0);

    await tester.tap(find.text('Giao d\u{1ECB}ch'));
    await tester.pumpAndSettle();
    expect(find.text('Transaction history'), findsOneWidget);
    expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        1);
    await tester.tap(find.text('T\u{00E0}i kho\u{1EA3}n'));
    await tester.pumpAndSettle();
    expect(find.text('Accounts'), findsOneWidget);
    expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        2);
  });

  testWidgets('signed-out overview restores auth actions and hides navigation',
      (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [authStateProvider.overrideWith(_SignedOutState.new)],
      child: const SaveMonApp(),
    ));
    await tester.pumpAndSettle();
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.text('Ch\u{00E0}o m\u{1EEB}ng b\u{1EA1}n'), findsOneWidget);
    expect(find.text('8.250.000 \u{20AB}'), findsNothing);
    await tester.tap(find.text('\u{0110}\u{0103}ng nh\u{1EAD}p'));
    await tester.pumpAndSettle();
    expect(find.text('Email'), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);
  });

  testWidgets('direct detail links keep the owning shell branch and Back list',
      (tester) async {
    final container = ProviderContainer(
      overrides: [authStateProvider.overrideWith(_SignedInState.new)],
    );
    addTearDown(container.dispose);
    final router = container.read(appRouterProvider);
    await tester.pumpWidget(UncontrolledProviderScope(
      container: container,
      child: MaterialApp.router(routerConfig: router),
    ));
    await tester.pumpAndSettle();

    router.go('/personal-finance/accounts');
    await tester.pumpAndSettle();
    expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        2);
    router.go('/personal-finance/transactions/example-id');
    await tester.pumpAndSettle();
    expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        1);
    router.pop();
    await tester.pumpAndSettle();
    expect(find.text('Transaction history'), findsOneWidget);
  });

  testWidgets('scan and result are contextual full-screen routes with Back',
      (tester) async {
    final container = ProviderContainer(
      overrides: [authStateProvider.overrideWith(_SignedInState.new)],
    );
    addTearDown(container.dispose);
    final router = container.read(appRouterProvider);
    await tester.pumpWidget(UncontrolledProviderScope(
      container: container,
      child: MaterialApp.router(routerConfig: router),
    ));
    await tester.pumpAndSettle();
    router.push('/expense-scan');
    await tester.pumpAndSettle();
    expect(find.byType(NavigationBar), findsNothing);
    router.push('/expense-scan/result');
    await tester.pumpAndSettle();
    expect(find.text('K\u{1EBF}t qu\u{1EA3} qu\u{E9}t'), findsOneWidget);
    router.pop();
    await tester.pumpAndSettle();
    expect(find.text('Qu\u{00E9}t QR h\u{00F3}a \u{0111}\u{01A1}n'),
        findsOneWidget);
    router.pop();
    await tester.pumpAndSettle();
    expect(find.text('B\u{1EA3}n xem tr\u{01B0}\u{1EDB}c'), findsOneWidget);
    expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        0);
  });
}
