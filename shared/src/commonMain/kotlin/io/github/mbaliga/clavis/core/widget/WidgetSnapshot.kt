package io.github.mbaliga.clavis.core.widget

import io.github.mbaliga.clavis.core.Card
import io.github.mbaliga.clavis.core.EdgeType
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Mask
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.SceneStats
import io.github.mbaliga.clavis.core.engine.ClavisEngine
import io.github.mbaliga.clavis.core.hash01
import io.github.mbaliga.clavis.core.lineOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * What the Android home-screen widgets read. The app is the only writer of the learner document
 * (the UI thread mutates it in place), and the corpus behind coverage, cloze lines and etymology is
 * 20 MB of packs a widget cannot load in its ten-second broadcast window. So the app publishes this
 * small document whenever it leaves the foreground, built from the live engine, and the widgets
 * draw from it alone. It is a projection of `clavis-state.json` plus the packs, never an input
 * back into them.
 *
 * Time-dependent counts stay true without the app: each lane carries the times its cards fall due,
 * so a widget redrawn later counts what is due *then*. Values that decay with retrievability
 * (words held, bubbles readable) are as of [WidgetSnapshot.at], and the widgets print that time.
 */

/** Schema version of [WidgetSnapshot]; a widget meeting a newer one shows its "could not read" state. */
const val WIDGET_SNAPSHOT_VERSION: Int = 1

@Serializable
data class WidgetSnapshot(
    val v: Int = WIDGET_SNAPSHOT_VERSION,
    /** When the app built this, epoch ms. */
    val at: Long,
    val onboarded: Boolean,
    /** Active lanes (not resting and present in the corpus), in registry order. */
    val lanes: List<LaneGlance> = emptyList(),
    /** The card that is first in line across the active lanes: due now, or the next to fall due. */
    val word: DueWord? = null,
    /** `state.lastSession`, with language names resolved. */
    val last: LastDip? = null,
    /** Candidates for the word-origin widget, which shows one per local day. */
    val words: List<OriginWord> = emptyList(),
    /** True when [words] are words from the learner's own cards; false when they are common words of the active lanes. */
    val wordsMet: Boolean = false,
    /** The word-origin widget's word for one local day, carried over when the app publishes again that day. */
    val originPin: OriginPin? = null,
)

/**
 * The word of the day already shown on local day [day] (epoch days), by lane and lemma. The
 * candidate list is rebuilt on every publish and grows as the learner meets words, so without this
 * a second publish within the day could move the rotation to another word.
 */
@Serializable
data class OriginPin(val day: Long, val l: String, val lemma: String)

@Serializable
data class LaneGlance(
    val l: String,
    val name: String,
    /** Lane identity colour, `0xAARRGGBB` from the registry. Decoration only: the name is always printed beside it. */
    val colour: Long,
    val rtl: Boolean = false,
    /** Cards due at [WidgetSnapshot.at], counted exactly like `ClavisEngine.dueCards` (not provisional, not introduced-only). */
    val dueNow: Int = 0,
    /** For every further card, the minutes after [WidgetSnapshot.at] at which it falls due (rounded up), ascending. */
    val laterMin: List<Int> = emptyList(),
    /** True when more cards fall due after the last entry of [laterMin] than the list holds. */
    val moreLater: Boolean = false,
    /** `knownCount` at [WidgetSnapshot.at]. */
    val held: Int = 0,
    /** `corpusCoverage` at [WidgetSnapshot.at]: share of this lane's bubbles readable at the 95% threshold. */
    val readable: Double = 0.0,
    val tested: Boolean = false,
)

@Serializable
data class DueWord(
    val l: String,
    val lemma: String,
    val due: Long,
    /** A line holding the word with the word gapped (`Mask.forCloze`), or null when no line can be gapped safely. */
    val prompt: String? = null,
    val rtl: Boolean = false,
)

@Serializable
data class LastDip(val t: Long, val correct: Int, val n: Int, val newWords: Int, val languages: List<String>)

@Serializable
data class OriginWord(
    val l: String,
    val lemma: String,
    val surface: String,
    /** Romanisation, only when the learner keeps readings on (`settings.readings`). */
    val reading: String? = null,
    val rtl: Boolean = false,
    val lang: String,
    /** Display-ready origin from the etymology pack ("Latin casa"); never invented (D-5, R-10). */
    val origin: String,
    val borrowed: Boolean,
    /** The record's provenance (`EtymEntry.source`: "wiktionary", "etymdb" or both joined by "+"). */
    val source: String? = null,
    val relations: List<Relation> = emptyList(),
)

