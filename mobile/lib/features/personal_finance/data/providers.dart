import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import 'personal_finance_repository.dart';

final personalFinanceRepositoryProvider =
    Provider<PersonalFinanceRepository>((ref) {
  return PersonalFinanceRepository(ref.watch(apiClientProvider));
});
