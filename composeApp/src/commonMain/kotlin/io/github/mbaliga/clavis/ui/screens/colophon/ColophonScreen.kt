package io.github.mbaliga.clavis.ui.screens.colophon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.PackManifest
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.colophon.BACK_TO_CLAIM
import io.github.mbaliga.clavis.ui.colophon.CLAIMS
import io.github.mbaliga.clavis.ui.colophon.COLOPHON_H2
import io.github.mbaliga.clavis.ui.colophon.COLOPHON_INTRO
import io.github.mbaliga.clavis.ui.colophon.CREDITS_PLANNED
import io.github.mbaliga.clavis.ui.colophon.GLOSSARY
import io.github.mbaliga.clavis.ui.colophon.REFERENCES
import io.github.mbaliga.clavis.ui.components.Cite
import io.github.mbaliga.clavis.ui.components.Eyebrow
import io.github.mbaliga.clavis.ui.components.H2
import io.github.mbaliga.clavis.ui.components.LinkButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.theme.ClavisMotion
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import kotlinx.coroutines.delay

private const val FIRST_REF_ITEM = 4

/**
 * The colophon (template-spec §7.10): claims → references (scrolled to and highlighted for 1.4 s
 * when opened from a `[n]`) → credits generated from the pack manifests → glossary → licences.
 * When opened over another screen, `← Back to the claim` returns to the sender (D-14).
 */
