package io.github.mbaliga.clavis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FsrsTest {
    private val stabilities = listOf(0.5, 1.0, 3.7, 10.0, 90.0, 365.0)

    @Test
    fun retrievabilityIsOneImmediatelyAfterReview() {
        for (s in stabilities) {
            assertEquals(1.0, Fsrs.retrievability(0.0, s), 1e-12, "S=$s")
        }
    }

    @Test
    fun retrievabilityIsNinetyPercentAtStability() {
        // The defining property of FSRS stability: recall probability is 0.9 when t == S.
        for (s in stabilities) {
            assertEquals(0.9, Fsrs.retrievability(s, s), 1e-12, "S=$s")
        }
    }

    @Test
    fun retrievabilityDecreasesMonotonically() {
        for (s in stabilities) {
            var previous = Fsrs.retrievability(0.0, s)
            var t = 0.25
            while (t <= 4 * s) {
                val r = Fsrs.retrievability(t, s)
                assertTrue(r < previous, "S=$s t=$t: $r should be < $previous")
                assertTrue(r > 0.0, "S=$s t=$t: $r should stay positive")
                previous = r
                t += 0.25
            }
        }
    }

    @Test
    fun rejectsNonPositiveStability() {
        assertFailsWith<IllegalArgumentException> { Fsrs.retrievability(1.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { Fsrs.retrievability(1.0, -2.0) }
    }

    @Test
    fun versionSeedIsPresent() {
        assertEquals("0.1.0-rc.1", ClavisVersion.NAME)
        // CODE follows gradle/version.gradle.kts: MAJOR·10000 + MINOR·100 + PATCH of NAME (a pre-release shares its final's code).
        val (major, minor, patch) = ClavisVersion.NAME.substringBefore('-').split('.').map { it.toInt() }
        assertEquals(major * 10_000 + minor * 100 + patch, ClavisVersion.CODE)
        assertTrue(platformName().isNotBlank())
    }
}
