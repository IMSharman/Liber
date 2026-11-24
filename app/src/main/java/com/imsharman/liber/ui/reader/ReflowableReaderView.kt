package com.imsharman.liber.ui.reader

import android.graphics.Typeface
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.imsharman.liber.data.local.entity.HighlightEntity
import com.imsharman.liber.data.parser.BookChapter
import com.imsharman.liber.data.parser.ContentBlock
import com.imsharman.liber.data.parser.InlineStyle
import com.imsharman.liber.domain.model.PageTurnAnimation
import com.imsharman.liber.domain.model.ReaderFontFamily
import com.imsharman.liber.domain.model.ReaderScrollMode
import com.imsharman.liber.domain.model.ReaderSettings
import com.imsharman.liber.domain.model.TapZonePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

sealed interface PageSliceItem {
    val blockIndex: Int
    val sourceBlock: ContentBlock

    data class TextSlice(
        override val blockIndex: Int,
        override val sourceBlock: ContentBlock,
        val text: AnnotatedString,
        val style: TextStyle
    ) : PageSliceItem

    data class QuoteSlice(
        override val blockIndex: Int,
        override val sourceBlock: ContentBlock.Quote,
        val text: AnnotatedString,
        val style: TextStyle
    ) : PageSliceItem

    data class CodeSlice(
        override val blockIndex: Int,
        override val sourceBlock: ContentBlock.CodeBlock,
        val text: AnnotatedString,
        val style: TextStyle,
        val innerPaddingDp: Int
    ) : PageSliceItem

    data class ImageSlice(
        override val blockIndex: Int,
        override val sourceBlock: ContentBlock.EmbeddedImage,
        val maxHeightDp: Int
    ) : PageSliceItem

    data class DividerSlice(
        override val blockIndex: Int,
        override val sourceBlock: ContentBlock.Divider
    ) : PageSliceItem
}

data class MeasuredVirtualPage(
    val chapterIndex: Int,
    val chapterTitle: String,
    val startBlockIndex: Int,
    val endBlockIndex: Int,
    val items: List<PageSliceItem>
)

enum class TapZoneAction {
    PREVIOUS,
    TOGGLE_HUD,
    NEXT
}

