package com.imsharman.liber.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReaderFontFamily(val label: String) {
    SERIF("Editorial Serif"),
    SANS_SERIF("Clean Sans"),
    MONOSPACE("Typewriter Mono"),
    CURSIVE("Humanist Script"),
    DYSLEXIC_WIDE("Accessible Wide"),
    CUSTOM_FILE("Custom Imported Font")
}

@Serializable
enum class ReaderScrollMode(val label: String) {
    PAGINATED_HORIZONTAL("Paginated Flip"),
    CONTINUOUS_VERTICAL("Continuous Scroll")
}

@Serializable
enum class PageTurnAnimation(val label: String) {
    SLIDE("Smooth Slide"),
    FADE("Subtle Crossfade"),
    INSTANT("E-Ink Instant")
}

@Serializable
enum class TapZonePreset(val label: String, val description: String) {
    CLASSIC_SIDES(
        "ReadEra Classic (Left / Center / Right)",
        "Tap left edge for previous page, right edge for next page, center for controls"
    ),
    VERTICAL_HALVES(
        "Vertical Split (Top / Center / Bottom)",
        "Tap top third for previous page, bottom third for next page, center for controls"
    ),
    ONE_HAND_RIGHT(
        "One-Handed Thumb",
        "Narrow left strip goes back; wide right and bottom zone advances page"
    )
}

@Serializable
enum class ComicReadingDirection(val label: String) {
    LEFT_TO_RIGHT("Western (Left → Right)"),
    RIGHT_TO_LEFT("Manga (Right → Left)")
}

@Serializable
data class ReaderSettings(
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorPreset: ReaderColorPreset = ReaderColorPreset.PAPER_DAY,
    val customBackgroundArgb: Long = 0xFF10141C,
    val customTextArgb: Long = 0xFFE6E9EF,
    val customAccentArgb: Long = 0xFFE09F3E,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SERIF,
    val customFontPath: String? = null,
    val customFontName: String? = null,
    val fontSizeSp: Float = 18f,
    val fontWeight: Int = 400,
    val lineHeightMultiplier: Float = 1.55f,
    val paragraphSpacingDp: Int = 12,
    val horizontalMarginDp: Int = 22,
    val verticalMarginDp: Int = 18,
    val justifyText: Boolean = true,
    val hyphenationEnabled: Boolean = true,
    val firstLineIndentEnabled: Boolean = false,
    val bionicReadingEnabled: Boolean = false,
    val scrollMode: ReaderScrollMode = ReaderScrollMode.PAGINATED_HORIZONTAL,
    val pageTurnAnimation: PageTurnAnimation = PageTurnAnimation.SLIDE,
    val tapZonePreset: TapZonePreset = TapZonePreset.CLASSIC_SIDES,
    val leftEdgeBrightnessSwipe: Boolean = true,
    val volumeKeysTurnPages: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val showMiniStatusFooter: Boolean = true,
    val visualPageColorFilter: VisualPageColorFilter = VisualPageColorFilter.ORIGINAL,
    val comicReadingDirection: ComicReadingDirection = ComicReadingDirection.LEFT_TO_RIGHT,
    val pdfReflowMode: Boolean = false,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val dailyGoalMinutes: Int = 30,
    val offlineOnlyMode: Boolean = false,
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val lastWebDavSyncEpochMs: Long = 0L
) {
    val activeBackgroundArgb: Long
        get() = if (colorPreset == ReaderColorPreset.CUSTOM) customBackgroundArgb else colorPreset.backgroundArgb

    val activeTextArgb: Long
        get() = if (colorPreset == ReaderColorPreset.CUSTOM) customTextArgb else colorPreset.textArgb

    val activeAccentArgb: Long
        get() = if (colorPreset == ReaderColorPreset.CUSTOM) customAccentArgb else colorPreset.accentArgb

    val isDarkReader: Boolean
        get() = if (colorPreset == ReaderColorPreset.CUSTOM) {
            val r = (customBackgroundArgb shr 16) and 0xFF
            val g = (customBackgroundArgb shr 8) and 0xFF
            val b = customBackgroundArgb and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b) < 128
        } else {
            colorPreset.isDark
        }
}
