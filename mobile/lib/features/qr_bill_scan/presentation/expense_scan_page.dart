import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../data/expense_scan_repository.dart';
import 'expense_scan_controller.dart';

class ExpenseScanPage extends ConsumerWidget {
  const ExpenseScanPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(expenseScanControllerProvider);
    final controller = ref.read(expenseScanControllerProvider.notifier);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Quét hóa đơn nhà hàng'),
        leading: IconButton(
          tooltip: 'Quay lại',
          icon: const Icon(Icons.arrow_back),
          onPressed: () {
            controller.discard();
            if (context.canPop()) context.pop();
          },
        ),
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
          children: [
            const Icon(Icons.receipt_long_outlined, size: 58),
            const SizedBox(height: 12),
            Text('Chụp rõ toàn bộ hóa đơn',
                textAlign: TextAlign.center,
                style: theme.textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                )),
            const SizedBox(height: 8),
            Text(
              'Đặt hóa đơn phẳng, đủ sáng, ảnh nét và tránh lóa. SaveMon chỉ đề xuất thông tin để bạn kiểm tra; chưa tạo giao dịch.',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium,
            ),
            const SizedBox(height: 22),
            if (state.stage == ExpenseScanStage.selecting ||
                state.stage == ExpenseScanStage.submitting)
              const Card(
                child: Padding(
                  padding: EdgeInsets.all(20),
                  child: Column(children: [
                    CircularProgressIndicator(),
                    SizedBox(height: 12),
                    Text('Đang xử lý ảnh trong phiên hiện tại…'),
                  ]),
                ),
              ),
            if (state.stage == ExpenseScanStage.recovery)
              _RecoveryCard(
                failure: state.failure,
                reasonCode: state.reasonCode,
                onRetry: controller.retry,
                onManual: controller.beginManualEntry,
              ),
            if (state.stage == ExpenseScanStage.manual)
              Card(
                child: ListTile(
                  leading: const Icon(Icons.edit_note),
                  title: const Text('Nhập thủ công'),
                  subtitle: const Text(
                    'Bản scan tạm đã được xóa. Mở khu vực giao dịch để tiếp tục thủ công.',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => context.go('/personal-finance'),
                ),
              ),
            if (state.stage == ExpenseScanStage.review)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Kiểm tra thông tin đề xuất',
                          style: theme.textTheme.titleMedium),
                      const SizedBox(height: 8),
                      const Text('Mọi trường đều có thể chỉnh sửa.'),
                      _editField('Nhà cung cấp', 'scan-merchant',
                          state.merchant, (v) => controller.edit(merchant: v)),
                      _editField(
                          'Tổng đã thanh toán',
                          'scan-total',
                          state.paidTotal,
                          (v) => controller.edit(paidTotal: v)),
                      _editField('Tiền tệ', 'scan-currency', state.currency,
                          (v) => controller.edit(currency: v)),
                      _editField(
                          'Ngày hóa đơn (YYYY-MM-DD)',
                          'scan-date',
                          state.transactionDate,
                          (v) => controller.edit(date: v)),
                      if (state.draft?.missingOrAmbiguousFields.isNotEmpty ??
                          false)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 8),
                          child: Text(
                            'Thiếu hoặc chưa rõ một số trường. Hãy tự kiểm tra, không dùng giá trị chưa xác minh.',
                            style: TextStyle(color: Colors.deepOrange),
                          ),
                        ),
                      const Text(
                        'Ngày hóa đơn chỉ có ngày, không suy diễn giờ giao dịch.',
                      ),
                      const SizedBox(height: 12),
                      const Text(
                        'Bản nháp chỉ tồn tại trong màn hình này. Chưa có thao tác xác nhận chi tiêu trong phần scan.',
                      ),
                    ],
                  ),
                ),
              ),
            const SizedBox(height: 12),
            if (state.stage == ExpenseScanStage.guidance ||
                state.stage == ExpenseScanStage.recovery)
              FilledButton.icon(
                onPressed: state.stage == ExpenseScanStage.submitting
                    ? null
                    : controller.chooseImage,
                icon: const Icon(Icons.photo_camera_outlined),
                label: const Text('Chụp hoặc chọn hóa đơn'),
              ),
            if (state.stage == ExpenseScanStage.guidance ||
                state.stage == ExpenseScanStage.recovery)
              OutlinedButton.icon(
                onPressed: controller.beginManualEntry,
                icon: const Icon(Icons.edit_outlined),
                label: const Text('Nhập thủ công'),
              ),
            if (state.stage == ExpenseScanStage.review ||
                state.stage == ExpenseScanStage.manual)
              TextButton.icon(
                onPressed: controller.discard,
                icon: const Icon(Icons.delete_outline),
                label: const Text('Hủy và xóa bản nháp'),
              ),
          ],
        ),
      ),
    );
  }

  Widget _editField(String label, String key, String value,
          ValueChanged<String> onChanged) =>
      Padding(
        padding: const EdgeInsets.only(top: 10),
        child: TextFormField(
          key: ValueKey(key),
          initialValue: value,
          onChanged: onChanged,
          decoration: InputDecoration(labelText: label),
        ),
      );
}

