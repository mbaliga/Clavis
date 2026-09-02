package io.github.mbaliga.clavis.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.mbaliga.clavis.platform.scriptFallbackFonts
import io.github.mbaliga.clavis.resources.Res
import io.github.mbaliga.clavis.resources.hind_bold
import io.github.mbaliga.clavis.resources.hind_medium
import io.github.mbaliga.clavis.resources.hind_regular
import io.github.mbaliga.clavis.resources.hind_semibold
import io.github.mbaliga.clavis.resources.ibmplexmono_medium
import io.github.mbaliga.clavis.resources.ibmplexmono_regular
import io.github.mbaliga.clavis.resources.rozhaone_regular
import org.jetbrains.compose.resources.Font

/**
 * The three faces of D-9 — Hind (body), Rozha One (display), IBM Plex Mono (readings, eyebrows,
 * small caps) — each followed by the per-script fallbacks the platform provides
 * ([scriptFallbackFonts]: Noto Sans Arabic / Devanagari / Hebrew / JP / KR / SC bundled on
 * desktop; nothing on Android, whose system fonts cover those scripts).
 */
@Immutable
class ClavisFontFamilies(val body: FontFamily, val display: FontFamily, val mono: FontFamily)

val LocalClavisFonts = staticCompositionLocalOf<ClavisFontFamilies> { error("ClavisTheme not applied") }

@Composable
fun rememberClavisFontFamilies(): ClavisFontFamilies {
    val fallbacks: List<Font> = scriptFallbackFonts()
    val body = FontFamily(
        listOf(
            Font(Res.font.hind_regular, weight = FontWeight.Normal),
            Font(Res.font.hind_medium, weight = FontWeight.Medium),
            Font(Res.font.hind_semibold, weight = FontWeight.SemiBold),
            Font(Res.font.hind_bold, weight = FontWeight.Bold),
        ) + fallbacks,
    )
    val display = FontFamily(listOf(Font(Res.font.rozhaone_regular, weight = FontWeight.Normal)) + fallbacks)
    val mono = FontFamily(
        listOf(
            Font(Res.font.ibmplexmono_regular, weight = FontWeight.Normal),
            Font(Res.font.ibmplexmono_medium, weight = FontWeight.Medium),
        ) + fallbacks,
    )
    return ClavisFontFamilies(body, display, mono)
}

/**
 * Text styles from template-spec §11.2, in sp (1 rem = 17 px). Names follow the CSS classes so a
 * screen can be checked against the spec line by line.
 */
@Immutable
class ClavisTypography(f: ClavisFontFamilies) {
    val body = TextStyle(fontFamily = f.body, fontSize = 17.sp, lineHeight = 25.5.sp, fontWeight = FontWeight.Normal)
    val small = TextStyle(fontFamily = f.body, fontSize = 15.sp, lineHeight = 22.sp)
    val tiny = TextStyle(fontFamily = f.body, fontSize = 13.sp, lineHeight = 18.sp)
    val h2 = TextStyle(fontFamily = f.display, fontSize = 27.2.sp, lineHeight = 31.3.sp, fontWeight = FontWeight.Normal)
    val h3 = TextStyle(fontFamily = f.body, fontSize = 17.9.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val wordmark = TextStyle(fontFamily = f.display, fontSize = 23.8.sp, lineHeight = 28.sp)
    val eyebrow = TextStyle(fontFamily = f.mono, fontSize = 12.2.sp, letterSpacing = 0.09.em, lineHeight = 16.sp)
    val viewName = TextStyle(fontFamily = f.mono, fontSize = 12.2.sp, letterSpacing = 0.12.em, lineHeight = 16.sp)
    val chip = TextStyle(fontFamily = f.mono, fontSize = 11.9.sp, letterSpacing = 0.05.em, lineHeight = 14.sp)
    val th = TextStyle(fontFamily = f.mono, fontSize = 11.6.sp, letterSpacing = 0.06.em, lineHeight = 14.sp)
    val mono = TextStyle(fontFamily = f.mono, fontSize = 15.sp, lineHeight = 20.sp)
    val stat = TextStyle(fontFamily = f.display, fontSize = 25.5.sp, lineHeight = 28.sp)
    val caption = TextStyle(fontFamily = f.body, fontSize = 22.1.sp, lineHeight = 42.sp)
    val captionWide = TextStyle(fontFamily = f.body, fontSize = 24.65.sp, lineHeight = 46.sp)
    val choice = TextStyle(fontFamily = f.body, fontSize = 19.55.sp, lineHeight = 24.sp)
    val choiceReading = TextStyle(fontFamily = f.mono, fontSize = 12.2.sp, lineHeight = 14.sp)
    val testWord = TextStyle(fontFamily = f.display, fontSize = 37.4.sp, lineHeight = 44.sp)
    val testWordPlain = TextStyle(fontFamily = f.body, fontSize = 37.4.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold)
    val obH2 = TextStyle(fontFamily = f.display, fontSize = 34.sp, lineHeight = 39.sp)
    val obGlyphs = TextStyle(fontFamily = f.mono, fontSize = 34.sp, lineHeight = 40.sp)
    val orbLabel = TextStyle(fontFamily = f.display, fontSize = 23.sp, lineHeight = 26.sp)
    val button = TextStyle(fontFamily = f.body, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val quietButton = TextStyle(fontFamily = f.body, fontSize = 16.sp, lineHeight = 20.sp)
    val dockLabel = TextStyle(fontFamily = f.body, fontSize = 11.9.sp, lineHeight = 14.sp)
    val toast = TextStyle(fontFamily = f.body, fontSize = 15.3.sp, lineHeight = 20.sp)
    val balloon = TextStyle(fontFamily = f.body, fontWeight = FontWeight.Bold, lineHeight = 1.05.em)

    /** The reading sub-line under a token: `.62em` of the caption. */
    val readingEm: TextUnit = 0.62.em
}

val LocalClavisType = staticCompositionLocalOf<ClavisTypography> { error("ClavisTheme not applied") }
