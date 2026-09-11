import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:googleapis/drive/v3.dart' as drive;
import 'package:car_logger/widgets/drive_folder_picker_sheet.dart';

void main() {
  group('DriveFolderSelection Data Model', () {
    test('Constructs and exposes properties correctly', () {
      const selection = DriveFolderSelection(
        id: 'sonic_folder_123',
        name: 'sonic',
        path: 'cars / sonic',
      );

      expect(selection.id, equals('sonic_folder_123'));
      expect(selection.name, equals('sonic'));
      expect(selection.path, equals('cars / sonic'));
      expect(selection.toString(), contains('cars / sonic'));
    });
  });

  group('DriveFolderPickerSheet Widget Tests', () {
    // Simulated folder structure:
    // root
    //  ├── cars (id: 'cars_id')
    //  │    ├── sonic (id: 'sonic_id')
    //  │    └── corvette (id: 'corvette_id')
    //  └── taxes (id: 'taxes_id')

    Future<List<drive.File>> mockFolderFetcher({
      String parentId = 'root',
      String? searchName,
    }) async {
      if (searchName != null && searchName.isNotEmpty) {
        if ('sonic'.contains(searchName.toLowerCase())) {
          return [
            drive.File(id: 'sonic_id', name: 'sonic'),
          ];
        }
        return [];
      }

      if (parentId == 'root') {
        return [
          drive.File(id: 'cars_id', name: 'cars'),
          drive.File(id: 'taxes_id', name: 'taxes'),
        ];
      } else if (parentId == 'cars_id') {
        return [
          drive.File(id: 'sonic_id', name: 'sonic'),
          drive.File(id: 'corvette_id', name: 'corvette'),
        ];
      } else if (parentId == 'sonic_id') {
        return []; // No subfolders in sonic
      }
      return [];
    }

    testWidgets('Displays root folders and disables select at root', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: DriveFolderPickerSheet(
              folderFetcher: mockFolderFetcher,
            ),
          ),
        ),
      );

      // Let initial async load complete
      await tester.pumpAndSettle();

      // Should show 'My Drive' breadcrumb and root folders
      expect(find.text('My Drive'), findsOneWidget);
      expect(find.text('cars'), findsOneWidget);
      expect(find.text('taxes'), findsOneWidget);

      // At root, button is disabled with prompt
      expect(find.text('Open a folder above to select it'), findsOneWidget);
    });

    testWidgets('Drills down into cars -> sonic, updates breadcrumbs and selects nested path', (
      WidgetTester tester,
    ) async {
      DriveFolderSelection? result;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) {
                return ElevatedButton(
                  onPressed: () async {
                    result = await showDriveFolderPicker(
                      context,
                      folderFetcher: mockFolderFetcher,
                    );
                  },
                  child: const Text('Open Picker'),
                );
              },
            ),
          ),
        ),
      );

      // Open sheet
      await tester.tap(find.text('Open Picker'));
      await tester.pumpAndSettle();

      expect(find.text('cars'), findsOneWidget);

      // Tap 'cars' row to navigate into it
      await tester.tap(find.text('cars'));
      await tester.pumpAndSettle();

      // Breadcrumb should show 'cars'
      expect(find.text('My Drive'), findsOneWidget);
      expect(find.text('cars'), findsOneWidget);
      expect(find.text('sonic'), findsOneWidget);
      expect(find.text('corvette'), findsOneWidget);

      // Bottom button now allows selecting 'cars'
      expect(find.text('Use "cars"'), findsOneWidget);

      // Tap 'sonic' to navigate deeper
      await tester.tap(find.text('sonic'));
      await tester.pumpAndSettle();

      // Now inside sonic (empty subfolders)
      expect(find.text('No subfolders found in "sonic"'), findsOneWidget);
      expect(find.text('Use "cars / sonic"'), findsOneWidget);

      // Tap "Use cars / sonic" to select it
      await tester.tap(find.text('Use "cars / sonic"'));
      await tester.pumpAndSettle();

      // Sheet closed and returned nested selection
      expect(result, isNotNull);
      expect(result!.id, equals('sonic_id'));
      expect(result!.name, equals('sonic'));
      expect(result!.path, equals('cars / sonic'));
    });

    testWidgets('Direct inline Select button picks folder without navigating into it', (
      WidgetTester tester,
    ) async {
      DriveFolderSelection? result;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) {
                return ElevatedButton(
                  onPressed: () async {
                    result = await showDriveFolderPicker(
                      context,
                      folderFetcher: mockFolderFetcher,
                    );
                  },
                  child: const Text('Open Picker'),
                );
              },
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open Picker'));
      await tester.pumpAndSettle();

      // Navigate to 'cars'
      await tester.tap(find.text('cars'));
      await tester.pumpAndSettle();

      // In 'cars' view, find the 'Select' button next to 'sonic'
      final sonicSelectButton = find.descendant(
        of: find.widgetWithText(ListTile, 'sonic'),
        matching: find.widgetWithText(OutlinedButton, 'Select'),
      );
      expect(sonicSelectButton, findsOneWidget);

      // Tap sonic's select button
      await tester.tap(sonicSelectButton);
      await tester.pumpAndSettle();

      expect(result, isNotNull);
      expect(result!.id, equals('sonic_id'));
      expect(result!.name, equals('sonic'));
      expect(result!.path, equals('cars / sonic'));
    });

    testWidgets('Breadcrumbs allow jumping back up the tree', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: DriveFolderPickerSheet(
              folderFetcher: mockFolderFetcher,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Go into cars
      await tester.tap(find.text('cars'));
      await tester.pumpAndSettle();

      // Go into sonic
      await tester.tap(find.text('sonic'));
      await tester.pumpAndSettle();
      expect(find.text('Use "cars / sonic"'), findsOneWidget);

      // Tap up arrow to go back to cars
      await tester.tap(find.byTooltip('Go to parent folder'));
      await tester.pumpAndSettle();
      expect(find.text('Use "cars"'), findsOneWidget);
      expect(find.text('corvette'), findsOneWidget);

      // Tap 'My Drive' breadcrumb chip to jump directly back to root
      await tester.tap(find.text('My Drive'));
      await tester.pumpAndSettle();
      expect(find.text('taxes'), findsOneWidget);
      expect(find.text('Open a folder above to select it'), findsOneWidget);
    });

    testWidgets('Search filters folders globally and allows selection', (
      WidgetTester tester,
    ) async {
      DriveFolderSelection? result;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) {
                return ElevatedButton(
                  onPressed: () async {
                    result = await showDriveFolderPicker(
                      context,
                      folderFetcher: mockFolderFetcher,
                    );
                  },
                  child: const Text('Open Picker'),
                );
              },
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open Picker'));
      await tester.pumpAndSettle();

      // Enter search query 'sonic'
      await tester.enterText(find.byType(TextField), 'sonic');
      // Advance timer for 350ms debounce
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();

      expect(find.widgetWithText(ListTile, 'sonic'), findsOneWidget);
      expect(find.text('Search result'), findsOneWidget);

      // Tap sonic result
      await tester.tap(find.widgetWithText(ListTile, 'sonic'));
      await tester.pumpAndSettle();

      expect(result, isNotNull);
      expect(result!.id, equals('sonic_id'));
      expect(result!.name, equals('sonic'));
    });
  });
}
