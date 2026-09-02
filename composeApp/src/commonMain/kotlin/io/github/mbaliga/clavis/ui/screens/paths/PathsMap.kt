package io.github.mbaliga.clavis.ui.screens.paths

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Branch
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/** Map view placeholder until the force-drawn map lands (template-spec §6.6). */
@Composable
internal fun PathsMapPanel(store: ClavisStore, branches: List<Branch>, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    SoftCard(Modifier.tagged("paths-map")) {
        Text("The map is drawn in a later pass — the same ${branches.size} branches are in the tree.", style = t.small, color = p.ink2)
        CitedText(Copy.MAP_FOOTER, onCite = store::openColophon, color = p.ink3, style = t.tiny, modifier = Modifier.padding(top = 8.dp))
    }
}
