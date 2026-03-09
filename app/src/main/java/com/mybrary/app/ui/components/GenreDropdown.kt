package com.mybrary.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth

/**
 * Combobox for genre selection: shows a text field backed by a dropdown of known genres.
 * The user can type a new genre (not in the list) or select an existing one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    genres: List<String>,
    modifier: Modifier = Modifier,
    label: String = "Genre",
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = if (value.isBlank()) genres
    else genres.filter { it.contains(value, ignoreCase = true) }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Category, null) },
            trailingIcon = {
                if (filtered.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && filtered.isNotEmpty())
                }
            },
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded && filtered.isNotEmpty(),
                onDismissRequest = { expanded = false },
            ) {
                filtered.forEach { genre ->
                    DropdownMenuItem(
                        text = { Text(genre) },
                        onClick = {
                            onValueChange(genre)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
