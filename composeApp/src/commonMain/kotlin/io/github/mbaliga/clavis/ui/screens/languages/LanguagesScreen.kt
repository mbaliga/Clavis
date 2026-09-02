package io.github.mbaliga.clavis.ui.screens.languages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Family
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.core.LanguageInfo
import io.github.mbaliga.clavis.core.PackKind
import io.github.mbaliga.clavis.core.Script
import io.github.mbaliga.clavis.core.bandLabel
import io.github.mbaliga.clavis.core.pct
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.Chip
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.Eyebrow
import io.github.mbaliga.clavis.ui.components.H2
import io.github.mbaliga.clavis.ui.components.HairlineDivider
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.PrimaryButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.Segmented
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.nav.VIEW_NAMES
import io.github.mbaliga.clavis.ui.screens.learn.laneColour
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/** Copy for the Languages catalogue (product-spec §3.7.13, §4). */
object LanguagesCopy {
    const val H2 = "Languages"
    const val INTRO = "Speaking practice is heavier than reading, so one speaking language per family is the honest default — the rest you'll learn to read and recognise."
    const val SHEET_TITLE = "Two speaking languages in one family?"
    fun sheetBody(x: String, y: String) =
        "Producing two sibling languages from zero at the same time is the one configuration the research warns against — learners build blends like portuñol, and the closest language becomes the default supplier of words [9][8]. Reading a sibling is cheap and safe, and it is what every intercomprehension programme does [6]. Recommended: keep $x for speaking and read $y."
    const val SHEET_NOTE = "The constraint is a prudent inference from the interference literature rather than a demonstrated finding — no controlled study exists either way."
    fun readInstead(y: String) = "Read $y instead"
    const val SPEAK_BOTH = "Speak both anyway"
    fun alongside(x: String) = "speaking alongside $x — mix-ups are being counted"
    fun thin(episodes: Int, lang: String) = "Only $episodes episode${if (episodes == 1) "" else "s"} ${if (episodes == 1) "is" else "are"} translated into $lang; sessions will repeat pages sooner."
    const val THIN = "thin"
    fun resting(kept: Int) = "resting · $kept word${if (kept == 1) "" else "s"} kept"
    fun noVoice(lang: String) = "no permissively licensed voice exists for $lang yet — it uses your device's voice if it has one"
    const val VOICE_NONE = "none on this device"
    const val TAKE_CHECK = "Take the check"
    const val PRESETS = "Presets"
    const val PRESET_NINE = "The nine"
    const val PRESET_ROMANCE = "Romance only"
    const val PRESET_ES_IT = "Just Spanish + Italian"
    const val NO_FIGURES = "no pack figures yet"

    val FAMILY_LABEL: Map<Family, String> = mapOf(
        Family.ROMANCE to "Romance", Family.GERMANIC to "Germanic", Family.CJK to "China · Japan · Korea", Family.SEMITIC to "Semitic",
        Family.SLAVIC to "Slavic", Family.TURKIC to "Turkic", Family.INDIC to "Indic", Family.OTHER to "Other",
    )

    fun scriptChip(info: LanguageInfo): String = when (info.script) {
        Script.LATIN -> "Latin"
        Script.CYRILLIC -> "Cyrillic"
        Script.GREEK -> "Greek"
        Script.ARABIC -> "Arabic"
        Script.HEBREW -> "Hebrew"
        Script.DEVANAGARI -> "Devanagari"
        Script.HANGUL -> "Hangul"
        Script.CJK -> if (info.iso.code == "ja") "Kana · Kanji" else "Hanzi"
        Script.OTHER -> "script"
    }

