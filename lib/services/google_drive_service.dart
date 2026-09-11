import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:googleapis/drive/v3.dart' as drive;
import 'package:extension_google_sign_in_as_googleapis_auth/extension_google_sign_in_as_googleapis_auth.dart';

class GoogleDriveSignInException implements Exception {
  final String message;
  final String? code;
  final String? originalError;
  final bool isConfigurationError;
  final List<String> troubleshootingSteps;

  GoogleDriveSignInException({
    required this.message,
    this.code,
    this.originalError,
    this.isConfigurationError = false,
    this.troubleshootingSteps = const [],
  });

  @override
  String toString() => message;

  static GoogleDriveSignInException fromError(dynamic error) {
    if (error is GoogleDriveSignInException) return error;

    final str = error.toString();
    // Check for ApiException: 10 (DEVELOPER_ERROR)
    if (str.contains('10') || str.contains('DEVELOPER_ERROR')) {
      return GoogleDriveSignInException(
        message: 'Google Cloud configuration error (ApiException 10).',
        code: '10',
        originalError: str,
        isConfigurationError: true,
        troubleshootingSteps: [
          'Add your Google account to "Test users" in Google Cloud Console under APIs & Services > OAuth consent screen.',
          'Verify that "https://www.googleapis.com/auth/drive.readonly" is added in "Scopes for Google APIs".',
          'Ensure the OAuth consent screen user support email is filled out.',
        ],
      );
    }

    // Check for ApiException: 12500 (SIGN_IN_FAILED)
    if (str.contains('12500') || str.contains('SIGN_IN_FAILED')) {
      return GoogleDriveSignInException(
        message: 'Google Sign-In failed (ApiException 12500).',
        code: '12500',
        originalError: str,
        isConfigurationError: true,
        troubleshootingSteps: [
          'Verify that Google Play Services on your device is updated.',
          'Ensure your Google account is added to "Test users" in Google Cloud Console.',
          'Ensure the OAuth consent screen is configured in Google Cloud Console.',
        ],
      );
    }

    if (str.contains('network_error') || str.contains('7')) {
      return GoogleDriveSignInException(
        message: 'Network error connecting to Google. Please check your internet connection.',
        code: 'NETWORK_ERROR',
        originalError: str,
        troubleshootingSteps: [
          'Check your Wi-Fi or mobile data connection and try again.',
        ],
      );
    }

    if (str.contains('sign_in_canceled') || str.contains('12501')) {
      return GoogleDriveSignInException(
        message: 'Sign-in was canceled.',
        code: 'CANCELED',
        originalError: str,
      );
    }

    return GoogleDriveSignInException(
      message: 'Sign-in failed: $str',
      originalError: str,
      troubleshootingSteps: [
        'Ensure your Google account is added as a Test User in Google Cloud Console.',
        'Verify your device has internet access and try again.',
      ],
    );
  }
}

class GoogleDriveService {
  static const List<String> _scopes = <String>[
    'email',
    drive.DriveApi.driveReadonlyScope,
  ];

  static final GoogleSignIn _googleSignIn = GoogleSignIn(
    scopes: _scopes,
  );

  static GoogleSignInAccount? get currentUser => _googleSignIn.currentUser;

  static Stream<GoogleSignInAccount?> get onCurrentUserChanged => _googleSignIn.onCurrentUserChanged;

  /// Attempts silent sign-in only — no UI prompt. Use on page init.
  static Future<GoogleSignInAccount?> signInSilently() async {
    try {
      return await _googleSignIn.signInSilently();
    } catch (e) {
      print('Silent sign-in failed: $e');
      return null;
    }
  }

