package io.github.mbaliga.clavis.core

/*
 * Platform service boundaries. `:shared` declares them; `:composeApp`'s androidMain / desktopMain
 * provide the actuals. The UI depends only on these interfaces.
 */

/** Speech output (D-8). Providers are tried in order: Piper voice → system TTS → none. */
interface Speaker {
    /** Whether any provider can currently voice [l]. Gate the Speak button on this — never fail silently. */
    fun canSpeak(l: Lang): Boolean
    /** Speaks [text] in [l]; cancels any utterance in progress first. Rate is 0.85 like the web build. */
    fun speak(l: Lang, text: String)
    fun stop()
    /** Human-readable name of the provider that would be used, for the settings screen. */
    fun providerFor(l: Lang): String?
}

/** Plays a downloaded/cached audio clip (Tatoeba or Commons); never streams without user consent. */
interface AudioPlayer {
    suspend fun play(bytes: ByteArray, mime: String)
    fun stop()
}

/** App directories for state, caches and downloaded packs/voices. */
interface AppDirs {
    val data: String
    val cache: String
    val downloads: String
}

/**
 * Opens a platform picker and returns the chosen text file's name and contents (D-7 user media),
 * or null when the user cancelled. A file over [ImportLimits.MAX_BYTES] or one that is not mostly
 * text is refused with an [ImportRefusedException] carrying the user-facing reason — before it is
 * read whole into memory.
 */
interface FileImporter {
    suspend fun pickText(allowedExtensions: List<String> = listOf("txt", "srt", "lrc")): ImportedFile?
}

data class ImportedFile(val name: String, val text: String)

/** A [FileImporter] refused the chosen file; [message] is copy the UI shows as a toast. */
class ImportRefusedException(message: String) : Exception(message)

/**
 * Bounds on user-imported text: the importers read at most [MAX_BYTES] through a bounded stream,
 * the store keeps at most [MAX_LINES] lines of a text, and a decoded file whose printable share is
 * under [MIN_PRINTABLE] (a binary renamed `.srt`) is refused.
 */
object ImportLimits {
    const val MAX_BYTES: Long = 2L * 1024 * 1024
    const val MAX_LINES: Int = 5_000
    const val MIN_PRINTABLE: Double = 0.9

    /** Share of characters that are text: anything but control characters (tab and line breaks allowed), NUL and U+FFFD. */
    fun printableShare(text: String): Double {
        if (text.isEmpty()) return 1.0
        var ok = 0
        for (ch in text) if (isPrintable(ch)) ok++
        return ok.toDouble() / text.length
    }

    fun isText(text: String): Boolean = !text.contains('\u0000') && printableShare(text) >= MIN_PRINTABLE

    private fun isPrintable(ch: Char): Boolean =
        ch == '\n' || ch == '\r' || ch == '\t' || ch == '\uFEFF' || (!ch.isISOControl() && ch != '\uFFFD')
}

/** Explicit, user-initiated downloads only (D-13). */
interface Downloader {
    suspend fun download(url: String, toPath: String, onProgress: (Long, Long?) -> Unit = { _, _ -> }): Boolean
}
