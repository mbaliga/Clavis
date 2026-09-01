package io.github.mbaliga.clavis.core

import kotlin.math.pow

/**
 * FSRS-5 primitives. Only the retrievability curve lives here for now; the scheduler proper
 * (stability / difficulty updates, the 19 default weights) arrives with the domain layer.
 *
 * Retrievability is the probability of recalling a card `elapsedDays` after the last review,
 * given its stability `S` (the interval in days at which recall probability is 90 %):
 *
 *     R(t, S) = (1 + FACTOR * t / S) ^ DECAY,   DECAY = -0.5,   FACTOR = 19 / 81
 *
 * FACTOR is chosen so that R(S, S) = 0.9 exactly: 0.9^(1/DECAY) - 1 = 19/81.
 */
object Fsrs {
    const val DECAY: Double = -0.5
    const val FACTOR: Double = 19.0 / 81.0

    fun retrievability(elapsedDays: Double, stability: Double): Double {
        require(stability > 0.0) { "stability must be positive, was $stability" }
        require(elapsedDays >= 0.0) { "elapsedDays must be non-negative, was $elapsedDays" }
        return (1.0 + FACTOR * elapsedDays / stability).pow(DECAY)
    }
}
