package com.example.drive

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.AppDatabase
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpResponseException
import com.google.api.client.http.UrlEncodedContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Dedicated Google Drive backup and restore repository using current official
 * Google Identity Services (AuthorizationClient) and Google Drive API v3.
 *
 * Uses strictly the narrow `drive.file` scope (`https://www.googleapis.com/auth/drive.file`)
 * so Novel Studio only accesses backup files created by this app.
 *
 * Responsibilities:
 * 1. Connecting and authorizing the user's Google account for Drive file access
 * 2. Uploading a backup file to Google Drive
 * 3. Listing Novel Studio backup files stored in Google Drive
 * 4. Downloading a selected backup file from Google Drive
 * 5. Disconnecting and revoking the app's Google Drive authorization
 */
class GoogleDriveBackupRepository(
    context: Context,
    private val metadataStore: DriveBackupMetadataStore = DriveBackupMetadataStore(context.applicationContext)
) {
    companion object {
        private const val TAG = "GoogleDriveBackupRepo"
        private const val APPLICATION_NAME = "Novel Studio"
        private const val APP_PROPERTY_KEY = "app"
        private const val APP_PROPERTY_VALUE = "novel_studio"
        private const val BACKUP_TYPE_KEY = "backup_type"
        private const val BACKUP_TYPE_VALUE = "manuscript_sqlite_backup"
        private const val DEFAULT_BACKUP_MIME_TYPE = "application/octet-stream"

        // Narrowest suitable Drive scope: only files created/opened by this app
        val REQUIRED_DRIVE_SCOPE = Scope(DriveScopes.DRIVE_FILE)
    }

    private val appContext: Context = context.applicationContext
    private val authorizationClient: AuthorizationClient by lazy {
        Identity.getAuthorizationClient(appContext)
    }

    @Volatile
    private var cachedAccessToken: String? = null

    val metadataFlow: Flow<DriveBackupMetadata> = metadataStore.metadataFlow

    /**
     * 1. Connects/authorizes the user's Google account using Google Identity Services
     * with the narrow `drive.file` scope.
     *
     * If user interaction is required (consent screen / account picker), returns
     * [DriveAuthorizationOutcome.ResolutionRequired] containing the [PendingIntent].
     * Otherwise returns [DriveAuthorizationOutcome.Authorized] with the granted OAuth access token.
     */
    suspend fun connectAndAuthorize(accountHint: Account? = null): Result<DriveAuthorizationOutcome> =
        withContext(Dispatchers.IO) {
            try {
                val requestBuilder = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(REQUIRED_DRIVE_SCOPE))
                if (accountHint != null) {
                    requestBuilder.setAccount(accountHint)
                }

                val authResult = authorizationClient.authorize(requestBuilder.build()).await()
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                        ?: return@withContext Result.failure(
                            DriveBackupError.MissingConfiguration(
                                "Authorization requires user resolution, but no PendingIntent was returned by Google Play Services."
                            )
                        )
                    Result.success(DriveAuthorizationOutcome.ResolutionRequired(pendingIntent))
                } else {
                    val token = authResult.accessToken
                    if (token.isNullOrBlank()) {
                        return@withContext Result.failure(
                            DriveBackupError.ExpiredOrRevokedAuthorization(
                                "Google Identity Services did not return a valid Drive access token."
                            )
                        )
                    }
                    cachedAccessToken = token
                    metadataStore.saveConnectedAccount(accountHint?.name ?: "Authorized Google Account")
                    Result.success(
                        DriveAuthorizationOutcome.Authorized(
                            accessToken = token,
                            accountEmail = accountHint?.name
                        )
                    )
                }
            } catch (e: Exception) {
                val mappedError = mapToDriveBackupError(e)
                Log.e(TAG, "connectAndAuthorize failed: ${mappedError.message}", e)
                Result.failure(mappedError)
            }
        }

    /**
     * Completes an interactive authorization flow after the user responds to the
     * Google Identity Services consent/account resolution sheet.
     *
     * Logs the authorization result code, whether the result Intent is present, and any
     * exception thrown by `getAuthorizationResultFromIntent()`, while never logging
     * access tokens or credentials.
     */
    suspend fun handleAuthorizationIntentResult(
        resultCode: Int,
        data: Intent?
    ): Result<DriveAuthorizationOutcome.Authorized> = withContext(Dispatchers.IO) {
        val resultCodeLabel = when (resultCode) {
            Activity.RESULT_OK -> "RESULT_OK ($resultCode)"
            Activity.RESULT_CANCELED -> "RESULT_CANCELED ($resultCode)"
            else -> "CUSTOM ($resultCode)"
        }
        val hasIntent = (data != null)
        Log.i(
            TAG,
            "handleAuthorizationIntentResult: resultCode=$resultCodeLabel, hasResultIntent=$hasIntent"
        )

        if (data == null) {
            if (resultCode == Activity.RESULT_CANCELED) {
                val msg = "Google Drive authorization was cancelled by the user (resultCode=$resultCodeLabel, hasResultIntent=false)."
                Log.w(TAG, msg)
                return@withContext Result.failure(
                    DriveBackupError.CancelledAuthorization(msg)
                )
            } else {
                val msg = "Google Drive authorization returned no result Intent data (resultCode=$resultCodeLabel, hasResultIntent=false)."
                Log.e(TAG, msg)
                return@withContext Result.failure(
                    DriveBackupError.DriveApiFailure(
                        message = msg
                    )
                )
            }
        }

        try {
            val authResult = authorizationClient.getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            val hasToken = !token.isNullOrBlank()
            Log.i(
                TAG,
                "getAuthorizationResultFromIntent succeeded: resultCode=$resultCodeLabel, hasAccessToken=$hasToken, hasResolution=${authResult.hasResolution()}"
            )

            if (!hasToken) {
                if (resultCode == Activity.RESULT_CANCELED) {
                    val msg = "Google Drive authorization was cancelled before granting access (resultCode=$resultCodeLabel, hasResultIntent=true, hasAccessToken=false)."
                    Log.w(TAG, msg)
                    return@withContext Result.failure(
                        DriveBackupError.CancelledAuthorization(msg)
                    )
                }
                val msg = "Google Drive authorization completed without returning an access token (resultCode=$resultCodeLabel, hasResultIntent=true, hasAccessToken=false)."
                Log.e(TAG, msg)
                return@withContext Result.failure(
                    DriveBackupError.ExpiredOrRevokedAuthorization(msg)
                )
            }

            cachedAccessToken = token
            metadataStore.saveConnectedAccount("Authorized Google Account")
            Result.success(DriveAuthorizationOutcome.Authorized(accessToken = token!!))
        } catch (e: Exception) {
            val apiStatusInfo = if (e is ApiException) {
                "ApiException(statusCode=${e.statusCode}, statusMessage=${CommonStatusCodes.getStatusCodeString(e.statusCode)}, status=${e.status})"
            } else {
                "${e::class.java.simpleName}(${e.message ?: "no message"})"
            }
            Log.e(
                TAG,
                "getAuthorizationResultFromIntent threw exception (resultCode=$resultCodeLabel, hasResultIntent=$hasIntent): $apiStatusInfo",
                e
            )
            val mappedError = mapToDriveBackupError(e, resultCode = resultCode, hasResultIntent = hasIntent)
            Log.e(TAG, "handleAuthorizationIntentResult mapped error: ${mappedError.message}", e)
            Result.failure(mappedError)
        }
    }

    /**
     * Preserves and maps an exception thrown when launching the Google Identity Services
     * authorization resolution PendingIntent.
     */
    fun handleAuthorizationLaunchFailure(exception: Throwable): DriveBackupError {
        val excType = exception::class.java.simpleName
        val detail = exception.localizedMessage ?: exception.message ?: "Unknown launch exception"
        val msg = "Failed to launch Google Drive authorization UI ($excType: $detail)."
        Log.e(TAG, "handleAuthorizationLaunchFailure: $msg", exception)
        return DriveBackupError.DriveApiFailure(
            message = msg,
            cause = exception
        )
    }

    /**
     * Creates a consistent temporary SQLite snapshot of the active Room database, verifies the
     * completed snapshot, uploads ONLY the snapshot file to Google Drive, and deletes the
     * temporary snapshot afterward.
     *
     * If snapshot creation or verification fails, aborts immediately without uploading anything.
     */
    suspend fun backupDatabaseSnapshotToDrive(
        backupFileName: String? = null,
        description: String = "Novel Studio Local Backup"
    ): Result<DriveBackupFile> = withContext(Dispatchers.IO) {
        val snapshotResult = createConsistentRoomDatabaseSnapshot()
        if (snapshotResult.isFailure) {
            val snapshotErr = snapshotResult.exceptionOrNull()
            val message = "Aborted Google Drive backup because creating a consistent SQLite snapshot failed: ${snapshotErr?.message ?: "Unknown snapshot error"}"
            Log.e(TAG, message, snapshotErr)
            return@withContext Result.failure(
                DriveBackupError.DriveApiFailure(
                    message = message,
                    cause = snapshotErr
                )
            )
        }

        val (tempSnapshotDir, tempSnapshotFile) = snapshotResult.getOrThrow()
        try {
            val resolvedName = backupFileName ?: "novel_studio_backup_${System.currentTimeMillis()}.sqlite"
            uploadBackupFile(
                localFile = tempSnapshotFile,
                backupFileName = resolvedName,
                description = description
            )
        } finally {
            try {
                if (tempSnapshotDir.exists() && !tempSnapshotDir.deleteRecursively()) {
                    tempSnapshotFile.delete()
                    tempSnapshotDir.delete()
                }
            } catch (cleanupErr: Exception) {
                Log.w(TAG, "Temporary SQLite snapshot cleanup completed with notice: ${cleanupErr.message}")
            }
        }
    }

    /**
     * 2. Uploads a completed, verified SQLite backup snapshot file to Google Drive under the `drive.file` scope.
     *
     * Explicitly refuses to upload the live Room database file (`novel_writer_database`) directly.
     * Only uploads a non-empty, integrity-verified SQLite snapshot file.
     */
    suspend fun uploadBackupFile(
        localFile: File,
        backupFileName: String? = null,
        mimeType: String = DEFAULT_BACKUP_MIME_TYPE,
        description: String = "Novel Studio Local Backup"
    ): Result<DriveBackupFile> = withContext(Dispatchers.IO) {
        if (!localFile.exists() || !localFile.isFile || localFile.length() <= 0L) {
            return@withContext Result.failure(
                DriveBackupError.DriveApiFailure(
                    message = "Aborted Google Drive upload: snapshot file is missing or empty (${localFile.name})."
                )
            )
        }

        val liveDbFile = appContext.getDatabasePath("novel_writer_database")
        if (liveDbFile != null && runCatching { localFile.canonicalPath == liveDbFile.canonicalPath }.getOrDefault(false)) {
            val msg = "Refusing to upload the live Room database file directly. Create a temporary SQLite snapshot first."
            Log.e(TAG, msg)
            return@withContext Result.failure(DriveBackupError.DriveApiFailure(message = msg))
        }

        // Verify the snapshot file passes SQLite integrity_check before uploading
        try {
            verifySqliteSnapshotFile(localFile)
        } catch (verifyErr: Exception) {
            val msg = "Aborted Google Drive upload: SQLite snapshot verification failed (${verifyErr.message})."
            Log.e(TAG, msg, verifyErr)
            return@withContext Result.failure(DriveBackupError.DriveApiFailure(message = msg, cause = verifyErr))
        }

        try {
            val driveService = getAuthorizedDriveService()
            val metadata = DriveFile().apply {
                name = backupFileName ?: localFile.name
                this.description = description
                this.mimeType = mimeType
                appProperties = mapOf(
                    APP_PROPERTY_KEY to APP_PROPERTY_VALUE,
                    BACKUP_TYPE_KEY to BACKUP_TYPE_VALUE
                )
            }

            val mediaContent = FileContent(mimeType, localFile)
            val createdFile = driveService.files()
                .create(metadata, mediaContent)
                .setFields("id, name, size, createdTime, modifiedTime, mimeType, description")
                .execute()

            val backupFile = createdFile.toDriveBackupFile()
            metadataStore.recordUploadedBackup(
                fileId = backupFile.id,
                timestampMillis = backupFile.createdTimeMillis
            )
            Result.success(backupFile)
        } catch (e: Exception) {
            val mappedError = mapToDriveBackupError(e)
            if (mappedError is DriveBackupError.ExpiredOrRevokedAuthorization) {
                cachedAccessToken = null
                metadataStore.clearConnectionMetadata()
            }
            Log.e(TAG, "uploadBackupFile failed: ${mappedError.message}", e)
            Result.failure(mappedError)
        }
    }

    /**
     * Creates a transactionally consistent, standalone temporary SQLite snapshot of the active
     * Room database using Room's own [SupportSQLiteDatabase] connection:
     *
     * 1. Flushes pending WAL frames on Room's active connection via `PRAGMA wal_checkpoint(FULL)`.
     * 2. Creates a uniquely named temporary directory under `cacheDir/drive_temp_backups`.
     * 3. Executes SQLite's native `VACUUM INTO '<snapshot_path>'` on Room's active connection
     *    (or falls back to an atomic `BEGIN IMMEDIATE` + `ATTACH DATABASE` transactional clone
     *    on older Android SQLite engines where `VACUUM INTO` is not supported).
     * 4. Verifies the completed snapshot file is non-empty, distinct from the live DB, and passes
     *    `PRAGMA integrity_check`.
     */
    fun createConsistentRoomDatabaseSnapshot(): Result<Pair<File, File>> {
        var uniqueTempDir: File? = null
        return try {
            val liveDbFile = appContext.getDatabasePath("novel_writer_database")
            if (liveDbFile == null || !liveDbFile.exists() || liveDbFile.length() <= 0L) {
                throw IOException("Live database file does not exist or is empty.")
            }

            val roomDb = AppDatabase.getDatabase(appContext)
            val supportDb = roomDb.openHelper.writableDatabase

            // 1. Create a uniquely named temporary snapshot directory
            val tempRoot = File(appContext.cacheDir, "drive_temp_backups")
            if (!tempRoot.exists() && !tempRoot.mkdirs()) {
                throw IOException("Failed to create temporary backup root directory at ${tempRoot.absolutePath}")
            }

            val timestamp = System.currentTimeMillis()
            var candidateDir: File
            var attempt = 0
            do {
                val token = java.util.UUID.randomUUID().toString().take(8)
                val dirName = if (attempt == 0) {
                    "temp_drive_snapshot_${timestamp}_$token"
                } else {
                    "temp_drive_snapshot_${timestamp}_${token}_$attempt"
                }
                candidateDir = File(tempRoot, dirName)
                attempt++
            } while (candidateDir.exists() && attempt < 10)

            if (candidateDir.exists()) {
                throw IOException("Refusing to overwrite existing temporary snapshot directory: ${candidateDir.absolutePath}")
            }
            if (!candidateDir.mkdirs() || !candidateDir.isDirectory) {
                throw IOException("Failed to create unique temporary snapshot directory at ${candidateDir.absolutePath}")
            }
            uniqueTempDir = candidateDir

            val tempSnapshotFile = File(candidateDir, "novel_studio_snapshot_${timestamp}.sqlite")
            if (tempSnapshotFile.exists()) {
                throw IOException("Refusing to overwrite existing temporary snapshot file: ${tempSnapshotFile.absolutePath}")
            }
            if (tempSnapshotFile.canonicalPath == liveDbFile.canonicalPath) {
                throw IOException("Snapshot target path must never match the live database path.")
            }

            val escapedSnapshotPath = tempSnapshotFile.absolutePath.replace("'", "''")

            // 2. Configure busy timeout and flush committed WAL frames on Room's active connection
            supportDb.query("PRAGMA busy_timeout = 5000").close()
            supportDb.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                if (cursor.moveToFirst()) {
                    Log.d(
                        TAG,
                        "Pre-snapshot WAL checkpoint on Room connection: busy=${cursor.getInt(0)}, log=${cursor.getInt(1)}, checkpointed=${cursor.getInt(2)}"
                    )
                }
            }

            // 3. Create the atomic SQLite snapshot through Room's SupportSQLiteDatabase
            var snapshotCreatedByVacuum = false
            try {
                supportDb.execSQL("VACUUM INTO '$escapedSnapshotPath'")
                snapshotCreatedByVacuum = true
                Log.d(TAG, "Created atomic SQLite snapshot via VACUUM INTO at ${tempSnapshotFile.absolutePath}")
            } catch (vacuumError: Exception) {
                Log.w(
                    TAG,
                    "VACUUM INTO not supported on this SQLite engine (${vacuumError.message}); falling back to transactional ATTACH DATABASE snapshot."
                )
                if (tempSnapshotFile.exists() && !tempSnapshotFile.delete()) {
                    throw IOException("Failed to clean up partial snapshot file before transactional fallback: ${tempSnapshotFile.absolutePath}")
                }
            }

            if (!snapshotCreatedByVacuum) {
                createTransactionalAttachedSnapshot(supportDb, escapedSnapshotPath)
            }

            // 4. Verify the completed snapshot exists, is non-empty, and passes PRAGMA integrity_check
            verifySqliteSnapshotFile(tempSnapshotFile)

            Result.success(candidateDir to tempSnapshotFile)
        } catch (e: Exception) {
            uniqueTempDir?.let { dir ->
                try {
                    dir.deleteRecursively()
                } catch (_: Exception) {
                }
            }
            Result.failure(e)
        }
    }

    private fun verifySqliteSnapshotFile(snapshotFile: File) {
        if (!snapshotFile.exists() || !snapshotFile.isFile || snapshotFile.length() <= 0L) {
            throw IOException("SQLite snapshot file is missing or empty at ${snapshotFile.absolutePath}")
        }

        android.database.sqlite.SQLiteDatabase.openDatabase(
            snapshotFile.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        ).use { verifyDb ->
            verifyDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                val status = if (cursor.moveToFirst()) cursor.getString(0) else null
                if (!status.equals("ok", ignoreCase = true)) {
                    throw IOException("SQLite snapshot failed PRAGMA integrity_check: ${status ?: "no result"}")
                }
            }
        }
    }

    /**
     * Fallback for Android SQLite versions prior to 3.27.0 (API 24-29):
     * Attaches the empty target snapshot file and executes a `BEGIN IMMEDIATE` transaction
     * on Room's [supportDb]. Holding `BEGIN IMMEDIATE` blocks any concurrent write transactions
     * for the entire duration of the clone, ensuring a transactionally consistent snapshot
     * of all committed data.
     */
    private fun createTransactionalAttachedSnapshot(
        supportDb: SupportSQLiteDatabase,
        escapedSnapshotPath: String
    ) {
        supportDb.execSQL("ATTACH DATABASE '$escapedSnapshotPath' AS snapshot_db")
        var inTransaction = false
        try {
            supportDb.execSQL("BEGIN IMMEDIATE")
            inTransaction = true

            supportDb.execSQL("PRAGMA snapshot_db.foreign_keys = OFF")

            var userVersion = 0L
            supportDb.query("PRAGMA main.user_version").use { cursor ->
                if (cursor.moveToFirst()) userVersion = cursor.getLong(0)
            }
            var appId = 0L
            supportDb.query("PRAGMA main.application_id").use { cursor ->
                if (cursor.moveToFirst()) appId = cursor.getLong(0)
            }
            supportDb.execSQL("PRAGMA snapshot_db.user_version = $userVersion")
            supportDb.execSQL("PRAGMA snapshot_db.application_id = $appId")

            data class SchemaEntry(val type: String, val name: String, val sql: String)
            val entries = mutableListOf<SchemaEntry>()

            supportDb.query(
                "SELECT type, name, sql FROM main.sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    entries.add(
                        SchemaEntry(
                            type = cursor.getString(0) ?: "",
                            name = cursor.getString(1) ?: "",
                            sql = cursor.getString(2) ?: ""
                        )
                    )
                }
            }

            // 1. Create all tables and copy their committed rows inside the atomic transaction
            val createTablePrefixRegex = Regex("(?i)^\\s*CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?")
            for (entry in entries.filter { it.type.equals("table", ignoreCase = true) }) {
                val qualifiedCreateTable = createTablePrefixRegex.replace(entry.sql, "CREATE TABLE snapshot_db.")
                supportDb.execSQL(qualifiedCreateTable)

                val quotedTable = entry.name.replace("\"", "\"\"")
                supportDb.execSQL("INSERT INTO snapshot_db.\"$quotedTable\" SELECT * FROM main.\"$quotedTable\"")
            }

            // 2. Copy sqlite_sequence if present (for AUTOINCREMENT primary keys)
            var hasSequenceTable = false
            supportDb.query(
                "SELECT COUNT(*) FROM main.sqlite_master WHERE type='table' AND name='sqlite_sequence'"
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    hasSequenceTable = cursor.getLong(0) > 0L
                }
            }
            if (hasSequenceTable) {
                supportDb.execSQL("DELETE FROM snapshot_db.sqlite_sequence")
                supportDb.execSQL("INSERT INTO snapshot_db.sqlite_sequence SELECT * FROM main.sqlite_sequence")
            }

            // 3. Recreate indices, views, and triggers in snapshot_db
            val createUniqueIndexRegex = Regex("(?i)^\\s*CREATE\\s+UNIQUE\\s+INDEX\\s+(IF\\s+NOT\\s+EXISTS\\s+)?")
            val createIndexRegex = Regex("(?i)^\\s*CREATE\\s+INDEX\\s+(IF\\s+NOT\\s+EXISTS\\s+)?")
            val createViewRegex = Regex("(?i)^\\s*CREATE\\s+VIEW\\s+(IF\\s+NOT\\s+EXISTS\\s+)?")
            val createTriggerRegex = Regex("(?i)^\\s*CREATE\\s+TRIGGER\\s+(IF\\s+NOT\\s+EXISTS\\s+)?")

            for (entry in entries.filter { !it.type.equals("table", ignoreCase = true) }) {
                val qualifiedSql = when (entry.type.lowercase()) {
                    "index" -> {
                        if (createUniqueIndexRegex.containsMatchIn(entry.sql)) {
                            createUniqueIndexRegex.replace(entry.sql, "CREATE UNIQUE INDEX snapshot_db.")
                        } else {
                            createIndexRegex.replace(entry.sql, "CREATE INDEX snapshot_db.")
                        }
                    }
                    "view" -> createViewRegex.replace(entry.sql, "CREATE VIEW snapshot_db.")
                    "trigger" -> createTriggerRegex.replace(entry.sql, "CREATE TRIGGER snapshot_db.")
                    else -> continue
                }
                supportDb.execSQL(qualifiedSql)
            }

            supportDb.execSQL("COMMIT")
            inTransaction = false
        } finally {
            if (inTransaction) {
                try {
                    supportDb.execSQL("ROLLBACK")
                } catch (_: Exception) {
                }
            }
            try {
                supportDb.execSQL("DETACH DATABASE snapshot_db")
            } catch (detachErr: Exception) {
                Log.w(TAG, "Detaching snapshot_db completed with notice: ${detachErr.message}")
            }
        }
    }

    /**
     * 3. Lists Novel Studio backup files stored in the user's Google Drive (`drive.file` scope).
     */
    suspend fun listBackupFiles(pageSize: Int = 25): Result<List<DriveBackupFile>> =
        withContext(Dispatchers.IO) {
            try {
                val driveService = getAuthorizedDriveService()
                val query = "trashed = false and appProperties has { key='$APP_PROPERTY_KEY' and value='$APP_PROPERTY_VALUE' }"
                val fileList = driveService.files()
                    .list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setPageSize(pageSize)
                    .setOrderBy("createdTime desc")
                    .setFields("files(id, name, size, createdTime, modifiedTime, mimeType, description)")
                    .execute()

                val backups = (fileList.files ?: emptyList()).map { it.toDriveBackupFile() }
                metadataStore.updateKnownBackupIds(backups.map { it.id }.toSet())
                Result.success(backups)
            } catch (e: Exception) {
                val mappedError = mapToDriveBackupError(e)
                if (mappedError is DriveBackupError.ExpiredOrRevokedAuthorization) {
                    cachedAccessToken = null
                    metadataStore.clearConnectionMetadata()
                }
                Log.e(TAG, "listBackupFiles failed: ${mappedError.message}", e)
                Result.failure(mappedError)
            }
        }

    /**
     * 4. Downloads a selected Novel Studio backup file from Google Drive to [destinationFile].
     * Never downloads directly over the live Room database file.
     */
    suspend fun downloadBackupFile(
        driveFileId: String,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        if (driveFileId.isBlank()) {
            return@withContext Result.failure(
                DriveBackupError.DriveApiFailure(message = "Drive backup file ID cannot be blank.")
            )
        }

        val liveDbFile = appContext.getDatabasePath("novel_writer_database")
        if (liveDbFile != null && runCatching { destinationFile.canonicalPath == liveDbFile.canonicalPath }.getOrDefault(false)) {
            return@withContext Result.failure(
                DriveBackupError.DriveApiFailure(
                    message = "Refusing to download directly over the live Room database file."
                )
            )
        }

        val parentDir = destinationFile.parentFile
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            return@withContext Result.failure(
                DriveBackupError.DriveApiFailure(
                    message = "Could not create local download directory: ${parentDir.absolutePath}"
                )
            )
        }

        val tempDownloadFile = File(
            parentDir ?: appContext.cacheDir,
            "${destinationFile.name}.part_${System.currentTimeMillis()}"
        )

        try {
            val driveService = getAuthorizedDriveService()
            FileOutputStream(tempDownloadFile).use { output ->
                driveService.files()
                    .get(driveFileId)
                    .executeMediaAndDownloadTo(output)
                output.flush()
            }

            if (!tempDownloadFile.exists() || tempDownloadFile.length() == 0L) {
                tempDownloadFile.delete()
                return@withContext Result.failure(
                    DriveBackupError.DriveApiFailure(
                        message = "Downloaded Drive backup file ($driveFileId) was empty."
                    )
                )
            }

            if (destinationFile.exists() && !destinationFile.delete()) {
                tempDownloadFile.delete()
                return@withContext Result.failure(
                    DriveBackupError.DriveApiFailure(
                        message = "Could not replace existing destination file: ${destinationFile.absolutePath}"
                    )
                )
            }

            if (!tempDownloadFile.renameTo(destinationFile)) {
                tempDownloadFile.copyTo(destinationFile, overwrite = true)
                tempDownloadFile.delete()
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            if (tempDownloadFile.exists()) {
                tempDownloadFile.delete()
            }
            val mappedError = mapToDriveBackupError(e)
            if (mappedError is DriveBackupError.ExpiredOrRevokedAuthorization) {
                cachedAccessToken = null
                metadataStore.clearConnectionMetadata()
            }
            Log.e(TAG, "downloadBackupFile failed: ${mappedError.message}", e)
            Result.failure(mappedError)
        }
    }

    /**
     * 5. Safely restores a selected Google Drive backup into the local Novel Studio database:
     *
     * 1. Downloads the selected backup to a unique local staging file (never over the live DB).
     * 2. Validates the staged database (SQLite header, `PRAGMA integrity_check == ok`, expected
     *    Novel Studio tables/columns, Room schema & migration compatibility dry-run).
     * 3. Creates a pre-restore local safety backup using `AppDatabase.backupDatabaseSafely()`.
     *    If that safety backup fails, aborts the restore completely before touching the live DB.
     * 4. Safely closes and invalidates the active Room database.
     * 5. Replaces the live database file and removes any stale `-wal`, `-shm`, or `-journal` files.
     * 6. Reopens Room and verifies that the restored database opens and can be queried.
     * 7. If anything fails during replacement or reopen, automatically rolls back using the
     *    pre-restore local safety backup.
     */
    suspend fun restoreBackupFromDrive(
        driveFileId: String,
        backupFileName: String,
        onProgress: (String) -> Unit = {}
    ): Result<Pair<AppDatabase, DriveRestoreSummary>> = withContext(Dispatchers.IO) {
        if (driveFileId.isBlank()) {
            return@withContext Result.failure(
                DriveBackupError.InvalidBackupFile("Selected Google Drive backup ID is blank.")
            )
        }

        val liveDbFile = appContext.getDatabasePath("novel_writer_database")
            ?: return@withContext Result.failure(
                DriveBackupError.DriveApiFailure(message = "Could not resolve local database path.")
            )

        // 1. Create a uniquely named local staging directory
        val stagingRoot = File(appContext.cacheDir, "drive_restore_staging")
        if (!stagingRoot.exists() && !stagingRoot.mkdirs()) {
            return@withContext Result.failure(
                DriveBackupError.DriveApiFailure(
                    message = "Failed to create local restore staging directory."
                )
            )
        }

        val timestamp = System.currentTimeMillis()
        var stagingDir: File
        var attempt = 0
        do {
            val token = java.util.UUID.randomUUID().toString().take(8)
            val dirName = if (attempt == 0) {
                "restore_stage_${timestamp}_$token"
            } else {
                "restore_stage_${timestamp}_${token}_$attempt"
            }
            stagingDir = File(stagingRoot, dirName)
            attempt++
        } while (stagingDir.exists() && attempt < 10)

        if (stagingDir.exists() || !stagingDir.mkdirs() || !stagingDir.isDirectory) {
            return@withContext Result.failure(
                DriveBackupError.DriveApiFailure(
                    message = "Failed to initialize unique restore staging directory."
                )
            )
        }

        val stagedFile = File(stagingDir, "staged_backup_${timestamp}.sqlite")

        try {
            if (stagedFile.canonicalPath == liveDbFile.canonicalPath) {
                return@withContext Result.failure(
                    DriveBackupError.DriveApiFailure(
                        message = "Staging file path must never match the live database path."
                    )
                )
            }

            // Step 1: Download the selected backup into the unique staging file
            onProgress("Downloading '$backupFileName' to local staging...")
            val downloadResult = downloadBackupFile(driveFileId, stagedFile)
            if (downloadResult.isFailure) {
                val err = downloadResult.exceptionOrNull()!!
                return@withContext Result.failure(mapToDriveBackupError(err))
            }

            // Step 2: Validate the staged SQLite database before touching the live database
            onProgress("Validating staged SQLite database and Novel Studio schema...")
            try {
                validateStagedDatabaseForRestore(stagedFile, stagingDir)
            } catch (validationErr: Exception) {
                val msg = "Restore aborted — backup validation failed: ${validationErr.message ?: "Invalid or incompatible SQLite file"}"
                Log.e(TAG, msg, validationErr)
                return@withContext Result.failure(
                    DriveBackupError.InvalidBackupFile(message = msg, cause = validationErr)
                )
            }

            // Step 3: Create a local pre-restore safety backup using AppDatabase.backupDatabaseSafely()
            onProgress("Creating local pre-restore safety backup...")
            val hadExistingLiveDb = liveDbFile.exists() && liveDbFile.length() > 0L
            val safetyBackupResult = AppDatabase.backupDatabaseSafely(appContext)
            if (safetyBackupResult.isFailure) {
                val backupErr = safetyBackupResult.exceptionOrNull()
                val msg = "Restore aborted — pre-restore local safety backup failed: ${backupErr?.message ?: "Unknown backup error"}"
                Log.e(TAG, msg, backupErr)
                return@withContext Result.failure(
                    DriveBackupError.PreRestoreBackupFailed(message = msg, cause = backupErr)
                )
            }

            val safetyBackupDir = safetyBackupResult.getOrNull()
            if (hadExistingLiveDb) {
                val primaryBackupFile = safetyBackupDir?.let { File(it, liveDbFile.name) }
                if (safetyBackupDir == null || !safetyBackupDir.exists() ||
                    primaryBackupFile == null || !primaryBackupFile.exists() || primaryBackupFile.length() <= 0L
                ) {
                    val msg = "Restore aborted — pre-restore local safety backup directory could not be verified."
                    Log.e(TAG, msg)
                    return@withContext Result.failure(DriveBackupError.PreRestoreBackupFailed(message = msg))
                }
            }

            // Step 4: Safely close and invalidate the active Room database before replacing files
            onProgress("Closing active database and restoring validated backup...")
            AppDatabase.closeAndInvalidateDatabase()

            // Step 5 & 6: Replace live database cleanly (removing stale WAL/SHM/journal files), reopen Room, and verify
            try {
                replaceLiveDatabaseWithValidatedBackup(stagedFile, liveDbFile)

                onProgress("Reopening Room and verifying restored database...")
                val reopenedDb = AppDatabase.reopenAndVerifyDatabase(appContext)

                // Query counts on reopened Room database to confirm data accessibility
                val supportDb = reopenedDb.openHelper.writableDatabase
                val nodesCount = queryTableRowCount(supportDb, "manuscript_nodes")
                val charsCount = queryTableRowCount(supportDb, "character_profiles")
                val eventsCount = queryTableRowCount(supportDb, "story_events")

                val summary = DriveRestoreSummary(
                    backupFileId = driveFileId,
                    backupFileName = backupFileName,
                    restoredNodesCount = nodesCount,
                    restoredCharactersCount = charsCount,
                    restoredEventsCount = eventsCount,
                    preRestoreBackupPath = safetyBackupDir?.name
                )
                Log.i(
                    TAG,
                    "Successfully restored '$backupFileName' ($nodesCount nodes, $charsCount characters, $eventsCount events)."
                )
                Result.success(reopenedDb to summary)
            } catch (restoreErr: Exception) {
                Log.e(
                    TAG,
                    "CRITICAL: Restore or Room reopen failed (${restoreErr.message}); attempting rollback from pre-restore local safety backup...",
                    restoreErr
                )
                onProgress("Restore failed; rolling back to pre-restore local safety backup...")
                val rolledBackDb = rollbackFromSafetyBackup(safetyBackupDir, liveDbFile)
                val rollbackSucceeded = (rolledBackDb != null)
                val message = if (rollbackSucceeded) {
                    "Restore of '$backupFileName' failed (${restoreErr.message ?: "database reopen error"}). Your previous local database was safely restored from the pre-restore backup."
                } else {
                    "Restore of '$backupFileName' failed (${restoreErr.message ?: "database reopen error"}), and automatic rollback encountered an error."
                }
                Result.failure(
                    DriveBackupError.RestoreFailedWithRollback(
                        message = message,
                        rolledBackSuccessfully = rollbackSucceeded,
                        cause = restoreErr
                    )
                )
            }
        } finally {
            // Always clean up the local staging directory
            try {
                if (stagingDir.exists()) {
                    stagingDir.deleteRecursively()
                }
            } catch (cleanupErr: Exception) {
                Log.w(TAG, "Staging directory cleanup completed with notice: ${cleanupErr.message}")
            }
        }
    }

    /**
     * Validates a staged backup file before any live database state is modified:
     * 1. Verifies non-empty file and 16-byte SQLite 3 magic header (`"SQLite format 3\u0000"`).
     * 2. Verifies `PRAGMA integrity_check` returns `"ok"` and `PRAGMA foreign_key_check` has no errors.
     * 3. Verifies `PRAGMA user_version` is in `1..3` (Novel Studio Room versions 1, 2, or 3) and
     *    `room_master_table` exists.
     * 4. Verifies all expected Novel Studio tables and columns exist for that schema version.
     * 5. Performs a dry-run Room open with `MIGRATION_1_2` and `MIGRATION_2_3` on a copy inside
     *    [stagingDir] to guarantee full compatibility with the current Room schema and migrations.
     */
    fun validateStagedDatabaseForRestore(stagedFile: File, stagingDir: File) {
        if (!stagedFile.exists() || !stagedFile.isFile || stagedFile.length() < 100L) {
            throw IOException("Staged file is missing or too small to be a valid SQLite database (${stagedFile.length()} bytes).")
        }

        // 1. Check 16-byte SQLite 3 file header
        val sqliteHeaderBytes = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val fileHeader = ByteArray(16)
        FileInputStream(stagedFile).use { input ->
            val bytesRead = input.read(fileHeader)
            if (bytesRead != 16 || !fileHeader.contentEquals(sqliteHeaderBytes)) {
                throw IOException("Staged file does not have a valid SQLite 3 header.")
            }
        }

        // 2. Open read-only to check PRAGMA integrity_check, user_version, and expected Novel Studio schema
        android.database.sqlite.SQLiteDatabase.openDatabase(
            stagedFile.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                val status = if (cursor.moveToFirst()) cursor.getString(0) else null
                if (!status.equals("ok", ignoreCase = true)) {
                    throw IOException("Staged SQLite database failed PRAGMA integrity_check: ${status ?: "no result"}")
                }
            }

            val version = db.version
            if (version !in 1..3) {
                throw IOException(
                    "Unsupported database schema version ($version). Expected Novel Studio Room schema version 1, 2, or 3."
                )
            }

            val existingTables = mutableSetOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { existingTables.add(it) }
                }
            }

            if (!existingTables.contains("room_master_table")) {
                throw IOException("Staged SQLite file is missing 'room_master_table' and is not a valid Room backup.")
            }

            val requiredTablesByVersion = buildMap<String, List<String>> {
                put(
                    "manuscript_nodes",
                    buildList {
                        addAll(listOf("id", "name", "parentId", "isFolder", "content", "lastUpdated", "wordCount"))
                        if (version >= 3) add("lastAnalyzedHash")
                    }
                )
                put(
                    "character_profiles",
                    buildList {
                        addAll(listOf("id", "name", "role", "age", "appearance", "backstory", "plotArc", "notes", "avatarColor"))
                        if (version >= 2) add("novelId")
                    }
                )
                put(
                    "writing_settings",
                    buildList {
                        addAll(listOf("id", "selectedTexture", "fontSize", "isDistractionFree", "isAutoSyncEnabled", "lastSyncedTime"))
                        if (version >= 3) add("isAutoAnalysisEnabled")
                    }
                )
                put(
                    "story_events",
                    buildList {
                        addAll(listOf("id", "title", "description", "arcPhase", "orderIndex"))
                        if (version >= 2) add("novelId")
                    }
                )
                put(
                    "character_relationships",
                    buildList {
                        addAll(listOf("id", "sourceCharacterId", "targetId", "isToEvent", "relationType", "description"))
                        if (version >= 2) add("novelId")
                    }
                )
                if (version >= 2) {
                    put(
                        "manuscript_commits",
                        listOf("id", "nodeId", "commitHash", "commitMessage", "contentSnapshot", "timestamp")
                    )
                }
            }

            for ((tableName, requiredColumns) in requiredTablesByVersion) {
                if (!existingTables.contains(tableName)) {
                    throw IOException("Staged database is missing required Novel Studio table '$tableName'.")
                }
                val actualColumns = mutableSetOf<String>()
                db.rawQuery("PRAGMA table_info(`$tableName`)", null).use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx != -1) {
                            cursor.getString(nameIdx)?.let { actualColumns.add(it) }
                        }
                    }
                }
                for (reqCol in requiredColumns) {
                    if (actualColumns.none { it.equals(reqCol, ignoreCase = true) }) {
                        throw IOException(
                            "Staged database table '$tableName' is missing expected column '$reqCol'."
                        )
                    }
                }
            }
        }

        // 3. Dry-run Room schema & migration validation on a temporary copy inside stagingDir
        val dryRunFile = File(stagingDir, "room_schema_dry_run_${System.currentTimeMillis()}.sqlite")
        try {
            FileInputStream(stagedFile).use { input ->
                FileOutputStream(dryRunFile, false).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            val dryRunDb = Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                dryRunFile.absolutePath
            )
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .build()

            try {
                val supportDb = dryRunDb.openHelper.writableDatabase
                supportDb.query("SELECT COUNT(*) FROM manuscript_nodes").use { cursor ->
                    if (!cursor.moveToFirst()) {
                        throw IOException("Dry-run Room query on manuscript_nodes failed.")
                    }
                }
            } finally {
                try {
                    dryRunDb.close()
                } catch (_: Exception) {
                }
            }
        } finally {
            File(dryRunFile.path + "-wal").delete()
            File(dryRunFile.path + "-shm").delete()
            File(dryRunFile.path + "-journal").delete()
            dryRunFile.delete()
        }
    }

    /**
     * Replaces [liveDbFile] with [validatedStagedFile] and removes any stale `-wal`, `-shm`, and `-journal`
     * files so no old WAL state can corrupt the restored database.
     */
    private fun replaceLiveDatabaseWithValidatedBackup(validatedStagedFile: File, liveDbFile: File) {
        val dbParent = liveDbFile.parentFile
        if (dbParent != null && !dbParent.exists() && !dbParent.mkdirs()) {
            throw IOException("Failed to create database parent directory: ${dbParent.absolutePath}")
        }

        // 1. Delete stale WAL, SHM, and journal files before replacing the main database file
        deleteSidecarDatabaseFiles(liveDbFile)

        // 2. Write to a temporary file in the database directory first and verify its byte length
        val expectedLength = validatedStagedFile.length()
        val tempReplacementFile = File(
            dbParent ?: appContext.filesDir,
            "${liveDbFile.name}.restore_tmp_${System.currentTimeMillis()}"
        )

        try {
            FileInputStream(validatedStagedFile).use { input ->
                FileOutputStream(tempReplacementFile, false).use { output ->
                    input.copyTo(output)
                    output.flush()
                    try {
                        output.fd.sync()
                    } catch (_: Exception) {
                    }
                }
            }

            if (!tempReplacementFile.exists() || tempReplacementFile.length() != expectedLength) {
                throw IOException(
                    "Temporary replacement database verification failed: expected $expectedLength bytes, found ${tempReplacementFile.length()} bytes."
                )
            }

            if (liveDbFile.exists() && !liveDbFile.delete()) {
                throw IOException("Failed to remove old live database file during restore: ${liveDbFile.absolutePath}")
            }

            if (!tempReplacementFile.renameTo(liveDbFile)) {
                FileInputStream(tempReplacementFile).use { input ->
                    FileOutputStream(liveDbFile, false).use { output ->
                        input.copyTo(output)
                        output.flush()
                        try {
                            output.fd.sync()
                        } catch (_: Exception) {
                        }
                    }
                }
                tempReplacementFile.delete()
            }

            // Ensure no stale sidecar files remain after replacement
            deleteSidecarDatabaseFiles(liveDbFile)

            if (!liveDbFile.exists() || liveDbFile.length() != expectedLength) {
                throw IOException(
                    "Restored database file verification failed: expected $expectedLength bytes, found ${liveDbFile.length()} bytes."
                )
            }
        } finally {
            if (tempReplacementFile.exists()) {
                tempReplacementFile.delete()
            }
        }
    }

    private fun deleteSidecarDatabaseFiles(dbFile: File) {
        val sidecars = listOf(
            File(dbFile.path + "-wal"),
            File(dbFile.path + "-shm"),
            File(dbFile.path + "-journal")
        )
        for (sidecar in sidecars) {
            if (sidecar.exists() && !sidecar.delete()) {
                throw IOException("Failed to delete stale SQLite sidecar file: ${sidecar.absolutePath}")
            }
        }
    }

    /**
     * Rolls back the live database from [safetyBackupDir] if a restore or reopen fails,
     * then reopens and verifies Room.
     */
    private fun rollbackFromSafetyBackup(safetyBackupDir: File?, liveDbFile: File): AppDatabase? {
        return try {
            AppDatabase.closeAndInvalidateDatabase()

            if (safetyBackupDir == null || !safetyBackupDir.exists()) {
                Log.w(TAG, "No pre-restore safety backup directory was present; reopening clean database.")
                deleteSidecarDatabaseFiles(liveDbFile)
                if (liveDbFile.exists()) liveDbFile.delete()
                return AppDatabase.reopenAndVerifyDatabase(appContext)
            }

            val backupMainDb = File(safetyBackupDir, liveDbFile.name)
            if (!backupMainDb.exists() || backupMainDb.length() <= 0L) {
                throw IOException("Safety backup main database file is missing in ${safetyBackupDir.absolutePath}")
            }

            // Remove failed database and sidecar files first
            deleteSidecarDatabaseFiles(liveDbFile)
            if (liveDbFile.exists() && !liveDbFile.delete()) {
                throw IOException("Failed to remove corrupted live database before rollback.")
            }

            // Restore main database file and any preserved WAL/SHM/journal files from safetyBackupDir
            val candidateSidecars = listOf(
                liveDbFile.name,
                "${liveDbFile.name}-wal",
                "${liveDbFile.name}-shm",
                "${liveDbFile.name}-journal"
            )

            for (fileName in candidateSidecars) {
                val src = File(safetyBackupDir, fileName)
                if (src.exists() && src.isFile) {
                    val dest = File(liveDbFile.parentFile, fileName)
                    val expectedLen = src.length()
                    FileInputStream(src).use { input ->
                        FileOutputStream(dest, false).use { output ->
                            input.copyTo(output)
                            output.flush()
                            try {
                                output.fd.sync()
                            } catch (_: Exception) {
                            }
                        }
                    }
                    if (!dest.exists() || dest.length() != expectedLen) {
                        throw IOException("Rollback verification failed for $fileName")
                    }
                }
            }

            val reopened = AppDatabase.reopenAndVerifyDatabase(appContext)
            Log.i(TAG, "Successfully rolled back database from pre-restore safety backup at ${safetyBackupDir.absolutePath}")
            reopened
        } catch (rollbackErr: Exception) {
            Log.e(TAG, "CRITICAL: Automatic rollback from safety backup failed: ${rollbackErr.message}", rollbackErr)
            null
        }
    }

    private fun queryTableRowCount(supportDb: SupportSQLiteDatabase, tableName: String): Int {
        supportDb.query("SELECT COUNT(*) FROM `$tableName`").use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /**
     * 5. Disconnects and revokes Novel Studio's Google Drive authorization, clearing cached
     * tokens, revoking the OAuth 2.0 access token, clearing CredentialManager state, and
     * removing local connection metadata.
     */
    suspend fun disconnectAndRevoke(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val tokenToRevoke = cachedAccessToken ?: runCatching { getValidAccessToken() }.getOrNull()
            cachedAccessToken = null

            if (!tokenToRevoke.isNullOrBlank()) {
                try {
                    GoogleAuthUtil.clearToken(appContext, tokenToRevoke)
                } catch (e: Exception) {
                    Log.w(TAG, "Clearing cached GoogleAuthUtil token completed with notice: ${e.message}")
                }

                // Revoke the OAuth 2.0 token via Google's official OAuth2 revocation endpoint
                val revokeUrl = GenericUrl("https://oauth2.googleapis.com/revoke")
                val revokeContent = UrlEncodedContent(mapOf("token" to tokenToRevoke))
                val httpRequestFactory = NetHttpTransport().createRequestFactory()
                val response = httpRequestFactory.buildPostRequest(revokeUrl, revokeContent).apply {
                    throwExceptionOnExecuteError = false
                }.execute()

                try {
                    val statusCode = response.statusCode
                    if (statusCode !in 200..299 && statusCode != 400) {
                        throw DriveBackupError.DriveApiFailure(
                            statusCode = statusCode,
                            message = "Google OAuth token revocation failed (HTTP $statusCode): ${response.statusMessage ?: "Request failed"}"
                        )
                    }
                } finally {
                    response.disconnect()
                }
            }

            try {
                CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Clearing CredentialManager state completed with notice: ${e.message}")
            }

            metadataStore.clearConnectionMetadata()
            Result.success(Unit)
        } catch (e: Exception) {
            metadataStore.clearConnectionMetadata()
            val mappedError = mapToDriveBackupError(e)
            Log.e(TAG, "disconnectAndRevoke failed: ${mappedError.message}", e)
            Result.failure(mappedError)
        }
    }

    /**
     * Obtains a valid OAuth 2.0 access token silently if already authorized, or throws
     * [DriveBackupError.ExpiredOrRevokedAuthorization] if interactive resolution is required.
     */
    private suspend fun getValidAccessToken(): String {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(REQUIRED_DRIVE_SCOPE))
            .build()

        val result = authorizationClient.authorize(request).await()
        if (result.hasResolution()) {
            cachedAccessToken = null
            throw DriveBackupError.ExpiredOrRevokedAuthorization(
                "Google Drive access requires re-authorization. Please reconnect Google Drive."
            )
        }

        val token = result.accessToken
        if (token.isNullOrBlank()) {
            cachedAccessToken = null
            throw DriveBackupError.ExpiredOrRevokedAuthorization(
                "Google Drive access token is missing or expired. Please reconnect Google Drive."
            )
        }

        cachedAccessToken = token
        return token
    }

    private suspend fun getAuthorizedDriveService(): Drive {
        val token = getValidAccessToken()
        val credentials = GoogleCredentials.create(AccessToken(token, null))
            .createScoped(listOf(DriveScopes.DRIVE_FILE))

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        )
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    private fun DriveFile.toDriveBackupFile(): DriveBackupFile {
        val createdMillis = this.createdTime?.value ?: System.currentTimeMillis()
        val modifiedMillis = this.modifiedTime?.value ?: createdMillis
        return DriveBackupFile(
            id = this.id ?: "",
            name = this.name ?: "novel_studio_backup",
            sizeBytes = this.getSize() ?: 0L,
            createdTimeMillis = createdMillis,
            modifiedTimeMillis = modifiedMillis,
            mimeType = this.mimeType ?: DEFAULT_BACKUP_MIME_TYPE,
            description = this.description
        )
    }

    /**
     * Maps exceptions from Google Identity Services, OkHttp/NetHttpTransport, and Drive API v3
     * into explicit [DriveBackupError] categories with diagnostic details.
     */
    fun mapToDriveBackupError(
        throwable: Throwable,
        resultCode: Int? = null,
        hasResultIntent: Boolean? = null
    ): DriveBackupError {
        if (throwable is DriveBackupError) return throwable

        val diagnosticContext = buildString {
            if (resultCode != null || hasResultIntent != null) {
                append(" [resultCode=")
                append(resultCode ?: "n/a")
                append(", hasIntent=")
                append(hasResultIntent ?: "n/a")
                append("]")
            }
        }

        return when (throwable) {
            is ApiException -> {
                val statusName = CommonStatusCodes.getStatusCodeString(throwable.statusCode)
                when (throwable.statusCode) {
                    CommonStatusCodes.CANCELED -> DriveBackupError.CancelledAuthorization(
                        "Google Drive authorization was cancelled by the user (status ${throwable.statusCode}: $statusName)$diagnosticContext."
                    )
                    CommonStatusCodes.DEVELOPER_ERROR -> DriveBackupError.MissingConfiguration(
                        "Google Cloud OAuth 2.0 Android Client ID is not configured or mismatched for package '${appContext.packageName}' and this build's SHA-1 signing certificate (status ${throwable.statusCode}: $statusName — ${throwable.localizedMessage ?: "DEVELOPER_ERROR"})$diagnosticContext."
                    )
                    CommonStatusCodes.SIGN_IN_REQUIRED,
                    CommonStatusCodes.RESOLUTION_REQUIRED,
                    CommonStatusCodes.INVALID_ACCOUNT -> DriveBackupError.ExpiredOrRevokedAuthorization(
                        "Google Drive authorization requires account sign-in or consent resolution (status ${throwable.statusCode}: $statusName — ${throwable.localizedMessage ?: "Sign-in required"})$diagnosticContext."
                    )
                    CommonStatusCodes.NETWORK_ERROR,
                    CommonStatusCodes.TIMEOUT -> DriveBackupError.NetworkFailure(
                        message = "Network error during Google Drive authorization/API request (status ${throwable.statusCode}: $statusName)$diagnosticContext. Please check your connection and try again.",
                        cause = throwable
                    )
                    else -> {
                        val msg = throwable.message ?: ""
                        if (msg.contains("10:") || msg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
                            msg.contains("client_id", ignoreCase = true)
                        ) {
                            DriveBackupError.MissingConfiguration(
                                "Google Cloud OAuth configuration error (status ${throwable.statusCode}: $statusName — ${throwable.localizedMessage ?: msg})$diagnosticContext. Verify package '${appContext.packageName}', SHA-1 fingerprint, OAuth test user access, and Drive API enablement."
                            )
                        } else {
                            DriveBackupError.DriveApiFailure(
                                statusCode = throwable.statusCode,
                                message = "Google authorization error (status ${throwable.statusCode}: $statusName): ${throwable.localizedMessage ?: "Unknown error"}$diagnosticContext",
                                cause = throwable
                            )
                        }
                    }
                }
            }

            is GoogleJsonResponseException -> {
                val code = throwable.statusCode
                val detailsMessage = throwable.details?.message ?: throwable.statusMessage ?: "Drive API error"
                val reasons = throwable.details?.errors?.mapNotNull { it.reason } ?: emptyList()

                when {
                    code == 401 -> DriveBackupError.ExpiredOrRevokedAuthorization(
                        "Google Drive access token has expired or was revoked (HTTP 401). Please reconnect Google Drive."
                    )
                    code == 403 && reasons.any {
                        it.equals("accessNotConfigured", ignoreCase = true) ||
                            it.equals("dailyLimitExceededUnreg", ignoreCase = true)
                    } -> DriveBackupError.MissingConfiguration(
                        "Google Drive API is not enabled in the Google Cloud project (HTTP 403: $detailsMessage)."
                    )
                    code == 403 && reasons.any {
                        it.equals("insufficientPermissions", ignoreCase = true) ||
                            it.equals("authError", ignoreCase = true)
                    } -> DriveBackupError.ExpiredOrRevokedAuthorization(
                        "Google Drive permission was revoked or is insufficient (HTTP 403). Please reconnect Google Drive."
                    )
                    else -> DriveBackupError.DriveApiFailure(
                        statusCode = code,
                        message = "Google Drive API failure (HTTP $code): $detailsMessage",
                        cause = throwable
                    )
                }
            }

            is HttpResponseException -> {
                val code = throwable.statusCode
                if (code == 401) {
                    DriveBackupError.ExpiredOrRevokedAuthorization(
                        "Google Drive session expired or was revoked (HTTP 401). Please reconnect Google Drive."
                    )
                } else {
                    DriveBackupError.DriveApiFailure(
                        statusCode = code,
                        message = "Google Drive HTTP error ($code): ${throwable.statusMessage ?: "Request failed"}",
                        cause = throwable
                    )
                }
            }

            is UnknownHostException,
            is SocketTimeoutException,
            is ConnectException -> DriveBackupError.NetworkFailure(cause = throwable)

            is IOException -> DriveBackupError.NetworkFailure(
                message = "Network or I/O error while communicating with Google Drive: ${throwable.localizedMessage ?: "Connection failed"}",
                cause = throwable
            )

            else -> DriveBackupError.DriveApiFailure(
                message = throwable.localizedMessage ?: "Unexpected Google Drive error.",
                cause = throwable
            )
        }
    }
}
