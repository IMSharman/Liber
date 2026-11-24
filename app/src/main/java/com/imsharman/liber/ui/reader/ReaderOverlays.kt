package com.imsharman.liber.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imsharman.liber.data.local.entity.BookmarkEntity
import com.imsharman.liber.data.local.entity.HighlightEntity
import com.imsharman.liber.data.parser.TocEntry
import com.imsharman.liber.domain.model.HighlightColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocAndBookmarksSheet(
    toc: List<TocEntry>,
    bookmarks: List<BookmarkEntity>,
    highlights: List<HighlightEntity>,
    currentChapterIndex: Int,
    onSelectToc: (TocEntry) -> Unit,
    onSelectBookmark: (BookmarkEntity) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onSelectHighlight: (HighlightEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(bottom = 20.dp)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Contents (${toc.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bookmarks (${bookmarks.size})") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Quotes (${highlights.size})") })
            }

            when (tab) {
                0 -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        items(toc) { entry ->
                            val isActive = entry.chapterIndex == currentChapterIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        onSelectToc(entry)
                                        onDismiss()
                                    }
                                    .padding(
                                        start = (10 + entry.depth * 14).dp,
                                        end = 10.dp,
                                        top = 10.dp,
                                        bottom = 10.dp
                                    )
                            ) {
                                Text(
                                    text = entry.title,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (bookmarks.isEmpty()) {
                            item {
                                Text(
                                    "Tap the Bookmark icon in the top bar while reading to mark pages.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(bookmarks, key = { it.id }) { bm ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectBookmark(bm)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(bm.label, fontWeight = FontWeight.SemiBold)
                                            if (bm.snippet.isNotBlank()) {
                                                Text(
                                                    bm.snippet,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (highlights.isEmpty()) {
                            item {
                                Text(
                                    "Long-press any paragraph in the book to highlight in 5 colors or add notes.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(highlights, key = { it.id }) { hl ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectHighlight(hl)
                                            onDismiss()
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "${hl.chapterTitle} • ${hl.color.label}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(hl.color.accentHex)
                                        )
                                        Text(
                                            "“${hl.quoteText}”",
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            fontStyle = FontStyle.Italic
                                        )
                                        if (hl.noteText.isNotBlank()) {
                                            Text(
                                                "Note: ${hl.noteText}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InBookSearchSheet(
    query: String,
    isSearching: Boolean,
    hits: List<InBookSearchHit>,
    onQueryChange: (String) -> Unit,
    onSelectHit: (InBookSearchHit) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search full text inside this book…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else if (query.length >= 2) {
                Text(
                    text = "${hits.size}${if (hits.size >= 100) "+" else ""} matches found",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hits) { hit ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectHit(hit)
                                onDismiss()
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                hit.chapterTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(hit.snippet, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightAndNoteDialog(
    quoteText: String,
    existingHighlight: HighlightEntity?,
    onSave: (color: HighlightColor, note: String) -> Unit,
    onDelete: (() -> Unit)?,
    onSpeakFromHere: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedColor by remember(existingHighlight) {
        mutableStateOf(existingHighlight?.color ?: HighlightColor.AMBER)
    }
    var noteText by remember(existingHighlight) {
        mutableStateOf(existingHighlight?.noteText.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Highlight, Note & Quote") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "“${quoteText.take(280)}${if (quoteText.length > 280) "…" else ""}”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic
                )

                // 5 Highlight Color Swatches (all unlocked for free!)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HighlightColor.entries.forEach { hc ->
                        val active = selectedColor == hc
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(hc.accentHex))
                                .border(
                                    width = if (active) 3.dp else 1.dp,
                                    color = if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hc }
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Personal note (optional, Markdown supported)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Book Quote", quoteText))
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                    OutlinedButton(
                        onClick = {
                            onSpeakFromHere()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Read Aloud")
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onSave(selectedColor, noteText) }) {
                Text("Save Highlight")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun FloatingTtsBar(
    ttsState: TtsUiState,
    speechRate: Float,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCycleSpeed: () -> Unit,
    onSelectSleepMinutes: (minutes: Int?, endOfChapter: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sleepMenuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous paragraph")
            }
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(38.dp)) {
                Icon(
                    if (ttsState.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play or pause TTS"
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next paragraph")
            }

            AssistChip(
                onClick = onCycleSpeed,
                label = { Text("${"%.2g".format(speechRate)}x") }
            )

            Box {
                IconButton(onClick = { sleepMenuOpen = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Timer, contentDescription = "Sleep Timer")
                }
                DropdownMenu(
                    expanded = sleepMenuOpen,
                    onDismissRequest = { sleepMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sleep Timer: Off") },
                        onClick = {
                            sleepMenuOpen = false
                            onSelectSleepMinutes(null, false)
                        }
                    )
                    listOf(15, 30, 45).forEach { m ->
                        DropdownMenuItem(
                            text = { Text("Stop in $m minutes") },
                            onClick = {
                                sleepMenuOpen = false
                                onSelectSleepMinutes(m, false)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Stop at End of Chapter") },
                        onClick = {
                            sleepMenuOpen = false
                            onSelectSleepMinutes(null, true)
                        }
                    )
                }
            }

            if (ttsState.sleepSecondsRemaining != null) {
                val mins = (ttsState.sleepSecondsRemaining / 60).coerceAtLeast(1)
                Text("${mins}m", style = MaterialTheme.typography.labelMedium)
            }

            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Stop TTS")
            }
        }
    }
}
