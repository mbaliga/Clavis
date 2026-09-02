package io.github.mbaliga.clavis.ui.screens.paths

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mbaliga.clavis.core.Branch
import io.github.mbaliga.clavis.core.BranchKind
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.hash01
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.PrimaryButton
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.screens.cosmos.ExploreModel
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** One node of the map: the root, a branch (tappable, draggable) or a ghost (draggable only). */
class MapNode(val id: String, val branch: Int, val ghost: Boolean, val size: Float, var x: Float, var y: Float)

/** Geometry of template-spec §6.6 in layout units: root at the origin, branches on r 130, ghosts on r 200. */
object PathsMapLayout {
    const val R_BRANCH = 130f
    const val R_GHOST = 200f
    const val ROOT_SIZE = 46f
    const val BRANCH_SIZE = 36f

    fun build(branches: List<Branch>): List<MapNode> {
        val out = ArrayList<MapNode>()
        out.add(MapNode("you", -1, false, ROOT_SIZE, 0f, 0f))
        val n = branches.size
        branches.forEachIndexed { i, b ->
            val a = (i.toDouble() / n) * 2 * PI - PI / 2
            out.add(MapNode("b$i", i, false, BRANCH_SIZE, (R_BRANCH * cos(a)).toFloat(), (R_BRANCH * sin(a)).toFloat()))
            val ghosts = if (b.kind == BranchKind.NEW) 3 else 2
            for (g in 0 until ghosts) {
                val ga = a + (g - 1) * 0.35
                val id = "g$i-$g"
                out.add(MapNode(id, i, true, (11 + hash01(id) * 7).toFloat(), (R_GHOST * cos(ga)).toFloat(), (R_GHOST * sin(ga)).toFloat()))
            }
        }
        return out
    }

    /** Scale so the r-200 ring plus a margin fits in the box (`padding 26`). */
    fun scale(w: Float, h: Float): Float = min(1f, min(w, h) / (2 * (R_GHOST + 26f + 12f)))
}

@Stable
class MapController(val branches: List<Branch>) {
    val nodes: List<MapNode> = PathsMapLayout.build(branches)
    var selected by mutableIntStateOf(-1) // branch index
    var rev by mutableIntStateOf(0)
    var dragging = -1
    var w = 0f
    var h = 0f
    fun scale() = PathsMapLayout.scale(w, h)
    fun toScreen(n: MapNode): Offset { val s = scale(); return Offset(w / 2 + n.x * s, h / 2 + n.y * s) }
    fun hit(p: Offset): Int {
        val s = scale()
        var best = -1; var bestD = Float.MAX_VALUE
        for ((i, n) in nodes.withIndex()) {
            val c = toScreen(n)
            val d = hypot(p.x - c.x, p.y - c.y)
            if (d <= n.size / 2 * s + 10 && d < bestD) { bestD = d; best = i }
        }
        return best
    }
}

