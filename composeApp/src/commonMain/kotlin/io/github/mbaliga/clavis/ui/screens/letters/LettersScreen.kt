package io.github.mbaliga.clavis.ui.screens.letters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.Eyebrow
import io.github.mbaliga.clavis.ui.components.H2
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.Segmented
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.nav.VIEW_NAMES
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/**
 * Letters (template-spec §7.7, D-14/D-21): tabs ja / ko / zh / ar (persisted `S.letterTab`), the
 * kana set toggle, the verbatim tables, every tile speaking on tap. Registry languages that need
 * a script primer but have none yet (ru, uk, hi …) get an honest card instead of an empty screen.
 * Opening a tab marks that script as visited (`coach["letters:<iso>"]`), retiring its Paths branch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LettersScreen(store: ClavisStore, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val extra = store.engine.activeLanes().map { it.code }.filter { it !in LetterData.PRIMER_TABS && store.info(Lang(it))?.needsScriptPrimer == true }
    val tabs = (LetterData.PRIMER_TABS + extra + listOf(store.state.letterTab)).distinct().filter { store.info(Lang(it)) != null }
    val tab = store.state.letterTab.takeIf { it in tabs } ?: "ja"
    val l = Lang(tab)
    LaunchedEffect(tab) { if (store.state.coach["letters:$tab"] != true) store.setLetterTab(l, byUser = true) }

    fun speakTile(text: String) {
        if (store.canSpeak(l)) store.speak(l, text) else store.toast(Copy.noVoiceLetters(store.langName(l)))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("view-letters")) {
        ScreenTopBar(store, VIEW_NAMES["letters"]!!)
        H2(LetterData.H2)
        Text(LetterData.INTRO, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (code in tabs) {
                val on = code == tab
                QuietButton(store.langName(Lang(code)), onClick = { if (!on) store.setLetterTab(Lang(code)) }, selected = on, minHeight = 44.dp, testTag = "letters-tab-$code")
            }
        }
        SoftCard(Modifier.padding(top = 12.dp).tagged("letters-card-$tab")) {
            when (tab) {
                "ja" -> JapaneseTab(store, ::speakTile)
                "ko" -> KoreanTab(store, ::speakTile)
                "zh" -> ChineseTab(store, ::speakTile)
                "ar" -> ArabicTab(store, ::speakTile)
                else -> NoPrimerTab(store, l)
            }
        }
        Spacer(Modifier.height(130.dp))
    }
}

/** A tile ≥ 60 dp: big glyph, sub-label(s); speaks on tap; accessible as "glyph, reading". */
@Composable
private fun RowScope.Tile(glyph: String, sub: String, sub2: String? = null, tag: String, rtl: Boolean = false, onTap: () -> Unit) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    Column(
        Modifier.weight(1f).defaultMinSize(minHeight = 60.dp)
            .clip(ClavisShapes.button).background(p.surface).border(1.dp, p.line, ClavisShapes.button)
            .clickable(role = Role.Button, onClick = onTap)
            .semantics { contentDescription = "$glyph, $sub" + (if (sub2 != null) ", $sub2" else "") }
            .tagged(tag)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Text(glyph, style = t.body.copy(fontSize = 24.65.sp, lineHeight = 30.sp), color = p.ink, textAlign = TextAlign.Center)
        }
        Text(sub, style = t.chip, color = p.ink3, textAlign = TextAlign.Center)
        if (sub2 != null) Text(sub2, style = t.chip, color = p.ink3, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RowScope.BlankCell() = Box(Modifier.weight(1f).defaultMinSize(minHeight = 60.dp))

@Composable
private fun JapaneseTab(store: ClavisStore, speak: (String) -> Unit) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val kata = store.state.kanaSet == "kata"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Segmented(listOf("Hiragana", "Katakana"), if (kata) 1 else 0, onSelect = { store.setKanaSet(if (it == 1) "kata" else "hira") }, groupLabel = "Kana set", testTagPrefix = "kana")
        Spacer(Modifier.width(10.dp))
        Text(if (kata) LetterData.KATA_CAPTION else LetterData.HIRA_CAPTION, style = t.small, color = p.ink2, modifier = Modifier.weight(1f))
    }
    val cells = if (kata) LetterData.KATA else LetterData.HIRA
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).tagged("kana-grid"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in cells.chunked(LetterData.KANA_COLS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in row) if (c == null) BlankCell() else Tile(c.glyph, c.roma, tag = "tile-${c.glyph}") { speak(c.glyph) }
            }
        }
    }
    Text(LetterData.JA_FOOT, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun JamoGrid(items: List<LetterData.Jamo>, speak: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in items.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (j in row) Tile(j.syllable, j.roma, j.jamo, tag = "tile-${j.jamo}") { speak(j.syllable) }
                repeat(5 - row.size) { BlankCell() }
            }
        }
    }
}

@Composable
private fun KoreanTab(store: ClavisStore, speak: (String) -> Unit) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    CitedText(LetterData.KO_INTRO, onCite = store::openColophon, color = p.ink, style = t.body, bold = setOf(LetterData.KO_INTRO_BOLD))
    Eyebrow(LetterData.KO_CONSONANTS, Modifier.padding(top = 12.dp))
    JamoGrid(LetterData.JAMO_C, speak)
    Eyebrow(LetterData.KO_VOWELS, Modifier.padding(top = 12.dp))
    JamoGrid(LetterData.JAMO_V, speak)
    Text(LetterData.KO_FOOT, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun ChineseTab(store: ClavisStore, speak: (String) -> Unit) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    CitedText(LetterData.ZH_INTRO, onCite = store::openColophon, color = p.ink, style = t.body, bold = LetterData.ZH_INTRO_BOLD)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (tone in LetterData.TONES) Tile(tone.char, tone.pinyin, tone.description, tag = "tile-${tone.char}") { speak(tone.char) }
    }
    Text(LetterData.ZH_FOOT, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun ArabicTab(store: ClavisStore, speak: (String) -> Unit) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    CitedText(LetterData.AR_INTRO, onCite = store::openColophon, color = p.ink, style = t.body, bold = setOf(LetterData.AR_INTRO_BOLD))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in LetterData.ABJAD.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (x in row) Tile(x.glyph, x.sound, x.name, tag = "tile-${x.glyph}", rtl = true) { speak(x.glyph) }
                    repeat(4 - row.size) { BlankCell() }
                }
            }
        }
    }
    Text(LetterData.AR_FOOT, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun NoPrimerTab(store: ClavisStore, l: Lang) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    Text(LetterData.noPrimer(store.langName(l)), style = t.body, color = p.ink, modifier = Modifier.tagged("letters-noprimer"))
    Spacer(Modifier.height(10.dp))
    QuietButton(Copy.OPEN_COMIC, onClick = { store.setStoryLane(l); store.setView("story") }, testTag = "letters-read")
}
