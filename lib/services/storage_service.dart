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
  static const String _keyAutoDevApiKey = 'car_logger_auto_dev_api_key';
  static const String _keyGarageName = 'car_logger_garage_name';

  static const String defaultGarageName = 'My Garage';

  static Future<void> init({bool force = false}) async {
    if (force || _prefs == null) {
      _prefs = await SharedPreferences.getInstance();
    }
  }

  // Cars
  static List<Car> getCars() {
    final data = _prefs?.getString(_keyCars);
    if (data == null) {
      // Default initial cars so the user doesn't see an empty screen
      final defaults = [
        Car(
          id: '1',
          make: 'Chevrolet',
          model: 'Sonic',
          year: 2013,
          imageUrl:
              'https://upload.wikimedia.org/wikipedia/commons/f/f9/2012_Chevrolet_Sonic_1LT_hatchback_--_10-19-2011_front_1.jpg',
        ),
        Car(
          id: '2',
          make: 'Tesla',
          model: 'Model 3',
          year: 2018,
          imageUrl:
              'https://api.auto.dev/photos/retail/5YJ3E1EAXMF860954-1.jpg',
          isEv: true,
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

  static Future<void> updateCar(Car car) async {
    final cars = getCars();
    final index = cars.indexWhere((c) => c.id == car.id);
    if (index != -1) {
      cars[index] = car;
      await saveCars(cars);
    }
  }

  static Future<void> toggleArchiveCar(String carId) async {
    final cars = getCars();
    final index = cars.indexWhere((c) => c.id == carId);
    if (index != -1) {
      cars[index] = cars[index].copyWith(isArchived: !cars[index].isArchived);
      await saveCars(cars);
    }
  }

  static Future<void> deleteCar(String carId) async {
    final cars = getCars();
    cars.removeWhere((car) => car.id == carId);
    await saveCars(cars);
    // Clean up associated maintenance records and unmark imported files
    final records = getMaintenanceRecords(carId);
    for (final r in records) {
      if (r.driveFileId != null) {
        await unmarkFileAsImported(r.driveFileId!);
      }
    }
    await _prefs?.remove('$_keyRecordsPrefix$carId');
  }

  // Maintenance Records
  static List<MaintenanceRecord> getMaintenanceRecords(String carId) {
    final data = _prefs?.getString('$_keyRecordsPrefix$carId');
    if (data == null) {
      // Return default mock record for the Chevy Sonic for visual continuity
      if (carId == '1') {
        final now = DateTime.now();
        final defaults = [
          MaintenanceRecord(
            id: 'mock_1',
            carId: '1',
            title: 'Oil Change & Filter',
            date: now.subtract(const Duration(days: 30)),
            odometer: 45000,
            cost: 65.0,
            description: 'Regular synthetic oil and oil filter replacement.',
          ),
          MaintenanceRecord(
            id: 'mock_2',
            carId: '1',
            title: 'Brake Pad Replacement & Rotor Resurface',
            date: DateTime(now.year - 1, 9, 15),
            odometer: 38200,
            cost: 320.0,
            description: 'Front ceramic brake pads installed and rotors resurfaced.',
          ),
          MaintenanceRecord(
            id: 'mock_3',
            carId: '1',
            title: 'Tire Rotation & Wheel Balance',
            date: DateTime(now.year - 1, 4, 10),
            odometer: 33500,
            cost: 45.0,
            description: 'Rotated all 4 tires and rebalanced front wheels.',
          ),
          MaintenanceRecord(
            id: 'mock_4',
            carId: '1',
            title: 'Battery Replacement',
            date: DateTime(now.year - 2, 11, 20),
            odometer: 28000,
            cost: 165.0,
            description: 'Installed new AGM 12V battery with 3-year warranty.',
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

  static Future<void> updateMaintenanceRecord(MaintenanceRecord record) async {
    await addMaintenanceRecord(record);
  }

  static Future<void> moveMaintenanceRecord({
    required MaintenanceRecord record,
    required String fromCarId,
    required String toCarId,
  }) async {
    if (fromCarId == toCarId) {
      await addMaintenanceRecord(record);
      return;
    }
    // Remove from source car without unmarking imported file
    await deleteMaintenanceRecord(fromCarId, record.id, keepImported: true);
    // Add to target car
    final updatedRecord = record.copyWith(carId: toCarId);
    await addMaintenanceRecord(updatedRecord);
  }

  static Future<void> deleteMaintenanceRecord(
    String carId,
    String recordId, {
    bool keepImported = false,
  }) async {
    final records = getMaintenanceRecords(carId);
    final idx = records.indexWhere((r) => r.id == recordId);
    if (idx != -1) {
      final record = records[idx];
      if (!keepImported && record.driveFileId != null) {
        await unmarkFileAsImported(record.driveFileId!);
      }
      records.removeAt(idx);
      await saveMaintenanceRecords(carId, records);
    }
  }

  /// Locates the car and maintenance record associated with a Google Drive file ID
  static ({Car car, MaintenanceRecord record})? findRecordByDriveFileId(String driveFileId) {
    final cars = getCars();
    for (final car in cars) {
      final records = getMaintenanceRecords(car.id);
      for (final record in records) {
        if (record.driveFileId == driveFileId) {
          return (car: car, record: record);
        }
      }
    }
    return null;
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

  static Future<void> unmarkFileAsImported(String fileId) async {
    final fileIds = getImportedFileIds();
    if (fileIds.remove(fileId)) {
      await _prefs?.setStringList(_keyImportedFiles, fileIds.toList());
    }
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

  // Auto.dev API Key
  static String? getAutoDevApiKey() {
    return _prefs?.getString(_keyAutoDevApiKey);
  }

  static Future<void> saveAutoDevApiKey(String? key) async {
    if (key == null || key.isEmpty) {
      await _prefs?.remove(_keyAutoDevApiKey);
    } else {
      await _prefs?.setString(_keyAutoDevApiKey, key);
    }
  }

  // Garage Name
  static String getGarageName() {
    return _prefs?.getString(_keyGarageName) ?? defaultGarageName;
  }

  static Future<void> saveGarageName(String name) async {
    final trimmed = name.trim();
    if (trimmed.isEmpty || trimmed == defaultGarageName) {
      await _prefs?.remove(_keyGarageName);
    } else {
      await _prefs?.setString(_keyGarageName, trimmed);
    }
  }
}
