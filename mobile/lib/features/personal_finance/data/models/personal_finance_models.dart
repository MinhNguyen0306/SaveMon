class PersonalAccount {
  const PersonalAccount({
    required this.id,
    required this.name,
    required this.type,
    required this.currency,
    required this.balance,
    required this.status,
  });

  factory PersonalAccount.fromJson(Map<String, dynamic> json) =>
      PersonalAccount(
        id: _stringField(json, 'id'),
        name: _stringField(json, 'name'),
        type: _stringField(json, 'type'),
        currency: _stringField(json, 'currency'),
        balance: _numberField(json, 'balance'),
        status: _stringField(json, 'status'),
      );

  final String id;
  final String name;
  final String type;
  final String currency;
  final num balance;
  final String status;
}

class PageResult<T> {
  const PageResult({
    required this.items,
    required this.page,
    required this.size,
    required this.totalItems,
    required this.totalPages,
  });

  final List<T> items;
  final int page;
  final int size;
  final int totalItems;
  final int totalPages;
}

class TransactionHistoryPage {
  const TransactionHistoryPage({
    required this.items,
    required this.page,
    required this.size,
    required this.totalItems,
    required this.totalPages,
  });

  final List<PersonalTransaction> items;
  final int page;
  final int size;
  final int totalItems;
  final int totalPages;
}

class CalendarDaySummary {
  const CalendarDaySummary({
    required this.date,
    required this.income,
    required this.expense,
    required this.transactionCount,
  });

  factory CalendarDaySummary.fromJson(Map<String, dynamic> json) =>
      CalendarDaySummary(
        date: _stringField(json, 'date'),
        income: _numberField(json, 'income'),
        expense: _numberField(json, 'expense'),
        transactionCount: _integerField(json, 'transactionCount'),
      );

  final String date;
  final num income;
  final num expense;
  final int transactionCount;
}

class CalendarSummary {
  const CalendarSummary({required this.currency, required this.days});

  factory CalendarSummary.fromJson(Map<String, dynamic> json) {
    final days = json['days'];
    if (days is! List) {
      throw const FormatException('Expected "days" to be a list.');
    }
    return CalendarSummary(
      currency: _stringField(json, 'currency'),
      days: days.map((day) {
        if (day is! Map<String, dynamic>) {
          throw const FormatException('Expected calendar day to be an object.');
        }
        return CalendarDaySummary.fromJson(day);
      }).toList(growable: false),
    );
  }

  final String currency;
  final List<CalendarDaySummary> days;
}

class FinancialSummary {
  const FinancialSummary({
    required this.currency,
    required this.income,
    required this.expense,
    required this.net,
  });

  factory FinancialSummary.fromJson(Map<String, dynamic> json) =>
      FinancialSummary(
        currency: _stringField(json, 'currency'),
        income: _numberField(json, 'income'),
        expense: _numberField(json, 'expense'),
        net: _numberField(json, 'net'),
      );

  final String currency;
  final num income;
  final num expense;
  final num net;
}

class PersonalTransactionItem {
  const PersonalTransactionItem({
    required this.categoryId,
    required this.categoryName,
    required this.amount,
  });

  factory PersonalTransactionItem.fromJson(Map<String, dynamic> json) =>
      PersonalTransactionItem(
        categoryId: _stringField(json, 'categoryId'),
        categoryName: _stringField(json, 'categoryName'),
        amount: _numberField(json, 'amount'),
      );

  final String categoryId;
  final String categoryName;
  final num amount;
}

class PersonalTransaction {
  const PersonalTransaction({
    required this.id,
    required this.type,
    required this.amount,
    required this.currency,
    required this.transactionDate,
    required this.description,
    required this.status,
    required this.accountId,
    required this.accountName,
    required this.items,
  });

  factory PersonalTransaction.fromJson(Map<String, dynamic> json) {
    final account = json['account'];
    final items = json['items'];
    if (account is! Map<String, dynamic>) {
      throw const FormatException('Expected "account" to be an object.');
    }
    if (items is! List) {
      throw const FormatException('Expected "items" to be a list.');
    }
    return PersonalTransaction(
      id: _stringField(json, 'id'),
      type: _stringField(json, 'type'),
      amount: _numberField(json, 'amount'),
      currency: _stringField(json, 'currency'),
      transactionDate: _stringField(json, 'transactionDate'),
      description: _stringField(json, 'description'),
      status: _stringField(json, 'status'),
      accountId: _stringField(account, 'id'),
      accountName: _stringField(account, 'name'),
      items: items
          .map((item) {
            if (item is! Map<String, dynamic>) {
              throw const FormatException(
                'Expected transaction item to be an object.',
              );
            }
            return PersonalTransactionItem.fromJson(item);
          })
          .toList(growable: false),
    );
  }

  final String id;
  final String type;
  final num amount;
  final String currency;
  final String transactionDate;
  final String description;
  final String status;
  final String accountId;
  final String accountName;
  final List<PersonalTransactionItem> items;
}

String _stringField(Map<String, dynamic> json, String field) {
  final value = json[field];
  if (value is String) return value;
  throw FormatException('Expected "$field" to be a string.');
}

num _numberField(Map<String, dynamic> json, String field) {
  final value = json[field];
  if (value is num) return value;
  throw FormatException('Expected "$field" to be a number.');
}

int _integerField(Map<String, dynamic> json, String field) {
  final value = json[field];
  if (value is int) return value;
  throw FormatException('Expected "$field" to be an integer.');
}
