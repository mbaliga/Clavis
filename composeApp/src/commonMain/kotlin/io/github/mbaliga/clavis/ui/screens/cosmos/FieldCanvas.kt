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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mbaliga.clavis.core.EdgeType
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.theme.ClavisPalette
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A camera + selection + focus snapshot for the undo/redo history (cap 40; parity item 66). */
data class FieldSnapshot(val camera: Camera, val selected: Int, val focusRoot: Int)

/**
 * The Field's interaction state, remembered per data set: camera, selection, focus web, the entry
 * wave, the ripple clock, history, and the last projection (for picking and for tests).
 */
@Stable
class FieldController(val data: FieldData, motion: Boolean) {
    var camera by mutableStateOf(Camera.HOME)
    var selected by mutableIntStateOf(-1)
    var focus by mutableStateOf<List<WebNode>?>(null)
    var focusRoot by mutableIntStateOf(-1)
    var wave by mutableFloatStateOf(if (motion) 0f else 1f)
    var time by mutableFloatStateOf(0f)
    private val undo = ArrayDeque<FieldSnapshot>()
    private val redo = ArrayDeque<FieldSnapshot>()
    var histRev by mutableIntStateOf(0)
        private set

    /** Last projection of every word (canvas px), refreshed on each draw. */
    val sx = FloatArray(data.points.size)
    val sy = FloatArray(data.points.size)
    val visible = BooleanArray(data.points.size)
    var lastW = 0f
    var lastH = 0f

    val canUndo: Boolean get() { histRev; return undo.isNotEmpty() }
    val canRedo: Boolean get() { histRev; return redo.isNotEmpty() }

    private fun snapshot() = FieldSnapshot(camera, selected, focusRoot)
    private fun restore(s: FieldSnapshot) { camera = s.camera; selected = s.selected; setFocus(s.focusRoot) }

    fun push() { undo.addLast(snapshot()); while (undo.size > 40) undo.removeFirst(); redo.clear(); histRev++ }
    fun undo() { val s = undo.removeLastOrNull() ?: return; redo.addLast(snapshot()); restore(s); histRev++ }
    fun redo() { val s = redo.removeLastOrNull() ?: return; undo.addLast(snapshot()); restore(s); histRev++ }

    var greyForWeb: Color = Color.Gray
    var langNameForWeb: (io.github.mbaliga.clavis.core.Lang) -> String = { it.code }

    fun setFocus(root: Int) {
        focusRoot = root
        focus = if (root >= 0) WebLayout.build(data, root, greyForWeb, langNameForWeb) else null
    }

    fun select(i: Int, record: Boolean = true) {
        if (record) push()
        selected = i
        wave = 1f
    }

    fun reset() { push(); camera = Camera.HOME }

    /** Projects the world point of word [i] with the current camera for a `w × h` canvas (for tests). */
    fun project(i: Int, w: Float, h: Float): Offset? {
        val p = data.points[i]
        val pr = Projector(w, h, camera)
        val e = FieldModel.litness(wave, p.d)
        val y = (FieldModel.terrainY(p.x, p.z, time) * 0.6f + p.yj + p.h * FieldModel.H + 0.035f * sin(0.5f * time + p.seed)) * e
        val zc = pr.depth(p.x, y, p.z)
        if (zc < Projector.MIN_Z) return null
        return Offset(pr.screenX(p.x, y, p.z, zc), pr.screenY(p.x, y, p.z, zc))
    }
}

/** Growable float pairs per draw bucket. */
private class Bucket(val colour: Color, val alpha: Float, val size: Float) {
    val pts = ArrayList<Offset>(64)
}

/**
 * The Field canvas: pseudo-3-D point cloud with a camera (drag = look, pinch/wheel = zoom), the
 * entry wave, dust, family labels, the selected word's relations, and the focus web overlay.
 * Points are batched by (colour, alpha, size) into `drawPoints` calls. Decorative for assistive
 * tech — the word card and the search box are the accessible carriers.
 */
