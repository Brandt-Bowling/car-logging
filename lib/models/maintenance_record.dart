class MaintenanceRecord {
  final String id;
  final String carId;
  final String title;
  final DateTime date;
  final int odometer;
  final String? description;
  final String? receiptUrl;
  final double? cost;
  final String? driveFileId;

  MaintenanceRecord({
    required this.id,
    required this.carId,
    required this.title,
    required this.date,
    required this.odometer,
    this.description,
    this.receiptUrl,
    this.cost,
    this.driveFileId,
  });

  MaintenanceRecord copyWith({
    String? id,
    String? carId,
    String? title,
    DateTime? date,
    int? odometer,
    String? description,
    String? receiptUrl,
    double? cost,
    String? driveFileId,
  }) {
    return MaintenanceRecord(
      id: id ?? this.id,
      carId: carId ?? this.carId,
      title: title ?? this.title,
      date: date ?? this.date,
      odometer: odometer ?? this.odometer,
      description: description ?? this.description,
      receiptUrl: receiptUrl ?? this.receiptUrl,
      cost: cost ?? this.cost,
      driveFileId: driveFileId ?? this.driveFileId,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'carId': carId,
      'title': title,
      'date': date.toIso8601String(),
      'odometer': odometer,
      'description': description,
      'receiptUrl': receiptUrl,
      'cost': cost,
      'driveFileId': driveFileId,
    };
  }

  factory MaintenanceRecord.fromMap(Map<String, dynamic> map) {
    return MaintenanceRecord(
      id: map['id'] ?? '',
      carId: map['carId'] ?? '',
      title: map['title'] ?? '',
      date: map['date'] != null ? DateTime.parse(map['date']) : DateTime.now(),
      odometer: map['odometer'] ?? 0,
      description: map['description'],
      receiptUrl: map['receiptUrl'],
      cost: map['cost'] != null ? (map['cost'] as num).toDouble() : null,
      driveFileId: map['driveFileId'],
    );
  }
}
