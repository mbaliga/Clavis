package io.github.mbaliga.clavis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mbaliga.clavis.platform.AndroidApp
import io.github.mbaliga.clavis.platform.AndroidAppDirs
import io.github.mbaliga.clavis.platform.AndroidAudioPlayer
import io.github.mbaliga.clavis.platform.AndroidFileImporter
import io.github.mbaliga.clavis.platform.AndroidShare
import io.github.mbaliga.clavis.platform.AndroidSpeaker
import io.github.mbaliga.clavis.platform.HttpDownloader
import io.github.mbaliga.clavis.platform.PlatformServices
import io.github.mbaliga.clavis.platform.platformReducedMotion
import io.github.mbaliga.clavis.resources.Res
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.widget.ClavisWidgets
import io.github.mbaliga.clavis.widget.WidgetIntents
import org.jetbrains.compose.resources.ExperimentalResourceApi

class MainActivity : ComponentActivity() {
    private var store: ClavisStore? = null
    private var speaker: AndroidSpeaker? = null

    @OptIn(ExperimentalResourceApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!AndroidApp.ready) AndroidApp.context = applicationContext
        val importer = AndroidFileImporter(this) // must be registered before STARTED
        // The engine binds asynchronously; once it is ready the store re-renders so Speak buttons appear.
        val tts = AndroidSpeaker(applicationContext) { runOnUiThread { store?.refresh() } }
        speaker = tts
        val services = PlatformServices(
            dirs = AndroidAppDirs(applicationContext),
            speaker = tts,
            audio = AndroidAudioPlayer(applicationContext),
            importer = importer,
            downloader = HttpDownloader(),
            share = AndroidShare(this),
            reducedMotion = ::platformReducedMotion,
            audioFormats = setOf("wav", "mp3", "ogg"), // MediaPlayer decodes all three
            canDownload = true,
            dustCap = 2000,
        )
        val s = ClavisStore(services, readResource = { path -> Res.readBytes(path) })
        store = s
        setContent { ClavisApp(s) }
        // A home-screen widget tap (docs/widgets.md); not again when the activity is recreated.
        if (savedInstanceState == null) openFromWidget(intent)
    }

    /** A widget tapped while Clavis is already running: the same activity and store take it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromWidget(intent)
    }

    private fun openFromWidget(intent: Intent?) {
        val tap = WidgetIntents.tapOf(intent) ?: return
        store?.openFromWidget(tap) // waits for boot when the packs are still loading
    }

    /**
     * The process may be killed any time after this: write the learner document now, then publish
     * what the home-screen widgets show (the user is likely on the home screen next).
     */
    override fun onStop() {
        store?.flushSaves()
        store?.let { ClavisWidgets.publish(applicationContext, it) }
        super.onStop()
    }

    override fun onDestroy() {
        speaker?.shutdown() // a TextToSpeech binding must be released, not just stopped
        speaker = null
        super.onDestroy()
    }
}
