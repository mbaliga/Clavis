package io.github.mbaliga.clavis.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/**
 * The floating bottom dock for compact widths (D-10): a soft pill on `--dock-bg`, five items
 * ≥ 62 × 48 dp, the active one lit with the lane-neutral accent-soft wash. Radio-group semantics.
 */
@Composable
fun Dock(active: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = ClavisTheme.palette
    Row(
        modifier
            .shadow(6.dp, ClavisShapes.dock, ambientColor = p.shadowAmbient, spotColor = p.shadowKey)
            .clip(ClavisShapes.dock)
            .background(p.dockBg)
            .border(1.dp, p.line, ClavisShapes.dock)
            .padding(6.dp)
            .selectableGroup()
            .tagged("dock"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (item in NAV) {
            val on = item.id == active
            DockButton(item, on, p.accentInk, p.ink2, if (on) p.accentSoft else Color.Transparent) { onSelect(item.id) }
        }
    }
}

@Composable
private fun DockButton(item: NavItem, on: Boolean, onInk: Color, offInk: Color, bg: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .defaultMinSize(minWidth = 62.dp, minHeight = 48.dp)
            .clip(ClavisShapes.dockButton)
            .background(bg)
            .selectable(selected = on, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = item.label }
            .tagged("nav-${item.id}")
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NavIcon(item.id, if (on) onInk else offInk)
        Spacer(Modifier.height(2.dp))
        Text(item.label, style = ClavisTheme.type.dockLabel, color = if (on) onInk else offInk)
    }
}

/** Rail-only secondary destinations: id · label · glyph. */
val RAIL_MORE: List<Triple<String, String, String>> = listOf(
    Triple("story", "Story", "¶"), Triple("languages", "Languages", "Aa"), Triple("media", "Texts", "≡"),
)

/**
 * The left navigation rail for medium/expanded widths: wordmark on top, the five items, the
 * secondary destinations, the gear at the bottom (product-spec §3.4).
 */
@Composable
fun Rail(active: String, onSelect: (String) -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val p = ClavisTheme.palette
    Column(
        modifier
            .fillMaxHeight()
            .width(88.dp)
            .background(p.paper)
            .padding(vertical = 14.dp)
            .selectableGroup()
            .tagged("rail"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Clavis", style = ClavisTheme.type.wordmark, color = p.ink, modifier = Modifier.padding(bottom = 18.dp).tagged("wordmark"))
        for (item in NAV) {
            val on = item.id == active
            Column(
                Modifier
                    .padding(vertical = 3.dp)
                    .size(width = 72.dp, height = 56.dp)
                    .clip(ClavisShapes.dockButton)
                    .background(if (on) p.accentSoft else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSelect(item.id) }
                    .semantics { contentDescription = item.label }
                    .tagged("nav-${item.id}")
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NavIcon(item.id, if (on) p.accentInk else p.ink2)
                Spacer(Modifier.height(2.dp))
                Text(item.label, style = ClavisTheme.type.dockLabel, color = if (on) p.accentInk else p.ink2)
            }
        }
        Spacer(Modifier.weight(1f))
        // Secondary destinations (the "dock overflow" of expanded widths): Story, Languages, Your texts.
        for ((id, label, glyph) in RAIL_MORE) {
            Column(
                Modifier
                    .padding(vertical = 2.dp)
                    .size(width = 72.dp, height = 48.dp)
                    .clip(ClavisShapes.dockButton)
                    .selectable(selected = false, role = Role.Button) { onSelect(id) }
                    .semantics { contentDescription = label }
                    .tagged("rail-$id")
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(glyph, style = ClavisTheme.type.small, color = p.ink3)
                Text(label, style = ClavisTheme.type.dockLabel, color = p.ink3)
            }
        }
        Column(
            Modifier
                .size(width = 72.dp, height = 56.dp)
                .clip(ClavisShapes.dockButton)
                .background(if (active == "set") p.accentSoft else Color.Transparent)
                .selectable(selected = active == "set", role = Role.Button, onClick = onSettings)
                .semantics { contentDescription = "Settings" }
                .tagged("gear")
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NavIcon("set", p.ink2)
            Spacer(Modifier.height(2.dp))
            Text("Settings", style = ClavisTheme.type.dockLabel, color = p.ink2)
        }
    }
}
