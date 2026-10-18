import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_client.dart';
import '../../../core/session/auth_state.dart';
import '../../../core/widgets/async_value_view.dart';
import '../data/models/personal_finance_models.dart';
import '../data/providers.dart';

final personalAccountsPageProvider = StateProvider<int>((ref) {
  ref.watch(authSubjectEpochProvider);
  return 0;
});
final personalTransactionPageProvider = StateProvider<int>((ref) {
  ref.watch(authSubjectEpochProvider);
  return 0;
});
final personalCurrencyProvider = StateProvider<String>((ref) {
  ref.watch(authSubjectEpochProvider);
  return '';
});
final personalAccountsProvider =
    FutureProvider.family<PageResult<PersonalAccount>, int>((ref, page) {
  ref.watch(authSubjectEpochProvider);
  return ref.watch(personalFinanceRepositoryProvider).listAccounts(page: page);
});
final personalTransactionsProvider =
    FutureProvider.family<TransactionHistoryPage, int>((ref, page) {
  ref.watch(authSubjectEpochProvider);
  return ref
      .watch(personalFinanceRepositoryProvider)
      .listTransactions(page: page);
});
final personalSummaryProvider =
    FutureProvider.family<FinancialSummary, String>((ref, currency) {
  ref.watch(authSubjectEpochProvider);
  return ref
      .watch(personalFinanceRepositoryProvider)
      .getSummary(currency: currency);
});
final personalMonthSummaryProvider =
    FutureProvider.family<FinancialSummary, (String, String, String)>(
        (ref, range) {
  ref.watch(authSubjectEpochProvider);
  return ref.watch(personalFinanceRepositoryProvider).getSummary(
        currency: range.$1,
        from: range.$2,
        to: range.$3,
      );
});
final personalCalendarProvider =
    FutureProvider.family<CalendarSummary, String>((ref, currency) {
  ref.watch(authSubjectEpochProvider);
  return ref
      .watch(personalFinanceRepositoryProvider)
      .getCalendar(currency: currency);
});

enum PersonalFinanceSection { overview, transactions, accounts }

class PersonalFinancePage extends ConsumerStatefulWidget {
  const PersonalFinancePage(
      {super.key, this.section = PersonalFinanceSection.overview});
  final PersonalFinanceSection section;

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
    ref.invalidate(
        personalAccountsProvider(ref.read(personalAccountsPageProvider)));
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

    if (widget.section == PersonalFinanceSection.transactions) {
      return Scaffold(
        body: SafeArea(
          child: _TransactionLanding(
            page: transactionPage,
            currency: currency,
            transactions:
                ref.watch(personalTransactionsProvider(transactionPage)),
            monthSummary: currency.isEmpty
                ? null
                : ref.watch(
                    personalMonthSummaryProvider(_currentMonthRange(currency))),
            onCurrencyChanged: (value) =>
                ref.read(personalCurrencyProvider.notifier).state = value,
            onPageChanged: (page) =>
                ref.read(personalTransactionPageProvider.notifier).state = page,
            onRefresh: _refresh,
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
          title: Text(switch (widget.section) {
        PersonalFinanceSection.overview => 'Personal finance',
        PersonalFinanceSection.transactions => 'Transactions',
        PersonalFinanceSection.accounts => 'Accounts',
      })),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          children: [
            if (widget.section != PersonalFinanceSection.accounts) ...[
              Text('Summary and calendar',
                  style: Theme.of(context).textTheme.titleLarge),
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
                  child: Text(
                      'Enter a currency to load summary and calendar totals.'),
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
                          Text(
                              'Expense: ${summary.expense} ${summary.currency}'),
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
                                  trailing: Text(
                                      '${day.transactionCount} transactions'),
                                ),
                              )
                              .toList(),
                        ),
                ),
              ],
              const SizedBox(height: 20),
            ],
            if (widget.section != PersonalFinanceSection.transactions) ...[
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
                              subtitle:
                                  Text('${account.type} | ${account.status}'),
                              trailing: Text(
                                  '${account.balance} ${account.currency}'),
                            ),
                          )),
                    _Pagination(
                      page: accounts.page,
                      totalPages: accounts.totalPages,
                      onPageChanged: (page) => ref
                          .read(personalAccountsPageProvider.notifier)
                          .state = page,
                      label: 'account',
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
            ],
            if (widget.section != PersonalFinanceSection.accounts) ...[
              Text('Transaction history',
                  style: Theme.of(context).textTheme.titleLarge),
              AsyncValueView(
                value: ref.watch(personalTransactionsProvider(transactionPage)),
                onRetry: () async => ref
                    .invalidate(personalTransactionsProvider(transactionPage)),
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
          ],
        ),
      ),
    );
  }
}

