import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/session/auth_state.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authStateProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('SaveMon')),
      body: auth.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => const Center(child: Text('Could not load local session.')),
        data: (status) => ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Text('Welcome to SaveMon', style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 16),
            if (status == AuthenticationStatus.unauthenticated) ...[
              const Text('Create an account to get started.'),
              const SizedBox(height: 12),
              FilledButton(
                onPressed: () => context.push('/register'),
                child: const Text('Create account'),
              ),
              const SizedBox(height: 12),
              OutlinedButton(
                onPressed: () => context.push('/login'),
                child: const Text('Log in'),
              ),
            ] else ...[
              OutlinedButton(
                onPressed: () => context.push('/profile'),
                child: const Text('Profile'),
              ),
              OutlinedButton(
                onPressed: () => context.push('/personal-finance'),
                child: const Text('Personal finance'),
              ),
              OutlinedButton(
                onPressed: () => context.push('/shared-finance'),
                child: const Text('Shared vaults'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
