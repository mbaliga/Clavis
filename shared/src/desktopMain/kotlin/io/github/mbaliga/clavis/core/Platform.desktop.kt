package io.github.mbaliga.clavis.core

actual fun platformName(): String =
    "Desktop (${System.getProperty("os.name")}, JVM ${System.getProperty("java.version")})"