class _RecoveryCard extends StatelessWidget {
  const _RecoveryCard(
      {required this.failure,
      required this.reasonCode,
      required this.onRetry,
      required this.onManual});
  final Object? failure;
  final String? reasonCode;
  final VoidCallback onRetry;
  final VoidCallback onManual;

  @override
  Widget build(BuildContext context) {
    final reasonMessage = switch (reasonCode) {
      'PAYMENT_QR' =>
        'Ảnh có mã QR thanh toán. Ảnh bị từ chối và không được gửi qua OCR. Hãy chọn hóa đơn khác hoặc nhập thủ công.',
      'UNSUPPORTED_QR_FORMAT' =>
        'Mã QR trên hóa đơn chưa được hỗ trợ hoặc không thể xác minh. Ảnh bị từ chối và không được gửi qua OCR.',
      'UNRELATED_IMAGE' =>
        'Ảnh không được nhận diện là hóa đơn nhà hàng/quán cà phê. Hãy chọn lại ảnh hóa đơn hoặc nhập thủ công.',
      'UNSUPPORTED_BILL_CONTENT' =>
        'Nội dung hóa đơn này chưa được hỗ trợ. Hãy chọn ảnh khác hoặc nhập thủ công.',
      'UNREADABLE_BILL' =>
        'Không đọc rõ được hóa đơn. Hãy chụp lại ảnh phẳng, đủ sáng, nét và không lóa; hoặc nhập thủ công.',
      _ => null,
    };
    final message = reasonMessage ??
        switch (failure) {
          ScanFailureKind.premiumRequired =>
            'Tính năng này yêu cầu gói Premium đang hoạt động.',
          ScanFailureKind.premiumUnavailable =>
            'Không xác minh được quyền Premium. Hãy thử lại sau.',
          ScanFailureKind.unauthorized =>
            'Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.',
          ScanFailureKind.invalidImage =>
            'Ảnh không hợp lệ. Hãy chọn ảnh JPEG hoặc PNG rõ nét.',
          ScanFailureKind.imageTooLarge =>
            'Ảnh vượt quá dung lượng cho phép. Hãy chọn ảnh nhỏ hơn.',
          ScanFailureKind.timeout =>
            'Xử lý quá thời gian. Hãy thử ảnh khác hoặc nhập thủ công.',
          ScanFailureKind.extractionUnavailable =>
            'Chức năng chọn ảnh hoặc OCR hiện chưa khả dụng.',
          _ =>
            'Không thể xử lý ảnh lúc này. Ảnh và bản nháp đã bị xóa khỏi màn hình.',
        };
    return Card(
      color: Theme.of(context).colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(message),
          const SizedBox(height: 8),
          Wrap(spacing: 8, children: [
            TextButton(onPressed: onRetry, child: const Text('Thử lại')),
            TextButton(onPressed: onManual, child: const Text('Nhập thủ công')),
          ]),
        ]),
      ),
    );
  }
}
