import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/app/app.dart';
import 'package:savemon_mobile/core/session/auth_state.dart';

class _UnauthenticatedState extends AuthStateController {
  @override
  Future<AuthenticationStatus> build() async =>
      AuthenticationStatus.unauthenticated;
}

void main() {
  testWidgets('home presents reference dashboard and navigation',
      (tester) async {
    tester.view.physicalSize = const Size(430, 932);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(ProviderScope(
        overrides: [authStateProvider.overrideWith(_UnauthenticatedState.new)],
        child: const SaveMonApp()));
    await tester.pumpAndSettle();
    expect(find.text('SaveMon'), findsOneWidget);
    expect(find.text('T\u1ed5ng chi th\u00e1ng 4/2025'), findsOneWidget);
    expect(find.text('Danh m\u1ee5c chi ti\u00eau'), findsOneWidget);
    expect(find.text('Giao d\u1ecbch g\u1ea7n \u0111\u00e2y'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.add));
    await tester.pumpAndSettle();
    expect(find.text('Qu\u00e9t QR h\u00f3a \u0111\u01a1n'), findsOneWidget);
  });

  testWidgets('history, scanner and preview result routes render',
      (tester) async {
    await tester.pumpWidget(ProviderScope(
        overrides: [authStateProvider.overrideWith(_UnauthenticatedState.new)],
        child: const SaveMonApp()));
    await tester.pumpAndSettle();
    await tester.tap(find.text('L\u1ecbch s\u1eed'));
    await tester.pumpAndSettle();
    expect(find.text('L\u1ecbch s\u1eed chi ti\u00eau'), findsOneWidget);
    expect(find.text('Starbucks'), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.add));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.image));
    await tester.pumpAndSettle();
    expect(find.text('K\u1ebft qu\u1ea3 qu\u00e9t'), findsOneWidget);
    expect(find.text('B\u1ea3n xem tr\u01b0\u1edbc'), findsOneWidget);
  });
}
