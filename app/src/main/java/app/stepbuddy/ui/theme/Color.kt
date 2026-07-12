package app.stepbuddy.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Warm teal + amber palette, chosen and tuned for high contrast so large text
 * stays comfortably readable for elderly users (WCAG AA+ on the sizes used).
 */
val Teal700 = Color(0xFF00695C)
val Teal500 = Color(0xFF00897B)
val Teal100 = Color(0xFFB2DFDB)
val TealDark = Color(0xFF4DB6AC)
val Amber600 = Color(0xFFFF8F00)
val Amber100 = Color(0xFFFFE0B2)
val WarmWhite = Color(0xFFFFFDF8)
val InkBlack = Color(0xFF1A1C1B)
val DangerRed = Color(0xFFB3261E)

val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Amber600,
    onSecondary = Color.Black,
    secondaryContainer = Amber100,
    onSecondaryContainer = Color(0xFF2A1800),
    background = WarmWhite,
    onBackground = InkBlack,
    surface = Color.White,
    onSurface = InkBlack,
    surfaceVariant = Color(0xFFE7E0D8),
    onSurfaceVariant = Color(0xFF474640),
    outline = Color(0xFF6E6D66),
    error = DangerRed,
    onError = Color.White,
)

val DarkColors = darkColorScheme(
    primary = TealDark,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Teal700,
    onPrimaryContainer = Teal100,
    secondary = Amber600,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF5A3D00),
    onSecondaryContainer = Amber100,
    background = Color(0xFF12140F),
    onBackground = Color(0xFFECEBE4),
    surface = Color(0xFF1B1D18),
    onSurface = Color(0xFFECEBE4),
    surfaceVariant = Color(0xFF45473F),
    onSurfaceVariant = Color(0xFFC6C7BC),
    outline = Color(0xFF90918A),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)
