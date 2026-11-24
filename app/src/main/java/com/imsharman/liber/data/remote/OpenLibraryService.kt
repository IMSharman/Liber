package com.imsharman.liber.data.remote

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
    val coverId: Int?
) {
    val coverUrl: String?
        get() = coverId?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" }
}

/**
 * Opt-in metadata & cover lookup using the open, account-free Open Library API.
 * Strictly triggered ONLY when the user clicks "Search Online" in the Metadata Editor,
 * and completely blocked when `offlineOnlyMode` is enabled.
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
                .header("User-Agent", "LiberReader/1.0 (Android; Privacy-First Ebook Reader)")
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
                    val authors = obj["author_name"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                        ?.take(3)
                        ?.joinToString(", ")
                        .orEmpty()
                        .ifEmpty { "Unknown Author" }
                    val year = obj["first_publish_year"]?.jsonPrimitive?.intOrNull
                    val publisher = obj["publisher"]?.jsonArray
                        ?.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty()
                    val isbn = obj["isbn"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?.firstOrNull { it.length == 13 || it.length == 10 }
                        .orEmpty()
                    val subjects = obj["subject"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                        ?.take(5)
                        .orEmpty()
                    val coverId = obj["cover_i"]?.jsonPrimitive?.intOrNull

                    BookLookupCandidate(
                        workKey = workKey,
                        title = title,
                        author = authors,
                        firstPublishYear = year,
                        publisher = publisher,
                        isbn = isbn,
                        subjects = subjects,
                        coverId = coverId
                    )
                }
            }
        }
    }

    suspend fun fetchWorkDescription(workKey: String, offlineOnlyMode: Boolean): String =
        withContext(Dispatchers.IO) {
            if (offlineOnlyMode || !workKey.startsWith("/works/")) return@withContext ""
            runCatching {
                val req = Request.Builder()
                    .url("https://openlibrary.org$workKey.json")
                    .header("User-Agent", "LiberReader/1.0")
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
                    .header("User-Agent", "LiberReader/1.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}
