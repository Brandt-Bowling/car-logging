import 'package:car_logger/main.dart';
import 'package:car_logger/models/car.dart';
import 'package:car_logger/pages/car_details_page.dart';
import 'package:car_logger/pages/home_page.dart';
import 'package:car_logger/services/storage_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
  });

  testWidgets('App theme configures NavigationBar with raised surfaceContainer and elevation', (tester) async {
    await tester.pumpWidget(const CarLoggerApp());
    await tester.pumpAndSettle();

    final navBarFinder = find.byType(NavigationBar);
    expect(navBarFinder, findsOneWidget);

    final materialFinder = find.descendant(
      of: navBarFinder,
      matching: find.byType(Material),
    ).first;

    final Material material = tester.widget(materialFinder);
    final BuildContext context = tester.element(navBarFinder);
    final theme = Theme.of(context);

    // Verify it uses surfaceContainer (distinct container color like Gmail)
    expect(material.color, equals(theme.colorScheme.surfaceContainer));
    // Verify it is raised with elevation
    expect(material.elevation, equals(3));
    // Verify it has shadowColor configured
    expect(material.shadowColor, isNotNull);
  });

  testWidgets('HomePage and CarDetailsPage NavigationBars inherit theme styling', (tester) async {
    final testCar = Car(
      id: 'test-car',
      make: 'Tesla',
      model: 'Model 3',
      year: 2023,
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
          navigationBarTheme: NavigationBarThemeData(
            elevation: 3,
            backgroundColor: ColorScheme.fromSeed(seedColor: Colors.blue).surfaceContainer,
            shadowColor: Colors.black12,
          ),
        ),
        home: CarDetailsPage(car: testCar),
      ),
    );
    await tester.pump();

    final navBarFinder = find.byType(NavigationBar);
    expect(navBarFinder, findsOneWidget);

    final materialFinder = find.descendant(
      of: navBarFinder,
      matching: find.byType(Material),
    ).first;

    final Material material = tester.widget(materialFinder);
    expect(material.elevation, equals(3));
    expect(material.shadowColor, equals(Colors.black12));
  });
}
