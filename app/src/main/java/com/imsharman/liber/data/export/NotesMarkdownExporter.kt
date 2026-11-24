package com.imsharman.liber.data.export

import com.imsharman.liber.data.local.entity.BookEntity
import com.imsharman.liber.data.local.entity.BookmarkEntity
import com.imsharman.liber.data.local.entity.HighlightEntity
import com.imsharman.liber.data.local.entity.HighlightWithBook
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Exports book highlights, quotes, personal notes, and bookmarks into clean
 * Obsidian-compatible Markdown (`.md`) with YAML frontmatter and callout blocks!
 */
object NotesMarkdownExporter {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun exportSingleBookMarkdown(
        book: BookEntity,
        highlights: List<HighlightEntity>,
        bookmarks: List<BookmarkEntity>
    ): String = buildString {
        appendLine("---")
        appendLine("title: \"${escapeYaml(book.title)}\"")
        appendLine("author: \"${escapeYaml(book.author)}\"")
        if (!book.seriesName.isNullOrBlank()) {
            appendLine("series: \"${escapeYaml(book.seriesName)}\"")
            if (book.seriesIndex != null) {
                appendLine("series_index: ${formatSeriesNumber(book.seriesIndex)}")
            }
        }
        appendLine("format: ${book.format.displayName}")
        appendLine("progress: ${(book.progressFraction * 100).toInt()}%")
        if (book.personalRating > 0) {
            appendLine("rating: ${book.personalRating}/5")
        }
        appendLine("tags: [liber-notes, book-highlights]")
        appendLine("---")
        appendLine()
        appendLine("# ${book.title}")
        appendLine("**Author:** ${book.author}")
        if (!book.seriesName.isNullOrBlank()) {
            val idxPart = book.seriesIndex?.let { " #${formatSeriesNumber(it)}" }.orEmpty()
            appendLine("  \n**Series:** ${book.seriesName}$idxPart")
        }
        appendLine()

        if (highlights.isNotEmpty()) {
            appendLine("## Highlights & Notes (${highlights.size})")
            appendLine()
            for (hl in highlights) {
                val ts = formatTimestamp(hl.createdAtEpochMs)
                appendLine("> [!quote] ${hl.chapterTitle} • ${hl.color.label} • $ts")
                for (qLine in hl.quoteText.lines()) {
                    appendLine("> $qLine")
                }
                appendLine()
                if (hl.noteText.isNotBlank()) {
                    appendLine("**Note:** ${hl.noteText.trim()}")
                    appendLine()
                }
            }
        }

        if (bookmarks.isNotEmpty()) {
            appendLine("## Bookmarks (${bookmarks.size})")
            appendLine()
            for (bm in bookmarks) {
                val pct = (bm.progressFraction * 100).toInt()
                appendLine("- **${bm.label}** — *${bm.chapterTitle}* ($pct%)")
                if (bm.snippet.isNotBlank()) {
                    appendLine("  - \"${bm.snippet.trim()}\"")
                }
            }
            appendLine()
        }
    }

    fun exportAllHighlightsMarkdown(items: List<HighlightWithBook>): String = buildString {
        appendLine("# Liber Reading Quotes & Highlights")
        appendLine()
        val grouped = items.groupBy { it.book.id }
        for ((_, group) in grouped) {
            val book = group.first().book
            appendLine("## ${book.title} — *${book.author}*")
            appendLine()
            for (entry in group.sortedBy { it.highlight.chapterIndex * 10000 + it.highlight.blockIndex }) {
                val hl = entry.highlight
                appendLine("> [!quote] ${hl.chapterTitle} (${hl.color.label})")
                for (line in hl.quoteText.lines()) {
                    appendLine("> $line")
                }
                appendLine()
                if (hl.noteText.isNotBlank()) {
                    appendLine("**Note:** ${hl.noteText.trim()}")
                    appendLine()
                }
            }
            appendLine("---")
            appendLine()
        }
    }

    private fun escapeYaml(input: String): String = input.replace("\"", "\\\"")

    private fun formatTimestamp(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(dateFormatter)

    fun formatSeriesNumber(num: Float): String =
        if (num % 1f == 0f) num.toInt().toString() else num.toString()
}
