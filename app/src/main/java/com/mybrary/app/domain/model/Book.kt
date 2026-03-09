package com.mybrary.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class ReadingStatus { UNREAD, TO_READ, READING, READ, DNF }

data class Book(
    val id: String,
    val isbn: String,
    val isbn13: String? = null,
    val title: String,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val pages: Int? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val genre: String? = null,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val readingProgress: Int = 0,      // 0–100 percent
    val notes: String = "",
    val location: String = "",         // physical shelf/room location
    val tags: List<String> = emptyList(),
    val loanedTo: String? = null,
    val loanDueDate: LocalDate? = null,
    val dateAdded: LocalDateTime = LocalDateTime.now(),
    val dateModified: LocalDateTime = LocalDateTime.now(),
    val sheetRowIndex: Int? = null,    // row in Google Sheet; null until synced
    val pendingSync: Boolean = false,  // queued for upload
    val libraryId: String = "default", // which library this book belongs to
)
