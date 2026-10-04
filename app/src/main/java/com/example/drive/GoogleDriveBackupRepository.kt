package com.example.drive

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
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
     */
    suspend fun handleAuthorizationIntentResult(
        resultCode: Int,
        data: Intent?
    ): Result<DriveAuthorizationOutcome.Authorized> = withContext(Dispatchers.IO) {
        if (resultCode == Activity.RESULT_CANCELED || data == null) {
            return@withContext Result.failure(
                DriveBackupError.CancelledAuthorization(
                    "Google Drive authorization was cancelled before completion."
                )
            )
        }

        try {
            val authResult = authorizationClient.getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (token.isNullOrBlank()) {
                return@withContext Result.failure(
                    DriveBackupError.ExpiredOrRevokedAuthorization(
                        "Authorization completed without returning an access token."
                    )
                )
            }
            cachedAccessToken = token
            metadataStore.saveConnectedAccount("Authorized Google Account")
            Result.success(DriveAuthorizationOutcome.Authorized(accessToken = token))
        } catch (e: Exception) {
            val mappedError = mapToDriveBackupError(e)
            Log.e(TAG, "handleAuthorizationIntentResult failed: ${mappedError.message}", e)
            Result.failure(mappedError)
        }
    }

    /**
     * 2. Uploads a local backup file to Google Drive under the `drive.file` scope.
     * Tagging with `appProperties` ensures `listBackupFiles()` retrieves only Novel Studio backups.
     */
    suspend fun uploadBackupFile(
        localFile: File,
        backupFileName: String? = null,
        mimeType: String = DEFAULT_BACKUP_MIME_TYPE,
        description: String = "Novel Studio Local Backup"
    ): Result<DriveBackupFile> = withContext(Dispatchers.IO) {
        if (!localFile.exists() || !localFile.isFile || localFile.length() == 0L) {
            return@withContext Result.failure(
                DriveBackupError.DriveApiFailure(
                    message = "Local backup file to upload is missing or empty: ${localFile.name}"
                )
            )
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
     * Does NOT overwrite or restore into the live Room database.
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
     * into explicit [DriveBackupError] categories.
     */
    fun mapToDriveBackupError(throwable: Throwable): DriveBackupError {
        if (throwable is DriveBackupError) return throwable

        return when (throwable) {
            is ApiException -> {
                when (throwable.statusCode) {
                    CommonStatusCodes.CANCELED -> DriveBackupError.CancelledAuthorization()
                    CommonStatusCodes.DEVELOPER_ERROR -> DriveBackupError.MissingConfiguration(
                        "Google Cloud OAuth 2.0 Android Client ID is not configured for package '${appContext.packageName}' and this build's SHA-1 signing certificate (code 10: DEVELOPER_ERROR)."
                    )
                    CommonStatusCodes.SIGN_IN_REQUIRED,
                    CommonStatusCodes.RESOLUTION_REQUIRED,
                    CommonStatusCodes.INVALID_ACCOUNT -> DriveBackupError.ExpiredOrRevokedAuthorization()
                    CommonStatusCodes.NETWORK_ERROR,
                    CommonStatusCodes.TIMEOUT -> DriveBackupError.NetworkFailure(cause = throwable)
                    else -> {
                        val msg = throwable.message ?: ""
                        if (msg.contains("10:") || msg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
                            msg.contains("client_id", ignoreCase = true)
                        ) {
                            DriveBackupError.MissingConfiguration()
                        } else {
                            DriveBackupError.DriveApiFailure(
                                statusCode = throwable.statusCode,
                                message = "Google authorization error (status ${throwable.statusCode}): ${throwable.localizedMessage ?: "Unknown error"}",
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
