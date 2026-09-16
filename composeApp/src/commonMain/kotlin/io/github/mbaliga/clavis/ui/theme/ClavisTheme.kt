package io.github.mbaliga.clavis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

/** Theme preference persisted by the UI layer (product-spec §7.1 ★ `theme`). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Access the raw Clavis tokens and type ramp where Material roles are too coarse. */
object ClavisTheme {
    val palette: ClavisPalette
        @Composable @ReadOnlyComposable get() = LocalClavisTokens.current
    val type: ClavisTypography
        @Composable @ReadOnlyComposable get() = LocalClavisType.current
    val fonts: ClavisFontFamilies
        @Composable @ReadOnlyComposable get() = LocalClavisFonts.current
}

/** Radii from template-spec §11.3. */
object ClavisShapes {
    val button = RoundedCornerShape(10.dp)
    val card = RoundedCornerShape(14.dp)
    val scene = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(999.dp)
    val stat = RoundedCornerShape(10.dp)
    val tok = RoundedCornerShape(6.dp)
    val dock = RoundedCornerShape(18.dp)
    val dockButton = RoundedCornerShape(11.dp)
    val hint = RoundedCornerShape(10.dp)
    val parallel = RoundedCornerShape(10.dp)
    val pathcard = RoundedCornerShape(12.dp)
    val wordcard = RoundedCornerShape(16.dp)
}

private val M3Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Material 3 theme fed entirely from [ClavisTokens], plus the Clavis-specific locals: the raw
 * tokens ([LocalClavisTokens]), the font families and type ramp, and the reduced-motion flag.
 */
@Composable
fun ClavisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) ClavisTokens.Dark else ClavisTokens.Light
    val colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette)
    val fonts = rememberClavisFontFamilies()
    val type = remember(fonts) { ClavisTypography(fonts) }
    val m3Type = remember(type) {
        Typography(
            displayLarge = type.testWord, displayMedium = type.obH2, displaySmall = type.h2,
            headlineLarge = type.h2, headlineMedium = type.stat, headlineSmall = type.h3,
            titleLarge = type.h3, titleMedium = type.button, titleSmall = type.small,
            bodyLarge = type.body, bodyMedium = type.small, bodySmall = type.tiny,
            labelLarge = type.button, labelMedium = type.eyebrow, labelSmall = type.chip,
        )
    }
    CompositionLocalProvider(
        LocalClavisTokens provides palette,
        LocalClavisFonts provides fonts,
        LocalClavisType provides type,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(colorScheme = colorScheme, shapes = M3Shapes, typography = m3Type, content = content)
    }
}

private fun lightScheme(p: ClavisPalette): ColorScheme = lightColorScheme(
    primary = p.btnPrimary, onPrimary = p.btnPrimaryInk,
    primaryContainer = p.accentSoft, onPrimaryContainer = p.accentInk,
    secondary = p.accent, onSecondary = p.surface,
    secondaryContainer = p.accentSoft, onSecondaryContainer = p.accentInk,
    tertiary = p.ochre, onTertiary = p.surface,
    tertiaryContainer = p.ochreSoft, onTertiaryContainer = p.ochre,
    error = p.rust, onError = p.surface, errorContainer = p.rustSoft, onErrorContainer = p.rust,
    background = p.paper, onBackground = p.ink,
    surface = p.surface, onSurface = p.ink,
    surfaceVariant = p.surface2, onSurfaceVariant = p.ink2,
    surfaceContainerLowest = p.surface, surfaceContainerLow = p.paper, surfaceContainer = p.surface2,
    surfaceContainerHigh = p.cosmosGround, surfaceContainerHighest = p.line,
    outline = p.line, outlineVariant = p.line2, scrim = p.ink,
)

private fun darkScheme(p: ClavisPalette): ColorScheme = darkColorScheme(
    primary = p.btnPrimary, onPrimary = p.btnPrimaryInk,
    primaryContainer = p.accentSoft, onPrimaryContainer = p.accentInk,
    secondary = p.accent, onSecondary = p.paper,
    secondaryContainer = p.accentSoft, onSecondaryContainer = p.accentInk,
    tertiary = p.ochre, onTertiary = p.paper,
    tertiaryContainer = p.ochreSoft, onTertiaryContainer = p.ochre,
    error = p.rust, onError = p.paper, errorContainer = p.rustSoft, onErrorContainer = p.rust,
    background = p.paper, onBackground = p.ink,
    surface = p.surface, onSurface = p.ink,
    surfaceVariant = p.surface2, onSurfaceVariant = p.ink2,
    surfaceContainerLowest = p.paper, surfaceContainerLow = p.surface, surfaceContainer = p.surface2,
    surfaceContainerHigh = p.cosmosGround, surfaceContainerHighest = p.line,
    outline = p.line, outlineVariant = p.line2, scrim = p.paper,
)
