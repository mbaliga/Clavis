package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.engine.Fsrs5

/**
 * Retrievability façade kept for callers that only need the forgetting curve. The full FSRS-5
 * scheduler (weights, stability/difficulty updates, `rate()`) is [Fsrs5], which implements
 * [Scheduler]; this object delegates to it and adds argument validation.
 *
 *     R(t, S) = (1 + FACTOR * t / S) ^ DECAY,   DECAY = -0.5,   FACTOR = 19 / 81
 *
 * FACTOR is chosen so that R(S, S) = 0.9 exactly: 0.9^(1/DECAY) - 1 = 19/81.
 */
object Fsrs {
    const val DECAY: Double = Fsrs5.DECAY
    const val FACTOR: Double = Fsrs5.FACTOR

    fun retrievability(elapsedDays: Double, stability: Double): Double {
        require(stability > 0.0) { "stability must be positive, was $stability" }
        require(elapsedDays >= 0.0) { "elapsedDays must be non-negative, was $elapsedDays" }
        return Fsrs5.retr(elapsedDays, stability)
    }
}
