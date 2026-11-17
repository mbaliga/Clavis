package io.github.mbaliga.clavis.platform

import io.github.mbaliga.clavis.core.AppDirs
import io.github.mbaliga.clavis.core.AudioPlayer
import io.github.mbaliga.clavis.core.FileImporter
import io.github.mbaliga.clavis.core.ImportPolicy
import io.github.mbaliga.clavis.core.ImportedFile
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Speaker
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAudioPlayer
import platform.AVFoundation.AVAudioSession
import platform.AVFoundation.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVSpeechBoundary
import platform.AVFoundation.AVSpeechSynthesisVoice
import platform.AVFoundation.AVSpeechSynthesizer
import platform.AVFoundation.AVSpeechUtterance
import platform.AVFoundation.AVSpeechUtteranceDefaultSpeechRate
import platform.AVFoundation.stop
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.setValue
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject

/**
 * Application Support/Clavis for the learner document, Caches/Clavis for caches, `<data>/downloads` for clips.
 * [excludeFromBackup] mirrors Android's cloud-backup exclusion (PRIVACY.md); whether that also withholds the
 * document from device-to-device migration is unverified (docs/PORTING.md section 8, Q12) and is the one switch to flip.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAppDirs(excludeFromBackup: Boolean = EXCLUDE_FROM_BACKUP) : AppDirs {
    companion object { const val EXCLUDE_FROM_BACKUP = true }

    override val data: String = userDir(NSApplicationSupportDirectory) + "/Clavis"
    override val cache: String = userDir(NSCachesDirectory) + "/Clavis"
    override val downloads: String = "$data/downloads"

    init {
        val fm = NSFileManager.defaultManager
        for (d in listOf(data, cache, downloads)) fm.createDirectoryAtPath(d, withIntermediateDirectories = true, attributes = null, error = null)
        if (excludeFromBackup) NSURL.fileURLWithPath(data).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    }

    private fun userDir(dir: NSSearchPathDirectory): String {
        val url = NSFileManager.defaultManager.URLsForDirectory(dir, NSUserDomainMask).firstOrNull() as? NSURL
        return url?.path ?: error("no user directory for $dir")
    }
}

/**
 * `AVSpeechSynthesizer` (D-8): voice chosen exact locale, then language, then none; cancel before speak. The 0.85
 * rate is mapped onto AVSpeechUtterance's scale as 0.85 x the default rate; by-ear tuning is device-only.
 */
class IosSpeaker : Speaker, LocaleAware {
    override var localeFor: (Lang) -> String? = { null }
    private val synth = AVSpeechSynthesizer()

    private fun voice(l: Lang): AVSpeechSynthesisVoice? {
        val voices = AVSpeechSynthesisVoice.speechVoices().filterIsInstance<AVSpeechSynthesisVoice>()
        val tag = (localeFor(l) ?: l.code).replace('_', '-')
        return voices.firstOrNull { it.language.equals(tag, ignoreCase = true) }
            ?: voices.firstOrNull { it.language.substringBefore('-').equals(l.code, ignoreCase = true) }
    }

    override fun canSpeak(l: Lang): Boolean = voice(l) != null

    override fun speak(l: Lang, text: String) {
        val v = voice(l) ?: return
        synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val u = AVSpeechUtterance(string = text)
        u.voice = v
        u.rate = AVSpeechUtteranceDefaultSpeechRate * 0.85f
        synth.speakUtterance(u)
    }

    override fun stop() {
        synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    override fun providerFor(l: Lang): String? = voice(l)?.let { "your device (${it.language})" }
}

/** `AVAudioPlayer` over the clip's bytes. Decodes wav and mp3; it cannot decode Ogg Vorbis (the caller hides Play for ogg via `audioFormats`). */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosAudioPlayer : AudioPlayer {
    private var player: AVAudioPlayer? = null

    override suspend fun play(bytes: ByteArray, mime: String) {
        stop()
        // The silent switch should not mute a clip the learner tapped Play on.
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        val p = AVAudioPlayer(data = bytes.toNSData(), error = null)
        player = p
        p.prepareToPlay()
        p.play()
    }

    override fun stop() {
        player?.stop()
        player = null
    }
}

/**
 * `UIDocumentPickerViewController` in copy mode (the file is copied into the app's temp area, so no security-scoped
 * access is needed), then the same D-7 refusals as the other platforms via [ImportPolicy]: the declared size is
 * checked before the file is read, and the extension, size and "mostly text" checks after.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosFileImporter : FileImporter {
    private var delegate: PickerDelegate? = null // UIKit holds its delegate weakly

    private class PickerDelegate(val result: CompletableDeferred<NSURL?>) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            result.complete(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
        }
        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            result.complete(null)
        }
    }

    override suspend fun pickText(allowedExtensions: List<String>): ImportedFile? {
        val top = topViewController() ?: return null
        val types = (listOf("public.plain-text", "public.text", "public.data") + allowedExtensions.mapNotNull { UTType.typeWithFilenameExtension(it)?.identifier })
            .mapNotNull { UTType.typeWithIdentifier(it) }.distinct()
        val result = CompletableDeferred<NSURL?>()
        val d = PickerDelegate(result)
        delegate = d
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        picker.delegate = d
        picker.allowsMultipleSelection = false
        top.presentViewController(picker, animated = true, completion = null)
        val url = try { result.await() } finally { delegate = null } ?: return null
        val path = url.path ?: return null
        val name = url.lastPathComponent ?: "import.txt"
        return withContext(Dispatchers.Default) {
            val size = (NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue
            ImportPolicy.checkDeclaredSize(size)
            val bytes = NSData.dataWithContentsOfFile(path)?.toByteArray() ?: return@withContext null
            ImportPolicy.decode(name, bytes, allowedExtensions)
        }
    }
}

/**
 * `UIActivityViewController` (D-11: nothing is sent by the app; the learner picks the destination). On iPad it is a
 * popover and needs an anchor: the centre of the presenting view, no arrow.
 */
@OptIn(ExperimentalForeignApi::class)
class IosShare : ShareSheet {
    override fun share(subject: String, text: String): Boolean {
        val top = topViewController() ?: return false
        val vc = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        vc.setValue(subject, forKey = "subject")
        vc.popoverPresentationController?.let { pop ->
            pop.sourceView = top.view
            top.view.bounds.useContents {
                pop.sourceRect = platform.CoreGraphics.CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
            }
            pop.permittedArrowDirections = 0u
        }
        top.presentViewController(vc, animated = true, completion = null)
        return true
    }
}

/**
 * The iOS bundle: every service enabled, with the formats AVAudioPlayer decodes (no Ogg: about 3.2% of recordings,
 * almost all of Latin, then show no Play control, docs/PORTING.md section 6.1) and the phone dust cap.
 */
fun iosServices(): PlatformServices = PlatformServices(
    dirs = IosAppDirs(),
    speaker = IosSpeaker(),
    audio = IosAudioPlayer(),
    importer = IosFileImporter(),
    downloader = IosDownloader(),
    share = IosShare(),
    reducedMotion = ::platformReducedMotion,
    audioFormats = setOf("wav", "mp3"),
    canDownload = true,
    dustCap = 2000,
)
