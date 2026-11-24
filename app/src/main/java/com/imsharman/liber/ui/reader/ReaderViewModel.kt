package com.imsharman.liber.ui.reader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.imsharman.liber.AppContainer
import com.imsharman.liber.data.local.entity.BookEntity
import com.imsharman.liber.data.local.entity.BookmarkEntity
import com.imsharman.liber.data.local.entity.HighlightEntity
import com.imsharman.liber.data.parser.BookChapter
import com.imsharman.liber.data.parser.LoadedBookContent
import com.imsharman.liber.data.parser.TocEntry
import com.imsharman.liber.domain.model.HighlightColor
import com.imsharman.liber.domain.model.ReaderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReadingPositionSnapshot(
    val label: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val pageIndex: Int
)

data class InBookSearchHit(
    val chapterIndex: Int,
    val chapterTitle: String,
    val blockIndex: Int,
    val snippet: String
)

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: BookEntity? = null,
    val content: LoadedBookContent? = null,
    val currentChapterIndex: Int = 0,
    val currentBlockIndex: Int = 0,
    val currentPageIndex: Int = 0,
    val totalUnits: Int = 1,
    val progressFraction: Float = 0f,
    val jumpBackSpot: ReadingPositionSnapshot? = null,
    val searchQuery: String = "",
    val searchHits: List<InBookSearchHit> = emptyList(),
    val isSearching: Boolean = false
) {
    val activeReflowChapters: List<BookChapter>
        get() = when (val c = content) {
            is LoadedBookContent.Reflowable -> c.chapters
            is LoadedBookContent.VisualPaged -> c.reflowChapters
            null -> emptyList()
        }

    val activeToc: List<TocEntry>
        get() = content?.toc.orEmpty()
}

