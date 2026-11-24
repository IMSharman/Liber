package com.imsharman.liber.data.parser

enum class InlineStyle {
    BOLD,
    ITALIC,
    CODE,
    STRIKETHROUGH
}

data class InlineSpan(
    val start: Int,
    val end: Int,
    val style: InlineStyle
)

sealed interface ContentBlock {
    fun plainText(): String

    fun wordCount(): Int {
        val txt = plainText().trim()
        if (txt.isEmpty()) return 0
        return txt.split(WHITESPACE_REGEX).count { it.isNotEmpty() }
    }

    data class Heading(
        val level: Int,
        val text: String
    ) : ContentBlock {
        override fun plainText(): String = text
    }

    data class Paragraph(
        val text: String,
        val spans: List<InlineSpan> = emptyList()
    ) : ContentBlock {
        override fun plainText(): String = text
    }

    data class Quote(
        val text: String,
        val attribution: String? = null
    ) : ContentBlock {
        override fun plainText(): String =
            if (attribution.isNullOrBlank()) text else "$text — $attribution"
    }

    data class CodeBlock(
        val code: String
    ) : ContentBlock {
        override fun plainText(): String = code
    }

    data class ListItems(
        val ordered: Boolean,
        val items: List<String>
    ) : ContentBlock {
        override fun plainText(): String = items.joinToString("\n")
    }

    data class EmbeddedImage(
        val localCachePath: String,
        val altText: String = ""
    ) : ContentBlock {
        override fun plainText(): String = altText
    }

    data object Divider : ContentBlock {
        override fun plainText(): String = ""
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}

data class BookChapter(
    val index: Int,
    val title: String,
    val blocks: List<ContentBlock>,
    val wordCount: Int = blocks.sumOf { it.wordCount() }
)

data class TocEntry(
    val title: String,
    val chapterIndex: Int = 0,
    val blockIndex: Int = 0,
    val pageIndex: Int = 0,
    val depth: Int = 0
)

data class ParsedBookMetadata(
    val title: String,
    val author: String = "Unknown Author",
    val seriesName: String? = null,
    val seriesIndex: Float? = null,
    val description: String = "",
    val publisher: String = "",
    val isbn: String = "",
    val language: String = "",
    val tags: List<String> = emptyList(),
    val embeddedCoverBytes: ByteArray? = null,
    val totalUnits: Int = 1,
    val totalWords: Int = 0
)

sealed interface LoadedBookContent {
    val metadata: ParsedBookMetadata
    val toc: List<TocEntry>

    data class Reflowable(
        override val metadata: ParsedBookMetadata,
        val chapters: List<BookChapter>,
        override val toc: List<TocEntry>
    ) : LoadedBookContent

    data class VisualPaged(
        override val metadata: ParsedBookMetadata,
        val pageCount: Int,
        override val toc: List<TocEntry>,
        val reflowChapters: List<BookChapter> = emptyList()
    ) : LoadedBookContent
}
