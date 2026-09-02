package io.github.mbaliga.clavis.ui.screens.cosmos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import io.github.mbaliga.clavis.core.EdgeType
import io.github.mbaliga.clavis.core.Family
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Explore's state: the subsample, its edges, the force layout (run once off the main thread,
 * progressively redrawn), the view transform, the selection and the drag target.
 */
@Stable
class ExploreController(val data: FieldData) {
    val nodes: List<ExploreNode> = ExploreModel.subsample(data)
    val edges: List<Triple<Int, Int, EdgeType>> = ExploreModel.edges(data, nodes)
    private val byPoint = HashMap<Int, Int>().also { m -> nodes.forEachIndexed { i, n -> m[n.pointIndex] = i } }
    val neighbours: Map<Int, List<Int>> = HashMap<Int, MutableList<Int>>().also { m ->
        for ((a, b, _) in edges) { m.getOrPut(a) { mutableListOf() }.add(b); m.getOrPut(b) { mutableListOf() }.add(a) }
    }

    var layoutRev by mutableIntStateOf(0)
    var layoutDone by mutableStateOf(false)
    var layoutResult by mutableStateOf<ExploreModel.Result?>(null)
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var selected by mutableIntStateOf(-1) // node index
    var dragging = -1
    var fitted = false
    var lastW = 0f
    var lastH = 0f
    val labels = HashMap<Int, TextLayoutResult>()

    fun nodeOf(pointIndex: Int): Int = byPoint[pointIndex] ?: -1
    fun contains(pointIndex: Int): Boolean = pointIndex in byPoint
    val selectedPoint: Int get() = if (selected >= 0) nodes[selected].pointIndex else -1

    fun toScreen(x: Float, y: Float): Offset = Offset(x * scale + offset.x, y * scale + offset.y)
    fun toWorld(p: Offset): Offset = Offset((p.x - offset.x) / scale, (p.y - offset.y) / scale)

