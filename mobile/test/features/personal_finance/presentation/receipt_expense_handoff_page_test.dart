import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/features/personal_finance/application/receipt_expense_handoff.dart';
import 'package:savemon_mobile/features/personal_finance/presentation/receipt_expense_handoff_page.dart';

class _CapturePort implements ReceiptExpenseCommandPort {
  final personal = <PersonalReceiptExpenseIntent>[];
  final shared = <SharedReceiptExpenseIntent>[];
  bool failFirst = false;

  @override
  Future<void> recordPersonal(PersonalReceiptExpenseIntent intent) async {
    personal.add(intent);
    if (failFirst && personal.length == 1) throw Exception('unknown result');
  }

  @override
  Future<void> recordShared(SharedReceiptExpenseIntent intent) async {
    shared.add(intent);
  }
}

const _prefill = ReceiptExpensePrefill(
  amount: '125.00',
  currency: 'USD',
  civilDate: '2026-10-08',
  merchant: 'Cafe Demo',
);

Widget _app(_CapturePort port) => ProviderScope(
      overrides: [receiptExpenseCommandPortProvider.overrideWithValue(port)],
      child: const MaterialApp(
        home: ReceiptExpenseHandoffPage(prefill: _prefill),
      ),
    );

void _useTallViewport(WidgetTester tester) {
  tester.view.physicalSize = const Size(800, 1400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

void main() {
  testWidgets(
      'personal handoff requires explicit account and preserves date-only prefill',
      (tester) async {
    final port = _CapturePort();
    _useTallViewport(tester);
    await tester.pumpWidget(_app(port));
    expect(find.text('125.00'), findsOneWidget);
    expect(find.text('USD'), findsOneWidget);
    expect(find.text('2026-10-08'), findsOneWidget);
    expect(find.textContaining('12:00 UTC (DATE_ONLY)'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('context-personal')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('handoff-account')),
      'account-1',
    );
    await tester.enterText(
      find.byKey(const ValueKey('handoff-category')),
      'food-category',
    );
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('handoff-confirm')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.byKey(const ValueKey('handoff-confirm')));
    await tester.pumpAndSettle();

    expect(port.personal, hasLength(1));
    final command = port.personal.single;
    expect(command.accountId, 'account-1');
    expect(command.amount, '125.00');
    expect(command.currency, 'USD');
    expect(command.transactionDate, '2026-10-08T12:00:00Z');
    expect(command.datePrecision, 'DATE_ONLY');
    expect(command.items, hasLength(1));
    expect(command.items.single.categoryId, 'food-category');
    expect(command.items.single.amount, '125.00');
    expect(command.idempotencyKey, matches(RegExp(r'^[0-9a-f-]{36}$')));
    expect(find.textContaining('không có giao dịch được lưu'), findsOneWidget);
  });

  testWidgets('shared handoff requires explicit vault, funding and splits',
      (tester) async {
    final port = _CapturePort();
    _useTallViewport(tester);
    await tester.pumpWidget(_app(port));
    await tester.tap(find.byKey(const ValueKey('context-shared')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('handoff-vault')),
      'vault-1',
    );
    await tester.drag(find.byType(ListView), const Offset(0, -300));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byType(DropdownButtonFormField<ReceiptExpenseFundingChoice>),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester
        .tap(find.byType(DropdownButtonFormField<ReceiptExpenseFundingChoice>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Thành viên thanh toán').last);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('handoff-payer')),
      'member-payer',
    );
    await tester.enterText(
      find.byKey(const ValueKey('handoff-source-account')),
      'account-source',
    );
    await tester.enterText(
      find.byKey(const ValueKey('handoff-splits')),
      'member-1 | 125.00',
    );
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('handoff-confirm')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.byKey(const ValueKey('handoff-confirm')));
    await tester.pumpAndSettle();

    expect(port.shared, hasLength(1));
    final command = port.shared.single;
    expect(command.vaultId, 'vault-1');
    expect(command.funding, ReceiptExpenseFundingChoice.member);
    expect(command.payerMemberId, 'member-payer');
    expect(command.sourceAccountId, 'account-source');
    expect(command.splits.single.memberId, 'member-1');
    expect(command.splits.single.amount, '125.00');
    expect(command.transactionDate, '2026-10-08T12:00:00Z');
    expect(command.datePrecision, 'DATE_ONLY');
  });

  testWidgets('shared handoff requires explicit funding selection',
      (tester) async {
    final port = _CapturePort();
    _useTallViewport(tester);
    await tester.pumpWidget(_app(port));
    await tester.tap(find.byKey(const ValueKey('context-shared')));
    await tester.pumpAndSettle();
    await tester.enterText(
        find.byKey(const ValueKey('handoff-vault')), 'vault-1');
    await tester.enterText(
        find.byKey(const ValueKey('handoff-splits')), 'member-1 | 125.00');
    await tester.scrollUntilVisible(
        find.byKey(const ValueKey('handoff-confirm')), 400,
        scrollable: find.byType(Scrollable).first);
    await tester.tap(find.byKey(const ValueKey('handoff-confirm')));
    await tester.pumpAndSettle();
    expect(port.shared, isEmpty);
    expect(find.text('Chọn nguồn chi.'), findsOneWidget);

    await tester.scrollUntilVisible(
        find.byKey(const ValueKey('handoff-funding')), 400,
        scrollable: find.byType(Scrollable).first);
    await tester.tap(find.byKey(const ValueKey('handoff-funding')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Quỹ thanh toán').last);
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
        find.byKey(const ValueKey('handoff-confirm')), 400,
        scrollable: find.byType(Scrollable).first);
    await tester.tap(find.byKey(const ValueKey('handoff-confirm')));
    await tester.pumpAndSettle();

    expect(port.shared, hasLength(1));
    expect(port.shared.single.funding, ReceiptExpenseFundingChoice.vault);
    expect(port.shared.single.payerMemberId, isNull);
    expect(port.shared.single.sourceAccountId, isNull);
  });
  testWidgets('same-command retry keeps its independent idempotency key',
      (tester) async {
    final port = _CapturePort()..failFirst = true;
    _useTallViewport(tester);
    await tester.pumpWidget(_app(port));
    await tester.tap(find.byKey(const ValueKey('context-personal')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('handoff-account')),
      'account-1',
    );
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('handoff-confirm')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.byKey(const ValueKey('handoff-confirm')));
    await tester.pumpAndSettle();
    final firstKey = port.personal.single.idempotencyKey;
    expect(find.text('Thử lại cùng lệnh'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('handoff-confirm')));
    await tester.pumpAndSettle();

    expect(port.personal, hasLength(2));
    expect(port.personal.last.idempotencyKey, firstKey);
    expect(port.personal.last.accountId, 'account-1');
  });
}
