package com.imsharman.liber.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.imsharman.liber.AppContainer
import com.imsharman.liber.data.local.entity.BookWithCollections
import com.imsharman.liber.data.local.entity.CollectionEntity
import com.imsharman.liber.data.local.entity.LinkedFolderEntity
import com.imsharman.liber.domain.model.BookFormat
import com.imsharman.liber.domain.model.LibrarySection
import com.imsharman.liber.domain.model.LibrarySortOrder
import com.imsharman.liber.domain.model.LibraryViewMode
import com.imsharman.liber.domain.model.ReadingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthorGroup(
    val author: String,
    val books: List<BookWithCollections>
)

data class SeriesGroup(
    val seriesName: String,
    val books: List<BookWithCollections>
)

data class LibraryUiState(
    val allBooks: List<BookWithCollections> = emptyList(),
    val filteredBooks: List<BookWithCollections> = emptyList(),
    val activeSection: LibrarySection = LibrarySection.ALL_BOOKS,
    val selectedAuthor: String? = null,
    val selectedSeries: String? = null,
    val selectedCollectionId: Long? = null,
    val selectedFolderUri: String? = null,
    val selectedFormatFilter: BookFormat? = null,
    val searchQuery: String = "",
    val viewMode: LibraryViewMode = LibraryViewMode.DETAILED_LIST,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.LAST_READ,
    val collections: List<CollectionEntity> = emptyList(),
    val linkedFolders: List<LinkedFolderEntity> = emptyList(),
    val authorGroups: List<AuthorGroup> = emptyList(),
    val seriesGroups: List<SeriesGroup> = emptyList(),
    val isImporting: Boolean = false,
    val statusBanner: String? = null
)

private data class FilterCriteria(
    val section: LibrarySection = LibrarySection.ALL_BOOKS,
    val author: String? = null,
    val series: String? = null,
    val collectionId: Long? = null,
    val folderUri: String? = null,
    val formatFilter: BookFormat? = null,
    val query: String = ""
)

class LibraryViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repo = container.libraryRepository
    private val prefs = container.preferencesRepository

    private val filterState = MutableStateFlow(FilterCriteria())
    private val isImportingState = MutableStateFlow(false)
    private val bannerState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        repo.observeAllBooksWithCollections(),
        repo.observeCollections(),
        repo.observeLinkedFolders(),
        prefs.libraryViewModeFlow,
        prefs.librarySortOrderFlow,
        filterState,
        isImportingState,
        bannerState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allBooks = values[0] as List<BookWithCollections>
        @Suppress("UNCHECKED_CAST")
        val collections = values[1] as List<CollectionEntity>
        @Suppress("UNCHECKED_CAST")
        val folders = values[2] as List<LinkedFolderEntity>
        val viewMode = values[3] as LibraryViewMode
        val sortOrder = values[4] as LibrarySortOrder
        val criteria = values[5] as FilterCriteria
        val importing = values[6] as Boolean
        val banner = values[7] as String?

        val authorGroups = allBooks
            .groupBy { it.book.author.ifBlank { "Unknown Author" } }
            .map { (a, list) -> AuthorGroup(a, list.sortedBy { it.book.title.lowercase() }) }
            .sortedBy { it.author.lowercase() }

        val seriesGroups = allBooks
            .filter { !it.book.seriesName.isNullOrBlank() }
            .groupBy { it.book.seriesName!!.trim() }
            .map { (s, list) ->
                SeriesGroup(
                    s,
                    list.sortedWith(
                        compareBy<BookWithCollections> { it.book.seriesIndex ?: Float.MAX_VALUE }
                            .thenBy { it.book.title.lowercase() }
                    )
                )
            }
            .sortedBy { it.seriesName.lowercase() }

        val sectionFiltered = when (criteria.section) {
            LibrarySection.READING_NOW -> allBooks.filter { it.book.readingStatus == ReadingStatus.READING_NOW }
            LibrarySection.ALL_BOOKS -> allBooks
            LibrarySection.FAVORITES -> allBooks.filter { it.book.isFavorite }
            LibrarySection.TO_READ -> allBooks.filter { it.book.readingStatus == ReadingStatus.TO_READ }
            LibrarySection.HAVE_READ -> allBooks.filter { it.book.readingStatus == ReadingStatus.HAVE_READ }
            LibrarySection.AUTHORS -> {
                if (criteria.author != null) {
                    allBooks.filter { it.book.author.equals(criteria.author, ignoreCase = true) }
                } else allBooks
            }
            LibrarySection.SERIES -> {
                if (criteria.series != null) {
                    allBooks.filter { it.book.seriesName?.equals(criteria.series, ignoreCase = true) == true }
                } else allBooks.filter { !it.book.seriesName.isNullOrBlank() }
            }
            LibrarySection.COLLECTIONS -> {
                if (criteria.collectionId != null) {
                    allBooks.filter { b -> b.collections.any { it.id == criteria.collectionId } }
                } else allBooks
            }
            LibrarySection.FOLDERS -> {
                if (criteria.folderUri != null) {
                    allBooks.filter { it.book.linkedFolderUri == criteria.folderUri }
                } else allBooks.filter { it.book.linkedFolderUri != null }
            }
            else -> allBooks
        }

        val formatFiltered = if (criteria.formatFilter != null) {
            sectionFiltered.filter { it.book.format == criteria.formatFilter }
        } else {
            sectionFiltered
        }

        val q = criteria.query.trim().lowercase()
        val searchFiltered = if (q.isEmpty()) {
            formatFiltered
        } else {
            formatFiltered.filter { item ->
                val b = item.book
                b.title.lowercase().contains(q) ||
                    b.author.lowercase().contains(q) ||
                    (b.seriesName?.lowercase()?.contains(q) == true) ||
                    b.tagsCsv.lowercase().contains(q) ||
                    b.fileName.lowercase().contains(q)
            }
        }

        val sorted = sortBooks(
            books = searchFiltered,
            order = if (criteria.section == LibrarySection.SERIES && criteria.series != null) {
                LibrarySortOrder.SERIES_ORDER
            } else {
                sortOrder
            }
        )

        LibraryUiState(
            allBooks = allBooks,
            filteredBooks = sorted,
            activeSection = criteria.section,
            selectedAuthor = criteria.author,
            selectedSeries = criteria.series,
            selectedCollectionId = criteria.collectionId,
            selectedFolderUri = criteria.folderUri,
            selectedFormatFilter = criteria.formatFilter,
            searchQuery = criteria.query,
            viewMode = viewMode,
            sortOrder = sortOrder,
            collections = collections,
            linkedFolders = folders,
            authorGroups = authorGroups,
            seriesGroups = seriesGroups,
            isImporting = importing,
            statusBanner = banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    private fun sortBooks(
        books: List<BookWithCollections>,
        order: LibrarySortOrder
    ): List<BookWithCollections> = when (order) {
        LibrarySortOrder.LAST_READ -> books.sortedByDescending {
            it.book.lastReadAtEpochMs ?: it.book.addedAtEpochMs
        }
        LibrarySortOrder.DATE_ADDED -> books.sortedByDescending { it.book.addedAtEpochMs }
        LibrarySortOrder.TITLE -> books.sortedBy { it.book.title.lowercase() }
        LibrarySortOrder.AUTHOR -> books.sortedWith(
            compareBy<BookWithCollections> { it.book.author.lowercase() }
                .thenBy { it.book.seriesName?.lowercase().orEmpty() }
                .thenBy { it.book.seriesIndex ?: Float.MAX_VALUE }
                .thenBy { it.book.title.lowercase() }
        )
        LibrarySortOrder.PROGRESS -> books.sortedByDescending { it.book.progressFraction }
        LibrarySortOrder.SERIES_ORDER -> books.sortedWith(
            compareBy<BookWithCollections> { it.book.seriesName == null }
                .thenBy { it.book.seriesName?.lowercase().orEmpty() }
                .thenBy { it.book.seriesIndex ?: Float.MAX_VALUE }
                .thenBy { it.book.title.lowercase() }
        )
        LibrarySortOrder.FILE_SIZE -> books.sortedByDescending { it.book.fileSizeBytes }
    }

    fun selectSection(section: LibrarySection) {
        filterState.value = filterState.value.copy(
            section = section,
            author = null,
            series = null,
            collectionId = null,
            folderUri = null
        )
    }

    fun jumpToAuthor(author: String) {
        filterState.value = filterState.value.copy(
            section = LibrarySection.AUTHORS,
            author = author
        )
    }

    fun jumpToSeries(series: String) {
        filterState.value = filterState.value.copy(
            section = LibrarySection.SERIES,
            series = series
        )
    }

    fun selectCollection(collectionId: Long?) {
        filterState.value = filterState.value.copy(
            section = LibrarySection.COLLECTIONS,
            collectionId = collectionId
        )
    }

    fun selectFolder(folderUri: String?) {
        filterState.value = filterState.value.copy(
            section = LibrarySection.FOLDERS,
            folderUri = folderUri
        )
    }

    fun setFormatFilter(format: BookFormat?) {
        filterState.value = filterState.value.copy(formatFilter = format)
    }

    fun setSearchQuery(query: String) {
        filterState.value = filterState.value.copy(query = query)
    }

    fun setViewMode(mode: LibraryViewMode) {
        viewModelScope.launch { prefs.setLibraryViewMode(mode) }
    }

    fun setSortOrder(order: LibrarySortOrder) {
        viewModelScope.launch { prefs.setLibrarySortOrder(order) }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isImportingState.value = true
            val count = repo.importExplicitUris(uris)
            isImportingState.value = false
            bannerState.value = if (count > 0) {
                "Imported $count book${if (count == 1) "" else "s"} into Liber."
            } else {
                "No new supported book files found in selection."
            }
        }
    }

    fun linkFolder(treeUri: Uri) {
        viewModelScope.launch {
            isImportingState.value = true
            val count = repo.linkAndScanFolder(treeUri)
            isImportingState.value = false
            bannerState.value = "Linked folder scanned ($count new book${if (count == 1) "" else "s"} added)."
        }
    }

    fun rescanFolders() {
        viewModelScope.launch {
            isImportingState.value = true
            val count = repo.rescanAllLinkedFolders()
            isImportingState.value = false
            bannerState.value = "Rescan complete ($count new book${if (count == 1) "" else "s"} discovered)."
        }
    }

    fun unlinkFolder(treeUri: String, removeBooks: Boolean) {
        viewModelScope.launch {
            repo.unlinkFolder(treeUri, removeBooks)
            if (filterState.value.folderUri == treeUri) {
                selectFolder(null)
            }
            bannerState.value = "Folder permission revoked."
        }
    }

    fun loadSampleBook() {
        viewModelScope.launch {
            isImportingState.value = true
            repo.importBundledSampleClassic()
            isImportingState.value = false
            bannerState.value = "Loaded public-domain classic: Meditations by Marcus Aurelius."
        }
    }

    fun dismissBanner() {
        bannerState.value = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(container) as T
                }
            }
    }
}
