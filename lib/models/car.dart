class Car {
  final String id;
  final String make;
  final String model;
  final int year;
  final String? imageUrl;
  final String? licensePlate;
  final String? vin;
  final int? odometer;

  Car({
    required this.id,
    required this.make,
    required this.model,
    required this.year,
    this.imageUrl,
    this.licensePlate,
    this.vin,
    this.odometer,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'make': make,
      'model': model,
      'year': year,
      'imageUrl': imageUrl,
      'licensePlate': licensePlate,
      'vin': vin,
    };
  }

  factory Car.fromMap(Map<String, dynamic> map) {
    return Car(
      id: map['id'] ?? '',
      make: map['make'] ?? '',
      model: map['model'] ?? '',
      year: map['year'] ?? 0,
      imageUrl: map['imageUrl'],
      licensePlate: map['licensePlate'],
      vin: map['vin'],
    );
  }
}
