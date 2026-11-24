package com.imsharman.liber.data.parser

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Parses Plain Text (`.txt`) and Markdown (`.md`, `.markdown`) ebooks and documents.
 * Supports:
 * - Smart BOM & UTF-8 validation with Windows-1252 fallback
 * - Optional YAML frontmatter (`title`, `author`, `series`, `tags`)
 * - Markdown headings (`#`–`######`) turned into structured TOC & chapters
 * - Markdown inline styles (`**bold**`, `*italic*`, `` `code` ``), fenced code blocks, blockquotes, lists
 * - Plain-text heuristic chapter recognition (`Chapter 1`, `CHAPTER IV`, `PART ONE`, `PROLOGUE`)
 */
object PlainTextMarkdownParser {

    private val PLAIN_CHAPTER_REGEX = Regex(
        "^(?:chapter|book|part|act|volume|prologue|epilogue)\\b[\\s.:\\-—0-9ivxlcdmA-Za-z]{0,50}$",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        inputStream: InputStream,
        fallbackTitle: String,
        isMarkdown: Boolean,
        metadataOnly: Boolean = false
    ): LoadedBookContent.Reflowable {
        val rawBytes = inputStream.readBytes()
        val decoded = decodeSmartText(rawBytes).replace("\r\n", "\n").replace('\r', '\n')

        val (frontmatter, bodyText) = extractYamlFrontmatter(decoded)
        val title = frontmatter["title"]?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val author = frontmatter["author"]?.takeIf { it.isNotBlank() } ?: "Unknown Author"
        val seriesName = frontmatter["series"]?.takeIf { it.isNotBlank() }
        val seriesIndex = frontmatter["series_index"]?.toFloatOrNull()
        val description = frontmatter["description"].orEmpty()
        val tags = frontmatter["tags"]
            ?.removeSurrounding("[", "]")
            ?.split(',')
            ?.map { it.trim().trim('"', '\'') }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        if (metadataOnly) {
            val approxWords = bodyText.split(Regex("\\s+")).count { it.isNotEmpty() }
            return LoadedBookContent.Reflowable(
                metadata = ParsedBookMetadata(
                    title = title,
                    author = author,
                    seriesName = seriesName,
                    seriesIndex = seriesIndex,
                    description = description,
                    tags = tags,
                    totalUnits = 1,
                    totalWords = approxWords
                ),
                chapters = emptyList(),
                toc = emptyList()
            )
        }

        val blocks = if (isMarkdown) parseMarkdownBlocks(bodyText) else parsePlainTextBlocks(bodyText)
        val chapters = mutableListOf<BookChapter>()
        val toc = mutableListOf<TocEntry>()

        var currentTitle = title
        var currentBlocks = mutableListOf<ContentBlock>()

        for (block in blocks) {
            if (block is ContentBlock.Heading && block.level <= 2 && currentBlocks.isNotEmpty()) {
                val idx = chapters.size
                chapters += BookChapter(index = idx, title = currentTitle, blocks = currentBlocks)
                toc += TocEntry(title = currentTitle, chapterIndex = idx, depth = (block.level - 1).coerceAtLeast(0))
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
            chapters += BookChapter(0, title, listOf(ContentBlock.Paragraph("Empty text file.")))
            toc += TocEntry(title, 0)
        }

        val totalWords = chapters.sumOf { it.wordCount }
        return LoadedBookContent.Reflowable(
            metadata = ParsedBookMetadata(
                title = title,
                author = author,
                seriesName = seriesName,
                seriesIndex = seriesIndex,
                description = description,
                tags = tags,
                totalUnits = chapters.size,
                totalWords = totalWords
            ),
            chapters = chapters,
            toc = toc
        )
    }

    private fun parseMarkdownBlocks(text: String): List<ContentBlock> {
        val lines = text.split('\n')
        val out = mutableListOf<ContentBlock>()
        var i = 0
        val paraBuf = mutableListOf<String>()

        fun flushPara() {
            if (paraBuf.isNotEmpty()) {
                val joined = paraBuf.joinToString(" ").trim()
                if (joined.isNotEmpty()) {
                    val (clean, spans) = parseInlineMarkdown(joined)
                    out += ContentBlock.Paragraph(clean, spans)
                }
                paraBuf.clear()
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> {
                    flushPara()
                    i++
                }
                trimmed.startsWith("```") -> {
                    flushPara()
                    i++
                    val codeLines = mutableListOf<String>()
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines += lines[i]
                        i++
                    }
                    if (i < lines.size) i++ // skip closing fence
                    out += ContentBlock.CodeBlock(codeLines.joinToString("\n"))
                }
                trimmed.matches(Regex("^#{1,6}\\s+.+")) -> {
                    flushPara()
                    val hashes = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val headingText = trimmed.drop(hashes).trim()
                    out += ContentBlock.Heading(level = hashes, text = headingText)
                    i++
                }
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    flushPara()
                    out += ContentBlock.Divider
                    i++
                }
                trimmed.startsWith(">") -> {
                    flushPara()
                    val quoteLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        quoteLines += lines[i].trim().removePrefix(">").trim()
                        i++
                    }
                    out += ContentBlock.Quote(quoteLines.joinToString("\n"))
                }
                trimmed.matches(Regex("^([-*+]|\\d+\\.)\\s+.+")) -> {
                    flushPara()
                    val ordered = trimmed.first().isDigit()
                    val items = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().matches(Regex("^([-*+]|\\d+\\.)\\s+.+"))) {
                        val itemRaw = lines[i].trim().replaceFirst(Regex("^([-*+]|\\d+\\.)\\s+"), "")
                        items += parseInlineMarkdown(itemRaw).first
                        i++
                    }
                    out += ContentBlock.ListItems(ordered = ordered, items = items)
                }
                else -> {
                    paraBuf += trimmed
                    i++
                }
            }
        }
        flushPara()
        return out
    }

    private fun parsePlainTextBlocks(text: String): List<ContentBlock> {
        val out = mutableListOf<ContentBlock>()
        val rawParagraphs = text.split(Regex("\\n\\s*\\n"))
        for (raw in rawParagraphs) {
            val cleaned = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            if (cleaned.isEmpty()) continue
            when {
                cleaned == "* * *" || cleaned == "***" || cleaned == "---" -> out += ContentBlock.Divider
                cleaned.length <= 70 && PLAIN_CHAPTER_REGEX.matches(cleaned) ->
                    out += ContentBlock.Heading(level = 1, text = cleaned)
                else -> out += ContentBlock.Paragraph(text = cleaned)
            }
        }
        return out
    }

    internal fun parseInlineMarkdown(raw: String): Pair<String, List<InlineSpan>> {
        val sb = StringBuilder()
        val spans = mutableListOf<InlineSpan>()
        var i = 0
        while (i < raw.length) {
            when {
                raw.startsWith("**", i) -> {
                    val close = raw.indexOf("**", i + 2)
                    if (close > i + 2) {
                        val start = sb.length
                        sb.append(raw.substring(i + 2, close))
                        spans += InlineSpan(start, sb.length, InlineStyle.BOLD)
                        i = close + 2
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw[i] == '*' || raw[i] == '_' -> {
                    val marker = raw[i]
                    val close = raw.indexOf(marker, i + 1)
                    if (close > i + 1 && !raw.substring(i + 1, close).contains(' ').let { false }) {
                        val start = sb.length
                        sb.append(raw.substring(i + 1, close))
                        spans += InlineSpan(start, sb.length, InlineStyle.ITALIC)
                        i = close + 1
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw[i] == '`' -> {
                    val close = raw.indexOf('`', i + 1)
                    if (close > i + 1) {
                        val start = sb.length
                        sb.append(raw.substring(i + 1, close))
                        spans += InlineSpan(start, sb.length, InlineStyle.CODE)
                        i = close + 1
                    } else {
                        sb.append(raw[i++])
                    }
                }
                else -> sb.append(raw[i++])
            }
        }
        return sb.toString() to spans
    }

    private fun extractYamlFrontmatter(text: String): Pair<Map<String, String>, String> {
        if (!text.startsWith("---\n")) return emptyMap<String, String>() to text
        val endIdx = text.indexOf("\n---\n", startIndex = 4)
        if (endIdx < 0 || endIdx > 2500) return emptyMap<String, String>() to text
        val yaml = text.substring(4, endIdx)
        val body = text.substring(endIdx + 5)
        val map = LinkedHashMap<String, String>()
        for (line in yaml.lines()) {
            val colon = line.indexOf(':')
            if (colon > 0) {
                val key = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim().trim('"', '\'')
                map[key] = value
            }
        }
        return map to body
    }

    private fun decodeSmartText(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrElse {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}
