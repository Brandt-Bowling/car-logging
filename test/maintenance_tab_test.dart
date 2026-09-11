import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:car_logger/models/car.dart';
import 'package:car_logger/models/maintenance_record.dart';
import 'package:car_logger/pages/tabs/maintenance_tab.dart';
import 'package:car_logger/services/storage_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final testCar = Car(
    id: 'test_car_1',
    make: 'Chevrolet',
    model: 'Sonic',
    year: 2013,
  );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
  });

  testWidgets('MaintenanceTab indicates source via leading icon without redundant text tag', (
    WidgetTester tester,
  ) async {
    // Set up 3 records:
    // 1. Drive import WITH cost ($120.00)
    // 2. Drive import WITHOUT cost (null)
    // 3. Manual entry WITH cost ($45.00)
    final records = [
      MaintenanceRecord(
        id: 'rec_1',
        carId: testCar.id,
        title: 'Oil & Filter Change',
        date: DateTime(2024, 5, 12),
        odometer: 45000,
        cost: 120.0,
        driveFileId: 'drive_file_1',
      ),
      MaintenanceRecord(
        id: 'rec_2',
        carId: testCar.id,
        title: 'Tire Rotation',
        date: DateTime(2024, 5, 10),
        odometer: 40000,
        cost: null,
        driveFileId: 'drive_file_2',
      ),
      MaintenanceRecord(
        id: 'rec_3',
        carId: testCar.id,
        title: 'Wiper Blade Replacement',
        date: DateTime(2024, 4, 1),
        odometer: 38000,
        cost: 45.0,
        driveFileId: null, // manual
      ),
    ];

    await StorageService.saveMaintenanceRecords(testCar.id, records);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MaintenanceTab(car: testCar),
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Verify all 3 titles exist as standalone Text widgets
    expect(find.text('Oil & Filter Change'), findsOneWidget);
    expect(find.text('Tire Rotation'), findsOneWidget);
    expect(find.text('Wiper Blade Replacement'), findsOneWidget);

    // Verify NO "Drive" tag text is displayed anywhere
    expect(find.text('Drive'), findsNothing);

    // Verify leading icons indicate source:
    // 2 Drive records use Icons.cloud_done
    expect(find.byIcon(Icons.cloud_done), findsNWidgets(2));
    // 1 manual record uses Icons.build
    expect(find.byIcon(Icons.build), findsNWidgets(1));

    // Verify trailing costs:
    expect(find.text('\$120.00'), findsOneWidget);
    expect(find.text('\$45.00'), findsOneWidget);

    // Inspect rec_2 ListTile (Tire Rotation - no cost)
    final tireTile = tester.widget<ListTile>(
      find.ancestor(
        of: find.text('Tire Rotation'),
        matching: find.byType(ListTile),
      ),
    );
    expect(tireTile.trailing, isNull);
  });
}
