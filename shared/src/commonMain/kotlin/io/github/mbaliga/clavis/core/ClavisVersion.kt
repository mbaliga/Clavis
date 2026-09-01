package io.github.mbaliga.clavis.core

/**
 * Single source of truth for the app version as seen from Kotlin code. Keep in step with
 * `versionName` / `versionCode` in `composeApp/build.gradle.kts` until a later change derives
 * one from the other.
 */
object ClavisVersion {
    const val NAME = "0.1.0"
    const val CODE = 1
}
