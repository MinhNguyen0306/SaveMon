import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/widgets.dart';

import '../../../core/session/auth_state.dart';
import '../data/expense_scan_repository.dart';
import '../data/providers.dart';
import '../data/scan_image_source.dart';
import '../data/scan_models.dart';

enum ExpenseScanStage {
  guidance,
  selecting,
  submitting,
  review,
  recovery,
  manual
}

class ExpenseScanState {
  const ExpenseScanState({
    this.stage = ExpenseScanStage.guidance,
    this.draft,
    this.failure,
    this.outcome,
    this.reasonCode,
    this.paidTotal = '',
    this.currency = '',
    this.transactionDate = '',
    this.merchant = '',
    this.localReviewAcknowledged = false,
  });

  final ExpenseScanStage stage;
  final ExpenseScanDraft? draft;
  final ScanFailureKind? failure;
  final ScanOutcome? outcome;
  final String? reasonCode;
  final String paidTotal;
  final String currency;
  final String transactionDate;
  final String merchant;
  final bool localReviewAcknowledged;

  ExpenseScanState copyWith({
    ExpenseScanStage? stage,
    ExpenseScanDraft? draft,
    ScanFailureKind? failure,
    ScanOutcome? outcome,
    String? reasonCode,
    bool clearFailure = false,
    bool clearOutcome = false,
    String? paidTotal,
    String? currency,
    String? transactionDate,
    String? merchant,
    bool? localReviewAcknowledged,
  }) =>
      ExpenseScanState(
        stage: stage ?? this.stage,
        draft: draft ?? this.draft,
        failure: clearFailure ? null : failure ?? this.failure,
        outcome: clearOutcome ? null : outcome ?? this.outcome,
        reasonCode: clearOutcome ? null : reasonCode ?? this.reasonCode,
        paidTotal: paidTotal ?? this.paidTotal,
        currency: currency ?? this.currency,
        transactionDate: transactionDate ?? this.transactionDate,
        merchant: merchant ?? this.merchant,
        localReviewAcknowledged:
            localReviewAcknowledged ?? this.localReviewAcknowledged,
      );
}

final expenseScanControllerProvider =
    StateNotifierProvider.autoDispose<ExpenseScanController, ExpenseScanState>(
  (ref) {
    ref.watch(authSubjectEpochProvider);
    final controller = ExpenseScanController(
      ref.read(scanImageSourceProvider),
      ref.read(expenseScanRepositoryProvider),
    );
    ref.onDispose(controller.disposeVolatile);
    return controller;
  },
);

class ExpenseScanController extends StateNotifier<ExpenseScanState>
    with WidgetsBindingObserver {
  ExpenseScanController(this._source, this._repository)
      : super(const ExpenseScanState()) {
    WidgetsBinding.instance.addObserver(this);
  }

  final ScanImageSource _source;
  final ExpenseScanRepository _repository;
  ScanCancellation? _cancellation;
  int _generation = 0;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) discard();
  }

  Future<void> chooseImage() async {
    final generation = ++_generation;
    state = state.copyWith(
      stage: ExpenseScanStage.selecting,
      clearFailure: true,
      clearOutcome: true,
    );
    try {
      final bytes = await _source.selectImage();
      if (generation != _generation) return;
      if (bytes == null) {
        state = state.copyWith(stage: ExpenseScanStage.guidance);
        return;
      }
      state = state.copyWith(stage: ExpenseScanStage.submitting);
      final cancellation = ScanCancellation();
      _cancellation = cancellation;
      final result = await _repository.scan(bytes, cancellation: cancellation);
      if (generation != _generation) return;
      if (result.outcome == ScanOutcome.draft && result.draft != null) {
        final draft = result.draft!;
        state = state.copyWith(
          stage: ExpenseScanStage.review,
          draft: draft,
          paidTotal: draft.paidTotal ?? '',
          currency: draft.currency ?? '',
          transactionDate: draft.transactionDate ?? '',
          merchant: draft.merchant ?? '',
          clearFailure: true,
          clearOutcome: true,
        );
      } else {
        discardImage();
        state = state.copyWith(
          stage: ExpenseScanStage.recovery,
          outcome: result.outcome,
          reasonCode: result.reasonCode,
        );
      }
    } on ScanImageSourceUnavailable {
      if (generation == _generation) {
        state = state.copyWith(
          stage: ExpenseScanStage.recovery,
          failure: ScanFailureKind.extractionUnavailable,
        );
      }
    } on ScanRequestException catch (error) {
      if (generation == _generation) {
        discardImage();
        state = state.copyWith(
          stage: ExpenseScanStage.recovery,
          failure: error.kind,
        );
      }
    } catch (_) {
      if (generation == _generation) {
        discardImage();
        state = state.copyWith(
          stage: ExpenseScanStage.recovery,
          failure: ScanFailureKind.network,
        );
      }
    }
  }

  void beginManualEntry() {
    discardImage();
    state = const ExpenseScanState(stage: ExpenseScanStage.manual);
  }

  void edit(
      {String? paidTotal, String? currency, String? date, String? merchant}) {
    state = state.copyWith(
      paidTotal: paidTotal,
      currency: currency,
      transactionDate: date,
      merchant: merchant,
      localReviewAcknowledged: false,
    );
  }

  void acknowledgeReviewLocally() =>
      state = state.copyWith(localReviewAcknowledged: true);

  void retry() {
    discard();
  }

  void discardImage() {
    _cancellation?.cancel();
    _cancellation = null;
  }

  void discard() {
    _generation++;
    discardImage();
    state = const ExpenseScanState();
  }

  void disposeVolatile() {
    _generation++;
    discardImage();
    WidgetsBinding.instance.removeObserver(this);
  }
}