class _TransactionLanding extends StatefulWidget {
  const _TransactionLanding({
    required this.page,
    required this.currency,
    required this.transactions,
    required this.monthSummary,
    required this.onCurrencyChanged,
    required this.onPageChanged,
    required this.onRefresh,
  });

  final int page;
  final String currency;
  final AsyncValue<TransactionHistoryPage> transactions;
  final AsyncValue<FinancialSummary>? monthSummary;
  final ValueChanged<String> onCurrencyChanged;
  final ValueChanged<int> onPageChanged;
  final Future<void> Function() onRefresh;

  @override
  State<_TransactionLanding> createState() => _TransactionLandingState();
}

enum _TransactionFilter { all, expense, income }

class _TransactionLandingState extends State<_TransactionLanding> {
  final _search = TextEditingController();
  _TransactionFilter _filter = _TransactionFilter.all;

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final monthText = 'Tháng ${now.month}, ${now.year}';
    return RefreshIndicator(
      onRefresh: widget.onRefresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 10, 20, 28),
        children: [
          Row(children: [
            const Icon(Icons.eco, color: Color(0xff15966e), size: 30),
            const SizedBox(width: 8),
            Text('SaveMon',
                style: Theme.of(context)
                    .textTheme
                    .titleLarge
                    ?.copyWith(fontWeight: FontWeight.w800)),
            const Spacer(),
            const Icon(Icons.notifications_none, color: Color(0xff16333c)),
          ]),
          const SizedBox(height: 18),
          Text('Giao dịch',
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w800, color: const Color(0xff12353a))),
          const SizedBox(height: 3),
          const Text('Theo dõi chi tiêu và thu nhập của bạn.',
              style: TextStyle(color: Color(0xff687a9b))),
          const SizedBox(height: 16),
          Row(children: [
            Expanded(
                child: TextField(
                    controller: _search,
                    onChanged: (_) => setState(() {}),
                    decoration: const InputDecoration(
                        prefixIcon: Icon(Icons.search),
                        hintText: 'Tìm trong giao dịch đã tải...'))),
            const SizedBox(width: 8),
            Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                    color: Colors.white,
                    border: Border.all(color: const Color(0xffe9edf0)),
                    borderRadius: BorderRadius.circular(16)),
                child: const Icon(Icons.filter_alt_outlined,
                    color: Color(0xff243b50))),
          ]),
          const SizedBox(height: 14),
          SegmentedButton<_TransactionFilter>(
            segments: const [
              ButtonSegment(
                  value: _TransactionFilter.all, label: Text('Tất cả')),
              ButtonSegment(
                  value: _TransactionFilter.expense, label: Text('Chi tiêu')),
              ButtonSegment(
                  value: _TransactionFilter.income, label: Text('Thu nhập')),
            ],
            selected: {_filter},
            onSelectionChanged: (values) =>
                setState(() => _filter = values.first),
            showSelectedIcon: false,
          ),
          const SizedBox(height: 14),
          _monthCard(context, monthText),
          const SizedBox(height: 10),
          widget.transactions.when(
            loading: () => const Padding(
                padding: EdgeInsets.all(28),
                child: Center(child: CircularProgressIndicator())),
            error: (error, stack) => _errorState(context, error),
            data: (history) {
              final query = _search.text.trim().toLowerCase();
              final matching = history.items.where((tx) {
                final isIncome = tx.type.toUpperCase() == 'INCOME';
                if (_filter == _TransactionFilter.expense && isIncome) {
                  return false;
                }
                if (_filter == _TransactionFilter.income && !isIncome) {
                  return false;
                }
                return query.isEmpty ||
                    tx.description.toLowerCase().contains(query) ||
                    tx.accountName.toLowerCase().contains(query) ||
                    tx.items.any((item) =>
                        item.categoryName.toLowerCase().contains(query));
              }).toList();
              if (matching.isEmpty) {
                return Padding(
                    padding: const EdgeInsets.all(22),
                    child: Text(
                        history.items.isEmpty
                            ? 'Chưa có giao dịch.'
                            : 'Không tìm thấy giao dịch phù hợp.',
                        textAlign: TextAlign.center));
              }
              final grouped = <String, List<PersonalTransaction>>{};
              for (final tx in matching) {
                grouped
                    .putIfAbsent(_transactionDay(tx.transactionDate), () => [])
                    .add(tx);
              }
              return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    for (final entry in grouped.entries) ...[
                      Padding(
                          padding: const EdgeInsets.fromLTRB(2, 12, 2, 8),
                          child: Row(children: [
                            Expanded(
                                child: Text(
                                    entry.key ==
                                            _dateOnly(DateTime.now().toUtc())
                                        ? 'Hôm nay'
                                        : entry.key ==
                                                _dateOnly(DateTime.now()
                                                    .toUtc()
                                                    .subtract(const Duration(
                                                        days: 1)))
                                            ? 'Hôm qua'
                                            : entry.key,
                                    style: const TextStyle(
                                        fontSize: 17,
                                        fontWeight: FontWeight.w700,
                                        color: Color(0xff142c45)))),
                            Text('${entry.value.length} giao dịch',
                                style:
                                    const TextStyle(color: Color(0xff74819a)))
                          ])),
                      Card(
                          elevation: 0,
                          margin: EdgeInsets.zero,
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(17),
                              side: const BorderSide(color: Color(0xffedf0f2))),
                          child: Padding(
                              padding:
                                  const EdgeInsets.symmetric(horizontal: 14),
                              child: Column(children: [
                                for (var i = 0; i < entry.value.length; i++)
                                  _transactionRow(context, entry.value[i],
                                      i < entry.value.length - 1)
                              ]))),
                    ],
                    _Pagination(
                        page: history.page,
                        totalPages: history.totalPages,
                        onPageChanged: widget.onPageChanged,
                        label: 'giao dịch'),
                  ]);
            },
          ),
        ],
      ),
    );
  }

  Widget _monthCard(BuildContext context, String monthText) => Card(
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(17),
          side: const BorderSide(color: Color(0xffedf0f2))),
      child: Padding(
          padding: const EdgeInsets.all(16),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Expanded(
                  child: Text(monthText,
                      style: const TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w700,
                          color: Color(0xff142c45)))),
              const Icon(Icons.keyboard_arrow_down, color: Color(0xff576b87))
            ]),
            if (widget.currency.isEmpty) ...[
              const SizedBox(height: 12),
              TextField(
                  textCapitalization: TextCapitalization.characters,
                  decoration: const InputDecoration(
                      labelText: 'Đơn vị tiền tệ', hintText: 'VND'),
                  onSubmitted: widget.onCurrencyChanged),
            ] else ...[
              const SizedBox(height: 14),
              widget.monthSummary!.when(
                loading: () => const SizedBox(
                    height: 65,
                    child: Center(child: CircularProgressIndicator())),
                error: (error, stack) => _errorState(context, error),
                data: (summary) => Row(children: [
                  Expanded(
                      child: _summaryColumn(
                          'Tổng chi tiêu',
                          summary.expense,
                          summary.currency,
                          const Color(0xffed4057),
                          Icons.bar_chart)),
                  Container(
                      width: 1, height: 72, color: const Color(0xffe5e9ee)),
                  Expanded(
                      child: _summaryColumn(
                          'Tổng thu nhập',
                          summary.income,
                          summary.currency,
                          const Color(0xff07965f),
                          Icons.track_changes)),
                ]),
              ),
            ],
          ])));

  Widget _summaryColumn(String label, num amount, String currency, Color color,
          IconData icon) =>
      Padding(
          padding: const EdgeInsets.symmetric(horizontal: 6),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Icon(icon, color: color, size: 20),
              const SizedBox(width: 6),
              Flexible(
                  child: Text(label,
                      style: const TextStyle(
                          color: Color(0xff60718b), fontSize: 12)))
            ]),
            const SizedBox(height: 5),
            Text('${_groupAmount(amount)} $currency',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                    color: color, fontSize: 16, fontWeight: FontWeight.w800))
          ]));

  Widget _transactionRow(
      BuildContext context, PersonalTransaction tx, bool divider) {
    final income = tx.type.toUpperCase() == 'INCOME';
    final name = tx.description.isEmpty
        ? (income
            ? 'Thu nhập'
            : tx.items.isNotEmpty
                ? tx.items.first.categoryName
                : tx.accountName)
        : tx.description;
    final color = income ? const Color(0xff07965f) : const Color(0xff17263d);
    return Column(children: [
      ListTile(
          contentPadding: EdgeInsets.zero,
          minLeadingWidth: 42,
          leading: CircleAvatar(
              radius: 21,
              backgroundColor:
                  income ? const Color(0xffe3f5ed) : const Color(0xffeef2f2),
              child: Icon(
                  income
                      ? Icons.account_balance_wallet_outlined
                      : Icons.receipt_long,
                  color: income
                      ? const Color(0xff07965f)
                      : const Color(0xff158761),
                  size: 21)),
          title: Text(name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style:
                  const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
          subtitle: Text(
              tx.items.isEmpty
                  ? tx.accountName
                  : '${tx.items.first.categoryName}  ·  ${tx.accountName}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Color(0xff7885a0), fontSize: 12)),
          trailing: Row(mainAxisSize: MainAxisSize.min, children: [
            Text(
                '${income ? '+' : '-'} ${_groupAmount(tx.amount)} ${tx.currency}',
                style: TextStyle(
                    fontWeight: FontWeight.w700, color: color, fontSize: 13)),
            const SizedBox(width: 3),
            const Icon(Icons.chevron_right, color: Color(0xff526984))
          ]),
          onTap: () => context.push(
              '/personal-finance/transactions/${Uri.encodeComponent(tx.id)}')),
      if (divider)
        const Divider(height: 1, indent: 56, color: Color(0xffedf0f2))
    ]);
  }
}

Widget _errorState(BuildContext context, Object error) => Padding(
    padding: const EdgeInsets.all(18),
    child: Text(_requestErrorMessage(error), textAlign: TextAlign.center));

(String, String, String) _currentMonthRange(String currency) {
  final now = DateTime.now();
  final from = DateTime(now.year, now.month, 1);
  final to = DateTime(now.year, now.month + 1, 0);
  return (currency, _dateOnly(from), _dateOnly(to));
}

String _dateOnly(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';

String _transactionDay(String value) {
  final parsed = DateTime.tryParse(value);
  if (parsed == null || value.length == 10) return value;
  return _dateOnly(parsed.toUtc());
}

String _groupAmount(num amount) {
  final raw = amount.round().toString();
  final out = StringBuffer();
  for (var i = 0; i < raw.length; i++) {
    if (i > 0 && (raw.length - i) % 3 == 0) out.write('.');
    out.write(raw[i]);
  }
  return out.toString();
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
