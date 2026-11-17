package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.data.PureGzip
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only cross-check of the pure-Kotlin cores the iOS actuals use against the JDK implementations the
 * other targets use. This is what machine-verifies the iOS digest and gunzip path without a Mac.
 */
class PureCoresJvmCrossCheckTest {
    private fun gzip(b: ByteArray, level: Int? = null): ByteArray {
        val bo = ByteArrayOutputStream()
        object : GZIPOutputStream(bo) { init { if (level != null) def.setLevel(level) } }.use { it.write(b) }
        return bo.toByteArray()
    }

    @Test fun sha256MatchesMessageDigestOnRandomLengths() {
        val r = Random(7)
        for (n in (0..300) + listOf(1023, 1024, 1025, 65_537)) {
            val b = r.nextBytes(n)
            val want = MessageDigest.getInstance("SHA-256").digest(b)
            assertContentEquals(want, Sha256.digest(b), "length $n")
        }
    }

    @Test fun gunzipMatchesJdkOnGeneratedInputsAtEveryLevel() {
        val r = Random(11)
        val texty = buildString { repeat(4000) { append("la palabra ${r.nextInt(50)} está aquí; ") } }.encodeToByteArray()
        val inputs = listOf(ByteArray(0), "a".encodeToByteArray(), texty, r.nextBytes(5000), ByteArray(70_000) { (it % 7).toByte() })
        for (level in listOf(0, 1, 6, 9)) for ((i, input) in inputs.withIndex()) {
            val gz = gzip(input, level)
            assertContentEquals(input, PureGzip.gunzip(gz), "level $level input $i")
        }
    }

    @Test fun gunzipMatchesJdkOnEveryShippedPack() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "composeApp/src/commonMain/composeResources/files/packs") }.firstOrNull { it.isDirectory }
        assertTrue(root != null, "packs directory not found from ${File("").absolutePath}")
        val packs = root.walkTopDown().filter { it.isFile && it.name.endsWith(".gz") }.toList()
        assertTrue(packs.size >= 30, "expected the shipped packs, found ${packs.size}")
        var bytes = 0L
        for (f in packs) {
            val raw = f.readBytes()
            val want = GZIPInputStream(raw.inputStream()).readBytes()
            assertContentEquals(want, PureGzip.gunzip(raw), f.name)
            bytes += want.size
        }
        println("PureGzip verified against GZIPInputStream on ${packs.size} packs, $bytes bytes inflated")
        assertEquals(packs.size, packs.distinctBy { it.path }.size)
    }
}
