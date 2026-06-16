import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/car.dart';
import '../models/maintenance_record.dart';

class StorageService {
  static SharedPreferences? _prefs;

  static const String _keyCars = 'car_logger_cars';
  static const String _keyRecordsPrefix = 'car_logger_records_';
  static const String _keyImportedFiles = 'car_logger_imported_files';
  static const String _keySyncFolderId = 'car_logger_sync_folder_id';
  static const String _keySyncFolderName = 'car_logger_sync_folder_name';
  static const String _keyGeminiApiKey = 'car_logger_gemini_api_key';

  static Future<void> init() async {
    _prefs ??= await SharedPreferences.getInstance();
  }

  // Cars
  static List<Car> getCars() {
    final data = _prefs?.getString(_keyCars);
    if (data == null) {
      // Default initial mock cars so the user doesn't see an empty screen
      final defaults = [
        Car(
          id: '1',
          make: 'Toyota',
          model: 'Camry',
          year: 2020,
          licensePlate: 'ABC-1234',
        ),
        Car(
          id: '2',
          make: 'Honda',
          model: 'Civic',
          year: 2018,
          licensePlate: 'XYZ-9876',
        ),
      ];
      saveCars(defaults);
      return defaults;
    }
    final List decoded = jsonDecode(data);
    return decoded.map((item) => Car.fromMap(item)).toList();
  }

  static Future<void> saveCars(List<Car> cars) async {
    final data = jsonEncode(cars.map((car) => car.toMap()).toList());
    await _prefs?.setString(_keyCars, data);
  }

  static Future<void> addCar(Car car) async {
    final cars = getCars();
    cars.add(car);
    await saveCars(cars);
  }

  // Maintenance Records
  static List<MaintenanceRecord> getMaintenanceRecords(String carId) {
    final data = _prefs?.getString('$_keyRecordsPrefix$carId');
    if (data == null) {
      // Return default mock record for the Toyota Camry for visual continuity
      if (carId == '1') {
        final defaults = [
          MaintenanceRecord(
            id: 'mock_1',
            carId: '1',
            title: 'Oil Change',
            date: DateTime.now().subtract(const Duration(days: 30)),
            odometer: 45000,
            cost: 65.0,
            description: 'Regular maintenance oil and filter change.',
          ),
        ];
        saveMaintenanceRecords('1', defaults);
        return defaults;
      }
      return [];
    }
    final List decoded = jsonDecode(data);
    return decoded.map((item) => MaintenanceRecord.fromMap(item)).toList();
  }

  static Future<void> saveMaintenanceRecords(String carId, List<MaintenanceRecord> records) async {
    final data = jsonEncode(records.map((r) => r.toMap()).toList());
    await _prefs?.setString('$_keyRecordsPrefix$carId', data);
  }

  static Future<void> addMaintenanceRecord(MaintenanceRecord record) async {
    final records = getMaintenanceRecords(record.carId);
    // Overwrite or update if ID already exists, otherwise add
    final index = records.indexWhere((r) => r.id == record.id);
    if (index != -1) {
      records[index] = record;
    } else {
      records.add(record);
    }
    await saveMaintenanceRecords(record.carId, records);
  }

  // Google Drive Imported Files Track
  static Set<String> getImportedFileIds() {
    final list = _prefs?.getStringList(_keyImportedFiles) ?? [];
    return list.toSet();
  }

  static Future<void> markFileAsImported(String fileId) async {
    final fileIds = getImportedFileIds();
    fileIds.add(fileId);
    await _prefs?.setStringList(_keyImportedFiles, fileIds.toList());
  }

  // Google Drive Settings
  static String? getSyncFolderId() {
    return _prefs?.getString(_keySyncFolderId);
  }

  static Future<void> saveSyncFolderId(String? folderId) async {
    if (folderId == null) {
      await _prefs?.remove(_keySyncFolderId);
    } else {
      await _prefs?.setString(_keySyncFolderId, folderId);
    }
  }

  static String? getSyncFolderName() {
    return _prefs?.getString(_keySyncFolderName);
  }

  static Future<void> saveSyncFolderName(String? folderName) async {
    if (folderName == null) {
      await _prefs?.remove(_keySyncFolderName);
    } else {
      await _prefs?.setString(_keySyncFolderName, folderName);
    }
  }

  // Gemini API Key
  static String? getGeminiApiKey() {
    return _prefs?.getString(_keyGeminiApiKey);
  }

  static Future<void> saveGeminiApiKey(String? key) async {
    if (key == null || key.isEmpty) {
      await _prefs?.remove(_keyGeminiApiKey);
    } else {
      await _prefs?.setString(_keyGeminiApiKey, key);
    }
  }
}
