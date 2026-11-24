package com.imsharman.liber.data.export

import android.content.Context
import android.net.Uri
import com.imsharman.liber.data.local.LiberDatabase
import com.imsharman.liber.data.local.entity.BookCollectionCrossRef
import com.imsharman.liber.data.local.entity.BookmarkEntity
import com.imsharman.liber.data.local.entity.CollectionEntity
import com.imsharman.liber.data.local.entity.HighlightEntity
import com.imsharman.liber.data.local.entity.ReadingSessionEntity
import com.imsharman.liber.data.preferences.ReaderPreferencesRepository
import com.imsharman.liber.data.storage.InternalSandboxManager
import com.imsharman.liber.domain.model.HighlightColor
import com.imsharman.liber.domain.model.ReaderSettings
import com.imsharman.liber.domain.model.ReadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class SyncedBookState(
    val contentFingerprint: String,
    val title: String,
    val author: String,
    val seriesName: String? = null,
    val seriesIndex: Float? = null,
    val description: String = "",
    val publisher: String = "",
    val isbn: String = "",
    val language: String = "",
    val tagsCsv: String = "",
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    val isFavorite: Boolean = false,
    val personalRating: Int = 0,
    val currentChapterIndex: Int = 0,
    val currentBlockIndex: Int = 0,
    val currentScrollOffset: Int = 0,
    val currentPageIndex: Int = 0,
    val totalUnits: Int = 1,
    val progressFraction: Float = 0f,
    val collectionNames: List<String> = emptyList(),
    val lastReadAtEpochMs: Long? = null,
    val finishedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long = 0L,
    val coverEntryName: String? = null
)

