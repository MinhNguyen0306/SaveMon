import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/widgets/async_value_view.dart';
import '../data/models/personal_finance_models.dart';
import '../data/personal_finance_repository.dart';
import '../data/providers.dart';

final personalTransactionProvider =
    FutureProvider.family<PersonalTransaction, String>((ref, id) =>
        ref.watch(personalFinanceRepositoryProvider).getTransaction(id));

class TransactionDetailPage extends ConsumerWidget {
  const TransactionDetailPage({required this.transactionId, super.key});
  final String transactionId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final transaction = ref.watch(personalTransactionProvider(transactionId));
    return Scaffold(
      appBar: AppBar(title: const Text('Transaction details')),
      body: AsyncValueView(
        value: transaction,
        onRetry: () async => ref.invalidate(personalTransactionProvider(transactionId)),
        errorMessage: _requestErrorMessage,
        builder: (item) => _TransactionDetails(transaction: item),
      ),
    );
  }
}

class _TransactionDetails extends StatelessWidget {
  const _TransactionDetails({required this.transaction});
  final PersonalTransaction transaction;

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(24),
        children: [
          ListTile(title: const Text('Type'), subtitle: Text(transaction.type)),
          ListTile(title: const Text('Amount'), subtitle: Text('${transaction.amount} ${transaction.currency}')),
          ListTile(title: const Text('Date'), subtitle: Text(transaction.transactionDate)),
          ListTile(title: const Text('Description'), subtitle: Text(transaction.description)),
          ListTile(title: const Text('Status'), subtitle: Text(transaction.status)),
          ListTile(title: const Text('Account'), subtitle: Text(transaction.accountName)),
          const Divider(),
          for (final item in transaction.items)
            ListTile(
              title: Text(item.categoryName),
              subtitle: Text(item.categoryId),
              trailing: Text('${item.amount} ${transaction.currency}'),
            ),
        ],
      );
}

String _requestErrorMessage(Object error) =>
    error is ApiException && error.statusCode == 401
        ? 'Your session has expired. Please sign in again.'
        : 'Could not load this information. Please retry.';
