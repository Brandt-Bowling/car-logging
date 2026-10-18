import 'package:car_logger/main.dart';
import 'package:car_logger/models/car.dart';
import 'package:car_logger/pages/car_details_page.dart';
import 'package:car_logger/services/storage_service.dart';
import 'package:car_logger/widgets/material3_body_container.dart';
import 'package:car_logger/widgets/raised_navigation_bar.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
  });

  testWidgets('RaisedNavigationBar renders with solid surfaceContainer color and zero shadows/borders', (tester) async {
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

    // Verify container background color is shellColor
    final containerColor = container.color ?? (container.decoration as BoxDecoration?)?.color;
    final expectedColor = Material3BodyContainer.getShellColor(tester.element(raisedNavBarFinder));
    expect(containerColor, equals(expectedColor));

    // Verify no shadow or border is present
    if (container.decoration is BoxDecoration) {
      final BoxDecoration decoration = container.decoration as BoxDecoration;
      expect(decoration.boxShadow == null || decoration.boxShadow!.isEmpty, isTrue);
      expect(decoration.border, isNull);
    }

    // Verify NavigationBar widget properties
    final navBarFinder = find.descendant(
      of: raisedNavBarFinder,
      matching: find.byType(NavigationBar),
    );
    expect(navBarFinder, findsOneWidget);
    final NavigationBar navBar = tester.widget(navBarFinder);
    expect(navBar.elevation, equals(0));
    expect(navBar.shadowColor, equals(Colors.transparent));
  });

  testWidgets('HomePage uses RaisedNavigationBar without shadows', (tester) async {
    await tester.pumpWidget(const CarLoggerApp());
    await tester.pumpAndSettle();

    final raisedNavBarFinder = find.byType(RaisedNavigationBar);
    expect(raisedNavBarFinder, findsOneWidget);

    final containerFinder = find.descendant(
      of: raisedNavBarFinder,
      matching: find.byType(Container),
    ).first;

    final Container container = tester.widget(containerFinder);
    if (container.decoration is BoxDecoration) {
      final BoxDecoration decoration = container.decoration as BoxDecoration;
      expect(decoration.boxShadow == null || decoration.boxShadow!.isEmpty, isTrue);
      expect(decoration.border, isNull);
    }
  });

  testWidgets('CarDetailsPage uses RaisedNavigationBar without shadows', (tester) async {
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
    if (container.decoration is BoxDecoration) {
      final BoxDecoration decoration = container.decoration as BoxDecoration;
      expect(decoration.boxShadow == null || decoration.boxShadow!.isEmpty, isTrue);
      expect(decoration.border, isNull);
    }
  });
}
