package com.example.running.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private const val TNUM = "tnum"

/** System sans (no bundled fonts). Numeric styles use tabular figures so digits don't jitter. */
object AppText {
    /** 72 Bold — Record distance. */
    val hero = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = TNUM)
    /** 44 SemiBold — detail headline stats. */
    val numberXL = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TNUM)
    /** 28 SemiBold — stat tiles. */
    val numberL = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = TNUM)
    /** 20 Medium — dense grids, splits. */
    val numberM = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp, fontFeatureSettings = TNUM)
    val titleL = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp)
    val titleM = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val bodyS = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    /** 11 Medium, 1.2 tracking — stat labels. Uppercase the text itself (`.uppercase()`). */
    val eyebrow = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp)
}

internal val AppTypography = Typography(
    displayLarge = AppText.hero,
    displayMedium = AppText.numberXL,
    displaySmall = AppText.numberL,
    headlineLarge = AppText.titleL,
    headlineMedium = AppText.titleL,
    headlineSmall = AppText.titleM,
    titleLarge = AppText.titleL,
    titleMedium = AppText.titleM,
    titleSmall = AppText.label,
    bodyLarge = AppText.body,
    bodyMedium = AppText.bodyS,
    bodySmall = AppText.label,
    labelLarge = AppText.label.copy(fontSize = 14.sp),
    labelMedium = AppText.label,
    labelSmall = AppText.eyebrow,
)
