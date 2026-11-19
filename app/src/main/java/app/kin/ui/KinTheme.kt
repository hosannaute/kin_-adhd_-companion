package app.kin.ui

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Ink = Color(0xFF18262B)
val Violet = Color(0xFF6554B3)
val Mint = Color(0xFFA6D5C5)
val Cream = Color(0xFFF6F8F5)

@Immutable
data class KinExtendedColors(
    val xpGold: Color,
    val xpGoldContainer: Color,
    val xpGoldOnContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val warningOnContainer: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val celebrationStart: Color,
    val celebrationEnd: Color,
    val cardBorder: Color,
    val priorityHigh: Color,
    val priorityNormal: Color,
    val priorityGentle: Color
)

private val LightExtendedColors = KinExtendedColors(
    xpGold = Color(0xFFC57B1A),
    xpGoldContainer = Color(0xFFFFF3DC),
    xpGoldOnContainer = Color(0xFF6E4104),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFEF3C7),
    warningOnContainer = Color(0xFF78350F),
    heroStart = Color(0xFFD8EFE6),
    heroEnd = Color(0xFFEAF4F7),
    celebrationStart = Color(0xFFD9F3E7),
    celebrationEnd = Color(0xFFFFF6E3),
    cardBorder = Color(0xFFD8E3DD),
    priorityHigh = Color(0xFFC94A53),
    priorityNormal = Color(0xFF1E6B5C),
    priorityGentle = Color(0xFF6B7F86)
)

private val DarkExtendedColors = KinExtendedColors(
    xpGold = Color(0xFFF5C469),
    xpGoldContainer = Color(0xFF3D2E12),
    xpGoldOnContainer = Color(0xFFFFE4AE),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF3E2B10),
    warningOnContainer = Color(0xFFFDE68A),
    heroStart = Color(0xFF1B3A35),
    heroEnd = Color(0xFF1A2D38),
    celebrationStart = Color(0xFF1B3C33),
    celebrationEnd = Color(0xFF352C1B),
    cardBorder = Color(0xFF2B3E44),
    priorityHigh = Color(0xFFF28B92),
    priorityNormal = Color(0xFF7CE0C3),
    priorityGentle = Color(0xFF93AAB0)
)

val LocalKinColors = staticCompositionLocalOf { LightExtendedColors }

object KinPalette {
    val current: KinExtendedColors
        @Composable get() = LocalKinColors.current
}

private val KinTypography = Typography(
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1.0).sp, fontFeatureSettings = "tnum"),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp, letterSpacing = (-0.15).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.5.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.6.sp)
)

@Suppress("DEPRECATION")
@Composable
fun KinTheme(dark: Boolean, content: @Composable () -> Unit) {
    val extended = if (dark) DarkExtendedColors else LightExtendedColors
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF7CE0C3), onPrimary = Color(0xFF04382C), secondary = Mint,
        background = Color(0xFF0D1518), onBackground = Color(0xFFECF4F1),
        surface = Color(0xFF152227), onSurface = Color(0xFFECF4F1),
        onSurfaceVariant = Color(0xFFA7BDBE), surfaceVariant = Color(0xFF1E3036),
        primaryContainer = Color(0xFF1C433A), onPrimaryContainer = Color(0xFFC5F4E6),
        secondaryContainer = Color(0xFF21363E), onSecondaryContainer = Color(0xFFD0E7EE),
        error = Color(0xFFF28B92), onError = Color(0xFF5C1118),
        errorContainer = Color(0xFF471B20), onErrorContainer = Color(0xFFFFDAD9),
        outline = Color(0xFF7C9493), outlineVariant = Color(0xFF2B3E44),
        surfaceTint = Color(0xFF7CE0C3), surfaceDim = Color(0xFF0D1518), surfaceBright = Color(0xFF27393F),
        surfaceContainerLowest = Color(0xFF081013), surfaceContainerLow = Color(0xFF121E23),
        surfaceContainer = Color(0xFF152227), surfaceContainerHigh = Color(0xFF1E2E34), surfaceContainerHighest = Color(0xFF26383F))
    else lightColorScheme(
        primary = Color(0xFF1C6B5B), onPrimary = Color.White, secondary = Color(0xFF3E6370),
        background = Cream, onBackground = Ink, surface = Color.White, onSurface = Ink,
        surfaceVariant = Color(0xFFEAF0EC), onSurfaceVariant = Color(0xFF4B605C),
        primaryContainer = Color(0xFFD9EFE7), onPrimaryContainer = Color(0xFF144238),
        secondaryContainer = Color(0xFFE2EDF1), onSecondaryContainer = Color(0xFF23424D),
        error = Color(0xFFC43D47), onError = Color.White,
        errorContainer = Color(0xFFFCE8E9), onErrorContainer = Color(0xFF7C1D24),
        outline = Color(0xFF738881), outlineVariant = Color(0xFFD8E3DD),
        surfaceTint = Color(0xFF1C6B5B), surfaceDim = Color(0xFFD9E3DE), surfaceBright = Color(0xFFFCFEFC),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F5F2),
        surfaceContainer = Color(0xFFEBF0ED), surfaceContainerHigh = Color(0xFFE4ECE7), surfaceContainerHighest = Color(0xFFDCE6E0))
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalKinColors provides extended) {
        MaterialTheme(
            colorScheme = colors,
            typography = KinTypography,
            shapes = Shapes(
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(26.dp),
                extraLarge = RoundedCornerShape(32.dp)
            ),
            content = content
        )
    }
}
