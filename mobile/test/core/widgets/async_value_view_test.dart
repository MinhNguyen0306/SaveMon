import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:savemon_mobile/core/widgets/async_value_view.dart';

void main() {
  testWidgets('shows a loading indicator while the value is loading', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: AsyncValueView<String>(
          value: AsyncLoading<String>(),
          builder: _buildData,
        ),
      ),
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('shows a retry action on error', (tester) async {
    var retried = false;
    await tester.pumpWidget(
      MaterialApp(
        home: AsyncValueView<String>(
          value: AsyncError<String>(
            Exception('private detail'),
            StackTrace.current,
          ),
          builder: _buildData,
          onRetry: () async {
            retried = true;
          },
        ),
      ),
    );

    expect(
      find.text('Something went wrong. Please try again.'),
      findsOneWidget,
    );
    expect(find.text('private detail'), findsNothing);
    await tester.tap(find.text('Retry'));

    expect(retried, isTrue);
  });
}

Widget _buildData(String data) => Text(data);
