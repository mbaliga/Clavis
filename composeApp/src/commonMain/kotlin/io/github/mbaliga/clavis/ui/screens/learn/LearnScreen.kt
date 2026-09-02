package io.github.mbaliga.clavis.ui.screens.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.core.pct
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.SessionOrigin
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.Chip
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.Eyebrow
import io.github.mbaliga.clavis.ui.components.HairlineDivider
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.LinkButton
import io.github.mbaliga.clavis.ui.components.ProgressBar
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import io.github.mbaliga.clavis.ui.theme.ClavisTokens

fun laneColour(store: ClavisStore, l: Lang) = store.info(l)?.let { ClavisTokens.laneColour(it.colour) } ?: ClavisTokens.fallbackDot
fun poolColour(store: ClavisStore, l: Lang) = ClavisTokens.cosmosCol[l.code] ?: laneColour(store, l)

/** Learn (template-spec §7.2) plus the D-14 lane row and the "Just read" link. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearnScreen(store: ClavisStore, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val now = store.now
    val lanes = store.engine.activeLanes()
    val due = store.engine.dueTotal(now)
    val untested = store.engine.untestedLanes()
    val settings = store.state.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("learn")) {
        ScreenTopBar(store, "")
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            val size = minOf(maxWidth * 0.72f, 290.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Pool(lanes = lanes.map { it.code to poolColour(store, it) }, due = minOf(24, due), onStart = { store.startSession(SessionOrigin.POOL) }, size = size)
            }
        }
        Text(
            if (due > 0) Copy.POOL_DUE else Copy.POOL_STILL,
            style = t.body, color = p.ink2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).tagged("pool-caption"),
        )

        // D-14(3): the pedagogically significant switches where the pedagogy happens.
        SoftCard {
            Eyebrow("Languages")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Chip("hopping languages", selected = !settings.monoSession, onClick = { store.updateSettings { monoSession = false } })
                val held = store.state.pin?.let { store.langName(Lang(it)) } ?: "one language"
                Chip("holding $held", selected = settings.monoSession, onClick = { store.updateSettings { monoSession = true } })
            }
            for (l in lanes) {
                val n = store.engine.dueCards(l, now).size
                val pinned = store.state.pin == l.code
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    LaneDot(laneColour(store, l))
                    Spacer(Modifier.width(10.dp))
                    Text(store.langName(l), style = t.body, color = p.ink, modifier = Modifier.weight(1f))
                    Chip(if (store.state.laneMode(l) == LaneMode.PROD) Copy.CHIP_SPEAK else Copy.CHIP_READ)
                    Spacer(Modifier.width(8.dp))
                    Text("$n ready", style = t.small, color = p.ink2, modifier = Modifier.width(64.dp))
                    QuietButton(if (pinned) Copy.PINNED_BUTTON else Copy.PIN, onClick = { store.setPin(if (pinned) null else l) }, selected = pinned, minHeight = 40.dp, testTag = "pin-${l.code}")
                }
                HairlineDivider()
            }
            LinkButton("More…", onClick = { store.setView("languages") }, contentDescription = "Manage languages")
        }

        if (untested.isNotEmpty()) {
            SoftCard {
                Eyebrow(Copy.BEFORE_FIRST_DIP)
                Text(Copy.BEFORE_FIRST_DIP_COPY, style = t.body, color = p.ink, modifier = Modifier.padding(bottom = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (l in untested.take(9)) {
                        QuietButton(store.langName(l), onClick = { store.startTest(l) }, testTag = "test-${l.code}", leading = { LaneDot(laneColour(store, l)) })
                    }
                }
            }
        }

        SoftCard {
            Eyebrow(Copy.HOW_MUCH)
            for (l in lanes) {
                val cov = store.engine.corpusCoverage(l, now)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    LaneDot(laneColour(store, l))
                    Spacer(Modifier.width(8.dp))
                    Text(store.langName(l), style = t.body, color = p.ink, modifier = Modifier.weight(1f))
                    Text(pct(cov), style = t.mono, color = p.ink2)
                }
                ProgressBar(cov, laneColour(store, l), Modifier.padding(top = 4.dp, bottom = 4.dp))
            }
            CitedText(Copy.HOW_MUCH_FOOTER, onCite = store::openColophon, color = p.ink2, style = t.small, modifier = Modifier.padding(top = 8.dp))
        }

        SoftCard {
            Eyebrow(Copy.JUST_READING)
            CitedText(Copy.JUST_READING_COPY, onCite = store::openColophon, color = p.ink, style = t.body)
            Spacer(Modifier.height(10.dp))
            QuietButton(Copy.OPEN_COMIC, onClick = { store.setView("story") }, testTag = "open-comic")
        }
        Spacer(Modifier.height(130.dp))
    }
}
