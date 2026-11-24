package com.imsharman.liber.ui.bookdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imsharman.liber.data.export.NotesMarkdownExporter
import com.imsharman.liber.data.local.entity.BookEntity
import com.imsharman.liber.data.remote.BookLookupCandidate
import com.imsharman.liber.data.remote.OpenLibraryService
import com.imsharman.liber.data.storage.InternalSandboxManager
import com.imsharman.liber.ui.components.BookCoverImage
import kotlinx.coroutines.launch

@Composable
fun MetadataEditorDialog(
    book: BookEntity,
    offlineOnlyMode: Boolean,
    openLibraryService: OpenLibraryService,
    sandboxManager: InternalSandboxManager,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        author: String,
        seriesName: String?,
        seriesIndex: Float?,
        description: String,
        publisher: String,
        isbn: String,
        language: String,
        tagsCsv: String,
        newInternalCoverPath: String?,
        clearCoverToTypographic: Boolean
    ) -> Unit
) {
    val scope = rememberCoroutineScope()

    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var seriesName by remember(book.id) { mutableStateOf(book.seriesName.orEmpty()) }
    var seriesIndexText by remember(book.id) {
        mutableStateOf(book.seriesIndex?.let { NotesMarkdownExporter.formatSeriesNumber(it) }.orEmpty())
    }
    var description by remember(book.id) { mutableStateOf(book.description) }
    var publisher by remember(book.id) { mutableStateOf(book.publisher) }
    var isbn by remember(book.id) { mutableStateOf(book.isbn) }
    var language by remember(book.id) { mutableStateOf(book.language) }
    var tagsCsv by remember(book.id) { mutableStateOf(book.tagsCsv) }

    var previewCoverPath by remember(book.id) { mutableStateOf(book.internalCoverPath) }
    var clearToTypographic by remember(book.id) { mutableStateOf(false) }

    var lookupQuery by remember(book.id) {
        mutableStateOf(
            if (book.isbn.isNotBlank()) book.isbn else "${book.title} ${book.author}".trim()
        )
    }
    var isSearching by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<BookLookupCandidate>>(emptyList()) }

    val customCoverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedPath = sandboxManager.importExplicitCustomCover(uri, book.id)
                if (savedPath != null) {
                    previewCoverPath = savedPath
                    clearToTypographic = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Metadata & Cover") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cover Preview + Explicit Cover Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BookCoverImage(
                        title = title.ifBlank { book.title },
                        author = author.ifBlank { book.author },
                        format = book.format,
                        internalCoverPath = if (clearToTypographic) null else previewCoverPath,
                        seedColorArgb = book.coverSeedColorArgb,
                        modifier = Modifier
                            .width(72.dp)
                            .height(106.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(onClick = { customCoverPicker.launch("image/*") }) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Cover Image")
                        }
                        OutlinedButton(
                            onClick = {
                                previewCoverPath = null
                                clearToTypographic = true
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Typographic Cover")
                        }
                    }
                }

                HorizontalDivider()

                // Online Metadata Lookup (Open Library)
                Text(
                    text = "Automatic Book Info Lookup (Open Library)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = lookupQuery,
                        onValueChange = { lookupQuery = it },
                        label = { Text("Title, Author, or ISBN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        enabled = !isSearching && lookupQuery.isNotBlank(),
                        onClick = {
                            scope.launch {
                                isSearching = true
                                lookupError = null
                                openLibraryService.searchBooks(lookupQuery, offlineOnlyMode)
                                    .onSuccess { list ->
                                        candidates = list
                                        if (list.isEmpty()) lookupError = "No matching editions found."
                                    }
                                    .onFailure { err ->
                                        lookupError = err.message ?: "Lookup failed."
                                    }
                                isSearching = false
                            }
                        }
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }

                lookupError?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (candidates.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        candidates.take(4).forEach { cand ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            title = cand.title
                                            author = cand.author
                                            if (cand.publisher.isNotBlank()) publisher = cand.publisher
                                            if (cand.isbn.isNotBlank()) isbn = cand.isbn
                                            if (cand.subjects.isNotEmpty()) {
                                                tagsCsv = cand.subjects.joinToString(", ")
                                            }
                                            val desc = openLibraryService.fetchWorkDescription(
                                                cand.workKey,
                                                offlineOnlyMode
                                            )
                                            if (desc.isNotBlank()) description = desc

                                            cand.coverUrl?.let { url ->
                                                val bytes = openLibraryService.downloadCoverBytes(url, offlineOnlyMode)
                                                if (bytes != null) {
                                                    val saved = sandboxManager.saveCoverBytes(
                                                        bytes,
                                                        "ol_book_${book.id}"
                                                    )
                                                    if (saved != null) {
                                                        previewCoverPath = saved
                                                        clearToTypographic = false
                                                    }
                                                }
                                            }
                                            candidates = emptyList()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cand.title,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val yearStr = cand.firstPublishYear?.let { " ($it)" }.orEmpty()
                                        Text(
                                            text = "${cand.author}$yearStr",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = "Apply Metadata",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author(s)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = seriesName,
                        onValueChange = { seriesName = it },
                        label = { Text("Series Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1.8f)
                    )
                    OutlinedTextField(
                        value = seriesIndexText,
                        onValueChange = { seriesIndexText = it },
                        label = { Text("Book #") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = publisher,
                        onValueChange = { publisher = it },
                        label = { Text("Publisher") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = isbn,
                        onValueChange = { isbn = it },
                        label = { Text("ISBN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text("Language") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                    OutlinedTextField(
                        value = tagsCsv,
                        onValueChange = { tagsCsv = it },
                        label = { Text("Tags (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Synopsis / Description") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onSave(
                        title,
                        author,
                        seriesName.takeIf { it.isNotBlank() },
                        seriesIndexText.trim().toFloatOrNull(),
                        description,
                        publisher,
                        isbn,
                        language,
                        tagsCsv,
                        previewCoverPath,
                        clearToTypographic
                    )
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
