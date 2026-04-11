class MaintenanceRecord {
  final String id;
  final String carId;
  final String title;
  final DateTime date;
  final int odometer;
  final String? description;
  final String? receiptUrl;
  final double? cost;

  MaintenanceRecord({
    required this.id,
    required this.carId,
    required this.title,
    required this.date,
    required this.odometer,
    this.description,
    this.receiptUrl,
    this.cost,
  });
}
