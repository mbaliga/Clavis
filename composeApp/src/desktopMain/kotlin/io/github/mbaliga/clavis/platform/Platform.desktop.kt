package io.github.mbaliga.clavis.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import io.github.mbaliga.clavis.resources.Res
import io.github.mbaliga.clavis.resources.notosansarabic
import io.github.mbaliga.clavis.resources.notosansdevanagari
import io.github.mbaliga.clavis.resources.notosanshebrew
import io.github.mbaliga.clavis.resources.notosansjp
import io.github.mbaliga.clavis.resources.notosanskr
import io.github.mbaliga.clavis.resources.notosanssc
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.Font

/**
 * D-9: the desktop JVM has no guarantee of CJK/Arabic/Devanagari system fonts, so the Noto faces
 * are bundled (desktop-only resource set) and appended to every family as glyph fallbacks.
 */
@Composable
actual fun scriptFallbackFonts(): List<Font> = listOf(
    Font(Res.font.notosansarabic),
    Font(Res.font.notosansdevanagari),
    Font(Res.font.notosanshebrew),
    Font(Res.font.notosansjp),
    Font(Res.font.notosanskr),
    Font(Res.font.notosanssc),
)

/** The JVM exposes no portable reduced-motion flag; the in-app switch is the control (product-spec §8.6). */
actual fun platformReducedMotion(): Boolean = false

private val singleWriter: CoroutineDispatcher by lazy { Dispatchers.IO.limitedParallelism(1) }

actual fun ioDispatcher(): CoroutineDispatcher = singleWriter

actual fun <T> runBlockingCompat(block: suspend () -> T): T = runBlocking { block() }
