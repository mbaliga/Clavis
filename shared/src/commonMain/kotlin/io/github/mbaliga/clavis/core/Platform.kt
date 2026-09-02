package io.github.mbaliga.clavis.core

/** Human-readable name of the platform this build is running on, e.g. for an About screen. */
expect fun platformName(): String

/** Lower-case hex SHA-256 of [bytes]; cache file names are derived from it so no part of a URL reaches the file system. */
expect fun sha256Hex(bytes: ByteArray): String
