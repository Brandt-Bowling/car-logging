class Glovebox {
  final String carId;
  final String? registrationUrl;
  final String? insuranceUrl;
  final String? manualUrl;

  Glovebox({
    required this.carId,
    this.registrationUrl,
    this.insuranceUrl,
    this.manualUrl,
  });

  Map<String, dynamic> toMap() {
    return {
      'carId': carId,
      'registrationUrl': registrationUrl,
      'insuranceUrl': insuranceUrl,
      'manualUrl': manualUrl,
    };
  }

  factory Glovebox.fromMap(Map<String, dynamic> map) {
    return Glovebox(
      carId: map['carId'] ?? '',
      registrationUrl: map['registrationUrl'],
      insuranceUrl: map['insuranceUrl'],
      manualUrl: map['manualUrl'],
    );
  }
}
