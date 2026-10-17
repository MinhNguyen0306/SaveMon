import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Static presentation-only scanner preview. It deliberately does not open a
/// camera, decode a QR code, or create a financial transaction.
class ExpenseScanPage extends StatelessWidget {
  const ExpenseScanPage({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xff211914),
        body: SafeArea(
          child: LayoutBuilder(
            builder: (context, viewport) => SingleChildScrollView(
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: viewport.maxHeight),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 20),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          IconButton(
                            tooltip: 'Quay \u{1EA1}i',
                            onPressed: () => context.pop(),
                            color: Colors.white,
                            icon: const Icon(Icons.arrow_back),
                          ),
                          const Expanded(
                            child: Text(
                              'Qu\u{00E9}t QR h\u{00F3}a \u{0111}\u{01A1}n',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 18,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                          IconButton(
                            tooltip:
                                'Xem k\u{1EBF}t qu\u{1EA3} minh h\u{1ECD}a',
                            onPressed: () =>
                                context.push('/expense-scan/result'),
                            color: Colors.white,
                            icon: const Icon(Icons.image_outlined),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      const Text(
                        '\u{0110}\u{01B0}a m\u{00E3} QR v\u{00E0}o khung \u{0111}\u{1EC3} qu\u{00E9}t t\u{1EF1} \u{0111}\u{1ED9}ng',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Colors.white70),
                      ),
                      const SizedBox(height: 18),
                      LayoutBuilder(
                        builder: (context, constraints) {
                          final previewHeight = math.min(
                            390.0,
                            math.max(210.0, viewport.maxHeight * .48),
                          );
                          final previewWidth = math.min(
                            360.0,
                            constraints.maxWidth,
                          );
                          return SizedBox(
                            width: previewWidth,
                            height: previewHeight,
                            child: DecoratedBox(
                              decoration: BoxDecoration(
                                gradient: const LinearGradient(
                                  begin: Alignment.topLeft,
                                  end: Alignment.bottomRight,
                                  colors: [
                                    Color(0xff384127),
                                    Color(0xff76523b),
                                    Color(0xffb77a4c),
                                  ],
                                ),
                                borderRadius: BorderRadius.circular(20),
                              ),
                              child: Stack(
                                alignment: Alignment.center,
                                children: [
                                  Positioned(
                                    left: 18,
                                    top: 24,
                                    child: Icon(Icons.local_cafe,
                                        size: 42,
                                        color: Colors.white
                                            .withValues(alpha: .12)),
                                  ),
                                  Container(
                                    width: math.min(280.0, previewWidth - 34),
                                    height: previewHeight - 26,
                                    decoration: BoxDecoration(
                                      border: Border.all(
                                          color: Colors.white, width: 2.5),
                                      borderRadius: BorderRadius.circular(20),
                                    ),
                                    child: Stack(
                                      alignment: Alignment.center,
                                      children: [
                                        _ReceiptPreview(
                                          width: math.min(
                                              200.0, previewWidth - 70),
                                          height: math.min(
                                              250.0, previewHeight - 58),
                                        ),
                                        const Positioned(
                                            top: 4, left: 4, child: _Corner()),
                                        const Positioned(
                                            top: 4,
                                            right: 4,
                                            child: RotatedBox(
                                                quarterTurns: 1,
                                                child: _Corner())),
                                        const Positioned(
                                            bottom: 4,
                                            left: 4,
                                            child: RotatedBox(
                                                quarterTurns: 3,
                                                child: _Corner())),
                                        const Positioned(
                                            bottom: 4,
                                            right: 4,
                                            child: RotatedBox(
                                                quarterTurns: 2,
                                                child: _Corner())),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 16),
                        child: Text(
                          'M\u{00E1}y qu\u{00E9}t ch\u{01B0}a \u{0111}\u{01B0}\u{1EE3}c b\u{1EAD}t \u{00B7} b\u{1EA3}n xem tr\u{01B0}\u{1EDB}c t\u{0129}nh',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: Colors.white70),
                        ),
                      ),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          IconButton.filledTonal(
                            tooltip:
                                '\u{0110}\u{00E8}n pin (b\u{1EA3}n xem tr\u{01B0}\u{1EDB}c)',
                            onPressed: () {},
                            icon: const Icon(Icons.flashlight_on),
                          ),
                          IconButton.filledTonal(
                            tooltip:
                                'M\u{1EDF} k\u{1EBF}t qu\u{1EA3} minh h\u{1ECD}a',
                            onPressed: () =>
                                context.push('/expense-scan/result'),
                            icon: const Icon(Icons.image),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      );
}

class _ReceiptPreview extends StatelessWidget {
  const _ReceiptPreview({required this.width, required this.height});
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) => Container(
        width: width,
        height: height,
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 5),
        decoration: BoxDecoration(
          color: const Color(0xfffffbf4),
          borderRadius: BorderRadius.circular(3),
          boxShadow: const [
            BoxShadow(
                color: Colors.black38, blurRadius: 10, offset: Offset(0, 5)),
          ],
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            const Icon(Icons.coffee, size: 20, color: Color(0xff147452)),
            const Text('STARBUCKS',
                style: TextStyle(
                    fontWeight: FontWeight.w900,
                    letterSpacing: 1,
                    fontSize: 11)),
            const Text(
              'H\u{00D3}A \u{0110}\u{01A0}N B\u{00C1}N H\u{00C0}NG',
              style: TextStyle(fontSize: 7, fontWeight: FontWeight.w700),
            ),
            const Text(
              '72 Nguy\u{1EC5}n Hu\u{1EC7} \u{00B7} TP. H\u{1ED3} Ch\u{00ED} Minh',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 6),
            ),
            Icon(Icons.qr_code_2, size: math.min(64, height * .3)),
            const Text('T\u{1ED5}ng ti\u{1EC1}n: 120.000 \u{0111}',
                style: TextStyle(fontSize: 7, fontWeight: FontWeight.w700)),
          ],
        ),
      );
}

class _Corner extends StatelessWidget {
  const _Corner();
  @override
  Widget build(BuildContext context) => const SizedBox(
        width: 28,
        height: 28,
        child: DecoratedBox(
          decoration: BoxDecoration(
            border: Border(
              top: BorderSide(color: Color(0xff04a66f), width: 4),
              left: BorderSide(color: Color(0xff04a66f), width: 4),
            ),
          ),
        ),
      );
}