@Composable
fun FieldCanvas(
    ctl: FieldController,
    reduced: Boolean,
    onPick: (Int) -> Unit,
    onFocusTravel: (Int) -> Unit,
    onFocusClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = ClavisTheme.palette
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = ClavisTheme.fonts.mono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = p.cosmosLabel)
    val webStyle = TextStyle(fontFamily = ClavisTheme.fonts.body, fontSize = 12.sp, color = p.cosmosLabel)
    val titleStyle = TextStyle(fontFamily = ClavisTheme.fonts.display, fontSize = 17.sp, color = p.ink)
    val subStyle = TextStyle(fontFamily = ClavisTheme.fonts.mono, fontSize = 11.sp, color = p.ink3)
    ctl.greyForWeb = p.cosmosGrey

    if (!reduced) {
        LaunchedEffect(ctl) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        ctl.time += 0.016f
                        if (ctl.wave < 1f) ctl.wave = min(1f, ctl.wave + 0.0062f)
                    }
                    last = now
                }
            }
        }
    } else {
        ctl.wave = 1f
    }
    val pickRadius = with(density) { 30.dp.toPx() }

    Canvas(
        modifier
            .tagged("field-canvas")
            .semantics { contentDescription = "the Field — every word you have touched, as specks" }
            .pointerInput(ctl) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    ctl.wave = 1f
                }
            }
            .pointerInput(ctl) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (pan != Offset.Zero || zoom != 1f) ctl.camera = ctl.camera.drag(pan.x, pan.y).zoom(zoom)
                }
            }
            .pointerInput(ctl) {
                awaitEachGesture {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type == PointerEventType.Scroll) {
                            val dy = ev.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy != 0f) { ctl.camera = ctl.camera.zoom(if (dy > 0) 1f / 1.12f else 1.12f); ev.changes.forEach { it.consume() } }
                        }
                        if (ev.changes.all { it.changedToUpIgnoreConsumed() }) break
                    }
                }
            }
            .pointerInput(ctl) {
                detectTapGestures { pos ->
                    val focus = ctl.focus
                    if (focus != null) {
                        val w = ctl.lastW; val h = ctl.lastH
                        val scale = min(1f, min(w, h) * 0.42f / 110f)
                        val hit = focus.firstOrNull { n -> hypot(pos.x - (w / 2 + n.x * scale), pos.y - (h / 2 + n.y * scale)) <= n.r * scale + 6 }
                        if (hit != null && hit.kind == WebNode.Kind.KIN && hit.pointIndex >= 0) onFocusTravel(hit.pointIndex)
                        else if (hit == null || hit.kind != WebNode.Kind.ROOT) onFocusClose()
                        return@detectTapGestures
                    }
                    val i = FieldModel.nearest(ctl.sx, ctl.sy, ctl.visible, ctl.data.points, pos.x, pos.y, pickRadius)
                    if (i >= 0) onPick(i)
                }
            },
    ) {
        val w = size.width; val h = size.height
        ctl.lastW = w; ctl.lastH = h
        val wave = ctl.wave
        val t = ctl.time
        val cam = ctl.camera
        val pr = Projector(w, h, cam)
        drawRect(p.paper)
        drawCircle(
            Brush.radialGradient(0f to p.cosmosGround, 1f to p.paper, center = Offset(w / 2, h * 0.48f), radius = max(w, h) * 0.72f),
            radius = max(w, h) * 0.72f, center = Offset(w / 2, h * 0.48f),
        )
        val data = ctl.data
        val grey = p.cosmosGrey
        val crestCol = p.cosmosCrest
        val buckets = HashMap<Long, Bucket>()
        fun bucketFor(colour: Color, alpha: Float, sz: Float): Bucket {
            val aq = (alpha * 12).toInt().coerceIn(0, 12)
            val sq = (sz * 2).toInt().coerceIn(0, 12)
            val key = (colour.value shr 32).toLong() xor (aq.toLong() shl 40) xor (sq.toLong() shl 48)
            return buckets.getOrPut(key) { Bucket(colour, aq / 12f, sq / 2f) }
        }
        val colourCache = HashMap<Int, Color>()
        fun paint(rgb: Color, e: Float, crest: Float): Color {
            val eq = (e * 6).toInt().coerceIn(0, 6); val cq = (crest * 4).toInt().coerceIn(0, 4)
            val k = (rgb.hashCode() * 31 + eq) * 31 + cq
            return colourCache.getOrPut(k) {
                val a = mixColour(grey, rgb, eq / 6f)
                if (cq == 0) a else mixColour(a, crestCol, cq / 4f * 0.8f)
            }
        }
        // dust
        for (m in data.dust) {
            val e = FieldModel.litness(wave, m.d)
            val crest = FieldModel.crestOf(wave, m.d)
            val y = FieldModel.terrainY(m.x, m.z, t) * e
            val zc = pr.depth(m.x, y, m.z)
            if (zc < Projector.MIN_Z) continue
            val x = pr.screenX(m.x, y, m.z, zc); val yy = pr.screenY(m.x, y, m.z, zc)
            if (x < -4 || x > w + 4 || yy < -4 || yy > h + 4) continue
            val fog = ((zc - 0.5f) / 4.6f).coerceIn(0f, 0.72f)
            val alpha = ((0.3f + (m.al - 0.3f) * e + crest * 0.55f) * (1f - fog)).coerceIn(0f, 1f)
            val sz = (m.sz * pr.focal * 0.0017f / zc * 3.2f).coerceIn(0.5f, 3.6f) * (0.45f + 0.55f * e)
            bucketFor(paint(m.col, e, crest), alpha, sz).pts.add(Offset(x, yy))
        }
        // words
        val pts = data.points
        for (i in pts.indices) {
            val q = pts[i]
            val e = FieldModel.litness(wave, q.d)
            val crest = FieldModel.crestOf(wave, q.d)
            val y = (FieldModel.terrainY(q.x, q.z, t) * 0.6f + q.yj + q.h * FieldModel.H + 0.035f * sin(0.5f * t + q.seed)) * e
            val zc = pr.depth(q.x, y, q.z)
            if (zc < Projector.MIN_Z) { ctl.visible[i] = false; continue }
            val x = pr.screenX(q.x, y, q.z, zc); val yy = pr.screenY(q.x, y, q.z, zc)
            ctl.sx[i] = x; ctl.sy[i] = yy
            if (x < -6 || x > w + 6 || yy < -6 || yy > h + 6) { ctl.visible[i] = false; continue }
            ctl.visible[i] = true
            val fog = ((zc - 0.5f) / 4.6f).coerceIn(0f, 0.72f)
            val hcurve = q.h.pow(1.15f)
            val alpha = ((0.38f + 0.62f * hcurve * e + crest * 0.55f) * (1f - fog)).coerceIn(0.12f, 1f)
            val sz = (q.sz * pr.focal * 0.0017f / zc * 3.4f * (0.8f + 0.5f * q.h * e)).coerceIn(0.6f, 5.5f) * (0.45f + 0.55f * e)
            bucketFor(paint(q.rgb, e, crest), alpha, sz).pts.add(Offset(x, yy))
        }
        for (b in buckets.values) {
            drawPoints(b.pts, PointMode.Points, b.colour, strokeWidth = max(0.8f, b.size), cap = StrokeCap.Round, alpha = b.alpha)
        }
        // family labels
        if (wave >= 1f) {
            for (f in data.families) {
                val fx = (0.62 * cos(f.angle)).toFloat(); val fz = (0.62 * sin(f.angle)).toFloat()
                val zc = pr.depth(fx, 0.46f, fz)
                if (zc < Projector.MIN_Z) continue
                val fog = ((zc - 0.5f) / 4.6f).coerceIn(0f, 0.72f)
                val layout = measurer.measure(f.label, labelStyle)
                drawText(layout, topLeft = Offset(pr.screenX(fx, 0.46f, fz, zc) - layout.size.width / 2, pr.screenY(fx, 0.46f, fz, zc) - layout.size.height / 2), alpha = 0.62f * (1f - fog))
            }
        }
        // selection: relation lines + ring
        val sel = ctl.selected
        if (sel >= 0 && sel < pts.size && ctl.visible[sel]) {
            val c = Offset(ctl.sx[sel], ctl.sy[sel])
            for (ed in data.adjacency[sel].orEmpty()) {
                val j = if (ed.a == sel) ed.b else ed.a
                if (!ctl.visible[j]) continue
                val col = if (ed.type == EdgeType.BORROWING) p.relBor else p.relKin
                drawLine(col, c, Offset(ctl.sx[j], ctl.sy[j]), strokeWidth = 1.2f, alpha = 0.8f)
                drawCircle(col, 4.5f, Offset(ctl.sx[j], ctl.sy[j]))
            }
            drawCircle(p.cosmosRing, 8f, c, style = Stroke(1.6f))
        }
        // focus web
        val focus = ctl.focus
        if (focus != null) drawWeb(focus, p, w, h, measurer, webStyle, titleStyle, subStyle, ctl.data.points.getOrNull(ctl.focusRoot)?.surface ?: "")
    }
}

