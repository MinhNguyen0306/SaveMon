import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_client.dart';
import '../../../core/widgets/async_value_view.dart';
import '../data/models/personal_finance_models.dart';
import '../data/personal_finance_repository.dart';
import '../data/providers.dart';

final personalAccountsPageProvider = StateProvider<int>((ref) => 0);
final personalTransactionPageProvider = StateProvider<int>((ref) => 0);
final personalCurrencyProvider = StateProvider<String>((ref) => '');
final personalAccountsProvider =
    FutureProvider.family<PageResult<PersonalAccount>, int>((ref, page) =>
        ref.watch(personalFinanceRepositoryProvider).listAccounts(page: page));
final personalTransactionsProvider =
    FutureProvider.family<TransactionHistoryPage, int>((ref, page) =>
        ref.watch(personalFinanceRepositoryProvider).listTransactions(page: page));
final personalSummaryProvider =
    FutureProvider.family<FinancialSummary, String>((ref, currency) => ref
        .watch(personalFinanceRepositoryProvider)
        .getSummary(currency: currency));
final personalCalendarProvider =
    FutureProvider.family<CalendarSummary, String>((ref, currency) => ref
        .watch(personalFinanceRepositoryProvider)
        .getCalendar(currency: currency));

class PersonalFinancePage extends ConsumerStatefulWidget {
  const PersonalFinancePage({super.key});

  @override
  ConsumerState<PersonalFinancePage> createState() =>
      _PersonalFinancePageState();
}

class _PersonalFinancePageState extends ConsumerState<PersonalFinancePage> {
  final _currencyController = TextEditingController();

  @override
  void dispose() {
    _currencyController.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    final currency = ref.read(personalCurrencyProvider);
    ref.invalidate(personalAccountsProvider(ref.read(personalAccountsPageProvider)));
    ref.invalidate(
      personalTransactionsProvider(ref.read(personalTransactionPageProvider)),
    );
    if (currency.isNotEmpty) {
      ref.invalidate(personalSummaryProvider(currency));
      ref.invalidate(personalCalendarProvider(currency));
    }
  }

  void _submitCurrency() {
    final currency = _currencyController.text.trim();
    if (currency.isNotEmpty) {
      ref.read(personalCurrencyProvider.notifier).state = currency;
    }
  }

