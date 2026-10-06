package com.example.drive

import android.app.PendingIntent

/**
 * Narrow OAuth 2.0 scope for Google Drive per-file access.
 * Grants access ONLY to files created or opened by Novel Studio.
 */
const val GOOGLE_DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/**
 * Minimal local metadata persisted to remember the connected Google Drive state
 * and Novel Studio backup file identifiers.
 */
data class DriveBackupMetadata(
    val isConnected: Boolean = false,
    val connectedAccountEmail: String? = null,
    val lastBackupDriveFileId: String? = null,
    val lastBackupTimestampMillis: Long? = null,
    val knownBackupIds: Set<String> = emptySet()
)

/**
 * Metadata representing a Novel Studio backup file stored in Google Drive.
 */
data class DriveBackupFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val createdTimeMillis: Long,
    val modifiedTimeMillis: Long,
    val mimeType: String,
    val description: String? = null
)

/**
 * Outcome of initiating Google Identity Services OAuth authorization for Drive.
 */
sealed class DriveAuthorizationOutcome {
    data class Authorized(
        val accessToken: String,
        val accountEmail: String? = null
    ) : DriveAuthorizationOutcome()

    data class ResolutionRequired(
        val pendingIntent: PendingIntent
    ) : DriveAuthorizationOutcome()
}

/**
 * Categorized error types for Google Drive authorization and backup operations.
 */
sealed class DriveBackupError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    class CancelledAuthorization(
        message: String = "Google Drive authorization was cancelled."
    ) : DriveBackupError(message)

    class MissingConfiguration(
        message: String = "Google Cloud OAuth configuration is missing or incomplete. Ensure the Android OAuth 2.0 Client ID (package name + SHA-1) and Google Drive API are enabled in Google Cloud Console."
    ) : DriveBackupError(message)

    class ExpiredOrRevokedAuthorization(
        message: String = "Google Drive authorization has expired or been revoked. Please reconnect Google Drive."
    ) : DriveBackupError(message)

    class NetworkFailure(
        message: String = "Network error while communicating with Google Drive. Please check your internet connection and try again.",
        cause: Throwable? = null
    ) : DriveBackupError(message, cause)

    class DriveApiFailure(
        val statusCode: Int? = null,
        message: String = "Google Drive API request failed.",
        cause: Throwable? = null
    ) : DriveBackupError(message, cause)

    class InvalidBackupFile(
        message: String = "Selected file is not a valid or compatible Novel Studio database backup.",
        cause: Throwable? = null
    ) : DriveBackupError(message, cause)

    class PreRestoreBackupFailed(
        message: String = "Aborted restore because creating the local pre-restore safety backup failed.",
        cause: Throwable? = null
    ) : DriveBackupError(message, cause)

    class RestoreFailedWithRollback(
        message: String = "Database restore failed; rolled back to pre-restore local backup.",
        val rolledBackSuccessfully: Boolean = true,
        cause: Throwable? = null
    ) : DriveBackupError(message, cause)
}

/**
 * Summary of a completed Google Drive backup restore operation.
 */
data class DriveRestoreSummary(
    val backupFileId: String,
    val backupFileName: String,
    val restoredNodesCount: Int,
    val restoredCharactersCount: Int,
    val restoredEventsCount: Int,
    val preRestoreBackupPath: String?
)

/**
 * UI state representing the Google Drive backup & restore connection in Settings.
 */
data class DriveConnectionUiState(
    val isConnected: Boolean = false,
    val isAuthorizing: Boolean = false,
    val isBusy: Boolean = false,
    val isRestoring: Boolean = false,
    val restoringFileId: String? = null,
    val restoreProgressStep: String? = null,
    val connectedAccountLabel: String? = null,
    val lastBackupFileId: String? = null,
    val lastBackupTimestampMillis: Long? = null,
    val knownBackupCount: Int = 0,
    val remoteBackups: List<DriveBackupFile> = emptyList(),
    val lastRestoreSummary: DriveRestoreSummary? = null,
    val statusMessage: String? = null,
    val error: DriveBackupError? = null
)