private fun DrawScope.drawWeb(
    nodes: List<WebNode>, p: ClavisPalette, w: Float, h: Float,
    measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle, titleStyle: TextStyle, subStyle: TextStyle, rootWord: String,
) {
    drawRect(p.cosmosGround, alpha = 0.82f)
    val scale = min(1f, min(w, h) * 0.42f / 110f)
    val cx = w / 2; val cy = h / 2
    val root = nodes.firstOrNull() ?: return
    for (n in nodes.drop(1)) {
        val col = if (n.kind == WebNode.Kind.ANCESTOR) p.cosmosGrey else if (n.edgeType == EdgeType.BORROWING) p.relBor else p.relKin
        val dashed = n.edgeType == EdgeType.BORROWING
        drawLine(
            col, Offset(cx + root.x * scale, cy + root.y * scale), Offset(cx + n.x * scale, cy + n.y * scale), strokeWidth = 1.4f,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null,
        )
    }
    for (n in nodes) {
        val c = Offset(cx + n.x * scale, cy + n.y * scale)
        drawCircle(n.colour, n.r * scale, c, alpha = 0.9f)
        drawCircle(p.cosmosRing, n.r * scale, c, style = Stroke(1f), alpha = 0.5f)
        val maxChars = if (n.kind == WebNode.Kind.ANCESTOR) 20 else 13
        val label = if (n.label.length > maxChars) n.label.take(maxChars - 1) + "…" else n.label
        val layout = measurer.measure(label, style)
        drawRect(p.cosmosLabelBg, topLeft = Offset(c.x - layout.size.width / 2 - 3, c.y + n.r * scale + 2), size = androidx.compose.ui.geometry.Size(layout.size.width + 6f, layout.size.height.toFloat()))
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2, c.y + n.r * scale + 2))
        if (n.kind == WebNode.Kind.ANCESTOR) {
            val sub = measurer.measure(CosmosCopy.NODE_ORIGIN, subStyle)
            drawText(sub, topLeft = Offset(c.x - sub.size.width / 2, c.y + n.r * scale + 2 + layout.size.height))
        }
    }
    val title = measurer.measure(CosmosCopy.webOf(rootWord), titleStyle)
    drawText(title, topLeft = Offset(14f, 12f))
    val sub = measurer.measure(CosmosCopy.WEB_SUB, subStyle)
    drawText(sub, topLeft = Offset(14f, 12f + title.size.height + 2))
}
