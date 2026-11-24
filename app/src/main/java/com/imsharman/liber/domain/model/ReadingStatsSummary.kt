package com.imsharman.liber.domain.model

import java.time.LocalDate

data class DailyReadingPoint(
    val date: LocalDate,
    val minutesRead: Int,
    val pagesRead: Int,
    val wordsRead: Int
)

data class ReadingSessionRecord(
    val id: Long,
    val bookId: Long,
    val bookTitle: String,
    val startedAtEpochMs: Long,
    val durationSeconds: Int,
    val pagesTurned: Int,
    val wordsRead: Int
) {
    val estimatedWpm: Int
        get() = if (durationSeconds >= 30 && wordsRead > 0) {
            ((wordsRead * 60f) / durationSeconds).toInt().coerceIn(60, 1200)
        } else {
            0
        }
}

data class ReadingStatsSummary(
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val todayMinutes: Int = 0,
    val dailyGoalMinutes: Int = 30,
    val totalMinutesAllTime: Int = 0,
    val totalBooksFinished: Int = 0,
    val totalBooksInLibrary: Int = 0,
    val totalHighlightsCount: Int = 0,
    val totalWordsRead: Long = 0L,
    val averageWpm: Int = 240,
    val heatmapDays: List<DailyReadingPoint> = emptyList(),
    val recentSessions: List<ReadingSessionRecord> = emptyList()
)