/** One etymological relation of an [OriginWord]; [kind] is one of [RelationKind]. */
@Serializable
data class Relation(val surface: String, val lang: String, val kind: String, val rtl: Boolean = false)

/** The values of [Relation.kind], the Cosmos word card's three edge words. */
object RelationKind {
    const val KIN = "kin"
    const val BORROWED = "borrowed"
    const val DERIVED = "derived"
}

/** Builds, encodes and decodes [WidgetSnapshot]s. */
object WidgetSnapshots {
    const val MINUTE_MS: Long = 60_000L

    /** At most this many future due times per lane; beyond it the lane says "at least". */
    const val MAX_LATER: Int = 600

    /** Word-origin candidates kept (two weeks of daily rotation). */
    const val MAX_WORDS: Int = 14

    /** Common words taken per lane when the learner has met none with a recorded origin. */
    const val COMMON_PER_LANE: Int = 3

    const val MAX_RELATIONS: Int = 3

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(s: WidgetSnapshot): String = json.encodeToString(WidgetSnapshot.serializer(), s)

    /** Null when [text] is not a snapshot this build can read (corrupt, or written by a newer schema). */
    fun decode(text: String): WidgetSnapshot? {
        val s = runCatching { json.decodeFromString(WidgetSnapshot.serializer(), text) }.getOrNull() ?: return null
        return s.takeIf { it.v in 1..WIDGET_SNAPSHOT_VERSION }
    }

    /**
     * The snapshot of [engine]'s live document at [now]. Call it on the thread that owns the
     * document (the UI thread): it reads the cards, the corpus and the registry, and mutates nothing.
     */
    fun build(engine: ClavisEngine, now: Long): WidgetSnapshot {
        val st = engine.state
        val lanes = engine.activeLanes()
        val (words, met) = originWords(engine, lanes)
        return WidgetSnapshot(
            at = now,
            onboarded = st.onboarded,
            lanes = lanes.map { laneGlance(engine, it, now) },
            word = firstInLine(engine, lanes, now),
            last = st.lastSession?.let { ls ->
                LastDip(ls.t, ls.correct, ls.n, ls.newWords, ls.lanes.distinct().map { engine.langName(Lang(it)) })
            },
            words = words,
            wordsMet = met,
        )
    }

    /** Cards that take part in review, exactly as `dueCards` filters them. */
    private fun reviewable(c: Card): Boolean = !c.prov && !c.intro

    /**
     * [next] with the word the [previous] snapshot shows on local day [day] pinned for the rest of
     * that day, as long as it is still among [next]'s candidates. A word that is no longer a
     * candidate (the first met word replacing the common words) gives way to the rotation.
     */
    fun keepTodaysOrigin(next: WidgetSnapshot, previous: WidgetSnapshot?, day: Long): WidgetSnapshot {
        val shown = previous?.let { WidgetFaces.originOfTheDay(it, day) } ?: return next
        if (next.words.none { it.l == shown.l && it.lemma == shown.lemma }) return next
        return next.copy(originPin = OriginPin(day, shown.l, shown.lemma))
    }

