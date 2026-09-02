package io.github.mbaliga.clavis.ui.screens.cosmos

import androidx.compose.ui.graphics.Color
import io.github.mbaliga.clavis.core.ClavisState
import io.github.mbaliga.clavis.core.Corpus
import io.github.mbaliga.clavis.core.EdgeType
import io.github.mbaliga.clavis.core.EtymEntry
import io.github.mbaliga.clavis.core.Family
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.core.LanguageRegistry
import io.github.mbaliga.clavis.core.Scheduler
import io.github.mbaliga.clavis.core.hash01
import io.github.mbaliga.clavis.ui.theme.ClavisTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Cosmos copy (template-spec §7.4, §7.5, §8). */
object CosmosCopy {
    const val FIELD = "Field"
    const val EXPLORE = "Explore"
    const val RESET = "Reset"
    const val CALM = "Calm"
    const val WAKE = "Wake"
    const val FIT = "Fit"
    const val RELATIONS = "Relations"
    const val FAMILIES = "Families"
    const val UNDO = "↶"
    const val REDO = "↷"
    const val UNDO_TITLE = "Undo"
    const val REDO_TITLE = "Redo"
    const val FIT_TITLE = "Fit everything back in view"
    const val RELATIONS_TITLE = "Show only the kinship and borrowing edges around the tapped word"
    const val FAMILIES_TITLE = "Wrap each family's lit words in a contour"
    const val SEARCH_PLACEHOLDER = "find a word…"
    const val FIND = "Find"
    const val HINT_FIELD = "drag to look · pinch or scroll to zoom · tap a speck"
    const val HINT_EXPLORE = "pinch to zoom · drag to pan · tap a word · any speck can be picked up and moved"
    const val ORIGIN_MISSING = "origin not yet in the graph"
    fun holds(p: String, band: String) = " · you hold it at $p · $band"
    const val SEE_WEB = "See its web"
    const val READ_WHERE = "Read where it appears"
    const val REL_BOR = " · borrowed"
    const val REL_KIN = " · kin"
    const val REL_DER = " · derived"
    const val NOT_IN_CORPUS = "Not in your corpus yet"
    const val NOT_DRAWN_HERE = "Not among the words drawn here — the Field holds the rest"
    fun outsideLanes(lang: String, w: String) = "$lang · $w — outside your lanes"
    fun webOf(w: String) = "the web of $w"
    const val WEB_SUB = "tap a word to travel · tap the ground to close"
    const val NODE_ORIGIN = "origin"
    const val LEGEND_1 =
        "Every speck is a word; the field runs past the horizon because the world holds more dialects than any course will list — a language is a dialect with an army and a navy. Direction is the family, pull toward the centre is kinship [32], height and brightness are how well you hold a word, size is how often the world uses it [1]. Grey specks are words the light has not reached yet."
    const val LEGEND_2 =
        "This is the field at dusk — for reading it up close, Explore lays the same words flat and hands you real instruments: a relations lens [38] and family contours [39]. Tap a word here for its own force-drawn web [40]."
    const val LEGEND_EXPLORE =
        "The field from straight above — the words nearest your path, kept legible. Relations shows only the kinship and borrowing edges around the word you tapped [38]. Families wraps each family's lit words in a smooth contour [39]. Drag pans, pinch zooms, and any speck can be picked up and moved."
    const val PRON_WIKTIONARY = "Wiktionary"
    const val SYNTHETIC = "synthetic voice"
    const val PLAY = "Play"
    const val DOWNLOAD_RECORDING = "Download recording"
    const val DOWNLOADING = "downloading…"
    const val DOWNLOAD_FAILED = "The recording could not be fetched — you can try again"
    fun recordingLabel(source: String, licence: String?, by: String?) =
        listOfNotNull("recording", source, licence?.takeIf { it.isNotBlank() }, by?.takeIf { it.isNotBlank() }).joinToString(" · ")
    fun memory(p: String, days: String, reps: Int) = "memory $p · holds ~$days days · met $reps×"
    const val FAM_LABEL_CJK = "CHINA · JAPAN · KOREA"
}