@Serializable
data class SyncedHighlight(
    val uuid: String,
    val bookFingerprint: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val blockIndex: Int,
    val pageIndex: Int,
    val quoteText: String,
    val noteText: String,
    val color: HighlightColor,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Serializable
data class SyncedBookmark(
    val uuid: String,
    val bookFingerprint: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val blockIndex: Int,
    val pageIndex: Int,
    val progressFraction: Float,
    val label: String,
    val snippet: String,
    val createdAtEpochMs: Long
)

@Serializable
data class SyncedSession(
    val uuid: String,
    val bookFingerprint: String,
    val dateIso: String,
    val startedAtEpochMs: Long,
    val durationSeconds: Int,
    val pagesTurned: Int,
    val wordsRead: Int
)

@Serializable
data class LiberSyncPayload(
    val schemaVersion: Int = 1,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val settings: ReaderSettings,
    val collections: List<String>,
    val books: List<SyncedBookState>,
    val highlights: List<SyncedHighlight>,
    val bookmarks: List<SyncedBookmark>,
    val sessions: List<SyncedSession>
)

/**
 * Handles both:
 * 1. Local `.liberbackup` ZIP archive backup & restore (includes settings, books, shelves,
 *    highlights, notes, bookmarks, stats, AND internal private covers!).
 * 2. Cross-device WebDAV sync (`GET` + merge + `PUT` `liber_sync.json`) matching books across devices
 *    by `contentFingerprint` regardless of device-specific SAF URIs!
 */
class BackupAndSyncManager(
    private val context: Context,
    private val db: LiberDatabase,
    private val prefs: ReaderPreferencesRepository,
    private val sandboxManager: InternalSandboxManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun buildSyncPayload(includeSafeSettingsOnly: Boolean = true): LiberSyncPayload =
        withContext(Dispatchers.IO) {
            val rawSettings = prefs.readerSettingsFlow.first()
            val exportedSettings = if (includeSafeSettingsOnly) {
                rawSettings.copy(webDavPassword = "", customFontPath = null)
            } else {
                rawSettings
            }

            val allBooks = db.bookDao().getAllBooksSnapshot()
            val bookById = allBooks.associateBy { it.id }
            val allCollections = db.collectionDao().getAllCollectionsSnapshot()
            val collectionById = allCollections.associateBy { it.id }
            val crossRefs = db.collectionDao().getAllCrossRefsSnapshot().groupBy { it.bookId }

            val syncedBooks = allBooks.map { book ->
                val colNames = crossRefs[book.id].orEmpty()
                    .mapNotNull { collectionById[it.collectionId]?.name }
                val coverEntry = book.internalCoverPath?.let { path ->
                    val f = File(path)
                    if (f.exists()) f.name else null
                }
                SyncedBookState(
                    contentFingerprint = book.contentFingerprint,
                    title = book.title,
                    author = book.author,
                    seriesName = book.seriesName,
                    seriesIndex = book.seriesIndex,
                    description = book.description,
                    publisher = book.publisher,
                    isbn = book.isbn,
                    language = book.language,
                    tagsCsv = book.tagsCsv,
                    readingStatus = book.readingStatus,
                    isFavorite = book.isFavorite,
                    personalRating = book.personalRating,
                    currentChapterIndex = book.currentChapterIndex,
                    currentBlockIndex = book.currentBlockIndex,
                    currentScrollOffset = book.currentScrollOffset,
                    currentPageIndex = book.currentPageIndex,
                    totalUnits = book.totalUnits,
                    progressFraction = book.progressFraction,
                    collectionNames = colNames,
                    lastReadAtEpochMs = book.lastReadAtEpochMs,
                    finishedAtEpochMs = book.finishedAtEpochMs,
                    updatedAtEpochMs = book.updatedAtEpochMs,
                    coverEntryName = coverEntry
                )
            }

            val syncedHighlights = db.annotationDao().getAllHighlightsSnapshot().mapNotNull { hl ->
                val fp = bookById[hl.bookId]?.contentFingerprint ?: return@mapNotNull null
                SyncedHighlight(
                    uuid = hl.uuid,
                    bookFingerprint = fp,
                    chapterIndex = hl.chapterIndex,
                    chapterTitle = hl.chapterTitle,
                    blockIndex = hl.blockIndex,
                    pageIndex = hl.pageIndex,
                    quoteText = hl.quoteText,
                    noteText = hl.noteText,
                    color = hl.color,
                    createdAtEpochMs = hl.createdAtEpochMs,
                    updatedAtEpochMs = hl.updatedAtEpochMs
                )
            }

            val syncedBookmarks = db.annotationDao().getAllBookmarksSnapshot().mapNotNull { bm ->
                val fp = bookById[bm.bookId]?.contentFingerprint ?: return@mapNotNull null
                SyncedBookmark(
                    uuid = bm.uuid,
                    bookFingerprint = fp,
                    chapterIndex = bm.chapterIndex,
                    chapterTitle = bm.chapterTitle,
                    blockIndex = bm.blockIndex,
                    pageIndex = bm.pageIndex,
                    progressFraction = bm.progressFraction,
                    label = bm.label,
                    snippet = bm.snippet,
                    createdAtEpochMs = bm.createdAtEpochMs
                )
            }

            val syncedSessions = db.readingSessionDao().getAllSessionsSnapshot().mapNotNull { s ->
                val fp = bookById[s.bookId]?.contentFingerprint ?: return@mapNotNull null
                SyncedSession(
                    uuid = s.uuid,
                    bookFingerprint = fp,
                    dateIso = s.dateIso,
                    startedAtEpochMs = s.startedAtEpochMs,
                    durationSeconds = s.durationSeconds,
                    pagesTurned = s.pagesTurned,
                    wordsRead = s.wordsRead
                )
            }

            LiberSyncPayload(
                settings = exportedSettings,
                collections = allCollections.map { it.name },
                books = syncedBooks,
                highlights = syncedHighlights,
                bookmarks = syncedBookmarks,
                sessions = syncedSessions
            )
        }

    suspend fun exportLocalBackupZip(destinationUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildSyncPayload(includeSafeSettingsOnly = true)
            val payloadJson = json.encodeToString(payload)

            val outBytes = ByteArrayOutputStream()
            ZipOutputStream(outBytes).use { zos ->
                zos.putNextEntry(ZipEntry("liber_sync.json"))
                zos.write(payloadJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                sandboxManager.coversDir.listFiles()?.forEach { coverFile ->
                    if (coverFile.isFile) {
                        zos.putNextEntry(ZipEntry("covers/${coverFile.name}"))
                        zos.write(coverFile.readBytes())
                        zos.closeEntry()
                    }
                }
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                out.write(outBytes.toByteArray())
            } ?: error("Could not open destination URI for writing backup.")

            payload.books.size
        }
    }

    suspend fun restoreLocalBackupZip(sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rawZip = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: error("Could not open backup archive.")

            var syncJson: String? = null
            val restoredCoverPaths = HashMap<String, String>()

            ZipInputStream(ByteArrayInputStream(rawZip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when {
                            entry.name == "liber_sync.json" -> {
                                syncJson = zis.readBytes().toString(Charsets.UTF_8)
                            }
                            entry.name.startsWith("covers/") -> {
                                val fileName = entry.name.substringAfterLast('/')
                                val dest = File(sandboxManager.coversDir, fileName)
                                dest.writeBytes(zis.readBytes())
                                restoredCoverPaths[fileName] = dest.absolutePath
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val payload = json.decodeFromString<LiberSyncPayload>(
                syncJson ?: error("Archive does not contain liber_sync.json.")
            )
            val mergedCount = mergePayloadIntoLocalDatabase(payload, restoredCoverPaths, applySettings = true)
            "Restored settings, ${payload.collections.size} collections, and merged $mergedCount matching books."
        }
    }

    suspend fun syncWithWebDav(): Result<String> = withContext(Dispatchers.IO) {
        val currentSettings = prefs.readerSettingsFlow.first()
        if (currentSettings.offlineOnlyMode) {
            return@withContext Result.failure(
                IllegalStateException("Offline-Only Mode is active. Disable it in Settings to use WebDAV sync.")
            )
        }
        val baseUrl = currentSettings.webDavUrl.trim().trimEnd('/')
        if (baseUrl.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter a WebDAV server URL first (e.g. https://cloud.example.com/remote.php/dav/files/user/Liber).")
            )
        }

        runCatching {
            val targetFileUrl = if (baseUrl.endsWith(".json")) baseUrl else "$baseUrl/liber_sync.json"
            val authHeader = if (currentSettings.webDavUsername.isNotBlank()) {
                Credentials.basic(currentSettings.webDavUsername, currentSettings.webDavPassword)
            } else null

            // 1. Pull remote payload if it already exists
            val getReqBuilder = Request.Builder().url(targetFileUrl).get()
            if (authHeader != null) getReqBuilder.header("Authorization", authHeader)

            var mergedBooks = 0
            httpClient.newCall(getReqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val remoteText = resp.body?.string().orEmpty()
                    if (remoteText.isNotBlank()) {
                        val remotePayload = json.decodeFromString<LiberSyncPayload>(remoteText)
                        mergedBooks = mergePayloadIntoLocalDatabase(
                            remotePayload,
                            restoredCoverMap = emptyMap(),
                            applySettings = false
                        )
                    }
                } else if (resp.code != 404) {
                    error("WebDAV GET failed with HTTP ${resp.code}")
                }
            }

            // 2. Push unified merged payload back to WebDAV
            val updatedPayload = buildSyncPayload(includeSafeSettingsOnly = true)
            val jsonBody = json.encodeToString(updatedPayload)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val putReqBuilder = Request.Builder().url(targetFileUrl).put(jsonBody)
            if (authHeader != null) putReqBuilder.header("Authorization", authHeader)

            httpClient.newCall(putReqBuilder.build()).execute().use { putResp ->
                if (!putResp.isSuccessful && putResp.code !in listOf(200, 201, 204)) {
                    error("WebDAV PUT failed with HTTP ${putResp.code}")
                }
            }

            val now = System.currentTimeMillis()
            prefs.updateReaderSettings { it.copy(lastWebDavSyncEpochMs = now) }
            "WebDAV sync complete (${updatedPayload.books.size} books uploaded, $mergedBooks local books updated)."
        }
    }

    private suspend fun mergePayloadIntoLocalDatabase(
        payload: LiberSyncPayload,
        restoredCoverMap: Map<String, String>,
        applySettings: Boolean
    ): Int {
        if (applySettings) {
            val existing = prefs.readerSettingsFlow.first()
            prefs.replaceReaderSettings(
                payload.settings.copy(
                    webDavUrl = existing.webDavUrl.ifBlank { payload.settings.webDavUrl },
                    webDavUsername = existing.webDavUsername.ifBlank { payload.settings.webDavUsername },
                    webDavPassword = existing.webDavPassword,
                    customFontPath = existing.customFontPath
                )
            )
        }

        // Ensure collections exist
        val collectionIdByName = HashMap<String, Long>()
        for (colName in payload.collections) {
            val existingCol = db.collectionDao().getCollectionByName(colName)
            val id = existingCol?.id ?: db.collectionDao().insertCollection(CollectionEntity(name = colName))
            collectionIdByName[colName] = id
        }

        var updatedBookCount = 0
        val bookIdByFingerprint = HashMap<String, Long>()
        for (local in db.bookDao().getAllBooksSnapshot()) {
            bookIdByFingerprint[local.contentFingerprint] = local.id
        }

        for (remoteBook in payload.books) {
            val local = db.bookDao().getBookByFingerprint(remoteBook.contentFingerprint) ?: continue
            bookIdByFingerprint[remoteBook.contentFingerprint] = local.id

            if (remoteBook.updatedAtEpochMs >= local.updatedAtEpochMs) {
                val restoredCover = remoteBook.coverEntryName?.let { restoredCoverMap[it] } ?: local.internalCoverPath
                db.bookDao().updateBook(
                    local.copy(
                        title = remoteBook.title,
                        author = remoteBook.author,
                        seriesName = remoteBook.seriesName,
                        seriesIndex = remoteBook.seriesIndex,
                        description = remoteBook.description.ifBlank { local.description },
                        publisher = remoteBook.publisher.ifBlank { local.publisher },
                        isbn = remoteBook.isbn.ifBlank { local.isbn },
                        language = remoteBook.language.ifBlank { local.language },
                        tagsCsv = remoteBook.tagsCsv.ifBlank { local.tagsCsv },
                        readingStatus = remoteBook.readingStatus,
                        isFavorite = remoteBook.isFavorite,
                        personalRating = remoteBook.personalRating,
                        currentChapterIndex = remoteBook.currentChapterIndex,
                        currentBlockIndex = remoteBook.currentBlockIndex,
                        currentScrollOffset = remoteBook.currentScrollOffset,
                        currentPageIndex = remoteBook.currentPageIndex,
                        progressFraction = maxOf(local.progressFraction, remoteBook.progressFraction),
                        internalCoverPath = restoredCover,
                        lastReadAtEpochMs = remoteBook.lastReadAtEpochMs ?: local.lastReadAtEpochMs,
                        finishedAtEpochMs = remoteBook.finishedAtEpochMs ?: local.finishedAtEpochMs,
                        updatedAtEpochMs = remoteBook.updatedAtEpochMs
                    )
                )
                updatedBookCount++
            }

            for (colName in remoteBook.collectionNames) {
                val colId = collectionIdByName[colName]
                    ?: db.collectionDao().getCollectionByName(colName)?.id
                    ?: db.collectionDao().insertCollection(CollectionEntity(name = colName))
                collectionIdByName[colName] = colId
                db.collectionDao().addBookToCollection(BookCollectionCrossRef(local.id, colId))
            }
        }

        for (hl in payload.highlights) {
            val localBookId = bookIdByFingerprint[hl.bookFingerprint] ?: continue
            val existing = db.annotationDao().getHighlightByUuid(hl.uuid)
            if (existing == null || hl.updatedAtEpochMs > existing.updatedAtEpochMs) {
                db.annotationDao().upsertHighlight(
                    HighlightEntity(
                        id = existing?.id ?: 0L,
                        uuid = hl.uuid,
                        bookId = localBookId,
                        chapterIndex = hl.chapterIndex,
                        chapterTitle = hl.chapterTitle,
                        blockIndex = hl.blockIndex,
                        pageIndex = hl.pageIndex,
                        quoteText = hl.quoteText,
                        noteText = hl.noteText,
                        color = hl.color,
                        createdAtEpochMs = hl.createdAtEpochMs,
                        updatedAtEpochMs = hl.updatedAtEpochMs
                    )
                )
            }
        }

        for (bm in payload.bookmarks) {
            val localBookId = bookIdByFingerprint[bm.bookFingerprint] ?: continue
            val existing = db.annotationDao().getBookmarkByUuid(bm.uuid)
            if (existing == null) {
                db.annotationDao().upsertBookmark(
                    BookmarkEntity(
                        uuid = bm.uuid,
                        bookId = localBookId,
                        chapterIndex = bm.chapterIndex,
                        chapterTitle = bm.chapterTitle,
                        blockIndex = bm.blockIndex,
                        pageIndex = bm.pageIndex,
                        progressFraction = bm.progressFraction,
                        label = bm.label,
                        snippet = bm.snippet,
                        createdAtEpochMs = bm.createdAtEpochMs
                    )
                )
            }
        }

        for (ses in payload.sessions) {
            val localBookId = bookIdByFingerprint[ses.bookFingerprint] ?: continue
            val existing = db.readingSessionDao().getSessionByUuid(ses.uuid)
            if (existing == null) {
                db.readingSessionDao().insertSession(
                    ReadingSessionEntity(
                        uuid = ses.uuid,
                        bookId = localBookId,
                        dateIso = ses.dateIso,
                        startedAtEpochMs = ses.startedAtEpochMs,
                        durationSeconds = ses.durationSeconds,
                        pagesTurned = ses.pagesTurned,
                        wordsRead = ses.wordsRead
                    )
                )
            }
        }

        return updatedBookCount
    }
}
