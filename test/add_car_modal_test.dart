import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:car_logger/data/vehicle_data.dart';
import 'package:car_logger/widgets/add_car_modal.dart';

void main() {
  group('Chevrolet Sonic vehicle data tests', () {
    test('Chevrolet models list includes Sonic', () {
      final chevroletModels = getModelsForMake('Chevrolet');
      expect(chevroletModels.contains('Sonic'), isTrue);
    });

    test('Chevrolet Sonic has a default image URL defined', () {
      final imageUrl = getDefaultImageUrl('Chevrolet', 'Sonic');
      expect(imageUrl, isNotNull);
      expect(imageUrl, contains('Sonic'));
    });
  });

  group('AddCarModal keyboard and reflow tests', () {
    testWidgets('Tapping lower fields like License Plate and VIN works and fields receive focus',
        (WidgetTester tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () {
                  showModalBottomSheet(
                    context: context,
                    isScrollControlled: true,
                    builder: (context) => const AddCarModal(),
                  );
                },
                child: const Text('Open Modal'),
              ),
            ),
          ),
        ),
      );

      // Open the modal
      await tester.tap(find.text('Open Modal'));
      await tester.pumpAndSettle();

      expect(find.text('Add New Vehicle'), findsOneWidget);

      // Scroll down to License Plate if needed and verify fields exist
      final licensePlateFinder = find.widgetWithText(TextFormField, 'License Plate');
      final vinFinder = find.widgetWithText(TextFormField, 'VIN');
      final odometerFinder = find.widgetWithText(TextFormField, 'Current Odometer (miles)');

      expect(licensePlateFinder, findsOneWidget);
      expect(vinFinder, findsOneWidget);
      expect(odometerFinder, findsOneWidget);

      // Simulate keyboard opening by adding viewInsets
      tester.view.viewInsets = const FakeViewPadding(bottom: 300);
      await tester.pumpAndSettle();

      final sheet = tester.widget<DraggableScrollableSheet>(find.byType(DraggableScrollableSheet));
      expect(sheet.controller?.size, equals(0.95));

      // Tap on License Plate and verify it scrolls above the keyboard
      await tester.ensureVisible(licensePlateFinder);
      await tester.pumpAndSettle();

      final plateBox = tester.getRect(licensePlateFinder);
      expect(plateBox.bottom, lessThanOrEqualTo(500.0)); // Well above 600 - 100 keyboard height

      await tester.tap(licensePlateFinder);
      await tester.pumpAndSettle();

      // Enter text into License Plate
      await tester.enterText(licensePlateFinder, 'ABC-1234');
      await tester.pumpAndSettle();
      expect(find.text('ABC-1234'), findsOneWidget);

      // Tap on VIN and verify it scrolls above the keyboard
      await tester.ensureVisible(vinFinder);
      await tester.pumpAndSettle();

      final vinBox = tester.getRect(vinFinder);
      expect(vinBox.bottom, lessThanOrEqualTo(500.0));

      await tester.tap(vinFinder);
      await tester.pumpAndSettle();

      // Enter text into VIN
      await tester.enterText(vinFinder, '1G1JC5SH4D4123456');
      await tester.pumpAndSettle();
      expect(find.text('1G1JC5SH4D4123456'), findsOneWidget);

      // Tap on Odometer and verify it scrolls above the keyboard
      await tester.ensureVisible(odometerFinder);
      await tester.pumpAndSettle();

      final odoBox = tester.getRect(odometerFinder);
      expect(odoBox.bottom, lessThanOrEqualTo(500.0));

      await tester.tap(odometerFinder);
      await tester.pumpAndSettle();

      // Enter text into Odometer
      await tester.enterText(odometerFinder, '52000');
      await tester.pumpAndSettle();
      expect(find.text('52000'), findsOneWidget);

      // Reset viewInsets after test
      tester.view.resetViewInsets();
    });
  });
}
