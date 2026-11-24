package com.imsharman.liber.data.parser

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.imsharman.liber.data.storage.InternalSandboxManager
import com.imsharman.liber.domain.model.BookFormat
import com.imsharman.liber.domain.util.BookMetadataNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Central dispatcher routing all 10 supported book & document formats
 * (`EPUB`, `PDF`, `CBZ`, `CBR`, `MOBI`, `AZW3`, `FB2`, `TXT`, `MARKDOWN`, `DOCX`)
 * to their respective parsers, with in-memory LRU caching for instant chapter navigation.
 */
class BookParserDispatcher(
    private val context: Context,
    private val sandboxManager: InternalSandboxManager
) {
    val pdfHandler = PdfBookHandler(context)
    val comicHandler = ComicArchiveParser(context)

    private val loadedCache = LruCache<String, LoadedBookContent>(6)

    suspend fun parseMetadataForImport(
        uri: Uri,
        fileName: String,
        format: BookFormat
    ): ParsedBookMetadata = withContext(Dispatchers.IO) {
        val fallbackTitle = cleanTitleFromFileName(fileName)
        val rawMeta = runCatching {
            when (format) {
                BookFormat.EPUB -> openStream(uri)?.use {
                    EpubParser.parseMetadataOnly(it, fallbackTitle)
                }
                BookFormat.PDF -> pdfHandler.extractMetadata(uri, fallbackTitle)
                BookFormat.CBZ, BookFormat.CBR -> comicHandler.extractMetadata(uri, format, fallbackTitle)
                BookFormat.FB2 -> openStream(uri)?.use {
                    Fb2Parser.parse(it, fileName, fallbackTitle, metadataOnly = true).metadata
                }
                BookFormat.MOBI, BookFormat.AZW3 -> openStream(uri)?.use {
                    MobiParser.parse(it, fallbackTitle, metadataOnly = true).metadata
                }
                BookFormat.DOCX -> openStream(uri)?.use {
                    DocxParser.parse(it, fallbackTitle, metadataOnly = true).metadata
                }
                BookFormat.TXT -> openStream(uri)?.use {
                    PlainTextMarkdownParser.parse(it, fallbackTitle, isMarkdown = false, metadataOnly = true).metadata
                }
                BookFormat.MARKDOWN -> openStream(uri)?.use {
                    PlainTextMarkdownParser.parse(it, fallbackTitle, isMarkdown = true, metadataOnly = true).metadata
                }
            } ?: ParsedBookMetadata(title = fallbackTitle)
        }.getOrElse {
            ParsedBookMetadata(title = fallbackTitle)
        }

        val normalizedAuthor = BookMetadataNormalizer.normalizeAuthor(rawMeta.author)
        var seriesName = BookMetadataNormalizer.normalizeSeriesName(rawMeta.seriesName)
        var seriesIndex = rawMeta.seriesIndex
        if (seriesName == null) {
            val inferred = BookMetadataNormalizer.inferSeriesFromBookSignals(
                title = rawMeta.title,
                fileName = fileName,
                description = rawMeta.description
            )
            if (inferred != null) {
                seriesName = inferred.seriesName
                if (seriesIndex == null) seriesIndex = inferred.seriesIndex
            }
        }
        rawMeta.copy(
            title = BookMetadataNormalizer.cleanBookTitle(rawMeta.title),
            author = normalizedAuthor,
            seriesName = seriesName,
            seriesIndex = seriesIndex
        )
    }

    suspend fun loadBookContent(
        bookId: Long,
        uri: Uri,
        fileName: String,
        format: BookFormat,
        fallbackTitle: String,
        pdfReflowRequested: Boolean = false
    ): LoadedBookContent = withContext(Dispatchers.IO) {
        val cacheKey = "$bookId:$format:$pdfReflowRequested"
        loadedCache.get(cacheKey)?.let { return@withContext it }

        val loaded: LoadedBookContent = when (format) {
            BookFormat.EPUB -> openStream(uri)?.use { stream ->
                EpubParser.parseFullBook(
                    inputStream = stream,
                    fallbackTitle = fallbackTitle,
                    assetSaver = { entryName, bytes ->
                        sandboxManager.saveInternalBookAsset("book_$bookId", entryName, bytes)
                    }
                )
            } ?: emptyReflowable(fallbackTitle)

            BookFormat.PDF -> pdfHandler.loadVisualContent(
                uri = uri,
                fallbackTitle = fallbackTitle,
                includeReflowText = pdfReflowRequested
            )

            BookFormat.CBZ, BookFormat.CBR -> comicHandler.loadVisualContent(
                uri = uri,
                format = format,
                fallbackTitle = fallbackTitle
            )

            BookFormat.FB2 -> openStream(uri)?.use {
                Fb2Parser.parse(it, fileName, fallbackTitle, metadataOnly = false)
            } ?: emptyReflowable(fallbackTitle)

            BookFormat.MOBI, BookFormat.AZW3 -> openStream(uri)?.use {
                MobiParser.parse(it, fallbackTitle, metadataOnly = false)
            } ?: emptyReflowable(fallbackTitle)

            BookFormat.DOCX -> openStream(uri)?.use {
                DocxParser.parse(it, fallbackTitle, metadataOnly = false)
            } ?: emptyReflowable(fallbackTitle)

            BookFormat.TXT -> openStream(uri)?.use {
                PlainTextMarkdownParser.parse(it, fallbackTitle, isMarkdown = false, metadataOnly = false)
            } ?: emptyReflowable(fallbackTitle)

            BookFormat.MARKDOWN -> openStream(uri)?.use {
                PlainTextMarkdownParser.parse(it, fallbackTitle, isMarkdown = true, metadataOnly = false)
            } ?: emptyReflowable(fallbackTitle)
        }

        loadedCache.put(cacheKey, loaded)
        loaded
    }

    private fun openStream(uri: Uri): InputStream? {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: return null)
            return if (f.exists()) FileInputStream(f) else null
        }
        return context.contentResolver.openInputStream(uri)
    }

    private fun emptyReflowable(title: String) = LoadedBookContent.Reflowable(
        metadata = ParsedBookMetadata(title = title),
        chapters = listOf(
            BookChapter(
                index = 0,
                title = title,
                blocks = listOf(
                    ContentBlock.Paragraph(
                        "Unable to open this file. Its SAF permission may have been revoked or the file was moved."
                    )
                )
            )
        ),
        toc = listOf(TocEntry(title = title, chapterIndex = 0))
    )

    companion object {
        fun cleanTitleFromFileName(fileName: String): String {
            val withoutFb2Zip = if (fileName.endsWith(".fb2.zip", ignoreCase = true)) {
                fileName.dropLast(8)
            } else {
                fileName.substringBeforeLast('.')
            }
            return withoutFb2Zip
                .replace('_', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "Untitled Book" }
        }
    }
}
