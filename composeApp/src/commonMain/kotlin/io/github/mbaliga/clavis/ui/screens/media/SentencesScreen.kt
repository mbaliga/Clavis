package io.github.mbaliga.clavis.ui.screens.media

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Script
import io.github.mbaliga.clavis.core.Sentence
import io.github.mbaliga.clavis.core.Token
import io.github.mbaliga.clavis.core.pct
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.AudioRow
import io.github.mbaliga.clavis.ui.components.Chip
import io.github.mbaliga.clavis.ui.components.backArrow
import io.github.mbaliga.clavis.ui.components.H2
import io.github.mbaliga.clavis.ui.components.HairlineDivider
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.LinkButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.nav.VIEW_NAMES
import io.github.mbaliga.clavis.ui.screens.learn.laneColour
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/** A sentence with its lexicon coverage against the learner's known set. */
class RankedSentence(val s: Sentence, val matched: Int, val known: Int) {
    val coverage: Double get() = if (matched == 0) 0.0 else known.toDouble() / matched
}

/** Ranks a pack: sentences at ≥ [gate] coverage first (by coverage), then the rest by coverage; stable otherwise. */
fun rankSentences(store: ClavisStore, l: Lang, all: List<Sentence>, gate: Double = 0.9): List<RankedSentence> {
    val lex = store.corpus.lex[l].orEmpty()
    val now = store.now
    val knownCache = HashMap<String, Boolean>()
    val ranked = all.map { s ->
        var matched = 0; var known = 0
        for (lm in s.lemmas) {
            val e = lex[lm] ?: continue
            matched++
            val k = knownCache.getOrPut(lm) { store.engine.isKnown(l, Token(e.surface.ifEmpty { lm }, lm, e.zipf, reading = e.reading), now) }
            if (k) known++
        }
        RankedSentence(s, matched, known)
    }
    val (first, rest) = ranked.partition { it.matched > 0 && it.coverage >= gate }
    return first.sortedByDescending { it.coverage } + rest.sortedByDescending { it.coverage }
}

/** Only these licences are ever offered for a Tatoeba recording (D-7). */
fun permissive(licence: String?): Boolean {
    val s = licence?.lowercase()?.replace('-', ' ')?.replace('_', ' ') ?: return false
    if (Regex("""\b(nc|nd)\b""").containsMatchIn(s)) return false // NC / ND are never offered (D-7)
    return s.startsWith("cc0") || s.startsWith("cc by") || s.contains("public domain")
}

/**
 * Sentences mode (product-spec §5.4, brief G): the Tatoeba pack for a lane, sentences whose
 * lexicon coverage against the known set is ≥ 90 % first, English on tap, Play for permissive
 * audio (download on tap, labelled `recording · Tatoeba · {licence} · {user}`), else the synthetic
 * voice. No scheduling, no claims.
 */
@Composable
fun SentencesScreen(store: ClavisStore, ui: UiState, l: Lang) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    // null while loading; a failed Result when the pack cannot be parsed (shown as such, not as "none yet")
    val pack by produceState<Result<List<Sentence>>?>(null, l) { value = runCatching { store.sentences(l) } }
    val ranked = remember(pack, rev) { pack?.getOrNull()?.let { rankSentences(store, l, it) } }
    var shown by remember(l) { mutableIntStateOf(60) }
    var english by remember(l) { mutableStateOf<Long?>(null) }
    val rtl = store.isRtl(l)
    val wide = rtl || store.info(l)?.script == Script.CJK
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("view-sentences")) {
        ScreenTopBar(store, VIEW_NAMES["media"]!!)
        LinkButton("${backArrow()} ${MediaCopy.SENTENCES_BACK}", onClick = { store.openSentences(null) }, testTag = "sentences-back")
        Row(verticalAlignment = Alignment.CenterVertically) {
            LaneDot(laneColour(store, l)); Spacer(Modifier.width(8.dp))
            H2(MediaCopy.sentencesIn(store.langName(l)))
        }
        Text(MediaCopy.SENTENCES_FOOTER, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        when {
            pack == null -> Text(MediaCopy.SENTENCES_LOADING, style = t.small, color = p.ink3, modifier = Modifier.tagged("sentences-loading"))
            pack?.isFailure == true -> Text(MediaCopy.sentencesUnreadable(store.langName(l)), style = t.small, color = p.rust, modifier = Modifier.tagged("sentences-failed"))
            ranked == null || ranked.isEmpty() -> Text(MediaCopy.noSentences(store.langName(l)), style = t.small, color = p.ink2, modifier = Modifier.tagged("sentences-empty"))
            else -> {
                val gated = ranked.count { it.matched > 0 && it.coverage >= 0.9 }
                Text("$gated of ${ranked.size} sentences at ≥ 90% known words", style = t.tiny, color = p.ink3, modifier = Modifier.tagged("sentences-gated"))
                ranked.take(shown).forEachIndexed { i, r ->
                    val s = r.s
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).tagged("sentence-row-$i")) {
                        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                            Text(
                                s.text, style = if (wide) t.captionWide.copy(fontSize = t.caption.fontSize) else t.body, color = p.ink,
                                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { english = if (english == s.id) null else s.id }.tagged("sentence-text-$i"),
                            )
                        }
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Chip(pct(r.coverage) + MediaCopy.KNOWN_SUFFIX)
                            if (r.matched > 0 && r.coverage >= 0.95) Chip(MediaCopy.READABLE_CHIP)
                            Text("Tatoeba · ${s.audioBy ?: "contributor"}", style = t.tiny, color = p.ink3)
                        }
                        val en = s.en
                        if (english == s.id && !en.isNullOrBlank()) Text(en, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 4.dp).tagged("sentence-en-$i"))
                        val audio = s.audioUrl?.takeIf { permissive(s.audioLicence) }
                        AudioRow(store, l, text = s.text, ipa = null, ipaSource = null, audioUrl = audio, audioLicence = s.audioLicence, audioBy = s.audioBy, audioSource = "Tatoeba", modifier = Modifier.padding(top = 4.dp), testTagPrefix = "sentence-audio-$i")
                    }
                    HairlineDivider()
                }
                if (shown < ranked.size) QuietButton(MediaCopy.SHOW_MORE, onClick = { shown += 60 }, modifier = Modifier.padding(top = 10.dp), testTag = "sentences-more")
            }
        }
        Spacer(Modifier.height(130.dp))
    }
}
