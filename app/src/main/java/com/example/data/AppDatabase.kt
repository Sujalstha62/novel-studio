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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Executing safe migration 1 -> 2")
                try {
                    db.execSQL("ALTER TABLE character_profiles ADD COLUMN novelId INTEGER DEFAULT NULL")
                } catch (e: Exception) {
                    Log.w(TAG, "Column novelId in character_profiles might already exist: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE story_events ADD COLUMN novelId INTEGER DEFAULT NULL")
                } catch (e: Exception) {
                    Log.w(TAG, "Column novelId in story_events might already exist: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE character_relationships ADD COLUMN novelId INTEGER DEFAULT NULL")
                } catch (e: Exception) {
                    Log.w(TAG, "Column novelId in character_relationships might already exist: ${e.message}")
                }
                try {
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
                } catch (e: Exception) {
                    Log.w(TAG, "Error ensuring manuscript_commits table: ${e.message}")
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Executing safe migration 2 -> 3 (adding lastAnalyzedHash and isAutoAnalysisEnabled)")
                try {
                    db.execSQL("ALTER TABLE manuscript_nodes ADD COLUMN lastAnalyzedHash TEXT DEFAULT NULL")
                } catch (e: Exception) {
                    Log.w(TAG, "Column lastAnalyzedHash might already exist: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE writing_settings ADD COLUMN isAutoAnalysisEnabled INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    Log.w(TAG, "Column isAutoAnalysisEnabled might already exist: ${e.message}")
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Perform safety backup before opening/migrating database
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
         * Creates an automatic local backup of the database file prior to executing
         * schema migrations or database operations to guarantee absolute data protection.
         */
        private fun backupDatabaseSafely(context: Context) {
            try {
                val dbFile = context.getDatabasePath("novel_writer_database")
                if (dbFile != null && dbFile.exists() && dbFile.length() > 0) {
                    val backupDir = File(context.filesDir, "database_backups").apply { mkdirs() }
                    val backupFile = File(backupDir, "novel_writer_database_preupgrade.bak")
                    FileInputStream(dbFile).use { input ->
                        FileOutputStream(backupFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Pre-migration database backup created successfully at ${backupFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not create database backup: ${e.message}", e)
            }
        }
    }
}
