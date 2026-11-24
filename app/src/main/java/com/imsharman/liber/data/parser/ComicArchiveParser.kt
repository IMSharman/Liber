package com.imsharman.liber.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.github.junrar.Archive
import com.imsharman.liber.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Comic & Manga archive handler supporting `.cbz` (ZIP) and `.cbr` (RAR via Junrar),
 * including `ComicInfo.xml` metadata (Series, Number, Writer, Summary) when present!
 */
class ComicArchiveParser(private val context: Context) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    private fun isComicPageEntry(entryName: String): Boolean {
        val clean = entryName.replace('\\', '/')
        val base = clean.substringAfterLast('/')
        if (base.startsWith(".") || clean.contains("__MACOSX")) return false
        val ext = base.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return ext in imageExtensions
    }

    suspend fun extractMetadata(
        uri: Uri,
        format: BookFormat,
        fallbackTitle: String
    ): ParsedBookMetadata = withContext(Dispatchers.IO) {
        if (format == BookFormat.CBR) {
            extractCbrMetadata(uri, fallbackTitle)
        } else {
            extractCbzMetadata(uri, fallbackTitle)
        }
    }

    suspend fun loadVisualContent(
        uri: Uri,
        format: BookFormat,
        fallbackTitle: String
    ): LoadedBookContent.VisualPaged = withContext(Dispatchers.IO) {
        val meta = extractMetadata(uri, format, fallbackTitle)
        val count = meta.totalUnits.coerceAtLeast(1)
        val toc = (0 until count step maxOf(1, count / 25)).map { idx ->
            TocEntry(title = "Page ${idx + 1}", pageIndex = idx, chapterIndex = idx)
        }
        LoadedBookContent.VisualPaged(
            metadata = meta,
            pageCount = count,
            toc = toc
        )
    }

    private fun extractCbzMetadata(uri: Uri, fallbackTitle: String): ParsedBookMetadata {
        var title = fallbackTitle
        var author = "Unknown Artist"
        var seriesName: String? = null
        var seriesIndex: Float? = null
        var description = ""
        val pageNames = mutableListOf<String>()
        var comicInfoXml: String? = null

        val cachedZip = materializeTempArchive(uri, ".cbz")
            ?: return ParsedBookMetadata(title = fallbackTitle)

        ZipFile(cachedZip).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                if (e.name.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)) {
                    comicInfoXml = zf.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }
                } else if (isComicPageEntry(e.name)) {
                    pageNames += e.name
                }
            }
            pageNames.sortWith(NaturalSortComparator)

            comicInfoXml?.let { xml ->
                val doc = Jsoup.parse(xml, "", Parser.xmlParser())
                doc.selectFirst("Title")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { title = it }
                val writer = doc.selectFirst("Writer")?.text()?.trim()
                val penciller = doc.selectFirst("Penciller")?.text()?.trim()
                listOfNotNull(writer, penciller).filter { it.isNotEmpty() }.joinToString(", ")
                    .takeIf { it.isNotEmpty() }?.let { author = it }
                seriesName = doc.selectFirst("Series")?.text()?.trim()?.takeIf { it.isNotEmpty() }
                seriesIndex = doc.selectFirst("Number")?.text()?.trim()?.toFloatOrNull()
                description = doc.selectFirst("Summary")?.text()?.trim().orEmpty()
                if (title == fallbackTitle && seriesName != null && seriesIndex != null) {
                    val numStr = if (seriesIndex!! % 1f == 0f) seriesIndex!!.toInt().toString() else seriesIndex.toString()
                    title = "$seriesName #$numStr"
                }
            }

            val firstCover = pageNames.firstOrNull()?.let { firstEntry ->
                zf.getEntry(firstEntry)?.let { ze ->
                    zf.getInputStream(ze).use { it.readBytes() }
                }
            }

            return ParsedBookMetadata(
                title = title,
                author = author,
                seriesName = seriesName,
                seriesIndex = seriesIndex,
                description = description,
                embeddedCoverBytes = firstCover,
                totalUnits = pageNames.size.coerceAtLeast(1)
            )
        }
    }

    private fun extractCbrMetadata(uri: Uri, fallbackTitle: String): ParsedBookMetadata {
        val cachedRar = materializeTempArchive(uri, ".cbr")
            ?: return ParsedBookMetadata(title = fallbackTitle)
        return runCatching {
            Archive(cachedRar).use { archive ->
                val headers = archive.fileHeaders
                    .filter { !it.isDirectory && isComicPageEntry(it.fileName) }
                    .sortedWith { a, b -> NaturalSortComparator.compare(a.fileName, b.fileName) }
                val coverBytes = headers.firstOrNull()?.let { firstHeader ->
                    ByteArrayOutputStream().also { out ->
                        archive.extractFile(firstHeader, out)
                    }.toByteArray()
                }
                ParsedBookMetadata(
                    title = fallbackTitle,
                    author = "Comic Archive",
                    embeddedCoverBytes = coverBytes,
                    totalUnits = headers.size.coerceAtLeast(1)
                )
            }
        }.getOrElse {
            ParsedBookMetadata(
                title = fallbackTitle,
                description = "Note: RAR5 compressed CBR files may require standard CBZ/RAR4 format.",
                totalUnits = 1
            )
        }
    }

    inner class ComicRenderSession(
        private val archiveFile: File,
        private val format: BookFormat,
        val sortedEntryNames: List<String>
    ) : AutoCloseable {

        val pageCount: Int get() = sortedEntryNames.size.coerceAtLeast(1)

        suspend fun renderPage(pageIndex: Int, maxDimensionPx: Int = 1920): Bitmap? =
            withContext(Dispatchers.IO) {
                if (pageIndex !in sortedEntryNames.indices) return@withContext null
                val targetName = sortedEntryNames[pageIndex]
                val rawBytes = if (format == BookFormat.CBR) {
                    runCatching {
                        Archive(archiveFile).use { archive ->
                            val hdr = archive.fileHeaders.firstOrNull { it.fileName == targetName }
                                ?: return@use null
                            ByteArrayOutputStream().also { archive.extractFile(hdr, it) }.toByteArray()
                        }
                    }.getOrNull()
                } else {
                    runCatching {
                        ZipFile(archiveFile).use { zf ->
                            val entry = zf.getEntry(targetName) ?: return@use null
                            zf.getInputStream(entry).use { it.readBytes() }
                        }
                    }.getOrNull()
                } ?: return@withContext null

                decodeSampledBitmap(rawBytes, maxDimensionPx)
            }

        override fun close() = Unit
    }

    suspend fun openRenderSession(uri: Uri, format: BookFormat): ComicRenderSession? =
        withContext(Dispatchers.IO) {
            val ext = if (format == BookFormat.CBR) ".cbr" else ".cbz"
            val file = materializeTempArchive(uri, ext) ?: return@withContext null
            val names = if (format == BookFormat.CBR) {
                runCatching {
                    Archive(file).use { archive ->
                        archive.fileHeaders
                            .filter { !it.isDirectory && isComicPageEntry(it.fileName) }
                            .map { it.fileName }
                            .sortedWith(NaturalSortComparator)
                    }
                }.getOrDefault(emptyList())
            } else {
                runCatching {
                    ZipFile(file).use { zf ->
                        val list = mutableListOf<String>()
                        val en = zf.entries()
                        while (en.hasMoreElements()) {
                            val e = en.nextElement()
                            if (!e.isDirectory && isComicPageEntry(e.name)) list += e.name
                        }
                        list.sortedWith(NaturalSortComparator)
                    }
                }.getOrDefault(emptyList())
            }
            ComicRenderSession(file, format, names)
        }

    private fun materializeTempArchive(uri: Uri, suffix: String): File? {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: return null)
            if (f.exists()) return f
        }
        return runCatching {
            val dir = File(context.cacheDir, "comic_archives").apply { mkdirs() }
            val dest = File(dir, "comic_${uri.toString().hashCode()}$suffix")
            if (!dest.exists()) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { out -> input.copyTo(out) }
                } ?: return null
            }
            dest
        }.getOrNull()
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
