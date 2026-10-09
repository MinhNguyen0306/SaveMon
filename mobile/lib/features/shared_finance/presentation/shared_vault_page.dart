import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_client.dart';
import '../../../core/widgets/async_value_view.dart';
import '../application/shared_finance_providers.dart';
import '../data/shared_finance_models.dart';

const _pageSize = 20;

class SharedVaultListPage extends ConsumerStatefulWidget {
  const SharedVaultListPage({super.key});

  @override
  ConsumerState<SharedVaultListPage> createState() =>
      _SharedVaultListPageState();
}

class _SharedVaultListPageState extends ConsumerState<SharedVaultListPage> {
  int _page = 0;

  @override
  Widget build(BuildContext context) {
    final request = (page: _page, size: _pageSize);
    return Scaffold(
      appBar: AppBar(title: const Text('Shared vaults')),
      body: _PagedSection<SharedVault>(
        value: ref.watch(sharedVaultsProvider(request)),
        emptyMessage: 'No shared vaults to show.',
        onRetry: () async => ref.invalidate(sharedVaultsProvider(request)),
        onPageChanged: (page) => setState(() => _page = page),
        itemBuilder: (vault) => Card(
          child: ListTile(
            title: Text(vault.name),
            subtitle: Text(vault.status),
            trailing: Text('${vault.balance} ${vault.currency}'),
            onTap: () => context.push(
              '/shared-finance/vault/${Uri.encodeComponent(vault.id)}',
            ),
          ),
        ),
      ),
    );
  }
}

class SharedVaultPage extends ConsumerStatefulWidget {
  const SharedVaultPage({required this.vaultId, super.key});
  final String vaultId;

  @override
  ConsumerState<SharedVaultPage> createState() => _SharedVaultPageState();
}

class _SharedVaultPageState extends ConsumerState<SharedVaultPage> {
  int _membersPage = 0;
  int _contributionsPage = 0;
  int _expensesPage = 0;

