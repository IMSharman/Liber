package com.imsharman.liber.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.imsharman.liber.domain.model.AppThemeMode

private val EditorialLightColors = lightColorScheme(
    primary = Color(0xFF1F4E5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E7EC),
    onPrimaryContainer = Color(0xFF0A262E),
    secondary = Color(0xFFA86424),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8E2C8),
    onSecondaryContainer = Color(0xFF3A1E04),
    tertiary = Color(0xFF3B624B),
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFAF7F2),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEDE7DD),
    onSurfaceVariant = Color(0xFF534E46),
    outline = Color(0xFFCDC5B9)
)

private val SlateDarkColors = darkColorScheme(
    primary = Color(0xFF7CC6D6),
    onPrimary = Color(0xFF06353F),
    primaryContainer = Color(0xFF184B57),
    onPrimaryContainer = Color(0xFFC7EBF4),
    secondary = Color(0xFFE5A454),
    onSecondary = Color(0xFF422600),
    secondaryContainer = Color(0xFF5D3809),
    onSecondaryContainer = Color(0xFFFFDDB7),
    tertiary = Color(0xFF8EC9A3),
    background = Color(0xFF12161C),
    onBackground = Color(0xFFE5E8EE),
    surface = Color(0xFF171C24),
    onSurface = Color(0xFFE5E8EE),
    surfaceVariant = Color(0xFF232A36),
    onSurfaceVariant = Color(0xFFA7B0C0),
    outline = Color(0xFF394252)
)

private val AmoledBlackColors = darkColorScheme(
    primary = Color(0xFF82CED8),
    onPrimary = Color(0xFF002B31),
    primaryContainer = Color(0xFF113942),
    onPrimaryContainer = Color(0xFFCBEFF5),
    secondary = Color(0xFFE09F3E),
    onSecondary = Color(0xFF382000),
    secondaryContainer = Color(0xFF4B2D06),
    onSecondaryContainer = Color(0xFFFFDEA8),
    tertiary = Color(0xFF80C49F),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE8EAEE),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE8EAEE),
    surfaceVariant = Color(0xFF101318),
    onSurfaceVariant = Color(0xFF9EA6B4),
    outline = Color(0xFF242933)
)

val LiberTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun LiberTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val scheme = when (themeMode) {
        AppThemeMode.LIGHT -> EditorialLightColors
        AppThemeMode.DARK -> SlateDarkColors
        AppThemeMode.AMOLED_BLACK -> AmoledBlackColors
        AppThemeMode.SYSTEM -> if (systemDark) SlateDarkColors else EditorialLightColors
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = LiberTypography,
        content = content
    )
}
