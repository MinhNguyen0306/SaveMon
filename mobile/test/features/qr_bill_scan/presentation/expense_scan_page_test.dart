import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/features/qr_bill_scan/data/expense_scan_repository.dart';
import 'package:savemon_mobile/features/qr_bill_scan/data/providers.dart';
import 'package:savemon_mobile/features/qr_bill_scan/data/scan_image_source.dart';
import 'package:savemon_mobile/features/qr_bill_scan/presentation/expense_scan_page.dart';
import 'package:savemon_mobile/features/personal_finance/application/receipt_expense_handoff.dart';
import 'package:savemon_mobile/features/personal_finance/presentation/receipt_expense_handoff_page.dart';

class _TestImageSource implements ScanImageSource {
  @override
  Future<Uint8List?> selectImage() async =>
      Uint8List.fromList([0xff, 0xd8, 0xff, 0x00]);
}

void main() {
  testWidgets(
      'unavailable image source offers manual recovery without fake success',
      (tester) async {
    final api = ApiClient(
      baseUri: Uri.parse('https://api.example.test/api/v1/'),
      httpClient: MockClient((_) async => http.Response('', 500)),
      readSession: () async => null,
      onUnauthorized: () async {},
    );
    await tester.pumpWidget(ProviderScope(
      overrides: [
        expenseScanRepositoryProvider
            .overrideWithValue(ExpenseScanRepository(api)),
      ],
      child: const MaterialApp(home: ExpenseScanPage()),
    ));
    await tester.tap(find.text('Chụp hoặc chọn hóa đơn'));
    await tester.pumpAndSettle();

    expect(find.text('Chức năng chọn ảnh hoặc OCR hiện chưa khả dụng.'),
        findsOneWidget);
    expect(find.text('Nhập thủ công'), findsWidgets);
    expect(find.text('Starbucks'), findsNothing);
    expect(find.text('120.000'), findsNothing);
  });

  testWidgets(
      'shows editable volatile review and does not submit finance command',
      (tester) async {
    var requests = 0;
    final api = ApiClient(
      baseUri: Uri.parse('https://api.example.test/api/v1/'),
      httpClient: MockClient((request) async {
        requests++;
        expect(request.url.path, '/api/v1/expense-scans');
        return http.Response(
          '{"outcome":"DRAFT","manualEntryAllowed":true,"draft":{'
          '"paidTotal":"125.00","currency":null,'
          '"transactionDate":"2026-10-08","merchant":"Cafe Demo",'
          '"missingOrAmbiguousFields":["currency"]}}',
          200,
        );
      }),
      readSession: () async => null,
      onUnauthorized: () async {},
    );
    await tester.pumpWidget(ProviderScope(
      overrides: [
        scanImageSourceProvider.overrideWithValue(_TestImageSource()),
        expenseScanRepositoryProvider.overrideWithValue(
          ExpenseScanRepository(api, createIdempotencyKey: () => 'scan-key'),
        ),
      ],
      child: const MaterialApp(home: ExpenseScanPage()),
    ));

    await tester.tap(find.text('Chụp hoặc chọn hóa đơn'));
    await tester.pumpAndSettle();
    expect(find.text('Kiểm tra thông tin đề xuất'), findsOneWidget);
    expect(
        find.text(
            'Thiếu hoặc chưa rõ một số trường. Hãy tự kiểm tra, không dùng giá trị chưa xác minh.'),
        findsOneWidget);
    expect(
        tester
            .widget<TextFormField>(find.byKey(const ValueKey('scan-merchant')))
            .initialValue,
        'Cafe Demo');
    expect(find.textContaining('Chưa có thao tác xác nhận chi tiêu'),
        findsOneWidget);

    await tester.enterText(
        find.byKey(const ValueKey('scan-merchant')), 'Cafe Edited');
    expect(requests, 1);
    expect(find.text('Lưu chi tiêu'), findsNothing);
  });

  testWidgets('payment QR outcome is retained and gets fail-closed guidance',
      (tester) async {
    final api = ApiClient(
      baseUri: Uri.parse('https://api.example.test/api/v1/'),
      httpClient: MockClient((_) async => http.Response(
            '{"outcome":"UNSUPPORTED","manualEntryAllowed":true,'
            '"draft":null,"reasonCode":"PAYMENT_QR"}',
            200,
          )),
      readSession: () async => null,
      onUnauthorized: () async {},
    );
    await tester.pumpWidget(ProviderScope(
      overrides: [
        scanImageSourceProvider.overrideWithValue(_TestImageSource()),
        expenseScanRepositoryProvider.overrideWithValue(
          ExpenseScanRepository(api, createIdempotencyKey: () => 'qr-key'),
        ),
      ],
      child: const MaterialApp(home: ExpenseScanPage()),
    ));

    await tester.tap(find.text('Chụp hoặc chọn hóa đơn'));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('Ảnh có mã QR thanh toán. Ảnh bị từ chối'),
      findsOneWidget,
    );
    expect(find.textContaining('không được gửi qua OCR'), findsOneWidget);
    expect(find.text('Nhập thủ công'), findsWidgets);
    expect(find.text('Kiểm tra thông tin đề xuất'), findsNothing);
    expect(find.text('Lưu chi tiêu'), findsNothing);
  });
  testWidgets(
      'reviewed fields hand off to finance form without selecting a context',
      (tester) async {
    final api = ApiClient(
      baseUri: Uri.parse('https://api.example.test/api/v1/'),
      httpClient: MockClient((_) async => http.Response(
          '{"outcome":"DRAFT","manualEntryAllowed":true,"draft":{'
          '"paidTotal":"125.00","currency":"USD",'
          '"transactionDate":"2026-10-08","merchant":"Cafe Demo",'
          '"missingOrAmbiguousFields":[]}}',
          200)),
      readSession: () async => null,
      onUnauthorized: () async {},
    );
    final router = GoRouter(
      initialLocation: '/expense-scan',
      routes: [
        GoRoute(
          path: '/expense-scan',
          builder: (_, __) => const ExpenseScanPage(),
          routes: [
            GoRoute(
              path: 'confirm',
              builder: (_, state) => ReceiptExpenseHandoffPage(
                prefill: state.extra is ReceiptExpensePrefill
                    ? state.extra! as ReceiptExpensePrefill
                    : null,
              ),
            )
          ],
        )
      ],
    );
    addTearDown(router.dispose);
    await tester.pumpWidget(ProviderScope(
      overrides: [
        scanImageSourceProvider.overrideWithValue(_TestImageSource()),
        expenseScanRepositoryProvider
            .overrideWithValue(ExpenseScanRepository(api)),
      ],
      child: MaterialApp.router(routerConfig: router),
    ));
    await tester.tap(find.byIcon(Icons.photo_camera_outlined));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('scan-total')), findsOneWidget);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('continue-to-finance-form')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.byKey(const ValueKey('continue-to-finance-form')));
    await tester.pumpAndSettle();

    expect(find.byType(ReceiptExpenseHandoffPage), findsOneWidget);
    expect(find.text('125.00'), findsOneWidget);
    expect(find.text('USD'), findsOneWidget);
    expect(find.text('2026-10-08'), findsOneWidget);
    expect(
        tester
            .widget<ChoiceChip>(find.byKey(const ValueKey('context-personal')))
            .selected,
        isFalse);
    expect(
        tester
            .widget<ChoiceChip>(find.byKey(const ValueKey('context-shared')))
            .selected,
        isFalse);
  });
}
