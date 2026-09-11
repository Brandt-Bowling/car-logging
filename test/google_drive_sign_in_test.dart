import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:car_logger/services/google_drive_service.dart';

void main() {
  group('GoogleDriveSignInException Tests', () {
    test('Correctly identifies and formats ApiException 10 (DEVELOPER_ERROR)', () {
      final platformException = PlatformException(
        code: 'sign_in_failed',
        message: 'com.google.android.gms.common.api.ApiException: 10: ',
      );

      final exception = GoogleDriveSignInException.fromError(platformException);

      expect(exception.code, equals('10'));
      expect(exception.isConfigurationError, isTrue);
      expect(exception.message, contains('ApiException 10'));
      expect(
        exception.troubleshootingSteps.any((s) => s.contains('Test users')),
        isTrue,
      );
      expect(
        exception.troubleshootingSteps.any((s) => s.contains('drive.readonly')),
        isTrue,
      );
    });

    test('Correctly identifies and formats ApiException 12500', () {
      final platformException = PlatformException(
        code: 'sign_in_failed',
        message: 'com.google.android.gms.common.api.ApiException: 12500: ',
      );

      final exception = GoogleDriveSignInException.fromError(platformException);

      expect(exception.code, equals('12500'));
      expect(exception.isConfigurationError, isTrue);
      expect(exception.message, contains('ApiException 12500'));
    });

    test('Correctly identifies network errors', () {
      final platformException = PlatformException(
        code: 'network_error',
        message: 'A network error occurred.',
      );

      final exception = GoogleDriveSignInException.fromError(platformException);

      expect(exception.code, equals('NETWORK_ERROR'));
      expect(exception.isConfigurationError, isFalse);
      expect(exception.message, contains('Network error'));
    });

    test('Correctly identifies canceled sign-in', () {
      final platformException = PlatformException(
        code: 'sign_in_canceled',
        message: 'User canceled sign in.',
      );

      final exception = GoogleDriveSignInException.fromError(platformException);

      expect(exception.code, equals('CANCELED'));
    });
  });
}
