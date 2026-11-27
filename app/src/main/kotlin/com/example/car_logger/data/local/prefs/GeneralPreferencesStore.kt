package com.example.car_logger.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.car_logger.data.local.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class GeneralPreferencesStore(private val dataStore: DataStore<Preferences>) {

    val garageNameFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_GARAGE_NAME] ?: PreferencesManager.DEFAULT_GARAGE_NAME
    }

    val syncFolderIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_SYNC_FOLDER_ID]
    }

    val syncFolderNameFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_SYNC_FOLDER_NAME]
    }

    val geminiApiKeyFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_GEMINI_API_KEY]
    }

    val autoDevApiKeyFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_AUTO_DEV_API_KEY]
    }

    val importedFileIdsFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_IMPORTED_FILE_IDS] ?: emptySet()
    }

    val isMigrationCompleteFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_MIGRATION_V1_COMPLETE] ?: false
    }

    val isMigrationV2CompleteFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_MIGRATION_V2_COMPLETE] ?: false
    }

    val isMigrationV3CompleteFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_MIGRATION_V3_COMPLETE] ?: false
    }

    suspend fun getGarageName(): String = garageNameFlow.first()
    suspend fun getSyncFolderId(): String? = syncFolderIdFlow.first()
    suspend fun getSyncFolderName(): String? = syncFolderNameFlow.first()
    suspend fun getGeminiApiKey(): String? = geminiApiKeyFlow.first()
    suspend fun getAutoDevApiKey(): String? = autoDevApiKeyFlow.first()
    suspend fun getImportedFileIds(): Set<String> = importedFileIdsFlow.first()
    suspend fun isMigrationComplete(): Boolean = isMigrationCompleteFlow.first()
    suspend fun isMigrationV2Complete(): Boolean = isMigrationV2CompleteFlow.first()
    suspend fun isMigrationV3Complete(): Boolean = isMigrationV3CompleteFlow.first()

    suspend fun setGarageName(name: String) {
        val trimmed = name.trim()
        dataStore.edit { prefs ->
            if (trimmed.isEmpty() || trimmed == PreferencesManager.DEFAULT_GARAGE_NAME) {
                prefs.remove(PreferencesManager.KEY_GARAGE_NAME)
            } else {
                prefs[PreferencesManager.KEY_GARAGE_NAME] = trimmed
            }
        }
    }

    suspend fun setSyncFolder(folderId: String?, folderName: String?) {
        dataStore.edit { prefs ->
            if (folderId == null) {
                prefs.remove(PreferencesManager.KEY_SYNC_FOLDER_ID)
                prefs.remove(PreferencesManager.KEY_SYNC_FOLDER_NAME)
            } else {
                prefs[PreferencesManager.KEY_SYNC_FOLDER_ID] = folderId
                folderName?.let { prefs[PreferencesManager.KEY_SYNC_FOLDER_NAME] = it }
            }
        }
    }

    suspend fun setGeminiApiKey(key: String?) {
        dataStore.edit { prefs ->
            if (key.isNullOrBlank()) {
                prefs.remove(PreferencesManager.KEY_GEMINI_API_KEY)
            } else {
                prefs[PreferencesManager.KEY_GEMINI_API_KEY] = key.trim()
            }
        }
    }

    suspend fun setAutoDevApiKey(key: String?) {
        dataStore.edit { prefs ->
            if (key.isNullOrBlank()) {
                prefs.remove(PreferencesManager.KEY_AUTO_DEV_API_KEY)
            } else {
                prefs[PreferencesManager.KEY_AUTO_DEV_API_KEY] = key.trim()
            }
        }
    }

    suspend fun markFileAsImported(fileId: String) {
        dataStore.edit { prefs ->
            val current = prefs[PreferencesManager.KEY_IMPORTED_FILE_IDS]?.toMutableSet() ?: mutableSetOf()
            current.add(fileId)
            prefs[PreferencesManager.KEY_IMPORTED_FILE_IDS] = current
        }
    }

    suspend fun unmarkFileAsImported(fileId: String) {
        dataStore.edit { prefs ->
            val current = prefs[PreferencesManager.KEY_IMPORTED_FILE_IDS]?.toMutableSet() ?: mutableSetOf()
            if (current.remove(fileId)) {
                prefs[PreferencesManager.KEY_IMPORTED_FILE_IDS] = current
            }
        }
    }

    suspend fun setImportedFileIds(fileIds: Set<String>) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_IMPORTED_FILE_IDS] = fileIds
        }
    }

    suspend fun setMigrationComplete(complete: Boolean = true) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_MIGRATION_V1_COMPLETE] = complete
        }
    }

    suspend fun setMigrationV2Complete(complete: Boolean = true) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_MIGRATION_V2_COMPLETE] = complete
        }
    }

    suspend fun setMigrationV3Complete(complete: Boolean = true) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_MIGRATION_V3_COMPLETE] = complete
        }
    }
}
