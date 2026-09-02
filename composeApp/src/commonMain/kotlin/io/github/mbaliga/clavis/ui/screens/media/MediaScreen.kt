package io.github.mbaliga.clavis.ui.screens.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.ImportedFile
import io.github.mbaliga.clavis.core.ImportedMedia
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Script
import io.github.mbaliga.clavis.core.Token
import io.github.mbaliga.clavis.core.pct
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.Confirm
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.Chip
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.Eyebrow
import io.github.mbaliga.clavis.ui.components.H2
import io.github.mbaliga.clavis.ui.components.HairlineDivider
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.LinkButton
import io.github.mbaliga.clavis.ui.components.PrimaryButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.SpeakButton
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.nav.VIEW_NAMES
import io.github.mbaliga.clavis.ui.screens.learn.laneColour
import io.github.mbaliga.clavis.ui.screens.session.HintContent
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Copy for Media / Your texts (product-spec §3.7.14, §5.5, §5.6 — allowed claims only). */
object MediaCopy {
    const val H2 = "Your texts"
    const val IMPORT = "Import a file"
    const val PASTE = "Paste text"
    const val ADD_PASTED = "Add pasted text"
    const val INTRO = "Your text. Words you know are lit; the rest are dim. Nothing is graded."
    const val INTRO_2 = "Lines you import become extra places to meet words you're already learning [17]."
    const val CJK_NOTE = "Some words could not be separated — Japanese and Chinese text is matched word by word against the comic's vocabulary."
    const val APPROX = "approximate"
    const val LANGUAGE = "Language"
    const val ADD = "Add to my texts"
    const val OPEN = "Open"
    const val DELETE = "Delete"
    const val DELETE_CONFIRM = "Delete this text? It only ever lived on this device."
    const val BACK = "← Your texts"
    fun lines(n: Int) = "$n line${if (n == 1) "" else "s"}"
    fun readable(p: String) = "$p of these lines readable"
    const val TINGLE = "Met this word out here"
    const val TINGLE_NOTED = "Noted. Seeing a word out in the world leaves a trace [14][35]."
    const val TINGLE_EYEBROW = "Tingles"
    const val TINGLE_ADD = "+ I just met a word"
    const val TINGLE_PLACEHOLDER = "the word you met…"
    const val TINGLE_MISS = "Not in the comic's vocabulary — nothing was logged"
    const val SENTENCES_EYEBROW = "Sentences"
    const val SENTENCES_COPY = "Short sentences from Tatoeba, English on tap — more places to meet words you're already learning [17]. Nothing is scheduled from here."
    const val SENTENCES_BACK = "← Back"
    fun sentencesIn(lang: String) = "Sentences in $lang"
    const val SENTENCES_FOOTER = "Sorted so that sentences you can almost read come first (≥ 90% of their words known). Recordings are one volunteer's voice, named with their licence; the synthetic voice says so. Nothing here grades or schedules."
    const val SHOW_MORE = "Show more"
    const val KNOWN_SUFFIX = " known"
    const val READABLE_CHIP = "readable"
    const val NO_TEXTS = "Nothing imported yet. A .txt, .srt or .lrc file stays on this device and is never sent anywhere."
    const val PRIVACY = "Files stay on this device, are never uploaded, and deleting one is immediate."
}

