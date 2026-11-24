package com.imsharman.liber.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.imsharman.liber.AppContainer
import com.imsharman.liber.data.repository.StatsCalculator
import com.imsharman.liber.ui.annotations.AnnotationsHubScreen
import com.imsharman.liber.ui.bookdetail.BookDetailScreen
import com.imsharman.liber.ui.library.LibraryScreen
import com.imsharman.liber.ui.library.LibraryViewModel
import com.imsharman.liber.ui.reader.ReaderScreen
import com.imsharman.liber.ui.reader.ReaderViewModel
import com.imsharman.liber.ui.settings.SettingsScreen
import com.imsharman.liber.ui.stats.StatsScreen
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun LiberNavHost(
    container: AppContainer,
    externalOpenBookId: Long?,
    onConsumedExternalBook: () -> Unit,
    volumeKeyTurnFlow: SharedFlow<Int>,
    onSetInterceptVolumeKeys: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))

    val sessions by container.libraryRepository.observeAllSessionsWithBook().collectAsState(initial = emptyList())
    val avgWpm = remember(sessions) {
        StatsCalculator.computeSummary(
            sessionsWithBooks = sessions,
            dailyGoalMinutes = 30,
            totalBooksInLibrary = 0,
            totalBooksFinished = 0,
            totalHighlightsCount = 0
        ).averageWpm
    }

    LaunchedEffect(externalOpenBookId) {
        if (externalOpenBookId != null && externalOpenBookId > 0L) {
            onConsumedExternalBook()
            navController.navigate("reader/$externalOpenBookId?chapter=-1&page=-1")
        }
    }

    NavHost(
        navController = navController,
        startDestination = "library"
    ) {
        composable("library") {
            LibraryScreen(
                viewModel = libraryViewModel,
                container = container,
                onOpenReader = { id ->
                    navController.navigate("reader/$id?chapter=-1&page=-1")
                },
                onOpenDetails = { id ->
                    navController.navigate("detail/$id")
                },
                onNavigateAnnotations = { navController.navigate("annotations") },
                onNavigateStats = { navController.navigate("stats") },
                onNavigateSettings = { navController.navigate("settings") }
            )
        }

        composable(
            route = "detail/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStack ->
            val bookId = backStack.arguments?.getLong("bookId") ?: return@composable
            BookDetailScreen(
                bookId = bookId,
                container = container,
                averageWpm = avgWpm,
                onBack = { navController.popBackStack() },
                onOpenReader = { id, chap, page ->
                    val c = chap ?: -1
                    val p = page ?: -1
                    navController.navigate("reader/$id?chapter=$c&page=$p")
                },
                onAuthorSelected = { author ->
                    libraryViewModel.jumpToAuthor(author)
                    navController.popBackStack("library", inclusive = false)
                },
                onSeriesSelected = { series ->
                    libraryViewModel.jumpToSeries(series)
                    navController.popBackStack("library", inclusive = false)
                }
            )
        }

        composable(
            route = "reader/{bookId}?chapter={chapter}&page={page}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType },
                navArgument("chapter") { type = NavType.IntType; defaultValue = -1 },
                navArgument("page") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStack ->
            val bookId = backStack.arguments?.getLong("bookId") ?: return@composable
            val rawChap = backStack.arguments?.getInt("chapter") ?: -1
            val rawPage = backStack.arguments?.getInt("page") ?: -1
            val readerVm: ReaderViewModel = viewModel(
                key = "reader_${bookId}_${rawChap}_${rawPage}",
                factory = ReaderViewModel.factory(
                    bookId = bookId,
                    jumpChapter = rawChap.takeIf { it >= 0 },
                    jumpPage = rawPage.takeIf { it >= 0 },
                    container = container
                )
            )
            ReaderScreen(
                viewModel = readerVm,
                volumeKeyTurnFlow = volumeKeyTurnFlow,
                onSetInterceptVolumeKeys = onSetInterceptVolumeKeys,
                onBack = { navController.popBackStack() }
            )
        }

        composable("annotations") {
            AnnotationsHubScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onJumpToBookSpot = { bookId, chap, page ->
                    navController.navigate("reader/$bookId?chapter=$chap&page=$page")
                }
            )
        }

        composable("stats") {
            StatsScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
