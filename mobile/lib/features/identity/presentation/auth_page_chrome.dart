import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

abstract final class AuthColors {
  static const ink = Color(0xFF092E2C);
  static const inkSoft = Color(0xFF334155);
  static const muted = Color(0xFF69768A);
  static const green = Color(0xFF079765);
  static const greenDark = Color(0xFF00845C);
  static const mint = Color(0xFFDDF4E9);
  static const field = Color(0xFFF4F5F7);
  static const stroke = Color(0xFFE8EAED);
  static const page = Color(0xFFFCFDFB);
}

/// Shared, scroll-safe visual shell for the public identity screens.
class AuthPageFrame extends StatelessWidget {
  const AuthPageFrame({required this.child, super.key});

  final Widget child;

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AuthColors.page,
        resizeToAvoidBottomInset: true,
        body: Stack(
          children: [
            const Positioned.fill(child: _AuthBackdrop()),
            SafeArea(
              child: LayoutBuilder(
                builder: (context, constraints) => SingleChildScrollView(
                  keyboardDismissBehavior:
                      ScrollViewKeyboardDismissBehavior.onDrag,
                  padding: const EdgeInsets.fromLTRB(24, 20, 24, 22),
                  child: Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 480),
                      child: ConstrainedBox(
                        constraints: BoxConstraints(
                          minHeight: constraints.maxHeight - 42,
                        ),
                        child: child,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      );
}

class _AuthBackdrop extends StatelessWidget {
  const _AuthBackdrop();

  @override
  Widget build(BuildContext context) => IgnorePointer(
        child: CustomPaint(
          painter: _BackdropPainter(),
          child: const SizedBox.expand(),
        ),
      );
}

class _BackdropPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = const Color(0xFFE8F7EF).withValues(alpha: .68);
    final pale = Paint()..color = const Color(0xFFF3FAF4).withValues(alpha: .9);
    canvas.drawCircle(
        Offset(size.width * 1.04, size.height * .24), size.width * .38, pale);
    canvas.drawCircle(
        Offset(-size.width * .18, size.height * .73), size.width * .35, paint);
    canvas.drawCircle(
        Offset(size.width * .95, size.height * .91), size.width * .29, pale);

    final leaf = Paint()
      ..color = const Color(0xFFDDF3E6).withValues(alpha: .65);
    final path = Path()
      ..moveTo(size.width * .83, size.height * .18)
      ..cubicTo(size.width * .87, size.height * .06, size.width * .99,
          size.height * .04, size.width * .98, size.height * .17)
      ..cubicTo(size.width * .95, size.height * .22, size.width * .87,
          size.height * .23, size.width * .83, size.height * .18)
      ..close();
    canvas.drawPath(path, leaf);
    canvas.drawLine(
      Offset(size.width * .84, size.height * .20),
      Offset(size.width * .96, size.height * .10),
      Paint()
        ..color = const Color(0xFFCEEAD9)
        ..strokeWidth = 1,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

enum AuthIllustration { wallet, growth }

class AuthBrandHeader extends StatelessWidget {
  const AuthBrandHeader({
    required this.title,
    required this.subtitle,
    required this.illustration,
    super.key,
  });

  final String title;
  final String subtitle;
  final AuthIllustration illustration;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const _SaveMonLogo(),
              const SizedBox(width: 10),
              const Text(
                'SaveMon',
                style: TextStyle(
                  color: AuthColors.ink,
                  fontSize: 27,
                  height: 1,
                  fontWeight: FontWeight.w800,
                  letterSpacing: -.9,
                ),
              ),
              Expanded(
                child: Align(
                  alignment: Alignment.centerRight,
                  child: _HeroIllustration(kind: illustration),
                ),
              ),
            ],
          ),
          const SizedBox(height: 23),
          Text(
            title,
            style: const TextStyle(
              color: AuthColors.ink,
              fontSize: 27,
              height: 1.17,
              fontWeight: FontWeight.w800,
              letterSpacing: -.7,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: const TextStyle(
              color: AuthColors.muted,
              fontSize: 15.5,
              height: 1.42,
              fontWeight: FontWeight.w400,
            ),
          ),
        ],
      );
}

class _SaveMonLogo extends StatelessWidget {
  const _SaveMonLogo();

  @override
  Widget build(BuildContext context) => Container(
        width: 70,
        height: 70,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF42D98B), Color(0xFF008B5E)],
          ),
          boxShadow: const [
            BoxShadow(
                color: Color(0x3321A972), blurRadius: 16, offset: Offset(0, 7)),
          ],
        ),
        child: const Stack(
          alignment: Alignment.center,
          children: [
            Icon(Icons.account_balance_wallet_rounded,
                color: Colors.white, size: 42),
            Positioned(
              left: 13,
              bottom: 12,
              child:
                  Icon(Icons.eco_rounded, color: Color(0xFFB7F2CF), size: 22),
            ),
            Positioned(
              right: 11,
              top: 20,
              child: Icon(Icons.circle, color: Color(0xFFB9F0D0), size: 8),
            ),
          ],
        ),
      );
}

