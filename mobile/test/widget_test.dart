import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/core/session/auth_state.dart';
import 'package:savemon_mobile/features/home/presentation/home_page.dart';

class _UnauthenticatedState extends AuthStateController {
  @override
  Future<AuthenticationStatus> build() async =>
      AuthenticationStatus.unauthenticated;
}

class _AuthenticatedState extends AuthStateController {
  @override
  Future<AuthenticationStatus> build() async =>
      AuthenticationStatus.authenticated;
}

void main() {
  testWidgets(
      'signed-out Home shows welcome and auth actions, not mock finance',
      (tester) async {
    tester.view.physicalSize = const Size(430, 932);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(ProviderScope(
      overrides: [authStateProvider.overrideWith(_UnauthenticatedState.new)],
      child: const MaterialApp(home: HomePage()),
    ));
    await tester.pumpAndSettle();

    expect(find.text('SaveMon'), findsOneWidget);
    expect(
        find.text(
            'Ch\u{00E0}o m\u{1EEB}ng b\u{1EA1}n \u{0111}\u{1EBF}n v\u{1EDB}i SaveMon'),
        findsOneWidget);
    expect(find.text('\u{0110}\u{0103}ng nh\u{1EAD}p'), findsOneWidget);
    expect(find.text('\u{0110}\u{0103}ng k\u{00FD}'), findsOneWidget);
    expect(find.text('Qu\u{1EF9} chung'), findsOneWidget);
    expect(find.text('8.250.000 \u{20AB}'), findsNothing);
    expect(find.text('Danh m\u{1EE5}c chi ti\u{00EA}u'), findsNothing);
  });

  testWidgets('signed-in Home labels its reference dashboard as demo data',
      (tester) async {
    tester.view.physicalSize = const Size(430, 932);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(ProviderScope(
      overrides: [authStateProvider.overrideWith(_AuthenticatedState.new)],
      child: const MaterialApp(home: HomePage()),
    ));
    await tester.pumpAndSettle();

    expect(find.textContaining('S\u{1ED1} li\u{1EC7}u minh h\u{1ECD}a'),
        findsOneWidget);
    expect(find.text('T\u{1ED5}ng chi th\u{00E1}ng 4/2025'), findsOneWidget);
    expect(find.text('Danh m\u{1EE5}c chi ti\u{00EA}u'), findsOneWidget);
    expect(find.text('Giao d\u{1ECB}ch g\u{1EA7}n \u{0111}\u{00E2}y'),
        findsOneWidget);
    expect(find.text('8.250.000 \u{20AB}'), findsOneWidget);
  });
}
