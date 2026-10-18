import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Volatile values copied from the user's reviewed receipt draft.
class ReceiptExpensePrefill {
  const ReceiptExpensePrefill({
    required this.amount,
    required this.currency,
    required this.civilDate,
    required this.merchant,
  });

  final String amount;
  final String currency;
  final String civilDate;
  final String? merchant;
}

class PersonalReceiptExpenseIntent {
  const PersonalReceiptExpenseIntent({
    required this.accountId,
    required this.amount,
    required this.currency,
    required this.transactionDate,
    required this.datePrecision,
    required this.description,
    required this.idempotencyKey,
    required this.items,
    this.categoryId,
  });

  final String accountId;
  final String? categoryId;
  final String amount;
  final String currency;
  final String transactionDate;
  final String datePrecision;
  final String description;
  final String idempotencyKey;
  final List<PersonalReceiptExpenseItem> items;
}

class PersonalReceiptExpenseItem {
  const PersonalReceiptExpenseItem(
      {required this.categoryId, required this.amount});
  final String categoryId;
  final String amount;
}

enum ReceiptExpenseFundingChoice { vault, member }

class ReceiptExpenseSplitChoice {
  const ReceiptExpenseSplitChoice(
      {required this.memberId, required this.amount});
  final String memberId;
  final String amount;
}

class SharedReceiptExpenseIntent {
  const SharedReceiptExpenseIntent({
    required this.vaultId,
    required this.funding,
    required this.amount,
    required this.currency,
    required this.transactionDate,
    required this.datePrecision,
    required this.description,
    required this.splits,
    required this.idempotencyKey,
    this.payerMemberId,
    this.sourceAccountId,
  });

  final String vaultId;
  final ReceiptExpenseFundingChoice funding;
  final String? payerMemberId;
  final String? sourceAccountId;
  final String amount;
  final String currency;
  final String transactionDate;
  final String datePrecision;
  final String description;
  final List<ReceiptExpenseSplitChoice> splits;
  final String idempotencyKey;
}

abstract interface class ReceiptExpenseCommandPort {
  Future<void> recordPersonal(PersonalReceiptExpenseIntent intent);
  Future<void> recordShared(SharedReceiptExpenseIntent intent);
}

class ReceiptExpenseCommandUnavailable implements Exception {
  const ReceiptExpenseCommandUnavailable();
}

class DisabledReceiptExpenseCommandPort implements ReceiptExpenseCommandPort {
  const DisabledReceiptExpenseCommandPort();

  @override
  Future<void> recordPersonal(PersonalReceiptExpenseIntent intent) =>
      Future.error(const ReceiptExpenseCommandUnavailable());

  @override
  Future<void> recordShared(SharedReceiptExpenseIntent intent) =>
      Future.error(const ReceiptExpenseCommandUnavailable());
}

final receiptExpenseCommandPortProvider = Provider<ReceiptExpenseCommandPort>(
  (ref) => const DisabledReceiptExpenseCommandPort(),
);
