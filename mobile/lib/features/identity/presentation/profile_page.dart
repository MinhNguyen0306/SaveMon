import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/widgets/async_value_view.dart';
import '../data/profile_repository.dart';

class ProfilePage extends ConsumerWidget {
  const ProfilePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(currentUserProfileProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Profile')),
      body: AsyncValueView(
        value: profile,
        onRetry: () async => ref.invalidate(currentUserProfileProvider),
        errorMessage: (error) => error is ApiException && error.statusCode == 401
            ? 'Your session has expired. Please sign in again.'
            : 'Could not load your profile. Please retry.',
        builder: (user) => ListView(
          padding: const EdgeInsets.all(24),
          children: [
            const CircleAvatar(radius: 32, child: Icon(Icons.person)),
            const SizedBox(height: 20),
            _ProfileField(label: 'Display name', value: user.displayName),
            _ProfileField(label: 'Email', value: user.email),
            _ProfileField(label: 'User ID', value: user.id),
          ],
        ),
      ),
    );
  }
}

class _ProfileField extends StatelessWidget {
  const _ProfileField({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => ListTile(title: Text(label), subtitle: Text(value));
}
