package com.mybrary.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN genre TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `genres` " +
            "(`name` TEXT NOT NULL, PRIMARY KEY(`name`))"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN libraryId TEXT NOT NULL DEFAULT 'default'")
    }
}

@Database(
    entities = [BookEntity::class, GenreEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun genreDao(): GenreDao
}
