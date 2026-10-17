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
      body: SafeArea(
        child: auth.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, __) => Center(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Text(
                  'Kh\u{F4}ng th\u{1EC3} t\u{1EA3}i phi\u{EA}n \u{111}\u{103}ng nh\u{1EAD}p c\u{1EE5}c b\u{1ED9}.'),
              TextButton(
                onPressed: () => ref.invalidate(authStateProvider),
                child: const Text('Th\u{1EED} l\u{1EA1}i'),
              ),
            ]),
          ),
          data: (status) => _HomeContent(
              authed: status != AuthenticationStatus.unauthenticated),
        ),
      ),
    );
  }
}

class _HomeContent extends StatelessWidget {
  const _HomeContent({required this.authed});
  final bool authed;
  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 96),
        children: [
          Row(children: [
            const Icon(Icons.eco, color: Color(0xff15966e), size: 30),
            const SizedBox(width: 7),
            Text('SaveMon',
                style: Theme.of(context)
                    .textTheme
                    .titleLarge
                    ?.copyWith(fontWeight: FontWeight.w800)),
            const Spacer(),
            IconButton(
                onPressed: () {}, icon: const Icon(Icons.notifications_none)),
            const CircleAvatar(child: Icon(Icons.person_outline))
          ]),
          const SizedBox(height: 18),
          if (!authed) ...[
            Text(
                'Ch\u{00E0}o m\u{1EEB}ng b\u{1EA1}n \u{0111}\u{1EBF}n v\u{1EDB}i SaveMon',
                style: Theme.of(context).textTheme.headlineSmall),
            const Text(
                '\u{0110}\u{0103}ng nh\u{1EAD}p ho\u{1EB7}c t\u{1EA1}o t\u{00E0}i kho\u{1EA3}n \u{0111}\u{1EC3} qu\u{1EA3}n l\u{00FD} chi ti\u{00EA}u c\u{1EE7}a b\u{1EA1}n.'),
            const SizedBox(height: 18),
            FilledButton(
                onPressed: () => context.push('/login'),
                child: const Text('\u{0110}\u{0103}ng nh\u{1EAD}p')),
            OutlinedButton(
                onPressed: () => context.push('/register'),
                child: const Text('\u{0110}\u{0103}ng k\u{00FD}')),
            Card(
                child: ListTile(
              leading: const Icon(Icons.groups_outlined),
              title: const Text('Qu\u{1EF9} chung'),
              subtitle: const Text(
                  '\u{0110}\u{0103}ng nh\u{1EAD}p \u{0111}\u{1EC3} xem c\u{00E1}c qu\u{1EF9} c\u{1EE7}a b\u{1EA1}n'),
              onTap: () => context.go('/shared-finance'),
            )),
          ] else ...[
            const Card(
                child: Padding(
              padding: EdgeInsets.all(12),
              child: Text(
                  'B\u{1EA3}n xem tr\u{01B0}\u{1EDB}c \u{00B7} S\u{1ED1} li\u{1EC7}u minh h\u{1ECD}a, kh\u{00F4}ng ph\u{1EA3}i d\u{1EEF} li\u{1EC7}u t\u{00E0}i kho\u{1EA3}n',
                  style: TextStyle(color: Color(0xff53645d))),
            )),
            Wrap(spacing: 8, children: [
              OutlinedButton.icon(
                  onPressed: () => context.push('/expense-history'),
                  icon: const Icon(Icons.receipt_long),
                  label: const Text('L\u{1ECB}ch s\u{1EED} chi ti\u{00EA}u')),
              OutlinedButton.icon(
                  onPressed: () => context.push('/expense-scan'),
                  icon: const Icon(Icons.document_scanner),
                  label:
                      const Text('Qu\u{00E9}t h\u{00F3}a \u{0111}\u{01A1}n')),
            ]),
            Text('Xin ch\u00e0o, Minh Anh! \u{1F44B}',
                style: Theme.of(context)
                    .textTheme
                    .headlineSmall
                    ?.copyWith(fontWeight: FontWeight.w800)),
            const Text(
                'C\u00f9ng qu\u1ea3n l\u00fd chi ti\u00eau, h\u01b0\u1edbng t\u1edbi nh\u1eefng m\u1ee5c ti\u00eau l\u1edbn h\u01a1n m\u1ed7i ng\u00e0y nh\u00e9!'),
            const SizedBox(height: 18),
            Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                    gradient: const LinearGradient(
                        colors: [Color(0xff20a97b), Color(0xff087b56)]),
                    borderRadius: BorderRadius.circular(22)),
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('T\u1ed5ng chi th\u00e1ng 4/2025',
                          style: TextStyle(color: Colors.white)),
                      const SizedBox(height: 5),
                      Text('8.250.000 \u20ab',
                          style: Theme.of(context)
                              .textTheme
                              .headlineMedium
                              ?.copyWith(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w800)),
                      const Row(children: [
                        Icon(Icons.south, color: Colors.white, size: 16),
                        Text(' 12% so v\u1edbi th\u00e1ng tr\u01b0\u1edbc',
                            style: TextStyle(color: Colors.white))
                      ])
                    ])),
            const SizedBox(height: 14),
            _CardSection(
                title: 'Ng\u{E2}n s\u{E1}ch',
                trailing: 'Xem chi ti\u{1EBF}t',
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                          '\u0110\u00e3 chi 8.250.000 / 12.000.000 \u20ab'),
                      const SizedBox(height: 10),
                      ClipRRect(
                          borderRadius: BorderRadius.circular(20),
                          child: const LinearProgressIndicator(
                              value: .69, minHeight: 9)),
                      const SizedBox(height: 5),
                      const Align(
                          alignment: Alignment.centerRight,
                          child: Text('69%',
                              style: TextStyle(
                                  color: Color(0xff12805c),
                                  fontWeight: FontWeight.bold)))
                    ])),
            const SizedBox(height: 14),
            _CardSection(
                title: 'Danh m\u{1EE5}c chi ti\u{EA}u',
                trailing: 'Xem t\u{1EA5}t c\u{1EA3}',
                child: const Row(children: [
                  Expanded(
                      child: _Category(
                          icon: Icons.restaurant,
                          name: '\u{102}n u\u{1ED1}ng',
                          total: '2.850.000 \u{20AB}',
                          color: Color(0xffff8a43))),
                  Expanded(
                      child: _Category(
                          icon: Icons.directions_car,
                          name: 'Di chuy\u{1EC3}n',
                          total: '1.650.000 \u{20AB}',
                          color: Color(0xff2799e8))),
                  Expanded(
                      child: _Category(
                          icon: Icons.shopping_bag,
                          name: 'Mua s\u{1EAF}m',
                          total: '1.420.000 \u{20AB}',
                          color: Color(0xffed4e85))),
                  Expanded(
                      child: _Category(
                          icon: Icons.receipt_long,
                          name: 'H\u{F3}a \u{111}\u{1A1}n',
                          total: '980.000 \u{20AB}',
                          color: Color(0xffa66be7)))
                ])),
            const SizedBox(height: 14),
            _CardSection(
                title: 'Giao d\u{1ECB}ch g\u{1EA7}n \u{111}\u{E2}y',
                trailing: 'Xem t\u{1EA5}t c\u{1EA3}',
                child: Column(children: const [
                  _Tx(
                      icon: Icons.coffee,
                      name: 'Starbucks',
                      amount: '- 120.000 \u{20AB}',
                      category: '\u{102}n u\u{1ED1}ng'),
                  _Tx(
                      icon: Icons.directions_car,
                      name: 'Grab',
                      amount: '- 85.000 \u{20AB}',
                      category: 'Di chuy\u{1EC3}n'),
                  _Tx(
                      icon: Icons.shopping_cart,
                      name: 'WinMart',
                      amount: '- 320.000 \u{20AB}',
                      category: 'Mua s\u{1EAF}m')
                ])),
          ],
        ],
      );
}

