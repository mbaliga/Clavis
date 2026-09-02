package io.github.mbaliga.clavis.core.data

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

actual fun gunzip(bytes: ByteArray): ByteArray =
    GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
