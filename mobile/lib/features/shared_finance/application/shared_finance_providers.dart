import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../data/shared_finance_api.dart' show SharedFinanceApi;
import '../data/shared_finance_models.dart';
import '../data/shared_vault_repository.dart';

typedef SharedPageRequest = ({int page, int size});
typedef SharedVaultListPageRequest = ({String vaultId, int page, int size});

final sharedFinanceApiProvider = Provider<SharedFinanceApi>((ref) {
  return SharedFinanceApi(ref.watch(apiClientProvider));
});

final sharedVaultRepositoryProvider = Provider<SharedVaultRepository>(
  (ref) => SharedVaultRepository(ref.watch(apiClientProvider)),
);

final sharedVaultsProvider =
    FutureProvider.family<SharedFinancePage<SharedVault>, SharedPageRequest>(
  (ref, request) => ref.watch(sharedVaultRepositoryProvider).listVaults(
        page: request.page,
        size: request.size,
      ),
);

final sharedVaultProvider = FutureProvider.family<SharedVault, String>(
  (ref, id) => ref.watch(sharedVaultRepositoryProvider).getVault(id),
);

final sharedVaultMembersProvider =
    FutureProvider.family<SharedFinancePage<SharedVaultMember>,
        SharedVaultListPageRequest>(
  (ref, request) => ref.watch(sharedVaultRepositoryProvider).listMembers(
        request.vaultId,
        page: request.page,
        size: request.size,
      ),
);

final sharedVaultContributionsProvider =
    FutureProvider.family<SharedFinancePage<SharedContribution>,
        SharedVaultListPageRequest>(
  (ref, request) => ref
      .watch(sharedVaultRepositoryProvider)
      .listContributions(
        vaultId: request.vaultId,
        page: request.page,
        size: request.size,
      ),
);

final sharedVaultExpensesProvider =
    FutureProvider.family<SharedFinancePage<SharedExpense>,
        SharedVaultListPageRequest>(
  (ref, request) => ref.watch(sharedVaultRepositoryProvider).listExpenses(
        request.vaultId,
        page: request.page,
        size: request.size,
      ),
);

final sharedExpenseProvider =
    FutureProvider.family<SharedExpense, ({String vaultId, String expenseId})>(
  (ref, parameters) => ref.watch(sharedVaultRepositoryProvider).getExpense(
        vaultId: parameters.vaultId,
        expenseId: parameters.expenseId,
      ),
);
