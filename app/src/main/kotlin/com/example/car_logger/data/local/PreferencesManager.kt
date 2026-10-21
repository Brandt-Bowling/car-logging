package com.example.car_logger.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "car_logger_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_GARAGE_NAME = stringPreferencesKey("garage_name")
        val KEY_SYNC_FOLDER_ID = stringPreferencesKey("sync_folder_id")
        val KEY_SYNC_FOLDER_NAME = stringPreferencesKey("sync_folder_name")
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_AUTO_DEV_API_KEY = stringPreferencesKey("auto_dev_api_key")
        val KEY_IMPORTED_FILE_IDS = stringSetPreferencesKey("imported_file_ids")
        val KEY_MIGRATION_V1_COMPLETE = booleanPreferencesKey("migration_from_flutter_v1_complete")
        const val DEFAULT_GARAGE_NAME = "My Garage"
    }

    val garageNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GARAGE_NAME] ?: DEFAULT_GARAGE_NAME
    }

    val syncFolderIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYNC_FOLDER_ID]
    }

    val syncFolderNameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYNC_FOLDER_NAME]
    }

    val geminiApiKeyFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY]
    }

    val autoDevApiKeyFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_DEV_API_KEY]
    }

    val importedFileIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_IMPORTED_FILE_IDS] ?: emptySet()
    }

    val isMigrationCompleteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MIGRATION_V1_COMPLETE] ?: false
    }

    suspend fun getGarageName(): String = garageNameFlow.first()
    suspend fun getSyncFolderId(): String? = syncFolderIdFlow.first()
    suspend fun getSyncFolderName(): String? = syncFolderNameFlow.first()
    suspend fun getGeminiApiKey(): String? = geminiApiKeyFlow.first()
    suspend fun getAutoDevApiKey(): String? = autoDevApiKeyFlow.first()
    suspend fun getImportedFileIds(): Set<String> = importedFileIdsFlow.first()
    suspend fun isMigrationComplete(): Boolean = isMigrationCompleteFlow.first()

    suspend fun setGarageName(name: String) {
        val trimmed = name.trim()
        context.dataStore.edit { prefs ->
            if (trimmed.isEmpty() || trimmed == DEFAULT_GARAGE_NAME) {
                prefs.remove(KEY_GARAGE_NAME)
            } else {
                prefs[KEY_GARAGE_NAME] = trimmed
            }
        }
    }

    suspend fun setSyncFolder(folderId: String?, folderName: String?) {
        context.dataStore.edit { prefs ->
            if (folderId == null) {
                prefs.remove(KEY_SYNC_FOLDER_ID)
                prefs.remove(KEY_SYNC_FOLDER_NAME)
            } else {
                prefs[KEY_SYNC_FOLDER_ID] = folderId
                folderName?.let { prefs[KEY_SYNC_FOLDER_NAME] = it }
            }
        }
    }

    suspend fun setGeminiApiKey(key: String?) {
        context.dataStore.edit { prefs ->
            if (key.isNullOrBlank()) {
                prefs.remove(KEY_GEMINI_API_KEY)
            } else {
                prefs[KEY_GEMINI_API_KEY] = key.trim()
            }
        }
    }

    suspend fun setAutoDevApiKey(key: String?) {
        context.dataStore.edit { prefs ->
            if (key.isNullOrBlank()) {
                prefs.remove(KEY_AUTO_DEV_API_KEY)
            } else {
                prefs[KEY_AUTO_DEV_API_KEY] = key.trim()
            }
        }
    }

    suspend fun markFileAsImported(fileId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_IMPORTED_FILE_IDS]?.toMutableSet() ?: mutableSetOf()
            current.add(fileId)
            prefs[KEY_IMPORTED_FILE_IDS] = current
        }
    }

    suspend fun unmarkFileAsImported(fileId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_IMPORTED_FILE_IDS]?.toMutableSet() ?: mutableSetOf()
            if (current.remove(fileId)) {
                prefs[KEY_IMPORTED_FILE_IDS] = current
            }
        }
    }

    suspend fun setImportedFileIds(fileIds: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IMPORTED_FILE_IDS] = fileIds
        }
    }

    suspend fun setMigrationComplete(complete: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MIGRATION_V1_COMPLETE] = complete
        }
    }
}
