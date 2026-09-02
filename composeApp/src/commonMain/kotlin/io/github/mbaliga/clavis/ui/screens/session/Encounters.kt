package io.github.mbaliga.clavis.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Script
import io.github.mbaliga.clavis.core.lineOf
import io.github.mbaliga.clavis.store.Answered
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.EncounterUi
import io.github.mbaliga.clavis.ui.components.BodyText
import io.github.mbaliga.clavis.ui.components.CaptionTokens
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.CoachBox
import io.github.mbaliga.clavis.ui.components.Eyebrow
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.LinkButton
import io.github.mbaliga.clavis.ui.components.ParallelBox
import io.github.mbaliga.clavis.ui.components.PrimaryButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.SpeakButton
import io.github.mbaliga.clavis.ui.components.TokenMark
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.screens.learn.laneColour
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

private fun ClavisStore.isCjk(l: Lang) = info(l)?.script == Script.CJK
private fun ClavisStore.spaced(l: Lang) = info(l)?.spaceSeparated ?: (l.code != "ja" && l.code != "zh")
private fun ClavisStore.siblings(l: Lang): List<Lang> = info(l)?.siblings.orEmpty()

// ------------------------------------------------------------------ cloze (§5.2)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClozeBody(store: ClavisStore, eu: EncounterUi, e: Encounter.Cloze) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val answered = eu.answered as? Answered.Cloze
    val toks = e.sc.k[e.l].orEmpty()
    val gapIdx = remember(e) { store.engine.clozeGapIndex(e) }
    val target = toks.getOrNull(gapIdx)
    val correct = target?.surface ?: e.lemma
    // The mask: once per render, shared by cropped and whole-page renderers (§5.1).
    val mask = remember(e, answered != null) { clozeMask(e, revealed = answered != null) }
    val readings = store.state.settings.readings

    SceneSection(store, eu, e.sc, e.l, mask)

    val marks = toks.indices.map { i ->
        when {
            i != gapIdx -> TokenMark.PLAIN
            answered == null -> TokenMark.GAP
            answered.result.ok -> TokenMark.GAP_HIT
            else -> TokenMark.GAP_MISS
        }
    }
    CaptionTokens(
        tokens = toks, marks = marks, rtl = store.isRtl(e.l), cjk = store.isCjk(e.l), spaced = store.spaced(e.l),
        readings = readings, gapText = correct,
        onTap = { i, tok -> store.speak(e.l, tok.surface); store.showHint(i) },
        hintIndex = eu.hint?.tokenIndex,
        hint = eu.hint?.let { h -> toks.getOrNull(h.tokenIndex)?.let { tok -> { HintContent(store, e.l, tok) } } },
        onHintDismiss = { store.showHint(null) },
        modifier = Modifier.padding(vertical = 6.dp),
    )

    if (eu.fastReturn) CitedText(Copy.FAST_RETURN, onCite = store::openColophon, color = p.ink2, style = t.small, modifier = Modifier.padding(vertical = 6.dp))
    if (eu.showClozeCoach) CoachBox(Copy.COACH_CLOZE, onCite = store::openColophon, modifier = Modifier.padding(vertical = 6.dp))
    if (e.lowCov && answered == null) {
        BodyText(Copy.LOW_COV, Modifier.padding(vertical = 6.dp), color = p.ink2, style = t.small)
        ParallelBox(e.sc, e.l, emptyList(), isRtl = store::isRtl)
        Spacer(Modifier.height(8.dp))
    }

    // Options: three large buttons with reading sub-lines; after answering, hit/miss and the right one revealed.
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).tagged("choices"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        eu.options.forEachIndexed { i, opt ->
            val chosen = answered?.chosen === opt
            val bg = when {
                answered == null -> p.surface
                opt.right -> p.mossSoft
                chosen -> p.rustSoft
                else -> p.surface
            }
            val border = when {
                answered == null -> p.line
                opt.right -> p.moss
                chosen -> p.rust
                else -> p.line
            }
            val glyph = when {
                answered == null -> ""
                opt.right -> " ✓"
                chosen -> " ✗"
                else -> ""
            }
            CompositionLocalProvider(LocalLayoutDirection provides if (store.isRtl(e.l)) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(ClavisShapes.button)
                        .background(bg)
                        .border(1.dp, border, ClavisShapes.button)
                        .clickable(enabled = answered == null, role = Role.Button) { store.answerCloze(opt) }
                        .semantics { contentDescription = "option ${i + 1}: ${opt.w}" + (if (opt.right && answered != null) ", right" else "") }
                        .tagged("cloze-option-$i")
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(opt.w + glyph, style = t.choice, color = p.ink)
                    if (readings && opt.reading.isNotEmpty()) Text(opt.reading, style = t.choiceReading, color = p.ink3)
                }
            }
        }
    }

    if (answered != null) {
        val ok = answered.result.ok
        Text(
            if (ok) "right" else "the answer was $correct",
            style = t.tiny, color = p.ink3,
            modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }.tagged("cloze-announce"),
        )
        Spacer(Modifier.height(10.dp))
        ParallelBox(e.sc, e.l, if (answered.result.showSiblings) store.siblings(e.l) else emptyList(), isRtl = store::isRtl)
        val bridges = target?.bridges.orEmpty()
        if (bridges.isNotEmpty()) {
            val parts = bridges.map { store.langName(it.lang) to it.word }
            CitedText(Copy.bridgeLine(parts), onCite = store::openColophon, color = p.ink2, style = t.small, bold = bridges.map { it.word }.toSet(), modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Copy.ROUTES_PROMPT, style = t.small, color = p.ink2)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                for (b in bridges.map { it.lang }.distinct()) {
                    val helped = b.code in eu.routes
                    QuietButton(if (helped) Copy.routeTicked(store.langName(b)) else store.langName(b), onClick = { if (!helped) store.routeHelped(b) }, selected = helped, minHeight = 40.dp, testTag = "route-${b.code}")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (ok) Copy.clozeHeld(answered.result.dueInDays) else Copy.CLOZE_MISS, style = t.body, color = if (ok) p.moss else p.rust, modifier = Modifier.weight(1f).tagged("cloze-result"))
            if (store.canSpeak(e.l)) SpeakButton(onClick = { store.speak(e.l, e.sc.x[e.l].orEmpty()) }, size = 38.dp)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(Copy.NEXT, onClick = store::next, modifier = Modifier.fillMaxWidth(), testTag = "next")
    }
}

