package io.github.mbaliga.clavis

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
import org.jetbrains.compose.resources.ExperimentalResourceApi

class MainActivity : ComponentActivity() {
    private var store: ClavisStore? = null

    @OptIn(ExperimentalResourceApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!AndroidApp.ready) AndroidApp.context = applicationContext
        val importer = AndroidFileImporter(this) // must be registered before STARTED
        val services = PlatformServices(
            dirs = AndroidAppDirs(applicationContext),
            speaker = AndroidSpeaker(applicationContext),
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
    }

    override fun onDestroy() {
        store?.services?.speaker?.stop()
        super.onDestroy()
    }
}
