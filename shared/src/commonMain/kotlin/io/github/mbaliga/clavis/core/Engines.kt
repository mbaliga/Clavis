package io.github.mbaliga.clavis.core

import kotlin.random.Random

/*
 * Engine contracts. Implementations live in :shared (pure Kotlin, unit-tested against
 * template-spec.md §2–§6). The UI never computes scheduling, selection or coverage itself.
 */

// ---------- FSRS-5 (template-spec §2) ----------

interface Scheduler {
    /** Retrievability after [days] for stability [s]: (1 + FACTOR·days/s)^DECAY. */
    fun retr(days: Double, s: Double): Double
    /** Integer interval in days for target retention [r], minimum 1. */
    fun ivlDays(s: Double, r: Double): Int
    /** Current retrievability of a card (0 if never rated). */
    fun cardR(card: Card, now: Long): Double
    /** Applies one review with grade 1..4 in place (template-spec §2.3) and returns the card. */
    fun rate(card: Card, grade: Int, now: Long, retention: Double): Card
    fun provisionalCard(now: Long): Card
    fun introCard(now: Long): Card
}

// ---------- Knownness, coverage, encounters (template-spec §3) ----------

data class SceneStats(val cov: Double, val unk: List<Token>, val total: Int)

sealed interface Encounter {
    val l: Lang
    var why: String?

    data class Cloze(
        override val l: Lang, val sc: Scene, val lemma: String, val st: SceneStats, val lowCov: Boolean,
        override var why: String? = null,
    ) : Encounter

    data class Read(
        override val l: Lang, val sc: Scene, val st: SceneStats, val fluency: Boolean = false,
        override var why: String? = null,
    ) : Encounter

    data class Echo(
        override val l: Lang, val sc: Scene, val lemma: String, val tok: Token, val st: SceneStats,
        override var why: String? = null,
    ) : Encounter

    data class LangId(
        override val l: Lang, val sc: Scene, val opts: List<Lang>,
        override var why: String? = null,
    ) : Encounter
}

data class LaneChoice(val l: Lang, val why: String)

/** Mutable session object (template-spec §3.9). */
class Session(
    val n: Int,
    val start: Long,
    var hold: Lang? = null,
    var holdWhy: String? = null,
    var intentScene: Int? = null,
) {
    var i: Int = 0
    val lanes: MutableList<Lang> = mutableListOf()
    var correct: Int = 0
    var newWords: Int = 0
    var done: Boolean = false
    var cur: Encounter? = null
    /**
     * Why nothing could be served at all — every lane is resting (D-6). Then [cur] is null, [done]
     * is false, nothing was mutated, and the UI shows this text instead of opening a session.
     */
    var blocked: String? = null
}

/** Options for a cloze prompt. */
data class ClozeOption(val w: String, val right: Boolean = false, val sib: Lang? = null, val reading: String = "")

interface Knowledge {
    fun thr(l: Lang): Double
    fun isKnown(l: Lang, tok: Token, now: Long): Boolean
    fun sceneStats(l: Lang, sc: Scene, now: Long): SceneStats
    /** Share of the lane's scenes readable at the 95 % threshold (never "% of the language"). */
    fun corpusCoverage(l: Lang, now: Long): Double
    fun dueCards(l: Lang, now: Long): List<Pair<String, Card>>
    fun knownCount(l: Lang, now: Long): Int
    fun seenRecently(sceneId: Int, now: Long): Boolean
    fun activeLanes(): List<Lang>
}

interface SelectionEngine : Knowledge {
    fun pickForLane(l: Lang, now: Long): Encounter?
    fun chooseLane(now: Long, avoid: Lang?): LaneChoice
    fun makeEcho(now: Long): Encounter.Echo?
    fun makeLangId(now: Long): Encounter.LangId
    fun startSession(now: Long): Session
    /** Advances [session] to its next encounter or marks it done; mutates state (lastSession). */
    fun nextEncounter(session: Session, now: Long)
    fun clozeOptions(e: Encounter.Cloze, random: Random): List<ClozeOption>
    /** Bandit reward, template-spec §3.5. */
    fun reward(l: Lang, r: Double)
    fun learningProgress(l: Lang): Double?
    fun whyText(l: Lang, now: Long): String
}

