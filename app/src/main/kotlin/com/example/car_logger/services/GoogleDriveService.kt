package com.example.car_logger.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Collections

data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long? = null,
    val webViewLink: String? = null,
    val createdTime: String? = null,
    val isFolder: Boolean = false
)

class GoogleDriveService(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveService"
        val SCOPES = listOf(
            DriveScopes.DRIVE_READONLY,
            "email"
        )
    }

    private val _currentUser = MutableStateFlow<GoogleSignInAccount?>(null)
    val currentUser: StateFlow<GoogleSignInAccount?> = _currentUser.asStateFlow()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    init {
        _currentUser.value = GoogleSignIn.getLastSignedInAccount(context)
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun handleSignInResult(account: GoogleSignInAccount?) {
        _currentUser.value = account
    }

    suspend fun signOut(): Unit = withContext(Dispatchers.IO) {
        try {
            googleSignInClient.signOut()
            _currentUser.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error: ${e.message}", e)
        }
    }

    suspend fun checkSilentSignIn(): GoogleSignInAccount? = withContext(Dispatchers.IO) {
        try {
            val account = googleSignInClient.silentSignIn().result
            _currentUser.value = account
            account
        } catch (e: Exception) {
            Log.d(TAG, "Silent sign in not available: ${e.message}")
            null
        }
    }

    private fun getDriveClient(): Drive? {
        val account = _currentUser.value ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_READONLY)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Garage Journal")
            .build()
    }

    suspend fun listFolders(parentId: String = "root", searchName: String? = null): List<DriveFileItem> = withContext(Dispatchers.IO) {
        val drive = getDriveClient() ?: return@withContext emptyList()
        try {
            val query = if (!searchName.isNullOrBlank()) {
                val escaped = searchName.replace("'", "\\'")
                "mimeType = 'application/vnd.google-apps.folder' and name contains '$escaped' and trashed = false"
            } else {
                "mimeType = 'application/vnd.google-apps.folder' and '$parentId' in parents and trashed = false"
            }

            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setPageSize(100)
                .setFields("files(id, name, mimeType)")
                .execute()

            result.files?.map { file ->
                DriveFileItem(
                    id = file.id,
                    name = file.name,
                    mimeType = file.mimeType,
                    isFolder = true
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "listFolders error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun listFiles(folderId: String): List<DriveFileItem> = withContext(Dispatchers.IO) {
        val drive = getDriveClient() ?: return@withContext emptyList()
        try {
            val query = "'$folderId' in parents and mimeType != 'application/vnd.google-apps.folder' and trashed = false"
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setPageSize(200)
                .setFields("files(id, name, mimeType, size, webViewLink, createdTime)")
                .execute()

            result.files?.map { file ->
                DriveFileItem(
                    id = file.id,
                    name = file.name,
                    mimeType = file.mimeType,
                    size = file.getSize(),
                    webViewLink = file.webViewLink,
                    createdTime = file.createdTime?.toStringRfc3339(),
                    isFolder = false
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "listFiles error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun downloadFileBytes(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        val drive = getDriveClient() ?: return@withContext null
        try {
            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "downloadFileBytes error: ${e.message}", e)
            null
        }
    }
}
