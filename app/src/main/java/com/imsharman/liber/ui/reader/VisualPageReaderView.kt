package com.imsharman.liber.ui.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.imsharman.liber.AppContainer
import com.imsharman.liber.data.parser.ComicArchiveParser
import com.imsharman.liber.data.parser.PdfBookHandler
import com.imsharman.liber.domain.model.BookFormat
import com.imsharman.liber.domain.model.ComicReadingDirection
import com.imsharman.liber.domain.model.ReaderScrollMode
import com.imsharman.liber.domain.model.ReaderSettings
import com.imsharman.liber.domain.model.VisualPageColorFilter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Hardware-backed visual page reader for PDFs and Comic/Manga archives (`.cbz` / `.cbr`).
 * Supports:
 * - Pinch-to-zoom & pan + double-tap 2.2x zoom
 * - Horizontal Pager (Western Left→Right OR Manga Right→Left!) & Continuous Vertical Scroll
 * - Color matrix filters (`Original`, `Warm Tint`, `Smart Dark Invert`, `True AMOLED Invert`)
 * - Tap zones & volume-key page turning
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VisualPageReaderView(
    uriString: String,
    format: BookFormat,
    pageCount: Int,
    currentPageIndex: Int,
    settings: ReaderSettings,
    container: AppContainer,
    volumeTurnEventTick: Pair<Long, Int>?,
    onPageChanged: (Int) -> Unit,
    onToggleHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val targetWidthPx = remember(screenWidthDp) {
        with(density) { (screenWidthDp.dp.toPx() * 1.6f).toInt().coerceIn(720, 2200) }
    }

    var pdfSession by remember { mutableStateOf<PdfBookHandler.PdfRenderSession?>(null) }
    var comicSession by remember { mutableStateOf<ComicArchiveParser.ComicRenderSession?>(null) }

    DisposableEffect(uriString, format) {
        val job = scope.launch {
            val uri = Uri.parse(uriString)
            if (format == BookFormat.PDF) {
                pdfSession = container.parserDispatcher.pdfHandler.openRenderSession(uri)
            } else {
                comicSession = container.parserDispatcher.comicHandler.openRenderSession(uri, format)
            }
        }
        onDispose {
            job.cancel()
            pdfSession?.close()
            comicSession?.close()
        }
    }

    val colorFilter = remember(settings.visualPageColorFilter) {
        buildVisualColorFilter(settings.visualPageColorFilter)
    }

    val isMangaRtl = (format == BookFormat.CBZ || format == BookFormat.CBR) &&
        settings.comicReadingDirection == ComicReadingDirection.RIGHT_TO_LEFT

    if (settings.scrollMode == ReaderScrollMode.PAGINATED_HORIZONTAL) {
        val pagerState = rememberPagerState(
            initialPage = currentPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
            pageCount = { pageCount.coerceAtLeast(1) }
        )

        LaunchedEffect(currentPageIndex) {
            val safe = currentPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            if (pagerState.currentPage != safe) {
                pagerState.scrollToPage(safe)
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { onPageChanged(it) }
        }

        LaunchedEffect(volumeTurnEventTick) {
            val (_, delta) = volumeTurnEventTick ?: return@LaunchedEffect
            val next = (pagerState.currentPage + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            pagerState.animateScrollToPage(next)
        }

        var zoomScale by remember(pagerState.currentPage) { mutableFloatStateOf(1f) }
        var panOffset by remember(pagerState.currentPage) { mutableStateOf(Offset.Zero) }

        HorizontalPager(
            state = pagerState,
            reverseLayout = isMangaRtl,
            userScrollEnabled = zoomScale <= 1.04f,
            modifier = modifier.fillMaxSize()
        ) { pageIdx ->
            val bitmap by produceState<Bitmap?>(initialValue = null, pageIdx, pdfSession, comicSession) {
                value = if (format == BookFormat.PDF) {
                    pdfSession?.renderPage(pageIdx, targetWidthPx)
                } else {
                    comicSession?.renderPage(pageIdx, targetWidthPx)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(settings.tapZonePreset, isMangaRtl) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoomScale > 1.1f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.25f
                                }
                            },
                            onTap = { tapPos ->
                                if (zoomScale > 1.05f) {
                                    onToggleHud()
                                } else {
                                    val action = resolveTapZone(tapPos, size, settings.tapZonePreset)
                                    val effectiveDelta = when (action) {
                                        TapZoneAction.PREVIOUS -> if (isMangaRtl) +1 else -1
                                        TapZoneAction.NEXT -> if (isMangaRtl) -1 else +1
                                        TapZoneAction.TOGGLE_HUD -> 0
                                    }
                                    if (effectiveDelta == 0) {
                                        onToggleHud()
                                    } else {
                                        scope.launch {
                                            val dest = (pagerState.currentPage + effectiveDelta)
                                                .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                            pagerState.animateScrollToPage(dest)
                                        }
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(pageIdx) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                            zoomScale = newScale
                            panOffset = if (newScale <= 1.02f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = (panOffset.x + pan.x).coerceIn(-size.width.toFloat(), size.width.toFloat()),
                                    y = (panOffset.y + pan.y).coerceIn(-size.height.toFloat(), size.height.toFloat())
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIdx + 1}",
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    } else {
        // Continuous vertical page stream
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = currentPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        )

        val visiblePage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
        LaunchedEffect(visiblePage) {
            onPageChanged(visiblePage)
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onToggleHud() }
                }
        ) {
            items(count = pageCount.coerceAtLeast(1), key = { it }) { pageIdx ->
                val bitmap by produceState<Bitmap?>(initialValue = null, pageIdx, pdfSession, comicSession) {
                    value = if (format == BookFormat.PDF) {
                        pdfSession?.renderPage(pageIdx, targetWidthPx)
                    } else {
                        comicSession?.renderPage(pageIdx, targetWidthPx)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Page ${pageIdx + 1}",
                            contentScale = ContentScale.FillWidth,
                            colorFilter = colorFilter,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Rendering page ${pageIdx + 1}…",
                            color = Color.Gray,
                            modifier = Modifier.padding(48.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun buildVisualColorFilter(mode: VisualPageColorFilter): ColorFilter? = when (mode) {
    VisualPageColorFilter.ORIGINAL -> null
    VisualPageColorFilter.WARM_PAPER -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.96f, 0f, 0f, 0f, 0f,
                0f, 0.91f, 0f, 0f, 0f,
                0f, 0f, 0.82f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    VisualPageColorFilter.NIGHT_INVERT -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -0.86f, 0f, 0f, 0f, 232f,
                0f, -0.86f, 0f, 0f, 234f,
                0f, 0f, -0.86f, 0f, 240f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    VisualPageColorFilter.AMOLED_CONTRAST -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}