fun resolveTapZone(
    offset: Offset,
    size: IntSize,
    preset: TapZonePreset
): TapZoneAction {
    if (size.width <= 0 || size.height <= 0) return TapZoneAction.TOGGLE_HUD
    val nx = offset.x / size.width.toFloat()
    val ny = offset.y / size.height.toFloat()

    return when (preset) {
        TapZonePreset.CLASSIC_SIDES -> when {
            nx < 0.30f -> TapZoneAction.PREVIOUS
            nx > 0.70f -> TapZoneAction.NEXT
            else -> TapZoneAction.TOGGLE_HUD
        }
        TapZonePreset.VERTICAL_HALVES -> when {
            ny < 0.30f -> TapZoneAction.PREVIOUS
            ny > 0.70f -> TapZoneAction.NEXT
            else -> TapZoneAction.TOGGLE_HUD
        }
        TapZonePreset.ONE_HAND_RIGHT -> when {
            nx < 0.22f && ny < 0.78f -> TapZoneAction.PREVIOUS
            nx > 0.58f || ny > 0.78f -> TapZoneAction.NEXT
            else -> TapZoneAction.TOGGLE_HUD
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReflowableReaderView(
    chapters: List<BookChapter>,
    currentChapterIndex: Int,
    currentBlockIndex: Int,
    settings: ReaderSettings,
    highlights: List<HighlightEntity>,
    ttsChapterIndex: Int?,
    ttsBlockIndex: Int?,
    volumeTurnEventTick: Pair<Long, Int>?,
    onPositionChanged: (chapterIdx: Int, blockIdx: Int, subFraction: Float) -> Unit,
    onMeasuredPageStatsChanged: ((currentPageOneBased: Int, totalPages: Int) -> Unit)? = null,
    onToggleHud: () -> Unit,
    onBlockLongPressed: (chapterIdx: Int, chapterTitle: String, blockIdx: Int, blockText: String, existingHighlight: HighlightEntity?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)

    val resolvedFont = remember(settings.fontFamily, settings.customFontPath) {
        resolveReaderFontFamily(settings)
    }

    val textColor = Color(settings.activeTextArgb)
    val accentColor = Color(settings.activeAccentArgb)
    val isDark = settings.isDarkReader

    val highlightsBySpot = remember(highlights) {
        highlights.associateBy { "${it.chapterIndex}:${it.blockIndex}" }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportPxSize = IntSize(constraints.maxWidth, constraints.maxHeight)

        if (settings.scrollMode == ReaderScrollMode.PAGINATED_HORIZONTAL) {
            val hMarginPx = with(density) { settings.horizontalMarginDp.dp.roundToPx() }
            val vMarginPx = with(density) { settings.verticalMarginDp.dp.roundToPx() }
            val paraGapPx = with(density) { settings.paragraphSpacingDp.dp.roundToPx() }.coerceAtLeast(2)
            val quoteIndentPx = with(density) { 15.dp.roundToPx() }
            val codePadDp = 10
            val codePadPx = with(density) { (codePadDp * 2).dp.roundToPx() }
            val dividerHeightPx = with(density) { 21.dp.roundToPx() }

            val contentWidthPx = (constraints.maxWidth - hMarginPx * 2).coerceAtLeast(100)
            // Small 4px descender safety cushion so bottom line descenders ('g', 'y', 'p') never clip
            val contentHeightPx = (constraints.maxHeight - vMarginPx * 2 - 4).coerceAtLeast(120)
            val maxImageHeightDp = with(density) { (contentHeightPx * 0.62f).toDp().value.toInt().coerceIn(120, 380) }
            val maxImageHeightPx = with(density) { maxImageHeightDp.dp.roundToPx() }

            val layoutSignature = remember(
                chapters,
                contentWidthPx,
                contentHeightPx,
                paraGapPx,
                settings.fontFamily,
                settings.customFontPath,
                settings.fontSizeSp,
                settings.fontWeight,
                settings.lineHeightMultiplier,
                settings.justifyText,
                settings.hyphenationEnabled,
                settings.firstLineIndentEnabled,
                settings.bionicReadingEnabled,
                settings.activeTextArgb,
                settings.activeAccentArgb
            ) {
                listOf(
                    chapters.hashCode(),
                    contentWidthPx,
                    contentHeightPx,
                    paraGapPx,
                    settings.fontFamily,
                    settings.customFontPath,
                    settings.fontSizeSp,
                    settings.fontWeight,
                    settings.lineHeightMultiplier,
                    settings.justifyText,
                    settings.hyphenationEnabled,
                    settings.firstLineIndentEnabled,
                    settings.bionicReadingEnabled,
                    settings.activeTextArgb,
                    settings.activeAccentArgb
                )
            }

            var measuredPages by remember { mutableStateOf<List<MeasuredVirtualPage>>(emptyList()) }
            var isMeasuring by remember { mutableStateOf(true) }

            LaunchedEffect(layoutSignature) {
                if (chapters.isEmpty() || contentWidthPx <= 100 || contentHeightPx <= 120) {
                    measuredPages = emptyList()
                    isMeasuring = false
                    return@LaunchedEffect
                }
                isMeasuring = measuredPages.isEmpty()
                val computed = withContext(Dispatchers.Default) {
                    paginateChaptersWithMeasurer(
                        chapters = chapters,
                        textMeasurer = textMeasurer,
                        fontFamily = resolvedFont,
                        textColor = textColor,
                        accentColor = accentColor,
                        settings = settings,
                        contentWidthPx = contentWidthPx,
                        contentHeightPx = contentHeightPx,
                        paraGapPx = paraGapPx,
                        quoteIndentPx = quoteIndentPx,
                        codePadDp = codePadDp,
                        codePadPx = codePadPx,
                        dividerHeightPx = dividerHeightPx,
                        maxImageHeightDp = maxImageHeightDp,
                        maxImageHeightPx = maxImageHeightPx
                    )
                }
                measuredPages = computed
                isMeasuring = false
            }

            if (isMeasuring || measuredPages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isMeasuring) CircularProgressIndicator()
                }
                return@BoxWithConstraints
            }

            val initialPageIdx = remember(measuredPages) {
                findMatchingMeasuredPageIndex(measuredPages, currentChapterIndex, currentBlockIndex)
            }

            val pagerState = rememberPagerState(
                initialPage = initialPageIdx,
                pageCount = { measuredPages.size.coerceAtLeast(1) }
            )

            // Sync external jumps (TOC, Search, Scrubber, TTS, Jump-back) to pager
            LaunchedEffect(currentChapterIndex, currentBlockIndex, measuredPages) {
                val curPage = measuredPages.getOrNull(pagerState.currentPage)
                val alreadyOnSpot = curPage != null &&
                    curPage.chapterIndex == currentChapterIndex &&
                    currentBlockIndex in curPage.startBlockIndex..curPage.endBlockIndex

                if (!alreadyOnSpot && measuredPages.isNotEmpty()) {
                    val idx = findMatchingMeasuredPageIndex(measuredPages, currentChapterIndex, currentBlockIndex)
                    pagerState.scrollToPage(idx)
                }
            }

            // Observe user page turns and report exact book page stats
            LaunchedEffect(pagerState, measuredPages) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { pIdx ->
                        val vp = measuredPages.getOrNull(pIdx) ?: return@collect
                        onMeasuredPageStatsChanged?.invoke(pIdx + 1, measuredPages.size)
                        val exactBookFrac = (pIdx + 1).toFloat() / measuredPages.size.coerceAtLeast(1).toFloat()
                        onPositionChanged(vp.chapterIndex, vp.startBlockIndex, exactBookFrac)
                    }
            }

            // Volume-key page turn support
            LaunchedEffect(volumeTurnEventTick) {
                val (_, delta) = volumeTurnEventTick ?: return@LaunchedEffect
                val next = (pagerState.currentPage + delta).coerceIn(0, (measuredPages.size - 1).coerceAtLeast(0))
                if (next != pagerState.currentPage) {
                    if (settings.pageTurnAnimation == PageTurnAnimation.INSTANT) {
                        pagerState.scrollToPage(next)
                    } else {
                        pagerState.animateScrollToPage(next, animationSpec = tween(220))
                    }
                }
            }

            fun turnByDelta(delta: Int) {
                scope.launch {
                    val dest = (pagerState.currentPage + delta).coerceIn(0, (measuredPages.size - 1).coerceAtLeast(0))
                    if (settings.pageTurnAnimation == PageTurnAnimation.INSTANT) {
                        pagerState.scrollToPage(dest)
                    } else {
                        pagerState.animateScrollToPage(dest, animationSpec = tween(220))
                    }
                }
            }

            fun handleTapAtOffset(windowOffset: Offset) {
                when (resolveTapZone(windowOffset, viewportPxSize, settings.tapZonePreset)) {
                    TapZoneAction.PREVIOUS -> turnByDelta(-1)
                    TapZoneAction.NEXT -> turnByDelta(+1)
                    TapZoneAction.TOGGLE_HUD -> onToggleHud()
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(settings.tapZonePreset, viewportPxSize) {
                        detectTapGestures { offset ->
                            handleTapAtOffset(offset)
                        }
                    }
            ) { pageIdx ->
                val vPage = measuredPages.getOrNull(pageIdx)
                val pageOffset = ((pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction).absoluteValue

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (settings.pageTurnAnimation == PageTurnAnimation.FADE) {
                                alpha = (1f - pageOffset * 0.85f).coerceIn(0.15f, 1f)
                            }
                        }
                        .padding(
                            horizontal = settings.horizontalMarginDp.dp,
                            vertical = settings.verticalMarginDp.dp
                        )
                ) {
                    if (vPage != null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp)
                        ) {
                            vPage.items.forEach { item ->
                                val hl = highlightsBySpot["${vPage.chapterIndex}:${item.blockIndex}"]
                                val isTtsSpeaking = ttsChapterIndex == vPage.chapterIndex && ttsBlockIndex == item.blockIndex
                                RenderMeasuredSlice(
                                    item = item,
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    isDark = isDark,
                                    highlight = hl,
                                    isTtsActive = isTtsSpeaking,
                                    onTapWindowOffset = { winPos -> handleTapAtOffset(winPos) },
                                    onLongPress = {
                                        val fullBlockText = item.sourceBlock.plainText().trim()
                                        if (fullBlockText.isNotEmpty()) {
                                            onBlockLongPressed(
                                                vPage.chapterIndex,
                                                vPage.chapterTitle,
                                                item.blockIndex,
                                                fullBlockText,
                                                hl
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // CONTINUOUS VERTICAL SCROLL MODE
            LaunchedEffect(Unit) {
                onMeasuredPageStatsChanged?.invoke(0, 0)
            }

            val activeChap = chapters.getOrNull(currentChapterIndex) ?: chapters.firstOrNull()
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = currentBlockIndex.coerceAtLeast(0)
            )

            LaunchedEffect(currentChapterIndex) {
                val safeIdx = currentBlockIndex.coerceIn(0, (activeChap?.blocks?.size ?: 1) - 1)
                listState.scrollToItem(safeIdx.coerceAtLeast(0))
            }

            val visibleIdx by remember { derivedStateOf { listState.firstVisibleItemIndex } }
            LaunchedEffect(visibleIdx, currentChapterIndex) {
                if (activeChap != null) {
                    onPositionChanged(currentChapterIndex, visibleIdx, 0f)
                }
            }

            LaunchedEffect(volumeTurnEventTick) {
                val (_, delta) = volumeTurnEventTick ?: return@LaunchedEffect
                val nextItem = (listState.firstVisibleItemIndex + delta * 4).coerceAtLeast(0)
                listState.animateScrollToItem(nextItem)
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = settings.horizontalMarginDp.dp,
                    end = settings.horizontalMarginDp.dp,
                    top = settings.verticalMarginDp.dp,
                    bottom = (settings.verticalMarginDp + 48).dp
                ),
                verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { onToggleHud() }
                    }
            ) {
                if (activeChap != null) {
                    itemsIndexed(
                        items = activeChap.blocks,
                        key = { idx, _ -> "${activeChap.index}_$idx" }
                    ) { blockIdx, block ->
                        val hl = highlightsBySpot["${activeChap.index}:$blockIdx"]
                        val isTtsSpeaking = ttsChapterIndex == activeChap.index && ttsBlockIndex == blockIdx
                        RenderScrollContentBlock(
                            block = block,
                            settings = settings,
                            fontFamily = resolvedFont,
                            textColor = textColor,
                            accentColor = accentColor,
                            isDark = isDark,
                            highlight = hl,
                            isTtsActive = isTtsSpeaking,
                            onTapWindowOffset = { onToggleHud() },
                            onLongPress = {
                                val txt = block.plainText().trim()
                                if (txt.isNotEmpty()) {
                                    onBlockLongPressed(
                                        activeChap.index,
                                        activeChap.title,
                                        blockIdx,
                                        txt,
                                        hl
                                    )
                                }
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (currentChapterIndex > 0) {
                                FilledTonalButton(
                                    onClick = { onPositionChanged(currentChapterIndex - 1, 0, 0f) }
                                ) {
                                    Text("← Previous Chapter")
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            if (currentChapterIndex + 1 < chapters.size) {
                                FilledTonalButton(
                                    onClick = { onPositionChanged(currentChapterIndex + 1, 0, 0f) }
                                ) {
                                    Text("Next Chapter →")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a pre-measured slice inside a paginated page with zero extra layout padding
 * so the rendered height matches `TextMeasurer` 1:1 down to the pixel.
 */
@Composable
private fun RenderMeasuredSlice(
    item: PageSliceItem,
    accentColor: Color,
    textColor: Color,
    isDark: Boolean,
    highlight: HighlightEntity?,
    isTtsActive: Boolean,
    onTapWindowOffset: (Offset) -> Unit,
    onLongPress: () -> Unit
) {
    var sliceWindowPos by remember { mutableStateOf(Offset.Zero) }

    val highlightBg = highlight?.let {
        Color(if (isDark) it.color.bgHexDark else it.color.bgHexLight)
    }

    val ttsDrawMod = if (isTtsActive) {
        Modifier.drawBehind {
            drawRoundRect(
                color = accentColor,
                cornerRadius = CornerRadius(12f, 12f),
                style = Stroke(width = 3f)
            )
        }
    } else {
        Modifier
    }

    val interactionMod = Modifier
        .onGloballyPositioned { coords ->
            sliceWindowPos = coords.positionInWindow()
        }
        .pointerInput(onTapWindowOffset, onLongPress) {
            detectTapGestures(
                onTap = { localPos -> onTapWindowOffset(sliceWindowPos + localPos) },
                onLongPress = { onLongPress() }
            )
        }

    when (item) {
        is PageSliceItem.TextSlice -> {
            val displayAnnotated = remember(item.text, highlightBg) {
                applyHighlightBackground(item.text, highlightBg)
            }
            Text(
                text = displayAnnotated,
                style = item.style,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(ttsDrawMod)
                    .then(interactionMod)
            )
        }

        is PageSliceItem.QuoteSlice -> {
            val displayAnnotated = remember(item.text, highlightBg) {
                applyHighlightBackground(item.text, highlightBg)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(ttsDrawMod)
                    .then(interactionMod)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .heightIn(min = 20.dp)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = displayAnnotated,
                    style = item.style,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is PageSliceItem.CodeSlice -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(textColor.copy(alpha = 0.08f))
                    .padding(item.innerPaddingDp.dp)
                    .then(interactionMod)
            ) {
                Text(
                    text = item.text,
                    style = item.style,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is PageSliceItem.ImageSlice -> {
            val f = remember(item.sourceBlock.localCachePath) {
                File(item.sourceBlock.localCachePath).takeIf { it.exists() }
            }
            if (f != null) {
                AsyncImage(
                    model = f,
                    contentDescription = item.sourceBlock.altText,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = item.maxHeightDp.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(interactionMod)
                )
            }
        }

        is PageSliceItem.DividerSlice -> {
            HorizontalDivider(
                color = textColor.copy(alpha = 0.20f),
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 42.dp)
            )
        }
    }
}

/**
 * True pixel-accurate book typesetting paginator using Compose `TextMeasurer`:
 * - Measures exact glyph wrap & line heights for every block against `contentWidthPx`.
 * - Splits overflowing paragraphs cleanly line-by-line across page turns so every page fills
 *   the screen down to the bottom margin (only ending shorter on the final page of a chapter).
 * - Keeps continuation fragments flush-left (`TextIndent.None`) just like a printed novel.
 * - Prevents headings from being stranded alone at the very bottom of a page.
 */
private fun paginateChaptersWithMeasurer(
    chapters: List<BookChapter>,
    textMeasurer: TextMeasurer,
    fontFamily: FontFamily,
    textColor: Color,
    accentColor: Color,
    settings: ReaderSettings,
    contentWidthPx: Int,
    contentHeightPx: Int,
    paraGapPx: Int,
    quoteIndentPx: Int,
    codePadDp: Int,
    codePadPx: Int,
    dividerHeightPx: Int,
    maxImageHeightDp: Int,
    maxImageHeightPx: Int
): List<MeasuredVirtualPage> {
    val baseLineHeightSp = (settings.fontSizeSp * settings.lineHeightMultiplier).sp
    val letterSpacing = if (settings.fontFamily == ReaderFontFamily.DYSLEXIC_WIDE) 0.05.sp else 0.sp
    val baseWeight = FontWeight(settings.fontWeight.coerceIn(300, 800))

    val paraFirstStyle = TextStyle(
        color = textColor,
        fontFamily = fontFamily,
        fontWeight = baseWeight,
        fontSize = settings.fontSizeSp.sp,
        lineHeight = baseLineHeightSp,
        letterSpacing = letterSpacing,
        textAlign = if (settings.justifyText) TextAlign.Justify else TextAlign.Start,
        hyphens = if (settings.hyphenationEnabled) Hyphens.Auto else Hyphens.None,
        textIndent = if (settings.firstLineIndentEnabled) {
            TextIndent(firstLine = (settings.fontSizeSp * 1.4f).sp)
        } else TextIndent.None
    )
    val paraContinuationStyle = paraFirstStyle.copy(textIndent = TextIndent.None)

    val quoteStyle = TextStyle(
        color = textColor.copy(alpha = 0.90f),
        fontFamily = fontFamily,
        fontStyle = FontStyle.Italic,
        fontSize = (settings.fontSizeSp * 0.96f).sp,
        lineHeight = baseLineHeightSp,
        letterSpacing = letterSpacing
    )

    val codeStyle = TextStyle(
        color = textColor,
        fontFamily = FontFamily.Monospace,
        fontSize = (settings.fontSizeSp * 0.84f).sp,
        lineHeight = (settings.fontSizeSp * 1.28f).sp
    )

    val approxSingleLinePx = textMeasurer.measure(
        text = AnnotatedString("Ag"),
        style = paraContinuationStyle,
        constraints = Constraints(maxWidth = contentWidthPx)
    ).size.height.coerceAtLeast(16)

    val pages = ArrayList<MeasuredVirtualPage>(chapters.size * 4)

    for (chap in chapters) {
        if (chap.blocks.isEmpty()) continue

        var currentItems = ArrayList<PageSliceItem>(12)
        var remainingPx = contentHeightPx

        fun flushPage() {
            if (currentItems.isNotEmpty()) {
                pages += MeasuredVirtualPage(
                    chapterIndex = chap.index,
                    chapterTitle = chap.title,
                    startBlockIndex = currentItems.first().blockIndex,
                    endBlockIndex = currentItems.last().blockIndex,
                    items = currentItems
                )
                currentItems = ArrayList(12)
                remainingPx = contentHeightPx
            }
        }

        fun packSplittableText(
            blockIndex: Int,
            sourceBlock: ContentBlock,
            annotated: AnnotatedString,
            firstStyle: TextStyle,
            contStyle: TextStyle,
            maxWidthPx: Int,
            extraVerticalChromePx: Int = 0,
            sliceFactory: (AnnotatedString, TextStyle) -> PageSliceItem
        ) {
            var cursor = 0
            while (cursor < annotated.length) {
                if (cursor > 0) {
                    while (cursor < annotated.length && annotated[cursor].isWhitespace()) {
                        cursor++
                    }
                    if (cursor >= annotated.length) break
                }

                val sub = annotated.subSequence(cursor, annotated.length)
                val style = if (cursor == 0) firstStyle else contStyle
                val gap = if (currentItems.isEmpty()) 0 else paraGapPx
                val availTextPx = remainingPx - gap - extraVerticalChromePx

                if (availTextPx < approxSingleLinePx && currentItems.isNotEmpty()) {
                    flushPage()
                    continue
                }

                val res = textMeasurer.measure(
                    text = sub,
                    style = style,
                    constraints = Constraints(maxWidth = maxWidthPx)
                )

                if (res.size.height <= availTextPx) {
                    currentItems += sliceFactory(sub, style)
                    remainingPx -= (gap + res.size.height + extraVerticalChromePx)
                    break
                }

                // Find the last line whose bottom fits inside availTextPx
                var fitLine = -1
                for (line in 0 until res.lineCount) {
                    if (res.getLineBottom(line) <= availTextPx) {
                        fitLine = line
                    } else {
                        break
                    }
                }

                if (fitLine < 0) {
                    if (currentItems.isEmpty()) {
                        // Safety valve for giant font sizes: always advance by at least 1 line
                        fitLine = 0
                    } else {
                        flushPage()
                        continue
                    }
                }

                val cutOffset = res.getLineEnd(fitLine, visibleEnd = false).coerceIn(1, sub.length)
                if (cutOffset >= sub.length) {
                    currentItems += sliceFactory(sub, style)
                    remainingPx -= (gap + res.size.height + extraVerticalChromePx)
                    break
                }

                val fittedPart = trimTrailingWhitespaceAnnotated(sub.subSequence(0, cutOffset))
                if (fittedPart.isNotEmpty()) {
                    currentItems += sliceFactory(fittedPart, style)
                }
                flushPage()
                cursor += cutOffset
            }
        }

        for ((bIdx, block) in chap.blocks.withIndex()) {
            when (block) {
                is ContentBlock.Heading -> {
                    val scale = when (block.level) {
                        1 -> 1.40f
                        2 -> 1.24f
                        3 -> 1.12f
                        else -> 1.05f
                    }
                    val hStyle = TextStyle(
                        color = textColor,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (settings.fontSizeSp * scale).sp,
                        lineHeight = (settings.fontSizeSp * scale * 1.26f).sp,
                        letterSpacing = letterSpacing
                    )
                    val hAnnotated = AnnotatedString(block.text)
                    val hRes = textMeasurer.measure(
                        text = hAnnotated,
                        style = hStyle,
                        constraints = Constraints(maxWidth = contentWidthPx)
                    )
                    val gap = if (currentItems.isEmpty()) 0 else paraGapPx
                    val hasNextBlock = bIdx + 1 < chap.blocks.size
                    val keepWithNextPx = if (hasNextBlock) approxSingleLinePx + paraGapPx else 0

                    if (currentItems.isNotEmpty() && gap + hRes.size.height + keepWithNextPx > remainingPx) {
                        flushPage()
                    }
                    val gapAfterFlush = if (currentItems.isEmpty()) 0 else paraGapPx
                    currentItems += PageSliceItem.TextSlice(bIdx, block, hAnnotated, hStyle)
                    remainingPx -= (gapAfterFlush + hRes.size.height)
                }

                is ContentBlock.Paragraph -> {
                    val annotated = buildParagraphAnnotatedString(
                        paragraph = block,
                        bionicEnabled = settings.bionicReadingEnabled,
                        baseWeight = settings.fontWeight,
                        codeTint = accentColor
                    )
                    if (annotated.isNotEmpty()) {
                        packSplittableText(
                            blockIndex = bIdx,
                            sourceBlock = block,
                            annotated = annotated,
                            firstStyle = paraFirstStyle,
                            contStyle = paraContinuationStyle,
                            maxWidthPx = contentWidthPx,
                            sliceFactory = { sliceTxt, st ->
                                PageSliceItem.TextSlice(bIdx, block, sliceTxt, st)
                            }
                        )
                    }
                }

                is ContentBlock.Quote -> {
                    val fullQuote = if (block.attribution.isNullOrBlank()) {
                        block.text
                    } else {
                        "${block.text}\n— ${block.attribution}"
                    }
                    val quoteWidth = (contentWidthPx - quoteIndentPx).coerceAtLeast(80)
                    packSplittableText(
                        blockIndex = bIdx,
                        sourceBlock = block,
                        annotated = AnnotatedString(fullQuote),
                        firstStyle = quoteStyle,
                        contStyle = quoteStyle,
                        maxWidthPx = quoteWidth,
                        sliceFactory = { sliceTxt, st ->
                            PageSliceItem.QuoteSlice(bIdx, block, sliceTxt, st)
                        }
                    )
                }

                is ContentBlock.ListItems -> {
                    block.items.forEachIndexed { itemIdx, rawItem ->
                        val prefix = if (block.ordered) "${itemIdx + 1}.  " else "•  "
                        val annotatedItem = buildAnnotatedString {
                            pushStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold))
                            append(prefix)
                            pop()
                            append(rawItem)
                        }
                        packSplittableText(
                            blockIndex = bIdx,
                            sourceBlock = block,
                            annotated = annotatedItem,
                            firstStyle = paraContinuationStyle,
                            contStyle = paraContinuationStyle,
                            maxWidthPx = contentWidthPx,
                            sliceFactory = { sliceTxt, st ->
                                PageSliceItem.TextSlice(bIdx, block, sliceTxt, st)
                            }
                        )
                    }
                }

                is ContentBlock.CodeBlock -> {
                    val innerWidth = (contentWidthPx - codePadPx).coerceAtLeast(80)
                    packSplittableText(
                        blockIndex = bIdx,
                        sourceBlock = block,
                        annotated = AnnotatedString(block.code),
                        firstStyle = codeStyle,
                        contStyle = codeStyle,
                        maxWidthPx = innerWidth,
                        extraVerticalChromePx = codePadPx,
                        sliceFactory = { sliceTxt, st ->
                            PageSliceItem.CodeSlice(bIdx, block, sliceTxt, st, codePadDp)
                        }
                    )
                }

                is ContentBlock.EmbeddedImage -> {
                    val gap = if (currentItems.isEmpty()) 0 else paraGapPx
                    if (currentItems.isNotEmpty() && gap + maxImageHeightPx > remainingPx) {
                        flushPage()
                    }
                    val gapAfterFlush = if (currentItems.isEmpty()) 0 else paraGapPx
                    currentItems += PageSliceItem.ImageSlice(bIdx, block, maxImageHeightDp)
                    remainingPx -= (gapAfterFlush + maxImageHeightPx)
                }

                ContentBlock.Divider -> {
                    val gap = if (currentItems.isEmpty()) 0 else paraGapPx
                    if (gap + dividerHeightPx <= remainingPx) {
                        if (currentItems.isNotEmpty()) {
                            currentItems += PageSliceItem.DividerSlice(bIdx, ContentBlock.Divider)
                            remainingPx -= (gap + dividerHeightPx)
                        }
                    } else {
                        flushPage()
                    }
                }
            }
        }

        // End of chapter: flush the final page (only here can a page legitimately be shorter than full screen!)
        flushPage()
    }

    return pages
}

@Composable
private fun RenderScrollContentBlock(
    block: ContentBlock,
    settings: ReaderSettings,
    fontFamily: FontFamily,
    textColor: Color,
    accentColor: Color,
    isDark: Boolean,
    highlight: HighlightEntity?,
    isTtsActive: Boolean,
    onTapWindowOffset: (Offset) -> Unit,
    onLongPress: () -> Unit
) {
    var blockWindowPos by remember { mutableStateOf(Offset.Zero) }

    val bgHighlight = highlight?.let {
        Color(if (isDark) it.color.bgHexDark else it.color.bgHexLight)
    } ?: Color.Transparent

    val ttsBorderMod = if (isTtsActive) {
        Modifier
            .border(1.5.dp, accentColor, RoundedCornerShape(6.dp))
            .padding(6.dp)
    } else if (highlight != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgHighlight)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    } else {
        Modifier
    }

    val clickMod = Modifier
        .onGloballyPositioned { coords ->
            blockWindowPos = coords.positionInWindow()
        }
        .pointerInput(onTapWindowOffset, onLongPress) {
            detectTapGestures(
                onTap = { localPos -> onTapWindowOffset(blockWindowPos + localPos) },
                onLongPress = { onLongPress() }
            )
        }

    val baseLineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp
    val letterSpacing = if (settings.fontFamily == ReaderFontFamily.DYSLEXIC_WIDE) 0.05.sp else 0.sp

    when (block) {
        is ContentBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.40f
                2 -> 1.24f
                3 -> 1.12f
                else -> 1.05f
            }
            Text(
                text = block.text,
                color = textColor,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (settings.fontSizeSp * scale).sp,
                lineHeight = (settings.fontSizeSp * scale * 1.26f).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(ttsBorderMod)
                    .then(clickMod)
                    .padding(top = 6.dp, bottom = 2.dp)
            )
        }

        is ContentBlock.Paragraph -> {
            val annotated = remember(block, settings.bionicReadingEnabled, settings.fontWeight, accentColor) {
                buildParagraphAnnotatedString(
                    paragraph = block,
                    bionicEnabled = settings.bionicReadingEnabled,
                    baseWeight = settings.fontWeight,
                    codeTint = accentColor
                )
            }
            Text(
                text = annotated,
                style = TextStyle(
                    color = textColor,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight(settings.fontWeight.coerceIn(300, 800)),
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = baseLineHeight,
                    letterSpacing = letterSpacing,
                    textAlign = if (settings.justifyText) TextAlign.Justify else TextAlign.Start,
                    hyphens = if (settings.hyphenationEnabled) Hyphens.Auto else Hyphens.None,
                    textIndent = if (settings.firstLineIndentEnabled) {
                        TextIndent(firstLine = (settings.fontSizeSp * 1.4f).sp)
                    } else TextIndent.None
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(ttsBorderMod)
                    .then(clickMod)
            )
            if (highlight != null && highlight.noteText.isNotBlank()) {
                Text(
                    text = "📝 ${highlight.noteText}",
                    color = accentColor,
                    fontSize = (settings.fontSizeSp * 0.80f).sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
            }
        }

        is ContentBlock.Quote -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(ttsBorderMod)
                    .then(clickMod)
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .heightIn(min = 28.dp)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = block.text,
                        color = textColor.copy(alpha = 0.90f),
                        fontFamily = fontFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = (settings.fontSizeSp * 0.96f).sp,
                        lineHeight = baseLineHeight
                    )
                    if (!block.attribution.isNullOrBlank()) {
                        Text(
                            text = "— ${block.attribution}",
                            color = accentColor,
                            fontSize = (settings.fontSizeSp * 0.82f).sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        is ContentBlock.CodeBlock -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(textColor.copy(alpha = 0.08f))
                    .padding(12.dp)
                    .then(clickMod)
            ) {
                Text(
                    text = block.code,
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (settings.fontSizeSp * 0.84f).sp,
                    lineHeight = (settings.fontSizeSp * 1.28f).sp
                )
            }
        }

        is ContentBlock.ListItems -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(ttsBorderMod)
                    .then(clickMod)
            ) {
                block.items.forEachIndexed { idx, item ->
                    val bullet = if (block.ordered) "${idx + 1}." else "•"
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = bullet,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = settings.fontSizeSp.sp,
                            modifier = Modifier.width(26.dp)
                        )
                        Text(
                            text = item,
                            color = textColor,
                            fontFamily = fontFamily,
                            fontSize = settings.fontSizeSp.sp,
                            lineHeight = baseLineHeight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        is ContentBlock.EmbeddedImage -> {
            val f = remember(block.localCachePath) { File(block.localCachePath).takeIf { it.exists() } }
            if (f != null) {
                AsyncImage(
                    model = f,
                    contentDescription = block.altText,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }

        ContentBlock.Divider -> {
            HorizontalDivider(
                color = textColor.copy(alpha = 0.20f),
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 42.dp)
            )
        }
    }
}

private fun applyHighlightBackground(
    source: AnnotatedString,
    highlightBg: Color?
): AnnotatedString {
    if (highlightBg == null || source.isEmpty()) return source
    return buildAnnotatedString {
        append(source)
        addStyle(SpanStyle(background = highlightBg), 0, source.length)
    }
}

private fun trimTrailingWhitespaceAnnotated(ann: AnnotatedString): AnnotatedString {
    var end = ann.length
    while (end > 0 && ann[end - 1].isWhitespace()) {
        end--
    }
    return if (end == ann.length) ann else ann.subSequence(0, end)
}

private fun buildParagraphAnnotatedString(
    paragraph: ContentBlock.Paragraph,
    bionicEnabled: Boolean,
    baseWeight: Int,
    codeTint: Color
): AnnotatedString = buildAnnotatedString {
    val text = paragraph.text
    append(text)

    for (sp in paragraph.spans) {
        val s = sp.start.coerceIn(0, text.length)
        val e = sp.end.coerceIn(0, text.length)
        if (s >= e) continue
        val style = when (sp.style) {
            InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            InlineStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, color = codeTint)
            InlineStyle.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        }
        addStyle(style, s, e)
    }

    if (bionicEnabled) {
        val boldWeight = FontWeight((baseWeight + 250).coerceAtMost(900))
        var idx = 0
        while (idx < text.length) {
            if (text[idx].isLetterOrDigit()) {
                val wordStart = idx
                while (idx < text.length && text[idx].isLetterOrDigit()) idx++
                val wordLen = idx - wordStart
                val boldChars = ((wordLen + 1) / 2).coerceIn(1, 5)
                addStyle(SpanStyle(fontWeight = boldWeight), wordStart, (wordStart + boldChars).coerceAtMost(idx))
            } else {
                idx++
            }
        }
    }
}

private fun resolveReaderFontFamily(settings: ReaderSettings): FontFamily {
    if (settings.fontFamily == ReaderFontFamily.CUSTOM_FILE && !settings.customFontPath.isNullOrBlank()) {
        val file = File(settings.customFontPath)
        if (file.exists()) {
            val tf = runCatching { Typeface.createFromFile(file) }.getOrNull()
            if (tf != null) return FontFamily(tf)
        }
    }
    return when (settings.fontFamily) {
        ReaderFontFamily.SERIF -> FontFamily.Serif
        ReaderFontFamily.SANS_SERIF -> FontFamily.SansSerif
        ReaderFontFamily.MONOSPACE -> FontFamily.Monospace
        ReaderFontFamily.CURSIVE -> FontFamily.Cursive
        ReaderFontFamily.DYSLEXIC_WIDE -> FontFamily.SansSerif
        ReaderFontFamily.CUSTOM_FILE -> FontFamily.Serif
    }
}

private fun findMatchingMeasuredPageIndex(
    pages: List<MeasuredVirtualPage>,
    chapterIdx: Int,
    blockIdx: Int
): Int {
    if (pages.isEmpty()) return 0
    val exact = pages.indexOfFirst { p ->
        p.chapterIndex == chapterIdx && blockIdx in p.startBlockIndex..p.endBlockIndex
    }
    if (exact >= 0) return exact
    val chapFirst = pages.indexOfFirst { it.chapterIndex == chapterIdx }
    return if (chapFirst >= 0) chapFirst else 0
}
