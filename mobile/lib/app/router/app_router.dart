import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/session/auth_state.dart';
import '../../features/home/presentation/home_page.dart';
import '../../features/identity/presentation/login_page.dart';
import '../../features/identity/presentation/profile_page.dart';
import '../../features/identity/presentation/registration_page.dart';
import '../../features/personal_finance/presentation/personal_finance_page.dart';
import '../../features/personal_finance/presentation/transaction_detail_page.dart';
import '../../features/qr_bill_scan/presentation/expense_history_preview_page.dart'
    show ExpenseHistoryPage;
import '../../features/qr_bill_scan/presentation/expense_scan_page.dart';
import '../../features/qr_bill_scan/presentation/scan_result_page.dart';
import '../../features/shared_finance/presentation/shared_expense_detail_page.dart';
import '../../features/shared_finance/presentation/shared_vault_page.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  // Recreate the stateful shell only when its authenticated subject changes,
  // never for ordinary same-subject token rotation.
  ref.watch(authSubjectEpochProvider);
  final refresh = _RouterRefresh();
  ref.listen(authStateProvider, (_, __) => refresh.refresh());
  final router = GoRouter(
    refreshListenable: refresh,
    redirect: (context, state) {
      final auth = ref.read(authStateProvider);
      final path = state.matchedLocation;
      final isPublic = path == '/' || path == '/login' || path == '/register';
      if (auth.isLoading || auth.hasError) {
        return isPublic ? null : '/';
      }
      final isAuthenticated = auth.value == AuthenticationStatus.authenticated;
      if (!isAuthenticated && !isPublic) return '/login';
      if (isAuthenticated && (path == '/login' || path == '/register')) {
        return '/';
      }
      return null;
    },
    routes: [
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => _AppShell(navigationShell: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(path: '/', builder: (_, __) => const HomePage()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/personal-finance',
              builder: (_, __) => const PersonalFinancePage(
                  section: PersonalFinanceSection.transactions),
              routes: [
                GoRoute(
                  path: 'transactions/:transactionId',
                  builder: (_, state) => TransactionDetailPage(
                    transactionId: state.pathParameters['transactionId']!,
                  ),
                ),
              ],
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/personal-finance/accounts',
              builder: (_, __) => const PersonalFinancePage(
                  section: PersonalFinanceSection.accounts),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/shared-finance',
              builder: (_, __) => const SharedVaultListPage(),
              routes: [
                GoRoute(
                  path: 'vault/:vaultId',
                  builder: (_, state) => SharedVaultPage(
                    vaultId: state.pathParameters['vaultId']!,
                  ),
                  routes: [
                    GoRoute(
                      path: 'expenses/:expenseId',
                      builder: (_, state) => SharedExpenseDetailPage(
                        vaultId: state.pathParameters['vaultId']!,
                        expenseId: state.pathParameters['expenseId']!,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/profile', builder: (_, __) => const ProfilePage()),
          ]),
        ],
      ),
      GoRoute(path: '/login', builder: (_, __) => const LoginPage()),
      GoRoute(path: '/register', builder: (_, __) => const RegistrationPage()),
      GoRoute(
        path: '/expense-scan',
        builder: (_, __) => const ExpenseScanPage(),
        routes: [
          GoRoute(
            path: 'result',
            builder: (_, __) => const ScanResultPage(),
          ),
        ],
      ),
      GoRoute(
        path: '/expense-history',
        builder: (_, __) => const ExpenseHistoryPage(),
      ),
    ],
  );
  ref.onDispose(() {
    router.dispose();
    refresh.dispose();
  });
  return router;
});

class _RouterRefresh extends ChangeNotifier {
  void refresh() => notifyListeners();
}

class _AppShell extends ConsumerWidget {
  const _AppShell({required this.navigationShell});
  final StatefulNavigationShell navigationShell;

  static const _destinations = [
    NavigationDestination(
        icon: Icon(Icons.home_outlined), label: 'T\u{1ED5}ng quan'),
    NavigationDestination(
        icon: Icon(Icons.receipt_long_outlined), label: 'Giao d\u{1ECB}ch'),
    NavigationDestination(
        icon: Icon(Icons.account_balance_wallet_outlined),
        label: 'T\u{00E0}i kho\u{1EA3}n'),
    NavigationDestination(
        icon: Icon(Icons.groups_outlined), label: 'Qu\u{1EF9} chung'),
    NavigationDestination(
        icon: Icon(Icons.person_outline), label: 'H\u{1ED3} s\u{01A1}'),
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) => Scaffold(
        body: navigationShell,
        bottomNavigationBar: ref.watch(authStateProvider).value ==
                AuthenticationStatus.authenticated
            ? NavigationBar(
                selectedIndex: navigationShell.currentIndex,
                destinations: _destinations,
                onDestinationSelected: (index) =>
                    navigationShell.goBranch(index, initialLocation: false),
              )
            : null,
      );
}