  /// Interactive sign-in — shows the Google sign-in prompt and validates Drive scopes.
  /// Use only when the user explicitly requests sign-in.
  static Future<GoogleSignInAccount?> signIn() async {
    try {
      GoogleSignInAccount? account = _googleSignIn.currentUser;
      account ??= await _googleSignIn.signIn();

      if (account == null) {
        return null;
      }

      // On web, scope authorization is separate and canAccessScopes is supported.
      // On mobile (Android/iOS), signing in with scopes already authorizes them.
      if (kIsWeb) {
        try {
          final hasScope = await _googleSignIn.canAccessScopes([
            drive.DriveApi.driveReadonlyScope,
          ]);

          if (!hasScope) {
            final granted = await _googleSignIn.requestScopes([
              drive.DriveApi.driveReadonlyScope,
            ]);
            if (!granted) {
              throw GoogleDriveSignInException(
                message: 'Google Drive access was not granted.',
                troubleshootingSteps: [
                  'Please grant permissions when prompted by Google to allow reading maintenance receipts.',
                ],
              );
            }
          }
        } on UnimplementedError {
          // Ignored on platforms where canAccessScopes is not implemented.
        } on PlatformException catch (e) {
          if (e.code == 'Unimplemented' ||
              e.message?.toLowerCase().contains('not implemented') == true) {
            // Ignored on platforms where canAccessScopes is not implemented.
          } else {
            rethrow;
          }
        }
      }

      return account;
    } on PlatformException catch (e) {
      final parsed = GoogleDriveSignInException.fromError(e);
      print('Google Drive Sign-In PlatformException: $e (parsed: ${parsed.message})');
      throw parsed;
    } catch (e) {
      print('Google Drive Sign-In Error: $e');
      if (e is GoogleDriveSignInException) rethrow;
      throw GoogleDriveSignInException.fromError(e);
    }
  }

  static Future<void> signOut() async {
    await _googleSignIn.signOut();
  }

  static Future<bool> isSignedIn() async {
    return _googleSignIn.isSignedIn();
  }

  static Future<drive.DriveApi> getDriveApi() async {
    final client = await _googleSignIn.authenticatedClient();
    if (client == null) {
      throw Exception('User is not authenticated with Google.');
    }
    return drive.DriveApi(client);
  }

  // Lists folders inside a specific parent folder (defaults to 'root')
  // Or filters globally by name if searchName is provided.
  static Future<List<drive.File>> listFolders({
    String parentId = 'root',
    String? searchName,
  }) async {
    try {
      final api = await getDriveApi();
      
      String query;
      if (searchName != null && searchName.trim().isNotEmpty) {
        final escapedName = searchName.replaceAll("'", "\\'");
        query = "mimeType = 'application/vnd.google-apps.folder' and name contains '$escapedName' and trashed = false";
      } else {
        query = "mimeType = 'application/vnd.google-apps.folder' and '$parentId' in parents and trashed = false";
      }

      final list = await api.files.list(
        q: query,
        spaces: 'drive',
        pageSize: 1000,
        $fields: 'files(id, name)',
      );
      return list.files ?? [];
    } catch (e) {
      print('Error listing Google Drive folders (parentId: $parentId, search: $searchName): $e');
      return [];
    }
  }

  // Fetches folder metadata by id
  static Future<drive.File?> getFolderInfo(String folderId) async {
    try {
      final api = await getDriveApi();
      final file = await api.files.get(
        folderId,
        $fields: 'id, name, parents',
      ) as drive.File;
      return file;
    } catch (e) {
      print('Error getting folder info ($folderId): $e');
      return null;
    }
  }

  // Lists all files (images, PDFs, documents) in a specific folder
  static Future<List<drive.File>> listFiles(
    String folderId, {
    bool recursive = false,
  }) async {
    try {
      final api = await getDriveApi();
      // Exclude subfolders from the file list so only document/media files are returned
      final query =
          "'$folderId' in parents and trashed = false and mimeType != 'application/vnd.google-apps.folder'";
      final list = await api.files.list(
        q: query,
        spaces: 'drive',
        pageSize: 100,
        orderBy: 'createdTime desc',
        $fields: 'files(id, name, mimeType, createdTime, size, thumbnailLink)',
      );
      final files = List<drive.File>.from(list.files ?? []);

      if (recursive) {
        final subfolders = await listFolders(parentId: folderId);
        for (final sub in subfolders) {
          if (sub.id != null) {
            final childFiles = await listFiles(sub.id!, recursive: true);
            files.addAll(childFiles);
          }
        }
      }

      return files;
    } catch (e) {
      print('Error listing files in folder ($folderId): $e');
      return [];
    }
  }

  // Downloads the file bytes for a specific file in Google Drive
  static Future<Uint8List> downloadFile(String fileId) async {
    try {
      final api = await getDriveApi();
      final drive.Media media = await api.files.get(
        fileId,
        downloadOptions: drive.DownloadOptions.fullMedia,
      ) as drive.Media;

      final List<int> bytes = [];
      await for (final List<int> chunk in media.stream) {
        bytes.addAll(chunk);
      }
      return Uint8List.fromList(bytes);
    } catch (e) {
      print('Error downloading Google Drive file ($fileId): $e');
      rethrow;
    }
  }
}
