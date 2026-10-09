import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_error_view.dart';

class AsyncValueView<T> extends StatelessWidget {
  const AsyncValueView({
    required this.value,
    required this.builder,
    this.onRetry,
    this.errorMessage,
    super.key,
  });

  final AsyncValue<T> value;
  final Widget Function(T data) builder;
  final Future<void> Function()? onRetry;
  final String Function(Object error)? errorMessage;

  @override
  Widget build(BuildContext context) {
    return value.when(
      data: builder,
      error: (error, stackTrace) => AppErrorView(
        onRetry: onRetry,
        message: errorMessage?.call(error) ?? 'Something went wrong. Please try again.',
      ),
      loading: () => const Center(child: CircularProgressIndicator()),
    );
  }
}
