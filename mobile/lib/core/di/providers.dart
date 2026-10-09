import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../network/api_client.dart';
import '../session/auth_state.dart';

final appConfigProvider = Provider<AppConfig>((ref) {
  return AppConfig.fromEnvironment();
});

final httpClientProvider = Provider<http.Client>((ref) {
  final client = http.Client();
  ref.onDispose(client.close);
  return client;
});

final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(
    baseUri: ref.watch(appConfigProvider).apiBaseUri,
    httpClient: ref.watch(httpClientProvider),
    readSession: () =>
        ref.read(authStateProvider.notifier).sessionForApiRequest(),
    onUnauthorized: () => ref.read(authStateProvider.notifier).clearSession(),
  );
});
