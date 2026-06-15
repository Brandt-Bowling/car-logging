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

  Car copyWith({
    String? id,
    String? make,
    String? model,
    int? year,
    String? imageUrl,
    String? licensePlate,
    String? vin,
    int? odometer,
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
    );
  }
}
