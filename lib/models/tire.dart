class Tire {
  final String id;
  final String carId;
  final String manufacturer;
  final String model;
  final DateTime dateInstalled;
  final int odometerInstalled;
  final int estimatedTreadLifeMiles;
  final DateTime? lastRotationDate;
  final int? lastRotationOdometer;
  final String? receiptUrl;

  Tire({
    required this.id,
    required this.carId,
    required this.manufacturer,
    required this.model,
    required this.dateInstalled,
    required this.odometerInstalled,
    required this.estimatedTreadLifeMiles,
    this.lastRotationDate,
    this.lastRotationOdometer,
    this.receiptUrl,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'carId': carId,
      'manufacturer': manufacturer,
      'model': model,
      'dateInstalled': dateInstalled.toIso8601String(),
      'odometerInstalled': odometerInstalled,
      'estimatedTreadLifeMiles': estimatedTreadLifeMiles,
      'lastRotationDate': lastRotationDate?.toIso8601String(),
      'lastRotationOdometer': lastRotationOdometer,
      'receiptUrl': receiptUrl,
    };
  }

  factory Tire.fromMap(Map<String, dynamic> map) {
    return Tire(
      id: map['id'] ?? '',
      carId: map['carId'] ?? '',
      manufacturer: map['manufacturer'] ?? '',
      model: map['model'] ?? '',
      dateInstalled: map['dateInstalled'] != null ? DateTime.parse(map['dateInstalled']) : DateTime.now(),
      odometerInstalled: map['odometerInstalled'] ?? 0,
      estimatedTreadLifeMiles: map['estimatedTreadLifeMiles'] ?? 0,
      lastRotationDate: map['lastRotationDate'] != null ? DateTime.parse(map['lastRotationDate']) : null,
      lastRotationOdometer: map['lastRotationOdometer'],
      receiptUrl: map['receiptUrl'],
    );
  }
}
