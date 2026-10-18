import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart' as http_testing;
import 'package:shared_preferences/shared_preferences.dart';

import 'package:car_logger/models/car.dart';
import 'package:car_logger/services/auto_dev_service.dart';
import 'package:car_logger/widgets/add_car_modal.dart';

final _kTransparentImage = Uint8List.fromList(<int>[
  0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
  0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
  0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
  0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4,
  0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
  0x54, 0x78, 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
  0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4, 0x00,
  0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE,
  0x42, 0x60, 0x82,
]);

class _MockHttpClientRequest implements HttpClientRequest {
  final _MockHttpClientResponse _response = _MockHttpClientResponse();

  @override
  final HttpHeaders headers = _MockHttpHeaders();

  @override
  Future<HttpClientResponse> close() async => _response;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _MockHttpClientResponse extends Stream<List<int>>
    implements HttpClientResponse {
  @override
  int get statusCode => HttpStatus.ok;

  @override
  int get contentLength => _kTransparentImage.length;

  @override
  HttpClientResponseCompressionState get compressionState =>
      HttpClientResponseCompressionState.notCompressed;

  @override
  final HttpHeaders headers = _MockHttpHeaders();

  @override
  StreamSubscription<List<int>> listen(
    void Function(List<int> event)? onData, {
    Function? onError,
    void Function()? onDone,
    bool? cancelOnError,
  }) {
    return Stream<List<int>>.fromIterable([_kTransparentImage]).listen(
      onData,
      onError: onError,
      onDone: onDone,
      cancelOnError: cancelOnError,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _MockHttpHeaders implements HttpHeaders {
  @override
  dynamic noSuchMethod(Invocation invocation) => null;
}

class _TestHttpOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) => _MockHttpClient();
}

class _MockHttpClient implements HttpClient {
  @override
  bool autoUncompress = true;

  @override
  Future<HttpClientRequest> getUrl(Uri url) async => _MockHttpClientRequest();

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  setUpAll(() {
    HttpOverrides.global = _TestHttpOverrides();
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  tearDown(() {
    AutoDevService.testClient = null;
  });

  group('AddCarModal Add by VIN Tests', () {
    testWidgets('Entering VIN in lower field and tapping auto-fill decodes and fills fields',
        (WidgetTester tester) async {
      Car? savedCar;

      AutoDevService.testClient = http_testing.MockClient((request) async {
        if (request.url.path.contains('/vin/')) {
          return http.Response(
            jsonEncode({
              'vin': '1HGCV1F18MA000000',
              'make': 'Honda',
              'model': 'Accord',
              'year': 2021,
              'trim': 'Sport',
              'body': 'Sedan',
              'engine': '1.5L I4 Turbocharged',
              'fuel': 'Gasoline',
            }),
            200,
          );
        } else if (request.url.path.contains('/photos/')) {
          return http.Response(
            jsonEncode({
              'data': {
                'retail': ['https://api.auto.dev/photos/retail/1HGCV1F18MA000000-1.jpg'],
              }
            }),
            200,
          );
        }
        return http.Response('Not found', 404);
      });

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () async {
                  savedCar = await showModalBottomSheet<Car>(
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

      await tester.tap(find.text('Open Modal'));
      await tester.pumpAndSettle();

      // Find the VIN TextFormField
      final vinFieldFinder = find.widgetWithText(TextFormField, 'VIN');
      expect(vinFieldFinder, findsOneWidget);

      await tester.ensureVisible(vinFieldFinder);
      await tester.pumpAndSettle();

      await tester.enterText(vinFieldFinder, '1HGCV1F18MA000000');
      await tester.pumpAndSettle();

      // Tap the auto-fill/decode button inside the VIN field decoration
      final autoFillButtonFinder = find.byTooltip('Decode VIN & populate vehicle details via auto.dev');
      expect(autoFillButtonFinder, findsOneWidget);

      await tester.tap(autoFillButtonFinder);
      await tester.pump(); // Start loading
      await tester.pumpAndSettle(); // Finish network call and animations

      // Verify fields got populated
      expect(find.text('Honda'), findsOneWidget);
      expect(find.text('Accord'), findsOneWidget);
      expect(find.text('2021'), findsOneWidget);

      // Verify SnackBar confirmation
      expect(find.textContaining('Vehicle details populated for 2021 Honda Accord!'), findsOneWidget);

      // Scroll down and submit the form
      final addVehicleButtonFinder = find.widgetWithText(FilledButton, 'Add Vehicle');
      await tester.ensureVisible(addVehicleButtonFinder);
      await tester.pumpAndSettle();

      await tester.tap(addVehicleButtonFinder);
      await tester.pumpAndSettle();

      // Verify the saved car has VIN, make, model, year, trim, bodyStyle, and photoUrl
      expect(savedCar, isNotNull);
      expect(savedCar!.vin, equals('1HGCV1F18MA000000'));
      expect(savedCar!.make, equals('Honda'));
      expect(savedCar!.model, equals('Accord'));
      expect(savedCar!.year, equals(2021));
      expect(savedCar!.trim, equals('Sport'));
      expect(savedCar!.bodyStyle, equals('Sedan'));
      expect(savedCar!.imageUrl, equals('https://api.auto.dev/photos/retail/1HGCV1F18MA000000-1.jpg'));
      expect(savedCar!.isEv, isFalse);
    });

    testWidgets('AddCarModal with openVinPrompt: true shows prompt and populates EV vehicle',
        (WidgetTester tester) async {
      Car? savedCar;

      AutoDevService.testClient = http_testing.MockClient((request) async {
        if (request.url.path.contains('/vin/')) {
          return http.Response(
            jsonEncode({
              'vin': '5YJ3E1EB8NF000000',
              'make': 'Tesla',
              'model': 'Model 3',
              'year': 2022,
              'trim': 'Long Range',
              'body': 'Sedan',
              'engine': 'Electric Motor',
              'fuel': 'Electric',
            }),
            200,
          );
        } else if (request.url.path.contains('/photos/')) {
          return http.Response(
            jsonEncode({
              'data': {
                'retail': ['https://api.auto.dev/photos/retail/5YJ3E1EB8NF000000-1.jpg'],
              }
            }),
            200,
          );
        }
        return http.Response('Not found', 404);
      });

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () async {
                  savedCar = await showModalBottomSheet<Car>(
                    context: context,
                    isScrollControlled: true,
                    builder: (context) => const AddCarModal(
                      openVinPrompt: true,
                    ),
                  );
                },
                child: const Text('Open Modal With Prompt'),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open Modal With Prompt'));
      await tester.pumpAndSettle();

      // Verify the prompt dialog is visible
      expect(find.text('Add Vehicle by VIN'), findsOneWidget);

      // Enter VIN in dialog
      final dialogVinFinder = find.descendant(
        of: find.byType(AlertDialog),
        matching: find.byType(TextField),
      );
      expect(dialogVinFinder, findsOneWidget);

      await tester.enterText(dialogVinFinder, '5YJ3E1EB8NF000000');
      await tester.pumpAndSettle();

      // Tap "Decode & Auto-fill"
      await tester.tap(find.text('Decode & Auto-fill'));
      await tester.pump();
      await tester.pumpAndSettle();

      // Check fields
      expect(find.text('Tesla'), findsOneWidget);
      expect(find.text('Model 3'), findsOneWidget);
      expect(find.text('2022'), findsOneWidget);

      // Save vehicle
      final addVehicleButton = find.widgetWithText(FilledButton, 'Add Vehicle');
      await tester.ensureVisible(addVehicleButton);
      await tester.pumpAndSettle();

      await tester.tap(addVehicleButton);
      await tester.pumpAndSettle();

      expect(savedCar, isNotNull);
      expect(savedCar!.make, equals('Tesla'));
      expect(savedCar!.model, equals('Model 3'));
      expect(savedCar!.year, equals(2022));
      expect(savedCar!.isEv, isTrue);
      expect(savedCar!.imageUrl, equals('https://api.auto.dev/photos/retail/5YJ3E1EB8NF000000-1.jpg'));
    });

    testWidgets('Shows error SnackBar when VIN decoding fails',
        (WidgetTester tester) async {
      AutoDevService.testClient = http_testing.MockClient((request) async {
        return http.Response(
          jsonEncode({
            'error': {'message': 'Invalid VIN or vehicle not found'}
          }),
          400,
        );
      });

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () async {
                  await showModalBottomSheet<Car>(
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

      await tester.tap(find.text('Open Modal'));
      await tester.pumpAndSettle();

      final vinFieldFinder = find.widgetWithText(TextFormField, 'VIN');
      await tester.ensureVisible(vinFieldFinder);
      await tester.pumpAndSettle();

      await tester.enterText(vinFieldFinder, 'INVALIDVIN123');
      await tester.pumpAndSettle();

      final autoFillButtonFinder = find.byTooltip('Decode VIN & populate vehicle details via auto.dev');
      await tester.tap(autoFillButtonFinder);
      await tester.pump();
      await tester.pumpAndSettle();

      expect(find.text('Invalid VIN or vehicle not found'), findsWidgets);
      expect(find.byType(SnackBar), findsOneWidget);
    });

    testWidgets('Prompt dialog retains focus, does not close on decode error, and shows error message',
        (WidgetTester tester) async {
      AutoDevService.testClient = http_testing.MockClient((request) async {
        return http.Response(
          jsonEncode({
            'error': {'message': 'VIN decode error: vehicle not found'}
          }),
          404,
        );
      });

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () async {
                  await showModalBottomSheet<Car>(
                    context: context,
                    isScrollControlled: true,
                    builder: (context) => const AddCarModal(openVinPrompt: true),
                  );
                },
                child: const Text('Open Modal With Prompt'),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open Modal With Prompt'));
      await tester.pumpAndSettle();

      expect(find.text('Add Vehicle by VIN'), findsOneWidget);

      final dialogVinFinder = find.descendant(
        of: find.byType(AlertDialog),
        matching: find.byType(TextField),
      );
      await tester.enterText(dialogVinFinder, 'BADVIN999');
      await tester.pumpAndSettle();

      await tester.tap(find.text('Decode & Auto-fill'));
      await tester.pump();
      await tester.pumpAndSettle();

      // Dialog is still open!
      expect(find.text('Add Vehicle by VIN'), findsOneWidget);
      expect(find.text('VIN decode error: vehicle not found'), findsOneWidget);
    });

    testWidgets('API key 401 error displays Set API Key in dialog and allows setting key',
        (WidgetTester tester) async {
      AutoDevService.testClient = http_testing.MockClient((request) async {
        return http.Response(
          jsonEncode({
            'error': {'message': 'Authentication required. Get your API key at https://auto.dev/dashboard/api-keys'}
          }),
          401,
        );
      });

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () async {
                  await showModalBottomSheet<Car>(
                    context: context,
                    isScrollControlled: true,
                    builder: (context) => const AddCarModal(openVinPrompt: true),
                  );
                },
                child: const Text('Open Modal With Prompt'),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open Modal With Prompt'));
      await tester.pumpAndSettle();

      final dialogVinFinder = find.descendant(
        of: find.byType(AlertDialog),
        matching: find.byType(TextField),
      );
      await tester.enterText(dialogVinFinder, '1HGCV1F18MA000000');
      await tester.pumpAndSettle();

      await tester.tap(find.text('Decode & Auto-fill'));
      await tester.pump();
      await tester.pumpAndSettle();

      // Dialog is open and shows error + Set API Key action button
      expect(find.text('Set API Key'), findsOneWidget);

      // Tap Set API Key to open key dialog
      await tester.tap(find.text('Set API Key'));
      await tester.pumpAndSettle();

      expect(find.text('auto.dev API Key'), findsOneWidget);
      expect(find.text('Save'), findsOneWidget);
    });

    testWidgets('VIN decode with multiple photos displays carousel and allows selecting another photo',
        (WidgetTester tester) async {
      Car? savedCar;

      AutoDevService.testClient = http_testing.MockClient((request) async {
        if (request.url.path.contains('/vin/')) {
          return http.Response(
            jsonEncode({
              'vin': '19XFL1H87RE004388',
              'make': 'Honda',
              'model': 'Civic Hatchback',
              'year': 2024,
            }),
            200,
          );
        } else if (request.url.path.contains('/photos/')) {
          return http.Response(
            jsonEncode({
              'data': {
                'retail': [
                  'https://api.auto.dev/photos/retail/19XFL1H87RE004388-1.jpg',
                  'https://api.auto.dev/photos/retail/19XFL1H87RE004388-2.jpg',
                  'https://api.auto.dev/photos/retail/19XFL1H87RE004388-3.jpg',
                ],
                'wholesale': [],
              }
            }),
            200,
          );
        }
        return http.Response('Not found', 404);
      });

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () async {
                  savedCar = await showModalBottomSheet<Car>(
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

      await tester.tap(find.text('Open Modal'));
      await tester.pumpAndSettle();

      final vinFieldFinder = find.widgetWithText(TextFormField, 'VIN');
      await tester.ensureVisible(vinFieldFinder);
      await tester.enterText(vinFieldFinder, '19XFL1H87RE004388');
      await tester.pumpAndSettle();

      final autoFillButton = find.byTooltip('Decode VIN & populate vehicle details via auto.dev');
      await tester.tap(autoFillButton);
      await tester.pump();
      await tester.pumpAndSettle();

      // Verify carousel is rendered
      expect(find.text('Vehicle Photos'), findsOneWidget);
      expect(find.textContaining('available'), findsOneWidget);
      expect(find.text('Selected photo'), findsOneWidget);
      expect(find.text('Tap any photo to select:'), findsOneWidget);
      expect(find.textContaining('1 / '), findsOneWidget);

      // Verify Next photo button exists
      final nextButton = find.byTooltip('Next photo');
      expect(nextButton, findsOneWidget);
      await tester.ensureVisible(nextButton);
      await tester.pumpAndSettle();

      // Tap Next photo button to advance to photo 2
      await tester.tap(nextButton);
      await tester.pumpAndSettle();

      // Verify counter now shows 2
      expect(find.textContaining('2 / '), findsOneWidget);

      // Scroll to Add Vehicle button and save
      final addVehicleButton = find.widgetWithText(FilledButton, 'Add Vehicle');
      await tester.ensureVisible(addVehicleButton);
      await tester.pumpAndSettle();

      await tester.tap(addVehicleButton);
      await tester.pumpAndSettle();

      // Verify the saved car picked photo 2 instead of photo 1!
      expect(savedCar, isNotNull);
      expect(savedCar!.imageUrl, equals('https://api.auto.dev/photos/retail/19XFL1H87RE004388-2.jpg'));
    });
  });
}
