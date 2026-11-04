package io.github.mbaliga.clavis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** `ClavisEngine.startLaneSession`: the session a home-screen widget tap opens (docs/widgets.md). */
class LaneSessionTest {
    private val now = Fx.T0

    @Test fun opensOnTheLanesFirstDueCardAndHoldsTheLane() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1)
        e.state.cardsOf(Fx.FR)["chat"] = Fx.knownCard(due = now - 2)
        val s = e.startLaneSession(Fx.ES, Copy.HOME_SCREEN, now)
        val c = assertIs<Encounter.Cloze>(s.cur)
        assertEquals(Fx.ES, c.l); assertEquals("gato", c.lemma); assertEquals(Copy.HOME_SCREEN, c.why)
        assertEquals(Fx.ES, s.hold); assertEquals(listOf(Fx.ES), s.lanes)
        assertNull(s.blocked)
    }

    @Test fun itIsNotAPathsChoiceSoPathsAndItsUndoAreUntouched() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1)
        e.state.pathLog.add(PathLogEntry(now - 10, "due", "fr", "Bring back 1 French word"))
        e.state.intent = Intent("fr")
        e.startLaneSession(Fx.ES, Copy.HOME_SCREEN, now)
        assertEquals(Intent("fr"), e.state.intent) // consumed only by a later encounter, as for any session
        assertEquals(1, e.state.pathLog.size)
    }

    @Test fun aLaneWithNothingDueStillOpensInThatLane() {
        val e = Fx.engine()
        val s = e.startLaneSession(Fx.FR, Copy.HOME_SCREEN, now)
        assertEquals(Fx.FR, assertNotNull(s.cur).l)
    }

    @Test fun aRestingOrUnknownLaneGivesAnOrdinarySession() {
        val e = Fx.engine()
        e.state.lanes["fr"] = LaneMode.OFF
        val s = e.startLaneSession(Fx.FR, Copy.HOME_SCREEN, now)
        assertNull(s.hold)
        assertNotEquals(Fx.FR, assertNotNull(s.cur).l)
        assertNull(e.startLaneSession(Fx.AR, Copy.HOME_SCREEN, now).hold) // enabled, but no scenes in this corpus
    }

    @Test fun everyLaneRestingIsRefusedWithTheReason() {
        val e = Fx.engine()
        for (k in e.state.lanes.keys) e.state.lanes[k] = LaneMode.OFF
        val s = e.startLaneSession(Fx.ES, Copy.HOME_SCREEN, now)
        assertEquals(Copy.ALL_RESTING, s.blocked); assertNull(s.cur)
    }
}
