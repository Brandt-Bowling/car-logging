import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:car_logger/models/car.dart';
import 'package:car_logger/pages/car_details_page.dart';
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

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init(force: true);
  });

  group('Car Model & StorageService Archive Tests', () {
    test('Car model defaults isArchived to false and supports copyWith and serialization', () {
      final car = Car(
        id: 'c1',
        make: 'Honda',
        model: 'Civic',
        year: 2020,
      );
      expect(car.isArchived, isFalse);

      final archivedCar = car.copyWith(isArchived: true);
      expect(archivedCar.isArchived, isTrue);

      final map = archivedCar.toMap();
      expect(map['isArchived'], isTrue);

      final reconstructed = Car.fromMap(map);
      expect(reconstructed.isArchived, isTrue);

      // Default fromMap without isArchived
      final legacy = Car.fromMap({
        'id': 'c2',
        'make': 'Toyota',
        'model': 'Corolla',
        'year': 2019,
      });
      expect(legacy.isArchived, isFalse);
    });

    test('StorageService.toggleArchiveCar flips isArchived', () async {
      final car1 = Car(id: '1', make: 'Chevrolet', model: 'Sonic', year: 2013);
      final car2 = Car(id: '2', make: 'Tesla', model: 'Model 3', year: 2018);
      await StorageService.saveCars([car1, car2]);

      expect(StorageService.getCars().first.isArchived, isFalse);

      await StorageService.toggleArchiveCar('1');
      expect(StorageService.getCars().firstWhere((c) => c.id == '1').isArchived, isTrue);

      await StorageService.toggleArchiveCar('1');
      expect(StorageService.getCars().firstWhere((c) => c.id == '1').isArchived, isFalse);
    });
  });

  group('HomePage Archive Car Widget Tests', () {
    testWidgets('Archived cars appear at the bottom of the list and are grayed out', (tester) async {
      // Setup: 1st car is archived, 2nd car is active
      final car1 = Car(
        id: '1',
        make: 'Chevrolet',
        model: 'Sonic',
        year: 2013,
        isArchived: true,
      );
      final car2 = Car(
        id: '2',
        make: 'Tesla',
        model: 'Model 3',
        year: 2018,
        isEv: true,
        isArchived: false,
      );
      await StorageService.saveCars([car1, car2]);

      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      await tester.pumpAndSettle();

      // Tesla should be the first vehicle listed, Chevrolet at the bottom
      final teslaFinder = find.text('2018 Tesla Model 3');
      final chevyFinder = find.text('2013 Chevrolet Sonic');

      expect(teslaFinder, findsOneWidget);
      expect(chevyFinder, findsOneWidget);

      final teslaY = tester.getTopLeft(teslaFinder).dy;
      final chevyY = tester.getTopLeft(chevyFinder).dy;

      // Active car should appear before (above) the archived car
      expect(teslaY, lessThan(chevyY));

      // Badges
      expect(find.text('ACTIVE EV'), findsOneWidget);
      expect(find.text('ARCHIVED'), findsOneWidget);

      // Verify the archived vehicle card is wrapped in Opacity and ColorFiltered
      final colorFilteredFinder = find.byType(ColorFiltered);
      expect(colorFilteredFinder, findsWidgets);

      final opacityWidgets = tester.widgetList<Opacity>(find.byType(Opacity));
      final hasCardOpacity = opacityWidgets.any((op) => op.opacity == 0.6);
      expect(hasCardOpacity, isTrue);
    });

    testWidgets('Archive vehicle from card popup menu updates position and applies grayed out styling', (tester) async {
      final car1 = Car(id: '1', make: 'Chevrolet', model: 'Sonic', year: 2013);
      final car2 = Car(id: '2', make: 'Tesla', model: 'Model 3', year: 2018, isEv: true);
      await StorageService.saveCars([car1, car2]);

      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      await tester.pumpAndSettle();

      // Initially Chevy is first (index 0)
      double chevyY = tester.getTopLeft(find.text('2013 Chevrolet Sonic')).dy;
      double teslaY = tester.getTopLeft(find.text('2018 Tesla Model 3')).dy;
      expect(chevyY, lessThan(teslaY));
      expect(find.text('ACTIVE'), findsOneWidget);
      expect(find.text('ACTIVE EV'), findsOneWidget);
      expect(find.text('ARCHIVED'), findsNothing);

      // Tap popup menu on the Chevy card
      final popupFinder = find.byType(PopupMenuButton<String>);
      expect(popupFinder, findsNWidgets(2));
      await tester.tap(popupFinder.first);
      await tester.pumpAndSettle();

      // Select "Archive Vehicle"
      expect(find.text('Archive Vehicle'), findsOneWidget);
      await tester.tap(find.text('Archive Vehicle'));
      await tester.pumpAndSettle();

      // Verify SnackBar
      expect(find.text('Archived Chevrolet Sonic'), findsOneWidget);

      // Chevy should now be at the bottom (below Tesla)
      chevyY = tester.getTopLeft(find.text('2013 Chevrolet Sonic')).dy;
      teslaY = tester.getTopLeft(find.text('2018 Tesla Model 3')).dy;
      expect(teslaY, lessThan(chevyY));

      // Badge updated to ARCHIVED
      expect(find.text('ARCHIVED'), findsOneWidget);

      // Tap "Undo" on SnackBar
      expect(find.text('Undo'), findsOneWidget);
      await tester.tap(find.text('Undo'));
      await tester.pumpAndSettle();

      // Chevy moves back to the top
      chevyY = tester.getTopLeft(find.text('2013 Chevrolet Sonic')).dy;
      teslaY = tester.getTopLeft(find.text('2018 Tesla Model 3')).dy;
      expect(chevyY, lessThan(teslaY));
      expect(find.text('ACTIVE'), findsOneWidget);
      expect(find.text('ARCHIVED'), findsNothing);
    });

    testWidgets('Archive and unarchive from CarDetailsPage AppBar updates HomePage list', (tester) async {
      final car1 = Car(id: '1', make: 'Chevrolet', model: 'Sonic', year: 2013);
      final car2 = Car(id: '2', make: 'Tesla', model: 'Model 3', year: 2018, isEv: true);
      await StorageService.saveCars([car1, car2]);

      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      await tester.pumpAndSettle();

      // Open Chevy details page
      await tester.tap(find.text('2013 Chevrolet Sonic'));
      await tester.pumpAndSettle();

      expect(find.byType(CarDetailsPage), findsOneWidget);

      // Find archive button in AppBar
      final archiveButton = find.byTooltip('Archive Vehicle');
      expect(archiveButton, findsOneWidget);

      // Tap Archive
      await tester.tap(archiveButton);
      await tester.pumpAndSettle();

      expect(find.text('Chevrolet Sonic archived'), findsOneWidget);
      expect(find.byTooltip('Unarchive Vehicle'), findsOneWidget);

      // Pop back to HomePage
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();

      // Now Chevy is at the bottom, Tesla is first
      final chevyY = tester.getTopLeft(find.text('2013 Chevrolet Sonic')).dy;
      final teslaY = tester.getTopLeft(find.text('2018 Tesla Model 3')).dy;
      expect(teslaY, lessThan(chevyY));
      expect(find.text('ARCHIVED'), findsOneWidget);

      // Tap Chevy again to open details and unarchive it
      await tester.tap(find.text('2013 Chevrolet Sonic'));
      await tester.pumpAndSettle();

      final unarchiveButton = find.byTooltip('Unarchive Vehicle');
      expect(unarchiveButton, findsOneWidget);
      await tester.tap(unarchiveButton);
      await tester.pumpAndSettle();

      expect(find.text('Chevrolet Sonic unarchived'), findsOneWidget);
      expect(find.byTooltip('Archive Vehicle'), findsOneWidget);

      // Pop back to HomePage
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();

      // Chevy restored to top
      final chevyYRestored = tester.getTopLeft(find.text('2013 Chevrolet Sonic')).dy;
      final teslaYRestored = tester.getTopLeft(find.text('2018 Tesla Model 3')).dy;
      expect(chevyYRestored, lessThan(teslaYRestored));
      expect(find.text('ACTIVE'), findsOneWidget);
      expect(find.text('ARCHIVED'), findsNothing);
    });

    testWidgets('Long pressing vehicle card shows options bottom sheet to archive', (tester) async {
      final car1 = Car(id: '1', make: 'Chevrolet', model: 'Sonic', year: 2013);
      await StorageService.saveCars([car1]);

      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      await tester.pumpAndSettle();

      // Long press card
      await tester.longPress(find.text('2013 Chevrolet Sonic'));
      await tester.pumpAndSettle();

      // Verify bottom sheet options
      expect(find.text('Archive Vehicle'), findsOneWidget);
      expect(find.text('Remove Vehicle'), findsOneWidget);

      // Tap Archive Vehicle
      await tester.tap(find.text('Archive Vehicle'));
      await tester.pumpAndSettle();

      expect(find.text('Archived Chevrolet Sonic'), findsOneWidget);
      expect(find.text('ARCHIVED'), findsOneWidget);
    });
  });
}
