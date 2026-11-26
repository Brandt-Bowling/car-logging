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

open class GoogleDriveService private constructor(
    private val context: Context?,
    initialUser: GoogleSignInAccount?
) {

    constructor(context: Context) : this(
        context = context.applicationContext ?: context,
        initialUser = GoogleSignIn.getLastSignedInAccount(context.applicationContext ?: context)
    )

    internal constructor(
        initialUser: GoogleSignInAccount? = null
    ) : this(
        context = null,
        initialUser = initialUser
    )

    companion object {
        private const val TAG = "GoogleDriveService"
        val SCOPES = listOf(
            DriveScopes.DRIVE_READONLY,
            "email"
        )
    }

    private val _currentUser = MutableStateFlow<GoogleSignInAccount?>(initialUser)
    open val currentUser: StateFlow<GoogleSignInAccount?> = _currentUser.asStateFlow()

    private val googleSignInClient: GoogleSignInClient? by lazy {
        val appContext = context ?: return@lazy null
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
            .build()
        GoogleSignIn.getClient(appContext, gso)
    }

    open fun getSignInIntent(): Intent {
        val client = checkNotNull(googleSignInClient) {
            "GoogleDriveService requires an Android Context to create a sign-in Intent"
        }
        return client.signInIntent
    }

    open fun handleSignInResult(account: GoogleSignInAccount?) {
        _currentUser.value = account
    }

    open suspend fun signOut(): Unit = withContext(Dispatchers.IO) {
        try {
            googleSignInClient?.signOut()
            _currentUser.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error: ${e.message}", e)
        }
    }

    open suspend fun checkSilentSignIn(): GoogleSignInAccount? = withContext(Dispatchers.IO) {
        val client = googleSignInClient ?: return@withContext null
        try {
            val account = client.silentSignIn().result
            _currentUser.value = account
            account
        } catch (e: Exception) {
            Log.d(TAG, "Silent sign in not available: ${e.message}")
            null
        }
    }

    private fun getDriveClient(): Drive? {
        val appContext = context ?: return null
        val account = _currentUser.value ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            appContext,
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

    open suspend fun listFolders(
        parentId: String = "root",
        searchName: String? = null
    ): List<DriveFileItem> = withContext(Dispatchers.IO) {
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

    open suspend fun listFiles(folderId: String): List<DriveFileItem> = withContext(Dispatchers.IO) {
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

    open suspend fun downloadFileBytes(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
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
