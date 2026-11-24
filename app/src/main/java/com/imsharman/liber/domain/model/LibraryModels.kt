package com.imsharman.liber.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BookFormat(
    val displayName: String,
    val extensions: List<String>,
    val isVisualPagedDefault: Boolean,
    val badgeColorArgb: Long
) {
    EPUB("EPUB", listOf("epub"), false, 0xFF2B6CB0),
    PDF("PDF", listOf("pdf"), true, 0xFFC53030),
    CBZ("CBZ", listOf("cbz"), true, 0xFF805AD5),
    CBR("CBR", listOf("cbr"), true, 0xFF6B46C1),
    MOBI("MOBI", listOf("mobi", "prc"), false, 0xFF2F855A),
    AZW3("AZW3", listOf("azw3", "azw", "kf8"), false, 0xFF2C7A7B),
    FB2("FB2", listOf("fb2"), false, 0xFFD69E2E),
    TXT("TXT", listOf("txt", "text"), false, 0xFF4A5568),
    MARKDOWN("MD", listOf("md", "markdown"), false, 0xFF3182CE),
    DOCX("DOCX", listOf("docx"), false, 0xFF2B579A);

    companion object {
        val ALL_SUPPORTED_EXTENSIONS: Set<String> = entries
            .flatMap { it.extensions }
            .toSet()

        /**
         * Strictly matches recognized book & document extensions ONLY.
         * Standalone images (.jpg, .png, .webp, .gif), videos, audio, and system files
         * ALWAYS return null so Liber never ingests or touches non-book files.
         */
        fun fromFileName(fileName: String): BookFormat? {
            val clean = fileName.trim().lowercase()
            if (clean.isEmpty() || clean.startsWith(".")) return null
            if (clean.endsWith(".fb2.zip")) return FB2
            val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
            if (ext.isEmpty()) return null
            return entries.firstOrNull { ext in it.extensions }
        }
    }
}

@Serializable
enum class ReadingStatus(val label: String) {
    UNREAD("Unread"),
    READING_NOW("Reading Now"),
    TO_READ("To Read"),
    HAVE_READ("Have Read")
}

enum class LibrarySection(val title: String) {
    READING_NOW("Reading Now"),
    ALL_BOOKS("Books & Documents"),
    FAVORITES("Favorites"),
    TO_READ("To Read"),
    HAVE_READ("Have Read"),
    AUTHORS("Authors"),
    SERIES("Series"),
    COLLECTIONS("Collections"),
    FOLDERS("Folders"),
    ANNOTATIONS("Quotes & Notes"),
    STATS("Reading Stats"),
    SETTINGS("Settings & Sync")
}

enum class LibraryViewMode(val label: String) {
    DETAILED_LIST("Detailed Cards"),
    GRID("Cover Grid"),
    COMPACT_LIST("Compact List")
}

enum class LibrarySortOrder(val label: String) {
    LAST_READ("Last Read"),
    DATE_ADDED("Date Added"),
    TITLE("Title (A–Z)"),
    AUTHOR("Author (A–Z)"),
    PROGRESS("Reading Progress"),
    SERIES_ORDER("Series Order"),
    FILE_SIZE("File Size")
}

@Serializable
enum class HighlightColor(
    val label: String,
    val bgHexLight: Long,
    val bgHexDark: Long,
    val accentHex: Long
) {
    AMBER("Amber", 0x66F6AD55, 0x55DD6B20, 0xFFDD6B20),
    EMERALD("Emerald", 0x6668D391, 0x552F855A, 0xFF2F855A),
    SKY("Sky", 0x6663B3ED, 0x552B6CB0, 0xFF2B6CB0),
    VIOLET("Violet", 0x66B794F4, 0x556B46C1, 0xFF6B46C1),
    CORAL("Coral", 0x66FC8181, 0x55C53030, 0xFFC53030)
}
