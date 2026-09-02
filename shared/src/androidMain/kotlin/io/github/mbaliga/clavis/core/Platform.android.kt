package io.github.mbaliga.clavis.core

import android.os.Build
import java.security.MessageDigest

actual fun platformName(): String = "Android ${Build.VERSION.SDK_INT}"

actual fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b -> ((b.toInt() and 0xFF) or 0x100).toString(16).substring(1) }
