import 'dart:typed_data';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:googleapis/drive/v3.dart' as drive;
import 'package:extension_google_sign_in_as_googleapis_auth/extension_google_sign_in_as_googleapis_auth.dart';

class GoogleDriveService {
  static final GoogleSignIn _googleSignIn = GoogleSignIn(
    scopes: <String>[
      drive.DriveApi.driveReadonlyScope,
    ],
  );

  static GoogleSignInAccount? get currentUser => _googleSignIn.currentUser;

  static Stream<GoogleSignInAccount?> get onCurrentUserChanged => _googleSignIn.onCurrentUserChanged;

  static Future<GoogleSignInAccount?> signIn() async {
    try {
      // First try silent sign-in
      var account = await _googleSignIn.signInSilently();
      account ??= await _googleSignIn.signIn();
      return account;
    } catch (e) {
      print('Google Drive Sign-In Error: $e');
      rethrow;
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

  // Lists all folders in the user's Google Drive
  static Future<List<drive.File>> listFolders() async {
    try {
      final api = await getDriveApi();
      final list = await api.files.list(
        q: "mimeType = 'application/vnd.google-apps.folder' and trashed = false",
        spaces: 'drive',
        pageSize: 100,
        $fields: 'files(id, name)',
      );
      return list.files ?? [];
    } catch (e) {
      print('Error listing Google Drive folders: $e');
      return [];
    }
  }

  // Lists all files (images, PDFs, documents) in a specific folder
  static Future<List<drive.File>> listFiles(String folderId) async {
    try {
      final api = await getDriveApi();
      // Look for images, PDFs, and general document types in this folder
      final query = "'$folderId' in parents and trashed = false";
      final list = await api.files.list(
        q: query,
        spaces: 'drive',
        pageSize: 100,
        orderBy: 'createdTime desc',
        $fields: 'files(id, name, mimeType, createdTime, size, thumbnailLink)',
      );
      return list.files ?? [];
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
