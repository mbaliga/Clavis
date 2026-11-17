package io.github.mbaliga.clavis.core.data

/** Plain-Kotlin inflate (`PureGzip`), verified against `GZIPInputStream` on every shipped pack; it also checks CRC-32 and length. */
actual fun gunzip(bytes: ByteArray): ByteArray = PureGzip.gunzip(bytes)
