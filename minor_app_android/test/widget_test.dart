import 'package:flutter_test/flutter_test.dart';

import 'package:minor_app_android/main.dart';

void main() {
  testWidgets('SafeGuard app renders dashboard', (WidgetTester tester) async {
    await tester.pumpWidget(const SafeGuardApp());

    // Verify the SafeGuard title is displayed
    expect(find.text('SafeGuard'), findsOneWidget);

    // Verify key dashboard elements
    expect(find.text('Secure'), findsOneWidget);
    expect(find.text('Trust Code'), findsOneWidget);
  });
}
