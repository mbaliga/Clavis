package io.github.mbaliga.clavis.core.engine

import io.github.mbaliga.clavis.core.BANDS
import io.github.mbaliga.clavis.core.Band
import io.github.mbaliga.clavis.core.BaselineTest
import io.github.mbaliga.clavis.core.BaselineTester
import io.github.mbaliga.clavis.core.Branch
import io.github.mbaliga.clavis.core.BranchAction
import io.github.mbaliga.clavis.core.BranchKind
import io.github.mbaliga.clavis.core.Card
import io.github.mbaliga.clavis.core.ClavisState
import io.github.mbaliga.clavis.core.Clock
import io.github.mbaliga.clavis.core.ClozeOption
import io.github.mbaliga.clavis.core.ClozeResult
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Corpus
import io.github.mbaliga.clavis.core.DAY_MS
import io.github.mbaliga.clavis.core.DayKey
import io.github.mbaliga.clavis.core.EchoResult
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.core.EncounterGrader
import io.github.mbaliga.clavis.core.Intent
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneChoice
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.core.LanguageRegistry
import io.github.mbaliga.clavis.core.LastSession
import io.github.mbaliga.clavis.core.LexRow
import io.github.mbaliga.clavis.core.LogEntry
import io.github.mbaliga.clavis.core.PathLogEntry
import io.github.mbaliga.clavis.core.PathsEngine
import io.github.mbaliga.clavis.core.ReadResult
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.SceneStats
import io.github.mbaliga.clavis.core.Scheduler
import io.github.mbaliga.clavis.core.Script
import io.github.mbaliga.clavis.core.SelectionEngine
import io.github.mbaliga.clavis.core.Session
import io.github.mbaliga.clavis.core.TestItem
import io.github.mbaliga.clavis.core.TestResult
import io.github.mbaliga.clavis.core.Token
import io.github.mbaliga.clavis.core.WordChip
import io.github.mbaliga.clavis.core.bandLabel
import io.github.mbaliga.clavis.core.jsRound
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** `laneScore(l, now)` (template L490): the bandit's view of one lane. */
data class LaneScore(val w: Double, val pressure: Double, val due: Int, val score: Double, val lp: Double?)

/** One row of the You table (template-spec §7.8, parity item 74). */
data class YouLaneRow(
    val l: Lang, val name: String, val assumes: String, val held: Int, val ready: Int,
    val readable: Double, val recall: Double?, val mixUps: Int, val tested: Boolean,
)

data class YouMetrics(val encounters: Int, val daysShowedUp: Int, val recall: Double?, val rows: List<YouLaneRow>)

/** "The longer view, per language" (template-spec §6.7, parity item 55). */
data class LaneOverview(
    val l: Lang, val name: String, val mode: LaneMode, val pinned: Boolean, val lp: Double?, val lpLabel: String,
    val due: Int, val held: Int, val readable: Double, val newToday: Int, val newPerDay: Int, val assumes: String,
    val meta: String, val lookAlikes: List<Pair<String, Int>>, val bridges: List<Pair<String, Int>>,
)

/** `S.lastSession` prepared for `renderSummary` (template-spec §5.7). */
data class SessionSummary(val correct: Int, val n: Int, val newWords: Int, val languagesVisited: Int)

/**
 * The whole v0.10 domain in one deterministic object: knowledge model, selection engine and
 * bandit, encounter grading, baseline test and Paths (template-spec §3–§6), over an injected
 * [corpus], language [registry] (D-6), live [state] document (D-12), [clock], [random] and
 * [dayKey]. Nothing here renders, speaks or persists; the UI calls `store.save(engine.state)`.
 *
 * Deliberate divergences from the web build are marked `// D-xx` (DECISIONS.md) or
 * `// divergence` and listed in shared/README.md.
 */
