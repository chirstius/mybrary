package com.mybrary.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey val name: String,
)

@Dao
interface GenreDao {
    @Query("SELECT * FROM genres ORDER BY name ASC")
    fun observeAll(): Flow<List<GenreEntity>>

    @Query("SELECT name FROM genres ORDER BY name ASC")
    suspend fun getAll(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(genre: GenreEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(genres: List<GenreEntity>)

    @Query("DELETE FROM genres WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("SELECT COUNT(*) FROM genres WHERE name = :name")
    suspend fun exists(name: String): Int
}