@Composable
fun ColophonScreen(store: ClavisStore, ui: UiState, overlay: Boolean) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val uri = LocalUriHandler.current
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val listState = rememberLazyListState()
    var lit by remember { mutableStateOf<Int?>(null) }
    val target = store.state.refTarget
    LaunchedEffect(target, ui.colophon) {
        if (target != null) {
            val first = if (overlay) FIRST_REF_ITEM else FIRST_REF_ITEM
            val idx = if (target == 0) first + REFERENCES.size + 3 else first + (target - 1).coerceIn(0, REFERENCES.size - 1)
            listState.scrollToItem(idx)
            lit = target
            store.consumeRefTarget()
            delay(ClavisMotion.REF_HIGHLIGHT_MS.toLong())
            lit = null
        }
    }
    val attribution by produceState<String?>(null) { value = runCatching { store.readText("packs/ATTRIBUTION.md") }.getOrNull() }
    // The OFL texts for the bundled typefaces and the LGPL 2.1 for the desktop audio decoders (MP3SPI,
    // JLayer, VorbisSPI, JOrbis, Tritonus): these files are the copy of each licence that ships with
    // every distribution (THIRD_PARTY_NOTICES.md), shown here in full.
    val licences by produceState<List<Pair<String, String>>>(emptyList()) {
        value = listOf("OFL-Hind", "OFL-RozhaOne", "OFL-IBMPlexMono", "OFL-NotoSans", "OFL-NotoSansCJK", "LGPL-2.1").mapNotNull { n ->
            runCatching { store.readText("licenses/$n.txt") }.getOrNull()?.let { n to it }
        }
    }
    Column(Modifier.fillMaxSize().tagged("colophon")) {
        if (overlay) {
            // Stays visible while the list scrolls to the reference (D-14: "back to the claim").
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                QuietButton(BACK_TO_CLAIM, onClick = store::closeColophon, testTag = "back-to-claim")
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
        item {
            if (!overlay) ScreenTopBar(store, "evidence & credits")
        }
        item { H2(COLOPHON_H2, Modifier.padding(top = 8.dp)); Text(COLOPHON_INTRO, style = t.body, color = p.ink2, modifier = Modifier.padding(bottom = 6.dp)) }
        item {
            SoftCard {
                for (c in CLAIMS) {
                    ClaimRow(c.text, c.cites) { n -> store.openColophon(n) }
                }
            }
        }
        item { Eyebrow("References", Modifier.padding(top = 10.dp, start = 4.dp)) }
        items(REFERENCES.size) { i ->
            val r = REFERENCES[i]
            val bg by animateColorAsState(if (lit == r.id) p.accentSoft else Color.Transparent, tween(200), label = "lit")
            Column(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).background(bg, ClavisShapes.hint).padding(horizontal = 8.dp, vertical = 6.dp).tagged("ref-${r.id}")
                    .semantics { contentDescription = "reference ${r.id}: ${r.s}, ${r.t}" },
            ) {
                Row {
                    Text("[${r.id}]", style = t.mono, color = p.ink3, modifier = Modifier.width(40.dp))
                    Column {
                        Text("${r.s}. ", style = t.small, color = p.ink2)
                        Text(
                            r.t, style = t.body.copy(textDecoration = TextDecoration.Underline), color = p.accentInk,
                            modifier = Modifier.clickable(role = Role.Button) { runCatching { uri.openUri(r.u) } }.semantics { contentDescription = "open ${r.t}" },
                        )
                        Text(r.n, style = t.small, color = p.ink2)
                    }
                }
            }
        }
        item { Eyebrow("Materials & credits", Modifier.padding(top = 14.dp, start = 4.dp)) }
        item { CreditsCard(store.manifests) }
        item {
            SoftCard {
                Text(CREDITS_PLANNED, style = t.small, color = p.ink2)
            }
        }
        item {
            SoftCard {
                Eyebrow("Glossary")
                for ((k, v) in GLOSSARY) {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(k, style = t.h3, color = p.ink, modifier = Modifier.width(150.dp))
                        Text(v, style = t.small, color = p.ink2, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            SoftCard(Modifier.tagged("licences")) {
                Eyebrow("Licences")
                Text("Application code: Apache License 2.0. Data packs: CC BY-SA 4.0 (see the attribution below). Fonts: Hind, Rozha One, IBM Plex Mono and the Noto Sans families under the SIL Open Font License 1.1. Desktop builds decode MP3 and OGG recordings with JavaZOOM MP3SPI, JLayer, VorbisSPI, JCraft JOrbis and Tritonus, all under the GNU Lesser General Public License 2.1 — the full text is below.", style = t.small, color = p.ink)
                var open by remember { mutableStateOf(false) }
                LinkButton(if (open) "Hide the full notices" else "Show the full notices", onClick = { open = !open })
                if (open) {
                    for ((n, txt) in licences) {
                        Text(n, style = t.eyebrow, color = p.ink3, modifier = Modifier.padding(top = 8.dp))
                        Text(txt, style = t.tiny.copy(fontFamily = ClavisTheme.fonts.mono), color = p.ink2)
                    }
                    attribution?.let {
                        Text("ATTRIBUTION.md", style = t.eyebrow, color = p.ink3, modifier = Modifier.padding(top = 8.dp))
                        Text(it, style = t.tiny.copy(fontFamily = ClavisTheme.fonts.mono), color = p.ink2)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(130.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClaimRow(text: String, cites: List<Int>, onCite: (Int) -> Unit) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text(text, style = t.h3, color = p.ink)
        for (c in cites) Cite(c, onCite)
    }
}

/** Credits generated from the pack manifests, never hand-maintained (D-7). */
@Composable
private fun CreditsCard(manifests: List<PackManifest>) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    SoftCard(Modifier.tagged("credits")) {
        Text("Comic: Pepper&Carrot by David Revoy and its volunteer translators, peppercarrot.com, CC BY 4.0. Frequencies: wordfreq (Speer), CC BY-SA. Lemmas & readings: simplemma, UniDic, jieba, mecab-ko, pypinyin, OpenCC. Scheduling: FSRS-5. Voices: a downloaded voice or your device. This app and its data pipeline are open source; the word-graph data pack is CC BY-SA 4.0.", style = t.small, color = p.ink)
        for (m in manifests) {
            Spacer(Modifier.height(10.dp))
            Text("${m.title} · ${m.licence}", style = t.h3, color = p.ink)
            Text("${m.source} · version ${m.version} · ${m.languages.joinToString(" ")}", style = t.tiny, color = p.ink3)
            val names = m.attribution.map { it.name }.distinct()
            val shown = names.take(14)
            val more = names.size - shown.size
            Text(shown.joinToString(", ") + if (more > 0) " and $more more" else "", style = t.small, color = p.ink2)
        }
        if (manifests.isEmpty()) Text("No pack manifest was found.", style = t.small, color = p.ink2)
    }
}
