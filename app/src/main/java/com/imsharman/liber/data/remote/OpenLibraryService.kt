package com.imsharman.liber.data.remote

import com.imsharman.liber.domain.util.BookMetadataNormalizer
import com.imsharman.liber.domain.util.InferredSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class BookLookupCandidate(
    val workKey: String,
    val title: String,
    val author: String,
    val firstPublishYear: Int?,
    val publisher: String,
    val isbn: String,
    val subjects: List<String>,
    val coverId: Int?,
    val seriesName: String? = null
) {
    val coverUrl: String?
        get() = coverId?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" }
}

/**
 * Metadata, series, and cover lookup using the open, account-free Open Library API.
 * Completely disabled whenever `offlineOnlyMode` is enabled in Settings.
 */
class OpenLibraryService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchBooks(
        query: String,
        offlineOnlyMode: Boolean
    ): Result<List<BookLookupCandidate>> = withContext(Dispatchers.IO) {
        if (offlineOnlyMode) {
            return@withContext Result.failure(
                IllegalStateException("Offline-Only Mode is enabled in Settings. Disable it to query Open Library.")
            )
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext Result.success(emptyList())

        runCatching {
            val url = "https://openlibrary.org/search.json".toHttpUrl().newBuilder()
                .addQueryParameter("q", trimmed)
                .addQueryParameter("limit", "10")
                .addQueryParameter(
                    "fields",
                    "key,title,author_name,first_publish_year,publisher,isbn,subject,cover_i"
                )
                .build()

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "LiberReader/1.1 (Android)")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("Open Library returned HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val docs = root["docs"]?.jsonArray ?: JsonArray(emptyList())

                docs.mapNotNull { el ->
                    val obj = el.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                    val workKey = obj["key"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val rawAuthors = obj["author_name"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                        ?.take(3)
                        ?.joinToString("; ")
                        .orEmpty()
                    val author = BookMetadataNormalizer.normalizeAuthor(rawAuthors)
                    val year = obj["first_publish_year"]?.jsonPrimitive?.intOrNull
                    val publisher = obj["publisher"]?.jsonArray
                        ?.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty()
                    val isbn = obj["isbn"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?.firstOrNull { it.length == 13 || it.length == 10 }
                        .orEmpty()
                    val allSubjects = obj["subject"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                        .orEmpty()
                    val detectedSeries = extractSeriesNameFromSubjects(allSubjects)
                    val cleanSubjects = allSubjects
                        .filterNot { it.contains(':') }
                        .take(5)
                    val coverId = obj["cover_i"]?.jsonPrimitive?.intOrNull

                    BookLookupCandidate(
                        workKey = workKey,
                        title = title,
                        author = author,
                        firstPublishYear = year,
                        publisher = publisher,
                        isbn = isbn,
                        subjects = cleanSubjects,
                        coverId = coverId,
                        seriesName = detectedSeries
                    )
                }
            }
        }
    }

    /**
     * Resolves canonical series name and book `#` for retail ebooks that omit OPF series metadata
     * (e.g., *Red Rising*, *Golden Son*, *Morning Star*, *Iron Gold* -> `Red Rising #1..#4`).
     */
    suspend fun resolveSeriesForBook(
        title: String,
        author: String,
        offlineOnlyMode: Boolean
    ): InferredSeries? = withContext(Dispatchers.IO) {
        if (offlineOnlyMode) return@withContext null
        val cleanTitle = BookMetadataNormalizer.cleanBookTitle(title).trim()
        if (cleanTitle.isEmpty()) return@withContext null

        val primaryAuthor = BookMetadataNormalizer.normalizeAuthor(author)
            .substringBefore(',')
            .takeIf { !it.equals("Unknown Author", ignoreCase = true) }

        runCatching {
            val urlBuilder = "https://openlibrary.org/search.json".toHttpUrl().newBuilder()
                .addQueryParameter("title", cleanTitle)
                .addQueryParameter("limit", "4")
                .addQueryParameter("fields", "key,title,author_name,subject")
            if (!primaryAuthor.isNullOrBlank()) {
                urlBuilder.addQueryParameter("author", primaryAuthor)
            }

            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "LiberReader/1.1 (Android)")
                .build()

            var workKey: String? = null
            var subjectSeries: String? = null

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val docs = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["docs"]?.jsonArray ?: return@use

                for (docEl in docs) {
                    val obj = docEl.jsonObject
                    val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: continue
                    val subjects = obj["subject"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                        .orEmpty()
                    val candidateSeries = extractSeriesNameFromSubjects(subjects)
                    if (workKey == null) workKey = key
                    if (candidateSeries != null) {
                        workKey = key
                        subjectSeries = candidateSeries
                        break
                    }
                }
            }

            val resolvedWorkKey = workKey ?: return@runCatching null
            val editionSeriesItems = fetchEditionSeriesStrings(resolvedWorkKey)
            val parsedEditions = editionSeriesItems.mapNotNull {
                BookMetadataNormalizer.parseSeriesAndNumberFromEditionString(it)
            }.filterNot {
                val lower = it.seriesName.lowercase()
                lower.contains("large print") || lower.contains("penguin") || lower.contains("vintage")
            }

            val finalSeriesName = subjectSeries
                ?: parsedEditions.map { it.seriesName }
                    .groupingBy { BookMetadataNormalizer.canonicalSeriesKey(it) ?: it.lowercase() }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.let { bestKey ->
                        parsedEditions.firstOrNull {
                            (BookMetadataNormalizer.canonicalSeriesKey(it.seriesName) ?: it.seriesName.lowercase()) == bestKey.key
                        }?.seriesName
                    }
                ?: return@runCatching null

            val targetKey = BookMetadataNormalizer.canonicalSeriesKey(finalSeriesName)

            // Prefer volume numbers from editions matching the canonical series name, else mode across all editions
            val matchingNumbers = parsedEditions
                .filter { BookMetadataNormalizer.canonicalSeriesKey(it.seriesName) == targetKey }
                .mapNotNull { it.seriesIndex }

            val allNumbers = parsedEditions.mapNotNull { it.seriesIndex }
            val chosenNumber: Float? = mostFrequentFloat(matchingNumbers)
                ?: mostFrequentFloat(allNumbers)
                ?: if (BookMetadataNormalizer.canonicalSeriesKey(cleanTitle) == targetKey) 1f else null

            InferredSeries(seriesName = finalSeriesName, seriesIndex = chosenNumber)
        }.getOrNull()
    }

    private fun extractSeriesNameFromSubjects(subjects: List<String>): String? {
        val franchise = subjects.firstOrNull { it.startsWith("franchise:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
            ?.let(BookMetadataNormalizer::normalizeSeriesName)
        if (franchise != null) return franchise

        return subjects
            .filter { it.startsWith("series:", ignoreCase = true) }
            .mapNotNull { BookMetadataNormalizer.normalizeSeriesName(it.substringAfter(':')) }
            .firstOrNull()
    }

    private fun fetchEditionSeriesStrings(workKey: String): List<String> {
        if (!workKey.startsWith("/works/")) return emptyList()
        return runCatching {
            val req = Request.Builder()
                .url("https://openlibrary.org$workKey/editions.json?limit=30")
                .header("User-Agent", "LiberReader/1.1 (Android)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val entries = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["entries"]?.jsonArray ?: return@use emptyList()
                entries.flatMap { entry ->
                    entry.jsonObject["series"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                        .orEmpty()
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun mostFrequentFloat(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        return values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    suspend fun fetchWorkDescription(workKey: String, offlineOnlyMode: Boolean): String =
        withContext(Dispatchers.IO) {
            if (offlineOnlyMode || !workKey.startsWith("/works/")) return@withContext ""
            runCatching {
                val req = Request.Builder()
                    .url("https://openlibrary.org$workKey.json")
                    .header("User-Agent", "LiberReader/1.1 (Android)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use ""
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val descEl = obj["description"] ?: return@use ""
                    if (descEl is JsonObject) {
                        descEl["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    } else {
                        descEl.jsonPrimitive.contentOrNull.orEmpty()
                    }
                }
            }.getOrDefault("")
        }

    suspend fun downloadCoverBytes(coverUrl: String, offlineOnlyMode: Boolean): ByteArray? =
        withContext(Dispatchers.IO) {
            if (offlineOnlyMode) return@withContext null
            runCatching {
                val req = Request.Builder()
                    .url(coverUrl)
                    .header("User-Agent", "LiberReader/1.1 (Android)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}
