package com.imsharman.liber.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.imsharman.liber.AppContainer
import com.imsharman.liber.data.local.entity.BookWithCollections
import com.imsharman.liber.domain.model.BookFormat
import com.imsharman.liber.domain.model.LibrarySection
import com.imsharman.liber.domain.model.LibrarySortOrder
import com.imsharman.liber.domain.model.LibraryViewMode
import com.imsharman.liber.domain.model.ReaderSettings
import com.imsharman.liber.domain.model.ReadingStatus
import com.imsharman.liber.ui.bookdetail.MetadataEditorDialog
import com.imsharman.liber.ui.components.CompactBookRow
import com.imsharman.liber.ui.components.DetailedBookCard
import com.imsharman.liber.ui.components.GridBookCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    container: AppContainer,
    onOpenReader: (bookId: Long) -> Unit,
    onOpenDetails: (bookId: Long) -> Unit,
    onNavigateAnnotations: () -> Unit,
    onNavigateStats: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val settings by container.preferencesRepository.readerSettingsFlow.collectAsState(initial = ReaderSettings())
    val repo = container.libraryRepository
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var searchOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var createCollectionDialogOpen by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    var editingMetadataBook by remember { mutableStateOf<BookWithCollections?>(null) }
    var managingCollectionsBook by remember { mutableStateOf<BookWithCollections?>(null) }

    val openFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) viewModel.linkFolder(treeUri)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(
                        text = "Liber",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ebook, Comic & Document Reader",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                val readingNowCount = state.allBooks.count { it.book.readingStatus == ReadingStatus.READING_NOW }
                val favCount = state.allBooks.count { it.book.isFavorite }
                val toReadCount = state.allBooks.count { it.book.readingStatus == ReadingStatus.TO_READ }
                val haveReadCount = state.allBooks.count { it.book.readingStatus == ReadingStatus.HAVE_READ }

                val shelfEntries = listOf(
                    Triple(LibrarySection.READING_NOW, Icons.Default.AutoStories, readingNowCount),
                    Triple(LibrarySection.ALL_BOOKS, Icons.AutoMirrored.Filled.LibraryBooks, state.allBooks.size),
                    Triple(LibrarySection.FAVORITES, Icons.Default.Star, favCount),
                    Triple(LibrarySection.TO_READ, Icons.Default.Bookmark, toReadCount),
                    Triple(LibrarySection.HAVE_READ, Icons.Default.CheckCircle, haveReadCount),
                    Triple(LibrarySection.AUTHORS, Icons.Default.Person, state.authorGroups.size),
                    Triple(LibrarySection.SERIES, Icons.Default.Bookmarks, state.seriesGroups.size),
                    Triple(LibrarySection.COLLECTIONS, Icons.Default.CollectionsBookmark, state.collections.size),
                    Triple(LibrarySection.FOLDERS, Icons.Default.Folder, state.linkedFolders.size)
                )

                shelfEntries.forEach { (sec, icon, count) ->
                    NavigationDrawerItem(
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(sec.title) },
                        badge = { Text(count.toString()) },
                        selected = state.activeSection == sec,
                        onClick = {
                            viewModel.selectSection(sec)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
                    label = { Text("Quotes, Notes & Bookmarks") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateAnnotations()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Insights, contentDescription = null) },
                    label = { Text("Reading Stats & Streaks") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateStats()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings, Privacy & Sync") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateSettings()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                val subHeader = when {
                    state.activeSection == LibrarySection.AUTHORS && state.selectedAuthor != null -> state.selectedAuthor!!
                    state.activeSection == LibrarySection.SERIES && state.selectedSeries != null -> state.selectedSeries!!
                    state.activeSection == LibrarySection.COLLECTIONS && state.selectedCollectionId != null ->
                        state.collections.firstOrNull { it.id == state.selectedCollectionId }?.name ?: "Collection"
                    state.activeSection == LibrarySection.FOLDERS && state.selectedFolderUri != null ->
                        state.linkedFolders.firstOrNull { it.treeUriString == state.selectedFolderUri }?.displayName ?: "Folder"
                    else -> null
                }

                TopAppBar(
                    title = {
                        if (searchOpen) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = viewModel::setSearchQuery,
                                placeholder = { Text("Search title, author, series…") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = subHeader ?: state.activeSection.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (subHeader != null) {
                            IconButton(
                                onClick = { viewModel.selectSection(state.activeSection) }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to groups")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open shelves")
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (searchOpen) {
                                    viewModel.setSearchQuery("")
                                    searchOpen = false
                                } else {
                                    searchOpen = true
                                }
                            }
                        ) {
                            Icon(
                                if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }

                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false }
                            ) {
                                LibrarySortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (state.sortOrder == order) "✓ ${order.label}" else order.label
                                            )
                                        },
                                        onClick = {
                                            sortMenuOpen = false
                                            viewModel.setSortOrder(order)
                                        }
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(onClick = { viewMenuOpen = true }) {
                                Icon(
                                    when (state.viewMode) {
                                        LibraryViewMode.DETAILED_LIST -> Icons.Default.ViewAgenda
                                        LibraryViewMode.GRID -> Icons.Default.GridView
                                        LibraryViewMode.COMPACT_LIST -> Icons.AutoMirrored.Filled.FormatListBulleted
                                    },
                                    contentDescription = "View mode"
                                )
                            }
                            DropdownMenu(
                                expanded = viewMenuOpen,
                                onDismissRequest = { viewMenuOpen = false }
                            ) {
                                LibraryViewMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (state.viewMode == mode) "✓ ${mode.label}" else mode.label)
                                        },
                                        onClick = {
                                            viewMenuOpen = false
                                            viewModel.setViewMode(mode)
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { addMenuOpen = true },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Add Books") }
                    )
                    DropdownMenu(
                        expanded = addMenuOpen,
                        onDismissRequest = { addMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import Book Files…") },
                            leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                            onClick = {
                                addMenuOpen = false
                                openFilesLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Link Book Folder…") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                addMenuOpen = false
                                openFolderLauncher.launch(null)
                            }
                        )
                        if (state.activeSection == LibrarySection.COLLECTIONS) {
                            DropdownMenuItem(
                                text = { Text("New Collection Shelf…") },
                                leadingIcon = { Icon(Icons.Default.CollectionsBookmark, contentDescription = null) },
                                onClick = {
                                    addMenuOpen = false
                                    createCollectionDialogOpen = true
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (state.isImporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.statusBanner?.let { banner ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = banner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::dismissBanner,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Format filter chips bar
                FormatFilterRow(
                    selectedFormat = state.selectedFormatFilter,
                    onSelectFormat = viewModel::setFormatFilter
                )

                // Group index screens (Authors, Series, Collections, Folders) when no group is selected yet
                when {
                    state.activeSection == LibrarySection.AUTHORS && state.selectedAuthor == null && state.searchQuery.isBlank() -> {
                        AuthorsDirectoryList(
                            groups = state.authorGroups,
                            onSelectAuthor = viewModel::jumpToAuthor
                        )
                    }

                    state.activeSection == LibrarySection.SERIES && state.selectedSeries == null && state.searchQuery.isBlank() -> {
                        SeriesDirectoryList(
                            groups = state.seriesGroups,
                            hasAnyBooks = state.allBooks.isNotEmpty(),
                            onSelectSeries = viewModel::jumpToSeries,
                            onDetectSeries = viewModel::refreshMetadataAndSeries
                        )
                    }

                    state.activeSection == LibrarySection.COLLECTIONS && state.selectedCollectionId == null && state.searchQuery.isBlank() -> {
                        CollectionsDirectoryList(
                            collections = state.collections,
                            allBooks = state.allBooks,
                            onSelectCollection = viewModel::selectCollection,
                            onCreateCollectionClick = { createCollectionDialogOpen = true },
                            onDeleteCollection = { col -> scope.launch { repo.deleteCollection(col) } }
                        )
                    }

                    state.activeSection == LibrarySection.FOLDERS && state.selectedFolderUri == null && state.searchQuery.isBlank() -> {
                        ScopedFoldersDirectoryList(
                            folders = state.linkedFolders,
                            onSelectFolder = viewModel::selectFolder,
                            onLinkNewFolder = { openFolderLauncher.launch(null) },
                            onRescanAll = viewModel::rescanFolders,
                            onUnlinkFolder = { uri -> viewModel.unlinkFolder(uri, removeBooks = false) }
                        )
                    }

                    state.filteredBooks.isEmpty() -> {
                        EmptyLibraryState(
                            hasAnyBooksInDb = state.allBooks.isNotEmpty(),
                            activeSection = state.activeSection,
                            onImportFiles = { openFilesLauncher.launch(arrayOf("*/*")) },
                            onLinkFolder = { openFolderLauncher.launch(null) },
                            onLoadSampleBook = viewModel::loadSampleBook
                        )
                    }

                    else -> {
                        BooksContentArea(
                            books = state.filteredBooks,
                            viewMode = state.viewMode,
                            onOpenReader = onOpenReader,
                            onOpenDetails = onOpenDetails,
                            onAuthorClick = viewModel::jumpToAuthor,
                            onSeriesClick = viewModel::jumpToSeries,
                            onToggleFavorite = { b ->
                                scope.launch { repo.toggleFavorite(b.book.id, b.book.isFavorite) }
                            },
                            onSetStatus = { b, status ->
                                scope.launch { repo.setReadingStatus(b.book.id, status) }
                            },
                            onManageCollections = { managingCollectionsBook = it },
                            onEditMetadata = { editingMetadataBook = it },
                            onDeleteBook = { b ->
                                scope.launch { repo.deleteBookFromLibrary(b.book.id) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (createCollectionDialogOpen) {
        AlertDialog(
            onDismissRequest = { createCollectionDialogOpen = false },
            title = { Text("Create Collection Shelf") },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    label = { Text("Collection name (e.g., Sci-Fi, Study)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                FilledTonalButton(
                    enabled = newCollectionName.isNotBlank(),
                    onClick = {
                        val name = newCollectionName.trim()
                        scope.launch { repo.createCollection(name) }
                        newCollectionName = ""
                        createCollectionDialogOpen = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { createCollectionDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    editingMetadataBook?.let { item ->
        MetadataEditorDialog(
            book = item.book,
            offlineOnlyMode = settings.offlineOnlyMode,
            openLibraryService = container.openLibraryService,
            sandboxManager = container.sandboxManager,
            onDismiss = { editingMetadataBook = null },
            onSave = { title, author, series, sIdx, desc, pub, isbn, lang, tags, newCover, clearTypo ->
                scope.launch {
                    repo.updateBookMetadata(
                        bookId = item.book.id,
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
                    editingMetadataBook = null
                }
            }
        )
    }

    managingCollectionsBook?.let { target ->
        val freshTarget = state.allBooks.firstOrNull { it.book.id == target.book.id } ?: target
        ManageBookCollectionsDialog(
            bookWithCollections = freshTarget,
            allCollections = state.collections,
            onToggleCollection = { colId, assigned ->
                scope.launch { repo.setBookInCollection(freshTarget.book.id, colId, assigned) }
            },
            onCreateAndAssignCollection = { name ->
                scope.launch {
                    val colId = repo.createCollection(name)
                    if (colId > 0L) repo.setBookInCollection(freshTarget.book.id, colId, true)
                }
            },
            onDismiss = { managingCollectionsBook = null }
        )
    }
}

@Composable
private fun FormatFilterRow(
    selectedFormat: BookFormat?,
    onSelectFormat: (BookFormat?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFormat == null,
            onClick = { onSelectFormat(null) },
            label = { Text("All Formats") }
        )
        BookFormat.entries.forEach { fmt ->
            FilterChip(
                selected = selectedFormat == fmt,
                onClick = { onSelectFormat(if (selectedFormat == fmt) null else fmt) },
                label = { Text(fmt.displayName) }
            )
        }
    }
}

@Composable
private fun BooksContentArea(
    books: List<BookWithCollections>,
    viewMode: LibraryViewMode,
    onOpenReader: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onAuthorClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onToggleFavorite: (BookWithCollections) -> Unit,
    onSetStatus: (BookWithCollections, ReadingStatus) -> Unit,
    onManageCollections: (BookWithCollections) -> Unit,
    onEditMetadata: (BookWithCollections) -> Unit,
    onDeleteBook: (BookWithCollections) -> Unit
) {
    when (viewMode) {
        LibraryViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 116.dp),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(books, key = { it.book.id }) { item ->
                    GridBookCard(
                        item = item,
                        onReadClick = { onOpenReader(item.book.id) },
                        onDetailsClick = { onOpenDetails(item.book.id) },
                        onToggleFavorite = { onToggleFavorite(item) }
                    )
                }
            }
        }

        LibraryViewMode.COMPACT_LIST -> {
            LazyColumn(
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(books, key = { it.book.id }) { item ->
                    CompactBookRow(
                        item = item,
                        onReadClick = { onOpenReader(item.book.id) },
                        onDetailsClick = { onOpenDetails(item.book.id) }
                    )
                }
            }
        }

        LibraryViewMode.DETAILED_LIST -> {
            LazyColumn(
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(books, key = { it.book.id }) { item ->
                    DetailedBookCard(
                        item = item,
                        onReadClick = { onOpenReader(item.book.id) },
                        onDetailsClick = { onOpenDetails(item.book.id) },
                        onAuthorClick = onAuthorClick,
                        onSeriesClick = onSeriesClick,
                        onToggleFavorite = { onToggleFavorite(item) },
                        onSetStatus = { st -> onSetStatus(item, st) },
                        onManageCollections = { onManageCollections(item) },
                        onEditMetadata = { onEditMetadata(item) },
                        onDeleteBook = { onDeleteBook(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorsDirectoryList(
    groups: List<AuthorGroup>,
    onSelectAuthor: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(groups, key = { it.author }) { group ->
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAuthor(group.author) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(group.author, style = MaterialTheme.typography.titleMedium)
                    }
                    AssistChip(
                        onClick = { onSelectAuthor(group.author) },
                        label = { Text("${group.books.size} book${if (group.books.size == 1) "" else "s"}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesDirectoryList(
    groups: List<SeriesGroup>,
    hasAnyBooks: Boolean,
    onSelectSeries: (String) -> Unit,
    onDetectSeries: () -> Unit
) {
    if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "No series detected yet.",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Series tags from EPUB/FB2/CBZ files and Open Library are grouped automatically, or you can set Series & Book # in Edit Metadata.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasAnyBooks) {
                    FilledTonalButton(onClick = onDetectSeries) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Detect Series")
                    }
                }
            }
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDetectSeries) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh Series Info")
                }
            }
        }
        items(groups, key = { it.seriesName }) { group ->
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSeries(group.seriesName) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.seriesName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = group.books.map { it.book.author }.distinct().joinToString(", "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AssistChip(
                        onClick = { onSelectSeries(group.seriesName) },
                        label = { Text("${group.books.size} vol.") }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionsDirectoryList(
    collections: List<com.imsharman.liber.data.local.entity.CollectionEntity>,
    allBooks: List<BookWithCollections>,
    onSelectCollection: (Long) -> Unit,
    onCreateCollectionClick: () -> Unit,
    onDeleteCollection: (com.imsharman.liber.data.local.entity.CollectionEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            FilledTonalButton(onClick = onCreateCollectionClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Custom Collection")
            }
        }
        items(collections, key = { it.id }) { col ->
            val count = allBooks.count { b -> b.collections.any { it.id == col.id } }
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectCollection(col.id) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(col.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$count book${if (count == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onDeleteCollection(col) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete collection")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopedFoldersDirectoryList(
    folders: List<com.imsharman.liber.data.local.entity.LinkedFolderEntity>,
    onSelectFolder: (String) -> Unit,
    onLinkNewFolder: () -> Unit,
    onRescanAll: () -> Unit,
    onUnlinkFolder: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Linked Book Folders", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = "Link one or more folders on your device to keep your library synced with local book files.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onLinkNewFolder) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Link Folder")
                        }
                        if (folders.isNotEmpty()) {
                            OutlinedButton(onClick = onRescanAll) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rescan")
                            }
                        }
                    }
                }
            }
        }

        items(folders, key = { it.treeUriString }) { folder ->
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectFolder(folder.treeUriString) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(folder.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${folder.bookCount} book file${if (folder.bookCount == 1) "" else "s"} discovered",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onUnlinkFolder(folder.treeUriString) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Unlink folder")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    hasAnyBooksInDb: Boolean,
    activeSection: LibrarySection,
    onImportFiles: () -> Unit,
    onLinkFolder: () -> Unit,
    onLoadSampleBook: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp)
            )
            if (!hasAnyBooksInDb) {
                Text("Welcome to Liber", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Import EPUB, PDF, CBZ/CBR, MOBI/AZW3, FB2, DOCX, TXT, or Markdown files—or link a book folder to get started.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onImportFiles) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Books")
                    }
                    FilledTonalButton(onClick = onLinkFolder) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Link Folder")
                    }
                }
                TextButton(onClick = onLoadSampleBook) {
                    Text("Or load sample public-domain classic (Meditations)")
                }
            } else {
                Text(
                    text = "No matching books in ${activeSection.title}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Try clearing your format filter or search query.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
