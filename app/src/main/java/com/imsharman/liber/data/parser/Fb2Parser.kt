package com.imsharman.liber.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * Pure-Kotlin FictionBook 2 (`.fb2` and `.fb2.zip`) parser.
 * Supports:
 * - `<title-info>` metadata (Title, Author, Sequence/Series + #index, Annotation, Genre tags, Language)
 * - `<binary>` base64 embedded cover extraction (strictly from inside the `.fb2`)
 * - Nested `<section>` chapters, `<epigraph>`, `<poem>`, `<cite>`, `<subtitle>`, and inline emphasis
 */
object Fb2Parser {

    fun parse(
        inputStream: InputStream,
        fileName: String,
        fallbackTitle: String,
        metadataOnly: Boolean = false
    ): LoadedBookContent.Reflowable {
        val rawBytes = unwrapIfZipped(inputStream.readBytes(), fileName)
        val xmlText = decodeXmlBytes(rawBytes)
        val doc = Jsoup.parse(xmlText, "", Parser.xmlParser())

        val titleInfo = doc.selectFirst("description > title-info") ?: doc.selectFirst("title-info")
        val title = titleInfo?.selectFirst("book-title")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: fallbackTitle

        val authors = titleInfo?.select("author")?.mapNotNull { authorEl ->
            val parts = listOfNotNull(
                authorEl.selectFirst("first-name")?.text()?.trim(),
                authorEl.selectFirst("middle-name")?.text()?.trim(),
                authorEl.selectFirst("last-name")?.text()?.trim()
            ).filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) parts.joinToString(" ")
            else authorEl.selectFirst("nickname")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        }.orEmpty()
        val author = if (authors.isEmpty()) "Unknown Author" else authors.joinToString(", ")

        val sequenceEl = titleInfo?.selectFirst("sequence")
        val seriesName = sequenceEl?.attr("name")?.trim()?.takeIf { it.isNotEmpty() }
        val seriesIndex = sequenceEl?.attr("number")?.trim()?.toFloatOrNull()

        val description = titleInfo?.selectFirst("annotation")?.text()?.trim().orEmpty()
        val language = titleInfo?.selectFirst("lang")?.text()?.trim().orEmpty()
        val genres = titleInfo?.select("genre")?.mapNotNull { it.text().trim().takeIf(String::isNotEmpty) }.orEmpty()

        val publishInfo = doc.selectFirst("description > publish-info")
        val publisher = publishInfo?.selectFirst("publisher")?.text()?.trim().orEmpty()
        val isbn = publishInfo?.selectFirst("isbn")?.text()?.trim().orEmpty()

        // Extract embedded <binary> cover
        val coverHref = titleInfo?.selectFirst("coverpage > image")?.let { img ->
            img.attributes().firstOrNull { it.key.endsWith("href", ignoreCase = true) }?.value
        }?.removePrefix("#")?.trim()

        val binaryEl = if (!coverHref.isNullOrEmpty()) {
            doc.select("binary").firstOrNull { it.attr("id") == coverHref }
        } else {
            doc.selectFirst("binary[content-type^=image/]")
        }
        val coverBytes = binaryEl?.text()?.replace(Regex("\\s+"), "")?.let { b64 ->
            runCatching { Base64.getDecoder().decode(b64) }.getOrNull()
        }

        if (metadataOnly) {
            val sectionCount = doc.select("body > section").size.coerceAtLeast(1)
            return LoadedBookContent.Reflowable(
                metadata = ParsedBookMetadata(
                    title = title,
                    author = author,
                    seriesName = seriesName,
                    seriesIndex = seriesIndex,
                    description = description,
                    publisher = publisher,
                    isbn = isbn,
                    language = language,
                    tags = genres,
                    embeddedCoverBytes = coverBytes,
                    totalUnits = sectionCount
                ),
                chapters = emptyList(),
                toc = emptyList()
            )
        }

        val chapters = mutableListOf<BookChapter>()
        val toc = mutableListOf<TocEntry>()
        val mainBody = doc.selectFirst("body")

        if (mainBody != null) {
            val topSections = mainBody.children().filter { it.tagName().equals("section", true) }
            if (topSections.isNotEmpty()) {
                for (sec in topSections) {
                    extractFb2Section(sec, depth = 0, chapters = chapters, toc = toc)
                }
            } else {
                val blocks = extractSectionBlocks(mainBody)
                if (blocks.isNotEmpty()) {
                    chapters += BookChapter(index = 0, title = title, blocks = blocks)
                    toc += TocEntry(title = title, chapterIndex = 0, depth = 0)
                }
            }
        }

        if (chapters.isEmpty()) {
            chapters += BookChapter(
                index = 0,
                title = title,
                blocks = listOf(ContentBlock.Paragraph("Unable to extract readable FB2 body sections."))
            )
        }

        val totalWords = chapters.sumOf { it.wordCount }
        val meta = ParsedBookMetadata(
            title = title,
            author = author,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            description = description,
            publisher = publisher,
            isbn = isbn,
            language = language,
            tags = genres,
            embeddedCoverBytes = coverBytes,
            totalUnits = chapters.size,
            totalWords = totalWords
        )
        return LoadedBookContent.Reflowable(metadata = meta, chapters = chapters, toc = toc)
    }

    private fun extractFb2Section(
        section: Element,
        depth: Int,
        chapters: MutableList<BookChapter>,
        toc: MutableList<TocEntry>
    ) {
        val secTitle = section.children()
            .firstOrNull { it.tagName().equals("title", true) }
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }

        val nestedSections = section.children().filter { it.tagName().equals("section", true) }
        val directBlocks = extractSectionBlocks(section)

        if (directBlocks.isNotEmpty()) {
            val idx = chapters.size
            val finalTitle = secTitle ?: "Section ${idx + 1}"
            chapters += BookChapter(index = idx, title = finalTitle, blocks = directBlocks)
            toc += TocEntry(title = finalTitle, chapterIndex = idx, depth = depth)
        }

        for (childSec in nestedSections) {
            extractFb2Section(childSec, depth + 1, chapters, toc)
        }
    }

    private fun extractSectionBlocks(section: Element): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        for (child in section.children()) {
            when (child.tagName().lowercase()) {
                "section" -> Unit // handled recursively
                "title" -> {
                    val t = child.text().trim()
                    if (t.isNotEmpty()) blocks += ContentBlock.Heading(level = 2, text = t)
                }
                "subtitle" -> {
                    val t = child.text().trim()
                    if (t.isNotEmpty()) blocks += ContentBlock.Heading(level = 3, text = t)
                }
                "epigraph", "cite" -> {
                    val author = child.selectFirst("text-author")?.text()?.trim()
                    val bodyText = child.children()
                        .filterNot { it.tagName().equals("text-author", true) }
                        .joinToString("\n") { it.text().trim() }
                        .trim()
                    if (bodyText.isNotEmpty()) {
                        blocks += ContentBlock.Quote(text = bodyText, attribution = author)
                    }
                }
                "poem" -> {
                    val verses = child.select("stanza").joinToString("\n\n") { stanza ->
                        stanza.select("v").joinToString("\n") { it.text().trim() }
                    }.trim()
                    if (verses.isNotEmpty()) {
                        blocks += ContentBlock.Quote(text = verses)
                    }
                }
                "empty-line" -> blocks += ContentBlock.Divider
                "p" -> {
                    val normalizedHtml = child.html()
                        .replace("<emphasis>", "<em>")
                        .replace("</emphasis>", "</em>")
                    val temp = Jsoup.parseBodyFragment("<p>$normalizedHtml</p>").body()
                    blocks += HtmlBlockExtractor.extractBlocks(temp)
                }
                else -> {
                    val t = child.text().trim()
                    if (t.isNotEmpty()) blocks += ContentBlock.Paragraph(t)
                }
            }
        }
        return blocks
    }

    private fun unwrapIfZipped(bytes: ByteArray, fileName: String): ByteArray {
        val isZipHeader = bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (!isZipHeader && !fileName.endsWith(".zip", ignoreCase = true)) {
            return bytes
        }
        return runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".fb2", ignoreCase = true)) {
                        return@use zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
                bytes
            }
        }.getOrDefault(bytes)
    }

    private fun decodeXmlBytes(bytes: ByteArray): String {
        val headerSample = String(bytes, 0, minOf(bytes.size, 256), Charsets.ISO_8859_1)
        val encodingMatch = Regex("encoding=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(headerSample)?.groupValues?.getOrNull(1)
        val charset = encodingMatch?.let {
            runCatching { Charset.forName(it) }.getOrNull()
        } ?: Charsets.UTF_8
        return String(bytes, charset)
    }
}
