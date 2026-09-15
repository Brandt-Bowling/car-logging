import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:car_logger/pages/home_page.dart';
import 'package:car_logger/services/storage_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

class _MockHttpClient implements HttpClient {
  @override
  bool autoUncompress = true;

  @override
  Future<HttpClientRequest> getUrl(Uri url) async => _MockHttpClientRequest();

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _TestHttpOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) => _MockHttpClient();
}

void main() {
  setUpAll(() {
    HttpOverrides.global = _TestHttpOverrides();
  });

  group('StorageService garage name unit tests', () {
    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      await StorageService.init(force: true);
    });

    test('getGarageName returns default "My Garage" initially', () {
      expect(StorageService.getGarageName(), equals('My Garage'));
    });

    test('saveGarageName persists custom name and can be retrieved', () async {
      await StorageService.saveGarageName('Project Cars Hub');
      expect(StorageService.getGarageName(), equals('Project Cars Hub'));
    });

    test('saveGarageName with default or empty resets to default', () async {
      await StorageService.saveGarageName('Temporary');
      expect(StorageService.getGarageName(), equals('Temporary'));

      await StorageService.saveGarageName('My Garage');
      expect(StorageService.getGarageName(), equals('My Garage'));

      await StorageService.saveGarageName('Custom');
      expect(StorageService.getGarageName(), equals('Custom'));

      await StorageService.saveGarageName('');
      expect(StorageService.getGarageName(), equals('My Garage'));
    });
  });

  group('Garage Renaming Widget Tests', () {
    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      await StorageService.init(force: true);
    });

    testWidgets('Displays default "My Garage" in AppBar and allows renaming via AppBar button', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      await tester.pumpAndSettle();

      // Verify initial title
      expect(find.text('My Garage'), findsOneWidget);

      // Tap the rename icon button in AppBar
      final renameButton = find.byTooltip('Rename Garage');
      expect(renameButton, findsOneWidget);
      await tester.tap(renameButton);
      await tester.pumpAndSettle();

      // Verify dialog is open
      expect(find.text('Rename Garage'), findsOneWidget);
      expect(find.byType(TextFormField), findsOneWidget);

      // Enter a new name
      await tester.enterText(find.byType(TextFormField), "Brandt's Fleet");
      await tester.pump();

      // Tap Save
      await tester.tap(find.widgetWithText(FilledButton, 'Save'));
      await tester.pumpAndSettle();

      // Verify title updated
      expect(find.text("Brandt's Fleet"), findsOneWidget);
      expect(find.text('Garage renamed to "Brandt\'s Fleet"'), findsOneWidget);
      expect(StorageService.getGarageName(), equals("Brandt's Fleet"));
    });

    testWidgets('Allows renaming garage from Settings tab', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      await tester.pumpAndSettle();

      // Switch to Settings tab
      await tester.tap(find.text('Settings'));
      await tester.pumpAndSettle();

      // Verify Garage Name tile is present
      final garageTile = find.text('Garage Name');
      expect(garageTile, findsOneWidget);
      expect(find.text('Current: My Garage'), findsOneWidget);

      // Tap the Garage Name tile
      await tester.tap(garageTile);
      await tester.pumpAndSettle();

      // Verify dialog is open
      expect(find.text('Rename Garage'), findsOneWidget);

      // Enter new name and save
      await tester.enterText(find.byType(TextFormField), 'The Batcave');
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Save'));
      await tester.pumpAndSettle();

      // Verify settings tile updated
      expect(find.text('Current: The Batcave'), findsOneWidget);

      // Switch back to Garage tab and verify AppBar updated
      await tester.tap(find.text('Garage'));
      await tester.pumpAndSettle();
      expect(find.text('The Batcave'), findsOneWidget);
    });

    testWidgets('Validates against empty or whitespace-only name', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('Rename Garage'));
      await tester.pumpAndSettle();

      // Clear text
      await tester.enterText(find.byType(TextFormField), '   ');
      await tester.pump();

      // Tap Save
      await tester.tap(find.widgetWithText(FilledButton, 'Save'));
      await tester.pumpAndSettle();

      // Verify validator error message appears and dialog is still visible
      expect(find.text('Please enter a garage name'), findsOneWidget);
      expect(find.text('Rename Garage'), findsOneWidget);

      // Tap Cancel
      await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
      await tester.pumpAndSettle();

      // Verify title remained unchanged
      expect(find.text('My Garage'), findsOneWidget);
    });
  });
}
