import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/features/shared_finance/data/shared_finance_api.dart';

void main() {
  group('SharedFinanceApi', () {
    late List<http.Request> requests;
    late SharedFinanceApi api;

    setUp(() {
      requests = [];
      final apiClient = ApiClient(
        baseUri: Uri.parse('https://api.example.test/api/v1'),
        httpClient: MockClient((request) async {
          requests.add(request);
          return http.Response('response body', 200);
        }),
        readSession: () async => null,
        onUnauthorized: () async {},
      );
      api = SharedFinanceApi(apiClient);
    });

    test('calls vault collection and item endpoints', () async {
      await api.createVault(name: 'Trip Fund', currency: 'VND');
      await api.listVaults();
      await api.getVault('vault id');

      expect(requests.map((request) => request.method), ['POST', 'GET', 'GET']);
      expect(
        requests.map((request) => request.url.toString()),
        [
          'https://api.example.test/api/v1/vaults',
          'https://api.example.test/api/v1/vaults?page=0&size=20',
          'https://api.example.test/api/v1/vaults/vault%20id',
        ],
      );
      expect(jsonDecode(requests.first.body), {
        'name': 'Trip Fund',
        'currency': 'VND',
      });
      expect(requests.first.headers['content-type'], 'application/json');
    });

    test('uses documented member routes and request body', () async {
      await api.listMembers('vault-1');
      await api.addMember(vaultId: 'vault-1', userId: 'user-2');
      await api.removeMember(vaultId: 'vault-1', memberId: 'member-3');

      expect(
        requests.map((request) => request.url.path),
        [
          '/api/v1/vaults/vault-1/members',
          '/api/v1/vaults/vault-1/members',
          '/api/v1/vaults/vault-1/members/member-3/remove',
        ],
      );
      expect(requests.first.url.queryParameters, {'page': '0', 'size': '20'});
      expect(jsonDecode(requests[1].body), {'userId': 'user-2'});
      expect(requests[2].body, isEmpty);
    });

    test('sends contribution command and idempotency key', () async {
      await api.createContribution(
        vaultId: 'vault-1',
        sourceAccountId: 'account-1',
        amount: 1000000,
        currency: 'VND',
        contributedAt: '2026-10-08T12:00:00Z',
        idempotencyKey: 'key-1',
      );

      expect(requests.single.url.path, '/api/v1/vaults/vault-1/contributions');
      expect(requests.single.headers['idempotency-key'], 'key-1');
      expect(jsonDecode(requests.single.body), {
        'sourceAccountId': 'account-1',
        'amount': 1000000,
        'currency': 'VND',
        'contributedAt': '2026-10-08T12:00:00Z',
      });
    });

    test('encodes documented contribution filters', () async {
      await api.listContributions(
        vaultId: 'vault-1',
        memberId: 'member 2',
        from: '2026-10-01',
        to: '2026-10-08',
        page: 2,
        size: 10,
      );

      expect(requests.single.url.path, '/api/v1/vaults/vault-1/contributions');
      expect(requests.single.url.queryParameters, {
        'memberId': 'member 2',
        'from': '2026-10-01',
        'to': '2026-10-08',
        'page': '2',
        'size': '10',
      });
    });

    test('sends shared expense funding, splits, and idempotency key', () async {
      await api.createExpense(
        vaultId: 'vault-1',
        amount: 900000,
        currency: 'VND',
        description: 'Dinner',
        expenseDate: '2026-10-08T19:00:00Z',
        funding: const SharedExpenseFunding.member(
          payerMemberId: 'member-1',
          sourceAccountId: 'account-1',
        ),
        splits: const [
          SharedExpenseSplit(memberId: 'member-1', amount: 300000),
          SharedExpenseSplit(memberId: 'member-2', amount: 600000),
        ],
        idempotencyKey: 'expense-key',
      );

      expect(requests.single.url.path, '/api/v1/vaults/vault-1/expenses');
      expect(requests.single.headers['idempotency-key'], 'expense-key');
      expect(jsonDecode(requests.single.body), {
        'amount': 900000,
        'currency': 'VND',
        'description': 'Dinner',
        'expenseDate': '2026-10-08T19:00:00Z',
        'funding': {
          'type': 'MEMBER',
          'payerMemberId': 'member-1',
          'sourceAccountId': 'account-1',
        },
        'splits': [
          {'memberId': 'member-1', 'amount': 300000},
          {'memberId': 'member-2', 'amount': 600000},
        ],
      });
    });

    test('supports vault-funded shared expenses', () async {
      await api.createExpense(
        vaultId: 'vault-1',
        amount: 100,
        currency: 'VND',
        description: 'Shared expense',
        expenseDate: '2026-10-08T19:00:00Z',
        funding: const SharedExpenseFunding.vault(),
        splits: const [SharedExpenseSplit(memberId: 'member-1', amount: 100)],
        idempotencyKey: 'expense-key',
      );

      expect(jsonDecode(requests.single.body)['funding'], {'type': 'VAULT'});
    });

    test(
      'calls expense and responsibility queries without interpreting bodies',
      () async {
        final expenseResponse = await api.getExpense(
          vaultId: 'vault-1',
          expenseId: 'expense-1',
        );
        await api.listExpenses(
          vaultId: 'vault-1',
          from: '2026-10-01',
          to: '2026-10-08',
          memberId: 'member-1',
          page: 1,
          size: 25,
        );
        await api.getMemberResponsibility(
          vaultId: 'vault-1',
          memberId: 'member-1',
        );

        expect(expenseResponse.body, 'response body');
        expect(
          requests.map((request) => request.url.path),
          [
            '/api/v1/vaults/vault-1/expenses/expense-1',
            '/api/v1/vaults/vault-1/expenses',
            '/api/v1/vaults/vault-1/members/member-1/responsibility',
          ],
        );
        expect(requests[1].url.queryParameters, {
          'from': '2026-10-01',
          'to': '2026-10-08',
          'memberId': 'member-1',
          'page': '1',
          'size': '25',
        });
      },
    );
  });
}
