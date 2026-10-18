import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/features/qr_bill_scan/data/scan_models.dart';

void main() {
  group('ExpenseScanResult', () {
    test('parses only the allowlisted editable draft fields', () {
      final result = ExpenseScanResult.fromJson({
        'outcome': 'DRAFT',
        'manualEntryAllowed': true,
        'draft': {
          'paidTotal': '125000.00',
          'currency': 'VND',
          'transactionDate': '2026-10-08',
          'merchant': 'Example Market',
          'missingOrAmbiguousFields': [],
          'providerDebug': 'ignored',
        },
        'providerOutput': 'ignored',
      });

      expect(result.outcome, ScanOutcome.draft);
      expect(result.draft?.paidTotal, '125000.00');
      expect(result.draft?.merchant, 'Example Market');
      expect(result.reasonCode, isNull);
    });

    test('requires manual-entry fallback for unsupported outcomes', () {
      final result = ExpenseScanResult.fromJson({
        'outcome': 'UNSUPPORTED',
        'draft': null,
        'reasonCode': 'PAYMENT_QR',
        'manualEntryAllowed': true,
      });

      expect(result.outcome, ScanOutcome.unsupported);
      expect(result.reasonCode, 'PAYMENT_QR');
    });

    test('rejects invalid financial values and unknown classifications', () {
      expect(
        () => ExpenseScanResult.fromJson({
          'outcome': 'DRAFT',
          'manualEntryAllowed': true,
          'draft': {
            'paidTotal': '0',
            'currency': 'VND',
            'transactionDate': '2026-02-30',
            'merchant': null,
            'missingOrAmbiguousFields': [],
          },
        }),
        throwsFormatException,
      );
      expect(
        () => ExpenseScanResult.fromJson({
          'outcome': 'UNSUPPORTED',
          'draft': null,
          'reasonCode': 'ARBITRARY_QR_PAYLOAD',
          'manualEntryAllowed': true,
        }),
        throwsFormatException,
      );
    });

    test('requires financial nulls and missing-field classifications to agree',
        () {
      final fields = ['paidTotal', 'currency', 'transactionDate'];
      for (final field in fields) {
        final draft = <String, dynamic>{
          'paidTotal': '125',
          'currency': 'VND',
          'transactionDate': '2026-10-08',
          'merchant': 'Cafe',
          'missingOrAmbiguousFields': <String>[],
        };
        draft[field] = null;
        draft['missingOrAmbiguousFields'] = [field];
        expect(() => ExpenseScanDraft.fromJson(draft), returnsNormally);

        draft['missingOrAmbiguousFields'] = <String>[];
        expect(() => ExpenseScanDraft.fromJson(draft), throwsFormatException,
            reason: '$field is null but not classified as missing');

        draft[field] = field == 'paidTotal'
            ? '125'
            : field == 'currency'
                ? 'VND'
                : '2026-10-08';
        draft['missingOrAmbiguousFields'] = [field];
        expect(() => ExpenseScanDraft.fromJson(draft), throwsFormatException,
            reason: '$field is populated but classified as missing');
      }
    });

    test('rejects wholly unreadable drafts and unsupported missing-field names',
        () {
      expect(
        () => ExpenseScanDraft.fromJson({
          'paidTotal': null,
          'currency': null,
          'transactionDate': null,
          'merchant': null,
          'missingOrAmbiguousFields': [
            'paidTotal',
            'currency',
            'transactionDate',
          ],
        }),
        throwsFormatException,
      );
      expect(
        () => ExpenseScanDraft.fromJson({
          'paidTotal': '125',
          'currency': 'VND',
          'transactionDate': '2026-10-08',
          'merchant': 'Cafe',
          'missingOrAmbiguousFields': ['paymentToken'],
        }),
        throwsFormatException,
      );
    });

    test('accepts backend decimal strings with leading zeros without doubles',
        () {
      ExpenseScanDraft parse(String total, String? currency,
              {bool missingCurrency = false}) =>
          ExpenseScanDraft.fromJson({
            'paidTotal': total,
            'currency': currency,
            'transactionDate': '2026-10-08',
            'merchant': 'Cafe',
            'missingOrAmbiguousFields': missingCurrency ? ['currency'] : [],
          });

      expect(parse('00125', 'VND').paidTotal, '00125');
      expect(parse('999999999999999', 'VND').paidTotal, '999999999999999');
      expect(parse('000125.00', 'USD').paidTotal, '000125.00');
      expect(parse('0.0001', null, missingCurrency: true).paidTotal, '0.0001');
      expect(() => parse('125.01', 'VND'), throwsFormatException);
      expect(() => parse('125.001', 'USD'), throwsFormatException);
      expect(() => parse('1000000000000000', 'VND'), throwsFormatException);
      expect(() => parse('00000', 'USD'), throwsFormatException);
    });
  });
}
