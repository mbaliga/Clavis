package io.github.mbaliga.clavis.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The full Clavis colour token table, transcribed from the v0.10 web build's `:root` block
 * (template-spec §11.1 / template-constants.json `tokens`), with `ink-2`/`ink-3` since darkened
 * (light) or lightened (dark) off the original web-build values to clear WCAG AA's 4.5:1 on both
 * `paper` and `surface2` (usability review: `ink-3` was as low as 2.30:1). Light and dark are two
 * structurally identical sets — not an inversion. `ink-2` is the floor for running text; `ink-3` is
 * quieter (lower contrast, never the sole carrier of meaning) but no longer sub-AA.
 *
 * Read them through [LocalClavisTokens]; the Material 3 mapping in [ClavisTheme] loses this
 * intent (Material has no "quiet secondary ink" role), so components that care use the tokens.
 */
@Immutable
data class ClavisPalette(
    val isDark: Boolean,
    val paper: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val line2: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val ochre: Color,
    val ochreSoft: Color,
    val moss: Color,
    val mossSoft: Color,
    val rust: Color,
    val rustSoft: Color,
    val unk: Color,
    val unkLine: Color,
    val fluid: Color,
    val fluidHi: Color,
    val pool: Color,
    val cosmosGround: Color,
    val cosmosGrey: Color,
    val cosmosLabel: Color,
    val cosmosLabelBg: Color,
    val cosmosRing: Color,
    val cosmosCrest: Color,
    val cosmosDustLite: Color,
    val relBor: Color,
    val relKin: Color,
    val hudBg: Color,
    val hudInk: Color,
    val hudOnBg: Color,
    val hudOnInk: Color,
    val orbInk: Color,
    val btnPrimary: Color,
    val btnPrimaryInk: Color,
    val dockBg: Color,
    /** `--shadow`: `0 1px 2px <ambient>, 0 12px 32px -20px <key>`. */
    val shadowAmbient: Color,
    val shadowKey: Color,
)

val LocalClavisTokens = staticCompositionLocalOf { ClavisTokens.Light }

object ClavisTokens {
    val Light = ClavisPalette(
        isDark = false,
        paper = Color(0xFFF4F4F2),
        surface = Color(0xFFFBFBFA),
        surface2 = Color(0xFFEEEEEB),
        ink = Color(0xFF2A2C31),
        // usability review: the old ink2/ink3 (#6B6E75 on paper 4.64:1 / on surface2 4.39:1; #9C9EA5 on
        // paper 2.43:1 / on surface2 2.30:1) failed WCAG AA for text that carries information (eyebrows,
        // the cloze right/wrong line, romanisations). Darkened just enough to clear 4.5:1 on both
        // paper and surface2 while keeping ink3 visibly lighter than ink2 (see ClavisTokens KDoc below).
        ink2 = Color(0xFF5E616A),
        ink3 = Color(0xFF6A6C73),
        line = Color(0xFFE6E6E2),
        line2 = Color(0xFFEFEFEC),
        accent = Color(0xFF4A5578),
        accentInk = Color(0xFF3D4763),
        accentSoft = Color(0xFFE7E9F0),
        ochre = Color(0xFF96712F),
        ochreSoft = Color(0xFFF0EADC),
        moss = Color(0xFF4C7057),
        mossSoft = Color(0xFFE4EBE5),
        rust = Color(0xFF9A5648),
        rustSoft = Color(0xFFF0E2DE),
        unk = Color(0xFFF4ECD6),
        unkLine = Color(0xFF96712F),
        fluid = Color(0xFF2A2C31),
        fluidHi = Color(0x4DFFFFFF),
        pool = Color(0x124A5578),
        cosmosGround = Color(0xFFEAEAE6),
        cosmosGrey = Color(0xFFD2D2CC),
        cosmosLabel = Color(0xFF54565C),
        cosmosLabelBg = Color(0xB8FBFBFA),
        cosmosRing = Color(0xFF54565C),
        cosmosCrest = Color(0xFF7E8089),
        cosmosDustLite = Color(0xFFCFCFC8),
        relBor = Color(0xFFB08A3E),
        relKin = Color(0xFF7C88B5),
        hudBg = Color(0xD1FBFBFA),
        hudInk = Color(0xFF54565C),
        hudOnBg = Color(0xFF2A2C31),
        hudOnInk = Color(0xFFF4F4F2),
        orbInk = Color(0xFF2A2C31),
        btnPrimary = Color(0xFF2A2C31),
        btnPrimaryInk = Color(0xFFF7F7F5),
        dockBg = Color(0xE6FBFBFA),
        shadowAmbient = Color(0x0826282E),
        shadowKey = Color(0x2926282E),
    )

