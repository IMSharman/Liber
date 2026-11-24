package com.imsharman.liber.ui.annotations

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imsharman.liber.AppContainer
import com.imsharman.liber.data.export.NotesMarkdownExporter
import com.imsharman.liber.domain.model.HighlightColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsHubScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onJumpToBookSpot: (bookId: Long, chapterIndex: Int, pageIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = container.libraryRepository

    val allHighlights by repo.observeAllHighlightsWithBook().collectAsState(initial = emptyList())
    val allBookmarks by repo.observeAllBookmarksWithBook().collectAsState(initial = emptyList())

    var activeTab by remember { mutableIntStateOf(0) }
    var colorFilter by remember { mutableStateOf<HighlightColor?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredHighlights = remember(allHighlights, colorFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        allHighlights.filter { entry ->
            val matchesColor = colorFilter == null || entry.highlight.color == colorFilter
            val matchesQuery = q.isEmpty() ||
                entry.highlight.quoteText.lowercase().contains(q) ||
                entry.highlight.noteText.lowercase().contains(q) ||
                entry.book.title.lowercase().contains(q)
            matchesColor && matchesQuery
        }
    }

    val saveAllMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val md = NotesMarkdownExporter.exportAllHighlightsMarkdown(filteredHighlights)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(md.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quotes, Notes & Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (filteredHighlights.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val md = NotesMarkdownExporter.exportAllHighlightsMarkdown(filteredHighlights)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/markdown"
                                    putExtra(Intent.EXTRA_SUBJECT, "Liber Quotes & Highlights")
                                    putExtra(Intent.EXTRA_TEXT, md)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Obsidian/Markdown Notes"))
                            }
                        ) {
                            Icon(Icons.Default.IosShare, contentDescription = "Share Markdown")
                        }
                        IconButton(onClick = { saveAllMdLauncher.launch("Liber_Highlights.md") }) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Export .md")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Quotes & Notes (${allHighlights.size})") }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Bookmarks (${allBookmarks.size})") }
                )
            }

            if (activeTab == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search quotes, notes, or book titles…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = colorFilter == null,
                        onClick = { colorFilter = null },
                        label = { Text("All Colors") }
                    )
                    HighlightColor.entries.forEach { hc ->
                        FilterChip(
                            selected = colorFilter == hc,
                            onClick = { colorFilter = if (colorFilter == hc) null else hc },
                            label = { Text(hc.label) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(hc.accentHex))
                                )
                            }
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filteredHighlights.isEmpty()) {
                        item {
                            Text(
                                text = "No highlights match this filter. While reading any book, long-press a paragraph to save multi-color highlights and notes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(filteredHighlights, key = { it.highlight.id }) { item ->
                            val hl = item.highlight
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onJumpToBookSpot(item.book.id, hl.chapterIndex, hl.pageIndex)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(Color(hl.color.accentHex))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${item.book.title} • ${hl.chapterTitle}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = { scope.launch { repo.deleteHighlight(hl.id) } },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "“${hl.quoteText}”",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontStyle = FontStyle.Italic
                                    )
                                    if (hl.noteText.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = hl.noteText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (allBookmarks.isEmpty()) {
                        item {
                            Text(
                                text = "No saved bookmarks yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(allBookmarks, key = { it.bookmark.id }) { entry ->
                            val bm = entry.bookmark
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onJumpToBookSpot(entry.book.id, bm.chapterIndex, bm.pageIndex)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(bm.label, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${entry.book.title} • ${bm.chapterTitle} (${(bm.progressFraction * 100).toInt()}%)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { scope.launch { repo.deleteBookmark(bm.id) } }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete bookmark")
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
