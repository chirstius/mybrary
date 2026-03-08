package com.mybrary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val isbn: String,
    val isbn13: String?,
    val title: String,
    val authors: String,             // JSON array stored as string
    val publisher: String?,
    val publishedYear: Int?,
    val pages: Int?,
    val description: String?,
    val coverUrl: String?,
    val status: String,              // ReadingStatus name
    val readingProgress: Int,
    val notes: String,
    val location: String,
    val tags: String,                // JSON array stored as string
    val loanedTo: String?,
    val loanDueDate: String?,        // ISO date string
    val dateAdded: String,           // ISO datetime string
    val dateModified: String,
    val sheetRowIndex: Int?,
    val pendingSync: Boolean,
)

fun BookEntity.toDomain(): Book = Book(
    id = id,
    isbn = isbn,
    isbn13 = isbn13,
    title = title,
    authors = authors.fromJsonStringList(),
    publisher = publisher,
    publishedYear = publishedYear,
    pages = pages,
    description = description,
    coverUrl = coverUrl,
    status = ReadingStatus.valueOf(status),
    readingProgress = readingProgress,
    notes = notes,
    location = location,
    tags = tags.fromJsonStringList(),
    loanedTo = loanedTo,
    loanDueDate = loanDueDate?.let { LocalDate.parse(it) },
    dateAdded = LocalDateTime.parse(dateAdded),
    dateModified = LocalDateTime.parse(dateModified),
    sheetRowIndex = sheetRowIndex,
    pendingSync = pendingSync,
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    isbn = isbn,
    isbn13 = isbn13,
    title = title,
    authors = authors.toJsonString(),
    publisher = publisher,
    publishedYear = publishedYear,
    pages = pages,
    description = description,
    coverUrl = coverUrl,
    status = status.name,
    readingProgress = readingProgress,
    notes = notes,
    location = location,
    tags = tags.toJsonString(),
    loanedTo = loanedTo,
    loanDueDate = loanDueDate?.toString(),
    dateAdded = dateAdded.toString(),
    dateModified = dateModified.toString(),
    sheetRowIndex = sheetRowIndex,
    pendingSync = pendingSync,
)

private fun List<String>.toJsonString(): String =
    "[${joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"

private fun String.fromJsonStringList(): List<String> =
    if (isBlank() || this == "[]") emptyList()
    else removeSurrounding("[", "]")
        .split(",")
        .map { it.trim().removeSurrounding("\"").replace("\\\"", "\"") }
        .filter { it.isNotEmpty() }
