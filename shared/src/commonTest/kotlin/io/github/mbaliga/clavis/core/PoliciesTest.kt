package io.github.mbaliga.clavis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The D-13 download guards and D-7 import refusals as pure functions (the iOS downloader/importer are written against them). */
class PoliciesTest {
    @Test fun httpsOnly() {
        assertTrue(DownloadPolicy.isHttps("https://upload.wikimedia.org/a.mp3"))
        assertTrue(DownloadPolicy.isHttps("HTTPS://example.org/x"))
        assertFalse(DownloadPolicy.isHttps("http://example.org/x"))
        assertFalse(DownloadPolicy.isHttps("file:///etc/passwd"))
        assertFalse(DownloadPolicy.isHttps("ftp://example.org/x"))
        assertFalse(DownloadPolicy.isHttps("https://"))
        assertFalse(DownloadPolicy.isHttps("https:///x"))
        assertFalse(DownloadPolicy.isHttps("//example.org/x"))
        assertFalse(DownloadPolicy.isHttps(""))
    }

    @Test fun redirectsAreCountedAndVetted() {
        val g = DownloadPolicy.RedirectGuard()
        repeat(DownloadPolicy.MAX_REDIRECTS) { assertTrue(g.allow("https://a.example/$it")) }
        assertFalse(g.allow("https://a.example/one-too-many"))
        val h = DownloadPolicy.RedirectGuard()
        assertFalse(h.allow("http://a.example/downgrade"))
        assertEquals(0, h.hops)
    }

    @Test fun sizeCapDeclaredAndRunning() {
        assertTrue(DownloadPolicy.declaredLengthAllowed(-1)) // unknown length
        assertTrue(DownloadPolicy.declaredLengthAllowed(DownloadPolicy.MAX_BYTES))
        assertFalse(DownloadPolicy.declaredLengthAllowed(DownloadPolicy.MAX_BYTES + 1))
        val b = DownloadPolicy.ByteBudget(10)
        assertTrue(b.add(6)); assertTrue(b.add(4)); assertFalse(b.add(1))
        assertEquals(11, b.received)
        assertTrue(DownloadPolicy.isSuccess(204)); assertFalse(DownloadPolicy.isSuccess(302)); assertFalse(DownloadPolicy.isSuccess(404))
    }

    @Test fun importRefusals() {
        val ok = ImportPolicy.decode("song.LRC", "[00:01]hola\nadios".encodeToByteArray(), listOf("txt", "srt", "lrc"))
        assertEquals("song.LRC", ok.name)
        val wrong = assertFailsWith<ImportRefusedException> { ImportPolicy.decode("a.pdf", "x".encodeToByteArray(), listOf("txt")) }
        assertEquals(Copy.importWrongKind(listOf("txt")), wrong.message)
        val big = assertFailsWith<ImportRefusedException> { ImportPolicy.decode("a.txt", ByteArray(ImportLimits.MAX_BYTES.toInt() + 1) { 'a'.code.toByte() }, listOf("txt")) }
        assertEquals(Copy.IMPORT_TOO_LARGE, big.message)
        val bin = assertFailsWith<ImportRefusedException> { ImportPolicy.decode("a.srt", ByteArray(100) { 0 }, listOf("srt")) }
        assertEquals(Copy.IMPORT_NOT_TEXT, bin.message)
        assertFailsWith<ImportRefusedException> { ImportPolicy.checkDeclaredSize(ImportLimits.MAX_BYTES + 1) }
        ImportPolicy.checkDeclaredSize(null); ImportPolicy.checkDeclaredSize(10)
    }
}
