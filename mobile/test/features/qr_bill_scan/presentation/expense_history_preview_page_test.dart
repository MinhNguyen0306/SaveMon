import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/features/qr_bill_scan/data/expense_scan_repository.dart';
import 'package:savemon_mobile/features/qr_bill_scan/data/providers.dart';
import 'package:savemon_mobile/features/qr_bill_scan/presentation/expense_scan_page.dart';
import 'package:savemon_mobile/features/qr_bill_scan/presentation/scan_result_page.dart';

void main() {
  testWidgets('receipt guidance fits compact viewport without overflow',
      (tester) async {
    tester.view.physicalSize = const Size(320, 568);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = ExpenseScanRepository(ApiClient(
      baseUri: Uri.parse('https://api.example.test/api/v1/'),
      httpClient: MockClient((_) async => http.Response('', 503)),
      readSession: () async => null,
      onUnauthorized: () async {},
    ));
    await tester.pumpWidget(ProviderScope(
      overrides: [expenseScanRepositoryProvider.overrideWithValue(repository)],
      child: const MaterialApp(home: ExpenseScanPage()),
    ));
    await tester.pumpAndSettle();
    expect(find.text('Chụp rõ toàn bộ hóa đơn'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.drag(find.byType(ListView), const Offset(0, -180));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'result route without volatile draft shows recovery, not fixture data',
      (tester) async {
    final repository = ExpenseScanRepository(ApiClient(
      baseUri: Uri.parse('https://api.example.test/api/v1/'),
      httpClient: MockClient((_) async => http.Response('', 503)),
      readSession: () async => null,
      onUnauthorized: () async {},
    ));
    await tester.pumpWidget(ProviderScope(
      overrides: [expenseScanRepositoryProvider.overrideWithValue(repository)],
      child: const MaterialApp(home: ScanResultPage()),
    ));
    expect(find.textContaining('Không còn bản nháp'), findsOneWidget);
    expect(find.text('Starbucks'), findsNothing);
    expect(find.text('120.000'), findsNothing);
  });
}