class _HeroIllustration extends StatelessWidget {
  const _HeroIllustration({required this.kind});

  final AuthIllustration kind;

  @override
  Widget build(BuildContext context) => SizedBox(
        width: 118,
        height: 106,
        child: Stack(
          alignment: Alignment.center,
          clipBehavior: Clip.none,
          children: [
            Container(
              width: 106,
              height: 106,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [Color(0xFFDFF7E8), Color(0x00E7F8EC)],
                ),
              ),
            ),
            if (kind == AuthIllustration.wallet) ...[
              Transform.rotate(
                angle: -.15,
                child: Container(
                  width: 78,
                  height: 58,
                  decoration: BoxDecoration(
                    color: const Color(0xFF16AE75),
                    borderRadius: BorderRadius.circular(17),
                    boxShadow: const [
                      BoxShadow(
                          color: Color(0x33318E65),
                          blurRadius: 15,
                          offset: Offset(0, 7)),
                    ],
                  ),
                  child: const Icon(Icons.account_balance_wallet_rounded,
                      color: Color(0xFFECFFF5), size: 50),
                ),
              ),
              const Positioned(
                top: 17,
                left: 43,
                child: Icon(Icons.monetization_on_rounded,
                    color: Color(0xFFFFC84D), size: 35),
              ),
              const Positioned(
                top: 2,
                right: 8,
                child:
                    Icon(Icons.eco_rounded, color: Color(0xFF35B879), size: 37),
              ),
            ] else ...[
              Transform.rotate(
                angle: .08,
                child: Container(
                  width: 86,
                  height: 67,
                  decoration: BoxDecoration(
                    color: const Color(0xEFFFFFFF),
                    borderRadius: BorderRadius.circular(16),
                    boxShadow: const [
                      BoxShadow(
                          color: Color(0x22388B60),
                          blurRadius: 18,
                          offset: Offset(0, 8)),
                    ],
                  ),
                  child: const Padding(
                    padding: EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(Icons.show_chart_rounded,
                            color: Color(0xFF1CAD75), size: 20),
                        Spacer(),
                        Row(
                          children: [
                            _ChartBar(height: 10),
                            SizedBox(width: 5),
                            _ChartBar(height: 14),
                            SizedBox(width: 5),
                            _ChartBar(height: 18),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const Positioned(
                right: 1,
                bottom: 0,
                child: Icon(Icons.monetization_on_rounded,
                    color: Color(0xFFFFC84D), size: 35),
              ),
              const Positioned(
                top: 2,
                right: 25,
                child:
                    Icon(Icons.eco_rounded, color: Color(0xFF35B879), size: 30),
              ),
            ],
          ],
        ),
      );
}

class _ChartBar extends StatelessWidget {
  const _ChartBar({required this.height});
  final double height;

  @override
  Widget build(BuildContext context) => Container(
        width: 10,
        height: height,
        decoration: BoxDecoration(
          color: const Color(0xFF48C88A),
          borderRadius: BorderRadius.circular(5),
        ),
      );
}

class AuthInputField extends StatelessWidget {
  const AuthInputField({
    required this.controller,
    required this.hint,
    required this.icon,
    this.helper,
    this.keyboardType,
    this.textCapitalization = TextCapitalization.none,
    this.textInputAction,
    this.autofillHints,
    this.obscureText = false,
    this.suffix,
    this.errorText,
    this.onSubmitted,
    super.key,
  });

  final TextEditingController controller;
  final String hint;
  final String? helper;
  final IconData icon;
  final TextInputType? keyboardType;
  final TextCapitalization textCapitalization;
  final TextInputAction? textInputAction;
  final Iterable<String>? autofillHints;
  final bool obscureText;
  final Widget? suffix;
  final String? errorText;
  final ValueChanged<String>? onSubmitted;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            constraints: BoxConstraints(minHeight: helper == null ? 66 : 72),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
            decoration: BoxDecoration(
              color: AuthColors.field.withValues(alpha: .94),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: AuthColors.stroke, width: 1),
            ),
            child: Row(
              children: [
                Icon(icon, size: 25, color: AuthColors.inkSoft),
                const SizedBox(width: 20),
                Expanded(
                  child: helper == null
                      ? TextField(
                          controller: controller,
                          keyboardType: keyboardType,
                          textCapitalization: textCapitalization,
                          textInputAction: textInputAction,
                          autofillHints: autofillHints,
                          obscureText: obscureText,
                          onSubmitted: onSubmitted,
                          style: const TextStyle(
                            color: AuthColors.inkSoft,
                            fontSize: 16,
                            fontWeight: FontWeight.w500,
                          ),
                          decoration: InputDecoration(
                            hintText: hint,
                            hintStyle: const TextStyle(
                                color: Color(0xFF8994A6), fontSize: 16),
                            border: InputBorder.none,
                            enabledBorder: InputBorder.none,
                            focusedBorder: InputBorder.none,
                            isDense: true,
                            contentPadding: EdgeInsets.zero,
                          ),
                        )
                      : Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              hint,
                              style: const TextStyle(
                                color: AuthColors.inkSoft,
                                fontSize: 14.5,
                                height: 1.2,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 2),
                            TextField(
                              controller: controller,
                              keyboardType: keyboardType,
                              textCapitalization: textCapitalization,
                              textInputAction: textInputAction,
                              autofillHints: autofillHints,
                              obscureText: obscureText,
                              onSubmitted: onSubmitted,
                              style: const TextStyle(
                                color: AuthColors.inkSoft,
                                fontSize: 14.5,
                                height: 1.2,
                              ),
                              decoration: InputDecoration(
                                hintText: helper,
                                hintStyle: const TextStyle(
                                  color: Color(0xFF8994A6),
                                  fontSize: 14.5,
                                ),
                                border: InputBorder.none,
                                enabledBorder: InputBorder.none,
                                focusedBorder: InputBorder.none,
                                isDense: true,
                                contentPadding: EdgeInsets.zero,
                              ),
                            ),
                          ],
                        ),
                ),
                if (suffix != null) ...[const SizedBox(width: 2), suffix!],
              ],
            ),
          ),
          if (errorText != null) ...[
            const SizedBox(height: 4),
            Padding(
              padding: const EdgeInsets.only(left: 12),
              child: Text(
                errorText!,
                style: TextStyle(
                    color: Theme.of(context).colorScheme.error, fontSize: 12),
              ),
            ),
          ],
        ],
      );
}

