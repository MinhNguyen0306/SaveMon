import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_client.dart';
import '../data/auth_repository.dart';

class RegistrationPage extends ConsumerStatefulWidget {
  const RegistrationPage({super.key});

  @override
  ConsumerState<RegistrationPage> createState() => _RegistrationPageState();
}

class _RegistrationPageState extends ConsumerState<RegistrationPage> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _displayName = TextEditingController();
  bool _submitting = false;
  String? _error;
  String? _passwordError;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _displayName.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final password = _password.text;
    if (password.trim().isEmpty || password.runes.length < 8 || password.runes.length > 128) {
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
      await ref.read(authRepositoryProvider).register(
            email: _email.text,
            password: password,
            displayName: _displayName.text,
          );
      if (mounted) context.go('/');
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _apiMessage(error));
    } on FormatException {
      if (mounted) setState(() => _error = 'The server returned an unexpected response.');
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

  String _apiMessage(ApiException error) {
    try {
      final body = jsonDecode(error.responseBody);
      if (body is Map<String, dynamic> && body['message'] is String) {
        return body['message'] as String;
      }
    } catch (_) {
      // Keep the UI independent of undocumented error response details.
    }
    return switch (error.statusCode) {
      409 => 'An account with this identity already exists.',
      429 => 'Too many attempts. Please try again later.',
      503 => 'Registration is temporarily unavailable. Please try again later.',
      _ => 'Registration failed. Please check your details and try again.',
    };
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Create account')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Text('Create your SaveMon account', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 24),
          TextField(
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            autofillHints: const [AutofillHints.email],
            decoration: const InputDecoration(labelText: 'Email'),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _password,
            obscureText: true,
            autofillHints: const [AutofillHints.newPassword],
            decoration: InputDecoration(labelText: 'Password', errorText: _passwordError),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _displayName,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'Display name'),
          ),
          if (_error != null) ...[
            const SizedBox(height: 16),
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 24),
          FilledButton(
            onPressed: _submitting ? null : _submit,
            child: _submitting
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Create account'),
          ),
          TextButton(
            onPressed: _submitting ? null : () => context.go('/login'),
            child: const Text('Already have an account? Log in'),
          ),
        ],
      ),
    );
  }
}
