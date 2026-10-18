import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/app/theme/app_theme.dart';
import 'package:savemon_mobile/features/personal_finance/data/models/personal_finance_models.dart';
import 'package:savemon_mobile/features/personal_finance/presentation/personal_finance_page.dart';

void main() {
  testWidgets(
      'groups timestamped transactions by UTC day and labels recent days',
      (tester) async {
    tester.view.physicalSize = const Size(900, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final now = DateTime.now().toUtc();
    final today = _dateOnly(now);
    final yesterday = _dateOnly(now.subtract(const Duration(days: 1)));
    final items = [
      _transaction('one', '${today}T09:00:00Z', 'EXPENSE', 'Coffee'),
      _transaction('two', '${today}T15:45:00Z', 'EXPENSE', 'Lunch'),
      _transaction('three', '${yesterday}T22:00:00Z', 'INCOME', 'Salary'),
    ];
    await tester.pumpWidget(ProviderScope(
      overrides: [
        personalCurrencyProvider.overrideWith((ref) => 'VND'),
        personalMonthSummaryProvider.overrideWith((ref, range) async =>
            const FinancialSummary(
                currency: 'VND', income: 0, expense: 0, net: 0)),
        personalTransactionsProvider.overrideWith((ref, page) async =>
            TransactionHistoryPage(
                items: items,
                page: page,
                size: 20,
                totalItems: 3,
                totalPages: 2)),
      ],
      child: MaterialApp(
        theme: AppTheme.light,
        home: const PersonalFinancePage(
            section: PersonalFinanceSection.transactions),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.text('Hôm nay'), findsOneWidget);
    expect(find.text('Hôm qua'), findsOneWidget);
    expect(find.text('2 giao dịch'), findsOneWidget);
    expect(find.text('Coffee'), findsWidgets);
    expect(find.text('Lunch'), findsOneWidget);
    expect(find.text('Salary'), findsOneWidget);
    expect(find.byTooltip('Next giao dịch page'), findsOneWidget);
    await tester.tap(find.byTooltip('Next giao dịch page'));
    await tester.pumpAndSettle();
    expect(find.text('Page 2 of 2'), findsOneWidget);

    await tester.enterText(find.byType(TextField).first, 'Coffee');
    await tester.pumpAndSettle();
    expect(find.text('Coffee'), findsNWidgets(2));
    expect(find.text('Lunch'), findsNothing);

    await tester.enterText(find.byType(TextField).first, '');
    await tester.pumpAndSettle();
    await tester.tap(find.text('Thu nhập'));
    await tester.pumpAndSettle();
    expect(find.text('Salary'), findsOneWidget);
    expect(find.text('Coffee'), findsNothing);
  });

  testWidgets('monthly totals request exact current-month date bounds',
      (tester) async {
    (String, String, String)? requestedRange;
    await tester.pumpWidget(ProviderScope(
      overrides: [
        personalCurrencyProvider.overrideWith((ref) => 'VND'),
        personalMonthSummaryProvider.overrideWith((ref, range) async {
          requestedRange = range;
          return const FinancialSummary(
              currency: 'VND', income: 0, expense: 0, net: 0);
        }),
        personalTransactionsProvider.overrideWith((ref, page) async =>
            const TransactionHistoryPage(
                items: [], page: 0, size: 20, totalItems: 0, totalPages: 0)),
      ],
      child: MaterialApp(
        theme: AppTheme.light,
        home: const PersonalFinancePage(
            section: PersonalFinanceSection.transactions),
      ),
    ));
    await tester.pumpAndSettle();
    final now = DateTime.now();
    expect(requestedRange, (
      'VND',
      _dateOnly(DateTime(now.year, now.month)),
      _dateOnly(DateTime(now.year, now.month + 1, 0)),
    ));
  });

  testWidgets('transactions landing shows monthly totals and empty history',
      (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        personalCurrencyProvider.overrideWith((ref) => 'VND'),
        personalMonthSummaryProvider.overrideWith((ref, range) async =>
            const FinancialSummary(
                currency: 'VND', income: 0, expense: 0, net: 0)),
        personalTransactionsProvider.overrideWith((ref, page) async =>
            const TransactionHistoryPage(
                items: [], page: 0, size: 20, totalItems: 0, totalPages: 0)),
      ],
      child: MaterialApp(
        theme: AppTheme.light,
        home: const PersonalFinancePage(
            section: PersonalFinanceSection.transactions),
      ),
    ));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Giao d\u{1ECB}ch'), findsOneWidget);
    expect(find.text('T\u{1ED5}ng chi ti\u{EA}u'), findsOneWidget);
    expect(find.text('T\u{1ED5}ng thu nh\u{1EAD}p'), findsOneWidget);
    expect(
        find.text('Ch\u{01B0}a c\u{00F3} giao d\u{1ECB}ch.'), findsOneWidget);
    expect(find.text('Accounts'), findsNothing);
  });

  testWidgets('accounts landing does not include transaction summary',
      (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        personalAccountsProvider.overrideWith((ref, page) async =>
            const PageResult<PersonalAccount>(
                items: [], page: 0, size: 20, totalItems: 0, totalPages: 0)),
      ],
      child: MaterialApp(
        theme: AppTheme.light,
        home:
            const PersonalFinancePage(section: PersonalFinanceSection.accounts),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.text('Accounts'), findsWidgets);
    expect(find.text('Summary and calendar'), findsNothing);
    expect(find.text('Transaction history'), findsNothing);
  });
}

PersonalTransaction _transaction(
        String id, String date, String type, String description) =>
    PersonalTransaction(
      id: id,
      type: type,
      amount: 1200,
      currency: 'VND',
      transactionDate: date,
      description: description,
      status: 'ACTIVE',
      accountId: 'account',
      accountName: 'Cash',
      items: const [],
    );

String _dateOnly(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
