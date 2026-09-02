package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.engine.Fsrs5
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every expected value below was produced by running the template's own JavaScript
 * (template.html L371–L396) in Node 22 with the same inputs; they are stated to 17 significant
 * digits and compared at 1e-12.
 */
class Fsrs5Test {
    private val T0 = Fx.T0
    private val eps = 1e-12
    private val r3 = Fsrs5.retr(3.0, 3.173) // 0.90469821088932723

    @Test fun constantsMatchTheTemplate() {
        assertEquals(19, Fsrs5.W.size)
        assertEquals(0.40255, Fsrs5.W[0]); assertEquals(0.6621, Fsrs5.W[18]); assertEquals(15.69105, Fsrs5.W[3])
        assertEquals(-0.5, Fsrs5.DECAY); assertEquals(19.0 / 81.0, Fsrs5.FACTOR); assertEquals(600_000L, Fsrs5.FAIL_DUE_MS)
    }

    @Test fun retrievabilityCurve() {
        assertEquals(1.0, Fsrs5.retr(0.0, 5.0), eps)
        assertEquals(0.9, Fsrs5.retr(10.0, 10.0), eps)
        assertEquals(0.90469821088932723, r3, eps)
        assertEquals(0.98847398562905031, Fsrs5.retr(1.0, 10.0), eps)
        assertEquals(0.54671106530770830, Fsrs5.retr(100.0, 10.0), eps)
    }

    @Test fun intervalAtDefaultRetentionIsRoundedStabilityWithFloorOne() {
        assertEquals(1, Fsrs5.ivlDays(0.4, 0.9)); assertEquals(1, Fsrs5.ivlDays(1.0, 0.9)); assertEquals(3, Fsrs5.ivlDays(3.173, 0.9))
        assertEquals(16, Fsrs5.ivlDays(15.69105, 0.9)); assertEquals(40, Fsrs5.ivlDays(40.0, 0.9))
    }

    @Test fun intervalStretchesForLowerRetentionAndShrinksForHigher() {
        assertEquals(2, Fsrs5.ivlDays(1.0, 0.8)); assertEquals(8, Fsrs5.ivlDays(3.173, 0.8)); assertEquals(38, Fsrs5.ivlDays(15.69105, 0.8)); assertEquals(96, Fsrs5.ivlDays(40.0, 0.8))
        assertEquals(1, Fsrs5.ivlDays(3.173, 0.95)); assertEquals(7, Fsrs5.ivlDays(15.69105, 0.95)); assertEquals(18, Fsrs5.ivlDays(40.0, 0.95))
    }

    @Test fun initialDifficultyAndStabilityByGrade() {
        assertEquals(7.1948999999999996, Fsrs5.initD(1), eps); assertEquals(6.4883052684714526, Fsrs5.initD(2), eps)
        assertEquals(5.2824344223190050, Fsrs5.initD(3), eps); assertEquals(3.2245015893713678, Fsrs5.initD(4), eps)
        assertEquals(0.40255, Fsrs5.initS(1), eps); assertEquals(1.18385, Fsrs5.initS(2), eps); assertEquals(3.173, Fsrs5.initS(3), eps); assertEquals(15.69105, Fsrs5.initS(4), eps)
    }

    @Test fun nextDifficultyMeanRevertsAndClamps() {
        assertEquals(6.6070351073111082, Fsrs5.nextD(5.0, 1), eps)
        assertEquals(4.9918327073111080, Fsrs5.nextD(5.0, 3), eps)
        assertEquals(4.1842315073111083, Fsrs5.nextD(5.0, 4), eps)
        assertEquals(1.0, Fsrs5.nextD(1.0, 4), eps) // clamped at 1
        assertEquals(9.9688327073111065, Fsrs5.nextD(10.0, 1), eps)
    }

