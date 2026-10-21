import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:car_logger/models/car.dart';
import 'package:car_logger/pages/tabs/tires_tab.dart';
import 'package:car_logger/services/storage_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
  });

  testWidgets('TiresTab displays Rotation Interval and allows configuring it', (
    WidgetTester tester,
  ) async {
    Car currentCar = Car(
      id: 'test_car',
      make: 'Tesla',
      model: 'Model Y',
      year: 2023,
      isEv: true,
      tireRotationInterval: 7500,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: StatefulBuilder(
          builder: (context, setState) {
            return Scaffold(
              body: TiresTab(
                car: currentCar,
                onCarUpdated: (updated) {
                  setState(() {
                    currentCar = updated;
                  });
                },
              ),
            );
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Rotation Interval'), findsOneWidget);
    expect(find.text('7,500 miles'), findsOneWidget);

    // Tap on Rotation Interval tile
    await tester.tap(find.text('Rotation Interval'));
    await tester.pumpAndSettle();

    // Verify dialog appears
    expect(find.text('Tire Rotation Interval'), findsOneWidget);
    expect(find.text('6000 mi'), findsOneWidget);

    // Tap 6000 mi preset chip
    await tester.tap(find.text('6000 mi'));
    await tester.pumpAndSettle();

    // Tap Save
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    // Verify updated interval
    expect(currentCar.tireRotationInterval, 6000);
    expect(find.text('6,000 miles'), findsOneWidget);
  });
}