    /**
     * MICReLa receptive hints (product-spec §4.6, Gooskens et al. 2017 = ref [6]), keyed by
     * (spoken lane, read lane). Pairs without a measured figure show nothing; the Marx 2012 and
     * Gil 2026 sentences are omitted because their references [42]/[43] are not in the colophon (R-8).
     */
    val HINTS: Map<Pair<String, String>, String> = mapOf(
        ("es" to "it") to "Untrained Spanish listeners understood 45.7% of spoken Italian; about 40% is the threshold for getting by [6]. Reading is usually easier than listening — no reading figure was measured for this pair.",
        ("it" to "es") to "Untrained Italian listeners understood 65.7% of spoken Spanish [6]. Reading is usually easier than listening — no reading figure was measured for this pair.",
        ("es" to "fr") to "Untrained Spanish listeners understood 28.2% of spoken French — below the ~40% threshold [6]. Expect reading to help more than listening; no reading figure exists.",
        ("fr" to "es") to "Untrained French listeners understood 31.5% of spoken Spanish — below the ~40% threshold [6]. Expect reading to help more than listening; no reading figure exists.",
    )

    /** Languages without a permissively licensed Piper voice (D-8 audit). */
    val NO_PERMISSIVE_VOICE = setOf("ar", "ja", "ko", "hi", "tr")
}

/** Per-language figures read from the pack manifests at runtime (never constants; D-4, D-6, §4.5). */
data class Coverage(
    val scenes: Int?, val pages: Int?, val episodes: Int?, val totalEpisodes: Int,
    val lemmas: Int?, val origins: Int?, val ipa: Int?, val audio: Int?, val sentences: Int?, val sentenceAudio: Int?,
) {
    val thin: Boolean get() = (scenes ?: 0) < 200 || (episodes ?: 0) < 6

    fun line(): String {
        if (scenes == null && lemmas == null) return LanguagesCopy.NO_FIGURES
        val parts = mutableListOf<String>()
        if (scenes != null) parts += "$scenes bubbles"
        if (pages != null) parts += "$pages pages"
        if (episodes != null) parts += "$episodes/$totalEpisodes episodes"
        if (lemmas != null && lemmas > 0) {
            if (origins != null) parts += "origins for ${pct(origins.toDouble() / lemmas)} of words"
            if (ipa != null) parts += "IPA for ${pct(ipa.toDouble() / lemmas)}"
            parts += "recordings for ${audio ?: 0} of $lemmas words"
        }
        if (sentences != null) parts += if (sentences > 0) "$sentences sentences, ${sentenceAudio ?: 0} with audio" else "no sentences yet"
        return parts.joinToString(" · ")
    }
}

fun coverageOf(store: ClavisStore, l: Lang): Coverage {
    fun stat(kind: PackKind, key: String): Int? = store.manifests.firstOrNull { it.kind == kind }?.stats?.get(l.code)?.get(key)
    val total = store.manifests.firstOrNull { it.kind == PackKind.COMICS }?.stats?.values?.mapNotNull { it["episodes"] }?.maxOrNull() ?: 0
    return Coverage(
        scenes = stat(PackKind.COMICS, "scenes") ?: store.sceneCount(l).takeIf { it > 0 },
        pages = stat(PackKind.COMICS, "pages"), episodes = stat(PackKind.COMICS, "episodes"), totalEpisodes = total,
        lemmas = stat(PackKind.LEXICON, "lemmas") ?: stat(PackKind.PRONUNCIATIONS, "lemmas") ?: store.corpus.lex[l]?.size,
        origins = stat(PackKind.ETYMOLOGY, "origins"), ipa = stat(PackKind.PRONUNCIATIONS, "ipa"), audio = stat(PackKind.PRONUNCIATIONS, "audio"),
        sentences = stat(PackKind.SENTENCES, "sentences"), sentenceAudio = stat(PackKind.SENTENCES, "withAudio"),
    )
}

/** Registry languages that ship as lanes: present in the corpus and not the English bridge. */
fun catalogue(store: ClavisStore): List<LanguageInfo> = store.registry.all.filter { it.iso != Lang.EN && store.sceneCount(it.iso) > 0 }

/** The other PROD language in [info]'s family, if any (R-4). */
fun prodSibling(store: ClavisStore, info: LanguageInfo): LanguageInfo? =
    store.registry.family(info.family).firstOrNull { it.iso != info.iso && store.state.laneMode(it.iso) == LaneMode.PROD && store.sceneCount(it.iso) > 0 }

