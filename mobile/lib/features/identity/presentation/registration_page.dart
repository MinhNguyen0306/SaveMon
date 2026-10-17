import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_client.dart';
import '../data/auth_repository.dart';
import 'auth_page_chrome.dart';

class RegistrationPage extends ConsumerStatefulWidget {
  const RegistrationPage({super.key});

  @override
  ConsumerState<RegistrationPage> createState() => _RegistrationPageState();
}

class _RegistrationPageState extends ConsumerState<RegistrationPage> {
  final _displayName = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _confirmPassword = TextEditingController();
  bool _submitting = false;
  bool _passwordVisible = false;
  bool _confirmVisible = false;
  String? _error;
  String? _passwordError;
  String? _confirmationError;
  bool _recoveryPossible = false;

  @override
  void dispose() {
    _displayName.dispose();
    _email.dispose();
    _password.dispose();
    _confirmPassword.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final name = _displayName.text.trim();
    final password = _password.text;
    final confirmation = _confirmPassword.text;
    final invalidPassword = password.trim().isEmpty ||
        password.runes.length < 12 ||
        password.runes.length > 128;
    setState(() {
      _passwordError =
          invalidPassword ? 'Mật khẩu cần từ 12 đến 128 ký tự.' : null;
      _confirmationError = !invalidPassword && confirmation != password
          ? 'Mật khẩu xác nhận chưa khớp.'
          : null;
      _error = null;
      _recoveryPossible = false;
    });
    if (name.isEmpty ||
        name.runes.length > 100 ||
        invalidPassword ||
        confirmation != password) {
      if (name.isEmpty || name.runes.length > 100) {
        setState(() => _error = 'Vui lòng nhập họ tên (tối đa 100 ký tự).');
      }
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(authRepositoryProvider).register(
            email: _email.text,
            password: password,
            displayName: name,
          );
      if (mounted) context.go('/');
    } on ApiException catch (error) {
      if (mounted) {
        setState(() {
          _recoveryPossible = _isIssuerUnavailable(error);
          _error = _apiMessage(error);
        });
      }
    } on FormatException {
      if (mounted) setState(() => _error = 'Phản hồi từ máy chủ không hợp lệ.');
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'Không thể kết nối. Hãy thử lại sau nhé.');
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  String _apiMessage(ApiException error) {
    try {
      final body = jsonDecode(error.responseBody);
      if (body is Map<String, dynamic> &&
          body['code'] == 'TOKEN_ISSUER_UNAVAILABLE') {
        return 'Tài khoản có thể đã được tạo. Hãy thử đăng nhập.';
      }
      if (body is Map<String, dynamic> && body['message'] is String) {
        return body['message'] as String;
      }
    } catch (_) {
      // Keep the UI independent of undocumented error response details.
    }
    return switch (error.statusCode) {
      409 => 'Email này đã được sử dụng.',
      429 => 'Bạn đã thử quá nhiều lần. Vui lòng thử lại sau.',
      503 => 'Đăng ký đang tạm gián đoạn. Vui lòng thử lại sau.',
      _ => 'Đăng ký chưa thành công. Vui lòng kiểm tra lại thông tin.',
    };
  }

  bool _isIssuerUnavailable(ApiException error) {
    try {
      final body = jsonDecode(error.responseBody);
      return body is Map<String, dynamic> &&
          body['code'] == 'TOKEN_ISSUER_UNAVAILABLE';
    } catch (_) {
      return false;
    }
  }

  @override
  Widget build(BuildContext context) => AuthPageFrame(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const AuthBrandHeader(
              title: 'Tạo tài khoản mới 🌱',
              subtitle:
                  'Bắt đầu hành trình quản lý tài chính cá nhân\nthông minh và dễ dàng cùng SaveMon.',
              illustration: AuthIllustration.growth,
            ),
            const SizedBox(height: 22),
            AuthInputField(
              controller: _displayName,
              hint: 'Họ và tên',
              helper: 'Ví dụ: Nguyễn Minh Anh',
              icon: Icons.person_outline_rounded,
              textCapitalization: TextCapitalization.words,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.name],
            ),
            const SizedBox(height: 12),
            AuthInputField(
              controller: _email,
              hint: 'Email',
              helper: 'example@gmail.com',
              icon: Icons.mail_outline_rounded,
              keyboardType: TextInputType.emailAddress,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.email],
            ),
            const SizedBox(height: 12),
            AuthInputField(
              controller: _password,
              hint: 'Mật khẩu',
              helper: 'Ít nhất 12 ký tự',
              icon: Icons.lock_outline_rounded,
              obscureText: !_passwordVisible,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.newPassword],
              suffix: _visibilityButton(
                visible: _passwordVisible,
                onPressed: () =>
                    setState(() => _passwordVisible = !_passwordVisible),
              ),
              errorText: _passwordError,
            ),
            const SizedBox(height: 12),
            AuthInputField(
              controller: _confirmPassword,
              hint: 'Xác nhận mật khẩu',
              helper: 'Nhập lại mật khẩu',
              icon: Icons.lock_outline_rounded,
              obscureText: !_confirmVisible,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _submitting ? null : _submit(),
              suffix: _visibilityButton(
                visible: _confirmVisible,
                onPressed: () =>
                    setState(() => _confirmVisible = !_confirmVisible),
              ),
              errorText: _confirmationError,
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              AuthErrorBanner(message: _error!),
              if (_recoveryPossible)
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: () => context.go('/login'),
                    child: const Text('Thử đăng nhập'),
                  ),
                ),
            ],
            const SizedBox(height: 20),
            AuthPrimaryButton(
              label: 'Tạo tài khoản',
              isLoading: _submitting,
              onPressed: _submitting ? null : _submit,
            ),
            AuthBottomPrompt(
              leading: 'Đã có tài khoản?',
              action: 'Đăng nhập',
              onTapRoute: '/login',
              disabled: _submitting,
            ),
          ],
        ),
      );

  Widget _visibilityButton(
          {required bool visible, required VoidCallback onPressed}) =>
      IconButton(
        tooltip: visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu',
        onPressed: onPressed,
        icon: Icon(
          visible ? Icons.visibility_outlined : Icons.visibility_off_outlined,
          color: AuthColors.inkSoft,
          size: 22,
        ),
      );
}
