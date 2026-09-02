package io.github.mbaliga.clavis.platform

import io.github.mbaliga.clavis.core.AppDirs
import io.github.mbaliga.clavis.core.AudioPlayer
import io.github.mbaliga.clavis.core.FileImporter
import io.github.mbaliga.clavis.core.ImportedFile
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

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
                val proc = ProcessBuilder(bin.absolutePath, "--model", model.absolutePath, "--output_file", out.absolutePath, "--length_scale", "1.18")
                    .redirectErrorStream(true).start()
                proc.outputStream.use { it.write(text.toByteArray()); it.write('\n'.code) }
                proc.waitFor()
                val bytes = out.readBytes()
                out.delete()
                kotlinx.coroutines.runBlocking { audio.play(bytes, "audio/wav") }
            }
        }.apply { isDaemon = true }.start()
    }

    override fun stop() { audio.stop() }
    override fun providerFor(l: Lang): String? = voice(l)?.let { "Piper voice ${it.nameWithoutExtension}" }
}

/** The operating system's own voices, invoked as a process. */
class SystemSpeaker(private val localeFor: (Lang) -> String?) : Speaker {
    @Volatile private var current: Process? = null
    private val macVoices: Map<String, String> by lazy { listMacVoices() }
    private val linuxBin: File? by lazy { onPath("spd-say") ?: onPath("espeak-ng") ?: onPath("espeak") }
    private val powershell: File? by lazy { onPath("powershell") ?: onPath("pwsh") }

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
            isMac -> listOf("say", "-v", macVoice(l) ?: return, "-r", "150", text)
            isWindows -> {
                val ps = powershell ?: return
                val safe = text.replace("'", "''")
                val culture = tag(l)
                listOf(
                    ps.absolutePath, "-NoProfile", "-Command",
                    "Add-Type -AssemblyName System.Speech; \$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                        "\$v = \$s.GetInstalledVoices() | Where-Object { \$_.VoiceInfo.Culture.Name -eq '$culture' -or \$_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq '${l.code}' } | Select-Object -First 1; " +
                        "if (\$v) { \$s.SelectVoice(\$v.VoiceInfo.Name) }; \$s.Rate = -2; \$s.Speak('$safe')",
                )
            }
            else -> {
                val bin = linuxBin ?: return
                if (bin.name.startsWith("spd-say")) listOf(bin.absolutePath, "-l", l.code, "-r", "-15", "-w", text)
                else listOf(bin.absolutePath, "-v", l.code, "-s", "150", text)
            }
        }
        current = runCatching { ProcessBuilder(cmd).redirectErrorStream(true).start() }.getOrNull()
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

/** javax.sound: WAV (and other `AudioSystem`-supported PCM containers). TODO(ui-features): MP3/OGG need a decoder (e.g. JLayer/JOrbis) before Commons/Tatoeba clips play here. */
class DesktopAudioPlayer : AudioPlayer {
    @Volatile private var clip: Clip? = null

    override suspend fun play(bytes: ByteArray, mime: String) {
        stop()
        withContext(Dispatchers.IO) {
            runCatching {
                val ais = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
                val c = AudioSystem.getClip()
                c.open(ais)
                c.addLineListener { ev -> if (ev.type == javax.sound.sampled.LineEvent.Type.STOP) c.close() }
                clip = c
                c.start()
            }
        }
    }

    override fun stop() { clip?.let { runCatching { it.stop(); it.close() } }; clip = null }
}

/** AWT `FileDialog` on the Swing thread. */
class DesktopFileImporter : FileImporter {
    override suspend fun pickText(allowedExtensions: List<String>): ImportedFile? = withContext(Dispatchers.IO) {
        val dlg = FileDialog(null as Frame?, "Open a text", FileDialog.LOAD)
        dlg.setFilenameFilter { _, name -> allowedExtensions.isEmpty() || allowedExtensions.any { name.lowercase().endsWith(".$it") } }
        dlg.isVisible = true
        val dir = dlg.directory ?: return@withContext null
        val name = dlg.file ?: return@withContext null
        val f = File(dir, name)
        if (!f.isFile) return@withContext null
        ImportedFile(name, f.readText())
    }
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
        share = DesktopShare(),
        reducedMotion = ::platformReducedMotion,
    )
}
