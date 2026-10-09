import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/features/shared_finance/data/shared_vault_repository.dart';

void main() {
  test('parses vault collection using documented item schema', () async {
    final repository = _repository((_) async => http.Response(
          '{"items":[{"id":"v-1","name":"Trip Fund","currency":"VND",'
          '"balance":5000000,"status":"ACTIVE"}],'
          '"page":0,"size":20,"totalItems":21,"totalPages":2}',
          200,
        ));

    final vaults = await repository.listVaults(page: 0, size: 20);

    expect(vaults.items, hasLength(1));
    expect(vaults.items.single.name, 'Trip Fund');
    expect(vaults.items.single.balance, 5000000);
    expect(vaults.page, 0);
    expect(vaults.size, 20);
    expect(vaults.totalItems, 21);
    expect(vaults.totalPages, 2);
  });

  test('forwards optional date and member expense filters', () async {
    Uri? requestedUri;
    final repository = _repository((request) async {
      requestedUri = request.url;
      return http.Response(
        '{"items":[],"page":1,"size":10,"totalItems":0,"totalPages":0}',
        200,
      );
    });

    await repository.listExpenses(
      'vault-1',
      from: '2026-10-01',
      to: '2026-10-31',
      memberId: 'member 2',
      page: 1,
      size: 10,
    );

    expect(requestedUri!.path, '/api/v1/vaults/vault-1/expenses');
    expect(requestedUri!.queryParameters, {
      'from': '2026-10-01',
      'to': '2026-10-31',
      'memberId': 'member 2',
      'page': '1',
      'size': '10',
    });
  });

  test('requests the requested vault page and size', () async {
    Uri? requestedUri;
    final repository = _repository((request) async {
      requestedUri = request.url;
      return http.Response(
        '{"items":[],"page":2,"size":10,"totalItems":25,"totalPages":3}',
        200,
      );
    });

    final result = await repository.listVaults(page: 2, size: 10);

    expect(requestedUri!.queryParameters, {'page': '2', 'size': '10'});
    expect(result.page, 2);
    expect(result.totalPages, 3);
  });

  test('parses only the documented vault detail response fields', () async {
    final repository = SharedVaultRepository(ApiClient(
      baseUri: Uri.parse('https://api.test/api/v1'),
      httpClient: MockClient((_) async => http.Response(
        '{"id":"v-1","name":"Trip Fund","currency":"VND","balance":5000000,"status":"ACTIVE"}',
        200,
      )),
      readSession: () async => null,
      onUnauthorized: () async {},
    ));

    final vault = await repository.getVault('v-1');

    expect(vault.id, 'v-1');
    expect(vault.name, 'Trip Fund');
    expect(vault.currency, 'VND');
    expect(vault.balance, 5000000);
    expect(vault.status, 'ACTIVE');
  });

  test('rejects vault payloads outside documented detail schema', () async {
    final repository = _repository(
      (_) async => http.Response('{"id":"v-1"}', 200),
    );
    await expectLater(repository.getVault('v-1'), throwsFormatException);
  });

  test('parses documented vault members', () async {
    Uri? requestedUri;
    final repository = _repository((request) async {
      requestedUri = request.url;
      return http.Response(
        '{"items":[{"id":"member-1","userId":"user-1",'
        '"displayName":"Minh","role":"OWNER","status":"ACTIVE",'
        '"joinedAt":"2026-10-08T12:00:00Z"}],'
        '"page":1,"size":10,"totalItems":11,"totalPages":2}',
        200,
      );
    });

    final members = await repository.listMembers(
      'vault-1',
      page: 1,
      size: 10,
    );

    expect(requestedUri!.queryParameters, {'page': '1', 'size': '10'});
    expect(members.items.single.id, 'member-1');
    expect(members.items.single.displayName, 'Minh');
    expect(members.items.single.role, 'OWNER');
    expect(members.totalItems, 11);
  });

  test('lists contributions with documented filters and fields', () async {
    Uri? requestedUri;
    final repository = _repository((request) async {
      requestedUri = request.url;
      return http.Response(
        '{"items":[{"id":"contribution-1","vaultId":"vault-1",'
        '"memberId":"member-1","sourceAccountId":"account-1",'
        '"amount":1000000,"currency":"VND",'
        '"description":"Monthly contribution",'
        '"contributedAt":"2026-10-08T12:00:00Z",'
        '"transactionId":"transaction-1","status":"ACTIVE"}],'
        '"page":1,"size":10,"totalItems":12,"totalPages":2}',
        200,
      );
    });

    final contributions = await repository.listContributions(
      vaultId: 'vault 1',
      memberId: 'member 1',
      from: '2026-10-01',
      to: '2026-10-31',
      page: 1,
      size: 10,
    );

    expect(requestedUri!.path, '/api/v1/vaults/vault%201/contributions');
    expect(requestedUri!.queryParameters, {
      'memberId': 'member 1',
      'from': '2026-10-01',
      'to': '2026-10-31',
      'page': '1',
      'size': '10',
    });
    expect(contributions.items.single.sourceAccountId, 'account-1');
    expect(contributions.items.single.description, 'Monthly contribution');
    expect(contributions.items.single.status, 'ACTIVE');
    expect(contributions.page, 1);
    expect(contributions.totalPages, 2);
  });

  test('accepts a contribution response with omitted description', () async {
    final repository = _repository((_) async => http.Response(
          '{"items":[{"id":"contribution-1","vaultId":"vault-1",'
          '"memberId":"member-1","sourceAccountId":"account-1",'
          '"amount":1000000,"currency":"VND",'
          '"contributedAt":"2026-10-08T12:00:00Z",'
          '"transactionId":"transaction-1","status":"ACTIVE"}],'
          '"page":0,"size":20,"totalItems":1,"totalPages":1}',
          200,
        ));

    final contributions =
        await repository.listContributions(vaultId: 'vault-1');

    expect(contributions.items.single.description, isNull);
  });

  test('parses documented member-funded expense collection resources', () async {
    Uri? requestedUri;
    final repository = _repository((request) async {
      requestedUri = request.url;
      return http.Response(
        '{"items":[{"id":"expense-1","vaultId":"vault-1",'
        '"amount":900000,"currency":"VND","description":"Dinner",'
        '"expenseDate":"2026-10-08T19:00:00Z",'
        '"funding":{"type":"MEMBER","sourceAccountId":"account-1"},'
        '"paidByMemberId":"member-1","splits":['
        '{"memberId":"member-1","amount":300000},'
        '{"memberId":"member-2","amount":600000}],'
        '"transactionId":"transaction-1","status":"ACTIVE"}],'
        '"page":1,"size":10,"totalItems":11,"totalPages":2}',
        200,
      );
    });

    final expenses = await repository.listExpenses(
      'vault-1',
      page: 1,
      size: 10,
    );

    expect(requestedUri!.path, '/api/v1/vaults/vault-1/expenses');
    expect(requestedUri!.queryParameters, {'page': '1', 'size': '10'});
    expect(expenses.items, hasLength(1));
    expect(expenses.items.single.description, 'Dinner');
    expect(expenses.items.single.fundingType, 'MEMBER');
    expect(expenses.items.single.sourceAccountId, 'account-1');
    expect(expenses.items.single.paidByMemberId, 'member-1');
    expect(expenses.items.single.splits, hasLength(2));
    expect(expenses.items.single.splits.last.amount, 600000);
    expect(expenses.items.single.transactionId, 'transaction-1');
  });

  test('rejects malformed pagination metadata', () async {
    final repository = _repository(
      (_) async => http.Response('{"items":[],"page":0}', 200),
    );

    await expectLater(repository.listVaults(), throwsFormatException);
  });

  test('parses documented vault-funded expense detail resource', () async {
    Uri? requestedUri;
    final repository = _repository((request) async {
      requestedUri = request.url;
      return http.Response(
        '{"id":"expense-1","vaultId":"vault-1","amount":1200,'
        '"currency":"VND","description":"Snacks",'
        '"expenseDate":"2026-10-08T19:00:00Z",'
        '"funding":{"type":"VAULT"},"paidByMemberId":null,'
        '"splits":[{"memberId":"member-1","amount":1200}],'
        '"transactionId":"transaction-1","status":"ACTIVE"}',
        200,
      );
    });

    final expense = await repository.getExpense(
      vaultId: 'vault 1',
      expenseId: 'expense/1',
    );

    expect(requestedUri!.path, '/api/v1/vaults/vault%201/expenses/expense%2F1');
    expect(expense.fundingType, 'VAULT');
    expect(expense.sourceAccountId, isNull);
    expect(expense.paidByMemberId, isNull);
    expect(expense.splits.single.memberId, 'member-1');
  });

  test('rejects expense payloads outside the documented response schema',
      () async {
    final repository = _repository(
      (_) async => http.Response(
        '{"id":"expense-1","amount":100}',
        200,
      ),
    );

    await expectLater(
      repository.getExpense(vaultId: 'vault-1', expenseId: 'expense-1'),
      throwsFormatException,
    );
  });
}

SharedVaultRepository _repository(
  Future<http.Response> Function(http.Request) handler,
) =>
    SharedVaultRepository(ApiClient(
      baseUri: Uri.parse('https://api.test/api/v1'),
      httpClient: MockClient(handler),
      readSession: () async => null,
      onUnauthorized: () async {},
    ));