  @override
  void didUpdateWidget(covariant SharedVaultPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.vaultId != widget.vaultId) {
      _membersPage = 0;
      _contributionsPage = 0;
      _expensesPage = 0;
    }
  }

  @override
  Widget build(BuildContext context) {
    final membersRequest = (
      vaultId: widget.vaultId,
      page: _membersPage,
      size: _pageSize,
    );
    final contributionsRequest = (
      vaultId: widget.vaultId,
      page: _contributionsPage,
      size: _pageSize,
    );
    final expensesRequest = (
      vaultId: widget.vaultId,
      page: _expensesPage,
      size: _pageSize,
    );

    return Scaffold(
      appBar: AppBar(title: const Text('Shared vault')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          AsyncValueView(
            value: ref.watch(sharedVaultProvider(widget.vaultId)),
            onRetry: () async =>
                ref.invalidate(sharedVaultProvider(widget.vaultId)),
            errorMessage: _requestErrorMessage,
            builder: (vault) => Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(vault.name,
                        style: Theme.of(context).textTheme.titleLarge),
                    ListTile(
                      title: const Text('Balance'),
                      subtitle: Text('${vault.balance} ${vault.currency}'),
                    ),
                    ListTile(
                      title: const Text('Status'),
                      subtitle: Text(vault.status),
                    ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text('Members', style: Theme.of(context).textTheme.titleLarge),
          _PagedSection<SharedVaultMember>(
            value: ref.watch(sharedVaultMembersProvider(membersRequest)),
            emptyMessage: 'No members to show.',
            onRetry: () =>
                ref.invalidate(sharedVaultMembersProvider(membersRequest)),
            onPageChanged: (page) => setState(() => _membersPage = page),
            itemBuilder: (member) => Card(
              child: ExpansionTile(
                title: Text(member.displayName),
                subtitle: Text('${member.role} · ${member.status}'),
                children: [
                  ListTile(
                    title: const Text('Member'),
                    subtitle: Text(member.id),
                  ),
                  ListTile(
                    title: const Text('User'),
                    subtitle: Text(member.userId),
                  ),
                  ListTile(
                    title: const Text('Joined'),
                    subtitle: Text(member.joinedAt),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text('Contributions',
              style: Theme.of(context).textTheme.titleLarge),
          _PagedSection<SharedContribution>(
            value: ref.watch(
              sharedVaultContributionsProvider(contributionsRequest),
            ),
            emptyMessage: 'No contributions to show.',
            onRetry: () => ref.invalidate(
              sharedVaultContributionsProvider(contributionsRequest),
            ),
            onPageChanged: (page) =>
                setState(() => _contributionsPage = page),
            itemBuilder: (contribution) => Card(
              child: ExpansionTile(
                title: Text(
                  '${contribution.amount} ${contribution.currency}',
                ),
                subtitle: Text(
                  '${contribution.description ?? ''} · '
                  '${contribution.contributedAt}',
                ),
                children: [
                  ListTile(
                    title: const Text('Member'),
                    subtitle: Text(contribution.memberId),
                  ),
                  ListTile(
                    title: const Text('Source account'),
                    subtitle: Text(contribution.sourceAccountId),
                  ),
                  ListTile(
                    title: const Text('Status'),
                    subtitle: Text(contribution.status),
                  ),
                  ListTile(
                    title: const Text('Transaction'),
                    subtitle: Text(contribution.transactionId),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text('Expenses', style: Theme.of(context).textTheme.titleLarge),
          _PagedSection<SharedExpense>(
            value: ref.watch(sharedVaultExpensesProvider(expensesRequest)),
            emptyMessage: 'No expenses to show.',
            onRetry: () =>
                ref.invalidate(sharedVaultExpensesProvider(expensesRequest)),
            onPageChanged: (page) => setState(() => _expensesPage = page),
            itemBuilder: (expense) => Card(
              child: ListTile(
                title: Text('${expense.amount} ${expense.currency}'),
                subtitle: Text(
                  '${expense.description} · ${expense.expenseDate}',
                ),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => context.push(
                  '/shared-finance/vault/'
                  '${Uri.encodeComponent(widget.vaultId)}/expenses/'
                  '${Uri.encodeComponent(expense.id)}',
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PagedSection<T> extends StatelessWidget {
  const _PagedSection({
    required this.value,
    required this.emptyMessage,
    required this.onRetry,
    required this.onPageChanged,
    required this.itemBuilder,
  });

  final AsyncValue<SharedFinancePage<T>> value;
  final String emptyMessage;
  final VoidCallback onRetry;
  final ValueChanged<int> onPageChanged;
  final Widget Function(T item) itemBuilder;

  @override
  Widget build(BuildContext context) => AsyncValueView(
        value: value,
        onRetry: () async => onRetry(),
        errorMessage: _requestErrorMessage,
        builder: (result) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (result.items.isEmpty)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  result.totalItems == 0
                      ? emptyMessage
                      : 'No items on this page.',
                ),
              )
            else
              for (final item in result.items) itemBuilder(item),
            _PageControls(
              result: result,
              onPageChanged: onPageChanged,
            ),
          ],
        ),
      );
}

class _PageControls<T> extends StatelessWidget {
  const _PageControls({
    required this.result,
    required this.onPageChanged,
  });

  final SharedFinancePage<T> result;
  final ValueChanged<int> onPageChanged;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              result.totalPages == 0
                  ? 'No pages'
                  : 'Page ${result.page + 1} of ${result.totalPages}',
            ),
            Row(
              children: [
                TextButton(
                  onPressed: result.page > 0
                      ? () => onPageChanged(result.page - 1)
                      : null,
                  child: const Text('Previous'),
                ),
                TextButton(
                  onPressed: result.page + 1 < result.totalPages
                      ? () => onPageChanged(result.page + 1)
                      : null,
                  child: const Text('Next'),
                ),
              ],
            ),
          ],
        ),
      );
}

String _requestErrorMessage(Object error) =>
    error is ApiException && error.statusCode == 401
        ? 'Your session has expired. Please sign in again.'
        : 'Could not load this information. Please retry.';
