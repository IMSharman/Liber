package com.imsharman.liber.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imsharman.liber.data.export.NotesMarkdownExporter
import com.imsharman.liber.data.local.entity.BookWithCollections
import com.imsharman.liber.domain.model.ReadingStatus

/**
 * ReadEra-inspired rich Detailed Book Card, modernized with Material 3 Editorial polish:
 * - Cover thumbnail -> opens reader directly
 * - Clickable Author & Series `#index` badges -> filter library by that author/series
 * - Collection chips, reading progress bar, Favorite star, shelf status menu, Details & Read CTA
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailedBookCard(
    item: BookWithCollections,
    onReadClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onAuthorClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetStatus: (ReadingStatus) -> Unit,
    onManageCollections: () -> Unit,
    onEditMetadata: () -> Unit,
    onDeleteBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    val book = item.book
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onDetailsClick,
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                BookCoverImage(
                    title = book.title,
                    author = book.author,
                    format = book.format,
                    internalCoverPath = book.internalCoverPath,
                    seedColorArgb = book.coverSeedColorArgb,
                    modifier = Modifier
                        .width(78.dp)
                        .height(116.dp)
                        .clickable(onClick = onReadClick)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onAuthorClick(book.author) }
                    )

                    if (!book.seriesName.isNullOrBlank()) {
                        val idxLabel = book.seriesIndex?.let { " #${NotesMarkdownExporter.formatSeriesNumber(it)}" }.orEmpty()
                        Spacer(modifier = Modifier.height(3.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.clickable { onSeriesClick(book.seriesName) }
                        ) {
                            Text(
                                text = "${book.seriesName}$idxLabel",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (item.collections.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item.collections.take(3).forEach { col ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = col.name,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val pct = (book.progressFraction * 100).toInt().coerceIn(0, 100)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { book.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (book.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (book.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Book actions"
                            )
                        }
                    }

                    BookQuickMenu(
                        expanded = menuExpanded,
                        currentStatus = book.readingStatus,
                        onDismiss = { menuExpanded = false },
                        onSetStatus = onSetStatus,
                        onManageCollections = onManageCollections,
                        onEditMetadata = onEditMetadata,
                        onDetailsClick = onDetailsClick,
                        onDeleteBook = onDeleteBook
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = book.readingStatus.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (book.fileSizeBytes > 0) {
                        Text(
                            text = "• ${formatFileSize(book.fileSizeBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onDetailsClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Info", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onReadClick,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (book.progressFraction > 0.01f && book.progressFraction < 0.99f) "Continue" else "Read",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GridBookCard(
    item: BookWithCollections,
    onReadClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val book = item.book
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onReadClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BookCoverImage(
                title = book.title,
                author = book.author,
                format = book.format,
                internalCoverPath = book.internalCoverPath,
                seedColorArgb = book.coverSeedColorArgb,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
            )
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(30.dp)
            ) {
                Icon(
                    imageVector = if (book.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (book.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { book.progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onDetailsClick)
        )
        Text(
            text = book.author,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CompactBookRow(
    item: BookWithCollections,
    onReadClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val book = item.book
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onReadClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCoverImage(
            title = book.title,
            author = book.author,
            format = book.format,
            internalCoverPath = book.internalCoverPath,
            seedColorArgb = book.coverSeedColorArgb,
            showFormatBadge = false,
            modifier = Modifier
                .width(38.dp)
                .height(54.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${book.author} • ${book.format.displayName} • ${(book.progressFraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDetailsClick) {
            Icon(Icons.Default.Info, contentDescription = "Book Info")
        }
    }
}

@Composable
private fun BookQuickMenu(
    expanded: Boolean,
    currentStatus: ReadingStatus,
    onDismiss: () -> Unit,
    onSetStatus: (ReadingStatus) -> Unit,
    onManageCollections: () -> Unit,
    onEditMetadata: () -> Unit,
    onDetailsClick: () -> Unit,
    onDeleteBook: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ReadingStatus.entries.forEach { status ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (status == currentStatus) "✓ Shelf: ${status.label}" else "Move to ${status.label}"
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                },
                onClick = {
                    onDismiss()
                    onSetStatus(status)
                }
            )
        }
        DropdownMenuItem(
            text = { Text("Collections…") },
            leadingIcon = { Icon(Icons.Default.BookmarkAdded, contentDescription = null) },
            onClick = {
                onDismiss()
                onManageCollections()
            }
        )
        DropdownMenuItem(
            text = { Text("Edit Metadata & Cover…") },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = {
                onDismiss()
                onEditMetadata()
            }
        )
        DropdownMenuItem(
            text = { Text("Book Details & Notes") },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
            onClick = {
                onDismiss()
                onDetailsClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Remove from Liber", color = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismiss()
                onDeleteBook()
            }
        )
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${kb.toInt()} KB"
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}
