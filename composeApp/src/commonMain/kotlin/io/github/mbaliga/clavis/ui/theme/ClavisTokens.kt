package io.github.mbaliga.clavis.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Clavis colour tokens, transcribed from docs/06-app-architecture.md §8 ("Design system v0.10").
 *
 * The brief: "the entire interface is low contrast, similar colours for the foreground and
 * background... the ground is a low contrast colour that fades into the background." Light and
 * dark are two structurally identical sets, not an inversion of one another.
 */
@Immutable
data class ClavisPalette(
    val paper: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val line2: Color,
    val accent: Color,
    val cosmosGround: Color,
    val btnPrimary: Color,
    val btnPrimaryInk: Color,
)

object ClavisTokens {
    /** Every value below is verbatim from the spec's `:root` block. */
    val Light = ClavisPalette(
        paper = Color(0xFFF4F4F2),
        surface = Color(0xFFFBFBFA),
        surface2 = Color(0xFFEEEEEB),
        ink = Color(0xFF2A2C31),
        ink2 = Color(0xFF6B6E75),
        ink3 = Color(0xFF9C9EA5),
        line = Color(0xFFE6E6E2),
        line2 = Color(0xFFEFEFEC),
        accent = Color(0xFF4A5578),
        cosmosGround = Color(0xFFEAEAE6),
        btnPrimary = Color(0xFF2A2C31),
        btnPrimaryInk = Color(0xFFF7F7F5),
    )

    /**
     * `paper`, `ink`, `accent` and `btnPrimary` are verbatim from the spec. The spec does not list
     * the remaining dark values; the ones here are derived to keep the same contrast steps as the
     * light set and should be replaced if the reference prototype's CSS is imported later.
     */
    val Dark = ClavisPalette(
        paper = Color(0xFF15161A),
        surface = Color(0xFF1B1C21),
        surface2 = Color(0xFF212229),
        ink = Color(0xFFDCDDE1),
        ink2 = Color(0xFF9A9DA6),
        ink3 = Color(0xFF6E7179),
        line = Color(0xFF2A2B32),
        line2 = Color(0xFF23242A),
        accent = Color(0xFF9AA5C8),
        cosmosGround = Color(0xFF1B1C20),
        btnPrimary = Color(0xFFD7D8DC),
        btnPrimaryInk = Color(0xFF1A1B1F),
    )
}
