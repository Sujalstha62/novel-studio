package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Database(
    entities = [
        ManuscriptNode::class,
        CharacterProfile::class,
        WritingSettings::class,
        StoryEvent::class,
        CharacterRelationship::class,
        ManuscriptCommit::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun manuscriptDao(): ManuscriptDao
    abstract fun characterDao(): CharacterDao
    abstract fun settingsDao(): SettingsDao
    abstract fun storyEventDao(): StoryEventDao
    abstract fun characterRelationshipDao(): CharacterRelationshipDao
    abstract fun manuscriptCommitDao(): ManuscriptCommitDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DATABASE_NAME = "novel_writer_database"
        private const val CURRENT_DB_VERSION = 3

        @Volatile
        var lastBackupResult: Result<File?>? = null
            private set

        /**
         * Checks whether a specific column exists in a given SQLite table.
         * Used to safely tolerate re-entered migrations without catching arbitrary SQL exceptions.
         */
        private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            db.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex != -1 && cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Executing safe migration 1 -> 2")
                if (!columnExists(db, "character_profiles", "novelId")) {
                    db.execSQL("ALTER TABLE character_profiles ADD COLUMN novelId INTEGER DEFAULT NULL")
                }
                if (!columnExists(db, "story_events", "novelId")) {
                    db.execSQL("ALTER TABLE story_events ADD COLUMN novelId INTEGER DEFAULT NULL")
                }
                if (!columnExists(db, "character_relationships", "novelId")) {
                    db.execSQL("ALTER TABLE character_relationships ADD COLUMN novelId INTEGER DEFAULT NULL")
                }
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS manuscript_commits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        nodeId INTEGER NOT NULL,
                        commitHash TEXT NOT NULL,
                        commitMessage TEXT NOT NULL,
                        contentSnapshot TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Executing safe migration 2 -> 3 (adding lastAnalyzedHash and isAutoAnalysisEnabled)")
                if (!columnExists(db, "manuscript_nodes", "lastAnalyzedHash")) {
                    db.execSQL("ALTER TABLE manuscript_nodes ADD COLUMN lastAnalyzedHash TEXT DEFAULT NULL")
                }
                if (!columnExists(db, "writing_settings", "isAutoAnalysisEnabled")) {
                    db.execSQL("ALTER TABLE writing_settings ADD COLUMN isAutoAnalysisEnabled INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    // Never block the UI thread (prevents FrameTracker / IME_INSETS_SHOW_ANIMATION timeouts)
                    Thread {
                        val backupResult = backupDatabaseSafely(appContext)
                        lastBackupResult = backupResult
                    }.start()
                } else {
                    // Perform safety backup with WAL checkpoint before opening/migrating database
                    val backupResult = backupDatabaseSafely(appContext)
                    lastBackupResult = backupResult
                    if (backupResult.isFailure) {
                        val err = backupResult.exceptionOrNull()
                        val message = "Aborted database initialization and migration because pre-migration local database backup failed: ${err?.message ?: "Unknown backup error"}"
                        Log.e(TAG, "CRITICAL: $message", err)
                        throw IllegalStateException(message, err)
                    }
                }

                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // NEVER use destructive migration for user manuscript data!
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Safely checkpoints and closes the active Room database instance and invalidates the singleton
         * reference so the underlying SQLite files can be replaced cleanly during a verified restore.
         */
        fun closeAndInvalidateDatabase() {
            synchronized(this) {
                val current = INSTANCE
                INSTANCE = null
                if (current != null) {
                    try {
                        if (current.isOpen) {
                            current.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "WAL checkpoint before closing Room database completed with notice: ${e.message}")
                    }
                    try {
                        current.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Closing Room database completed with notice: ${e.message}")
                    }
                }
            }
        }

        /**
         * Reopens Room after a database restore (or rollback) and verifies that the database opens,
         * passes SQLite integrity_check, and can be queried across Novel Studio tables.
         */
        fun reopenAndVerifyDatabase(context: Context): AppDatabase {
            return synchronized(this) {
                INSTANCE?.let { existing ->
                    try {
                        existing.close()
                    } catch (_: Exception) {
                    }
                    INSTANCE = null
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

                try {
                    val supportDb = instance.openHelper.writableDatabase
                    supportDb.query("PRAGMA integrity_check").use { cursor ->
                        val status = if (cursor.moveToFirst()) cursor.getString(0) else null
                        if (!status.equals("ok", ignoreCase = true)) {
                            throw IllegalStateException("Reopened database failed PRAGMA integrity_check: ${status ?: "no result"}")
                        }
                    }

                    // Verify core Novel Studio tables can be queried through SQLite/Room
                    val requiredTables = listOf(
                        "manuscript_nodes",
                        "character_profiles",
                        "writing_settings",
                        "story_events",
                        "character_relationships",
                        "manuscript_commits"
                    )
                    for (table in requiredTables) {
                        supportDb.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                            if (!cursor.moveToFirst()) {
                                throw IllegalStateException("Verification query failed on table '$table'")
                            }
                        }
                    }

                    INSTANCE = instance
                    instance
                } catch (e: Exception) {
                    try {
                        instance.close()
                    } catch (_: Exception) {
                    }
                    INSTANCE = null
                    throw e
                }
            }
        }

        /**
         * Safely checkpoints any pending SQLite Write-Ahead Log (WAL) data and creates a consistent,
         * recoverable local backup set inside a uniquely named backup directory prior to schema migrations.
         *
         * - Flushes pending WAL frames via PRAGMA wal_checkpoint(FULL)
         * - Creates a uniquely named directory under filesDir/database_backups so existing backups are never overwritten
         * - Preserves the primary database file along with any WAL/SHM/journal files required for recovery
         * - Verifies every copied file's existence and byte length
         * - Reports any backup failure explicitly via Result.failure and error logs rather than pretending success
         */
        fun backupDatabaseSafely(context: Context): Result<File?> {
            return try {
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                if (dbFile == null || !dbFile.exists() || dbFile.length() == 0L) {
                    return Result.success(null)
                }

                var existingVersion = 0
                var walCheckpointBusy = false

                // 1. Safely checkpoint/flush the SQLite WAL before copying files
                try {
                    android.database.sqlite.SQLiteDatabase.openDatabase(
                        dbFile.path,
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                    ).use { tempDb ->
                        existingVersion = tempDb.version
                        tempDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                            if (cursor.moveToFirst()) {
                                val busy = cursor.getInt(0)
                                val logFrames = cursor.getInt(1)
                                val checkpointedFrames = cursor.getInt(2)
                                walCheckpointBusy = (busy != 0)
                                if (walCheckpointBusy) {
                                    Log.w(
                                        TAG,
                                        "WAL checkpoint reported busy=$busy (log=$logFrames, checkpointed=$checkpointedFrames); active WAL/SHM files will be preserved for recovery."
                                    )
                                } else {
                                    Log.d(
                                        TAG,
                                        "WAL checkpoint completed cleanly: busy=$busy, log=$logFrames, checkpointed=$checkpointedFrames"
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    walCheckpointBusy = true
                    Log.w(
                        TAG,
                        "Could not run direct WAL checkpoint before backup (${e.message}); preserving all WAL/SHM/journal recovery files."
                    )
                }

                // 2. Create a uniquely named backup directory without ever overwriting an existing backup
                val backupsRoot = File(context.filesDir, "database_backups")
                if (!backupsRoot.exists() && !backupsRoot.mkdirs()) {
                    throw java.io.IOException("Failed to create root backup directory at ${backupsRoot.absolutePath}")
                }

                val timestamp = System.currentTimeMillis()
                val versionLabel = if (existingVersion > 0) "v${existingVersion}_to_v$CURRENT_DB_VERSION" else "v_to_v$CURRENT_DB_VERSION"
                var uniqueBackupDir: File
                var attempt = 0
                do {
                    val token = java.util.UUID.randomUUID().toString().take(8)
                    val dirName = if (attempt == 0) {
                        "backup_${versionLabel}_${timestamp}_$token"
                    } else {
                        "backup_${versionLabel}_${timestamp}_${token}_$attempt"
                    }
                    uniqueBackupDir = File(backupsRoot, dirName)
                    attempt++
                } while (uniqueBackupDir.exists() && attempt < 10)

                if (uniqueBackupDir.exists()) {
                    throw java.io.IOException("Refusing to overwrite existing backup directory: ${uniqueBackupDir.absolutePath}")
                }
                if (!uniqueBackupDir.mkdirs() || !uniqueBackupDir.isDirectory) {
                    throw java.io.IOException("Failed to create unique backup directory at ${uniqueBackupDir.absolutePath}")
                }

                // 3. Preserve the main database file and any WAL/SHM/journal files required for recovery
                val walFile = File(dbFile.path + "-wal")
                val shmFile = File(dbFile.path + "-shm")
                val journalFile = File(dbFile.path + "-journal")

                val filesToPreserve = buildList {
                    add(dbFile)
                    // Include WAL/SHM/journal if they exist and contain data, or if WAL checkpoint was busy/incomplete
                    if (walFile.exists() && (walFile.length() > 0L || walCheckpointBusy)) {
                        add(walFile)
                    }
                    if (shmFile.exists() && (shmFile.length() > 0L || walFile.length() > 0L || walCheckpointBusy)) {
                        add(shmFile)
                    }
                    if (journalFile.exists() && journalFile.length() > 0L) {
                        add(journalFile)
                    }
                }

                for (src in filesToPreserve) {
                    if (!src.exists() || !src.isFile) {
                        throw java.io.IOException("Source database file missing or unreadable: ${src.absolutePath}")
                    }
                    val expectedLength = src.length()
                    val dest = File(uniqueBackupDir, src.name)
                    if (dest.exists()) {
                        throw java.io.IOException("Refusing to overwrite existing backup file: ${dest.absolutePath}")
                    }

                    FileInputStream(src).use { input ->
                        FileOutputStream(dest, false).use { output ->
                            input.copyTo(output)
                            output.flush()
                            try {
                                output.fd.sync()
                            } catch (_: Exception) {
                                // FileDescriptor.sync() may not be supported on all virtual filesystems
                            }
                        }
                    }

                    if (!dest.exists() || dest.length() != expectedLength) {
                        throw java.io.IOException(
                            "Backup file verification failed for ${src.name}: expected $expectedLength bytes, found ${dest.length()} bytes"
                        )
                    }
                }

                val primaryBackupFile = File(uniqueBackupDir, dbFile.name)
                if (!primaryBackupFile.exists() || primaryBackupFile.length() <= 0L) {
                    throw java.io.IOException("Primary database backup file is missing or empty in ${uniqueBackupDir.absolutePath}")
                }

                Log.i(
                    TAG,
                    "Consistent pre-migration database backup created at ${uniqueBackupDir.absolutePath} (${filesToPreserve.size} file(s) preserved)"
                )
                Result.success(uniqueBackupDir)
            } catch (e: Exception) {
                Log.e(TAG, "Pre-migration database backup FAILED: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}
