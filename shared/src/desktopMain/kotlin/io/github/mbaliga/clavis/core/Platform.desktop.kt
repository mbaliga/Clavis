package io.github.mbaliga.clavis.core

import java.security.MessageDigest

actual fun platformName(): String =
    "Desktop (${System.getProperty("os.name")}, JVM ${System.getProperty("java.version")})"

actual fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b -> ((b.toInt() and 0xFF) or 0x100).toString(16).substring(1) }
