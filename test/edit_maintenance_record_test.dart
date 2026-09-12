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
    odometer: 40000,
  );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
    await StorageService.saveCars([testCar]);
  });

  testWidgets('Tapping a maintenance record opens edit dialog with pre-filled fields', (
    WidgetTester tester,
  ) async {
    final record = MaintenanceRecord(
      id: 'rec_oil',
      carId: testCar.id,
      title: 'Oil Change',
      date: DateTime(2024, 6, 15),
      odometer: 42000,
      cost: 65.0,
      description: 'Synthetic oil 5W-30',
    );

    await StorageService.saveMaintenanceRecords(testCar.id, [record]);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MaintenanceTab(car: testCar),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Oil Change'), findsOneWidget);

    // Tap on the ListTile to edit
    await tester.tap(find.text('Oil Change'));
    await tester.pumpAndSettle();

    // Verify dialog title
    expect(find.text('Edit Maintenance Record'), findsOneWidget);

    // Verify fields are pre-populated
    expect(find.widgetWithText(TextFormField, 'Oil Change'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, '42000'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, '65'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Synthetic oil 5W-30'), findsOneWidget);
    expect(find.text('2024-06-15'), findsOneWidget);

    // Cancel closes dialog
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    expect(find.text('Edit Maintenance Record'), findsNothing);
  });

  testWidgets('Editing maintenance record fields and saving updates storage and UI', (
    WidgetTester tester,
  ) async {
    final record = MaintenanceRecord(
      id: 'rec_oil',
      carId: testCar.id,
      title: 'Oil Change',
      date: DateTime(2024, 6, 15),
      odometer: 42000,
      cost: 65.0,
      description: 'Synthetic oil 5W-30',
    );

    await StorageService.saveMaintenanceRecords(testCar.id, [record]);

    Car? updatedCarReceived;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MaintenanceTab(
            car: testCar,
            onCarUpdated: (car) {
              updatedCarReceived = car;
            },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Tap on the record
    await tester.tap(find.text('Oil Change'));
    await tester.pumpAndSettle();

    // Find text fields
    final titleFinder = find.widgetWithText(TextFormField, 'Oil Change');
    final odoFinder = find.widgetWithText(TextFormField, '42000');
    final costFinder = find.widgetWithText(TextFormField, '65');
    final notesFinder = find.widgetWithText(TextFormField, 'Synthetic oil 5W-30');

    // Update title
    await tester.enterText(titleFinder, 'Full Synthetic Oil & Filter Service');
    // Update odometer to 46000 (higher than car's 40000)
    await tester.enterText(odoFinder, '46000');
    // Update cost to 89.50
    await tester.enterText(costFinder, '89.50');
    // Update notes
    await tester.enterText(notesFinder, 'Replaced filter and oil drain plug gasket');

    // Tap Save Changes
    await tester.tap(find.text('Save Changes'));
    await tester.pumpAndSettle();

    // Dialog should be dismissed
    expect(find.text('Edit Maintenance Record'), findsNothing);

    // UI should display updated title and cost
    expect(find.text('Full Synthetic Oil & Filter Service'), findsOneWidget);
    expect(find.text('\$89.50'), findsOneWidget);
    expect(find.textContaining('46,000 mi'), findsOneWidget);

    // Verify storage persistence
    final savedRecords = StorageService.getMaintenanceRecords(testCar.id);
    expect(savedRecords.length, 1);
    expect(savedRecords.first.title, 'Full Synthetic Oil & Filter Service');
    expect(savedRecords.first.odometer, 46000);
    expect(savedRecords.first.cost, 89.50);
    expect(savedRecords.first.description, 'Replaced filter and oil drain plug gasket');

    // Verify vehicle odometer was updated
    expect(updatedCarReceived?.odometer, 46000);
    final savedCars = StorageService.getCars();
    final carInStorage = savedCars.firstWhere((c) => c.id == testCar.id);
    expect(carInStorage.odometer, 46000);
  });

  testWidgets('Deleting maintenance record removes it from storage and UI', (
    WidgetTester tester,
  ) async {
    final record = MaintenanceRecord(
      id: 'rec_brake',
      carId: testCar.id,
      title: 'Brake Pad Replacement',
      date: DateTime(2024, 7, 1),
      odometer: 44000,
      cost: 210.0,
    );

    await StorageService.saveMaintenanceRecords(testCar.id, [record]);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MaintenanceTab(car: testCar),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Brake Pad Replacement'), findsOneWidget);

    // Tap on record
    await tester.tap(find.text('Brake Pad Replacement'));
    await tester.pumpAndSettle();

    // Tap delete button in dialog
    await tester.tap(find.byIcon(Icons.delete_outline));
    await tester.pumpAndSettle();

    // Confirmation dialog appears
    expect(find.text('Delete Record?'), findsOneWidget);
    expect(find.text('Delete'), findsOneWidget);

    // Confirm delete
    await tester.tap(find.widgetWithText(FilledButton, 'Delete'));
    await tester.pumpAndSettle();

    // Verify item is removed from UI and empty state is shown
    expect(find.text('Brake Pad Replacement'), findsNothing);
    expect(find.text('No maintenance history recorded.'), findsOneWidget);

    // Verify removed from storage
    final savedRecords = StorageService.getMaintenanceRecords(testCar.id);
    expect(savedRecords.isEmpty, isTrue);
  });

  testWidgets('Validation prevents saving empty title or invalid odometer', (
    WidgetTester tester,
  ) async {
    final record = MaintenanceRecord(
      id: 'rec_1',
      carId: testCar.id,
      title: 'Air Filter',
      date: DateTime(2024, 1, 1),
      odometer: 30000,
    );

    await StorageService.saveMaintenanceRecords(testCar.id, [record]);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MaintenanceTab(car: testCar),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Air Filter'));
    await tester.pumpAndSettle();

    // Clear title
    final titleFinder = find.byType(TextFormField).at(0);
    await tester.enterText(titleFinder, '');

    // Tap Save Changes
    await tester.tap(find.text('Save Changes'));
    await tester.pumpAndSettle();

    // Validation error shown
    expect(find.text('Service title is required'), findsOneWidget);

    // Enter invalid odometer
    final odoFinder = find.byType(TextFormField).at(1);
    await tester.enterText(odoFinder, '-50');
    await tester.enterText(titleFinder, 'Valid Title');

    await tester.tap(find.text('Save Changes'));
    await tester.pumpAndSettle();

    expect(find.text('Please enter a valid odometer number'), findsOneWidget);

    // Dialog stays open
    expect(find.text('Edit Maintenance Record'), findsOneWidget);
  });

  testWidgets('Drive imported records show Synced from Google Drive badge in edit dialog', (
    WidgetTester tester,
  ) async {
    final record = MaintenanceRecord(
      id: 'rec_drive',
      carId: testCar.id,
      title: 'Tire Rotation',
      date: DateTime(2024, 8, 20),
      odometer: 43000,
      driveFileId: 'drive_file_abc',
    );

    await StorageService.saveMaintenanceRecords(testCar.id, [record]);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MaintenanceTab(car: testCar),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Tire Rotation'));
    await tester.pumpAndSettle();

    expect(find.text('Synced from Google Drive'), findsOneWidget);
  });
}

