package com.imsharman.liber.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppThemeMode(val label: String) {
    SYSTEM("Follow System"),
    LIGHT("Editorial Light"),
    DARK("Slate Dark"),
    AMOLED_BLACK("True AMOLED Black")
}

@Serializable
enum class ReaderColorPreset(
    val label: String,
    val backgroundArgb: Long,
    val textArgb: Long,
    val secondaryTextArgb: Long,
    val accentArgb: Long,
    val isDark: Boolean
) {
    PAPER_DAY(
        label = "Warm Paper",
        backgroundArgb = 0xFFFAF7F0,
        textArgb = 0xFF1E1C19,
        secondaryTextArgb = 0xFF6B645B,
        accentArgb = 0xFF8C5319,
        isDark = false
    ),
    CRISP_LIGHT(
        label = "Crisp Day",
        backgroundArgb = 0xFFFFFFFF,
        textArgb = 0xFF121417,
        secondaryTextArgb = 0xFF5A626D,
        accentArgb = 0xFF1D5B79,
        isDark = false
    ),
    WARM_SEPIA(
        label = "Classic Sepia",
        backgroundArgb = 0xFFF3E7D3,
        textArgb = 0xFF3B2D1F,
        secondaryTextArgb = 0xFF745E46,
        accentArgb = 0xFF9C4A1A,
        isDark = false
    ),
    DUSK_SLATE(
        label = "Dusk Slate",
        backgroundArgb = 0xFF1B2028,
        textArgb = 0xFFDAE0EA,
        secondaryTextArgb = 0xFF8E99AB,
        accentArgb = 0xFF7AA2F7,
        isDark = true
    ),
    CONSOLE(
        label = "Console",
        backgroundArgb = 0xFF0E1511,
        textArgb = 0xFF9AD1B3,
        secondaryTextArgb = 0xFF5C8D73,
        accentArgb = 0xFF52B788,
        isDark = true
    ),
    AMOLED_BLACK(
        label = "AMOLED Pure",
        backgroundArgb = 0xFF000000,
        textArgb = 0xFFD0D4DA,
        secondaryTextArgb = 0xFF787E87,
        accentArgb = 0xFFE09F3E,
        isDark = true
    ),
    AMOLED_AMBER(
        label = "AMOLED Night",
        backgroundArgb = 0xFF000000,
        textArgb = 0xFFDFB98C,
        secondaryTextArgb = 0xFF8C7152,
        accentArgb = 0xFFD98A3C,
        isDark = true
    ),
    CUSTOM(
        label = "Custom Palette",
        backgroundArgb = 0xFF12161F,
        textArgb = 0xFFE4E8F0,
        secondaryTextArgb = 0xFF94A0B8,
        accentArgb = 0xFFE09F3E,
        isDark = true
    )
}

@Serializable
enum class VisualPageColorFilter(val label: String) {
    ORIGINAL("Original Page"),
    WARM_PAPER("Warm Tint"),
    NIGHT_INVERT("Smart Dark Invert"),
    AMOLED_CONTRAST("True AMOLED Invert")
}
