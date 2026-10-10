package com.example.drive

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists only the minimum local metadata needed to remember the connected Google account
 * and Google Drive backup file IDs. Does not touch Room or manuscript tables.
 */
class DriveBackupMetadataStore(context: Context) {

    private val appContext: Context = context.applicationContext

    companion object {
        private const val DATASTORE_NAME = "novel_studio_drive_backup_metadata"
        private val KEY_IS_CONNECTED = booleanPreferencesKey("is_drive_connected")
        private val KEY_CONNECTED_ACCOUNT_EMAIL = stringPreferencesKey("connected_account_email")
        private val KEY_LAST_BACKUP_FILE_ID = stringPreferencesKey("last_backup_drive_file_id")
        private val KEY_LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp_millis")
        private val KEY_KNOWN_BACKUP_IDS = stringSetPreferencesKey("known_drive_backup_file_ids")

        @Volatile
        private var dataStoreInstance: DataStore<Preferences>? = null

        fun getDataStore(context: Context): DataStore<Preferences> {
            return dataStoreInstance ?: synchronized(this) {
                dataStoreInstance ?: PreferenceDataStoreFactory.create(
                    produceFile = {
                        context.applicationContext.preferencesDataStoreFile(DATASTORE_NAME)
                    }
                ).also { dataStoreInstance = it }
            }
        }
    }

    private val dataStore: DataStore<Preferences>
        get() = getDataStore(appContext)

    val metadataFlow: Flow<DriveBackupMetadata> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            DriveBackupMetadata(
                isConnected = prefs[KEY_IS_CONNECTED] ?: false,
                connectedAccountEmail = prefs[KEY_CONNECTED_ACCOUNT_EMAIL],
                lastBackupDriveFileId = prefs[KEY_LAST_BACKUP_FILE_ID],
                lastBackupTimestampMillis = prefs[KEY_LAST_BACKUP_TIMESTAMP],
                knownBackupIds = prefs[KEY_KNOWN_BACKUP_IDS] ?: emptySet()
            )
        }

    suspend fun getMetadata(): DriveBackupMetadata = metadataFlow.first()

    suspend fun saveConnectedAccount(accountEmail: String?) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_CONNECTED] = true
            if (!accountEmail.isNullOrBlank()) {
                prefs[KEY_CONNECTED_ACCOUNT_EMAIL] = accountEmail
            }
        }
    }

    suspend fun recordUploadedBackup(fileId: String, timestampMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKUP_FILE_ID] = fileId
            prefs[KEY_LAST_BACKUP_TIMESTAMP] = timestampMillis
            val existing = prefs[KEY_KNOWN_BACKUP_IDS] ?: emptySet()
            prefs[KEY_KNOWN_BACKUP_IDS] = existing + fileId
        }
    }

    suspend fun updateKnownBackupIds(fileIds: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_KNOWN_BACKUP_IDS] = fileIds
        }
    }

    suspend fun clearConnectionMetadata() {
        dataStore.edit { prefs ->
            prefs[KEY_IS_CONNECTED] = false
            prefs.remove(KEY_CONNECTED_ACCOUNT_EMAIL)
            prefs.remove(KEY_LAST_BACKUP_FILE_ID)
            prefs.remove(KEY_LAST_BACKUP_TIMESTAMP)
            prefs.remove(KEY_KNOWN_BACKUP_IDS)
        }
    }
}
