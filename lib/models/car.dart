class Car {
  final String id;
  final String make;
  final String model;
  final int year;
  final String? imageUrl;
  final String? licensePlate;
  final String? vin;

  Car({
    required this.id,
    required this.make,
    required this.model,
    required this.year,
    this.imageUrl,
    this.licensePlate,
    this.vin,
  });
}
