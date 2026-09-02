package io.github.mbaliga.clavis.ui.screens.cosmos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.PrimaryButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.nav.VIEW_NAMES
import io.github.mbaliga.clavis.ui.screens.learn.poolColour
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import io.github.mbaliga.clavis.ui.theme.LocalReducedMotion

/** A key that changes only when the Field's geometry or familiarity can change. */
private fun fieldKey(store: ClavisStore): String {
    val st = store.state
    return st.lanes.entries.joinToString(",") { "${it.key}=${it.value}" } + "|" + st.cards.entries.sumOf { it.value.size } + "|" + st.tests.size + "|" + st.log.size
}

/**
 * Cosmos (template-spec §7.4–§7.5, product-spec §3.7.6): the Field (default) or Explore, persisted
 * in `S.cosmos.mode`; HUD, canvas, undo/redo, hint, word card, legend, family bar, search.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CosmosScreen(store: ClavisStore, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val reduced = LocalReducedMotion.current
    val key = fieldKey(store)
    val data = remember(key) {
        FieldModel.build(
            store.corpus, store.registry, store.state, store.engine.scheduler, { l -> store.engine.thr(l) }, store.now,
            dustCount = store.services.dustCap, dustLite = p.cosmosDustLite,
        )
    }
    val ctl = remember(data) { FieldController(data, motion = store.state.settings.motion && !reduced).also { it.langNameForWeb = { l -> store.langName(l) } } }
    val exCtl = remember(data) { ExploreController(data) }
    val explore = store.state.cosmos.mode == "explore"
    val families = store.state.cosmos.exsets
    val relations = store.state.cosmos.exlens == "relations"
    var query by remember { mutableStateOf("") }
    val windowH = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }.takeIf { it > 0.dp } ?: 800.dp

    // Explore → Field handoff (`S.cosmos.webFor`)
    LaunchedEffect(data, store.state.cosmos.webFor, explore) {
        val id = store.state.cosmos.webFor
        if (!explore && id != null) {
            data.index[id]?.let { i -> ctl.select(i); ctl.setFocus(i) }
            store.setCosmosWebFor(null)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("view-cosmos")) {
        ScreenTopBar(store, VIEW_NAMES["cosmos"]!!)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val canvasH = minOf(maxWidth * 1.12f, windowH * 0.66f)
            Column {
                Box(Modifier.fillMaxWidth().height(canvasH).clip(ClavisShapes.card).background(p.paper)) {
                    if (explore) {
                        ExploreCanvas(exCtl, families = families, relations = relations, onPick = { }, modifier = Modifier.fillMaxSize())
                    } else {
                        FieldCanvas(
                            ctl, reduced = reduced || !store.state.settings.motion,
                            onPick = { i -> ctl.select(i) },
                            onFocusTravel = { i -> ctl.select(i); ctl.setFocus(i) },
                            onFocusClose = { ctl.setFocus(-1) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // HUD
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Hud(CosmosCopy.FIELD, !explore, "hud-field") { store.setCosmosMode("depth") }
                        Spacer(Modifier.width(6.dp))
                        Hud(CosmosCopy.EXPLORE, explore, "hud-explore") { store.setCosmosMode("explore") }
                        Spacer(Modifier.width(6.dp))
                        if (!explore && ctl.camera.moved) Hud(CosmosCopy.RESET, false, "hud-reset") { ctl.reset() }
                        if (explore) Hud(CosmosCopy.FIT, false, "hud-fit", description = CosmosCopy.FIT_TITLE) { exCtl.fit() }
                        Spacer(Modifier.weight(1f))
                        val motion = store.state.settings.motion
                        Hud(if (motion) CosmosCopy.CALM else CosmosCopy.WAKE, false, "hud-motion") { store.updateSettings { this.motion = !this.motion } }
                    }
                    if (!explore) {
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Hud(CosmosCopy.UNDO, false, "hud-undo", enabled = ctl.canUndo, description = CosmosCopy.UNDO_TITLE) { ctl.undo() }
                            Hud(CosmosCopy.REDO, false, "hud-redo", enabled = ctl.canRedo, description = CosmosCopy.REDO_TITLE) { ctl.redo() }
                        }
                    } else {
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Hud(CosmosCopy.RELATIONS, relations, "tool-relations", description = CosmosCopy.RELATIONS_TITLE) { store.setCosmosLens(if (relations) "off" else "relations") }
                            Hud(CosmosCopy.FAMILIES, families, "tool-families", description = CosmosCopy.FAMILIES_TITLE) { store.setCosmosSets(!families) }
                        }
                    }
                    Text(
                        if (explore) CosmosCopy.HINT_EXPLORE else CosmosCopy.HINT_FIELD, style = t.chip, color = p.ink3, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 6.dp).tagged("cosmos-hint"),
                    )
                }
                val sel = if (explore) exCtl.selectedPoint else ctl.selected
                if (sel >= 0 && sel < data.points.size) {
                    WordCard(
                        store, data, sel,
                        onClose = { if (explore) exCtl.selected = -1 else { ctl.push(); ctl.selected = -1; ctl.setFocus(-1) } },
                        onSeeWeb = {
                            if (explore) { store.setCosmosWebFor(data.points[sel].id); store.setCosmosMode("depth") } // Explore → Field handoff
                            else { ctl.push(); ctl.setFocus(sel) }
                        },
                        onJump = { j ->
                            if (explore) { val n = exCtl.nodeOf(j); if (n >= 0) { exCtl.selected = n; exCtl.centreOn(n) } else store.toast(CosmosCopy.NOT_DRAWN_HERE) }
                            else { ctl.select(j); if (ctl.focus != null) ctl.setFocus(j) }
                        },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        CitedText(if (explore) CosmosCopy.LEGEND_EXPLORE else CosmosCopy.LEGEND_1, onCite = store::openColophon, color = p.ink2, style = t.small, modifier = Modifier.padding(top = 10.dp), bold = setOf("Explore", "Relations", "Families"))
        val lanes = store.engine.activeLanes()
        if (lanes.size >= 2) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp).clip(ClavisShapes.chip)
                    .background(Brush.horizontalGradient(lanes.map { poolColour(store, it) }))
                    .semantics { contentDescription = "family colour bar: " + lanes.joinToString { store.langName(it) } },
            )
        }
        if (!explore) CitedText(CosmosCopy.LEGEND_2, onCite = store::openColophon, color = p.ink2, style = t.small, bold = setOf("Explore"))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(CosmosCopy.SEARCH_PLACEHOLDER, style = t.body, color = p.ink3) },
                textStyle = t.body.copy(color = p.ink),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, unfocusedBorderColor = p.line, focusedTextColor = p.ink, unfocusedTextColor = p.ink),
                modifier = Modifier.weight(1f).tagged("search-field"),
            )
            PrimaryButton(CosmosCopy.FIND, onClick = {
                val i = FieldModel.find(data.points, query)
                if (i < 0) store.toast(CosmosCopy.NOT_IN_CORPUS)
                else if (explore) { val n = exCtl.nodeOf(i); if (n >= 0) { exCtl.selected = n; exCtl.centreOn(n) } else store.toast(CosmosCopy.NOT_DRAWN_HERE) }
                else ctl.select(i)
            }, testTag = "find")
        }
        Spacer(Modifier.height(130.dp))
    }
}

/** A HUD pill: dark when active (the mode buttons), quiet otherwise. */
@Composable
private fun Hud(label: String, active: Boolean, tag: String, enabled: Boolean = true, description: String? = null, onClick: () -> Unit) {
    val m = Modifier.then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
    if (active) PrimaryButton(label, onClick = onClick, modifier = m, testTag = tag)
    else QuietButton(label, onClick = onClick, modifier = m, enabled = enabled, minHeight = 44.dp, testTag = tag)
}

@Suppress("unused")
private fun keepLang(l: Lang) = l
