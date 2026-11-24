package com.imsharman.liber.ui.reader

import android.graphics.Typeface
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue

data class VirtualReflowPage(
    val chapterIndex: Int,
    val chapterTitle: String,
    val startBlockIndex: Int,
    val blocks: List<IndexedValue<ContentBlock>>
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
    onToggleHud: () -> Unit,
    onBlockLongPressed: (chapterIdx: Int, chapterTitle: String, blockIdx: Int, blockText: String, existingHighlight: HighlightEntity?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
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
        val viewportWidthDp = maxWidth.value
        val viewportHeightDp = maxHeight.value

        if (settings.scrollMode == ReaderScrollMode.PAGINATED_HORIZONTAL) {
            val pages = remember(
                chapters,
                viewportWidthDp,
                viewportHeightDp,
                settings.fontSizeSp,
                settings.lineHeightMultiplier,
                settings.horizontalMarginDp,
                settings.verticalMarginDp,
                settings.paragraphSpacingDp
            ) {
                paginateReflowChapters(
                    chapters = chapters,
                    viewportWidthDp = viewportWidthDp,
                    viewportHeightDp = viewportHeightDp,
                    settings = settings
                )
            }

            val targetInitialPage = remember(pages) {
                findMatchingVirtualPageIndex(pages, currentChapterIndex, currentBlockIndex)
            }

            val pagerState = rememberPagerState(
                initialPage = targetInitialPage,
                pageCount = { pages.size.coerceAtLeast(1) }
            )

            // Sync external jumps (TOC, Search, Scrubber, TTS, Jump-back) to pager
            LaunchedEffect(currentChapterIndex, currentBlockIndex, pages.size) {
                val curPageObj = pages.getOrNull(pagerState.currentPage)
                val alreadyContainsSpot = curPageObj != null &&
                    curPageObj.chapterIndex == currentChapterIndex &&
                    curPageObj.blocks.any { it.index == currentBlockIndex }

                if (!alreadyContainsSpot && pages.isNotEmpty()) {
                    val idx = findMatchingVirtualPageIndex(pages, currentChapterIndex, currentBlockIndex)
                    pagerState.scrollToPage(idx)
                }
            }

            // Observe user swipes/page flips
            LaunchedEffect(pagerState, pages) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { pIdx ->
                        val vp = pages.getOrNull(pIdx) ?: return@collect
                        val chapTotalBlocks = chapters.getOrNull(vp.chapterIndex)?.blocks?.size?.coerceAtLeast(1) ?: 1
                        val sub = vp.startBlockIndex.toFloat() / chapTotalBlocks.toFloat()
                        onPositionChanged(vp.chapterIndex, vp.startBlockIndex, sub)
                    }
            }

            // Volume-key page turn support
            LaunchedEffect(volumeTurnEventTick) {
                val (_, delta) = volumeTurnEventTick ?: return@LaunchedEffect
                val next = (pagerState.currentPage + delta).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
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
                    val dest = (pagerState.currentPage + delta).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    if (settings.pageTurnAnimation == PageTurnAnimation.INSTANT) {
                        pagerState.scrollToPage(dest)
                    } else {
                        pagerState.animateScrollToPage(dest, animationSpec = tween(220))
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(settings.tapZonePreset) {
                        detectTapGestures { offset ->
                            when (resolveTapZone(offset, size, settings.tapZonePreset)) {
                                TapZoneAction.PREVIOUS -> turnByDelta(-1)
                                TapZoneAction.NEXT -> turnByDelta(+1)
                                TapZoneAction.TOGGLE_HUD -> onToggleHud()
                            }
                        }
                    }
            ) { pageIdx ->
                val vPage = pages.getOrNull(pageIdx)
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
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp)
                        ) {
                            vPage.blocks.forEach { (blockIdx, block) ->
                                val hl = highlightsBySpot["${vPage.chapterIndex}:$blockIdx"]
                                val isTtsSpeaking = ttsChapterIndex == vPage.chapterIndex && ttsBlockIndex == blockIdx
                                RenderContentBlock(
                                    block = block,
                                    settings = settings,
                                    fontFamily = resolvedFont,
                                    textColor = textColor,
                                    accentColor = accentColor,
                                    isDark = isDark,
                                    highlight = hl,
                                    isTtsActive = isTtsSpeaking,
                                    onShortTap = {
                                        // Forward tap to center HUD toggle for convenience
                                        onToggleHud()
                                    },
                                    onLongPress = {
                                        val txt = block.plainText().trim()
                                        if (txt.isNotEmpty()) {
                                            onBlockLongPressed(
                                                vPage.chapterIndex,
                                                vPage.chapterTitle,
                                                blockIdx,
                                                txt,
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

            // Volume-key scroll in continuous mode
            LaunchedEffect(volumeTurnEventTick) {
                val (_, delta) = volumeTurnEventTick ?: return@LaunchedEffect
                val nextItem = (listState.firstVisibleItemIndex + delta * 4)
                    .coerceAtLeast(0)
                listState.animateScrollToItem(nextItem)
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = settings.horizontalMarginDp.dp,
                    end = settings.horizontalMarginDp.dp,
                    top = (settings.verticalMarginDp + 16).dp,
                    bottom = (settings.verticalMarginDp + 56).dp
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
                        RenderContentBlock(
                            block = block,
                            settings = settings,
                            fontFamily = resolvedFont,
                            textColor = textColor,
                            accentColor = accentColor,
                            isDark = isDark,
                            highlight = hl,
                            isTtsActive = isTtsSpeaking,
                            onShortTap = onToggleHud,
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

                    // Chapter navigation footer at the end of chapter in scroll mode
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (currentChapterIndex > 0) {
                                FilledTonalButton(
                                    onClick = {
                                        onPositionChanged(currentChapterIndex - 1, 0, 0f)
                                    }
                                ) {
                                    Text("← Previous Chapter")
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            if (currentChapterIndex + 1 < chapters.size) {
                                FilledTonalButton(
                                    onClick = {
                                        onPositionChanged(currentChapterIndex + 1, 0, 0f)
                                    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RenderContentBlock(
    block: ContentBlock,
    settings: ReaderSettings,
    fontFamily: FontFamily,
    textColor: Color,
    accentColor: Color,
    isDark: Boolean,
    highlight: HighlightEntity?,
    isTtsActive: Boolean,
    onShortTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val bgHighlight = when {
        highlight != null -> Color(if (isDark) highlight.color.bgHexDark else highlight.color.bgHexLight)
        else -> Color.Transparent
    }

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

    val clickMod = Modifier.combinedClickable(
        onClick = onShortTap,
        onLongClick = onLongPress
    )

    val baseLineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp
    val letterSpacing = if (settings.fontFamily == ReaderFontFamily.DYSLEXIC_WIDE) 0.05.sp else 0.sp

    when (block) {
        is ContentBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.42f
                2 -> 1.25f
                3 -> 1.12f
                else -> 1.05f
            }
            Text(
                text = block.text,
                color = textColor,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (settings.fontSizeSp * scale).sp,
                lineHeight = (settings.fontSizeSp * scale * 1.28f).sp,
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

private fun paginateReflowChapters(
    chapters: List<BookChapter>,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    settings: ReaderSettings
): List<VirtualReflowPage> {
    val usableWidth = (viewportWidthDp - settings.horizontalMarginDp * 2f).coerceAtLeast(180f)
    val usableHeight = (viewportHeightDp - settings.verticalMarginDp * 2f - 36f).coerceAtLeast(240f)

    val approxCharsPerLine = (usableWidth / (settings.fontSizeSp * 0.56f)).toInt().coerceIn(22, 120)
    val lineHeightDp = settings.fontSizeSp * settings.lineHeightMultiplier
    val maxLinesPerPage = (usableHeight / lineHeightDp).toInt().coerceIn(8, 55)

    val pages = mutableListOf<VirtualReflowPage>()

    for (chap in chapters) {
        if (chap.blocks.isEmpty()) continue
        var currentSlice = mutableListOf<IndexedValue<ContentBlock>>()
        var usedLines = 0
        var sliceStartBlock = 0

        for ((bIdx, block) in chap.blocks.withIndex()) {
            val blockLines = when (block) {
                is ContentBlock.Heading -> ((block.text.length / approxCharsPerLine) + 2)
                is ContentBlock.EmbeddedImage -> (maxLinesPerPage * 0.55f).toInt().coerceAtLeast(5)
                ContentBlock.Divider -> 2
                else -> {
                    val txtLen = block.plainText().length.coerceAtLeast(1)
                    (txtLen / approxCharsPerLine) + 2
                }
            }

            if (currentSlice.isNotEmpty() && usedLines + blockLines > maxLinesPerPage) {
                pages += VirtualReflowPage(
                    chapterIndex = chap.index,
                    chapterTitle = chap.title,
                    startBlockIndex = sliceStartBlock,
                    blocks = currentSlice
                )
                currentSlice = mutableListOf()
                usedLines = 0
                sliceStartBlock = bIdx
            }

            if (currentSlice.isEmpty()) sliceStartBlock = bIdx
            currentSlice += IndexedValue(bIdx, block)
            usedLines += blockLines + 1
        }

        if (currentSlice.isNotEmpty()) {
            pages += VirtualReflowPage(
                chapterIndex = chap.index,
                chapterTitle = chap.title,
                startBlockIndex = sliceStartBlock,
                blocks = currentSlice
            )
        }
    }

    return pages
}

private fun findMatchingVirtualPageIndex(
    pages: List<VirtualReflowPage>,
    chapterIdx: Int,
    blockIdx: Int
): Int {
    if (pages.isEmpty()) return 0
    val exact = pages.indexOfFirst { p ->
        p.chapterIndex == chapterIdx && p.blocks.any { it.index == blockIdx }
    }
    if (exact >= 0) return exact
    val chapFirst = pages.indexOfFirst { it.chapterIndex == chapterIdx }
    return if (chapFirst >= 0) chapFirst else 0
}
