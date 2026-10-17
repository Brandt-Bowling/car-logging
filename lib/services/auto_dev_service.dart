import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../data/vehicle_data.dart';
import 'storage_service.dart';

/// Parsed vehicle information from auto.dev VIN decode and photos APIs.
class AutoDevVehicleInfo {
  final String vin;
  final String? make;
  final String? model;
  final int? year;
  final String? trim;
  final String? body;
  final String? style;
  final String? engine;
  final String? drive;
  final String? transmission;
  final bool isEv;
  final String? photoUrl;
  final bool vinValid;
  final String? errorMessage;

  const AutoDevVehicleInfo({
    required this.vin,
    this.make,
    this.model,
    this.year,
    this.trim,
    this.body,
    this.style,
    this.engine,
    this.drive,
    this.transmission,
    this.isEv = false,
    this.photoUrl,
    this.vinValid = true,
    this.errorMessage,
  });

  factory AutoDevVehicleInfo.fromJson(
    Map<String, dynamic> json, {
    String? photoUrl,
  }) {
    final vin = (json['vin'] ?? '').toString().trim();
    final vehicle = json['vehicle'] is Map<String, dynamic>
        ? json['vehicle'] as Map<String, dynamic>
        : (json['vehicle'] is Map ? Map<String, dynamic>.from(json['vehicle']) : null);

    final make = (json['make'] ?? vehicle?['make'])?.toString().trim();
    final model = (json['model'] ?? vehicle?['model'])?.toString().trim();

    int? year;
    if (json['year'] is int) {
      year = json['year'] as int;
    } else if (vehicle?['year'] is int) {
      year = vehicle!['year'] as int;
    } else if (json['years'] is List && (json['years'] as List).isNotEmpty) {
      final firstYear = (json['years'] as List).first;
      year = firstYear is int ? firstYear : int.tryParse(firstYear.toString());
    } else if (json['year'] != null) {
      year = int.tryParse(json['year'].toString());
    }

    final trim = json['trim']?.toString().trim();
    final body = (json['body'] ?? json['bodyStyle'] ?? vehicle?['bodyStyle'])?.toString().trim();
    final style = json['style']?.toString().trim();
    final engine = (json['engine'] ?? vehicle?['engine'])?.toString().trim();
    final drive = (json['drive'] ?? json['drivetrain'] ?? vehicle?['drivetrain'])?.toString().trim();
    final transmission = (json['transmission'] ?? vehicle?['transmission'])?.toString().trim();
    final fuel = (json['fuel'] ?? vehicle?['fuel'])?.toString().trim().toLowerCase();

    // Determine if vehicle is an Electric Vehicle (EV)
    final bool isEvBySpecs = (engine != null && engine.toLowerCase().contains('electric')) ||
        (style != null && style.toLowerCase().contains('electric')) ||
        (fuel != null && (fuel == 'electric' || fuel.contains('electric'))) ||
        (make != null && model != null && isKnownEv(make, model));

    final vinValid = json['vinValid'] != false;
    final errorMessage = json['error'] is Map
        ? (json['error']['error'] ?? json['error']['message'])?.toString()
        : json['error']?.toString();

    return AutoDevVehicleInfo(
      vin: vin,
      make: make,
      model: model,
      year: year,
      trim: trim,
      body: body,
      style: style,
      engine: engine,
      drive: drive,
      transmission: transmission,
      isEv: isEvBySpecs,
      photoUrl: photoUrl,
      vinValid: vinValid && errorMessage == null,
      errorMessage: errorMessage,
    );
  }

  AutoDevVehicleInfo copyWith({
    String? vin,
    String? make,
    String? model,
    int? year,
    String? trim,
    String? body,
    String? style,
    String? engine,
    String? drive,
    String? transmission,
    bool? isEv,
    String? photoUrl,
    bool? vinValid,
    String? errorMessage,
  }) {
    return AutoDevVehicleInfo(
      vin: vin ?? this.vin,
      make: make ?? this.make,
      model: model ?? this.model,
      year: year ?? this.year,
      trim: trim ?? this.trim,
      body: body ?? this.body,
      style: style ?? this.style,
      engine: engine ?? this.engine,
      drive: drive ?? this.drive,
      transmission: transmission ?? this.transmission,
      isEv: isEv ?? this.isEv,
      photoUrl: photoUrl ?? this.photoUrl,
      vinValid: vinValid ?? this.vinValid,
      errorMessage: errorMessage ?? this.errorMessage,
    );
  }
}

/// Service to interact with the auto.dev Automotive APIs for VIN decoding and vehicle photos.
class AutoDevService {
  static const String defaultApiKey =
      'at_9348733ff052bcb41d54ce21e45503374ba5436be4b952de';
  static const String baseUrl = 'https://api.auto.dev';

  /// Optional client override, primarily used in tests.
  static http.Client? testClient;

