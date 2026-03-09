package com.mybrary.app.domain.model

data class UserLibrary(
    val id: String,
    val name: String,
    val spreadsheetId: String,
    val folderId: String? = null,
    val icon: String = "📚", // emoji icon chosen by the user
)
