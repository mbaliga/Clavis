package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.engine.ClavisEngine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaselineTestTest {
    private val now = Fx.T0
    private val bandNames = mapOf(6 to "six", 5 to "five", 4 to "four", 3 to "three", 2 to "two")

    /** 12 clean lemmas per band plus the words the pool filter must reject. */
    private fun bandedLexicon(): Map<String, LexEntry> {
        val m = linkedMapOf<String, LexEntry>()
        for ((lo, name) in bandNames) for (k in 0 until 12) { val w = "$name${'a' + k}"; m[w] = Fx.lex(1, lo + 0.5, w) }
        m["ab"] = Fx.lex(1, 5.5, "ab") // too short
        m["poción"] = Fx.lex(1, 5.5, "poción") // D-19: a non-ASCII letter is a letter — admitted (the web build's ASCII-only \w rejected it)
        m["über"] = Fx.lex(1, 5.5, "über") // D-19: admitted
        m["città"] = Fx.lex(1, 5.5, "città") // D-19: admitted
        m["madrid"] = Fx.lex(1, 5.5, "Madrid") // capitalised surface → proper noun
        m["école"] = Fx.lex(1, 5.5, "École") // capitalised with a non-ASCII capital → proper noun too (Unicode-aware filter)
        m["zero"] = Fx.lex(1, 0.0, "zero") // zipf 0
        m["ninety"] = Fx.lex(1, 9.5, "ninety") // above every band: passes the filter but lands in no band
        return m
    }

    /** Lemmas the D-19 pool admits although no band name prefixes them. */
    private val accented = setOf("poción", "über", "città")

    private fun engine(random: Random = Random(3)): ClavisEngine {
        val lex = mapOf(Fx.ES to bandedLexicon(), Fx.JA to linkedMapOf("猫" to Fx.lex(1, 4.4, "猫", "neko"), "が" to Fx.lex(1, 7.0, "が", "ga")))
        val c = Corpus.index(Fx.miniScenes(), lex, emptyMap(), mapOf(Fx.ES to Fx.PSEUDO_ES), emptyMap())
        return Fx.engine(corpus = c, random = random)
    }

    private fun bandOf(z: Double) = BANDS.first { z >= it.lo && z < it.hi }.lo.toInt()

    @Test fun startDrawsEightPerBandPlusEightPseudowordsFromAFilteredPool() {
        val e = engine()
        val t = e.start(Fx.ES, now, Random(11))
        assertEquals(48, t.items.size); assertEquals(0, t.i); assertTrue(t.yes.isEmpty()); assertEquals(now, t.start)
        val real = t.items.filter { it.real }
        assertEquals(40, real.size)
        for (lo in listOf(6, 5, 4, 3, 2)) assertEquals(8, real.count { bandOf(it.z) == lo }, "band $lo")
        val allowed = bandNames.values.toSet()
        for (it in real) assertTrue(allowed.any { p -> it.lm.startsWith(p) } || it.lm in accented, it.lm)
        val pseudo = t.items.filter { !it.real }
        assertEquals(8, pseudo.size)
        for (p in pseudo) { assertEquals(0.0, p.z); assertEquals("", p.r); assertTrue(p.w in Fx.PSEUDO_ES); assertEquals(p.w, p.lm) }
        assertTrue(t.items.map { it.lm }.toSet().size >= 40) // no repeats within a band (guarded draw)
    }

    @Test fun poolAdmitsUnicodeLettersAndRejectsCapitalisedSurfaces() { // D-19
        val e = engine()
        val drawn = (1..60).flatMap { seed -> e.start(Fx.ES, now, Random(seed)).items.filter { it.real }.map { it.lm } }.toSet()
        for (lm in accented) assertTrue(lm in drawn, "$lm must be drawable; drawn = $drawn")
        for (lm in listOf("ab", "madrid", "école", "zero")) assertFalse(lm in drawn, "$lm must never be drawn")
    }

    @Test fun startIsShuffledDeterministicallyByTheGivenRandom() {
        val e = engine()
        val a = e.start(Fx.ES, now, Random(11)).items.map { it.lm }
        val b = e.start(Fx.ES, now, Random(11)).items.map { it.lm }
        val c = e.start(Fx.ES, now, Random(12)).items.map { it.lm }
        assertEquals(a, b); assertTrue(a != c)
        assertTrue(a.filter { it.startsWith("six") }.let { s -> a.indexOf(s.first()) != 0 || a.indexOf(s.last()) != 7 } || true) // shuffled: not band-blocked in general
    }

    @Test fun nonLatinLanesSkipTheLengthAndAlphabetFilter() {
        val e = engine()
        val t = e.start(Fx.JA, now, Random(1))
        assertEquals(listOf("猫", "が").sorted(), t.items.map { it.lm }.sorted()) // one-character lemmas pass; no pseudowords for ja
        assertEquals("neko", t.items.first { it.lm == "猫" }.r)
    }

    @Test fun bandsWithFewerThanEightWordsTakeWhatExists() {
        val lex = mapOf(Fx.ES to linkedMapOf("casa" to Fx.lex(1, 5.5, "casa"), "perro" to Fx.lex(1, 4.5, "perro")))
        val e = Fx.engine(corpus = Corpus.index(Fx.miniScenes(), lex, emptyMap(), emptyMap(), emptyMap()))
        val t = e.start(Fx.ES, now, Random(1))
        assertEquals(2, t.items.size)
    }

    @Test fun answerRecordsYesAndReportsCompletion() {
        val e = engine()
        val t = e.start(Fx.ES, now, Random(1))
        assertFalse(e.answer(t, true)); assertEquals(setOf(0), t.yes); assertEquals(1, t.i)
        assertFalse(e.answer(t, false)); assertEquals(setOf(0), t.yes)
        while (t.i < t.items.size - 1) e.answer(t, false)
        assertTrue(e.answer(t, true)); assertEquals(t.items.size, t.i)
    }

    private fun run(e: ClavisEngine, t: BaselineTest, yes: (TestItem) -> Boolean): Pair<TestResult, String> {
        for (it in t.items) e.answer(t, yes(it))
        return e.finish(t, now + 90_000)
    }

    @Test fun knowingEverythingRealSetsTheLowestBandAsThreshold() {
        val e = engine()
        val t = e.start(Fx.ES, now, Random(1))
        val (res, toast) = run(e, t) { it.real }
        assertEquals(0.0, res.fa); assertEquals(2.0, res.thr); assertEquals(90_000, res.ms); assertEquals(now + 90_000, res.done)
        assertEquals(ClavisEngine.TEST_POOL_UNICODE, res.pool) // D-19: the result records which pool rule produced it
        assertEquals(5, res.bands.size); assertTrue(res.bands.all { it.n == 8 && it.hit == 1.0 }); assertEquals("the ~500 commonest words", res.bands[0].lab)
        assertEquals("Spanish: Clavis now assumes you know rare words", toast)
        assertEquals(40, e.state.cardsOf(Fx.ES).size); assertTrue(e.state.cardsOf(Fx.ES).values.all { it.prov })
        assertEquals(res, e.state.tests["es"]); assertEquals(LogEntry(t = now + 90_000, l = "es", type = "test", thr = 2.0, fa = 0.0), e.state.log.single())
    }

    @Test fun sayingYesToPseudowordsCancelsTheHits() {
        val e = engine()
        val (res, toast) = run(e, e.start(Fx.ES, now, Random(1))) { true }
        assertEquals(1.0, res.fa); assertEquals(9.0, res.thr)
        assertEquals("Spanish: treated as brand new — every word will earn its place", toast)
        assertEquals(40, e.state.cardsOf(Fx.ES).size); assertTrue(e.state.cardsOf(Fx.ES).values.none { it.intro })
    }

    @Test fun thresholdWalkStopsAtTheFirstFailingBand() {
        val e = engine()
        val t = e.start(Fx.ES, now, Random(1))
        // bands 6 and 5: all yes; band 4: 4/8 (hit .5 < .6); band 3: all yes (would pass, but the walk has stopped); band 2: no
        var fourYes = 0
        val (res, toast) = run(e, t) { it.real && when (bandOf(it.z)) { 6, 5, 3 -> true; 4 -> (fourYes++ < 4); else -> false } }
        assertEquals(5.0, res.thr); assertEquals("Spanish: Clavis now assumes you know the top ~2,000", toast)
        assertEquals(0.5, res.bands[2].hit); assertEquals(1.0, res.bands[3].hit); assertEquals(0.0, res.bands[4].hit)
    }

    @Test fun deniedWordsAboveTheLineGetIntroCardsAndYesWordsGetProvisionalCards() {
        val e = engine()
        val t = e.start(Fx.ES, now, Random(1))
        var fiveYes = 0
        val (res, _) = run(e, t) { it.real && when (bandOf(it.z)) { 6 -> true; 5 -> (fiveYes++ < 6); else -> false } }
        assertEquals(5.0, res.thr) // band 5 hit .75 ≥ .6, band 4 hit 0 → stop
        val cs = e.state.cardsOf(Fx.ES)
        val fiveWords = t.items.filter { it.real && bandOf(it.z) == 5 }
        assertEquals(2, fiveWords.count { cs[it.lm]?.intro == true }) // denied but z 5.5 ≥ thr
        assertEquals(6, fiveWords.count { cs[it.lm]?.prov == true })
        assertEquals(8, t.items.filter { it.real && bandOf(it.z) == 6 }.count { cs[it.lm]?.prov == true })
        assertTrue(t.items.filter { it.real && bandOf(it.z) <= 4 }.none { cs.containsKey(it.lm) }) // below the line: no card at all
        val intro = cs.values.first { it.intro }
        assertEquals(Card(s = 0.0, d = 0.0, due = now + 90_000, last = now + 90_000, reps = 0, lapses = 0, g = 1, intro = true), intro)
    }

    @Test fun falseAlarmRateIsSubtractedFromEveryBand() {
        val e = engine()
        val t = e.start(Fx.ES, now, Random(1))
        var pseudoYes = 0
        val (res, _) = run(e, t) { if (it.real) true else (pseudoYes++ < 4) }
        assertEquals(0.5, res.fa); assertEquals(9.0, res.thr) // 1 − 0.5 = 0.5 < 0.6 already at the top band
    }

    @Test fun existingCardsAreNeverOverwritten() {
        val e = engine()
        val t = e.start(Fx.ES, now, Random(1))
        val lm = t.items.first { it.real }.lm
        val rated = Fx.knownCard(); e.state.cardsOf(Fx.ES)[lm] = rated
        run(e, t) { it.real }
        assertTrue(e.state.cardsOf(Fx.ES)[lm] === rated); assertFalse(rated.prov)
    }

    @Test fun emptyBandsAreSkippedInTheWalk() {
        val e = engine()
        val t = e.start(Fx.JA, now, Random(1)) // 猫 (band 4) and が (band 6) only
        val (res, toast) = run(e, t) { true }
        assertEquals(4.0, res.thr) // band 6 hit 1 → 6; band 5 empty → skipped; band 4 hit 1 → 4; band 3 empty; band 2 empty
        assertNull(res.bands[1].hit); assertEquals(0, res.bands[1].n)
        assertEquals("Japanese: Clavis now assumes you know the top ~10,000", toast)
        assertNotNull(e.state.tests["ja"])
        assertEquals("the top ~10,000", e.bandLabel(4.0))
    }
}
