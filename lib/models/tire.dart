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
}
