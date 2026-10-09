class SharedVault {
  const SharedVault({
    required this.id,
    required this.name,
    required this.currency,
    required this.balance,
    required this.status,
  });

  factory SharedVault.fromJson(Map<String, dynamic> json) => SharedVault(
        id: _string(json, 'id'),
        name: _string(json, 'name'),
        currency: _string(json, 'currency'),
        balance: _number(json, 'balance'),
        status: _string(json, 'status'),
      );

  final String id;
  final String name;
  final String currency;
  final num balance;
  final String status;
}

class SharedVaultMember {
  const SharedVaultMember({
    required this.id,
    required this.userId,
    required this.displayName,
    required this.role,
    required this.status,
    required this.joinedAt,
  });

  factory SharedVaultMember.fromJson(Map<String, dynamic> json) =>
      SharedVaultMember(
        id: _string(json, 'id'),
        userId: _string(json, 'userId'),
        displayName: _string(json, 'displayName'),
        role: _string(json, 'role'),
        status: _string(json, 'status'),
        joinedAt: _string(json, 'joinedAt'),
      );

  final String id;
  final String userId;
  final String displayName;
  final String role;
  final String status;
  final String joinedAt;
}

class SharedContribution {
  const SharedContribution({
    required this.id,
    required this.vaultId,
    required this.memberId,
    required this.sourceAccountId,
    required this.amount,
    required this.currency,
    required this.description,
    required this.contributedAt,
    required this.transactionId,
    required this.status,
  });

  factory SharedContribution.fromJson(Map<String, dynamic> json) =>
      SharedContribution(
        id: _string(json, 'id'),
        vaultId: _string(json, 'vaultId'),
        memberId: _string(json, 'memberId'),
        sourceAccountId: _string(json, 'sourceAccountId'),
        amount: _number(json, 'amount'),
        currency: _string(json, 'currency'),
        description: _optionalString(json, 'description'),
        contributedAt: _string(json, 'contributedAt'),
        transactionId: _string(json, 'transactionId'),
        status: _string(json, 'status'),
      );

  final String id;
  final String vaultId;
  final String memberId;
  final String sourceAccountId;
  final num amount;
  final String currency;
  final String? description;
  final String contributedAt;
  final String transactionId;
  final String status;
}

class SharedExpenseSplit {
  const SharedExpenseSplit({
    required this.memberId,
    required this.amount,
  });

  factory SharedExpenseSplit.fromJson(Map<String, dynamic> json) =>
      SharedExpenseSplit(
        memberId: _string(json, 'memberId'),
        amount: _number(json, 'amount'),
      );

  final String memberId;
  final num amount;
}

class SharedExpense {
  const SharedExpense({
    required this.id,
    required this.vaultId,
    required this.amount,
    required this.currency,
    required this.description,
    required this.expenseDate,
    required this.fundingType,
    required this.sourceAccountId,
    required this.paidByMemberId,
    required this.splits,
    required this.transactionId,
    required this.status,
  });

  factory SharedExpense.fromJson(Map<String, dynamic> json) {
    final funding = expectJsonObject(json['funding'], 'expense funding');
    final rawSplits = json['splits'];
    if (rawSplits is! List) {
      throw const FormatException('Expected "splits" to be a list.');
    }

    return SharedExpense(
      id: _string(json, 'id'),
      vaultId: _string(json, 'vaultId'),
      amount: _number(json, 'amount'),
      currency: _string(json, 'currency'),
      description: _string(json, 'description'),
      expenseDate: _string(json, 'expenseDate'),
      fundingType: _string(funding, 'type'),
      sourceAccountId: _optionalString(funding, 'sourceAccountId'),
      paidByMemberId: _nullableString(json, 'paidByMemberId'),
      splits: rawSplits
          .map(
            (split) => SharedExpenseSplit.fromJson(
              expectJsonObject(split, 'expense split'),
            ),
          )
          .toList(growable: false),
      transactionId: _string(json, 'transactionId'),
      status: _string(json, 'status'),
    );
  }

  final String id;
  final String vaultId;
  final num amount;
  final String currency;
  final String description;
  final String expenseDate;
  final String fundingType;
  final String? sourceAccountId;
  final String? paidByMemberId;
  final List<SharedExpenseSplit> splits;
  final String transactionId;
  final String status;
}

String? _optionalString(Map<String, dynamic> json, String key) {
  if (!json.containsKey(key)) return null;
  final value = json[key];
  if (value is String) return value;
  throw FormatException('Expected "$key" to be a string when present.');
}

String? _nullableString(Map<String, dynamic> json, String key) {
  if (!json.containsKey(key)) {
    throw FormatException('Expected "$key" to be present.');
  }
  final value = json[key];
  if (value == null) return null;
  if (value is String) return value;
  throw FormatException('Expected "$key" to be a string or null.');
}

String _string(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String) return value;
  throw FormatException('Expected "$key" to be a string.');
}

num _number(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is num) return value;
  throw FormatException('Expected "$key" to be a number.');
}

int _integer(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is int) return value;
  throw FormatException('Expected "$key" to be an integer.');
}

Map<String, dynamic> expectJsonObject(Object? value, String description) {
  if (value is Map<String, dynamic>) return value;
  throw FormatException('Expected $description to be an object.');
}

List<T> parseItems<T>(
  Map<String, dynamic> json,
  T Function(Map<String, dynamic>) parse,
  String description,
) {
  final items = json['items'];
  if (items is! List) {
    throw const FormatException('Expected "items" to be a list.');
  }
  return items
      .map((item) => parse(expectJsonObject(item, description)))
      .toList(growable: false);
}

class SharedFinancePage<T> {
  const SharedFinancePage({
    required this.items,
    required this.page,
    required this.size,
    required this.totalItems,
    required this.totalPages,
  });

  factory SharedFinancePage.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) parse,
    String description,
  ) {
    final rawPage = _integer(json, 'page');
    final rawSize = _integer(json, 'size');
    final totalItems = _integer(json, 'totalItems');
    final totalPages = _integer(json, 'totalPages');
    if (rawPage < 0 ||
        rawSize < 1 ||
        rawSize > 100 ||
        totalItems < 0 ||
        totalPages < 0) {
      throw const FormatException('Invalid pagination metadata.');
    }
    return SharedFinancePage(
      items: parseItems(json, parse, description),
      page: rawPage,
      size: rawSize,
      totalItems: totalItems,
      totalPages: totalPages,
    );
  }

  final List<T> items;
  final int page;
  final int size;
  final int totalItems;
  final int totalPages;
}
