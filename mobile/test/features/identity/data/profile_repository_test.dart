import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:savemon_mobile/core/network/api_client.dart';
import 'package:savemon_mobile/features/identity/data/profile_repository.dart';

void main() {
  test('loads current profile using the documented response fields', () async {
    http.Request? sent;
    final repository = ProfileRepository(ApiClient(
      baseUri: Uri.parse('https://api.test/api/v1'),
      httpClient: MockClient((request) async {
        sent = request;
        return http.Response('{"id":"u-1","email":"a@example.test","displayName":"A"}', 200);
      }),
      readSession: () async => null,
      onUnauthorized: () async {},
    ));

    final profile = await repository.getCurrentUser();

    expect(sent!.method, 'GET');
    expect(sent!.url.path, '/api/v1/me');
    expect(profile.id, 'u-1');
    expect(profile.email, 'a@example.test');
    expect(profile.displayName, 'A');
  });
}
