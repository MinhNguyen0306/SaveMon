import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

enum PreviewScreenState { loading, ready, empty, error }

final historyPreviewStateProvider = StateProvider<PreviewScreenState>(
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
          Align(
            alignment: Alignment.centerLeft,
            child: Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(
                'D\u{1EEF} li\u{1EC7}u minh h\u{1ECD}a',
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
            ),
          ),
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
            label: const Text('Qu\u{E9}t h\u{F3}a \u{111}\u{1A1}n'),
          ),
        ],
      ),
    );
  }
}
