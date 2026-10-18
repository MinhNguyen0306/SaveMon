import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/idempotency_key.dart';
import '../application/receipt_expense_handoff.dart';
import '../../qr_bill_scan/data/scan_models.dart';

enum _ExpenseContext { personal, shared }

class ReceiptExpenseHandoffPage extends ConsumerStatefulWidget {
  const ReceiptExpenseHandoffPage({super.key, this.prefill});

  final ReceiptExpensePrefill? prefill;

  @override
  ConsumerState<ReceiptExpenseHandoffPage> createState() =>
      _ReceiptExpenseHandoffPageState();
}

class _ReceiptExpenseHandoffPageState
    extends ConsumerState<ReceiptExpenseHandoffPage> {
  late final TextEditingController _amount;
  late final TextEditingController _currency;
  late final TextEditingController _date;
  late final TextEditingController _description;
  final _accountId = TextEditingController();
  final _categoryId = TextEditingController();
  final _vaultId = TextEditingController();
  final _payerMemberId = TextEditingController();
  final _sourceAccountId = TextEditingController();
  final _splitLines = TextEditingController();
  _ExpenseContext? _expenseContext;
  ReceiptExpenseFundingChoice? _funding;
  Object? _pendingCommand;
  bool _saving = false;
  bool _fakeCompleted = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final prefill = widget.prefill;
    _amount = TextEditingController(text: prefill?.amount ?? '');
    _currency = TextEditingController(text: prefill?.currency ?? '');
    _date = TextEditingController(text: prefill?.civilDate ?? '');
    _description = TextEditingController(text: prefill?.merchant ?? '');
    for (final field in [
      _amount,
      _currency,
      _date,
      _description,
      _accountId,
      _categoryId,
      _vaultId,
      _payerMemberId,
      _sourceAccountId,
      _splitLines,
    ]) {
      field.addListener(_onEdit);
    }
  }

  void _onEdit() {
    _pendingCommand = null;
    _fakeCompleted = false;
    if (_error != null) setState(() => _error = null);
  }

  void _changeContext(_ExpenseContext? value) {
    setState(() {
      _expenseContext = value;
      _pendingCommand = null;
      _fakeCompleted = false;
      _error = null;
    });
  }

  @override
  void dispose() {
    for (final field in [
      _amount,
      _currency,
      _date,
      _description,
      _accountId,
      _categoryId,
      _vaultId,
      _payerMemberId,
      _sourceAccountId,
      _splitLines,
    ]) {
      field.removeListener(_onEdit);
      field.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.prefill == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Chi tiêu')),
        body: const Center(
          child:
              Text('Không còn dữ liệu xem xét trong phiên này. Hãy quét lại.'),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Xác nhận chi tiêu')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            const Card(
              child: ListTile(
                leading: Icon(Icons.fact_check_outlined),
                title: Text('Từ hóa đơn đã xem xét'),
                subtitle: Text(
                  'Chọn ngữ cảnh và kiểm tra lại. Chưa có giao dịch nào được lưu.',
                ),
              ),
            ),
            TextFormField(
              key: const ValueKey('handoff-amount'),
              controller: _amount,
              decoration: const InputDecoration(labelText: 'Số tiền đã trả'),
            ),
            TextFormField(
              key: const ValueKey('handoff-currency'),
              controller: _currency,
              decoration: const InputDecoration(labelText: 'Tiền tệ'),
            ),
            TextFormField(
              key: const ValueKey('handoff-date'),
              controller: _date,
              decoration:
                  const InputDecoration(labelText: 'Ngày hóa đơn (YYYY-MM-DD)'),
            ),
            TextFormField(
              key: const ValueKey('handoff-description'),
              controller: _description,
              maxLength: 500,
              decoration: const InputDecoration(
                  labelText: 'Nhà cung cấp / mô tả (tùy chọn)'),
            ),
            const Padding(
              padding: EdgeInsets.only(bottom: 8),
              child: Text(
                'Ngày chỉ có độ chính xác theo ngày; lệnh sẽ dùng thời điểm kỹ thuật 12:00 UTC (DATE_ONLY).',
              ),
            ),
            const Text('Chọn ngữ cảnh chi tiêu',
                style: TextStyle(fontWeight: FontWeight.w700)),
            Wrap(spacing: 8, children: [
              ChoiceChip(
                  key: const ValueKey('context-personal'),
                  label: const Text('Chi tiêu cá nhân'),
                  selected: _expenseContext == _ExpenseContext.personal,
                  onSelected: (selected) => _changeContext(
                      selected ? _ExpenseContext.personal : null)),
              ChoiceChip(
                  key: const ValueKey('context-shared'),
                  label: const Text('Chi tiêu quỹ chung'),
                  selected: _expenseContext == _ExpenseContext.shared,
                  onSelected: (selected) =>
                      _changeContext(selected ? _ExpenseContext.shared : null)),
            ]),
            if (_expenseContext == _ExpenseContext.personal) ...[
              TextField(
                  key: const ValueKey('handoff-account'),
                  controller: _accountId,
                  decoration: const InputDecoration(
                      labelText: 'Tài khoản cá nhân ID (bắt buộc)')),
              TextField(
                  key: const ValueKey('handoff-category'),
                  controller: _categoryId,
                  decoration: const InputDecoration(
                      labelText: 'Danh mục ID (tùy chọn)')),
              const Text(
                  'Nếu chọn danh mục, một mục chi tiêu toàn số tiền sẽ dùng danh mục đó.'),
            ],
            if (_expenseContext == _ExpenseContext.shared) ...[
              TextField(
                  key: const ValueKey('handoff-vault'),
                  controller: _vaultId,
                  decoration: const InputDecoration(
                      labelText: 'Quỹ chung ID (bắt buộc)')),
              DropdownButtonFormField<ReceiptExpenseFundingChoice>(
                key: const ValueKey('handoff-funding'),
                initialValue: _funding,
                decoration: const InputDecoration(labelText: 'Nguồn chi'),
                items: const [
                  DropdownMenuItem(
                      value: ReceiptExpenseFundingChoice.vault,
                      child: Text('Quỹ thanh toán')),
                  DropdownMenuItem(
                      value: ReceiptExpenseFundingChoice.member,
                      child: Text('Thành viên thanh toán')),
                ],
                onChanged: (value) => setState(() {
                  if (value != null) _funding = value;
                  _pendingCommand = null;
                  _fakeCompleted = false;
                  _error = null;
                }),
              ),
              if (_funding == ReceiptExpenseFundingChoice.member) ...[
                TextField(
                    key: const ValueKey('handoff-payer'),
                    controller: _payerMemberId,
                    decoration: const InputDecoration(
                        labelText: 'Người trả - thành viên ID')),
                TextField(
                    key: const ValueKey('handoff-source-account'),
                    controller: _sourceAccountId,
                    decoration:
                        const InputDecoration(labelText: 'Tài khoản nguồn ID')),
              ],
              TextField(
                key: const ValueKey('handoff-splits'),
                controller: _splitLines,
                minLines: 2,
                maxLines: 5,
                decoration: const InputDecoration(
                  labelText: 'Phân bổ (mỗi dòng: thành viên ID | số tiền)',
                ),
              ),
              const Text(
                  'Chọn rõ thành viên và số tiền phân bổ; danh mục không áp dụng cho quỹ chung.'),
            ],
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(_error!,
                    style:
                        TextStyle(color: Theme.of(context).colorScheme.error)),
              ),
            if (_fakeCompleted)
              const Padding(
                padding: EdgeInsets.only(top: 12),
                child: Text(
                    'Lệnh seam thử nghiệm đã nhận dữ liệu; không có giao dịch được lưu.'),
              ),
            const SizedBox(height: 12),
            FilledButton(
              key: const ValueKey('handoff-confirm'),
              onPressed: _saving || _fakeCompleted ? null : _confirm,
              child: Text(_pendingCommand == null
                  ? 'Xác nhận trong biểu mẫu'
                  : 'Thử lại cùng lệnh'),
            ),
            TextButton(
              onPressed: () => context.pop(),
              child: const Text('Hủy'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirm() async {
    if (_saving) return;
    try {
      _pendingCommand ??= _buildCommand();
    } on FormatException catch (error) {
      setState(() => _error = error.message);
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final port = ref.read(receiptExpenseCommandPortProvider);
      final command = _pendingCommand!;
      if (command is PersonalReceiptExpenseIntent) {
        await port.recordPersonal(command);
      } else {
        await port.recordShared(command as SharedReceiptExpenseIntent);
      }
      if (mounted) setState(() => _fakeCompleted = true);
    } on ReceiptExpenseCommandUnavailable {
      if (mounted) {
        setState(() => _error =
            'Lệnh chi tiêu chưa được kết nối trong ứng dụng này. Không có gì được lưu.');
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error =
            'Không xác nhận được. Có thể thử lại cùng lệnh; chưa báo thành công.');
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Object _buildCommand() {
    final contextChoice = _expenseContext;
    if (contextChoice == null) {
      throw const FormatException('Hãy chọn chi tiêu cá nhân hoặc quỹ chung.');
    }
    final amount = _amount.text.trim();
    final currency = _currency.text.trim();
    final civilDate = _date.text.trim();
    final description = _description.text.trim();
    try {
      ExpenseScanDraft.fromJson({
        'paidTotal': amount,
        'currency': currency,
        'transactionDate': civilDate,
        'merchant': description.isEmpty ? null : description,
        'missingOrAmbiguousFields': const <String>[],
      });
    } on FormatException {
      throw const FormatException('Kiểm tra số tiền, tiền tệ và ngày hóa đơn.');
    }
    if (description.runes.length > 500) {
      throw const FormatException('Mô tả tối đa 500 ký tự.');
    }
    final transactionDate = '${civilDate}T12:00:00Z';
    final key = newIdempotencyKey();
    if (contextChoice == _ExpenseContext.personal) {
      final accountId = _accountId.text.trim();
      if (accountId.isEmpty) {
        throw const FormatException('Chọn tài khoản cá nhân.');
      }
      final categoryId = _categoryId.text.trim();
      return PersonalReceiptExpenseIntent(
        accountId: accountId,
        categoryId: categoryId.isEmpty ? null : categoryId,
        amount: amount,
        currency: currency,
        transactionDate: transactionDate,
        datePrecision: 'DATE_ONLY',
        description: description,
        idempotencyKey: key,
        items: List.unmodifiable(categoryId.isEmpty
            ? const <PersonalReceiptExpenseItem>[]
            : [
                PersonalReceiptExpenseItem(
                    categoryId: categoryId, amount: amount)
              ]),
      );
    }
    final vaultId = _vaultId.text.trim();
    if (vaultId.isEmpty) {
      throw const FormatException('Chọn quỹ chung.');
    }
    final funding = _funding;
    if (funding == null) {
      throw const FormatException('Chọn nguồn chi.');
    }
    final payer = _payerMemberId.text.trim();
    final source = _sourceAccountId.text.trim();
    if (funding == ReceiptExpenseFundingChoice.member &&
        (payer.isEmpty || source.isEmpty)) {
      throw const FormatException('Chọn người trả và tài khoản nguồn.');
    }
    final splits = _splitLines.text
        .split('\n')
        .where((line) => line.trim().isNotEmpty)
        .map((line) {
      final columns = line.split('|').map((item) => item.trim()).toList();
      if (columns.length != 2 || columns.any((item) => item.isEmpty)) {
        throw const FormatException('Mỗi phân bổ cần thành viên ID | số tiền.');
      }
      return ReceiptExpenseSplitChoice(
          memberId: columns[0], amount: columns[1]);
    }).toList(growable: false);
    if (splits.isEmpty) {
      throw const FormatException('Chọn ít nhất một phân bổ thành viên.');
    }
    return SharedReceiptExpenseIntent(
      vaultId: vaultId,
      funding: funding,
      payerMemberId:
          funding == ReceiptExpenseFundingChoice.member ? payer : null,
      sourceAccountId:
          funding == ReceiptExpenseFundingChoice.member ? source : null,
      amount: amount,
      currency: currency,
      transactionDate: transactionDate,
      datePrecision: 'DATE_ONLY',
      description: description,
      splits: List.unmodifiable(splits),
      idempotencyKey: key,
    );
  }
}