class AuthPrimaryButton extends StatelessWidget {
  const AuthPrimaryButton({
    required this.label,
    required this.onPressed,
    this.isLoading = false,
    super.key,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool isLoading;

  @override
  Widget build(BuildContext context) => SizedBox(
        height: 64,
        child: DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF22BE80), Color(0xFF00875D)],
            ),
            boxShadow: const [
              BoxShadow(
                  color: Color(0x3325A56E),
                  blurRadius: 14,
                  offset: Offset(0, 6)),
            ],
          ),
          child: FilledButton(
            onPressed: onPressed,
            style: FilledButton.styleFrom(
              backgroundColor: Colors.transparent,
              disabledBackgroundColor: Colors.transparent,
              shadowColor: Colors.transparent,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(22)),
            ),
            child: isLoading
                ? const SizedBox.square(
                    dimension: 23,
                    child: CircularProgressIndicator(
                        strokeWidth: 2.4, color: Colors.white),
                  )
                : Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        label,
                        style: const TextStyle(
                            fontSize: 18, fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(width: 18),
                      const Icon(Icons.arrow_forward_rounded, size: 25),
                    ],
                  ),
          ),
        ),
      );
}

class AuthOutlinedButton extends StatelessWidget {
  const AuthOutlinedButton(
      {required this.label, required this.onPressed, super.key});
  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) => SizedBox(
        height: 58,
        child: OutlinedButton(
          onPressed: onPressed,
          style: OutlinedButton.styleFrom(
            foregroundColor: AuthColors.greenDark,
            side: const BorderSide(color: Color(0xFF18B67A), width: 1.2),
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(21)),
          ),
          child: Text(label,
              style:
                  const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
        ),
      );
}

class AuthBottomPrompt extends StatelessWidget {
  const AuthBottomPrompt({
    required this.leading,
    required this.action,
    required this.onTapRoute,
    this.disabled = false,
    super.key,
  });

  final String leading;
  final String action;
  final String onTapRoute;
  final bool disabled;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 26, bottom: 4),
        child: Wrap(
          alignment: WrapAlignment.center,
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 7,
          children: [
            Text(leading,
                style: const TextStyle(color: AuthColors.muted, fontSize: 15)),
            TextButton(
              onPressed: disabled ? null : () => context.go(onTapRoute),
              style: TextButton.styleFrom(
                foregroundColor: AuthColors.greenDark,
                minimumSize: const Size(44, 44),
                padding: const EdgeInsets.symmetric(horizontal: 2),
                textStyle:
                    const TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
              ),
              child: Text(action),
            ),
          ],
        ),
      );
}

class AuthErrorBanner extends StatelessWidget {
  const AuthErrorBanner({required this.message, super.key});
  final String message;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0xFFFFF1F0),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFFFFD5D1)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.error_outline_rounded,
                size: 18, color: Color(0xFFB42318)),
            const SizedBox(width: 8),
            Expanded(
              child: Text(message,
                  style:
                      const TextStyle(color: Color(0xFF912018), height: 1.35)),
            ),
          ],
        ),
      );
}