// ------------------------------------------------------------------ read (§5.3)

@Composable
internal fun ReadBody(store: ClavisStore, eu: EncounterUi, e: Encounter.Read) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val toks = e.sc.k[e.l].orEmpty()
    val now = store.now
    SceneSection(store, eu, e.sc, e.l, mask = null)
    val marks = toks.map { tok ->
        val modelKnown = store.engine.isKnown(e.l, tok, now)
        val flipped = tok.lemma in eu.flips
        val unk = if (modelKnown) flipped else !flipped
        when {
            unk -> TokenMark.UNKNOWN
            !modelKnown && flipped -> TokenMark.KNOWN_FLIP
            else -> TokenMark.PLAIN
        }
    }
    Row(verticalAlignment = Alignment.Top) {
        CaptionTokens(
            tokens = toks, marks = marks, rtl = store.isRtl(e.l), cjk = store.isCjk(e.l), spaced = store.spaced(e.l),
            readings = store.state.settings.readings,
            onTap = { i, tok -> store.flipToken(tok.lemma); store.speak(e.l, tok.surface); store.showHint(i) },
            hintIndex = eu.hint?.tokenIndex,
            hint = eu.hint?.let { h -> toks.getOrNull(h.tokenIndex)?.let { tok -> { HintContent(store, e.l, tok) } } },
            onHintDismiss = { store.showHint(null) },
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        if (store.canSpeak(e.l)) { Spacer(Modifier.width(8.dp)); SpeakButton(onClick = { store.speak(e.l, e.sc.x[e.l].orEmpty()) }, size = 38.dp, testTag = "speak-line") }
    }
    if (eu.showReadCoach) CoachBox(Copy.COACH_READ, onCite = store::openColophon, modifier = Modifier.padding(vertical = 6.dp))
    else CitedText(eu.readCopy, onCite = store::openColophon, color = p.ink2, style = t.small, modifier = Modifier.padding(vertical = 6.dp).tagged("read-copy"))
    if (eu.showEnglish) ParallelBox(e.sc, e.l, emptyList(), isRtl = store::isRtl, modifier = Modifier.padding(vertical = 6.dp))
    else LinkButton(Copy.SHOW_ENGLISH, onClick = store::showEnglish, testTag = "show-english")
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuietButton(Copy.TOO_HARD, onClick = { store.finishRead(false) }, modifier = Modifier.weight(1f), testTag = "too-hard")
        PrimaryButton(Copy.UNDERSTOOD, onClick = { store.finishRead(true) }, modifier = Modifier.weight(1f), testTag = "understood")
    }
}

// ------------------------------------------------------------------ echo (§5.4)