    val Dark = ClavisPalette(
        isDark = true,
        paper = Color(0xFF15161A),
        surface = Color(0xFF1B1C21),
        surface2 = Color(0xFF222328),
        ink = Color(0xFFDCDDE1),
        ink2 = Color(0xFF9B9DA5),
        // usability review: the old dark ink3 (#6E7077, 3.66:1 on paper / 3.17:1 on surface2) failed WCAG
        // AA. Lightened just enough to clear 4.5:1 on both while staying darker (lower contrast) than ink2.
        ink3 = Color(0xFF888A91),
        line = Color(0xFF26272D),
        line2 = Color(0xFF202126),
        accent = Color(0xFF9AA5C8),
        accentInk = Color(0xFFB3BDDA),
        accentSoft = Color(0xFF262A38),
        ochre = Color(0xFFC3A469),
        ochreSoft = Color(0xFF2C2820),
        moss = Color(0xFF8FAE97),
        mossSoft = Color(0xFF20261F),
        rust = Color(0xFFC08A7D),
        rustSoft = Color(0xFF2C211E),
        unk = Color(0xFF2C2820),
        unkLine = Color(0xFFC3A469),
        fluid = Color(0xFF0C0D11),
        fluidHi = Color(0x38BEC8EB),
        pool = Color(0x129AA5C8),
        cosmosGround = Color(0xFF1B1C21),
        cosmosGrey = Color(0xFF34353C),
        cosmosLabel = Color(0xFFB6B8BF),
        cosmosLabelBg = Color(0xA815161A),
        cosmosRing = Color(0xFFC9CBD2),
        cosmosCrest = Color(0xFFEDEEF2),
        cosmosDustLite = Color(0xFF4A4B52),
        relBor = Color(0xFFC9A96A),
        relKin = Color(0xFF98A3C6),
        hudBg = Color(0xC71B1C21),
        hudInk = Color(0xFFB6B8BF),
        hudOnBg = Color(0xFFDCDDE1),
        hudOnInk = Color(0xFF1B1C21),
        orbInk = Color(0xFFDCDDE1),
        btnPrimary = Color(0xFFD7D8DC),
        btnPrimaryInk = Color(0xFF1B1C21),
        dockBg = Color(0xE61B1C21),
        shadowAmbient = Color(0x40000000),
        shadowKey = Color(0x80000000),
    )

    /** Hard-coded (non-token) colours the template uses (template-constants `tokens.hardcoded`). */
    val balloonInk = Color(0xFF20242C)
    val balloonPlate = Color(0xE6FFFFFF)
    val markerRing = Color(0x99161E1E)
    val pageNumBg = Color(0xB3161E1E)
    val fallbackDot = Color(0xFF8B8F9C)

    /** `COSMOS_COL` — the saturated lane colours used for the pool aura, the morph and the Field. */
    val cosmosCol: Map<String, Color> = mapOf(
        "es" to Color(0xFFFF7043), "fr" to Color(0xFFB388FF), "it" to Color(0xFF5FD97F), "de" to Color(0xFF64B0FF),
        "nl" to Color(0xFFFFC642), "ja" to Color(0xFFFF5F9E), "ko" to Color(0xFF2FDFCE), "zh" to Color(0xFFCE7BFF),
        "ar" to Color(0xFF4FC3F7),
    )

    /** `FAM_COL`. */
    val familyCol: Map<String, Color> = mapOf(
        "ROMANCE" to Color(0xFFFF8A5C), "GERMANIC" to Color(0xFF7FB6FF), "CJK" to Color(0xFFFF6FB0), "SEMITIC" to Color(0xFF4FC3F7),
    )

    /** Paths kind colours `KC`. */
    val kindCol: Map<String, Color> = mapOf(
        "TEST" to Color(0xFFDCAA47), "DUE" to Color(0xFF93A4F5), "NEW" to Color(0xFF86C197), "READ" to Color(0xFFB0B6C6),
        "GAME" to Color(0xFFE6907F), "LETTERS" to Color(0xFFDCAA47),
    )

    /** Lane colour from the registry's `0xAARRGGBB` value (`LANGS[l].col`). */
    fun laneColour(argb: Long): Color = Color((argb and 0xFFFFFFFFL).toInt())
}
