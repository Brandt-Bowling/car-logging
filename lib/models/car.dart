class Car {
  final String id;
  final String make;
  final String model;
  final int year;
  final String? imageUrl;
  final String? licensePlate;
  final String? vin;
  final int? odometer;
  final bool isEv;

  Car({
    required this.id,
    required this.make,
    required this.model,
    required this.year,
    this.imageUrl,
    this.licensePlate,
    this.vin,
    this.odometer,
    this.isEv = false,
  });

  Car copyWith({
    String? id,
    String? make,
    String? model,
    int? year,
    String? imageUrl,
    String? licensePlate,
    String? vin,
    int? odometer,
    bool? isEv,
  }) {
    return Car(
      id: id ?? this.id,
      make: make ?? this.make,
      model: model ?? this.model,
      year: year ?? this.year,
      imageUrl: imageUrl ?? this.imageUrl,
      licensePlate: licensePlate ?? this.licensePlate,
      vin: vin ?? this.vin,
      odometer: odometer ?? this.odometer,
      isEv: isEv ?? this.isEv,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'make': make,
      'model': model,
      'year': year,
      'imageUrl': imageUrl,
      'licensePlate': licensePlate,
      'vin': vin,
      'odometer': odometer,
      'isEv': isEv,
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
      odometer: map['odometer'],
      isEv: map['isEv'] ?? false,
    );
  }
}