class ReaderViewModel(
    private val bookId: Long,
    private val initialJumpChapter: Int?,
    private val initialJumpPage: Int?,
    val container: AppContainer
) : ViewModel() {

    private val repo = container.libraryRepository
    val prefs = container.preferencesRepository

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val settings: StateFlow<ReaderSettings> = prefs.readerSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderSettings()
    )

    val highlights: StateFlow<List<HighlightEntity>> = repo.observeHighlightsForBook(bookId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val bookmarks: StateFlow<List<BookmarkEntity>> = repo.observeBookmarksForBook(bookId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var sessionStartMs: Long = System.currentTimeMillis()
    private var pagesTurnedInSession: Int = 0
    private var wordsReadInSession: Int = 0
    private val visitedChapterBlocks = HashSet<String>()

    init {
        loadBook(forcePdfReflow = null)
    }

    fun loadBook(forcePdfReflow: Boolean?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val entity = repo.getBookById(bookId) ?: run {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }
            val currentSettings = prefs.readerSettingsFlow.first()
            val usePdfReflow = forcePdfReflow ?: currentSettings.pdfReflowMode

            val loaded = container.parserDispatcher.loadBookContent(
                bookId = entity.id,
                uri = Uri.parse(entity.uriString),
                fileName = entity.fileName,
                format = entity.format,
                fallbackTitle = entity.title,
                pdfReflowRequested = usePdfReflow
            )

            val chapIdx = (initialJumpChapter ?: entity.currentChapterIndex)
                .coerceAtLeast(0)
            val pageIdx = (initialJumpPage ?: entity.currentPageIndex)
                .coerceAtLeast(0)
            val blockIdx = if (initialJumpChapter != null) 0 else entity.currentBlockIndex

            val units = when (loaded) {
                is LoadedBookContent.Reflowable -> loaded.chapters.size.coerceAtLeast(1)
                is LoadedBookContent.VisualPaged -> loaded.pageCount.coerceAtLeast(1)
            }

            val safeChap = if (loaded is LoadedBookContent.Reflowable) {
                chapIdx.coerceIn(0, (loaded.chapters.size - 1).coerceAtLeast(0))
            } else chapIdx

            val safePage = if (loaded is LoadedBookContent.VisualPaged) {
                pageIdx.coerceIn(0, (loaded.pageCount - 1).coerceAtLeast(0))
            } else pageIdx

            val initialProgress = computeProgress(loaded, safeChap, blockIdx, safePage)

            _uiState.value = ReaderUiState(
                isLoading = false,
                book = entity,
                content = loaded,
                currentChapterIndex = safeChap,
                currentBlockIndex = blockIdx,
                currentPageIndex = safePage,
                totalUnits = units,
                progressFraction = initialProgress
            )
            sessionStartMs = System.currentTimeMillis()
        }
    }

    fun onReflowPositionChanged(
        chapterIndex: Int,
        blockIndex: Int,
        subPageFraction: Float = 0f,
        recordJumpOrigin: Boolean = false
    ) {
        val current = _uiState.value
        val loaded = current.content ?: return
        val chapters = current.activeReflowChapters
        if (chapters.isEmpty()) return

        val safeChap = chapterIndex.coerceIn(0, chapters.lastIndex)
        val chap = chapters[safeChap]
        val safeBlock = blockIndex.coerceIn(0, (chap.blocks.size - 1).coerceAtLeast(0))

        val jumpOrigin = if (recordJumpOrigin) {
            val prevChapTitle = chapters.getOrNull(current.currentChapterIndex)?.title ?: "Previous spot"
            ReadingPositionSnapshot(
                label = prevChapTitle,
                chapterIndex = current.currentChapterIndex,
                blockIndex = current.currentBlockIndex,
                pageIndex = current.currentPageIndex
            )
        } else {
            current.jumpBackSpot
        }

        if (safeChap != current.currentChapterIndex || safeBlock != current.currentBlockIndex) {
            pagesTurnedInSession++
            val key = "$safeChap:$safeBlock"
            if (visitedChapterBlocks.add(key)) {
                wordsReadInSession += chap.blocks.getOrNull(safeBlock)?.wordCount() ?: 0
            }
        }

        val chapProgress = if (chap.blocks.size <= 1) {
            subPageFraction.coerceIn(0f, 1f)
        } else {
            ((safeBlock + subPageFraction) / chap.blocks.size.toFloat()).coerceIn(0f, 1f)
        }
        val overallProgress = ((safeChap + chapProgress) / chapters.size.toFloat()).coerceIn(0f, 1f)

        _uiState.value = current.copy(
            currentChapterIndex = safeChap,
            currentBlockIndex = safeBlock,
            progressFraction = overallProgress,
            jumpBackSpot = jumpOrigin
        )

        persistCurrentPositionAsync()
    }

    fun onVisualPageChanged(
        pageIndex: Int,
        recordJumpOrigin: Boolean = false
    ) {
        val current = _uiState.value
        val visual = current.content as? LoadedBookContent.VisualPaged ?: return
        val total = visual.pageCount.coerceAtLeast(1)
        val safePage = pageIndex.coerceIn(0, total - 1)

        val jumpOrigin = if (recordJumpOrigin) {
            ReadingPositionSnapshot(
                label = "Page ${current.currentPageIndex + 1}",
                chapterIndex = current.currentPageIndex,
                blockIndex = 0,
                pageIndex = current.currentPageIndex
            )
        } else {
            current.jumpBackSpot
        }

        if (safePage != current.currentPageIndex) {
            pagesTurnedInSession++
            wordsReadInSession += 180 // Approximate words per textbook/comic page for session velocity
        }

        val overall = if (total <= 1) 1f else (safePage + 1).toFloat() / total.toFloat()
        _uiState.value = current.copy(
            currentChapterIndex = safePage,
            currentPageIndex = safePage,
            progressFraction = overall.coerceIn(0f, 1f),
            jumpBackSpot = jumpOrigin
        )

        persistCurrentPositionAsync()
    }

    fun returnToJumpBackSpot() {
        val spot = _uiState.value.jumpBackSpot ?: return
        val content = _uiState.value.content
        _uiState.value = _uiState.value.copy(jumpBackSpot = null)
        if (content is LoadedBookContent.VisualPaged && !settings.value.pdfReflowMode) {
            onVisualPageChanged(spot.pageIndex, recordJumpOrigin = false)
        } else {
            onReflowPositionChanged(spot.chapterIndex, spot.blockIndex, recordJumpOrigin = false)
        }
    }

    fun dismissJumpBackSpot() {
        _uiState.value = _uiState.value.copy(jumpBackSpot = null)
    }

    fun scrubToFraction(targetFraction: Float) {
        val current = _uiState.value
        val content = current.content ?: return
        val f = targetFraction.coerceIn(0f, 1f)
        if (content is LoadedBookContent.VisualPaged && !settings.value.pdfReflowMode) {
            val targetPage = ((content.pageCount - 1) * f).toInt().coerceIn(0, (content.pageCount - 1).coerceAtLeast(0))
            onVisualPageChanged(targetPage, recordJumpOrigin = current.jumpBackSpot == null)
        } else {
            val chapters = current.activeReflowChapters
            if (chapters.isEmpty()) return
            val scaled = f * chapters.size
            val targetChap = scaled.toInt().coerceIn(0, chapters.lastIndex)
            val rem = scaled - targetChap
            val targetBlock = (rem * chapters[targetChap].blocks.size).toInt()
                .coerceIn(0, (chapters[targetChap].blocks.size - 1).coerceAtLeast(0))
            onReflowPositionChanged(
                chapterIndex = targetChap,
                blockIndex = targetBlock,
                recordJumpOrigin = current.jumpBackSpot == null
            )
        }
    }

    fun searchInBook(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.value = _uiState.value.copy(searchHits = emptyList(), isSearching = false)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            val chapters = _uiState.value.activeReflowChapters
            val hits = withContext(Dispatchers.Default) {
                val results = mutableListOf<InBookSearchHit>()
                val qLower = trimmed.lowercase()
                for (chap in chapters) {
                    for ((bIdx, block) in chap.blocks.withIndex()) {
                        val plain = block.plainText()
                        val matchIdx = plain.lowercase().indexOf(qLower)
                        if (matchIdx >= 0) {
                            val start = (matchIdx - 40).coerceAtLeast(0)
                            val end = (matchIdx + trimmed.length + 60).coerceAtMost(plain.length)
                            val prefix = if (start > 0) "…" else ""
                            val suffix = if (end < plain.length) "…" else ""
                            results += InBookSearchHit(
                                chapterIndex = chap.index,
                                chapterTitle = chap.title,
                                blockIndex = bIdx,
                                snippet = "$prefix${plain.substring(start, end)}$suffix"
                            )
                            if (results.size >= 100) break
                        }
                    }
                    if (results.size >= 100) break
                }
                results
            }
            _uiState.value = _uiState.value.copy(searchHits = hits, isSearching = false)
        }
    }

    fun saveHighlight(
        existing: HighlightEntity?,
        chapterIndex: Int,
        chapterTitle: String,
        blockIndex: Int,
        quoteText: String,
        noteText: String,
        color: HighlightColor
    ) {
        viewModelScope.launch {
            repo.saveHighlight(
                existingId = existing?.id ?: 0L,
                existingUuid = existing?.uuid,
                bookId = bookId,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                blockIndex = blockIndex,
                pageIndex = _uiState.value.currentPageIndex,
                quoteText = quoteText,
                noteText = noteText,
                color = color
            )
        }
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch { repo.deleteHighlight(id) }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repo.deleteBookmark(id) }
    }

    fun toggleBookmarkAtCurrentSpot() {
        val st = _uiState.value
        val existingHere = bookmarks.value.firstOrNull { bm ->
            bm.chapterIndex == st.currentChapterIndex &&
                (bm.blockIndex == st.currentBlockIndex || bm.pageIndex == st.currentPageIndex)
        }
        viewModelScope.launch {
            if (existingHere != null) {
                repo.deleteBookmark(existingHere.id)
            } else {
                val chap = st.activeReflowChapters.getOrNull(st.currentChapterIndex)
                val chapTitle = chap?.title ?: "Page ${st.currentPageIndex + 1}"
                val snippet = chap?.blocks?.getOrNull(st.currentBlockIndex)?.plainText().orEmpty()
                repo.addBookmark(
                    bookId = bookId,
                    chapterIndex = st.currentChapterIndex,
                    chapterTitle = chapTitle,
                    blockIndex = st.currentBlockIndex,
                    pageIndex = st.currentPageIndex,
                    progressFraction = st.progressFraction,
                    label = "$chapTitle (${(st.progressFraction * 100).toInt()}%)",
                    snippet = snippet
                )
            }
        }
    }

    fun flushReadingSession() {
        val now = System.currentTimeMillis()
        val elapsedSec = ((now - sessionStartMs) / 1000L).toInt()
        val turned = pagesTurnedInSession
        val words = wordsReadInSession
        sessionStartMs = now
        pagesTurnedInSession = 0
        wordsReadInSession = 0
        if (elapsedSec >= 5) {
            viewModelScope.launch {
                repo.recordReadingSession(
                    bookId = bookId,
                    startedAtEpochMs = now - elapsedSec * 1000L,
                    durationSeconds = elapsedSec,
                    pagesTurned = turned,
                    wordsRead = words
                )
            }
        }
    }

    private fun persistCurrentPositionAsync() {
        val st = _uiState.value
        val loaded = st.content ?: return
        viewModelScope.launch {
            repo.saveReadingPosition(
                bookId = bookId,
                chapterIndex = st.currentChapterIndex,
                blockIndex = st.currentBlockIndex,
                scrollOffset = 0,
                pageIndex = st.currentPageIndex,
                totalUnits = st.totalUnits,
                totalWords = loaded.metadata.totalWords,
                progressFraction = st.progressFraction
            )
        }
    }

    private fun computeProgress(
        loaded: LoadedBookContent,
        chapIdx: Int,
        blockIdx: Int,
        pageIdx: Int
    ): Float = when (loaded) {
        is LoadedBookContent.VisualPaged -> {
            val total = loaded.pageCount.coerceAtLeast(1)
            if (total <= 1) 0f else ((pageIdx + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
        is LoadedBookContent.Reflowable -> {
            val totalChaps = loaded.chapters.size.coerceAtLeast(1)
            val chap = loaded.chapters.getOrNull(chapIdx)
            val blockFrac = if (chap != null && chap.blocks.isNotEmpty()) {
                blockIdx.toFloat() / chap.blocks.size.toFloat()
            } else 0f
            ((chapIdx + blockFrac) / totalChaps.toFloat()).coerceIn(0f, 1f)
        }
    }

    companion object {
        fun factory(
            bookId: Long,
            jumpChapter: Int?,
            jumpPage: Int?,
            container: AppContainer
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(bookId, jumpChapter, jumpPage, container) as T
            }
        }
    }
}
