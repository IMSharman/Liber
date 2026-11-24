package com.imsharman.liber.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.imsharman.liber.data.local.LiberDatabase
import com.imsharman.liber.data.local.entity.BookCollectionCrossRef
import com.imsharman.liber.data.local.entity.BookEntity
import com.imsharman.liber.data.local.entity.BookWithCollections
import com.imsharman.liber.data.local.entity.BookmarkEntity
import com.imsharman.liber.data.local.entity.BookmarkWithBook
import com.imsharman.liber.data.local.entity.CollectionEntity
import com.imsharman.liber.data.local.entity.HighlightEntity
import com.imsharman.liber.data.local.entity.HighlightWithBook
import com.imsharman.liber.data.local.entity.LinkedFolderEntity
import com.imsharman.liber.data.local.entity.ReadingSessionEntity
import com.imsharman.liber.data.local.entity.ReadingSessionWithBook
import com.imsharman.liber.data.parser.BookParserDispatcher
import com.imsharman.liber.data.storage.InternalSandboxManager
import com.imsharman.liber.data.storage.ScopedFolderScanner
import com.imsharman.liber.domain.model.BookFormat
import com.imsharman.liber.domain.model.HighlightColor
import com.imsharman.liber.domain.model.ReadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

class LibraryRepository(
    private val context: Context,
    private val db: LiberDatabase,
    val sandboxManager: InternalSandboxManager,
    val parserDispatcher: BookParserDispatcher,
    private val folderScanner: ScopedFolderScanner
) {
    private val bookDao = db.bookDao()
    private val collectionDao = db.collectionDao()
    private val annotationDao = db.annotationDao()
    private val sessionDao = db.readingSessionDao()
    private val folderDao = db.linkedFolderDao()

    fun observeAllBooksWithCollections(): Flow<List<BookWithCollections>> =
        bookDao.observeAllBooksWithCollections()

    fun observeBookWithCollections(bookId: Long): Flow<BookWithCollections?> =
        bookDao.observeBookWithCollections(bookId)

    fun observeCollections(): Flow<List<CollectionEntity>> =
        collectionDao.observeCollections()

    fun observeLinkedFolders(): Flow<List<LinkedFolderEntity>> =
        folderDao.observeLinkedFolders()

    fun observeHighlightsForBook(bookId: Long): Flow<List<HighlightEntity>> =
        annotationDao.observeHighlightsForBook(bookId)

    fun observeAllHighlightsWithBook(): Flow<List<HighlightWithBook>> =
        annotationDao.observeAllHighlightsWithBook()

    fun observeBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>> =
        annotationDao.observeBookmarksForBook(bookId)

    fun observeAllBookmarksWithBook(): Flow<List<BookmarkWithBook>> =
        annotationDao.observeAllBookmarksWithBook()

    fun observeAllSessionsWithBook(): Flow<List<ReadingSessionWithBook>> =
        sessionDao.observeAllSessionsWithBook()

    fun observeSessionsForBook(bookId: Long): Flow<List<ReadingSessionEntity>> =
        sessionDao.observeSessionsForBook(bookId)

    suspend fun getBookById(bookId: Long): BookEntity? = bookDao.getBookById(bookId)

    /**
     * Imports explicitly selected document URIs (`OpenMultipleDocuments` or `ACTION_VIEW`).
     * Takes read-only persistable URI permission when supported and extracts metadata/cover
     * strictly from inside the book file itself.
     */
    suspend fun importExplicitUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var addedCount = 0
        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val id = importSingleUriInternal(uri, linkedFolderUri = null, relativeFolderPath = "")
            if (id != null && id > 0L) addedCount++
        }
        addedCount
    }

    suspend fun importOrOpenSingleUri(uri: Uri): Long? = withContext(Dispatchers.IO) {
        val existing = bookDao.getBookByUri(uri.toString())
        if (existing != null) return@withContext existing.id
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        importSingleUriInternal(uri, linkedFolderUri = null, relativeFolderPath = "")
            ?: bookDao.getBookByUri(uri.toString())?.id
    }

    /**
     * Links a user-selected SAF folder (`OpenDocumentTree`) and imports ONLY recognized book files inside it.
     */
    suspend fun linkAndScanFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val (folderName, discovered) = folderScanner.scanGrantedTree(treeUri)
        var newlyAdded = 0
        for (item in discovered) {
            val existing = bookDao.getBookByUri(item.uri.toString())
            if (existing == null) {
                val id = importSingleUriInternal(
                    uri = item.uri,
                    explicitFileName = item.fileName,
                    explicitSize = item.fileSizeBytes,
                    explicitFormat = item.format,
                    linkedFolderUri = treeUri.toString(),
                    relativeFolderPath = item.relativeFolderPath
                )
                if (id != null && id > 0L) newlyAdded++
            }
        }
        folderDao.upsertLinkedFolder(
            LinkedFolderEntity(
                treeUriString = treeUri.toString(),
                displayName = folderName,
                bookCount = discovered.size,
                lastScannedEpochMs = System.currentTimeMillis()
            )
        )
        newlyAdded
    }

    suspend fun rescanAllLinkedFolders(): Int = withContext(Dispatchers.IO) {
        var totalAdded = 0
        for (folder in folderDao.getLinkedFoldersSnapshot()) {
            totalAdded += linkAndScanFolder(Uri.parse(folder.treeUriString))
        }
        totalAdded
    }

    suspend fun unlinkFolder(treeUriString: String, removeImportedBooks: Boolean) =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(treeUriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (removeImportedBooks) {
                bookDao.deleteBooksFromLinkedFolder(treeUriString)
            }
            folderDao.deleteLinkedFolderByUri(treeUriString)
        }

    suspend fun importBundledSampleClassic(): Long? = withContext(Dispatchers.IO) {
        val sampleFile = SampleBookFactory.createMeditationsSampleEpub(sandboxManager.samplesDir)
        val uri = Uri.fromFile(sampleFile)
        val existing = bookDao.getBookByUri(uri.toString())
        if (existing != null) return@withContext existing.id
        importSingleUriInternal(
            uri = uri,
            explicitFileName = sampleFile.name,
            explicitSize = sampleFile.length(),
            explicitFormat = BookFormat.EPUB,
            linkedFolderUri = null,
            relativeFolderPath = ""
        )
    }

    private suspend fun importSingleUriInternal(
        uri: Uri,
        explicitFileName: String? = null,
        explicitSize: Long? = null,
        explicitFormat: BookFormat? = null,
        linkedFolderUri: String?,
        relativeFolderPath: String
    ): Long? {
        val uriStr = uri.toString()
        bookDao.getBookByUri(uriStr)?.let { return it.id }

        val (resolvedName, resolvedSize) = if (explicitFileName != null && explicitSize != null) {
            explicitFileName to explicitSize
        } else {
            queryDocumentDisplayNameAndSize(uri)
        }

        val format = explicitFormat ?: BookFormat.fromFileName(resolvedName)
            ?: inferFormatFromMimeType(context.contentResolver.getType(uri))
            ?: return null

        val fingerprint = computeBookFingerprint(uri, resolvedName, resolvedSize)
        val parsedMeta = parserDispatcher.parseMetadataForImport(uri, resolvedName, format)

        val internalCoverPath = parsedMeta.embeddedCoverBytes?.let { bytes ->
            sandboxManager.saveCoverBytes(bytes, "book_${fingerprint.take(12)}")
        }

        val seedColor = InternalSandboxManager.deterministicCoverColor("${parsedMeta.title}:${parsedMeta.author}")
        val now = System.currentTimeMillis()

        val entity = BookEntity(
            uriString = uriStr,
            fileName = resolvedName,
            fileSizeBytes = resolvedSize,
            contentFingerprint = fingerprint,
            format = format,
            title = parsedMeta.title,
            author = parsedMeta.author,
            seriesName = parsedMeta.seriesName,
            seriesIndex = parsedMeta.seriesIndex,
            description = parsedMeta.description,
            publisher = parsedMeta.publisher,
            isbn = parsedMeta.isbn,
            language = parsedMeta.language,
            tagsCsv = parsedMeta.tags.joinToString(", "),
            internalCoverPath = internalCoverPath,
            coverSeedColorArgb = seedColor,
            totalUnits = parsedMeta.totalUnits.coerceAtLeast(1),
            totalWords = parsedMeta.totalWords,
            linkedFolderUri = linkedFolderUri,
            relativeFolderPath = relativeFolderPath,
            addedAtEpochMs = now,
            updatedAtEpochMs = now
        )
        val insertedId = bookDao.insertBook(entity)
        return if (insertedId > 0L) insertedId else bookDao.getBookByUri(uriStr)?.id
    }

    suspend fun toggleFavorite(bookId: Long, currentFavorite: Boolean) {
        bookDao.setFavorite(bookId, !currentFavorite)
    }

    suspend fun setReadingStatus(bookId: Long, status: ReadingStatus) {
        bookDao.setReadingStatus(bookId, status)
    }

    suspend fun setPersonalRating(bookId: Long, rating: Int) {
        bookDao.setRating(bookId, rating.coerceIn(0, 5))
    }

    suspend fun updateBookMetadata(
        bookId: Long,
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
        clearCoverToTypographic: Boolean = false
    ) {
        val current = bookDao.getBookById(bookId) ?: return
        val finalCoverPath = when {
            clearCoverToTypographic -> {
                sandboxManager.deleteInternalCover(current.internalCoverPath)
                null
            }
            newInternalCoverPath != null && newInternalCoverPath != current.internalCoverPath -> {
                sandboxManager.deleteInternalCover(current.internalCoverPath)
                newInternalCoverPath
            }
            else -> current.internalCoverPath
        }
        bookDao.updateBook(
            current.copy(
                title = title.trim().ifEmpty { current.title },
                author = author.trim().ifEmpty { "Unknown Author" },
                seriesName = seriesName?.trim()?.takeIf { it.isNotEmpty() },
                seriesIndex = seriesIndex,
                description = description.trim(),
                publisher = publisher.trim(),
                isbn = isbn.trim(),
                language = language.trim(),
                tagsCsv = tagsCsv.trim(),
                internalCoverPath = finalCoverPath,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteBookFromLibrary(bookId: Long) = withContext(Dispatchers.IO) {
        val book = bookDao.getBookById(bookId) ?: return@withContext
        sandboxManager.deleteInternalCover(book.internalCoverPath)
        bookDao.deleteBookById(bookId)
    }

    suspend fun saveReadingPosition(
        bookId: Long,
        chapterIndex: Int,
        blockIndex: Int,
        scrollOffset: Int,
        pageIndex: Int,
        totalUnits: Int,
        totalWords: Int,
        progressFraction: Float
    ) {
        val existing = bookDao.getBookById(bookId) ?: return
        val clampedProgress = progressFraction.coerceIn(0f, 1f)
        val nextStatus = when {
            clampedProgress >= 0.985f -> ReadingStatus.HAVE_READ
            existing.readingStatus == ReadingStatus.HAVE_READ -> ReadingStatus.HAVE_READ
            else -> ReadingStatus.READING_NOW
        }
        bookDao.updateReadingPosition(
            bookId = bookId,
            chapterIndex = chapterIndex,
            blockIndex = blockIndex,
            scrollOffset = scrollOffset,
            pageIndex = pageIndex,
            totalUnits = totalUnits.coerceAtLeast(1),
            progressFraction = clampedProgress,
            status = nextStatus,
            nowMs = System.currentTimeMillis()
        )
        if (totalWords > 0 && existing.totalWords != totalWords) {
            bookDao.getBookById(bookId)?.let { latest ->
                bookDao.updateBook(latest.copy(totalWords = totalWords))
            }
        }
    }

    suspend fun recordReadingSession(
        bookId: Long,
        startedAtEpochMs: Long,
        durationSeconds: Int,
        pagesTurned: Int,
        wordsRead: Int
    ) {
        if (durationSeconds < 5) return
        sessionDao.insertSession(
            ReadingSessionEntity(
                uuid = UUID.randomUUID().toString(),
                bookId = bookId,
                dateIso = LocalDate.now().toString(),
                startedAtEpochMs = startedAtEpochMs,
                durationSeconds = durationSeconds,
                pagesTurned = pagesTurned.coerceAtLeast(0),
                wordsRead = wordsRead.coerceAtLeast(0)
            )
        )
    }

    // Collections
    suspend fun createCollection(name: String): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return -1L
        return collectionDao.getCollectionByName(clean)?.id
            ?: collectionDao.insertCollection(CollectionEntity(name = clean))
    }

    suspend fun deleteCollection(collection: CollectionEntity) {
        collectionDao.deleteCollection(collection)
    }

    suspend fun setBookInCollection(bookId: Long, collectionId: Long, assigned: Boolean) {
        if (assigned) {
            collectionDao.addBookToCollection(BookCollectionCrossRef(bookId, collectionId))
        } else {
            collectionDao.removeBookFromCollection(bookId, collectionId)
        }
    }

    // Highlights & Bookmarks
    suspend fun saveHighlight(
        existingId: Long = 0L,
        existingUuid: String? = null,
        bookId: Long,
        chapterIndex: Int,
        chapterTitle: String,
        blockIndex: Int,
        pageIndex: Int = 0,
        quoteText: String,
        noteText: String,
        color: HighlightColor
    ) {
        val now = System.currentTimeMillis()
        annotationDao.upsertHighlight(
            HighlightEntity(
                id = existingId,
                uuid = existingUuid ?: UUID.randomUUID().toString(),
                bookId = bookId,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                blockIndex = blockIndex,
                pageIndex = pageIndex,
                quoteText = quoteText.trim(),
                noteText = noteText.trim(),
                color = color,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
    }

    suspend fun deleteHighlight(highlightId: Long) {
        annotationDao.deleteHighlightById(highlightId)
    }

    suspend fun addBookmark(
        bookId: Long,
        chapterIndex: Int,
        chapterTitle: String,
        blockIndex: Int,
        pageIndex: Int,
        progressFraction: Float,
        label: String,
        snippet: String
    ) {
        annotationDao.upsertBookmark(
            BookmarkEntity(
                uuid = UUID.randomUUID().toString(),
                bookId = bookId,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                blockIndex = blockIndex,
                pageIndex = pageIndex,
                progressFraction = progressFraction.coerceIn(0f, 1f),
                label = label.trim().ifEmpty { chapterTitle },
                snippet = snippet.take(180)
            )
        )
    }

    suspend fun deleteBookmark(bookmarkId: Long) {
        annotationDao.deleteBookmarkById(bookmarkId)
    }

    private fun queryDocumentDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        if (uri.scheme == "file") {
            val f = File(uri.path.orEmpty())
            return f.name.ifBlank { "book.epub" } to f.length()
        }
        var name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Document"
        var size = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        name = cursor.getString(nameIdx)
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return name to size
    }

    private fun inferFormatFromMimeType(mime: String?): BookFormat? = when (mime?.lowercase()) {
        "application/epub+zip" -> BookFormat.EPUB
        "application/pdf" -> BookFormat.PDF
        "application/vnd.comicbook+zip", "application/x-cbz" -> BookFormat.CBZ
        "application/vnd.comicbook-rar", "application/x-cbr" -> BookFormat.CBR
        "application/x-mobipocket-ebook" -> BookFormat.MOBI
        "application/x-fictionbook+xml" -> BookFormat.FB2
        "text/markdown" -> BookFormat.MARKDOWN
        "text/plain" -> BookFormat.TXT
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> BookFormat.DOCX
        else -> null
    }

    private fun computeBookFingerprint(uri: Uri, fileName: String, sizeBytes: Long): String {
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val stream = if (uri.scheme == "file") {
                File(uri.path.orEmpty()).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { input ->
                val buf = ByteArray(16 * 1024)
                val read = input.read(buf)
                if (read > 0) md.update(buf, 0, read)
            }
            md.update(sizeBytes.toString().toByteArray())
            md.digest().joinToString("") { "%02x".format(it) }.take(32)
        }.getOrElse {
            "${fileName.lowercase()}_$sizeBytes"
        }
    }
}
