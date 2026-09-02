package io.github.mbaliga.clavis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KnowledgeTest {
    private val now = Fx.T0

    @Test fun thresholdIsNineUntilTestedThenTheTestValue() {
        val e = Fx.engine()
        assertEquals(9.0, e.thr(Fx.ES))
        Fx.tested(e.state, Fx.ES, 5.0)
        assertEquals(5.0, e.thr(Fx.ES))
    }

    @Test fun provisionalCardIsKnownRegardlessOfRetrievability() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(s = 0.0).also { it.prov = true; it.g = 3 }
        assertTrue(e.isKnown(Fx.ES, Fx.gato, now))
    }

    @Test fun ratedCardIsKnownWhenRetrievabilityAtLeast075AndLastGradeNotAgain() {
        val e = Fx.engine()
        val cs = e.state.cardsOf(Fx.ES)
        cs["gato"] = Fx.knownCard(daysAgo = 1.0) // R ≈ 0.988
        assertTrue(e.isKnown(Fx.ES, Fx.gato, now))
        cs["gato"] = Fx.knownCard(daysAgo = 100.0) // R ≈ 0.55
        assertFalse(e.isKnown(Fx.ES, Fx.gato, now))
        cs["gato"] = Fx.knownCard(daysAgo = 1.0, g = 1) // high R but last grade Again
        assertFalse(e.isKnown(Fx.ES, Fx.gato, now))
    }

    @Test fun introCardIsNotKnown() {
        val e = Fx.engine()
        Fx.tested(e.state, Fx.ES, 2.0) // everything above 2 presumed known …
        e.state.cardsOf(Fx.ES)["gato"] = e.scheduler.introCard(now) // … except the intro'd word
        assertFalse(e.isKnown(Fx.ES, Fx.gato, now))
        assertTrue(e.isKnown(Fx.ES, Fx.el, now))
    }

    @Test fun cardlessTokenIsKnownByZipfAgainstTheThreshold() {
        val e = Fx.engine()
        assertFalse(e.isKnown(Fx.ES, Fx.de, now)) // untested → thr 9 → nothing presumed known
        Fx.tested(e.state, Fx.ES, 5.0)
        assertTrue(e.isKnown(Fx.ES, Fx.de, now)) // 7.8 ≥ 5
        assertTrue(e.isKnown(Fx.ES, Fx.hola, now)) // 5.0 ≥ 5 (inclusive)
        assertFalse(e.isKnown(Fx.ES, Fx.gato, now)) // 4.2 < 5
    }

    @Test fun sceneStatsDedupesUnknownsByLemmaKeepingTheFirstToken() {
        val e = Fx.engine()
        val sc = Fx.scene(7, "P7", mapOf("en" to "x", "es" to "el gato y El gato"),
            mapOf("es" to listOf(Fx.tok("el", 7.5), Fx.gato, Fx.tok("y", 7.0), Fx.tok("El", 7.5, lemma = "el"), Fx.gato)))
        Fx.tested(e.state, Fx.ES, 9.0)
        val st = e.sceneStats(Fx.ES, sc, now)
        assertEquals(5, st.total); assertEquals(0.0, st.cov)
        assertEquals(listOf("el", "gato", "y"), st.unk.map { it.lemma })
        assertEquals("el", st.unk[0].surface) // first token of the lemma, not the capitalised second one
    }

    @Test fun sceneStatsCoverageAndEmptyScene() {
        val e = Fx.engine()
        Fx.tested(e.state, Fx.ES, 5.0)
        val st = e.sceneStats(Fx.ES, e.corpus.scene(2), now) // La poción de vuelo → la, de known
        assertEquals(4, st.total); assertEquals(0.5, st.cov); assertEquals(listOf("poción", "vuelo"), st.unk.map { it.lemma })
        val empty = e.sceneStats(Fx.KO, e.corpus.scene(2), now)
        assertEquals(0, empty.total); assertEquals(1.0, empty.cov); assertTrue(empty.unk.isEmpty())
    }

    @Test fun corpusCoverageCountsScenesReadableAt95Percent() {
        val e = Fx.engine()
        assertEquals(0.0, e.corpusCoverage(Fx.ES, now))
        Fx.tested(e.state, Fx.ES, 5.0)
        // readable: sc0 (hola, mundo), sc4 (hola) → 2 of 5
        assertEquals(0.4, e.corpusCoverage(Fx.ES, now), 1e-12)
        assertEquals(0.0, e.corpusCoverage(Fx.KO, now)) // no scenes → 0
    }

    @Test fun dueCardsExcludesProvisionalAndIntroAndSortsByDue() {
        val e = Fx.engine()
        val cs = e.state.cardsOf(Fx.ES)
        cs["b"] = Fx.knownCard(due = now - 1000)
        cs["a"] = Fx.knownCard(due = now - 5000)
        cs["c"] = Fx.knownCard(due = now) // due == now counts
        cs["later"] = Fx.knownCard(due = now + 1)
        cs["prov"] = e.scheduler.provisionalCard(now - DAY_MS * 40) // due in the past but provisional
        cs["intro"] = e.scheduler.introCard(now - 1000)
        assertEquals(listOf("a", "b", "c"), e.dueCards(Fx.ES, now).map { it.first })
    }

    @Test fun knownCountIsProvOrRetrievabilityAtLeast09WithoutLapse() {
        val e = Fx.engine()
        val cs = e.state.cardsOf(Fx.ES)
        cs["p"] = e.scheduler.provisionalCard(now)
        cs["held"] = Fx.knownCard(daysAgo = 5.0) // R ≈ 0.946
        cs["known-not-held"] = Fx.knownCard(daysAgo = 20.0) // R ≈ 0.825 → known but not held
        cs["lapsed"] = Fx.knownCard(daysAgo = 1.0, g = 1)
        cs["intro"] = e.scheduler.introCard(now)
        assertEquals(2, e.knownCount(Fx.ES, now))
    }

    @Test fun seenRecentlyIsSixHoursAndZeroMeansNever() {
        val e = Fx.engine()
        e.state.seen["3"] = now - 6 * Fx.HOUR + 1
        assertTrue(e.seenRecently(3, now))
        e.state.seen["3"] = now - 6 * Fx.HOUR
        assertFalse(e.seenRecently(3, now))
        e.state.seen["4"] = 0L
        assertFalse(e.seenRecently(4, now))
        assertFalse(e.seenRecently(99, now))
    }

    @Test fun activeLanesIsRegistryOrderMinusOffMinusAbsentFromCorpus() {
        val e = Fx.engine()
        assertEquals(listOf(Fx.ES, Fx.FR, Fx.JA), e.activeLanes()) // it/de/nl/ko/zh/ar have no scenes (D-6)
        e.state.lanes["fr"] = LaneMode.OFF
        assertEquals(listOf(Fx.ES, Fx.JA), e.activeLanes())
        e.state.lanes.remove("es") // a missing lane entry counts as OFF
        assertEquals(listOf(Fx.JA), e.activeLanes())
    }

    @Test fun rollDayZeroesNewTodayOnlyWhenTheDayChanges() {
        var day = "2025-9-2"
        val e = Fx.engine(dayKey = DayKey { day })
        e.state.newToday["es"] = 4; e.state.newToday["ja"] = 2
        e.rollDay(now) // first call: newDay was '' → rolls
        assertEquals("2025-9-2", e.state.newDay); assertEquals(0, e.state.newToday["es"])
        e.state.newToday["es"] = 3
        e.rollDay(now)
        assertEquals(3, e.state.newToday["es"]) // same day: untouched
        day = "2025-9-3"
        e.rollDay(now + DAY_MS)
        assertEquals("2025-9-3", e.state.newDay); assertEquals(0, e.state.newToday["es"]); assertEquals(0, e.state.newToday["ar"])
    }

    @Test fun dueTotalAndUntestedLanes() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1)
        e.state.cardsOf(Fx.FR)["chat"] = Fx.knownCard(due = now - 1)
        e.state.cardsOf(Fx.KO)["x"] = Fx.knownCard(due = now - 1) // not an active lane
        assertEquals(2, e.dueTotal(now))
        Fx.tested(e.state, Fx.FR, 5.0)
        assertEquals(listOf(Fx.ES, Fx.JA), e.untestedLanes())
    }
}
