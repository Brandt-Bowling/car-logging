class Car {
  final String id;
  final String make;
  final String model;
  final int year;
  final String? imageUrl;
  final String? localImagePath;
  final String? licensePlate;
  final String? vin;
  final int? odometer;
  final bool isEv;
  final bool isArchived;

  final String? trim;
  final String? bodyStyle;
  final int? tireRotationInterval;

  Car({
    required this.id,
    required this.make,
    required this.model,
    required this.year,
    this.imageUrl,
    this.localImagePath,
    this.licensePlate,
    this.vin,
    this.odometer,
    this.isEv = false,
    this.isArchived = false,
    this.trim,
    this.bodyStyle,
    this.tireRotationInterval,
  });

  int get effectiveTireRotationInterval => tireRotationInterval ?? 7500;

  Car copyWith({
    String? id,
    String? make,
    String? model,
    int? year,
    String? imageUrl,
    String? localImagePath,
    String? licensePlate,
    String? vin,
    int? odometer,
    bool? isEv,
    bool? isArchived,
    String? trim,
    String? bodyStyle,
    int? tireRotationInterval,
  }) {
    return Car(
      id: id ?? this.id,
      make: make ?? this.make,
      model: model ?? this.model,
      year: year ?? this.year,
      imageUrl: imageUrl ?? this.imageUrl,
      localImagePath: localImagePath ?? this.localImagePath,
      licensePlate: licensePlate ?? this.licensePlate,
      vin: vin ?? this.vin,
      odometer: odometer ?? this.odometer,
      isEv: isEv ?? this.isEv,
      isArchived: isArchived ?? this.isArchived,
      trim: trim ?? this.trim,
      bodyStyle: bodyStyle ?? this.bodyStyle,
      tireRotationInterval: tireRotationInterval ?? this.tireRotationInterval,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'make': make,
      'model': model,
      'year': year,
      'imageUrl': imageUrl,
      'localImagePath': localImagePath,
      'licensePlate': licensePlate,
      'vin': vin,
      'odometer': odometer,
      'isEv': isEv,
      'isArchived': isArchived,
      'trim': trim,
      'bodyStyle': bodyStyle,
      'tireRotationInterval': tireRotationInterval,
    };
  }

  factory Car.fromMap(Map<String, dynamic> map) {
    return Car(
      id: map['id'] ?? '',
      make: map['make'] ?? '',
      model: map['model'] ?? '',
      year: map['year'] ?? 0,
      imageUrl: map['imageUrl'],
      localImagePath: map['localImagePath'],
      licensePlate: map['licensePlate'],
      vin: map['vin'],
      odometer: map['odometer'],
      isEv: map['isEv'] ?? false,
      isArchived: map['isArchived'] ?? false,
      trim: map['trim'],
      bodyStyle: map['bodyStyle'],
      tireRotationInterval: map['tireRotationInterval'],
    );
  }
}
