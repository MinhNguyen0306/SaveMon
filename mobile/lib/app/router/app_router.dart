import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/home/presentation/home_page.dart';
import '../../features/identity/presentation/login_page.dart';
import '../../features/identity/presentation/profile_page.dart';
import '../../features/identity/presentation/registration_page.dart';
import '../../features/personal_finance/presentation/personal_finance_page.dart';
import '../../features/personal_finance/presentation/transaction_detail_page.dart';
import '../../features/shared_finance/presentation/shared_expense_detail_page.dart';
import '../../features/shared_finance/presentation/shared_vault_page.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  final router = GoRouter(
    routes: [
      GoRoute(path: '/', builder: (context, state) => const HomePage()),
      GoRoute(path: '/login', builder: (context, state) => const LoginPage()),
      GoRoute(path: '/register', builder: (context, state) => const RegistrationPage()),
      GoRoute(path: '/profile', builder: (context, state) => const ProfilePage()),
      GoRoute(path: '/personal-finance', builder: (context, state) => const PersonalFinancePage()),
      GoRoute(
        path: '/personal-finance/transactions/:transactionId',
        builder: (context, state) => TransactionDetailPage(
          transactionId: state.pathParameters['transactionId']!,
        ),
      ),
      GoRoute(
        path: '/shared-finance',
        builder: (context, state) => const SharedVaultListPage(),
      ),
      GoRoute(
        path: '/shared-finance/vault/:vaultId',
        builder: (context, state) => SharedVaultPage(vaultId: state.pathParameters['vaultId']!),
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
  ref.onDispose(router.dispose);
  return router;
});