    @Test fun recallStabilityWithHardAndEasyMultipliers() {
        assertEquals(11.112664960555056, Fsrs5.recallS(5.0, 3.173, r3, 3), eps)
        assertEquals(5.0110324383684954, Fsrs5.recallS(5.0, 3.173, r3, 2), eps)
        assertEquals(26.911010299067510, Fsrs5.recallS(5.0, 3.173, r3, 4), eps)
    }

    @Test fun forgetStabilityIsCappedAtPreviousStability() {
        assertEquals(1.0619606521824987, Fsrs5.forgetS(5.0, 3.173, r3), eps)
        assertEquals(0.46439033317189909, Fsrs5.forgetS(5.0, 0.5, Fsrs5.retr(3.0, 0.5)), eps)
        assertEquals(0.1, Fsrs5.forgetS(1.0, 0.1, Fsrs5.retr(3.0, 0.1)), eps) // the raw formula gives ≈0.24 → min(…, s)
    }

    @Test fun shortTermStability() {
        assertEquals(4.4668580643622180, Fsrs5.shortS(3.173, 3), eps)
        assertEquals(1.5897635072660019, Fsrs5.shortS(3.173, 1), eps)
        assertEquals(7.4875022773945332, Fsrs5.shortS(3.173, 4), eps)
    }

    @Test fun firstRatingTakesTheInitBranchAndDoesNotTouchDifficultyAfterwards() {
        val c = Card()
        Fsrs5.rate(c, 3, T0, 0.9)
        assertEquals(3.173, c.s, eps); assertEquals(5.282434422319005, c.d, eps)
        assertEquals(1, c.reps); assertEquals(0, c.lapses); assertEquals(T0, c.first); assertEquals(T0, c.last); assertEquals(3, c.g)
        assertEquals(T0 + 3 * DAY_MS, c.due); assertFalse(c.prov); assertFalse(c.intro)
    }

    @Test fun secondGoodRatingAfterThreeDays() {
        val c = Card().also { Fsrs5.rate(it, 3, T0, 0.9) }
        Fsrs5.rate(c, 3, T0 + 3 * DAY_MS, 0.9)
        assertEquals(10.73892584613159, c.s, eps); assertEquals(5.272967931287446, c.d, eps)
        assertEquals(2, c.reps); assertEquals(T0, c.first); assertEquals(T0 + 3 * DAY_MS, c.last)
        assertEquals(T0 + 3 * DAY_MS + 11 * DAY_MS, c.due) // round(10.74) = 11 days
    }

    @Test fun lapseAfterThreeDaysUsesForgetStabilityAndTenMinuteDue() {
        val c = Card().also { Fsrs5.rate(it, 3, T0, 0.9) }
        Fsrs5.rate(c, 1, T0 + 3 * DAY_MS, 0.9)
        assertEquals(1.0555610912864883, c.s, eps); assertEquals(6.796932579932991, c.d, eps)
        assertEquals(2, c.reps); assertEquals(1, c.lapses); assertEquals(1, c.g)
        assertEquals(T0 + 3 * DAY_MS + 600_000L, c.due)
    }

    @Test fun sameDayReviewUsesShortTermStability() {
        val c = Card().also { Fsrs5.rate(it, 3, T0, 0.9) }
        Fsrs5.rate(c, 4, T0 + Fx.HOUR, 0.9)
        assertEquals(7.487502277394533, c.s, eps); assertEquals(4.510985606964673, c.d, eps)
        assertEquals(T0 + Fx.HOUR + 7 * DAY_MS, c.due)
    }

    @Test fun provisionalCardFirstRatingTakesTheInitBranchAndClearsTheFlag() {
        val c = Fsrs5.provisionalCard(T0 - 5 * DAY_MS)
        assertTrue(c.prov)
        Fsrs5.rate(c, 2, T0, 0.9)
        assertFalse(c.prov); assertFalse(c.intro)
        assertEquals(1.18385, c.s, eps); assertEquals(6.488305268471453, c.d, eps)
        assertEquals(1, c.reps); assertEquals(T0, c.first); assertEquals(T0 + DAY_MS, c.due)
    }