/**
 * Media / Your texts (D-7 `user-media`): import `.txt` / `.srt` / `.lrc` (or paste), choose the
 * language, read it with the lexicon lit, log a tingle, delete. Also hosts the Sentences browser.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaScreen(store: ClavisStore, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val open = ui.mediaOpen?.let { store.state.media[it] }
    val sentencesLane = ui.sentencesLane?.let { Lang(it) }
    when {
        sentencesLane != null -> { SentencesScreen(store, ui, sentencesLane); return }
        open != null -> { ReadingMode(store, open); return }
    }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<ImportedFile, Lang?>?>(null) }
    var pasted by remember { mutableStateOf("") }
    var showPaste by remember { mutableStateOf(false) }
    var tingleWord by remember { mutableStateOf("") }
    var tingleLane by remember { mutableStateOf<Lang?>(null) }
    val lanes = store.engine.activeLanes()

    fun stage(file: ImportedFile) {
        val (_, lines) = MediaParse.parse(file.name, file.text)
        pending = file to MediaParse.guessLang(lines, lanes, store.registry, store.corpus)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("view-media")) {
        ScreenTopBar(store, VIEW_NAMES["media"]!!)
        H2(MediaCopy.H2)
        Text(MediaCopy.INTRO, style = t.body, color = p.ink2, modifier = Modifier.padding(top = 6.dp))
        CitedText(MediaCopy.INTRO_2, onCite = store::openColophon, color = p.ink2, style = t.small, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(MediaCopy.IMPORT, onClick = { scope.launch { store.services.importer.pickText()?.let { stage(it) } } }, testTag = "media-import")
            QuietButton(MediaCopy.PASTE, onClick = { showPaste = !showPaste }, testTag = "media-paste")
        }
        if (showPaste) {
            OutlinedTextField(
                value = pasted, onValueChange = { pasted = it }, minLines = 3, maxLines = 8, textStyle = t.small.copy(color = p.ink),
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, unfocusedBorderColor = p.line, focusedTextColor = p.ink, unfocusedTextColor = p.ink),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).tagged("media-paste-field"),
            )
            QuietButton(MediaCopy.ADD_PASTED, onClick = { if (pasted.isNotBlank()) { stage(ImportedFile("pasted text.txt", pasted)); pasted = ""; showPaste = false } }, modifier = Modifier.padding(top = 6.dp), testTag = "media-paste-add")
        }
        val pend = pending
        if (pend != null) {
            val (file, guess) = pend
            val (kind, lines) = remember(file) { MediaParse.parse(file.name, file.text) }
            SoftCard(Modifier.padding(top = 10.dp).tagged("media-pending")) {
                Text(file.name, style = t.h3, color = p.ink)
                Text("${kind.uppercase()} · ${MediaCopy.lines(lines.size)}", style = t.small, color = p.ink2)
                Eyebrow(MediaCopy.LANGUAGE, Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (l in lanes) Chip(store.langName(l), selected = l == guess, onClick = { pending = file to l }, modifier = Modifier.tagged("media-lang-${l.code}"))
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(MediaCopy.ADD, onClick = {
                        val l = guess ?: lanes.firstOrNull() ?: return@PrimaryButton
                        val id = store.addMedia(file.name.substringBeforeLast('.'), l, kind, lines)
                        pending = null
                        store.openMedia(id)
                    }, enabled = guess != null && lines.isNotEmpty(), testTag = "media-add")
                    QuietButton(Copy.CANCEL, onClick = { pending = null })
                }
            }
        }
        val items = store.state.media.values.sortedByDescending { it.addedAt }
        SoftCard(Modifier.padding(top = 10.dp)) {
            if (items.isEmpty()) Text(MediaCopy.NO_TEXTS, style = t.small, color = p.ink2)
            items.forEachIndexed { i, m ->
                val l = Lang(m.lang)
                val cov = remember(m.id, rev) { readableShare(store, m) }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).tagged("media-item-${m.id}"), verticalAlignment = Alignment.CenterVertically) {
                    LaneDot(laneColour(store, l)); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = t.h3, color = p.ink)
                        Text("${store.langName(l)} · ${m.kind.uppercase()} · ${MediaCopy.lines(m.lines.size)} · ${MediaCopy.readable(pct(cov))} · ${dayOf(m.addedAt)}", style = t.small, color = p.ink2)
                    }
                    QuietButton(MediaCopy.OPEN, onClick = { store.openMedia(m.id) }, minHeight = 40.dp, testTag = "media-open-${m.id}")
                    Spacer(Modifier.width(6.dp))
                    QuietButton(MediaCopy.DELETE, onClick = { store.confirm(Confirm(null, MediaCopy.DELETE_CONFIRM, MediaCopy.DELETE, Copy.CANCEL) { store.deleteMedia(m.id) }) }, minHeight = 40.dp, testTag = "media-delete-${m.id}")
                }
                if (i < items.lastIndex) HairlineDivider()
            }
            Text(MediaCopy.PRIVACY, style = t.tiny, color = p.ink3, modifier = Modifier.padding(top = 8.dp))
        }
        SoftCard {
            Eyebrow(MediaCopy.SENTENCES_EYEBROW)
            CitedText(MediaCopy.SENTENCES_COPY, onCite = store::openColophon, color = p.ink, style = t.small)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in lanes) QuietButton(store.langName(l), onClick = { store.openSentences(l) }, minHeight = 40.dp, testTag = "sentences-${l.code}", leading = { LaneDot(laneColour(store, l)) })
            }
        }
        SoftCard {
            Eyebrow(MediaCopy.TINGLE_EYEBROW)
            CitedText(MediaCopy.TINGLE_NOTED, onCite = store::openColophon, color = p.ink2, style = t.small)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tingleWord, onValueChange = { tingleWord = it }, singleLine = true, textStyle = t.body.copy(color = p.ink),
                    placeholder = { Text(MediaCopy.TINGLE_PLACEHOLDER, style = t.body, color = p.ink3) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, unfocusedBorderColor = p.line, focusedTextColor = p.ink, unfocusedTextColor = p.ink),
                    modifier = Modifier.weight(1f).tagged("tingle-field"),
                )
            }
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (l in lanes) Chip(store.langName(l), selected = l == (tingleLane ?: lanes.firstOrNull()), onClick = { tingleLane = l }, modifier = Modifier.tagged("tingle-lang-${l.code}"))
            }
            QuietButton(MediaCopy.TINGLE_ADD, onClick = {
                val l = tingleLane ?: lanes.firstOrNull() ?: return@QuietButton
                val idx = MediaParse.LexIndex(store.corpus.lex[l].orEmpty())
                val hit = idx.lookup(tingleWord.trim())
                if (hit != null) { store.logTingle(l, hit.first); tingleWord = ""; store.toast("Noted — ${hit.second.surface.ifEmpty { hit.first }}") } else store.toast(MediaCopy.TINGLE_MISS)
            }, modifier = Modifier.padding(top = 8.dp), testTag = "tingle-add")
        }
        Spacer(Modifier.height(130.dp))
    }
}

private fun dayOf(t: Long): String {
    val d = Instant.fromEpochMilliseconds(t).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}

/** A line's lexicon view: pieces, and for word pieces the lemma/entry and knownness. */
class LinePiece(val text: String, val word: Boolean, val token: Token?, val known: Boolean)

