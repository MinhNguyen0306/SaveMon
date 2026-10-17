import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:savemon_mobile/core/session/session.dart';

const _channel = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  tearDown(() => TestDefaultBinaryMessengerBinding
      .instance.defaultBinaryMessenger
      .setMockMethodCallHandler(_channel, null));

  test('unreadable auth ciphertext is removed without clearing other keys',
      () async {
    final deletedKeys = <String>[];
    final values = <String, String>{'offline_database_key': 'preserve-me'};
    _mockPlatform(
      onCall: (call) async {
        if (call.method == 'read') {
          throw PlatformException(
            code: 'read_error',
            message:
                'javax.crypto.IllegalBlockSizeException: WRONG_FINAL_BLOCK_LENGTH',
          );
        }
        if (call.method == 'delete') {
          final key = (call.arguments as Map)['key'] as String;
          deletedKeys.add(key);
          values.remove(key);
        }
        return null;
      },
    );
    final storage =
        FlutterSecureAccessTokenStorage(const FlutterSecureStorage());

    expect(await storage.readAccessToken(), isNull);
    expect(deletedKeys, ['access_token']);
    expect(values, contains('offline_database_key'));
  });

  test('unrelated secure-storage errors are not treated as logged out',
      () async {
    final deletedKeys = <String>[];
    _mockPlatform(onCall: (call) async {
      if (call.method == 'read') {
        throw PlatformException(code: 'storage_unavailable', message: 'locked');
      }
      if (call.method == 'delete') deletedKeys.add('access_token');
      return null;
    });
    final storage =
        FlutterSecureAccessTokenStorage(const FlutterSecureStorage());

    await expectLater(
        storage.readAccessToken(), throwsA(isA<PlatformException>()));
    expect(deletedKeys, isEmpty);
  });
}

void _mockPlatform({required Future<Object?> Function(MethodCall) onCall}) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(_channel, onCall);
}
