import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/features/personal_finance/data/personal_finance_repository.dart';

void main() {
  group('PersonalFinanceRepository', () {
    test('lists accounts and parses the documented response', () async {
      Uri? requestedUri;
      final repository = _repository((request) async {
        requestedUri = request.url;
        return http.Response(
          '{"items":[{"id":"account-1","name":"Cash","type":"CASH",'
          '"currency":"VND","balance":2500000,"status":"ACTIVE"}],'
          '"page":0,"size":20,"totalItems":1,"totalPages":1}',
          200,
        );
      });

      final accounts = await repository.listAccounts(status: 'ACTIVE');

      expect(
        requestedUri.toString(),
        'https://api.test/api/v1/accounts?status=ACTIVE&page=0&size=20',
      );
      expect(accounts.items, hasLength(1));
      expect(accounts.items.single.id, 'account-1');
      expect(accounts.items.single.name, 'Cash');
      expect(accounts.items.single.balance, 2500000);
      expect(accounts.items.single.currency, 'VND');
      expect(accounts.items.single.status, 'ACTIVE');
      expect(accounts.page, 0);
      expect(accounts.size, 20);
      expect(accounts.totalItems, 1);
      expect(accounts.totalPages, 1);
    });

    test(
      'returns an empty account collection for an empty response list',
      () async {
        final repository = _repository(
          (_) async => http.Response(
            '{"items":[],"page":0,"size":20,"totalItems":0,"totalPages":0}',
            200,
          ),
        );

        expect((await repository.listAccounts()).items, isEmpty);
      },
    );

    test('requests the selected account page and size', () async {
      Uri? requestedUri;
      final repository = _repository((request) async {
        requestedUri = request.url;
        return http.Response(
          '{"items":[],"page":2,"size":10,"totalItems":21,"totalPages":3}',
          200,
        );
      });

      final result = await repository.listAccounts(page: 2, size: 10);

      expect(requestedUri!.queryParameters, {'page': '2', 'size': '10'});
      expect(result.page, 2);
      expect(result.size, 10);
      expect(result.totalItems, 21);
      expect(result.totalPages, 3);
    });

    test(
      'queries calendar with required currency and parses daily summaries',
      () async {
        Uri? requestedUri;
        final repository = _repository((request) async {
          requestedUri = request.url;
          return http.Response(
            '{"currency":"VND","days":[{"date":"2026-10-08","income":15000000,'
            '"expense":350000,"transactionCount":4}]}',
            200,
          );
        });

        final days = await repository.getCalendar(
          currency: 'VND',
          from: '2026-10-01',
          to: '2026-10-31',
        );

        expect(
          requestedUri.toString(),
          'https://api.test/api/v1/calendar?currency=VND&from=2026-10-01&to=2026-10-31',
        );
        expect(days.currency, 'VND');
        expect(days.days.single.date, '2026-10-08');
        expect(days.days.single.income, 15000000);
        expect(days.days.single.expense, 350000);
        expect(days.days.single.transactionCount, 4);
      },
    );

    test(
      'queries summary for the given range without deriving totals',
      () async {
        Uri? requestedUri;
        final repository = _repository((request) async {
          requestedUri = request.url;
          return http.Response(
            '{"currency":"VND","income":25000000,'
            '"expense":12000000,"net":13000000}',
            200,
          );
        });

        final summary = await repository.getSummary(
          currency: 'VND',
          from: '2026-10-01',
          to: '2026-10-31',
        );

        expect(
          requestedUri.toString(),
          'https://api.test/api/v1/summary?currency=VND&from=2026-10-01&to=2026-10-31',
        );
        expect(summary.currency, 'VND');
        expect(summary.income, 25000000);
        expect(summary.expense, 12000000);
        expect(summary.net, 13000000);
      },
    );

    test(
      'rejects response data that does not match the documented schema',
      () async {
        final repository = _repository(
          (_) async => http.Response('{"items":[{"id":"account-1"}]}', 200),
        );

        await expectLater(repository.listAccounts(), throwsFormatException);
      },
    );

    test(
      'loads transaction detail using its documented response shape',
      () async {
        Uri? requestedUri;
        final repository = _repository((request) async {
          requestedUri = request.url;
          return http.Response(
            '{"id":"transaction-1","type":"EXPENSE","amount":200000,'
            '"currency":"VND","transactionDate":"2026-10-08T12:00:00Z",'
            '"description":"Lunch","status":"ACTIVE",'
            '"account":{"id":"account-1","name":"Cash"},'
            '"items":[{"categoryId":"category-1","categoryName":"Food",'
            '"amount":200000}]}',
            200,
          );
        });

        final transaction = await repository.getTransaction('transaction-1');

        expect(requestedUri!.path, '/api/v1/transactions/transaction-1');
        expect(transaction.id, 'transaction-1');
        expect(transaction.accountName, 'Cash');
        expect(transaction.items.single.categoryName, 'Food');
        expect(transaction.items.single.amount, 200000);
      },
    );

    test(
      'lists paginated transaction history with documented filters',
      () async {
        Uri? requestedUri;
        final repository = _repository((request) async {
          requestedUri = request.url;
          return http.Response(
            '{"items":[{"id":"transaction-1","type":"EXPENSE","amount":200000,'
            '"currency":"VND","transactionDate":"2026-10-08T12:00:00Z",'
            '"description":"Lunch","status":"ACTIVE",'
            '"account":{"id":"account-1","name":"Cash"},'
            '"items":[{"categoryId":"category-1","categoryName":"Food",'
            '"amount":200000}]}],"page":1,"size":10,"totalItems":21,'
            '"totalPages":3}',
            200,
          );
        });

        final history = await repository.listTransactions(
          page: 1,
          size: 10,
          accountId: 'account 1',
          type: 'EXPENSE',
          status: 'ACTIVE',
          from: '2026-10-01',
          to: '2026-10-31',
          categoryId: 'food',
          currency: 'VND',
        );

        expect(requestedUri!.path, '/api/v1/transactions');
        expect(requestedUri!.queryParameters, {
          'page': '1',
          'size': '10',
          'accountId': 'account 1',
          'type': 'EXPENSE',
          'status': 'ACTIVE',
          'from': '2026-10-01',
          'to': '2026-10-31',
          'categoryId': 'food',
          'currency': 'VND',
        });
        expect(history.items.single.description, 'Lunch');
        expect(history.page, 1);
        expect(history.size, 10);
        expect(history.totalItems, 21);
        expect(history.totalPages, 3);
      },
    );

    test('requires integer totalPages in transaction history responses',
        () async {
      final repository = _repository(
        (_) async => http.Response(
          '{"items":[],"page":0,"size":20,"totalItems":0}',
          200,
        ),
      );

      await expectLater(
        repository.listTransactions(),
        throwsFormatException,
      );
    });

    test('rejects negative transaction history totalPages', () async {
      final repository = _repository(
        (_) async => http.Response(
          '{"items":[],"page":0,"size":20,"totalItems":0,"totalPages":-1}',
          200,
        ),
      );

      await expectLater(
        repository.listTransactions(),
        throwsFormatException,
      );
    });

    test('propagates API failures without fabricating data', () async {
      final repository = _repository(
        (_) async => http.Response('{"code":"ACCESS_DENIED"}', 403),
      );

      await expectLater(
        repository.listAccounts(),
        throwsA(
          isA<ApiException>().having(
            (error) => error.statusCode,
            'statusCode',
            403,
          ),
        ),
      );
    });
  });
}

PersonalFinanceRepository _repository(
  Future<http.Response> Function(http.Request) handler,
) {
  return PersonalFinanceRepository(
    ApiClient(
      baseUri: Uri.parse('https://api.test/api/v1'),
      httpClient: MockClient(handler),
      readSession: () async => null,
      onUnauthorized: () async {},
    ),
  );
}
