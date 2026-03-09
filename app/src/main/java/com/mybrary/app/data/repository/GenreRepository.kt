package com.mybrary.app.data.repository

import com.mybrary.app.data.local.GenreDao
import com.mybrary.app.data.local.GenreEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenreRepository @Inject constructor(
    private val genreDao: GenreDao,
) {
    fun observeAll(): Flow<List<String>> =
        genreDao.observeAll().map { list -> list.map { it.name } }

    suspend fun getAll(): List<String> = genreDao.getAll()

    /** Returns true if the genre was newly added, false if it already existed. */
    suspend fun add(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        if (genreDao.exists(trimmed) > 0) return false
        genreDao.insert(GenreEntity(trimmed))
        return true
    }

    suspend fun addAll(names: List<String>) {
        val entities = names.map { it.trim() }.filter { it.isNotBlank() }.map { GenreEntity(it) }
        genreDao.insertAll(entities)
    }

    suspend fun delete(name: String) = genreDao.deleteByName(name)

    suspend fun exists(name: String): Boolean = genreDao.exists(name.trim()) > 0
}