/** One word of the Field: identity, corpus facts, and its fixed 3-D place (`buildCosmos`, template-spec §7.4). */
class FieldPoint(
    val l: Lang, val lm: String, val surface: String, val reading: String, val zipf: Double,
    val x: Float, val z: Float, val yj: Float, val sz: Float, val h: Float, val rgb: Color,
    val ell: Float, val rot: Float, val seed: Float, val hasCard: Boolean, val et: EtymEntry?, val family: Family,
) {
    val id: String get() = "${l.code}|$lm"
    val d: Float get() = hypot(x, z)
}

class FieldEdge(val a: Int, val b: Int, val type: EdgeType)

/** A dust mote: no word, just light (template-spec §7.4 "Dust"). */
class Dust(val x: Float, val z: Float, val col: Color, val al: Float, val sz: Float, val ell: Float, val rot: Float, val seed: Float) {
    val d: Float get() = hypot(x, z)
}

class FamilyLabel(val family: Family, val label: String, val angle: Double, val colour: Color)

class FieldData(
    val points: List<FieldPoint>,
    val edges: List<FieldEdge>,
    val index: Map<String, Int>,
    val dust: List<Dust>,
    val families: List<FamilyLabel>,
) {
    /** Edges touching point [i]. */
    val adjacency: Map<Int, List<FieldEdge>> by lazy {
        val m = HashMap<Int, MutableList<FieldEdge>>()
        for (e in edges) { m.getOrPut(e.a) { mutableListOf() }.add(e); m.getOrPut(e.b) { mutableListOf() }.add(e) }
        m
    }
}

/** The Field's fixed geometry and familiarity, straight from template-spec §7.4 `buildCosmos`. */
object FieldModel {
    const val WAVE_R = 4.6f
    const val H = 0.5f
    const val DUST_DESKTOP = 7200
    const val DUST_MOBILE = 2000

    val FAM_ANGLE: Map<Family, Double> = mapOf(
        Family.ROMANCE to PI * 0.25, Family.GERMANIC to PI * 0.75, Family.CJK to PI * 1.25, Family.SEMITIC to PI * 1.75,
        // D-6 additions sit between the four original sectors
        Family.SLAVIC to PI * 0.5, Family.INDIC to PI * 1.0, Family.TURKIC to PI * 1.5, Family.OTHER to 0.0,
    )
    val LANG_OFF: Map<String, Double> = mapOf(
        "es" to -0.24, "fr" to 0.0, "it" to 0.24, "de" to -0.14, "nl" to 0.14, "ja" to -0.24, "ko" to 0.0, "zh" to 0.24, "ar" to 0.0,
    )
    private val EXTRA_OFF = doubleArrayOf(-0.38, 0.38, -0.5, 0.5, -0.12, 0.12)

    val FAM_LABEL: Map<Family, String> = mapOf(
        Family.ROMANCE to "ROMANCE", Family.GERMANIC to "GERMANIC", Family.CJK to CosmosCopy.FAM_LABEL_CJK, Family.SEMITIC to "SEMITIC",
        Family.SLAVIC to "SLAVIC", Family.INDIC to "INDIC", Family.TURKIC to "TURKIC", Family.OTHER to "OTHER",
    )

    private val ORIGIN_FAM: List<Pair<Family, Regex>> = listOf(
        Family.ROMANCE to Regex("latin|old french|middle french|old spanish|portuguese|vulgar|anglo-norman|italian|spanish|french", RegexOption.IGNORE_CASE),
        Family.GERMANIC to Regex("english|old norse|german|dutch|frankish|gothic|proto-germanic|proto-west", RegexOption.IGNORE_CASE),
        Family.CJK to Regex("chinese|japanese|korean", RegexOption.IGNORE_CASE),
        Family.SEMITIC to Regex("arabic|hebrew|aramaic|proto-semitic|persian|sanskrit|ottoman|turkish|egyptian", RegexOption.IGNORE_CASE),
    )

