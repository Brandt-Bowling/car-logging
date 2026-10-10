import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:googleapis/drive/v3.dart' as drive;
import 'package:car_logger/models/car.dart';
import 'package:car_logger/models/maintenance_record.dart';
import 'package:car_logger/services/storage_service.dart';
import 'package:car_logger/pages/google_drive_sync_page.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final car1 = Car(
    id: 'car_1',
    make: 'Chevrolet',
    model: 'Sonic',
    year: 2013,
    odometer: 40000,
  );

  final car2 = Car(
    id: 'car_2',
    make: 'Mazda',
    model: 'Miata',
    year: 2020,
    odometer: 15000,
  );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
    await StorageService.saveCars([car1, car2]);
  });

  group('StorageService Drive Assignment Helpers', () {
    test('findRecordByDriveFileId finds correct car and record', () async {
      final record = MaintenanceRecord(
        id: 'rec_1',
        carId: car1.id,
        title: 'Oil Change',
        date: DateTime(2024, 5, 1),
        odometer: 41000,
        driveFileId: 'drive_doc_101',
      );
      await StorageService.saveMaintenanceRecords(car1.id, [record]);
      await StorageService.markFileAsImported('drive_doc_101');

      final result = StorageService.findRecordByDriveFileId('drive_doc_101');
      expect(result, isNotNull);
      expect(result!.car.id, car1.id);
      expect(result.record.id, 'rec_1');
      expect(result.record.title, 'Oil Change');
    });

    test('findRecordByDriveFileId returns null when no matching record exists', () {
      final result = StorageService.findRecordByDriveFileId('non_existent_id');
      expect(result, isNull);
    });

    test('moveMaintenanceRecord moves record between cars', () async {
      final record = MaintenanceRecord(
        id: 'rec_move',
        carId: car1.id,
        title: 'Tire Replacement',
        date: DateTime(2024, 6, 1),
        odometer: 42000,
        driveFileId: 'drive_doc_102',
      );
      await StorageService.saveMaintenanceRecords(car1.id, [record]);
      await StorageService.markFileAsImported('drive_doc_102');

      await StorageService.moveMaintenanceRecord(
        record: record,
        fromCarId: car1.id,
        toCarId: car2.id,
      );

      expect(StorageService.getMaintenanceRecords(car1.id).isEmpty, isTrue);
      final car2Records = StorageService.getMaintenanceRecords(car2.id);
      expect(car2Records.length, 1);
      expect(car2Records.first.carId, car2.id);
      expect(car2Records.first.title, 'Tire Replacement');
      expect(car2Records.first.driveFileId, 'drive_doc_102');
      // Drive file should still be marked imported
      expect(StorageService.getImportedFileIds().contains('drive_doc_102'), isTrue);
    });

    test('unmarkFileAsImported removes file ID from imported set', () async {
      await StorageService.markFileAsImported('drive_doc_103');
      expect(StorageService.getImportedFileIds().contains('drive_doc_103'), isTrue);

      await StorageService.unmarkFileAsImported('drive_doc_103');
      expect(StorageService.getImportedFileIds().contains('drive_doc_103'), isFalse);
    });

    test('deleteMaintenanceRecord unmarks driveFileId from imported set', () async {
      final record = MaintenanceRecord(
        id: 'rec_del',
        carId: car1.id,
        title: 'Battery Replacement',
        date: DateTime(2024, 7, 1),
        odometer: 43000,
        driveFileId: 'drive_doc_104',
      );
      await StorageService.saveMaintenanceRecords(car1.id, [record]);
      await StorageService.markFileAsImported('drive_doc_104');
      expect(StorageService.getImportedFileIds().contains('drive_doc_104'), isTrue);

      await StorageService.deleteMaintenanceRecord(car1.id, 'rec_del');
      expect(StorageService.getMaintenanceRecords(car1.id).isEmpty, isTrue);
      expect(StorageService.getImportedFileIds().contains('drive_doc_104'), isFalse);
    });
  });

  group('EditAssignedModal Widget Tests', () {
    testWidgets('Renders pre-populated fields and assigned vehicle badge', (
      WidgetTester tester,
    ) async {
      final dummyFile = drive.File()
        ..id = 'file_123'
        ..name = 'Oil_Service_Invoice.pdf'
        ..mimeType = 'application/pdf';

      final record = MaintenanceRecord(
        id: 'rec_existing',
        carId: car1.id,
        title: 'Full Synthetic Oil Change',
        date: DateTime(2024, 5, 10),
        odometer: 41200,
        cost: 75.50,
        description: 'Castrol 5W-30 synthetic',
        driveFileId: 'file_123',
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: EditAssignedModal(
              file: dummyFile,
              record: record,
              currentCar: car1,
              cars: [car1, car2],
              onSave: (_, __) async {},
              onUnassign: () async {},
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Check header
      expect(find.text('Edit Assigned Document'), findsOneWidget);
      expect(find.text('Oil_Service_Invoice.pdf'), findsOneWidget);
      expect(find.text('Assigned to 2013 Chevrolet Sonic'), findsOneWidget);

      // Check pre-populated fields
      expect(find.widgetWithText(TextFormField, 'Full Synthetic Oil Change'), findsOneWidget);
      expect(find.widgetWithText(TextFormField, '41200'), findsOneWidget);
      expect(find.widgetWithText(TextFormField, '75.50'), findsOneWidget);
      expect(find.widgetWithText(TextFormField, 'Castrol 5W-30 synthetic'), findsOneWidget);
      expect(find.text('2024-05-10'), findsOneWidget);

      // Verify buttons exist
      expect(find.widgetWithText(OutlinedButton, 'Unassign'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Save Changes'), findsOneWidget);
    });

    testWidgets('Changing assigned vehicle and clicking Save Changes triggers onSave with new car ID', (
      WidgetTester tester,
    ) async {
      final dummyFile = drive.File()
        ..id = 'file_123'
        ..name = 'Tire_Receipt.pdf'
        ..mimeType = 'application/pdf';

      final record = MaintenanceRecord(
        id: 'rec_tire',
        carId: car1.id,
        title: 'Tire Installation',
        date: DateTime(2024, 6, 1),
        odometer: 42000,
        driveFileId: 'file_123',
      );

      MaintenanceRecord? savedRecordResult;
      String? savedCarIdResult;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: EditAssignedModal(
              file: dummyFile,
              record: record,
              currentCar: car1,
              cars: [car1, car2],
              onSave: (updatedRecord, newCarId) async {
                savedRecordResult = updatedRecord;
                savedCarIdResult = newCarId;
              },
              onUnassign: () async {},
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Tap on the Car dropdown
      await tester.tap(find.text('2013 Chevrolet Sonic'));
      await tester.pumpAndSettle();

      // Select the Mazda Miata
      await tester.tap(find.text('2020 Mazda Miata').last);
      await tester.pumpAndSettle();

      // Tap Save Changes
      await tester.tap(find.text('Save Changes'));
      await tester.pumpAndSettle();

      expect(savedCarIdResult, equals(car2.id));
      expect(savedRecordResult, isNotNull);
      expect(savedRecordResult!.carId, equals(car2.id));
      expect(savedRecordResult!.title, equals('Tire Installation'));
    });

    testWidgets('Tapping Unassign shows confirmation dialog and triggers onUnassign upon confirm', (
      WidgetTester tester,
    ) async {
      final dummyFile = drive.File()
        ..id = 'file_unassign'
        ..name = 'Wrong_Receipt.pdf'
        ..mimeType = 'application/pdf';

      final record = MaintenanceRecord(
        id: 'rec_wrong',
        carId: car1.id,
        title: 'Accidental Import',
        date: DateTime(2024, 7, 1),
        odometer: 40000,
        driveFileId: 'file_unassign',
      );

      bool unassignedCalled = false;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: EditAssignedModal(
              file: dummyFile,
              record: record,
              currentCar: car1,
              cars: [car1, car2],
              onSave: (_, __) async {},
              onUnassign: () async {
                unassignedCalled = true;
              },
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Tap Unassign button
      await tester.tap(find.text('Unassign'));
      await tester.pumpAndSettle();

      // Confirmation dialog appears
      expect(find.text('Unassign Document?'), findsOneWidget);
      expect(
        find.textContaining('Are you sure you want to unassign "Wrong_Receipt.pdf" from 2013 Chevrolet Sonic?'),
        findsOneWidget,
      );

      // Confirm unassign
      await tester.tap(find.widgetWithText(FilledButton, 'Unassign'));
      await tester.pumpAndSettle();

      expect(unassignedCalled, isTrue);
    });
  });
}
