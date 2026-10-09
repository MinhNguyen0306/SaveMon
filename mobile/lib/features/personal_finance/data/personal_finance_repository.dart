import 'dart:convert';

import '../../../core/network/api_client.dart';
import 'models/personal_finance_models.dart';

class PersonalFinanceRepository {
  const PersonalFinanceRepository(this._apiClient);

  final ApiClient _apiClient;

  Future<PersonalTransaction> getTransaction(String transactionId) async {
    final response = await _apiClient.send(
      method: 'GET',
      path: 'transactions/${Uri.encodeComponent(transactionId)}',
    );
    return PersonalTransaction.fromJson(_decodeObject(response.body));
  }

  Future<TransactionHistoryPage> listTransactions({
    int page = 0,
    int size = 20,
    String? accountId,
    String? type,
    String? status,
    String? from,
    String? to,
    String? categoryId,
    String? currency,
  }) async {
    final query = Uri(queryParameters: {
      'page': '$page',
      'size': '$size',
      if (accountId != null) 'accountId': accountId,
      if (type != null) 'type': type,
      if (status != null) 'status': status,
      if (from != null) 'from': from,
      if (to != null) 'to': to,
      if (categoryId != null) 'categoryId': categoryId,
      if (currency != null) 'currency': currency,
    }).query;
    final response = await _apiClient.send(
      method: 'GET',
      path: 'transactions?$query',
    );
    final body = _decodeObject(response.body);
    final items = body['items'];
    if (items is! List) {
      throw const FormatException('Expected "items" to be a list.');
    }
    final totalPages = _integerField(body, 'totalPages');
    if (totalPages < 0) {
      throw const FormatException('Expected "totalPages" to be non-negative.');
    }
    return TransactionHistoryPage(
      items: items
          .map(
            (item) => PersonalTransaction.fromJson(
              _expectObject(item, 'transaction'),
            ),
          )
          .toList(growable: false),
      page: _integerField(body, 'page'),
      size: _integerField(body, 'size'),
      totalItems: _integerField(body, 'totalItems'),
      totalPages: totalPages,
    );
  }

  Future<PageResult<PersonalAccount>> listAccounts({
    String? status,
    int page = 0,
    int size = 20,
  }) async {
    final query = Uri(queryParameters: {
      if (status != null) 'status': status,
      'page': '$page',
      'size': '$size',
    }).query;
    final response = await _apiClient.send(
      method: 'GET',
      path: 'accounts?$query',
    );
    final body = _decodeObject(response.body);
    final items = body['items'];
    if (items is! List) {
      throw const FormatException('Expected "items" to be a list.');
    }
    final parsedItems = items
        .map(
          (item) => PersonalAccount.fromJson(
            _expectObject(item, 'account'),
          ),
        )
        .toList(growable: false);
    return PageResult(
      items: parsedItems,
      page: _integerField(body, 'page'),
      size: _integerField(body, 'size'),
      totalItems: _integerField(body, 'totalItems'),
      totalPages: _integerField(body, 'totalPages'),
    );
  }

  Future<CalendarSummary> getCalendar({
    required String currency,
    String? from,
    String? to,
  }) async {
    final response = await _apiClient.send(
      method: 'GET',
      path: _pathWithRange('calendar', currency: currency, from: from, to: to),
    );
    return CalendarSummary.fromJson(_decodeObject(response.body));
  }

  Future<FinancialSummary> getSummary({
    required String currency,
    String? from,
    String? to,
  }) async {
    final response = await _apiClient.send(
      method: 'GET',
      path: _pathWithRange('summary', currency: currency, from: from, to: to),
    );
    return FinancialSummary.fromJson(_decodeObject(response.body));
  }

  String _pathWithRange(
    String endpoint, {
    required String currency,
    String? from,
    String? to,
  }) {
    final parameters = <String, String>{
      'currency': currency,
      if (from != null) 'from': from,
      if (to != null) 'to': to,
    };
    return '$endpoint?${Uri(queryParameters: parameters).query}';
  }

  Map<String, dynamic> _decodeObject(String body) {
    final decoded = jsonDecode(body);
    return _expectObject(decoded, 'response');
  }

  Map<String, dynamic> _expectObject(Object? value, String description) {
    if (value is Map<String, dynamic>) {
      return value;
    }
    throw FormatException('Expected $description to be an object.');
  }

  int _integerField(Map<String, dynamic> json, String field) {
    final value = json[field];
    if (value is int) {
      return value;
    }
    throw FormatException('Expected "$field" to be an integer.');
  }
}
