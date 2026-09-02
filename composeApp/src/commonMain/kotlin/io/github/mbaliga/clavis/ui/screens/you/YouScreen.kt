package io.github.mbaliga.clavis.ui.screens.you

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.pct
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.Cite
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.Eyebrow
import io.github.mbaliga.clavis.ui.components.H2
import io.github.mbaliga.clavis.ui.components.HairlineDivider
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.LinkButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.StatTile
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.screens.learn.laneColour
import io.github.mbaliga.clavis.ui.screens.settings.YourDataCard
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/** You (template-spec §7.8) + the four hypothesis panels (product-spec §6). */
@Composable
fun YouScreen(store: ClavisStore, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val rev = LocalRev.current
    val now = store.now
    val m = remember(rev) { store.engine.youMetrics(now) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("you")) {
        ScreenTopBar(store, "your record")
        H2(Copy.YOU_H2)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${m.encounters}", Copy.YOU_ENCOUNTERS)
            StatTile("${m.daysShowedUp}", Copy.YOU_DAYS)
            StatTile(m.recall?.let { pct(it) } ?: Copy.DASH, Copy.YOU_RECALL)
        }

        SoftCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp, horizontal = 8.dp)) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                val widths = listOf(118, 132, 56, 60, 84, 68, 68)
                Row(Modifier.padding(horizontal = 8.dp)) {
                    Copy.YOU_HEADERS.forEachIndexed { i, h -> Text(h.uppercase(), style = t.th, color = p.ink3, modifier = Modifier.width(widths[i].dp)) }
                }
                HairlineDivider(Modifier.padding(vertical = 6.dp))
                for (r in m.rows) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.width(widths[0].dp), verticalAlignment = Alignment.CenterVertically) {
                            LaneDot(laneColour(store, r.l)); Spacer(Modifier.width(6.dp)); Text(r.name, style = t.small, color = p.ink)
                        }
                        Text(r.assumes, style = t.small, color = p.ink2, modifier = Modifier.width(widths[1].dp))
                        Text("${r.held}", style = t.small, color = p.ink, modifier = Modifier.width(widths[2].dp))
                        Text("${r.ready}", style = t.small, color = p.ink, modifier = Modifier.width(widths[3].dp))
                        Text(pct(r.readable), style = t.small, color = p.ink, modifier = Modifier.width(widths[4].dp))
                        Text(r.recall?.let { pct(it) } ?: Copy.DASH, style = t.small, color = p.ink, modifier = Modifier.width(widths[5].dp))
                        Text("${r.mixUps}", style = t.small, color = p.ink, modifier = Modifier.width(widths[6].dp))
                    }
                }
            }
        }
        CitedText(Copy.YOU_FOOTER, onCite = store::openColophon, color = p.ink2, style = t.small, modifier = Modifier.padding(vertical = 6.dp))
        LinkButton("Evidence & credits", onClick = { store.openColophon() })

        // Hypothesis panels (D-14(5))
        SoftCard {
            Eyebrow("The record, per hypothesis")
            Text(Hypotheses.CAVEAT_1, style = t.body, color = p.ink)
            Text(Hypotheses.CAVEAT_2, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 4.dp))
        }
        val panels = remember(rev) {
            listOf(
                Hypotheses.h1(store.state, store.corpus, store.registry, now),
                Hypotheses.h4(store.state, store.corpus, now),
                Hypotheses.h8(store.state),
                Hypotheses.h9(store.state, now),
            )
        }
        for (h in panels) HypothesisPanel(store, h)

        SoftCard {
            Eyebrow(Copy.BASELINE_TESTS)
            for (r in m.rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LaneDot(laneColour(store, r.l)); Spacer(Modifier.width(8.dp))
                    Text(r.name, style = t.body, color = p.ink)
                    Spacer(Modifier.width(6.dp))
                    Text(if (r.tested) Copy.knows(r.assumes) else Copy.UNTESTED, style = t.small, color = p.ink2, modifier = Modifier.weight(1f))
                    QuietButton(if (r.tested) Copy.RETAKE else Copy.TAKE_TEST, onClick = { store.startTest(r.l) }, minHeight = 40.dp, testTag = "test-${r.l.code}")
                }
            }
        }
        YourDataCard(store)
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
private fun HypothesisPanel(store: ClavisStore, h: Hypotheses.Panel) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    SoftCard(Modifier.tagged("panel-${h.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(h.id, style = t.eyebrow, color = p.ink3)
            Spacer(Modifier.width(8.dp))
            Text(h.title, style = t.h3, color = p.ink, modifier = Modifier.weight(1f))
            for (c in h.cites) Cite(c, store::openColophon)
        }
        Text(h.claim, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        if (h.enough) {
            for (line in h.lines) Text(line, style = t.body, color = p.ink, modifier = Modifier.padding(vertical = 2.dp))
        } else {
            Text(Hypotheses.NOT_ENOUGH, style = t.body, color = p.ink, modifier = Modifier.padding(vertical = 2.dp))
            for (line in h.lines) Text(line, style = t.small, color = p.ink2, modifier = Modifier.padding(vertical = 1.dp))
        }
        if (h.note != null) Text(h.note, style = t.tiny, color = p.ink3, modifier = Modifier.padding(top = 6.dp))
        Text("What would count against it: ${h.against}", style = t.small, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
    }
}
