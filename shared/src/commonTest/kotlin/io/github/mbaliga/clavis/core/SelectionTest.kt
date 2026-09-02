package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.data.ListLanguageRegistry
import io.github.mbaliga.clavis.core.engine.ClavisEngine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionTest {
    private val now = Fx.T0

    /** A corpus with two scenes per language in [langs]; token zipfs 4.0 / 7.0 (scene A) and 4.0 / 3.0 (scene B). */
    private fun multi(langs: List<String>): Corpus {
        fun toks(l: String, b: Boolean) = if (!b) listOf(Fx.tok("${l}one", 4.0), Fx.tok("${l}two", 7.0)) else listOf(Fx.tok("${l}one", 4.0), Fx.tok("${l}three", 3.0))
        val all = listOf("en") + langs
        val a = Fx.scene(0, "PA", all.associateWith { "${it}one ${it}two" }, all.associateWith { toks(it, false) })
        val b = Fx.scene(1, "PB", all.associateWith { "${it}one ${it}three" }, all.associateWith { toks(it, true) })
        val lex = langs.associate { l -> Lang(l) to linkedMapOf("${l}one" to Fx.lex(1, 4.0, "${l}one"), "${l}two" to Fx.lex(1, 7.0, "${l}two"), "${l}three" to Fx.lex(1, 3.0, "${l}three")) }
        return Corpus.index(listOf(a, b), lex, emptyMap(), emptyMap(), emptyMap())
    }

    // ------------------------------------------------------------ pickForLane

    @Test fun dueLemmaFirstAndBestSceneByCoverage() {
        val e = Fx.engine()
        Fx.tested(e.state, Fx.ES, 5.0)
        val cs = e.state.cardsOf(Fx.ES)
        cs["gato"] = Fx.knownCard(due = now - 1000)
        cs["comer"] = Fx.knownCard(due = now - 500) // due later than gato
        val enc = e.pickForLane(Fx.ES, now)
        val cl = assertIs<Encounter.Cloze>(enc)
        assertEquals("gato", cl.lemma) // oldest due
        assertEquals(1, cl.sc.i) // sc1 fully known (cov 1) beats sc3 (duerme unknown)
        assertFalse(cl.lowCov); assertEquals(1.0, cl.st.cov)
    }

    @Test fun dueSceneTieBreaksPreferUnseenThenRandom() {
        val toks = listOf(Fx.el, Fx.gato)
        val scenes = listOf(
            Fx.scene(0, "A", mapOf("en" to "x", "es" to "El gato"), mapOf("es" to toks)),
            Fx.scene(1, "B", mapOf("en" to "x", "es" to "El gato"), mapOf("es" to toks)),
            Fx.scene(2, "C", mapOf("en" to "x", "es" to "El gato"), mapOf("es" to toks)),
        )
        val c = Corpus.index(scenes, Fx.miniLex(), emptyMap(), emptyMap(), emptyMap())
        val e = Fx.engine(corpus = c, random = ScriptedRandom(default = 0.0))
        Fx.tested(e.state, Fx.ES, 5.0)
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1)
        e.state.seen["0"] = now - 1000 // seen recently → sorted after the unseen ones
        e.state.seen["1"] = now - 1000
        assertEquals(2, (e.pickForLane(Fx.ES, now) as Encounter.Cloze).sc.i)
        e.state.seen.remove("1") // now 1 and 2 tie fully; scripted random 0.0 picks the first of the tied group
        assertEquals(1, (e.pickForLane(Fx.ES, now) as Encounter.Cloze).sc.i)
    }

    @Test fun dueLemmaWithoutScenesFallsThroughToANewScene() {
        val e = Fx.engine()
        Fx.tested(e.state, Fx.ES, 5.0)
        e.state.cardsOf(Fx.ES)["nowhere"] = Fx.knownCard(due = now - 1)
        val r = assertIs<Encounter.Read>(e.pickForLane(Fx.ES, now))
        assertFalse(r.fluency)
    }

    @Test fun lowCovIsSetWhenTheBestSceneIsUnderSeventyPercent() {
        val e = Fx.engine() // untested: thr 9
        e.state.cardsOf(Fx.ES)["poción"] = Fx.knownCard(due = now - 1)
        val cl = assertIs<Encounter.Cloze>(e.pickForLane(Fx.ES, now))
        assertEquals(2, cl.sc.i); assertTrue(cl.lowCov); assertEquals(0.25, cl.st.cov)
    }

    @Test fun newSceneScoreOrderingWithoutJitter() {
        // thr 5: sc1 → cov 2/3, 1 unk (4.2): 4 + 1 + 2.52 = 7.52 ; sc2 → 0.5, 2 unk: 3 + 1 + 2.25 = 6.25 ; sc3 → 1/3, 2 unk: 2 + 1 + 2.46 = 5.46
        val e = Fx.engine(random = ScriptedRandom(default = 0.0))
        Fx.tested(e.state, Fx.ES, 5.0)
        val r = assertIs<Encounter.Read>(e.pickForLane(Fx.ES, now))
        assertEquals(1, r.sc.i); assertFalse(r.fluency); assertEquals(listOf("gato"), r.st.unk.map { it.lemma })
    }

    @Test fun storyContinuationBonusFlipsTheChoice() {
        val e = Fx.engine(random = ScriptedRandom(default = 0.0))
        Fx.tested(e.state, Fx.ES, 5.0)
        e.state.lastScene["es"] = 1 // sc2 = last + 1 → 6.25 + 1.5 = 7.75 > 7.52
        assertEquals(2, (e.pickForLane(Fx.ES, now) as Encounter.Read).sc.i)
    }

    @Test fun jitterCanChangeTheChoiceButStaysWithinZeroPointEight() {
        // sc2 needs +1.28 to pass sc1; jitter max 0.8 → sc1 always wins when sc1's own jitter is 0
        val e = Fx.engine(random = ScriptedRandom(listOf(0.0, 0.999, 0.999), default = 0.0))
        Fx.tested(e.state, Fx.ES, 5.0)
        assertEquals(1, (e.pickForLane(Fx.ES, now) as Encounter.Read).sc.i)
    }

    @Test fun newScenesSkipSeenRecentlyAndFullyKnown() {
        val e = Fx.engine(random = ScriptedRandom(default = 0.0))
        Fx.tested(e.state, Fx.ES, 5.0)
        e.state.seen["1"] = now - Fx.HOUR
        assertEquals(2, (e.pickForLane(Fx.ES, now) as Encounter.Read).sc.i) // sc0/sc4 have no unknowns, sc1 seen
        e.state.seen["1"] = now - 7 * Fx.HOUR // older than six hours → eligible again
        assertEquals(1, (e.pickForLane(Fx.ES, now) as Encounter.Read).sc.i)
    }

    @Test fun dailyCapBlocksNewScenesAndFallsBackToFluency() {
        val e = Fx.engine()
        Fx.tested(e.state, Fx.ES, 5.0)
        e.state.newToday["es"] = 10
        val r = assertIs<Encounter.Read>(e.pickForLane(Fx.ES, now))
        assertTrue(r.fluency); assertTrue(r.sc.i == 0 || r.sc.i == 4); assertTrue(r.st.unk.isEmpty())
        e.state.settings.newPerDay = 11
        assertFalse((e.pickForLane(Fx.ES, now) as Encounter.Read).fluency)
    }

    @Test fun nothingLeftGivesNull() {
        val e = Fx.engine()
        Fx.tested(e.state, Fx.ES, 5.0)
        e.state.newToday["es"] = 10
        for (i in 0..4) e.state.seen[i.toString()] = now
        assertNull(e.pickForLane(Fx.ES, now))
        assertNull(e.pickForLane(Fx.KO, now)) // no scenes at all
    }

    // ------------------------------------------------------------ bandit

    @Test fun learningProgressNeedsEightRewards() {
        val e = Fx.engine()
        val b = e.state.banditOf(Fx.ES)
        b.hist.addAll(listOf(1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0))
        assertNull(e.learningProgress(Fx.ES))
        b.hist.add(0.0)
        assertEquals(1.0, e.learningProgress(Fx.ES)!!, 1e-12) // n = 4: |mean(0,0,0,0) − mean(1,1,1,1)|
        b.hist.add(1.0) // 9 entries → n = 4: rec = (0,0,0,1) mean .25, old = slice(-8,-4) = (1,1,1,0) mean .75
        assertEquals(0.5, e.learningProgress(Fx.ES)!!, 1e-12)
        b.hist.clear(); repeat(20) { b.hist.add(0.0) }; repeat(20) { b.hist.add(0.5) }
        assertEquals(0.0, e.learningProgress(Fx.ES)!!, 1e-12) // n = 8: last 16 are all 0.5
    }

    @Test fun laneScoreFormula() {
        val e = Fx.engine()
        assertEquals(0.42, e.laneScore(Fx.ES, now).score, 1e-12) // prod: (0.2 + 0.08) × 1.5
        assertEquals(0.28, e.laneScore(Fx.FR, now).score, 1e-12) // rec
        val cs = e.state.cardsOf(Fx.ES)
        repeat(3) { cs["w$it"] = Fx.knownCard(due = now - 1) }
        val s3 = e.laneScore(Fx.ES, now)
        assertEquals(3, s3.due); assertEquals(0.6, s3.pressure, 1e-12); assertEquals(0.96, s3.score, 1e-12)
        repeat(4) { cs["v$it"] = Fx.knownCard(due = now - 1) }
        assertEquals(1.0, e.laneScore(Fx.ES, now).pressure); assertEquals(1.32, e.laneScore(Fx.ES, now).score, 1e-12)
        assertNull(s3.lp)
    }

    @Test fun rewardCapsHistoryAndUpdatesWeightOnlyWithLearningProgress() {
        val e = Fx.engine()
        val b = e.state.banditOf(Fx.ES)
        repeat(7) { e.reward(Fx.ES, if (it < 4) 0.0 else 1.0) }
        assertEquals(0.2, b.w) // lp still null
        e.reward(Fx.ES, 1.0) // hist = 0,0,0,0,1,1,1,1 → lp 1
        assertEquals(0.85 * 0.2 + 0.15 * 1.0, b.w, 1e-12)
        repeat(50) { e.reward(Fx.ES, 0.3) }
        assertEquals(40, b.hist.size); assertEquals(0.3, b.hist.first())
        assertTrue(b.w >= 0.02 && b.w <= 1.0)
        repeat(200) { e.reward(Fx.ES, 0.3) } // lp → 0, w decays toward the 0.02 floor
        assertEquals(0.02, b.w, 1e-9)
    }

    @Test fun whyTextThreeBranches() {
        val e = Fx.engine()
        assertEquals("getting to know you", e.whyText(Fx.ES, now))
        e.state.banditOf(Fx.ES).hist.addAll(listOf(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0))
        assertEquals("you're improving quickly here", e.whyText(Fx.ES, now))
        e.state.cardsOf(Fx.ES)["a"] = Fx.knownCard(due = now - 1)
        assertEquals("1 word ready to return", e.whyText(Fx.ES, now))
        e.state.cardsOf(Fx.ES)["b"] = Fx.knownCard(due = now - 1)
        assertEquals("2 words ready to return", e.whyText(Fx.ES, now))
    }

    @Test fun pinnedLaneOverridesEverythingUnlessOff() {
        val e = Fx.engine()
        e.state.pin = "ja"
        assertEquals(LaneChoice(Fx.JA, "pinned by you"), e.chooseLane(now, avoid = Fx.JA))
        e.state.lanes["ja"] = LaneMode.OFF
        assertTrue(e.chooseLane(now, null).l != Fx.JA)
    }

    @Test fun avoidIsDroppedOnlyWhenMoreThanOneLaneRemains() {
        val e = Fx.engine(random = ScriptedRandom(0.1, 0.0)) // explore → index 0 of the remaining lanes
        assertEquals(LaneChoice(Fx.FR, "trying something new"), e.chooseLane(now, avoid = Fx.ES))
        e.state.lanes["fr"] = LaneMode.OFF; e.state.lanes["ja"] = LaneMode.OFF
        val e2 = Fx.engine(state = e.state, random = ScriptedRandom(0.1, 0.0))
        assertEquals(Fx.ES, e2.chooseLane(now, avoid = Fx.ES).l) // the only lane cannot be avoided
    }

    @Test fun rouletteIsProportionalToScore() {
        // scores es 0.42, fr 0.28, ja 0.28 (total 0.98); the first draw ≥ 0.25 skips exploration
        assertEquals(LaneChoice(Fx.ES, "getting to know you"), Fx.engine(random = ScriptedRandom(0.9, 0.0)).chooseLane(now, null))
        assertEquals(Fx.ES, Fx.engine(random = ScriptedRandom(0.9, 0.42)).chooseLane(now, null).l) // r = 0.4116 → es
        assertEquals(Fx.FR, Fx.engine(random = ScriptedRandom(0.9, 0.5)).chooseLane(now, null).l) // r = 0.49 → fr
        assertEquals(Fx.JA, Fx.engine(random = ScriptedRandom(0.9, 0.999)).chooseLane(now, null).l)
    }

    @Test fun chooseLaneGuardWhenNoLaneIsOpen() { // D-14
        val e = Fx.engine()
        for (l in Fx.registry.order) e.state.lanes[l.code] = LaneMode.OFF
        assertEquals(LaneChoice(Fx.ES, "nothing else is open"), e.chooseLane(now, null))
    }

    // ------------------------------------------------------------ echo / langid

    private fun echoReady(e: ClavisEngine) {
        Fx.tested(e.state, Fx.ES, 5.0)
        e.state.cardsOf(Fx.ES)["hola"] = Fx.knownCard(daysAgo = 21.0 / 24, reps = 1).also { it.first = now - 2 * DAY_MS }
    }

    @Test fun makeEchoFindsANeverSeenReadableSceneOfAMetWord() {
        val e = Fx.engine(); echoReady(e)
        val ec = assertNotNull(e.makeEcho(now))
        assertEquals(Fx.ES, ec.l); assertEquals("hola", ec.lemma); assertEquals("hola", ec.tok.lemma)
        assertTrue(ec.sc.i == 0 || ec.sc.i == 4); assertTrue(ec.st.cov >= 0.7)
    }

    @Test fun makeEchoConstraints() {
        val e = Fx.engine(); echoReady(e)
        val c = e.state.cardsOf(Fx.ES)["hola"]!!
        c.prov = true; assertNull(e.makeEcho(now)); c.prov = false
        c.reps = 0; assertNull(e.makeEcho(now)); c.reps = 1
        c.last = now - 19 * Fx.HOUR; assertNull(e.makeEcho(now)); c.last = now - 21 * Fx.HOUR
        e.state.seen["0"] = now - 30 * DAY_MS; e.state.seen["4"] = now - 30 * DAY_MS // ever seen, however long ago
        assertNull(e.makeEcho(now))
        e.state.seen.clear()
        assertNotNull(e.makeEcho(now))
    }

    @Test fun makeEchoRequiresSeventyPercentCoverage() {
        val e = Fx.engine() // untested → only carded words known
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(daysAgo = 1.0, reps = 1) // sc1 and sc3 → cov 1/3
        assertNull(e.makeEcho(now))
        e.state.cardsOf(Fx.ES)["el"] = Fx.knownCard(daysAgo = 0.1, reps = 1); e.state.cardsOf(Fx.ES)["comer"] = Fx.knownCard(daysAgo = 0.1, reps = 1)
        assertEquals(1, e.makeEcho(now)!!.sc.i) // sc1 now fully known; sc3 still 2/3 < 0.7
    }

    @Test fun makeLangIdOptionsFromAllPresentLanguagesSameScriptFirst() {
        val e = Fx.engine()
        val li = e.makeLangId(now)
        assertEquals(3, li.opts.size); assertTrue(li.l in li.opts); assertEquals(li.opts.size, li.opts.toSet().size)
        assertTrue(!e.corpus.scene(li.sc.i).k[li.l].isNullOrEmpty())
        // only ja active, but es/fr/it/zh/ko present: options = ja + both cjk + one latin
        val c = multi(listOf("es", "fr", "it", "ja", "zh", "ko"))
        val e2 = Fx.engine(corpus = c)
        for (l in listOf("es", "fr", "it", "zh", "ko")) e2.state.lanes[l] = LaneMode.OFF
        repeat(5) {
            val o = e2.makeLangId(now)
            assertEquals(Fx.JA, o.l); assertEquals(4, o.opts.size); assertTrue(Fx.ZH in o.opts && Fx.KO in o.opts)
        }
    }

    // ------------------------------------------------------------ session / nextEncounter

    private fun session(e: ClavisEngine, i: Int = 0, hold: Lang? = null, intentScene: Int? = null) =
        Session(n = e.state.settings.sessionSize, start = now, hold = hold, holdWhy = if (hold != null) Copy.PATH_YOU_CHOSE else null, intentScene = intentScene).also { it.i = i }

    @Test fun sessionEndsWhenIndexReachesSize() {
        val e = Fx.engine()
        val s = session(e, i = 5); s.correct = 3; s.newWords = 2; s.lanes.addAll(listOf(Fx.ES, Fx.FR, Fx.ES))
        e.nextEncounter(s, now)
        assertTrue(s.done); assertNull(s.cur)
        assertEquals(LastSession(now, 3, 5, 2, listOf("es", "fr", "es")), e.state.lastSession)
        assertEquals(SessionSummaryOf(3, 5, 2, 2), e.summary()!!.let { SessionSummaryOf(it.correct, it.n, it.newWords, it.languagesVisited) })
    }
    private data class SessionSummaryOf(val c: Int, val n: Int, val nw: Int, val langs: Int)

    @Test fun intentSceneIsUsedWhenItStillHasUnknowns() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        val s = session(e, hold = Fx.ES, intentScene = 2)
        e.nextEncounter(s, now)
        val r = assertIs<Encounter.Read>(s.cur)
        assertEquals(2, r.sc.i); assertEquals("the path you chose", r.why); assertEquals(listOf(Fx.ES), s.lanes); assertNull(s.intentScene)
    }

    @Test fun intentSceneWithoutUnknownsIsSkippedButTheHoldRemains() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        val s = session(e, hold = Fx.ES, intentScene = 0)
        e.nextEncounter(s, now)
        assertTrue(s.cur!!.sc().i != 0); assertEquals(Fx.ES, s.cur!!.l); assertEquals("the path you chose", s.cur!!.why); assertNull(s.intentScene)
    }

    private fun Encounter.sc(): Scene = when (this) { is Encounter.Read -> sc; is Encounter.Cloze -> sc; is Encounter.Echo -> sc; is Encounter.LangId -> sc }

    @Test fun langIdOnTheLastSlotSeventyPercentWithThreeLanesAndNoHold() {
        val e = Fx.engine(random = ScriptedRandom(listOf(0.5), seed = 3)); Fx.tested(e.state, Fx.ES, 5.0)
        val s = session(e, i = 4)
        e.nextEncounter(s, now)
        val li = assertIs<Encounter.LangId>(s.cur)
        assertNull(li.why); assertTrue(s.lanes.isEmpty()) // langid is not pushed and has no reason
    }

    @Test fun langIdSkippedWhenDrawFailsOrHeldOrFewerThanThreeLanes() {
        val e = Fx.engine(random = ScriptedRandom(listOf(0.8, 0.9), seed = 3)); Fx.tested(e.state, Fx.ES, 5.0)
        val s = session(e, i = 4); e.nextEncounter(s, now)
        assertFalse(s.cur is Encounter.LangId)
        val e2 = Fx.engine(random = ScriptedRandom(listOf(0.5, 0.9), seed = 3)); Fx.tested(e2.state, Fx.ES, 5.0)
        val s2 = session(e2, i = 4, hold = Fx.ES); e2.nextEncounter(s2, now)
        assertFalse(s2.cur is Encounter.LangId); assertEquals(Fx.ES, s2.cur!!.l)
        val e3 = Fx.engine(random = ScriptedRandom(listOf(0.5, 0.9), seed = 3)); Fx.tested(e3.state, Fx.ES, 5.0)
        e3.state.lanes["ja"] = LaneMode.OFF
        val s3 = session(e3, i = 4); e3.nextEncounter(s3, now)
        assertFalse(s3.cur is Encounter.LangId)
    }

    @Test fun echoAtTwentyTwoPercentAfterTheFirstSlot() {
        val e = Fx.engine(random = ScriptedRandom(listOf(0.1), seed = 3)); echoReady(e)
        val s = session(e, i = 1)
        e.nextEncounter(s, now)
        val ec = assertIs<Encounter.Echo>(s.cur)
        assertEquals("an echo from earlier", ec.why); assertEquals(listOf(Fx.ES), s.lanes)
        val e0 = Fx.engine(random = ScriptedRandom(listOf(0.1, 0.0), seed = 3)); echoReady(e0)
        val s0 = session(e0, i = 0); e0.nextEncounter(s0, now)
        assertFalse(s0.cur is Encounter.Echo) // never on the first slot
    }

    @Test fun laneLoopPushesTheLaneAndReason() {
        val e = Fx.engine(random = ScriptedRandom(listOf(0.1, 0.0), seed = 3)); Fx.tested(e.state, Fx.ES, 5.0)
        val s = session(e)
        e.nextEncounter(s, now)
        val cur = assertNotNull(s.cur)
        assertEquals(Fx.ES, cur.l); assertEquals("trying something new", cur.why); assertEquals(listOf(Fx.ES), s.lanes)
    }

    @Test fun langIdFallbackWhenNoLaneHasAnything() {
        val e = Fx.engine()
        for (i in 0..4) e.state.seen[i.toString()] = now // everything seen; nothing due, nothing new, nothing known
        val s = session(e)
        e.nextEncounter(s, now)
        assertIs<Encounter.LangId>(s.cur); assertTrue(s.lanes.isEmpty())
    }

    @Test fun heldLaneWithNothingToOfferReleasesTheHold() {
        val e = Fx.engine(random = ScriptedRandom(listOf(0.9, 0.1, 0.0), seed = 3)); Fx.tested(e.state, Fx.ES, 5.0)
        e.state.newToday["fr"] = 10 // fr: untested (no known scenes) + cap reached → null
        val s = session(e, hold = Fx.FR)
        e.nextEncounter(s, now)
        assertNull(s.hold); assertTrue(s.cur!!.l != Fx.FR)
    }

    @Test fun startSessionWithIntentKeepsTheIntentUntilTheFirstEncounterIsConsumed() { // D-14
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        e.state.intent = Intent("es", 2)
        val s = e.startSession(now)
        assertEquals(Fx.ES, s.hold); assertEquals("the path you chose", s.holdWhy); assertEquals(2, (s.cur as Encounter.Read).sc.i)
        assertNotNull(e.state.intent); assertTrue(e.canUndo().not() || e.state.pathLog.isNotEmpty())
        e.advance(s, now)
        assertNull(e.state.intent); assertEquals(1, s.i)
    }

    @Test fun startSessionMonoSessionHoldsTheChosenLane() {
        val e = Fx.engine(random = ScriptedRandom(listOf(0.1, 0.0), seed = 3)); Fx.tested(e.state, Fx.ES, 5.0)
        e.state.settings.monoSession = true
        val s = e.startSession(now)
        assertEquals(Fx.ES, s.hold); assertEquals("trying something new", s.holdWhy); assertEquals("trying something new", s.cur!!.why)
        assertEquals(5, s.n); assertEquals(now, s.start)
    }

    @Test fun startSessionRollsTheDayAndUsesSessionSize() {
        val e = Fx.engine(dayKey = DayKey { "2025-9-9" }); Fx.tested(e.state, Fx.ES, 5.0)
        e.state.newToday["es"] = 7; e.state.settings.sessionSize = 8
        val s = e.startSession(now)
        assertEquals(8, s.n); assertEquals("2025-9-9", e.state.newDay); assertEquals(0, e.state.newToday["es"])
    }

    @Test fun startGameSessionForcesLangIdAndClearsIntent() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        e.state.intent = Intent("es", null)
        val s = e.startGameSession(now)
        assertIs<Encounter.LangId>(s.cur); assertNull(e.state.intent)
    }

    @Test fun fullSessionRunsToCompletionDeterministically() {
        val e = Fx.engine(random = Random(42)); Fx.tested(e.state, Fx.ES, 5.0); Fx.tested(e.state, Fx.FR, 5.0)
        val s = e.startSession(now)
        var guard = 0
        while (!s.done && guard++ < 20) {
            when (val c = s.cur) {
                is Encounter.Read -> e.finishRead(s, c, true, c.st.unk.map { it.lemma }.toSet(), now)
                is Encounter.Cloze -> e.answerCloze(s, c, e.clozeOptions(c, e.random).first { it.right }, 3000, now)
                is Encounter.Echo -> e.answerEcho(s, c, 3, now)
                is Encounter.LangId -> e.answerLangId(s, c, c.l, now)
                null -> error("no encounter")
            }
            e.advance(s, now)
        }
        assertTrue(s.done); assertEquals(5, s.i); assertEquals(5, s.correct)
        assertNotNull(e.state.lastSession); assertTrue(e.state.log.size >= 5)
    }

    @Test fun registryWithoutTheCorpusLanguagesStillWorks() {
        val reg = ListLanguageRegistry(Fx.registry.all.filter { it.iso.code in setOf("es", "fr") })
        val e = ClavisEngine(Fx.mini(), reg, freshState(reg, now), Clock { now }, Random(1), DayKey { "d" })
        assertEquals(listOf(Fx.ES, Fx.FR), e.activeLanes())
        assertEquals(2, e.makeLangId(now).opts.size) // only two languages are known to the registry
    }
}
