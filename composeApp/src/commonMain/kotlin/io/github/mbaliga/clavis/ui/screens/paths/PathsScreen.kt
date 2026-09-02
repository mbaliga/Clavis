package io.github.mbaliga.clavis.ui.screens.paths

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Branch
import io.github.mbaliga.clavis.core.BranchKind
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.Chip
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.Eyebrow
import io.github.mbaliga.clavis.ui.components.HairlineDivider
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.PrimaryButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SceneBox
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.nav.VIEW_NAMES
import io.github.mbaliga.clavis.ui.screens.learn.laneColour
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import io.github.mbaliga.clavis.ui.theme.ClavisTokens

/** Kind colour `KC` for a branch (template-spec §6.6); LETTERS shares TEST's ochre (D-14). */
fun kindColour(kind: BranchKind): Color = ClavisTokens.kindCol[kind.name] ?: Color(0xFFB0B6C6)

/**
 * Paths (template-spec §6.5–§6.7, product-spec §3.7.7): intro, tree/map toggle, the `last:` row
 * with `↶ undo`, the root row, one `.pathcard` per branch, the tail card, and "The longer view,
 * per language". Branches come from `engine.computeBranches` once per store revision (the NEW
 * branch's scene is drawn with the engine's Random, so it is remembered for the visit).
 */
@Composable
fun PathsScreen(store: ClavisStore, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val rev = LocalRev.current
    val now = store.now
    val branches = remember(rev) { store.engine.computeBranches(now) }
    val st = store.state
    val map = st.pathView == "map"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("view-paths")) {
        ScreenTopBar(store, VIEW_NAMES["paths"]!!)
        CitedText(Copy.PATHS_INTRO, onCite = store::openColophon, color = p.ink, style = t.body, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!map) PrimaryButton(Copy.AS_TREE, onClick = {}, testTag = "paths-tree-btn")
            else QuietButton(Copy.AS_TREE, onClick = { store.setPathView("tree") }, testTag = "paths-tree-btn")
            if (map) PrimaryButton(Copy.AS_MAP, onClick = {}, testTag = "paths-map-btn")
            else QuietButton(Copy.AS_MAP, onClick = { store.setPathView("map") }, testTag = "paths-map-btn")
        }
        val last = st.pathLog.lastOrNull()
        if (last != null) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Copy.lastTaken(last.title), style = t.small, color = p.ink3, modifier = Modifier.weight(1f).tagged("paths-last"))
                if (store.engine.canUndo()) {
                    QuietButton(
                        Copy.UNDO, onClick = store::undoBranch, minHeight = 40.dp, testTag = "paths-undo",
                        modifier = Modifier.semantics { contentDescription = Copy.UNDO_TITLE },
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (map) PathsMapPanel(store, branches, ui) else PathsTree(store, branches)
        LongerView(store, now)
        Spacer(Modifier.height(130.dp))
    }
}

/** The tree: root row, spine, branch cards, tail card (template-spec §6.5). */
@Composable
private fun PathsTree(store: ClavisStore, branches: List<Branch>) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    Column(Modifier.fillMaxWidth().tagged("paths-tree")) {
        // Root row: `you` · Here, now · n ways forward
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.width(SPINE_W).fillMaxHeight().drawBehind {
                    val x = SPINE_X.toPx()
                    drawLine(p.line, Offset(x, 22.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                },
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(p.ink).align(Alignment.TopStart).semantics { contentDescription = "you" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("you", style = t.wordmark.copy(fontSize = t.small.fontSize), color = p.paper)
                }
            }
            Column(Modifier.padding(start = 8.dp, top = 2.dp, bottom = 10.dp)) {
                Text(Copy.HERE_NOW, style = t.h3, color = p.ink, modifier = Modifier.tagged("paths-here"))
                Text(Copy.waysForward(branches.size), style = t.small, color = p.ink2, modifier = Modifier.tagged("paths-ways"))
            }
        }
        branches.forEachIndexed { i, b -> PathCard(store, b, i) }
        // Tail card
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Spine(p.line, last = true)
            Box(
                Modifier.weight(1f).padding(vertical = 6.dp)
                    .drawBehind {
                        drawRoundRect(
                            p.line, cornerRadius = CornerRadius(12.dp.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                        )
                    }
                    .padding(16.dp).tagged("paths-tail"),
            ) {
                Text(Copy.TAIL_CARD, style = t.small, color = p.ink3)
            }
        }
    }
}

private val SPINE_W = 40.dp
private val SPINE_X = 22.dp