    /** Minutes from [from] to [to], rounded up so a card is never counted due early. */
    fun minutesUntil(from: Long, to: Long): Int {
        val ms = to - from
        if (ms <= 0) return 0
        return ((ms + MINUTE_MS - 1) / MINUTE_MS).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun laneGlance(engine: ClavisEngine, l: Lang, now: Long): LaneGlance {
        val cards = engine.state.cards[l.code].orEmpty().values.filter(::reviewable)
        val later = cards.filter { it.due > now }.map { minutesUntil(now, it.due) }.sorted()
        val info = engine.registry[l]
        return LaneGlance(
            l = l.code,
            name = engine.langName(l),
            colour = info?.colour ?: 0xFF8B8F9CL,
            rtl = info?.rtl == true,
            dueNow = cards.count { it.due <= now },
            laterMin = later.take(MAX_LATER),
            moreLater = later.size > MAX_LATER,
            held = engine.knownCount(l, now),
            readable = engine.corpusCoverage(l, now),
            tested = engine.state.tests[l.code] != null,
        )
    }

    /**
     * The card a lane-held session for its lane would serve first: per lane the earliest-due
     * reviewable card in the document's order (what `dueCards(l).first()` is once it is due), then
     * the earliest across lanes, registry order breaking ties.
     */
    private fun firstInLine(engine: ClavisEngine, lanes: List<Lang>, now: Long): DueWord? {
        var best: Triple<Lang, String, Long>? = null
        for (l in lanes) {
            val first = engine.state.cards[l.code].orEmpty().entries
                .filter { reviewable(it.value) }
                .minByOrNull { it.value.due } ?: continue
            if (best == null || first.value.due < best.third) best = Triple(l, first.key, first.value.due)
        }
        val (l, lemma, due) = best ?: return null
        return DueWord(l.code, lemma, due, prompt(engine, l, lemma, now), engine.registry[l]?.rtl == true)
    }

    /**
     * The gapped line the cloze would most likely use: the lemma's scenes ranked as `pickForLane`
     * ranks them (coverage, then fewest unknowns, then not seen recently), ties to the first. Null
     * when the lemma has no scene or the line cannot be gapped without showing the word — the
     * cloze integrity rule (05-information-architecture §4.2), applied a step stricter: a line that
     * still contains the word after gapping is not shown either.
     */
    private fun prompt(engine: ClavisEngine, l: Lang, lemma: String, now: Long): String? {
        val ids = engine.corpus.byLemma[l]?.get(lemma).orEmpty().distinct()
        if (ids.isEmpty()) return null
        val sc = ids.map { id -> engine.corpus.scene(id).let { it to engine.sceneStats(l, it, now) } }
            .sortedWith(
                compareByDescending<Pair<Scene, SceneStats>> { it.second.cov }
                    .thenBy { it.second.unk.size }
                    .thenBy { if (engine.seenRecently(it.first.i, now)) 1 else 0 },
            ).first().first
        val masked = Mask.forCloze(sc, l, lemma, revealed = false)
        if (masked.isNullOrEmpty()) return null
        val surface = sc.k[l]?.firstOrNull { it.lemma == lemma }?.surface ?: return null
        if (masked.contains(surface, ignoreCase = true)) return null
        return lineOf(l, masked)
    }

    private fun originWords(engine: ClavisEngine, lanes: List<Lang>): Pair<List<OriginWord>, Boolean> {
        val corpus = engine.corpus
        fun hasOrigin(l: Lang, lm: String) = !corpus.etym[l]?.get(lm)?.origin.isNullOrEmpty()
        val met = lanes.flatMap { l ->
            engine.state.cards[l.code].orEmpty().filter { (lm, c) -> (c.reps > 0 || c.prov) && hasOrigin(l, lm) }.keys.map { l to it }
        }
        val picked = if (met.isNotEmpty()) {
            met
        } else {
            lanes.flatMap { l ->
                corpus.etym[l].orEmpty().keys
                    .filter { lm -> hasOrigin(l, lm) && corpus.lex[l]?.containsKey(lm) == true }
                    .sortedByDescending { lm -> corpus.lex[l]?.get(lm)?.zipf ?: 0.0 }
                    .take(COMMON_PER_LANE)
                    .map { l to it }
            }
        }
        // A stable spread across lanes: the same words keep their places while the set grows.
        val words = picked.sortedBy { (l, lm) -> hash01("${l.code}|$lm") }.take(MAX_WORDS).map { (l, lm) -> originWord(engine, l, lm) }
        return words to met.isNotEmpty()
    }

    private fun originWord(engine: ClavisEngine, l: Lang, lm: String): OriginWord {
        val corpus = engine.corpus
        val et = corpus.etym.getValue(l).getValue(lm)
        val lex = corpus.lex[l]?.get(lm)
        return OriginWord(
            l = l.code,
            lemma = lm,
            surface = lex?.surface?.ifEmpty { null } ?: lm,
            reading = lex?.reading?.takeIf { it.isNotEmpty() && engine.state.settings.readings },
            rtl = engine.registry[l]?.rtl == true,
            lang = engine.langName(l),
            origin = et.origin.orEmpty(),
            borrowed = et.originType == "bor",
            source = et.source,
            relations = et.edges.take(MAX_RELATIONS).map { ed ->
                Relation(
                    surface = corpus.lex[ed.lang]?.get(ed.lemma)?.surface?.ifEmpty { null } ?: ed.lemma,
                    lang = engine.langName(ed.lang),
                    kind = when (ed.type) {
                        EdgeType.COGNATE -> RelationKind.KIN
                        EdgeType.BORROWING -> RelationKind.BORROWED
                        EdgeType.DERIVATION -> RelationKind.DERIVED
                    },
                    rtl = engine.registry[ed.lang]?.rtl == true,
                )
            },
        )
    }
}