    private val DUSTCOL = listOf(
        Color(0xFFF2EFE4), Color(0xFFFFFBEF), Color(0xFFFF7043), Color(0xFFB388FF), Color(0xFF5FD97F), Color(0xFF64B0FF), Color(0xFFFFC642),
        Color(0xFFFF5F9E), Color(0xFF2FDFCE), Color(0xFFCE7BFF), Color(0xFF4FC3F7), Color(0xFFE8D45A),
    )

    fun wrapToPi(a: Double): Double {
        var x = (a + PI) % (2 * PI)
        if (x < 0) x += 2 * PI
        return x - PI
    }

    fun familyColour(f: Family): Color = ClavisTokens.familyCol[f.name] ?: Color(0xFFB0B6C6)

    /** `familiarity(l, lm, z, now)` (template L803–807). */
    fun familiarity(state: ClavisState, scheduler: Scheduler, thr: Double, l: Lang, lm: String, z: Double, now: Long): Float {
        val c = state.cards[l.code]?.get(lm)
        if (c != null) {
            if (c.prov) return 0.72f
            if (c.intro) return 0f
            if (c.g == 1) return 0.18f
            return scheduler.cardR(c, now).coerceIn(0.05, 1.0).toFloat()
        }
        return if (z >= thr) 0.55f else 0f
    }

    /**
     * Builds every point of the non-off lanes present in the corpus. Deterministic for a fixed
     * (corpus, state, now): positions depend only on `hash01`, familiarity only on the cards.
     */
    fun build(
        corpus: Corpus, registry: LanguageRegistry, state: ClavisState, scheduler: Scheduler, thrOf: (Lang) -> Double, now: Long,
        dustCount: Int = DUST_DESKTOP, dustLite: Color, includeResting: Boolean = false,
    ): FieldData {
        val points = ArrayList<FieldPoint>()
        val index = HashMap<String, Int>()
        val langs = registry.order.filter { l -> (includeResting || state.laneMode(l) != LaneMode.OFF) && l != Lang.EN && !corpus.byLang[l].isNullOrEmpty() }
        val offs = HashMap<String, Double>()
        for (l in langs) {
            val info = registry[l] ?: continue
            offs[l.code] = LANG_OFF[l.code] ?: run {
                val extras = langs.filter { registry[it]?.family == info.family && it.code !in LANG_OFF }
                EXTRA_OFF[extras.indexOf(l).coerceIn(0, EXTRA_OFF.lastIndex)]
            }
        }
        val familiesSeen = LinkedHashSet<Family>()
        for (l in langs) {
            val info = registry[l] ?: continue
            val fam = info.family
            familiesSeen.add(fam)
            val base = FAM_ANGLE[fam] ?: 0.0
            val off = offs[l.code] ?: 0.0
            val rgb = ClavisTokens.cosmosCol[l.code] ?: ClavisTokens.laneColour(info.colour)
            val lex = corpus.lex[l] ?: continue
            val etym = corpus.etym[l].orEmpty()
            val thr = thrOf(l)
            for ((lm, e) in lex) {
                var ang = base + off + (hash01(l.code + lm) - 0.5) * 0.58
                var rad = 0.62 + (hash01(lm + l.code) - 0.5) * 0.55
                val et = etym[lm]
                val origin = et?.origin
                if (et?.originType == "inh") {
                    rad -= 0.16
                } else if (et?.originType == "bor" && !origin.isNullOrEmpty()) {
                    val that = ORIGIN_FAM.firstOrNull { (_, rx) -> rx.containsMatchIn(origin) }?.first
                    if (that != null && that != fam) {
                        ang += wrapToPi((FAM_ANGLE[that] ?: 0.0) - ang) * 0.42
                        rad = 0.85 + (hash01(lm) - 0.5) * 0.12
                    } else {
                        rad += 0.1
                    }
                }
                val h = familiarity(state, scheduler, thr, l, lm, e.zipf, now)
                val p = FieldPoint(
                    l = l, lm = lm, surface = e.surface.ifEmpty { lm }, reading = e.reading.orEmpty(), zipf = e.zipf,
                    x = (rad * cos(ang)).toFloat(), z = (rad * sin(ang)).toFloat(),
                    yj = ((hash01(lm + "y") - 0.5) * 0.05).toFloat(),
                    sz = (0.85 + (e.zipf - 2) * 0.5).coerceIn(0.7, 3.2).toFloat(),
                    h = h, rgb = rgb,
                    ell = (0.55 + hash01(lm) * 0.45).toFloat(), rot = (hash01(l.code + lm + "r") * PI).toFloat(), seed = (hash01(lm + l.code + "s") * 6.28).toFloat(),
                    hasCard = state.cards[l.code]?.containsKey(lm) == true, et = et, family = fam,
                )
                index[p.id] = points.size
                points.add(p)
            }
        }
        val edges = ArrayList<FieldEdge>()
        for ((i, p) in points.withIndex()) {
            val es = p.et?.edges ?: continue
            for (ed in es) {
                val j = index["${ed.lang.code}|${ed.lemma}"] ?: continue
                if (p.l.code < ed.lang.code || (p.l == ed.lang && p.lm < ed.lemma)) edges.add(FieldEdge(i, j, ed.type))
            }
        }
        val rnd = Random(1969)
        val cols = listOf(dustLite, dustLite) + DUSTCOL.drop(2)
        val dust = List(dustCount) {
            val a = rnd.nextDouble() * 2 * PI
            val r = 3.8 * sqrt(rnd.nextDouble())
            val u = rnd.nextDouble()
            Dust(
                x = (r * cos(a)).toFloat(), z = (r * sin(a)).toFloat(), col = cols[rnd.nextInt(cols.size)],
                al = (0.22 + rnd.nextDouble() * 0.4).toFloat(), sz = (0.5 + u.pow(2.2) * 1.7).toFloat(),
                ell = (0.5 + rnd.nextDouble() * 0.5).toFloat(), rot = (rnd.nextDouble() * PI).toFloat(), seed = (rnd.nextDouble() * 6.28).toFloat(),
            )
        }
        val families = familiesSeen.map { f -> FamilyLabel(f, FAM_LABEL[f] ?: f.name, FAM_ANGLE[f] ?: 0.0, familyColour(f)) }
        return FieldData(points, edges, index, dust, families)
    }

