import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:savemon_mobile/features/qr_bill_scan/presentation/expense_history_preview_page.dart';
import 'package:savemon_mobile/features/qr_bill_scan/presentation/expense_scan_page.dart';

void main() {
  testWidgets('scanner preview scrolls on compact viewport without overflow',
      (tester) async {
    tester.view.physicalSize = const Size(320, 568);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(const MaterialApp(home: ExpenseScanPage()));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('Qu\u{E9}t QR h\u{F3}a \u{111}\u{1A1}n'), findsOneWidget);
    await tester.drag(
        find.byType(SingleChildScrollView), const Offset(0, -200));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'history \u{2192} scanner \u{2192} result preview navigation works',
      (tester) async {
    final router = GoRouter(initialLocation: '/expense-history', routes: [
      GoRoute(
          path: '/', builder: (_, __) => const Scaffold(body: Text('Home'))),
      GoRoute(
          path: '/expense-history',
          builder: (_, __) => const ExpenseHistoryPage()),
      GoRoute(
          path: '/expense-scan', builder: (_, __) => const ExpenseScanPage()),
      GoRoute(
          path: '/expense-scan/result',
          builder: (_, __) => const ScanResultPage()),
    ]);
    addTearDown(router.dispose);
    await tester.pumpWidget(
        ProviderScope(child: MaterialApp.router(routerConfig: router)));
    await tester.pumpAndSettle();
    expect(find.text('Starbucks'), findsOneWidget);
    await tester.tap(find.text('Qu\u{E9}t QR h\u{F3}a \u{111}\u{1A1}n'));
    await tester.pumpAndSettle();
    expect(find.text('Qu\u{E9}t QR h\u{F3}a \u{111}\u{1A1}n'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.image));
    await tester.pumpAndSettle();
    expect(find.text('B\u{1EA3}n xem tr\u{1B0}\u{1EDB}c'), findsOneWidget);
    expect(find.text('Nh\u{E0} cung c\u{1EA5}p'), findsOneWidget);
    expect(find.text('T\u{1ED5}ng ti\u{1EC1}n'), findsOneWidget);
    expect(find.text('Thu\u{1EBF} VAT (8%)'), findsOneWidget);
    await tester.drag(find.byType(ListView).last, const Offset(0, -700));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('L\u{1B0}u chi ti\u{EA}u'));
    await tester.pumpAndSettle();
    expect(find.text('L\u{1B0}u chi ti\u{EA}u'), findsOneWidget);
    await tester.tap(find.text('L\u{1B0}u chi ti\u{EA}u'));
    await tester.pump();
    expect(
        find.text(
            'B\u{1EA3}n xem tr\u{1B0}\u{1EDB}c, giao d\u{1ECB}ch ch\u{1B0}a \u{111}\u{1B0}\u{1EE3}c l\u{1B0}u.'),
        findsOneWidget);
  });
}
