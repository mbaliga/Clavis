package io.github.mbaliga.clavis.ui.screens.story

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Lettering
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.Script
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.BodyText
import io.github.mbaliga.clavis.ui.components.CaptionTokens
import io.github.mbaliga.clavis.ui.components.Chip
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.PrimaryButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SceneBox
import io.github.mbaliga.clavis.ui.components.SpeakButton
import io.github.mbaliga.clavis.ui.components.TokenMark
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.nav.VIEW_NAMES
import io.github.mbaliga.clavis.ui.screens.learn.laneColour
import io.github.mbaliga.clavis.ui.screens.session.HintContent
import io.github.mbaliga.clavis.ui.theme.ClavisMotion
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Resolves the Story lane: `S.story.lane` if it is an active lane, else the first active lane. */
fun storyLane(store: ClavisStore): Lang? {
    val lanes = store.engine.activeLanes()
    val want = Lang(store.state.story.lane)
    return if (want in lanes) want else lanes.firstOrNull()
}

/**
 * Story — "just reading" (template-spec §7.11, product-spec §3.7.12): lane chips, the episode
 * picker (episodes with ≥ 6 aligned scenes, `engine.storyEpisodes`), the whole page lettered with
 * numbered balloons, caption rows, page controls. Tapping a balloon speaks the line and lights its
 * caption row for 1.6 s. Nothing here grades, stamps `seen`, or writes the log.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun StoryScreen(store: ClavisStore, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val lanes = store.engine.activeLanes()
    val lane = storyLane(store)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("view-story")) {
        ScreenTopBar(store, VIEW_NAMES["story"]!!)
        if (lane == null) {
            BodyText("No language is open — turn one on in Languages to read the comic.", Modifier.padding(vertical = 12.dp))
            QuietButton("Languages", onClick = { store.setView("languages") })
            Spacer(Modifier.height(130.dp))
            return@Column
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (l in lanes) {
                val on = l == lane
                QuietButton(
                    store.langName(l), onClick = { if (!on) store.setStoryLane(l) }, selected = on, minHeight = 40.dp,
                    testTag = "story-lane-${l.code}", leading = { LaneDot(laneColour(store, l)) },
                )
            }
        }
        val episodes = remember(lane) { store.engine.storyEpisodes(lane) }
        val ep = store.state.story.ep?.takeIf { it in episodes } ?: episodes.firstOrNull()
        if (ep == null) {
            BodyText("Nothing to read in ${store.langName(lane)} yet.", Modifier.padding(vertical = 12.dp))
            Spacer(Modifier.height(130.dp))
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Episode", style = t.small, color = p.ink2, modifier = Modifier.padding(end = 10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = "Episode" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (e in episodes) {
                    Chip(Copy.episode(e), selected = e == ep, onClick = { store.setStoryEpisode(e) }, modifier = Modifier.tagged("episode-$e"))
                }
            }
        }
        val pages = remember(lane, ep) { store.engine.storyPages(lane, ep) }
        if (pages.isEmpty()) { Spacer(Modifier.height(130.dp)); return@Column }
        val pi = store.state.story.pi.coerceIn(0, pages.lastIndex)
        val pageKey = pages[pi]
        val scenes = remember(pageKey, lane) { store.engine.pageScenes(pageKey, lane) }
        StoryPage(store, lane, scenes)
        CitedText(Copy.STORY_FOOTER, onCite = store::openColophon, color = p.ink2, style = t.small, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton("← Page", onClick = { store.setStoryPage(pi - 1) }, enabled = pi > 0, modifier = Modifier.weight(1f), testTag = "story-prev")
            PrimaryButton(Copy.DONE, onClick = { store.setView("learn") }, modifier = Modifier.weight(1f), testTag = "story-done")
            QuietButton("Page →", onClick = { store.setStoryPage(pi + 1) }, enabled = pi < pages.lastIndex, modifier = Modifier.weight(1f), testTag = "story-next")
        }
        Text(Copy.pageOfN(pi + 1, pages.size), style = t.small, color = p.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).tagged("story-page-of"))
        QuietButton("Sentences in ${store.langName(lane)}", onClick = { store.openSentences(lane) }, modifier = Modifier.padding(top = 10.dp), minHeight = 40.dp, testTag = "story-sentences")
        Spacer(Modifier.height(130.dp))
    }
}

/** The lettered page and its caption rows; tap a balloon → speak + light + scroll to the row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryPage(store: ClavisStore, lane: Lang, scenes: List<Scene>) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    if (scenes.isEmpty()) return
    val scope = rememberCoroutineScope()
    val requesters = remember(scenes) { scenes.map { BringIntoViewRequester() } }
    var lit by remember(scenes) { mutableStateOf<Int?>(null) }
    var hint by remember(scenes) { mutableStateOf<Pair<Int, Int>?>(null) } // (scene index, token index)
    val lettered = remember(scenes, lane) { scenes.count { Lettering.rectOK(Lettering.rectFor(it, lane)) && !it.x[lane].isNullOrEmpty() } }
    val rtl = store.isRtl(lane)
    val cjk = store.info(lane)?.script == Script.CJK
    val spaced = store.info(lane)?.spaceSeparated ?: (lane.code != "ja" && lane.code != "zh")
    val readings = store.state.settings.readings

    fun light(i: Int) {
        lit = i
        scope.launch { runCatching { requesters[i].bringIntoView() } }
    }
    LaunchedEffect(lit) {
        if (lit != null) { delay(ClavisMotion.CAPROW_LIT_MS.toLong()); lit = null }
    }

    Box(Modifier.padding(top = 12.dp)) {
        SceneBox(
            corpus = store.corpus, images = store.pageImages, scene = scenes[0], lang = lane, full = true, mask = null,
            siblings = scenes, numbers = true, noHalo = true, tapCurrent = true, rtl = rtl,
            onTapBalloon = { s2 ->
                store.speak(lane, s2.x[lane].orEmpty())
                val i = scenes.indexOfFirst { it.i == s2.i }
                if (i >= 0) light(i)
            },
        )
    }
    BodyText(if (lettered > 0) Copy.storyLettered(store.langName(lane)) else Copy.STORY_NUMBERS, Modifier.padding(vertical = 8.dp).tagged("story-hint"))
    scenes.forEachIndexed { i, sc ->
        val toks = sc.k[lane].orEmpty()
        val isLit = lit == i
        Row(
            Modifier.fillMaxWidth().bringIntoViewRequester(requesters[i]).padding(vertical = 4.dp)
                .clip(ClavisShapes.hint).background(if (isLit) p.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .tagged(if (isLit) "caprow-lit" else "caprow-${sc.i}"),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.padding(top = 10.dp).size(24.dp).clip(CircleShape).background(p.surface2).semantics { contentDescription = "line ${i + 1}" },
                contentAlignment = Alignment.Center,
            ) { Text("${i + 1}", style = t.chip, color = p.ink2) }
            Spacer(Modifier.width(8.dp))
            CaptionTokens(
                tokens = toks, marks = toks.map { TokenMark.PLAIN }, rtl = rtl, cjk = cjk, spaced = spaced, readings = readings,
                onTap = { ti, tok -> store.speak(lane, tok.surface); hint = i to ti },
                hintIndex = hint?.takeIf { it.first == i }?.second,
                hint = hint?.takeIf { it.first == i }?.let { h -> toks.getOrNull(h.second)?.let { tok -> { HintContent(store, lane, tok) } } },
                onHintDismiss = { hint = null },
                modifier = Modifier.weight(1f),
                testTagPrefix = "story-tok-${sc.i}",
            )
            if (store.canSpeak(lane)) {
                Spacer(Modifier.width(6.dp))
                SpeakButton(onClick = { store.speak(lane, sc.x[lane].orEmpty()) }, size = 38.dp, testTag = "story-speak-${sc.i}")
            }
        }
    }
}
