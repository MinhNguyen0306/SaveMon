import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_client.dart';
import '../data/auth_repository.dart';
import 'auth_page_chrome.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _submitting = false;
  bool _passwordVisible = false;
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
    if (password.trim().isEmpty) {
      setState(() {
        _passwordError = 'Vui lòng nhập mật khẩu.';
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
              ? 'Email hoặc mật khẩu chưa chính xác.'
              : 'Chưa thể đăng nhập. Vui lòng kiểm tra lại thông tin.';
        });
      }
    } on FormatException {
      if (mounted) {
        setState(() => _error = 'Phản hồi từ máy chủ không hợp lệ.');
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'Không thể kết nối. Hãy thử lại sau nhé.');
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) => AuthPageFrame(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const AuthBrandHeader(
              title: 'Chào mừng trở lại 👋',
              subtitle:
                  'Quản lý chi tiêu thông minh hơn\nđể vun đắp tương lai tốt đẹp hơn mỗi ngày.',
              illustration: AuthIllustration.wallet,
            ),
            const SizedBox(height: 28),
            AuthInputField(
              controller: _email,
              hint: 'Email của bạn',
              icon: Icons.mail_outline_rounded,
              keyboardType: TextInputType.emailAddress,
              autofillHints: const [
                AutofillHints.username,
                AutofillHints.email
              ],
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 14),
            AuthInputField(
              controller: _password,
              hint: 'Mật khẩu',
              icon: Icons.lock_outline_rounded,
              obscureText: !_passwordVisible,
              autofillHints: const [AutofillHints.password],
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _submitting ? null : _submit(),
              suffix: IconButton(
                tooltip: _passwordVisible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu',
                onPressed: () =>
                    setState(() => _passwordVisible = !_passwordVisible),
                icon: Icon(
                  _passwordVisible
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined,
                  color: AuthColors.inkSoft,
                  size: 22,
                ),
              ),
              errorText: _passwordError,
            ),
            if (_error != null) ...[
              const SizedBox(height: 14),
              AuthErrorBanner(message: _error!),
            ],
            const SizedBox(height: 24),
            AuthPrimaryButton(
              label: 'Đăng nhập',
              isLoading: _submitting,
              onPressed: _submitting ? null : _submit,
            ),
            const SizedBox(height: 12),
            AuthOutlinedButton(
              label: 'Tạo tài khoản',
              onPressed: _submitting ? null : () => context.go('/register'),
            ),
            AuthBottomPrompt(
              leading: 'Chưa có tài khoản?',
              action: 'Đăng ký',
              onTapRoute: '/register',
              disabled: _submitting,
            ),
          ],
        ),
      );
}
