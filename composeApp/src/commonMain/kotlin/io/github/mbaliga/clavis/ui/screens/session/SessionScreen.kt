package io.github.mbaliga.clavis.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Mask
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.Session
import io.github.mbaliga.clavis.core.Token
import io.github.mbaliga.clavis.core.bandLabel
import io.github.mbaliga.clavis.core.lineOf
import io.github.mbaliga.clavis.core.pct
import io.github.mbaliga.clavis.store.Answered
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.Confirm
import io.github.mbaliga.clavis.store.EncounterUi
import io.github.mbaliga.clavis.store.Gloss
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.SessionOrigin
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.BodyText
import io.github.mbaliga.clavis.ui.components.Chip
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.H2
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.LinkButton
import io.github.mbaliga.clavis.ui.components.PrimaryButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SceneBox
import io.github.mbaliga.clavis.ui.components.SpeakButton
import io.github.mbaliga.clavis.ui.components.StatTile
import io.github.mbaliga.clavis.ui.components.TopBar
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.screens.learn.laneColour
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

const val STOP = "Stop"
const val STOP_CONFIRM = "Stop this session? The current item is not graded."
const val KEEP_GOING = "Keep going"

fun confirmStop(store: ClavisStore) = store.confirm(Confirm(null, STOP_CONFIRM, STOP, KEEP_GOING, onConfirm = store::stopSession))

/** Dispatches between the encounter chrome and the summary (template-spec §5, §5.7). */
@Composable
fun SessionScreen(store: ClavisStore, ui: UiState) {
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current // `session.i` / `done` are mutated in place
    val s = ui.session ?: return
    if (s.done) { SummaryScreen(store) ; return }
    val eu = ui.enc ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("session")) {
        TopBar("")
        SessionBar(s, eu.enc)
        EncounterHeader(store, eu.enc)
        when (val e = eu.enc) {
            is Encounter.Cloze -> ClozeBody(store, eu, e)
            is Encounter.Read -> ReadBody(store, eu, e)
            is Encounter.Echo -> EchoBody(store, eu, e)
            is Encounter.LangId -> LangIdBody(store, eu, e)
        }
        Spacer(Modifier.height(60.dp))
    }
}

/** `.sessionbar`: n 4 dp segments — done, now (lane-coloured), to come. */
@Composable
private fun SessionBar(s: Session, e: Encounter) {
    val p = ClavisTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp).semantics { contentDescription = "encounter ${s.i + 1} of ${s.n}" }.tagged("session-bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(s.n) { i ->
            val c = when { i < s.i -> p.ink3; i == s.i -> p.ochre; else -> p.line }
            Box(Modifier.weight(1f).height(4.dp).background(c, ClavisShapes.chip))
        }
    }
}

@Composable
private fun EncounterHeader(store: ClavisStore, e: Encounter) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (e is Encounter.LangId) {
            Chip(Copy.LANGID_CHIP)
            Spacer(Modifier.weight(1f))
            Text(Copy.LANGID_TAG, style = t.small, color = p.ink2)
        } else {
            LaneDot(laneColour(store, e.l))
            Spacer(Modifier.width(8.dp))
            Text(store.langName(e.l), style = t.h3, color = p.ink, modifier = Modifier.tagged("enc-lang"))
            Spacer(Modifier.width(8.dp))
            val sub = when (e) {
                is Encounter.Cloze -> Copy.SUB_CLOZE
                is Encounter.Echo -> Copy.SUB_ECHO
                is Encounter.Read -> if (e.fluency) Copy.SUB_FLUENCY else Copy.SUB_READ
                else -> ""
            }
            Text(sub, style = t.small, color = p.ink2, modifier = Modifier.tagged("enc-sub"))
            Spacer(Modifier.weight(1f))
            e.why?.let { Text(it, style = t.small, color = p.ink2, modifier = Modifier.tagged("enc-why")) }
        }
        Spacer(Modifier.width(6.dp))
        LinkButton(STOP, onClick = { confirmStop(store) }, testTag = "stop", contentDescription = "Stop this session")
    }
}

