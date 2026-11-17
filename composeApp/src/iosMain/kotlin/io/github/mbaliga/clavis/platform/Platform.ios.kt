package io.github.mbaliga.clavis.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * D-9 working assumption: iOS supplies CJK, Arabic, Devanagari and Hebrew glyphs through its own font
 * cascade, so no Noto face is bundled. NOT YET VERIFIED: docs/DEVICE_CHECKLIST_IOS.md has the per-script
 * screenshot pass that turns this into a decision; if a script renders as tofu, bundle that face here.
 */
@Composable
actual fun scriptFallbackFonts(): List<Font> = emptyList()

/** Settings > Accessibility > Motion > Reduce Motion, read on each call so a change is honoured. */
actual fun platformReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()

@OptIn(ExperimentalCoroutinesApi::class)
private val singleWriter: CoroutineDispatcher by lazy { Dispatchers.IO.limitedParallelism(1) }

actual fun ioDispatcher(): CoroutineDispatcher = singleWriter

/** Never call this while waiting on the main queue. */
actual fun <T> runBlockingCompat(block: suspend () -> T): T = runBlocking { block() }
