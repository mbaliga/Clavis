package io.github.mbaliga.clavis.core

import platform.UIKit.UIDevice

/** "iOS 18.2" or "iPadOS 18.2": the system's own name for the device family and version. */
actual fun platformName(): String = UIDevice.currentDevice.let { "${it.systemName} ${it.systemVersion}" }

/** Plain-Kotlin digest (`Sha256`), verified against `MessageDigest` on the JVM, so no CommonCrypto binding is on the path. */
actual fun sha256Hex(bytes: ByteArray): String = Sha256.hex(bytes)
