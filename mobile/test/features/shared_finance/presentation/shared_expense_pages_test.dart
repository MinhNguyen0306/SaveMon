import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:savemon_mobile/features/shared_finance/application/shared_finance_providers.dart';
import 'package:savemon_mobile/features/shared_finance/data/shared_finance_models.dart';
import 'package:savemon_mobile/features/shared_finance/presentation/shared_expense_detail_page.dart';
import 'package:savemon_mobile/features/shared_finance/presentation/shared_vault_page.dart';

void main() {
  const expense = SharedExpense(
    id: 'expense-1',
    vaultId: 'vault-1',
    amount: 900000,
    currency: 'VND',
    description: 'Dinner',
    expenseDate: '2026-10-08T19:00:00Z',
    fundingType: 'MEMBER',
    sourceAccountId: 'account-1',
    paidByMemberId: 'member-1',
    splits: [
      SharedExpenseSplit(memberId: 'member-1', amount: 300000),
      SharedExpenseSplit(memberId: 'member-2', amount: 600000),
    ],
    transactionId: 'transaction-1',
    status: 'ACTIVE',
  );

  testWidgets('selecting a vault expense opens its read-only details', (
    tester,
  ) async {
    final router = _router();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          sharedVaultProvider('vault-1').overrideWith(
            (ref) async => const SharedVault(
              id: 'vault-1',
              name: 'Trip Fund',
              currency: 'VND',
              balance: 1000000,
              status: 'ACTIVE',
            ),
          ),
          sharedVaultMembersProvider(
            (vaultId: 'vault-1', page: 0, size: 20),
          ).overrideWith((ref) async => _page<SharedVaultMember>([])),
          sharedVaultContributionsProvider(
            (vaultId: 'vault-1', page: 0, size: 20),
          ).overrideWith((ref) async => _page<SharedContribution>([])),
          sharedVaultExpensesProvider(
            (vaultId: 'vault-1', page: 0, size: 20),
          ).overrideWith((ref) async => _page([expense])),
          sharedExpenseProvider(
            (vaultId: 'vault-1', expenseId: 'expense-1'),
          ).overrideWith((ref) async => expense),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Dinner · 2026-10-08T19:00:00Z'),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('Dinner · 2026-10-08T19:00:00Z'));
    await tester.pumpAndSettle();

    expect(find.text('Expense details'), findsOneWidget);
    expect(find.text('900000 VND'), findsOneWidget);
    expect(find.text('Paid by member'), findsOneWidget);
    expect(find.text('Member member-2'), findsOneWidget);
    expect(find.text('600000 VND'), findsOneWidget);
  });

  testWidgets('vault expense section presents an empty state', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          sharedVaultProvider('vault-1').overrideWith(
            (ref) async => const SharedVault(
              id: 'vault-1',
              name: 'Trip Fund',
              currency: 'VND',
              balance: 1000000,
              status: 'ACTIVE',
            ),
          ),
          sharedVaultMembersProvider(
            (vaultId: 'vault-1', page: 0, size: 20),
          ).overrideWith((ref) async => _page<SharedVaultMember>([])),
          sharedVaultContributionsProvider(
            (vaultId: 'vault-1', page: 0, size: 20),
          ).overrideWith((ref) async => _page<SharedContribution>([])),
          sharedVaultExpensesProvider(
            (vaultId: 'vault-1', page: 0, size: 20),
          ).overrideWith((ref) async => _page<SharedExpense>([])),
        ],
        child: MaterialApp.router(routerConfig: _router()),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('No expenses to show.'),
      300,
      scrollable: find.byType(Scrollable).first,
    );

    expect(find.text('No expenses to show.'), findsOneWidget);
  });

  testWidgets('expense list error can be retried', (tester) async {
    var attempts = 0;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          sharedVaultProvider('vault-1').overrideWith(
            (ref) async => const SharedVault(
              id: 'vault-1',
              name: 'Trip Fund',
              currency: 'VND',
              balance: 1000000,
              status: 'ACTIVE',
            ),
          ),
          sharedVaultMembersProvider(
            (vaultId: 'vault-1', page: 0, size: 20),
          ).overrideWith((ref) async => _page<SharedVaultMember>([])),
          sharedVaultContributionsProvider(
            (vaultId: 'vault-1', page: 0, size: 20),
          ).overrideWith((ref) async => _page<SharedContribution>([])),
          sharedVaultExpensesProvider(
            (vaultId: 'vault-1', page: 0, size: 20),
          ).overrideWith((ref) async {
            attempts++;
            if (attempts == 1) throw Exception('temporary network failure');
            return _page([expense]);
          }),
        ],
        child: MaterialApp.router(routerConfig: _router()),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Could not load this information. Please retry.'),
      300,
      scrollable: find.byType(Scrollable).first,
    );

    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();

    expect(attempts, 2);
    expect(find.text('Dinner · 2026-10-08T19:00:00Z'), findsOneWidget);
  });

  testWidgets('vault list pagination advances and returns to the prior page',
      (tester) async {
    final requestedPages = <int>[];
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          sharedVaultsProvider((page: 0, size: 20)).overrideWith((ref) async {
            requestedPages.add(0);
            return _page(
              const [
                SharedVault(
                  id: 'vault-1',
                  name: 'First page vault',
                  currency: 'VND',
                  balance: 100,
                  status: 'ACTIVE',
                ),
              ],
              totalItems: 21,
              totalPages: 2,
            );
          }),
          sharedVaultsProvider((page: 1, size: 20)).overrideWith((ref) async {
            requestedPages.add(1);
            return _page(
              const [
                SharedVault(
                  id: 'vault-2',
                  name: 'Second page vault',
                  currency: 'VND',
                  balance: 200,
                  status: 'ACTIVE',
                ),
              ],
              page: 1,
              totalItems: 21,
              totalPages: 2,
            );
          }),
        ],
        child: const MaterialApp(home: SharedVaultListPage()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('First page vault'), findsOneWidget);
    expect(find.text('Page 1 of 2'), findsOneWidget);
    await tester.tap(find.text('Next'));
    await tester.pumpAndSettle();

    expect(find.text('Second page vault'), findsOneWidget);
    expect(find.text('Page 2 of 2'), findsOneWidget);
    await tester.tap(find.text('Previous'));
    await tester.pumpAndSettle();

    expect(find.text('First page vault'), findsOneWidget);
    expect(requestedPages, [0, 1]);
  });

  testWidgets('vault list presents loading before its empty result',
      (tester) async {
    final response = Completer<SharedFinancePage<SharedVault>>();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          sharedVaultsProvider((page: 0, size: 20))
              .overrideWith((ref) => response.future),
        ],
        child: const MaterialApp(home: SharedVaultListPage()),
      ),
    );
    await tester.pump();

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    response.complete(_page<SharedVault>([]));
    await tester.pumpAndSettle();

    expect(find.text('No shared vaults to show.'), findsOneWidget);
  });
}

SharedFinancePage<T> _page<T>(
  List<T> items, {
  int page = 0,
  int size = 20,
  int? totalItems,
  int? totalPages,
}) =>
    SharedFinancePage(
      items: items,
      page: page,
      size: size,
      totalItems: totalItems ?? items.length,
      totalPages: totalPages ?? (items.isEmpty ? 0 : 1),
    );

GoRouter _router() => GoRouter(
      initialLocation: '/shared-finance/vault/vault-1',
      routes: [
        GoRoute(
          path: '/shared-finance/vault/:vaultId',
          builder: (context, state) => SharedVaultPage(
            vaultId: state.pathParameters['vaultId']!,
          ),
        ),
        GoRoute(
          path: '/shared-finance/vault/:vaultId/expenses/:expenseId',
          builder: (context, state) => SharedExpenseDetailPage(
            vaultId: state.pathParameters['vaultId']!,
            expenseId: state.pathParameters['expenseId']!,
          ),
        ),
      ],
    );
