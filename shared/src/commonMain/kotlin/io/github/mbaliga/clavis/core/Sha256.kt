package io.github.mbaliga.clavis.core

/**
 * FIPS 180-4 SHA-256 in plain Kotlin, no platform API. The JVM targets keep `MessageDigest`; the iOS
 * actual uses this so that the one digest the app computes (the cache file name derived from a URL)
 * is machine-verified on the JVM against `MessageDigest` rather than resting on an unrun cinterop call.
 */
object Sha256 {
    private val K = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
        0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
        0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
        0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
        0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
        0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
        0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
        0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )

    /** The 32-byte digest of [bytes]. */
    fun digest(bytes: ByteArray): ByteArray {
        val h = intArrayOf(
        0x6a09e667.toInt(), 0xbb67ae85.toInt(), 0x3c6ef372.toInt(), 0xa54ff53a.toInt(),
        0x510e527f.toInt(), 0x9b05688c.toInt(), 0x1f83d9ab.toInt(), 0x5be0cd19.toInt(),
        )
        val bitLen = bytes.size.toLong() * 8
        val padded = bytes.size + 1 + 8
        val total = (padded + 63) / 64 * 64
        val msg = ByteArray(total)
        bytes.copyInto(msg)
        msg[bytes.size] = 0x80.toByte()
        for (i in 0 until 8) msg[total - 1 - i] = (bitLen ushr (8 * i)).toByte()

        val w = IntArray(64)
        var off = 0
        while (off < total) {
            for (t in 0 until 16) {
                val j = off + t * 4
                w[t] = ((msg[j].toInt() and 0xff) shl 24) or ((msg[j + 1].toInt() and 0xff) shl 16) or
                    ((msg[j + 2].toInt() and 0xff) shl 8) or (msg[j + 3].toInt() and 0xff)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotr(7) xor w[t - 15].rotr(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotr(17) xor w[t - 2].rotr(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (t in 0 until 64) {
                val s1 = e.rotr(6) xor e.rotr(11) xor e.rotr(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[t] + w[t]
                val s0 = a.rotr(2) xor a.rotr(13) xor a.rotr(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
            off += 64
        }
        val out = ByteArray(32)
        for (i in 0 until 8) for (k in 0 until 4) out[i * 4 + k] = (h[i] ushr (24 - 8 * k)).toByte()
        return out
    }

    /** Lower-case hex of [digest]. */
    fun hex(bytes: ByteArray): String {
        val d = digest(bytes)
        val sb = StringBuilder(64)
        for (b in d) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4]).append(HEX[v and 15])
        }
        return sb.toString()
    }

    private const val HEX = "0123456789abcdef"
    private fun Int.rotr(n: Int): Int = (this ushr n) or (this shl (32 - n))
}
