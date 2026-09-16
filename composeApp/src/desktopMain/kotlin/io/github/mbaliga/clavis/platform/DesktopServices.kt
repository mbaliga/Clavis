package io.github.mbaliga.clavis.platform

import io.github.mbaliga.clavis.core.AppDirs
import io.github.mbaliga.clavis.core.AudioPlayer
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.FileImporter
import io.github.mbaliga.clavis.core.ImportLimits
import io.github.mbaliga.clavis.core.ImportRefusedException
import io.github.mbaliga.clavis.core.ImportedFile
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

private val os: String = System.getProperty("os.name").lowercase()
private val isMac get() = os.contains("mac")
private val isWindows get() = os.contains("win")

/** `~/.local/share/Clavis` (Linux, or `$XDG_DATA_HOME`), `~/Library/Application Support/Clavis` (macOS), `%APPDATA%/Clavis` (Windows). */
class DesktopAppDirs(root: File? = null) : AppDirs {
    private val home = System.getProperty("user.home")
    private val base: File = root ?: when {
        isMac -> File(home, "Library/Application Support/Clavis")
        isWindows -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Clavis")
        else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "Clavis")
    }
    override val data: String = base.absolutePath
    override val cache: String = when {
        isMac -> File(home, "Library/Caches/Clavis").absolutePath
        isWindows -> File(System.getenv("LOCALAPPDATA") ?: base.absolutePath, "Clavis/cache").absolutePath
        else -> File(System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.cache", "Clavis").absolutePath
    }
    override val downloads: String = File(base, "downloads").absolutePath
}

internal fun onPath(bin: String): File? {
    val exts = if (isWindows) listOf(".exe", ".cmd", ".bat", "") else listOf("")
    return (System.getenv("PATH") ?: "").split(File.pathSeparator).asSequence()
        .flatMap { dir -> exts.asSequence().map { File(dir, bin + it) } }
        .firstOrNull { it.isFile && it.canExecute() }
}

/**
 * D-8 provider chain on desktop: a Piper voice (only if a configured voice directory exists) →
 * the system's own TTS (macOS `say`, Windows `System.Speech` via PowerShell, Linux `spd-say` /
 * `espeak-ng` when on PATH) → none. Each provider is a separate process, so a failing engine can
 * never take the app down. No download UI yet (D-13).
 */
class DesktopSpeaker(private val dirs: AppDirs, private val audio: AudioPlayer = DesktopAudioPlayer()) : Speaker, LocaleAware {
    override var localeFor: (Lang) -> String? = { null }
    private val piper = PiperSpeaker(dirs, audio)
    private val system = SystemSpeaker { localeFor(it) }

    private fun provider(l: Lang): Speaker? = when {
        piper.canSpeak(l) -> piper
        system.canSpeak(l) -> system
        else -> null
    }

    override fun canSpeak(l: Lang): Boolean = provider(l) != null
    override fun speak(l: Lang, text: String) { stop(); provider(l)?.speak(l, text) }
    override fun stop() { piper.stop(); system.stop() }
    override fun providerFor(l: Lang): String? = provider(l)?.providerFor(l)
}

/** Piper voices live at `<data>/voices/<iso>/<name>.onnx` (+ `.onnx.json`); the binary is `$CLAVIS_PIPER`, `<data>/piper/piper`, or `piper` on PATH. */
class PiperSpeaker(private val dirs: AppDirs, private val audio: AudioPlayer) : Speaker {
    private fun binary(): File? =
        System.getenv("CLAVIS_PIPER")?.let { File(it) }?.takeIf { it.isFile && it.canExecute() }
            ?: File(dirs.data, "piper/piper").takeIf { it.isFile && it.canExecute() }
            ?: onPath("piper")

    private fun voice(l: Lang): File? = File(dirs.data, "voices/${l.code}").listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".onnx") }

    override fun canSpeak(l: Lang): Boolean = binary() != null && voice(l) != null

    override fun speak(l: Lang, text: String) {
        val bin = binary() ?: return
        val model = voice(l) ?: return
        Thread {
            runCatching {
                val out = File.createTempFile("clavis-piper", ".wav")
                // The text goes through stdin (never argv); merged stdout is discarded so a chatty voice cannot
                // fill the pipe and block us, and a hung engine is killed after PIPER_TIMEOUT_S.
                val proc = ProcessBuilder(bin.absolutePath, "--model", model.absolutePath, "--output_file", out.absolutePath, "--length_scale", "1.18")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                proc.outputStream.use { it.write(text.toByteArray()); it.write('\n'.code) }
                if (!proc.waitFor(PIPER_TIMEOUT_S, TimeUnit.SECONDS)) { proc.destroyForcibly(); out.delete(); return@runCatching }
                val bytes = out.readBytes()
                out.delete()
                kotlinx.coroutines.runBlocking { audio.play(bytes, "audio/wav") }
            }
        }.apply { isDaemon = true }.start()
    }

    override fun stop() { audio.stop() }
    override fun providerFor(l: Lang): String? = voice(l)?.let { "Piper voice ${it.nameWithoutExtension}" }

    companion object { const val PIPER_TIMEOUT_S = 30L }
}