/** The scene box in the current mode, the toggle and the hint paragraph (shared by cloze and read). */
@Composable
internal fun SceneSection(store: ClavisStore, eu: EncounterUi, sc: Scene, l: Lang, mask: String?, allowFull: Boolean = true) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val siblings = remember(sc.i, l) { store.pageOf(sc, l) }
    val rtl = store.isRtl(l)
    val credible = io.github.mbaliga.clavis.core.Lettering.rectOK(io.github.mbaliga.clavis.core.Lettering.rectFor(sc, l))
    SceneBox(
        corpus = store.corpus, images = store.pageImages, scene = sc, lang = l, full = eu.fullPage && allowFull, mask = mask,
        siblings = siblings, rtl = rtl,
        onTapBalloon = { s2 ->
            val line = lineOf(l, s2.x[l].orEmpty())
            store.showGloss(Gloss(s2, line, s2.x[Lang.EN].orEmpty()))
            store.speak(l, s2.x[l].orEmpty())
        },
    )
    if (allowFull) {
        LinkButton(if (eu.fullPage) Copy.BACK_TO_BALLOON else Copy.SEE_WHOLE_PAGE, onClick = store::toggleFullPage, testTag = "toggle-page")
    }
    val hint = when {
        eu.fullPage && allowFull -> Copy.hintFullPage(store.langName(l))
        credible -> Copy.HINT_CREDIBLE
        else -> Copy.HINT_NOT_CREDIBLE
    }
    BodyText(hint, Modifier.padding(bottom = 8.dp).tagged("scene-hint"))
    val g = eu.gloss
    if (eu.fullPage && allowFull && g != null) {
        Column(Modifier.fillMaxWidth().background(p.surface2, ClavisShapes.hint).padding(12.dp).tagged("gloss")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.line, style = t.body, color = p.ink, modifier = Modifier.weight(1f))
                if (store.canSpeak(l)) SpeakButton(onClick = { store.speak(l, g.scene.x[l].orEmpty()) }, size = 38.dp)
            }
            Text(g.english, style = t.small, color = p.ink2)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Hint popover content (template-spec §5.6). */
@Composable
internal fun HintContent(store: ClavisStore, l: Lang, tok: Token) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val card = store.state.cards[l.code]?.get(tok.lemma)
    Column {
        val parts = buildList {
            add(tok.surface)
            if (!tok.reading.isNullOrEmpty()) add("· ${tok.reading}")
            if (tok.lemma != tok.surface.lowercase()) add("→ ${tok.lemma}")
            add("· ${bandLabel(tok.zipf)}")
        }
        Text(parts.joinToString(" "), style = t.small, color = p.ink)
        if (card != null && card.s > 0) {
            val r = store.engine.scheduler.cardR(card, store.now)
            val s1 = ((card.s * 10).toInt() / 10.0).toString()
            Text("memory ${pct(r)} · holds ~$s1 days · met ${card.reps}×", style = t.tiny, color = p.ink2)
        }
        if (tok.bridges.isNotEmpty()) {
            Text(Copy.bridgeLine(tok.bridges.map { store.langName(it.lang) to it.word }), style = t.tiny, color = p.ink2)
        }
    }
}

/** `renderSummary` (template L2024–2034). */
@Composable
fun SummaryScreen(store: ClavisStore) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val sum = store.engine.summary()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("summary")) {
        TopBar("")
        Spacer(Modifier.height(24.dp))
        H2(Copy.SUMMARY_H2, Modifier.tagged("summary-h2"))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${sum?.correct ?: 0}/${sum?.n ?: 0}", Copy.SUMMARY_CORRECT)
            StatTile("${sum?.newWords ?: 0}", Copy.SUMMARY_NEW)
            StatTile("${sum?.languagesVisited ?: 0}", Copy.SUMMARY_LANGS)
        }
        Spacer(Modifier.height(14.dp))
        CitedText(Copy.SUMMARY_COPY, onCite = store::openColophon, color = p.ink2, style = t.body)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton(Copy.SEE_PATHS, onClick = { store.endSession("paths") }, testTag = "see-paths")
            PrimaryButton(Copy.ANOTHER, onClick = { store.startSession(SessionOrigin.ANOTHER) }, modifier = Modifier.weight(1f), testTag = "another")
            QuietButton(Copy.DONE, onClick = { store.endSession() }, testTag = "done")
        }
    }
}

/** The cloze mask, computed once per encounter render and shared by both scene renderers (§5.1). */
fun clozeMask(e: Encounter.Cloze, revealed: Boolean): String? = Mask.forCloze(e.sc, e.l, e.lemma, revealed)

@Suppress("unused")
private fun keepAnsweredImport(a: Answered?) = a
