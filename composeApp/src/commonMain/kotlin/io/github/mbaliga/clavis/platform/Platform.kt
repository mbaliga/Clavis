package io.github.mbaliga.clavis.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import io.github.mbaliga.clavis.core.AppDirs
import io.github.mbaliga.clavis.core.AudioPlayer
import io.github.mbaliga.clavis.core.Downloader
import io.github.mbaliga.clavis.core.FileImporter
import io.github.mbaliga.clavis.core.ImportedFile
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Speaker
import io.github.mbaliga.clavis.core.platformName

/**
 * Everything the UI needs from the platform, bundled so `ClavisApp` takes one argument. Each
 * entry point (MainActivity, desktop `main`, the tests) builds its own bundle; the domain
 * interfaces come from `:shared` (`Services.kt`, D-3).
 */
class PlatformServices(
    val dirs: AppDirs,
    val speaker: Speaker = NoSpeaker,
    val audio: AudioPlayer = NoAudio,
    val importer: FileImporter = NoImporter,
    val downloader: Downloader = RefusingDownloader,
    val share: ShareSheet = NoShare,
    /** The platform's reduced-motion flag, read on each call so a settings change is honoured. */
    val reducedMotion: () -> Boolean = { false },
    val name: String = platformName(),
    /** Container formats the [audio] player can decode ("wav", "mp3", "ogg"); Play is hidden for the rest. */
    val audioFormats: Set<String> = setOf("wav"),
    /** Whether the [downloader] can actually fetch (false = every Play stays a "no download in this build"). */
    val canDownload: Boolean = false,
    /** Cap on Cosmos dust motes (phones draw fewer). */
    val dustCap: Int = 7200,
)

/** Opens the platform share sheet with a user-readable draft (D-11 feedback flow; D-13: no network). */
fun interface ShareSheet {
    /** Returns true when a share sheet was shown; false when the platform has none (the caller then copies the draft). */
    fun share(subject: String, text: String): Boolean
}

object NoShare : ShareSheet {
    override fun share(subject: String, text: String): Boolean = false
}

/** A speaker that needs the registry's TTS locale per language; the store binds it at boot. */
interface LocaleAware {
    var localeFor: (Lang) -> String?
}

/** The "no provider" end of the D-8 chain: the Speak button is simply not rendered. */
object NoSpeaker : Speaker {
    override fun canSpeak(l: Lang): Boolean = false
    override fun speak(l: Lang, text: String) {}
    override fun stop() {}
    override fun providerFor(l: Lang): String? = null
}

object NoAudio : AudioPlayer {
    override suspend fun play(bytes: ByteArray, mime: String) {}
    override fun stop() {}
}

object NoImporter : FileImporter {
    override suspend fun pickText(allowedExtensions: List<String>): ImportedFile? = null
}

/**
 * D-13: the app makes no network request unless the user explicitly starts a download. Until a
 * download UI with consent exists, every request is refused with a clear message.
 */
object RefusingDownloader : Downloader {
    const val MESSAGE = "Downloads are not enabled in this build — Clavis makes no network requests unless you start one."
    override suspend fun download(url: String, toPath: String, onProgress: (Long, Long?) -> Unit): Boolean = false
}

/** Per-script fallback faces appended to every font family (D-9): Noto on desktop, none on Android. */
@Composable
expect fun scriptFallbackFonts(): List<Font>

/** The platform's reduced-motion preference (Android: animator duration scale 0; desktop: false). */
expect fun platformReducedMotion(): Boolean
