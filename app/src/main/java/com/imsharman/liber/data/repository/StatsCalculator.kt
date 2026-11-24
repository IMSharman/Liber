package com.imsharman.liber.data.repository

import com.imsharman.liber.data.local.entity.ReadingSessionWithBook
import com.imsharman.liber.domain.model.DailyReadingPoint
import com.imsharman.liber.domain.model.ReadingSessionRecord
import com.imsharman.liber.domain.model.ReadingStatsSummary
import java.time.LocalDate

object StatsCalculator {

    fun computeSummary(
        sessionsWithBooks: List<ReadingSessionWithBook>,
        dailyGoalMinutes: Int,
        totalBooksInLibrary: Int,
        totalBooksFinished: Int,
        totalHighlightsCount: Int,
        today: LocalDate = LocalDate.now()
    ): ReadingStatsSummary {
        val byDate = sessionsWithBooks.groupBy { entry ->
            runCatching { LocalDate.parse(entry.session.dateIso) }.getOrDefault(today)
        }

        val minutesPerDate: Map<LocalDate, Int> = byDate.mapValues { (_, list) ->
            (list.sumOf { it.session.durationSeconds } / 60).coerceAtLeast(if (list.isNotEmpty()) 1 else 0)
        }

        val activeDatesSorted = minutesPerDate.filterValues { it > 0 }.keys.sorted()

        // Calculate current streak (allows today OR yesterday to keep streak alive before today's session)
        var currentStreak = 0
        var cursor = if ((minutesPerDate[today] ?: 0) > 0) today else today.minusDays(1)
        while ((minutesPerDate[cursor] ?: 0) > 0) {
            currentStreak++
            cursor = cursor.minusDays(1)
        }

        // Calculate longest streak
        var longestStreak = 0
        var runLength = 0
        var prevDate: LocalDate? = null
        for (date in activeDatesSorted) {
            if (prevDate != null && date == prevDate.plusDays(1)) {
                runLength++
            } else {
                runLength = 1
            }
            if (runLength > longestStreak) longestStreak = runLength
            prevDate = date
        }

        // Build 84-day (12-week) activity heatmap ending today
        val startDay = today.minusDays(83)
        val heatmap = (0L..83L).map { offset ->
            val d = startDay.plusDays(offset)
            val entries = byDate[d].orEmpty()
            val secs = entries.sumOf { it.session.durationSeconds }
            val mins = if (secs == 0) 0 else (secs / 60).coerceAtLeast(1)
            DailyReadingPoint(
                date = d,
                minutesRead = mins,
                pagesRead = entries.sumOf { it.session.pagesTurned },
                wordsRead = entries.sumOf { it.session.wordsRead }
            )
        }

        val totalSeconds = sessionsWithBooks.sumOf { it.session.durationSeconds }
        val totalWords = sessionsWithBooks.sumOf { it.session.wordsRead.toLong() }

        val validWpmSamples = sessionsWithBooks.mapNotNull { entry ->
            val s = entry.session
            if (s.durationSeconds >= 30 && s.wordsRead >= 40) {
                ((s.wordsRead * 60f) / s.durationSeconds).toInt().coerceIn(80, 900)
            } else null
        }
        val avgWpm = if (validWpmSamples.isNotEmpty()) {
            validWpmSamples.average().toInt()
        } else {
            240
        }

        val recent = sessionsWithBooks.take(25).map { entry ->
            ReadingSessionRecord(
                id = entry.session.id,
                bookId = entry.book.id,
                bookTitle = entry.book.title,
                startedAtEpochMs = entry.session.startedAtEpochMs,
                durationSeconds = entry.session.durationSeconds,
                pagesTurned = entry.session.pagesTurned,
                wordsRead = entry.session.wordsRead
            )
        }

        return ReadingStatsSummary(
            currentStreakDays = currentStreak,
            longestStreakDays = maxOf(longestStreak, currentStreak),
            todayMinutes = minutesPerDate[today] ?: 0,
            dailyGoalMinutes = dailyGoalMinutes.coerceAtLeast(5),
            totalMinutesAllTime = totalSeconds / 60,
            totalBooksFinished = totalBooksFinished,
            totalBooksInLibrary = totalBooksInLibrary,
            totalHighlightsCount = totalHighlightsCount,
            totalWordsRead = totalWords,
            averageWpm = avgWpm,
            heatmapDays = heatmap,
            recentSessions = recent
        )
    }
}