    fun terrainY(x: Float, z: Float, t: Float): Float =
        0.07f * sin(1.5f * x + 0.85f * z + 0.17f * t) + 0.05f * sin(2.3f * z - 1.1f * x + 0.12f * t) + 0.028f * sin(3.7f * x + 2.9f * z - 0.09f * t)

    fun front(wave: Float): Float = wave.pow(2.6f) * WAVE_R * 1.12f
    fun easeOut(u: Float): Float = 1f - (1f - u).pow(3)
    fun litness(wave: Float, d: Float): Float = if (wave >= 1f) 1f else easeOut(((front(wave) - d) / 0.7f).coerceIn(0f, 1f))
    fun crestOf(wave: Float, d: Float): Float = if (wave >= 1f) 0f else exp(-((front(wave) - d) / 0.34f).let { it * it })

    /** Picking: within [radius] px, minimise `d − h·9 − min(zipf,6)·1.2` (template L1158). */
    fun nearest(sx: FloatArray, sy: FloatArray, visible: BooleanArray, points: List<FieldPoint>, px: Float, py: Float, radius: Float): Int {
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (i in points.indices) {
            if (!visible[i]) continue
            val d = hypot(sx[i] - px, sy[i] - py)
            if (d > radius) continue
            val score = d - points[i].h * 9f - min(points[i].zipf, 6.0).toFloat() * 1.2f
            if (score < bestScore) { bestScore = score; best = i }
        }
        return best
    }

    /** First point whose lemma or surface starts with [q] (lowercased). */
    fun find(points: List<FieldPoint>, q: String): Int {
        val s = q.trim().lowercase()
        if (s.isEmpty()) return -1
        return points.indexOfFirst { it.lm.lowercase().startsWith(s) || it.surface.lowercase().startsWith(s) }
    }
}

