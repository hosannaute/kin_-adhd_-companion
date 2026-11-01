package app.kin.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF26334D)
val Violet = Color(0xFF7770BD)
val Mint = Color(0xFF9ACBBB)
val Cream = Color(0xFFFAF7F2)

@Composable
fun KinTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(primary = Color(0xFFBBB4FF), secondary = Mint,
        background = Color(0xFF171E30), surface = Color(0xFF222A3D), onSurface = Color(0xFFF2EDF6))
    else lightColorScheme(primary = Violet, secondary = Color(0xFF437D70), background = Cream,
        surface = Color(0xFFFFFDF9), surfaceVariant = Color(0xFFEDE9F4), onSurface = Ink,
        primaryContainer = Color(0xFFE4DFFA), onPrimaryContainer = Ink)
    MaterialTheme(colorScheme = colors, content = content)
}