    @Test fun introCardFirstRatingClearsTheFlag() {
        val c = Fsrs5.introCard(T0 - DAY_MS)
        assertTrue(c.intro)
        Fsrs5.rate(c, 3, T0, 0.9)
        assertFalse(c.intro); assertEquals(3.173, c.s, eps); assertEquals(T0 + 3 * DAY_MS, c.due)
    }

    @Test fun freshAgainAndEasy() {
        val c1 = Card().also { Fsrs5.rate(it, 1, T0, 0.9) }
        assertEquals(0.40255, c1.s, eps); assertEquals(7.1949, c1.d, eps); assertEquals(1, c1.lapses); assertEquals(T0 + 600_000L, c1.due)
        val c4 = Card().also { Fsrs5.rate(it, 4, T0, 0.9) }
        assertEquals(15.69105, c4.s, eps); assertEquals(3.2245015893713678, c4.d, eps); assertEquals(T0 + 16 * DAY_MS, c4.due)
    }

    @Test fun threeReviewSequenceHardHardGood() {
        val c = Card()
        Fsrs5.rate(c, 2, T0, 0.9)
        Fsrs5.rate(c, 2, T0 + 2 * DAY_MS, 0.9)
        Fsrs5.rate(c, 3, T0 + 9 * DAY_MS, 0.9)
        assertEquals(12.584242282305176, c.s, eps); assertEquals(7.022947947583929, c.d, eps)
        assertEquals(3, c.reps); assertEquals(0, c.lapses); assertEquals(T0 + 9 * DAY_MS + 13 * DAY_MS, c.due)
    }

    @Test fun retentionSettingChangesTheDue() {
        val c = Card().also { Fsrs5.rate(it, 3, T0, 0.8) }
        assertEquals(T0 + 8 * DAY_MS, c.due)
        val d = Card().also { Fsrs5.rate(it, 3, T0, 0.95) }
        assertEquals(T0 + 1 * DAY_MS, d.due)
    }

    @Test fun firstIsSetOnceAndGradeIsStored() {
        val c = Card().also { Fsrs5.rate(it, 3, T0, 0.9) }
        Fsrs5.rate(c, 4, T0 + 5 * DAY_MS, 0.9)
        assertEquals(T0, c.first); assertEquals(4, c.g); assertEquals(T0 + 5 * DAY_MS, c.last)
    }

    @Test fun cardRetrievability() {
        assertEquals(0.0, Fsrs5.cardR(Card(), T0))
        assertEquals(0.0, Fsrs5.cardR(Fsrs5.provisionalCard(T0), T0)) // s = 0 → 0, even though prov counts as known
        val c = Card(s = 10.0, last = T0 - DAY_MS)
        assertEquals(0.98847398562905031, Fsrs5.cardR(c, T0), eps)
        assertEquals(1.0, Fsrs5.cardR(c, T0 - 2 * DAY_MS), eps) // negative elapsed clamps to 0
    }

    @Test fun cardLiterals() {
        val p = Fsrs5.provisionalCard(T0)
        assertEquals(Card(s = 0.0, d = 0.0, due = T0 + 30 * DAY_MS, last = T0, reps = 0, lapses = 0, g = 3, prov = true), p)
        val i = Fsrs5.introCard(T0)
        assertEquals(Card(s = 0.0, d = 0.0, due = T0, last = T0, reps = 0, lapses = 0, g = 1, intro = true), i)
    }

    @Test fun rejectsGradesOutsideOneToFour() {
        assertFailsWith<IllegalArgumentException> { Fsrs5.rate(Card(), 0, T0, 0.9) }
        assertFailsWith<IllegalArgumentException> { Fsrs5.rate(Card(), 5, T0, 0.9) }
    }

    @Test fun facadeDelegatesToTheScheduler() {
        assertEquals(Fsrs5.retr(3.0, 3.173), Fsrs.retrievability(3.0, 3.173))
        assertEquals(Fsrs5.DECAY, Fsrs.DECAY); assertEquals(Fsrs5.FACTOR, Fsrs.FACTOR)
    }
}
