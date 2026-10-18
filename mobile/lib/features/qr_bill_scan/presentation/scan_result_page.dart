import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'expense_scan_controller.dart';

class ScanResultPage extends ConsumerWidget {
  const ScanResultPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scan = ref.watch(expenseScanControllerProvider);
    final controller = ref.read(expenseScanControllerProvider.notifier);
    if (scan.stage != ExpenseScanStage.review || scan.draft == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Kết quả quét')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.hourglass_empty, size: 48),
              const SizedBox(height: 12),
              const Text(
                  'Không còn bản nháp trong phiên này. Hãy quét lại hoặc nhập thủ công.'),
              TextButton(
                onPressed: () {
                  controller.discard();
                  context.go('/expense-scan');
                },
                child: const Text('Quay lại hướng dẫn'),
              ),
            ]),
          ),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Kiểm tra hóa đơn')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        const Card(
            child: ListTile(
                leading: Icon(Icons.fact_check_outlined),
                title: Text('Bản nháp chưa lưu'),
                subtitle:
                    Text('Kiểm tra và sửa thông tin. Chưa tạo chi tiêu.'))),
        _field('Nhà cung cấp', 'scan-merchant', scan.merchant,
            (v) => controller.edit(merchant: v)),
        _field('Tổng đã thanh toán', 'scan-total', scan.paidTotal,
            (v) => controller.edit(paidTotal: v)),
        _field('Tiền tệ', 'scan-currency', scan.currency,
            (v) => controller.edit(currency: v)),
        _field('Ngày (YYYY-MM-DD)', 'scan-date', scan.transactionDate,
            (v) => controller.edit(date: v)),
        if (scan.draft!.missingOrAmbiguousFields.isNotEmpty)
          const Text('Có trường thiếu hoặc chưa rõ; vui lòng tự xác minh.',
              style: TextStyle(color: Colors.deepOrange)),
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 12),
          child: Text('Ngày hóa đơn chỉ có ngày; không suy diễn thời gian.'),
        ),
        const Text(
            'Việc xác nhận và tạo chi tiêu chưa được kết nối trong phần scan.'),
        TextButton(
          onPressed: () {
            controller.discard();
            context.pop();
          },
          child: const Text('Hủy và xóa bản nháp'),
        ),
      ]),
    );
  }

  Widget _field(String label, String key, String value,
          ValueChanged<String> changed) =>
      TextFormField(
        key: ValueKey(key),
        initialValue: value,
        onChanged: changed,
        decoration: InputDecoration(labelText: label),
      );
}
