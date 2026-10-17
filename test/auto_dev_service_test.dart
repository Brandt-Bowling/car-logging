import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:car_logger/services/auto_dev_service.dart';

void main() {
  group('AutoDevService Tests', () {
    test('decodeVin correctly parses standard ICE vehicle (Honda Civic)', () async {
      final mockResponse = {
        'vin': '19XFL1H87RE004388',
        'vinValid': true,
        'year': 2024,
        'make': 'Honda',
        'model': 'Civic Hatchback',
        'trim': 'Sport Touring CVT',
        'body': 'Car',
        'engine': '1.5, 4 Cylinder Engine',
        'drive': 'Front Wheel Drive',
        'transmission': 'Automatic',
      };

      final client = MockClient((request) async {
        if (request.url.path.contains('/vin/19XFL1H87RE004388')) {
          expect(request.headers['Authorization'], contains('Bearer '));
          return http.Response(jsonEncode(mockResponse), 200);
        }
        return http.Response('Not Found', 404);
      });

      final info = await AutoDevService.decodeVin('19XFL1H87RE004388', client: client);

      expect(info.vinValid, isTrue);
      expect(info.vin, equals('19XFL1H87RE004388'));
      expect(info.make, equals('Honda'));
      expect(info.model, equals('Civic Hatchback'));
      expect(info.year, equals(2024));
      expect(info.trim, equals('Sport Touring CVT'));
      expect(info.isEv, isFalse);
    });

    test('decodeVin identifies Electric Vehicle (EV) by engine/style specs', () async {
      final mockTesla = {
        'vin': '5YJ3E1EAXMF860954',
        'vinValid': true,
        'year': 2021,
        'make': 'Tesla',
        'model': 'Model 3',
        'trim': 'Standard Range Plus RWD',
        'style': 'Electric Motor',
        'engine': 'Electric Motor',
        'drive': 'Rear Wheel Drive',
      };

      final client = MockClient((request) async {
        return http.Response(jsonEncode(mockTesla), 200);
      });

      final info = await AutoDevService.decodeVin('5YJ3E1EAXMF860954', client: client);

      expect(info.vinValid, isTrue);
      expect(info.make, equals('Tesla'));
      expect(info.model, equals('Model 3'));
      expect(info.year, equals(2021));
      expect(info.isEv, isTrue);
    });

    test('getVehiclePhoto retrieves retail vehicle photo from auto.dev', () async {
      final mockPhotoData = {
        'data': {
          'retail': [
            'https://api.auto.dev/photos/retail/19XFL1H87RE004388-1.jpg',
            'https://api.auto.dev/photos/retail/19XFL1H87RE004388-2.jpg',
          ],
          'wholesale': [],
        }
      };

      final client = MockClient((request) async {
        if (request.url.path.contains('/photos/19XFL1H87RE004388')) {
          return http.Response(jsonEncode(mockPhotoData), 200);
        }
        return http.Response('Not Found', 404);
      });

      final photo = await AutoDevService.getVehiclePhoto('19XFL1H87RE004388', client: client);

      expect(photo, equals('https://api.auto.dev/photos/retail/19XFL1H87RE004388-1.jpg'));
    });

    test('decodeAndFetchDetails populates vehicle info and auto.dev photo', () async {
      final mockVinData = {
        'vin': '3GNKDCRJ1RS284858',
        'vinValid': true,
        'year': 2024,
        'make': 'Chevrolet',
        'model': 'Blazer EV',
        'engine': 'Electric Motor',
      };

      final mockPhotoData = {
        'data': {
          'retail': [
            'https://api.auto.dev/photos/retail/3GNKDCRJ1RS284858-1.jpg',
          ],
        }
      };

      final client = MockClient((request) async {
        if (request.url.path.contains('/vin/')) {
          return http.Response(jsonEncode(mockVinData), 200);
        }
        if (request.url.path.contains('/photos/')) {
          return http.Response(jsonEncode(mockPhotoData), 200);
        }
        return http.Response('Not Found', 404);
      });

      final details = await AutoDevService.decodeAndFetchDetails('3GNKDCRJ1RS284858', client: client);

      expect(details.make, equals('Chevrolet'));
      expect(details.model, equals('Blazer EV'));
      expect(details.year, equals(2024));
      expect(details.isEv, isTrue);
      expect(details.photoUrl, equals('https://api.auto.dev/photos/retail/3GNKDCRJ1RS284858-1.jpg'));
    });

    test('decodeAndFetchDetails falls back to default vehicle image if auto.dev has no photos', () async {
      final mockVinData = {
        'vin': '1HGCR2F83HA000000',
        'vinValid': true,
        'year': 2017,
        'make': 'Honda',
        'model': 'Accord',
      };

      final client = MockClient((request) async {
        if (request.url.path.contains('/vin/')) {
          return http.Response(jsonEncode(mockVinData), 200);
        }
        if (request.url.path.contains('/photos/')) {
          return http.Response('{"error": "Not Found"}', 404);
        }
        return http.Response('Not Found', 404);
      });

      final details = await AutoDevService.decodeAndFetchDetails('1HGCR2F83HA000000', client: client);

      expect(details.make, equals('Honda'));
      expect(details.model, equals('Accord'));
      expect(details.year, equals(2017));
      expect(details.photoUrl, isNotNull);
      expect(details.photoUrl, contains('Accord'));
    });

    test('decodeVin handles invalid VIN response from auto.dev', () async {
      final mockErrorResponse = {
        'vin': 'ABC',
        'vinValid': false,
        'error': {
          'status': 400,
          'error': 'VIN must be 10, 11, 13, or 17 characters for data lookup',
        }
      };

      final client = MockClient((request) async {
        return http.Response(jsonEncode(mockErrorResponse), 400);
      });

      final info = await AutoDevService.decodeVin('ABC', client: client);

      expect(info.vinValid, isFalse);
      expect(info.errorMessage, contains('VIN must be'));
    });
  });
}
