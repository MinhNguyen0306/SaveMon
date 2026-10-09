import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/core/config/app_config.dart';

void main() {
  group('AppConfig', () {
    test('normalizes the API base URL for relative requests', () {
      const config = AppConfig(
        apiBaseUrl: 'https://api.example.test/api/v1',
        environment: 'test',
      );

      expect(config.apiBaseUri.toString(), 'https://api.example.test/api/v1/');
      expect(config.environment, 'test');
    });

    test('rejects a missing API base URL when it is needed', () {
      const config = AppConfig(apiBaseUrl: '');

      expect(() => config.apiBaseUri, throwsA(isA<StateError>()));
    });
  });
}
