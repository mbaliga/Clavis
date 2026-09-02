package io.github.mbaliga.clavis

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.mbaliga.clavis.platform.desktopServices
import io.github.mbaliga.clavis.resources.Res
import io.github.mbaliga.clavis.store.ClavisStore
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.awt.Dimension

@OptIn(ExperimentalResourceApi::class)
fun main() = application {
    val services = remember { desktopServices() }
    val store = remember { ClavisStore(services, readResource = { path -> Res.readBytes(path) }) }
    val icon = remember { runCatching { BitmapPainter(useResource("icons/icon-256.png", ::loadImageBitmap)) }.getOrNull() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Clavis",
        icon = icon,
        state = rememberWindowState(size = DpSize(1100.dp, 780.dp)),
    ) {
        // OD-12: minimum window 480 × 720 dp renders the compact layout.
        LaunchedEffect(Unit) { window.minimumSize = Dimension(480, 720) }
        ClavisApp(store)
    }
}
