package com.imsharman.liber.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imsharman.liber.AppContainer
import com.imsharman.liber.data.repository.StatsCalculator
import com.imsharman.liber.domain.model.DailyReadingPoint
import com.imsharman.liber.domain.model.ReaderSettings
import com.imsharman.liber.domain.model.ReadingStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val repo = container.libraryRepository
    val sessions by repo.observeAllSessionsWithBook().collectAsState(initial = emptyList())
    val allBooks by repo.observeAllBooksWithCollections().collectAsState(initial = emptyList())
    val allHighlights by repo.observeAllHighlightsWithBook().collectAsState(initial = emptyList())
    val settings by container.preferencesRepository.readerSettingsFlow.collectAsState(initial = ReaderSettings())

    val summary = remember(sessions, allBooks, allHighlights, settings.dailyGoalMinutes) {
        StatsCalculator.computeSummary(
            sessionsWithBooks = sessions,
            dailyGoalMinutes = settings.dailyGoalMinutes,
            totalBooksInLibrary = allBooks.size,
            totalBooksFinished = allBooks.count { it.book.readingStatus == ReadingStatus.HAVE_READ },
            totalHighlightsCount = allHighlights.size
        )
    }

    val dtFormatter = remember { DateTimeFormatter.ofPattern("MMM d, HH:mm") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Statistics & Streaks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Streak & Daily Goal Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${summary.currentStreakDays} Day Streak",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Longest streak: ${summary.longestStreakDays} days  •  Today: ${summary.todayMinutes} / ${summary.dailyGoalMinutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        val goalRatio = (summary.todayMinutes.toFloat() / summary.dailyGoalMinutes.coerceAtLeast(1))
                            .coerceIn(0f, 1f)
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { goalRatio },
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 6.dp
                            )
                            Text(
                                text = "${(goalRatio * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Key Metrics Grid
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val hrs = summary.totalMinutesAllTime / 60
                    val mins = summary.totalMinutesAllTime % 60
                    MetricTile(
                        label = "Total Time",
                        value = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m",
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Reading Speed",
                        value = "${summary.averageWpm} WPM",
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Finished",
                        value = "${summary.totalBooksFinished} books",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 12-Week Reading Heatmap
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("12-Week Activity Heatmap", style = MaterialTheme.typography.titleMedium)
                        HeatmapGrid(
                            days = summary.heatmapDays,
                            goalMinutes = summary.dailyGoalMinutes
                        )
                        Text(
                            text = "${summary.totalWordsRead} words read  •  ${summary.totalHighlightsCount} highlights saved",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recent Sessions
            item {
                Text("Recent Reading Sessions", style = MaterialTheme.typography.titleMedium)
            }

            if (summary.recentSessions.isEmpty()) {
                item {
                    Text(
                        text = "Open any book to begin tracking reading sessions, daily streaks, and personal WPM speed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(summary.recentSessions, key = { it.id }) { session ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.bookTitle, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                val dateStr = Instant.ofEpochMilli(session.startedAtEpochMs)
                                    .atZone(ZoneId.systemDefault()).format(dtFormatter)
                                val wpmPart = if (session.estimatedWpm > 0) " • ${session.estimatedWpm} WPM" else ""
                                Text(
                                    text = "$dateStr$wpmPart",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val m = (session.durationSeconds / 60).coerceAtLeast(1)
                            Text("${m} min", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeatmapGrid(
    days: List<DailyReadingPoint>,
    goalMinutes: Int
) {
    val weeks = days.chunked(7)
    val baseColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { pt ->
                    val alpha = when {
                        pt.minutesRead <= 0 -> 0f
                        pt.minutesRead >= goalMinutes -> 1.0f
                        pt.minutesRead >= goalMinutes / 2 -> 0.68f
                        else -> 0.38f
                    }
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (alpha == 0f) emptyColor else baseColor.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}