  /// Resolves the API key to use for auto.dev requests.
  static String get apiKey {
    final configuredKey = StorageService.getAutoDevApiKey();
    if (configuredKey != null && configuredKey.trim().isNotEmpty) {
      return configuredKey.trim();
    }
    const envKey = String.fromEnvironment('AUTODEV_API_KEY');
    if (envKey.isNotEmpty) {
      return envKey;
    }
    return defaultApiKey;
  }

  static Map<String, String> _buildHeaders(String key) {
    return {
      'Authorization': 'Bearer $key',
      'X-API-Key': key,
      'Accept': 'application/json',
    };
  }

  /// Decodes a VIN using auto.dev `/vin/{vin}` endpoint.
  static Future<AutoDevVehicleInfo> decodeVin(
    String rawVin, {
    http.Client? client,
  }) async {
    final vin = rawVin.trim().toUpperCase();
    if (vin.isEmpty) {
      return const AutoDevVehicleInfo(
        vin: '',
        vinValid: false,
        errorMessage: 'VIN cannot be empty',
      );
    }

    final httpClient = client ?? testClient ?? http.Client();
    final url = Uri.parse('$baseUrl/vin/${Uri.encodeComponent(vin)}');

    try {
      final response = await httpClient.get(
        url,
        headers: _buildHeaders(apiKey),
      ).timeout(const Duration(seconds: 15));

      if (response.statusCode == 200) {
        final Map<String, dynamic> data = jsonDecode(response.body);
        return AutoDevVehicleInfo.fromJson(data);
      } else {
        String? errorMsg;
        try {
          final Map<String, dynamic> errData = jsonDecode(response.body);
          if (errData['error'] is Map) {
            errorMsg = (errData['error']['message'] ?? errData['error']['error'])?.toString();
          } else if (errData['error'] != null) {
            errorMsg = errData['error'].toString();
          }
        } catch (_) {}

        if (response.statusCode == 401) {
          errorMsg = errorMsg ??
              'Authentication required. Get your API key at https://auto.dev/dashboard/api-keys';
        }

        return AutoDevVehicleInfo(
          vin: vin,
          vinValid: false,
          errorMessage: errorMsg ?? 'Error ${response.statusCode}: Failed to decode VIN',
        );
      }
    } catch (e) {
      debugPrint('AutoDevService.decodeVin error: $e');
      return AutoDevVehicleInfo(
        vin: vin,
        vinValid: false,
        errorMessage: 'Failed to connect to auto.dev: $e',
      );
    } finally {
      if (client == null && testClient == null) {
        httpClient.close();
      }
    }
  }

  /// Retrieves the vehicle photo URL by VIN from auto.dev `/photos/{vin}` endpoint.
  static Future<String?> getVehiclePhoto(
    String rawVin, {
    http.Client? client,
  }) async {
    final vin = rawVin.trim().toUpperCase();
    if (vin.isEmpty) return null;

    final httpClient = client ?? testClient ?? http.Client();
    final url = Uri.parse('$baseUrl/photos/${Uri.encodeComponent(vin)}');

    try {
      final response = await httpClient.get(
        url,
        headers: _buildHeaders(apiKey),
      ).timeout(const Duration(seconds: 15));

      if (response.statusCode == 200) {
        final Map<String, dynamic> json = jsonDecode(response.body);
        final data = json['data'] is Map ? json['data'] : json;

        if (data['retail'] is List && (data['retail'] as List).isNotEmpty) {
          return (data['retail'] as List).first.toString();
        }
        if (data['wholesale'] is List && (data['wholesale'] as List).isNotEmpty) {
          return (data['wholesale'] as List).first.toString();
        }
      }
      return null;
    } catch (e) {
      debugPrint('AutoDevService.getVehiclePhoto error: $e');
      return null;
    } finally {
      if (client == null && testClient == null) {
        httpClient.close();
      }
    }
  }

  /// Decodes the VIN and fetches the vehicle photo concurrently, populating all available fields.
  static Future<AutoDevVehicleInfo> decodeAndFetchDetails(
    String rawVin, {
    http.Client? client,
  }) async {
    final vin = rawVin.trim().toUpperCase();
    if (vin.isEmpty) {
      return const AutoDevVehicleInfo(
        vin: '',
        vinValid: false,
        errorMessage: 'VIN cannot be empty',
      );
    }

    final httpClient = client ?? testClient ?? http.Client();

    try {
      final results = await Future.wait([
        decodeVin(vin, client: httpClient),
        getVehiclePhoto(vin, client: httpClient),
      ]);

      final info = results[0] as AutoDevVehicleInfo;
      var photoUrl = results[1] as String?;

      // If auto-dev photo endpoint returned null or empty, fall back to default vehicle image if known
      if ((photoUrl == null || photoUrl.isEmpty) && info.make != null && info.model != null) {
        photoUrl = getDefaultImageUrl(info.make!, info.model!);
      }

      return info.copyWith(photoUrl: photoUrl);
    } finally {
      if (client == null && testClient == null) {
        httpClient.close();
      }
    }
  }
}
