package io.github.mbaliga.clavis.platform

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Application context holder, set by [io.github.mbaliga.clavis.ClavisApplication]. */
object AndroidApp {
    lateinit var context: Context
    val ready: Boolean get() = ::context.isInitialized
}

/** Android's system fonts already cover CJK, Arabic and Devanagari (D-9): nothing bundled. */
@Composable
actual fun scriptFallbackFonts(): List<Font> = emptyList()

/** `Settings.Global.ANIMATOR_DURATION_SCALE == 0` is Android's "remove animations". */
actual fun platformReducedMotion(): Boolean = try {
    AndroidApp.ready && Settings.Global.getFloat(AndroidApp.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (e: Exception) {
    false
}

private val singleWriter: CoroutineDispatcher by lazy { Dispatchers.IO.limitedParallelism(1) }

actual fun ioDispatcher(): CoroutineDispatcher = singleWriter

actual fun <T> runBlockingCompat(block: suspend () -> T): T = runBlocking { block() }
