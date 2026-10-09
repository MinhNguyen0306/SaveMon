import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/widgets/async_value_view.dart';
import '../application/shared_finance_providers.dart';
import '../data/shared_finance_models.dart';

class SharedExpenseDetailPage extends ConsumerWidget {
  const SharedExpenseDetailPage({
    required this.vaultId,
    required this.expenseId,
    super.key,
  });

  final String vaultId;
  final String expenseId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final parameters = (vaultId: vaultId, expenseId: expenseId);
    return Scaffold(
      appBar: AppBar(title: const Text('Expense details')),
      body: AsyncValueView(
        value: ref.watch(sharedExpenseProvider(parameters)),
        onRetry: () async => ref.invalidate(sharedExpenseProvider(parameters)),
        errorMessage: _requestErrorMessage,
        builder: (expense) => _SharedExpenseDetails(expense: expense),
      ),
    );
  }
}

class _SharedExpenseDetails extends StatelessWidget {
  const _SharedExpenseDetails({required this.expense});

  final SharedExpense expense;

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(24),
        children: [
          ListTile(
            title: const Text('Amount'),
            subtitle: Text('${expense.amount} ${expense.currency}'),
          ),
          ListTile(
            title: const Text('Description'),
            subtitle: Text(expense.description),
          ),
          ListTile(
            title: const Text('Date'),
            subtitle: Text(expense.expenseDate),
          ),
          ListTile(
            title: const Text('Funding'),
            subtitle: Text(expense.fundingType),
          ),
          if (expense.paidByMemberId != null)
            ListTile(
              title: const Text('Paid by member'),
              subtitle: Text(expense.paidByMemberId!),
            ),
          if (expense.sourceAccountId != null)
            ListTile(
              title: const Text('Source account'),
              subtitle: Text(expense.sourceAccountId!),
            ),
          ListTile(
            title: const Text('Status'),
            subtitle: Text(expense.status),
          ),
          ListTile(
            title: const Text('Transaction'),
            subtitle: Text(expense.transactionId),
          ),
          const Divider(),
          Text('Splits', style: Theme.of(context).textTheme.titleLarge),
          if (expense.splits.isEmpty)
            const ListTile(title: Text('No splits to show.'))
          else
            for (final split in expense.splits)
              ListTile(
                title: Text('Member ${split.memberId}'),
                trailing: Text('${split.amount} ${expense.currency}'),
              ),
        ],
      );
}

String _requestErrorMessage(Object error) =>
    error is ApiException && error.statusCode == 401
        ? 'Your session has expired. Please sign in again.'
        : 'Could not load this information. Please retry.';