  @override
  Widget build(BuildContext context) {
    final accountPage = ref.watch(personalAccountsPageProvider);
    final transactionPage = ref.watch(personalTransactionPageProvider);
    final currency = ref.watch(personalCurrencyProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Personal finance')),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          children: [
            Text('Summary and calendar', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _currencyController,
                    textCapitalization: TextCapitalization.characters,
                    decoration: const InputDecoration(
                      labelText: 'Currency (required)',
                      hintText: 'For example, VND',
                    ),
                    onSubmitted: (_) => _submitCurrency(),
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: _submitCurrency,
                  child: const Text('Apply'),
                ),
              ],
            ),
            if (currency.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text('Enter a currency to load summary and calendar totals.'),
              )
            else ...[
              const SizedBox(height: 12),
              AsyncValueView(
                value: ref.watch(personalSummaryProvider(currency)),
                onRetry: () async =>
                    ref.invalidate(personalSummaryProvider(currency)),
                errorMessage: _requestErrorMessage,
                builder: (summary) => Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Summary · ${summary.currency}'),
                        Text('Income: ${summary.income} ${summary.currency}'),
                        Text('Expense: ${summary.expense} ${summary.currency}'),
                        Text('Net: ${summary.net} ${summary.currency}'),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Text('Calendar activity · $currency',
                  style: Theme.of(context).textTheme.titleMedium),
              AsyncValueView(
                value: ref.watch(personalCalendarProvider(currency)),
                onRetry: () async =>
                    ref.invalidate(personalCalendarProvider(currency)),
                errorMessage: _requestErrorMessage,
                builder: (calendar) => calendar.days.isEmpty
                    ? const Padding(
                        padding: EdgeInsets.all(16),
                        child: Text('No calendar activity to show.'),
                      )
                    : Column(
                        children: calendar.days
                            .map(
                              (day) => ListTile(
                                title: Text(day.date),
                                subtitle: Text(
                                  'Income ${day.income} ${calendar.currency} · '
                                  'Expense ${day.expense} ${calendar.currency}',
                                ),
                                trailing:
                                    Text('${day.transactionCount} transactions'),
                              ),
                            )
                            .toList(),
                      ),
              ),
            ],
            const SizedBox(height: 20),
            Text('Accounts', style: Theme.of(context).textTheme.titleLarge),
            AsyncValueView(
              value: ref.watch(personalAccountsProvider(accountPage)),
              onRetry: () async =>
                  ref.invalidate(personalAccountsProvider(accountPage)),
              errorMessage: _requestErrorMessage,
              builder: (accounts) => Column(
                children: [
                  if (accounts.items.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(16),
                      child: Text('No accounts to show.'),
                    )
                  else
                    ...accounts.items.map((account) => Card(
                          child: ListTile(
                            title: Text(account.name),
                            subtitle: Text('${account.type} | ${account.status}'),
                            trailing:
                                Text('${account.balance} ${account.currency}'),
                          ),
                        )),
                  _Pagination(
                    page: accounts.page,
                    totalPages: accounts.totalPages,
                    onPageChanged: (page) =>
                        ref.read(personalAccountsPageProvider.notifier).state =
                            page,
                    label: 'account',
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            Text('Transaction history',
                style: Theme.of(context).textTheme.titleLarge),
            AsyncValueView(
              value: ref.watch(personalTransactionsProvider(transactionPage)),
              onRetry: () async =>
                  ref.invalidate(personalTransactionsProvider(transactionPage)),
              errorMessage: _requestErrorMessage,
              builder: (history) => Column(
                children: [
                  if (history.items.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(16),
                      child: Text('No transactions to show.'),
                    )
                  else
                    ...history.items.map((transaction) => Card(
                          child: ListTile(
                            title: Text(
                              transaction.description.isEmpty
                                  ? transaction.type
                                  : transaction.description,
                            ),
                            subtitle: Text(
                              '${transaction.transactionDate} · '
                              '${transaction.accountName} · ${transaction.status}',
                            ),
                            trailing: Text(
                              '${transaction.amount} ${transaction.currency}',
                            ),
                            onTap: () => context.push(
                              '/personal-finance/transactions/${Uri.encodeComponent(transaction.id)}',
                            ),
                          ),
                        )),
                  _Pagination(
                    page: history.page,
                    totalPages: history.totalPages,
                    onPageChanged: (page) => ref
                        .read(personalTransactionPageProvider.notifier)
                        .state = page,
                    label: 'transaction',
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Pagination extends StatelessWidget {
  const _Pagination({
    required this.page,
    required this.totalPages,
    required this.onPageChanged,
    required this.label,
  });

  final int page;
  final int totalPages;
  final ValueChanged<int> onPageChanged;
  final String label;

  @override
  Widget build(BuildContext context) => Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          IconButton(
            tooltip: 'Previous $label page',
            onPressed: page > 0 ? () => onPageChanged(page - 1) : null,
            icon: const Icon(Icons.chevron_left),
          ),
          Text(totalPages == 0
              ? 'Page 0 of 0'
              : 'Page ${page + 1} of $totalPages'),
          IconButton(
            tooltip: 'Next $label page',
            onPressed:
                page + 1 < totalPages ? () => onPageChanged(page + 1) : null,
            icon: const Icon(Icons.chevron_right),
          ),
        ],
      );
}

String _requestErrorMessage(Object error) =>
    error is ApiException && error.statusCode == 401
        ? 'Your session has expired. Please sign in again.'
        : 'Could not load this information. Please retry.';
