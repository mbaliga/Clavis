package io.github.mbaliga.clavis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalClavisPalette = staticCompositionLocalOf { ClavisTokens.Light }

/** Access the raw Clavis tokens (for grounds, grain, HUD pills) where Material roles are too coarse. */
object ClavisTheme {
    val palette: ClavisPalette
        @Composable @ReadOnlyComposable get() = LocalClavisPalette.current
}

private val ClavisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Material 3 theme fed entirely from [ClavisTokens]. Roles are mapped so that default M3
 * components already read as Clavis: charcoal-on-porcelain primary buttons, `accent` reserved for
 * secondary/tertiary emphasis, hairline outlines from `line`, and grounds from `paper`/`surface`.
 */
@Composable
fun ClavisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) ClavisTokens.Dark else ClavisTokens.Light
    val colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette)
    CompositionLocalProvider(LocalClavisPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ClavisShapes,
            content = content,
        )
    }
}

private fun lightScheme(p: ClavisPalette): ColorScheme = lightColorScheme(
    primary = p.btnPrimary,
    onPrimary = p.btnPrimaryInk,
    primaryContainer = p.surface2,
    onPrimaryContainer = p.ink,
    secondary = p.accent,
    onSecondary = p.surface,
    secondaryContainer = p.cosmosGround,
    onSecondaryContainer = p.ink,
    tertiary = p.accent,
    onTertiary = p.surface,
    background = p.paper,
    onBackground = p.ink,
    surface = p.surface,
    onSurface = p.ink,
    surfaceVariant = p.surface2,
    onSurfaceVariant = p.ink2,
    surfaceContainerLowest = p.surface,
    surfaceContainerLow = p.paper,
    surfaceContainer = p.surface2,
    surfaceContainerHigh = p.cosmosGround,
    surfaceContainerHighest = p.line,
    outline = p.line,
    outlineVariant = p.line2,
    scrim = p.ink,
)

private fun darkScheme(p: ClavisPalette): ColorScheme = darkColorScheme(
    primary = p.btnPrimary,
    onPrimary = p.btnPrimaryInk,
    primaryContainer = p.surface2,
    onPrimaryContainer = p.ink,
    secondary = p.accent,
    onSecondary = p.paper,
    secondaryContainer = p.cosmosGround,
    onSecondaryContainer = p.ink,
    tertiary = p.accent,
    onTertiary = p.paper,
    background = p.paper,
    onBackground = p.ink,
    surface = p.surface,
    onSurface = p.ink,
    surfaceVariant = p.surface2,
    onSurfaceVariant = p.ink2,
    surfaceContainerLowest = p.paper,
    surfaceContainerLow = p.surface,
    surfaceContainer = p.surface2,
    surfaceContainerHigh = p.cosmosGround,
    surfaceContainerHighest = p.line,
    outline = p.line,
    outlineVariant = p.line2,
    scrim = p.paper,
)
