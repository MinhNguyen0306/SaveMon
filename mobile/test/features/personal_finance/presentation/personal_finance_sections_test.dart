import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/app/theme/app_theme.dart';
import 'package:savemon_mobile/features/personal_finance/data/models/personal_finance_models.dart';
import 'package:savemon_mobile/features/personal_finance/presentation/personal_finance_page.dart';

void main() {
  testWidgets('transactions landing retains summary/calendar and history',
      (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        personalCurrencyProvider.overrideWith((ref) => 'VND'),
        personalSummaryProvider.overrideWith((ref, currency) async =>
            const FinancialSummary(
                currency: 'VND', income: 0, expense: 0, net: 0)),
        personalCalendarProvider.overrideWith((ref, currency) async =>
            const CalendarSummary(currency: 'VND', days: [])),
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
    expect(find.text('Summary and calendar'), findsOneWidget);
    expect(find.text('Calendar activity · VND'), findsOneWidget);
    expect(find.text('Transaction history'), findsOneWidget);
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
