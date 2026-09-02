package io.github.mbaliga.clavis.core

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.floor

/*
 * Small pure helpers shared by the engines, the state store and the UI (template-spec §1.5, §4,
 * §7.4, §9, §10). Everything here is deterministic and allocation-light.
 */

/** One day in milliseconds (`DAY`, template L368). */
const val DAY_MS: Long = 86_400_000L

/** A Zipf band of the baseline test: `lo <= z < hi` (template L366). */
data class BandDef(val lo: Double, val hi: Double, val lab: String)

/** `BANDS`, highest band first — the order the threshold walk uses (template-spec §4.3). */
val BANDS: List<BandDef> = listOf(
    BandDef(6.0, 9.0, "the ~500 commonest words"),
    BandDef(5.0, 6.0, "the top ~2,000"),
    BandDef(4.0, 5.0, "the top ~10,000"),
    BandDef(3.0, 4.0, "the top ~50,000"),
    BandDef(2.0, 3.0, "rare words"),
)

/** Label of the band containing [z], else `nothing yet` (template L512). */
fun bandLabel(z: Double): String {
    for (b in BANDS) if (z >= b.lo && z < b.hi) return b.lab
    return Copy.NOTHING_YET
}

/**
 * FNV-1a over UTF-16 code units with 32-bit wrap-around, exactly like the web build's
 * `hash01` (template L801: `h ^= charCodeAt(i); h = Math.imul(h, 16777619)`), then
 * `((h >>> 0) % 1000) / 1000`. The Cosmos point positions depend on it (parity item 59).
 */
fun hash01(s: String): Double {
    var h = 2166136261L.toInt() // 0x811C9DC5 as a wrapped Int
    for (ch in s) {
        h = h xor ch.code
        h *= 16777619 // Int multiplication wraps like Math.imul
    }
    return ((h.toLong() and 0xFFFF_FFFFL) % 1000L) / 1000.0
}

/** JavaScript `Math.round`: halves round toward +∞. */
fun jsRound(x: Double): Long = floor(x + 0.5).toLong()

/** `pct(x)` = `Math.round(x*100) + '%'` (template L513). */
fun pct(x: Double): String = "${jsRound(x * 100)}%"

/**
 * Sibling scenes on the same page that have a line in [lang], in reading order — the balloons
 * `letterPage` draws around the current one (template L1700; template-spec §10).
 */
fun pageOf(corpus: Corpus, sc: Scene, lang: Lang): List<Scene> =
    corpus.byPage[sc.m].orEmpty().map(corpus::scene).filter { !it.x[lang].isNullOrEmpty() }

/**
 * Re-joins token surfaces into a caption: a space between tokens unless the language is written
 * without spaces (ja/zh; template L1816 `LANGS[l].t!=='cjk' || l==='ko'`). Driven by the
 * registry's `spaceSeparated`; falls back to the v0.10 rule for a language the registry lacks.
 */
fun tokenJoin(registry: LanguageRegistry, lang: Lang, tokens: List<Token>): String {
    val spaced = registry[lang]?.spaceSeparated ?: (lang.code != "ja" && lang.code != "zh")
    return tokens.joinToString(if (spaced) " " else "") { it.surface }
}

/** Wall-clock [Clock] for production; tests inject a fixed one. */
object SystemClock : Clock {
    override fun now(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}

/**
 * Local, unpadded `Y-M-D` day key exactly like `todayKey()` (template L413:
 * `getFullYear()+'-'+(getMonth()+1)+'-'+getDate()`), evaluated in [zone].
 */
class LocalDayKey(private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }) : DayKey {
    override fun today(now: Long): String {
        val d = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone())
        return "${d.year}-${d.monthNumber}-${d.dayOfMonth}"
    }
}

/**
 * `freshState()` (template L401–406, template-spec §1.2) generalised over the registry (D-6):
 * every registry language gets its per-lane maps; the lane mode is the registry default for
 * languages enabled by default and OFF otherwise. `tests`, `seen` and `log` are not per lane.
 */
fun freshState(registry: LanguageRegistry, now: Long): ClavisState {
    val s = ClavisState(created = now)
    for (info in registry.all) {
        val l = info.iso.code
        s.lanes[l] = if (info.enabledByDefault) info.defaultLane else LaneMode.OFF
        s.cards[l] = mutableMapOf()
        s.bandit[l] = BanditState(w = 0.2)
        s.inter[l] = mutableMapOf()
        s.routes[l] = mutableMapOf()
        s.newToday[l] = 0
    }
    return s
}