/** The pseudo-3-D camera (template-spec §7.4 "Camera"): `theta`, `camH`, `camR`, HOME = (−π·0.35, 0.85, 2.4). */
data class Camera(val theta: Float = HOME_THETA, val camH: Float = HOME_H, val camR: Float = HOME_R) {
    val moved: Boolean get() = abs(theta - HOME_THETA) > 0.02f || abs(camH - HOME_H) > 0.02f || abs(camR - HOME_R) > 0.02f
    fun drag(dx: Float, dy: Float) = copy(theta = theta + dx * 0.005f, camH = (camH + dy * 0.006f).coerceIn(0.4f, 2.2f))
    fun zoom(factor: Float) = copy(camR = (camR / factor).coerceIn(1.15f, 4.6f))

    companion object {
        val HOME_THETA: Float = (-PI * 0.35).toFloat()
        const val HOME_H = 0.85f
        const val HOME_R = 2.4f
        val HOME = Camera()
    }
}

/** Projects world points to a `w × h` canvas for one camera; pure and allocation-free per call. */
class Projector(val w: Float, val h: Float, cam: Camera) {
    private val ex = cam.camR * cos(cam.theta)
    private val ey = cam.camH
    private val ez = cam.camR * sin(cam.theta)
    private val fx: Float; private val fy: Float; private val fz: Float
    private val rx: Float; private val ry: Float; private val rz: Float
    private val ux: Float; private val uy: Float; private val uz: Float
    val focal: Float = h * 1.05f

    init {
        var dx = 0f - ex; var dy = 0.1f - ey; var dz = 0f - ez
        val dl = sqrt(dx * dx + dy * dy + dz * dz); dx /= dl; dy /= dl; dz /= dl
        fx = dx; fy = dy; fz = dz
        // r = normalize(f × up), up = (0,1,0) → (−fz, 0, fx)
        var rX = -fz; var rY = 0f; var rZ = fx
        val rl = sqrt(rX * rX + rY * rY + rZ * rZ).takeIf { it > 1e-6f } ?: 1f
        rX /= rl; rY /= rl; rZ /= rl
        rx = rX; ry = rY; rz = rZ
        // u = r × f
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx
    }

    /** Depth of a world point along the view axis; below [MIN_Z] the point is behind the camera. */
    fun depth(x: Float, y: Float, z: Float): Float = (x - ex) * fx + (y - ey) * fy + (z - ez) * fz
    fun screenX(x: Float, y: Float, z: Float, zc: Float): Float = w / 2 + ((x - ex) * rx + (y - ey) * ry + (z - ez) * rz) / zc * focal
    fun screenY(x: Float, y: Float, z: Float, zc: Float): Float = h / 2 - ((x - ex) * ux + (y - ey) * uy + (z - ez) * uz) / zc * focal

    companion object { const val MIN_Z = 0.12f }
}

/** The word's own force-drawn web (template-spec §7.4 "Focus"): root, ancestor, ≤ 8 kin, Fruchterman–Reingold. */
class WebNode(val id: String, val label: String, val kind: Kind, val colour: Color, val pointIndex: Int, val edgeType: EdgeType?) {
    enum class Kind { ROOT, ANCESTOR, KIN }
    var x = 0f
    var y = 0f
    val r: Float get() = when (kind) { Kind.ROOT -> 22f; Kind.ANCESTOR -> 17f; Kind.KIN -> 16f }
}

object WebLayout {
    const val K = 78f
    const val ITER = 420

    fun build(data: FieldData, root: Int, grey: Color, langName: (Lang) -> String): List<WebNode> {
        val p = data.points[root]
        val nodes = ArrayList<WebNode>()
        nodes.add(WebNode(p.id, p.surface, WebNode.Kind.ROOT, p.rgb, root, null))
        p.et?.origin?.takeIf { it.isNotEmpty() }?.let { nodes.add(WebNode("origin", it, WebNode.Kind.ANCESTOR, grey, -1, null)) }
        for (ed in p.et?.edges.orEmpty().take(8)) {
            val j = data.index["${ed.lang.code}|${ed.lemma}"]
            val col = if (j != null) data.points[j].rgb else grey
            val label = if (j != null) data.points[j].surface else ed.lemma
            nodes.add(WebNode("${ed.lang.code}|${ed.lemma}", "$label · ${langName(ed.lang)}", WebNode.Kind.KIN, col, j ?: -1, ed.type))
        }
        layout(nodes)
        return nodes
    }