class _CardSection extends StatelessWidget {
  const _CardSection({required this.title, required this.child, this.trailing});
  final String title;
  final Widget child;
  final String? trailing;
  @override
  Widget build(BuildContext context) => Card(
      elevation: 1,
      color: Colors.white,
      child: Padding(
          padding: const EdgeInsets.all(15),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Expanded(
                  child: Text(title,
                      style: const TextStyle(
                          fontWeight: FontWeight.w700, fontSize: 16))),
              if (trailing != null)
                Text(trailing!,
                    style:
                        const TextStyle(color: Color(0xff168562), fontSize: 12))
            ]),
            const SizedBox(height: 13),
            child
          ])));
}

class _Category extends StatelessWidget {
  const _Category(
      {required this.icon,
      required this.name,
      required this.total,
      required this.color});
  final IconData icon;
  final String name, total;
  final Color color;
  @override
  Widget build(BuildContext context) => Column(children: [
        CircleAvatar(
            backgroundColor: color.withValues(alpha: .14),
            foregroundColor: color,
            child: Icon(icon)),
        const SizedBox(height: 7),
        Text(name,
            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600)),
        Text(total, style: const TextStyle(fontSize: 9))
      ]);
}

class _Tx extends StatelessWidget {
  const _Tx(
      {required this.icon,
      required this.name,
      required this.amount,
      required this.category});
  final IconData icon;
  final String name, amount, category;
  @override
  Widget build(BuildContext context) => ListTile(
      dense: true,
      contentPadding: EdgeInsets.zero,
      leading: CircleAvatar(
          backgroundColor: const Color(0xffe0f3eb),
          child: Icon(icon, color: const Color(0xff11825b), size: 19)),
      title: Text(name,
          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
      subtitle: Text('H\u00f4m nay \u00b7 09:24 \u00b7 ',
          style: const TextStyle(fontSize: 11)),
      trailing: Text(amount,
          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)));
}