/**
 * Languages (D-6): the catalogue grouped by family, a `speak / read / off` control per language,
 * the honest per-language coverage line from the manifests, the voice status, the baseline status,
 * MICReLa receptive hints, and the family-constraint sheet before a second speaking sibling.
 * Turning a language off pauses its cards (state only); nothing is deleted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagesScreen(store: ClavisStore, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    var pending by remember { mutableStateOf<Pair<LanguageInfo, LanguageInfo>?>(null) } // (Y wants speak, X already speaks)
    val langs = remember(store.registry) { catalogue(store) }
    val families = remember(langs) { langs.groupBy { it.family } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("view-languages")) {
            ScreenTopBar(store, VIEW_NAMES["languages"]!!)
            H2(LanguagesCopy.H2)
            Text(LanguagesCopy.INTRO, style = t.body, color = p.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
            SoftCard {
                Eyebrow(LanguagesCopy.PRESETS)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton(LanguagesCopy.PRESET_NINE, onClick = { store.setLanes(store.registry.all.filter { it.enabledByDefault }.associate { it.iso.code to it.defaultLane }) }, minHeight = 40.dp, testTag = "preset-nine")
                    QuietButton(LanguagesCopy.PRESET_ROMANCE, onClick = { store.setLanes(mapOf("es" to LaneMode.PROD, "fr" to LaneMode.REC, "it" to LaneMode.REC)) }, minHeight = 40.dp, testTag = "preset-romance")
                    QuietButton(LanguagesCopy.PRESET_ES_IT, onClick = { store.setLanes(mapOf("es" to LaneMode.PROD, "it" to LaneMode.REC)) }, minHeight = 40.dp, testTag = "preset-es-it")
                }
            }
            for ((family, members) in families) {
                SoftCard(Modifier.tagged("family-${family.name.lowercase()}")) {
                    Eyebrow(LanguagesCopy.FAMILY_LABEL[family] ?: family.name)
                    members.forEachIndexed { i, info ->
                        LanguageRow(store, info, onWantSpeak = { x -> pending = info to x })
                        if (i < members.lastIndex) HairlineDivider(Modifier.padding(vertical = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(130.dp))
        }
        val pend = pending
        if (pend != null) {
            FamilySheet(
                store, y = pend.first, x = pend.second,
                onRead = { store.setLaneMode(pend.first.iso, LaneMode.REC); pending = null },
                onSpeak = { store.setLaneMode(pend.first.iso, LaneMode.PROD); pending = null },
                onDismiss = { pending = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageRow(store: ClavisStore, info: LanguageInfo, onWantSpeak: (LanguageInfo) -> Unit) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current // strong skipping: re-read lane mode after commits
    val l = info.iso
    val mode = store.state.laneMode(l)
    val name = store.langName(l)
    val cov = remember(l, store.manifests) { coverageOf(store, l) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).tagged("lang-row-${l.code}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LaneDot(laneColour(store, l))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = t.h3, color = p.ink)
                    if (info.nativeName.isNotBlank() && info.nativeName != name) {
                        Spacer(Modifier.width(8.dp))
                        Text(info.nativeName, style = t.small, color = p.ink2)
                    }
                }
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Chip(LanguagesCopy.scriptChip(info))
                    if (info.rtl) Chip("RTL")
                    if (cov.thin) Chip(LanguagesCopy.THIN, modifier = Modifier.tagged("thin-${l.code}"))
                }
            }
        }
        Segmented(
            options = listOf(Copy.CHIP_SPEAK, Copy.CHIP_READ, Copy.CHIP_OFF),
            selected = when (mode) { LaneMode.PROD -> 0; LaneMode.REC -> 1; LaneMode.OFF -> 2 },
            onSelect = { i ->
                val want = when (i) { 0 -> LaneMode.PROD; 1 -> LaneMode.REC; else -> LaneMode.OFF }
                if (want == LaneMode.PROD && mode != LaneMode.PROD) {
                    val x = prodSibling(store, info)
                    if (x != null) onWantSpeak(x) else store.setLaneMode(l, want)
                } else store.setLaneMode(l, want)
            },
            groupLabel = "$name lane mode",
            testTagPrefix = "lang-${l.code}",
            modifier = Modifier.padding(top = 8.dp),
        )
        if (mode == LaneMode.OFF) {
            Text(LanguagesCopy.resting(store.state.cards[l.code]?.size ?: 0), style = t.small, color = p.ink3, modifier = Modifier.padding(top = 6.dp).tagged("resting-${l.code}"))
            return@Column
        }
        Text(cov.line(), style = t.small, color = p.ink2, modifier = Modifier.padding(top = 8.dp).tagged("lang-cov-${l.code}"))
        if (cov.thin && cov.episodes != null) Text(LanguagesCopy.thin(cov.episodes, name), style = t.tiny, color = p.ochre, modifier = Modifier.padding(top = 2.dp))
        val voice = store.services.speaker.providerFor(l)
        Text("voice: ${voice ?: LanguagesCopy.VOICE_NONE}", style = t.small, color = p.ink2, modifier = Modifier.padding(top = 4.dp))
        if (l.code in LanguagesCopy.NO_PERMISSIVE_VOICE) Text(LanguagesCopy.noVoice(name), style = t.tiny, color = p.ink3, modifier = Modifier.padding(top = 2.dp))
        val test = store.state.tests[l.code]
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (test != null) Copy.knows(bandLabel(test.thr)) else Copy.UNTESTED, style = t.small, color = p.ink2, modifier = Modifier.weight(1f))
            if (test == null) QuietButton(LanguagesCopy.TAKE_CHECK, onClick = { store.startTest(l) }, minHeight = 40.dp, testTag = "lang-test-${l.code}")
        }
        if (mode == LaneMode.PROD) {
            val x = prodSibling(store, info)
            if (x != null) Text(LanguagesCopy.alongside(store.langName(x.iso)), style = t.tiny, color = p.rust, modifier = Modifier.padding(top = 4.dp).tagged("alongside-${l.code}"))
        }
        if (mode == LaneMode.REC) {
            val x = prodSibling(store, info)
            val hint = x?.let { LanguagesCopy.HINTS[it.iso.code to l.code] }
            if (hint != null) CitedText(hint, onCite = store::openColophon, color = p.ink2, style = t.tiny, modifier = Modifier.padding(top = 6.dp).tagged("hint-${l.code}"))
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (info.needsScriptPrimer) QuietButton("Letters", onClick = { store.setLetterTab(l); store.setView("letters") }, minHeight = 40.dp, testTag = "lang-letters-${l.code}")
            QuietButton("Read", onClick = { store.setStoryLane(l); store.setView("story") }, minHeight = 40.dp, testTag = "lang-read-${l.code}")
        }
    }
}

/** The family-constraint sheet (product-spec §4.2), blocking until answered. */
@Composable
private fun FamilySheet(store: ClavisStore, y: LanguageInfo, x: LanguageInfo, onRead: () -> Unit, onSpeak: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val yn = store.langName(y.iso)
    val xn = store.langName(x.iso)
    Box(Modifier.fillMaxSize().background(p.ink.copy(alpha = 0.35f)).clickable(onClick = onDismiss).semantics { contentDescription = "dismiss" })
    Column(
        modifier.fillMaxWidth().clip(ClavisShapes.card).background(p.surface).border(1.dp, p.line, ClavisShapes.card)
            .clickable(enabled = false) {}
            .padding(20.dp).tagged("family-sheet"),
    ) {
        Text(LanguagesCopy.SHEET_TITLE, style = t.h3, color = p.ink)
        CitedText(LanguagesCopy.sheetBody(xn, yn), onCite = store::openColophon, color = p.ink, style = t.small, modifier = Modifier.padding(top = 8.dp))
        Text(LanguagesCopy.SHEET_NOTE, style = t.tiny, color = p.ink3, modifier = Modifier.padding(top = 6.dp))
        LanguagesCopy.HINTS[x.iso.code to y.iso.code]?.let { CitedText(it, onCite = store::openColophon, color = p.ink2, style = t.tiny, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(LanguagesCopy.readInstead(yn), onClick = onRead, modifier = Modifier.weight(1f), testTag = "sheet-read")
            QuietButton(LanguagesCopy.SPEAK_BOTH, onClick = onSpeak, modifier = Modifier.weight(1f), testTag = "sheet-speak")
        }
    }
}