/**
 * The operating system's own voices, invoked as a process. The utterance is never parsed as an
 * option or by a shell: `say`, `spd-say` and `espeak-ng` get it after `--` (comic lines such as
 * "- Non !" start with a hyphen), and PowerShell reads it from an environment variable.
 */
class SystemSpeaker(private val localeFor: (Lang) -> String?) : Speaker {
    @Volatile private var current: Process? = null
    private val macVoices: Map<String, String> by lazy { listMacVoices() }
    private val linuxBin: File? by lazy { onPath("spd-say") ?: onPath("espeak-ng") ?: onPath("espeak") }
    private val powershell: File? by lazy { onPath("powershell") ?: onPath("pwsh") }

    companion object {
        const val ENV_TEXT = "CLAVIS_TTS_TEXT"
        const val ENV_CULTURE = "CLAVIS_TTS_CULTURE"
        const val ENV_LANG = "CLAVIS_TTS_LANG"

        /** A constant script: the text and the culture arrive through the environment, so no quoting of user text is ever needed. */
        internal val WINDOWS_SCRIPT = listOf(
            "Add-Type -AssemblyName System.Speech",
            "\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
            "\$c = \$env:$ENV_CULTURE; \$l = \$env:$ENV_LANG",
            "\$v = \$s.GetInstalledVoices() | Where-Object { \$_.VoiceInfo.Culture.Name -eq \$c -or \$_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq \$l } | Select-Object -First 1",
            "if (\$v) { \$s.SelectVoice(\$v.VoiceInfo.Name) }",
            "\$s.Rate = -2",
            "\$s.Speak(\$env:$ENV_TEXT)",
        ).joinToString("; ")
    }

    private fun tag(l: Lang) = (localeFor(l) ?: l.code).replace('_', '-')

    private fun macVoice(l: Lang): String? {
        val t = tag(l).replace('-', '_')
        return macVoices[t] ?: macVoices.entries.firstOrNull { it.key.startsWith(l.code + "_") }?.value
    }

    override fun canSpeak(l: Lang): Boolean = when {
        isMac -> macVoice(l) != null
        isWindows -> powershell != null
        else -> linuxBin != null
    }

    override fun speak(l: Lang, text: String) {
        stop()
        val cmd: List<String> = when {
            isMac -> listOf("say", "-v", macVoice(l) ?: return, "-r", "150", "--", text)
            isWindows -> {
                val ps = powershell ?: return
                listOf(ps.absolutePath, "-NoProfile", "-NonInteractive", "-Command", WINDOWS_SCRIPT)
            }
            else -> {
                val bin = linuxBin ?: return
                if (bin.name.startsWith("spd-say")) listOf(bin.absolutePath, "-l", l.code, "-r", "-15", "-w", "--", text)
                else listOf(bin.absolutePath, "-v", l.code, "-s", "150", "--", text)
            }
        }
        current = runCatching {
            ProcessBuilder(cmd).apply {
                environment()[ENV_TEXT] = text
                environment()[ENV_CULTURE] = tag(l)
                environment()[ENV_LANG] = l.code
                redirectErrorStream(true)
                redirectOutput(ProcessBuilder.Redirect.DISCARD)
            }.start()
        }.getOrNull()
    }

    override fun stop() { current?.destroy(); current = null }

    override fun providerFor(l: Lang): String? = when {
        !canSpeak(l) -> null
        isMac -> "macOS voice ${macVoice(l)}"
        isWindows -> "Windows voice"
        else -> "${linuxBin?.name} (system)"
    }

    private fun listMacVoices(): Map<String, String> {
        if (!isMac) return emptyMap()
        return runCatching {
            val p = ProcessBuilder("say", "-v", "?").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            val m = LinkedHashMap<String, String>()
            for (line in out.lines()) {
                val mm = Regex("^(.+?)\\s{2,}([a-z]{2,3}[_-][A-Za-z0-9]+)\\s").find(line) ?: continue
                val loc = mm.groupValues[2].replace('-', '_')
                if (loc !in m) m[loc] = mm.groupValues[1].trim()
            }
            m
        }.getOrDefault(emptyMap())
    }
}

/**
 * javax.sound with explicit decoders: WAV (and any `AudioSystem` PCM container) natively, MP3 via
 * JLayer/mp3spi and OGG Vorbis via JOrbis/vorbisspi — both pure Java, LGPL 2.1 (THIRD_PARTY_NOTICES
 * "Desktop audio"). The container is sniffed from the bytes (ID3 / MPEG sync, `OggS`, `RIFF`) with
 * the MIME as a fallback; decoded PCM streams through a `SourceDataLine` so a clip of unknown
 * frame length (every decoded stream) plays without buffering it whole into a `Clip`.
 */
