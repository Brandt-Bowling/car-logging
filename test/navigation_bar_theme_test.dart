import 'package:car_logger/main.dart';
import 'package:car_logger/models/car.dart';
import 'package:car_logger/pages/car_details_page.dart';
import 'package:car_logger/pages/home_page.dart';
import 'package:car_logger/services/storage_service.dart';
import 'package:car_logger/widgets/raised_navigation_bar.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
  });

  testWidgets('RaisedNavigationBar renders with upward elevation shadow and top border', (tester) async {
    const colorScheme = ColorScheme.light();
    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: colorScheme,
        ),
        home: Scaffold(
          body: const Text('Body'),
          bottomNavigationBar: RaisedNavigationBar(
            selectedIndex: 0,
            destinations: const [
              NavigationDestination(icon: Icon(Icons.home), label: 'Home'),
              NavigationDestination(icon: Icon(Icons.settings), label: 'Settings'),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final raisedNavBarFinder = find.byType(RaisedNavigationBar);
    expect(raisedNavBarFinder, findsOneWidget);

    final containerFinder = find.descendant(
      of: raisedNavBarFinder,
      matching: find.byType(Container),
    ).first;

    final Container container = tester.widget(containerFinder);
    final BoxDecoration decoration = container.decoration as BoxDecoration;

    // Verify container background color is surfaceContainer
    expect(decoration.color, equals(colorScheme.surfaceContainer));

    // Verify upward elevation shadow is present with negative Y offset
    expect(decoration.boxShadow, isNotNull);
    expect(decoration.boxShadow!.isNotEmpty, isTrue);
    final shadow = decoration.boxShadow!.first;
    expect(shadow.offset.dy, lessThan(0)); // Negative Y casts upward onto canvas
    expect(shadow.blurRadius, greaterThanOrEqualTo(8));

    // Verify subtle top border is present
    expect(decoration.border, isNotNull);
    final border = decoration.border as Border;
    expect(border.top.width, greaterThan(0));
  });

  testWidgets('HomePage uses RaisedNavigationBar with upward elevation shadow', (tester) async {
    await tester.pumpWidget(const CarLoggerApp());
    await tester.pumpAndSettle();

    final raisedNavBarFinder = find.byType(RaisedNavigationBar);
    expect(raisedNavBarFinder, findsOneWidget);

    final containerFinder = find.descendant(
      of: raisedNavBarFinder,
      matching: find.byType(Container),
    ).first;

    final Container container = tester.widget(containerFinder);
    final BoxDecoration decoration = container.decoration as BoxDecoration;
    expect(decoration.boxShadow, isNotNull);
    expect(decoration.boxShadow!.first.offset.dy, lessThan(0));
  });

  testWidgets('CarDetailsPage uses RaisedNavigationBar with upward elevation shadow', (tester) async {
    final testCar = Car(
      id: 'test-car',
      make: 'Tesla',
      model: 'Model 3',
      year: 2023,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: CarDetailsPage(car: testCar),
      ),
    );
    await tester.pump();

    final raisedNavBarFinder = find.byType(RaisedNavigationBar);
    expect(raisedNavBarFinder, findsOneWidget);

    final containerFinder = find.descendant(
      of: raisedNavBarFinder,
      matching: find.byType(Container),
    ).first;

    final Container container = tester.widget(containerFinder);
    final BoxDecoration decoration = container.decoration as BoxDecoration;
    expect(decoration.boxShadow, isNotNull);
    expect(decoration.boxShadow!.first.offset.dy, lessThan(0));
  });
}