fun analyseLine(store: ClavisStore, l: Lang, line: String, index: MediaParse.LexIndex, now: Long): List<LinePiece> {
    val spaced = store.info(l)?.spaceSeparated ?: (l.code != "ja" && l.code != "zh")
    return MediaParse.tokenise(line, spaced, index).map { pc ->
        if (!pc.word) return@map LinePiece(pc.text, false, null, false)
        val hit = index.lookup(pc.text) ?: return@map LinePiece(pc.text, true, null, false)
        val tok = Token(surface = pc.text, lemma = hit.first, zipf = hit.second.zipf, reading = hit.second.reading)
        LinePiece(pc.text, true, tok, store.engine.isKnown(l, tok, now))
    }
}

/** Share of lines (with at least one lexicon word) whose lexicon words are ≥ 95 % known — the 0.95 rule. */
fun readableShare(store: ClavisStore, m: ImportedMedia): Double {
    val l = Lang(m.lang)
    val index = MediaParse.LexIndex(store.corpus.lex[l].orEmpty())
    val now = store.now
    var lines = 0; var ok = 0
    for (line in m.lines.take(400)) {
        val toks = analyseLine(store, l, line, index, now).filter { it.token != null }
        if (toks.isEmpty()) continue
        lines++
        if (toks.count { it.known }.toDouble() / toks.size >= 0.95) ok++
    }
    return if (lines == 0) 0.0 else ok.toDouble() / lines
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingMode(store: ClavisStore, m: ImportedMedia) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val l = Lang(m.lang)
    val now = store.now
    val index = remember(l) { MediaParse.LexIndex(store.corpus.lex[l].orEmpty()) }
    val analysed = remember(m.id, rev) { m.lines.take(400).map { analyseLine(store, l, it, index, now) } }
    val cov = remember(analysed) { readableShare(store, m) }
    val rtl = store.isRtl(l)
    val cjk = store.info(l)?.script == Script.CJK
    val spaced = store.info(l)?.spaceSeparated ?: true
    var hint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var noted by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("media-reading")) {
        ScreenTopBar(store, VIEW_NAMES["media"]!!)
        LinkButton(MediaCopy.BACK, onClick = { store.openMedia(null) }, testTag = "media-back")
        Row(verticalAlignment = Alignment.CenterVertically) {
            LaneDot(laneColour(store, l)); Spacer(Modifier.width(8.dp))
            H2(m.title, Modifier.weight(1f))
        }
        Text("${store.langName(l)} · ${MediaCopy.readable(pct(cov))}" + (if (!spaced || l.code == "ko") " · ${MediaCopy.APPROX}" else ""), style = t.small, color = p.ink2, modifier = Modifier.tagged("media-readable"))
        Text(MediaCopy.INTRO, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 4.dp))
        if (!spaced) Text(MediaCopy.CJK_NOTE, style = t.tiny, color = p.ink3, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(8.dp))
        analysed.forEachIndexed { li, pieces ->
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                FlowRow(Modifier.fillMaxWidth().padding(vertical = 3.dp).tagged("media-line-$li"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    pieces.forEachIndexed { pi, pc ->
                        val style = if (cjk || rtl) t.captionWide.copy(fontSize = t.caption.fontSize) else t.body
                        when {
                            !pc.word -> Text(pc.text, style = style, color = p.ink3)
                            pc.token == null -> Text(pc.text, style = style, color = p.ink3, modifier = Modifier.tagged("media-tok-$li-$pi"))
                            else -> Text(
                                pc.text, style = style, color = p.ink,
                                modifier = Modifier
                                    .clip(ClavisShapes.tok)
                                    .background(if (pc.known) androidx.compose.ui.graphics.Color.Transparent else p.unk)
                                    .clickable(role = Role.Button) { store.speak(l, pc.text); hint = li to pi; noted = null }
                                    .semantics { contentDescription = pc.text + (if (pc.known) "" else ", new to you") }
                                    .tagged(if (pc.known) "media-known-$li-$pi" else "media-unk-$li-$pi")
                                    .padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }
            val h = hint
            if (h != null && h.first == li) {
                val tok = pieces.getOrNull(h.second)?.token
                if (tok != null) {
                    Column(Modifier.fillMaxWidth().clip(ClavisShapes.hint).background(p.surface2).padding(10.dp).tagged("media-hint")) {
                        HintContent(store, l, tok)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (store.canSpeak(l)) SpeakButton(onClick = { store.speak(l, tok.surface) }, size = 36.dp)
                            QuietButton(MediaCopy.TINGLE, onClick = { store.logTingle(l, tok.lemma); noted = tok.lemma }, minHeight = 40.dp, testTag = "tingle-${tok.lemma}")
                            LinkButton("✕", onClick = { hint = null }, contentDescription = "Close hint")
                        }
                        if (noted == tok.lemma) CitedText(MediaCopy.TINGLE_NOTED, onCite = store::openColophon, color = p.ink2, style = t.tiny, modifier = Modifier.padding(top = 4.dp).tagged("tingle-noted"))
                    }
                }
            }
        }
        if (m.lines.size > 400) Text("Showing the first 400 of ${m.lines.size} lines.", style = t.tiny, color = p.ink3, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(130.dp))
    }
}

@Suppress("unused")
private fun keep(b: Box?) = b
private typealias Box = Unit