    /** FR with the template's constants: ring start r 90, k 78, 420 iterations, gravity −0.006, root pinned. */
    fun layout(nodes: List<WebNode>) {
        val n = nodes.size
        if (n == 0) return
        for (i in 1 until n) {
            val a = (i - 1).toDouble() / max(1, n - 1) * 2 * PI
            nodes[i].x = (90 * cos(a)).toFloat(); nodes[i].y = (90 * sin(a)).toFloat()
        }
        nodes[0].x = 0f; nodes[0].y = 0f
        val dx = FloatArray(n); val dy = FloatArray(n)
        for (it in 0 until ITER) {
            val cool = 1f - it.toFloat() / ITER
            dx.fill(0f); dy.fill(0f)
            for (i in 0 until n) for (j in i + 1 until n) {
                var ex = nodes[i].x - nodes[j].x; var ey = nodes[i].y - nodes[j].y
                var d = sqrt(ex * ex + ey * ey); if (d < 0.01f) { d = 0.01f; ex = 0.01f; ey = 0f }
                val f = K * K / d
                dx[i] += ex / d * f; dy[i] += ey / d * f; dx[j] -= ex / d * f; dy[j] -= ey / d * f
            }
            for (j in 1 until n) { // every non-root node is attached to the root
                val ex = nodes[j].x - nodes[0].x; val ey = nodes[j].y - nodes[0].y
                val d = max(0.01f, sqrt(ex * ex + ey * ey))
                val f = d * d / K
                dx[j] -= ex / d * f; dy[j] -= ey / d * f; dx[0] += ex / d * f; dy[0] += ey / d * f
            }
            for (i in 1 until n) {
                dx[i] -= 0.006f * nodes[i].x * K; dy[i] -= 0.006f * nodes[i].y * K
                val len = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
                if (len > 0f) {
                    val step = min(len, 14f * cool)
                    nodes[i].x += dx[i] / len * step; nodes[i].y += dy[i] / len * step
                }
            }
        }
    }
}

/** Explore: the subsample and its force layout (template-spec §7.5; brief B). */
class ExploreNode(val pointIndex: Int, val x0: Float, val y0: Float, val size: Float, val e: Float) {
    var x = x0
    var y = y0
}

object ExploreModel {
    const val CAP = 1250
    const val SCALE = 300f

    fun score(p: FieldPoint): Double =
        p.zipf + (if (p.hasCard) 8.0 else 0.0) + (if (p.et != null && (p.et.edges.isNotEmpty() || !p.et.origin.isNullOrEmpty())) 3.0 else 0.0) + (if (p.h > 0f) 2.0 else 0.0)

    /** Top [cap] points by score; stable on ties (index order). */
    fun subsample(data: FieldData, cap: Int = CAP): List<ExploreNode> {
        val order = data.points.indices.sortedWith(compareByDescending<Int> { score(data.points[it]) }.thenBy { it })
        return order.take(cap).map { i ->
            val p = data.points[i]
            ExploreNode(
                i, (p.x * SCALE).roundToInt().toFloat(), (p.z * SCALE).roundToInt().toFloat(),
                (5 + (p.zipf - 1.5) * 1.6 + p.h * 9).coerceIn(5.0, 19.0).toFloat(), max(p.h, 0.24f),
            )
        }
    }

    /** Edges whose both ends are in the subsample, as pairs of node indices. */
    fun edges(data: FieldData, nodes: List<ExploreNode>): List<Triple<Int, Int, EdgeType>> {
        val pos = HashMap<Int, Int>(); nodes.forEachIndexed { i, n -> pos[n.pointIndex] = i }
        return data.edges.mapNotNull { e -> val a = pos[e.a]; val b = pos[e.b]; if (a != null && b != null) Triple(a, b, e.type) else null }
    }

