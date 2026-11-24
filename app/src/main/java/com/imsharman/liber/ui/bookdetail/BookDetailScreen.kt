package com.imsharman.liber.ui.bookdetail

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.imsharman.liber.data.parser.TocEntry
import com.imsharman.liber.domain.model.ReadingStatus
import com.imsharman.liber.ui.components.BookCoverImage
import com.imsharman.liber.ui.components.formatFileSize
import com.imsharman.liber.ui.library.ManageBookCollectionsDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    container: AppContainer,
    averageWpm: Int,
    onBack: () -> Unit,
    onOpenReader: (bookId: Long, jumpChapter: Int?, jumpPage: Int?) -> Unit,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = container.libraryRepository

    val bookWithCols by repo.observeBookWithCollections(bookId).collectAsState(initial = null)
    val allCollections by repo.observeCollections().collectAsState(initial = emptyList())
    val highlights by repo.observeHighlightsForBook(bookId).collectAsState(initial = emptyList())
    val bookmarks by repo.observeBookmarksForBook(bookId).collectAsState(initial = emptyList())
    val sessions by repo.observeSessionsForBook(bookId).collectAsState(initial = emptyList())
    val settings by container.preferencesRepository.readerSettingsFlow.collectAsState(
        initial = com.imsharman.liber.domain.model.ReaderSettings()
    )

    var tocEntries by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMetadataEditor by remember { mutableStateOf(false) }
    var showCollectionsDialog by remember { mutableStateOf(false) }

    val currentItem = bookWithCols
    val book = currentItem?.book

    LaunchedEffect(book?.id, book?.uriString) {
        val b = book ?: return@LaunchedEffect
        runCatching {
            val loaded = container.parserDispatcher.loadBookContent(
                bookId = b.id,
                uri = Uri.parse(b.uriString),
                fileName = b.fileName,
                format = b.format,
                fallbackTitle = b.title,
                pdfReflowRequested = false
            )
            tocEntries = loaded.toc
        }
    }

    val markdownSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { destUri ->
        val b = book ?: return@rememberLauncherForActivityResult
        if (destUri != null) {
            scope.launch {
                val md = NotesMarkdownExporter.exportSingleBookMarkdown(b, highlights, bookmarks)
                context.contentResolver.openOutputStream(destUri)?.use {
                    it.write(md.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (book != null) {
                        IconButton(onClick = { showCollectionsDialog = true }) {
                            Icon(Icons.Default.BookmarkAdded, contentDescription = "Collections")
                        }
                        IconButton(onClick = { showMetadataEditor = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Metadata")
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    repo.deleteBookFromLibrary(book.id)
                                    onBack()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Book")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (book == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading book details…")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
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
                            .width(114.dp)
                            .height(170.dp)
                            .clickable { onOpenReader(book.id, null, null) }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onAuthorSelected(book.author) }
                        )

                        if (!book.seriesName.isNullOrBlank()) {
                            val idxStr = book.seriesIndex?.let { " #${NotesMarkdownExporter.formatSeriesNumber(it)}" }.orEmpty()
                            Spacer(modifier = Modifier.height(4.dp))
                            AssistChip(
                                onClick = { onSeriesSelected(book.seriesName) },
                                label = { Text("${book.seriesName}$idxStr") }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        // 5-star personal rating + Favorite
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            (1..5).forEach { star ->
                                Icon(
                                    imageVector = if (star <= book.personalRating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Rate $star",
                                    tint = if (star <= book.personalRating) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            scope.launch {
                                                val next = if (book.personalRating == star) 0 else star
                                                repo.setPersonalRating(book.id, next)
                                            }
                                        }
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            FilterChip(
                                selected = book.isFavorite,
                                onClick = {
                                    scope.launch { repo.toggleFavorite(book.id, book.isFavorite) }
                                },
                                label = { Text(if (book.isFavorite) "Favorited" else "Favorite") }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onOpenReader(book.id, null, null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (book.progressFraction > 0.01f && book.progressFraction < 0.99f) {
                                    "Continue Reading (${(book.progressFraction * 100).toInt()}%)"
                                } else {
                                    "Start Reading"
                                }
                            )
                        }
                    }
                }
            }

            // Reading Shelf Selector + Progress & Estimated Time Left
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReadingStatus.entries.forEach { st ->
                                FilterChip(
                                    selected = book.readingStatus == st,
                                    onClick = {
                                        scope.launch { repo.setReadingStatus(book.id, st) }
                                    },
                                    label = { Text(st.label) }
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { book.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        val wordsRemaining = ((1f - book.progressFraction) * book.totalWords).toInt().coerceAtLeast(0)
                        val estMinsLeft = if (wordsRemaining > 0 && averageWpm > 0) {
                            (wordsRemaining / averageWpm).coerceAtLeast(1)
                        } else null

                        val metaSummary = buildList {
                            add("${(book.progressFraction * 100).toInt()}% complete")
                            add(book.format.displayName)
                            if (book.fileSizeBytes > 0) add(formatFileSize(book.fileSizeBytes))
                            if (estMinsLeft != null) {
                                val hrs = estMinsLeft / 60
                                val mins = estMinsLeft % 60
                                add(if (hrs > 0) "~${hrs}h ${mins}m left" else "~${mins}m left")
                            }
                        }.joinToString("  •  ")

                        Text(
                            text = metaSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (currentItem.collections.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                currentItem.collections.forEach { col ->
                                    AssistChip(
                                        onClick = { showCollectionsDialog = true },
                                        label = { Text(col.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tabs: Overview, Chapters (TOC), Quotes & Notes, Bookmarks
            item {
                val tabs = listOf(
                    "Overview",
                    "Chapters (${tocEntries.size})",
                    "Quotes (${highlights.size})",
                    "Bookmarks (${bookmarks.size})"
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label, maxLines = 1) }
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (book.description.isNotBlank()) {
                                Text("Synopsis", style = MaterialTheme.typography.titleMedium)
                                Text(book.description, style = MaterialTheme.typography.bodyLarge)
                            } else {
                                Text(
                                    text = "No synopsis available yet. Tap the Edit icon above to write one or fetch book details automatically from Open Library.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider()

                            Text("File & Edition Info", style = MaterialTheme.typography.titleMedium)
                            InfoLine("File name", book.fileName)
                            if (book.publisher.isNotBlank()) InfoLine("Publisher", book.publisher)
                            if (book.isbn.isNotBlank()) InfoLine("ISBN", book.isbn)
                            if (book.language.isNotBlank()) InfoLine("Language", book.language)
                            if (book.tagsCsv.isNotBlank()) InfoLine("Subjects / Tags", book.tagsCsv)
                            if (sessions.isNotEmpty()) {
                                val totalMins = (sessions.sumOf { it.durationSeconds } / 60).coerceAtLeast(1)
                                InfoLine("Reading time logged", "$totalMins min across ${sessions.size} sessions")
                            }
                        }
                    }
                }

                1 -> {
                    if (tocEntries.isEmpty()) {
                        item {
                            Text(
                                "Table of contents will appear once the book is opened.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(tocEntries) { toc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onOpenReader(book.id, toc.chapterIndex, toc.pageIndex)
                                    }
                                    .padding(
                                        start = (12 + toc.depth * 14).dp,
                                        end = 12.dp,
                                        top = 10.dp,
                                        bottom = 10.dp
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = toc.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                enabled = highlights.isNotEmpty() || bookmarks.isNotEmpty(),
                                onClick = {
                                    val md = NotesMarkdownExporter.exportSingleBookMarkdown(
                                        book,
                                        highlights,
                                        bookmarks
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/markdown"
                                        putExtra(Intent.EXTRA_SUBJECT, "${book.title} — Notes")
                                        putExtra(Intent.EXTRA_TEXT, md)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Export Notes (Markdown / Obsidian)"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Markdown")
                            }

                            OutlinedButton(
                                enabled = highlights.isNotEmpty() || bookmarks.isNotEmpty(),
                                onClick = {
                                    val safeFile = book.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
                                    markdownSaveLauncher.launch("${safeFile}_notes.md")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save .md File")
                            }
                        }
                    }

                    if (highlights.isEmpty()) {
                        item {
                            Text(
                                "No highlights or quotes yet. Long-press any paragraph while reading to highlight in 5 colors or attach personal notes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(highlights, key = { it.id }) { hl ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenReader(book.id, hl.chapterIndex, hl.pageIndex)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(Color(hl.color.accentHex))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = hl.chapterTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = { scope.launch { repo.deleteHighlight(hl.id) } },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete quote", modifier = Modifier.size(16.dp))
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

                3 -> {
                    if (bookmarks.isEmpty()) {
                        item {
                            Text(
                                "No bookmarks added yet. Tap the Bookmark ribbon at the top-right of the reader to save a spot.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(bookmarks, key = { it.id }) { bm ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenReader(book.id, bm.chapterIndex, bm.pageIndex)
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
                                        Text(
                                            "${bm.chapterTitle} • ${(bm.progressFraction * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { scope.launch { repo.deleteBookmark(bm.id) } }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove bookmark")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMetadataEditor && book != null) {
        MetadataEditorDialog(
            book = book,
            offlineOnlyMode = settings.offlineOnlyMode,
            openLibraryService = container.openLibraryService,
            sandboxManager = container.sandboxManager,
            onDismiss = { showMetadataEditor = false },
            onSave = { title, author, series, sIdx, desc, pub, isbn, lang, tags, newCover, clearTypo ->
                scope.launch {
                    repo.updateBookMetadata(
                        bookId = book.id,
                        title = title,
                        author = author,
                        seriesName = series,
                        seriesIndex = sIdx,
                        description = desc,
                        publisher = pub,
                        isbn = isbn,
                        language = lang,
                        tagsCsv = tags,
                        newInternalCoverPath = newCover,
                        clearCoverToTypographic = clearTypo
                    )
                    showMetadataEditor = false
                }
            }
        )
    }

    if (showCollectionsDialog && currentItem != null) {
        ManageBookCollectionsDialog(
            bookWithCollections = currentItem,
            allCollections = allCollections,
            onToggleCollection = { colId, assigned ->
                scope.launch { repo.setBookInCollection(currentItem.book.id, colId, assigned) }
            },
            onCreateAndAssignCollection = { name ->
                scope.launch {
                    val colId = repo.createCollection(name)
                    if (colId > 0L) repo.setBookInCollection(currentItem.book.id, colId, true)
                }
            },
            onDismiss = { showCollectionsDialog = false }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
