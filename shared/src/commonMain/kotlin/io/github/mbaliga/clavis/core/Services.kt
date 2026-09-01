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

/** Opens a platform picker and returns the chosen text file's name and contents (D-7 user media). */
interface FileImporter {
    suspend fun pickText(allowedExtensions: List<String> = listOf("txt", "srt", "lrc")): ImportedFile?
}

data class ImportedFile(val name: String, val text: String)

/** Explicit, user-initiated downloads only (D-13). */
interface Downloader {
    suspend fun download(url: String, toPath: String, onProgress: (Long, Long?) -> Unit = { _, _ -> }): Boolean
}