/** Outcomes the UI reports back; the engine applies FSRS, rewards, logs, seen-stamps and counters. */
interface EncounterGrader {
    fun answerCloze(session: Session, e: Encounter.Cloze, chosen: ClozeOption, ms: Long, now: Long): ClozeResult
    fun finishRead(session: Session, e: Encounter.Read, understood: Boolean, userUnknownLemmas: Set<String>, now: Long): ReadResult
    fun answerEcho(session: Session, e: Encounter.Echo, grade: Int, now: Long): EchoResult
    fun answerLangId(session: Session, e: Encounter.LangId, guess: Lang, now: Long): Boolean
    fun routeHelped(l: Lang, via: Lang)
}

data class ClozeResult(val ok: Boolean, val grade: Int, val dueInDays: Int, val showSiblings: Boolean)
data class ReadResult(val introduced: Int, val reward: Double)
data class EchoResult(val daysSinceMet: Int, val origin: String?)

// ---------- Baseline test (template-spec §4) ----------

data class TestItem(val w: String, val lm: String, val z: Double, val r: String, val real: Boolean)

class BaselineTest(val l: Lang, val items: List<TestItem>, val start: Long) {
    var i: Int = 0
    val yes: MutableSet<Int> = mutableSetOf()
}

interface BaselineTester {
    fun start(l: Lang, now: Long, random: Random): BaselineTest
    /** Records an answer and advances; returns true when the test is complete. */
    fun answer(t: BaselineTest, knowIt: Boolean): Boolean
    /** Scores, writes cards + tests + log, returns the result and the toast text. */
    fun finish(t: BaselineTest, now: Long): Pair<TestResult, String>
    fun bandLabel(z: Double): String
}

// ---------- Paths (template-spec §6) ----------

enum class BranchKind { TEST, DUE, NEW, READ, GAME, LETTERS }

data class WordChip(val w: String, val r: String = "")

data class Branch(
    val kind: BranchKind,
    val l: Lang?,
    val score: Double,
    val title: String,
    val why: String,
    val then: String,
    val cites: List<Int> = emptyList(),
    val words: List<WordChip> = emptyList(),
    val sc: Scene? = null,
    val n: Int = 0,
)

interface PathsEngine {
    fun computeBranches(now: Long): List<Branch>
    fun unlockCount(l: Lang, learned: Set<String>, now: Long): Int
    /** Records the branch in pathLog and returns what the UI should do next. */
    fun takeBranch(b: Branch, now: Long): BranchAction
    fun undo()
}

sealed interface BranchAction {
    data class StartTest(val l: Lang) : BranchAction
    data class OpenStory(val l: Lang) : BranchAction
    data object StartGameSession : BranchAction
    data class StartSession(val intent: Intent) : BranchAction
    data class OpenLetters(val l: Lang) : BranchAction
}

// ---------- Lettering helpers (template-spec §10, §5.1) ----------

object Lettering {
    const val MIN_W = 0.045; const val MAX_W = 0.70; const val MIN_H = 0.010; const val MAX_H = 0.45
    fun rectOK(r: Rect?): Boolean = r != null && r.w in MIN_W..MAX_W && r.h in MIN_H..MAX_H
    fun rectFor(sc: Scene, l: Lang): Rect = sc.q[l] ?: sc.r
}

/**
 * Three-valued cloze mask (template-spec §5.1): null = letter the real text; "" = letter nothing
 * for this balloon; otherwise the gapped text. Computed once per render and passed to both the
 * cropped and whole-page renderers.
 */
object Mask {
    const val GAP = "____"
    fun forCloze(sc: Scene, l: Lang, lemma: String, revealed: Boolean): String? {
        if (revealed) return null
        val t = sc.k[l]?.firstOrNull { it.lemma == lemma } ?: return ""
        val line = sc.x[l] ?: return ""
        val m = line.replaceFirst(t.surface, GAP)
        return if (m == line) "" else m
    }
}

/**
 * Strips the translators' line-break spaces between two CJK characters (template-spec §10).
 * Applied to display, gloss and TTS input alike, only for ja/zh.
 */
private val CJK_GAP = Regex("(?<=[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\u3000-\\u303F\\uFF00-\\uFFEF])\\s+(?=[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\u3000-\\u303F\\uFF00-\\uFFEF])")

fun lineOf(l: Lang, s: String): String =
    if (l.code == "ja" || l.code == "zh") s.replace(CJK_GAP, "") else s
