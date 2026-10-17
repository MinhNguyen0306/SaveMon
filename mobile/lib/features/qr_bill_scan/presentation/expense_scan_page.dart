import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class ExpenseScanPage extends StatelessWidget {
  const ExpenseScanPage({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xff241b16),
        body: SafeArea(
            child: LayoutBuilder(
                builder: (context, constraints) => SingleChildScrollView(
                    child: ConstrainedBox(
                        constraints:
                            BoxConstraints(minHeight: constraints.maxHeight),
                        child: Column(children: [
                          Padding(
                              padding: const EdgeInsets.all(12),
                              child: Row(children: [
                                IconButton(
                                    onPressed: () => context.pop(),
                                    color: Colors.white,
                                    icon: const Icon(Icons.arrow_back)),
                                const Expanded(
                                    child: Text(
                                        'Qu\u00e9t QR h\u00f3a \u0111\u01a1n',
                                        textAlign: TextAlign.center,
                                        style: TextStyle(
                                            color: Colors.white,
                                            fontSize: 18,
                                            fontWeight: FontWeight.w700))),
                                IconButton(
                                    onPressed: () =>
                                        context.push('/expense-scan/result'),
                                    color: Colors.white,
                                    icon: const Icon(Icons.image_outlined)),
                              ])),
                          const Text(
                              '\u0110\u01b0a m\u00e3 QR v\u00e0o khung \u0111\u1ec3 qu\u00e9t t\u1ef1 \u0111\u1ed9ng',
                              style: TextStyle(color: Colors.white70)),
                          SizedBox(
                              height: (constraints.maxHeight * .48)
                                  .clamp(180.0, 420.0),
                              child: Center(
                                  child: Container(
                                margin: const EdgeInsets.all(22),
                                width: constraints.maxWidth.clamp(220.0, 320.0),
                                height: (constraints.maxHeight * .46)
                                    .clamp(180.0, 390.0),
                                decoration: BoxDecoration(
                                    color: const Color(0xff9e704b),
                                    borderRadius: BorderRadius.circular(20)),
                                child: Center(
                                    child: Container(
                                  width:
                                      constraints.maxWidth.clamp(190.0, 260.0),
                                  height: (constraints.maxHeight * .38)
                                      .clamp(150.0, 330.0),
                                  decoration: BoxDecoration(
                                      border: Border.all(
                                          color: Colors.white, width: 3),
                                      borderRadius: BorderRadius.circular(24)),
                                  child: Stack(
                                      alignment: Alignment.center,
                                      children: [
                                        Container(
                                            width: (constraints.maxWidth * .4)
                                                .clamp(120.0, 170.0),
                                            height:
                                                (constraints.maxHeight * .28)
                                                    .clamp(120.0, 170.0),
                                            decoration: BoxDecoration(
                                                color: const Color(0xfffaf8f4),
                                                borderRadius:
                                                    BorderRadius.circular(12)),
                                            child: Column(
                                                mainAxisAlignment:
                                                    MainAxisAlignment.center,
                                                children: [
                                                  Icon(Icons.coffee,
                                                      size: (constraints
                                                                  .maxHeight *
                                                              .07)
                                                          .clamp(24.0, 40.0),
                                                      color: Color(0xff147452)),
                                                  const Text('STARBUCKS',
                                                      style: TextStyle(
                                                          fontWeight:
                                                              FontWeight.w900)),
                                                  SizedBox(height: 10),
                                                  Icon(Icons.qr_code_2,
                                                      size: (constraints
                                                                  .maxHeight *
                                                              .12)
                                                          .clamp(52.0, 70.0))
                                                ])),
                                        const Positioned(
                                            top: 8, left: 8, child: _Corner()),
                                        const Positioned(
                                            top: 8, right: 8, child: _Corner()),
                                        const Positioned(
                                            bottom: 8,
                                            left: 8,
                                            child: _Corner()),
                                        const Positioned(
                                            bottom: 8,
                                            right: 8,
                                            child: _Corner()),
                                      ]),
                                )),
                              ))),
                          const Padding(
                              padding: EdgeInsets.all(20),
                              child: Text(
                                  'M\u{E1}y qu\u{E9}t ch\u{1B0}a \u{111}\u{1B0}\u{1EE3}c b\u{1EAD}t \u{B7} b\u{1EA3}n xem tr\u{1B0}\u{1EDB}c',
                                  style: TextStyle(color: Colors.white70))),
                          Padding(
                              padding: const EdgeInsets.only(bottom: 20),
                              child: Row(
                                  mainAxisAlignment:
                                      MainAxisAlignment.spaceEvenly,
                                  children: [
                                    IconButton.filledTonal(
                                        onPressed: () {},
                                        icon: const Icon(Icons.flashlight_on)),
                                    IconButton.filledTonal(
                                        onPressed: () => context
                                            .push('/expense-scan/result'),
                                        icon: const Icon(Icons.image))
                                  ])),
                        ]))))),
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
                  left: BorderSide(color: Color(0xff04a66f), width: 4)))));
}