class DesktopAudioPlayer : AudioPlayer {
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var generation = 0

    companion object {
        val FORMATS: Set<String> = setOf("wav", "mp3", "ogg")

        internal fun sniff(bytes: ByteArray, mime: String): String {
            if (bytes.size >= 4) {
                if (bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() && bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()) return "ogg"
                if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) return "wav"
                if (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) return "mp3"
                if ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0) return "mp3"
            }
            val m = mime.lowercase()
            return when {
                m.contains("ogg") || m.contains("vorbis") -> "ogg"
                m.contains("mpeg") || m.contains("mp3") -> "mp3"
                else -> "wav"
            }
        }

        /** Decodes [bytes] to 16-bit signed PCM; throws when no decoder applies. */
        internal fun decode(bytes: ByteArray, mime: String): AudioInputStream {
            val kind = sniff(bytes, mime)
            val input = BufferedInputStream(ByteArrayInputStream(bytes))
            val raw: AudioInputStream = when (kind) {
                "mp3" -> javazoom.spi.mpeg.sampled.file.MpegAudioFileReader().getAudioInputStream(input)
                "ogg" -> javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader().getAudioInputStream(input)
                else -> AudioSystem.getAudioInputStream(input)
            }
            val base = raw.format
            if (base.encoding == AudioFormat.Encoding.PCM_SIGNED && base.sampleSizeInBits == 16) return raw
            val rate = if (base.sampleRate > 0) base.sampleRate else 44_100f
            val ch = if (base.channels > 0) base.channels else 2
            val pcm = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, ch, ch * 2, rate, false)
            return when (kind) {
                "mp3" -> javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider().getAudioInputStream(pcm, raw)
                "ogg" -> javazoom.spi.vorbis.sampled.convert.VorbisFormatConversionProvider().getAudioInputStream(pcm, raw)
                else -> AudioSystem.getAudioInputStream(pcm, raw)
            }
        }
    }

    override suspend fun play(bytes: ByteArray, mime: String) {
        stop()
        val gen = ++generation
        withContext(Dispatchers.IO) {
            runCatching {
                decode(bytes, mime).use { pcm ->
                    val out = AudioSystem.getSourceDataLine(pcm.format)
                    out.open(pcm.format)
                    line = out
                    out.start()
                    val buf = ByteArray(8 * 1024)
                    while (gen == generation) {
                        val n = pcm.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    if (gen == generation) out.drain()
                    out.close()
                    if (line === out) line = null
                }
            }
        }
    }

    override fun stop() {
        generation++
        line?.let { runCatching { it.stop(); it.flush(); it.close() } }
        line = null
    }
}

/** AWT `FileDialog` on the Swing thread. The file is size-checked before it is read, then read through a bounded stream. */
class DesktopFileImporter : FileImporter {
    override suspend fun pickText(allowedExtensions: List<String>): ImportedFile? = withContext(Dispatchers.IO) {
        val dlg = FileDialog(null as Frame?, "Open a text", FileDialog.LOAD)
        dlg.setFilenameFilter { _, name -> allowedExtensions.isEmpty() || allowedExtensions.any { name.lowercase().endsWith(".$it") } }
        dlg.isVisible = true
        val dir = dlg.directory ?: return@withContext null
        val name = dlg.file ?: return@withContext null
        val f = File(dir, name)
        if (!f.isFile) return@withContext null
        // the dialog's filename filter is advisory on some platforms (Windows ignores it): refuse other kinds out loud
        val ext = name.substringAfterLast('.', "").lowercase()
        if (allowedExtensions.isNotEmpty() && ext !in allowedExtensions) throw ImportRefusedException(Copy.importWrongKind(allowedExtensions))
        if (f.length() > ImportLimits.MAX_BYTES) throw ImportRefusedException(Copy.IMPORT_TOO_LARGE)
        ImportedFile(name, f.inputStream().use { readBoundedText(it) })
    }
}

/**
 * Reads at most [ImportLimits.MAX_BYTES] from [input] and refuses a longer stream or one that is
 * not mostly text (a binary renamed `.srt`) with an [ImportRefusedException] the UI toasts.
 */
internal fun readBoundedText(input: InputStream): String {
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

/** Desktops have no share sheet: the draft goes to the clipboard and the UI says so. */
class DesktopShare : ShareSheet {
    override fun share(subject: String, text: String): Boolean {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection("$subject\n\n$text"), null) }
        return false
    }
}

fun desktopServices(root: File? = null): PlatformServices {
    val dirs = DesktopAppDirs(root)
    val audio = DesktopAudioPlayer()
    return PlatformServices(
        dirs = dirs,
        speaker = DesktopSpeaker(dirs, audio),
        audio = audio,
        importer = DesktopFileImporter(),
        downloader = HttpDownloader(),
        share = DesktopShare(),
        reducedMotion = ::platformReducedMotion,
        audioFormats = DesktopAudioPlayer.FORMATS,
        canDownload = true,
    )
}
