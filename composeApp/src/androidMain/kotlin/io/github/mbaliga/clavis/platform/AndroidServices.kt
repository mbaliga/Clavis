package io.github.mbaliga.clavis.platform

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import io.github.mbaliga.clavis.core.AppDirs
import io.github.mbaliga.clavis.core.AudioPlayer
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.FileImporter
import io.github.mbaliga.clavis.core.ImportLimits
import io.github.mbaliga.clavis.core.ImportRefusedException
import io.github.mbaliga.clavis.core.ImportedFile
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Speaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale

class AndroidAppDirs(ctx: Context) : AppDirs {
    override val data: String = ctx.filesDir.absolutePath
    override val cache: String = ctx.cacheDir.absolutePath
    override val downloads: String = File(ctx.filesDir, "downloads").absolutePath
}

/**
 * Android `TextToSpeech` (D-8, brief K): locale matching exact → language → none, rate 0.85,
 * cancel-before-speak; `canSpeak` is true only when a voice for the locale is installed.
 */
class AndroidSpeaker(ctx: Context, private val onReady: () -> Unit = {}) : Speaker, LocaleAware {
    override var localeFor: (Lang) -> String? = { null }
    @Volatile private var ready = false
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) onReady() // Speak buttons composed before the engine bound can now appear
    }

    /** Releases the engine binding; the Activity calls this from `onDestroy` (a `TextToSpeech` must be shut down). */
    fun shutdown() {
        ready = false
        runCatching { tts.stop(); tts.shutdown() }
    }

    private fun locale(l: Lang): Locale? {
        if (!ready) return null
        val tag = localeFor(l) ?: l.code
        val exact = Locale.forLanguageTag(tag)
        if (tts.isLanguageAvailable(exact) >= TextToSpeech.LANG_AVAILABLE) return exact
        val lang = Locale(l.code)
        if (tts.isLanguageAvailable(lang) >= TextToSpeech.LANG_AVAILABLE) return lang
        return null
    }

    override fun canSpeak(l: Lang): Boolean = locale(l) != null

    override fun speak(l: Lang, text: String) {
        val loc = locale(l) ?: return
        tts.stop()
        tts.language = loc
        tts.setSpeechRate(0.85f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clavis-${System.nanoTime()}")
    }

    override fun stop() { if (ready) tts.stop() }

    override fun providerFor(l: Lang): String? = locale(l)?.let { "your device (${it.toLanguageTag()})" }
}

/** Plays a clip from bytes through `MediaPlayer` via a temp file in the cache dir. */
class AndroidAudioPlayer(private val ctx: Context) : AudioPlayer {
    private var player: MediaPlayer? = null

    override suspend fun play(bytes: ByteArray, mime: String) {
        stop()
        val ext = when {
            mime.contains("ogg") -> "ogg"
            mime.contains("wav") -> "wav"
            else -> "mp3"
        }
        val f = withContext(Dispatchers.IO) { File.createTempFile("clip", ".$ext", ctx.cacheDir).also { it.writeBytes(bytes) } }
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            setOnCompletionListener { it.release(); f.delete() }
            prepare()
            start()
        }
    }

    override fun stop() {
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
    }
}

/** `ACTION_OPEN_DOCUMENT` through the activity-result API; register in `onCreate` (before STARTED). */
class AndroidFileImporter(private val activity: ComponentActivity) : FileImporter {
    private var pending: CompletableDeferred<Uri?>? = null
    private val launcher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pending?.complete(uri) }

    override suspend fun pickText(allowedExtensions: List<String>): ImportedFile? {
        val d = CompletableDeferred<Uri?>()
        pending = d
        launcher.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream"))
        val uri = d.await() ?: return null
        return withContext(Dispatchers.IO) {
            val cr = activity.contentResolver
            var name = "import.txt"
            var declared: Long? = null
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: name
                    if (!c.isNull(1)) declared = c.getLong(1)
                }
            }
            val ext = name.substringAfterLast('.', "").lowercase()
            // the MIME filter above is advisory: a `.pdf` can arrive here — say so instead of failing silently
            if (allowedExtensions.isNotEmpty() && ext !in allowedExtensions) throw ImportRefusedException(Copy.importWrongKind(allowedExtensions))
            val text = cr.openInputStream(uri)?.use { readBoundedText(it, declared) } ?: return@withContext null
            ImportedFile(name, text)
        }
    }
}

/**
 * Reads at most [ImportLimits.MAX_BYTES] from [input] (the picker's filters are advisory, so a
 * 500 MB file or a binary renamed `.srt` can arrive here) and refuses anything larger or not
 * mostly text with an [ImportRefusedException] the UI shows as a toast.
 */
internal fun readBoundedText(input: InputStream, declaredSize: Long?): String {
    if (declaredSize != null && declaredSize > ImportLimits.MAX_BYTES) throw ImportRefusedException(Copy.IMPORT_TOO_LARGE)
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > ImportLimits.MAX_BYTES) throw ImportRefusedException(Copy.IMPORT_TOO_LARGE)
        out.write(buf, 0, n)
    }
    val text = out.toByteArray().decodeToString()
    if (!ImportLimits.isText(text)) throw ImportRefusedException(Copy.IMPORT_NOT_TEXT)
    return text
}

/** The platform share sheet (opt-in feedback flow, D-11; nothing is sent by the app itself, D-13). */
class AndroidShare(private val ctx: Context) : ShareSheet {
    override fun share(subject: String, text: String): Boolean = try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
