package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ManuscriptNode::class,
        CharacterProfile::class,
        WritingSettings::class,
        StoryEvent::class,
        CharacterRelationship::class,
        ManuscriptCommit::class
    ],
    version = 2,
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
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novel_writer_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
