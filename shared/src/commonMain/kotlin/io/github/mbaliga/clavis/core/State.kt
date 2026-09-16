package io.github.mbaliga.clavis.core

import kotlinx.serialization.Serializable

/*
 * Persisted learner state — one document per profile (D-12). Field names deliberately match the
 * web build's `S` (template-spec §1.2/§1.3) so a web export imports as-is. Everything is optional
 * with a default so that partial/older documents load (merge-on-load semantics, §1.4).
 */

/** FSRS card for one (language, lemma). Field names match the web build exactly (template-spec §1.6). */
@Serializable
data class Card(
    /** Stability in days; 0 until first real rating. */
    var s: Double = 0.0,
    /** Difficulty 1..10; 0 until first real rating. */
    var d: Double = 0.0,
    var due: Long = 0,
    var last: Long = 0,
    var reps: Int = 0,
    var lapses: Int = 0,
    /** Last grade 1..4. */
    var g: Int = 0,
    /** Time of the first real rating; 0 = never rated. */
    var first: Long = 0,
    /** Provisional / presumed-known (from the baseline test or a "know this" flip). */
    var prov: Boolean = false,
    /** Introduced-but-unrated placeholder (denied above threshold in the baseline test). */
    var intro: Boolean = false,
)

@Serializable
data class Settings(
    var retention: Double = 0.9,
    var newPerDay: Int = 10,
    var sessionSize: Int = 5,
    var readings: Boolean = true,
    var monoSession: Boolean = false,
    var motion: Boolean = true,
    /** UI language of the app chrome (BCP-47); null = follow system. */
    var uiLocale: String? = null,
    /** Preferred TTS provider id per language ("piper:es_ES-davefx-medium", "system"), D-8. */
    var voices: MutableMap<String, String> = mutableMapOf(),
)

@Serializable
data class Band(val lo: Double, val hi: Double, val lab: String, val n: Int, val hit: Double? = null)

/**
 * Baseline-test result (template-spec §1.8). [pool] names the candidate-filter rule that produced
 * it (`"unicode"`, D-19); null for results imported from the web build, whose ASCII-only pool
 * makes the threshold not numerically comparable.
 */
@Serializable
data class TestResult(val done: Long, val thr: Double, val fa: Double, val bands: List<Band>, val ms: Long, val pool: String? = null)

@Serializable
data class BanditState(var w: Double = 0.2, val hist: MutableList<Double> = mutableListOf())

/** One line of the interaction log (template-spec §1.7). Unused fields stay null. */
@Serializable
data class LogEntry(
    val t: Long,
    val l: String,
    val type: String,
    val sc: Int? = null,
    val lm: String? = null,
    val ok: Int? = null,
    val ms: Long? = null,
    val sib: String? = null,
    val g: Int? = null,
    val unk: Int? = null,
    val thr: Double? = null,
    val fa: Double? = null,
    val guess: String? = null,
)

@Serializable
data class Intent(val l: String, val sc: Int? = null)

@Serializable
data class LastSession(val t: Long, val correct: Int, val n: Int, val newWords: Int, val lanes: List<String>)

@Serializable
data class PathLogEntry(val t: Long, val kind: String, val l: String? = null, val title: String)

@Serializable
data class StoryState(var lane: String = "es", var ep: Int? = null, var pi: Int = 0)

@Serializable
data class CosmosState(
    var mode: String = "depth",
    var exlens: String = "off",
    var exsets: Boolean = false,
    var v3: Boolean = true,
    var webFor: String? = null,
)

/** The whole persisted document. Keys are language codes as plain strings for JSON compatibility. */
@Serializable
data class ClavisState(
    var v: Int = 3,
    var created: Long = 0,
    var lanes: MutableMap<String, LaneMode> = mutableMapOf(),
    var cards: MutableMap<String, MutableMap<String, Card>> = mutableMapOf(),
    var tests: MutableMap<String, TestResult> = mutableMapOf(),
    var seen: MutableMap<String, Long> = mutableMapOf(),
    var bandit: MutableMap<String, BanditState> = mutableMapOf(),
    var log: MutableList<LogEntry> = mutableListOf(),
    var inter: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
    var routes: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
    var newDay: String = "",
    var newToday: MutableMap<String, Int> = mutableMapOf(),
    var pin: String? = null,
    var settings: Settings = Settings(),
    var coach: MutableMap<String, Boolean> = mutableMapOf(),
    var ob: Int = 0,
    var onboarded: Boolean = false,
    var view: String = "learn",
    // lazily-created in the web build; optional here
    var intent: Intent? = null,
    var lastScene: MutableMap<String, Int> = mutableMapOf(),
    var lastSession: LastSession? = null,
    var pathLog: MutableList<PathLogEntry> = mutableListOf(),
    var pathView: String = "tree",
    var story: StoryState = StoryState(),
    var cosmos: CosmosState = CosmosState(),
    var refTarget: Int? = null,
    var letterTab: String = "ja",
    var kanaSet: String = "hira",
    /** Media the user imported (D-7 `user-media`), by id. */
    var media: MutableMap<String, ImportedMedia> = mutableMapOf(),
) {
    fun cardsOf(l: Lang): MutableMap<String, Card> = cards.getOrPut(l.code) { mutableMapOf() }
    fun banditOf(l: Lang): BanditState = bandit.getOrPut(l.code) { BanditState() }
    fun laneMode(l: Lang): LaneMode = lanes[l.code] ?: LaneMode.OFF
}

/** A text the user brought in (lyrics, subtitles, plain text) for out-of-comic reading (D-7). */
@Serializable
data class ImportedMedia(
    val id: String,
    val title: String,
    val lang: String,
    val addedAt: Long,
    /** "txt" | "srt" | "lrc" */
    val kind: String,
    val lines: List<String>,
)

/** Persistence boundary: platform actuals write atomically to the app data dir (D-12). */
interface StateStore {
    suspend fun load(): ClavisState?
    suspend fun save(state: ClavisState)
    suspend fun exportJson(): String
    /** Accepts a web-build export (requires a `cards` key) or a native export. */
    suspend fun importJson(json: String): ClavisState
    suspend fun reset()
}

/** Injected time source so engines are deterministic in tests. */
fun interface Clock {
    fun now(): Long
}

/** Local calendar day key, unpadded `Y-M-D` like the web build (template-spec §1.5). */
fun interface DayKey {
    fun today(now: Long): String
}
