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

const _supportedScanCurrencies = {'VND', 'USD'};

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
    final currency = _nullableString(json, 'currency');
    if (currency != null && !_supportedScanCurrencies.contains(currency)) {
      throw const FormatException('Invalid scan currency.');
    }
    if (paidTotal != null && !_validPaidTotal(paidTotal, currency)) {
      throw const FormatException('Invalid scan total.');
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
    final missing = rawMissing.cast<String>();
    for (final field in ['paidTotal', 'currency', 'transactionDate']) {
      final isMissing = missing.contains(field);
      final isNull = json[field] == null;
      if (isMissing != isNull) {
        throw FormatException(
          'Missing-field classification does not match "$field".',
        );
      }
    }
    if (paidTotal == null &&
        currency == null &&
        date == null &&
        merchant == null) {
      throw const FormatException('Empty scan draft.');
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

bool _validPaidTotal(String value, String? currency) {
  if (!RegExp(r'^[0-9]+(?:\.[0-9]{1,4})?$').hasMatch(value)) {
    return false;
  }
  final parts = value.split('.');
  final fraction = parts.length == 2 ? parts[1] : '';
  final rawDigits = '${parts[0]}$fraction';
  final firstNonZero = rawDigits.indexOf(RegExp(r'[1-9]'));
  if (firstNonZero < 0) return false;
  var normalizedDigits = rawDigits.substring(firstNonZero);
  var trailingZeros = 0;
  while (normalizedDigits.length > 1 && normalizedDigits.endsWith('0')) {
    normalizedDigits =
        normalizedDigits.substring(0, normalizedDigits.length - 1);
    trailingZeros++;
  }
  final normalizedScale = fraction.length - trailingZeros;
  final integerDigits =
      (normalizedDigits.length - normalizedScale).clamp(0, 1000);
  if (integerDigits > 15 || normalizedDigits.length > 19) return false;
  final int maximumScale;
  if (currency == null) {
    maximumScale = 4;
  } else if (currency == 'VND') {
    maximumScale = 0;
  } else if (currency == 'USD') {
    maximumScale = 2;
  } else {
    return false;
  }
  return normalizedScale <= maximumScale;
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
