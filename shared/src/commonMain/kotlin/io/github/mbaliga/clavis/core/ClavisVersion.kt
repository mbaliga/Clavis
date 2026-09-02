package io.github.mbaliga.clavis.core

/**
 * Single source of truth for the app version as seen from Kotlin code. `NAME` is the fallback
 * input of `gradle/version.gradle.kts`, which derives every emitted number from it (docs/RELEASE.md
 * §5). `CODE` must equal the Android `versionCode` that script emits for `NAME`:
 *
 *     CODE = MAJOR·10000 + MINOR·100 + PATCH        (0.1.0 → 100, 1.4.7 → 10407)
 *
 * A pre-release (`-alpha.N`, `-beta.N`, `-rc.N`) shares the code of its final. `FsrsTest`
 * checks the two agree; `./gradlew :composeApp:clavisVersion` prints what the build will emit.
 */
object ClavisVersion {
    const val NAME = "0.1.0"
    const val CODE = 100
}
