import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/core/session/session.dart';
import 'package:savemon_mobile/features/qr_bill_scan/data/expense_scan_repository.dart';
import 'package:savemon_mobile/features/qr_bill_scan/data/scan_models.dart';

void main() {
  group('ExpenseScanRepository', () {
    test(
      'rejects unsupported bytes without uploading them',
      () async {
        var requests = 0;
        final repository = ExpenseScanRepository(
          ApiClient(
            baseUri: Uri.parse('https://api.example.test/api/v1/'),
            httpClient: MockClient((_) async {
              requests++;
              return http.Response('', 200);
            }),
            readSession: () async => null,
            onUnauthorized: () async {},
          ),
        );

        await expectLater(
          repository.scan(Uint8List.fromList([1, 2, 3])),
          throwsA(
            isA<ScanRequestException>().having(
              (error) => error.kind,
              'kind',
              ScanFailureKind.invalidImage,
            ),
          ),
        );
        expect(requests, 0);
      },
    );

    test('sends one in-memory image to the documented scan route', () async {
      final image = Uint8List.fromList([0xff, 0xd8, 0xff, 0x00]);
      final repository = ExpenseScanRepository(
        ApiClient(
          baseUri: Uri.parse('https://api.example.test/api/v1/'),
          httpClient: MockClient((request) async {
            expect(request, isA<http.Request>());
            final multipart = request;
            expect(multipart.method, 'POST');
            expect(
              multipart.url.toString(),
              'https://api.example.test/api/v1/expense-scans',
            );
            expect(
              multipart.headers['Idempotency-Key'],
              matches(
                RegExp(
                  r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-'
                  r'[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
                ),
              ),
            );
            expect(
              multipart.headers['Content-Type'],
              startsWith('multipart/form-data; boundary=savemon-'),
            );
            final rawBody = String.fromCharCodes(multipart.bodyBytes);
            expect(rawBody, contains('name="image"; filename="scan-upload"'));
            expect(rawBody, contains('Content-Type: image/jpeg'));
            expect(rawBody, contains('\r\n\r\n'));
            return http.Response(
              '{"outcome":"MANUAL_ENTRY","draft":null,'
              '"reasonCode":"UNREADABLE_BILL","manualEntryAllowed":true}',
              200,
            );
          }),
          readSession: () async =>
              const Session(accessToken: 'test-access-token'),
          onUnauthorized: () async {},
        ),
      );

      final result = await repository.scan(image);

      expect(result.outcome, ScanOutcome.manualEntry);
      expect(result.reasonCode, 'UNREADABLE_BILL');
    });

    test('maps entitlement and extraction unavailable separately', () async {
      for (final fixture in [
        (
          body: '{"code":"PREMIUM_ENTITLEMENT_UNAVAILABLE"}',
          failure: ScanFailureKind.premiumUnavailable,
        ),
        (
          body: '{"code":"BILL_EXTRACTION_UNAVAILABLE"}',
          failure: ScanFailureKind.extractionUnavailable,
        ),
        (
          body: '{"code":"BILL_EXTRACTION_TIMEOUT"}',
          failure: ScanFailureKind.timeout,
        ),
      ]) {
        final repository = ExpenseScanRepository(
          ApiClient(
            baseUri: Uri.parse('https://api.example.test/api/v1'),
            httpClient: MockClient(
              (_) async => http.Response(fixture.body, 503),
            ),
            readSession: () async => null,
            onUnauthorized: () async {},
          ),
          createIdempotencyKey: () => 'fresh-test-key',
        );

        await expectLater(
          repository.scan(
            Uint8List.fromList(
              [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a],
            ),
          ),
          throwsA(
            isA<ScanRequestException>().having(
              (error) => error.kind,
              'kind',
              fixture.failure,
            ),
          ),
        );
      }
    });
  });
}
