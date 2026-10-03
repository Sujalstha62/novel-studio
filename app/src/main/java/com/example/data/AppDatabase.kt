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
                // Perform safety backup with WAL checkpoint before opening/migrating database
                backupDatabaseSafely(context)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novel_writer_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // NEVER use destructive migration for user manuscript data!
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Checkpoints any pending SQLite Write-Ahead Log (WAL) data and creates a consistent
         * backup of the database files (.db, -wal, -shm) prior to executing schema migrations.
         * This provides practical protection against unexpected migration failures.
         */
        private fun backupDatabaseSafely(context: Context) {
            try {
                val dbFile = context.getDatabasePath("novel_writer_database")
                if (dbFile != null && dbFile.exists() && dbFile.length() > 0) {
                    // 1. Attempt WAL checkpoint flush to write pending log pages into the database file
                    try {
                        android.database.sqlite.SQLiteDatabase.openDatabase(
                            dbFile.path,
                            null,
                            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                        ).use { tempDb ->
                            tempDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                                if (cursor.moveToFirst()) {
                                    Log.d(TAG, "WAL checkpoint completed: busy=${cursor.getInt(0)}, log=${cursor.getInt(1)}, checkpointed=${cursor.getInt(2)}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "WAL checkpoint before backup completed with notice: ${e.message}")
                    }

                    // 2. Safely copy the database file and any active journal files to backups directory
                    val backupDir = File(context.filesDir, "database_backups").apply { mkdirs() }
                    val filesToBackup = listOf(
                        dbFile to File(backupDir, "novel_writer_database_preupgrade.bak"),
                        File(dbFile.path + "-wal") to File(backupDir, "novel_writer_database_preupgrade.bak-wal"),
                        File(dbFile.path + "-shm") to File(backupDir, "novel_writer_database_preupgrade.bak-shm")
                    )

                    for ((src, dest) in filesToBackup) {
                        if (src.exists() && src.length() > 0) {
                            FileInputStream(src).use { input ->
                                FileOutputStream(dest).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                    Log.i(TAG, "Pre-migration database backup created successfully at ${backupDir.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pre-migration backup notice: ${e.message}", e)
            }
        }
    }
}
