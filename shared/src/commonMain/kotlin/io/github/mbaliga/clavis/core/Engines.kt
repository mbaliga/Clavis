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
 *
 * Written as a scan, not a regex: Kotlin/Native's regex engine has no `\p{IsHan}`-style script classes, and
 * a top-level `Regex` that throws at class-init crashes the first call on iOS (found by running the common tests
 * on Native). The classes are the Han, Hiragana and Katakana scripts (an explicit range table, so the Common-script marks U+30FC, U+30FB and U+309B/C are excluded exactly as the regex excluded them) plus the CJK punctuation (U+3000-303F) and
 * full-width forms (U+FF00-FFEF); the whitespace is the regex `\s` set (ASCII space, tab, LF, VT, FF, CR).
 */
/** Inclusive start/end pairs of the Han, Hiragana and Katakana scripts (JDK 17 / Unicode 13 `UnicodeScript`, which is what the original regex used). */
private val CJK_SCRIPT_RANGES = intArrayOf(
    0x2E80, 0x2E99, 0x2E9B, 0x2EF3, 0x2F00, 0x2FD5, 0x3005, 0x3005,
    0x3007, 0x3007, 0x3021, 0x3029, 0x3038, 0x303B, 0x3041, 0x3096,
    0x309D, 0x309F, 0x30A1, 0x30FA, 0x30FD, 0x30FF, 0x31F0, 0x31FF,
    0x32D0, 0x32FE, 0x3300, 0x3357, 0x3400, 0x4DBF, 0x4E00, 0x9FFF,
    0xF900, 0xFA6D, 0xFA70, 0xFAD9, 0xFF66, 0xFF6F, 0xFF71, 0xFF9D,
    0x16FE2, 0x16FE3, 0x16FF0, 0x16FF1, 0x1AFF0, 0x1AFF3, 0x1AFF5, 0x1AFFB,
    0x1AFFD, 0x1AFFE, 0x1B000, 0x1B122, 0x1B132, 0x1B132, 0x1B150, 0x1B152,
    0x1B155, 0x1B155, 0x1B164, 0x1B167, 0x1F200, 0x1F200, 0x20000, 0x2A6DF,
    0x2A700, 0x2B739, 0x2B740, 0x2B81D, 0x2B820, 0x2CEA1, 0x2CEB0, 0x2EBE0,
    0x2F800, 0x2FA1D, 0x30000, 0x3134A, 0x31350, 0x323AF,
)

internal fun isCjkCodePoint(cp: Int): Boolean {
    if (cp in 0x3000..0x303F || cp in 0xFF00..0xFFEF) return true
    var i = 0
    while (i < CJK_SCRIPT_RANGES.size) {
        if (cp >= CJK_SCRIPT_RANGES[i] && cp <= CJK_SCRIPT_RANGES[i + 1]) return true
        i += 2
    }
    return false
}

private fun isGapSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

private fun codePointBefore(s: String, end: Int): Int {
    val lo = s[end - 1]
    if (lo.isLowSurrogate() && end >= 2 && s[end - 2].isHighSurrogate()) return 0x10000 + ((s[end - 2].code - 0xD800) shl 10) + (lo.code - 0xDC00)
    return lo.code
}

private fun codePointAt(s: String, i: Int): Int {
    val hi = s[i]
    if (hi.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) return 0x10000 + ((hi.code - 0xD800) shl 10) + (s[i + 1].code - 0xDC00)
    return hi.code
}

internal fun stripCjkGaps(s: String): String {
    if (s.none(::isGapSpace)) return s
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        if (!isGapSpace(s[i])) { sb.append(s[i]); i++; continue }
        var j = i
        while (j < s.length && isGapSpace(s[j])) j++
        val gap = i > 0 && j < s.length && isCjkCodePoint(codePointBefore(s, i)) && isCjkCodePoint(codePointAt(s, j))
        if (!gap) sb.append(s, i, j)
        i = j
    }
    return sb.toString()
}

fun lineOf(l: Lang, s: String): String =
    if (l.code == "ja" || l.code == "zh") stripCjkGaps(s) else s
