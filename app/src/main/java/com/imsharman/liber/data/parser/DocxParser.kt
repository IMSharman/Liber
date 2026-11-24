package com.imsharman.liber.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Lightweight pure-Kotlin OOXML `.docx` parser (zero bloated POI dependency).
 * Extracts:
 * - `docProps/core.xml` metadata (Title, Creator/Author, Description, Keywords)
 * - `docProps/thumbnail.*` embedded cover if present inside the `.docx`
 * - `word/document.xml` paragraphs, Heading1–Heading6 chapters + TOC, bold/italic/code runs, and lists
 */
object DocxParser {

    fun parse(
        inputStream: InputStream,
        fallbackTitle: String,
        metadataOnly: Boolean = false
    ): LoadedBookContent.Reflowable {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name.replace('\\', '/')] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val coreXml = entries["docProps/core.xml"]?.toString(Charsets.UTF_8)
        val coreDoc = coreXml?.let { Jsoup.parse(it, "", Parser.xmlParser()) }

        val title = coreDoc?.selectFirst("dc|title, title")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: fallbackTitle
        val author = coreDoc?.selectFirst("dc|creator, creator")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "Unknown Author"
        val description = coreDoc?.selectFirst("dc|description, description")?.text()?.trim().orEmpty()
        val keywords = coreDoc?.selectFirst("cp|keywords, keywords")?.text()
            ?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

        val coverBytes = entries.entries.firstOrNull { (k, _) ->
            k.startsWith("docProps/thumbnail", ignoreCase = true)
        }?.value

        if (metadataOnly) {
            return LoadedBookContent.Reflowable(
                metadata = ParsedBookMetadata(
                    title = title,
                    author = author,
                    description = description,
                    tags = keywords,
                    embeddedCoverBytes = coverBytes,
                    totalUnits = 1
                ),
                chapters = emptyList(),
                toc = emptyList()
            )
        }

        val docXml = entries["word/document.xml"]?.toString(Charsets.UTF_8)
        if (docXml == null) {
            return LoadedBookContent.Reflowable(
                metadata = ParsedBookMetadata(title = title, author = author, embeddedCoverBytes = coverBytes),
                chapters = listOf(
                    BookChapter(0, title, listOf(ContentBlock.Paragraph("Invalid DOCX archive: missing word/document.xml.")))
                ),
                toc = listOf(TocEntry(title, 0))
            )
        }

        val doc = Jsoup.parse(docXml, "", Parser.xmlParser())
        val body = doc.selectFirst("w|body, body")
        val allBlocks = mutableListOf<ContentBlock>()

        if (body != null) {
            for (el in body.children()) {
                when (el.tagName().substringAfter(':').lowercase()) {
                    "p" -> parseParagraphElement(el)?.let { allBlocks += it }
                    "tbl" -> {
                        val rows = el.select("w|tr, tr").mapNotNull { tr ->
                            val cells = tr.select("w|tc, tc").map { it.text().trim() }.filter { it.isNotEmpty() }
                            if (cells.isNotEmpty()) cells.joinToString("  |  ") else null
                        }
                        if (rows.isNotEmpty()) {
                            allBlocks += ContentBlock.ListItems(ordered = false, items = rows)
                        }
                    }
                }
            }
        }

        // Split into chapters at Heading 1 / Heading 2
        val chapters = mutableListOf<BookChapter>()
        val toc = mutableListOf<TocEntry>()
        var currentTitle = title
        var currentBlocks = mutableListOf<ContentBlock>()

        for (block in allBlocks) {
            if (block is ContentBlock.Heading && block.level <= 2 && currentBlocks.isNotEmpty()) {
                val idx = chapters.size
                chapters += BookChapter(index = idx, title = currentTitle, blocks = currentBlocks)
                toc += TocEntry(title = currentTitle, chapterIndex = idx, depth = 0)
                currentTitle = block.text
                currentBlocks = mutableListOf(block)
            } else {
                if (currentBlocks.isEmpty() && block is ContentBlock.Heading) {
                    currentTitle = block.text
                }
                currentBlocks += block
            }
        }
        if (currentBlocks.isNotEmpty()) {
            val idx = chapters.size
            chapters += BookChapter(index = idx, title = currentTitle, blocks = currentBlocks)
            toc += TocEntry(title = currentTitle, chapterIndex = idx, depth = 0)
        }

        if (chapters.isEmpty()) {
            chapters += BookChapter(0, title, listOf(ContentBlock.Paragraph("Document contains no readable text.")))
            toc += TocEntry(title, 0)
        }

        val totalWords = chapters.sumOf { it.wordCount }
        return LoadedBookContent.Reflowable(
            metadata = ParsedBookMetadata(
                title = title,
                author = author,
                description = description,
                tags = keywords,
                embeddedCoverBytes = coverBytes,
                totalUnits = chapters.size,
                totalWords = totalWords
            ),
            chapters = chapters,
            toc = toc
        )
    }

    private fun parseParagraphElement(p: Element): ContentBlock? {
        val styleVal = p.selectFirst("w|pPr > w|pStyle, pPr > pStyle")
            ?.let { it.attr("w:val").ifEmpty { it.attr("val") } }
            ?.lowercase().orEmpty()

        val isListItem = p.selectFirst("w|pPr > w|numPr, pPr > numPr") != null

        val sb = StringBuilder()
        val spans = mutableListOf<InlineSpan>()

        for (r in p.select("w|r, r")) {
            val rPr = r.selectFirst("w|rPr, rPr")
            val isBold = rPr?.selectFirst("w|b, b") != null
            val isItalic = rPr?.selectFirst("w|i, i") != null
            val isStrike = rPr?.selectFirst("w|strike, strike") != null

            val runText = buildString {
                for (child in r.children()) {
                    when (child.tagName().substringAfter(':').lowercase()) {
                        "t" -> append(child.wholeText())
                        "tab" -> append("    ")
                        "br", "cr" -> append("\n")
                    }
                }
            }
            if (runText.isNotEmpty()) {
                val start = sb.length
                sb.append(runText)
                val end = sb.length
                if (isBold) spans += InlineSpan(start, end, InlineStyle.BOLD)
                if (isItalic) spans += InlineSpan(start, end, InlineStyle.ITALIC)
                if (isStrike) spans += InlineSpan(start, end, InlineStyle.STRIKETHROUGH)
            }
        }

        val text = sb.toString().trim()
        if (text.isEmpty()) return null

        val headingMatch = Regex("heading\\s*([1-6])").find(styleVal)
        if (headingMatch != null) {
            val lvl = headingMatch.groupValues[1].toIntOrNull() ?: 2
            return ContentBlock.Heading(level = lvl, text = text)
        }
        if (styleVal == "title") {
            return ContentBlock.Heading(level = 1, text = text)
        }
        if (styleVal.contains("quote")) {
            return ContentBlock.Quote(text = text)
        }
        if (isListItem) {
            return ContentBlock.ListItems(ordered = false, items = listOf(text))
        }
        val clamped = spans.mapNotNull { sp ->
            val s = sp.start.coerceIn(0, text.length)
            val e = sp.end.coerceIn(0, text.length)
            if (s < e) InlineSpan(s, e, sp.style) else null
        }
        return ContentBlock.Paragraph(text = text, spans = clamped)
    }
}
