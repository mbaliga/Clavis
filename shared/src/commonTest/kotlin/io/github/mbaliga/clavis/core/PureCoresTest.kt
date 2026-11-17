package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.data.PureGzip
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Known-answer tests for the pure-Kotlin cores the iOS actuals use; they run on every target, the iOS simulator included. */
@OptIn(ExperimentalEncodingApi::class)
class PureCoresTest {
    @Test fun sha256KnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc".encodeToByteArray()))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
        // 1,000,000 x 'a' (FIPS 180-4 long message)
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", Sha256.hex(ByteArray(1_000_000) { 'a'.code.toByte() }))
    }

    @Test fun sha256PaddingBoundaries() {
        // 55, 56 and 64 bytes straddle the one-block / two-block padding boundary (digests from hashlib).
        assertEquals("eb2c86e932179f4ba13fe8715a26124b77d6bad290b9b4c1cc140cf633300c19", Sha256.hex(ByteArray(55) { 'b'.code.toByte() }))
        assertEquals("a5fc6e203a4c2b657d0d153885932414b2ffc6a93f0f8bf8b3183315e5a7212c", Sha256.hex(ByteArray(56) { 'b'.code.toByte() }))
        assertEquals("a0fab1377f49a759b57f63318262ebe89fabfc990e8e93ceac2984561482b9d4", Sha256.hex(ByteArray(64) { 'b'.code.toByte() }))
    }

    @Test fun sha256HexIsWhatTheNamingUses() {
        assertEquals(64, sha256Hex("https://example.org/a.mp3".encodeToByteArray()).length)
        assertEquals(Sha256.hex("x".encodeToByteArray()), sha256Hex("x".encodeToByteArray()))
    }

    @Test fun crc32KnownVector() {
        assertEquals(0xCBF43926.toInt(), PureGzip.crc32("123456789".encodeToByteArray()))
    }

    @Test fun gunzipMatchesTheRealFixture() {
        val gz = Base64.decode(RealFixture.SCENES_GZ_B64)
        assertEquals(RealFixture.SCENES_JSON, PureGzip.gunzip(gz).decodeToString())
    }

    @Test fun gunzipStoredAndFixedBlocks() {
        // gzip of "hello" with fixed Huffman (zlib level 6) and a stored-only member (level 0)
        val fixed = Base64.decode("H4sIAAAAAAAA/8tIzcnJBwCGphA2BQAAAA==")
        assertEquals("hello", PureGzip.gunzip(fixed).decodeToString())
        val stored = Base64.decode("H4sIAAAAAAAE/wEFAPr/aGVsbG+GphA2BQAAAA==")
        assertEquals("hello", PureGzip.gunzip(stored).decodeToString())
    }

    @Test fun gunzipConcatenatedMembers() {
        val a = Base64.decode("H4sIAAAAAAAA/8tIzcnJBwCGphA2BQAAAA==")
        assertEquals("hellohello", PureGzip.gunzip(a + a).decodeToString())
    }

    @Test fun gunzipRejectsCorruption() {
        val gz = Base64.decode(RealFixture.SCENES_GZ_B64)
        assertFailsWith<PureGzip.GzipException> { PureGzip.gunzip(gz.copyOf(gz.size - 12)) } // truncated
        val badCrc = gz.copyOf().also { it[it.size - 8] = (it[it.size - 8] + 1).toByte() }
        assertFailsWith<PureGzip.GzipException> { PureGzip.gunzip(badCrc) }
        assertFailsWith<PureGzip.GzipException> { PureGzip.gunzip(ByteArray(40) { 7 }) } // not gzip
        assertFailsWith<PureGzip.GzipException> { PureGzip.gunzip(ByteArray(0)) }
    }

    @Test fun emptyPayloadRoundTrips() {
        val empty = Base64.decode("H4sIAAAAAAAA/wMAAAAAAAAAAAA=")
        assertContentEquals(ByteArray(0), PureGzip.gunzip(empty))
    }
}