/** The vertical spine cell left of a card, with the elbow into it. */
@Composable
private fun Spine(colour: Color, last: Boolean = false) {
    Box(
        Modifier.width(SPINE_W).fillMaxHeight().drawBehind {
            val x = SPINE_X.toPx()
            val elbowY = 40.dp.toPx()
            val r = 14.dp.toPx()
            val sw = 2.dp.toPx()
            drawLine(colour, Offset(x, 0f), Offset(x, if (last) elbowY - r else size.height), strokeWidth = sw)
            drawArc(colour, 180f, -90f, false, topLeft = Offset(x, elbowY - 2 * r), size = Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
            drawLine(colour, Offset(x + r, elbowY), Offset(size.width, elbowY), strokeWidth = sw)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PathCard(store: ClavisStore, b: Branch, index: Int) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val kc = kindColour(b.kind)
    val l = b.l
    val readings = store.state.settings.readings
    val rtl = l != null && store.isRtl(l)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Spine(p.line)
        Column(
            Modifier.weight(1f).padding(vertical = 6.dp)
                .clip(ClavisShapes.pathcard)
                .background(p.surface)
                .border(1.dp, p.line, ClavisShapes.pathcard)
                .drawBehind { drawRect(kc, size = Size(3.dp.toPx(), size.height)) }
                .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
                .tagged("pathcard-${b.kind.name.lowercase()}-$index"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LaneDot(if (l != null) laneColour(store, l) else kc, size = 9.dp)
                Spacer(Modifier.width(8.dp))
                val label = (if (l != null) "${store.langName(l)} · " else "") + (Copy.KIND_LABEL[b.kind] ?: "")
                Text(label.uppercase(), style = t.eyebrow, color = if (p.isDark) kc else kc.darken(), modifier = Modifier.tagged("pathcard-kind-$index"))
            }
            Text(b.title, style = t.h3, color = p.ink, modifier = Modifier.padding(top = 6.dp).tagged("pathcard-title-$index"))
            val miniScene = b.sc
            if (b.kind == BranchKind.NEW && miniScene != null && l != null) {
                Box(Modifier.padding(top = 10.dp).width(220.dp)) {
                    SceneBox(corpus = store.corpus, images = store.pageImages, scene = miniScene, lang = l, full = false, mask = null, mini = true, rtl = rtl)
                }
            }
            if (b.words.isNotEmpty()) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (w in b.words) WordChipView(w.w, w.r, readings)
                    }
                }
            }
            Text(b.why, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(10.dp))
            PrimaryButton(Copy.branchButton(b.kind), onClick = { store.takeBranch(b) }, testTag = "take-$index")
            HairlineDivider(Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (a in listOf(1f, 0.55f, 0.3f)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(kc.copy(alpha = a)))
                    Spacer(Modifier.width(3.dp))
                }
                Spacer(Modifier.width(6.dp))
                CitedText(
                    Copy.THEN_PREFIX + b.then + b.cites.joinToString("") { " [$it]" },
                    onCite = store::openColophon, color = p.ink3, style = t.small, modifier = Modifier.tagged("pathcard-then-$index"),
                )
            }
        }
    }
}

/** `.chip` with the reading in italics when readings are on (template-spec §6.5). */
@Composable
fun WordChipView(w: String, r: String, readings: Boolean) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    Row(Modifier.clip(ClavisShapes.chip).background(p.surface2).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(w, style = t.chip, color = p.ink2)
        if (readings && r.isNotEmpty()) {
            Spacer(Modifier.width(5.dp))
            Text(r, style = t.chip.copy(fontStyle = FontStyle.Italic), color = p.ink3)
        }
    }
}

/** "The longer view, per language" (template-spec §6.7). */
@Composable
private fun LongerView(store: ClavisStore, now: Long) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current // strong skipping: subscribe this scope to store commits
    SoftCard(Modifier.padding(top = 14.dp).tagged("longer-view")) {
        Eyebrow(Copy.LONGER_VIEW)
        val lanes = store.engine.activeLanes()
        lanes.forEachIndexed { i, l ->
            val o = store.engine.laneOverview(l, now)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).tagged("lane-overview-${l.code}")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LaneDot(laneColour(store, l))
                    Spacer(Modifier.width(8.dp))
                    Text(o.name, style = t.h3, color = p.ink)
                    Spacer(Modifier.width(8.dp))
                    Chip(if (o.mode == LaneMode.PROD) Copy.CHIP_SPEAK else Copy.CHIP_READ)
                    Spacer(Modifier.weight(1f))
                    QuietButton(
                        if (o.pinned) Copy.PINNED_BUTTON else Copy.PIN, onClick = { store.setPin(if (o.pinned) null else l) },
                        selected = o.pinned, minHeight = 40.dp, testTag = "overview-pin-${l.code}",
                    )
                }
                Text(o.meta, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 6.dp).tagged("lane-meta-${l.code}"))
                if (o.lookAlikes.isNotEmpty()) Text(Copy.lookAlikes(o.lookAlikes), style = t.tiny, color = p.ink3, modifier = Modifier.padding(top = 2.dp))
                if (o.bridges.isNotEmpty()) Text(Copy.bridgesHelped(o.bridges), style = t.tiny, color = p.ink3, modifier = Modifier.padding(top = 2.dp))
            }
            if (i < lanes.lastIndex) HairlineDivider()
        }
    }
}

/** Darkens a pastel kind colour enough to read as an eyebrow on light paper. */
internal fun Color.darken(f: Float = 0.62f): Color = Color(red * f, green * f, blue * f, alpha)
