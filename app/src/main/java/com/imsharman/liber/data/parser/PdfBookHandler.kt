package com.imsharman.liber.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Handles PDF documents using:
 * 1. Android's hardware-backed `PdfRenderer` for fast, high-DPI visual page rendering & first-page cover generation.
 * 2. `PdfBox-Android` for metadata (Title, Author, Keywords), PDF Document Outline TOC, in-book search,
 *    optional Text Reflow mode, and Text-to-Speech!
 */
class PdfBookHandler(private val context: Context) {

    init {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
    }

    suspend fun extractMetadata(uri: Uri, fallbackTitle: String): ParsedBookMetadata =
        withContext(Dispatchers.IO) {
            var title = fallbackTitle
            var author = "Unknown Author"
            var description = ""
            var keywords: List<String> = emptyList()
            var pageCount = 1

            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { pdDoc ->
                        pageCount = pdDoc.numberOfPages.coerceAtLeast(1)
                        val info = pdDoc.documentInformation
                        info?.title?.trim()?.takeIf { it.isNotEmpty() }?.let { title = it }
                        info?.author?.trim()?.takeIf { it.isNotEmpty() }?.let { author = it }
                        info?.subject?.trim()?.takeIf { it.isNotEmpty() }?.let { description = it }
                        info?.keywords?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
                            keywords = it
                        }
                    }
                }
            }

            // Render page 0 strictly from inside the PDF as internal cover bytes
            val coverBytes = runCatching {
                openSeekableDescriptor(uri)?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount.coerceAtLeast(pageCount)
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { page ->
                                val targetW = 480
                                val targetH = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * targetW)
                                    .toInt().coerceIn(200, 900)
                                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                ByteArrayOutputStream().also { out ->
                                    bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                                }.toByteArray()
                            }
                        } else null
                    }
                }
            }.getOrNull()

            ParsedBookMetadata(
                title = title,
                author = author,
                description = description,
                tags = keywords,
                embeddedCoverBytes = coverBytes,
                totalUnits = pageCount
            )
        }

    suspend fun loadVisualContent(
        uri: Uri,
        fallbackTitle: String,
        includeReflowText: Boolean
    ): LoadedBookContent.VisualPaged = withContext(Dispatchers.IO) {
        var title = fallbackTitle
        var author = "Unknown Author"
        var pageCount = 1
        val toc = mutableListOf<TocEntry>()
        val reflowChapters = mutableListOf<BookChapter>()

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    pageCount = doc.numberOfPages.coerceAtLeast(1)
                    doc.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() }?.let { title = it }
                    doc.documentInformation?.author?.trim()?.takeIf { it.isNotEmpty() }?.let { author = it }

                    doc.documentCatalog?.documentOutline?.let { outline ->
                        collectPdfOutline(doc, outline, depth = 0, sink = toc)
                    }

                    if (includeReflowText) {
                        val stripper = PDFTextStripper()
                        val maxPages = pageCount.coerceAtMost(400)
                        for (p in 1..maxPages) {
                            stripper.startPage = p
                            stripper.endPage = p
                            val pageText = stripper.getText(doc).trim()
                            val paras = pageText.split(Regex("\\n\\s*\\n"))
                                .map { it.replace(Regex("\\s+"), " ").trim() }
                                .filter { it.isNotEmpty() }
                                .map { ContentBlock.Paragraph(it) }
                            val blocks = paras.ifEmpty {
                                listOf(ContentBlock.Paragraph("[Visual/Scanned Page $p]"))
                            }
                            reflowChapters += BookChapter(
                                index = p - 1,
                                title = "Page $p",
                                blocks = blocks
                            )
                        }
                    }
                }
            }
        }

        if (pageCount <= 1) {
            runCatching {
                openSeekableDescriptor(uri)?.use { pfd ->
                    PdfRenderer(pfd).use { pageCount = it.pageCount.coerceAtLeast(1) }
                }
            }
        }

        val finalToc = toc.ifEmpty {
            (0 until pageCount step maxOf(1, pageCount / 20)).map { idx ->
                TocEntry(title = "Page ${idx + 1}", pageIndex = idx, chapterIndex = idx)
            }
        }

        val totalWords = reflowChapters.sumOf { it.wordCount }
        LoadedBookContent.VisualPaged(
            metadata = ParsedBookMetadata(
                title = title,
                author = author,
                totalUnits = pageCount,
                totalWords = totalWords
            ),
            pageCount = pageCount,
            toc = finalToc,
            reflowChapters = reflowChapters
        )
    }

    private fun collectPdfOutline(
        doc: PDDocument,
        node: PDOutlineNode,
        depth: Int,
        sink: MutableList<TocEntry>
    ) {
        var current = node.firstChild
        while (current != null) {
            val pageIdx = runCatching {
                val pdPage = current.findDestinationPage(doc)
                if (pdPage != null) doc.pages.indexOf(pdPage).coerceAtLeast(0) else 0
            }.getOrDefault(0)
            val title = current.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Section"
            sink += TocEntry(
                title = title,
                pageIndex = pageIdx,
                chapterIndex = pageIdx,
                depth = depth
            )
            collectPdfOutline(doc, current, depth + 1, sink)
            current = current.nextSibling
        }
    }

    /**
     * Thread-safe PDF session wrapper around `PdfRenderer` for smooth lazy paging in Compose.
     */
    inner class PdfRenderSession(private val pfd: ParcelFileDescriptor) : AutoCloseable {
        private val renderer = PdfRenderer(pfd)
        private val mutex = Mutex()

        val pageCount: Int get() = renderer.pageCount

        suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
            if (pageIndex !in 0 until renderer.pageCount) return@withContext null
            mutex.withLock {
                renderer.openPage(pageIndex).use { page ->
                    val width = targetWidthPx.coerceIn(360, 2560)
                    val ratio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                    val height = (width * ratio).toInt().coerceIn(360, 4096)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

        override fun close() {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }

    suspend fun openRenderSession(uri: Uri): PdfRenderSession? = withContext(Dispatchers.IO) {
        val pfd = openSeekableDescriptor(uri) ?: return@withContext null
        runCatching { PdfRenderSession(pfd) }.getOrElse {
            runCatching { pfd.close() }
            null
        }
    }

    private fun openSeekableDescriptor(uri: Uri): ParcelFileDescriptor? {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: return null)
            if (f.exists()) return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        val direct = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")
        }.getOrNull()
        if (direct != null && direct.statSize >= 0) {
            return direct
        }
        direct?.close()

        // Fallback for non-seekable content providers: copy into private internal cache file
        return runCatching {
            val tempDir = File(context.cacheDir, "pdf_descriptors").apply { mkdirs() }
            val tempFile = File(tempDir, "doc_${uri.toString().hashCode()}.pdf")
            if (!tempFile.exists()) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { out -> input.copyTo(out) }
                } ?: return null
            }
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull()
    }
}