    fun hit(p: Offset): Int {
        val w = toWorld(p)
        var best = -1; var bestD = Float.MAX_VALUE
        for ((i, n) in nodes.withIndex()) {
            val d = hypot(w.x - n.x, w.y - n.y)
            if (d <= n.size / 2 + 8f / scale && d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /** `autoFit:'view'` with padding 18. */
    fun fit(w: Float = lastW, h: Float = lastH) {
        if (nodes.isEmpty() || w <= 0 || h <= 0) return
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (n in nodes) { minX = min(minX, n.x); maxX = max(maxX, n.x); minY = min(minY, n.y); maxY = max(maxY, n.y) }
        val bw = max(1f, maxX - minX + 40); val bh = max(1f, maxY - minY + 40)
        scale = min((w - 36) / bw, (h - 36) / bh).coerceIn(0.05f, 8f)
        offset = Offset(w / 2 - (minX + maxX) / 2 * scale, h / 2 - (minY + maxY) / 2 * scale)
        fitted = true
    }

    fun centreOn(i: Int) {
        if (i < 0 || lastW <= 0) return
        val n = nodes[i]
        offset = Offset(lastW / 2 - n.x * scale, lastH / 2 - n.y * scale)
    }

    fun zoomAt(factor: Float, at: Offset) {
        val ns = (scale * factor).coerceIn(0.05f, 8f)
        val f = ns / scale
        offset = Offset(at.x - (at.x - offset.x) * f, at.y - (at.y - offset.y) * f)
        scale = ns
    }

    /** Family contours (template-spec §7.5 "BubbleSets"): top-60 lit members per family, ≥ 3. */
    fun familyHulls(): List<Pair<Family, List<Pair<Float, Float>>>> {
        val byFam = HashMap<Family, MutableList<ExploreNode>>()
        for (n in nodes) { val p = data.points[n.pointIndex]; if (p.h > 0.03f) byFam.getOrPut(p.family) { mutableListOf() }.add(n) }
        return byFam.mapNotNull { (f, ns) ->
            val top = ns.sortedByDescending { data.points[it.pointIndex].h }.take(60)
            if (top.size < 3) null else f to ExploreModel.hull(top.map { it.x to it.y })
        }
    }
}

/**
 * Explore (template-spec §7.5, brief B): the top-1,250 words laid flat by force, edges inside the
 * subsample, pan/zoom/drag-node, tap → word card, Families contours, Relations lens (edges only
 * around the tapped word, the rest dimmed). The fisheye is not implemented.
 */
@Composable
fun ExploreCanvas(
    ctl: ExploreController,
    families: Boolean,
    relations: Boolean,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = ClavisTheme.palette
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = ClavisTheme.fonts.body, fontSize = 11.sp, color = p.cosmosLabel)
    LaunchedEffect(ctl) {
        if (ctl.layoutDone) return@LaunchedEffect
        val res = withContext(Dispatchers.Default) {
            ExploreModel.layout(ctl.nodes, ctl.edges, tick = { it -> if (it % 4 == 0) { ctl.layoutRev++; yield() } })
        }
        ctl.layoutResult = res
        ctl.layoutDone = true
        ctl.layoutRev++
        if (ctl.lastW > 0) ctl.fit()
    }
    Canvas(
        modifier
            .tagged("explore-canvas")
            .semantics { contentDescription = "Explore — the words nearest your path, laid flat" }
            .pointerInput(ctl) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    ctl.dragging = ctl.hit(down.position)
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.all { it.changedToUpIgnoreConsumed() }) break
                    }
                    ctl.dragging = -1
                }
            }
            .pointerInput(ctl) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val d = ctl.dragging
                    if (d >= 0) {
                        ctl.nodes[d].x += pan.x / ctl.scale; ctl.nodes[d].y += pan.y / ctl.scale
                        ctl.layoutRev++
                    } else {
                        if (zoom != 1f) ctl.zoomAt(zoom, centroid)
                        if (pan != Offset.Zero) ctl.offset += pan
                    }
                }
            }
            .pointerInput(ctl) {
                awaitEachGesture {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type == PointerEventType.Scroll) {
                            val ch = ev.changes.firstOrNull() ?: continue
                            val dy = ch.scrollDelta.y
                            if (dy != 0f) { ctl.zoomAt(if (dy > 0) 1f / 1.12f else 1.12f, ch.position); ev.changes.forEach { it.consume() } }
                        }
                        if (ev.changes.all { it.changedToUpIgnoreConsumed() }) break
                    }
                }
            }
            .pointerInput(ctl) {
                detectTapGestures { pos ->
                    val i = ctl.hit(pos)
                    if (i >= 0) { ctl.selected = i; onPick(ctl.nodes[i].pointIndex) }
                }
            },
    ) {
        @Suppress("UNUSED_VARIABLE") val rev = ctl.layoutRev
        val w = size.width; val h = size.height
        ctl.lastW = w; ctl.lastH = h
        if (!ctl.fitted) ctl.fit(w, h)
        drawRect(p.paper)
        drawCircle(
            Brush.radialGradient(0f to p.cosmosGround, 1f to p.paper, center = Offset(w / 2, h / 2), radius = max(w, h) * 0.72f),
            radius = max(w, h) * 0.72f, center = Offset(w / 2, h / 2),
        )
        val grey = p.cosmosGrey
        val sel = ctl.selected
        val hood: Set<Int> = if (relations && sel >= 0) (ctl.neighbours[sel].orEmpty() + sel).toSet() else emptySet()
        // Families
        if (families) {
            for ((f, hull) in ctl.familyHulls()) {
                if (hull.size < 3) continue
                val col = FieldModel.familyColour(f)
                val path = Path()
                hull.forEachIndexed { i, (x, y) -> val s = ctl.toScreen(x, y); if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y) }
                path.close()
                val pad = 22f
                drawPath(path, col, alpha = 0.07f)
                drawPath(path, col, alpha = 0.07f, style = Stroke(pad * 2, join = StrokeJoin.Round))
                drawPath(path, col, alpha = 0.6f, style = Stroke(1.2f, join = StrokeJoin.Round))
            }
        }
        // Edges
        for ((a, b, type) in ctl.edges) {
            val inHood = a in hood || b in hood
            if (relations && sel >= 0 && !(a == sel || b == sel)) continue
            val col = if (type == EdgeType.BORROWING) p.relBor else p.relKin
            val sa = ctl.toScreen(ctl.nodes[a].x, ctl.nodes[a].y); val sb = ctl.toScreen(ctl.nodes[b].x, ctl.nodes[b].y)
            if ((sa.x < 0 && sb.x < 0) || (sa.x > w && sb.x > w) || (sa.y < 0 && sb.y < 0) || (sa.y > h && sb.y > h)) continue
            drawLine(
                col, sa, sb, strokeWidth = if (inHood) 1.6f else 1f, alpha = if (relations && sel >= 0) 0.85f else 0.2f,
                pathEffect = if (type == EdgeType.BORROWING) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null,
            )
        }
        // Nodes
        val showAllLabels = ctl.scale >= 1.4f
        for ((i, n) in ctl.nodes.withIndex()) {
            val s = ctl.toScreen(n.x, n.y)
            val r = n.size / 2 * ctl.scale
            if (s.x < -r - 60 || s.x > w + r + 60 || s.y < -r - 30 || s.y > h + r + 30) continue
            val q = ctl.data.points[n.pointIndex]
            val dim = relations && sel >= 0 && i !in hood
            val fill = mixColour(grey, q.rgb, n.e)
            drawCircle(fill, max(1.5f, r), s, alpha = (0.5f + 0.5f * n.e) * (if (dim) 0.25f else 1f))
            if (q.h > 0.5f) drawCircle(p.cosmosRing, max(1.5f, r), s, style = Stroke(0.8f), alpha = if (dim) 0.25f else 1f)
            if (i == sel) drawCircle(p.cosmosRing, r + 4f, s, style = Stroke(1.6f))
            val label = showAllLabels || i == sel || i in hood || r >= 6f
            if (label && !dim) {
                val layout = ctl.labels.getOrPut(n.pointIndex) { measurer.measure(q.surface, labelStyle) }
                val tl = Offset(s.x - layout.size.width / 2, s.y + r + 2)
                drawRoundRect(p.cosmosLabelBg, topLeft = Offset(tl.x - 4, tl.y - 1), size = Size(layout.size.width + 8f, layout.size.height + 2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                drawText(layout, topLeft = tl)
            }
        }
    }
}

@Suppress("unused")
private fun keepMeasurer(m: TextMeasurer) = m
