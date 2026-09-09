@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.cijian.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.cijian.app.R

// ── 纸墨东方配色 ──
val Paper = Color(0xFFF6F2E7)        // 宣纸
val PaperDeep = Color(0xFFEDE7D8)    // 深宣
val Ink = Color(0xFF2A2722)          // 焦墨
val InkSoft = Color(0xFF72695B)      // 淡墨
val InkFaint = Color(0xFFB9B0A0)     // 枯墨
val Cinnabar = Color(0xFFB23A2E)     // 朱砂
val CinnabarDeep = Color(0xFF8E2D24) // 朱砂深
val Indigo = Color(0xFF35565A)       // 黛青
val Gold = Color(0xFF9C7C46)         // 描金

/** 思源宋体（Noto Serif SC 可变字体，随包分发，保证纸墨气质不依赖机型） */
val AppSerif: FontFamily = FontFamily(
    Font(
        R.font.noto_serif_sc, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.noto_serif_sc, FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.noto_serif_sc, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
)

private val PaperScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = PaperDeep,
    onPrimaryContainer = Ink,
    secondary = Cinnabar,
    onSecondary = Paper,
    secondaryContainer = Color(0xFFE8CFC6),
    onSecondaryContainer = CinnabarDeep,
    tertiary = Indigo,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkSoft,
    outline = InkFaint,
    outlineVariant = Color(0xFFD8D0BE),
    error = Cinnabar,
    onError = Paper,
)

private fun ciJianTypography(): Typography {
    val base = Typography()
    fun of(s: androidx.compose.ui.text.TextStyle) = s.copy(fontFamily = AppSerif)
    return Typography(
        displayLarge = of(base.displayLarge), displayMedium = of(base.displayMedium),
        displaySmall = of(base.displaySmall), headlineLarge = of(base.headlineLarge),
        headlineMedium = of(base.headlineMedium), headlineSmall = of(base.headlineSmall),
        titleLarge = of(base.titleLarge), titleMedium = of(base.titleMedium),
        titleSmall = of(base.titleSmall), bodyLarge = of(base.bodyLarge),
        bodyMedium = of(base.bodyMedium), bodySmall = of(base.bodySmall),
        labelLarge = of(base.labelLarge), labelMedium = of(base.labelMedium),
        labelSmall = of(base.labelSmall),
    )
}

private val CiJianShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(5.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun CiJianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperScheme,
        typography = ciJianTypography(),
        shapes = CiJianShapes,
        content = content
    )
}
