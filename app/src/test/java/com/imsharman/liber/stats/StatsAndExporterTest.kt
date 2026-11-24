package com.imsharman.liber.stats

import com.imsharman.liber.data.export.NotesMarkdownExporter
import com.imsharman.liber.data.local.entity.BookEntity
import com.imsharman.liber.data.local.entity.BookmarkEntity
import com.imsharman.liber.data.local.entity.HighlightEntity
import com.imsharman.liber.data.local.entity.ReadingSessionEntity
import com.imsharman.liber.data.local.entity.ReadingSessionWithBook
import com.imsharman.liber.data.repository.StatsCalculator
import com.imsharman.liber.domain.model.BookFormat
import com.imsharman.liber.domain.model.HighlightColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsAndExporterTest {

    private val dummyBook = BookEntity(
        id = 1L,
        uriString = "content://books/1",
        fileName = "dune.epub",
        fileSizeBytes = 1024L,
        contentFingerprint = "abc123",
        format = BookFormat.EPUB,
        title = "Dune",
        author = "Frank Herbert",
        seriesName = "Dune Chronicles",
        seriesIndex = 1f,
        progressFraction = 0.45f
    )

    @Test
    fun `StatsCalculator computes active streak, longest streak, heatmap, and WPM`() {
        val today = LocalDate.of(2026, 11, 23)
        val sessions = listOf(
            sessionOn(today, durationSecs = 1200, words = 5000),
            sessionOn(today.minusDays(1), durationSecs = 900, words = 3600),
            sessionOn(today.minusDays(2), durationSecs = 600, words = 2400),
            // Gap on minusDays(3), earlier 4-day streak
            sessionOn(today.minusDays(10), durationSecs = 600, words = 2400),
            sessionOn(today.minusDays(11), durationSecs = 600, words = 2400),
            sessionOn(today.minusDays(12), durationSecs = 600, words = 2400),
            sessionOn(today.minusDays(13), durationSecs = 600, words = 2400)
        )

        val summary = StatsCalculator.computeSummary(
            sessionsWithBooks = sessions,
            dailyGoalMinutes = 20,
            totalBooksInLibrary = 8,
            totalBooksFinished = 3,
            totalHighlightsCount = 12,
            today = today
        )

        assertEquals(3, summary.currentStreakDays)
        assertEquals(4, summary.longestStreakDays)
        assertEquals(20, summary.todayMinutes)
        assertEquals(84, summary.heatmapDays.size)
        assertTrue(summary.averageWpm in 230..260)
    }

    @Test
    fun `NotesMarkdownExporter formats Obsidian YAML frontmatter and quote callouts`() {
        val highlights = listOf(
            HighlightEntity(
                id = 1L,
                uuid = "u1",
                bookId = 1L,
                chapterIndex = 0,
                chapterTitle = "Book I: Dune",
                blockIndex = 4,
                quoteText = "Fear is the mind-killer.",
                noteText = "Litany Against Fear.",
                color = HighlightColor.AMBER,
                createdAtEpochMs = 1700000000000L
            )
        )
        val bookmarks = listOf(
            BookmarkEntity(
                id = 1L,
                uuid = "b1",
                bookId = 1L,
                chapterIndex = 2,
                chapterTitle = "Arrakis",
                blockIndex = 0,
                pageIndex = 12,
                progressFraction = 0.25f,
                label = "Arrival on Arrakis",
                snippet = "The desert planet."
            )
        )

        val md = NotesMarkdownExporter.exportSingleBookMarkdown(dummyBook, highlights, bookmarks)
        assertTrue(md.contains("title: \"Dune\""))
        assertTrue(md.contains("series: \"Dune Chronicles\""))
        assertTrue(md.contains("> [!quote] Book I: Dune • Amber"))
        assertTrue(md.contains("> Fear is the mind-killer."))
        assertTrue(md.contains("**Note:** Litany Against Fear."))
        assertTrue(md.contains("Arrival on Arrakis"))
    }

    private fun sessionOn(date: LocalDate, durationSecs: Int, words: Int) = ReadingSessionWithBook(
        session = ReadingSessionEntity(
            uuid = date.toString(),
            bookId = 1L,
            dateIso = date.toString(),
            startedAtEpochMs = 1700000000000L,
            durationSeconds = durationSecs,
            pagesTurned = 10,
            wordsRead = words
        ),
        book = dummyBook
    )
}
