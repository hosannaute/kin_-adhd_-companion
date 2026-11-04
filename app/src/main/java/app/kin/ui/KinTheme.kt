package app.kin.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF202F36)
val Violet = Color(0xFF6554B3)
val Mint = Color(0xFFA6D5C5)
val Cream = Color(0xFFF4F6F5)

private val KinTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

@Suppress("DEPRECATION")
@Composable
fun KinTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF8CD6C7), onPrimary = Color(0xFF063A32), secondary = Mint,
        background = Color(0xFF101B20), onBackground = Color(0xFFEDF4F2),
        surface = Color(0xFF1A282D), onSurface = Color(0xFFEDF4F2),
        onSurfaceVariant = Color(0xFFB7C9C7), surfaceVariant = Color(0xFF27383D),
        primaryContainer = Color(0xFF24483F), onPrimaryContainer = Color(0xFFC8F2E7),
        secondaryContainer = Color(0xFF293D44), onSecondaryContainer = Color(0xFFD2E6EC),
        outline = Color(0xFF8BA29F), outlineVariant = Color(0xFF35484B))
    else lightColorScheme(
        primary = Color(0xFF226A5B), onPrimary = Color.White, secondary = Color(0xFF446572),
        background = Cream, onBackground = Ink, surface = Color.White, onSurface = Ink,
        surfaceVariant = Color(0xFFEBF0EE), onSurfaceVariant = Color(0xFF50645F),
        primaryContainer = Color(0xFFDFEDE5), onPrimaryContainer = Color(0xFF22483D),
        secondaryContainer = Color(0xFFE4EDF0), onSecondaryContainer = Color(0xFF294650),
        outline = Color(0xFF768981), outlineVariant = Color(0xFFDCE5DF))
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
    MaterialTheme(colorScheme = colors, typography = KinTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp)), content = content)
}