@Composable
internal fun EchoBody(store: ClavisStore, eu: EncounterUi, e: Encounter.Echo) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val toks = e.sc.k[e.l].orEmpty()
    val answered = eu.answered as? Answered.Echo
    val hitIdx = toks.indexOfFirst { it.lemma == e.lemma }
    val credible = io.github.mbaliga.clavis.core.Lettering.rectOK(io.github.mbaliga.clavis.core.Lettering.rectFor(e.sc, e.l))
    io.github.mbaliga.clavis.ui.components.SceneBox(
        corpus = store.corpus, images = store.pageImages, scene = e.sc, lang = e.l, full = false, mask = null, rtl = store.isRtl(e.l),
    )
    BodyText(if (credible) Copy.HINT_CREDIBLE else Copy.HINT_ECHO_NOT_CREDIBLE, Modifier.padding(vertical = 8.dp).tagged("scene-hint"))
    CaptionTokens(
        tokens = toks, marks = toks.indices.map { if (it == hitIdx) TokenMark.HIT else TokenMark.PLAIN },
        rtl = store.isRtl(e.l), cjk = store.isCjk(e.l), spaced = store.spaced(e.l), readings = store.state.settings.readings,
        onTap = { i, tok -> store.speak(e.l, tok.surface); store.showHint(i) },
        hintIndex = eu.hint?.tokenIndex,
        hint = eu.hint?.let { h -> toks.getOrNull(h.tokenIndex)?.let { tok -> { HintContent(store, e.l, tok) } } },
        onHintDismiss = { store.showHint(null) },
        modifier = Modifier.padding(vertical = 6.dp),
    )
    BodyText(Copy.ECHO_COPY, Modifier.padding(vertical = 6.dp), color = p.ink2, style = t.small)
    if (answered == null) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton(Copy.ECHO_KNOW, onClick = { store.answerEcho(3) }, modifier = Modifier.fillMaxWidth(), minHeight = 52.dp, testTag = "echo-3")
            QuietButton(Copy.ECHO_FAMILIAR, onClick = { store.answerEcho(2) }, modifier = Modifier.fillMaxWidth(), minHeight = 52.dp, testTag = "echo-2")
            QuietButton(Copy.ECHO_NEW, onClick = { store.answerEcho(1) }, modifier = Modifier.fillMaxWidth(), minHeight = 52.dp, testTag = "echo-1")
        }
    } else {
        ParallelBox(e.sc, e.l, emptyList(), isRtl = store::isRtl)
        Spacer(Modifier.height(8.dp))
        CitedText(
            Copy.echoResult(e.tok.surface, e.tok.reading, answered.result.daysSinceMet, answered.result.origin),
            onCite = store::openColophon, color = p.ink, style = t.body, modifier = Modifier.tagged("echo-result"),
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(Copy.NEXT, onClick = store::next, modifier = Modifier.fillMaxWidth(), testTag = "next")
    }
}

// ------------------------------------------------------------------ langid (§5.5)

@Composable
internal fun LangIdBody(store: ClavisStore, eu: EncounterUi, e: Encounter.LangId) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val answered = eu.answered as? Answered.LangId
    val line = e.sc.x[e.l].orEmpty() // NOT passed through lineOf (template L2008)
    SoftCard {
        CompositionLocalProvider(LocalLayoutDirection provides if (store.isRtl(e.l)) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Text(line, style = if (store.isCjk(e.l) || store.isRtl(e.l)) t.captionWide else t.caption, color = p.ink, modifier = Modifier.fillMaxWidth().tagged("langid-line"))
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        e.opts.forEachIndexed { i, l ->
            val right = l == e.l
            val chosen = answered?.guess == l
            val bg = when { answered == null -> p.surface; right -> p.mossSoft; chosen -> p.rustSoft; else -> p.surface }
            val border = when { answered == null -> p.line; right -> p.moss; chosen -> p.rust; else -> p.line }
            Row(
                Modifier
                    .fillMaxWidth().defaultMinSize(minHeight = 52.dp).clip(ClavisShapes.button).background(bg).border(1.dp, border, ClavisShapes.button)
                    .clickable(enabled = answered == null, role = Role.Button) { store.answerLangId(l) }
                    .semantics { contentDescription = "option ${i + 1}: ${store.langName(l)}" }
                    .tagged("langid-option-$i")
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LaneDot(laneColour(store, l))
                Spacer(Modifier.width(10.dp))
                Text(store.langName(l) + (if (answered != null && right) " ✓" else if (chosen) " ✗" else ""), style = t.choice, color = p.ink)
            }
        }
    }
    if (answered != null) {
        Spacer(Modifier.height(10.dp))
        ParallelBox(e.sc, e.l, emptyList(), isRtl = store::isRtl)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(Copy.NEXT, onClick = store::next, modifier = Modifier.fillMaxWidth(), testTag = "next")
    }
    SoftCard(Modifier.padding(top = 12.dp)) {
        Eyebrow(Copy.LANGID_WHY_EYEBROW)
        CitedText(Copy.LANGID_WHY, onCite = store::openColophon, color = p.ink, style = t.small)
        LinkButton("Evidence & credits", onClick = { store.openColophon() })
    }
    @Suppress("UNUSED_VARIABLE") val unused = lineOf(e.l, line)
}
