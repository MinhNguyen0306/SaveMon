import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/network/api_client.dart';

class UserProfile {
  const UserProfile({required this.id, required this.email, required this.displayName});

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
        id: _string(json, 'id'),
        email: _string(json, 'email'),
        displayName: _string(json, 'displayName'),
      );

  final String id;
  final String email;
  final String displayName;
}

class ProfileRepository {
  const ProfileRepository(this._apiClient);
  final ApiClient _apiClient;

  Future<UserProfile> getCurrentUser() async {
    final response = await _apiClient.send(method: 'GET', path: 'me');
    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Expected profile response to be an object.');
    }
    return UserProfile.fromJson(decoded);
  }
}

String _string(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String) return value;
  throw FormatException('Expected "$key" to be a string.');
}

final profileRepositoryProvider = Provider<ProfileRepository>((ref) =>
    ProfileRepository(ref.watch(apiClientProvider)));
final currentUserProfileProvider = FutureProvider<UserProfile>((ref) =>
    ref.watch(profileRepositoryProvider).getCurrentUser());
