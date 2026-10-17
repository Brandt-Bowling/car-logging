import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:car_logger/models/car.dart';
import 'package:googleapis/drive/v3.dart' as drive;
import 'package:car_logger/models/maintenance_record.dart';
import 'package:car_logger/pages/tabs/maintenance_tab.dart';
import 'package:car_logger/services/storage_service.dart';
import 'package:car_logger/widgets/drive_attachment_viewer_sheet.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final testCar = Car(
    id: 'test_car_years',
    make: 'Chevrolet',
    model: 'Sonic',
    year: 2013,
  );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
    await StorageService.saveCars([testCar]);
  });

  testWidgets('Maintenance records are grouped by year and filterable by year chips', (
    WidgetTester tester,
  ) async {
    final records = [
      MaintenanceRecord(
        id: 'rec_2025_1',
        carId: testCar.id,
        title: 'Oil Change 2025',
        date: DateTime(2025, 6, 15),
        odometer: 48000,
        cost: 70.0,
        driveFileId: 'drive_file_2025',
      ),
      MaintenanceRecord(
        id: 'rec_2024_1',
        carId: testCar.id,
        title: 'Brake Service 2024',
        date: DateTime(2024, 8, 20),
        odometer: 42000,
        cost: 250.0,
        driveFileId: 'drive_file_2024',
      ),
      MaintenanceRecord(
        id: 'rec_2024_2',
        carId: testCar.id,
        title: 'Tire Rotation 2024',
        date: DateTime(2024, 3, 10),
        odometer: 39000,
        cost: 40.0,
      ),
      MaintenanceRecord(
        id: 'rec_2023_1',
        carId: testCar.id,
        title: 'Battery Replacement 2023',
        date: DateTime(2023, 11, 5),
        odometer: 32000,
        cost: 150.0,
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

    // 1. Verify year filter chips exist
    expect(find.widgetWithText(ChoiceChip, 'All Years (4)'), findsOneWidget);
    expect(find.widgetWithText(ChoiceChip, '2025 (1)'), findsOneWidget);
    expect(find.widgetWithText(ChoiceChip, '2024 (2)'), findsOneWidget);
    expect(find.widgetWithText(ChoiceChip, '2023 (1)'), findsOneWidget);

    // 2. In "All Years" view, verify year headers and records appear (using skipOffstage for items down the list)
    expect(find.text('2025'), findsWidgets);
    expect(find.text('2024'), findsWidgets);
    expect(find.text('2023', skipOffstage: false), findsWidgets);
    expect(find.text('Oil Change 2025'), findsOneWidget);
    expect(find.text('Brake Service 2024'), findsOneWidget);
    expect(find.text('Tire Rotation 2024'), findsOneWidget);
    expect(find.text('Battery Replacement 2023', skipOffstage: false), findsOneWidget);

    // 3. Tap on "2024 (2)" filter chip
    await tester.tap(find.widgetWithText(ChoiceChip, '2024 (2)'));
    await tester.pumpAndSettle();

    // Verify only 2024 records are shown
    expect(find.text('Brake Service 2024'), findsOneWidget);
    expect(find.text('Tire Rotation 2024'), findsOneWidget);
    expect(find.text('Oil Change 2025'), findsNothing);
    expect(find.text('Battery Replacement 2023'), findsNothing);

    // Verify summary card reflects Year 2024 ($250 + $40 = $290.00)
    expect(find.text('Year 2024 Summary'), findsOneWidget);
    expect(find.text('\$290.00'), findsWidgets);

    // 4. Tap on "All Years" to restore full view
    await tester.tap(find.widgetWithText(ChoiceChip, 'All Years (4)'));
    await tester.pumpAndSettle();
    expect(find.text('Oil Change 2025'), findsOneWidget);
  });

  testWidgets('Clicking on a record gives the option to see the linked Drive attachment', (
    WidgetTester tester,
  ) async {
    final records = [
      MaintenanceRecord(
        id: 'rec_with_drive',
        carId: testCar.id,
        title: 'Transmission Fluid Flush',
        date: DateTime(2025, 4, 18),
        odometer: 47000,
        cost: 180.0,
        description: 'OEM synthetic fluid replaced at dealer.',
        driveFileId: 'drive_receipt_xyz_123',
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

    // Verify option to view attachment is present directly on the record card
    expect(find.text('View Attachment'), findsOneWidget);

    // Click on the record card
    await tester.tap(find.text('Transmission Fluid Flush'));
    await tester.pumpAndSettle();

    // Verify dialog opened with prefilled fields AND option to view Drive attachment
    expect(find.text('Edit Maintenance Record'), findsOneWidget);
    expect(find.text('Synced from Google Drive'), findsOneWidget);
    expect(find.widgetWithText(TextButton, 'View Attachment'), findsOneWidget);

    // Tap on "View Attachment" button in dialog
    await tester.tap(find.widgetWithText(TextButton, 'View Attachment'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    // Verify DriveAttachmentViewerSheet opened
    expect(find.byType(DriveAttachmentViewerSheet), findsOneWidget);
    expect(find.text('Attachment: Transmission Fluid Flush'), findsOneWidget);
    expect(find.text('Linked from Google Drive'), findsOneWidget);
  });

  testWidgets('DriveAttachmentViewerSheet shows sign in option when user not signed in', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: DriveAttachmentViewerSheet(
            driveFileId: 'mock_drive_file_id',
            recordTitle: 'Oil Change Invoice',
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.text('Attachment: Oil Change Invoice'), findsOneWidget);
    expect(find.text('Google Drive Sign-In Needed'), findsOneWidget);
    expect(find.text('Sign In with Google'), findsOneWidget);
  });

  testWidgets('DriveAttachmentViewerSheet displays file preview when loaded', (
    WidgetTester tester,
  ) async {
    final mockPdfFile = drive.File(
      id: 'receipt_pdf_1',
      name: 'Oil_Change_Receipt.pdf',
      mimeType: 'application/pdf',
      size: '245760', // 240 KB
      webViewLink: 'https://drive.google.com/file/d/receipt_pdf_1/view',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DriveAttachmentViewerSheet(
            driveFileId: 'receipt_pdf_1',
            recordTitle: 'Oil Change Invoice',
            initialDriveFile: mockPdfFile,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Attachment: Oil Change Invoice'), findsOneWidget);
    expect(find.text('Oil_Change_Receipt.pdf'), findsWidgets);
    expect(find.text('PDF Document'), findsWidgets);
    expect(find.text('Open in Google Drive'), findsOneWidget);
  });
}
