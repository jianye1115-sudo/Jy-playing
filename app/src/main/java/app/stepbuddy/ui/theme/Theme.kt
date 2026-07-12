package app.stepbuddy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * App theme. [fontScale] is the user's Settings value (elderly default 1.3);
 * it multiplies the system font scale so ALL text — and by extension the
 * min-56dp touch targets that size from text — grows together.
 */
@Composable
fun StepBuddyTheme(
    fontScale: Float = 1.0f,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val base = LocalDensity.current
    val scaledDensity = Density(
        density = base.density,
        fontScale = base.fontScale * fontScale,
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StepBuddyTypography,
            content = content,
        )
    }
}
