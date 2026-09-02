package io.github.mbaliga.clavis.core.data

/** Inflates a gzip stream (RFC 1952). Both targets use `java.util.zip.GZIPInputStream`. */
expect fun gunzip(bytes: ByteArray): ByteArray

/** Inflates when the bytes carry the gzip magic (`1f 8b`), otherwise returns them unchanged. */
fun maybeGunzip(bytes: ByteArray): ByteArray =
    if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) gunzip(bytes) else bytes
