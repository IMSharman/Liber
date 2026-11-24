package com.imsharman.liber.ui.reader

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imsharman.liber.data.preferences.ReaderPreferencesRepository
import com.imsharman.liber.data.storage.InternalSandboxManager
import com.imsharman.liber.domain.model.BookFormat
import com.imsharman.liber.domain.model.ComicReadingDirection
import com.imsharman.liber.domain.model.PageTurnAnimation
import com.imsharman.liber.domain.model.ReaderColorPreset
import com.imsharman.liber.domain.model.ReaderFontFamily
import com.imsharman.liber.domain.model.ReaderScrollMode
import com.imsharman.liber.domain.model.ReaderSettings
import com.imsharman.liber.domain.model.TapZonePreset
import com.imsharman.liber.domain.model.VisualPageColorFilter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderControlsSheet(
    format: BookFormat,
    settings: ReaderSettings,
    prefs: ReaderPreferencesRepository,
    sandboxManager: InternalSandboxManager,
    onPdfReflowToggled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableIntStateOf(0) }

    var customBgHex by remember(settings.customBackgroundArgb) {
        mutableStateOf("%06X".format(settings.customBackgroundArgb and 0xFFFFFF))
    }
    var customFgHex by remember(settings.customTextArgb) {
        mutableStateOf("%06X".format(settings.customTextArgb and 0xFFFFFF))
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                var displayName = "custom_font.ttf"
                runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) displayName = c.getString(0)
                    }
                }
                val savedFile = sandboxManager.importExplicitFontFile(uri, displayName)
                if (savedFile != null) {
                    prefs.updateReaderSettings {
                        it.copy(
                            fontFamily = ReaderFontFamily.CUSTOM_FILE,
                            customFontPath = savedFile.absolutePath,
                            customFontName = displayName.substringBeforeLast('.')
                        )
                    }
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(bottom = 24.dp)
        ) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Theme") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Typography") })
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Layout & Touch") })
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        Text("Reader Color Presets", fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderColorPreset.entries.forEach { preset ->
                                val isSelected = settings.colorPreset == preset
                                val bg = if (preset == ReaderColorPreset.CUSTOM) {
                                    Color(settings.customBackgroundArgb)
                                } else Color(preset.backgroundArgb)
                                val fg = if (preset == ReaderColorPreset.CUSTOM) {
                                    Color(settings.customTextArgb)
                                } else Color(preset.textArgb)

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            scope.launch {
                                                prefs.updateReaderSettings { it.copy(colorPreset = preset) }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = preset.label,
                                        color = fg,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (settings.colorPreset == ReaderColorPreset.CUSTOM) {
                            HorizontalDivider()
                            Text("Custom Hex Palette (RRGGBB)", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customBgHex,
                                    onValueChange = { hex ->
                                        customBgHex = hex.take(6).uppercase()
                                        if (customBgHex.length == 6) {
                                            customBgHex.toLongOrNull(16)?.let { rgb ->
                                                scope.launch {
                                                    prefs.updateReaderSettings {
                                                        it.copy(customBackgroundArgb = 0xFF000000L or rgb)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    label = { Text("Background #") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = customFgHex,
                                    onValueChange = { hex ->
                                        customFgHex = hex.take(6).uppercase()
                                        if (customFgHex.length == 6) {
                                            customFgHex.toLongOrNull(16)?.let { rgb ->
                                                scope.launch {
                                                    prefs.updateReaderSettings {
                                                        it.copy(customTextArgb = 0xFF000000L or rgb)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    label = { Text("Text #") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (format == BookFormat.PDF || format == BookFormat.CBZ || format == BookFormat.CBR) {
                            HorizontalDivider()
                            Text("PDF & Comic Page Color Filter", fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                VisualPageColorFilter.entries.forEach { vf ->
                                    FilterChip(
                                        selected = settings.visualPageColorFilter == vf,
                                        onClick = {
                                            scope.launch {
                                                prefs.updateReaderSettings { it.copy(visualPageColorFilter = vf) }
                                            }
                                        },
                                        label = { Text(vf.label) }
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        Text("Font Family", fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReaderFontFamily.entries.forEach { ff ->
                                if (ff != ReaderFontFamily.CUSTOM_FILE || settings.customFontPath != null) {
                                    val lbl = if (ff == ReaderFontFamily.CUSTOM_FILE && settings.customFontName != null) {
                                        "Custom: ${settings.customFontName}"
                                    } else ff.label
                                    FilterChip(
                                        selected = settings.fontFamily == ff,
                                        onClick = {
                                            scope.launch {
                                                prefs.updateReaderSettings { it.copy(fontFamily = ff) }
                                            }
                                        },
                                        label = { Text(lbl) }
                                    )
                                }
                            }
                        }
                        FilledTonalButton(onClick = { fontPickerLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.FontDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Custom Font (.ttf / .otf)")
                        }

                        HorizontalDivider()

                        Text("Font Size: ${settings.fontSizeSp.toInt()} sp")
                        Slider(
                            value = settings.fontSizeSp,
                            onValueChange = { v ->
                                scope.launch { prefs.updateReaderSettings { it.copy(fontSizeSp = v) } }
                            },
                            valueRange = 13f..34f
                        )

                        Text("Line Spacing: ${"%.2f".format(settings.lineHeightMultiplier)}x")
                        Slider(
                            value = settings.lineHeightMultiplier,
                            onValueChange = { v ->
                                scope.launch { prefs.updateReaderSettings { it.copy(lineHeightMultiplier = v) } }
                            },
                            valueRange = 1.15f..2.20f
                        )

                        Text("Font Boldness / Weight: ${settings.fontWeight}")
                        Slider(
                            value = settings.fontWeight.toFloat(),
                            onValueChange = { v ->
                                scope.launch { prefs.updateReaderSettings { it.copy(fontWeight = v.toInt()) } }
                            },
                            valueRange = 300f..700f,
                            steps = 3
                        )

                        ToggleRow(
                            label = "Justified Text Alignment",
                            checked = settings.justifyText,
                            onCheckedChange = { c ->
                                scope.launch { prefs.updateReaderSettings { it.copy(justifyText = c) } }
                            }
                        )
                        ToggleRow(
                            label = "Automatic Hyphenation",
                            checked = settings.hyphenationEnabled,
                            onCheckedChange = { c ->
                                scope.launch { prefs.updateReaderSettings { it.copy(hyphenationEnabled = c) } }
                            }
                        )
                        ToggleRow(
                            label = "First-Line Paragraph Indent",
                            checked = settings.firstLineIndentEnabled,
                            onCheckedChange = { c ->
                                scope.launch { prefs.updateReaderSettings { it.copy(firstLineIndentEnabled = c) } }
                            }
                        )
                        ToggleRow(
                            label = "Bionic Reading (Bold Word Prefixes)",
                            checked = settings.bionicReadingEnabled,
                            onCheckedChange = { c ->
                                scope.launch { prefs.updateReaderSettings { it.copy(bionicReadingEnabled = c) } }
                            }
                        )
                    }

                    2 -> {
                        Text("Reading Mode", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReaderScrollMode.entries.forEach { sm ->
                                FilterChip(
                                    selected = settings.scrollMode == sm,
                                    onClick = {
                                        scope.launch { prefs.updateReaderSettings { it.copy(scrollMode = sm) } }
                                    },
                                    label = { Text(sm.label) }
                                )
                            }
                        }

                        if (settings.scrollMode == ReaderScrollMode.PAGINATED_HORIZONTAL) {
                            Text("Page Turn Animation", fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PageTurnAnimation.entries.forEach { anim ->
                                    FilterChip(
                                        selected = settings.pageTurnAnimation == anim,
                                        onClick = {
                                            scope.launch {
                                                prefs.updateReaderSettings { it.copy(pageTurnAnimation = anim) }
                                            }
                                        },
                                        label = { Text(anim.label) }
                                    )
                                }
                            }
                        }

                        if (format == BookFormat.PDF) {
                            HorizontalDivider()
                            ToggleRow(
                                label = "PDF Text Reflow Mode (Reflowable Typography & TTS)",
                                checked = settings.pdfReflowMode,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        prefs.updateReaderSettings { it.copy(pdfReflowMode = enabled) }
                                        onPdfReflowToggled(enabled)
                                    }
                                }
                            )
                        }

                        if (format == BookFormat.CBZ || format == BookFormat.CBR) {
                            HorizontalDivider()
                            Text("Comic / Manga Direction", fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ComicReadingDirection.entries.forEach { dir ->
                                    FilterChip(
                                        selected = settings.comicReadingDirection == dir,
                                        onClick = {
                                            scope.launch {
                                                prefs.updateReaderSettings { it.copy(comicReadingDirection = dir) }
                                            }
                                        },
                                        label = { Text(dir.label) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        Text("Side Margins: ${settings.horizontalMarginDp} dp")
                        Slider(
                            value = settings.horizontalMarginDp.toFloat(),
                            onValueChange = { v ->
                                scope.launch {
                                    prefs.updateReaderSettings { it.copy(horizontalMarginDp = v.toInt()) }
                                }
                            },
                            valueRange = 8f..54f
                        )

                        Text("Paragraph Spacing: ${settings.paragraphSpacingDp} dp")
                        Slider(
                            value = settings.paragraphSpacingDp.toFloat(),
                            onValueChange = { v ->
                                scope.launch {
                                    prefs.updateReaderSettings { it.copy(paragraphSpacingDp = v.toInt()) }
                                }
                            },
                            valueRange = 4f..28f
                        )

                        HorizontalDivider()

                        Text("Touch Tap Zone Layout", fontWeight = FontWeight.SemiBold)
                        TapZonePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = settings.tapZonePreset == preset,
                                onClick = {
                                    scope.launch {
                                        prefs.updateReaderSettings { it.copy(tapZonePreset = preset) }
                                    }
                                },
                                label = { Text(preset.label) }
                            )
                        }

                        ToggleRow(
                            label = "Swipe Left Screen Edge for Brightness",
                            checked = settings.leftEdgeBrightnessSwipe,
                            onCheckedChange = { c ->
                                scope.launch { prefs.updateReaderSettings { it.copy(leftEdgeBrightnessSwipe = c) } }
                            }
                        )
                        ToggleRow(
                            label = "Turn Pages with Volume Keys",
                            checked = settings.volumeKeysTurnPages,
                            onCheckedChange = { c ->
                                scope.launch { prefs.updateReaderSettings { it.copy(volumeKeysTurnPages = c) } }
                            }
                        )
                        ToggleRow(
                            label = "Show Minimalist Progress & Battery Footer",
                            checked = settings.showMiniStatusFooter,
                            onCheckedChange = { c ->
                                scope.launch { prefs.updateReaderSettings { it.copy(showMiniStatusFooter = c) } }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
