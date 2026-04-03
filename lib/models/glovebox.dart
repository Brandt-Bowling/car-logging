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
}
