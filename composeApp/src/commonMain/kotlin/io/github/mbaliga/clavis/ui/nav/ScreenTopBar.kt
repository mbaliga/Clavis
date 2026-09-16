package io.github.mbaliga.clavis.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.ui.components.TopBar
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/** True on compact widths in non-modal states: the gear lives in the topbar (rail has its own). */
val LocalShowGear = staticCompositionLocalOf { false }

/** True below the 600 dp breakpoint (product-spec §3.4); set once by the shell. */
val LocalCompact = staticCompositionLocalOf { true }

/** True while the rail is on screen — it carries the wordmark, so view topbars drop theirs. */
val LocalRailShown = staticCompositionLocalOf { false }

/** The topbar of a view: wordmark · `VIEWNAMES[view]` · gear (compact, non-modal only). */
@Composable
fun ScreenTopBar(store: ClavisStore, subtitle: String, modifier: Modifier = Modifier) {
    val showGear = LocalShowGear.current
    TopBar(subtitle, modifier, showWordmark = !LocalRailShown.current, trailing = if (showGear) ({ GearButton { store.setView("set") } }) else null)
}

@Composable
fun GearButton(onClick: () -> Unit) {
    val p = ClavisTheme.palette
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Settings" }
            .tagged("gear"),
        contentAlignment = Alignment.Center,
    ) { NavIcon("set", p.ink2, 22.dp) }
}
