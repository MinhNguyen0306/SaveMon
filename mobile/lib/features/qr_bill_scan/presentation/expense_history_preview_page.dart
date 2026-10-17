import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

enum PreviewScreenState { loading, ready, empty, error }

final historyPreviewStateProvider = StateProvider<PreviewScreenState>(
  (ref) => PreviewScreenState.ready,
);
final scanResultPreviewStateProvider = StateProvider<PreviewScreenState>(
  (ref) => PreviewScreenState.ready,
);

class ExpenseHistoryPage extends ConsumerWidget {
  const ExpenseHistoryPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(historyPreviewStateProvider);
    if (state == PreviewScreenState.loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (state == PreviewScreenState.empty) {
      return Scaffold(
        appBar:
            AppBar(title: const Text('L\u{1ECB}ch s\u{1EED} chi ti\u{EA}u')),
        body: const Center(
            child: Text(
                'Ch\u{1B0}a c\u{F3} giao d\u{1ECB}ch \u{111}\u{1EC3} hi\u{1EC3}n th\u{1ECB}.')),
      );
    }
    if (state == PreviewScreenState.error) {
      return Scaffold(
        appBar:
            AppBar(title: const Text('L\u{1ECB}ch s\u{1EED} chi ti\u{EA}u')),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                  'Kh\u{F4}ng th\u{1EC3} t\u{1EA3}i l\u{1ECB}ch s\u{1EED} xem tr\u{1B0}\u{1EDB}c.'),
              TextButton(
                onPressed: () => ref
                    .read(historyPreviewStateProvider.notifier)
                    .state = PreviewScreenState.ready,
                child: const Text('Th\u{1EED} l\u{1EA1}i'),
              ),
            ],
          ),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('L\u{1ECB}ch s\u{1EED} chi ti\u{EA}u')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Wrap(
            spacing: 8,
            children: [
              'T\u{1EA5}t c\u{1EA3}',
              '\u{102}n u\u{1ED1}ng',
              'Di chuy\u{1EC3}n',
              'Mua s\u{1EAF}m',
            ].map((x) => Chip(label: Text(x))).toList(),
          ),
          const SizedBox(height: 12),
          const TextField(
            decoration: InputDecoration(
              hintText:
                  'T\u{EC}m ki\u{1EBF}m giao d\u{1ECB}ch, nh\u{E0} cung c\u{1EA5}p...',
              prefixIcon: Icon(Icons.search),
            ),
          ),
          const SizedBox(height: 16),
          const Text(
            'Th\u{E1}ng 4, 2025',
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          const Card(
            child: Column(
              children: [
                ListTile(
                  leading: CircleAvatar(child: Icon(Icons.coffee)),
                  title: Text('Starbucks'),
                  subtitle: Text(
                      'H\u{F4}m nay \u{B7} 09:24 \u{B7} \u0102n u\u{1ED1}ng'),
                  trailing: Text('\u{2212} 120.000 \u{20AB}'),
                ),
                Divider(height: 1),
                ListTile(
                  leading: CircleAvatar(child: Icon(Icons.directions_car)),
                  title: Text('Grab'),
                  subtitle:
                      Text('H\u{F4}m qua \u{B7} 18:12 \u{B7} Di chuy\u{1EC3}n'),
                  trailing: Text('\u{2212} 85.000 \u{20AB}'),
                ),
                Divider(height: 1),
                ListTile(
                  leading: CircleAvatar(child: Icon(Icons.shopping_bag)),
                  title: Text('WinMart'),
                  subtitle:
                      Text('12/04/2025 \u{B7} 20:31 \u{B7} Mua s\u{1EAF}m'),
                  trailing: Text('\u{2212} 320.000 \u{20AB}'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: () => context.push('/expense-scan'),
            icon: const Icon(Icons.document_scanner),
            label: const Text('Qu\u{E9}t QR h\u{F3}a \u{111}\u{1A1}n'),
          ),
        ],
      ),
    );
  }
}

class ScanResultPage extends ConsumerWidget {
  const ScanResultPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(scanResultPreviewStateProvider);
    if (state == PreviewScreenState.loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (state == PreviewScreenState.empty) {
      return Scaffold(
        appBar: AppBar(title: const Text('K\u{1EBF}t qu\u{1EA3} qu\u{E9}t')),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                  'Ch\u{1B0}a c\u{F3} d\u{1EEF} li\u{1EC7}u h\u{F3}a \u{111}\u{1A1}n.'),
              TextButton(
                onPressed: () => context.go('/expense-scan'),
                child: const Text('Quay l\u{1EA1}i m\u{E1}y qu\u{E9}t'),
              ),
            ],
          ),
        ),
      );
    }
    if (state == PreviewScreenState.error) {
      return Scaffold(
        appBar: AppBar(title: const Text('K\u{1EBF}t qu\u{1EA3} qu\u{E9}t')),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                  'Kh\u{F4}ng th\u{1EC3} t\u{1EA3}i b\u{1EA3}n xem tr\u{1B0}\u{1EDB}c.'),
              TextButton(
                onPressed: () => ref
                    .read(scanResultPreviewStateProvider.notifier)
                    .state = PreviewScreenState.ready,
                child: const Text('Th\u{1EED} l\u{1EA1}i'),
              ),
            ],
          ),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('K\u{1EBF}t qu\u{1EA3} qu\u{E9}t')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Card(
            child: ListTile(
              leading: Icon(Icons.check_circle, color: Colors.green),
              title: Text('B\u{1EA3}n xem tr\u{1B0}\u{1EDB}c'),
              subtitle: Text(
                  'D\u{1EEF} li\u{1EC7}u minh h\u{1ECD}a; ch\u{1B0}a qu\u{E9}t, ch\u{1B0}a l\u{1B0}u.'),
            ),
          ),
          const Card(
            child: Column(
              children: [
                ListTile(
                  title: Text('Nh\u{E0} cung c\u{1EA5}p'),
                  subtitle: Text(
                      'Starbucks \u{B7} 72 Nguy\u{1EC5}n Hu\u{1EC7}, Qu\u{1EAD}n 1, TP. H\u{1ED3} Ch\u{ED} Minh'),
                ),
                Divider(height: 1),
                ListTile(
                  title: Text('Ng\u{E0}y giao d\u{1ECB}ch'),
                  trailing: Text('12/04/2025'),
                ),
                Divider(height: 1),
                ListTile(
                  title: Text('S\u{1ED1} h\u{F3}a \u{111}\u{1A1}n'),
                  trailing: Text('01234567'),
                ),
                Divider(height: 1),
                ListTile(
                    title: Text('T\u{1ED5}ng ti\u{1EC1}n'),
                    trailing: Text('120.000 \u{20AB}')),
                Divider(height: 1),
                ListTile(
                    title: Text('Thu\u{1EBF} VAT (8%)'),
                    trailing: Text('8.889 \u{20AB}')),
                Divider(height: 1),
                ListTile(
                    title: Text('Th\u{E0}nh ti\u{1EC1}n (ch\u{1B0}a VAT)'),
                    trailing: Text('111.111 \u{20AB}')),
              ],
            ),
          ),
          const SizedBox(height: 12),
          const Text('Danh m\u{1EE5}c g\u{1EE3}i \u{FD}',
              style: TextStyle(fontWeight: FontWeight.w700)),
          const ListTile(
              leading: Icon(Icons.restaurant),
              title: Text('\u{102}n u\u{1ED1}ng'),
              trailing: Icon(Icons.expand_more)),
          const TextField(
              decoration: InputDecoration(
                  labelText: 'Ghi ch\u{FA} (t\u{F9}y ch\u{1ECD}n)',
                  hintText: 'Nh\u{1EAD}p ghi ch\u{FA}')),
          FilledButton(
            onPressed: () => ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                  content: Text(
                      'B\u{1EA3}n xem tr\u{1B0}\u{1EDB}c, giao d\u{1ECB}ch ch\u{1B0}a \u{111}\u{1B0}\u{1EE3}c l\u{1B0}u.')),
            ),
            child: const Text('L\u{1B0}u chi ti\u{EA}u'),
          ),
        ],
      ),
    );
  }
}
