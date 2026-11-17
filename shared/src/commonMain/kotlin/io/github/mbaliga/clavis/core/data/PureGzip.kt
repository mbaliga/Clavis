package io.github.mbaliga.clavis.core.data

/**
 * RFC 1951 (DEFLATE) + RFC 1952 (gzip) decoder in plain Kotlin, no platform API. The JVM targets keep
 * `GZIPInputStream`; the iOS actual uses this. It is cross-checked on the JVM against `GZIPInputStream`
 * over every pack the app ships (`PureGzipTest`), which is how the iOS path is machine-verified without
 * a Mac. The gzip trailer's CRC-32 and ISIZE are checked, so a truncated or corrupt pack throws instead
 * of yielding a short document.
 */
object PureGzip {
    class GzipException(message: String) : Exception(message)

    /** Decodes a whole gzip member (concatenated members are decoded in sequence). */
    fun gunzip(input: ByteArray): ByteArray {
        val out = ByteSink(maxOf(input.size * 4, 1024))
        var pos = 0
        var members = 0
        while (pos < input.size) {
            if (members > 0 && (input.size - pos < 18)) break // trailing padding
            pos = member(input, pos, out)
            members++
        }
        if (members == 0) throw GzipException("empty input")
        return out.toByteArray()
    }

    private fun member(src: ByteArray, start: Int, out: ByteSink): Int {
        if (src.size - start < 18) throw GzipException("truncated header")
        if (src[start] != 0x1f.toByte() || src[start + 1] != 0x8b.toByte()) throw GzipException("bad magic")
        if (src[start + 2].toInt() != 8) throw GzipException("unknown compression method")
        val flg = src[start + 3].toInt() and 0xff
        var p = start + 10
        if (flg and 0x04 != 0) { // FEXTRA
            if (p + 2 > src.size) throw GzipException("truncated extra")
            val xlen = (src[p].toInt() and 0xff) or ((src[p + 1].toInt() and 0xff) shl 8)
            p += 2 + xlen
        }
        if (flg and 0x08 != 0) { while (p < src.size && src[p].toInt() != 0) p++; p++ } // FNAME
        if (flg and 0x10 != 0) { while (p < src.size && src[p].toInt() != 0) p++; p++ } // FCOMMENT
        if (flg and 0x02 != 0) p += 2 // FHCRC
        if (p >= src.size) throw GzipException("truncated header")

        val before = out.size
        val inflater = Inflater(src, p, out)
        inflater.run()
        p = inflater.endPos
        if (p + 8 > src.size) throw GzipException("truncated trailer")
        val crc = le32(src, p)
        val isize = le32(src, p + 4)
        val produced = out.size - before
        if (isize != produced) throw GzipException("length mismatch")
        if (crc32(out.buf, before, produced) != crc) throw GzipException("crc mismatch")
        return p + 8
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
            ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

    private val CRC_TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor -0x12477ce0 else c ushr 1 }
        c
    }

    /** CRC-32 (IEEE) of `[off, off+len)`. */
    fun crc32(b: ByteArray, off: Int = 0, len: Int = b.size - off): Int {
        var c = -1
        for (i in off until off + len) c = CRC_TABLE[(c xor b[i].toInt()) and 0xff] xor (c ushr 8)
        return c.inv()
    }

    internal class ByteSink(initial: Int) {
        var buf = ByteArray(initial)
        var size = 0
        fun put(b: Int) { ensure(1); buf[size++] = b.toByte() }
        fun ensure(extra: Int) {
            if (size + extra > buf.size) {
                var n = buf.size * 2
                while (n < size + extra) n *= 2
                buf = buf.copyOf(n)
            }
        }
        fun copyBack(dist: Int, len: Int) {
            if (dist > size) throw GzipException("distance too far back")
            ensure(len)
            var from = size - dist
            repeat(len) { buf[size++] = buf[from++] } // overlapping copies are intended
        }
        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    private class Huffman(lengths: IntArray, n: Int) {
        // canonical decoding (zlib "puff" style): count[len], symbol[] ordered by code
        val count = IntArray(16)
        val symbol = IntArray(n)
        init {
            for (i in 0 until n) count[lengths[i]]++
            count[0] = 0
            val offs = IntArray(16)
            for (l in 1 until 15) offs[l + 1] = offs[l] + count[l]
            for (i in 0 until n) if (lengths[i] != 0) symbol[offs[lengths[i]]++] = i
        }
    }

    private class Inflater(val src: ByteArray, start: Int, val out: ByteSink) {
        private var pos = start
        private var bitBuf = 0
        private var bitCnt = 0
        val endPos: Int get() = pos - bitCnt / 8 // unread whole bytes in the bit buffer go back

        private fun bits(need: Int): Int {
            var v = bitBuf
            while (bitCnt < need) {
                if (pos >= src.size) throw GzipException("unexpected end of data")
                v = v or ((src[pos++].toInt() and 0xff) shl bitCnt)
                bitCnt += 8
            }
            bitBuf = v ushr need
            bitCnt -= need
            return v and ((1 shl need) - 1)
        }

        private fun decode(h: Huffman): Int {
            var code = 0; var first = 0; var index = 0
            for (len in 1..15) {
                code = code or bits(1)
                val c = h.count[len]
                if (code - c < first) return h.symbol[index + (code - first)]
                index += c
                first += c
                first = first shl 1
                code = code shl 1
            }
            throw GzipException("invalid huffman code")
        }

        fun run() {
            do {
                val last = bits(1)
                when (bits(2)) {
                    0 -> stored()
                    1 -> codes(FIXED_LIT, FIXED_DIST)
                    2 -> dynamic()
                    else -> throw GzipException("invalid block type")
                }
            } while (last == 0)
        }

        private fun stored() {
            bitBuf = 0; bitCnt = 0
            if (pos + 4 > src.size) throw GzipException("truncated stored block")
            val len = (src[pos].toInt() and 0xff) or ((src[pos + 1].toInt() and 0xff) shl 8)
            val nlen = (src[pos + 2].toInt() and 0xff) or ((src[pos + 3].toInt() and 0xff) shl 8)
            if (len != (nlen xor 0xffff)) throw GzipException("stored length check failed")
            pos += 4
            if (pos + len > src.size) throw GzipException("truncated stored block")
            out.ensure(len)
            src.copyInto(out.buf, out.size, pos, pos + len)
            out.size += len
            pos += len
        }

        private fun dynamic() {
            val nlen = bits(5) + 257
            val ndist = bits(5) + 1
            val ncode = bits(4) + 4
            if (nlen > 286 || ndist > 30) throw GzipException("bad counts")
            val lengths = IntArray(320)
            for (i in 0 until ncode) lengths[ORDER[i]] = bits(3)
            val lencode = Huffman(lengths, 19)
            val ll = IntArray(320)
            var idx = 0
            while (idx < nlen + ndist) {
                val sym = decode(lencode)
                if (sym < 16) { ll[idx++] = sym; continue }
                var prev = 0
                val rep: Int
                when (sym) {
                    16 -> { if (idx == 0) throw GzipException("no previous length"); prev = ll[idx - 1]; rep = 3 + bits(2) }
                    17 -> rep = 3 + bits(3)
                    else -> rep = 11 + bits(7)
                }
                if (idx + rep > nlen + ndist) throw GzipException("too many lengths")
                repeat(rep) { ll[idx++] = prev }
            }
            if (ll[256] == 0) throw GzipException("no end-of-block code")
            codes(Huffman(ll, nlen), Huffman(ll.copyOfRange(nlen, nlen + ndist), ndist))
        }

        private fun codes(lit: Huffman, dist: Huffman) {
            while (true) {
                var sym = decode(lit)
                if (sym < 256) { out.put(sym); continue }
                if (sym == 256) return
                sym -= 257
                if (sym >= 29) throw GzipException("bad length symbol")
                val len = LEN_BASE[sym] + bits(LEN_EXTRA[sym])
                val ds = decode(dist)
                if (ds >= 30) throw GzipException("bad distance symbol")
                val d = DIST_BASE[ds] + bits(DIST_EXTRA[ds])
                out.copyBack(d, len)
            }
        }
    }

    private val ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
    private val LEN_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LEN_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)

    private val FIXED_LIT: Huffman = Huffman(IntArray(288) { if (it < 144) 8 else if (it < 256) 9 else if (it < 280) 7 else 8 }, 288)
    private val FIXED_DIST: Huffman = Huffman(IntArray(30) { 5 }, 30)
}