    class Result(val iterations: Int, val converged: Boolean)

    /**
     * Fruchterman–Reingold with early stop: repulsion `k²/d` (cut off at 6k), attraction `d²/k`
     * along edges, and a gentle pull toward each node's Field position so the family geography
     * survives; stops when the largest move falls under [eps] px. O(n²) per iteration — fine for
     * ≤ 1,250 nodes off the main thread. [tick] lets a caller yield between iterations.
     */
    suspend fun layout(nodes: List<ExploreNode>, edges: List<Triple<Int, Int, EdgeType>>, maxIter: Int = 120, eps: Float = 0.35f, tick: suspend (Int) -> Unit = {}): Result {
        val n = nodes.size
        if (n < 2) return Result(0, true)
        val area = 640f * 640f
        val k = sqrt(area / n)
        val cut2 = (6 * k) * (6 * k)
        val dx = FloatArray(n); val dy = FloatArray(n)
        var temp = k * 1.6f
        for (it in 0 until maxIter) {
            dx.fill(0f); dy.fill(0f)
            for (i in 0 until n) {
                val xi = nodes[i].x; val yi = nodes[i].y
                for (j in i + 1 until n) {
                    var ex = xi - nodes[j].x; var ey = yi - nodes[j].y
                    var d2 = ex * ex + ey * ey
                    if (d2 > cut2) continue
                    if (d2 < 0.01f) { ex = ((i * 7919 + j) % 13 - 6) * 0.01f; ey = ((i + j * 104729) % 11 - 5) * 0.01f; d2 = ex * ex + ey * ey + 0.001f }
                    val d = sqrt(d2)
                    val f = k * k / d
                    val fx = ex / d * f; val fy = ey / d * f
                    dx[i] += fx; dy[i] += fy; dx[j] -= fx; dy[j] -= fy
                }
            }
            for ((a, b, _) in edges) {
                val ex = nodes[a].x - nodes[b].x; val ey = nodes[a].y - nodes[b].y
                val d = max(0.01f, sqrt(ex * ex + ey * ey))
                val f = d * d / k
                val fx = ex / d * f; val fy = ey / d * f
                dx[a] -= fx; dy[a] -= fy; dx[b] += fx; dy[b] += fy
            }
            var maxMove = 0f
            for (i in 0 until n) {
                // anchor to the Field position: strong enough that the family sectors survive the repulsion
                dx[i] += (nodes[i].x0 - nodes[i].x) * 0.06f * k
                dy[i] += (nodes[i].y0 - nodes[i].y) * 0.06f * k
                val len = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
                if (len > 0f) {
                    val step = min(len, temp)
                    nodes[i].x += dx[i] / len * step; nodes[i].y += dy[i] / len * step
                    if (step > maxMove) maxMove = step
                }
            }
            temp = max(0.1f, temp * 0.93f)
            tick(it)
            if (maxMove < eps) return Result(it + 1, true)
        }
        return Result(maxIter, false)
    }

    /** Convex hull (Andrew's monotone chain) of points given as (x, y) pairs; ≥ 3 distinct points. */
    fun hull(pts: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val p = pts.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (p.size < 3) return p
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) = (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Float, Float>>()
        for (q in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], q) <= 0) lower.removeAt(lower.size - 1); lower.add(q) }
        val upper = ArrayList<Pair<Float, Float>>()
        for (q in p.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], q) <= 0) upper.removeAt(upper.size - 1); upper.add(q) }
        return lower.dropLast(1) + upper.dropLast(1)
    }
}

/** Lerp between two colours in sRGB (the template's `mix`). */
fun mixColour(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * u, a.green + (b.green - a.green) * u, a.blue + (b.blue - a.blue) * u, 1f)
}

/** JavaScript-style `Math.floor` helper for stable bucketing. */
internal fun bucket(v: Float, step: Float): Int = floor(v / step).toInt()
