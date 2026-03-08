package com.mybrary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus

@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cover image
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!book.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = "Cover of ${book.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.authors.isNotEmpty()) {
                    Text(
                        text = book.authors.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(book.status)
                    if (!book.loanedTo.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Loaned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (book.location.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        Text(
                            text = book.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (book.status == ReadingStatus.READING && book.readingProgress > 0) {
                    LinearProgressIndicator(
                        progress = { book.readingProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: ReadingStatus) {
    val (label, color) = when (status) {
        ReadingStatus.READ -> "Read" to Color(0xFF2E7D32)
        ReadingStatus.READING -> "Reading" to Color(0xFF1565C0)
        ReadingStatus.TO_READ -> "To Read" to Color(0xFF6A1B9A)
        ReadingStatus.UNREAD -> "Unread" to Color(0xFF546E7A)
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}
