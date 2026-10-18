const scanDraftFields = {
  'paidTotal',
  'transactionDate',
  'currency',
  'merchant',
};

const scanReasonCodes = {
  'PAYMENT_QR',
  'UNSUPPORTED_QR_FORMAT',
  'UNRELATED_IMAGE',
  'UNSUPPORTED_BILL_CONTENT',
  'UNREADABLE_BILL',
};

enum ScanOutcome { draft, unsupported, manualEntry }

class ExpenseScanDraft {
  const ExpenseScanDraft({
    required this.paidTotal,
    required this.currency,
    required this.transactionDate,
    required this.merchant,
    required this.missingOrAmbiguousFields,
  });

  final String? paidTotal;
  final String? currency;
  final String? transactionDate;
  final String? merchant;
  final List<String> missingOrAmbiguousFields;

  factory ExpenseScanDraft.fromJson(Map<String, dynamic> json) {
    for (final field in [
      'paidTotal',
      'currency',
      'transactionDate',
      'merchant',
      'missingOrAmbiguousFields',
    ]) {
      if (!json.containsKey(field)) {
        throw FormatException('Missing scan draft field: $field.');
      }
    }

    final paidTotal = _nullableString(json, 'paidTotal');
    if (paidTotal != null && !_validPaidTotal(paidTotal)) {
      throw const FormatException('Invalid scan total.');
    }
    final currency = _nullableString(json, 'currency');
    if (currency != null && !RegExp(r'^[A-Z]{3}$').hasMatch(currency)) {
      throw const FormatException('Invalid scan currency.');
    }
    final date = _nullableString(json, 'transactionDate');
    if (date != null && !_validDate(date)) {
      throw const FormatException('Invalid scan date.');
    }
    final merchant = _nullableString(json, 'merchant');
    if (merchant != null &&
        (merchant.trim().isEmpty || merchant.runes.length > 500)) {
      throw const FormatException('Invalid scan merchant.');
    }
    final rawMissing = json['missingOrAmbiguousFields'];
    if (rawMissing is! List ||
        rawMissing.length > scanDraftFields.length ||
        rawMissing.any(
          (field) => field is! String || !scanDraftFields.contains(field),
        ) ||
        rawMissing.toSet().length != rawMissing.length) {
      throw const FormatException('Invalid missing scan fields.');
    }

    return ExpenseScanDraft(
      paidTotal: paidTotal,
      currency: currency,
      transactionDate: date,
      merchant: merchant,
      missingOrAmbiguousFields: rawMissing.cast<String>().toList(
            growable: false,
          ),
    );
  }
}

class ExpenseScanResult {
  const ExpenseScanResult({
    required this.outcome,
    required this.draft,
    required this.reasonCode,
  });

  final ScanOutcome outcome;
  final ExpenseScanDraft? draft;
  final String? reasonCode;

  factory ExpenseScanResult.fromJson(Map<String, dynamic> json) {
    final outcomeValue = json['outcome'];
    final manualEntryAllowed = json['manualEntryAllowed'];
    if (manualEntryAllowed != true) {
      throw const FormatException('Manual entry must be allowed.');
    }

    final ScanOutcome outcome;
    switch (outcomeValue) {
      case 'DRAFT':
        outcome = ScanOutcome.draft;
        break;
      case 'UNSUPPORTED':
        outcome = ScanOutcome.unsupported;
        break;
      case 'MANUAL_ENTRY':
        outcome = ScanOutcome.manualEntry;
        break;
      default:
        throw const FormatException('Unknown scan outcome.');
    }

    final rawDraft = json['draft'];
    final draft = rawDraft == null
        ? null
        : ExpenseScanDraft.fromJson(_object(rawDraft, 'draft'));
    final rawReason = json['reasonCode'];
    if (rawReason != null &&
        (rawReason is! String || !scanReasonCodes.contains(rawReason))) {
      throw const FormatException('Unknown scan reason code.');
    }

    if ((outcome == ScanOutcome.draft) != (draft != null) ||
        (draft == null) != (rawReason is String)) {
      throw const FormatException('Inconsistent scan outcome.');
    }
    if ((outcome == ScanOutcome.manualEntry &&
            rawReason != 'UNREADABLE_BILL') ||
        (outcome == ScanOutcome.unsupported &&
            rawReason == 'UNREADABLE_BILL')) {
      throw const FormatException('Scan reason does not match outcome.');
    }

    return ExpenseScanResult(
      outcome: outcome,
      draft: draft,
      reasonCode: rawReason as String?,
    );
  }
}

bool _validPaidTotal(String value) {
  if (!RegExp(r'^(?:0|[1-9][0-9]{0,14})(?:\.[0-9]{1,4})?$').hasMatch(value)) {
    return false;
  }
  final numeric = double.tryParse(value);
  return numeric != null && numeric > 0;
}

bool _validDate(String value) {
  if (!RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value)) return false;
  final parsed = DateTime.tryParse(value);
  return parsed != null &&
      parsed.year.toString().padLeft(4, '0') == value.substring(0, 4) &&
      parsed.month.toString().padLeft(2, '0') == value.substring(5, 7) &&
      parsed.day.toString().padLeft(2, '0') == value.substring(8, 10);
}

String? _nullableString(Map<String, dynamic> json, String field) {
  final value = json[field];
  if (value == null) return null;
  if (value is String) return value;
  throw FormatException('Expected "$field" to be a string or null.');
}

Map<String, dynamic> _object(Object? value, String label) {
  if (value is Map<String, dynamic>) return value;
  throw FormatException('Expected "$label" to be an object.');
}