/** The map view of Paths (template-spec §6.6) drawn on a Canvas; tap a branch for its card. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun PathsMapPanel(store: ClavisStore, branches: List<Branch>, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val ctl = remember(branches) { MapController(branches) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = ClavisTheme.fonts.body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center)
    val subStyle = TextStyle(fontFamily = ClavisTheme.fonts.mono, fontSize = 10.sp, color = p.ink2, textAlign = TextAlign.Center)
    val rootStyle = TextStyle(fontFamily = ClavisTheme.fonts.display, fontSize = 13.sp, color = p.ink, textAlign = TextAlign.Center)
    val windowH = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }.takeIf { it > 0.dp } ?: 800.dp
    Column(Modifier.fillMaxWidth().tagged("paths-map")) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w3 = min(maxWidth.value, 572f).dp
            val h3 = min(w3.value, windowH.value * 0.6f).dp
            Canvas(
                Modifier.fillMaxWidth().height(h3).clip(ClavisShapes.card).background(p.surface)
                    .semantics { contentDescription = "map of the ${branches.size} branches" }
                    .tagged("paths-map-canvas")
                    .pointerInput(ctl) {
                        detectDragGestures(
                            onDragStart = { pos -> ctl.dragging = ctl.hit(pos) },
                            onDragEnd = { ctl.dragging = -1 }, onDragCancel = { ctl.dragging = -1 },
                        ) { change, drag ->
                            val d = ctl.dragging
                            if (d >= 0) {
                                change.consume()
                                val s = ctl.scale()
                                val n = ctl.nodes[d]
                                n.x += drag.x / s; n.y += drag.y / s
                                if (!n.ghost && n.branch >= 0) for (g in ctl.nodes) if (g.ghost && g.branch == n.branch) { g.x += drag.x / s; g.y += drag.y / s }
                                ctl.rev++
                            }
                        }
                    }
                    .pointerInput(ctl) {
                        detectTapGestures { pos ->
                            val i = ctl.hit(pos)
                            val n = ctl.nodes.getOrNull(i)
                            if (n != null && !n.ghost && n.branch >= 0) ctl.selected = n.branch
                        }
                    },
            ) {
                @Suppress("UNUSED_VARIABLE") val rev = ctl.rev
                ctl.w = size.width; ctl.h = size.height
                val s = ctl.scale()
                val root = ctl.nodes[0]
                val rc = ctl.toScreen(root)
                // soft contour per branch (BubbleSets stand-in: padded hull of the branch and its ghosts)
                branches.forEachIndexed { i, b ->
                    val kc = kindColour(b.kind)
                    val pts = ctl.nodes.filter { it.branch == i }.map { val c = ctl.toScreen(it); c.x to c.y }
                    if (pts.size < 2) return@forEachIndexed
                    val hull = if (pts.size >= 3) ExploreModel.hull(pts) else pts
                    val path = Path()
                    hull.forEachIndexed { k, (x, y) -> if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    path.close()
                    drawPath(path, kc, alpha = 0.08f, style = Stroke(56f * s, join = StrokeJoin.Round))
                    drawPath(path, kc, alpha = 0.08f)
                    drawPath(path, kc, alpha = 0.5f, style = Stroke(1.2f, join = StrokeJoin.Round))
                }
                // edges
                for (n in ctl.nodes) {
                    if (n.branch < 0) continue
                    val c = ctl.toScreen(n)
                    if (!n.ghost) {
                        drawLine(p.line, rc, c, strokeWidth = 1.6f)
                    } else {
                        val parent = ctl.nodes.first { !it.ghost && it.branch == n.branch }
                        drawLine(p.ink3, ctl.toScreen(parent), c, strokeWidth = 1f, alpha = 0.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)))
                    }
                }
                // nodes
                for (n in ctl.nodes) {
                    val c = ctl.toScreen(n)
                    val r = n.size / 2 * s
                    when {
                        n.branch < 0 -> {
                            drawCircle(p.surface, r, c)
                            drawCircle(p.ink3, r, c, style = Stroke(0.8f))
                            val lay = measurer.measure("you", rootStyle)
                            drawText(lay, topLeft = Offset(c.x - lay.size.width / 2, c.y - lay.size.height / 2))
                        }
                        n.ghost -> drawCircle(kindColour(branches[n.branch].kind), r, c, alpha = 0.4f)
                        else -> {
                            val b = branches[n.branch]
                            val kc = kindColour(b.kind)
                            drawCircle(kc, r, c)
                            if (ctl.selected == n.branch) drawCircle(p.ink, r + 3f, c, style = Stroke(1.6f))
                            val label = b.l?.let { store.langName(it) } ?: Copy.MAP_GAME_LABEL
                            val l1 = measurer.measure(label, labelStyle)
                            val l2 = measurer.measure(Copy.MAP_SUB[b.kind] ?: "", subStyle)
                            val top = c.y + r + 3
                            drawRoundRect(p.surface, topLeft = Offset(c.x - maxOf(l1.size.width, l2.size.width) / 2 - 4, top - 1), size = Size(maxOf(l1.size.width, l2.size.width) + 8f, (l1.size.height + l2.size.height + 2).toFloat()), cornerRadius = CornerRadius(4f), alpha = 0.85f)
                            drawText(l1, topLeft = Offset(c.x - l1.size.width / 2, top))
                            drawText(l2, topLeft = Offset(c.x - l2.size.width / 2, top + l1.size.height))
                        }
                    }
                }
            }
        }
        Text(Copy.MAP_HINT, style = t.chip, color = p.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        val sel = ctl.selected
        if (sel in branches.indices) {
            val b = branches[sel]
            SoftCard(Modifier.tagged("map-card")) {
                Text(b.title, style = t.h3, color = p.ink)
                Text("${b.why} ${Copy.THEN_PREFIX}${b.then}", style = t.small, color = p.ink2, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                PrimaryButton(Copy.branchButton(BranchKind.DUE), onClick = { store.takeBranch(b) }, testTag = "map-walk")
            }
        }
        CitedText(Copy.MAP_FOOTER, onCite = store::openColophon, color = p.ink2, style = t.small, modifier = Modifier.padding(top = 8.dp))
    }
}

@Suppress("unused")
private fun keepColour(c: Color) = c
