import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_client.dart';
import '../data/auth_repository.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _submitting = false;
  String? _error;
  String? _passwordError;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final password = _password.text;
    if (password.trim().isEmpty ||
        password.runes.length < 8 ||
        password.runes.length > 128) {
      setState(() {
        _passwordError = 'Password must be 8 to 128 characters and not blank.';
        _error = null;
      });
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
      _passwordError = null;
    });
    try {
      await ref.read(authRepositoryProvider).login(
            email: _email.text,
            password: password,
          );
      if (mounted) context.go('/');
    } on ApiException catch (error) {
      if (mounted) {
        setState(() {
          _error = error.statusCode == 401
              ? 'Email or password is incorrect.'
              : 'Could not sign in. Please check your details and try again.';
        });
      }
    } on FormatException {
      if (mounted) {
        setState(() => _error = 'The server returned an unexpected response.');
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'Could not connect. Check your connection and try again.',
        );
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Log in')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Text('Welcome back', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 24),
          TextField(
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            autofillHints: const [AutofillHints.username, AutofillHints.email],
            decoration: const InputDecoration(labelText: 'Email'),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _password,
            obscureText: true,
            autofillHints: const [AutofillHints.password],
            decoration: InputDecoration(
              labelText: 'Password',
              errorText: _passwordError,
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: 16),
            Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ],
          const SizedBox(height: 24),
          FilledButton(
            onPressed: _submitting ? null : _submit,
            child: _submitting
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Log in'),
          ),
          TextButton(
            onPressed: _submitting ? null : () => context.go('/register'),
            child: const Text('Create an account'),
          ),
        ],
      ),
    );
  }
}
