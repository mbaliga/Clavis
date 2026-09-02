package io.github.mbaliga.clavis.core.engine

import io.github.mbaliga.clavis.core.Card
import io.github.mbaliga.clavis.core.DAY_MS
import io.github.mbaliga.clavis.core.Scheduler
import io.github.mbaliga.clavis.core.jsRound
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * FSRS-5 with the v0.10 web build's default parameters. Every function is a line-for-line port of
 * template.html L371–L396 (template-spec §2); the constants are in template-constants.json.
 *
 * Grades: 1 Again · 2 Hard · 3 Good · 4 Easy. Times are epoch milliseconds.
 */
object Fsrs5 : Scheduler {
    val W: DoubleArray = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
    )
    const val DECAY: Double = -0.5
    const val FACTOR: Double = 19.0 / 81.0
    /** A failed review comes back in ten minutes (template L394). */
    const val FAIL_DUE_MS: Long = 10 * 60_000L
    /** A provisional (presumed-known) card is first checked after 30 days (template L1621). */
    const val PROVISIONAL_DUE_DAYS: Int = 30

    fun clamp(x: Double, a: Double, b: Double): Double = min(b, max(a, x))

    /** `retr(days, S) = (1 + FACTOR·days/S)^DECAY` (L375). */
    override fun retr(days: Double, s: Double): Double = (1.0 + FACTOR * days / s).pow(DECAY)

    /** `ivlDays(S, r) = max(1, round(S/FACTOR · (r^(1/DECAY) − 1)))` (L376). */
    override fun ivlDays(s: Double, r: Double): Int =
        max(1L, jsRound(s / FACTOR * (r.pow(1.0 / DECAY) - 1.0))).toInt()

    /** `initD(g) = clamp(W4 − e^(W5·(g−1)) + 1, 1, 10)` (L377). */
    fun initD(g: Int): Double = clamp(W[4] - exp(W[5] * (g - 1)) + 1.0, 1.0, 10.0)

    /** `initS(g) = max(0.1, W[g−1])` (L378). */
    fun initS(g: Int): Double = max(0.1, W[g - 1])

    /** Mean-reverting difficulty update (L379). */
    fun nextD(d: Double, g: Int): Double {
        val dp = d + (-W[6] * (g - 3)) * (10.0 - d) / 9.0
        return clamp(W[7] * initD(4) + (1.0 - W[7]) * dp, 1.0, 10.0)
    }

    /** Stability after a successful review (L380). */
    fun recallS(d: Double, s: Double, r: Double, g: Int): Double {
        val hard = if (g == 2) W[15] else 1.0
        val easy = if (g == 4) W[16] else 1.0
        return s * (exp(W[8]) * (11.0 - d) * s.pow(-W[9]) * (exp(W[10] * (1.0 - r)) - 1.0) * hard * easy + 1.0)
    }

    /** Stability after a lapse, capped at the previous stability (L381). */
    fun forgetS(d: Double, s: Double, r: Double): Double =
        min(W[11] * d.pow(-W[12]) * ((s + 1.0).pow(W[13]) - 1.0) * exp(W[14] * (1.0 - r)), s)

    /** Same-day review (L382). */
    fun shortS(s: Double, g: Int): Double = s * exp(W[17] * (g - 3 + W[18]))

    /** JS `!card.s`: a stability of 0 (or NaN) means "never really rated" — also true for prov/intro cards. */
    private fun unrated(card: Card): Boolean = card.s == 0.0 || card.s.isNaN()

    /** `cardR(card, now) = card.s ? retr(max(0, (now − last)/DAY), s) : 0` (L396). */
    override fun cardR(card: Card, now: Long): Double =
        if (unrated(card)) 0.0 else retr(max(0.0, (now - card.last) / DAY_MS.toDouble()), card.s)

    /**
     * `rate(card, g, now)` (L383–395), in the template's exact order. Difficulty is updated only
     * on the non-initial branch; `first` is set once; `prov`/`intro` are cleared; a fail is due in
     * ten minutes, a pass in `ivlDays(s, retention)` days.
     */
    override fun rate(card: Card, grade: Int, now: Long, retention: Double): Card {
        require(grade in 1..4) { "grade must be 1..4, was $grade" }
        if (unrated(card)) {
            card.s = initS(grade)
            card.d = initD(grade)
        } else {
            val days = (now - card.last) / DAY_MS.toDouble()
            if (days < 1) {
                card.s = shortS(card.s, grade)
            } else {
                val r = retr(days, card.s)
                card.s = if (grade == 1) forgetS(card.d, card.s, r) else recallS(card.d, card.s, r, grade)
            }
            card.d = nextD(card.d, grade)
        }
        card.reps += 1
        if (grade == 1) card.lapses += 1
        if (card.first == 0L) card.first = now
        card.last = now
        card.prov = false
        card.intro = false
        card.g = grade
        card.due = if (grade == 1) now + FAIL_DUE_MS else now + ivlDays(card.s, retention) * DAY_MS
        return card
    }

    /** `{prov:true, s:0, d:0, due:now+30·DAY, last:now, reps:0, lapses:0, g:3}` (L1621, L1919). */
    override fun provisionalCard(now: Long): Card =
        Card(s = 0.0, d = 0.0, due = now + PROVISIONAL_DUE_DAYS * DAY_MS, last = now, reps = 0, lapses = 0, g = 3, prov = true)

    /** `{intro:true, s:0, d:0, due:now, last:now, reps:0, lapses:0, g:1}` (L1622). */
    override fun introCard(now: Long): Card =
        Card(s = 0.0, d = 0.0, due = now, last = now, reps = 0, lapses = 0, g = 1, intro = true)
}