class ClavisEngine(
    val corpus: Corpus,
    val registry: LanguageRegistry,
    state: ClavisState,
    val clock: Clock,
    val random: Random,
    val dayKey: DayKey,
    val scheduler: Scheduler = Fsrs5,
) : SelectionEngine, EncounterGrader, BaselineTester, PathsEngine {

    /** The live document. Replace it after an import; every method reads and mutates it in place. */
    var state: ClavisState = state

    companion object {
        const val KNOWN_R = 0.75
        const val HELD_R = 0.9
        const val READABLE_COV = 0.95
        const val UNTESTED_THR = 9.0
        const val SEEN_WINDOW_MS = 6 * 3_600_000L
        const val MAX_NEW_CARDS_PER_READ = 3
        const val LOG_CAP = 6000
        const val PATH_LOG_CAP = 12
        const val BANDIT_HIST_CAP = 40
        const val CLOZE_LOW_COV = 0.7
        const val ECHO_MIN_COV = 0.7
        const val ECHO_MIN_MS_SINCE_LAST = 20 * 3_600_000L
        const val ECHO_LEMMA_SAMPLE = 6
        const val ECHO_SCENE_SAMPLE = 4
        const val EXPLORE_RATE = 0.25
        const val LANGID_RATE = 0.7
        const val LANGID_MIN_LANES = 3
        const val LANGID_OPTIONS = 4
        const val ECHO_RATE = 0.22
        const val LANE_TRIES = 6
        const val CLOZE_EASY_MS = 2500L
        const val CLOZE_EASY_MIN_REPS = 2
        const val FAST_RETURN_MS = 45 * 60_000L
        const val TEST_PER_BAND = 8
        const val TEST_PSEUDO = 8
        const val TEST_LATIN_MIN_LEN = 3
        const val TEST_THRESHOLD_ADJ_HIT = 0.6
        const val ZIPF_WINDOW = 1.2
        const val LEN_WINDOW = 2
        const val MAX_DISTRACTORS = 2
        const val PATHS_TEST_BONUS = 0.35
        const val PATHS_DUE_BONUS = 0.25
        const val PATHS_MAX_PER_KIND = 2
        const val PATHS_MAX_LANE_BRANCHES = 4
        const val PATHS_MAX_FAN = 5
        const val PATHS_GAME_MIN_LANES = 3
        const val PATHS_WORDS_SHOWN = 3
        const val STORY_MIN_SCENES = 6

        /**
         * The template's pool filter is `/^[^\W\d_]+$/u`. With the `u` flag JavaScript's `\w` is
         * still ASCII-only, so the class admits A–Z/a–z and nothing else ("poción" fails —
         * verified in Node). Reproduced exactly; see README "quirks preserved".
         */
        private val ASCII_LETTERS = Regex("^[A-Za-z]+$")
    }

    // ------------------------------------------------------------------ state accessors

    private val retention: Double get() = state.settings.retention
    private fun cards(l: Lang): MutableMap<String, Card> = state.cardsOf(l)
    private fun bandit(l: Lang) = state.banditOf(l)
    private fun interOf(l: Lang): MutableMap<String, Int> = state.inter.getOrPut(l.code) { mutableMapOf() }
    private fun routesOf(l: Lang): MutableMap<String, Int> = state.routes.getOrPut(l.code) { mutableMapOf() }
    private fun scenesOf(l: Lang): List<Int> = corpus.byLang[l].orEmpty()
    private fun hasScenes(l: Lang): Boolean = scenesOf(l).isNotEmpty()
    private fun seenAt(id: Int): Long = state.seen[id.toString()] ?: 0L

    /** `langName(l)` (template L519): English for the bridge language, else the registry name. */
    fun langName(l: Lang): String = if (l == Lang.EN) "English" else registry[l]?.name ?: l.code

    /** `surfaceOf(l, lm)` (L520): the lexicon surface, else the lemma itself. */
    fun surfaceOf(l: Lang, lm: String): String = corpus.lex[l]?.get(lm)?.surface ?: lm

    /** `readingOf(l, lm)` (L521): the lexicon reading or `""`. */
    fun readingOf(l: Lang, lm: String): String = corpus.lex[l]?.get(lm)?.reading.orEmpty()

    /** Log push with the 6000-entry cap applied uniformly (divergence: the web trims only on read/cloze). */
    private fun log(e: LogEntry) {
        state.log.add(e)
        val over = state.log.size - LOG_CAP
        if (over > 0) state.log.subList(0, over).clear()
    }

    /** `rollDay()` (L414): a new local day zeroes every lane's `newToday`. Call at every render and session start. */
    fun rollDay(now: Long = clock.now()) {
        val k = dayKey.today(now)
        if (state.newDay != k) {
            state.newDay = k
            for (l in registry.order) state.newToday[l.code] = 0
        }
    }

    // ------------------------------------------------------------------ Knowledge (§3.1)

    override fun thr(l: Lang): Double = state.tests[l.code]?.thr ?: UNTESTED_THR

    /** D-6: registry order, lane not OFF, and the language actually has scenes in this corpus. */
    override fun activeLanes(): List<Lang> =
        registry.order.filter { state.laneMode(it) != LaneMode.OFF && hasScenes(it) }

    override fun isKnown(l: Lang, tok: Token, now: Long): Boolean {
        val c = cards(l)[tok.lemma]
        if (c != null) {
            if (c.prov) return true
            return scheduler.cardR(c, now) >= KNOWN_R && c.g != 1
        }
        return tok.zipf >= thr(l)
    }

    override fun sceneStats(l: Lang, sc: Scene, now: Long): SceneStats {
        var known = 0
        var total = 0
        val unk = LinkedHashMap<String, Token>()
        for (t in sc.k[l].orEmpty()) {
            total++
            if (isKnown(l, t, now)) known++ else if (!unk.containsKey(t.lemma)) unk[t.lemma] = t
        }
        return SceneStats(if (total > 0) known.toDouble() / total else 1.0, unk.values.toList(), total)
    }

    override fun corpusCoverage(l: Lang, now: Long): Double {
        val ids = scenesOf(l)
        if (ids.isEmpty()) return 0.0
        var ok = 0
        for (id in ids) if (sceneStats(l, corpus.scene(id), now).cov >= READABLE_COV) ok++
        return ok.toDouble() / ids.size
    }

    override fun dueCards(l: Lang, now: Long): List<Pair<String, Card>> =
        cards(l).entries.filter { (_, c) -> c.due <= now && !c.prov && !c.intro }
            .sortedBy { it.value.due }
            .map { it.key to it.value }

    override fun knownCount(l: Lang, now: Long): Int =
        cards(l).values.count { it.prov || (scheduler.cardR(it, now) >= HELD_R && it.g != 1) }

    override fun seenRecently(sceneId: Int, now: Long): Boolean {
        val t = seenAt(sceneId)
        return t != 0L && (now - t) < SEEN_WINDOW_MS
    }

    /** Total due across active lanes — the Learn pool's "n words pulling" (template L1548). */
    fun dueTotal(now: Long): Int = activeLanes().sumOf { dueCards(it, now).size }

    /** Active lanes without a baseline test — the "Before your first dip" card (template L1566). */
    fun untestedLanes(): List<Lang> = activeLanes().filter { state.tests[it.code] == null }

    // ------------------------------------------------------------------ Selection (§3.4)

    override fun pickForLane(l: Lang, now: Long): Encounter? {
        val due = dueCards(l, now)
        if (due.isNotEmpty()) {
            val lm = due[0].first
            val ids = corpus.byLemma[l]?.get(lm).orEmpty()
            if (ids.isNotEmpty()) {
                val cands = ids.map { id -> val sc = corpus.scene(id); sc to sceneStats(l, sc, now) }
                val sorted = cands.sortedWith(
                    compareByDescending<Pair<Scene, SceneStats>> { it.second.cov }
                        .thenBy { it.second.unk.size }
                        .thenBy { if (seenRecently(it.first.i, now)) 1 else 0 },
                )
                val top = sorted.first()
                // the template breaks full ties with Math.random(); pick uniformly among them
                val ties = sorted.takeWhile {
                    it.second.cov == top.second.cov && it.second.unk.size == top.second.unk.size &&
                        seenRecently(it.first.i, now) == seenRecently(top.first.i, now)
                }
                val pick = if (ties.size > 1) ties[random.nextInt(ties.size)] else top
                return Encounter.Cloze(l, pick.first, lm, pick.second, lowCov = pick.second.cov < CLOZE_LOW_COV)
            }
            // a due lemma with no scenes falls through to a new scene; no other due lemma is tried
        }
        if ((state.newToday[l.code] ?: 0) < state.settings.newPerDay) {
            val last = state.lastScene[l.code]
            var best: Pair<Scene, SceneStats>? = null
            var bestScore = -1e9
            for (id in scenesOf(l)) {
                if (seenRecently(id, now)) continue
                val sc = corpus.scene(id)
                val st = sceneStats(l, sc, now)
                if (st.unk.isEmpty()) continue
                val n = st.unk.size
                var score = st.cov * 6 - max(0, n - 3) * 2 + (if (n <= 3) 1 else 0)
                score += (st.unk.sumOf { it.zipf } / n) * 0.6
                if (last != null && id == last + 1) score += 1.5
                score += random.nextDouble() * 0.8
                if (score > bestScore) { bestScore = score; best = sc to st }
            }
            if (best != null) return Encounter.Read(l, best.first, best.second)
        }
        val known = scenesOf(l).filter { !seenRecently(it, now) && sceneStats(l, corpus.scene(it), now).unk.isEmpty() }
        if (known.isNotEmpty()) {
            val sc = corpus.scene(known[random.nextInt(known.size)])
            return Encounter.Read(l, sc, sceneStats(l, sc, now), fluency = true)
        }
        return null
    }

    // ------------------------------------------------------------------ Bandit (§3.5)

    override fun learningProgress(l: Lang): Double? {
        val h = bandit(l).hist
        if (h.size < 8) return null
        val n = min(8, h.size / 2)
        val rec = h.subList(h.size - n, h.size)
        val old = h.subList(h.size - 2 * n, h.size - n)
        return abs(rec.average() - old.average())
    }

    fun laneScore(l: Lang, now: Long): LaneScore {
        val b = bandit(l)
        val due = dueCards(l, now).size
        val pressure = min(1.0, due / 5.0)
        val mult = if (state.laneMode(l) == LaneMode.PROD) 1.5 else 1.0
        return LaneScore(b.w, pressure, due, (b.w + 0.08 + 0.6 * pressure) * mult, learningProgress(l))
    }

    fun whyText(x: LaneScore): String = when {
        x.due > 0 -> Copy.readyToReturn(x.due)
        x.lp != null && x.lp > 0.1 -> Copy.IMPROVING_QUICKLY
        else -> Copy.GETTING_TO_KNOW
    }

    override fun whyText(l: Lang, now: Long): String = whyText(laneScore(l, now))

    override fun chooseLane(now: Long, avoid: Lang?): LaneChoice {
        val pin = state.pin?.let(::Lang)
        if (pin != null && state.laneMode(pin) != LaneMode.OFF) return LaneChoice(pin, Copy.PINNED)
        var lanes = activeLanes()
        if (lanes.size > 1 && avoid != null) lanes = lanes.filter { it != avoid }
        if (lanes.isEmpty()) { // D-14: the web build throws here; fall back to the first language that has scenes
            val fb = registry.order.firstOrNull { hasScenes(it) } ?: registry.order.first()
            return LaneChoice(fb, Copy.NOTHING_ELSE_OPEN)
        }
        val scored = lanes.map { it to laneScore(it, now) }
        if (random.nextDouble() < EXPLORE_RATE) {
            val p = scored[random.nextInt(scored.size)]
            return LaneChoice(p.first, Copy.TRYING_NEW)
        }
        val tot = scored.sumOf { it.second.score }
        var r = random.nextDouble() * tot
        for ((l, x) in scored) {
            r -= x.score
            if (r <= 0) return LaneChoice(l, whyText(x))
        }
        val (l, x) = scored.last()
        return LaneChoice(l, whyText(x))
    }

    override fun reward(l: Lang, r: Double) {
        val b = bandit(l)
        b.hist.add(r)
        if (b.hist.size > BANDIT_HIST_CAP) b.hist.removeAt(0)
        val v = learningProgress(l)
        if (v != null) b.w = Fsrs5.clamp(0.85 * b.w + 0.15 * v, 0.02, 1.0)
    }

    // ------------------------------------------------------------------ Echo / langid (§3.7, §3.8)

    override fun makeEcho(now: Long): Encounter.Echo? {
        for (l in activeLanes().shuffled(random)) {
            val mets = cards(l).entries.filter { (_, c) -> !c.prov && c.reps >= 1 && (now - c.last) > ECHO_MIN_MS_SINCE_LAST }
            if (mets.isEmpty()) continue
            for ((lm, _) in mets.shuffled(random).take(ECHO_LEMMA_SAMPLE)) {
                val ids = corpus.byLemma[l]?.get(lm).orEmpty().filter { seenAt(it) == 0L } // never seen at all
                for (id in ids.shuffled(random).take(ECHO_SCENE_SAMPLE)) {
                    val sc = corpus.scene(id)
                    val st = sceneStats(l, sc, now)
                    if (st.cov >= ECHO_MIN_COV) {
                        val tok = sc.k[l]?.firstOrNull { it.lemma == lm } ?: continue
                        return Encounter.Echo(l, sc, lm, tok, st)
                    }
                }
            }
        }
        return null
    }

    override fun makeLangId(now: Long): Encounter.LangId {
        val present = registry.order.filter { hasScenes(it) } // all registry languages in the corpus, off or not
        val lanes = activeLanes().ifEmpty { present }
        check(lanes.isNotEmpty()) { "no language in the registry has any scenes" }
        val l = lanes[random.nextInt(lanes.size)]
        val ids = scenesOf(l)
        val sc = corpus.scene(ids[random.nextInt(ids.size)])
        val opts = LinkedHashSet<Lang>()
        opts.add(l)
        val script = registry[l]?.script
        val same = present.filter { it != l && registry[it]?.script == script }.shuffled(random)
        val rest = present.filter { it != l && registry[it]?.script != script }.shuffled(random)
        for (x in same + rest) { if (opts.size >= LANGID_OPTIONS) break; opts.add(x) }
        return Encounter.LangId(l, sc, opts.toList().shuffled(random))
    }

    // ------------------------------------------------------------------ Session (§3.6, §3.9)

    override fun startSession(now: Long): Session {
        rollDay(now)
        val s = Session(n = state.settings.sessionSize, start = now)
        val intent = state.intent
        if (intent != null) {
            s.hold = Lang(intent.l)
            s.holdWhy = Copy.PATH_YOU_CHOSE
            s.intentScene = intent.sc
            // D-14: keep state.intent until the first encounter is consumed so Paths' undo is reachable
        } else if (state.settings.monoSession) {
            val c = chooseLane(now, null)
            s.hold = c.l
            s.holdWhy = c.why
        }
        nextEncounter(s, now)
        return s
    }

    /** Paths `game` branch (template L2155): a session whose first, already-picked encounter is replaced by langid. */
    fun startGameSession(now: Long): Session {
        state.intent = null
        val s = startSession(now)
        s.cur = makeLangId(now)
        return s
    }

    /** What every `Next` does: `session.i++; nextEncounter()`. */
    fun advance(session: Session, now: Long) {
        session.i++
        nextEncounter(session, now)
    }

    override fun nextEncounter(session: Session, now: Long) {
        if (session.i > 0 && state.intent != null) state.intent = null // D-14
        if (session.i >= session.n) {
            session.done = true
            state.lastSession = LastSession(now, session.correct, session.n, session.newWords, session.lanes.map { it.code })
            return
        }
        val intentScene = session.intentScene
        if (intentScene != null) {
            session.intentScene = null
            val sc = corpus.scenes.getOrNull(intentScene)
            val l = session.hold
            if (sc != null && l != null) {
                val st = sceneStats(l, sc, now)
                if (st.unk.isNotEmpty()) {
                    session.cur = Encounter.Read(l, sc, st, why = Copy.PATH_YOU_CHOSE)
                    session.lanes.add(l)
                    return
                }
            }
        }
        val avoid = if (session.hold != null) null else session.lanes.lastOrNull()
        if (session.hold == null && session.i == session.n - 1 && activeLanes().size >= LANGID_MIN_LANES && random.nextDouble() < LANGID_RATE) {
            session.cur = makeLangId(now) // lane not pushed, no why
            return
        }
        if (session.i > 0 && random.nextDouble() < ECHO_RATE) {
            val e = makeEcho(now)
            if (e != null) {
                e.why = Copy.ECHO
                session.cur = e
                session.lanes.add(e.l)
                return
            }
        }
        var tries = 0
        var enc: Encounter? = null
        var choice: LaneChoice? = null
        while (tries < LANE_TRIES && enc == null) {
            val hold = session.hold
            choice = if (hold != null) LaneChoice(hold, session.holdWhy ?: Copy.HELD) else chooseLane(now, avoid)
            enc = pickForLane(choice.l, now)
            tries++
            if (session.hold != null && enc == null) session.hold = null // a held lane with nothing to offer releases the hold
        }
        if (enc == null) {
            enc = makeLangId(now)
        } else {
            enc.why = choice!!.why
            session.lanes.add(enc.l)
        }
        session.cur = enc
    }

    /** `renderSummary` inputs from `S.lastSession`; null before the first completed session. */
    fun summary(): SessionSummary? = state.lastSession?.let { SessionSummary(it.correct, it.n, it.newWords, it.lanes.toSet().size) }

    // ------------------------------------------------------------------ Cloze (§5.2)

    override fun clozeOptions(e: Encounter.Cloze, random: Random): List<ClozeOption> {
        val l = e.l
        val toks = e.sc.k[l].orEmpty()
        val target = toks.firstOrNull { it.lemma == e.lemma } ?: return emptyList()
        val correct = target.surface
        val distract = mutableListOf<ClozeOption>()
        val sibs = registry[l]?.siblings.orEmpty()
        val sib = target.bridges.firstOrNull { it.lang in sibs }
        if (sib != null && sib.word.lowercase() != correct.lowercase()) distract.add(ClozeOption(w = sib.word, sib = sib.lang))
        val pool = corpus.lexArr[l].orEmpty().filter { x ->
            val w = x.surface.ifEmpty { x.lemma }
            x.lemma != e.lemma && abs(x.zipf - target.zipf) < ZIPF_WINDOW &&
                abs(w.length - correct.length) <= LEN_WINDOW && w.lowercase() != correct.lowercase()
        }.toMutableList()
        while (distract.size < MAX_DISTRACTORS && pool.isNotEmpty()) {
            val x: LexRow = pool.removeAt(random.nextInt(pool.size))
            val w = x.surface.ifEmpty { x.lemma }
            if (distract.none { it.w.lowercase() == w.lowercase() }) distract.add(ClozeOption(w = w, reading = x.reading))
        }
        return (listOf(ClozeOption(w = correct, right = true, reading = target.reading ?: "")) + distract).shuffled(random)
    }

    /** Index of the gap token (`toks.findIndex(lemmaOf === lm)`), or -1. */
    fun clozeGapIndex(e: Encounter.Cloze): Int = e.sc.k[e.l].orEmpty().indexOfFirst { it.lemma == e.lemma }

    /** The fast-return notice (template L1935): reps ≤ 1 and first rating within 45 minutes. */
    fun showFastReturnNotice(e: Encounter.Cloze, now: Long): Boolean {
        val c = cards(e.l)[e.lemma] ?: return false
        return c.reps <= 1 && c.first != 0L && (now - c.first) < FAST_RETURN_MS
    }

    override fun answerCloze(session: Session, e: Encounter.Cloze, chosen: ClozeOption, ms: Long, now: Long): ClozeResult {
        val l = e.l
        val ok = chosen.right
        if (!ok) {
            val sib = chosen.sib
            if (sib != null) { val m = interOf(l); m[sib.code] = (m[sib.code] ?: 0) + 1 }
        }
        val c = cards(l).getOrPut(e.lemma) { Card() }
        val grade = if (ok) (if (ms < CLOZE_EASY_MS && c.reps >= CLOZE_EASY_MIN_REPS) 4 else 3) else 1
        scheduler.rate(c, grade, now, retention)
        reward(l, if (ok) 1.0 else 0.0)
        if (ok) session.correct++
        state.seen[e.sc.i.toString()] = now
        log(LogEntry(t = now, l = l.code, type = "cloze", sc = e.sc.i, lm = e.lemma, ok = if (ok) 1 else 0, ms = ms, sib = chosen.sib?.code))
        val dueInDays = max(1L, jsRound((c.due - now) / DAY_MS.toDouble())).toInt()
        return ClozeResult(ok = ok, grade = grade, dueInDays = dueInDays, showSiblings = c.reps >= 2)
    }

    override fun routeHelped(l: Lang, via: Lang) {
        val m = routesOf(l)
        m[via.code] = (m[via.code] ?: 0) + 1
    }

    // ------------------------------------------------------------------ Read (§5.3)

    /** The coach box is shown on a non-fluency read until the first finish (template L1899). */
    fun showReadCoach(e: Encounter.Read): Boolean = state.coach["read"] != true && !e.fluency

    /** The small copy line under a read caption (template L1900–1904). */
    fun readCopy(e: Encounter.Read): String = when {
        e.fluency -> Copy.FLUENCY_COPY
        thr(e.l) >= UNTESTED_THR && state.tests[e.l.code] == null -> Copy.untestedCopy(langName(e.l))
        else -> Copy.readCountCopy(e.st.unk.size, e.st.cov)
    }

    /**
     * `finish(understood)` (template L1908–1925). [userUnknownLemmas] are the lemmas the user
     * considers unknown after flipping (`modelKnown ? flipped : !flipped`, computed per token by
     * the UI with [isKnown]). Does not advance the session: call [advance] afterwards.
     */
    override fun finishRead(session: Session, e: Encounter.Read, understood: Boolean, userUnknownLemmas: Set<String>, now: Long): ReadResult {
        state.coach["read"] = true
        val l = e.l
        val toks = e.sc.k[l].orEmpty()
        val unknownNow = LinkedHashSet<String>()
        val knownNow = LinkedHashSet<String>()
        var rewardNum = 0
        var rewardDen = 0
        for (t in toks) {
            val modelKnown = isKnown(l, t, now)
            val userUnknown = t.lemma in userUnknownLemmas
            if (userUnknown) unknownNow.add(t.lemma) else knownNow.add(t.lemma)
            if (modelKnown) { rewardDen++; if (!userUnknown) rewardNum++ }
        }
        val cs = cards(l)
        var introduced = 0
        for (lm in unknownNow) {
            var c = cs[lm]
            if (c == null) {
                if (introduced >= MAX_NEW_CARDS_PER_READ) continue // extra unknowns get no card at all
                introduced++
                state.newToday[l.code] = (state.newToday[l.code] ?: 0) + 1
                session.newWords++
                c = Card()
                cs[lm] = c
            }
            scheduler.rate(c, 1, now, retention)
        }
        for (lm in knownNow) {
            val c = cs[lm]
            if (c != null && !c.prov && c.due <= now) {
                scheduler.rate(c, if (understood) 3 else 2, now, retention)
            } else if (c == null && !e.fluency) {
                val t = toks.firstOrNull { it.lemma == lm }
                if (t != null && t.zipf < thr(l)) cs[lm] = scheduler.provisionalCard(now) // learned by reading
            }
        }
        val r = if (understood) (if (rewardDen > 0) rewardNum.toDouble() / rewardDen else 1.0) else 0.0
        reward(l, r)
        state.seen[e.sc.i.toString()] = now
        state.lastScene[l.code] = e.sc.i
        log(LogEntry(t = now, l = l.code, type = "read", sc = e.sc.i, unk = unknownNow.size, ok = if (understood) 1 else 0))
        if (understood) session.correct++
        return ReadResult(introduced = introduced, reward = r)
    }

    // ------------------------------------------------------------------ Echo / langid grading (§5.4, §5.5)

    override fun answerEcho(session: Session, e: Encounter.Echo, grade: Int, now: Long): EchoResult {
        val l = e.l
        val c = cards(l).getOrPut(e.lemma) { Card() }
        val met = if (c.first != 0L) c.first else c.last
        val days = max(1L, jsRound((now - met) / DAY_MS.toDouble())).toInt()
        scheduler.rate(c, grade, now, retention)
        reward(l, when { grade >= 3 -> 1.0; grade == 2 -> 0.5; else -> 0.0 })
        if (grade >= 3) session.correct++
        state.seen[e.sc.i.toString()] = now
        log(LogEntry(t = now, l = l.code, type = "echo", lm = e.lemma, ok = if (grade >= 3) 1 else 0, g = grade))
        val et = corpus.etym[l]?.get(e.lemma)
        val o = et?.origin?.takeIf { it.isNotEmpty() }
        val origin = o?.let { if (et.originType == "bor") Copy.borrowedFrom(it) else Copy.grewFrom(it) }
        return EchoResult(daysSinceMet = days, origin = origin)
    }

    override fun answerLangId(session: Session, e: Encounter.LangId, guess: Lang, now: Long): Boolean {
        val ok = guess == e.l
        log(LogEntry(t = now, l = e.l.code, type = "langid", ok = if (ok) 1 else 0, guess = guess.code))
        if (ok) session.correct++
        return ok // no FSRS, no reward, no seen-stamp
    }

    // ------------------------------------------------------------------ Baseline test (§4)

    override fun start(l: Lang, now: Long, random: Random): BaselineTest {
        val latin = registry[l]?.script == Script.LATIN
        val pool = corpus.lexArr[l].orEmpty().filter { x ->
            x.zipf > 0 &&
                (!latin || (x.lemma.length >= TEST_LATIN_MIN_LEN && ASCII_LETTERS.matches(x.lemma))) &&
                !(x.surface.firstOrNull()?.let { it in 'A'..'Z' } ?: false)
        }
        val items = mutableListOf<TestItem>()
        for (b in BANDS) {
            val band = pool.filter { it.zipf >= b.lo && it.zipf < b.hi }
            val take = min(TEST_PER_BAND, band.size)
            val used = HashSet<String>()
            repeat(take) {
                var x: LexRow
                var guard = 0
                do { x = band[random.nextInt(band.size)]; guard++ } while (used.contains(x.lemma) && guard < 50)
                used.add(x.lemma)
                items.add(TestItem(w = x.surface.ifEmpty { x.lemma }, lm = x.lemma, z = x.zipf, r = x.reading, real = true))
            }
        }
        for (p in corpus.pseudo[l].orEmpty().take(TEST_PSEUDO)) items.add(TestItem(w = p, lm = p, z = 0.0, r = "", real = false))
        return BaselineTest(l, items.shuffled(random), now)
    }

    override fun answer(t: BaselineTest, knowIt: Boolean): Boolean {
        if (knowIt) t.yes.add(t.i)
        t.i++
        return t.i >= t.items.size
    }

    override fun finish(t: BaselineTest, now: Long): Pair<TestResult, String> {
        val l = t.l
        val pseudo = t.items.indices.filter { !t.items[it].real }
        val fa = if (pseudo.isEmpty()) 0.0 else pseudo.count { it in t.yes }.toDouble() / pseudo.size
        val bands = BANDS.map { b ->
            val idx = t.items.indices.filter { val x = t.items[it]; x.real && x.z >= b.lo && x.z < b.hi }
            val hits = idx.count { it in t.yes }
            Band(b.lo, b.hi, b.lab, idx.size, if (idx.isNotEmpty()) hits.toDouble() / idx.size else null)
        }
        var thrZ = UNTESTED_THR
        for (b in bands) {
            if (b.n == 0) continue
            val adj = (b.hit ?: 0.0) - fa
            if (adj >= TEST_THRESHOLD_ADJ_HIT) thrZ = b.lo else break
        }
        val cs = cards(l)
        t.items.forEachIndexed { i, x ->
            if (!x.real) return@forEachIndexed
            if (i in t.yes) {
                if (cs[x.lm] == null) cs[x.lm] = scheduler.provisionalCard(now)
            } else if (x.z >= thrZ) {
                if (cs[x.lm] == null) cs[x.lm] = scheduler.introCard(now)
            }
        }
        val res = TestResult(done = now, thr = thrZ, fa = fa, bands = bands, ms = now - t.start)
        state.tests[l.code] = res
        log(LogEntry(t = now, l = l.code, type = "test", thr = thrZ, fa = fa))
        val toast = if (thrZ >= UNTESTED_THR) Copy.testBrandNew(langName(l)) else Copy.testAssumes(langName(l), bandLabel(thrZ))
        return res to toast
    }

    override fun bandLabel(z: Double): String = io.github.mbaliga.clavis.core.bandLabel(z)

    // ------------------------------------------------------------------ Paths (§6)

    override fun unlockCount(l: Lang, learned: Set<String>, now: Long): Int {
        var n = 0
        for (id in scenesOf(l)) {
            val st = sceneStats(l, corpus.scene(id), now)
            if (st.unk.isNotEmpty() && st.unk.size <= learned.size + 1 && st.unk.all { it.lemma in learned }) n++
        }
        return n
    }

    override fun computeBranches(now: Long): List<Branch> {
        val cand = mutableListOf<Branch>()
        val active = activeLanes()
        for (l in active) {
            val sc = laneScore(l, now)
            val name = langName(l)
            if (state.tests[l.code] == null) {
                cand.add(Branch(BranchKind.TEST, l, sc.score + PATHS_TEST_BONUS, Copy.testTitle(name), Copy.TEST_WHY, Copy.testThen(name)))
                continue
            }
            val due = dueCards(l, now)
            if (due.isNotEmpty()) {
                cand.add(
                    Branch(
                        BranchKind.DUE, l, sc.score + PATHS_DUE_BONUS, Copy.dueTitle(due.size, name), whyText(sc), Copy.DUE_THEN,
                        words = due.take(PATHS_WORDS_SHOWN).map { (lm, _) -> WordChip(surfaceOf(l, lm), readingOf(l, lm)) },
                        n = due.size,
                    ),
                )
                continue
            }
            val enc = pickForLane(l, now)
            if (enc is Encounter.Read && !enc.fluency) {
                val learned = enc.st.unk.map { it.lemma }.toSet()
                val u = unlockCount(l, learned, now)
                cand.add(
                    Branch(
                        BranchKind.NEW, l, sc.score, Copy.newTitle(enc.st.unk.size, name), whyText(sc), Copy.newThen(u),
                        words = enc.st.unk.take(PATHS_WORDS_SHOWN).map { WordChip(it.surface, it.reading ?: "") },
                        sc = enc.sc,
                    ),
                )
            }
        }
        cand.sortByDescending { it.score } // stable, like V8's sort
        val out = mutableListOf<Branch>()
        val perKind = HashMap<BranchKind, Int>()
        for (b in cand) {
            val k = perKind[b.kind] ?: 0
            if (k >= PATHS_MAX_PER_KIND) continue
            perKind[b.kind] = k + 1
            out.add(b)
            if (out.size >= PATHS_MAX_LANE_BRANCHES) break
        }
        // D-14: a script primer comes before free reading when a lane needs one and Letters has not been visited
        val primer = active.firstOrNull { registry[it]?.needsScriptPrimer == true && state.coach["letters:${it.code}"] != true }
        if (primer != null && out.size < PATHS_MAX_FAN) {
            out.add(Branch(BranchKind.LETTERS, primer, 0.0, Copy.lettersTitle(langName(primer)), Copy.LETTERS_WHY, Copy.LETTERS_THEN))
        }
        val readLane = active.firstOrNull { state.tests[it.code] != null } ?: active.firstOrNull()
        if (readLane != null && out.size < PATHS_MAX_FAN) {
            out.add(Branch(BranchKind.READ, readLane, 0.0, Copy.readTitle(langName(readLane)), Copy.READ_WHY, Copy.READ_THEN, cites = listOf(15, 17)))
        }
        if (active.size >= PATHS_GAME_MIN_LANES && out.size < PATHS_MAX_FAN) {
            out.add(Branch(BranchKind.GAME, null, 0.0, Copy.GAME_TITLE, Copy.GAME_WHY, Copy.GAME_THEN, cites = listOf(33)))
        }
        return out
    }

    override fun takeBranch(b: Branch, now: Long): BranchAction {
        state.pathLog.add(PathLogEntry(t = now, kind = b.kind.name.lowercase(), l = b.l?.code, title = b.title))
        if (state.pathLog.size > PATH_LOG_CAP) state.pathLog.removeAt(0)
        return when (b.kind) {
            BranchKind.TEST -> BranchAction.StartTest(requireNotNull(b.l))
            BranchKind.READ -> {
                val l = requireNotNull(b.l)
                state.story.lane = l.code // ep/pi are kept, like Object.assign({ep:null,pi:0}, S.story, {lane})
                state.view = "story"
                BranchAction.OpenStory(l)
            }
            BranchKind.GAME -> {
                state.intent = null
                BranchAction.StartGameSession // the UI then calls startGameSession(now)
            }
            BranchKind.LETTERS -> { // D-14
                val l = requireNotNull(b.l)
                state.coach["letters:${l.code}"] = true
                state.letterTab = l.code
                BranchAction.OpenLetters(l)
            }
            BranchKind.DUE, BranchKind.NEW -> {
                val l = requireNotNull(b.l)
                val intent = Intent(l.code, if (b.kind == BranchKind.NEW && b.sc != null) b.sc.i else null)
                state.intent = intent
                BranchAction.StartSession(intent) // the UI then calls startSession(now)
            }
        }
    }

    override fun undo() {
        state.intent = null
        state.pathLog.removeLastOrNull()
    }

    /** Whether the `↶ undo` button is shown (template L2242): a due/new branch was taken and not yet consumed. */
    fun canUndo(): Boolean = state.pathLog.isNotEmpty() && state.intent != null

    /** "The longer view, per language" row (template L2274–2290) for one non-off lane. */
    fun laneOverview(l: Lang, now: Long): LaneOverview {
        val sc = laneScore(l, now)
        val lpLabel = when { sc.lp == null -> Copy.LP_WATCHING; sc.lp > 0.1 -> Copy.LP_FAST; else -> Copy.LP_STEADY }
        val held = knownCount(l, now)
        val cov = corpusCoverage(l, now)
        val newToday = state.newToday[l.code] ?: 0
        val assumes = state.tests[l.code]?.let { bandLabel(it.thr) } ?: Copy.ASSUMES_UNTESTED
        val look = state.inter[l.code].orEmpty().filter { it.value > 0 }.map { (k, n) -> langName(Lang(k)) to n }
        val bridges = state.routes[l.code].orEmpty().filter { it.value > 0 }.map { (k, n) -> langName(Lang(k)) to n }
        return LaneOverview(
            l = l, name = langName(l), mode = state.laneMode(l), pinned = state.pin == l.code, lp = sc.lp, lpLabel = lpLabel,
            due = sc.due, held = held, readable = cov, newToday = newToday, newPerDay = state.settings.newPerDay, assumes = assumes,
            meta = Copy.laneMeta(lpLabel, sc.due, held, cov, newToday, state.settings.newPerDay, assumes),
            lookAlikes = look, bridges = bridges,
        )
    }

    // ------------------------------------------------------------------ You (§7.8, item 74)

    fun youMetrics(now: Long): YouMetrics {
        val encounters = state.log.count { it.type != "test" }
        val days = state.log.map { dayKey.today(it.t) }.toSet().size
        val cl = state.log.filter { it.type == "cloze" }
        val recall = if (cl.isEmpty()) null else cl.count { (it.ok ?: 0) != 0 }.toDouble() / cl.size
        val rows = registry.order.filter { state.laneMode(it) != LaneMode.OFF }.map { l ->
            val c = cl.filter { it.l == l.code }
            val t = state.tests[l.code]
            YouLaneRow(
                l = l, name = langName(l),
                assumes = t?.let { bandLabel(it.thr) } ?: Copy.NOTHING_UNTESTED,
                held = knownCount(l, now), ready = dueCards(l, now).size, readable = corpusCoverage(l, now),
                recall = if (c.isEmpty()) null else c.count { (it.ok ?: 0) != 0 }.toDouble() / c.size,
                mixUps = state.inter[l.code].orEmpty().values.sum(), tested = t != null,
            )
        }
        return YouMetrics(encounters, days, recall, rows)
    }

    // ------------------------------------------------------------------ Story helpers (§7.11, item 72)

    /** Episodes with ≥ 6 scenes carrying a line in [l]; all episodes with any if none qualify; ascending. */
    fun storyEpisodes(l: Lang): List<Int> {
        val counts = LinkedHashMap<Int, Int>()
        for (sc in corpus.scenes) if (!sc.x[l].isNullOrEmpty()) counts[sc.e] = (counts[sc.e] ?: 0) + 1
        val eps = counts.filterValues { it >= STORY_MIN_SCENES }.keys.ifEmpty { counts.keys }
        return eps.sorted()
    }

    /** Distinct page keys of episode [ep] that have a line in [l], in scene order. */
    fun storyPages(l: Lang, ep: Int): List<String> =
        corpus.scenes.filter { it.e == ep && !it.x[l].isNullOrEmpty() }.map { it.m }.distinct()

    /** Scenes of a page with a line in [l], in reading order (`pageOf`). */
    fun pageScenes(pageKey: String, l: Lang): List<Scene> =
        corpus.byPage[pageKey].orEmpty().map(corpus::scene).filter { !it.x[l].isNullOrEmpty() }
}
