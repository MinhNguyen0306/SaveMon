import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../core/network/api_client.dart';

/// Calls the documented Shared Finance endpoints.
///
/// Responses are returned unparsed for callers to interpret.
class SharedFinanceApi {
  const SharedFinanceApi(this._apiClient);

  final ApiClient _apiClient;

  Future<http.Response> createVault({
    required String name,
    required String currency,
  }) {
    return _sendJson(
      method: 'POST',
      path: 'vaults',
      body: {'name': name, 'currency': currency},
    );
  }

  Future<http.Response> listVaults({int page = 0, int size = 20}) {
    return _apiClient.send(
      method: 'GET',
      path: _pathWithQuery('vaults', {'page': '$page', 'size': '$size'}),
    );
  }

  Future<http.Response> getVault(String vaultId) {
    return _apiClient.send(
      method: 'GET',
      path: _vaultPath(vaultId),
    );
  }

  Future<http.Response> listMembers(
    String vaultId, {
    int page = 0,
    int size = 20,
  }) {
    return _apiClient.send(
      method: 'GET',
      path: _pathWithQuery(
        '${_vaultPath(vaultId)}/members',
        {'page': '$page', 'size': '$size'},
      ),
    );
  }

  Future<http.Response> addMember({
    required String vaultId,
    required String userId,
  }) {
    return _sendJson(
      method: 'POST',
      path: '${_vaultPath(vaultId)}/members',
      body: {'userId': userId},
    );
  }

  Future<http.Response> removeMember({
    required String vaultId,
    required String memberId,
  }) {
    return _apiClient.send(
      method: 'POST',
      path: '${_vaultPath(vaultId)}/members/${_pathSegment(memberId)}/remove',
    );
  }

  Future<http.Response> createContribution({
    required String vaultId,
    required String sourceAccountId,
    required int amount,
    required String currency,
    required String contributedAt,
    required String idempotencyKey,
  }) {
    return _sendJson(
      method: 'POST',
      path: '${_vaultPath(vaultId)}/contributions',
      headers: {'Idempotency-Key': idempotencyKey},
      body: {
        'sourceAccountId': sourceAccountId,
        'amount': amount,
        'currency': currency,
        'contributedAt': contributedAt,
      },
    );
  }

  Future<http.Response> listContributions({
    required String vaultId,
    String? memberId,
    String? from,
    String? to,
    int page = 0,
    int size = 20,
  }) {
    return _apiClient.send(
      method: 'GET',
      path: _pathWithQuery(
        '${_vaultPath(vaultId)}/contributions',
        {
          'page': '$page',
          'size': '$size',
          if (memberId != null) 'memberId': memberId,
          if (from != null) 'from': from,
          if (to != null) 'to': to,
        },
      ),
    );
  }

  Future<http.Response> createExpense({
    required String vaultId,
    required int amount,
    required String currency,
    required String description,
    required String expenseDate,
    required SharedExpenseFunding funding,
    required List<SharedExpenseSplit> splits,
    required String idempotencyKey,
  }) {
    return _sendJson(
      method: 'POST',
      path: '${_vaultPath(vaultId)}/expenses',
      headers: {'Idempotency-Key': idempotencyKey},
      body: {
        'amount': amount,
        'currency': currency,
        'description': description,
        'expenseDate': expenseDate,
        'funding': funding.toJson(),
        'splits': splits.map((split) => split.toJson()).toList(),
      },
    );
  }

  Future<http.Response> getExpense({
    required String vaultId,
    required String expenseId,
  }) {
    return _apiClient.send(
      method: 'GET',
      path: '${_vaultPath(vaultId)}/expenses/${_pathSegment(expenseId)}',
    );
  }

  Future<http.Response> listExpenses({
    required String vaultId,
    String? from,
    String? to,
    String? memberId,
    int page = 0,
    int size = 20,
  }) {
    return _apiClient.send(
      method: 'GET',
      path: _pathWithQuery(
        '${_vaultPath(vaultId)}/expenses',
        {
          'page': '$page',
          'size': '$size',
          if (from != null) 'from': from,
          if (to != null) 'to': to,
          if (memberId != null) 'memberId': memberId,
        },
      ),
    );
  }

  Future<http.Response> getMemberResponsibility({
    required String vaultId,
    required String memberId,
  }) {
    return _apiClient.send(
      method: 'GET',
      path:
          '${_vaultPath(vaultId)}/members/${_pathSegment(memberId)}/responsibility',
    );
  }

  Future<http.Response> _sendJson({
    required String method,
    required String path,
    required Map<String, Object?> body,
    Map<String, String> headers = const {},
  }) {
    return _apiClient.send(
      method: method,
      path: path,
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      body: jsonEncode(body),
    );
  }

  String _vaultPath(String vaultId) {
    return 'vaults/${_pathSegment(vaultId)}';
  }

  String _pathWithQuery(String path, Map<String, String> parameters) {
    if (parameters.isEmpty) {
      return path;
    }
    return '$path?${Uri(queryParameters: parameters).query}';
  }

  String _pathSegment(String value) => Uri.encodeComponent(value);
}

class SharedExpenseFunding {
  const SharedExpenseFunding.vault() : _values = const {'type': 'VAULT'};

  const SharedExpenseFunding.member({
    required String payerMemberId,
    required String sourceAccountId,
  }) : _values = const {
          'type': 'MEMBER',
          'payerMemberId': payerMemberId,
          'sourceAccountId': sourceAccountId,
        };

  final Map<String, Object?> _values;

  Map<String, Object?> toJson() => _values;
}

class SharedExpenseSplit {
  const SharedExpenseSplit({
    required this.memberId,
    required this.amount,
  });

  final String memberId;
  final int amount;

  Map<String, Object?> toJson() => {
        'memberId': memberId,
        'amount': amount,
      };
}
