package io.github.mbaliga.clavis.ui.screens.placeholders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.ui.components.H2
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.nav.VIEW_NAMES
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/**
 * Compiling placeholders for the routes the ui-features pass owns (Cosmos, Paths, Letters, Story,
 * Languages, Media): the topbar sub-title from the IA and a title, nothing else. To replace one,
 * add the real composable and point the `when` in `ClavisApp.ViewContent` at it.
 */
@Composable
fun PlaceholderScreen(store: ClavisStore, view: String, title: String) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("view-$view")) {
        ScreenTopBar(store, VIEW_NAMES[view] ?: "")
        H2(title)
        Spacer(Modifier.height(8.dp))
        Text("Coming in the next pass.", style = t.small, color = p.ink3)
        Spacer(Modifier.height(130.dp))
    }
}

@Composable fun CosmosPlaceholder(store: ClavisStore) = PlaceholderScreen(store, "cosmos", "Cosmos")
@Composable fun PathsPlaceholder(store: ClavisStore) = PlaceholderScreen(store, "paths", "Paths")
@Composable fun LettersPlaceholder(store: ClavisStore) = PlaceholderScreen(store, "letters", "Letters and their sounds")
@Composable fun StoryPlaceholder(store: ClavisStore) = PlaceholderScreen(store, "story", "Just reading")
@Composable fun LanguagesPlaceholder(store: ClavisStore) = PlaceholderScreen(store, "languages", "Languages")
@Composable fun MediaPlaceholder(store: ClavisStore) = PlaceholderScreen(store, "media", "Your texts")
