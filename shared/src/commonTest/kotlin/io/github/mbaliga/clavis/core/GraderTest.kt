package io.github.mbaliga.clavis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraderTest {
    private val now = Fx.T0

    private fun cloze(e: io.github.mbaliga.clavis.core.engine.ClavisEngine, sceneId: Int, lemma: String) =
        Encounter.Cloze(Fx.ES, e.corpus.scene(sceneId), lemma, e.sceneStats(Fx.ES, e.corpus.scene(sceneId), now), lowCov = false)

    private fun session() = Session(n = 5, start = now)

    // ------------------------------------------------------------ cloze options

    @Test fun clozeOptionsWithoutSiblingBridgeDrawTwoLexiconDistractors() {
        val e = Fx.engine()
        val opts = e.clozeOptions(cloze(e, 1, "gato"), ScriptedRandom(seed = 5))
        assertEquals(3, opts.size)
        assertEquals(1, opts.count { it.right }); assertEquals("gato", opts.first { it.right }.w); assertEquals("", opts.first { it.right }.reading)
        val pool = setOf("Hola", "come", "vuelo", "duerme", "pato", "gata", "ratón", "perro") // |Δzipf| < 1.2 and |Δlen| ≤ 2
        for (o in opts.filter { !it.right }) { assertTrue(o.w in pool, o.w); assertNull(o.sib) }
        assertEquals(3, opts.map { it.w.lowercase() }.toSet().size)
    }

    @Test fun clozeOptionsPutTheSiblingLookAlikeFirstThenFillFromTheLexicon() {
        val e = Fx.engine()
        val opts = e.clozeOptions(cloze(e, 2, "poción"), ScriptedRandom(seed = 5))
        assertEquals(3, opts.size)
        val sib = opts.first { it.sib != null }
        assertEquals("Potion", sib.w); assertEquals(Fx.FR, sib.sib)
        val other = opts.first { !it.right && it.sib == null }
        assertTrue(other.w in setOf("duerme", "gata", "ratón"), other.w) // |zipf − 2.9| < 1.2 (4.1 − 2.9 is 1.1999… in IEEE doubles, as in JS) and length within 2 of "poción"
        assertEquals("poción", opts.first { it.right }.w)
    }

    @Test fun clozeOptionsSkipASiblingEqualToTheAnswerAndTolerateAnEmptyPool() {
        val taxi = Fx.tok("Taxi", 4.0, bridges = listOf(Bridge(Fx.FR, "taxi", 1.0)))
        val sc = Fx.scene(0, "P", mapOf("en" to "x", "es" to "Taxi"), mapOf("es" to listOf(taxi)))
        val c = Corpus.index(listOf(sc), mapOf(Fx.ES to mapOf("taxi" to Fx.lex(1, 4.0, "Taxi"))), emptyMap(), emptyMap(), emptyMap())
        val e = Fx.engine(corpus = c)
        val opts = e.clozeOptions(Encounter.Cloze(Fx.ES, sc, "taxi", e.sceneStats(Fx.ES, sc, now), false), ScriptedRandom(seed = 1))
        assertEquals(listOf(ClozeOption("Taxi", right = true)), opts)
    }

    @Test fun clozeOptionsCarryReadingsForTheAnswerAndDistractors() {
        val e = Fx.engine()
        val sc = e.corpus.scene(1)
        val opts = e.clozeOptions(Encounter.Cloze(Fx.JA, sc, "猫", e.sceneStats(Fx.JA, sc, now), false), ScriptedRandom(seed = 2))
        assertEquals("neko", opts.first { it.right }.reading)
        for (o in opts.filter { !it.right }) assertTrue(o.reading.isNotEmpty())
        assertEquals(1, e.clozeGapIndex(Encounter.Cloze(Fx.ES, e.corpus.scene(1), "gato", e.sceneStats(Fx.ES, e.corpus.scene(1), now), false)))
    }

    // ------------------------------------------------------------ cloze grading

    @Test fun correctSlowClozeIsGood() {
        val e = Fx.engine()
        val c = Fx.knownCard(due = now - 1, reps = 1); e.state.cardsOf(Fx.ES)["gato"] = c
        val s = session()
        val r = e.answerCloze(s, cloze(e, 1, "gato"), ClozeOption("gato", right = true), ms = 4000, now = now)
        assertTrue(r.ok); assertEquals(3, r.grade); assertEquals(2, c.reps); assertEquals(3, c.g)
        assertTrue(r.dueInDays >= 1); assertEquals(r.dueInDays, kotlin.math.max(1L, jsRound((c.due - now) / DAY_MS.toDouble())).toInt())
        assertTrue(r.showSiblings) // reps ≥ 2 after rating
        assertEquals(listOf(1.0), e.state.banditOf(Fx.ES).hist); assertEquals(1, s.correct); assertEquals(now, e.state.seen["1"])
        val log = e.state.log.single()
        assertEquals(LogEntry(t = now, l = "es", type = "cloze", sc = 1, lm = "gato", ok = 1, ms = 4000, sib = null), log)
    }

    @Test fun correctFastClozeIsEasyOnlyFromTheSecondRep() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1, reps = 1)
        assertEquals(3, e.answerCloze(session(), cloze(e, 1, "gato"), ClozeOption("gato", right = true), 1000, now).grade)
        val e2 = Fx.engine()
        e2.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1, reps = 2)
        assertEquals(4, e2.answerCloze(session(), cloze(e2, 1, "gato"), ClozeOption("gato", right = true), 2499, now).grade)
        val e3 = Fx.engine()
        e3.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1, reps = 2)
        assertEquals(3, e3.answerCloze(session(), cloze(e3, 1, "gato"), ClozeOption("gato", right = true), 2500, now).grade)
    }

    @Test fun wrongClozeIsAgainAndCountsTheSiblingInterference() {
        val e = Fx.engine()
        val c = Fx.knownCard(due = now - 1, reps = 1); e.state.cardsOf(Fx.ES)["poción"] = c
        val s = session()
        val r = e.answerCloze(s, cloze(e, 2, "poción"), ClozeOption("Potion", sib = Fx.FR), 900, now)
        assertFalse(r.ok); assertEquals(1, r.grade); assertEquals(now + 600_000L, c.due); assertEquals(1, c.lapses)
        assertEquals(1, e.state.inter["es"]!!["fr"]); assertEquals(listOf(0.0), e.state.banditOf(Fx.ES).hist); assertEquals(0, s.correct)
        assertEquals("fr", e.state.log.single().sib); assertEquals(0, e.state.log.single().ok)
        assertEquals(1, r.dueInDays) // max(1, round(10 min / day)) — the copy for a miss is the ten-minute line anyway
        e.answerCloze(s, cloze(e, 2, "poción"), ClozeOption("gata"), 900, now) // wrong without a sibling: no interference
        assertEquals(1, e.state.inter["es"]!!["fr"]); assertNull(e.state.inter["es"]!!["null"])
    }

    @Test fun clozeOnACardlessLemmaCreatesTheCard() {
        val e = Fx.engine()
        e.answerCloze(session(), cloze(e, 1, "gato"), ClozeOption("gato", right = true), 3000, now)
        val c = assertNotNull(e.state.cardsOf(Fx.ES)["gato"])
        assertEquals(1, c.reps); assertEquals(3.173, c.s)
    }

    @Test fun fastReturnNoticeAndRouteHelped() {
        val e = Fx.engine()
        val c = Fx.knownCard(reps = 1); c.first = now - 10 * 60_000L; e.state.cardsOf(Fx.ES)["gato"] = c
        assertTrue(e.showFastReturnNotice(cloze(e, 1, "gato"), now))
        c.first = now - 46 * 60_000L; assertFalse(e.showFastReturnNotice(cloze(e, 1, "gato"), now))
        c.first = now - 10 * 60_000L; c.reps = 2; assertFalse(e.showFastReturnNotice(cloze(e, 1, "gato"), now))
        e.routeHelped(Fx.ES, Fx.EN); e.routeHelped(Fx.ES, Fx.EN); e.routeHelped(Fx.ES, Fx.FR)
        assertEquals<Map<String, Int>?>(mapOf("en" to 2, "fr" to 1), e.state.routes["es"])
    }

    // ------------------------------------------------------------ read finish

    private fun read(e: io.github.mbaliga.clavis.core.engine.ClavisEngine, sceneId: Int, fluency: Boolean = false) =
        Encounter.Read(Fx.ES, e.corpus.scene(sceneId), e.sceneStats(Fx.ES, e.corpus.scene(sceneId), now), fluency)

    @Test fun unknownWordsBecomeNewCardsRatedAgain() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        val s = session()
        val r = e.finishRead(s, read(e, 2), understood = true, userUnknownLemmas = setOf("poción", "vuelo"), now = now)
        assertEquals(2, r.introduced); assertEquals(1.0, r.reward)
        assertEquals(2, e.state.newToday["es"]); assertEquals(2, s.newWords); assertEquals(1, s.correct)
        for (lm in listOf("poción", "vuelo")) { val c = e.state.cardsOf(Fx.ES)[lm]!!; assertEquals(1, c.g); assertEquals(now + 600_000L, c.due); assertEquals(1, c.reps) }
        assertNull(e.state.cardsOf(Fx.ES)["la"]); assertNull(e.state.cardsOf(Fx.ES)["de"]) // known by zipf, above threshold: no card
        assertEquals(2, e.state.lastScene["es"]); assertEquals(now, e.state.seen["2"]); assertEquals(true, e.state.coach["read"])
        assertEquals(LogEntry(t = now, l = "es", type = "read", sc = 2, unk = 2, ok = 1), e.state.log.single())
        assertEquals(listOf(1.0), e.state.banditOf(Fx.ES).hist)
    }

    @Test fun atMostThreeBrandNewCardsPerRead() {
        val e = Fx.engine() // untested: all four words unknown
        val s = session()
        val r = e.finishRead(s, read(e, 2), true, setOf("la", "poción", "de", "vuelo"), now)
        assertEquals(3, r.introduced); assertEquals(3, e.state.newToday["es"]); assertEquals(3, s.newWords)
        assertEquals(setOf("la", "poción", "de"), e.state.cardsOf(Fx.ES).keys) // the fourth, vuelo, gets no card at all
        assertEquals(4, e.state.log.single().unk); assertEquals(1.0, r.reward) // no model-known token → denominator 0 → 1
    }

    @Test fun flippingAKnownWordToUnknownLowersTheReward() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        val r = e.finishRead(session(), read(e, 2), true, setOf("poción", "vuelo", "de"), now)
        assertEquals(0.5, r.reward); assertEquals(3, r.introduced)
        assertNotNull(e.state.cardsOf(Fx.ES)["de"])
    }

    @Test fun tooHardGivesZeroRewardAndLogsOkZero() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        val s = session()
        val r = e.finishRead(s, read(e, 2), false, setOf("poción", "vuelo"), now)
        assertEquals(0.0, r.reward); assertEquals(0, s.correct); assertEquals(0, e.state.log.single().ok); assertEquals(listOf(0.0), e.state.banditOf(Fx.ES).hist)
    }

    @Test fun knownAndDueCardsAreRatedGoodOrHard() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        val la = Fx.knownCard(due = now - 1, reps = 1); e.state.cardsOf(Fx.ES)["la"] = la
        val de = Fx.knownCard(due = now + DAY_MS, reps = 1); e.state.cardsOf(Fx.ES)["de"] = de // not due
        e.finishRead(session(), read(e, 2), true, setOf("poción", "vuelo"), now)
        assertEquals(3, la.g); assertEquals(2, la.reps); assertEquals(1, de.reps)
        val e2 = Fx.engine(); Fx.tested(e2.state, Fx.ES, 5.0)
        val la2 = Fx.knownCard(due = now - 1, reps = 1); e2.state.cardsOf(Fx.ES)["la"] = la2
        e2.finishRead(session(), read(e2, 2), false, setOf("poción", "vuelo"), now)
        assertEquals(2, la2.g)
    }

    @Test fun provisionalCardsAreLeftAloneWhenKnownButRatedAgainWhenFlippedUnknown() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        val la = e.scheduler.provisionalCard(now - 40 * DAY_MS); e.state.cardsOf(Fx.ES)["la"] = la // due in the past but prov
        e.finishRead(session(), read(e, 2), true, setOf("poción", "vuelo"), now)
        assertTrue(la.prov); assertEquals(0, la.reps)
        val r = e.finishRead(session(), read(e, 2), true, setOf("la"), now)
        assertFalse(la.prov); assertEquals(1, la.g); assertEquals(0, r.introduced) // an existing card is not "introduced"
    }

    @Test fun flippingABelowThresholdWordToKnownCreatesAProvisionalCardExceptInFluencyReads() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        e.finishRead(session(), read(e, 2), true, setOf("vuelo"), now) // poción (2.9 < 5) flipped to "I know this"
        val p = assertNotNull(e.state.cardsOf(Fx.ES)["poción"])
        assertTrue(p.prov); assertEquals(now + 30 * DAY_MS, p.due); assertEquals(3, p.g)
        val e2 = Fx.engine(); Fx.tested(e2.state, Fx.ES, 5.0)
        e2.finishRead(session(), read(e2, 2, fluency = true), true, setOf("vuelo"), now)
        assertNull(e2.state.cardsOf(Fx.ES)["poción"])
    }

    @Test fun readCopyVariantsAndCoachVisibility() {
        val e = Fx.engine()
        assertEquals(Copy.untestedCopy("Spanish"), e.readCopy(read(e, 2)))
        assertTrue(e.showReadCoach(read(e, 2))); assertFalse(e.showReadCoach(read(e, 2, fluency = true)))
        assertEquals(Copy.FLUENCY_COPY, e.readCopy(read(e, 0, fluency = true)))
        Fx.tested(e.state, Fx.ES, 5.0)
        assertEquals("2 words here are probably new; the rest you know (50% of the bubble).", e.readCopy(read(e, 2)))
        e.finishRead(session(), read(e, 2), true, emptySet(), now)
        assertFalse(e.showReadCoach(read(e, 2)))
    }

    // ------------------------------------------------------------ echo / langid

    private fun echo(e: io.github.mbaliga.clavis.core.engine.ClavisEngine, sceneId: Int, lemma: String): Encounter.Echo {
        val sc = e.corpus.scene(sceneId)
        return Encounter.Echo(Fx.ES, sc, lemma, sc.k[Fx.ES]!!.first { it.lemma == lemma }, e.sceneStats(Fx.ES, sc, now))
    }

    @Test fun echoGradesRewardsAndDaysSinceFirstMeeting() {
        for ((g, rew) in listOf(3 to 1.0, 2 to 0.5, 1 to 0.0)) {
            val e = Fx.engine()
            val c = Fx.knownCard(reps = 1); c.first = now - 2 * DAY_MS; c.last = now - 21 * Fx.HOUR; e.state.cardsOf(Fx.ES)["gato"] = c
            val s = session()
            val r = e.answerEcho(s, echo(e, 3, "gato"), g, now)
            assertEquals(2, r.daysSinceMet); assertEquals("grew from Latin cattus", r.origin)
            assertEquals(listOf(rew), e.state.banditOf(Fx.ES).hist); assertEquals(if (g >= 3) 1 else 0, s.correct)
            assertEquals(g, c.g); assertEquals(2, c.reps); assertEquals(now, e.state.seen["3"])
            assertEquals(LogEntry(t = now, l = "es", type = "echo", lm = "gato", ok = if (g >= 3) 1 else 0, g = g), e.state.log.single())
        }
    }

    @Test fun echoOriginForBorrowedAndUnknownAndFallsBackToLastWhenFirstIsMissing() {
        val e = Fx.engine()
        val p = Fx.knownCard(reps = 1); p.first = 0L; p.last = now - 5 * DAY_MS; e.state.cardsOf(Fx.ES)["poción"] = p
        val r = e.answerEcho(session(), echo(e, 2, "poción"), 3, now)
        assertEquals("borrowed from Latin potione", r.origin); assertEquals(5, r.daysSinceMet)
        val h = Fx.knownCard(reps = 1); h.first = now - 3 * Fx.HOUR; e.state.cardsOf(Fx.ES)["hola"] = h
        val r2 = e.answerEcho(session(), echo(e, 4, "hola"), 2, now)
        assertNull(r2.origin); assertEquals(1, r2.daysSinceMet) // max(1, round(0.125))
    }

    @Test fun langIdLogsWithoutSchedulingRewardOrSeen() {
        val e = Fx.engine()
        val li = Encounter.LangId(Fx.ES, e.corpus.scene(0), listOf(Fx.ES, Fx.FR, Fx.JA))
        val s = session()
        assertTrue(e.answerLangId(s, li, Fx.ES, now)); assertEquals(1, s.correct)
        assertFalse(e.answerLangId(s, li, Fx.FR, now)); assertEquals(1, s.correct)
        assertEquals(listOf(LogEntry(t = now, l = "es", type = "langid", ok = 1, guess = "es"), LogEntry(t = now, l = "es", type = "langid", ok = 0, guess = "fr")), e.state.log)
        assertTrue(e.state.seen.isEmpty()); assertTrue(e.state.banditOf(Fx.ES).hist.isEmpty()); assertTrue(e.state.cardsOf(Fx.ES).isEmpty())
    }

    @Test fun logIsCappedAtSixThousandOnEveryPush() {
        val e = Fx.engine()
        val li = Encounter.LangId(Fx.ES, e.corpus.scene(0), listOf(Fx.ES, Fx.FR, Fx.JA))
        val s = session()
        repeat(6005) { e.answerLangId(s, li, Fx.ES, now + it) }
        assertEquals(6000, e.state.log.size); assertEquals(now + 5, e.state.log.first().t); assertEquals(now + 6004, e.state.log.last().t)
    }

    @Test fun youMetricsCountEncountersDaysAndRecall() {
        val e = Fx.engine(dayKey = DayKey { t -> if (t < now + DAY_MS) "d1" else "d2" })
        Fx.tested(e.state, Fx.ES, 5.0)
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1, reps = 1)
        e.state.inter["es"] = mutableMapOf("fr" to 2, "it" to 1)
        val s = session()
        e.answerCloze(s, cloze(e, 1, "gato"), ClozeOption("gato", right = true), 3000, now)
        e.answerCloze(s, cloze(e, 1, "gato"), ClozeOption("x"), 3000, now + DAY_MS + 1)
        e.answerLangId(s, Encounter.LangId(Fx.ES, e.corpus.scene(0), listOf(Fx.ES)), Fx.ES, now)
        e.state.log.add(LogEntry(t = now, l = "fr", type = "test", thr = 9.0, fa = 0.0))
        val m = e.youMetrics(now + DAY_MS + 1)
        assertEquals(3, m.encounters); assertEquals(2, m.daysShowedUp); assertEquals(0.5, m.recall)
        assertEquals(9, m.rows.size) // every non-off registry lane, like the template's LANE_ORDER loop (not only lanes with scenes)
        val es = m.rows.first { it.l == Fx.ES }
        assertEquals("the top ~2,000", es.assumes); assertEquals(0.5, es.recall); assertEquals(3, es.mixUps); assertTrue(es.tested)
        val fr = m.rows.first { it.l == Fx.FR }
        assertEquals("nothing — untested", fr.assumes); assertNull(fr.recall); assertEquals(0, fr.mixUps)
    }
}
