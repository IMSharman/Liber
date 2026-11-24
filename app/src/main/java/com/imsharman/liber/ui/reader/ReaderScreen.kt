package com.imsharman.liber.ui.reader

import android.app.Activity
import android.content.Context
import android.os.BatteryManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.imsharman.liber.data.local.entity.HighlightEntity
import com.imsharman.liber.data.parser.LoadedBookContent
import com.imsharman.liber.domain.model.BookFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private data class PendingHighlightSelection(
    val chapterIndex: Int,
    val chapterTitle: String,
    val blockIndex: Int,
    val text: String,
    val existing: HighlightEntity?
)

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    volumeKeyTurnFlow: kotlinx.coroutines.flow.SharedFlow<Int>,
    onSetInterceptVolumeKeys: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    var hudVisible by remember { mutableStateOf(false) }
    var showControlsSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var ttsBarVisible by remember { mutableStateOf(false) }
    var pendingHighlight by remember { mutableStateOf<PendingHighlightSelection?>(null) }

    // Brightness edge gesture state
    var brightnessLevel by remember { mutableFloatStateOf(0.55f) }
    var extraNightDimAlpha by remember { mutableFloatStateOf(0f) }
    var showBrightnessHud by remember { mutableStateOf(false) }

    // Volume-key page turn tick
    var volumeTurnTick by remember { mutableStateOf<Pair<Long, Int>?>(null) }

    LaunchedEffect(settings.volumeKeysTurnPages) {
        onSetInterceptVolumeKeys(settings.volumeKeysTurnPages)
    }

    DisposableEffect(Unit) {
        onDispose { onSetInterceptVolumeKeys(false) }
    }

    LaunchedEffect(volumeKeyTurnFlow) {
        volumeKeyTurnFlow.collect { delta ->
            volumeTurnTick = System.nanoTime() to delta
        }
    }

    // Keep screen awake while reading if enabled
    DisposableEffect(settings.keepScreenAwake, activity) {
        if (settings.keepScreenAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Flush reading session when pausing or leaving ReaderScreen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushReadingSession()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushReadingSession()
        }
    }

    // Initialize Text-to-Speech Controller
    val ttsController = remember {
        TtsController(
            context = context,
            scope = scope,
            onActivePositionChanged = { chap, block ->
                viewModel.onReflowPositionChanged(chap, block)
            }
        )
    }
    val ttsState by ttsController.state.collectAsState()

    DisposableEffect(ttsController) {
        onDispose { ttsController.shutdown() }
    }

    LaunchedEffect(state.activeReflowChapters) {
        ttsController.bindChapters(state.activeReflowChapters)
    }

    LaunchedEffect(settings.ttsSpeechRate, settings.ttsPitch) {
        ttsController.updateVoiceParameters(settings.ttsSpeechRate, settings.ttsPitch)
    }

    // Live clock & battery for minimalist ReadEra-style footer
    var timeString by remember { mutableStateOf("") }
    var batteryPct by remember { mutableStateOf(100) }
    LaunchedEffect(Unit) {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        while (true) {
            timeString = LocalTime.now().format(fmt)
            batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100
            delay(30_000L)
        }
    }

    val bgColor = Color(settings.activeBackgroundArgb)
    val fgColor = Color(settings.activeTextArgb)
    val book = state.book
    val content = state.content

    val isBookmarkedHere = remember(bookmarks, state.currentChapterIndex, state.currentBlockIndex, state.currentPageIndex) {
        bookmarks.any { bm ->
            bm.chapterIndex == state.currentChapterIndex &&
                (bm.blockIndex == state.currentBlockIndex || bm.pageIndex == state.currentPageIndex)
        }
    }

    var measuredBookPage by remember { mutableIntStateOf(0) }
    var measuredTotalPages by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (state.isLoading || book == null || content == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val useVisualPager = content is LoadedBookContent.VisualPaged &&
                !(book.format == BookFormat.PDF && settings.pdfReflowMode && content.reflowChapters.isNotEmpty())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (useVisualPager) {
                        VisualPageReaderView(
                            uriString = book.uriString,
                            format = book.format,
                            pageCount = content.pageCount,
                            currentPageIndex = state.currentPageIndex,
                            settings = settings,
                            container = viewModel.container,
                            volumeTurnEventTick = volumeTurnTick,
                            onPageChanged = { viewModel.onVisualPageChanged(it) },
                            onToggleHud = { hudVisible = !hudVisible }
                        )
                    } else {
                        ReflowableReaderView(
                            chapters = state.activeReflowChapters,
                            currentChapterIndex = state.currentChapterIndex,
                            currentBlockIndex = state.currentBlockIndex,
                            settings = settings,
                            highlights = highlights,
                            ttsChapterIndex = if (ttsState.isSpeaking) ttsState.chapterIndex else null,
                            ttsBlockIndex = if (ttsState.isSpeaking) ttsState.blockIndex else null,
                            volumeTurnEventTick = volumeTurnTick,
                            onPositionChanged = { chap, blk, frac ->
                                val isPaged = settings.scrollMode == com.imsharman.liber.domain.model.ReaderScrollMode.PAGINATED_HORIZONTAL
                                viewModel.onReflowPositionChanged(
                                    chapterIndex = chap,
                                    blockIndex = blk,
                                    subPageFraction = if (isPaged) 0f else frac,
                                    exactBookFraction = if (isPaged && frac > 0f) frac else null
                                )
                            },
                            onMeasuredPageStatsChanged = { curP, totP ->
                                measuredBookPage = curP
                                measuredTotalPages = totP
                            },
                            onToggleHud = { hudVisible = !hudVisible },
                            onBlockLongPressed = { chapIdx, chapTitle, blkIdx, txt, existingHl ->
                                pendingHighlight = PendingHighlightSelection(
                                    chapterIndex = chapIdx,
                                    chapterTitle = chapTitle,
                                    blockIndex = blkIdx,
                                    text = txt,
                                    existing = existingHl
                                )
                            }
                        )
                    }

                    // Left-edge vertical drag strip for ReadEra-style brightness control
                    if (settings.leftEdgeBrightnessSwipe) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(22.dp)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = { showBrightnessHud = true },
                                        onDragEnd = {
                                            scope.launch {
                                                delay(900L)
                                                showBrightnessHud = false
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            val delta = -dragAmount / size.height.coerceAtLeast(1).toFloat()
                                            val next = (brightnessLevel + delta * 1.25f).coerceIn(-0.35f, 1.0f)
                                            brightnessLevel = next
                                            if (next >= 0.02f) {
                                                extraNightDimAlpha = 0f
                                                activity?.window?.let { win ->
                                                    val lp = win.attributes
                                                    lp.screenBrightness = next
                                                    win.attributes = lp
                                                }
                                            } else {
                                                extraNightDimAlpha = (-next).coerceIn(0f, 0.55f)
                                                activity?.window?.let { win ->
                                                    val lp = win.attributes
                                                    lp.screenBrightness = 0.01f
                                                    win.attributes = lp
                                                }
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }

                // Minimalist ReadEra-style Status Footer (keeps steady height so toggling HUD doesn't shift page layout)
                if (settings.showMiniStatusFooter) {
                    val chapterLabel = state.activeReflowChapters.getOrNull(state.currentChapterIndex)?.title
                        ?: "Page ${state.currentPageIndex + 1} / ${state.totalUnits}"
                    val pageStatPart = if (!useVisualPager && measuredTotalPages > 0) {
                        "$measuredBookPage / $measuredTotalPages  •  "
                    } else ""
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (hudVisible) 0f else 1f)
                            .padding(horizontal = settings.horizontalMarginDp.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = chapterLabel,
                            color = fgColor.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "$pageStatPart${(state.progressFraction * 100).toInt()}%  •  $timeString  •  $batteryPct%",
                            color = fgColor.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Extra-dim sub-zero night filter scrim
        if (extraNightDimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = extraNightDimAlpha))
            )
        }

        // Brightness pill indicator
        AnimatedVisibility(
            visible = showBrightnessHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.78f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    val displayPct = ((brightnessLevel.coerceAtLeast(0f)) * 100).toInt()
                    Text(
                        text = if (extraNightDimAlpha > 0f) "Extra Night Dim" else "Brightness $displayPct%",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Top Reader HUD Bar
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Library")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = state.activeReflowChapters.getOrNull(state.currentChapterIndex)?.title
                            ?: "Page ${state.currentPageIndex + 1} of ${state.totalUnits}"
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (state.activeReflowChapters.isNotEmpty()) {
                        IconButton(onClick = { showSearchSheet = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search in book")
                        }
                        IconButton(
                            onClick = {
                                ttsBarVisible = true
                                hudVisible = false
                                ttsController.togglePlayPause(
                                    state.currentChapterIndex,
                                    state.currentBlockIndex
                                )
                            }
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = "Read aloud (TTS)")
                        }
                    }

                    IconButton(onClick = viewModel::toggleBookmarkAtCurrentSpot) {
                        Icon(
                            imageVector = if (isBookmarkedHere) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark page",
                            tint = if (isBookmarkedHere) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = { showTocSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Contents & Quotes")
                    }

                    IconButton(onClick = { showControlsSheet = true }) {
                        Icon(Icons.Default.TextFormat, contentDescription = "Typography & Theme")
                    }
                }
            }
        }

        // Bottom Scrubber HUD + ReadEra Jump-Back Pill
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                state.jumpBackSpot?.let { origin ->
                    AssistChip(
                        onClick = viewModel::returnToJumpBackSpot,
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Return to ${origin.label}") },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    content is LoadedBookContent.VisualPaged && !settings.pdfReflowMode ->
                                        "Page ${state.currentPageIndex + 1} of ${content.pageCount}"
                                    measuredTotalPages > 0 ->
                                        "Page $measuredBookPage of $measuredTotalPages  •  Ch. ${state.currentChapterIndex + 1}/${state.activeReflowChapters.size.coerceAtLeast(1)}"
                                    else ->
                                        "Chapter ${state.currentChapterIndex + 1} of ${state.activeReflowChapters.size.coerceAtLeast(1)}"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "${(state.progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = state.progressFraction.coerceIn(0f, 1f),
                            onValueChange = { viewModel.scrubToFraction(it) }
                        )
                    }
                }
            }
        }

        // Floating TTS Mini-Player Bar
        if (ttsBarVisible || ttsState.isSpeaking) {
            FloatingTtsBar(
                ttsState = ttsState,
                speechRate = settings.ttsSpeechRate,
                onTogglePlayPause = {
                    ttsController.togglePlayPause(state.currentChapterIndex, state.currentBlockIndex)
                },
                onPrev = ttsController::skipPrevious,
                onNext = ttsController::skipNext,
                onCycleSpeed = {
                    val speeds = listOf(0.8f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                    val curIdx = speeds.indexOfFirst { kotlin.math.abs(it - settings.ttsSpeechRate) < 0.08f }
                    val nextRate = speeds[(curIdx + 1) % speeds.size]
                    scope.launch {
                        viewModel.prefs.updateReaderSettings { it.copy(ttsSpeechRate = nextRate) }
                    }
                },
                onSelectSleepMinutes = { mins, endChap ->
                    ttsController.setSleepTimerMinutes(mins, endChap)
                },
                onClose = {
                    ttsController.pause()
                    ttsBarVisible = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (hudVisible) 96.dp else 18.dp)
            )
        }
    }

    if (showControlsSheet && book != null) {
        ReaderControlsSheet(
            format = book.format,
            settings = settings,
            prefs = viewModel.prefs,
            sandboxManager = viewModel.container.sandboxManager,
            onPdfReflowToggled = { enabled ->
                viewModel.loadBook(forcePdfReflow = enabled)
            },
            onDismiss = { showControlsSheet = false }
        )
    }

    if (showTocSheet) {
        TocAndBookmarksSheet(
            toc = state.activeToc,
            bookmarks = bookmarks,
            highlights = highlights,
            currentChapterIndex = state.currentChapterIndex,
            onSelectToc = { entry ->
                if (content is LoadedBookContent.VisualPaged && !settings.pdfReflowMode) {
                    viewModel.onVisualPageChanged(entry.pageIndex, recordJumpOrigin = true)
                } else {
                    viewModel.onReflowPositionChanged(
                        entry.chapterIndex,
                        entry.blockIndex,
                        recordJumpOrigin = true
                    )
                }
                hudVisible = false
            },
            onSelectBookmark = { bm ->
                if (content is LoadedBookContent.VisualPaged && !settings.pdfReflowMode) {
                    viewModel.onVisualPageChanged(bm.pageIndex, recordJumpOrigin = true)
                } else {
                    viewModel.onReflowPositionChanged(bm.chapterIndex, bm.blockIndex, recordJumpOrigin = true)
                }
                hudVisible = false
            },
            onDeleteBookmark = viewModel::deleteBookmark,
            onSelectHighlight = { hl ->
                if (content is LoadedBookContent.VisualPaged && !settings.pdfReflowMode) {
                    viewModel.onVisualPageChanged(hl.pageIndex, recordJumpOrigin = true)
                } else {
                    viewModel.onReflowPositionChanged(hl.chapterIndex, hl.blockIndex, recordJumpOrigin = true)
                }
                hudVisible = false
            },
            onDismiss = { showTocSheet = false }
        )
    }

    if (showSearchSheet) {
        InBookSearchSheet(
            query = state.searchQuery,
            isSearching = state.isSearching,
            hits = state.searchHits,
            onQueryChange = viewModel::searchInBook,
            onSelectHit = { hit ->
                viewModel.onReflowPositionChanged(hit.chapterIndex, hit.blockIndex, recordJumpOrigin = true)
                hudVisible = false
            },
            onDismiss = { showSearchSheet = false }
        )
    }

    pendingHighlight?.let { target ->
        HighlightAndNoteDialog(
            quoteText = target.text,
            existingHighlight = target.existing,
            onSave = { color, note ->
                viewModel.saveHighlight(
                    existing = target.existing,
                    chapterIndex = target.chapterIndex,
                    chapterTitle = target.chapterTitle,
                    blockIndex = target.blockIndex,
                    quoteText = target.text,
                    noteText = note,
                    color = color
                )
                pendingHighlight = null
            },
            onDelete = target.existing?.let { ex ->
                {
                    viewModel.deleteHighlight(ex.id)
                    pendingHighlight = null
                }
            },
            onSpeakFromHere = {
                ttsBarVisible = true
                ttsController.startSpeakingFrom(target.chapterIndex, target.blockIndex)
            },
            onDismiss = { pendingHighlight = null }
        )
    }
}
