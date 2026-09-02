package io.github.mbaliga.clavis.ui.screens.cosmos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.EdgeType
import io.github.mbaliga.clavis.core.Pronunciation
import io.github.mbaliga.clavis.core.bandLabel
import io.github.mbaliga.clavis.core.pct
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.ui.components.AudioRow
import io.github.mbaliga.clavis.ui.components.Chip
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.LinkButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.screens.learn.laneColour
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/** Provenance label for an etymology record's `src` (D-5): "Wiktionary", "EtymDB", or both. */
fun etymSourceLabel(src: String?): String? = src?.split('+')?.mapNotNull {
    when (it.trim().lowercase()) { "wiktionary" -> "Wiktionary"; "etymdb" -> "EtymDB"; "" -> null; else -> it.trim() }
}?.takeIf { it.isNotEmpty() }?.joinToString(" + ")

/**
 * The word card (template-spec §7.4 "Word card", product-spec §5.3): surface (RTL-aware), reading,
 * lane, IPA + one labelled play control, the origin line with its provenance in small type (D-5),
 * the memory line, `See its web`, `Read where it appears`, and up to 8 relation chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordCard(
    store: ClavisStore,
    data: FieldData,
    idx: Int,
    onClose: () -> Unit,
    onSeeWeb: () -> Unit,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val w = data.points[idx]
    val l = w.l
    val pron by produceState<Pronunciation?>(null, l, w.lm) { value = runCatching { store.pronunciations(l)[w.lm] }.getOrNull() }
    val rtl = store.isRtl(l)
    SoftCard(modifier.tagged("word-card")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Text(w.surface, style = t.h2, color = p.ink, modifier = Modifier.fillMaxWidth().tagged("word-surface"))
                }
                if (w.reading.isNotEmpty() && store.state.settings.readings) Text(w.reading, style = t.mono, color = p.ink2)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    LaneDot(laneColour(store, l)); Spacer(Modifier.width(6.dp))
                    Text(store.langName(l), style = t.small, color = p.ink2)
                }
            }
            LinkButton("✕", onClick = onClose, testTag = "word-close", contentDescription = "Close")
        }
        val ipa = pron?.ipa ?: w.et?.ipa
        AudioRow(
            store, l, text = w.surface, ipa = ipa, ipaSource = if (ipa != null) CosmosCopy.PRON_WIKTIONARY else null,
            audioUrl = pron?.audioUrl, audioLicence = pron?.audioLicence, audioBy = pron?.audioBy, audioSource = "Wikimedia Commons",
            modifier = Modifier.padding(top = 8.dp), testTagPrefix = "word-audio",
        )
        // origin (D-5: never invented; provenance shown)
        val et = w.et
        val o = et?.origin?.takeIf { it.isNotEmpty() }
        val originLine = when {
            o == null -> CosmosCopy.ORIGIN_MISSING
            et.originType == "bor" -> Copy.borrowedFrom(o)
            else -> Copy.grewFrom(o)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(originLine + CosmosCopy.holds(pct(w.h.toDouble()), bandLabel(w.zipf)), style = t.small, color = p.ink, modifier = Modifier.weight(1f).tagged("word-origin"))
        }
        if (o != null) {
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Chip(if (et.originType == "bor") "borrowed" else "inherited")
                etymSourceLabel(et.source)?.let { Text(it, style = t.tiny, color = p.ink3, modifier = Modifier.align(Alignment.CenterVertically).tagged("word-etym-src")) }
            }
        }
        val card = store.state.cards[l.code]?.get(w.lm)
        if (card != null && card.s > 0) {
            val r = store.engine.scheduler.cardR(card, store.now)
            val s1 = ((card.s * 10).toInt() / 10.0).toString()
            Text(CosmosCopy.memory(pct(r), s1, card.reps), style = t.tiny, color = p.ink2, modifier = Modifier.padding(top = 4.dp).tagged("word-memory"))
        }
        val rels = et?.edges.orEmpty()
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rels.isNotEmpty() || o != null) QuietButton(CosmosCopy.SEE_WEB, onClick = onSeeWeb, minHeight = 40.dp, selected = true, testTag = "word-web")
            val scenes = store.corpus.byLemma[l]?.get(w.lm).orEmpty()
            if (scenes.isNotEmpty()) QuietButton(CosmosCopy.READ_WHERE, onClick = { store.openStoryAt(l, scenes.first()) }, minHeight = 40.dp, testTag = "word-read")
        }
        if (rels.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (ed in rels.take(8)) {
                    val j = data.index["${ed.lang.code}|${ed.lemma}"]
                    val surface = if (j != null) data.points[j].surface else ed.lemma
                    val rel = when (ed.type) { EdgeType.BORROWING -> CosmosCopy.REL_BOR; EdgeType.COGNATE -> CosmosCopy.REL_KIN; EdgeType.DERIVATION -> CosmosCopy.REL_DER }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LaneDot(laneColour(store, ed.lang), size = 8.dp); Spacer(Modifier.width(4.dp))
                        Chip("$surface ${store.langName(ed.lang)}$rel", onClick = {
                            if (j != null) onJump(j) else store.toast(CosmosCopy.outsideLanes(store.langName(ed.lang), ed.lemma))
                        }, modifier = Modifier.tagged("word-rel-${ed.lang.code}-${ed.lemma}"))
                    }
                }
            }
        }
    }
}
