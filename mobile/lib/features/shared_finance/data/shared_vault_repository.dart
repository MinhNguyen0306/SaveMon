import 'dart:convert';

import '../../../core/network/api_client.dart';
import 'shared_finance_models.dart';

class SharedVaultRepository {
  const SharedVaultRepository(this._apiClient);

  final ApiClient _apiClient;

  Future<SharedFinancePage<SharedVault>> listVaults({
    int page = 0,
    int size = 20,
  }) async {
    final response = await _apiClient.send(
      method: 'GET',
      path: _pathWithQuery('vaults', {'page': '$page', 'size': '$size'}),
    );
    return SharedFinancePage.fromJson(
      _decodeObject(response.body),
      SharedVault.fromJson,
      'vault',
    );
  }

  Future<SharedVault> getVault(String id) async {
    final response = await _apiClient.send(
      method: 'GET',
      path: _vaultPath(id),
    );
    return SharedVault.fromJson(_decodeObject(response.body));
  }

  Future<SharedFinancePage<SharedVaultMember>> listMembers(
    String vaultId, {
    int page = 0,
    int size = 20,
  }) async {
    final response = await _apiClient.send(
      method: 'GET',
      path: _pathWithQuery(
        '${_vaultPath(vaultId)}/members',
        {'page': '$page', 'size': '$size'},
      ),
    );
    return SharedFinancePage.fromJson(
      _decodeObject(response.body),
      SharedVaultMember.fromJson,
      'vault member',
    );
  }

  Future<SharedFinancePage<SharedContribution>> listContributions({
    required String vaultId,
    String? memberId,
    String? from,
    String? to,
    int page = 0,
    int size = 20,
  }) async {
    final path = _pathWithQuery(
      '${_vaultPath(vaultId)}/contributions',
      {
        'page': '$page',
        'size': '$size',
        if (memberId != null) 'memberId': memberId,
        if (from != null) 'from': from,
        if (to != null) 'to': to,
      },
    );
    final response = await _apiClient.send(method: 'GET', path: path);
    return SharedFinancePage.fromJson(
      _decodeObject(response.body),
      SharedContribution.fromJson,
      'contribution',
    );
  }

  Future<SharedFinancePage<SharedExpense>> listExpenses(
    String vaultId, {
    String? from,
    String? to,
    String? memberId,
    int page = 0,
    int size = 20,
  }) async {
    final response = await _apiClient.send(
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
    return SharedFinancePage.fromJson(
      _decodeObject(response.body),
      SharedExpense.fromJson,
      'expense',
    );
  }

  Future<SharedExpense> getExpense({
    required String vaultId,
    required String expenseId,
  }) async {
    final response = await _apiClient.send(
      method: 'GET',
      path: '${_vaultPath(vaultId)}/expenses/'
          '${Uri.encodeComponent(expenseId)}',
    );
    return SharedExpense.fromJson(_decodeObject(response.body));
  }

  Map<String, dynamic> _decodeObject(String body) =>
      expectJsonObject(jsonDecode(body), 'response');

  String _vaultPath(String vaultId) => 'vaults/${Uri.encodeComponent(vaultId)}';

  String _pathWithQuery(String path, Map<String, String> parameters) =>
      '$path?${Uri(queryParameters: parameters).query}';
}
